package services;

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Administrator-only, transactional consolidation of exact name/phone matches. */
public final class CustomerAccountMergeService {
    private static final Gson GSON = new Gson();
    private CustomerAccountMergeService() { }

    public record Group(int survivorId, List<Integer> duplicateIds, String name, String phone,
                        BigDecimal combinedBalance, Map<String,Long> references, String accountFingerprint) { }
    public record Preview(String fingerprint, List<Group> groups) { }
    public record Result(int groupsMerged, int accountsRetired) { }
    private record Account(int id, UUID uuid, String name, String phone, BigDecimal balance, String snapshot) { }
    private record Reference(String schema, String table, String column, String kind) {
        String tableSql() { return quote(schema)+"."+quote(table); }
        String columnSql() { return quote(column); }
        String label() { return schema+"."+table+"."+column; }
    }

    public static Preview preview(Connection c, int userId) throws SQLException {
        requireAdmin(c,userId);
        return plan(c);
    }

    /** Caller owns commit/rollback. A stale preview or any conflict rejects the entire batch. */
    public static Result merge(Connection c, int userId, String expectedFingerprint) throws SQLException {
        requireAdmin(c,userId);
        if(c.getAutoCommit()) throw new SQLException("Customer merges require a transaction.","25000");
        try(var statement=c.createStatement()) {
            statement.execute("SET LOCAL lock_timeout='10s'");
            statement.execute("SELECT pg_advisory_xact_lock(1936942691,1129665101)");
            // Block new financial writes while checking and moving references.
            var tables=new TreeSet<String>();
            tables.add("public.customer_accounts");tables.add("public.customer_account_merges");
            for(var reference:references(c))tables.add(reference.tableSql());
            for(String table:tables)statement.execute("LOCK TABLE "+table+" IN SHARE ROW EXCLUSIVE MODE");
        }
        Preview current=plan(c);
        if(expectedFingerprint==null||!expectedFingerprint.equals(current.fingerprint()))
            throw new SQLException("Customer accounts changed. Preview the duplicates again before merging.","40001");
        int retired=0;
        for(Group group:current.groups()) {
            // Do not silently convert stale balances or unallocated overpayments into a merge.
            for(int id:allIds(group)) {
                BigDecimal balance;
                try(var p=c.prepareStatement("SELECT COALESCE(current_balance,0) FROM customer_accounts WHERE customer_id=?")) {
                    p.setInt(1,id);try(var r=p.executeQuery()){r.next();balance=r.getBigDecimal(1);}
                }
                if(balance.compareTo(CustomerAccountLedgerService.calculateCustomerBalance(c,id))!=0)
                    throw new SQLException("Customer #"+id+" has a balance that needs reconciliation before merging.","55000");
            }
            for(int source:group.duplicateIds()) {
                try(var p=c.prepareStatement("""
                        INSERT INTO customer_account_merges(source_customer_id,target_customer_id,source_uuid,target_uuid,merged_by_user_id)
                        SELECT s.customer_id,t.customer_id,s.sync_uuid,t.sync_uuid,?
                        FROM customer_accounts s CROSS JOIN customer_accounts t
                        WHERE s.customer_id=? AND t.customer_id=?
                        """)) {
                    p.setInt(1,userId);p.setInt(2,source);p.setInt(3,group.survivorId());
                    if(p.executeUpdate()!=1)throw new SQLException("A customer account is no longer available.","40001");
                }
                retired++;
            }
            if(CustomerAccountLedgerService.calculateCustomerBalance(c,group.survivorId()).compareTo(group.combinedBalance())!=0)
                throw new SQLException("Customer #"+group.survivorId()+" has unallocated credits that need review before merging.","55000");
            verifyReferences(c,group);
            try(var p=c.prepareStatement("INSERT INTO security_audit_events(event_type,actor_user_id,details) VALUES('CUSTOMER_ACCOUNTS_MERGED',?,?)")) {
                p.setInt(1,userId);p.setString(2,GSON.toJson(group));p.executeUpdate();
            }
        }
        return new Result(current.groups().size(),retired);
    }

    static void requireAdmin(Connection c,int userId)throws SQLException {
        try(var p=c.prepareStatement("SELECT 1 FROM users u JOIN roles r ON r.role_id=u.role_id WHERE u.user_id=? AND u.is_active=TRUE AND UPPER(r.role_name)='ADMIN'")) {
            p.setInt(1,userId);try(var r=p.executeQuery()) {
                if(!r.next())throw new SQLException("Only an administrator can merge customer accounts.","42501");
            }
        }
    }

