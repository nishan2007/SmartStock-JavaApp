package services;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
