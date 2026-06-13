package services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SupabaseSecurityHardening {
    private static final Set<String> HARDENED_TABLES = ConcurrentHashMap.newKeySet();
    private static final long HARDENING_LOCK_KEY = 7_340_210_002L;

    private SupabaseSecurityHardening() {
    }

    public static void protectInternalTable(Connection conn, String tableName) throws SQLException {
        String cacheKey = databaseCacheKey(conn) + "|" + tableName;
        if (HARDENED_TABLES.contains(cacheKey)) {
            return;
        }
        boolean locked = false;
        try (Statement lock = conn.createStatement();
             ResultSet rs = lock.executeQuery("SELECT pg_try_advisory_lock(" + HARDENING_LOCK_KEY + ")")) {
            locked = rs.next() && rs.getBoolean(1);
        }
        if (!locked) {
            return;
        }
        try {
            if (HARDENED_TABLES.contains(cacheKey)) {
                return;
            }
            protectInternalTableUnlocked(conn, tableName);
            HARDENED_TABLES.add(cacheKey);
        } catch (SQLException ex) {
            if (isBusySchemaError(ex)) {
                System.err.println("Skipped security hardening for " + tableName + " because the database was busy: " + ex.getMessage());
                return;
            }
            throw ex;
        } finally {
            try (Statement unlock = conn.createStatement()) {
                unlock.execute("SELECT pg_advisory_unlock(" + HARDENING_LOCK_KEY + ")");
            }
        }
    }

    private static void protectInternalTableUnlocked(Connection conn, String tableName) throws SQLException {
        String table = quoteIdentifier(tableName);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET lock_timeout = '2s'");
            stmt.execute("SET statement_timeout = '10s'");
            stmt.executeUpdate("ALTER TABLE public." + table + " ENABLE ROW LEVEL SECURITY");
            stmt.executeUpdate("REVOKE ALL ON TABLE public." + table + " FROM PUBLIC");
            revokeRoleIfExists(conn, "anon", "TABLE public." + table);
            revokeRoleIfExists(conn, "authenticated", "TABLE public." + table);
            dropPolicyIfExists(stmt, table, tableName + "_authenticated_all");
            dropPolicyIfExists(stmt, table, tableName + "_anon_all");
            createServiceRolePolicyIfAvailable(conn, tableName);
            hardenOwnedSequences(conn, tableName);
        }
    }

    private static String databaseCacheKey(Connection conn) {
        try {
            String url = conn.getMetaData().getURL();
            return url == null || url.isBlank() ? "unknown" : url;
        } catch (SQLException ex) {
            return "unknown";
        }
    }

    private static void revokeRoleIfExists(Connection conn, String roleName, String target) throws SQLException {
        if (!roleExists(conn, roleName)) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("REVOKE ALL ON " + target + " FROM " + quoteIdentifier(roleName));
        }
    }

    private static void createServiceRolePolicyIfAvailable(Connection conn, String tableName) throws SQLException {
        if (!roleExists(conn, "service_role")) {
            return;
        }
        String table = quoteIdentifier(tableName);
        String policyName = tableName + "_service_role_all";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP POLICY IF EXISTS " + quoteIdentifier(policyName) + " ON public." + table);
            stmt.executeUpdate("""
                    CREATE POLICY %s
                    ON public.%s
                    FOR ALL
                    TO service_role
                    USING (true)
                    WITH CHECK (true)
                    """.formatted(quoteIdentifier(policyName), table));
            stmt.executeUpdate("GRANT ALL ON TABLE public." + table + " TO service_role");
        }
    }

    private static void dropPolicyIfExists(Statement stmt, String quotedTable, String policyName) throws SQLException {
        stmt.executeUpdate("DROP POLICY IF EXISTS " + quoteIdentifier(policyName) + " ON public." + quotedTable);
    }

    private static void hardenOwnedSequences(Connection conn, String tableName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT pg_get_serial_sequence('public.' || table_name, column_name) AS sequence_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_default LIKE 'nextval(%'
                """)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String sequenceName = rs.getString("sequence_name");
                    if (sequenceName == null || sequenceName.isBlank()) {
                        continue;
                    }
                    try (Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate("REVOKE ALL ON SEQUENCE " + sequenceName + " FROM PUBLIC");
                    }
                    revokeRoleIfExists(conn, "anon", "SEQUENCE " + sequenceName);
                    revokeRoleIfExists(conn, "authenticated", "SEQUENCE " + sequenceName);
                    if (roleExists(conn, "service_role")) {
                        try (Statement stmt = conn.createStatement()) {
                            stmt.executeUpdate("GRANT ALL ON SEQUENCE " + sequenceName + " TO service_role");
                        }
                    }
                }
            }
        }
    }

    private static boolean roleExists(Connection conn, String roleName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM pg_roles WHERE rolname = ?")) {
            ps.setString(1, roleName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static boolean isBusySchemaError(SQLException ex) {
        String state = ex.getSQLState();
        return "40P01".equals(state) || "55P03".equals(state) || "57014".equals(state);
    }
}
