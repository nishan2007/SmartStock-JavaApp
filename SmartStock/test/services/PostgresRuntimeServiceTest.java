package services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresRuntimeServiceTest {
    @TempDir
    Path tempDir;
    @Test
    void windowsPostgresInstallReportsProgressAndHasBoundedWait() throws Exception {
        String source = Files.readString(Path.of("src/services/PostgresRuntimeService.java"));

        assertTrue(source.contains("progress message every 15 seconds"));
        assertTrue(source.contains("Start-Sleep -Seconds 15"));
        assertTrue(source.contains("$Elapsed.TotalMinutes -ge 20"));
        assertTrue(source.contains("PostgreSQL package installation completed"));
        assertTrue(source.contains("superpassword="));
        assertTrue(source.contains("--optionfile"));
        assertTrue(source.contains("ProtectedData]::Protect"));
        assertTrue(source.contains("ALTER ROLE postgres WITH PASSWORD"));
    }

    @Test
    void windowsPrerequisiteCheckRunsPsqlFromFallbackFilePath() throws Exception {
        String source = Files.readString(Path.of("src/services/PostgresRuntimeService.java"));

        assertTrue(source.contains("if (-not $psqlPath) { $psqlPath = $psql.FullName }"));
        assertTrue(source.contains("& $psqlPath --version"));
    }

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
    void packagedWindowsServiceUsesNativeSmartStockLauncher() {
        String script = PostgresRuntimeService.windowsProductionInstallScript(
                Path.of("C:\\Program Files\\SmartStock\\app\\inventory-management-1.0.36.jar"),
                Path.of("C:\\Program Files\\SmartStock\\app\\dependency"),
                Path.of("C:\\Program Files\\SmartStock\\SmartStock.exe"), true,
                "https://abcdefghijklmnopqrst.supabase.co", "sb_publishable_example",
                "development", "LocalSubnet",
                Path.of("C:\\Users\\test\\.smartstock\\setup\\result.log"));
        assertTrue(script.contains("SmartStock.exe"));
        assertTrue(script.contains("--sync-service"));
        assertTrue(script.contains("('cd /d \"' + $ServiceAppDir + '\"')"));
        assertTrue(script.contains("Unregister-ScheduledTask"));
        assertTrue(script.contains("Register-ScheduledTask"));
        assertTrue(script.contains("System32\\cmd.exe"));
        assertTrue(script.contains("New-ScheduledTaskTrigger -AtLogOn"));
        assertFalse(script.contains("schtasks /Run"));
        assertFalse(script.contains("schtasks /Create"));
        assertFalse(script.contains("-jar \\\"inventory-management"));
    }

    @Test
    void packagedWindowsServiceScriptParsesInPowerShell() throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
        String script = PostgresRuntimeService.windowsProductionInstallScript(
                Path.of("C:\\Program Files\\SmartStock\\app\\inventory-management-1.0.36.jar"),
                Path.of("C:\\Program Files\\SmartStock\\app\\dependency"),
                Path.of("C:\\Program Files\\SmartStock\\SmartStock.exe"), true,
                "https://abcdefghijklmnopqrst.supabase.co", "sb_publishable_example",
                "development", "LocalSubnet", tempDir.resolve("result.log"));
        Path scriptFile = tempDir.resolve("install.ps1");
        Files.writeString(scriptFile, script, StandardCharsets.UTF_8);
        Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command",
                "$tokens=$null;$errors=$null;[System.Management.Automation.Language.Parser]::ParseFile("+
                        "'" + scriptFile.toString().replace("'", "''") + "',[ref]$tokens,[ref]$errors)|Out-Null;"+
                        "if($errors.Count){$errors|ForEach-Object{$_.Message};exit 1}")
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
    }

    @Test
    void windowsServiceStatusDoesNotRequireGitBash() throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
        PostgresRuntimeService.CommandResult result = PostgresRuntimeService.syncServiceStatus();
        assertTrue(result.success(), result.output());
        assertFalse(result.output().contains("CreateProcess error=2"), result.output());
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
