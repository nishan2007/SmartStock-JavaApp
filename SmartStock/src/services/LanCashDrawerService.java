package services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import models.CashDrawerContext;
import models.CashDrawerHandover;
import models.CashDrawerSession;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-only cash drawer domain boundary. */
final class LanCashDrawerService {
    private static final Gson GSON=new Gson();private LanCashDrawerService(){}

    static Map<String,Object> registerState(Connection c,UUID deviceId,int userId,int locationId)throws Exception{
        require(c,userId,"BALANCE_DRAWER");CashDrawerContext drawer=CashDrawerService.resolveDrawerForDevice(c,locationId,deviceId.toString());
        CashDrawerSession session=CashDrawerService.getActiveSessionForDevice(c,locationId,deviceId.toString());
        Map<String,Object>m=map("drawerId",drawer.cashDrawerId(),"drawerName",drawer.drawerName(),"session",session,
                "expectedCash",session==null?BigDecimal.ZERO:CashDrawerService.calculateExpectedCash(c,session.sessionId()),
                "floatMix",session==null?Map.of():CashDrawerService.getDrawerFloatMix(c,session.cashDrawerId()));return m;
    }
    static Map<String,Object> open(Connection c,UUID deviceId,int userId,String userName,int locationId)throws Exception{
        require(c,userId,"BALANCE_DRAWER");return map("session",CashDrawerService.openSessionForDevice(c,locationId,deviceId.toString(),deviceName(c,deviceId),userId,userName,null));
    }
    static Map<String,Object> handover(Connection c,JsonObject body,int userId,String userName)throws Exception{
        require(c,userId,"BALANCE_DRAWER");long id=requiredLong(body,"sessionId");BigDecimal count=requiredMoney(body,"countedCash");
        CashDrawerHandover result=CashDrawerService.recordHandover(c,id,count,text(body,"notes"),userId,userName);return map("handover",result);
    }
    static Map<String,Object> close(Connection c,JsonObject body,int userId,String userName)throws Exception{
        require(c,userId,"BALANCE_DRAWER");long id=requiredLong(body,"sessionId");BigDecimal count=requiredMoney(body,"countedCash");
        CashDrawerSession result=CashDrawerService.closeSession(c,id,count,text(body,"notes"),userId,userName);
        return map("session",result,"handlers",CashDrawerService.listCashHandlers(c,id),
                "returnedAmount",CashDrawerService.calculateReturnedCash(c,id));
    }
    static Map<String,Object> recent(Connection c,int userId,int locationId)throws Exception{
        require(c,userId,"BALANCE_DRAWER");return map("sessions",CashDrawerService.listRecentSessions(c,locationId,null,false));
    }
    static Map<String,Object> revise(Connection c,JsonObject body,int userId,String userName)throws Exception{
        require(c,userId,"BALANCE_DRAWER");CashDrawerSession result=CashDrawerService.reviseClosedSessionCount(c,requiredLong(body,"sessionId"),
                requiredMoney(body,"countedCash"),text(body,"notes"),userId,userName);return map("session",result);
    }

