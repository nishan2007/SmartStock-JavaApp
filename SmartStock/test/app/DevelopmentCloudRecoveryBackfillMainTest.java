package app;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DevelopmentCloudRecoveryBackfillMainTest {
    @Test
    void backfillIsDevelopmentOnlyAndRequiresExplicitLocations() throws Exception {
        String source = Files.readString(Path.of(System.getProperty("user.dir"),
                "src/app/DevelopmentCloudRecoveryBackfillMain.java"));
        assertTrue(source.contains("SupabaseProjectConfig.Environment.DEVELOPMENT"));
        assertTrue(source.contains("args.length == 0"));
        assertTrue(source.contains("requireLocalLocation"));
        assertTrue(source.contains("hasVerifiedSnapshot"));
    }
}
