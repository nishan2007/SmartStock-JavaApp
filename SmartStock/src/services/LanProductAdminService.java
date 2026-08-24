package services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-only product creation, editing, and print-catalog reads. */
final class LanProductAdminService {
    private static final Gson GSON = new Gson();

    private LanProductAdminService() { }

    static List<Map<String, Object>> searchEditable(Connection connection, String search,
                                                     int userId, int locationId) throws Exception {
        requirePermission(connection, userId, "EDIT_ITEM");
        String query = clean(search, 300);
        if (query.isBlank()) return List.of();
        String sql = """
                SELECT p.product_id,p.name,COALESCE(p.size,''),COALESCE(p.sku,''),COALESCE(p.barcode,''),
                  COALESCE(p.description,''),COALESCE(p.cost_price,0),COALESCE(p.price,0),
                  COALESCE(p.product_type,'INVENTORY'),COALESCE(i.quantity_on_hand,0),COALESCE(i.reorder_level,0),
                  p.category_id,COALESCE(c.name,''),p.vendor_id,COALESCE(v.name,''),COALESCE(p.image_url,''),
                  COALESCE(it.name,''),COALESCE(ib.name,''),COALESCE(sl.name,''),COALESCE(ssl.name,'')
                FROM products p LEFT JOIN categories c ON c.category_id=p.category_id
                LEFT JOIN vendors v ON v.vendor_id=p.vendor_id
                LEFT JOIN inventory i ON i.product_id=p.product_id AND i.location_id=?
                LEFT JOIN item_types it ON it.item_type_id=p.item_type_id
                LEFT JOIN item_brands ib ON ib.brand_id=p.brand_id
                LEFT JOIN product_shelf_assignments psa ON psa.product_id=p.product_id AND psa.location_id=?
                LEFT JOIN shelf_locations sl ON sl.shelf_location_id=psa.shelf_location_id
                LEFT JOIN shelf_locations ssl ON ssl.shelf_location_id=psa.storage_shelf_location_id
                WHERE %s ORDER BY p.name LIMIT 300
                """.formatted(ProductSearchHelper.predicate("p", locationId, query));
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, locationId); ps.setInt(2, locationId); ProductSearchHelper.bindTokens(ps, 3, query);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(map(
                        "productId", rs.getInt(1), "name", rs.getString(2), "size", rs.getString(3),
                        "sku", rs.getString(4), "barcode", rs.getString(5), "description", rs.getString(6),
                        "costPrice", rs.getBigDecimal(7), "price", rs.getBigDecimal(8), "productType", rs.getString(9),
                        "quantity", rs.getInt(10), "reorderLevel", rs.getInt(11),
                        "categoryId", nullableInt(rs, 12), "categoryName", rs.getString(13),
                        "vendorId", nullableInt(rs, 14), "vendorName", rs.getString(15), "imageUrl", rs.getString(16),
                        "itemTypeName", rs.getString(17), "brandName", rs.getString(18), "shelfName", rs.getString(19),
                        "storageShelfName", rs.getString(20), "additionalBarcodes", additionalBarcodes(connection, rs.getInt(1))));
            }
        }
        return rows;
    }

    static List<Map<String, Object>> priceTagItems(Connection connection, String search,
                                                    int userId, int locationId) throws Exception {
        requireAnyPermission(connection, userId, "VIEW_INVENTORY", "EDIT_ITEM", "NEW_ITEM", "MAKE_SALE");
        String query = clean(search, 300);
        String sql = """
                SELECT item_type,name,size,description,code,price,item_id FROM (
                  SELECT 'Product' item_type,p.name,COALESCE(p.size,'') size,COALESCE(p.description,'') description,
                    COALESCE(NULLIF(p.sku,''),NULLIF(p.barcode,''),'PRODUCT-'||p.product_id) code,
                    COALESCE(p.price,0) price,p.product_id item_id FROM products p WHERE %s
                  UNION ALL
                  SELECT 'Custom item',coi.item_name,'',COALESCE(coi.description,''),
                    COALESCE(NULLIF(coi.sku,''),NULLIF(coi.barcode,''),'CUSTOM-'||coi.custom_item_id),
                    COALESCE(coi.fixed_price,0),coi.custom_item_id FROM custom_order_items coi
                    WHERE coi.is_active=TRUE AND COALESCE(coi.has_variants,FALSE)=FALSE AND %s
                  UNION ALL
                  SELECT 'Custom variant',coi.item_name||' - '||v.variant_name,v.variant_name,
                    COALESCE(coi.description,''),COALESCE(NULLIF(v.sku,''),NULLIF(v.barcode,''),
                    'CUSTOM-'||coi.custom_item_id||'-'||v.custom_variant_id),COALESCE(v.fixed_price,coi.fixed_price,0),
                    v.custom_variant_id FROM custom_order_item_variants v
                    JOIN custom_order_items coi ON coi.custom_item_id=v.custom_item_id
                    WHERE coi.is_active=TRUE AND v.is_active=TRUE AND %s
                ) tags ORDER BY name LIMIT 300
                """.formatted(ProductSearchHelper.predicate("p", locationId, query),
                ProductSearchHelper.customItemPredicate("coi", query),
                ProductSearchHelper.customVariantPredicate("coi", "v", query));
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = ProductSearchHelper.bindTokens(ps, 1, query);
            index = ProductSearchHelper.bindTokens(ps, index, query);
            ProductSearchHelper.bindTokens(ps, index, query);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(map("itemType", rs.getString(1), "name", rs.getString(2),
                        "size", rs.getString(3), "description", rs.getString(4), "code", rs.getString(5),
                        "price", rs.getBigDecimal(6), "itemId", rs.getLong(7)));
            }
        }
        return rows;
    }

    static Map<String, Object> priceTagSettings(Connection connection, int userId,
                                                int locationId) throws Exception {
        requireAnyPermission(connection, userId, "VIEW_INVENTORY", "EDIT_ITEM", "NEW_ITEM", "MAKE_SALE");
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT COALESCE(price_tag_templates,''),COALESCE(price_tag_show_company,TRUE),
                  COALESCE(price_tag_show_sku,TRUE),COALESCE(price_tag_show_barcode,TRUE),
                  COALESCE(price_tag_width_inches,2.25),COALESCE(price_tag_height_inches,1.25)
                FROM company_customization WHERE location_id=?
                """)) {
            ps.setInt(1, locationId); try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map("encodedTemplates", rs.getString(1), "showCompany", rs.getBoolean(2),
                        "showSku", rs.getBoolean(3), "showBarcode", rs.getBoolean(4),
                        "widthInches", rs.getDouble(5), "heightInches", rs.getDouble(6));
            }
        }
        return map("encodedTemplates", "", "showCompany", true, "showSku", true,
                "showBarcode", true, "widthInches", 2.25d, "heightInches", 1.25d);
    }

    static Map<String, Object> create(Connection connection, JsonObject body, UUID deviceId,
                                      int userId, String userName, int locationId) throws Exception {
        requireDeviceId(deviceId);
        requirePermission(connection, userId, "NEW_ITEM");
        ProductRequest request = parsed(body);
        ValidatedProduct product = validate(connection, request, locationId);
        int productId;
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO products(name,size,sku,barcode,description,cost_price,price,product_type,category_id,
                  vendor_id,image_url,created_by_user_id,created_by_name,item_type_id,brand_id)
                VALUES (?,NULLIF(?,''),NULLIF(?,''),?,?,?,?,?,?,?,?,?,?,?,?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            bindProduct(ps, product, userId, userName, null);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Item could not be created.");
                productId = keys.getInt(1);
            }
        } catch (SQLException ex) { throw friendlyConstraint(ex); }
        if (product.inventoryItem()) {
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO inventory(product_id,location_id,quantity_on_hand,reorder_level) VALUES (?,?,?,?)
                    ON CONFLICT(product_id,location_id) DO UPDATE SET quantity_on_hand=EXCLUDED.quantity_on_hand,
                      reorder_level=EXCLUDED.reorder_level
                    """)) {
                ps.setInt(1, productId); ps.setInt(2, locationId); ps.setInt(3, request.quantity());
                ps.setInt(4, request.reorderLevel()); ps.executeUpdate();
            }
            if (request.quantity() != 0) movement(connection, productId, locationId, request.quantity(),
                    "NEW_ITEM", "Starting quantity for new item", userId, userName, deviceId);
        }
        ItemDetailsService.upsertShelfAssignment(connection, productId, locationId,
                product.shelfLocationId(), product.storageShelfLocationId());
        replaceBarcodes(connection, productId, Set.of(), product.extraBarcodes());
        SyncOutboxService.recordEvent(connection, "PRODUCT_CREATED", map(
                "product_id", productId, "location_id", locationId, "user_id", userId),
                locationId, deviceId.toString(), userId);
        audit(connection, "LAN_PRODUCT_CREATED", deviceId, userId,
                "product_id=" + productId + "; location_id=" + locationId);
        return map("productId", productId, "sku", product.sku());
    }

    static Map<String, Object> update(Connection connection, JsonObject body, UUID deviceId,
                                      int userId, String userName, int locationId) throws Exception {
        requireDeviceId(deviceId);
        requirePermission(connection, userId, "EDIT_ITEM");
        ProductRequest request = parsed(body);
        if (request.productId() == null || request.productId() <= 0)
            throw rule(400, "VALIDATION_ERROR", "Select an item to update.");
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM products WHERE product_id=? FOR UPDATE")) {
            ps.setInt(1, request.productId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw rule(404, "PRODUCT_NOT_FOUND", "Item was not found.");
            }
        }
        ValidatedProduct product = validate(connection, request, locationId);
        InventoryState inventory = lockInventory(connection, request.productId(), locationId);
        boolean adjust = request.adjustQuantity() && product.inventoryItem();
        if (adjust) {
            requirePermission(connection, userId, "MANUAL_ADJUSTMENT");
            if (request.expectedQuantity() == null || request.expectedQuantity() != inventory.quantity())
                throw rule(409, "STOCK_CHANGED", "Inventory changed on another register. Reload the item before applying the manual count.");
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE products SET name=?,size=NULLIF(?,''),sku=NULLIF(?,''),barcode=?,description=?,cost_price=?,
                  price=?,product_type=?,category_id=?,vendor_id=?,image_url=?,item_type_id=?,brand_id=?,
                  updated_at=CURRENT_TIMESTAMP WHERE product_id=?
                """)) {
            bindProduct(ps, product, userId, userName, request.productId());
            if (ps.executeUpdate() != 1) throw rule(404, "PRODUCT_NOT_FOUND", "Item was not found.");
        } catch (SQLException ex) { throw friendlyConstraint(ex); }
        if (product.inventoryItem()) {
            try (PreparedStatement ps = connection.prepareStatement(adjust ? """
                    UPDATE inventory SET quantity_on_hand=?,reorder_level=? WHERE product_id=? AND location_id=?
                    """ : """
                    UPDATE inventory SET reorder_level=? WHERE product_id=? AND location_id=?
                    """)) {
                if (adjust) {
                    ps.setInt(1, request.quantity()); ps.setInt(2, request.reorderLevel());
                    ps.setInt(3, request.productId()); ps.setInt(4, locationId);
                } else {
                    ps.setInt(1, request.reorderLevel()); ps.setInt(2, request.productId()); ps.setInt(3, locationId);
                }
                if (ps.executeUpdate() != 1) throw new SQLException("Inventory row could not be updated.");
            }
            if (adjust && request.quantity() != inventory.quantity()) movement(connection, request.productId(),
                    locationId, request.quantity() - inventory.quantity(), "MANUAL_ADJUSTMENT",
                    "Manual adjustment from Edit Item", userId, userName, deviceId);
        }
        ItemDetailsService.upsertShelfAssignment(connection, request.productId(), locationId,
                product.shelfLocationId(), product.storageShelfLocationId());
        Set<String> existing = additionalBarcodeSet(connection, request.productId(), true);
        replaceBarcodes(connection, request.productId(), existing, product.extraBarcodes());
        SyncOutboxService.recordEvent(connection, "PRODUCT_UPDATED", map(
                "product_id", request.productId(), "location_id", locationId, "user_id", userId),
                locationId, deviceId.toString(), userId);
        audit(connection, "LAN_PRODUCT_UPDATED", deviceId, userId,
                "product_id=" + request.productId() + "; location_id=" + locationId + "; quantity_adjusted=" + adjust);
        return map("productId", request.productId(), "quantity", adjust ? request.quantity() : inventory.quantity());
    }

    private static ValidatedProduct validate(Connection connection, ProductRequest request,
                                              int locationId) throws Exception {
        String name = required(request.name(), 300, "Item name is required.");
        String barcode = required(request.barcode(), 300, "Primary barcode is required.");
        String sku = clean(request.sku(), 300);
        if (request.productId() == null && request.costPrice() == null && requiresCostPrice(connection, locationId))
            throw rule(400, "VALIDATION_ERROR", "Cost price is required by Company Preferences.");
        BigDecimal cost = request.costPrice() == null ? BigDecimal.ZERO : request.costPrice();
        BigDecimal price = request.price() == null ? BigDecimal.ZERO : request.price();
        if (cost.signum() < 0 || price.signum() < 0)
            throw rule(400, "VALIDATION_ERROR", "Cost and selling prices cannot be negative.");
        if (request.reorderLevel() < 0) throw rule(400, "VALIDATION_ERROR", "Reorder quantity cannot be negative.");
        if (Math.abs((long) request.quantity()) > 1_000_000_000L)
            throw rule(400, "VALIDATION_ERROR", "Quantity is outside the supported range.");
        String type = normalizeProductType(request.productType());
        if (request.categoryId() == null || request.categoryId() <= 0)
            throw rule(400, "VALIDATION_ERROR", "Department is required.");
        requireReference(connection, "categories", "category_id", request.categoryId(), "Department");
        if (request.vendorId() != null) requireReference(connection, "vendors", "vendor_id", request.vendorId(), "Vendor");
        int itemTypeId = ItemDetailsService.resolveItemType(connection, request.categoryId(), request.itemTypeName());
        int brandId = ItemDetailsService.resolveBrand(connection, request.brandName());
        int shelfId = ItemDetailsService.resolveShelfLocation(connection, locationId, request.shelfName());
        Integer storageShelfId = clean(request.storageShelfName(), 300).isBlank() ? null
                : ItemDetailsService.resolveShelfLocation(connection, locationId, request.storageShelfName());
        Set<String> barcodes = normalizedBarcodes(request.additionalBarcodes(), sku, barcode);
        Set<String> allBarcodes = new LinkedHashSet<>(barcodes);
        allBarcodes.add(barcode);
        CatalogBarcodeService.requireAvailable(connection, allBarcodes, request.productId(), null, null);
        return new ValidatedProduct(name, clean(request.size(), 200), sku, barcode,
                clean(request.description(), 4000), cost, price, type, request.categoryId(), request.vendorId(),
                clean(request.imageUrl(), 4000), itemTypeId, brandId, shelfId, storageShelfId, barcodes,
                "INVENTORY".equals(type));
    }

    private static void bindProduct(PreparedStatement ps, ValidatedProduct product, int userId,
                                    String userName, Integer productId) throws SQLException {
        int i = 1;
        ps.setString(i++, product.name()); ps.setString(i++, product.size()); ps.setString(i++, product.sku());
        ps.setString(i++, product.barcode()); ps.setString(i++, product.description()); ps.setBigDecimal(i++, product.costPrice());
        ps.setBigDecimal(i++, product.price()); ps.setString(i++, product.productType()); ps.setInt(i++, product.categoryId());
        if (product.vendorId() == null) ps.setNull(i++, Types.INTEGER); else ps.setInt(i++, product.vendorId());
        ps.setString(i++, product.imageUrl());
        if (productId == null) { ps.setInt(i++, userId); ps.setString(i++, userName); }
        ps.setInt(i++, product.itemTypeId()); ps.setInt(i++, product.brandId());
        if (productId != null) ps.setInt(i, productId);
    }

    private static InventoryState lockInventory(Connection connection, int productId, int locationId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO inventory(product_id,location_id,quantity_on_hand,reorder_level) VALUES (?,?,0,0)
                ON CONFLICT(product_id,location_id) DO NOTHING
                """)) { ps.setInt(1, productId); ps.setInt(2, locationId); ps.executeUpdate(); }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT quantity_on_hand,reorder_level FROM inventory WHERE product_id=? AND location_id=? FOR UPDATE")) {
            ps.setInt(1, productId); ps.setInt(2, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Inventory row could not be locked.");
                return new InventoryState(rs.getInt(1), rs.getInt(2));
            }
        }
    }

    private static void replaceBarcodes(Connection connection, int productId, Set<String> existing,
                                        Set<String> wanted) throws SQLException {
        Set<String> removed = new LinkedHashSet<>(existing); removed.removeAll(wanted);
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM product_barcodes WHERE product_id=? AND barcode=?")) {
            for (String barcode : removed) {
                ReferenceDataSyncService.recordTombstone(connection, "product_barcodes", Map.of("barcode", barcode));
                delete.setInt(1, productId); delete.setString(2, barcode); delete.addBatch();
            }
            delete.executeBatch();
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO product_barcodes(product_id,barcode) VALUES (?,?)
                ON CONFLICT(barcode) DO UPDATE SET product_id=EXCLUDED.product_id,updated_at=CURRENT_TIMESTAMP
                """)) {
            for (String barcode : wanted) { insert.setInt(1, productId); insert.setString(2, barcode); insert.addBatch(); }
            insert.executeBatch();
        }
    }

    private static List<String> additionalBarcodes(Connection connection, int productId) throws SQLException {
        return new ArrayList<>(additionalBarcodeSet(connection, productId, false));
    }
    private static Set<String> additionalBarcodeSet(Connection connection, int productId,
                                                    boolean lock) throws SQLException {
        Set<String> values = new LinkedHashSet<>();
        String sql = "SELECT barcode FROM product_barcodes WHERE product_id=? ORDER BY barcode" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, productId); try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) if (rs.getString(1) != null && !rs.getString(1).isBlank()) values.add(rs.getString(1));
            }
        }
        return values;
    }

    private static Set<String> normalizedBarcodes(List<String> values, String sku, String primary) throws RuleViolation {
        if (values != null && values.size() > 100) throw rule(400, "VALIDATION_ERROR", "Too many additional barcodes.");
        Set<String> result = new LinkedHashSet<>();
        if (values != null) for (String value : values) {
            String barcode = clean(value, 300); if (!barcode.isBlank()) result.add(barcode);
        }
        result.remove(sku); result.remove(primary); return result;
    }

    private static void movement(Connection connection, int productId, int locationId, int change,
                                 String reason, String note, int userId, String userName,
                                 UUID deviceId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO inventory_movements(product_id,location_id,change_qty,reason,note,user_name,
                  user_id,device_id) VALUES (?,?,?,?,?,?,?,?)
                """)) {
            ps.setInt(1, productId); ps.setInt(2, locationId); ps.setInt(3, change); ps.setString(4, reason);
            ps.setString(5, note); ps.setString(6, userName); ps.setInt(7, userId); ps.setString(8, deviceId.toString()); ps.executeUpdate();
        }
    }

    static String deviceIdText(UUID deviceId) throws RuleViolation { requireDeviceId(deviceId); return deviceId.toString(); }
    static void requireDeviceId(UUID deviceId) throws RuleViolation {
        if (deviceId == null) throw rule(500, "DEVICE_ID_REQUIRED", "A device identity is required for every product change.");
    }

    private static void requireReference(Connection connection, String table, String idColumn,
                                         int id, String label) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM " + table + " WHERE " + idColumn + "=?")) {
            ps.setInt(1, id); try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw rule(400, "VALIDATION_ERROR", label + " was not found.");
            }
        }
    }

    private static boolean requiresCostPrice(Connection connection, int locationId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT COALESCE(require_cost_price_on_new_item, TRUE)
                FROM company_customization WHERE location_id=?
                """)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) { return !rs.next() || rs.getBoolean(1); }
        }
    }

    private static ProductRequest parsed(JsonObject body) throws RuleViolation {
        try { ProductRequest request = GSON.fromJson(body, ProductRequest.class);
            if (request == null) throw rule(400, "VALIDATION_ERROR", "Item details are required."); return request;
        } catch (RuleViolation ex) { throw ex; }
        catch (Exception ex) { throw rule(400, "VALIDATION_ERROR", "Item details are invalid."); }
    }
    private static String normalizeProductType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase().replace(' ', '_');
        return List.of("SERVICE", "NON_INVENTORY").contains(type) ? type : "INVENTORY";
    }
    private static Integer nullableInt(ResultSet rs, int index) throws SQLException {
        int value = rs.getInt(index); return rs.wasNull() ? null : value;
    }
    private static Exception friendlyConstraint(SQLException ex) {
        return "23505".equals(ex.getSQLState())
                ? rule(409, "ITEM_IDENTIFIER_EXISTS", "An item already uses this SKU or barcode.") : ex;
    }
    private static void requirePermission(Connection connection, int userId, String permission) throws Exception {
        if (!hasPermission(connection, userId, permission)) throw rule(403, "PERMISSION_DENIED", "You do not have permission for this item action.");
    }
    private static void requireAnyPermission(Connection connection, int userId, String... permissions) throws Exception {
        for (String permission : permissions) if (hasPermission(connection, userId, permission)) return;
        throw rule(403, "PERMISSION_DENIED", "You do not have permission to view price tag items.");
    }
    private static boolean hasPermission(Connection connection, int userId, String permission) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id
                JOIN permissions p ON p.permission_id=rp.permission_id
                WHERE u.user_id=? AND UPPER(p.permission_key)=? LIMIT 1
                """)) {
            ps.setInt(1, userId); ps.setString(2, permission); try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }
    private static void audit(Connection connection, String type, UUID deviceId, int userId,
                              String details) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO security_audit_events(event_type,device_id,actor_user_id,details) VALUES (?,?,?,?)
                """)) {
            ps.setString(1, type); ps.setObject(2, deviceId); ps.setInt(3, userId); ps.setString(4, details); ps.executeUpdate();
        }
    }
    private static String required(String value, int max, String message) throws RuleViolation {
        String cleaned = clean(value, max); if (cleaned.isBlank()) throw rule(400, "VALIDATION_ERROR", message); return cleaned;
    }
    private static String clean(String value, int max) throws RuleViolation {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.length() > max) throw rule(400, "VALIDATION_ERROR", "An item field is too long."); return cleaned;
    }
    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]); return result;
    }
    private static RuleViolation rule(int status, String code, String message) { return new RuleViolation(status, code, message); }

    static final class RuleViolation extends Exception {
        private final int status; private final String code; private final String safeMessage;
        RuleViolation(int status, String code, String message) { super(message); this.status=status; this.code=code; this.safeMessage=message; }
        int status() { return status; } String code() { return code; } String safeMessage() { return safeMessage; }
    }
    private record ProductRequest(Integer productId,String name,String size,String sku,String barcode,String description,
                                  BigDecimal costPrice,BigDecimal price,String productType,Integer categoryId,Integer vendorId,
                                  String imageUrl,String itemTypeName,String brandName,String shelfName,String storageShelfName,
                                  List<String> additionalBarcodes,int quantity,int reorderLevel,Integer expectedQuantity,
                                  boolean adjustQuantity) { }
    private record ValidatedProduct(String name,String size,String sku,String barcode,String description,
                                    BigDecimal costPrice,BigDecimal price,String productType,int categoryId,Integer vendorId,
                                    String imageUrl,int itemTypeId,int brandId,int shelfLocationId,
                                    Integer storageShelfLocationId,Set<String> extraBarcodes,boolean inventoryItem) { }
    private record InventoryState(int quantity,int reorderLevel) { }
}
