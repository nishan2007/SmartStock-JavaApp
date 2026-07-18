package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Rollback-only integration checks for LAN catalog, inventory, sales, receiving, and transfers. */
public final class InventoryBoundaryProbe {
    private InventoryBoundaryProbe() { }

    public static void main(String[] args) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                required("SMARTSTOCK_TEST_JDBC_URL"), required("SMARTSTOCK_TEST_DB_USER"),
                required("SMARTSTOCK_TEST_DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            try {
                Fixture fixture = fixture(connection);
                verifyCatalogAdministration(connection, fixture);
                verifyProductAndSaleConcurrency(connection, fixture);
                verifyInventoryReads(connection, fixture);
                verifyReceiving(connection, fixture);
                verifyTransfer(connection, fixture);
                verifyRejectedRequests(connection, fixture);
                System.out.println("Catalog, product, negative-inventory, sales, receiving, and transfer boundary integration checks passed.");
            } finally {
                connection.rollback();
            }
        }
    }

    private static void verifyCatalogAdministration(Connection connection, Fixture f) throws Exception {
        LanCatalogAdminService.departments(connection, "", f.userId(), f.sourceLocationId());
        String suffix = UUID.randomUUID().toString();
        JsonObject department = new JsonObject();
        department.addProperty("name", "Boundary Department " + suffix);
        department.addProperty("vatRatePercent", 0);
        department.addProperty("description", "Rollback-only catalog probe");
        Map<String, Object> savedDepartment = LanCatalogAdminService.saveDepartment(
                connection, department, f.deviceId(), f.userId(), f.sourceLocationId());
        int categoryId = ((Number) savedDepartment.get("categoryId")).intValue();
        department.addProperty("categoryId", categoryId);
        department.addProperty("description", "Rollback-only catalog probe update");
        LanCatalogAdminService.saveDepartment(connection, department, f.deviceId(),
                f.userId(), f.sourceLocationId());

        LanCatalogAdminService.vendors(connection, "", f.userId());
        JsonObject vendor = new JsonObject();
        vendor.addProperty("name", "Boundary Vendor " + suffix);
        vendor.addProperty("contactName", "Boundary Probe");
        vendor.addProperty("phone", "555-0100");
        vendor.addProperty("email", "boundary@example.invalid");
        vendor.addProperty("active", true);
        Map<String, Object> savedVendor = LanCatalogAdminService.saveVendor(
                connection, vendor, f.deviceId(), f.userId());
        vendor.addProperty("vendorId", ((Number) savedVendor.get("vendorId")).intValue());
        vendor.addProperty("notes", "Rollback-only update");
        LanCatalogAdminService.saveVendor(connection, vendor, f.deviceId(), f.userId());

        LanCatalogAdminService.customerTypes(connection, "", false, f.userId());
        JsonObject customerType = new JsonObject();
        customerType.addProperty("name", "Boundary Customer Type " + suffix);
        customerType.addProperty("description", "Rollback-only customer type probe");
        customerType.addProperty("active", true);
        Map<String, Object> savedCustomerType = LanCatalogAdminService.saveCustomerType(
                connection, customerType, f.deviceId(), f.userId());
        customerType.addProperty("customerTypeId", ((Number) savedCustomerType.get("customerTypeId")).intValue());
        customerType.addProperty("description", "Rollback-only customer type update");
        LanCatalogAdminService.saveCustomerType(connection, customerType, f.deviceId(), f.userId());
    }

    private static void verifyProductAndSaleConcurrency(Connection connection, Fixture f) throws Exception {
        String suffix = UUID.randomUUID().toString();
        JsonObject product = productBody(null, f.categoryId(), suffix, -3, null);
        Map<String, Object> created = LanProductAdminService.create(connection, product, f.deviceId(),
                f.userId(), "Inventory Boundary Probe", f.sourceLocationId());
        int productId = ((Number) created.get("productId")).intValue();
        if (stock(connection, productId, f.sourceLocationId()) != -3) {
            throw new AssertionError("New item did not preserve a negative starting quantity.");
        }
        if (LanProductAdminService.searchEditable(connection, suffix, f.userId(), f.sourceLocationId()).isEmpty()
                || LanProductAdminService.priceTagItems(connection, suffix, f.userId(), f.sourceLocationId()).isEmpty()) {
            throw new AssertionError("New item was not visible through edit and price-tag catalog reads.");
        }
        LanProductAdminService.priceTagSettings(connection, f.userId(), f.sourceLocationId());

        LanSalesService.checkout(connection, saleBody(productId), f.deviceId(), f.userId(),
                "Inventory Boundary Probe", f.sourceLocationId(), InventoryBoundaryProbe::unexpectedApproval);
        LanSalesService.checkout(connection, saleBody(productId), f.deviceId(), f.userId(),
                "Inventory Boundary Probe", f.sourceLocationId(), InventoryBoundaryProbe::unexpectedApproval);
        LanSalesService.checkout(connection, saleBody(productId, f.customerId()), f.deviceId(), f.userId(),
                "Inventory Boundary Probe", f.sourceLocationId(), InventoryBoundaryProbe::unexpectedApproval);
        if (stock(connection, productId, f.sourceLocationId()) != -6) {
            throw new AssertionError("Repeated register sales did not use cumulative relative stock subtraction.");
        }

        JsonObject update = productBody(productId, f.categoryId(), suffix, -7, -6);
        LanProductAdminService.update(connection, update, f.deviceId(), f.userId(),
                "Inventory Boundary Probe", f.sourceLocationId());
        if (stock(connection, productId, f.sourceLocationId()) != -7) {
            throw new AssertionError("Negative manual inventory adjustment was not preserved.");
        }
        JsonObject stale = productBody(productId, f.categoryId(), suffix, 0, -5);
        expectProduct("STOCK_CHANGED", () -> LanProductAdminService.update(connection, stale,
                f.deviceId(), f.userId(), "Inventory Boundary Probe", f.sourceLocationId()));
    }

    private static JsonObject productBody(Integer productId, int categoryId, String suffix,
                                          int quantity, Integer expectedQuantity) {
        JsonObject body = new JsonObject();
        if (productId != null) body.addProperty("productId", productId);
        body.addProperty("name", "Boundary Product " + suffix);
        body.addProperty("size", "Test"); body.addProperty("sku", "BOUNDARY-SKU-" + suffix);
        body.addProperty("barcode", "BOUNDARY-BARCODE-" + suffix);
        body.addProperty("description", "Rollback-only product and concurrency probe");
        body.addProperty("costPrice", 5); body.addProperty("price", 10);
        body.addProperty("productType", "INVENTORY"); body.addProperty("categoryId", categoryId);
        body.addProperty("imageUrl", ""); body.addProperty("itemTypeName", "BOUNDARY TYPE");
        body.addProperty("brandName", "BOUNDARY BRAND"); body.addProperty("shelfName", "BOUNDARY SHELF");
        body.addProperty("storageShelfName", "BOUNDARY STORAGE"); body.add("additionalBarcodes", new JsonArray());
        body.addProperty("quantity", quantity); body.addProperty("reorderLevel", 0);
        if (expectedQuantity != null) body.addProperty("expectedQuantity", expectedQuantity);
        body.addProperty("adjustQuantity", true);
        return body;
    }

    private static JsonObject saleBody(int productId) {
        return saleBody(productId, null);
    }

    private static JsonObject saleBody(int productId, Integer customerId) {
        JsonObject body = new JsonObject(); body.addProperty("paymentMethod", "CARD");
        if (customerId != null) body.addProperty("customerId", customerId);
        body.addProperty("saleDiscountPercent", BigDecimal.ZERO); body.addProperty("cashCollected", BigDecimal.ZERO);
        JsonObject line = new JsonObject(); line.addProperty("productId", productId); line.addProperty("quantity", 1);
        line.addProperty("unitPrice", BigDecimal.TEN); line.addProperty("discountPercent", BigDecimal.ZERO);
        JsonArray lines = new JsonArray(); lines.add(line); body.add("lines", lines); return body;
    }

    private static void verifyInventoryReads(Connection connection, Fixture f) throws Exception {
        LanInventoryService.lookups(connection, f.userId(), f.sourceLocationId(), null);
        List<Map<String, Object>> search = LanInventoryService.receivingSearch(
                connection, f.sku(), f.userId(), f.sourceLocationId());
        if (search.stream().noneMatch(row -> ((Number) row.get("itemId")).intValue() == f.productId())) {
            throw new AssertionError("Receiving search did not return the same-store product.");
        }
        JsonObject listRequest = new JsonObject();
        listRequest.addProperty("search", f.sku());
        Map<String, Object> inventory = LanInventoryService.inventory(
                connection, listRequest, f.userId(), f.sourceLocationId());
        if (((List<?>) inventory.get("products")).isEmpty()) {
            throw new AssertionError("Inventory list did not return the fixture product.");
        }
        Map<String, Object> details = LanInventoryService.details(
                connection, f.productId(), f.userId(), f.sourceLocationId());
        if (!((Map<?, ?>) details.get("fields")).containsKey("Product Id")) {
            throw new AssertionError("Inventory details did not return product fields.");
        }
        LanInventoryService.receivingHistory(connection, new JsonObject(),
                f.userId(), f.sourceLocationId());
    }

    private static void verifyReceiving(Connection connection, Fixture f) throws Exception {
        int before = stock(connection, f.productId(), f.sourceLocationId());
        JsonObject body = new JsonObject();
        JsonObject line = new JsonObject();
        line.addProperty("itemType", "PRODUCT");
        line.addProperty("itemId", f.productId());
        line.addProperty("countedStock", before);
        line.addProperty("quantity", 1);
        JsonArray lines = new JsonArray();
        lines.add(line);
        body.add("lines", lines);
        Map<String, Object> result = LanInventoryService.receive(connection, body, f.deviceId(),
                f.userId(), "Inventory Boundary Probe", f.sourceLocationId(),
                InventoryBoundaryProbe::unexpectedApproval);
        if (((Number) result.get("lineCount")).intValue() != 1
                || stock(connection, f.productId(), f.sourceLocationId()) != before + 1) {
            throw new AssertionError("Atomic inventory receiving did not update the expected stock.");
        }
    }

    private static void verifyTransfer(Connection connection, Fixture f) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("destinationLocationId", f.destinationLocationId());
        body.addProperty("note", "Rollback-only transfer boundary probe");
        JsonObject line = new JsonObject();
        line.addProperty("productId", f.productId());
        int sourceBefore = stock(connection, f.productId(), f.sourceLocationId());
        int transferQuantity = sourceBefore + 2;
        line.addProperty("quantity", transferQuantity);
        JsonArray lines = new JsonArray();
        lines.add(line);
        body.add("lines", lines);

        Map<String, Object> created = LanTransferService.create(connection, body, f.deviceId(),
                f.userId(), "Inventory Boundary Probe", f.sourceLocationId());
        long transferId = ((Number) created.get("transferId")).longValue();
        if (stock(connection, f.productId(), f.sourceLocationId()) != -2) {
            throw new AssertionError("Transfer creation did not atomically subtract source stock.");
        }
        LanTransferService.items(connection, transferId, f.userId(), f.destinationLocationId());
        expectTransfer("TRANSFER_NOT_FOUND", () -> LanTransferService.items(
                connection, transferId, f.userId(), f.sourceLocationId()));

        int destinationBefore = stock(connection, f.productId(), f.destinationLocationId());
        Map<String, Object> received = LanTransferService.receive(connection, transferId, f.deviceId(),
                f.userId(), "Inventory Boundary Probe", f.destinationLocationId());
        if (((Number) received.get("lineCount")).intValue() != 1
                || stock(connection, f.productId(), f.destinationLocationId()) != destinationBefore + transferQuantity) {
            throw new AssertionError("Transfer receiving did not atomically add destination stock.");
        }
        expectTransfer("TRANSFER_ALREADY_RECEIVED", () -> LanTransferService.receive(
                connection, transferId, f.deviceId(), f.userId(), "Inventory Boundary Probe",
                f.destinationLocationId()));
    }

    private static void verifyRejectedRequests(Connection connection, Fixture f) throws Exception {
        JsonObject duplicate = new JsonObject();
        duplicate.addProperty("destinationLocationId", f.destinationLocationId());
        JsonObject line = new JsonObject();
        line.addProperty("productId", f.productId());
        line.addProperty("quantity", 1);
        JsonArray lines = new JsonArray();
        lines.add(line); lines.add(line.deepCopy()); duplicate.add("lines", lines);
        expectTransfer("VALIDATION_ERROR", () -> LanTransferService.create(connection, duplicate,
                f.deviceId(), f.userId(), "Inventory Boundary Probe", f.sourceLocationId()));

        JsonObject invalidReceive = new JsonObject();
        JsonObject invalidLine = new JsonObject();
        invalidLine.addProperty("itemType", "PRODUCT");
        invalidLine.addProperty("itemId", f.productId());
        invalidLine.addProperty("countedStock", 0);
        invalidLine.addProperty("quantity", 0);
        JsonArray invalidLines = new JsonArray(); invalidLines.add(invalidLine);
        invalidReceive.add("lines", invalidLines);
        expectInventory("VALIDATION_ERROR", () -> LanInventoryService.receive(connection, invalidReceive,
                f.deviceId(), f.userId(), "Inventory Boundary Probe", f.sourceLocationId(),
                InventoryBoundaryProbe::unexpectedApproval));
    }

    private static Fixture fixture(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT u.user_id,ul.location_id,p.product_id,COALESCE(p.sku,''),
                       (SELECT location_id FROM locations WHERE location_id<>ul.location_id ORDER BY location_id LIMIT 1),
                       (SELECT device_id FROM devices WHERE COALESCE(is_approved,FALSE)=TRUE
                          AND COALESCE(is_blocked,FALSE)=FALSE AND COALESCE(allow_sales,TRUE)=TRUE
                          AND COALESCE(receipt_device_code,'')<>'' ORDER BY last_seen DESC NULLS LAST LIMIT 1),
                       p.category_id,
                       (SELECT customer_id FROM customer_accounts WHERE COALESCE(is_active,TRUE)=TRUE
                          ORDER BY customer_id LIMIT 1)
                FROM users u
                JOIN user_locations ul ON ul.user_id=u.user_id
                JOIN inventory i ON i.location_id=ul.location_id AND i.quantity_on_hand>0
                JOIN products p ON p.product_id=i.product_id
                WHERE u.is_active=TRUE AND COALESCE(p.product_type,'INVENTORY')='INVENTORY'
                  AND p.category_id IS NOT NULL
                  AND (SELECT COUNT(DISTINCT UPPER(x.permission_key))
                       FROM role_permissions rp JOIN permissions x ON x.permission_id=rp.permission_id
                       WHERE rp.role_id=u.role_id AND UPPER(x.permission_key) IN
                         ('VIEW_INVENTORY','VIEW_ITEM_DETAILS','VIEW_RECEIVING_HISTORY','RECEIVING_INVENTORY','STORE_TRANSFER',
                          'DEPARTMENT_MANAGEMENT','VENDOR_MANAGEMENT','NEW_ITEM','EDIT_ITEM','MANUAL_ADJUSTMENT',
                          'MAKE_SALE','CUSTOMER_ACCOUNTS'))=12
                ORDER BY u.user_id,p.product_id LIMIT 1
                """)) {
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getObject(5) == null || rs.getObject(6) == null
                        || rs.getObject(7) == null || rs.getObject(8) == null) {
                    throw new IllegalStateException("Probe needs two stores, one registered device, and an active "
                            + "customer plus an employee with the required inventory, sales, item, transfer, "
                            + "and catalog permissions.");
                }
                return new Fixture(rs.getInt(1), rs.getInt(2), rs.getInt(5), rs.getInt(3),
                        rs.getString(4), (UUID) rs.getObject(6), rs.getInt(7), rs.getInt(8));
            }
        }
    }

    private static int stock(Connection connection, int productId, int locationId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(quantity_on_hand,0) FROM inventory WHERE product_id=? AND location_id=?")) {
            ps.setInt(1, productId); ps.setInt(2, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static LanSalesService.Approval unexpectedApproval(
            String token, String permission, String action, String reason) {
        throw new AssertionError("Probe unexpectedly requested manager approval.");
    }

    private static void expectInventory(String code, Checked operation) throws Exception {
        try { operation.run(); throw new AssertionError("Expected inventory rejection " + code); }
        catch (LanInventoryService.RuleViolation ex) {
            if (!code.equals(ex.code())) throw new AssertionError("Expected " + code + " but got " + ex.code(), ex);
        }
    }

    private static void expectTransfer(String code, Checked operation) throws Exception {
        try { operation.run(); throw new AssertionError("Expected transfer rejection " + code); }
        catch (LanTransferService.RuleViolation ex) {
            if (!code.equals(ex.code())) throw new AssertionError("Expected " + code + " but got " + ex.code(), ex);
        }
    }

    private static void expectProduct(String code, Checked operation) throws Exception {
        try { operation.run(); throw new AssertionError("Expected product rejection " + code); }
        catch (LanProductAdminService.RuleViolation ex) {
            if (!code.equals(ex.code())) throw new AssertionError("Expected " + code + " but got " + ex.code(), ex);
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required.");
        return value;
    }

    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private record Fixture(int userId, int sourceLocationId, int destinationLocationId,
                           int productId, String sku, UUID deviceId, int categoryId, int customerId) { }
}
