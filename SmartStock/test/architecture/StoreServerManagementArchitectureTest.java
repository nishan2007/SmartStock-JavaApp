package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreServerManagementArchitectureTest {
    private static String source(String path) throws Exception { return Files.readString(Path.of(path)); }

    @Test
    void registryEnforcesOnePrimaryAndIsServiceRoleOnly() throws Exception {
        String sql=source("database/migrations/20260806180000_store_server_registry.sql");
        assertTrue(sql.contains("store_server_instances_one_primary_idx"));
        assertTrue(sql.contains("store_server_events_location_created_idx"));
        assertTrue(sql.contains("store_server_handoffs_source_idx"));
        assertTrue(sql.contains("v_action = 'ENSURE_LOCATION'"));
        assertTrue(sql.contains("'STANDBY_PREPARED'"));
        assertTrue(sql.contains("'SERVER_RENAMED'"));
        assertTrue(sql.contains("WHERE role = 'PRIMARY'"));
        assertTrue(sql.contains("ENABLE ROW LEVEL SECURITY"));
        assertTrue(sql.contains("REVOKE ALL ON TABLE public.store_server_instances FROM PUBLIC, anon, authenticated"));
        assertTrue(sql.contains("REVOKE ALL ON TABLE public.store_server_instances FROM service_role"));
        assertTrue(sql.contains("CREATE OR REPLACE FUNCTION smartstock_private.smartstock_server_registry"));
        assertTrue(sql.contains("SECURITY INVOKER"));
        assertTrue(sql.contains("GRANT EXECUTE ON FUNCTION public.smartstock_server_registry(text,jsonb) TO service_role"));
        assertFalse(sql.contains("GRANT EXECUTE ON FUNCTION public.smartstock_server_registry(text,jsonb) TO authenticated"));
    }

    @Test
    void lifecycleSupportsVerifiedAndEmergencyReplacement() throws Exception {
        String sql=source("database/migrations/20260806180000_store_server_registry.sql");
        for(String action:new String[]{"BEGIN_HANDOFF","MARK_HANDOFF_READY","COMPLETE_HANDOFF","FAIL_HANDOFF","EMERGENCY_TAKEOVER"})
            assertTrue(sql.contains("'"+action+"'"),action);
        assertTrue(sql.contains("v_handoff.status IN ('READY','COMPLETED')"));
        assertTrue(sql.contains("v_handoff.status='COMPLETED'"));
        assertTrue(sql.contains("v_handoff.status='FAILED'"));
        assertTrue(sql.contains("last_materialization_at IS NULL"));
        assertTrue(sql.contains("role='FENCED'"));
        assertTrue(sql.contains("warning_acknowledged"));
    }

    @Test
    void lanAndSetupRespectServerRoles() throws Exception {
        String server=source("src/services/LanApiServer.java");
        String setup=source("src/ui/screens/ServerSetupWizard.java");
        String sync=source("src/services/SyncWorker.java");
        String devices=source("src/ui/screens/DeviceManagement.java");
        String background=source("src/app/SyncServiceMain.java");
        String gateway=source("src/app/RemoteGatewayMain.java");
        String admin=source("src/services/LanServerAdminService.java");
        String managementClient=source("src/services/ServerManagementClient.java");
        assertTrue(server.contains("access_mode='SERVER'"));
        assertTrue(server.contains("/v1/security/servers/list"));
        for(String route:new String[]{"prepare-standby","begin-handoff","handoff-status","emergency-takeover","retire"})
            assertTrue(server.contains("/v1/security/servers/"+route),route);
        assertTrue(server.contains("ServerRoleGuard.blocks"));
        assertTrue(setup.contains("SmartStock will not start a second writable server"));
        assertTrue(setup.contains("discoverStoreServers"));
        assertTrue(source("src/services/ServerSetupGuardService.java").contains("ensureStoreLocation(locationId)"));
        assertTrue(source("src/services/ServerSetupGuardService.java").contains("RESTORE_MISMATCH"));
        String migrations=source("src/services/ServerSupabaseMigrationRunner.java");
        assertTrue(migrations.contains("20260806120000_add_other_income_entries.sql"));
        assertTrue(migrations.contains("20260806181000_store_server_registry_indexes.sql"));
        assertTrue(migrations.contains("20260806193000_require_whole_gyd_other_income.sql"));
        assertTrue(setup.contains("prepareToStart()"));
        assertTrue(setup.contains("Register reconnection"));
        assertTrue(sync.contains("CloudServerRegistryService.heartbeatCurrent"));
        assertTrue(sync.indexOf("heartbeatCurrent(local,null)")<sync.indexOf("TimeClockAutoCloseService.processExpiredOpenPunches"));
        assertFalse(sync.contains("registerCurrent(\n                                CloudServerRegistryService.ServerRole.PRIMARY)"));
        String provisioning=source("src/services/ServerProvisioningService.java");
        assertTrue(provisioning.contains("remain stopped until the store server role is verified"));
        assertFalse(provisioning.contains("ensureSyncServiceInstalled()"));
        assertTrue(background.contains("authorizeBackgroundService(connection)"));
        assertTrue(gateway.contains("authorizeBackgroundService(connection)"));
        assertTrue(admin.contains("OLD_SERVER_STILL_ONLINE"));
        assertTrue(admin.contains("RECOVERY_MUST_RUN_ON_STANDBY"));
        assertTrue(admin.contains("STALE_BACKUP"));
        assertTrue(admin.contains("STANDBY_NOT_READY"));
        assertTrue(admin.contains("RECOVERY_NOT_VALIDATED"));
        assertTrue(admin.contains("prepareHandoffRollback"));
        assertTrue(admin.contains("reconcilePendingHandoffRollback"));
        assertTrue(admin.contains("LanApiClient.discoverServers()"));
        assertTrue(admin.contains("LanApiClient.isServerReachable"));
        assertTrue(managementClient.contains("LanServerAdminService.mutate"));
        assertTrue(devices.contains("Start Verified Handoff"));
        assertTrue(devices.contains("Prepare Standby"));
        assertTrue(devices.contains("All health"));
        assertTrue(devices.contains("Last Sync"));
        assertTrue(devices.contains("Emergency Takeover"));
    }

    @Test
    void recoveryPermissionIsAdministratorOnlyByDefault() throws Exception {
        String setup=source("database/device_management_setup.sql");
        String admin=source("src/services/LanServerAdminService.java");
        assertTrue(setup.contains("'SERVER_RECOVERY'"));
        assertTrue(setup.contains("WHERE UPPER(r.role_name) = 'ADMIN'"));
        assertTrue(admin.contains("require(connection,userId,\"SERVER_RECOVERY\")"));
    }
}
