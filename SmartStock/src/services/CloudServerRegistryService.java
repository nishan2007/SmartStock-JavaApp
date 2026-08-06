package services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import data.DatabaseConfig;
import data.DatabaseMode;
import data.EnvironmentProfile;
import managers.SessionManager;
import models.DeviceInfo;
import utils.DeviceUtils;
import utils.SecureCredentialStore;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-only client for the Supabase store-server coordination registry. */
public final class CloudServerRegistryService {
    private static final Gson GSON = new Gson();
    private static final String INSTANCE_SECRET = EnvironmentProfile.active().secretKey("store-server-instance-id");
    private static final String ROLLBACK_SECRET = EnvironmentProfile.active().secretKey("store-server-handoff-rollback");

    private CloudServerRegistryService() { }

    public static List<ServerRecord> list(int locationId) throws IOException {
        JsonObject payload = location(locationId);
        JsonObject result = call("LIST", payload);
        JsonArray array = result.has("servers") ? result.getAsJsonArray("servers") : new JsonArray();
        List<ServerRecord> records = new ArrayList<>();
        array.forEach(element -> records.add(GSON.fromJson(element, ServerRecord.class)));
        return List.copyOf(records);
    }

    public static void ensureStoreLocation(int locationId) throws Exception {
        JsonObject payload=location(locationId);
        try (Connection local=data.DB.getConnection(); PreparedStatement ps=local.prepareStatement(
                "SELECT name,receipt_store_code,timezone,address FROM locations WHERE location_id=?")) {
            ps.setInt(1,locationId);
            try(ResultSet rs=ps.executeQuery()) {
                if(!rs.next())throw new IllegalStateException("The selected local store was not found.");
                payload.addProperty("store_name",rs.getString(1));
                payload.addProperty("store_code",rs.getString(2));
                payload.addProperty("timezone",rs.getString(3));
                if(rs.getString(4)!=null)payload.addProperty("address",rs.getString(4));
            }
        }
        call("ENSURE_LOCATION",payload);
    }

    public static List<ServerEvent> events(int locationId)throws IOException{
        JsonObject result=call("LIST_EVENTS",location(locationId));
        JsonArray array=result.has("events")?result.getAsJsonArray("events"):new JsonArray();
        List<ServerEvent> events=new ArrayList<>();array.forEach(e->events.add(GSON.fromJson(e,ServerEvent.class)));
        return List.copyOf(events);
    }

    public static Registration registerCurrent(ServerRole role) throws Exception {
        DatabaseConfig config = requireServerConfig();
        DeviceInfo device = DeviceUtils.collectDeviceInfo();
        JsonObject payload = location(config.locationId());
        payload.addProperty("installation_id", device.getInstallationId());
        payload.addProperty("display_name", device.getDeviceName());
        payload.addProperty("hostname", device.getHostname());
        payload.addProperty("app_version", device.getAppVersion());
        payload.addProperty("certificate_fingerprint", LanTlsIdentity.loadOrCreate().fingerprint());
        payload.addProperty("endpoint_host", LanTlsIdentity.tlsHostName());
        payload.addProperty("endpoint_port", LanApiServer.DEFAULT_PORT);
        JsonObject result = call(role == ServerRole.PRIMARY ? "REGISTER_PRIMARY" : "REGISTER_STANDBY", payload);
        String id = result.get("serverInstanceId").getAsString();
        SecureCredentialStore.write(INSTANCE_SECRET, id);
        ServerRoleGuard.update(role.name());
        return new Registration(id, true);
    }

