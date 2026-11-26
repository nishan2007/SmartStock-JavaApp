package services;

import com.google.gson.JsonObject;
import java.sql.*;
import java.util.*;

/** All callers must provide an authenticated server identity and an explicit transaction. */
final class ServerStoreKeyService {
    static final String SELF="STORE_KEYS_SELF", ASSIST="STORE_KEYS_ASSIST", CONFIG="STORE_KEYS_CONFIGURE",
            FORCE="STORE_KEYS_FORCE_RETURN", HISTORY="STORE_KEYS_HISTORY";
    private ServerStoreKeyService() { }

    static boolean allowed(Connection c,int user,String permission)throws SQLException {
        return exists(c,"SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions p ON p.permission_id=rp.permission_id WHERE u.user_id=? AND COALESCE(u.is_active,TRUE) AND p.permission_key=?",user,permission);
    }
    static void require(Connection c,int user,String permission)throws Exception {
        if(!allowed(c,user,permission))throw new Rule(403,"PERMISSION_DENIED","You do not have permission for this key operation.");
    }
    static void access(Connection c,int actor,int location)throws Exception {
        if(!exists(c,"SELECT 1 FROM users u JOIN user_locations ul ON ul.user_id=u.user_id WHERE u.user_id=? AND ul.location_id=? AND COALESCE(u.is_active,TRUE)",actor,location))
            throw new Rule(403,"STORE_ACCESS_DENIED","You do not have access to this store.");
        for(String p:List.of(SELF,ASSIST,CONFIG,FORCE,HISTORY))if(allowed(c,actor,p))return;
        throw new Rule(403,"PERMISSION_DENIED","You do not have access to store keys.");
    }
    static StoreKeys.Dashboard list(Connection c,int actor,int location)throws Exception {
        access(c,actor,location);
        boolean all=allowed(c,actor,ASSIST)||allowed(c,actor,FORCE)||allowed(c,actor,HISTORY)||allowed(c,actor,CONFIG);
        List<StoreKeys.Key> keys=new ArrayList<>();
        try(var p=prepare(c,"""
                SELECT k.key_id,k.key_number,k.label,k.retired,k.card_hash IS NOT NULL,k.version,
                s.custody_id,s.holder_user_id,COALESCE(NULLIF(u.full_name,''),u.username),s.checked_out_at
                FROM store_keys k LEFT JOIN store_key_custody s ON s.key_id=k.key_id AND s.returned_at IS NULL
                LEFT JOIN users u ON u.user_id=s.holder_user_id WHERE k.location_id=? ORDER BY k.key_number
                """,location);var r=p.executeQuery()) {
            while(r.next()) {
                Integer holder=(Integer)r.getObject(8);boolean visible=all||Objects.equals(holder,actor);
                keys.add(new StoreKeys.Key(r.getObject(1,UUID.class),r.getInt(2),r.getString(3),r.getBoolean(4),r.getBoolean(5),r.getLong(6),
                        r.getObject(7,UUID.class),visible?holder:null,visible?r.getString(9):null,r.getString(10)));
            }
        }
        List<StoreKeys.Employee> employees=new ArrayList<>();
        if(allowed(c,actor,ASSIST))try(var p=prepare(c,"SELECT u.user_id,COALESCE(NULLIF(u.full_name,''),u.username) FROM users u JOIN user_locations ul ON ul.user_id=u.user_id WHERE ul.location_id=? AND COALESCE(u.is_active,TRUE) ORDER BY 2",location);var r=p.executeQuery()) {
            while(r.next())employees.add(new StoreKeys.Employee(r.getInt(1),r.getString(2)));
        }
        return new StoreKeys.Dashboard(keys,employees,actor);
    }

