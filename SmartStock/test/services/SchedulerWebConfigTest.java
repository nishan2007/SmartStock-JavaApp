package services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SchedulerWebConfigTest {
    @TempDir Path directory;

    @Test void unconfiguredServerRetainsTemporaryTunnel() throws Exception {
        assertNull(SchedulerWebConfig.publicOrigin(null, directory.resolve("missing.properties")));
    }

    @Test void savedOriginSurvivesAbsenceOfLauncherEnvironment() throws Exception {
        Path settings = directory.resolve("scheduler.properties");
        Files.writeString(settings, "public.origin=https://scheduler.example.com/\n");
        assertEquals("https://scheduler.example.com", SchedulerWebConfig.publicOrigin(null, settings));
        assertEquals("https://override.example.com", SchedulerWebConfig.publicOrigin(
                "https://override.example.com", settings));
    }

    @Test void invalidSavedOriginFailsInsteadOfStartingTemporaryTunnel() throws Exception {
        Path settings = directory.resolve("scheduler.properties");
        for (String value : new String[]{"http://example.com", "https://example.com/scheduler/",
                "https://user:password@example.com", "https://example.com?query=1"}) {
            Files.writeString(settings, "public.origin=" + value);
            assertThrows(IOException.class, () -> SchedulerWebConfig.publicOrigin(null, settings));
        }
    }

    @Test void unreadableSettingsDoNotSilentlyChangePublicAddress() {
        assertThrows(IOException.class, () -> SchedulerWebConfig.publicOrigin(null, directory));
    }
}
