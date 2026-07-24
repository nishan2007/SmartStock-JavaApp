package services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.io.IOException;
import java.nio.file.Path;

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
        assertTrue(rollback.toString().endsWith(".smartstock/rollback"));
    }

    @Test
    void removesOnlyStaleUpdateStages(@TempDir Path tempDir) throws Exception {
        Path staged = tempDir.resolve("staged-1.0.1-old");
        Path rollback = tempDir.resolve("rollback");
        Files.createDirectories(staged);
        Files.createDirectories(rollback);
        Files.writeString(staged.resolve("release.zip"), "old");
        Files.writeString(tempDir.resolve("updater.log"), "keep");

        AppUpdateService.cleanupStaleStagingDirectories(tempDir);

        assertTrue(Files.notExists(staged));
        assertTrue(Files.isDirectory(rollback));
        assertTrue(Files.isRegularFile(tempDir.resolve("updater.log")));
    }
}
