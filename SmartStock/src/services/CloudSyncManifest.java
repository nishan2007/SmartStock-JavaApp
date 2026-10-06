package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Read-only schema/count evidence returned by the server-only Supabase RPC. */
public record CloudSyncManifest(Map<String, TableInfo> tables,
                                String snapshotGenerationId,
                                Instant completedAt,
                                Integer schemaVersion,
                                boolean schemaReady,
                                int crossStoreProtocol) {
    private static volatile SchemaReadiness latestSchemaReadiness =
            new SchemaReadiness(false, null, "Cloud schema has not been checked.");

    public CloudSyncManifest(Map<String, TableInfo> tables, String snapshotGenerationId,
                             Instant completedAt) {
        this(tables, snapshotGenerationId, completedAt, null, false, 0);
    }

    public CloudSyncManifest(Map<String, TableInfo> tables, String generationId, Instant completedAt,
                             Integer schemaVersion, boolean schemaReady) {
        this(tables, generationId, completedAt, schemaVersion, schemaReady, 0);
    }

    public static CloudSyncManifest fetch() throws IOException {
        CloudSyncManifest manifest;
        try {
            manifest = fetchRpc("smartstock_sync_manifest", new JsonObject());
        } catch (IOException ex) {
            latestSchemaReadiness = new SchemaReadiness(false, null,
                    "Cloud schema could not be verified; sync and recovery are disabled.");
            throw ex;
        }
        latestSchemaReadiness = new SchemaReadiness(manifest.schemaReady(),
                manifest.schemaVersion(), manifest.schemaReady()
                ? "Cloud schema v1 is ready."
                : "Cloud schema is unavailable or does not match v1; sync and recovery are disabled.");
        if (!manifest.schemaReady() || manifest.schemaVersion() == null
                || manifest.schemaVersion() != SchemaContractService.BASELINE_VERSION) {
            throw new IOException(latestSchemaReadiness.message());
        }
        return manifest;
    }

    /** Lightweight preflight for the frequent sync loop; recovery still uses fetch(). */
    public static void verifySchemaReady() throws IOException {
        SupabaseServerApi.Response response;
        try {
            response = SupabaseServerApi.postRpc("smartstock_sync_schema_status", new JsonObject());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Supabase schema check was interrupted.", ex);
        }
        if (!response.successful()) {
            throw new IOException(SupabaseServerApi.failureMessage(
                    "Supabase schema check", response));
        }
        CloudSyncManifest status = parse(response.body());
        latestSchemaReadiness = new SchemaReadiness(status.schemaReady(),
                status.schemaVersion(), status.schemaReady()
                ? "Cloud schema v1 is ready."
                : "Cloud schema is unavailable or does not match v1; sync and recovery are disabled.");
        if (!status.schemaReady() || status.schemaVersion() == null
                || status.schemaVersion() != SchemaContractService.BASELINE_VERSION) {
            throw new IOException("Cloud schema v1 is unavailable; synchronization is disabled.");
        }
    }

    public static CloudSyncManifest fetchStoreSnapshot(int locationId) throws IOException {
        return fetchStoreSnapshot(locationId,null);
    }

    public static CloudSyncManifest fetchStoreSnapshot(int locationId,java.sql.Connection local) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("p_location_id", locationId);
        CloudSyncManifest manifest = fetchRpc("smartstock_store_snapshot_manifest", body,local,locationId);
        if (!manifest.schemaReady() || manifest.schemaVersion() == null
                || manifest.schemaVersion() != SchemaContractService.BASELINE_VERSION) {
            throw new IOException("Cloud schema v1 is not ready; recovery is disabled.");
        }
        if (!manifest.tables().isEmpty() && !manifest.hasVerifiedSnapshot()) {
            throw new IOException("Supabase did not return a completed recovery generation.");
        }
        return manifest;
    }

    private static CloudSyncManifest fetchRpc(String function, JsonObject body) throws IOException {
        return fetchRpc(function,body,null,null);
    }

    private static CloudSyncManifest fetchRpc(String function,JsonObject body,java.sql.Connection local,Integer locationId)throws IOException {
        SupabaseServerApi.Response response;
        try {
            response = SupabaseServerApi.postRpc(function, body);
            if(local!=null)CloudTransferMetrics.record(local,function,locationId,body.toString(),response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Supabase manifest request was interrupted.", ex);
        } catch(java.sql.SQLException ex) {
            throw new IOException("Cloud manifest transfer measurement failed.",ex);
        }
        if (!response.successful()) {
            throw new IOException(SupabaseServerApi.failureMessage(
                    "Supabase manifest request", response));
        }
        return parse(response.body());
    }

    static CloudSyncManifest parse(String body) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject()) {
                throw new IOException("Supabase did not return a recovery manifest. A completed store snapshot may not exist yet.");
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonArray tableArray = root.has("tables") && root.get("tables").isJsonArray()
                    ? root.getAsJsonArray("tables") : new JsonArray();
            Map<String, TableInfo> tables = new LinkedHashMap<>();
            for (JsonElement element : tableArray) {
                JsonObject table = element.getAsJsonObject();
                String name = table.get("name").getAsString();
                long rowCount = table.get("row_count").getAsLong();
                Set<String> columns = new LinkedHashSet<>();
                if (table.has("columns") && table.get("columns").isJsonArray()) {
                    for (JsonElement column : table.getAsJsonArray("columns")) {
                        columns.add(column.getAsString());
                    }
                }
                if (!name.matches("[a-z][a-z0-9_]{0,100}") || rowCount < 0) {
                    throw new IllegalArgumentException("Invalid manifest table.");
                }
                String fingerprint = rootString(table, "fingerprint");
                tables.put(name, new TableInfo(rowCount, Set.copyOf(columns), fingerprint));
            }
            String generationId = root.has("generation_id")
                    && !root.get("generation_id").isJsonNull()
                    ? root.get("generation_id").getAsString() : null;
            if (generationId != null) java.util.UUID.fromString(generationId);
            Instant completedAt = root.has("completed_at")
                    && !root.get("completed_at").isJsonNull()
                    ? Instant.parse(root.get("completed_at").getAsString()) : null;
            Integer schemaVersion = root.has("schema_version")
                    && !root.get("schema_version").isJsonNull()
                    ? root.get("schema_version").getAsInt() : null;
            boolean schemaReady = root.has("schema_ready")
                    && root.get("schema_ready").getAsBoolean();
            return new CloudSyncManifest(Map.copyOf(tables), generationId, completedAt,
                    schemaVersion, schemaReady, root.has("cross_store_protocol")
                            ? root.get("cross_store_protocol").getAsInt() : 0);
        } catch (RuntimeException ex) {
            throw new IOException("Supabase returned an invalid schema manifest.", ex);
        }
    }

    public boolean hasTable(String table) {
        return tables.containsKey(table);
    }

    public long rowCount(String table) {
        TableInfo info = tables.get(table);
        return info == null ? -1 : info.rowCount();
    }

    public long totalRowCount() {
        return tables.values().stream().mapToLong(TableInfo::rowCount).sum();
    }

    public boolean hasVerifiedSnapshot() {
        return snapshotGenerationId != null && completedAt != null;
    }

    public static SchemaReadiness latestSchemaReadiness() {
        return latestSchemaReadiness;
    }

    private static String rootString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }

    public record TableInfo(long rowCount, Set<String> columns, String fingerprint) {
        public TableInfo(long rowCount, Set<String> columns) { this(rowCount, columns, null); }
    }

    public record SchemaReadiness(boolean ready, Integer version, String message) {
    }
}
