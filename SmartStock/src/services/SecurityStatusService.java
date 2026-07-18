package services;

import data.DatabaseConfig;
import data.DatabaseMode;
import utils.SecureCredentialStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SecurityStatusService {
    private SecurityStatusService() {
    }

    public static Report inspect(Connection conn) throws SQLException {
        DatabaseConfig config = DatabaseConfig.load();
        List<String> warnings = new ArrayList<>();
        boolean tls = connectionUsesTls(conn);
        if (config.mode() == DatabaseMode.CLIENT && !tls) warnings.add("This register's database connection is not encrypted.");

        int pending = count(conn, "SELECT count(*) FROM devices WHERE credential_status IN ('PENDING', 'ROTATION_PENDING') AND is_blocked = FALSE");
        int issued = count(conn, "SELECT count(*) FROM devices WHERE credential_status = 'ISSUED' AND is_blocked = FALSE");
        int claimed = count(conn, "SELECT count(*) FROM devices WHERE credential_status = 'CLAIMED' AND is_blocked = FALSE");
        int blocked = count(conn, "SELECT count(*) FROM devices WHERE is_blocked = TRUE");
        int broadPolicies = count(conn, """
                SELECT count(*) FROM pg_policies
                WHERE schemaname = 'public'
                  AND 'authenticated' = ANY(roles)
                  AND cmd <> 'SELECT'
                  AND trim(lower(COALESCE(qual, ''))) IN ('true', '(true)')
                  AND trim(lower(COALESCE(with_check, qual, ''))) IN ('true', '(true)')
                """);
        int exposedWithoutRls = count(conn, """
                SELECT count(DISTINCT c.oid)
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                JOIN information_schema.role_table_grants g
                  ON g.table_schema = n.nspname AND g.table_name = c.relname
                WHERE n.nspname = 'public' AND c.relkind = 'r' AND c.relrowsecurity = FALSE
                  AND g.grantee IN ('anon', 'authenticated')
                """);
        int publicDefiners = count(conn, """
                SELECT count(*)
                FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE n.nspname = 'public' AND p.prosecdef
                  AND has_function_privilege('public', p.oid, 'EXECUTE')
                """);
        if (pending > 0 || issued > 0) warnings.add((pending + issued) + " approved or pending device credential(s) have not been claimed yet.");
        if (broadPolicies > 0) warnings.add(broadPolicies + " unrestricted authenticated RLS policy/policies remain.");
        if (exposedWithoutRls > 0) warnings.add(exposedWithoutRls + " Data API table(s) are granted without RLS.");
        if (publicDefiners > 0) warnings.add(publicDefiners + " SECURITY DEFINER function(s) remain executable by PUBLIC.");

        String pairingPhrase = null;
        String lanFingerprint = null;
        if (config.mode() == DatabaseMode.SERVER) {
            try {
                LanTlsIdentity identity = LanTlsIdentity.loadOrCreate();
                pairingPhrase = identity.currentPairingPhrase();
                lanFingerprint = identity.fingerprint();
            } catch (Exception ex) {
                warnings.add("The LAN service TLS identity is unavailable: " + ex.getMessage());
            }
        }
        return new Report(tls, SecureCredentialStore.backendLabel(), pending, issued, claimed, blocked,
                broadPolicies, exposedWithoutRls, publicDefiners, latestAudit(conn), latestBackup(),
                pairingPhrase, lanFingerprint, warnings);
    }

    private static boolean connectionUsesTls(Connection conn) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COALESCE((SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()), FALSE)")) {
            return rs.next() && rs.getBoolean(1);
        } catch (SQLException ex) {
            return false;
        }
    }

    private static int count(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException ex) {
            if ("42P01".equals(ex.getSQLState()) || "42703".equals(ex.getSQLState())) return 0;
            throw ex;
        }
    }

    private static Instant latestAudit(Connection conn) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT max(created_at) FROM security_audit_events")) {
            if (rs.next() && rs.getTimestamp(1) != null) return rs.getTimestamp(1).toInstant();
        } catch (SQLException ignored) {
        }
        return null;
    }

    private static Instant latestBackup() {
        Path directory = Path.of(System.getProperty("user.home"), ".smartstock", "backups");
        if (!Files.isDirectory(directory)) return null;
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).map(path -> {
                try { return Files.getLastModifiedTime(path).toInstant(); }
                catch (Exception ex) { return Instant.EPOCH; }
            }).max(Instant::compareTo).filter(value -> !Instant.EPOCH.equals(value)).orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    public record Report(boolean tls, String credentialStore, int pendingCredentials, int issuedCredentials,
                         int claimedCredentials, int blockedDevices, int broadAuthenticatedPolicies,
                         int exposedTablesWithoutRls, int publicSecurityDefiners, Instant latestAudit,
                         Instant latestBackup, String pairingPhrase, String lanCertificateFingerprint,
                         List<String> warnings) {
        public boolean healthy() { return warnings.isEmpty(); }
    }
}
