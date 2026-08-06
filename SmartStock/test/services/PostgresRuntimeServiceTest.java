package services;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresRuntimeServiceTest {
    @Test
    void buildsLaunchersForTheCurrentlyPackagedJar() {
        Path macAppDir = Path.of("/Users/test/.smartstock/sync-service/app");
        assertEquals("#!/usr/bin/env bash\nset -euo pipefail\n"
                        + "cd '/Users/test/.smartstock/sync-service/app'\n"
                        + "exec java -Djava.awt.headless=true -Dapple.awt.UIElement=true -jar "
                        + "'inventory-management-1.0.11.jar' --sync-service\n",
                PostgresRuntimeService.installedSyncLauncherContent(
                        false, macAppDir, "inventory-management-1.0.11.jar"));

        Path windowsAppDir = Path.of("C:\\Users\\test\\.smartstock\\sync-service\\app");
        assertEquals("@echo off\r\ncd /d \"C:\\Users\\test\\.smartstock\\sync-service\\app\"\r\n"
                        + "java -jar \"inventory-management-1.0.11.jar\" --sync-service\r\n",
                PostgresRuntimeService.installedSyncLauncherContent(
                        true, windowsAppDir, "inventory-management-1.0.11.jar"));
    }

    @Test
    void buildsProductionWindowsSetupWithoutPrivateCredentials() {
        String script = PostgresRuntimeService.windowsProductionInstallScript(
                Path.of("C:\\SmartStock\\inventory-management-1.0.24.jar"),
                Path.of("C:\\SmartStock\\dependency"),
                Path.of("C:\\Program Files\\SmartStock\\runtime\\bin\\java.exe"),
                "https://abcdefghijklmnopqrst.supabase.co",
                "sb_publishable_example",
                "192.168.1.0/24",
                Path.of("C:\\Users\\test\\.smartstock\\setup\\result.log"));
        assertTrue(script.contains("SMARTSTOCK_ENVIRONMENT=production"));
        assertTrue(script.contains("SUPABASE_URL=https://abcdefghijklmnopqrst.supabase.co"));
        assertTrue(script.contains("New-NetFirewallRule"));
        assertTrue(script.contains("SmartStockServerService"));
        assertTrue(script.contains("runtime\\bin\\java.exe"));
        assertFalse(script.contains("'java -jar"));
        assertFalse(script.contains("service_role"));
        assertFalse(script.contains("DB_PASSWORD"));
    }

    @Test
    void parsesSupportedPostgresVersions() {
        assertEquals(17, PostgresRuntimeService.parsePostgresMajorVersion("psql (PostgreSQL) 17.5"));
        assertEquals(15, PostgresRuntimeService.parsePostgresMajorVersion("psql (PostgreSQL) 15.12"));
        assertEquals(0, PostgresRuntimeService.parsePostgresMajorVersion("command not found"));
    }

    @Test
    void validatesStoreLanScope() {
        assertTrue(PostgresRuntimeService.validLanSubnetForSetup("LocalSubnet"));
        assertTrue(PostgresRuntimeService.validLanSubnetForSetup("192.168.1.0/24"));
        assertFalse(PostgresRuntimeService.validLanSubnetForSetup(""));
        assertFalse(PostgresRuntimeService.validLanSubnetForSetup("anywhere"));
    }
}
