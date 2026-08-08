package services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
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
        String windowsLauncher = PostgresRuntimeService.installedSyncLauncherContent(
                true, windowsAppDir, "inventory-management-1.0.11.jar");
        assertTrue(windowsLauncher.startsWith("@echo off\r\ncd /d \"" + windowsAppDir + "\"\r\n\""));
        assertTrue(windowsLauncher.contains("\\bin\\java.exe\" -jar \"inventory-management-1.0.11.jar\" --sync-service"));
        assertTrue(windowsLauncher.endsWith(">> \"C:\\Users\\test\\.smartstock\\sync-service\\sync-service.log\" 2>&1\r\n"));
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
        assertFalse(script.contains("SUPABASE_URL="));
        assertTrue(script.contains("New-NetFirewallRule"));
        assertTrue(script.contains("SmartStockServerService"));
        assertTrue(script.contains("runtime\\bin\\java.exe"));
        assertFalse(script.contains("'java -jar"));
        assertFalse(script.contains("service_role"));
        assertFalse(script.contains("DB_PASSWORD"));
    }

    @Test
    void packagedWindowsServiceCanUseSmartStockLauncher() {
        String script = PostgresRuntimeService.windowsProductionInstallScript(
                Path.of("C:\\Program Files\\SmartStock\\app\\inventory-management-1.0.36.jar"),
                Path.of("C:\\Program Files\\SmartStock\\app\\dependency"),
                Path.of("C:\\Program Files\\SmartStock\\SmartStock.exe"), true,
                "https://abcdefghijklmnopqrst.supabase.co", "sb_publishable_example",
                "development", "LocalSubnet",
                Path.of("C:\\Users\\test\\.smartstock\\setup\\result.log"));
        assertTrue(script.contains("SmartStock.exe"));
        assertTrue(script.contains("--sync-service"));
        assertTrue(script.contains("New-ScheduledTaskAction -Execute $ServiceExecutable"));
        assertTrue(script.contains("$ServiceArguments = '--sync-service'"));
        assertTrue(script.contains("-Argument $ServiceArguments -WorkingDirectory $ServiceAppDir"));
        assertTrue(script.contains("Unregister-ScheduledTask"));
        assertTrue(script.contains("Register-ScheduledTask"));
        assertFalse(script.contains("System32\\cmd.exe"));
        assertFalse(script.contains("run-smartstock-sync-service.cmd"));
        assertTrue(script.contains("New-ScheduledTaskTrigger -AtLogOn"));
        assertTrue(script.contains("-RunLevel Limited"));
        assertFalse(script.contains("-RunLevel Highest"));
        assertFalse(script.contains("schtasks /Run"));
        assertFalse(script.contains("schtasks /Create"));
        assertFalse(script.contains("-jar \\\"inventory-management"));
    }

    @Test
    void namedWindowsServerLauncherOmitsEmptyTaskArgument() {
        String script = PostgresRuntimeService.windowsProductionInstallScript(
                Path.of("C:\\Program Files\\SmartStock\\app\\inventory-management-1.0.39.jar"),
                Path.of("C:\\Program Files\\SmartStock\\app\\dependency"),
                Path.of("C:\\Program Files\\SmartStock\\SmartStockServer.exe"), true,
                "https://abcdefghijklmnopqrst.supabase.co", "sb_publishable_example",
                "development", "LocalSubnet",
                Path.of("C:\\Users\\test\\.smartstock\\setup\\result.log"));

        assertTrue(script.contains("$ServiceArguments = ''"));
        assertTrue(script.contains("if ([string]::IsNullOrWhiteSpace($ServiceArguments))"));
        assertTrue(script.contains("New-ScheduledTaskAction -Execute $ServiceExecutable"));
        assertTrue(script.contains("-WorkingDirectory $ServiceAppDir"));
    }

    @Test
    void findsJarInInstalledWindowsAppLayout() throws Exception {
        Path installedRoot = tempDir.resolve("SmartStock");
        Path installedApp = Files.createDirectories(installedRoot.resolve("app"));
        Path jar = Files.writeString(
                installedApp.resolve("inventory-management-1.0.36.jar"), "test");

        assertEquals(jar.toAbsolutePath().normalize(),
                PostgresRuntimeService.findPackagedJar(installedRoot));
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
    void windowsServiceInstallationUsesEnvironmentAwareElevatedPath() throws Exception {
        String source = Files.readString(Path.of("src/services/PostgresRuntimeService.java"));

        assertTrue(source.contains("return installWindowsServer(SupabaseProjectConfig.load(),"));
        assertTrue(source.contains("Join-Path $AppDir 'app'"));
        assertTrue(source.contains("$SourceDependency = Join-Path $Jar.DirectoryName 'dependency'"));
        assertTrue(source.contains("Files.mismatch(currentJar, serviceJar)"));
        assertTrue(source.contains("StandardCopyOption.REPLACE_EXISTING"));
        assertTrue(source.contains("!isSyncServiceRunning(status.output())"));
    }

    @Test
    void recognizesRunningAndStoppedWindowsTasks() {
        assertTrue(PostgresRuntimeService.isSyncServiceRunning(
                "TaskName: SmartStockServerService\nState: Running"));
        assertFalse(PostgresRuntimeService.isSyncServiceRunning(
                "TaskName: SmartStockServerService\nState: Ready"));
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
