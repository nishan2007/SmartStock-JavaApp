package services;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ProductionRecoveryDrillServiceTest {
    @Test
    void readinessRejectsMissingRecoveryEvidence() {
        Path missing = Path.of(System.getProperty("java.io.tmpdir"),
                "smartstock-no-such-recovery-evidence.json");
        try {
            Files.deleteIfExists(missing);
        } catch (Exception ignored) {
        }
        assertFalse(ProductionReadinessService.recoveryEvidenceCheck(missing).passed());
    }
}
