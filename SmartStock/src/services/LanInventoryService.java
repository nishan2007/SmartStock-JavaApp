package services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import managers.ServerReceiptNumberManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-only inventory reads and atomic receiving operations. */
final class LanInventoryService {
    private static final Gson GSON = new Gson();

    private LanInventoryService() { }

    static Map<String, Object> lookups(Connection c, int userId, int locationId,
                                       Integer categoryId) throws Exception {
        requireAnyPermission(c, userId, "VIEW_INVENTORY", "VIEW_ITEM_DETAILS", "RECEIVING_INVENTORY",
                "STORE_TRANSFER", "NEW_ITEM", "EDIT_ITEM", "DEPARTMENT_MANAGEMENT", "VENDOR_MANAGEMENT");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("departments", idNameRows(c,
                "SELECT category_id,name FROM categories ORDER BY name"));
        result.put("brands", nameRows(c, "SELECT name FROM item_brands ORDER BY name"));
        if (categoryId == null) {
            result.put("itemTypes", nameRows(c, "SELECT DISTINCT name FROM item_types ORDER BY name"));
        } else {
            result.put("itemTypes", nameRows(c,
                    "SELECT name FROM item_types WHERE category_id=? ORDER BY name", categoryId));
        }
        result.put("shelves", nameRows(c,
                "SELECT name FROM shelf_locations WHERE location_id=? ORDER BY name", locationId));
        boolean mayViewVendors = hasAnyPermission(c, userId,
                "VIEW_VENDOR", "NEW_ITEM", "EDIT_ITEM", "VENDOR_MANAGEMENT");
        result.put("vendors", mayViewVendors ? idNameRows(c,
                "SELECT vendor_id,name FROM vendors WHERE COALESCE(is_active,TRUE)=TRUE ORDER BY name") : List.of());
        return result;
    }

