package services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-only small cash operations shared by register screens. */
final class LanCashOperationsService{
    private static final Gson GSON=new Gson();private LanCashOperationsService(){}
    static Map<String,Object>changeBasketState(Connection c,int userId,int locationId)throws Exception{
        require(c,userId,"BALANCE_DRAWER");try(PreparedStatement ps=c.prepareStatement("""
                SELECT COALESCE(l.name,'Current Store'),COALESCE(cc.change_basket_target_amount,0)
                FROM locations l LEFT JOIN company_customization cc ON cc.location_id=l.location_id WHERE l.location_id=?
                """)){ps.setInt(1,locationId);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw rule(404,"LOCATION_NOT_FOUND","The selected store was not found.");
            return Map.of("storeName",rs.getString(1),"targetAmount",rs.getBigDecimal(2));}}
    }
    static Map<String,Object>recordChangeBasket(Connection c,JsonObject body,UUID deviceId,int userId,String userName,int locationId)throws Exception{
        require(c,userId,"BALANCE_DRAWER");State state=state(c,locationId);Request r=GSON.fromJson(body,Request.class);
        if(r==null||r.denominationCounts()==null||r.denominationCounts().isEmpty())throw rule(400,"VALIDATION_ERROR","Enter the change basket bill counts.");
        for(Map.Entry<Integer,Integer>e:r.denominationCounts().entrySet())if(e.getKey()==null||e.getKey()<=0||e.getValue()==null||e.getValue()<0||e.getValue()>1_000_000)
            throw rule(400,"VALIDATION_ERROR","Change basket quantities are invalid.");
        long id=CashDrawerService.recordChangeBasketUpdate(c,locationId,state.storeName(),state.target(),r.denominationCounts(),
                r.notes(),userId,userName,deviceId,"LAN API Register");return Map.of("updateId",id);
    }
    private static State state(Connection c,int locationId)throws Exception{Map<String,Object>m=changeBasketStateUnchecked(c,locationId);return new State((String)m.get("storeName"),(BigDecimal)m.get("targetAmount"));}
    private static Map<String,Object>changeBasketStateUnchecked(Connection c,int locationId)throws Exception{try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(l.name,'Current Store'),COALESCE(cc.change_basket_target_amount,0) FROM locations l LEFT JOIN company_customization cc ON cc.location_id=l.location_id WHERE l.location_id=?")){ps.setInt(1,locationId);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw rule(404,"LOCATION_NOT_FOUND","The selected store was not found.");Map<String,Object>m=new LinkedHashMap<>();m.put("storeName",rs.getString(1));m.put("targetAmount",rs.getBigDecimal(2));return m;}}}
    private static void require(Connection c,int u,String p)throws Exception{try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions p ON p.permission_id=rp.permission_id WHERE u.user_id=? AND UPPER(p.permission_key)=? LIMIT 1")){ps.setInt(1,u);ps.setString(2,p);try(ResultSet rs=ps.executeQuery()){if(rs.next())return;}}throw rule(403,"PERMISSION_DENIED","You do not have permission to update the change basket.");}
    private static RuleViolation rule(int s,String c,String m){return new RuleViolation(s,c,m);}private record Request(Map<Integer,Integer>denominationCounts,String notes){}private record State(String storeName,BigDecimal target){}
    static final class RuleViolation extends Exception{private final int status;private final String code;private final String safeMessage;RuleViolation(int s,String c,String m){super(m);status=s;code=c;safeMessage=m;}int status(){return status;}String code(){return code;}String safeMessage(){return safeMessage;}}
}
