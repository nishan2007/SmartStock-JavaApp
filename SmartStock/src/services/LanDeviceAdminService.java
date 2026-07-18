package services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** Server-only approved-device administration. */
final class LanDeviceAdminService{
    private static final Gson GSON=new Gson();private LanDeviceAdminService(){}
    static Map<String,Object>list(Connection c,int userId)throws Exception{require(c,userId);return map("devices",DeviceManagementService.getAllDevices(c));}
    static Map<String,Object>sessions(Connection c,JsonObject b,int userId)throws Exception{require(c,userId);return map("sessions",DeviceManagementService.getDeviceSessionHistory(c,required(b,"deviceId"),25));}
    static Map<String,Object>update(Connection c,JsonObject b,int userId)throws Exception{require(c,userId);String action=required(b,"action"),id=required(b,"deviceId");
        switch(action){
            case "ACCESS"->DeviceManagementService.updateDeviceApproval(c,id,userId,bool(b,"approved"),bool(b,"allowSales"),bool(b,"allowOrders"),text(b,"notes"));
            case "BLOCK"->{DeviceManagementService.blockDevice(c,id,userId,text(b,"notes"));DeviceCredentialService.revokeCredential(c,id,userId);}
            case "NAME"->DeviceManagementService.updateDeviceFriendlyName(c,id,required(b,"deviceName"));
            case "RECEIPT_CODE"->DeviceManagementService.updateDeviceReceiptCode(c,id,required(b,"receiptCode"));
            case "ROTATE"->DeviceCredentialService.requestRotation(c,id,userId);
            default->throw rule(400,"VALIDATION_ERROR","Device action is invalid.");}
        return map("updated",true);
    }
    static Map<String,Object>security(Connection c,int userId)throws Exception{require(c,userId);SecurityStatusService.Report r=SecurityStatusService.inspect(c);
        return map("healthy",r.healthy(),"tls",r.tls(),"credentialStore",r.credentialStore(),"pendingCredentials",r.pendingCredentials(),
                "issuedCredentials",r.issuedCredentials(),"claimedCredentials",r.claimedCredentials(),"blockedDevices",r.blockedDevices(),
                "broadAuthenticatedPolicies",r.broadAuthenticatedPolicies(),"exposedTablesWithoutRls",r.exposedTablesWithoutRls(),
                "publicSecurityDefiners",r.publicSecurityDefiners(),"latestAuditEpochMillis",r.latestAudit()==null?0:r.latestAudit().toEpochMilli(),
                "latestBackupEpochMillis",r.latestBackup()==null?0:r.latestBackup().toEpochMilli(),"pairingPhrase",r.pairingPhrase(),
                "lanCertificateFingerprint",r.lanCertificateFingerprint(),"warnings",r.warnings());}
    private static void require(Connection c,int userId)throws Exception{try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions p ON p.permission_id=rp.permission_id WHERE u.user_id=? AND UPPER(p.permission_key)='DEVICE_MANAGEMENT' LIMIT 1")){ps.setInt(1,userId);try(ResultSet rs=ps.executeQuery()){if(rs.next())return;}}throw rule(403,"PERMISSION_DENIED","You do not have permission to manage devices.");}
    private static String required(JsonObject b,String k)throws RuleViolation{String v=text(b,k);if(v.isBlank())throw rule(400,"VALIDATION_ERROR",k+" is required.");return v;}
    private static String text(JsonObject b,String k){return b.has(k)&&!b.get(k).isJsonNull()?b.get(k).getAsString().trim():"";}
    private static boolean bool(JsonObject b,String k){return b.has(k)&&b.get(k).getAsBoolean();}
    private static Map<String,Object>map(Object...v){Map<String,Object>m=new LinkedHashMap<>();for(int i=0;i<v.length;i+=2)m.put((String)v[i],v[i+1]);return m;}
    private static RuleViolation rule(int s,String c,String m){return new RuleViolation(s,c,m);}
    static final class RuleViolation extends Exception{private final int status;private final String code;private final String safeMessage;RuleViolation(int s,String c,String m){super(m);status=s;code=c;safeMessage=m;}int status(){return status;}String code(){return code;}String safeMessage(){return safeMessage;}}
}
