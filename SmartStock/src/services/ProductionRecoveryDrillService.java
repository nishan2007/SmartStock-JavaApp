package services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class ProductionRecoveryDrillService {
    private static final List<String> RECOVERY_TABLES = List.of(
            "locations", "roles", "permissions", "role_permissions", "users", "user_locations",
            "products", "product_barcodes", "inventory", "vendors", "customer_accounts",
            "sales", "sale_items", "sale_returns", "sale_return_items",
            "cash_drawers", "cash_drawer_sessions", "custom_orders", "custom_order_lines",
            "quotations", "quotation_lines", "invoices", "invoice_lines",
            "employee_time_clock", "payroll_payments", "expenses", "bank_transactions"
    );

    private ProductionRecoveryDrillService() {
    }

    public static RecoveryResult run(Connection cleanTarget, int locationId,
                                     CloudSyncManifest storeMirror) throws SQLException {
        String targetName = validateTarget(cleanTarget);
        int restoredRows = CloudRecoveryService.restoreStoreMirror(
                cleanTarget, locationId, storeMirror);
        List<TableComparison> comparisons = comparisons(cleanTarget, storeMirror);
        requireMatchingComparisons(comparisons);
        return new RecoveryResult(targetName, restoredRows, List.copyOf(comparisons));
    }

    public static RecoveryResult run(Connection cleanTarget, CloudSyncManifest productionCloud)
            throws SQLException {
        String targetName = validateTarget(cleanTarget);
        int restoredRows = CloudRecoveryService.restore(cleanTarget, productionCloud);
        List<TableComparison> comparisons = comparisons(cleanTarget, productionCloud);
        requireMatchingComparisons(comparisons);
        return new RecoveryResult(targetName, restoredRows, List.copyOf(comparisons));
    }

    public static RecoveryResult run(Connection cleanTarget, Connection productionCloud)
            throws SQLException {
        String targetName = validateTarget(cleanTarget);

        int restoredRows = ReferenceDataSyncService.pullExistingLocationHistory(
                cleanTarget, productionCloud, null);
        List<TableComparison> comparisons = new ArrayList<>();
        for (String table : RECOVERY_TABLES) {
            if (!tableExists(cleanTarget, table) || !tableExists(productionCloud, table)) {
                throw new SQLException("Recovery schema is missing required table: " + table);
            }
            long targetCount = count(cleanTarget, table);
            long cloudCount = count(productionCloud, table);
            comparisons.add(new TableComparison(table, cloudCount, targetCount,
                    cloudCount == targetCount));
        }
        requireMatchingComparisons(comparisons);
        return new RecoveryResult(targetName, restoredRows, List.copyOf(comparisons));
    }

    private static String validateTarget(Connection cleanTarget) throws SQLException {
        String targetName = cleanTarget.getCatalog();
        if (targetName == null || !targetName.endsWith("_recovery_drill")) {
            throw new SQLException(
                    "Recovery target database name must end with _recovery_drill.");
        }
        requireEmpty(cleanTarget, "users");
        requireEmpty(cleanTarget, "products");
        requireEmpty(cleanTarget, "sales");
        return targetName;
    }

    private static void requireMatchingComparisons(List<TableComparison> comparisons)
            throws SQLException {
        if (comparisons.stream().anyMatch(comparison -> !comparison.matches())) {
            throw new SQLException("Recovery row-count comparison failed for: "
                    + comparisons.stream().filter(comparison -> !comparison.matches())
                    .map(TableComparison::table).toList());
        }
    }

    private static List<TableComparison> comparisons(Connection cleanTarget,
                                                       CloudSyncManifest cloud)
            throws SQLException {
        List<TableComparison> comparisons = new ArrayList<>();
        for (String table : RECOVERY_TABLES) {
            if (!tableExists(cleanTarget, table) || !cloud.hasTable(table)) {
                throw new SQLException("Recovery schema is missing required table: " + table);
            }
            long targetCount = count(cleanTarget, table);
            long cloudCount = cloud.rowCount(table);
            comparisons.add(new TableComparison(table, cloudCount, targetCount,
                    cloudCount == targetCount));
        }
        return comparisons;
    }

    private static void requireEmpty(Connection connection, String table) throws SQLException {
        if (!tableExists(connection, table)) {
            throw new SQLException("Recovery target schema is missing table: " + table);
        }
        if (count(connection, table) != 0) {
            throw new SQLException("Recovery target is not empty; found rows in " + table + ".");
        }
    }

    private static long count(Connection connection, String table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM \"" + table + "\"");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    private static boolean tableExists(Connection connection, String table)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT EXISTS (
                  SELECT 1 FROM information_schema.tables
                  WHERE table_schema='public' AND table_name=?
                )
                """)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    public record RecoveryResult(String targetDatabase, int restoredRows,
                                 List<TableComparison> comparisons) {
    }

    public record TableComparison(String table, long cloudRows, long restoredRows,
                                  boolean matches) {
    }
}
