package ui.screens.customorders;

import org.junit.jupiter.api.Test;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderSubmissionTest {
    @Test void repeatedClicksCannotReplaceRunningSave() {
        var state = new CustomOrderSubmission<String>();
        assertTrue(state.begin());
        var key = state.key();
        Callable<String> original = () -> "CO-1";
        state.prepare(original);
        assertFalse(state.begin());
        assertSame(original, state.pending());
        assertEquals(key, state.key());
    }
    @Test void lostResponseRetriesOriginalOperationAndKey() throws Exception {
        var state = new CustomOrderSubmission<String>();
        var calls = new AtomicInteger();
        assertTrue(state.begin());
        var key = state.key();
        state.prepare(() -> {
            if (calls.incrementAndGet() == 1) throw new java.io.IOException("Response lost after commit");
            assertEquals(key, state.key());
            return "CO-1";
        });
        assertThrows(java.io.IOException.class, () -> state.pending().call());
        state.failed();
        assertTrue(state.unresolved());
        assertTrue(state.begin());
        assertEquals("CO-1", state.pending().call());
        state.completed();
        assertFalse(state.unresolved());
        assertNull(state.pending());
        assertTrue(state.begin());
        assertNotEquals(key, state.key());
    }
    @Test void cancelledPreparationAllowsAnotherAttempt() {
        var state = new CustomOrderSubmission<String>();
        assertTrue(state.begin());
        state.failed();
        assertFalse(state.unresolved());
        assertTrue(state.begin());
    }
}