    static Map<String,Object> adminState(Connection c,JsonObject body,int userId)throws Exception{
        require(c,userId,"CASH_DRAWER_MANAGEMENT");Integer location=nullableInt(body,"locationId");boolean inactive=body.has("includeInactive")&&body.get("includeInactive").getAsBoolean();
        BigDecimal target=BigDecimal.ZERO;if(location!=null)try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(change_basket_target_amount,0) FROM company_customization WHERE location_id=?")){
            ps.setInt(1,location);try(ResultSet rs=ps.executeQuery()){if(rs.next())target=rs.getBigDecimal(1);}}
        return map("stores",CashDrawerService.listStores(c),"devices",CashDrawerService.listApprovedDevices(c),
                "drawers",CashDrawerService.listDrawers(c,location,inactive),"assignments",CashDrawerService.listAssignments(c,location,null),"changeBasketTarget",target);
    }
    static Map<String,Object> saveDrawer(Connection c,JsonObject body,int userId)throws Exception{
        require(c,userId,"CASH_DRAWER_MANAGEMENT");DrawerRequest r=parse(body,DrawerRequest.class,"Cash drawer details are invalid.");
        long id=CashDrawerService.saveDrawer(c,r.drawerId(),r.locationId(),r.drawerName(),r.description(),money(r.startingCashAmount()),r.floatMix(),r.active(),userId);return map("drawerId",id);
    }
    static Map<String,Object> assign(Connection c,JsonObject body,int userId)throws Exception{
        require(c,userId,"CASH_DRAWER_MANAGEMENT");CashDrawerService.assignDevice(c,requiredLong(body,"drawerId"),requiredInt(body,"locationId"),
                required(body,"deviceId"),userId,text(body,"notes"));return map("assigned",true);
    }
    static Map<String,Object> unassign(Connection c,JsonObject body,int userId)throws Exception{
        require(c,userId,"CASH_DRAWER_MANAGEMENT");CashDrawerService.unassignAssignment(c,requiredLong(body,"assignmentId"),userId);return map("unassigned",true);
    }
    static Map<String,Object> saveTarget(Connection c,JsonObject body,int userId)throws Exception{
        require(c,userId,"CASH_DRAWER_MANAGEMENT");int location=requiredInt(body,"locationId");BigDecimal target=requiredMoney(body,"targetAmount");
        if(target.signum()<0)throw rule(400,"VALIDATION_ERROR","Change basket target cannot be negative.");
        try(PreparedStatement ps=c.prepareStatement("""
                INSERT INTO company_customization(location_id,change_basket_target_amount) VALUES(?,?)
                ON CONFLICT(location_id) DO UPDATE SET change_basket_target_amount=EXCLUDED.change_basket_target_amount,updated_at=CURRENT_TIMESTAMP
                """)){ps.setInt(1,location);ps.setBigDecimal(2,target);ps.executeUpdate();}return map("targetAmount",target);
    }

    private static void require(Connection c,int userId,String permission)throws Exception{try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions p ON p.permission_id=rp.permission_id WHERE u.user_id=? AND UPPER(p.permission_key)=? LIMIT 1")){ps.setInt(1,userId);ps.setString(2,permission);try(ResultSet rs=ps.executeQuery()){if(rs.next())return;}}throw rule(403,"PERMISSION_DENIED","You do not have permission for this cash drawer operation.");}
    private static String deviceName(Connection c,UUID id)throws SQLException{try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(NULLIF(device_name,''),NULLIF(hostname,''),'LAN API Register') FROM devices WHERE device_id=?")){ps.setObject(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next()?rs.getString(1):"LAN API Register";}}}
    private static int requiredInt(JsonObject b,String k)throws RuleViolation{if(!b.has(k))throw rule(400,"VALIDATION_ERROR",k+" is required.");try{return b.get(k).getAsInt();}catch(Exception e){throw rule(400,"VALIDATION_ERROR",k+" is invalid.");}}
    private static Integer nullableInt(JsonObject b,String k)throws RuleViolation{if(!b.has(k)||b.get(k).isJsonNull())return null;return requiredInt(b,k);}
    private static long requiredLong(JsonObject b,String k)throws RuleViolation{if(!b.has(k))throw rule(400,"VALIDATION_ERROR",k+" is required.");try{return b.get(k).getAsLong();}catch(Exception e){throw rule(400,"VALIDATION_ERROR",k+" is invalid.");}}
    private static BigDecimal requiredMoney(JsonObject b,String k)throws RuleViolation{if(!b.has(k))throw rule(400,"VALIDATION_ERROR",k+" is required.");try{return b.get(k).getAsBigDecimal();}catch(Exception e){throw rule(400,"VALIDATION_ERROR",k+" is invalid.");}}
    private static String required(JsonObject b,String k)throws RuleViolation{String v=text(b,k);if(v.isBlank())throw rule(400,"VALIDATION_ERROR",k+" is required.");return v;}
    private static String text(JsonObject b,String k){return b.has(k)&&!b.get(k).isJsonNull()?b.get(k).getAsString().trim():"";}
    private static BigDecimal money(BigDecimal b){return b==null?BigDecimal.ZERO:b;}
    private static <T>T parse(JsonObject b,Class<T>t,String message)throws RuleViolation{try{T r=GSON.fromJson(b,t);if(r==null)throw new IllegalArgumentException();return r;}catch(Exception e){throw rule(400,"VALIDATION_ERROR",message);}}
    private static Map<String,Object>map(Object...v){Map<String,Object>m=new LinkedHashMap<>();for(int i=0;i<v.length;i+=2)m.put((String)v[i],v[i+1]);return m;}
    private static RuleViolation rule(int s,String c,String m){return new RuleViolation(s,c,m);}
    private record DrawerRequest(Long drawerId,int locationId,String drawerName,String description,BigDecimal startingCashAmount,Map<Integer,Integer>floatMix,boolean active){}
    static final class RuleViolation extends Exception{private final int status;private final String code;private final String safeMessage;RuleViolation(int s,String c,String m){super(m);status=s;code=c;safeMessage=m;}int status(){return status;}String code(){return code;}String safeMessage(){return safeMessage;}}
}
