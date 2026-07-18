package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;

/**
 * Read-mostly integration probe for the server-side refund boundary.
 * Every connection is rolled back; the probe deliberately exercises only
 * rejected refund requests, so it does not allocate IDs or write business data.
 */
public final class RefundBoundaryProbe {
    private RefundBoundaryProbe() {
    }

    public static void main(String[] args) throws Exception {
        String jdbcUrl = requiredEnvironment("SMARTSTOCK_TEST_JDBC_URL");
        String user = requiredEnvironment("SMARTSTOCK_TEST_DB_USER");
        String password = requiredEnvironment("SMARTSTOCK_TEST_DB_PASSWORD");
        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password)) {
            connection.setAutoCommit(false);
            try {
                Fixture fixture = loadFixture(connection);
                verifyStoreScope(connection, fixture);
                verifyQuantityRevalidation(connection, fixture);
                verifyForeignSaleItemRejected(connection, fixture);
                verifyApprovalIdentity();
                System.out.println("Refund boundary integration checks passed.");
            } finally {
                connection.rollback();
            }
        }
    }

    private static Fixture loadFixture(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT u.user_id, s.sale_id, s.location_id, si.sale_item_id,
                       GREATEST(COALESCE(si.quantity,0)
                         - COALESCE((SELECT SUM(sri.quantity) FROM sale_return_items sri
                                     WHERE sri.sale_item_id=si.sale_item_id),0),0) AS available
                FROM users u
                JOIN role_permissions rp ON rp.role_id=u.role_id
                JOIN permissions p ON p.permission_id=rp.permission_id
                CROSS JOIN LATERAL (
                    SELECT sale_id,location_id FROM sales ORDER BY sale_id LIMIT 1
                ) s
                JOIN sale_items si ON si.sale_id=s.sale_id
                WHERE u.is_active=TRUE AND UPPER(p.permission_key)='PROCESS_RETURNS'
                ORDER BY u.user_id,si.sale_item_id
                LIMIT 1
                """)) {
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException(
                            "Refund probe needs one sale item and one active user with PROCESS_RETURNS.");
                }
                return new Fixture(rs.getInt(1), rs.getInt(2), rs.getInt(3),
                        rs.getInt(4), rs.getInt(5));
            }
        }
    }

    private static void verifyStoreScope(Connection connection, Fixture fixture) throws Exception {
        Map<String, Object> valid = LanRefundService.details(
                connection, fixture.saleId(), fixture.userId(), fixture.locationId());
        if (((Number) valid.get("saleId")).intValue() != fixture.saleId()) {
            throw new AssertionError("Valid same-store sale lookup failed.");
        }
        expectViolation("SALE_NOT_FOUND", () -> LanRefundService.details(
                connection, fixture.saleId(), fixture.userId(), Integer.MAX_VALUE));
    }

    private static void verifyQuantityRevalidation(Connection connection, Fixture fixture) throws Exception {
        int invalidQuantity = fixture.available() == Integer.MAX_VALUE
                ? Integer.MAX_VALUE : fixture.available() + 1;
        JsonObject body = refundBody(fixture.saleId(), fixture.saleItemId(), invalidQuantity);
        expectViolation("RETURN_QUANTITY_CHANGED", () -> LanRefundService.refund(
                connection, body, UUID.randomUUID(), fixture.userId(), "Refund Probe",
                fixture.locationId(), RefundBoundaryProbe::unexpectedApproval));
    }

    private static void verifyForeignSaleItemRejected(Connection connection, Fixture fixture) throws Exception {
        JsonObject body = refundBody(fixture.saleId(), Integer.MAX_VALUE, 1);
        expectViolation("SALE_ITEM_INVALID", () -> LanRefundService.refund(
                connection, body, UUID.randomUUID(), fixture.userId(), "Refund Probe",
                fixture.locationId(), RefundBoundaryProbe::unexpectedApproval));
    }

    private static void verifyApprovalIdentity() {
        String first = RefundApprovalIdentity.build(
                42, new BigDecimal("25"), Map.of(9, 1, 3, 2));
        String reordered = RefundApprovalIdentity.build(
                42, new BigDecimal("25.00"), Map.of(3, 2, 9, 1));
        if (!first.equals(reordered)) {
            throw new AssertionError("Approval identity is not stable across line ordering.");
        }
        if (first.equals(RefundApprovalIdentity.build(
                42, new BigDecimal("25.01"), Map.of(3, 2, 9, 1)))) {
            throw new AssertionError("Approval identity does not bind the refund amount.");
        }
        if (RefundApprovalIdentity.withReason(first, "damaged").equals(
                RefundApprovalIdentity.withReason(first, "other"))) {
            throw new AssertionError("Approval identity does not bind the manager reason.");
        }
    }

    private static JsonObject refundBody(int saleId, int saleItemId, int quantity) {
        JsonObject body = new JsonObject();
        body.addProperty("saleId", saleId);
        body.addProperty("refundMethod", "CARD");
        body.addProperty("reason", "Refund security boundary probe");
        JsonObject line = new JsonObject();
        line.addProperty("saleItemId", saleItemId);
        line.addProperty("quantity", quantity);
        JsonArray lines = new JsonArray();
        lines.add(line);
        body.add("lines", lines);
        return body;
    }

    private static LanRefundService.Approval unexpectedApproval(
            String token, String permission, String action, String reason, String resource) {
        throw new AssertionError("Rejected probe request unexpectedly reached manager approval.");
    }

    private static void expectViolation(String code, CheckedOperation operation) throws Exception {
        try {
            operation.run();
            throw new AssertionError("Expected refund rejection " + code + ".");
        } catch (LanRefundService.RuleViolation ex) {
            if (!code.equals(ex.code())) {
                throw new AssertionError("Expected " + code + " but received " + ex.code() + ".", ex);
            }
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required.");
        }
        return value;
    }

    @FunctionalInterface
    private interface CheckedOperation {
        void run() throws Exception;
    }

    private record Fixture(int userId, int saleId, int locationId, int saleItemId, int available) {
    }
}
