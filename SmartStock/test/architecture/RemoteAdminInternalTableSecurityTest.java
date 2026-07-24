package architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RemoteAdminInternalTableSecurityTest {
    @Test
    void runtimeInstallerHardensRemoteAdminTables() throws Exception {
        String installer = Files.readString(Path.of(
                "src/services/SyncSchemaInstaller.java"));

        assertTrue(installer.contains(
                "protectInternalTable(conn, \"store_sync_status\")"));
        assertTrue(installer.contains(
                "protectInternalTable(conn, \"remote_admin_commands\")"));
    }

    @Test
    void migrationEnablesRlsAndRevokesClientRoles() throws Exception {
        String migration = Files.readString(Path.of(
                "database/migrations/20260724062034_harden_remote_admin_internal_tables.sql"));
        String migrationRunner = Files.readString(Path.of(
                "src/services/ServerSupabaseMigrationRunner.java"));

        for (String table : new String[]{
                "store_sync_status", "remote_admin_commands"}) {
            assertTrue(migration.contains(
                    "ALTER TABLE IF EXISTS public." + table
                            + " ENABLE ROW LEVEL SECURITY"));
            assertTrue(migration.contains(
                    "REVOKE ALL ON TABLE public." + table
                            + " FROM PUBLIC, anon, authenticated"));
            assertTrue(migration.contains(
                    "CREATE POLICY " + table + "_service_role_all"));
        }
        assertTrue(migrationRunner.contains(
                "database/migrations/20260724062034_harden_remote_admin_internal_tables.sql"));
    }
}
