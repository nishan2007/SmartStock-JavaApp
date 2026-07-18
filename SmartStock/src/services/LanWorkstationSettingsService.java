package services;

import managers.ServerReceiptNumberManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-only register receipt identity and selected-store timezone settings. */
final class LanWorkstationSettingsService {
    private LanWorkstationSettingsService() { }

    static Map<String,Object> load(Connection connection, UUID deviceId, int userId,
                                   int locationId) throws Exception {
        requireAny(connection, userId, "LOCAL_DEVICE_SETTINGS", "LOCATION_MANAGEMENT", "COMPANY_PREFERENCES");
        ServerReceiptNumberManager.DeviceReceiptSettings settings =
                ServerReceiptNumberManager.getDeviceReceiptSettings(connection, locationId, deviceId);
        String timezone;
        try (PreparedStatement ps=connection.prepareStatement("SELECT COALESCE(timezone,'') FROM locations WHERE location_id=?")) {
            ps.setInt(1,locationId); try(ResultSet rs=ps.executeQuery()) {
                if(!rs.next()) throw rule(404,"LOCATION_NOT_FOUND","The selected store was not found.");
                timezone=rs.getString(1);
            }
        }
        return map("deviceCode",settings.deviceId(),"storeCode",settings.storeCode(),
                "nextSequence",settings.nextSequence(),"nextReceiptPreview",settings.nextReceiptPreview(),
                "nextReceiveSequence",settings.nextReceiveSequence(),"nextReceivePreview",settings.nextReceivePreview(),
                "timezone",timezone);
    }

    static Map<String,Object> updateDeviceCode(Connection connection, UUID deviceId, int userId,
                                               String requestedCode) throws Exception {
        require(connection,userId,"LOCAL_DEVICE_SETTINGS");
        String saved;
        try { saved=ServerReceiptNumberManager.updateDeviceId(connection,deviceId,requestedCode); }
        catch(IllegalArgumentException ex){throw rule(400,"VALIDATION_ERROR",ex.getMessage());}
        return Map.of("deviceCode",saved);
    }

    static Map<String,Object> updateTimezone(Connection connection,int userId,int locationId,
                                             String requestedTimezone)throws Exception{
        requireAny(connection,userId,"LOCATION_MANAGEMENT","COMPANY_PREFERENCES");
        String timezone=requestedTimezone==null?"":requestedTimezone.trim();
        if(timezone.length()>100)throw rule(400,"VALIDATION_ERROR","Store timezone is too long.");
        try{ZoneId.of(timezone);}catch(Exception ex){throw rule(400,"VALIDATION_ERROR","Enter a valid store timezone.");}
        try(PreparedStatement ps=connection.prepareStatement("UPDATE locations SET timezone=? WHERE location_id=?")){
            ps.setString(1,timezone);ps.setInt(2,locationId);
            if(ps.executeUpdate()!=1)throw rule(404,"LOCATION_NOT_FOUND","The selected store was not found.");
        }
        return Map.of("timezone",timezone);
    }

    private static void require(Connection c,int userId,String permission)throws Exception{
        if(!has(c,userId,permission))throw rule(403,"PERMISSION_DENIED","You do not have permission to change this workstation setting.");
    }
    private static void requireAny(Connection c,int userId,String...permissions)throws Exception{
        for(String permission:permissions)if(has(c,userId,permission))return;
        throw rule(403,"PERMISSION_DENIED","You do not have permission to view workstation settings.");
    }
    private static boolean has(Connection c,int userId,String permission)throws Exception{
        try(PreparedStatement ps=c.prepareStatement("""
                SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id
                JOIN permissions p ON p.permission_id=rp.permission_id
                WHERE u.user_id=? AND UPPER(p.permission_key)=? LIMIT 1
                """)){ps.setInt(1,userId);ps.setString(2,permission);try(ResultSet rs=ps.executeQuery()){return rs.next();}}
    }
    private static Map<String,Object>map(Object...v){Map<String,Object>m=new LinkedHashMap<>();for(int i=0;i<v.length;i+=2)m.put((String)v[i],v[i+1]);return m;}
    private static RuleViolation rule(int s,String c,String m){return new RuleViolation(s,c,m);}
    static final class RuleViolation extends Exception{
        private final int status;private final String code;private final String safeMessage;
        RuleViolation(int status,String code,String safeMessage){super(safeMessage);this.status=status;this.code=code;this.safeMessage=safeMessage;}
        int status(){return status;}String code(){return code;}String safeMessage(){return safeMessage;}
    }
}