    static List<Map<String, Object>> receivingSearch(Connection c, String search,
                                                      int userId, int locationId) throws Exception {
        requirePermission(c, userId, "RECEIVING_INVENTORY");
        String query = text(search, 300);
        if (query.isBlank()) return List.of();
        String sql = """
                SELECT item_type,item_id,name,description,code,quantity_on_hand,item_type_name,brand_name,price,image_url FROM (
                  SELECT 'Product' item_type,p.product_id item_id,
                    p.name||CASE WHEN COALESCE(p.size,'')='' THEN '' ELSE ' ('||p.size||')' END name,
                    COALESCE(p.description,'') description,COALESCE(p.sku,'') code,
                    COALESCE(i.quantity_on_hand,0) quantity_on_hand,COALESCE(it.name,'') item_type_name,
                    COALESCE(ib.name,'') brand_name,COALESCE(p.price,0) price,COALESCE(p.image_url,'') image_url
                  FROM products p LEFT JOIN inventory i ON i.product_id=p.product_id AND i.location_id=?
                  LEFT JOIN item_types it ON it.item_type_id=p.item_type_id
                  LEFT JOIN item_brands ib ON ib.brand_id=p.brand_id
                  WHERE COALESCE(p.product_type,'INVENTORY')='INVENTORY' AND %s
                  UNION ALL
                  SELECT 'Custom Item',coi.custom_item_id,coi.item_name,COALESCE(coi.description,''),
                    COALESCE(NULLIF(coi.sku,''),NULLIF(coi.barcode,''),'CUSTOM-'||coi.custom_item_id),
                    COALESCE(coi.quantity_on_hand,0),COALESCE(it.name,''),COALESCE(ib.name,''),
                    COALESCE(coi.fixed_price,coi.area_price,0),COALESCE(coi.image_url,'')
                  FROM custom_order_items coi LEFT JOIN item_types it ON it.item_type_id=coi.item_type_id
                  LEFT JOIN item_brands ib ON ib.brand_id=coi.brand_id WHERE coi.is_active=TRUE
                    AND COALESCE(coi.product_type,'INVENTORY')='INVENTORY'
                    AND COALESCE(coi.has_variants,FALSE)=FALSE AND %s
                  UNION ALL
                  SELECT 'Custom Variant',coiv.custom_variant_id,coi.item_name||' - '||coiv.variant_name,
                    COALESCE(coi.description,''),COALESCE(NULLIF(coiv.sku,''),NULLIF(coiv.barcode,''),
                    'CUSTOM-'||coi.custom_item_id||'-'||coiv.custom_variant_id),COALESCE(coiv.quantity_on_hand,0),
                    COALESCE(it.name,''),COALESCE(ib.name,''),COALESCE(coiv.fixed_price,coi.fixed_price,coi.area_price,0),
                    COALESCE(NULLIF(coiv.image_url,''),coi.image_url,'')
                  FROM custom_order_item_variants coiv JOIN custom_order_items coi ON coi.custom_item_id=coiv.custom_item_id
                  LEFT JOIN item_types it ON it.item_type_id=coi.item_type_id LEFT JOIN item_brands ib ON ib.brand_id=coi.brand_id
                  WHERE coi.is_active=TRUE AND coiv.is_active=TRUE
                    AND COALESCE(coi.product_type,'INVENTORY')='INVENTORY' AND %s
                ) matched ORDER BY item_type,name LIMIT 300
                """.formatted(ProductSearchHelper.predicate("p", locationId, query),
                ProductSearchHelper.customItemPredicate("coi", query),
                ProductSearchHelper.customVariantPredicate("coi", "coiv", query));
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            int index = ProductSearchHelper.bindTokens(ps, 2, query);
            index = ProductSearchHelper.bindTokens(ps, index, query);
            ProductSearchHelper.bindTokens(ps, index, query);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(map(
                        "itemType", rs.getString(1), "itemId", rs.getInt(2), "name", rs.getString(3),
                        "description", rs.getString(4), "code", rs.getString(5), "quantityOnHand", rs.getInt(6),
                        "itemTypeName",rs.getString(7),"brandName",rs.getString(8),"price",rs.getBigDecimal(9),
                        "imageUrl",rs.getString(10)));
            }
        }
        return rows;
    }

    static Map<String, Object> inventory(Connection c, JsonObject body,
                                         int userId, int locationId) throws Exception {
        requirePermission(c, userId, "VIEW_INVENTORY");
        InventoryRequest r = GSON.fromJson(body, InventoryRequest.class);
        String search = text(r == null ? null : r.search(), 300);
        String stock = text(r == null ? null : r.stockFilter(), 40);
        String department = text(r == null ? null : r.department(), 200);
        String productType = text(r == null ? null : r.productType(), 40);
        String itemType = text(r == null ? null : r.itemType(), 200);
        String brand = text(r == null ? null : r.brand(), 200);
        String shelf = text(r == null ? null : r.shelf(), 200);
        String storageShelf = text(r == null ? null : r.storageShelf(), 200);
        boolean showVendor = hasPermission(c, userId, "VIEW_VENDOR");
        boolean showCost = hasPermission(c, userId, "VIEW_COST_PRICE");
        boolean showCreatedBy = hasPermission(c, userId, "VIEW_CREATED_BY");

        StringBuilder sql = new StringBuilder("""
                SELECT p.product_id,COALESCE(p.sku,''),COALESCE(p.barcode,''),COALESCE(p.name,''),COALESCE(p.size,''),
                  COALESCE(p.description,''),COALESCE(p.product_type,'INVENTORY'),COALESCE(cat.name,''),
                  COALESCE(it.name,''),COALESCE(ib.name,''),COALESCE(sl.name,''),COALESCE(ssl.name,''),
                  COALESCE(v.name,''),COALESCE(p.cost_price,0),COALESCE(p.price,0),
                  COALESCE(i.quantity_on_hand,0),COALESCE(i.reorder_level,0),COALESCE(p.created_by_name,'')
                FROM products p LEFT JOIN inventory i ON i.product_id=p.product_id AND i.location_id=?
                LEFT JOIN categories cat ON cat.category_id=p.category_id
                LEFT JOIN item_types it ON it.item_type_id=p.item_type_id
                LEFT JOIN item_brands ib ON ib.brand_id=p.brand_id
                LEFT JOIN product_shelf_assignments psa ON psa.product_id=p.product_id AND psa.location_id=?
                LEFT JOIN shelf_locations sl ON sl.shelf_location_id=psa.shelf_location_id
                LEFT JOIN shelf_locations ssl ON ssl.shelf_location_id=psa.storage_shelf_location_id
                LEFT JOIN vendors v ON v.vendor_id=p.vendor_id WHERE TRUE
                """);
        List<Object> args = new ArrayList<>(List.of(locationId, locationId));
        if (!search.isBlank()) {
            sql.append(" AND ").append(ProductSearchHelper.predicate("p", locationId, search, showVendor));
            for (String token : ProductSearchHelper.tokens(search)) args.add("%" + token + "%");
        }
        appendFilter(sql, args, "cat.name", department);
        appendProductTypeFilter(sql, productType);
        appendFilter(sql, args, "it.name", itemType);
        appendFilter(sql, args, "ib.name", brand);
        appendFilter(sql, args, "sl.name", shelf);
        appendFilter(sql, args, "ssl.name", storageShelf);
        if ("In Stock".equals(stock)) sql.append(" AND COALESCE(p.product_type,'INVENTORY')='INVENTORY' AND COALESCE(i.quantity_on_hand,0)>0");
        else if ("Low Stock".equals(stock)) sql.append(" AND COALESCE(p.product_type,'INVENTORY')='INVENTORY' AND COALESCE(i.quantity_on_hand,0)>0 AND COALESCE(i.quantity_on_hand,0)<=COALESCE(i.reorder_level,0)");
        else if ("Out of Stock".equals(stock)) sql.append(" AND COALESCE(p.product_type,'INVENTORY')='INVENTORY' AND COALESCE(i.quantity_on_hand,0)<=0");
        sql.append(" ORDER BY p.name LIMIT 2000");
        List<Map<String, Object>> rows = new ArrayList<>();
        int units = 0;
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("productId", rs.getInt(1)); row.put("sku", rs.getString(2)); row.put("barcode", rs.getString(3));
                    row.put("name", rs.getString(4)); row.put("size", rs.getString(5)); row.put("description", rs.getString(6));
                    row.put("productType", rs.getString(7)); row.put("department", rs.getString(8));
                    row.put("itemType", rs.getString(9)); row.put("brand", rs.getString(10));
                    row.put("shelf", rs.getString(11)); row.put("storageShelf", rs.getString(12));
                    row.put("vendor", showVendor ? rs.getString(13) : "");
                    row.put("costPrice", showCost ? rs.getBigDecimal(14) : null); row.put("price", rs.getBigDecimal(15));
                    row.put("quantityOnHand", rs.getInt(16)); row.put("reorderLevel", rs.getInt(17));
                    row.put("createdBy", showCreatedBy ? rs.getString(18) : ""); rows.add(row);
                    if ("INVENTORY".equals(normalizeProductType(rs.getString(7)))) units += rs.getInt(16);
                }
            }
        }
        return map("products", rows, "totalProducts", rows.size(), "totalUnits", units,
                "canViewVendor", showVendor, "canViewCostPrice", showCost, "canViewCreatedBy", showCreatedBy);
    }

    static Map<String, Object> details(Connection c, int productId,
                                       int userId, int locationId) throws Exception {
        requirePermission(c, userId, "VIEW_ITEM_DETAILS");
        boolean showVendor = hasPermission(c, userId, "VIEW_VENDOR");
        boolean showCost = hasPermission(c, userId, "VIEW_COST_PRICE");
        boolean showCreatedBy = hasPermission(c, userId, "VIEW_CREATED_BY");
        Map<String, Object> fields = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT p.product_id,p.name,COALESCE(p.product_type,'INVENTORY'),COALESCE(p.sku,''),
                  COALESCE(p.barcode,''),p.category_id,COALESCE(cat.name,''),COALESCE(p.image_url,''),
                  COALESCE(p.created_by_name,''),COALESCE(it.name,''),COALESCE(ib.name,''),
                  COALESCE(sl.name,''),COALESCE(ssl.name,''),COALESCE(v.name,''),COALESCE(p.cost_price,0),
                  COALESCE(p.price,0),COALESCE(i.quantity_on_hand,0),COALESCE(i.reorder_level,0),
                  COALESCE(p.description,'')
                FROM products p LEFT JOIN categories cat ON cat.category_id=p.category_id
                LEFT JOIN item_types it ON it.item_type_id=p.item_type_id LEFT JOIN item_brands ib ON ib.brand_id=p.brand_id
                LEFT JOIN vendors v ON v.vendor_id=p.vendor_id
                LEFT JOIN inventory i ON i.product_id=p.product_id AND i.location_id=?
                LEFT JOIN product_shelf_assignments psa ON psa.product_id=p.product_id AND psa.location_id=?
                LEFT JOIN shelf_locations sl ON sl.shelf_location_id=psa.shelf_location_id
                LEFT JOIN shelf_locations ssl ON ssl.shelf_location_id=psa.storage_shelf_location_id
                WHERE p.product_id=?
                """)) {
            ps.setInt(1, locationId); ps.setInt(2, locationId); ps.setInt(3, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw rule(404, "PRODUCT_NOT_FOUND", "Item was not found.");
                fields.put("Product Id", rs.getInt(1)); fields.put("Name", rs.getString(2));
                fields.put("Product Type", rs.getString(3)); fields.put("Sku", rs.getString(4));
                fields.put("Barcode", rs.getString(5)); fields.put("Category Id", rs.getObject(6));
                fields.put("Category Name", rs.getString(7)); fields.put("Image Url", rs.getString(8));
                if (showCreatedBy) fields.put("Created By Name", rs.getString(9));
                fields.put("Item Type", rs.getString(10)); fields.put("Item Brand", rs.getString(11));
                fields.put("Shelf Location", rs.getString(12)); fields.put("Storage Shelf Location", rs.getString(13));
                if (showVendor) fields.put("Vendor Name", rs.getString(14));
                if (showCost) fields.put("Cost Price", rs.getBigDecimal(15));
                fields.put("Price", rs.getBigDecimal(16)); fields.put("Quantity On Hand", rs.getInt(17));
                fields.put("Reorder Level", rs.getInt(18)); fields.put("Description", rs.getString(19));
            }
        }
        fields.put("Additional Barcodes", additionalBarcodes(c, productId));
        addSalesSummary(c, fields, productId, locationId);
        return map("fields", fields, "activities", activities(c, productId, locationId));
    }

    static List<Map<String, Object>> receivingHistory(Connection c, JsonObject body,
                                                       int userId, int locationId) throws Exception {
        requirePermission(c, userId, "VIEW_RECEIVING_HISTORY");
        HistoryRequest r = GSON.fromJson(body, HistoryRequest.class);
        String search = text(r == null ? null : r.search(), 300);
        ZoneId zone = storeZone(c, locationId);
        Instant from = dateBoundary(r == null ? null : r.fromDate(), zone, false);
        Instant to = dateBoundary(r == null ? null : r.toDate(), zone, true);
        StringBuilder sql = new StringBuilder("""
                SELECT im.movement_id,COALESCE(im.receive_id,''),im.created_at,COALESCE(p.name,'Unknown'),
                  COALESCE(p.sku,''),COALESCE(l.name,'Unknown'),COALESCE(im.change_qty,0),
                  COALESCE(im.user_name,rb.user_name,u.full_name,u.username,''),COALESCE(im.note,'')
                FROM inventory_movements im LEFT JOIN receiving_batches rb ON rb.receive_id=im.receive_id
                LEFT JOIN products p ON p.product_id=im.product_id LEFT JOIN locations l ON l.location_id=im.location_id
                LEFT JOIN users u ON u.user_id=rb.user_id
                WHERE UPPER(COALESCE(im.reason,''))='INVENTORY_ENTRY' AND im.location_id=?
                """);
        List<Object> args = new ArrayList<>(List.of(locationId));
        if (!search.isBlank()) {
            sql.append(" AND (CAST(im.movement_id AS TEXT) ILIKE ? OR COALESCE(im.receive_id,'') ILIKE ? OR COALESCE(p.name,'') ILIKE ? OR COALESCE(p.sku,'') ILIKE ? OR COALESCE(l.name,'') ILIKE ? OR COALESCE(im.user_name,rb.user_name,u.full_name,u.username,'') ILIKE ? OR COALESCE(im.note,'') ILIKE ?)");
            for (int i = 0; i < 7; i++) args.add("%" + search + "%");
        }
        if (from != null) { sql.append(" AND im.created_at>=?"); args.add(Timestamp.from(from)); }
        if (to != null) { sql.append(" AND im.created_at<?"); args.add(Timestamp.from(to)); }
        sql.append(" ORDER BY im.created_at DESC,im.movement_id DESC LIMIT 2000");
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bind(ps, args); try (ResultSet rs = ps.executeQuery()) { while (rs.next()) rows.add(map(
                    "movementId", rs.getLong(1), "receiveId", rs.getString(2),
                    "createdAtEpochMillis", rs.getTimestamp(3).getTime(), "productName", rs.getString(4),
                    "sku", rs.getString(5), "storeName", rs.getString(6), "changeQuantity", rs.getInt(7),
                    "receivedBy", rs.getString(8), "note", rs.getString(9)));
            }
        }
        return rows;
    }

    static Map<String, Object> receive(Connection c, JsonObject body, UUID deviceId,
                                       int userId, String userName, int locationId,
                                       LanSalesService.ApprovalConsumer approvals) throws Exception {
        requirePermission(c, userId, "RECEIVING_INVENTORY");
        ReceiveRequest request = GSON.fromJson(body, ReceiveRequest.class);
        if (request == null || request.lines() == null || request.lines().isEmpty())
            throw rule(400, "VALIDATION_ERROR", "No inventory entries were supplied.");
        if (request.lines().size() > 300) throw rule(400, "VALIDATION_ERROR", "Too many inventory entries.");
        List<ReceiveLine> requested = new ArrayList<>(request.lines());
        requested.sort(Comparator.comparing((ReceiveLine line) -> normalizeItemType(line.itemType()))
                .thenComparingInt(ReceiveLine::itemId));
        Set<String> unique = new HashSet<>();
        List<LockedStock> locked = new ArrayList<>();
        boolean needsOverride = false;
        for (ReceiveLine line : requested) {
            String type = normalizeItemType(line.itemType());
            if (line.itemId() <= 0 || line.quantity() <= 0 || line.quantity() > 1_000_000)
                throw rule(400, "VALIDATION_ERROR", "Every receiving line needs a valid item, count, and quantity.");
            if (!unique.add(type + ":" + line.itemId())) throw rule(400, "VALIDATION_ERROR", "An item appears more than once.");
            LockedStock stock = lockStock(c, type, line.itemId(), locationId);
            locked.add(stock);
            needsOverride |= stock.currentStock() != line.countedStock();
        }
        LanSalesService.Approval overrideApproval = null;
        if (needsOverride) {
            if (request.overrideReason() == null || request.overrideReason().isBlank())
                throw rule(403, "APPROVAL_REQUIRED", "A reason is required to change counted stock.");
            if (hasPermission(c, userId, "RECEIVING_STOCK_OVERRIDE")) {
                overrideApproval = new LanSalesService.Approval(userId, userName, request.overrideReason().trim());
            } else {
                overrideApproval = approvals.consume(request.overrideApprovalToken(), "RECEIVING_STOCK_OVERRIDE",
                        "Receiving Stock Override", request.overrideReason());
            }
        }
        ServerReceiptNumberManager.ReceiveNumber receive = ServerReceiptNumberManager.nextReceive(c, locationId, deviceId);
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO receiving_batches(receive_id,location_id,user_id,user_name,receive_device_id,receive_sequence)
                VALUES (?,?,?,?,?,?)
                """)) {
            ps.setString(1, receive.receiveId()); ps.setInt(2, locationId); ps.setInt(3, userId);
            ps.setString(4, userName); ps.setString(5, receive.deviceId()); ps.setInt(6, receive.sequence()); ps.executeUpdate();
        }
        for (int i = 0; i < requested.size(); i++) {
            ReceiveLine line = requested.get(i); LockedStock stock = locked.get(i);
            int adjustment = line.countedStock() - stock.currentStock();
            if (adjustment != 0) {
                applyStock(c, stock, adjustment, locationId);
                movement(c, stock, adjustment, "RECEIVING_STOCK_OVERRIDE",
                        "system_stock=" + stock.currentStock() + "; counted_stock=" + line.countedStock()
                                + "; reason=" + overrideApproval.reason() + "; approved_by_user_id="
                                + overrideApproval.approverUserId() + "; approved_by_name=" + overrideApproval.approverName(),
                        locationId, overrideApproval.approverUserId(), overrideApproval.approverName(), deviceId, receive);
            }
            applyStock(c, stock, line.quantity(), locationId);
            movement(c, stock, line.quantity(), "INVENTORY_ENTRY", "entered_by_user_id=" + userId,
                    locationId, userId, userName, deviceId, receive);
        }
        SyncOutboxService.recordEvent(c, "INVENTORY_RECEIVED", map(
                "receive_id", receive.receiveId(), "location_id", locationId, "device_id", receive.deviceId(),
                "receive_sequence", receive.sequence(), "line_count", requested.size(), "user_id", userId),
                locationId, deviceId.toString(), userId);
        SyncOutboxService.recordEvent(c, "INVENTORY_MOVEMENT_CREATED", map(
                "source", "ENTER_INVENTORY", "receive_id", receive.receiveId(),
                "location_id", locationId, "line_count", requested.size()),
                locationId, deviceId.toString(), userId);
        return map("receiveId", receive.receiveId(), "lineCount", requested.size());
    }

    private static LockedStock lockStock(Connection c, String type, int itemId, int locationId) throws Exception {
        if ("PRODUCT".equals(type)) {
            try (PreparedStatement ensure = c.prepareStatement("INSERT INTO inventory(product_id,location_id,quantity_on_hand,reorder_level) SELECT product_id,?,0,0 FROM products WHERE product_id=? AND COALESCE(product_type,'INVENTORY')='INVENTORY' AND is_active=TRUE ON CONFLICT(product_id,location_id) DO NOTHING")) {
                ensure.setInt(1, locationId); ensure.setInt(2, itemId); ensure.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT p.name,COALESCE(i.quantity_on_hand,0) FROM products p JOIN inventory i ON i.product_id=p.product_id AND i.location_id=? WHERE p.product_id=? AND COALESCE(p.product_type,'INVENTORY')='INVENTORY' AND p.is_active=TRUE FOR UPDATE OF i")) {
                ps.setInt(1, locationId); ps.setInt(2, itemId); try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw rule(404, "ITEM_NOT_FOUND", "A receiving item no longer exists.");
                    return new LockedStock(type, itemId, null, rs.getString(1), null, rs.getInt(2));
                }
            }
        }
        if ("CUSTOM_ITEM".equals(type)) {
            try (PreparedStatement ps = c.prepareStatement("SELECT item_name,COALESCE(quantity_on_hand,0) FROM custom_order_items WHERE custom_item_id=? AND is_active=TRUE AND COALESCE(product_type,'INVENTORY')='INVENTORY' AND COALESCE(has_variants,FALSE)=FALSE FOR UPDATE")) {
                ps.setInt(1, itemId); try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw rule(404, "ITEM_NOT_FOUND", "A custom receiving item no longer exists.");
                    return new LockedStock(type, itemId, null, rs.getString(1), null, rs.getInt(2));
                }
            }
        }
        if ("CUSTOM_VARIANT".equals(type)) {
            try (PreparedStatement ps = c.prepareStatement("SELECT v.custom_item_id,i.item_name||' - '||v.variant_name,v.variant_name,COALESCE(v.quantity_on_hand,0) FROM custom_order_item_variants v JOIN custom_order_items i ON i.custom_item_id=v.custom_item_id WHERE v.custom_variant_id=? AND v.is_active=TRUE AND i.is_active=TRUE AND COALESCE(i.product_type,'INVENTORY')='INVENTORY' FOR UPDATE OF v")) {
                ps.setInt(1, itemId); try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw rule(404, "ITEM_NOT_FOUND", "A custom receiving variant no longer exists.");
                    return new LockedStock(type, itemId, rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4));
                }
            }
        }
        throw rule(400, "VALIDATION_ERROR", "Unsupported receiving item type.");
    }

    private static void applyStock(Connection c, LockedStock stock, int change, int locationId) throws SQLException {
        String sql = switch (stock.type()) {
            case "PRODUCT" -> "UPDATE inventory SET quantity_on_hand=quantity_on_hand+? WHERE product_id=? AND location_id=?";
            case "CUSTOM_ITEM" -> "UPDATE custom_order_items SET quantity_on_hand=quantity_on_hand+?,updated_at=CURRENT_TIMESTAMP WHERE custom_item_id=?";
            default -> "UPDATE custom_order_item_variants SET quantity_on_hand=quantity_on_hand+?,updated_at=CURRENT_TIMESTAMP WHERE custom_variant_id=?";
        };
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, change); ps.setInt(2, stock.itemId()); if ("PRODUCT".equals(stock.type())) ps.setInt(3, locationId);
            if (ps.executeUpdate() != 1) throw new SQLException("Inventory changed before it could be updated.");
        }
    }

    private static void movement(Connection c, LockedStock stock, int change, String reason, String note,
                                 int locationId, int actorId, String actorName, UUID deviceId,
                                 ServerReceiptNumberManager.ReceiveNumber receive) throws SQLException {
        if ("PRODUCT".equals(stock.type())) {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO inventory_movements(product_id,location_id,change_qty,reason,note,user_name,user_id,
                      device_id,receive_id,receive_device_id,receive_sequence)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """)) {
                ps.setInt(1, stock.itemId()); ps.setInt(2, locationId); ps.setInt(3, change); ps.setString(4, reason);
                ps.setString(5, note); ps.setString(6, actorName); ps.setInt(7, actorId); ps.setString(8, deviceId.toString());
                ps.setString(9, receive.receiveId()); ps.setString(10, receive.deviceId()); ps.setInt(11, receive.sequence()); ps.executeUpdate();
            }
        } else {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO custom_order_item_movements(custom_item_id,custom_variant_id,variant_name,location_id,
                      change_qty,reason,note,user_name,user_id,device_id,receive_id,receive_device_id,receive_sequence)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """)) {
                ps.setInt(1, stock.parentItemId() == null ? stock.itemId() : stock.parentItemId());
                if (stock.parentItemId() == null) { ps.setNull(2, Types.INTEGER); ps.setNull(3, Types.VARCHAR); }
                else { ps.setInt(2, stock.itemId()); ps.setString(3, stock.variantName()); }
                ps.setInt(4, locationId); ps.setInt(5, change); ps.setString(6, reason); ps.setString(7, note);
                ps.setString(8, actorName); ps.setInt(9, actorId); ps.setString(10, deviceId.toString());
                ps.setString(11, receive.receiveId()); ps.setString(12, receive.deviceId()); ps.setInt(13, receive.sequence()); ps.executeUpdate();
            }
        }
    }

    private static List<Map<String, Object>> activities(Connection c, int productId, int locationId) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT created_at,COALESCE(reason,'INVENTORY'),COALESCE(change_qty,0),'',
                  COALESCE(CAST(movement_id AS TEXT),''),COALESCE(user_name,''),COALESCE(note,'')
                FROM inventory_movements WHERE product_id=? AND location_id=?
                UNION ALL
                SELECT s.created_at,'SALE',-COALESCE(si.quantity,0),
                  CAST(COALESCE(si.unit_price,0)*COALESCE(si.quantity,0) AS TEXT),
                  COALESCE(s.receipt_number,'Sale #'||s.sale_id),COALESCE(s.user_name,''),
                  'Sold without inventory quantity change'
                FROM sale_items si JOIN sales s ON s.sale_id=si.sale_id
                WHERE si.product_id=? AND s.location_id=? AND COALESCE(si.product_type,'INVENTORY')<>'INVENTORY'
                UNION ALL
                SELECT sr.created_at,'RETURN',COALESCE(sri.quantity,0),
                  CAST(COALESCE(sri.unit_price,0)*COALESCE(sri.quantity,0) AS TEXT),
                  'Return #'||sr.return_id,COALESCE(sr.user_name,''),'Returned from receipt '||COALESCE(s.receipt_number,'')
                FROM sale_return_items sri JOIN sale_returns sr ON sr.return_id=sri.return_id
                JOIN sale_items si ON si.sale_item_id=sri.sale_item_id LEFT JOIN sales s ON s.sale_id=sr.sale_id
                WHERE sri.product_id=? AND sr.location_id=? AND COALESCE(si.product_type,'INVENTORY')<>'INVENTORY'
                ORDER BY 1 DESC LIMIT 2000
                """)) {
            ps.setInt(1, productId); ps.setInt(2, locationId); ps.setInt(3, productId); ps.setInt(4, locationId);
            ps.setInt(5, productId); ps.setInt(6, locationId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) rows.add(map(
                    "createdAtEpochMillis", rs.getTimestamp(1).getTime(), "activityType", rs.getString(2),
                    "quantity", rs.getInt(3), "amount", rs.getString(4), "reference", rs.getString(5),
                    "userName", rs.getString(6), "note", rs.getString(7)));
            }
        }
        return rows;
    }

    private static void addSalesSummary(Connection c, Map<String, Object> fields,
                                        int productId, int locationId) throws SQLException {
        int sold = 0; BigDecimal sales = BigDecimal.ZERO;
        try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(SUM(si.quantity),0),COALESCE(SUM(si.quantity*si.unit_price),0) FROM sale_items si JOIN sales s ON s.sale_id=si.sale_id WHERE si.product_id=? AND s.location_id=?")) {
            ps.setInt(1, productId); ps.setInt(2, locationId); try (ResultSet rs = ps.executeQuery()) { if (rs.next()) { sold=rs.getInt(1); sales=zero(rs.getBigDecimal(2)); } }
        }
        int returned = 0; BigDecimal returns = BigDecimal.ZERO;
        try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(SUM(i.quantity),0),COALESCE(SUM(i.quantity*i.unit_price),0) FROM sale_return_items i JOIN sale_returns r ON r.return_id=i.return_id WHERE i.product_id=? AND r.location_id=?")) {
            ps.setInt(1, productId); ps.setInt(2, locationId); try (ResultSet rs = ps.executeQuery()) { if (rs.next()) { returned=rs.getInt(1); returns=zero(rs.getBigDecimal(2)); } }
        }
        fields.put("Total Sold", Math.max(0, sold-returned)); fields.put("Total Sales Amount", sales.subtract(returns).max(BigDecimal.ZERO));
        fields.put("Total Returned", returned); fields.put("Total Return Amount", returns);
    }

    private static String additionalBarcodes(Connection c, int productId) throws SQLException {
        List<String> values = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT barcode FROM product_barcodes WHERE product_id=? ORDER BY barcode")) {
            ps.setInt(1, productId); try (ResultSet rs = ps.executeQuery()) { while (rs.next()) if (rs.getString(1)!=null&&!rs.getString(1).isBlank()) values.add(rs.getString(1)); }
        }
        return String.join(", ", values);
    }

    private static List<Map<String, Object>> idNameRows(Connection c, String sql, Object... args) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) { bind(ps, List.of(args)); try (ResultSet rs=ps.executeQuery()){while(rs.next()) rows.add(map("id",rs.getInt(1),"name",rs.getString(2)));} }
        return rows;
    }
    private static List<String> nameRows(Connection c, String sql, Object... args) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) { bind(ps, List.of(args)); try (ResultSet rs=ps.executeQuery()){while(rs.next()) rows.add(rs.getString(1));} }
        return rows;
    }
    private static void appendFilter(StringBuilder sql,List<Object>args,String column,String value){if(!value.isBlank()){sql.append(" AND ").append(column).append("=?");args.add(value);}}
    private static void appendProductTypeFilter(StringBuilder sql,String filter){
        String type="COALESCE(p.product_type,'INVENTORY')";
        switch(filter){
            case "Inventory Only" -> sql.append(" AND ").append(type).append("='INVENTORY'");
            case "Service Only" -> sql.append(" AND ").append(type).append("='SERVICE'");
            case "Non Inventory Only" -> sql.append(" AND ").append(type).append("='NON_INVENTORY'");
            case "Hide Inventory" -> sql.append(" AND ").append(type).append("<>'INVENTORY'");
            case "Hide Services" -> sql.append(" AND ").append(type).append("<>'SERVICE'");
            case "Hide Non Inventory" -> sql.append(" AND ").append(type).append("<>'NON_INVENTORY'");
            case "" -> { }
            default -> throw new IllegalArgumentException("Unknown product type filter.");
        }
    }
    private static void bind(PreparedStatement ps,List<?>args)throws SQLException{for(int i=0;i<args.size();i++)ps.setObject(i+1,args.get(i));}
    private static String text(String value,int max)throws RuleViolation{String clean=value==null?"":value.trim();if(clean.length()>max)throw rule(400,"VALIDATION_ERROR","A request value is too long.");return clean;}
    private static String normalizeItemType(String value){String v=value==null?"":value.trim().toUpperCase().replace(' ','_');return switch(v){case "PRODUCT"->"PRODUCT";case "CUSTOM_ITEM"->"CUSTOM_ITEM";case "CUSTOM_VARIANT"->"CUSTOM_VARIANT";default->v;};}
    private static String normalizeProductType(String value){String v=value==null?"":value.trim().toUpperCase().replace(' ','_');return List.of("SERVICE","NON_INVENTORY").contains(v)?v:"INVENTORY";}
    private static BigDecimal zero(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
    private static ZoneId storeZone(Connection c,int id)throws SQLException{try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(timezone,'') FROM locations WHERE location_id=?")){ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()){if(rs.next())try{return ZoneId.of(rs.getString(1));}catch(Exception ignored){}}}return ZoneId.systemDefault();}
    private static Instant dateBoundary(String value,ZoneId zone,boolean end)throws RuleViolation{if(value==null||value.isBlank())return null;try{LocalDate d=LocalDate.parse(value.trim());return(end?d.plusDays(1):d).atStartOfDay(zone).toInstant();}catch(DateTimeParseException ex){throw rule(400,"VALIDATION_ERROR","Dates must use yyyy-MM-dd format.");}}
    private static boolean hasPermission(Connection c,int userId,String key)throws SQLException{try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions p ON p.permission_id=rp.permission_id WHERE u.user_id=? AND UPPER(p.permission_key)=? LIMIT 1")){ps.setInt(1,userId);ps.setString(2,key);try(ResultSet rs=ps.executeQuery()){return rs.next();}}}
    private static boolean hasAnyPermission(Connection c,int userId,String...keys)throws SQLException{for(String key:keys)if(hasPermission(c,userId,key))return true;return false;}
    private static void requirePermission(Connection c,int userId,String key)throws Exception{if(!hasPermission(c,userId,key))throw rule(403,"PERMISSION_DENIED","You do not have permission for this inventory operation.");}
    private static void requireAnyPermission(Connection c,int userId,String...keys)throws Exception{if(!hasAnyPermission(c,userId,keys))throw rule(403,"PERMISSION_DENIED","You do not have permission for inventory lookups.");}
    private static Map<String,Object> map(Object...values){Map<String,Object>m=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)m.put((String)values[i],values[i+1]);return m;}
    private static RuleViolation rule(int status,String code,String message){return new RuleViolation(status,code,message);}

    static final class RuleViolation extends Exception {
        private final int status; private final String code; private final String safeMessage;
        RuleViolation(int status,String code,String safeMessage){super(safeMessage);this.status=status;this.code=code;this.safeMessage=safeMessage;}
        int status(){return status;} String code(){return code;} String safeMessage(){return safeMessage;}
    }
    private record InventoryRequest(String search,String stockFilter,String department,String productType,String itemType,String brand,String shelf,String storageShelf){}
    private record HistoryRequest(String search,String fromDate,String toDate){}
    private record ReceiveRequest(String overrideApprovalToken,String overrideReason,List<ReceiveLine> lines){}
    private record ReceiveLine(String itemType,int itemId,int countedStock,int quantity){}
    private record LockedStock(String type,int itemId,Integer parentItemId,String name,String variantName,int currentStock){}
}