    public static HeartbeatResult heartbeatCurrent(Connection local, String statusMessage) throws Exception {
        DatabaseConfig config = requireServerConfig();
        String instanceId = currentInstanceId();
        if (instanceId == null) return new HeartbeatResult(false, "UNKNOWN", 0, false);
        DeviceInfo device = DeviceUtils.collectDeviceInfo();
        JsonObject payload = location(config.locationId());
        payload.addProperty("server_instance_id", instanceId);
        payload.addProperty("hostname", device.getHostname());
        payload.addProperty("app_version", device.getAppVersion());
        payload.addProperty("endpoint_host", LanTlsIdentity.tlsHostName());
        payload.addProperty("endpoint_port", LanApiServer.DEFAULT_PORT);
        if (statusMessage != null && !statusMessage.isBlank()) payload.addProperty("status_message", statusMessage);
        try (PreparedStatement ps = local.prepareStatement("""
                SELECT completed_at, active_row_count
                FROM sync_row_mirror_completion WHERE location_id=?
                """)) {
            ps.setInt(1, config.locationId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    payload.addProperty("last_materialization_at", rs.getTimestamp(1).toInstant().toString());
                    payload.addProperty("materialized_row_count", rs.getLong(2));
                }
            }
        }
        SyncWorker.SyncStatus sync = SyncWorker.latestStatus(local);
        if (sync.lastSuccess() != null) payload.addProperty("last_sync_at", sync.lastSuccess().toString());
        JsonObject result = call("HEARTBEAT", payload);
        HeartbeatResult heartbeat = GSON.fromJson(result, HeartbeatResult.class);
        ServerRoleGuard.update(heartbeat.role());
        return heartbeat;
    }

    public static HandoffResult beginHandoff(int locationId, String sourceId, String targetId,
                                             String idempotencyKey, Integer actorId, String actorName) throws IOException {
        JsonObject payload = actor(locationId, actorId, actorName);
        payload.addProperty("server_instance_id", sourceId);
        payload.addProperty("target_server_instance_id", targetId);
        payload.addProperty("idempotency_key", idempotencyKey);
        HandoffResult result = GSON.fromJson(call("BEGIN_HANDOFF", payload), HandoffResult.class);
        if (sourceId.equals(currentInstanceId())) ServerRoleGuard.update("DRAINING");
        return result;
    }

    public static void recordStandbyPrepared(int locationId,String standbyId,Integer actorId,String actorName)throws IOException{
        JsonObject payload=actor(locationId,actorId,actorName);
        payload.addProperty("server_instance_id",standbyId);
        call("PREPARE_STANDBY",payload);
    }

    public static HandoffResult markReady(int locationId, String sourceId, String handoffId) throws IOException {
        JsonObject payload = location(locationId);
        payload.addProperty("server_instance_id", sourceId);
        payload.addProperty("handoff_id", handoffId);
        return GSON.fromJson(call("MARK_HANDOFF_READY", payload), HandoffResult.class);
    }

    public static HandoffStatus handoffStatus(int locationId, String instanceId, String handoffId) throws IOException {
        JsonObject payload = location(locationId);
        if (instanceId != null) payload.addProperty("server_instance_id", instanceId);
        if (handoffId != null) payload.addProperty("handoff_id", handoffId);
        return GSON.fromJson(call("HANDOFF_STATUS", payload), HandoffStatus.class);
    }

    public static HandoffStatus handoffStatusByIdempotency(int locationId,String key)throws IOException{
        JsonObject payload=location(locationId);payload.addProperty("idempotency_key",key);
        return GSON.fromJson(call("HANDOFF_STATUS",payload),HandoffStatus.class);
    }

    public static void prepareHandoffRollback(String sourceId,String idempotencyKey)throws IOException{
        JsonObject pending=new JsonObject();pending.addProperty("sourceId",sourceId);
        pending.addProperty("idempotencyKey",idempotencyKey);
        SecureCredentialStore.write(ROLLBACK_SECRET,pending.toString());
    }

    public static void clearHandoffRollback()throws IOException{
        SecureCredentialStore.delete(ROLLBACK_SECRET);
    }

    public static boolean hasPendingHandoffRollback(){return pendingRollback()!=null;}

    public static void reconcilePendingHandoffRollback(int locationId)throws IOException{
        JsonObject pending=pendingRollback();if(pending==null)return;
        String source=pending.get("sourceId").getAsString(),key=pending.get("idempotencyKey").getAsString();
        HandoffStatus status=handoffStatusByIdempotency(locationId,key);
        if("NONE".equals(status.status())||"FAILED".equals(status.status())||"COMPLETED".equals(status.status())){
            clearHandoffRollback();return;
        }
        failHandoff(locationId,source,status.handoffId(),"Automatic rollback after handoff preparation failure.");
        clearHandoffRollback();
    }

    private static JsonObject pendingRollback(){
        try{
            String value=SecureCredentialStore.read(ROLLBACK_SECRET);
            return value==null||value.isBlank()?null:JsonParser.parseString(value).getAsJsonObject();
        }catch(Exception ex){return null;}
    }

    public static String markRecoveryReady(int locationId,String instanceId)throws IOException{
        JsonObject payload=location(locationId);payload.addProperty("server_instance_id",instanceId);
        JsonObject result=call("MARK_RECOVERY_READY",payload);
        return result.has("recoveryMaterializedAt")?result.get("recoveryMaterializedAt").getAsString():null;
    }

    public static HandoffResult completeHandoff(int locationId, String targetId, String handoffId) throws IOException {
        JsonObject payload = location(locationId);
        payload.addProperty("server_instance_id", targetId);
        payload.addProperty("handoff_id", handoffId);
        HandoffResult result = GSON.fromJson(call("COMPLETE_HANDOFF", payload), HandoffResult.class);
        if (targetId.equals(currentInstanceId())) ServerRoleGuard.update("PRIMARY");
        return result;
    }

    public static HandoffResult failHandoff(int locationId, String sourceId, String handoffId, String message) throws IOException {
        JsonObject payload = location(locationId);
        payload.addProperty("server_instance_id", sourceId);
        payload.addProperty("handoff_id", handoffId);
        payload.addProperty("failure_message", message);
        HandoffResult result = GSON.fromJson(call("FAIL_HANDOFF", payload), HandoffResult.class);
        if (sourceId.equals(currentInstanceId())) ServerRoleGuard.update("PRIMARY");
        return result;
    }

    public static HandoffResult emergencyTakeover(int locationId, String standbyId, String idempotencyKey,
                                                   Integer actorId, String actorName) throws IOException {
        JsonObject payload = actor(locationId, actorId, actorName);
        payload.addProperty("server_instance_id", standbyId);
        payload.addProperty("idempotency_key", idempotencyKey);
        payload.addProperty("warning_acknowledged", true);
        HandoffResult result = GSON.fromJson(call("EMERGENCY_TAKEOVER", payload), HandoffResult.class);
        if (standbyId.equals(currentInstanceId())) ServerRoleGuard.update("PRIMARY");
        return result;
    }

    public static void retire(int locationId, String instanceId, Integer actorId, String actorName) throws IOException {
        JsonObject payload = actor(locationId, actorId, actorName);
        payload.addProperty("server_instance_id", instanceId);
        call("RETIRE", payload);
        if(instanceId.equals(currentInstanceId()))ServerRoleGuard.update("RETIRED");
    }

    public static void rename(int locationId, String instanceId, String displayName,
                              Integer actorId,String actorName) throws IOException {
        JsonObject payload = actor(locationId,actorId,actorName);
        payload.addProperty("server_instance_id", instanceId);
        payload.addProperty("display_name", displayName);
        call("RENAME", payload);
    }

    public static String currentInstanceId() {
        String value = SecureCredentialStore.read(INSTANCE_SECRET);
        try { return value == null ? null : UUID.fromString(value).toString(); }
        catch (IllegalArgumentException ex) { return null; }
    }

    /** Re-links this installation to its existing registry row without changing role or generation. */
    static void adoptCurrent(ServerRecord record) throws IOException {
        if (record == null || record.serverInstanceId() == null || record.serverInstanceId().isBlank()) {
            throw new IOException("The existing server registry identity is incomplete.");
        }
        String installationId = DeviceUtils.collectDeviceInfo().getInstallationId();
        if (!installationId.equals(record.installationId())) {
            throw new IOException("The server registry identity belongs to another installation.");
        }
        SecureCredentialStore.write(INSTANCE_SECRET, UUID.fromString(record.serverInstanceId()).toString());
        ServerRoleGuard.update(record.role());
    }

    public static ServerRecord primary(List<ServerRecord> servers) {
        return servers == null ? null : servers.stream().filter(s -> "PRIMARY".equals(s.role()) || "DRAINING".equals(s.role())).findFirst().orElse(null);
    }

    private static JsonObject call(String action, JsonObject payload) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("p_action", action);
        body.add("p_payload", payload);
        SupabaseServerApi.Response response;
        try { response = SupabaseServerApi.postRpc("smartstock_server_registry", body); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IOException("Server registry request was interrupted.", ex); }
        if (!response.successful()) throw new RegistryException(code(response.body()), safeMessage(response.body(), response.statusCode()));
        try { return JsonParser.parseString(response.body()).getAsJsonObject(); }
        catch (RuntimeException ex) { throw new IOException("Server registry returned an invalid response.", ex); }
    }

    private static String code(String body) {
        try { return JsonParser.parseString(body).getAsJsonObject().get("code").getAsString(); }
        catch (Exception ex) { return "SERVER_REGISTRY_ERROR"; }
    }

    private static String safeMessage(String body, int status) {
        try {
            String message = JsonParser.parseString(body).getAsJsonObject().get("message").getAsString();
            return message == null || message.isBlank() ? "Server registry returned HTTP " + status + "." : message;
        } catch (Exception ex) { return "Server registry returned HTTP " + status + "."; }
    }

    private static DatabaseConfig requireServerConfig() {
        DatabaseConfig config = DatabaseConfig.load();
        if (config.mode() != DatabaseMode.SERVER || config.locationId() == null)
            throw new IllegalStateException("Server mode and a store assignment are required.");
        if (!ServerSupabaseCredentials.isConfigured())
            throw new IllegalStateException("Supabase Server Key is required for server coordination.");
        return config;
    }

    private static JsonObject location(int locationId) {
        JsonObject payload = new JsonObject(); payload.addProperty("location_id", locationId); return payload;
    }

    private static JsonObject actor(int locationId, Integer actorId, String actorName) {
        JsonObject payload = location(locationId);
        if (actorId != null) payload.addProperty("actor_user_id", actorId);
        if (actorName != null && !actorName.isBlank()) payload.addProperty("actor_name", actorName);
        return payload;
    }

    public enum ServerRole { PRIMARY, STANDBY }
    public record Registration(String serverInstanceId, boolean registered) { }
    public record HeartbeatResult(boolean accepted, String role, long generation, boolean fenced) { }
    public record HandoffResult(String handoffId, String status, long generation,
                                String recoveryMaterializedAt, Long recoveryRowCount) { }
    public record HandoffStatus(String handoffId, String status, String sourceServerInstanceId,
                                String targetServerInstanceId, boolean emergency,
                                String recoveryMaterializedAt, Long recoveryRowCount,
                                String failureMessage) { }
    public record ServerRecord(String serverInstanceId, int locationId, String installationId,
                               String displayName, String hostname, String appVersion,
                               String certificateFingerprint, String endpointHost, int endpointPort,
                               String role, long generation, String health, String lastHeartbeatAt,
                               String lastSyncAt, String lastMaterializationAt, Long materializedRowCount,
                               String recoveryValidatedAt,String recoveryMaterializationAt,
                               String recoveryNetworkCheckedAt,
                               String statusMessage, String replacedByServerInstanceId,
                               String createdAt, String retiredAt) { }
    public record ServerEvent(String eventType,String serverInstanceId,String handoffId,
                              String actorName,String details,String createdAt) { }

    public static final class RegistryException extends IOException {
        private final String code;
        RegistryException(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }
}
