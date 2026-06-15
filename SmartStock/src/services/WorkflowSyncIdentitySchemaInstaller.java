package services;

import data.DatabaseConfig;
import data.DatabaseMode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class WorkflowSyncIdentitySchemaInstaller {
    private static final Object INSTALL_LOCK = new Object();
    private static final Set<String> INSTALLED_DATABASES = ConcurrentHashMap.newKeySet();

    private WorkflowSyncIdentitySchemaInstaller() {
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        if (DatabaseConfig.load().mode() != DatabaseMode.SERVER) {
            return;
        }
        String key = databaseKey(conn);
        if (INSTALLED_DATABASES.contains(key)) {
            return;
        }
        synchronized (INSTALL_LOCK) {
            if (INSTALLED_DATABASES.contains(key)) {
                return;
            }
            runInstaller(conn);
            INSTALLED_DATABASES.add(key);
        }
    }

    public static HealthReport repairAndReport(Connection conn) throws SQLException {
        if (DatabaseConfig.load().mode() != DatabaseMode.SERVER) {
            return new HealthReport(true, true, List.of());
        }
        synchronized (INSTALL_LOCK) {
            runInstaller(conn);
            INSTALLED_DATABASES.add(databaseKey(conn));
        }
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

    private static void runInstaller(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE EXTENSION IF NOT EXISTS pgcrypto");
            ensureSalesReceiptIndex(conn, stmt);
            for (String table : SYNC_UUID_TABLES) {
                if (!tableExists(conn, table)) {
                    continue;
                }
                stmt.executeUpdate("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid()");
                stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS " + table + "_sync_uuid_key ON " + table + "(sync_uuid)");
            }
        }
    }

    private static final List<String> SYNC_UUID_TABLES = List.of(
            "sale_items",
            "sale_returns",
            "sale_return_items",
            "sale_audit_log",
            "inventory_movements",
            "customer_account_transactions",
            "customer_account_payment_allocations",
            "custom_order_lines",
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

    private static void ensureSalesReceiptIndex(Connection conn, Statement stmt) throws SQLException {
        if (!tableExists(conn, "sales")) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT 1
                FROM sales
                WHERE COALESCE(receipt_number, '') <> ''
                GROUP BY receipt_number
                HAVING COUNT(*) > 1
                LIMIT 1
                """);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                stmt.executeUpdate("""
                        CREATE UNIQUE INDEX IF NOT EXISTS sales_receipt_number_uidx
                        ON sales(receipt_number)
                        WHERE COALESCE(receipt_number, '') <> ''
                        """);
            }
        }
    }

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

    private static String databaseKey(Connection conn) throws SQLException {
        String url = conn.getMetaData().getURL();
        String user = conn.getMetaData().getUserName();
        return (url == null ? "unknown" : url) + "|" + (user == null ? "" : user);
    }

    public record HealthReport(boolean healthy, boolean salesReceiptIndexReady, List<String> missingObjects) {
        public String summary() {
            return healthy
                    ? "Workflow sync identity schema is healthy."
                    : "Workflow sync identity schema is missing: " + String.join(", ", missingObjects);
        }
    }
}
