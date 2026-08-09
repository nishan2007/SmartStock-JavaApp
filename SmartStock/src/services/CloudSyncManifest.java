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
                                Instant completedAt) {
    public static CloudSyncManifest fetch() throws IOException {
        return fetchRpc("smartstock_sync_manifest", new JsonObject());
    }

    public static CloudSyncManifest fetchStoreSnapshot(int locationId) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("p_location_id", locationId);
        CloudSyncManifest manifest = fetchRpc("smartstock_store_snapshot_manifest", body);
        if (!manifest.tables().isEmpty() && !manifest.hasVerifiedSnapshot()) {
            throw new IOException("Supabase did not return a completed recovery generation.");
        }
        return manifest;
    }

    private static CloudSyncManifest fetchRpc(String function, JsonObject body) throws IOException {
        SupabaseServerApi.Response response;
        try {
            response = SupabaseServerApi.postRpc(function, body);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Supabase manifest request was interrupted.", ex);
        }
        if (!response.successful()) {
            throw new IOException(SupabaseServerApi.failureMessage(
                    "Supabase manifest request", response));
        }
        return parse(response.body());
    }

    static CloudSyncManifest parse(String body) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
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
                tables.put(name, new TableInfo(rowCount, Set.copyOf(columns)));
            }
            String generationId = root.has("generation_id")
                    && !root.get("generation_id").isJsonNull()
                    ? root.get("generation_id").getAsString() : null;
            if (generationId != null) java.util.UUID.fromString(generationId);
            Instant completedAt = root.has("completed_at")
                    && !root.get("completed_at").isJsonNull()
                    ? Instant.parse(root.get("completed_at").getAsString()) : null;
            return new CloudSyncManifest(Map.copyOf(tables), generationId, completedAt);
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

    public record TableInfo(long rowCount, Set<String> columns) {
    }
}
