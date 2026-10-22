package services;

import com.google.gson.*;
import java.math.*;
import java.sql.*;
import java.util.*;

/** Server-only loyalty accounting. The caller owns the database transaction. */
public final class LoyaltyService {
    static final Gson JSON = new Gson();
    private LoyaltyService() { }
    public static LoyaltyPolicy.Settings settings(Connection c) throws SQLException {
        try (var p = c.prepareStatement("SELECT version,enabled,authority_location_id,spend_per_award,points_per_award,point_value,minimum_redemption,redemption_cap_percent FROM loyalty_settings ORDER BY version DESC LIMIT 1"); var r = p.executeQuery()) {
            if (!r.next()) return LoyaltyPolicy.Settings.defaults();
            return new LoyaltyPolicy.Settings(r.getLong(1),r.getBoolean(2),(Integer)r.getObject(3),r.getBigDecimal(4),r.getLong(5),r.getBigDecimal(6),r.getLong(7),r.getBigDecimal(8));
        }
    }
    static void lockPolicy(Connection c) throws SQLException {
        requireTransaction(c);
        try (var s = c.createStatement()) { s.execute("SELECT pg_advisory_xact_lock(1936942691,1280262476)"); }
    }
    public static LoyaltyPolicy.Settings saveSettings(Connection c, LoyaltyPolicy.Settings input, int userId, int location) throws Exception {
        CustomerAccountMergeService.requireAdmin(c,userId);
        lockPolicy(c);
        var old = settings(c);
        if (old.version()!=input.version()) throw new SQLException("Loyalty settings changed. Refresh before saving.","40001");
        if (old.authorityLocationId()!=null && old.authorityLocationId()!=location)
            throw new SQLException("Change the shared loyalty rules at the designated authority store.","22023");
        if (!Objects.equals(old.authorityLocationId(),input.authorityLocationId())) {
            try (var s=c.createStatement(); var r=s.executeQuery("SELECT EXISTS(SELECT 1 FROM loyalty_documents UNION ALL SELECT 1 FROM loyalty_lots UNION ALL SELECT 1 FROM loyalty_reservations)")) {
                r.next(); if (r.getBoolean(1)) throw new SQLException("The authority cannot change while loyalty history or unresolved operations exist.","22023");
            }
        }
        if (input.authorityLocationId()!=null && input.authorityLocationId()!=location) {
            // Joining an existing authority adopts its rules, rather than forking the policy.
            var remote=LoyaltyPeer.call(input.authorityLocationId(),location,"SETTINGS",new JsonObject());
            var authoritative=JSON.fromJson(remote.get("settings"),LoyaltyPolicy.Settings.class);
            if (!Objects.equals(authoritative.authorityLocationId(),input.authorityLocationId())) throw new SQLException("The selected server is not the loyalty authority.","22023");
            if(!Objects.equals(old.authorityLocationId(),input.authorityLocationId()))try(var clear=c.createStatement()){clear.executeUpdate("DELETE FROM loyalty_settings");}
            cacheSettings(c,authoritative,userId);
            return authoritative;
        }
        var saved=new LoyaltyPolicy.Settings(old.version()+1,input.enabled(),input.authorityLocationId(),input.spendPerAward(),input.pointsPerAward(),input.pointValue(),input.minimumRedemption(),input.redemptionCapPercent());
        cacheSettings(c,saved,userId);
        audit(c,userId,"LOYALTY_SETTINGS_CHANGED",JSON.toJson(saved));
        return saved;
    }
    static void cacheSettings(Connection c,LoyaltyPolicy.Settings s,Integer userId)throws SQLException {
        try(var p=c.prepareStatement("INSERT INTO loyalty_settings(version,enabled,authority_location_id,spend_per_award,points_per_award,point_value,minimum_redemption,redemption_cap_percent,changed_by) VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(version) DO NOTHING")) {
            p.setLong(1,s.version());p.setBoolean(2,s.enabled());p.setObject(3,s.authorityLocationId());p.setBigDecimal(4,s.spendPerAward());p.setLong(5,s.pointsPerAward());p.setBigDecimal(6,s.pointValue());p.setLong(7,s.minimumRedemption());p.setBigDecimal(8,s.redemptionCapPercent());p.setObject(9,userId);p.executeUpdate();
        }
    }
    static UUID customerUuid(Connection c,int customer)throws SQLException {
        try(var p=c.prepareStatement("SELECT sync_uuid FROM customer_accounts WHERE customer_id=public.canonical_customer_id(?)")) {
            p.setInt(1,customer);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Customer account was not found.","22023");return (UUID)r.getObject(1);}
        }
    }
    static UUID lockCustomer(Connection c,UUID id)throws SQLException {
        requireTransaction(c);
        for(int i=0;i<32;i++) {
            try(var p=c.prepareStatement("INSERT INTO loyalty_customers(customer_uuid) VALUES(?) ON CONFLICT DO NOTHING")){p.setObject(1,id);p.executeUpdate();}
            try(var p=c.prepareStatement("SELECT merged_into FROM loyalty_customers WHERE customer_uuid=? FOR UPDATE")){p.setObject(1,id);try(var r=p.executeQuery()){r.next();UUID target=(UUID)r.getObject(1);if(target==null)return id;id=target;}}
        }
        throw new SQLException("Customer loyalty identity needs administrator review.","22023");
    }
    public static JsonObject balance(Connection c,int customer,int location)throws Exception {
        var s=settings(c);UUID identity=customerUuid(c,customer);
        if(s.authorityLocationId()!=null && s.authorityLocationId()!=location) {
            var b=new JsonObject();b.addProperty("customerUuid",identity.toString());
            return withLocalPending(c,identity,location,LoyaltyPeer.call(s.authorityLocationId(),location,"BALANCE",b));
        }
        return withLocalPending(c,identity,location,authorityBalance(c,identity));
    }
    static JsonObject withLocalPending(Connection c,UUID customer,int location,JsonObject balance)throws SQLException {
        try(var p=c.prepareStatement("SELECT count(*) FROM loyalty_documents WHERE public.canonical_customer_uuid(customer_uuid)=? AND source_location_id=? AND revision>delivered_revision")){p.setObject(1,customer);p.setInt(2,location);try(var r=p.executeQuery()){r.next();balance.addProperty("localPendingDocuments",r.getLong(1));}}
        return balance;
    }
    static JsonObject authorityBalance(Connection c,UUID identity)throws SQLException {
        UUID customer=lockCustomer(c,identity);var out=new JsonObject();
        long points=0;BigDecimal value=BigDecimal.ZERO;var history=new JsonArray();
        for(var lot:lots(c,customer,false)){points=Math.addExact(points,lot.available());value=value.add(lot.value().multiply(BigDecimal.valueOf(lot.available())));}
        long held=0;BigDecimal heldValue=BigDecimal.ZERO;
        try(var p=c.prepareStatement("SELECT COALESCE(sum(points),0),COALESCE(sum(discount_amount),0) FROM loyalty_reservations WHERE customer_uuid=? AND status='RESERVED'")){p.setObject(1,customer);try(var r=p.executeQuery()){r.next();held=r.getLong(1);heldValue=r.getBigDecimal(2);}}
        out.addProperty("points",points-held);out.addProperty("value",value.subtract(heldValue));out.addProperty("reservedPoints",held);
        try(var p=c.prepareStatement("SELECT COALESCE(sum(pending_points),0) FROM loyalty_applied_documents WHERE customer_uuid=?")){p.setObject(1,customer);try(var r=p.executeQuery()){r.next();out.addProperty("pendingPoints",r.getLong(1));}}
        try(var p=c.prepareStatement("SELECT m.kind,m.points,l.point_value,m.reason,m.created_at FROM loyalty_movements m JOIN loyalty_lots l USING(lot_uuid) WHERE l.customer_uuid=? ORDER BY m.movement_id DESC LIMIT 200")){p.setObject(1,customer);try(var r=p.executeQuery()){while(r.next()){var row=new JsonObject();row.addProperty("kind",r.getString(1));row.addProperty("points",r.getLong(2));row.addProperty("pointValue",r.getBigDecimal(3));row.addProperty("reason",r.getString(4));row.addProperty("at",r.getString(5));history.add(row);}}}
        out.add("history",history);out.add("settings",JSON.toJsonTree(settings(c)));return out;
    }
    static List<LoyaltyPolicy.Lot> lots(Connection c,UUID customer,boolean subtractReservations)throws SQLException {
        var result=new ArrayList<LoyaltyPolicy.Lot>();var held=new HashMap<UUID,Long>();
        if(subtractReservations)try(var p=c.prepareStatement("SELECT portions FROM loyalty_reservations WHERE customer_uuid=? AND status='RESERVED'")){p.setObject(1,customer);try(var r=p.executeQuery()){while(r.next())for(var e:JsonParser.parseString(r.getString(1)).getAsJsonArray()){var portion=JSON.fromJson(e,LoyaltyPolicy.Portion.class);held.merge(portion.lotId(),portion.points(),Math::addExact);}}}
        try(var p=c.prepareStatement("SELECT l.lot_uuid,l.point_value,COALESCE(sum(m.points),0) FROM loyalty_lots l LEFT JOIN loyalty_movements m USING(lot_uuid) WHERE l.customer_uuid=? GROUP BY l.lot_uuid ORDER BY l.lot_sequence")){p.setObject(1,customer);try(var r=p.executeQuery()){while(r.next()){UUID id=(UUID)r.getObject(1);result.add(new LoyaltyPolicy.Lot(id,r.getLong(3)-held.getOrDefault(id,0L),r.getBigDecimal(2)));}}}
        return result;
    }
    static LoyaltyPolicy.Redemption reserve(Connection c,UUID document,UUID customer,long points,BigDecimal eligible,int location,boolean preview)throws Exception {
        var policy=settings(c);
        var body=new JsonObject();body.addProperty("documentUuid",document.toString());body.addProperty("customerUuid",customer.toString());body.addProperty("points",points);body.addProperty("eligible",eligible);body.addProperty("preview",preview);
        if(policy.authorityLocationId()==null)throw new SQLException("Configure a loyalty authority first.","22023");
        if(policy.authorityLocationId()!=location){
            if(!preview)try(var intent=data.DB.getConnection();var p=intent.prepareStatement("INSERT INTO loyalty_remote_intents(document_uuid,authority_location_id,source_location_id) VALUES(?,?,?) ON CONFLICT DO NOTHING")){p.setObject(1,document);p.setInt(2,policy.authorityLocationId());p.setInt(3,location);p.executeUpdate();}
            return JSON.fromJson(LoyaltyPeer.call(policy.authorityLocationId(),location,"RESERVE",body).get("redemption"),LoyaltyPolicy.Redemption.class);
        }
        return authorityReserve(c,body,location);
    }
    static LoyaltyPolicy.Redemption authorityReserve(Connection c,JsonObject b,int origin)throws SQLException {
        lockPolicy(c);
        UUID doc=UUID.fromString(b.get("documentUuid").getAsString()),customer=lockCustomer(c,UUID.fromString(b.get("customerUuid").getAsString()));
        boolean preview=b.get("preview").getAsBoolean();String hash=LanSecurity.sha256(b.toString());
        if(!preview)try(var p=c.prepareStatement("SELECT request_hash,points,discount_amount,portions,source_location_id,status FROM loyalty_reservations WHERE document_uuid=?")){p.setObject(1,doc);try(var r=p.executeQuery()){if(r.next()){
            if(!hash.equals(r.getString(1))||r.getInt(5)!=origin||"COMMITTED".equals(r.getString(6)))throw new SQLException("Loyalty reservation conflicts with an earlier request.","22023");
            if("RESERVED".equals(r.getString(6)))return new LoyaltyPolicy.Redemption(r.getLong(2),r.getBigDecimal(3),List.of(JSON.fromJson(r.getString(4),LoyaltyPolicy.Portion[].class)));
        }}}
        var available=authorityBalance(c,customer);
        if(available.get("points").getAsLong()<b.get("points").getAsLong()||available.get("value").getAsBigDecimal().signum()<0)throw new SQLException("Insufficient loyalty points; pending reversals must be earned back first.","22023");
        LoyaltyPolicy.Redemption redemption;
        try{redemption=LoyaltyPolicy.redeem(settings(c),b.get("points").getAsLong(),b.get("eligible").getAsBigDecimal(),lots(c,customer,true));}catch(IllegalArgumentException e){throw new SQLException(e.getMessage(),"22023");}
        if(redemption.discount().compareTo(available.get("value").getAsBigDecimal())>0)throw new SQLException("The redemption exceeds the balance after loyalty reversals.","22023");
        if(!preview)try(var p=c.prepareStatement("INSERT INTO loyalty_reservations(document_uuid,customer_uuid,source_location_id,request_hash,points,discount_amount,status,portions) VALUES(?,?,?,?,?,?,'RESERVED',?::jsonb) ON CONFLICT(document_uuid) DO UPDATE SET status='RESERVED',portions=EXCLUDED.portions,discount_amount=EXCLUDED.discount_amount,updated_at=now()")){p.setObject(1,doc);p.setObject(2,customer);p.setInt(3,origin);p.setString(4,hash);p.setLong(5,redemption.points());p.setBigDecimal(6,redemption.discount());p.setString(7,JSON.toJson(redemption.portions()));p.executeUpdate();}
        return redemption;
    }
    static void movement(Connection c,String key,UUID lot,long points,String kind,UUID doc,String reason,String actor)throws SQLException {
        if(points==0)return;
        try(var p=c.prepareStatement("INSERT INTO loyalty_movements(operation_key,lot_uuid,points,kind,document_uuid,reason,actor) VALUES(?,?,?,?,?,?,?) ON CONFLICT(operation_key) DO NOTHING")){p.setString(1,key);p.setObject(2,lot);p.setLong(3,points);p.setString(4,kind);p.setObject(5,doc);p.setString(6,reason);p.setString(7,actor);p.executeUpdate();}
    }
    /** Apply a monotonic document revision exactly once. Reservations never expire speculatively. */
    static void apply(Connection c,JsonObject b,int origin)throws SQLException {
        lockPolicy(c);
        UUID doc=UUID.fromString(b.get("documentUuid").getAsString()),customer=lockCustomer(c,UUID.fromString(b.get("customerUuid").getAsString()));
        long revision=b.get("revision").getAsLong(),earned=b.get("earnedPoints").getAsLong(),restored=b.get("restoredPoints").getAsLong(),pending=b.get("pendingPoints").getAsLong();
        if(revision<1||earned<0||restored<0||pending<0)throw new SQLException("Invalid loyalty document state.","22023");
        long oldEarned=0,oldRestored=0;
        try(var p=c.prepareStatement("SELECT revision,earned_points,restored_points,source_location_id,customer_uuid FROM loyalty_applied_documents WHERE document_uuid=? FOR UPDATE")){p.setObject(1,doc);try(var r=p.executeQuery()){if(r.next()){
            if(r.getInt(4)!=origin||!customer.equals(lockCustomer(c,(UUID)r.getObject(5))))throw new SQLException("Loyalty document belongs to another origin or customer.","22023");
            if(r.getLong(1)>=revision)return;oldEarned=r.getLong(2);oldRestored=r.getLong(3);
        }}}
        // A peer can only use a policy version that the authority actually issued.
        BigDecimal pointValue;
        try(var p=c.prepareStatement("SELECT point_value FROM loyalty_settings WHERE version=? AND enabled")){p.setLong(1,b.get("settingsVersion").getAsLong());try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Unknown loyalty policy version.","22023");pointValue=r.getBigDecimal(1);}}
        try(var p=c.prepareStatement("INSERT INTO loyalty_lots(lot_uuid,customer_uuid,document_uuid,point_value) VALUES(?,?,?,?) ON CONFLICT DO NOTHING")){p.setObject(1,doc);p.setObject(2,customer);p.setObject(3,doc);p.setBigDecimal(4,pointValue);p.executeUpdate();}
        String prefix=doc+":"+revision+":";
        // Consume reserved lots before releasing this purchase's new points.
        try(var p=c.prepareStatement("SELECT source_location_id,customer_uuid,status,portions,points FROM loyalty_reservations WHERE document_uuid=? FOR UPDATE")){p.setObject(1,doc);try(var r=p.executeQuery()){if(r.next()){
            if(r.getInt(1)!=origin||!customer.equals(lockCustomer(c,(UUID)r.getObject(2))))throw new SQLException("Loyalty reservation belongs to another customer or store.","22023");
            var portions=List.of(JSON.fromJson(r.getString(4),LoyaltyPolicy.Portion[].class));
            if("RESERVED".equals(r.getString(3))){for(var portion:portions)movement(c,doc+":redeem:"+portion.lotId(),portion.lotId(),-portion.points(),"REDEEM",doc,"Purchase redemption","store:"+origin);try(var u=c.prepareStatement("UPDATE loyalty_reservations SET status='COMMITTED',updated_at=now() WHERE document_uuid=?")){u.setObject(1,doc);u.executeUpdate();}}
            if(!b.has("restorationFraction")&&(restored>r.getLong(5)||restored<oldRestored))throw new SQLException("Invalid loyalty restoration.","22023");
            if(b.has("restorationFraction")) {
                BigDecimal fraction=b.get("restorationFraction").getAsBigDecimal();if(fraction.signum()<0||fraction.compareTo(BigDecimal.ONE)>0)throw new SQLException("Invalid loyalty restoration fraction.","22023");
                long restoredTotal=0;
                for(var portion:portions){long target=fraction.multiply(BigDecimal.valueOf(portion.points())).setScale(0,RoundingMode.DOWN).longValueExact(),previous=0;
                    try(var q=c.prepareStatement("SELECT COALESCE(sum(points),0) FROM loyalty_movements WHERE document_uuid=? AND lot_uuid=? AND kind='RESTORE'")){q.setObject(1,doc);q.setObject(2,portion.lotId());try(var rows=q.executeQuery()){rows.next();previous=rows.getLong(1);}}
                    if(target<previous)throw new SQLException("A returned loyalty allocation cannot decrease.","22023");
                    movement(c,prefix+"restore:"+portion.lotId(),portion.lotId(),target-previous,"RESTORE",doc,"Returned purchase points restored","store:"+origin);restoredTotal+=target;
                }restored=restoredTotal;
            }else {
                long skip=oldRestored,left=restored-oldRestored;
                for(var portion:portions){long start=Math.min(skip,portion.points());skip-=start;long count=Math.min(left,portion.points()-start);left-=count;movement(c,prefix+"restore:"+portion.lotId(),portion.lotId(),count,"RESTORE",doc,"Returned purchase points restored","store:"+origin);}
            }
        }else if(restored>0)throw new SQLException("No redemption exists to restore.","22023");}}
        movement(c,prefix+"earn",doc,earned-oldEarned,earned>=oldEarned?"EARN":"REVERSE_EARN",doc,earned>=oldEarned?"Paid purchase points":"Purchase points reversed","store:"+origin);
        offsetDebt(c,customer,prefix,doc,"store:"+origin);
        try(var p=c.prepareStatement("INSERT INTO loyalty_applied_documents(document_uuid,customer_uuid,source_location_id,revision,earned_points,restored_points,pending_points) VALUES(?,?,?,?,?,?,?) ON CONFLICT(document_uuid) DO UPDATE SET revision=EXCLUDED.revision,earned_points=EXCLUDED.earned_points,restored_points=EXCLUDED.restored_points,pending_points=EXCLUDED.pending_points,updated_at=now()")){p.setObject(1,doc);p.setObject(2,customer);p.setInt(3,origin);p.setLong(4,revision);p.setLong(5,earned);p.setLong(6,restored);p.setLong(7,pending);p.executeUpdate();}
    }
    static void audit(Connection c,int user,String event,String details)throws SQLException {
        try(var p=c.prepareStatement("INSERT INTO security_audit_events(event_type,actor_user_id,details) VALUES(?,?,?)")){p.setString(1,event);p.setInt(2,user);p.setString(3,details);p.executeUpdate();}
    }
    /** Debts are denominated in points. Consume unreserved awards explicitly when offsetting them. */
    static void offsetDebt(Connection c,UUID customer,String operation,UUID document,String actor)throws SQLException {
        var available=lots(c,customer,true);var credits=new ArrayList<LoyaltyPolicy.Lot>();
        for(var lot:available)if(lot.available()>0)credits.add(lot);
        int next=0;long remaining=credits.isEmpty()?0:credits.get(0).available();int sequence=0;
        for(var debt:available)if(debt.available()<0){long owed=Math.negateExact(debt.available());
            while(owed>0&&next<credits.size()){
                long count=Math.min(owed,remaining);var credit=credits.get(next);String key=operation+"offset:"+(sequence++);
                movement(c,key+":debt",debt.id(),count,"OFFSET",document,"Points reversal offset by earned points",actor);
                movement(c,key+":award",credit.id(),-count,"OFFSET",document,"Earned points used to offset prior points reversal",actor);
                owed-=count;remaining-=count;if(remaining==0&&++next<credits.size())remaining=credits.get(next).available();
            }
        }
    }
    static void release(Connection c,UUID doc,int origin)throws SQLException {
        lockPolicy(c);
        try(var p=c.prepareStatement("UPDATE loyalty_reservations SET status='RELEASED',updated_at=now() WHERE document_uuid=? AND source_location_id=? AND status='RESERVED' RETURNING customer_uuid")){p.setObject(1,doc);p.setInt(2,origin);try(var r=p.executeQuery()){if(r.next())offsetDebt(c,lockCustomer(c,(UUID)r.getObject(1)),"release:"+doc+":",doc,"store:"+origin);}}
    }
    static void merge(Connection c,UUID source,UUID target)throws SQLException {
        lockPolicy(c);
        UUID from=lockCustomer(c,source),to=lockCustomer(c,target);if(from.equals(to))return;
        try(var p=c.prepareStatement("UPDATE loyalty_lots SET customer_uuid=? WHERE customer_uuid=?")){p.setObject(1,to);p.setObject(2,from);p.executeUpdate();}
        try(var p=c.prepareStatement("UPDATE loyalty_reservations SET customer_uuid=? WHERE customer_uuid=?")){p.setObject(1,to);p.setObject(2,from);p.executeUpdate();}
        try(var p=c.prepareStatement("UPDATE loyalty_applied_documents SET customer_uuid=? WHERE customer_uuid=?")){p.setObject(1,to);p.setObject(2,from);p.executeUpdate();}
        try(var p=c.prepareStatement("UPDATE loyalty_customers SET merged_into=? WHERE customer_uuid=?")){p.setObject(1,to);p.setObject(2,from);p.executeUpdate();}
        offsetDebt(c,to,"merge:"+from+":"+to+":",null,"customer-merge");
    }
    static void adjust(Connection c,int customer,long points,String reason,UUID key,int user,int location)throws SQLException {
        CustomerAccountMergeService.requireAdmin(c,user);lockPolicy(c);
        if(!Objects.equals(settings(c).authorityLocationId(),location))throw new SQLException("Make manual loyalty adjustments at the authority store.","22023");
        if(reason==null||reason.isBlank()||reason.length()>2000||points==0)throw new SQLException("Enter a nonzero adjustment and its reason.","22023");
        UUID identity=lockCustomer(c,customerUuid(c,customer));
        try(var p=c.prepareStatement("INSERT INTO loyalty_lots(lot_uuid,customer_uuid,point_value) VALUES(?,?,?) ON CONFLICT DO NOTHING")){p.setObject(1,key);p.setObject(2,identity);p.setBigDecimal(3,settings(c).pointValue());p.executeUpdate();}
        movement(c,key+":adjust",key,points,"ADJUST",null,reason,"user:"+user);
        offsetDebt(c,identity,key+":",null,"user:"+user);
        audit(c,user,"LOYALTY_ADJUSTMENT",customer+": "+points+"; "+reason);
    }
    static void requireTransaction(Connection c)throws SQLException {if(c.getAutoCommit())throw new SQLException("Loyalty requires a transaction.","25000");}
}
