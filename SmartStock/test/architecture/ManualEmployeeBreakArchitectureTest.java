package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualEmployeeBreakArchitectureTest {
    @Test
    void backgroundWorkerNeverEndsAnOpenEmployeeBreak() throws Exception {
        String source = Files.readString(Path.of("src/services/TimeClockAutoCloseService.java"));

        assertFalse(source.contains("processOverdueBreaks"));
        assertFalse(source.contains("SET break_end = break_start + INTERVAL"));
        assertFalse(source.contains("CURRENT_TIMESTAMP > break_start + INTERVAL"));
    }

    @Test
    void employeesStillHaveExplicitBreakStartAndEndActions() throws Exception {
        String source = Files.readString(Path.of("src/managers/ServerTimeClockManager.java"));

        assertTrue(source.contains("break_start"));
        assertTrue(source.contains("break_end"));
        assertTrue(source.contains("End your break before starting lunch."));
    }
}
