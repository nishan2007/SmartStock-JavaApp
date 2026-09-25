package services;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Server-authoritative, append-only cash-count drafts and audit history. */
final class CashDrawerCountHistoryService {
    private static final Gson GSON = new Gson();
    private static final Set<String> TYPES = Set.of("COUNT_EDIT","CLEAR","FLOAT_CALCULATED","HANDOVER","CLOSE","CORRECTION");
    private CashDrawerCountHistoryService() { }

    static Map<String,Object> latest(Connection c,long sessionId,int locationId,UUID deviceId,int userId)throws Exception{
        requirePermission(c,userId,"BALANCE_DRAWER");
        requireSessionAccess(c,sessionId,locationId,deviceId,true);
        Draft row=latestRow(c,sessionId);
        return map("draft",row,"revision",row==null?0:row.revisionNo());
    }

    static Map<String,Object> save(Connection c,JsonObject body,int locationId,UUID deviceId,String deviceName,int userId,String userName)throws Exception{
        requirePermission(c,userId,"BALANCE_DRAWER");
        long sessionId=longValue(body,"sessionId");
        requireSessionAccess(c,sessionId,locationId,deviceId,true);
        lockSession(c,sessionId);
        if(CashDrawerHandoverService.pending(c,sessionId)!=null) {
            try(PreparedStatement p=c.prepareStatement("SELECT current_cashier_user_id FROM cash_drawer_sessions WHERE cash_drawer_session_id=?")) {
                p.setLong(1,sessionId);try(ResultSet r=p.executeQuery()) {
                    if(r.next() && r.getInt(1)==userId)throw new SQLException("Your outgoing count is confirmed. The next cashier must check and accept it.");
                }
            }
        }
        int actual=latestRevision(c,sessionId),expected=intValue(body,"expectedRevision",0);
        if(actual!=expected)throw new Conflict(actual,latestRow(c,sessionId));
        String type=text(body,"eventType","COUNT_EDIT").toUpperCase(Locale.ROOT);
        if(!Set.of("COUNT_EDIT","CLEAR","FLOAT_CALCULATED").contains(type))throw new IllegalArgumentException("The draft event type is invalid.");
        return map("draft",insert(c,sessionId,locationId,actual+1,type,json(body,"denominationCounts"),json(body,"floatCounts"),
                money(body,"countedCash"),money(body,"floatTotal"),money(body,"cashInHand"),expectedCash(c,sessionId),
                bool(body,"complete"),text(body,"reason",null),userId,userName,deviceId,deviceName));
    }

    static Draft appendLifecycle(Connection c,long sessionId,String type,JsonObject body,Integer userId,String userName,UUID deviceId,String deviceName,String reason)throws Exception{
        if(!TYPES.contains(type))throw new IllegalArgumentException("The drawer history event type is invalid.");
        lockSession(c,sessionId);int revision=latestRevision(c,sessionId)+1;
        return insert(c,sessionId,sessionLocation(c,sessionId),revision,type,json(body,"denominationCounts"),json(body,"floatCounts"),
                money(body,"countedCash"),money(body,"floatTotal"),money(body,"cashInHand"),expectedCash(c,sessionId),
                bool(body,"complete"),reason,userId,userName,deviceId,deviceName);
    }

