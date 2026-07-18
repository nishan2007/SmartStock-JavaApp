package ui.helpers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class BlockingCallGuardTest {
    @AfterEach
    void restoreTestGuard() {
        System.setProperty("smartstock.failOnEdtBlocking", "true");
    }

    @Test
    void failsAutomatedUiTestsWhenBlockingIoRunsOnEdt() throws Exception {
        System.setProperty("smartstock.failOnEdtBlocking", "true");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                BlockingCallGuard.check("test operation");
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
        assertInstanceOf(IllegalStateException.class, failure.get());
    }
}
