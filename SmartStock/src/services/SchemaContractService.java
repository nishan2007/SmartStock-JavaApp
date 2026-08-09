package services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Installs fresh v1 candidates and validates live schemas without repairing them. */
public final class SchemaContractService {
    public static final int BASELINE_VERSION = 1;
    private static final String RESOURCE_FINGERPRINT_TOKEN =
            "__SMARTSTOCK_RESOURCE_FINGERPRINT__";
    private static final List<String> LOCAL_BASELINE = List.of(
            "database/v1/local/001_schema.sql",
            "database/v1/local/002_seed.sql",
            "database/v1/local/003_metadata.sql"
    );
    private static final List<String> CLOUD_BASELINE = List.of(
            "database/v1/cloud/001_schema.sql",
            "database/v1/cloud/002_storage.sql",
            "database/v1/cloud/003_metadata.sql"
    );
    private static final List<String> CLOUD_POST_V1 = List.of(
            "database/migrations/v1_after/20260809190000_revoke_anon_security_definer_execute.sql",
            "database/migrations/v1_after/20260809192551_restrict_service_only_rpc_execute.sql"
    );
    private static final Set<String> VALIDATED_LOCAL_DATABASES =
            ConcurrentHashMap.newKeySet();

    private SchemaContractService() {
    }

    public static List<String> localBaselineResources() {
        return LOCAL_BASELINE;
    }

    public static List<String> cloudBaselineResources() {
        return CLOUD_BASELINE;
    }

    public static List<String> cloudPostV1MigrationResources() {
        return CLOUD_POST_V1;
    }

    public static List<String> cloudContractResources() {
        List<String> resources = new ArrayList<>(CLOUD_BASELINE);
        resources.addAll(CLOUD_POST_V1);
        return List.copyOf(resources);
    }

    public static void installLocalBaseline(Connection connection) throws Exception {
        installBaseline(connection, LOCAL_BASELINE, "LOCAL", List.of("public"));
    }

    public static Readiness validateLocal(Connection connection) throws SQLException {
        return validate(connection, "LOCAL", LOCAL_BASELINE, List.of("public"), false);
    }

    /** Blocks database-dependent work unless this database matches the packaged v1 baseline. */
    public static void requireLocalReady(Connection connection) throws SQLException {
        String key = connection.getMetaData().getURL() + "|" + connection.getMetaData().getUserName();
        if (VALIDATED_LOCAL_DATABASES.contains(key)) return;
        Readiness readiness = validateLocal(connection);
        if (!readiness.ready()) throw new SQLException(readiness.message(), "55000");
        VALIDATED_LOCAL_DATABASES.add(key);
    }

    public static Readiness validateCloud(Connection connection) throws SQLException {
        return validate(connection, "CLOUD", cloudContractResources(),
                List.of("public", "smartstock_private"), true);
    }

    static Readiness validateCloudApplied(Connection connection, List<String> resources)
            throws SQLException {
        boolean grantFingerprint = resources.contains(
                "database/migrations/v1_after/20260809190000_revoke_anon_security_definer_execute.sql");
        return validate(connection, "CLOUD", resources,
                List.of("public", "smartstock_private"), grantFingerprint);
    }

    static void installCloudBaseline(Connection connection) throws Exception {
        installBaseline(connection, CLOUD_BASELINE, "CLOUD",
                List.of("public", "smartstock_private"));
    }

