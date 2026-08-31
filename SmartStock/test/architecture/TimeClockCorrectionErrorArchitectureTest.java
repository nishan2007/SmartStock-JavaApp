package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class TimeClockCorrectionErrorArchitectureTest {
    @Test
    void safeCorrectionValidationReachesTheManager() throws Exception {
        String server=Files.readString(Path.of("src/services/LanApiServer.java"));
        assertTrue(server.contains("safeTimeClockChangeMessage(e)"));
        assertTrue(server.contains("error.getSQLState()==null"));
        assertTrue(server.contains("The time-clock change could not be completed."));
    }
}
