package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Rollback-only integration checks for the POS, held-cart, and history LAN boundary. */
public final class SalesBoundaryProbe {
    private SalesBoundaryProbe() { }

    public static void main(String[] args) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                required("SMARTSTOCK_TEST_JDBC_URL"), required("SMARTSTOCK_TEST_DB_USER"),
                required("SMARTSTOCK_TEST_DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            try {
                Fixture fixture = fixture(connection);
                verifyReadBoundary(connection, fixture);
                verifyHeldCartTransaction(connection, fixture);
                verifyRejectedRequests(connection, fixture);
                System.out.println("Sales, held-cart, and history boundary integration checks passed.");
            } finally {
                connection.rollback();
            }
        }
    }

    private static void verifyReadBoundary(Connection connection, Fixture f) throws Exception {
        LanHeldCartService.settings(connection, f.userId(), f.locationId());
        LanHeldCartService.list(connection, f.userId(), f.locationId());
        JsonObject history = new JsonObject();
        history.addProperty("search", ""); history.addProperty("fromDate", ""); history.addProperty("toDate", "");
        LanSalesHistoryService.history(connection, history, f.userId(), f.locationId());
        Map<String, Object> details = LanSalesHistoryService.details(
                connection, f.saleId(), f.userId(), f.locationId());
        if (((Number) details.get("saleId")).intValue() != f.saleId())
            throw new AssertionError("Same-store sale details were not returned.");
        expectHistory("SALE_NOT_FOUND", () -> LanSalesHistoryService.details(
                connection, f.saleId(), f.userId(), Integer.MAX_VALUE));
    }

    private static void verifyHeldCartTransaction(Connection connection, Fixture f) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("holdName", "Rollback-only LAN boundary probe");
        body.addProperty("paymentMethod", "CARD");
        body.addProperty("saleDiscountPercent", 0);
        JsonObject line = new JsonObject();
        line.addProperty("productId", f.productId()); line.addProperty("quantity", 1);
        line.addProperty("unitPrice", f.productPrice()); line.addProperty("discountPercent", 0);
        JsonArray lines = new JsonArray(); lines.add(line); body.add("lines", lines);
        Map<String, Object> created = LanHeldCartService.create(connection, body, f.deviceId(),
                f.userId(), "Boundary Probe", f.locationId(), SalesBoundaryProbe::unexpectedApproval);
        int heldCartId = ((Number) created.get("heldCartId")).intValue();
        boolean listed = LanHeldCartService.list(connection, f.userId(), f.locationId()).stream()
                .anyMatch(row -> ((Number) row.get("heldCartId")).intValue() == heldCartId);
        if (!listed) throw new AssertionError("Created held cart was not visible in its store.");
        Map<String, Object> resumed = LanHeldCartService.resume(connection, heldCartId, f.deviceId(),
                f.userId(), "Boundary Probe", f.locationId());
        if (((List<?>) resumed.get("items")).size() != 1)
            throw new AssertionError("Held-cart resume did not return its item.");
    }

    private static void verifyRejectedRequests(Connection connection, Fixture f) throws Exception {
        expectHeld("HELD_CART_NOT_FOUND", () -> LanHeldCartService.resume(connection,
                Integer.MAX_VALUE, f.deviceId(), f.userId(), "Boundary Probe", f.locationId()));
        JsonObject invalid = new JsonObject(); invalid.addProperty("holdName", "Invalid");
        JsonObject line = new JsonObject(); line.addProperty("productId", f.productId()); line.addProperty("quantity", 0);
        JsonArray lines = new JsonArray(); lines.add(line); invalid.add("lines", lines);
        expectHeld("VALIDATION_ERROR", () -> LanHeldCartService.create(connection, invalid, f.deviceId(),
                f.userId(), "Boundary Probe", f.locationId(), SalesBoundaryProbe::unexpectedApproval));
    }

    private static Fixture fixture(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT u.user_id,ul.location_id,s.sale_id,p.product_id,COALESCE(p.price,0),
                       (SELECT device_id FROM devices ORDER BY last_seen DESC NULLS LAST LIMIT 1)
                FROM users u JOIN user_locations ul ON ul.user_id=u.user_id
                JOIN sales s ON s.location_id=ul.location_id
                CROSS JOIN LATERAL (SELECT product_id,price FROM products ORDER BY product_id LIMIT 1) p
                WHERE u.is_active=TRUE AND EXISTS (
                  SELECT 1 FROM role_permissions rp JOIN permissions x ON x.permission_id=rp.permission_id
                  WHERE rp.role_id=u.role_id AND UPPER(x.permission_key)='MAKE_SALE')
                  AND EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions x ON x.permission_id=rp.permission_id
                  WHERE rp.role_id=u.role_id AND UPPER(x.permission_key)='VIEW_SALES')
                ORDER BY u.user_id,s.sale_id LIMIT 1
                """)) {
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException(
                        "Probe needs a product, sale, device, and active employee with MAKE_SALE and VIEW_SALES.");
                UUID deviceId = (UUID) rs.getObject(6);
                if (deviceId == null) throw new IllegalStateException("Probe needs one registered device.");
                return new Fixture(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                        rs.getBigDecimal(5), deviceId);
            }
        }
    }

    private static LanSalesService.Approval unexpectedApproval(
            String token, String permission, String action, String reason) {
        throw new AssertionError("Probe unexpectedly requested manager approval.");
    }

    private static void expectHeld(String code, Checked operation) throws Exception {
        try { operation.run(); throw new AssertionError("Expected held-cart rejection " + code); }
        catch (LanHeldCartService.RuleViolation ex) {
            if (!code.equals(ex.code())) throw new AssertionError("Expected " + code + " but got " + ex.code(), ex);
        }
    }

    private static void expectHistory(String code, Checked operation) throws Exception {
        try { operation.run(); throw new AssertionError("Expected history rejection " + code); }
        catch (LanSalesHistoryService.RuleViolation ex) {
            if (!code.equals(ex.code())) throw new AssertionError("Expected " + code + " but got " + ex.code(), ex);
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required.");
        return value;
    }

    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private record Fixture(int userId, int locationId, int saleId, int productId,
                           BigDecimal productPrice, UUID deviceId) { }
}
