package services;

import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative lifecycle for moving one physical register between stores. */
final class RegisterTransferService {
    private RegisterTransferService() { }

    static Map<String,Object> prepare(Connection c, JsonObject body, UUID currentDeviceId,
                                      int userId, int sourceLocationId) throws Exception {
        requirePermission(c,userId);
        String requestedDevice=text(body,"deviceId");
        if(!requestedDevice.isBlank()&&!currentDeviceId.toString().equals(requestedDevice))
            throw rule(409,"CURRENT_REGISTER_REQUIRED","Move This Register can only move the register currently in use.");
        int destination=requiredInt(body,"destinationLocationId");
        if(destination==sourceLocationId)throw rule(400,"SAME_STORE","Choose a different destination store.");
        String reason=text(body,"reason");
        if(reason.isBlank())throw rule(400,"REASON_REQUIRED","Enter a reason for moving this register.");
        requireLocation(c,destination);
        expirePrepared(c,currentDeviceId);
        if(hasOpenDrawer(c,currentDeviceId))throw rule(409,"OPEN_DRAWER_SESSION","Close and balance this register's open cash drawer before moving it.");
        String installationId;
        try(PreparedStatement ps=c.prepareStatement("SELECT installation_id FROM devices WHERE device_id=? AND is_approved=TRUE AND is_blocked=FALSE FOR UPDATE")){
            ps.setObject(1,currentDeviceId);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw rule(409,"DEVICE_NOT_MOVABLE","This register is not approved for transfer.");installationId=rs.getString(1);}}
        UUID transferId=UUID.randomUUID();Instant expires=Instant.now().plus(48,ChronoUnit.HOURS);
        try(PreparedStatement ps=c.prepareStatement("""
                INSERT INTO register_transfers(transfer_id,device_id,installation_id,source_location_id,
                  destination_location_id,status,emergency,reason,initiated_by_user_id,expires_at)
                VALUES (?,?,?,?,?,'PREPARED',FALSE,?,?,?)
                """)){ps.setObject(1,transferId);ps.setObject(2,currentDeviceId);ps.setString(3,installationId);ps.setInt(4,sourceLocationId);ps.setInt(5,destination);ps.setString(6,reason);ps.setInt(7,userId);ps.setTimestamp(8,Timestamp.from(expires));ps.executeUpdate();}
        deactivateAssignments(c,currentDeviceId,sourceLocationId,userId,"Register transfer to store "+destination);
        endSessions(c,currentDeviceId);
        DeviceCredentialService.revokeCredential(c,currentDeviceId.toString(),userId);
        audit(c,"REGISTER_TRANSFER_PREPARED",currentDeviceId,userId,"Transfer "+transferId+" from store "+sourceLocationId+" to "+destination);
        return transfer(c,transferId);
    }

    static Map<String,Object> inspect(Connection c, UUID deviceId, int userId) throws Exception {
        requirePermission(c,userId);expirePrepared(c,deviceId);
        try(PreparedStatement ps=c.prepareStatement("SELECT transfer_id FROM register_transfers WHERE device_id=? ORDER BY prepared_at DESC LIMIT 1")){
            ps.setObject(1,deviceId);try(ResultSet rs=ps.executeQuery()){return rs.next()?transfer(c,(UUID)rs.getObject(1)):Map.of("status","NONE");}}
    }

    static Map<String,Object> destinations(Connection c,int userId,int currentLocationId)throws Exception{
        requirePermission(c,userId);java.util.List<Map<String,Object>> rows=new java.util.ArrayList<>();
        try(PreparedStatement ps=c.prepareStatement("SELECT location_id,name,COALESCE(receipt_store_code,'0001') FROM locations WHERE location_id<>? AND COALESCE(is_active,TRUE)=TRUE ORDER BY name")){
            ps.setInt(1,currentLocationId);try(ResultSet rs=ps.executeQuery()){while(rs.next())rows.add(Map.of("locationId",rs.getInt(1),"name",rs.getString(2),"storeCode",rs.getString(3)));}}
        return Map.of("locations",rows);
    }

    static Map<String,Object> cancel(Connection c, JsonObject body, UUID deviceId, int userId) throws Exception {
        requirePermission(c,userId);UUID id=UUID.fromString(required(body,"transferId"));
        UUID movedDevice;
        try(PreparedStatement ps=c.prepareStatement("UPDATE register_transfers SET status='CANCELLED',cancelled_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE transfer_id=? AND status='PREPARED' RETURNING device_id")){
            ps.setObject(1,id);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw rule(409,"TRANSFER_NOT_PREPARED","The transfer is no longer available to cancel.");movedDevice=(UUID)rs.getObject(1);}}
        audit(c,"REGISTER_TRANSFER_CANCELLED",movedDevice,userId,"Transfer "+id+" cancelled by device "+deviceId);return transfer(c,id);
    }

    static UUID requirePreparedForDestination(Connection c,String installationId,int destination) throws Exception{
        try(PreparedStatement ps=c.prepareStatement("""
                SELECT transfer_id FROM register_transfers
                WHERE installation_id=? AND destination_location_id=? AND status='PREPARED' AND expires_at>CURRENT_TIMESTAMP
                ORDER BY prepared_at DESC LIMIT 1 FOR UPDATE
                """)){ps.setString(1,installationId);ps.setInt(2,destination);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw rule(409,"TRANSFER_AUTHORIZATION_REQUIRED","No valid transfer authorization exists for this destination store.");return (UUID)rs.getObject(1);}}
    }

    static void enforceEnrollmentDestination(Connection c,String installationId,int destination)throws Exception{
        try(PreparedStatement ps=c.prepareStatement("SELECT destination_location_id FROM register_transfers WHERE installation_id=? AND status='PREPARED' AND expires_at>CURRENT_TIMESTAMP ORDER BY prepared_at DESC LIMIT 1")){
            ps.setString(1,installationId);try(ResultSet rs=ps.executeQuery()){if(rs.next()&&rs.getInt(1)!=destination)throw rule(409,"WRONG_TRANSFER_DESTINATION","This register is authorized for a different destination store.");}}
    }

    static UUID createEmergency(Connection c,UUID deviceId,String installationId,int destination,String reason)throws Exception{
        if(reason==null||reason.isBlank())throw rule(400,"REASON_REQUIRED","An emergency recovery reason is required.");
        expirePrepared(c,deviceId);UUID id=UUID.randomUUID();
        try(PreparedStatement ps=c.prepareStatement("""
                INSERT INTO register_transfers(transfer_id,device_id,installation_id,source_location_id,destination_location_id,
                  status,emergency,reason,expires_at)
                SELECT ?,?,?,last_store_id,?,'PREPARED',TRUE,?,CURRENT_TIMESTAMP+INTERVAL '24 hours' FROM devices WHERE device_id=?
                """)){ps.setObject(1,id);ps.setObject(2,deviceId);ps.setString(3,installationId);ps.setInt(4,destination);ps.setString(5,reason.trim());ps.setObject(6,deviceId);if(ps.executeUpdate()!=1)throw rule(409,"DEVICE_NOT_FOUND","The register identity was not found in this company.");}
        audit(c,"REGISTER_TRANSFER_EMERGENCY_PREPARED",deviceId,null,"Emergency transfer "+id+" to store "+destination+": "+reason.trim());return id;
    }

    static void importCompanyDeviceForEmergency(Connection c,String installationId,int destination)throws Exception{
        try(PreparedStatement found=c.prepareStatement("SELECT 1 FROM devices WHERE installation_id=?")){found.setString(1,installationId);try(ResultSet rs=found.executeQuery()){if(rs.next())return;}}
        try(PreparedStatement locations=c.prepareStatement("SELECT location_id FROM locations WHERE location_id<>? AND COALESCE(is_active,TRUE)=TRUE")){
            locations.setInt(1,destination);try(ResultSet rs=locations.executeQuery()){while(rs.next())for(JsonObject row:CrossStoreInventoryService.fetchTable(rs.getInt(1),"devices")){
                if(!row.has("installation_id")||!installationId.equals(row.get("installation_id").getAsString()))continue;
                if(row.has("is_blocked")&&row.get("is_blocked").getAsBoolean())throw rule(403,"DEVICE_REVOKED","This device is blocked and cannot be recovered at another store.");
                insertSnapshotDevice(c,row,installationId);return;
            }}}
        throw rule(409,"COMPANY_DEVICE_NOT_FOUND","The register identity was not found in this company's verified store snapshots.");
    }

    static void importPreparedTransferForDestination(Connection c,String installationId,int destination)throws Exception{
        try(PreparedStatement found=c.prepareStatement("SELECT 1 FROM register_transfers WHERE installation_id=? AND destination_location_id=? AND status='PREPARED' AND expires_at>CURRENT_TIMESTAMP")){found.setString(1,installationId);found.setInt(2,destination);try(ResultSet rs=found.executeQuery()){if(rs.next())return;}}
        try(PreparedStatement locations=c.prepareStatement("SELECT location_id FROM locations WHERE location_id<>? AND COALESCE(is_active,TRUE)=TRUE")){
            locations.setInt(1,destination);try(ResultSet rs=locations.executeQuery()){while(rs.next()){
                int source=rs.getInt(1);JsonObject transfer=null;
                for(JsonObject row:CrossStoreInventoryService.fetchTable(source,"register_transfers"))if(row.has("installation_id")&&installationId.equals(row.get("installation_id").getAsString())&&row.has("destination_location_id")&&row.get("destination_location_id").getAsInt()==destination&&"PREPARED".equals(row.get("status").getAsString())){transfer=row;break;}
                if(transfer==null)continue;JsonObject device=null;for(JsonObject row:CrossStoreInventoryService.fetchTable(source,"devices"))if(row.has("installation_id")&&installationId.equals(row.get("installation_id").getAsString())){device=row;break;}if(device==null)throw rule(409,"TRANSFER_DEVICE_MISSING","The prepared transfer's device snapshot is unavailable.");insertSnapshotDevice(c,device,installationId);
                try(PreparedStatement ps=c.prepareStatement("""
                        INSERT INTO register_transfers(transfer_id,device_id,installation_id,source_location_id,destination_location_id,status,emergency,reason,initiated_by_user_id,prepared_at,expires_at)
                        VALUES(?::uuid,?::uuid,?,?,?,'PREPARED',FALSE,?,NULL,?::timestamptz,?::timestamptz) ON CONFLICT(transfer_id) DO NOTHING
                        """)){ps.setString(1,transfer.get("transfer_id").getAsString());ps.setString(2,device.get("device_id").getAsString());ps.setString(3,installationId);ps.setInt(4,source);ps.setInt(5,destination);ps.setString(6,jsonText(transfer,"reason"));ps.setString(7,transfer.get("prepared_at").getAsString());ps.setString(8,transfer.get("expires_at").getAsString());ps.executeUpdate();}return;
            }}}
    }

    private static void insertSnapshotDevice(Connection c,JsonObject row,String installationId)throws SQLException{
        try(PreparedStatement insert=c.prepareStatement("""
                INSERT INTO devices(device_id,installation_id,device_name,hostname,last_store_id,is_approved,is_blocked,
                  allow_sales,allow_orders,receipt_device_code,pairing_public_key,credential_status)
                VALUES(?::uuid,?,?,?,?,FALSE,FALSE,FALSE,FALSE,?,?, 'PENDING') ON CONFLICT(installation_id) DO NOTHING
                """)){insert.setString(1,row.get("device_id").getAsString());insert.setString(2,installationId);insert.setString(3,jsonText(row,"device_name"));insert.setString(4,jsonText(row,"hostname"));if(row.has("last_store_id")&&!row.get("last_store_id").isJsonNull())insert.setInt(5,row.get("last_store_id").getAsInt());else insert.setNull(5,java.sql.Types.INTEGER);insert.setString(6,row.has("receipt_device_code")?row.get("receipt_device_code").getAsString():"0001");insert.setString(7,jsonText(row,"pairing_public_key"));insert.executeUpdate();}
    }

    static void complete(Connection c,UUID transferId,UUID deviceId,int destination)throws Exception{
        try(PreparedStatement ps=c.prepareStatement("""
                UPDATE register_transfers SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
                WHERE transfer_id=? AND device_id=? AND destination_location_id=? AND status='PREPARED' AND expires_at>CURRENT_TIMESTAMP
                """)){ps.setObject(1,transferId);ps.setObject(2,deviceId);ps.setInt(3,destination);if(ps.executeUpdate()!=1)throw rule(409,"TRANSFER_NOT_PREPARED","The transfer can no longer be completed.");}
        try(PreparedStatement ps=c.prepareStatement("UPDATE devices SET last_store_id=?,is_approved=TRUE,is_blocked=FALSE,updated_at=CURRENT_TIMESTAMP WHERE device_id=?")){ps.setInt(1,destination);ps.setObject(2,deviceId);ps.executeUpdate();}
        audit(c,"REGISTER_TRANSFER_COMPLETED",deviceId,null,"Transfer "+transferId+" completed at store "+destination);
    }

    static int synchronizeCompleted(Connection c,int localLocationId)throws SQLException{
        int applied=0;
        try(PreparedStatement locations=c.prepareStatement("SELECT location_id FROM locations WHERE location_id<>? AND COALESCE(is_active,TRUE)=TRUE")){
            locations.setInt(1,localLocationId);try(ResultSet rs=locations.executeQuery()){while(rs.next()){
                for(JsonObject row:CrossStoreInventoryService.fetchTable(rs.getInt(1),"register_transfers")){
                    if(!row.has("source_location_id")||row.get("source_location_id").isJsonNull()||row.get("source_location_id").getAsInt()!=localLocationId||!"COMPLETED".equals(row.get("status").getAsString()))continue;
                    UUID transferId=UUID.fromString(row.get("transfer_id").getAsString());UUID deviceId=UUID.fromString(row.get("device_id").getAsString());
                    try(PreparedStatement update=c.prepareStatement("UPDATE register_transfers SET status='COMPLETED',completed_at=COALESCE(?::timestamptz,CURRENT_TIMESTAMP),updated_at=CURRENT_TIMESTAMP WHERE transfer_id=? AND status='PREPARED'")){
                        update.setString(1,jsonText(row,"completed_at"));update.setObject(2,transferId);if(update.executeUpdate()==1){DeviceCredentialService.revokeCredential(c,deviceId.toString(),null);endSessions(c,deviceId);audit(c,"REGISTER_TRANSFER_REMOTE_COMPLETION",deviceId,null,"Destination confirmed transfer "+transferId);applied++;}}
                }
            }}
        }return applied;
    }

    private static Map<String,Object> transfer(Connection c,UUID id)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("""
                SELECT r.transfer_id,r.device_id,r.source_location_id,r.destination_location_id,r.status,r.emergency,r.reason,
                  r.prepared_at,r.expires_at,COALESCE(s.name,''),COALESCE(d.name,'')
                FROM register_transfers r LEFT JOIN locations s ON s.location_id=r.source_location_id
                JOIN locations d ON d.location_id=r.destination_location_id WHERE r.transfer_id=?
                """)){ps.setObject(1,id);try(ResultSet rs=ps.executeQuery()){if(!rs.next())return Map.of("status","NONE");Map<String,Object>m=new LinkedHashMap<>();m.put("transferId",rs.getObject(1).toString());m.put("deviceId",rs.getObject(2).toString());m.put("sourceLocationId",rs.getObject(3));m.put("destinationLocationId",rs.getInt(4));m.put("status",rs.getString(5));m.put("emergency",rs.getBoolean(6));m.put("reason",rs.getString(7));m.put("preparedAt",rs.getTimestamp(8).toInstant().toString());m.put("expiresAt",rs.getTimestamp(9).toInstant().toString());m.put("sourceStoreName",rs.getString(10));m.put("destinationStoreName",rs.getString(11));return m;}}
    }
    private static void expirePrepared(Connection c,UUID device)throws SQLException{try(PreparedStatement ps=c.prepareStatement("UPDATE register_transfers SET status='EXPIRED',updated_at=CURRENT_TIMESTAMP WHERE device_id=? AND status='PREPARED' AND expires_at<=CURRENT_TIMESTAMP")){ps.setObject(1,device);ps.executeUpdate();}}
    private static boolean hasOpenDrawer(Connection c,UUID device)throws SQLException{try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM cash_drawer_sessions WHERE device_id=? AND status='OPEN' LIMIT 1")){ps.setObject(1,device);try(ResultSet rs=ps.executeQuery()){return rs.next();}}}
    private static void deactivateAssignments(Connection c,UUID device,int location,int user,String notes)throws SQLException{try(PreparedStatement ps=c.prepareStatement("UPDATE cash_drawer_device_assignments SET is_active=FALSE,unassigned_at=CURRENT_TIMESTAMP,unassigned_by_user_id=?,notes=CONCAT_WS(' | ',NULLIF(notes,''),?),updated_at=CURRENT_TIMESTAMP WHERE device_id=? AND location_id=? AND is_active=TRUE")){ps.setInt(1,user);ps.setString(2,notes);ps.setObject(3,device);ps.setInt(4,location);ps.executeUpdate();}}
    private static void endSessions(Connection c,UUID device)throws SQLException{try(PreparedStatement ps=c.prepareStatement("UPDATE device_sessions SET logout_time=CURRENT_TIMESTAMP,session_status='ENDED' WHERE device_id=? AND logout_time IS NULL")){ps.setObject(1,device);ps.executeUpdate();}try(PreparedStatement ps=c.prepareStatement("UPDATE lan_api_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE device_id=? AND revoked_at IS NULL")){ps.setObject(1,device);ps.executeUpdate();}}
    private static void requirePermission(Connection c,int user)throws Exception{try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions p ON p.permission_id=rp.permission_id WHERE u.user_id=? AND UPPER(p.permission_key)='DEVICE_MANAGEMENT' LIMIT 1")){ps.setInt(1,user);try(ResultSet rs=ps.executeQuery()){if(rs.next())return;}}throw rule(403,"PERMISSION_DENIED","You do not have permission to move registers.");}
    private static void requireLocation(Connection c,int id)throws Exception{try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM locations WHERE location_id=? AND COALESCE(is_active,TRUE)=TRUE")){ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()){if(rs.next())return;}}throw rule(400,"DESTINATION_INVALID","Choose an active destination store.");}
    private static void audit(Connection c,String type,UUID device,Integer user,String details)throws SQLException{try(PreparedStatement ps=c.prepareStatement("INSERT INTO security_audit_events(event_type,device_id,actor_user_id,details) VALUES(?,?,?,?)")){ps.setString(1,type);ps.setObject(2,device);if(user==null)ps.setNull(3,java.sql.Types.INTEGER);else ps.setInt(3,user);ps.setString(4,details);ps.executeUpdate();}}
    private static int requiredInt(JsonObject b,String k)throws RuleViolation{try{return b.get(k).getAsInt();}catch(Exception e){throw rule(400,"VALIDATION_ERROR",k+" is required.");}}
    private static String required(JsonObject b,String k)throws RuleViolation{String v=text(b,k);if(v.isBlank())throw rule(400,"VALIDATION_ERROR",k+" is required.");return v;}
    private static String text(JsonObject b,String k){return b!=null&&b.has(k)&&!b.get(k).isJsonNull()?b.get(k).getAsString().trim():"";}
    private static String jsonText(JsonObject b,String k){return b.has(k)&&!b.get(k).isJsonNull()?b.get(k).getAsString():null;}
    private static RuleViolation rule(int status,String code,String message){return new RuleViolation(status,code,message);}
    static final class RuleViolation extends Exception{final int status;final String code;RuleViolation(int status,String code,String message){super(message);this.status=status;this.code=code;}}
}
