package services;

import com.google.gson.*;
import java.sql.*;
import java.util.*;

/** Approved browser adapter; the existing store-side minutes service owns all money changes. */
final class PhoneMinutesWebService {
    private static final Gson JSON=LanJson.create();
    private PhoneMinutesWebService() { }
    static Object execute(Connection c,int user,String name,UUID browser,int location,String action,JsonObject body)throws Exception {
        LanCustomerAccountService.require(c,user,"CUSTOMER_ACCOUNTS");
        try(var p=c.prepareStatement("SELECT 1 FROM user_locations WHERE user_id=? AND location_id=?")){p.setInt(1,user);p.setInt(2,location);try(var r=p.executeQuery()){if(!r.next())throw new SecurityException("This store is not assigned to your account.");}}
        if("bootstrap".equals(action))return Map.of("accounts",LanCustomerAccountService.list(c,user),"drawers",drawers(c,user,location));
        if("history".equals(action))return Map.of("sales",PhoneMinutesService.history(c,body,user,location));
        if(!Set.of("create","reverse").contains(action))throw new IllegalArgumentException("Unknown minutes action.");
        if("reverse".equals(action))LanCustomerAccountService.require(c,user,"REVERSE_PHONE_MINUTES");
        UUID key;try{key=UUID.fromString(body.get("idempotencyKey").getAsString());}catch(Exception e){throw new IllegalArgumentException("A valid retry key is required.");}
        JsonObject canonical=body.deepCopy();canonical.remove("idempotencyKey");String hash=LanSecurity.sha256(JSON.toJson(canonical));
        boolean inserted;
        try(var p=c.prepareStatement("INSERT INTO phone_minutes_web_requests(browser_id,user_id,request_key,location_id,operation,request_hash) VALUES(?,?,?,?,?,?) ON CONFLICT DO NOTHING")){p.setObject(1,browser);p.setInt(2,user);p.setObject(3,key);p.setInt(4,location);p.setString(5,action);p.setString(6,hash);inserted=p.executeUpdate()==1;}
        if(!inserted){try(var p=c.prepareStatement("SELECT location_id,operation,request_hash,response::text FROM phone_minutes_web_requests WHERE browser_id=? AND user_id=? AND request_key=? FOR UPDATE")){p.setObject(1,browser);p.setInt(2,user);p.setObject(3,key);try(var r=p.executeQuery()){if(!r.next()||r.getInt(1)!=location||!action.equals(r.getString(2))||!LanSecurity.constantTimeEquals(hash,r.getString(3))||r.getString(4)==null)throw new SQLException("This retry key was already used for a different minutes request.","22023");return JSON.fromJson(r.getString(4),Map.class);}}}
        Long drawer=body.has("drawerSessionId")&&!body.get("drawerSessionId").isJsonNull()?body.get("drawerSessionId").getAsLong():null;
        var origin=new PhoneMinutesService.BrowserOrigin(browser,"Phone Minutes web",drawer);
        var result="create".equals(action)?PhoneMinutesService.create(c,body,null,user,name,location,origin):PhoneMinutesService.reverse(c,body,null,user,name,location,origin);
        try(var p=c.prepareStatement("UPDATE phone_minutes_web_requests SET response=?::jsonb WHERE browser_id=? AND user_id=? AND request_key=?")){p.setString(1,JSON.toJson(result));p.setObject(2,browser);p.setInt(3,user);p.setObject(4,key);if(p.executeUpdate()!=1)throw new SQLException("Minutes retry record could not be completed.");}
        return result;
    }
    static List<Map<String,Object>> drawers(Connection c,int user,int location)throws SQLException {
        List<Map<String,Object>> rows=new ArrayList<>();try(var p=c.prepareStatement("SELECT s.cash_drawer_session_id,s.drawer_name FROM cash_drawer_sessions s JOIN cash_drawers d ON d.cash_drawer_id=s.cash_drawer_id WHERE s.location_id=? AND s.status='OPEN' AND s.current_cashier_user_id=? AND d.is_active ORDER BY s.drawer_name")){p.setInt(1,location);p.setInt(2,user);try(var r=p.executeQuery()){while(r.next())if(CashDrawerHandoverService.pending(c,r.getLong(1))==null)rows.add(Map.of("sessionId",r.getLong(1),"name",r.getString(2)));}}return rows;
    }
}
