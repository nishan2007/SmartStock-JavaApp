package services;

import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Permission-checked LAN administration facade for store-server coordination. */
final class LanServerAdminService {
    private LanServerAdminService() { }

    static Map<String,Object> list(Connection connection, int userId, int locationId) throws Exception {
        require(connection, userId, "DEVICE_MANAGEMENT");
        return map("servers", CloudServerRegistryService.list(locationId),
                "events",CloudServerRegistryService.events(locationId),
                "currentServerInstanceId", CloudServerRegistryService.currentInstanceId(),
                "localRole", ServerRoleGuard.state().name());
    }

    static Map<String,Object> mutate(Connection connection, JsonObject body, int userId,
                                     String actorName, int locationId) throws Exception {
        require(connection, userId, "DEVICE_MANAGEMENT");
        String action = required(body, "action");
        String id = required(body, "serverInstanceId");
        switch (action) {
            case "PREPARE_STANDBY" -> {
                var servers=CloudServerRegistryService.list(locationId);
                var standby=servers.stream().filter(s->id.equals(s.serverInstanceId())).findFirst().orElseThrow(
                        ()->rule(409,"STANDBY_NOT_FOUND","The standby server was not found."));
                var primary=CloudServerRegistryService.primary(servers);
                validateStandbyReadiness(primary,standby);
                CloudServerRegistryService.recordStandbyPrepared(locationId,id,userId,actorName);
                return map("prepared",true,"server",standby);
            }
            case "BEGIN_HANDOFF" -> {
                String current = CloudServerRegistryService.currentInstanceId();
                if (current == null) throw rule(409,"SERVER_IDENTITY_MISSING","This primary server is not registered.");
                if (!current.equals(id)) throw rule(409,"SERVER_NOT_LOCAL_PRIMARY","A handoff must be started by the active physical server.");
                String target = required(body,"targetServerInstanceId");
                var servers=CloudServerRegistryService.list(locationId);
                var sourceRecord=servers.stream().filter(s->current.equals(s.serverInstanceId())).findFirst().orElseThrow(
                        ()->rule(409,"SERVER_IDENTITY_MISSING","The active primary registry record is missing."));
                var targetRecord=servers.stream().filter(s->target.equals(s.serverInstanceId())).findFirst().orElseThrow(
                        ()->rule(409,"STANDBY_NOT_FOUND","The replacement standby was not found."));
                validateStandbyReadiness(sourceRecord,targetRecord);
                String key = required(body,"idempotencyKey");
                CloudServerRegistryService.prepareHandoffRollback(current,key);
                try {
                    CloudServerRegistryService.HandoffResult started = CloudServerRegistryService.beginHandoff(
                            locationId,current,target,key,userId,actorName);
                    SyncWorker.SyncStatus sync = SyncWorker.runOnceNow();
                    if (!sync.cloudReachable() || sync.lastError()!=null)throw rule(409,"FINAL_SYNC_FAILED",
                            "The final sync failed; this server is being returned to primary mode.");
                    CloudServerRegistryService.heartbeatCurrent(connection,null);
                    var ready=CloudServerRegistryService.markReady(locationId,current,started.handoffId());
                    CloudServerRegistryService.clearHandoffRollback();
                    return map("handoff",ready);
                } catch(Exception failure) {
                    try { CloudServerRegistryService.reconcilePendingHandoffRollback(locationId); }
                    catch(Exception rollbackUnavailable) {
                        failure.addSuppressed(rollbackUnavailable);
                        ServerRoleGuard.update("PRIMARY");
                    }
                    if(failure instanceof RuleViolation violation)throw violation;
                    throw rule(409,"HANDOFF_PREPARATION_FAILED",
                            "Handoff preparation failed. The old server rollback is active and will retry automatically.");
                }
            }
            case "RENAME" -> {
                String name=required(body,"displayName");
                if(name.length()>200)throw rule(400,"VALIDATION_ERROR","Server name is too long.");
                CloudServerRegistryService.rename(locationId,id,name,userId,actorName);
                return map("updated",true);
            }
            case "RETIRE" -> {
                CloudServerRegistryService.retire(locationId,id,userId,actorName);
                return map("retired",true);
            }
            case "EMERGENCY_TAKEOVER" -> {
                require(connection,userId,"SERVER_RECOVERY");
                if(!bool(body,"warningAcknowledged"))throw rule(400,"RECOVERY_ACK_REQUIRED","Acknowledge the possible loss of unsynced data.");
                var servers=CloudServerRegistryService.list(locationId);
                var standby=servers.stream().filter(s->id.equals(s.serverInstanceId())).findFirst().orElseThrow(
                        ()->rule(409,"STANDBY_NOT_FOUND","The recovery standby was not found."));
                var primary=CloudServerRegistryService.primary(servers);
                if(primary==null)throw rule(409,"PRIMARY_SERVER_MISSING","No primary recovery source is registered.");
                if(!"OFFLINE".equals(primary.health()))throw rule(409,"OLD_SERVER_STILL_ONLINE",
                        "The current primary is not offline. Use verified handoff instead.");
                java.util.List<LanApiClient.DiscoveredServer> discovered;
                try { discovered=LanApiClient.discoverServers(); }
                catch(Exception ex){throw rule(503,"LAN_DISCOVERY_FAILED",
                        "SmartStock could not complete the required LAN discovery check.");}
                boolean foundOnLan=discovered.stream().anyMatch(server->primary.certificateFingerprint()!=null
                        && primary.certificateFingerprint().equalsIgnoreCase(server.certificateFingerprint()));
                boolean directlyReachable=LanApiClient.isServerReachable(primary.endpointHost(),primary.endpointPort(),
                        primary.certificateFingerprint());
                if(foundOnLan||directlyReachable)throw rule(409,"OLD_SERVER_STILL_ONLINE",
                        "The current primary is reachable on the store network. Use verified handoff instead.");
                if(CloudServerRegistryService.currentInstanceId()==null
                        ||!id.equals(CloudServerRegistryService.currentInstanceId()))throw rule(409,
                        "RECOVERY_MUST_RUN_ON_STANDBY","Emergency takeover must be approved on the physical recovery standby.");
                if(!"STANDBY".equals(standby.role()))throw rule(409,"SERVER_NOT_STANDBY",
                        "The selected recovery server is not a standby.");
                if(standby.recoveryValidatedAt()==null||standby.recoveryValidatedAt().isBlank()
                        ||standby.recoveryMaterializationAt()==null
                        ||!standby.recoveryMaterializationAt().equals(primary.lastMaterializationAt()))throw rule(409,
                        "RECOVERY_NOT_VALIDATED","Restore and validate the latest cloud recovery point before takeover.");
                CloudServerRegistryService.markRecoveryReady(locationId,id);
                return map("handoff",CloudServerRegistryService.emergencyTakeover(locationId,id,
                        required(body,"idempotencyKey"),userId,actorName));
            }
            default -> throw rule(400,"VALIDATION_ERROR","Server action is invalid.");
        }
    }

