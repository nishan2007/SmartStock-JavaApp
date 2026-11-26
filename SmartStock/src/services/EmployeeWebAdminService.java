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
                JsonObject request=new JsonObject();request.addProperty("name",body.has("name")?body.get("name").getAsString():"Employee browser");
                request.add("apps",body.get("apps"));
                return MobileDeviceService.issue(c,actor,request);
            }

            case "LIST" -> { return Map.of("devices",MobileDeviceService.state(c,actor).get("devices")); }
            case "REVOKE" -> {
                MobileDeviceService.requireAdmin(c,actor);
                UUID device=UUID.fromString(body.get("deviceId").getAsString());
                try(var p=c.prepareStatement("UPDATE mobile_managed_devices SET revoked_at=CURRENT_TIMESTAMP,revision=revision+1 WHERE device_id=? AND revoked_at IS NULL")) {
                    p.setObject(1,device);if(p.executeUpdate()!=1)throw new IllegalArgumentException("Active phone not found.");
                }
                MobileDeviceService.audit(c,actor,"MOBILE_DEVICE_REVOKED","Revoked device "+device+" from all staff apps");
                return Map.of("revoked",true);
            }
            case "STATUS" -> { return status(c); }
            default -> throw new IllegalArgumentException("Unknown employee web administration action.");
        }
    }

    static String activationEntry(String workspace,String employeeUrl) {
        return "minutes".equals(workspace)?"https://customermanagement.deckers.gy/employees/minutes.html":
                "balance-sheet".equals(workspace)?BalanceSheetWorkspaceService.PUBLIC_URL:
                "inventory".equals(workspace)?InventoryWebService.PUBLIC_URL:
                "orders".equals(workspace)?OrdersWebService.PUBLIC_URL:employeeUrl;
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
