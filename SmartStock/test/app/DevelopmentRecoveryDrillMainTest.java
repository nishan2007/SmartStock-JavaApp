package app;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DevelopmentRecoveryDrillMainTest {
    @Test
    void drillIsDevelopmentOnlyAndRequiresAnExplicitEmptyTargetConfirmation() throws Exception {
        String source = Files.readString(Path.of("src/app/DevelopmentRecoveryDrillMain.java"));
        assertTrue(source.contains("Environment.DEVELOPMENT"));
        assertTrue(source.contains("--confirm-empty-target"));
        assertTrue(source.contains("ProductionRecoveryDrillService.run"));
        assertTrue(source.contains("SMARTSTOCK_RECOVERY_TARGET_DB_URL"));
    }
}
