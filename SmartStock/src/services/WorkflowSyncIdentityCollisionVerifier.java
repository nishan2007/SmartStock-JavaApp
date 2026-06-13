package services;

import data.DB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class WorkflowSyncIdentityCollisionVerifier {
    private WorkflowSyncIdentityCollisionVerifier() {
    }

    public static VerificationResult run(Connection local, Connection cloud) throws SQLException {
        String marker = "CODEX-SYNC-" + Instant.now().toEpochMilli();
        String localReceipt = marker + "-SALE-LOCAL";
        String cloudReceipt = marker + "-SALE-CLOUD";
        String localOrderNumber = marker + "-CO-LOCAL";
        String cloudOrderNumber = marker + "-CO-CLOUD";
        String customerAccount = marker + "-CUSTOMER";
        List<String> checks = new ArrayList<>();
        try {
            WorkflowSyncIdentitySchemaInstaller.repairAndReport(local);
            WorkflowSyncIdentitySchemaInstaller.repairAndReport(cloud);

            int collisionSaleId = nextHighId(local, cloud, "sales", "sale_id");
            long collisionCustomOrderId = nextHighId(local, cloud, "custom_orders", "custom_order_id");
            int customerId = nextHighId(local, cloud, "customer_accounts", "customer_id");
            int locationId = nextHighId(local, cloud, "locations", "location_id");
            int productId = nextHighId(local, cloud, "products", "product_id");
            String storeCode = nextStoreCode(local, cloud);

            insertLocation(local, locationId, storeCode, marker);
            insertLocation(cloud, locationId, storeCode, marker);
            insertProduct(local, productId, marker);
            insertProduct(cloud, productId, marker);
            insertCustomer(local, customerId, customerAccount, marker);
            insertCustomer(cloud, customerId, customerAccount, marker);
            insertSale(cloud, collisionSaleId, cloudReceipt, locationId, marker);
            insertSale(local, collisionSaleId, localReceipt, locationId, marker);
            insertSaleItem(local, collisionSaleId, productId, marker);
            insertCustomOrder(cloud, collisionCustomOrderId, cloudOrderNumber, customerId, locationId, marker);
            insertCustomOrder(local, collisionCustomOrderId, localOrderNumber, customerId, locationId, marker);
            insertCustomOrderLine(local, collisionCustomOrderId, marker);

            int changed = ReferenceDataSyncService.pushLocalOperationalChanges(local, cloud);

            long remappedLocalSaleId = requireId(local, "sales", "sale_id", "receipt_number", localReceipt);
            long uploadedCloudSaleId = requireId(cloud, "sales", "sale_id", "receipt_number", localReceipt);
            long blockerCloudSaleId = requireId(cloud, "sales", "sale_id", "receipt_number", cloudReceipt);
            assertTrue(remappedLocalSaleId == uploadedCloudSaleId,
                    "local sale id remapped to uploaded cloud sale id", checks);
            assertTrue(blockerCloudSaleId == collisionSaleId,
                    "cloud blocker sale kept original id", checks);
            assertTrue(remappedLocalSaleId != collisionSaleId,
                    "local sale moved away from colliding id", checks);
            assertTrue(count(local, "sale_items", "sale_id", remappedLocalSaleId) == 1,
                    "local sale item followed remapped sale id", checks);
            assertTrue(count(cloud, "sale_items", "sale_id", uploadedCloudSaleId) == 1,
                    "cloud sale item uploaded under remapped sale id", checks);

            long remappedLocalOrderId = requireId(local, "custom_orders", "custom_order_id", "order_number", localOrderNumber);
            long uploadedCloudOrderId = requireId(cloud, "custom_orders", "custom_order_id", "order_number", localOrderNumber);
            long blockerCloudOrderId = requireId(cloud, "custom_orders", "custom_order_id", "order_number", cloudOrderNumber);
            assertTrue(remappedLocalOrderId == uploadedCloudOrderId,
                    "local custom order id remapped to uploaded cloud custom order id", checks);
            assertTrue(blockerCloudOrderId == collisionCustomOrderId,
                    "cloud blocker custom order kept original id", checks);
            assertTrue(remappedLocalOrderId != collisionCustomOrderId,
                    "local custom order moved away from colliding id", checks);
            assertTrue(count(local, "custom_order_lines", "custom_order_id", remappedLocalOrderId) == 1,
                    "local custom order line followed remapped order id", checks);
            assertTrue(count(cloud, "custom_order_lines", "custom_order_id", uploadedCloudOrderId) == 1,
                    "cloud custom order line uploaded under remapped order id", checks);

            SyncAuditService.record(local,
                    "WORKFLOW_SYNC_IDENTITY_COLLISION_TEST",
                    "sales/custom_orders",
                    collisionSaleId,
                    uploadedCloudSaleId,
                    uploadedCloudOrderId,
                    marker,
                    "PASSED",
                    java.util.Map.of("changed_rows", changed, "checks", String.join("; ", checks)));
            return new VerificationResult(true, marker, changed, List.copyOf(checks));
        } finally {
            cleanup(local, marker);
            cleanup(cloud, marker);
        }
    }

    public static VerificationResult runWithConfiguredDatabases() throws SQLException {
        try (Connection local = DB.getConnection(); Connection cloud = DB.getCloudConnection()) {
            return run(local, cloud);
        }
    }

    private static int nextHighId(Connection local, Connection cloud, String table, String idColumn) throws SQLException {
        long max = Math.max(maxId(local, table, idColumn), maxId(cloud, table, idColumn));
        return Math.toIntExact(max + 50_000L);
    }

    private static long maxId(Connection conn, String table, String idColumn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(" + quote(idColumn) + "), 0) FROM " + quote(table))) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static void insertCustomer(Connection conn, int customerId, String accountNumber, String marker) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO customer_accounts (customer_id, account_number, name, phone, email, is_business, is_active)
                VALUES (?, ?, ?, '', '', TRUE, TRUE)
                ON CONFLICT (customer_id) DO UPDATE SET account_number = EXCLUDED.account_number,
                    name = EXCLUDED.name, is_business = TRUE, is_active = TRUE
                """)) {
            ps.setInt(1, customerId);
            ps.setString(2, accountNumber);
            ps.setString(3, marker + " Customer");
            ps.executeUpdate();
        }
    }

    private static void insertProduct(Connection conn, int productId, String marker) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO products (product_id, name, sku, barcode, description, cost_price, price, product_type)
                VALUES (?, ?, ?, ?, ?, 0.00, 1.00, 'SERVICE')
                ON CONFLICT (product_id) DO UPDATE SET name = EXCLUDED.name,
                    sku = EXCLUDED.sku, barcode = EXCLUDED.barcode, product_type = EXCLUDED.product_type,
                    updated_at = CURRENT_TIMESTAMP
                """)) {
            ps.setInt(1, productId);
            ps.setString(2, marker + " Product");
            ps.setString(3, marker + "-SKU");
            ps.setString(4, marker + "-BARCODE");
            ps.setString(5, marker);
            ps.executeUpdate();
        }
    }

    private static String nextStoreCode(Connection local, Connection cloud) throws SQLException {
        for (int code = 9000; code >= 1000; code--) {
            String candidate = String.format("%04d", code);
            if (!storeCodeExists(local, candidate) && !storeCodeExists(cloud, candidate)) {
                return candidate;
            }
        }
        throw new SQLException("No free 4-digit receipt store code available for workflow sync verifier.");
    }

    private static boolean storeCodeExists(Connection conn, String code) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT 1 FROM locations WHERE receipt_store_code = ? LIMIT 1
                """)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void insertLocation(Connection conn, int locationId, String storeCode, String marker) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO locations (location_id, name, address, receipt_store_code)
                VALUES (?, ?, '', ?)
                ON CONFLICT (location_id) DO UPDATE SET name = EXCLUDED.name
                """)) {
            ps.setInt(1, locationId);
            ps.setString(2, marker + " Location");
            ps.setString(3, storeCode);
            ps.executeUpdate();
        }
    }

    private static void insertSale(Connection conn, int saleId, String receiptNumber, int locationId, String marker) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sales (sale_id, receipt_number, total_amount, subtotal_amount, amount_paid, status,
                                   payment_method, payment_status, user_name, transaction_source, device_id, location_id)
                VALUES (?, ?, 1.00, 1.00, 1.00, 'COMPLETED', 'CASH', 'PAID', 'Codex Verifier',
                        'WORKFLOW_SYNC_IDENTITY_TEST', ?, ?)
                """)) {
            ps.setInt(1, saleId);
            ps.setString(2, receiptNumber);
            ps.setString(3, marker);
            ps.setInt(4, locationId);
            ps.executeUpdate();
        }
    }

    private static void insertSaleItem(Connection conn, int saleId, int productId, String marker) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sale_items (sale_id, product_id, quantity, unit_price, original_unit_price, product_type, price_override_reason)
                VALUES (?, ?, 1, 1.00, 1.00, 'SERVICE', ?)
                """)) {
            ps.setInt(1, saleId);
            ps.setInt(2, productId);
            ps.setString(3, marker);
            ps.executeUpdate();
        }
    }

    private static void insertCustomOrder(Connection conn, long customOrderId, String orderNumber,
                                          int customerId, int locationId, String marker) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO custom_orders (
                    custom_order_id, order_number, customer_id, customer_name, customer_phone, status,
                    order_notes, total_amount, amount_paid, balance_due, payment_status,
                    taken_by_name, location_id, location_name, device_id, device_name
                )
                VALUES (?, ?, ?, ?, '', 'NEW', ?, 1.00, 0.00, 1.00, 'UNPAID',
                        'Codex Verifier', ?, 'Verifier', ?, 'Verifier')
                """)) {
            ps.setLong(1, customOrderId);
            ps.setString(2, orderNumber);
            ps.setInt(3, customerId);
            ps.setString(4, marker + " Customer");
            ps.setString(5, marker);
            ps.setInt(6, locationId);
            ps.setString(7, marker);
            ps.executeUpdate();
        }
    }

    private static void insertCustomOrderLine(Connection conn, long customOrderId, String marker) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO custom_order_lines (
                    custom_order_id, item_name, pricing_type, unit_price, line_total,
                    customization_details, sort_order
                )
                VALUES (?, ?, 'FIXED', 1.00, 1.00, ?, 0)
                """)) {
            ps.setLong(1, customOrderId);
            ps.setString(2, marker + " Item");
            ps.setString(3, marker);
            ps.executeUpdate();
        }
    }

    private static long requireId(Connection conn, String table, String idColumn, String matchColumn, String matchValue) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT " + quote(idColumn)
                + " FROM " + quote(table) + " WHERE " + quote(matchColumn) + " = ?")) {
            ps.setString(1, matchValue);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(idColumn);
                }
            }
        }
        throw new SQLException("Missing verifier row " + table + "." + matchColumn + "=" + matchValue);
    }

    private static long count(Connection conn, String table, String column, long value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + quote(table)
                + " WHERE " + quote(column) + " = ?")) {
            ps.setLong(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static void assertTrue(boolean condition, String message, List<String> checks) throws SQLException {
        if (!condition) {
            throw new SQLException("Workflow sync identity collision verification failed: " + message);
        }
        checks.add(message);
    }

    private static void cleanup(Connection conn, String marker) {
        boolean salesTriggersDisabled = false;
        try (Statement stmt = conn.createStatement()) {
            try {
                stmt.executeUpdate("ALTER TABLE sale_items DISABLE TRIGGER USER");
                stmt.executeUpdate("ALTER TABLE sales DISABLE TRIGGER USER");
                salesTriggersDisabled = true;
            } catch (SQLException ignored) {
                salesTriggersDisabled = false;
            }
            stmt.executeUpdate("DELETE FROM custom_order_lines WHERE customization_details LIKE '" + marker + "%'");
            stmt.executeUpdate("DELETE FROM custom_orders WHERE order_notes LIKE '" + marker + "%' OR device_id LIKE '" + marker + "%'");
            stmt.executeUpdate("DELETE FROM sale_audit_log WHERE location_id IN (SELECT location_id FROM locations WHERE name LIKE '" + marker + "%')");
            stmt.executeUpdate("DELETE FROM sale_audit_log WHERE sale_id IN (SELECT sale_id FROM sales WHERE receipt_number LIKE '" + marker + "%' OR device_id LIKE '" + marker + "%')");
            stmt.executeUpdate("DELETE FROM sale_items WHERE price_override_reason LIKE '" + marker + "%'");
            stmt.executeUpdate("DELETE FROM sales WHERE receipt_number LIKE '" + marker + "%' OR device_id LIKE '" + marker + "%'");
            stmt.executeUpdate("DELETE FROM customer_accounts WHERE account_number LIKE '" + marker + "%'");
            stmt.executeUpdate("DELETE FROM locations WHERE name LIKE '" + marker + "%'");
            stmt.executeUpdate("DELETE FROM products WHERE sku LIKE '" + marker + "%' OR barcode LIKE '" + marker + "%'");
            if (salesTriggersDisabled) {
                stmt.executeUpdate("ALTER TABLE sales ENABLE TRIGGER USER");
                stmt.executeUpdate("ALTER TABLE sale_items ENABLE TRIGGER USER");
            }
        } catch (SQLException ex) {
            System.err.println("Workflow sync verifier cleanup failed: " + ex.getMessage());
            if (salesTriggersDisabled) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE sales ENABLE TRIGGER USER");
                    stmt.executeUpdate("ALTER TABLE sale_items ENABLE TRIGGER USER");
                } catch (SQLException ignored) {
                    // Best effort cleanup path.
                }
            }
        }
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    public record VerificationResult(boolean passed, String marker, int changedRows, List<String> checks) {
        public String summary() {
            return "Workflow sync identity collision verification passed (" + changedRows + " changed row(s)).";
        }
    }
}
