package services;

import com.google.gson.*;
import java.sql.SQLException;
import java.util.*;

/** Downloads changed bodies only. Staging is independent of the live display cache. */
final class CrossStoreDelta {
    static final int PAGE_SIZE = 1_000;
    static final List<String> TABLES = List.of("products", "product_barcodes", "inventory", "sales",
            "sale_items", "sale_returns", "sale_return_items", "customer_account_transactions",
            "custom_orders", "quotations", "invoices");
    @FunctionalInterface interface Rpc { JsonObject call(String operation, JsonObject body) throws SQLException; }
    record Row(JsonElement key, String hash, JsonObject data) { }
    record Baseline(String fingerprint, Map<JsonElement,Row> rows) { }
    record Download(Map<String,Map<JsonElement,Row>> tables, Set<String> changed) {
        Map<String,List<JsonObject>> bodies() {
            Map<String,List<JsonObject>> result = new LinkedHashMap<>();
            tables.forEach((name,rows) -> result.put(name,rows.values().stream().map(Row::data).toList()));
            return result;
        }
    }

    static Download download(int location, CloudSyncManifest manifest, Map<String,Baseline> baseline, Rpc rpc)
            throws SQLException {
        if (!manifest.hasVerifiedSnapshot() || !manifest.schemaReady() || manifest.crossStoreProtocol()!=1) {
            throw new SQLException("Cloud changed-row sync is unavailable. Apply the additive egress cloud migration first.");
        }
        Map<String,Map<JsonElement,Row>> staged = new LinkedHashMap<>();
        Set<String> changed = new LinkedHashSet<>();
        for (String table : TABLES) {
            CloudSyncManifest.TableInfo info = manifest.tables().get(table);
            if (info==null || info.rowCount()<0 || info.fingerprint()==null
                    || !info.fingerprint().matches("[0-9a-f]{64}")) {
                throw new SQLException("Cloud snapshot lacks changed-row metadata for " + table + ".");
            }
            Baseline old = baseline.get(table);
            Map<JsonElement,Row> existing = old==null ? Map.of() : old.rows();
            if (old!=null && info.fingerprint().equals(old.fingerprint()) && existing.size()==info.rowCount()) {
                staged.put(table,existing);
                continue;
            }
            changed.add(table);
            Map<JsonElement,String> hashes = new LinkedHashMap<>();
            long cursor = 0;
            while (true) {
                JsonObject request = request(location,manifest.snapshotGenerationId(),table);
                request.addProperty("p_after_sequence",cursor);
                request.addProperty("p_limit",PAGE_SIZE);
                JsonObject page = rpc.call("smartstock_cross_store_row_hashes",request);
                JsonArray rows = rows(page,manifest.snapshotGenerationId());
                long next = cursor;
                for (JsonElement element : rows) {
                    JsonObject row = element.getAsJsonObject();
                    JsonElement key = key(row);
                    String hash = hash(row);
                    long sequence = row.get("sequence").getAsLong();
                    if (sequence<=next || hashes.putIfAbsent(key,hash)!=null)
                        throw new SQLException("Cloud row-hash page is duplicated or unordered.");
                    next = sequence;
                }
                if (page.get("next_cursor").getAsLong()!=next || hashes.size()>info.rowCount())
                    throw new SQLException("Cloud row-hash cursor or count is invalid.");
                cursor=next;
                if (rows.size()<PAGE_SIZE) break;
            }
            if (hashes.size()!=info.rowCount()) throw new SQLException("Cloud row-hash listing is incomplete.");
            Map<JsonElement,Row> nextRows = new LinkedHashMap<>();
            List<JsonElement> needed = new ArrayList<>();
            hashes.forEach((key,hash) -> {
                Row oldRow = existing.get(key);
                if (oldRow!=null && hash.equals(oldRow.hash())) nextRows.put(key,oldRow);
                else needed.add(key);
            });
            for (int start=0;start<needed.size();start+=PAGE_SIZE) {
                List<JsonElement> batch = needed.subList(start,Math.min(start+PAGE_SIZE,needed.size()));
                JsonArray keys = new JsonArray(); batch.forEach(keys::add);
                JsonObject request = request(location,manifest.snapshotGenerationId(),table);
                request.add("p_row_keys",keys);
                Set<JsonElement> pending = new HashSet<>(batch);
                for (JsonElement element : rows(rpc.call("smartstock_cross_store_rows",request),manifest.snapshotGenerationId())) {
                    JsonObject row = element.getAsJsonObject();
                    JsonElement key = key(row);
                    String hash = hash(row);
                    if (!pending.remove(key) || !hash.equals(hashes.get(key)) || !row.has("row_data")
                            || !row.get("row_data").isJsonObject())
                        throw new SQLException("Cloud changed-row response does not match the hash listing.");
                    nextRows.put(key,new Row(key,hash,row.getAsJsonObject("row_data")));
                }
                if (!pending.isEmpty()) throw new SQLException("Cloud changed-row response is incomplete.");
            }
            // Keys absent from the complete listing are deletions, including an empty table.
            staged.put(table,Map.copyOf(nextRows));
        }
        return new Download(Map.copyOf(staged),Set.copyOf(changed));
    }

    private static JsonObject request(int location,String generation,String table) {
        JsonObject result=new JsonObject(); result.addProperty("p_location_id",location);
        result.addProperty("p_generation_id",generation); result.addProperty("p_table_name",table); return result;
    }
    private static JsonArray rows(JsonObject page,String generation) throws SQLException {
        if (!page.has("generation_id") || !generation.equals(page.get("generation_id").getAsString())
                || !page.has("rows") || !page.get("rows").isJsonArray() || page.getAsJsonArray("rows").size()>PAGE_SIZE)
            throw new SQLException("Cloud returned an invalid snapshot page.");
        return page.getAsJsonArray("rows");
    }
    private static JsonElement key(JsonObject row) throws SQLException {
        if (!row.has("row_key") || !row.get("row_key").isJsonObject()) throw new SQLException("Cloud row key is invalid.");
        return row.get("row_key");
    }
    private static String hash(JsonObject row) throws SQLException {
        if (!row.has("row_hash") || !row.get("row_hash").isJsonPrimitive()) throw new SQLException("Cloud row hash is missing.");
        String value=row.get("row_hash").getAsString();
        if (!value.matches("[0-9a-f]{64}")) throw new SQLException("Cloud row hash is invalid.");
        return value;
    }
}
