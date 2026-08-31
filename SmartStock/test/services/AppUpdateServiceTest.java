package services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import managers.SupabaseSessionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppUpdateServiceTest {
    private static final String PROJECT_URL = "https://example.supabase.co";

    @Test
    void resolvesCurrentStorageSignedPathFormat() {
        assertEquals(
                PROJECT_URL + "/storage/v1/object/sign/releases/mac/app.zip?token=abc",
                AppUpdateService.resolveSignedDownloadUrl(
                        PROJECT_URL,
                        "/object/sign/releases/mac/app.zip?token=abc"));
    }

    @Test
    void preservesAbsoluteAndLegacySignedUrls() {
        assertEquals(
                "https://cdn.example/app.zip?token=abc",
                AppUpdateService.resolveSignedDownloadUrl(
                        PROJECT_URL,
                        "https://cdn.example/app.zip?token=abc"));
        assertEquals(
                PROJECT_URL + "/storage/v1/object/sign/releases/mac/app.zip?token=abc",
                AppUpdateService.resolveSignedDownloadUrl(
                        PROJECT_URL,
                        "/storage/v1/object/sign/releases/mac/app.zip?token=abc"));
    }

    @Test
    void refusesUpdatesFromMountedOrTranslocatedMacApps() {
        assertThrows(IOException.class,
                () -> AppUpdateService.validateMacUpdateLocation(
                        Path.of("/Volumes/SmartStock/SmartStock.app")));
        assertThrows(IOException.class,
                () -> AppUpdateService.validateMacUpdateLocation(
                        Path.of("/private/var/folders/x/AppTranslocation/y/SmartStock.app")));
    }

    @Test
    void badgeOnlySessionDoesNotAttemptSupabaseUpdateCheck() {
        assertEquals(false, AppUpdateService.hasMatchingSupabaseUpdateSession(
                null, null, 9, 1));
        assertEquals(true, AppUpdateService.hasMatchingSupabaseUpdateSession(
                "access-token", null, 9, 1));
        assertEquals(true, AppUpdateService.hasMatchingSupabaseUpdateSession(
                null, new SupabaseSessionManager.PersistedSession("old", "refresh", 9, 1), 9, 1));
    }

    @Test
    void updaterUsesOneStableRollbackDirectory() {
        Path rollback = AppUpdateService.rollbackDirectory();
        assertEquals("rollback", rollback.getFileName().toString());
        assertTrue(rollback.endsWith(Path.of(".smartstock", "rollback")));
    }

    @Test
    void windowsUpdaterTargetsInstalledServerTask() throws Exception {
        String source = Files.readString(Path.of("src/services/AppUpdateService.java"));
        assertTrue(source.contains("sync.service.task.name\", \"SmartStockServerService"));
        assertTrue(source.contains("sync.service.user\", windowsServiceUser()"));
        assertTrue(!source.contains("sync.service.task.name\", \"SmartStockBackgroundSync"));
    }

    @Test
    void windowsInstallerDeletesOldJarsOnlyAfterPayloadInstallation() throws Exception {
        String source = Files.readString(Path.of("tools/package-windows-release.ps1"));

        assertTrue(!source.contains("[InstallDelete]"));
        assertTrue(source.contains("if CurStep = ssPostInstall then"));
        assertTrue(source.contains("CompareText(FindRec.Name, '$($Jar.Name)')"));
    }

    @Test
    void windowsUpdaterRequestsElevationAndQuotesPaths() {
        List<String> command = AppUpdateService.buildWindowsElevatedUpdaterCommand(
                Path.of("C:\\Program Files\\SmartStock\\runtime\\bin\\javaw.exe"),
                Path.of("C:\\Users\\Test User\\stage's\\updater.jar"),
                Path.of("C:\\Users\\Test User\\stage's\\update.properties"));

        assertTrue(command.get(0).endsWith("WindowsPowerShell\\v1.0\\powershell.exe"));
        String script = command.get(command.size() - 1);
        assertTrue(script.contains("-Verb RunAs"));
        assertTrue(script.contains("-WindowStyle Hidden"));
        assertTrue(script.contains("-Wait -PassThru"));
        assertTrue(script.contains("exit $p.ExitCode"));
        assertTrue(script.contains("stage''s\\updater.jar"));
        assertTrue(script.contains("stage''s\\update.properties"));
    }

    @Test
    void removesOnlyStaleUpdateStages(@TempDir Path tempDir) throws Exception {
        Path staged = tempDir.resolve("staged-1.0.1-old");
        Path rollback = tempDir.resolve("rollback");
        Files.createDirectories(staged);
        Files.createDirectories(rollback);
        Files.writeString(staged.resolve("release.zip"), "old");
        Files.setLastModifiedTime(staged, java.nio.file.attribute.FileTime.from(
                java.time.Instant.now().minus(java.time.Duration.ofHours(25))));
        Files.writeString(tempDir.resolve("updater.log"), "keep");

        AppUpdateService.cleanupStaleStagingDirectories(tempDir);

        assertTrue(Files.notExists(staged));
        assertTrue(Files.isDirectory(rollback));
        assertTrue(Files.isRegularFile(tempDir.resolve("updater.log")));
    }

    @Test
    void preservesFreshUpdateStages(@TempDir Path tempDir) throws Exception {
        Path staged = tempDir.resolve("staged-1.0.2-active");
        Files.createDirectories(staged);

        AppUpdateService.cleanupStaleStagingDirectories(tempDir);

        assertTrue(Files.isDirectory(staged));
    }

    @Test
    void stagesUpdaterRunnerFromVerifiedIncomingRelease(@TempDir Path tempDir) throws Exception {
        Path release = tempDir.resolve("release.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(release))) {
            zip.putNextEntry(new ZipEntry("inventory-management-1.0.79.jar"));
            zip.write("incoming-updater".getBytes());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("dependency/example.jar"));
            zip.write("dependency".getBytes());
            zip.closeEntry();
        }
        Path runner = tempDir.resolve("smartstock-updater-runner.jar");

        AppUpdateService.extractUpdaterRunner(release, runner);

        assertEquals("incoming-updater", Files.readString(runner));
    }

    @Test
    void rejectsNestedOrAmbiguousUpdaterJars(@TempDir Path tempDir) throws Exception {
        Path release = tempDir.resolve("release.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(release))) {
            zip.putNextEntry(new ZipEntry("SmartStock/inventory-management-1.0.79.jar"));
            zip.write("nested".getBytes());
            zip.closeEntry();
        }

        assertThrows(IOException.class, () -> AppUpdateService.extractUpdaterRunner(
                release, tempDir.resolve("runner.jar")));
    }
}
