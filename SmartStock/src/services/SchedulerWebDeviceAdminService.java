package services;

import com.google.gson.JsonObject;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Server-side administration for automatically registered Scheduler browsers. */
final class SchedulerWebDeviceAdminService {
    private static final Duration REMEMBER_FOR = Duration.ofDays(30);
    private SchedulerWebDeviceAdminService() { }

    static Map<String,Object> list(Connection c,int actor)throws Exception {
        require(c,actor);
        List<Map<String,Object>> rows=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement("""
                SELECT d.device_id::text,d.device_name,COALESCE(u.full_name,u.username),u.username,
                       d.stay_signed_in,d.stay_signed_in_expires_at,d.created_at,d.last_seen_at,d.revoked_at
                FROM scheduler_web_devices d JOIN users u ON u.user_id=d.user_id
                ORDER BY d.last_seen_at DESC,d.created_at DESC
                """);ResultSet r=p.executeQuery()){
            while(r.next()){
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("deviceId",r.getString(1));row.put("deviceName",r.getString(2));
                row.put("employeeName",r.getString(3));row.put("username",r.getString(4));
                row.put("staySignedIn",r.getBoolean(5)&&r.getTimestamp(6)!=null&&r.getTimestamp(6).toInstant().isAfter(Instant.now())&&r.getTimestamp(9)==null);
                row.put("expiresAt",instant(r.getTimestamp(6)));row.put("createdAt",instant(r.getTimestamp(7)));
                row.put("lastSeenAt",instant(r.getTimestamp(8)));row.put("revoked",r.getTimestamp(9)!=null);
                rows.add(row);
            }
        }
        return Map.of("devices",rows);
    }

    static Map<String,Object> update(Connection c,JsonObject body,int actor)throws Exception {
        require(c,actor);UUID id;
        try{id=UUID.fromString(text(body,"deviceId"));}catch(Exception e){throw rule(400,"VALIDATION_ERROR","A valid Scheduler device is required.");}
        boolean enabled=body.has("staySignedIn")&&body.get("staySignedIn").getAsBoolean();
        try(PreparedStatement p=c.prepareStatement("""
                UPDATE scheduler_web_devices SET stay_signed_in=?,stay_signed_in_expires_at=?,revoked_at=NULL
                WHERE device_id=? RETURNING user_id
                """)){
            p.setBoolean(1,enabled);if(enabled)p.setTimestamp(2,Timestamp.from(Instant.now().plus(REMEMBER_FOR)));else p.setNull(2,Types.TIMESTAMP_WITH_TIMEZONE);p.setObject(3,id);
            try(ResultSet r=p.executeQuery()){if(!r.next())throw rule(404,"DEVICE_NOT_FOUND","The Scheduler browser no longer exists.");}
        }
        if(!enabled)try(PreparedStatement p=c.prepareStatement("UPDATE scheduler_web_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE browser_device_id=? AND revoked_at IS NULL")){p.setObject(1,id);p.executeUpdate();}
        return Map.of("updated",true,"staySignedIn",enabled);
    }

    private static void require(Connection c,int userId)throws Exception{
        try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions x ON x.permission_id=rp.permission_id WHERE u.user_id=? AND UPPER(x.permission_key)='DEVICE_MANAGEMENT' LIMIT 1")){p.setInt(1,userId);try(ResultSet r=p.executeQuery()){if(r.next())return;}}
        throw rule(403,"PERMISSION_DENIED","You do not have permission to manage Scheduler devices.");
    }
    private static String text(JsonObject b,String k){return b.has(k)&&!b.get(k).isJsonNull()?b.get(k).getAsString().trim():"";}
    private static String instant(Timestamp value){return value==null?"":value.toInstant().toString();}
    private static RuleViolation rule(int status,String code,String message){return new RuleViolation(status,code,message);}
    static final class RuleViolation extends Exception{final int status;final String code;RuleViolation(int status,String code,String message){super(message);this.status=status;this.code=code;}}
}