    static Map<String,Object> mutate(Connection c,int actor,int location,String action,JsonObject b)throws Exception {
        if(c.getAutoCommit())throw new SQLException("Key changes require a transaction.");
        access(c,actor,location);
        try(var lock=prepare(c,"SELECT location_id FROM locations WHERE location_id=? FOR UPDATE",location);var locked=lock.executeQuery()){if(!locked.next())throw conflict("Location not found.");}
        if("count".equals(action))return count(c,actor,location,b);
        UUID key=uuid(b,"keyId");
        try(var p=prepare(c,"SELECT version,retired,card_hash,label FROM store_keys WHERE key_id=? AND location_id=? FOR UPDATE",key,location);var r=p.executeQuery()) {
            if(!r.next())throw new Rule(404,"KEY_NOT_FOUND","This key does not belong to this store.");
            long version=r.getLong(1);boolean retired=r.getBoolean(2);
            validateVersion(version,b.has("version")?b.get("version").getAsLong():-1);
            UUID custody=null;Integer holder=null;
            try(var q=prepare(c,"SELECT custody_id,holder_user_id FROM store_key_custody WHERE key_id=? AND returned_at IS NULL FOR UPDATE",key);var s=q.executeQuery()) {
                if(s.next()){custody=s.getObject(1,UUID.class);holder=s.getInt(2);}
            }
            String method="MANUAL",reason="";
            if("label".equals(action)) {
                require(c,actor,CONFIG);String label=text(b,"label",120,true);
                update(c,"UPDATE store_keys SET label=? WHERE key_id=?",label,key);
            } else if("pair".equals(action)) {
                require(c,actor,CONFIG);
                if(retired||custody!=null)throw conflict("Return the key before replacing its NFC pairing.");
                String token=text(b,"cardToken",64,true);validateCardToken(token);
                if(exists(c,"SELECT 1 FROM store_keys WHERE card_hash=? AND key_id<>?",LanSecurity.sha256(token),key))throw conflict("This NFC card is already paired with another key.");
                update(c,"UPDATE store_keys SET card_hash=? WHERE key_id=?",LanSecurity.sha256(token),key);method="NFC";
            } else if("retire".equals(action)) {
                require(c,actor,CONFIG);
                if(retired||custody!=null)throw conflict("Only a returned, active key can be retired.");
                update(c,"UPDATE store_keys SET retired=TRUE,card_hash=NULL WHERE key_id=?",key);
            } else if("checkout".equals(action)) {
                holder=b.has("employeeId")?b.get("employeeId").getAsInt():actor;
                require(c,actor,holder==actor?SELF:ASSIST);
                if(retired||custody!=null)throw conflict("This key is not available for checkout.");
                if(!exists(c,"SELECT 1 FROM users u JOIN user_locations ul ON ul.user_id=u.user_id WHERE u.user_id=? AND ul.location_id=? AND COALESCE(u.is_active,TRUE) FOR SHARE OF u,ul",holder,location))
                    throw new Rule(400,"EMPLOYEE_UNAVAILABLE","Choose an active employee assigned to this store.");
                method=method(c,b,key);custody=UUID.randomUUID();
                update(c,"INSERT INTO store_key_custody(custody_id,key_id,location_id,holder_user_id,checked_out_by) VALUES(?,?,?,?,?)",custody,key,location,holder,actor);
            } else if("return".equals(action)||"force-return".equals(action)) {
                if(custody==null)throw conflict("This key has already been returned.");
                if(!custody.equals(uuid(b,"custodyId")))throw conflict("The key holder changed. Refresh and try again.");
                boolean force="force-return".equals(action);
                require(c,actor,force?FORCE:holder==actor?SELF:ASSIST);
                if(force){reason=text(b,"reason",1000,true);if(!b.has("recovered")||!b.get("recovered").getAsBoolean())throw new Rule(400,"RECOVERY_REQUIRED","Confirm the key has physically been recovered.");}
                else method=method(c,b,key);
                update(c,"UPDATE store_key_custody SET returned_by=?,returned_at=CURRENT_TIMESTAMP,return_kind=?,return_reason=? WHERE custody_id=?",actor,force?"FORCED_RETURN":"RETURN",reason,custody);
            } else throw new Rule(400,"INVALID_ACTION","Unknown key operation.");
            update(c,"UPDATE store_keys SET version=version+1,updated_at=CURRENT_TIMESTAMP WHERE key_id=?",key);
            String type=switch(action){case "force-return"->"FORCED_RETURN";case "pair"->"PAIR";default->action.toUpperCase(Locale.ROOT);};
            event(c,key,location,custody,type,actor,holder,method,reason);
            return Map.of("keyId",key,"version",version+1);
        }
    }
    private static Map<String,Object> count(Connection c,int actor,int location,JsonObject b)throws Exception {
        require(c,actor,CONFIG);int desired=b.get("count").getAsInt();
        if(desired<0||desired>1000)throw new Rule(400,"INVALID_COUNT","Enter a key count between 0 and 1000.");
        // Serializes count changes across registers. Reductions use explicit retirement, never delete history.
        try(var p=prepare(c,"SELECT location_id FROM locations WHERE location_id=? FOR UPDATE",location);var r=p.executeQuery()){if(!r.next())throw conflict("Location not found.");}
        int active=0,max=0;
        try(var p=prepare(c,"SELECT COUNT(*) FILTER(WHERE NOT retired),COALESCE(MAX(key_number),0) FROM store_keys WHERE location_id=?",location);var r=p.executeQuery()){r.next();active=r.getInt(1);max=r.getInt(2);}
        if(!b.has("expectedCount")||b.get("expectedCount").getAsInt()!=active)throw conflict("The key count changed. Refresh and try again.");
        if(desired<active)throw new Rule(400,"RETIRE_KEYS_FIRST","Select and retire returned keys individually to reduce the count.");
        for(int i=active;i<desired;i++) {
            UUID key=UUID.randomUUID();int number=++max;
            update(c,"INSERT INTO store_keys(key_id,location_id,key_number,label) VALUES(?,?,?,?)",key,location,number,"Key "+number);
            event(c,key,location,null,"CREATE",actor,null,"MANUAL","");
        }
        return Map.of("count",desired);
    }
    static StoreKeys.History history(Connection c,int actor,int location,JsonObject b)throws Exception {
        access(c,actor,location);boolean all=allowed(c,actor,HISTORY);
        if(!all)require(c,actor,SELF);
        String search=text(b,"search",120,false),from=text(b,"from",10,false),to=text(b,"to",10,false);
        try {if(!from.isEmpty())java.time.LocalDate.parse(from);if(!to.isEmpty())java.time.LocalDate.parse(to);}catch(Exception e){throw new Rule(400,"INVALID_DATE","Use dates in YYYY-MM-DD format.");}
        int offset=b.has("offset")?b.get("offset").getAsInt():0;if(offset<0)throw new Rule(400,"INVALID_OFFSET","Invalid history page.");
        List<StoreKeys.Event> events=new ArrayList<>();
        try(var p=prepare(c,"""
                SELECT e.event_id,k.label,e.event_type,e.actor_name,e.holder_name,e.method,e.reason,e.created_at
                FROM store_key_events e JOIN store_keys k ON k.key_id=e.key_id
                JOIN locations l ON l.location_id=e.location_id
                WHERE e.location_id=? AND (? OR e.holder_user_id=?)
                AND (k.label ILIKE ? OR COALESCE(e.holder_name,'') ILIKE ? OR e.actor_name ILIKE ? OR e.event_type ILIKE ?)
                AND (?='' OR (e.created_at AT TIME ZONE COALESCE(l.timezone,'UTC'))::date>=NULLIF(?,'')::date)
                AND (?='' OR (e.created_at AT TIME ZONE COALESCE(l.timezone,'UTC'))::date<=NULLIF(?,'')::date)
                ORDER BY e.created_at DESC,e.event_id LIMIT 200 OFFSET ?
                """,location,all,actor,"%"+search+"%","%"+search+"%","%"+search+"%","%"+search+"%",from,from,to,to,offset);var r=p.executeQuery()) {
            while(r.next())events.add(new StoreKeys.Event(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8)));
        }
        return new StoreKeys.History(events);
    }
    private static String method(Connection c,JsonObject b,UUID key)throws Exception {
        String token=text(b,"cardToken",64,false);if(token.isEmpty())return "MANUAL";
        validateCardToken(token);
        if(!exists(c,"SELECT 1 FROM store_keys WHERE key_id=? AND card_hash=?",key,LanSecurity.sha256(token)))throw conflict("This card is no longer paired with the selected key.");
        return "NFC";
    }
    static void validateCardToken(String token)throws Rule {
        if(token==null||!token.matches("SSK1[0-9a-f]{32}"))throw new Rule(400,"INVALID_KEY_CARD","Tap a SmartStock key card, not an employee badge.");
    }
    static void validateVersion(long actual,long expected)throws Rule {if(actual!=expected)throw conflict("This key changed. Refresh and confirm again.");}
    private static void event(Connection c,UUID key,int location,UUID custody,String type,int actor,Integer holder,String method,String reason)throws SQLException {
        update(c,"""
                INSERT INTO store_key_events(event_id,key_id,location_id,custody_id,event_type,actor_user_id,actor_name,holder_user_id,holder_name,method,reason)
                VALUES(?,?,?,?,?,?,(SELECT COALESCE(NULLIF(full_name,''),username) FROM users WHERE user_id=?),?,
                (SELECT COALESCE(NULLIF(full_name,''),username) FROM users WHERE user_id=?),?,?)
                """,UUID.randomUUID(),key,location,custody,type,actor,actor,holder,holder,method,reason);
    }
    static UUID uuid(JsonObject b,String field)throws Rule {try{return UUID.fromString(b.get(field).getAsString());}catch(Exception e){throw new Rule(400,"INVALID_REQUEST","A valid "+field+" is required.");}}
    private static String text(JsonObject b,String field,int max,boolean required)throws Rule {
        String s=b.has(field)&&!b.get(field).isJsonNull()?b.get(field).getAsString().trim():"";
        if(s.length()>max||(required&&s.isEmpty()))throw new Rule(400,"INVALID_REQUEST","Enter a valid "+field+" (maximum "+max+" characters).");return s;
    }
    private static PreparedStatement prepare(Connection c,String sql,Object...args)throws SQLException {var p=c.prepareStatement(sql);try{for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p;}catch(SQLException e){p.close();throw e;}}
    private static boolean exists(Connection c,String sql,Object...args)throws SQLException {try(var p=prepare(c,sql,args);var r=p.executeQuery()){return r.next();}}
    private static void update(Connection c,String sql,Object...args)throws SQLException {try(var p=prepare(c,sql,args)){p.executeUpdate();}}
    private static Rule conflict(String message){return new Rule(409,"KEY_STATE_CHANGED",message);}
    static final class Rule extends Exception {
        final int status;final String code;
        Rule(int status,String code,String message){super(message);this.status=status;this.code=code;}
    }
}
