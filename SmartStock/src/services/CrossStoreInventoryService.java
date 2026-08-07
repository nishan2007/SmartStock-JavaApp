package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maintains and queries a local, read-only cache of other stores' cloud inventory.
 * Remote rows are refreshed during normal server sync; UI searches never call Supabase.
 */
final class CrossStoreInventoryService {
    private static final int PAGE_SIZE = 1_000;

    private CrossStoreInventoryService() { }

    static RefreshResult refreshAll(Connection local, int currentLocationId) throws SQLException {
        SyncSchemaInstaller.ensureSchema(local);
        int stores = 0;
        int rows = 0;
        int failed = 0;
        List<Store> locations = stores(local, currentLocationId);
        for (Store store : locations) {
            try {
                int refreshed = refreshStore(local, store);
                markStatus(local, store, refreshed, "CURRENT", null);
                stores++;
                rows += refreshed;
            } catch (SQLException ex) {
                failed++;
                markStatus(local, store, cachedCount(local, store.locationId()),
                        "STALE", safeError(ex));
            }
        }
        return new RefreshResult(stores, rows, failed);
    }

    static SearchResult search(Connection local, int userId, int currentLocationId,
                               String query, Integer storeId) throws Exception {
        requirePermission(local, userId);
        SyncSchemaInstaller.ensureSchema(local);
        String clean = query == null ? "" : query.trim();
        if (clean.length() > 300) throw new RuleViolation(400, "VALIDATION_ERROR",
                "Inventory search text is too long.");
        if (storeId != null && storeId == currentLocationId) {
            throw new RuleViolation(400, "LOCAL_STORE_EXCLUDED",
                    "Use the normal inventory screen for this store's live inventory.");
        }
        StringBuilder sql = new StringBuilder("""
                SELECT c.source_location_id,c.store_name,c.product_id,c.sku,c.barcode,
                       c.product_name,c.size,c.description,c.quantity_on_hand,c.reorder_level,
                       c.source_updated_at,c.cache_refreshed_at,
                       COALESCE(s.status,'STALE'),COALESCE(s.last_error,'')
                FROM sync_cross_store_inventory_cache c
                LEFT JOIN sync_cross_store_inventory_status s
                  ON s.source_location_id=c.source_location_id
                WHERE c.source_location_id<>?
                """);
        List<Object> args = new ArrayList<>();
        args.add(currentLocationId);
        if (storeId != null) {
            sql.append(" AND c.source_location_id=?");
            args.add(storeId);
        }
        for (String token : ProductSearchHelper.tokens(clean)) {
            sql.append(" AND CONCAT_WS(' ',c.product_name,c.size,c.description,c.sku,c.barcode,c.additional_barcodes) ILIKE ?");
            args.add("%" + token + "%");
        }
        sql.append(" ORDER BY c.product_name,c.store_name LIMIT 500");
        List<Item> items = new ArrayList<>();
        try (PreparedStatement ps = local.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) items.add(new Item(
                        rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                        rs.getInt(9), rs.getInt(10), epoch(rs.getTimestamp(11)),
                        epoch(rs.getTimestamp(12)), rs.getString(13), rs.getString(14)));
            }
        }
        return new SearchResult(storeOptions(local, currentLocationId), List.copyOf(items));
    }

    private static int refreshStore(Connection local, Store store) throws SQLException {
        Map<Integer, JsonObject> products = keyed(fetchTable(store.locationId(), "products"), "product_id");
        Map<Integer, List<String>> additionalBarcodes = new HashMap<>();
        for (JsonObject barcode : fetchTable(store.locationId(), "product_barcodes")) {
            int productId = integer(barcode, "product_id");
            String value = text(barcode, "barcode");
            if (value != null && !value.isBlank()) {
                additionalBarcodes.computeIfAbsent(productId, ignored -> new ArrayList<>()).add(value);
            }
        }
        List<JsonObject> inventory = fetchTable(store.locationId(), "inventory");
        boolean oldAutoCommit = local.getAutoCommit();
        local.setAutoCommit(false);
        try (PreparedStatement delete = local.prepareStatement(
                "DELETE FROM sync_cross_store_inventory_cache WHERE source_location_id=?");
             PreparedStatement insert = local.prepareStatement("""
                INSERT INTO sync_cross_store_inventory_cache(
                  source_location_id,product_id,store_name,sku,barcode,additional_barcodes,
                  product_name,size,description,quantity_on_hand,reorder_level,
                  source_updated_at,cache_refreshed_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """)) {
            delete.setInt(1, store.locationId());
            delete.executeUpdate();
            int count = 0;
            for (JsonObject stock : inventory) {
                if (integer(stock, "location_id") != store.locationId()) continue;
                int productId = integer(stock, "product_id");
                JsonObject product = products.get(productId);
                if (product == null) continue;
                insert.setInt(1, store.locationId());
                insert.setInt(2, productId);
                insert.setString(3, store.name());
                insert.setString(4, text(product, "sku"));
                insert.setString(5, text(product, "barcode"));
                insert.setString(6, String.join(" ", additionalBarcodes.getOrDefault(productId, List.of())));
                insert.setString(7, requiredText(product, "name"));
                insert.setString(8, text(product, "size"));
                insert.setString(9, text(product, "description"));
                insert.setInt(10, integer(stock, "quantity_on_hand"));
                insert.setInt(11, integer(stock, "reorder_level"));
                insert.setTimestamp(12, timestamp(text(stock, "updated_at")));
                insert.addBatch();
                count++;
            }
            insert.executeBatch();
            local.commit();
            return count;
        } catch (SQLException ex) {
            local.rollback();
            throw ex;
        } finally {
            local.setAutoCommit(oldAutoCommit);
        }
    }

    static List<JsonObject> fetchTable(int locationId, String table) throws SQLException {
        List<JsonObject> rows = new ArrayList<>();
        long cursor = 0;
        while (true) {
            JsonObject body = new JsonObject();
            body.addProperty("p_location_id", locationId);
            body.addProperty("p_table_name", table);
            body.addProperty("p_after_sequence", cursor);
            body.addProperty("p_limit", PAGE_SIZE);
            try {
                SupabaseServerApi.Response response =
                        SupabaseServerApi.postRpc("smartstock_store_table_snapshot", body);
                if (!response.successful()) throw new SQLException(
                        SupabaseServerApi.failureMessage("Cross-store inventory refresh", response));
                JsonObject page = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonArray envelopes = page.getAsJsonArray("rows");
                if (envelopes == null || envelopes.isEmpty()) break;
                long next = cursor;
                for (JsonElement element : envelopes) {
                    JsonObject envelope = element.getAsJsonObject();
                    next = Math.max(next, envelope.get("sequence").getAsLong());
                    if (!envelope.get("is_deleted").getAsBoolean()) {
                        rows.add(envelope.getAsJsonObject("row_data"));
                    }
                }
                if (next <= cursor) throw new SQLException("Cloud inventory cursor did not advance.");
                cursor = next;
                if (envelopes.size() < PAGE_SIZE) break;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new SQLException("Cross-store inventory refresh was interrupted.", ex);
            } catch (IOException | RuntimeException ex) {
                throw new SQLException("Cross-store inventory could not be downloaded.", ex);
            }
        }
        return rows;
    }

    private static Map<Integer, JsonObject> keyed(List<JsonObject> rows, String key) {
        Map<Integer, JsonObject> result = new LinkedHashMap<>();
        for (JsonObject row : rows) result.put(integer(row, key), row);
        return result;
    }

    static List<Store> stores(Connection local, int currentLocationId) throws SQLException {
        List<Store> result = new ArrayList<>();
        try (PreparedStatement ps = local.prepareStatement(
                "SELECT location_id,name FROM locations WHERE location_id<>? ORDER BY name")) {
            ps.setInt(1, currentLocationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new Store(rs.getInt(1), rs.getString(2)));
            }
        }
        return result;
    }

    private static List<StoreOption> storeOptions(Connection local, int currentLocationId)
            throws SQLException {
        List<StoreOption> result = new ArrayList<>();
        for (Store store : stores(local, currentLocationId)) {
            try (PreparedStatement ps = local.prepareStatement("""
                    SELECT status,refreshed_at FROM sync_cross_store_inventory_status
                    WHERE source_location_id=?
                    """)) {
                ps.setInt(1, store.locationId());
                try (ResultSet rs = ps.executeQuery()) {
                    result.add(rs.next()
                            ? new StoreOption(store.locationId(), store.name(), rs.getString(1), epoch(rs.getTimestamp(2)))
                            : new StoreOption(store.locationId(), store.name(), "NOT_SYNCED", 0));
                }
            }
        }
        return List.copyOf(result);
    }

    private static void markStatus(Connection local, Store store, int count,
                                   String status, String error) throws SQLException {
        try (PreparedStatement ps = local.prepareStatement("""
                INSERT INTO sync_cross_store_inventory_status(
                  source_location_id,store_name,row_count,status,last_error,refreshed_at)
                VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)
                ON CONFLICT(source_location_id) DO UPDATE SET store_name=EXCLUDED.store_name,
                  row_count=EXCLUDED.row_count,status=EXCLUDED.status,last_error=EXCLUDED.last_error,
                  refreshed_at=CURRENT_TIMESTAMP
                """)) {
            ps.setInt(1, store.locationId()); ps.setString(2, store.name()); ps.setInt(3, count);
            ps.setString(4, status); ps.setString(5, error); ps.executeUpdate();
        }
    }

    private static int cachedCount(Connection local, int locationId) throws SQLException {
        try (PreparedStatement ps = local.prepareStatement(
                "SELECT COUNT(*) FROM sync_cross_store_inventory_cache WHERE source_location_id=?")) {
            ps.setInt(1, locationId); try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private static void requirePermission(Connection c, int userId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id
                JOIN permissions p ON p.permission_id=rp.permission_id
                WHERE u.user_id=? AND UPPER(p.permission_key) IN ('VIEW_INVENTORY','VIEW_MULTI_STORE_STOCK')
                GROUP BY u.user_id HAVING COUNT(DISTINCT UPPER(p.permission_key))=2
                """)) {
            ps.setInt(1, userId); try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return; }
        }
        throw new RuleViolation(403, "PERMISSION_DENIED",
                "You do not have permission to view store inventory.");
    }

    private static int integer(JsonObject row, String name) {
        return row.has(name) && !row.get(name).isJsonNull() ? row.get(name).getAsInt() : 0;
    }
    private static String text(JsonObject row, String name) {
        return row.has(name) && !row.get(name).isJsonNull() ? row.get(name).getAsString() : null;
    }
    private static String requiredText(JsonObject row, String name) throws SQLException {
        String value = text(row, name); if (value == null || value.isBlank())
            throw new SQLException("Cloud product is missing " + name + "."); return value;
    }
    private static Timestamp timestamp(String value) {
        try { return value == null || value.isBlank() ? null : Timestamp.from(Instant.parse(value)); }
        catch (RuntimeException ex) { return null; }
    }
    private static long epoch(Timestamp value) { return value == null ? 0 : value.getTime(); }
    private static String safeError(Exception ex) {
        String value = ex.getMessage(); return value == null || value.isBlank()
                ? ex.getClass().getSimpleName() : value.substring(0, Math.min(value.length(), 500));
    }

    record RefreshResult(int storesRefreshed, int rowsRefreshed, int storesFailed) { }
    record SearchResult(List<StoreOption> stores, List<Item> items) { }
    record StoreOption(int locationId, String name, String status, long refreshedAtEpochMillis) { }
    record Item(int locationId, String storeName, int productId, String sku, String barcode,
                String productName, String size, String description, int quantityOnHand, int reorderLevel,
                long sourceUpdatedAtEpochMillis, long cacheRefreshedAtEpochMillis,
                String cacheStatus, String cacheError) { }
    record Store(int locationId, String name) { }
    static final class RuleViolation extends Exception {
        private final int status; private final String code; private final String safeMessage;
        RuleViolation(int status, String code, String safeMessage) {
            super(safeMessage); this.status=status; this.code=code; this.safeMessage=safeMessage;
        }
        int status(){return status;} String code(){return code;} String safeMessage(){return safeMessage;}
    }
}