    static Map<String,Object> handoffStatus(Connection connection,JsonObject body,int userId,int locationId)throws Exception{
        require(connection,userId,"DEVICE_MANAGEMENT");
        String instance=body!=null&&body.has("serverInstanceId")&&!body.get("serverInstanceId").isJsonNull()
                ?body.get("serverInstanceId").getAsString():null;
        String handoff=body!=null&&body.has("handoffId")&&!body.get("handoffId").isJsonNull()
                ?body.get("handoffId").getAsString():null;
        return map("handoff",CloudServerRegistryService.handoffStatus(locationId,instance,handoff));
    }

    private static void validateStandbyReadiness(CloudServerRegistryService.ServerRecord primary,
                                                  CloudServerRegistryService.ServerRecord standby)throws RuleViolation{
        if(!"STANDBY".equals(standby.role()))throw rule(409,"SERVER_NOT_STANDBY","The selected server is not a standby.");
        if(primary==null)throw rule(409,"PRIMARY_SERVER_MISSING","This store does not have an active primary.");
        if(primary.appVersion()==null||primary.appVersion().isBlank()
                ||!primary.appVersion().equals(standby.appVersion()))throw rule(409,"UNSUPPORTED_SERVER_VERSION",
                "Primary and standby servers must run the same SmartStock version.");
        if(!"ONLINE".equals(standby.health()))throw rule(409,"STANDBY_NOT_READY",
                "The standby coordination service must be online before handoff.");
        if(primary.lastMaterializationAt()==null||primary.lastMaterializationAt().isBlank())throw rule(409,
                "RECOVERY_POINT_MISSING","The primary does not have a verified cloud recovery point.");
        try {
            if(java.time.Instant.parse(primary.lastMaterializationAt()).isBefore(java.time.Instant.now().minus(java.time.Duration.ofHours(24))))
                throw rule(409,"STALE_BACKUP","The latest verified cloud materialization is more than 24 hours old.");
        } catch(java.time.format.DateTimeParseException ex) {
            throw rule(409,"RECOVERY_POINT_INVALID","The primary recovery checkpoint is invalid.");
        }
    }

    private static void require(Connection c,int userId,String permission)throws Exception{
        try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions p ON p.permission_id=rp.permission_id WHERE u.user_id=? AND UPPER(p.permission_key)=? LIMIT 1")){
            ps.setInt(1,userId);ps.setString(2,permission);
            try(ResultSet rs=ps.executeQuery()){if(rs.next())return;}
        }
        throw rule(403,"PERMISSION_DENIED","You do not have permission for this server operation.");
    }
    private static String required(JsonObject b,String key)throws RuleViolation{
        if(b==null||!b.has(key)||b.get(key).isJsonNull()||b.get(key).getAsString().isBlank())throw rule(400,"VALIDATION_ERROR",key+" is required.");
        return b.get(key).getAsString().trim();
    }
    private static boolean bool(JsonObject b,String key){return b!=null&&b.has(key)&&!b.get(key).isJsonNull()&&b.get(key).getAsBoolean();}
    private static Map<String,Object> map(Object...values){Map<String,Object>m=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)m.put((String)values[i],values[i+1]);return m;}
    private static RuleViolation rule(int status,String code,String message){return new RuleViolation(status,code,message);}
    static final class RuleViolation extends Exception{
        final int status;final String code;RuleViolation(int status,String code,String message){super(message);this.status=status;this.code=code;}
    }
}
