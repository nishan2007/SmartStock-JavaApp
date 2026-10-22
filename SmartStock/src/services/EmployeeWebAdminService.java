package services;

import com.google.gson.JsonObject;
import java.sql.*;
import java.util.*;

/** Called only after LAN device, session, DEVICE_MANAGEMENT and server-console checks. */
final class EmployeeWebAdminService {
    private EmployeeWebAdminService() { }

    static Map<String,Object> execute(Connection c,int actor,JsonObject body)throws Exception {
        String action=body.has("action")?body.get("action").getAsString():"STATUS";
        switch(action) {
            case "START","STOP" -> {
                boolean enabled=action.equals("START");
                try(PreparedStatement p=c.prepareStatement("UPDATE employee_web_runtime SET enabled=?,generation=gen_random_uuid(),gateway_running=FALSE,changed_by_user_id=?,changed_at=CURRENT_TIMESTAMP WHERE runtime_id=1")) {
                    p.setBoolean(1,enabled);p.setInt(2,actor);p.executeUpdate();
                }
                try(Statement s=c.createStatement()) {
                    s.executeUpdate("UPDATE employee_web_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE revoked_at IS NULL");
                    s.executeUpdate("UPDATE employee_web_grants SET consumed_at=CURRENT_TIMESTAMP WHERE consumed_at IS NULL");
                }
                audit(c,actor,"EMPLOYEE_WEB_"+action,"Employee gateway "+action.toLowerCase(Locale.ROOT));
                return status(c);
            }
            case "ISSUE" -> {
                Map<String,Object> state=status(c);
                if(!Boolean.TRUE.equals(state.get("running"))) throw new IllegalArgumentException("Start the employee gateway and wait for it to connect first.");
                String name=body.has("name")?body.get("name").getAsString().trim():"Employee browser";
                if(name.isBlank()||name.length()>120)throw new IllegalArgumentException("Enter a browser name of at most 120 characters.");
                String token=LanSecurity.randomToken();String expires;
                try(PreparedStatement p=c.prepareStatement("INSERT INTO employee_web_grants(grant_hash,device_name,issued_by,expires_at) VALUES(?,?,?,CURRENT_TIMESTAMP+INTERVAL '10 minutes') RETURNING expires_at")) {
                    p.setString(1,LanSecurity.sha256(token));p.setString(2,name);p.setInt(3,actor);
                    try(ResultSet r=p.executeQuery()){r.next();expires=r.getTimestamp(1).toInstant().toString();}
                }
                audit(c,actor,"EMPLOYEE_WEB_QR_ISSUED","Issued single-use activation for "+name);
                String entry=body.has("workspace")&&"orders".equals(body.get("workspace").getAsString())?OrdersWebService.PUBLIC_URL:state.get("url").toString();
                return Map.of("url",entry+"#activate="+token,"expiresAt",expires);
            }
            case "LIST" -> {
                List<Map<String,Object>> rows=new ArrayList<>();
                try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT device_id,device_name,created_at,last_seen_at,revoked_at FROM employee_web_devices ORDER BY created_at DESC")) {
                    while(r.next()) rows.add(Map.of("deviceId",r.getObject(1).toString(),"name",r.getString(2),"createdAt",r.getTimestamp(3).toInstant().toString(),
                            "lastSeenAt",r.getTimestamp(4).toInstant().toString(),"revoked",r.getTimestamp(5)!=null));
                }
                return Map.of("devices",rows);
            }
            case "REVOKE" -> {
                UUID device=UUID.fromString(body.get("deviceId").getAsString());
                try(PreparedStatement p=c.prepareStatement("UPDATE employee_web_devices SET revoked_at=CURRENT_TIMESTAMP WHERE device_id=?")){p.setObject(1,device);if(p.executeUpdate()!=1)throw new IllegalArgumentException("Browser not found.");}
                try(PreparedStatement p=c.prepareStatement("UPDATE employee_web_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE browser_device_id=? AND revoked_at IS NULL")){p.setObject(1,device);p.executeUpdate();}
                audit(c,actor,"EMPLOYEE_WEB_DEVICE_REVOKED","Revoked browser "+device);
                return Map.of("revoked",true);
            }
            case "STATUS" -> { return status(c); }
            default -> throw new IllegalArgumentException("Unknown employee web administration action.");
        }
    }

    static Map<String,Object> status(Connection c)throws SQLException {
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT enabled,gateway_running AND gateway_heartbeat_at>CURRENT_TIMESTAMP-INTERVAL '10 seconds',public_origin FROM employee_web_runtime WHERE runtime_id=1")) {
            if(!r.next()) throw new SQLException("Employee web migration is required.");
            return Map.of("enabled",r.getBoolean(1),"running",r.getBoolean(2),"port",EmployeeWebServer.DEFAULT_PORT,
                    "url",r.getString(3)==null?"":r.getString(3)+"/employees/");
        }
    }
    private static void audit(Connection c,int actor,String event,String detail)throws SQLException {
        try(PreparedStatement p=c.prepareStatement("INSERT INTO security_audit_events(event_type,actor_user_id,details) VALUES(?,?,?)")) {
            p.setString(1,event);p.setInt(2,actor);p.setString(3,detail);p.executeUpdate();
        }
    }
}
