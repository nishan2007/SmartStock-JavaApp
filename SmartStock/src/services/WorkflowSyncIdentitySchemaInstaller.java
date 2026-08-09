package services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class WorkflowSyncIdentitySchemaInstaller {
    private WorkflowSyncIdentitySchemaInstaller() {
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        SchemaContractService.requireLocalReady(conn);
    }

    public static HealthReport repairAndReport(Connection conn) throws SQLException {
        SchemaContractService.requireLocalReady(conn);
        return healthReport(conn);
    }

    /**
     * Read-only preflight for sync. Schema changes belong to provisioning or a
     * migration, never to a live multi-device sync transaction.
     */
    public static HealthReport healthReport(Connection conn) throws SQLException {
        List<String> missing = new ArrayList<>();
        boolean receiptIndex = !tableExists(conn, "sales") || hasSalesReceiptIndex(conn);
        if (!receiptIndex) {
            missing.add("sales_receipt_number_uidx");
        }
        for (String table : SYNC_UUID_TABLES) {
            if (!tableExists(conn, table)) {
                continue;
            }
            if (!hasColumn(conn, table, "sync_uuid")) {
                missing.add(table + ".sync_uuid");
            }
            if (!hasUniqueSyncUuidIndex(conn, table)) {
                missing.add(table + "_sync_uuid_key");
            }
        }
        return new HealthReport(missing.isEmpty(), receiptIndex, List.copyOf(missing));
    }

    private static final List<String> SYNC_UUID_TABLES = List.of(
            "sales",
            "sale_items",
            "sale_returns",
            "sale_return_items",
            "sale_audit_log",
            "inventory_movements",
            "customer_accounts",
            "customer_account_transactions",
            "customer_account_payment_allocations",
            "custom_order_lines",
            "custom_orders",
            "custom_order_line_print_addons",
            "custom_order_payments",
            "custom_order_inventory_reservations",
            "custom_order_status_history",
            "custom_order_line_deliveries",
            "custom_order_line_production_history",
            "custom_order_line_returns",
            "custom_order_item_movements",
            "custom_order_audit_log"
    );

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND table_type = 'BASE TABLE'
                LIMIT 1
                """)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean hasColumn(Connection conn, String table, String column) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
                LIMIT 1
                """)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean hasUniqueSyncUuidIndex(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT 1
                FROM pg_index i
                JOIN pg_class rel ON rel.oid = i.indrelid
                JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
                WHERE nsp.nspname = 'public'
                  AND rel.relname = ?
                  AND i.indisunique
                  AND pg_get_indexdef(i.indexrelid) LIKE '%(sync_uuid)%'
                LIMIT 1
                """)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean hasSalesReceiptIndex(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT 1
                FROM pg_class idx
                JOIN pg_namespace nsp ON nsp.oid = idx.relnamespace
                WHERE nsp.nspname = 'public'
                  AND idx.relname = 'sales_receipt_number_uidx'
                LIMIT 1
                """);
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    public record HealthReport(boolean healthy, boolean salesReceiptIndexReady, List<String> missingObjects) {
        public String summary() {
            return healthy
                    ? "Workflow sync identity schema is healthy."
                    : "Workflow sync identity schema is missing: " + String.join(", ", missingObjects);
        }
    }
}
