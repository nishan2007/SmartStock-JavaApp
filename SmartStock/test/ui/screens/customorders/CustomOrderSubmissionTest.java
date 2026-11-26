package ui.screens.customorders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import services.LanApiClient;
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

    @ParameterizedTest
    @ValueSource(strings={"CUSTOMER_PHONE_IN_USE","CUSTOMER_NOT_FOUND","CUSTOM_ORDER_REJECTED","VALIDATION_ERROR"})
    void rejectedOrderCanBeCorrectedToUseAnExistingAccount(String code)throws Exception {
        var state=new CustomOrderSubmission<String>();
        assertTrue(state.begin());
        String rejectedKey=state.key();
        state.prepare(()->{throw new java.sql.SQLException("Request failed",new LanApiClient.LanApiException(code,"Order rejected",false));});
        var rejection=assertThrows(java.sql.SQLException.class,()->state.pending().call());
        assertTrue(state.failed(rejection));
        assertFalse(state.unresolved());
        assertNull(state.pending());
        // Selecting the existing account now builds a fresh request, rather than retrying the rejected one.
        assertTrue(state.begin());
        assertNotEquals(rejectedKey,state.key());
        state.prepare(()->"Existing account 42");
        assertEquals("Existing account 42",state.pending().call());
    }

    @Test void uncertainServerResponseStillRetainsTheOriginalRequest()throws Exception {
        var state=new CustomOrderSubmission<String>();
        assertTrue(state.begin());
        String originalKey=state.key();
        Callable<String> original=()->"Original order";
        state.prepare(original);
        assertFalse(state.failed(new LanApiClient.LanApiException("SERVER_UNREACHABLE","Response lost",true)));
        assertTrue(state.unresolved());
        assertTrue(state.begin());
        assertEquals(originalKey,state.key());
        assertSame(original,state.pending());
    }
}
