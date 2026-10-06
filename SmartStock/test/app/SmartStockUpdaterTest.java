package app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmartStockUpdaterTest {
    @Test
    void failureMessageKeepsServerReadinessContextAndUnderlyingDetail() throws Exception {
        var method = SmartStockUpdater.class.getDeclaredMethod("rootMessage", Throwable.class);
        method.setAccessible(true);
        String message = (String) method.invoke(null, new java.io.IOException(
                "The updated background server did not become ready. Check sync-service.log.",
                new java.nio.file.NoSuchFileException("lan-api.cer")));
        assertTrue(message.contains("background server did not become ready"));
        assertTrue(message.contains("sync-service.log"));
        assertTrue(message.contains("lan-api.cer"));
        String included = "The updated background server did not become ready: lan-api.cer";
        assertEquals(included, method.invoke(null, new java.io.IOException(included,
                new java.nio.file.NoSuchFileException("lan-api.cer"))));
    }
    @Test
    void replacementStagesCompleteLibrariesAndLauncherBeforeSwapping(@TempDir Path temp) throws Exception {
        Path app = temp.resolve("app"), payload = temp.resolve("payload");
        Files.createDirectories(app.resolve("dependency"));
        Files.createDirectories(payload.resolve("dependency"));
        Files.writeString(app.resolve("inventory-management-1.0.226.jar"), "old-app");
        Files.writeString(app.resolve("dependency/old.jar"), "old-library");
        Files.writeString(app.resolve("SmartStock.cfg"), "app.classpath=$APPDIR\\inventory-management-1.0.226.jar\n");
        Files.writeString(app.resolve("keep.txt"), "keep");
        Files.writeString(payload.resolve("inventory-management-1.0.231.jar"), "new-app");
        Files.writeString(payload.resolve("dependency/postgresql-test.jar"), "new-driver");
        SmartStockUpdater.replaceApp(app, payload);
        assertEquals("new-app", Files.readString(app.resolve("inventory-management-1.0.231.jar")));
        assertEquals("new-driver", Files.readString(app.resolve("dependency/postgresql-test.jar")));
        assertTrue(Files.readString(app.resolve("SmartStock.cfg")).contains("1.0.231.jar"));
        assertEquals("keep", Files.readString(app.resolve("keep.txt")));
        assertFalse(Files.exists(app.resolve("inventory-management-1.0.226.jar")));
    }

    @Test
    void invalidReplacementLeavesExistingInstallationIntact(@TempDir Path temp) throws Exception {
        Path app = temp.resolve("app"), payload = temp.resolve("payload");
        Files.createDirectories(app.resolve("dependency"));
        Files.createDirectories(payload.resolve("dependency"));
        Files.writeString(app.resolve("inventory-management-1.0.226.jar"), "old-app");
        Files.writeString(app.resolve("dependency/gson.jar"), "old-library");
        Files.writeString(payload.resolve("inventory-management-1.0.231.jar"), "new-app");
        assertThrows(java.io.IOException.class, () -> SmartStockUpdater.replaceApp(app, payload));
        assertEquals("old-app", Files.readString(app.resolve("inventory-management-1.0.226.jar")));
        assertEquals("old-library", Files.readString(app.resolve("dependency/gson.jar")));
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void lockedExecutableCannotLeaveLibrariesPartlyDeleted(@TempDir Path temp) throws Exception {
        Path app = temp.resolve("app"), payload = temp.resolve("payload");
        Files.createDirectories(app.resolve("dependency"));
        Files.createDirectories(payload.resolve("dependency"));
        Files.writeString(app.resolve("inventory-management-1.0.226.jar"), "old-app");
        Files.writeString(app.resolve("dependency/gson.jar"), "old-library");
        Path locked = app.resolve("dependency/cloudflared.exe");
        Files.writeString(locked, "locked");
        Files.writeString(payload.resolve("inventory-management-1.0.231.jar"), "new-app");
        Files.writeString(payload.resolve("dependency/postgresql-test.jar"), "new-driver");
        try (var channel = java.nio.channels.FileChannel.open(locked, java.nio.file.StandardOpenOption.READ,
                com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE)) {
            try {
                SmartStockUpdater.replaceApp(app, payload);
                assertEquals("new-driver", Files.readString(app.resolve("dependency/postgresql-test.jar")));
                assertTrue(Files.exists(app.resolve("inventory-management-1.0.231.jar")));
            } catch (java.io.IOException expected) {
                assertEquals("old-library", Files.readString(app.resolve("dependency/gson.jar")));
                assertEquals("old-app", Files.readString(app.resolve("inventory-management-1.0.226.jar")));
            }
        }
    }
    @Test
    void extractsPowerShellDirectoryEntries(@TempDir Path tempDir) throws Exception {
        Path zip = tempDir.resolve("release.zip");
        try (var out = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new java.util.zip.ZipEntry("dependency\\cloudflared\\"));
            out.closeEntry();
            out.putNextEntry(new java.util.zip.ZipEntry("dependency\\cloudflared\\windows-amd64\\cloudflared.exe"));
            out.write("test executable".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
        }
        var unzip = SmartStockUpdater.class.getDeclaredMethod("unzip", Path.class, Path.class);
        unzip.setAccessible(true);
        Path extracted = tempDir.resolve("extracted");
        unzip.invoke(null, zip, extracted);
        assertEquals("test executable", Files.readString(
                extracted.resolve("dependency/cloudflared/windows-amd64/cloudflared.exe")));
    }

    private static final Path PLIST = Path.of("/Users/test/Library/LaunchAgents/com.smartstock.sync.plist");

    @Test
    void buildsMacLaunchAgentLifecycleCommands() {
        assertEquals(
                List.of("/bin/launchctl", "bootout", "gui/501", PLIST.toString()),
                SmartStockUpdater.macLaunchctlCommand("bootout", "501", PLIST, "com.smartstock.sync"));
        assertEquals(
                List.of("/bin/launchctl", "bootstrap", "gui/501", PLIST.toString()),
                SmartStockUpdater.macLaunchctlCommand("bootstrap", "501", PLIST, "com.smartstock.sync"));
        assertEquals(
                List.of("/bin/launchctl", "kickstart", "-k", "gui/501/com.smartstock.sync"),
                SmartStockUpdater.macLaunchctlCommand("kickstart", "501", PLIST, "com.smartstock.sync"));
    }

    @Test
    void rejectsUnknownLaunchctlActions() {
        assertThrows(IllegalArgumentException.class,
                () -> SmartStockUpdater.macLaunchctlCommand("remove", "501", PLIST, "com.smartstock.sync"));
    }

    @Test
    void usesAbsoluteMacOpenCommandForRelaunch() {
        Path app = Path.of("/Applications/SmartStock.app");
        assertEquals(List.of("/usr/bin/open", "-n", app.toString()),
                SmartStockUpdater.macOpenCommand(app));
        assertEquals(List.of("/usr/bin/xattr", "-rc", app.toString()),
                SmartStockUpdater.macXattrCommand(app));
        assertEquals(List.of("/usr/bin/codesign", "--verify", "--deep", "--strict", app.toString()),
                SmartStockUpdater.macCodesignVerifyCommand(app));
    }

    @Test
    void rewritesBackgroundSyncLaunchersForUpdatedJar() {
        Path macAppDir = Path.of("/Users/test/.smartstock/sync-service/app");
        String macLauncher = SmartStockUpdater.syncLauncherContent(false, macAppDir, "inventory-management-1.0.11.jar");
        assertTrue(macLauncher.contains("-Duser.home="));
        assertTrue(macLauncher.contains("'inventory-management-1.0.11.jar' --sync-service"));
        assertFalse(macLauncher.contains("exec java "));

        Path windowsAppDir = Path.of("C:\\Users\\test\\.smartstock\\sync-service\\app");
        String windowsLauncher = SmartStockUpdater.syncLauncherContent(true, windowsAppDir, "inventory-management-1.0.11.jar");
        assertTrue(windowsLauncher.contains("%SMARTSTOCK_SERVER_JAR%"));
        assertTrue(windowsLauncher.contains("-Duser.home=\"C:\\Users\\test\""));
        assertFalse(windowsLauncher.contains("java -jar"));
        assertFalse(windowsLauncher.contains("1.0.11"));
    }

    @Test
    void replacesTheBackgroundServiceCopyAsACompleteDirectory(@TempDir Path tempDir) throws Exception {
        Path appDir = tempDir.resolve("installed-app");
        Path dependency = appDir.resolve("dependency");
        Files.createDirectories(dependency);
        Files.writeString(appDir.resolve("inventory-management-1.0.82.jar"), "new-app");
        Files.writeString(dependency.resolve("library.jar"), "new-library");
        Files.writeString(dependency.resolve("postgresql-42.7.3.jar"), "postgres-driver");
        Path tunnel=dependency.resolve("cloudflared/windows-amd64/cloudflared.exe");
        Files.createDirectories(tunnel.getParent());
        Files.writeString(tunnel,"verified-tunnel-fixture");

        Path syncAppDir = tempDir.resolve("sync-service").resolve("app");
        Files.createDirectories(syncAppDir.resolve("dependency"));
        Files.writeString(syncAppDir.resolve("inventory-management-1.0.81.jar"), "old-app");
        Files.writeString(syncAppDir.resolve("dependency").resolve("library.jar"), "old-library");

        Properties properties = new Properties();
        properties.setProperty("sync.service.app.dir", syncAppDir.toString());
        SmartStockUpdater.updateSyncServiceCopy(appDir, properties);

        assertEquals("verified-tunnel-fixture",Files.readString(syncAppDir.resolve("dependency/cloudflared/windows-amd64/cloudflared.exe")));

        assertFalse(Files.exists(syncAppDir.resolve("inventory-management-1.0.81.jar")));
        assertEquals("new-app", Files.readString(
                syncAppDir.resolve("inventory-management-1.0.82.jar")));
        assertEquals("new-library", Files.readString(
                syncAppDir.resolve("dependency").resolve("library.jar")));
    }

    @Test
    void eachUpdateReplacesThePreviousRollback(@TempDir Path tempDir) throws Exception {
        Path rollback = tempDir.resolve("rollback");
        Files.createDirectories(rollback);
        Files.writeString(rollback.resolve("older-copy.txt"), "old");

        SmartStockUpdater.prepareSingleRollbackDirectory(rollback);

        assertTrue(Files.isDirectory(rollback));
        assertFalse(Files.exists(rollback.resolve("older-copy.txt")));
        try (var files = Files.list(rollback)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void updatesNativeLauncherConfigsForInstalledJar(@TempDir Path appDir) throws Exception {
        String config = "[Application]\n"
                + "app.classpath=$APPDIR\\inventory-management-1.0.36.jar\n"
                + "app.mainclass=app.Main\n"
                + "app.classpath=$APPDIR\\dependency\\postgresql-42.7.3.jar\n\n"
                + "[JavaOptions]\n"
                + "java-options=-Djpackage.app-version=1.0.36\n";
        Path appConfig = appDir.resolve("SmartStock.cfg");
        Path serverConfig = appDir.resolve("SmartStockServer.cfg");
        Files.writeString(appConfig, config);
        Files.writeString(serverConfig, config + "\n[ArgOptions]\narguments=--sync-service\n");

        SmartStockUpdater.updateNativeLauncherConfigs(appDir, "inventory-management-1.0.37.jar");

        for (Path launcherConfig : List.of(appConfig, serverConfig)) {
            String updated = Files.readString(launcherConfig);
            assertTrue(updated.contains("app.classpath=$APPDIR\\inventory-management-1.0.37.jar"));
            assertTrue(updated.contains("java-options=-Djpackage.app-version=1.0.37"));
            assertTrue(updated.contains("app.classpath=$APPDIR\\dependency\\postgresql-42.7.3.jar"));
            assertFalse(updated.contains("inventory-management-1.0.36.jar"));
        }
    }

    @Test
    void rollbackRestoresNativeLauncherConfigsWithTheApplication(@TempDir Path tempDir) throws Exception {
        Path appDir = tempDir.resolve("app");
        Path backupDir = tempDir.resolve("rollback");
        Files.createDirectories(appDir.resolve("dependency"));
        Files.writeString(appDir.resolve("inventory-management-1.0.81.jar"), "old-app");
        Files.writeString(appDir.resolve("dependency").resolve("library.jar"), "old-library");
        Files.writeString(appDir.resolve("SmartStock.cfg"), "inventory-management-1.0.81.jar");
        Files.writeString(appDir.resolve("SmartStockServer.cfg"), "inventory-management-1.0.81.jar");

        SmartStockUpdater.backupCurrentApp(appDir, backupDir);
        Files.writeString(appDir.resolve("SmartStock.cfg"), "inventory-management-1.0.82.jar");
        Files.writeString(appDir.resolve("SmartStockServer.cfg"), "inventory-management-1.0.82.jar");
        SmartStockUpdater.restoreBackup(appDir, backupDir);

        assertEquals("inventory-management-1.0.81.jar",
                Files.readString(appDir.resolve("SmartStock.cfg")));
        assertEquals("inventory-management-1.0.81.jar",
                Files.readString(appDir.resolve("SmartStockServer.cfg")));
    }

    @Test
    void terminatesTheServerTreeWithoutKillingTheDescendantUpdater() {
        assertEquals(List.of("taskkill", "/F", "/T", "/IM", "SmartStockServer.exe"),
                SmartStockUpdater.windowsServerTerminationCommand());
        assertEquals(List.of("taskkill", "/F", "/IM", "SmartStock.exe"),
                SmartStockUpdater.windowsApplicationTerminationCommand());
    }

    @Test
    void identifiesOnlyTheSmartStockRuntimeSyncProcess() {
        Path java = Path.of("C:\\Program Files\\SmartStock\\runtime\\bin\\java.exe");

        assertTrue(SmartStockUpdater.isWindowsSyncServiceProcess(
                "C:\\Program Files\\SmartStock\\runtime\\bin\\javaw.exe",
                new String[]{"-jar", "inventory-management-1.0.46.jar", "--sync-service"}, java));
        assertFalse(SmartStockUpdater.isWindowsSyncServiceProcess(
                "C:\\Program Files\\SmartStock\\runtime\\bin\\javaw.exe",
                new String[]{"-jar", "inventory-management-1.0.46.jar"}, java));
        assertFalse(SmartStockUpdater.isWindowsSyncServiceProcess(
                "C:\\Other Java\\bin\\javaw.exe",
                new String[]{"-jar", "inventory-management-1.0.46.jar", "--sync-service"}, java));
    }

    @Test
    void identifiesOnlyTheCloudflareTunnelInsideTheSyncServiceCopy(@TempDir Path tempDir) {
        Path serviceApp = tempDir.resolve("sync-service").resolve("app").toAbsolutePath();
        Path bundledTunnel = serviceApp.resolve(
                "dependency/cloudflared/windows-amd64/cloudflared.exe");

        assertTrue(SmartStockUpdater.isWindowsSyncServiceCloudflareProcess(
                bundledTunnel.toString(), serviceApp));
        assertFalse(SmartStockUpdater.isWindowsSyncServiceCloudflareProcess(
                tempDir.resolve("other/cloudflared.exe").toString(), serviceApp));
        assertFalse(SmartStockUpdater.isWindowsSyncServiceCloudflareProcess(
                serviceApp.resolve("dependency/cloudflared/windows-amd64/helper.exe").toString(),
                serviceApp));
    }

    @Test
    void buildsAnExactPathWindowsCloudflareTerminationCommand() {
        List<String> command = SmartStockUpdater.windowsCloudflareTerminationCommand(
                Path.of("C:\\Users\\test\\.smartstock\\sync-service\\app"));

        assertTrue(command.get(0).endsWith("WindowsPowerShell\\v1.0\\powershell.exe"));
        String script = command.get(6);
        assertTrue(script.contains("Get-CimInstance Win32_Process"));
        assertTrue(script.contains("Name='cloudflared.exe'"));
        assertTrue(script.contains("C:\\Users\\test\\.smartstock\\sync-service\\app\\dependency\\cloudflared\\windows-amd64\\cloudflared.exe"));
        assertTrue(script.contains("OrdinalIgnoreCase"));
        assertTrue(script.contains("Invoke-CimMethod"));
        assertTrue(script.contains("ReturnValue"));
    }

    @Test
    void rewritesWindowsSyncTaskBeforeRestartingIt() {
        List<String> command = SmartStockUpdater.windowsSyncTaskUpdateCommand(
                "SmartStockServerService",
                Path.of("C:\\Program Files\\SmartStock\\runtime\\bin\\javaw.exe"),
                Path.of("C:\\Users\\test\\.smartstock\\sync-service\\app"),
                "inventory-management-1.0.52.jar", "STORE\\test");

        assertTrue(command.get(0).endsWith("WindowsPowerShell\\v1.0\\powershell.exe"));
        assertEquals(List.of("-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-Command"), command.subList(1, 6));
        String script = command.get(6);
        assertTrue(script.contains("New-ScheduledTaskAction"));
        assertFalse(script.contains("explorer.exe"));
        assertTrue(script.contains("-WorkingDirectory"));
        assertTrue(script.contains("javaw.exe"));
        assertTrue(script.contains("Get-ScheduledTask"));
        assertTrue(script.contains("Register-ScheduledTask"));
        assertTrue(script.contains("run-smartstock-sync-service.cmd"));
        assertFalse(script.contains("inventory-management-1.0.52.jar"));
        assertTrue(script.contains("CreateShortcut"));
        assertTrue(script.contains("Server task verification failed"));
        assertTrue(script.contains("$ErrorActionPreference='Stop'"));
        assertTrue(script.contains("C:\\Users\\test\\.smartstock\\sync-service\\app"));
        assertTrue(script.contains("Set-ScheduledTask -TaskName 'SmartStockServerService'"));
        assertTrue(script.contains("$serviceUser='STORE\\test'"));
    }

    @Test
    void refusesToEraseInstalledAppWhenRollbackIsMissing(@TempDir Path tempDir) throws Exception {
        Path appDir = tempDir.resolve("app");
        Files.createDirectories(appDir.resolve("dependency"));
        Files.writeString(appDir.resolve("inventory-management-1.0.81.jar"), "installed");

        assertThrows(java.io.IOException.class,
                () -> SmartStockUpdater.restoreBackup(appDir, tempDir.resolve("missing")));
        assertEquals("installed", Files.readString(
                appDir.resolve("inventory-management-1.0.81.jar")));
    }

    @Test
    void updaterStopsDesktopAndJavaBeforeTheSchedulerTunnel() throws Exception {
        String source = Files.readString(Path.of("src/app/SmartStockUpdater.java"));
        int method = source.indexOf("private static void stopSyncService");
        String body = source.substring(method, source.indexOf("private static void startSyncService", method));

        assertTrue(body.indexOf("terminateWindowsApplicationProcessTree()")
                < body.indexOf("runWindowsTaskCommand"));
        assertTrue(body.indexOf("terminateWindowsJavaSyncProcesses(props)")
                < body.indexOf("terminateWindowsSyncServiceCloudflareProcesses(props)"));
    }

    @Test
    void relaunchesThroughNativeWindowsLauncherWhenAvailable(@TempDir Path tempDir) throws Exception {
        Path launcher = tempDir.resolve("SmartStock.exe");
        Files.writeString(launcher, "launcher");
        Properties properties = new Properties();
        properties.setProperty("app.launcher.path", launcher.toString());

        assertEquals(List.of(launcher.toString()), SmartStockUpdater.relaunchCommand(
                properties, tempDir.resolve("java.exe"),
                tempDir.resolve("inventory-management-1.0.38.jar"), true));
    }
}
