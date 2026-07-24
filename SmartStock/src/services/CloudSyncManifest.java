package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Read-only schema/count evidence returned by the server-only Supabase RPC. */
public record CloudSyncManifest(Map<String, TableInfo> tables) {
    public static CloudSyncManifest fetch() throws IOException {
        return fetchRpc("smartstock_sync_manifest", new JsonObject());
    }

    public static CloudSyncManifest fetchStoreSnapshot(int locationId) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("p_location_id", locationId);
        return fetchRpc("smartstock_store_snapshot_manifest", body);
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
            return new CloudSyncManifest(Map.copyOf(tables));
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

    public record TableInfo(long rowCount, Set<String> columns) {
    }
}
