package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteAdminInternalTableSecurityTest {
    @Test
    void runtimeUsesSchemaValidatorInsteadOfPolicyRepair() throws Exception {
        String installer = Files.readString(Path.of("src/services/SyncSchemaInstaller.java"));

        assertTrue(installer.contains("SchemaContractService.requireLocalReady(connection)"));
        assertFalse(installer.contains("CREATE POLICY"));
        assertFalse(installer.contains("ALTER TABLE"));
    }

    @Test
    void cloudBaselineEnablesRlsAndServiceRolePolicies() throws Exception {
        String cloud = Files.readString(Path.of("database/v1/cloud/001_schema.sql"));
        String migrationRunner = Files.readString(Path.of(
                "src/services/ServerSupabaseMigrationRunner.java"));

        for (String table : new String[]{"store_sync_status", "remote_admin_commands"}) {
            assertTrue(cloud.contains("ALTER TABLE public." + table
                    + " ENABLE ROW LEVEL SECURITY"));
            assertTrue(cloud.contains("CREATE POLICY " + table + "_service_role_all"));
        }
        assertTrue(migrationRunner.contains("SchemaContractService.cloudContractResources()"));
        assertFalse(migrationRunner.contains("20260724062034_harden_remote_admin_internal_tables.sql"));
    }
}
