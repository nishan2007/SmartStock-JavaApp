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

    @Test
    void windowsDatabaseStartupDoesNotRestartAnAlreadyRunningService() throws Exception {
        String source = Files.readString(Path.of("src/services/PostgresRuntimeService.java"));

        assertTrue(source.contains("Where-Object {$_.Status -ne 'Running'}"));
        assertTrue(source.contains("if($stopped.Count -gt 0){$stopped | Start-Service"));
    }
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
        assertTrue(source.contains("('\"{0}\"' -f $OptionFile)"));
        assertTrue(source.contains("--debugtrace"));
        assertTrue(source.contains("Installer details: $InstallerTrace"));
        assertTrue(source.contains("ProtectedData]::Protect"));
        assertTrue(source.contains("DataProtectionScope]::LocalMachine"));
        assertTrue(source.contains("'machine:'"));
        assertTrue(source.contains("icacls.exe $BootstrapCredential /inheritance:r"));
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
        assertTrue(windowsLauncher.startsWith("@echo off\r\nsetlocal\r\ncd /d \"" + windowsAppDir + "\"\r\n"));
        assertTrue(windowsLauncher.contains("\\bin\\java.exe\" -Duser.home=\"C:\\Users\\test\""));
        assertTrue(windowsLauncher.contains("%SMARTSTOCK_SERVER_JAR%"));
        assertTrue(windowsLauncher.contains("sync-service.log"));
        assertFalse(windowsLauncher.contains("inventory-management-1.0.11.jar"));
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
        assertTrue(script.contains("CreateShortcut($ServiceShortcut)"));
        assertTrue(script.contains("New-ScheduledTaskAction -Execute $ServiceExecutable"));
        assertTrue(script.contains("-WorkingDirectory $ServiceAppDir"));
        assertTrue(script.contains("$ServiceArguments = '--sync-service'"));
        assertTrue(script.contains("$Shortcut.Arguments = $ServiceArguments"));
        assertTrue(script.contains("Unregister-ScheduledTask"));
        assertTrue(script.contains("Register-ScheduledTask"));
        assertFalse(script.contains("System32\\cmd.exe"));
        assertFalse(script.contains("run-smartstock-sync-service.cmd"));
        assertTrue(script.contains("New-ScheduledTaskTrigger -AtLogOn"));
        assertTrue(script.contains("Get-CimInstance Win32_ComputerSystem"));
        assertTrue(script.contains("ProfileImagePath"));
        assertTrue(script.contains("Name='cloudflared.exe'"));
        assertTrue(script.contains("$TunnelPath"));
        assertTrue(script.indexOf("existing SmartStock server process did not stop before update")
                < script.indexOf("Stop-Process -Id $Tunnel.ProcessId"));
        assertTrue(script.indexOf("Stop-Process -Id $Tunnel.ProcessId")
                < script.indexOf("Move-Item -LiteralPath $ServiceAppDir"));
        assertTrue(script.contains(".app-staged-"));
        assertTrue(script.contains("missing the PostgreSQL driver"));
        assertTrue(script.contains("-Duser.home=\""));
        assertTrue(script.contains("-UserId $ServiceUser"));
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
                Path.of("SmartStockServer.exe"), true,
                "https://abcdefghijklmnopqrst.supabase.co", "sb_publishable_example",
                "development", "LocalSubnet",
                Path.of("C:\\Users\\test\\.smartstock\\setup\\result.log"));

        assertTrue(script.contains("$ServiceArguments = ''"));
        assertTrue(script.contains("CreateShortcut($ServiceShortcut)"));
        assertTrue(script.contains("$Shortcut.Arguments = $ServiceArguments"));
        assertTrue(script.contains("$Shortcut.WorkingDirectory = $ServiceAppDir"));
    }

    @Test
    void findsJarInInstalledWindowsAppLayout() throws Exception {
        Path installedRoot = tempDir.resolve("SmartStock");
        Path installedApp = Files.createDirectories(installedRoot.resolve("app"));
        Path jar = Files.writeString(
                installedApp.resolve("inventory-management-1.0.36.jar"), "test");

        assertEquals(jar.toAbsolutePath().normalize(),
                PostgresRuntimeService.findPackagedJar(installedRoot));
        assertEquals(installedRoot.resolve("postgresql-installer.exe"),
                PostgresRuntimeService.bundledPostgresInstaller(jar));
    }

    @Test
    void standaloneWindowsInstallersPreserveInteractiveUserAndDpapiContext() throws Exception {
        String service = Files.readString(Path.of(
                "installer/windows/install-sync-service.ps1"));
        String production = Files.readString(Path.of(
                "installer/windows/install-production-server.ps1"));

        assertTrue(service.contains("Get-CimInstance Win32_ComputerSystem"));
        assertTrue(service.contains("ProfileImagePath"));
        assertTrue(service.contains("CreateShortcut($serviceShortcut)"));
        assertTrue(service.contains("New-ScheduledTaskAction -Execute $serviceExecutable"));
        assertTrue(service.contains("run-smartstock-sync-service.cmd"));
        assertTrue(service.contains("Server exited with code"));
        assertTrue(service.contains("-WorkingDirectory $serviceAppDir"));
        assertTrue(service.contains("New-ScheduledTaskTrigger -AtLogOn"));
        assertTrue(service.contains("-Duser.home="));
        assertTrue(service.contains("Invoke-CimMethod -InputObject $server -MethodName Terminate"));
        assertTrue(service.contains("existing SmartStock server process did not stop before update"));
        assertTrue(service.contains("Name='cloudflared.exe'"));
        assertTrue(service.indexOf("existing SmartStock server process did not stop before update")
                < service.indexOf("Stop-Process -Id $tunnel.ProcessId"));
        assertTrue(service.indexOf("Stop-Process -Id $tunnel.ProcessId")
                < service.indexOf("Move-Item -LiteralPath $serviceAppDir"));
        assertTrue(service.contains(".app-staged-"));
        assertTrue(service.contains("missing the PostgreSQL driver"));
        assertFalse(service.contains("$env:USERPROFILE"));
        assertFalse(service.contains("schtasks /Create"));
        assertFalse(service.contains("/SC ONSTART"));
        assertFalse(service.contains("SUPABASE_PUBLISHABLE_KEY="));
        assertTrue(production.contains(
                ".smartstock\\profiles\\production\\database.properties"));
        assertFalse(production.contains("$env:USERPROFILE"));
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
        assertTrue(source.contains("$StagedAppDir = Join-Path $ServiceDir"));
        assertTrue(source.contains("Copy-Item -LiteralPath $Dependencies -Destination"));
        assertTrue(source.contains("Files.mismatch(currentJar, serviceJar)"));
        assertTrue(source.contains("StandardCopyOption.REPLACE_EXISTING"));
        assertTrue(source.contains("!isSyncServiceRunning(status.output())"));
        assertFalse(source.contains("schtasks /Create"));
        assertFalse(source.contains("private static CommandResult installWindowsSyncTask"));
        assertTrue(source.contains("CreateShortcut($ServiceShortcut)"));
        assertTrue(source.contains("public static CommandResult refreshSyncServiceInstallation()"));
        assertTrue(source.contains("return installWindowsServer(SupabaseProjectConfig.load(),"));
        assertTrue(source.contains("$Psql = (Get-Command psql -ErrorAction SilentlyContinue).Source"));
        assertTrue(source.contains("Join-Path $env:ProgramFiles 'PostgreSQL'"));
        assertTrue(source.contains("Where-Object { $_.Directory.Name -eq 'bin' }"));
        assertTrue(source.contains("if ($CurrentListen -eq 'localhost')"));
    }

    @Test
    void recognizesRunningAndStoppedWindowsTasks() {
        assertTrue(PostgresRuntimeService.isSyncServiceInstalled(
                "TaskName : SmartStockServerService\nState : Ready"));
        assertTrue(PostgresRuntimeService.isSyncServiceInstalled(
                "TaskName : SmartStockBackgroundSync\nState : Running"));
        assertTrue(PostgresRuntimeService.isSyncServiceInstalled(
                "TaskName: SmartStockServerService\nState: Running"));
        assertFalse(PostgresRuntimeService.isSyncServiceInstalled(
                "SmartStock Windows service is not installed."));
        assertTrue(PostgresRuntimeService.isSyncServiceRunning(
                "TaskName: SmartStockServerService\nState: Running"));
        assertFalse(PostgresRuntimeService.isSyncServiceRunning(
                "TaskName: SmartStockServerService\nState: Ready"));
    }

    @Test
    void firewallVerificationRejectsDisabledAndIncorrectlyScopedRules() throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
        String fixture = """
                function Get-NetFirewallRule {
                  param($PolicyStore,$DisplayName,$ErrorAction)
                  [pscustomobject]@{ Enabled=%s; Direction='Inbound'; Action='Allow'; Profile=%s;
                    Port=$(if($DisplayName -eq 'SmartStock LAN API 8443'){ '8443' }else{ '18443' });
                    Protocol=$(if($DisplayName -eq 'SmartStock LAN API 8443'){ 'TCP' }else{ 'UDP' }); Scope=%s }
                }
                function Get-NetFirewallPortFilter { process { [pscustomobject]@{Protocol=$_.Protocol;LocalPort=$_.Port} } }
                function Get-NetFirewallAddressFilter { process { [pscustomobject]@{RemoteAddress=$_.Scope} } }
                """;
        String[][] cases = {{"'True'", "'Private'", "'LocalSubnet'", "0"},
                {"'False'", "'Private'", "'LocalSubnet'", "1"},
                {"'True'", "'Public'", "'LocalSubnet'", "1"},
                {"'True'", "'Private'", "'Any'", "1"}};
        for (String[] scenario : cases) {
            String script = fixture.formatted(scenario[0], scenario[1], scenario[2])
                    + "try { " + PostgresRuntimeService.windowsLanFirewallVerification("LocalSubnet")
                    + "; exit 0 } catch { Write-Output $_; exit 1 }";
            Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", script)
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(Integer.parseInt(scenario[3]), process.waitFor(), output);
        }
    }

    @Test
    void firewallRepairRejectsShellInjectionAndContainsNoBroadProfileRule() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> PostgresRuntimeService.windowsLanFirewallRepairScript("LocalSubnet'; exit 0; #", tempDir.resolve("log")));
        String script = PostgresRuntimeService.windowsLanFirewallRepairScript("192.168.10.0/24", tempDir.resolve("log"));
        assertTrue(script.contains("-Profile Private -RemoteAddress $Scope"));
        assertFalse(script.contains("Set-NetFirewallProfile"));
        assertFalse(script.contains("-Profile Any"));
    }

    @Test
    void windowsLanStartChecksTheListenerAndPreservesInstallationFailures() throws Exception {
        String source = Files.readString(Path.of("src/services/PostgresRuntimeService.java"));
        int start = source.indexOf("public static CommandResult startLanService()");
        String body = source.substring(start, source.indexOf("public static CommandResult startServerProcesses()", start));
        assertTrue(body.contains("if (!installation.success()) return installation;"));
        assertTrue(body.contains("ConnectAsync('127.0.0.1', 8443)"));
        assertTrue(body.contains("LastTaskResult"));
        assertTrue(body.contains("exit 1"));
        int hardened = source.indexOf("private static boolean isWindowsTaskActionHardened()");
        String check = source.substring(hardened, source.indexOf("public static CommandResult installWindowsProductionServer", hardened));
        assertFalse(check.contains("explorer"));
    }

    @Test
    void environmentSwitchStopsTheExplorerHandedOffJvmBeforeRestart() throws Exception {
        String source = Files.readString(Path.of("src/services/PostgresRuntimeService.java"));
        int method = source.indexOf("switchLanServiceEnvironment(boolean startSelectedServer)");
        String body = source.substring(method, source.indexOf(
                "public static CommandResult ensureServiceOnlyDatabaseAccess", method));

        assertTrue(body.contains("Stop-ScheduledTask"));
        assertTrue(body.contains("--sync-service"));
        assertTrue(body.contains("Invoke-CimMethod -InputObject $server -MethodName Terminate"));
        assertTrue(body.contains(
                "Start-ScheduledTask -TaskName SmartStockServerService -ErrorAction Stop"));
        assertTrue(body.indexOf("Invoke-CimMethod -InputObject $server -MethodName Terminate")
                < body.indexOf("%s"));
        assertTrue(body.contains("if (!startSelectedServer)"));
    }

    @Test
    void windowsServiceRepairStopsTheExplorerHandedOffJvmBeforeReplacingJar() throws Exception {
        String source = Files.readString(Path.of("src/services/PostgresRuntimeService.java"));
        int method = source.indexOf("private static String windowsProductionInstallScript");
        String body = source.substring(method, source.indexOf("private static", method + 20));

        assertTrue(body.contains("Invoke-CimMethod -InputObject $Server -MethodName Terminate"));
        assertTrue(body.contains("existing SmartStock server process did not stop before update"));
        assertTrue(body.indexOf("Invoke-CimMethod -InputObject $Server -MethodName Terminate")
                < body.indexOf("Move-Item -LiteralPath $ServiceAppDir"));
    }

    @Test
    void parsesSupportedPostgresVersions() {
        assertEquals(17, PostgresRuntimeService.parsePostgresMajorVersion("psql (PostgreSQL) 17.5"));
        assertEquals(15, PostgresRuntimeService.parsePostgresMajorVersion("psql (PostgreSQL) 15.12"));
        assertEquals(0, PostgresRuntimeService.parsePostgresMajorVersion("command not found"));
    }

    @Test
    void macStartupWorksWithPackagedAppPathAndAvoidsUnneededBrewRestart() {
        String script = PostgresRuntimeService.macStartPostgresScript(5432);

        assertTrue(script.contains("/opt/homebrew/bin/pg_isready"));
        assertTrue(script.contains("/opt/homebrew/bin/brew"));
        assertTrue(script.contains("homebrew.mxcl.postgresql*.plist"));
        assertTrue(script.contains("launchctl kickstart \"gui/"));
        assertFalse(script.contains("launchctl kickstart -k"));
        assertTrue(script.indexOf("pg_isready_bin") < script.indexOf("services list"));
        assertFalse(script.contains("formula=\"$(brew services"));
    }

    @Test
    void validatesStoreLanScope() {
        assertTrue(PostgresRuntimeService.validLanSubnetForSetup("LocalSubnet"));
        assertTrue(PostgresRuntimeService.validLanSubnetForSetup("192.168.1.0/24"));
        assertFalse(PostgresRuntimeService.validLanSubnetForSetup(""));
        assertFalse(PostgresRuntimeService.validLanSubnetForSetup("anywhere"));
    }
}
