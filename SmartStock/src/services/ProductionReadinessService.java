package services;

import com.google.gson.Gson;
import data.DatabaseConfig;
import data.DatabaseMode;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public final class ProductionReadinessService {
    private static final Gson GSON = new Gson();
    private static final List<String> REQUIRED_RECOVERY_TABLES = List.of(
            "locations", "roles", "permissions", "users", "user_locations",
            "products", "product_barcodes", "inventory", "vendors", "image_cloud_configuration",
            "customer_accounts", "sales", "sale_items", "sale_returns",
            "cash_drawers", "cash_drawer_sessions", "custom_orders",
            "quotations", "invoices", "employee_time_clock", "payroll_payments",
            "expenses", "bank_transactions"
    );
    private static final List<String> REQUIRED_CLOUD_CONTROL_TABLES = List.of(
            "sync_outbox", "sync_applied_events", "smartstock_store_rows",
            "smartstock_store_mirror_status", "smartstock_store_snapshot_generations",
            "smartstock_store_snapshot_rows", "store_sync_status",
            "remote_admin_commands", "store_server_instances"
    );

    private ProductionReadinessService() {
    }

    public static List<Check> configurationChecks(DatabaseConfig database,
                                                   SupabaseProjectConfig project,
                                                   boolean serverCredentialConfigured) {
        List<Check> checks = new ArrayList<>();
        checks.add(check("environment", project.isProduction(),
                "SMARTSTOCK_ENVIRONMENT must be production."));
        checks.add(check("server-mode", database.mode() == DatabaseMode.SERVER,
                "The store computer must use SERVER database mode."));
        checks.add(check("local-database", database.hasPrimaryConnection(),
                "The local PostgreSQL connection is incomplete."));
        checks.add(check("local-loopback", isLoopbackJdbc(database.jdbcUrl()),
                "The live store database must be bound through a loopback JDBC URL."));
        checks.add(check("cloud-api", project.url() != null && project.publishableKey() != null,
                "The production Supabase URL and publishable key are incomplete."));
        checks.add(check("location", database.locationId() != null,
                "A production store location ID is required."));
        checks.add(check("server-cloud-credential", serverCredentialConfigured,
                "The production server service-role credential is not installed."));
        return List.copyOf(checks);
    }

    public static List<Check> databaseChecks(Connection local, CloudSyncManifest cloudSchema,
                                              CloudSyncManifest storeMirror, int locationId)
            throws SQLException {
        List<Check> checks = new ArrayList<>();
        SchemaContractService.Readiness localSchema =
                SchemaContractService.validateLocal(local);
        checks.add(check("local-schema-v1", localSchema.ready(),
                localSchema.message()));
        checks.add(check("cloud-schema-v1",
                cloudSchema != null && cloudSchema.schemaReady()
                        && Integer.valueOf(SchemaContractService.BASELINE_VERSION)
                        .equals(cloudSchema.schemaVersion()),
                "Cloud schema v1 is unavailable or its fingerprint is invalid; local POS and LAN may continue offline but sync/recovery must remain disabled."));
        for (String table : REQUIRED_CLOUD_CONTROL_TABLES) {
            boolean cloudExists = cloudSchema != null && cloudSchema.hasTable(table);
            checks.add(check("cloud-control-" + table, cloudExists,
                    "Required cloud control table is missing: " + table));
        }
        checks.add(check("verified-snapshot-generation",
                storeMirror != null && storeMirror.hasVerifiedSnapshot(),
                "The store does not have a completed, immutable recovery generation."));
        for (String table : REQUIRED_RECOVERY_TABLES) {
            boolean localExists = tableExists(local, table);
            boolean mirrored = storeMirror != null && storeMirror.hasTable(table);
            checks.add(check("recovery-schema-" + table, localExists && mirrored,
                    "Required recovery table is missing locally or from the store snapshot: "
                            + table));
            long localRows = localExists
                    ? CloudRowMirrorService.countScopedRows(local, locationId, table) : -1;
            long mirrorRows = storeMirror == null ? -1 : storeMirror.rowCount(table);
            checks.add(check("materialized-" + table, localRows >= 0 && localRows == mirrorRows,
                    "Cloud mirror is incomplete for " + table + ": local="
                            + localRows + ", cloud=" + mirrorRows));
        }
        checks.addAll(localOperationalChecks(local));
        return List.copyOf(checks);
    }

    private static List<Check> localOperationalChecks(Connection local) throws SQLException {
        List<Check> checks = new ArrayList<>();
        if (tableExists(local, "sync_outbox")) {
            checks.add(check("pending-sync", count(local,
                            "SELECT COUNT(*) FROM sync_outbox WHERE status IN ('PENDING','FAILED')") == 0,
                    "Local sync contains pending or failed events."));
        }
        if (tableExists(local, "sync_conflicts")) {
            checks.add(check("open-conflicts", count(local,
                            "SELECT COUNT(*) FROM sync_conflicts WHERE status='OPEN'") == 0,
                    "Local sync contains unresolved conflicts."));
        }
        if (tableExists(local, "users")) {
            checks.add(check("three-linked-users", count(local,
                            "SELECT COUNT(*) FROM users WHERE auth_user_id IS NOT NULL AND is_active=TRUE") >= 3,
                    "At least three active production users must be linked to Supabase Auth."));
            checks.add(check("badge-uniqueness", count(local, """
                        SELECT COUNT(*) FROM (
                          SELECT UPPER(REGEXP_REPLACE(badge_id,'[^a-zA-Z0-9]','','g'))
                          FROM users WHERE badge_id IS NOT NULL
                          GROUP BY 1 HAVING COUNT(*) > 1
                        ) duplicates
                        """) == 0,
                    "Production contains duplicate normalized badge IDs."));
        }
        return checks;
    }

    public static Check recoveryEvidenceCheck(Path evidencePath) {
        if (evidencePath == null || !Files.isRegularFile(evidencePath)) {
            return new Check("recovery-drill", false,
                    "A successful clean-database recovery evidence file is required.");
        }
        try (Reader reader = Files.newBufferedReader(evidencePath)) {
            RecoveryEvidence evidence = GSON.fromJson(reader, RecoveryEvidence.class);
            boolean valid = evidence != null
                    && "PASS".equals(evidence.status())
                    && evidence.verifiedAt() != null
                    && evidence.operator() != null && !evidence.operator().isBlank()
                    && evidence.targetDatabase() != null
                    && evidence.targetDatabase().endsWith("_recovery_drill")
                    && Instant.parse(evidence.verifiedAt())
                    .isAfter(Instant.now().minus(30, ChronoUnit.DAYS));
            return check("recovery-drill", valid,
                    "Recovery evidence must be PASS, target *_recovery_drill, and no older than 30 days.");
        } catch (Exception ex) {
            return new Check("recovery-drill", false,
                    "Recovery evidence is invalid: " + ex.getMessage());
        }
    }

    public static boolean allPassed(List<Check> checks) {
        return checks != null && checks.stream().allMatch(Check::passed);
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

    private static long count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    static boolean isLoopbackJdbc(String jdbcUrl) {
        if (jdbcUrl == null) return false;
        String value = jdbcUrl.toLowerCase();
        return value.startsWith("jdbc:postgresql://127.0.0.1:")
                || value.startsWith("jdbc:postgresql://localhost:")
                || value.startsWith("jdbc:postgresql://[::1]:");
    }

    private static Check check(String name, boolean passed, String failure) {
        return new Check(name, passed, passed ? "Passed." : failure);
    }

    public record Check(String name, boolean passed, String message) {
    }

    public record RecoveryEvidence(String status, String verifiedAt, String operator,
                                   String targetDatabase, int comparedTables) {
    }
}