    static Map<String,Object> history(Connection c,long sessionId,int locationId,int userId)throws Exception{
        requirePermission(c,userId,"VIEW_DRAWER_HISTORY");
        try(PreparedStatement p=c.prepareStatement("SELECT location_id FROM cash_drawer_sessions WHERE cash_drawer_session_id=?")){
            p.setLong(1,sessionId);try(ResultSet r=p.executeQuery()){if(!r.next()||r.getInt(1)!=locationId)throw new IllegalArgumentException("Drawer session was not found at this store.");}
        }
        List<Map<String,Object>> rows=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement("SELECT opened_at,opened_by_name,device_name,opening_cash FROM cash_drawer_sessions WHERE cash_drawer_session_id=?")){
            p.setLong(1,sessionId);try(ResultSet r=p.executeQuery()){if(r.next())rows.add(map("revision",0,"eventType","OPEN","createdAt",instant(r,1),"changedByName",r.getString(2),"deviceName",r.getString(3),"countedCash",r.getBigDecimal(4),"denominationCounts",Map.of(),"floatCounts",Map.of(),"reason",null));}
        }
        try(PreparedStatement p=c.prepareStatement("SELECT revision_no,event_type,denomination_counts,float_counts,counted_cash,float_total,cash_in_hand,expected_cash,complete,reason,changed_by_name,device_name,created_at FROM cash_drawer_count_events WHERE cash_drawer_session_id=? ORDER BY revision_no")){
            p.setLong(1,sessionId);try(ResultSet r=p.executeQuery()){while(r.next())rows.add(map("revision",r.getInt(1),"eventType",r.getString(2),"denominationCounts",decode(r.getString(3)),"floatCounts",decode(r.getString(4)),"countedCash",r.getBigDecimal(5),"floatTotal",r.getBigDecimal(6),"cashInHand",r.getBigDecimal(7),"expectedCash",r.getBigDecimal(8),"complete",r.getBoolean(9),"reason",r.getString(10),"changedByName",r.getString(11),"deviceName",r.getString(12),"createdAt",instant(r,13)));}
        }
        return map("sessionId",sessionId,"events",rows);
    }

    private static Draft insert(Connection c,long sessionId,int locationId,int revision,String type,JsonObject denominations,JsonObject floats,BigDecimal counted,BigDecimal floatTotal,BigDecimal cih,BigDecimal expected,boolean complete,String reason,Integer userId,String userName,UUID deviceId,String deviceName)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("INSERT INTO cash_drawer_count_events(cash_drawer_session_id,location_id,revision_no,event_type,denomination_counts,float_counts,counted_cash,float_total,cash_in_hand,expected_cash,complete,reason,changed_by_user_id,changed_by_name,device_id,device_name) VALUES(?,?,?,?,?::jsonb,?::jsonb,?,?,?,?,?,?,?,?,?,?) RETURNING created_at")){
            int i=1;p.setLong(i++,sessionId);p.setInt(i++,locationId);p.setInt(i++,revision);p.setString(i++,type);p.setString(i++,GSON.toJson(denominations));p.setString(i++,GSON.toJson(floats));p.setBigDecimal(i++,counted);p.setBigDecimal(i++,floatTotal);p.setBigDecimal(i++,cih);p.setBigDecimal(i++,expected);p.setBoolean(i++,complete);p.setString(i++,blank(reason));if(userId==null)p.setNull(i++,Types.INTEGER);else p.setInt(i++,userId);p.setString(i++,blank(userName));if(deviceId==null)p.setNull(i++,Types.OTHER);else p.setObject(i++,deviceId);p.setString(i,blank(deviceName));
            try(ResultSet r=p.executeQuery()){r.next();return new Draft(sessionId,revision,type,decode(GSON.toJson(denominations)),decode(GSON.toJson(floats)),counted,floatTotal,cih,expected,complete,reason,userName,deviceName,r.getTimestamp(1).toInstant().toString());}
        }
    }
    private static Draft latestRow(Connection c,long id)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT revision_no,event_type,denomination_counts,float_counts,counted_cash,float_total,cash_in_hand,expected_cash,complete,reason,changed_by_name,device_name,created_at FROM cash_drawer_count_events WHERE cash_drawer_session_id=? ORDER BY revision_no DESC LIMIT 1")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){return r.next()?new Draft(id,r.getInt(1),r.getString(2),decode(r.getString(3)),decode(r.getString(4)),r.getBigDecimal(5),r.getBigDecimal(6),r.getBigDecimal(7),r.getBigDecimal(8),r.getBoolean(9),r.getString(10),r.getString(11),r.getString(12),r.getTimestamp(13).toInstant().toString()):null;}}}
    private static int latestRevision(Connection c,long id)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT COALESCE(MAX(revision_no),0) FROM cash_drawer_count_events WHERE cash_drawer_session_id=?")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){r.next();return r.getInt(1);}}}
    private static void lockSession(Connection c,long id)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM cash_drawer_sessions WHERE cash_drawer_session_id=? FOR UPDATE")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Drawer session was not found.");}}}
    private static void requireSessionAccess(Connection c,long id,int location,UUID device,boolean open)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM cash_drawer_sessions s JOIN cash_drawer_device_assignments a ON a.cash_drawer_id=s.cash_drawer_id AND a.device_id=? AND a.unassigned_at IS NULL WHERE s.cash_drawer_session_id=? AND s.location_id=? AND (?=false OR s.status='OPEN')")){p.setObject(1,device);p.setLong(2,id);p.setInt(3,location);p.setBoolean(4,open);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("This register cannot edit that open drawer session.");}}}
    private static void requirePermission(Connection c,int user,String permission)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions x ON x.permission_id=rp.permission_id WHERE u.user_id=? AND x.permission_key=?")){p.setInt(1,user);p.setString(2,permission);try(ResultSet r=p.executeQuery()){if(r.next())return;}}throw new SQLException("You do not have permission for this drawer history operation.","42501");}
    private static int sessionLocation(Connection c,long id)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT location_id FROM cash_drawer_sessions WHERE cash_drawer_session_id=?")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Drawer session was not found.");return r.getInt(1);}}}
    private static BigDecimal expectedCash(Connection c,long id)throws SQLException{return CashDrawerService.calculateExpectedCash(c,id);}
    private static JsonObject json(JsonObject b,String k){return b.has(k)&&b.get(k).isJsonObject()?b.getAsJsonObject(k):new JsonObject();}
    private static BigDecimal money(JsonObject b,String k){try{return b.has(k)?b.get(k).getAsBigDecimal():BigDecimal.ZERO;}catch(Exception e){return BigDecimal.ZERO;}}
    private static boolean bool(JsonObject b,String k){return b.has(k)&&b.get(k).getAsBoolean();}
    private static long longValue(JsonObject b,String k){return b.get(k).getAsLong();}
    private static int intValue(JsonObject b,String k,int d){return b.has(k)?b.get(k).getAsInt():d;}
    private static String text(JsonObject b,String k,String d){return b.has(k)&&!b.get(k).isJsonNull()?b.get(k).getAsString():d;}
    private static String blank(String s){return s==null||s.isBlank()?null:s.trim();}
    @SuppressWarnings("unchecked") private static Map<String,Integer> decode(String json){if(json==null||json.isBlank())return Map.of();Map<String,Double> raw=GSON.fromJson(json,Map.class);Map<String,Integer> out=new LinkedHashMap<>();if(raw!=null)raw.forEach((k,v)->out.put(k,v==null?null:v.intValue()));return out;}
    private static String instant(ResultSet r,int i)throws SQLException{Timestamp t=r.getTimestamp(i);return t==null?null:t.toInstant().toString();}
    private static Map<String,Object> map(Object...v){Map<String,Object>m=new LinkedHashMap<>();for(int i=0;i<v.length;i+=2)m.put((String)v[i],v[i+1]);return m;}
    record Draft(long sessionId,int revisionNo,String eventType,Map<String,Integer>denominationCounts,Map<String,Integer>floatCounts,BigDecimal countedCash,BigDecimal floatTotal,BigDecimal cashInHand,BigDecimal expectedCash,boolean complete,String reason,String changedByName,String deviceName,String createdAt){}
    static final class Conflict extends Exception{final int currentRevision;final Draft latest;Conflict(int r,Draft d){super("The drawer draft changed on another register. Reload the latest count.");currentRevision=r;latest=d;}}
}