    private static Preview plan(Connection c)throws SQLException {
        var grouped=new LinkedHashMap<String,List<Account>>();
        try(var p=c.prepareStatement("""
                WITH candidates AS (
                  SELECT ca.*,lower(regexp_replace(btrim(ca.name),'[[:space:]]+',' ','g')) AS match_name,
                    public.customer_phone_key(ca.phone) AS match_phone,to_jsonb(ca)::text AS snapshot
                  FROM customer_accounts ca WHERE NOT EXISTS(
                    SELECT 1 FROM customer_account_merges m WHERE m.source_customer_id=ca.customer_id)
                ), counted AS (
                  SELECT *,count(*) OVER(PARTITION BY match_name,match_phone) AS matches FROM candidates
                  WHERE match_name<>'' AND match_phone IS NOT NULL
                ) SELECT customer_id,sync_uuid,name,match_phone,COALESCE(current_balance,0),snapshot,match_name
                  FROM counted WHERE matches>1 ORDER BY match_name,match_phone,customer_id
                """);var r=p.executeQuery()) {
            while(r.next()) {
                var account=new Account(r.getInt(1),(UUID)r.getObject(2),r.getString(3),r.getString(4),r.getBigDecimal(5),r.getString(6));
                grouped.computeIfAbsent(GSON.toJson(List.of(r.getString(7),r.getString(4))),key->new ArrayList<>()).add(account);
            }
        }
        var groups=new ArrayList<Group>();
        var referenceColumns=references(c);
        for(var accounts:grouped.values()) {
            var first=accounts.get(0);
            var counts=new LinkedHashMap<String,Long>();
            for(var ref:referenceColumns) {
                var values=accounts.stream().skip(1).map(a->ref.kind().equals("UUID")?a.uuid():a.id()).toArray();
                try(var p=c.prepareStatement("SELECT count(*) FROM "+ref.tableSql()+" WHERE "+ref.columnSql()+"=ANY(?)")) {
                    var array=c.createArrayOf(ref.kind().equals("UUID")?"uuid":"integer",values);
                    try {p.setArray(1,array);try(var r=p.executeQuery()){r.next();long count=r.getLong(1);if(count>0)counts.put(ref.label(),count);}}
                    finally{array.free();}
                }
            }
            BigDecimal balance=accounts.stream().map(Account::balance).reduce(BigDecimal.ZERO,BigDecimal::add);
            groups.add(new Group(first.id(),accounts.stream().skip(1).map(Account::id).toList(),first.name(),first.phone(),balance,counts,
                    LanSecurity.sha256(GSON.toJson(accounts.stream().map(Account::snapshot).toList()))));
        }
        return new Preview(LanSecurity.sha256(GSON.toJson(groups)),groups);
    }

    private static void verifyReferences(Connection c,Group group)throws SQLException {
        for(var reference:references(c)) {
            for(int source:group.duplicateIds()) {
                String predicate=reference.kind().equals("UUID")
                        ? reference.columnSql()+"=(SELECT sync_uuid FROM customer_accounts WHERE customer_id=?)"
                        : reference.columnSql()+"=?";
                try(var p=c.prepareStatement("SELECT 1 FROM "+reference.tableSql()+" WHERE "+predicate+" LIMIT 1")) {
                    p.setInt(1,source);try(var r=p.executeQuery()) {
                        if(r.next())throw new SQLException("A customer reference could not be moved: "+reference.label(),"55000");
                    }
                }
            }
        }
    }

    private static List<Integer> allIds(Group group) {
        var ids=new ArrayList<Integer>();ids.add(group.survivorId());ids.addAll(group.duplicateIds());return ids;
    }
    private static List<Reference> references(Connection c)throws SQLException {
        var result=new ArrayList<Reference>();
        try(var s=c.createStatement();var r=s.executeQuery("SELECT * FROM public.customer_reference_columns() ORDER BY schema_name,table_name,column_name")) {
            while(r.next())result.add(new Reference(r.getString(1),r.getString(2),r.getString(3),r.getString(4)));
        }return result;
    }
    private static String quote(String identifier) {return '"'+identifier.replace("\"","\"\"")+'"';}

    /** Used by authenticated cross-store reference synchronization. No profile credentials leave the server. */
    static void applyMapping(Connection c,com.google.gson.JsonObject row)throws SQLException {
        try(var p=c.prepareStatement("""
                INSERT INTO customer_account_merges(source_customer_id,target_customer_id,source_uuid,target_uuid,merged_by_user_id,updated_at)
                VALUES(?,?,?,?,?,?) ON CONFLICT(source_customer_id) DO NOTHING
                """)) {
            p.setInt(1,row.get("source_customer_id").getAsInt());p.setInt(2,row.get("target_customer_id").getAsInt());
            p.setObject(3,UUID.fromString(row.get("source_uuid").getAsString()));p.setObject(4,UUID.fromString(row.get("target_uuid").getAsString()));
            p.setInt(5,row.get("merged_by_user_id").getAsInt());p.setTimestamp(6,Timestamp.from(java.time.Instant.parse(row.get("updated_at").getAsString())));p.executeUpdate();
        }
        try(var p=c.prepareStatement("SELECT target_customer_id,source_uuid,target_uuid FROM customer_account_merges WHERE source_customer_id=?")) {
            p.setInt(1,row.get("source_customer_id").getAsInt());try(var r=p.executeQuery()) {
                if(!r.next()||r.getInt(1)!=row.get("target_customer_id").getAsInt()
                        ||!r.getObject(2).toString().equals(row.get("source_uuid").getAsString())
                        ||!r.getObject(3).toString().equals(row.get("target_uuid").getAsString()))
                    throw new SQLException("Conflicting customer merge mapping requires administrator review.","55000");
            }
        }
    }
}