    static void refreshCloudContract(Connection connection) throws Exception {
        String resourceFingerprint = resourceFingerprint(cloudContractResources());
        String catalogFingerprint = catalogFingerprint(connection,
                List.of("public", "smartstock_private"), true);
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE smartstock_private.smartstock_schema_metadata
                SET resource_fingerprint_sha256=?, catalog_fingerprint_sha256=?
                WHERE schema_scope='CLOUD' AND baseline_version=?
                """)) {
            statement.setString(1, resourceFingerprint);
            statement.setString(2, catalogFingerprint);
            statement.setInt(3, BASELINE_VERSION);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Cloud schema metadata could not be refreshed.");
            }
        }
    }

    private static void installBaseline(Connection connection, List<String> resources,
                                        String scope, List<String> schemas) throws Exception {
        String resourceFingerprint = resourceFingerprint(resources);
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (String resource : resources) {
                String sql = SqlScriptRunner.readResource(resource)
                        .replace(RESOURCE_FINGERPRINT_TOKEN, resourceFingerprint);
                SqlScriptRunner.runSql(connection, sql);
            }
            // The already-applied v1 baseline predates grant fingerprinting. The
            // first post-v1 hardening migration upgrades the contract atomically.
            String catalogFingerprint = catalogFingerprint(connection, schemas, false);
            String metadataTable = "CLOUD".equals(scope)
                    ? "smartstock_private.smartstock_schema_metadata"
                    : "public.smartstock_schema_metadata";
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE %s
                    SET catalog_fingerprint_sha256=?
                    WHERE schema_scope=? AND baseline_version=?
                    """.formatted(metadataTable))) {
                statement.setString(1, catalogFingerprint);
                statement.setString(2, scope);
                statement.setInt(3, BASELINE_VERSION);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("SmartStock schema metadata was not installed.");
                }
            }
            connection.commit();
        } catch (Exception ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private static Readiness validate(Connection connection, String scope,
                                      List<String> resources, List<String> schemas)
            throws SQLException {
        return validate(connection, scope, resources, schemas,
                "CLOUD".equals(scope));
    }

    private static Readiness validate(Connection connection, String scope,
                                      List<String> resources, List<String> schemas,
                                      boolean includeGrants)
            throws SQLException {
        String metadataSchema = "CLOUD".equals(scope) ? "smartstock_private" : "public";
        if (!tableExists(connection, metadataSchema, "smartstock_schema_metadata")) {
            return new Readiness(false, null, null, null,
                    "Schema metadata is missing. Build and verify a side-by-side v1 candidate.");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT baseline_version, resource_fingerprint_sha256,
                       catalog_fingerprint_sha256
                FROM %s.smartstock_schema_metadata
                WHERE schema_scope=?
                """.formatted(metadataSchema))) {
            statement.setString(1, scope);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return new Readiness(false, null, null, null,
                            "Schema metadata has no " + scope + " contract.");
                }
                int version = rows.getInt(1);
                String storedResource = rows.getString(2);
                String storedCatalog = rows.getString(3);
                String expectedResource;
                try {
                    expectedResource = resourceFingerprint(resources);
                } catch (Exception ex) {
                    throw new SQLException("Packaged v1 SQL is missing or unreadable: "
                            + ex.getMessage(), ex);
                }
                String actualCatalog = catalogFingerprint(connection, schemas, includeGrants);
                boolean ready = version == BASELINE_VERSION
                        && expectedResource.equals(storedResource)
                        && actualCatalog.equals(storedCatalog);
                String message = ready ? "Schema v1 is ready."
                        : "Schema v1 fingerprint mismatch. Use side-by-side repair; in-place repair is blocked.";
                return new Readiness(ready, version, storedResource, actualCatalog, message);
            }
        }
    }

    static String resourceFingerprint(List<String> resources) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String resource : resources) {
            digest.update(resource.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(SqlScriptRunner.readResource(resource)
                    .getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static String catalogFingerprint(Connection connection, List<String> schemas)
            throws SQLException {
        return catalogFingerprint(connection, schemas,
                schemas.contains("smartstock_private"));
    }

    private static String catalogFingerprint(Connection connection, List<String> schemas,
                                             boolean includeGrants) throws SQLException {
        List<String> entries = new ArrayList<>();
        collect(connection, entries, """
                SELECT 'column|'||table_schema||'|'||table_name||'|'||
                       lpad(ordinal_position::text,5,'0')||'|'||column_name||'|'||
                       data_type||'|'||coalesce(udt_schema,'')||'|'||coalesce(udt_name,'')||'|'||
                       is_nullable||'|'||coalesce(column_default,'')
                FROM information_schema.columns
                WHERE table_schema = ANY (?)
                """, schemas);
        collect(connection, entries, """
                SELECT 'constraint|'||n.nspname||'|'||c.relname||'|'||con.conname||'|'||
                       pg_get_constraintdef(con.oid, true)
                FROM pg_constraint con JOIN pg_class c ON c.oid=con.conrelid
                JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname = ANY (?)
                """, schemas);
        collect(connection, entries, """
                SELECT 'index|'||schemaname||'|'||tablename||'|'||indexname||'|'||indexdef
                FROM pg_indexes WHERE schemaname = ANY (?)
                """, schemas);
        collect(connection, entries, """
                SELECT 'trigger|'||n.nspname||'|'||c.relname||'|'||t.tgname||'|'||
                       pg_get_triggerdef(t.oid, true)
                FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
                JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE NOT t.tgisinternal AND n.nspname = ANY (?)
                """, schemas);
        collect(connection, entries, """
                SELECT 'function|'||n.nspname||'|'||p.proname||'|'||
                       pg_get_function_identity_arguments(p.oid)||'|'||pg_get_functiondef(p.oid)
                FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
                WHERE p.prokind IN ('f','p') AND n.nspname = ANY (?)
                """, schemas);
        collect(connection, entries, """
                SELECT 'policy|'||schemaname||'|'||tablename||'|'||policyname||'|'||
                       permissive||'|'||coalesce(array_to_string(roles,','),'')||'|'||cmd||'|'||
                       coalesce(qual,'')||'|'||coalesce(with_check,'')
                FROM pg_policies WHERE schemaname = ANY (?)
                """, schemas);
        collect(connection, entries, """
                SELECT 'sequence|'||schemaname||'|'||sequencename||'|'||data_type||'|'||
                       start_value||'|'||min_value||'|'||max_value||'|'||increment_by||'|'||cycle
                FROM pg_sequences WHERE schemaname = ANY (?)
                """, schemas);
        if (schemas.contains("smartstock_private")) {
            if (includeGrants) {
            collect(connection, entries, """
                    SELECT 'relation-grant|'||n.nspname||'|'||c.relname||'|'||
                           coalesce(grantee.rolname,'PUBLIC')||'|'||acl.privilege_type||'|'||
                           acl.is_grantable
                    FROM pg_class c
                    JOIN pg_namespace n ON n.oid=c.relnamespace
                    CROSS JOIN LATERAL aclexplode(c.relacl) acl
                    LEFT JOIN pg_roles grantee ON grantee.oid=acl.grantee
                    WHERE n.nspname = ANY (?)
                      AND coalesce(grantee.rolname,'PUBLIC') = ANY
                          (ARRAY['PUBLIC','anon','authenticated','service_role'])
                    """, schemas);
            collect(connection, entries, """
                    SELECT 'function-grant|'||n.nspname||'|'||p.proname||'|'||
                           pg_get_function_identity_arguments(p.oid)||'|'||
                           coalesce(grantee.rolname,'PUBLIC')||'|'||acl.privilege_type||'|'||
                           acl.is_grantable
                    FROM pg_proc p
                    JOIN pg_namespace n ON n.oid=p.pronamespace
                    CROSS JOIN LATERAL aclexplode(p.proacl) acl
                    LEFT JOIN pg_roles grantee ON grantee.oid=acl.grantee
                    WHERE n.nspname = ANY (?)
                      AND coalesce(grantee.rolname,'PUBLIC') = ANY
                          (ARRAY['PUBLIC','anon','authenticated','service_role'])
                    """, schemas);
            collect(connection, entries, """
                    SELECT 'schema-grant|'||n.nspname||'|'||
                           coalesce(grantee.rolname,'PUBLIC')||'|'||acl.privilege_type||'|'||
                           acl.is_grantable
                    FROM pg_namespace n
                    CROSS JOIN LATERAL aclexplode(n.nspacl) acl
                    LEFT JOIN pg_roles grantee ON grantee.oid=acl.grantee
                    WHERE n.nspname = ANY (?)
                      AND coalesce(grantee.rolname,'PUBLIC') = ANY
                          (ARRAY['PUBLIC','anon','authenticated','service_role'])
                    """, schemas);
            }
            collectWithoutSchemas(connection, entries, """
                    SELECT 'storage-policy|'||schemaname||'|'||tablename||'|'||policyname||'|'||
                           permissive||'|'||coalesce(array_to_string(roles,','),'')||'|'||cmd||'|'||
                           coalesce(qual,'')||'|'||coalesce(with_check,'')
                    FROM pg_policies
                    WHERE schemaname='storage' AND tablename='objects'
                      AND policyname IN (
                        'Anyone can view product images',
                        'Authenticated users can upload product images',
                        'Authenticated users can update product images',
                        'employee files staff insert', 'employee files staff read',
                        'employee files staff update',
                        'smartstock releases admin insert',
                        'smartstock releases admin update',
                        'smartstock releases authenticated read'
                      )
                    """);
            collectWithoutSchemas(connection, entries, """
                    SELECT 'storage-bucket|'||id||'|'||name||'|'||public||'|'||
                           coalesce(file_size_limit::text,'')||'|'||
                           coalesce(array_to_string(allowed_mime_types,','),'')
                    FROM storage.buckets
                    WHERE id IN ('employee files','Product Images','smartstock-releases')
                    """);
        }
        entries.sort(Comparator.naturalOrder());
        return sha256(String.join("\n", entries));
    }

    private static void collect(Connection connection, List<String> target, String sql,
                                List<String> schemas) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setArray(1, connection.createArrayOf("text", schemas.toArray()));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) target.add(rows.getString(1));
            }
        }
    }

    private static void collectWithoutSchemas(Connection connection, List<String> target,
                                              String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) target.add(rows.getString(1));
        }
    }

    private static boolean tableExists(Connection connection, String schema, String table)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT to_regclass(?) IS NOT NULL")) {
            statement.setString(1, schema + "." + table);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getBoolean(1);
            }
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    public record Readiness(boolean ready, Integer version, String resourceFingerprint,
                            String catalogFingerprint, String message) {
    }
}
