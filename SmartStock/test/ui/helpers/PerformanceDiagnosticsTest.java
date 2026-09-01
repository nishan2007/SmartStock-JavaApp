package ui.helpers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceDiagnosticsTest {
    @TempDir Path temp;

    @Test void checkoutTimingsPersistEvenWhenFastAndSanitizeLabels() throws Exception {
        String previous = System.getProperty("user.home");
        try {
            System.setProperty("user.home", temp.toString());
            PerformanceDiagnostics.recordAlways("checkout", "drawer-command\nforged=value",
                    System.nanoTime(), true, -1);
            String text = Files.readString(temp.resolve(".smartstock/checkout-timing.log"));
            assertTrue(text.matches("(?s)^\\d{4}-.*Z SmartStock timing.*"));
            assertTrue(text.contains("operation=drawer-command_forged_value"));
            assertTrue(text.contains("durationMs="));
            assertTrue(text.contains("success=true"));
            assertEquals(1, text.lines().count());
        } finally {
            if (previous == null) System.clearProperty("user.home");
            else System.setProperty("user.home", previous);
        }
    }

    @Test void rotatesAndRetainsOnlyOnePreviousLog() throws Exception {
        Path log = temp.resolve("checkout-timing.log");
        PerformanceDiagnostics.appendCheckoutTiming(log, "first\n", 1);
        PerformanceDiagnostics.appendCheckoutTiming(log, "second\n", 1);
        assertTrue(Files.readString(temp.resolve("checkout-timing.log.1")).contains("first"));
        PerformanceDiagnostics.appendCheckoutTiming(log, "third\n", 1);
        assertTrue(Files.readString(temp.resolve("checkout-timing.log.1")).contains("second"));
        assertTrue(Files.readString(log).contains("third"));
        try (var files = Files.list(temp)) { assertEquals(2, files.count()); }
    }

    @Test void inaccessibleLogDoesNotInterruptSale() throws Exception {
        Path file = temp.resolve("not-a-directory");
        Files.writeString(file, "occupied");
        assertDoesNotThrow(() -> PerformanceDiagnostics.appendCheckoutTiming(
                file.resolve("checkout-timing.log"), "test\n", 1024));
    }
}
