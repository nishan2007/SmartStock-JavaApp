package ui.helpers;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import javax.swing.JButton;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiTaskRunnerTest {
    @Test
    void runsOffEdtAndDeliversOnEdt() throws Exception {
        Object owner = new Object();
        AtomicBoolean backgroundWasEdt = new AtomicBoolean(true);
        AtomicBoolean callbackWasEdt = new AtomicBoolean(false);
        CountDownLatch delivered = new CountDownLatch(1);
        UiTaskRunner.submitForTests(owner, () -> true, "delivery", () -> {
            backgroundWasEdt.set(SwingUtilities.isEventDispatchThread());
            return "done";
        }, value -> {
            callbackWasEdt.set(SwingUtilities.isEventDispatchThread());
            delivered.countDown();
        }, ignored -> delivered.countDown());
        assertTrue(delivered.await(3, TimeUnit.SECONDS));
        assertFalse(backgroundWasEdt.get());
        assertTrue(callbackWasEdt.get());
    }

    @Test
    void newerKeyedResultSuppressesOlderResponse() throws Exception {
        Object owner = new Object();
        CountDownLatch releaseOld = new CountDownLatch(1);
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<String> value = new AtomicReference<>();
        UiTaskRunner.submitForTests(owner, () -> true, "search", () -> {
            releaseOld.await(3, TimeUnit.SECONDS);
            return "old";
        }, value::set, ignored -> { });
        UiTaskRunner.submitForTests(owner, () -> true, "search", () -> "new", result -> {
            value.set(result);
            delivered.countDown();
        }, ignored -> delivered.countDown());
        assertTrue(delivered.await(3, TimeUnit.SECONDS));
        releaseOld.countDown();
        Thread.sleep(100);
        assertEquals("new", value.get());
    }

    @Test
    void inactiveOwnerPreventsDeliveryAndCancellationStopsTask() throws Exception {
        Object owner = new Object();
        AtomicBoolean active = new AtomicBoolean(true);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean delivered = new AtomicBoolean(false);
        UiTaskRunner.submitForTests(owner, active::get, "dispose", () -> {
            release.await(3, TimeUnit.SECONDS);
            return "late";
        }, ignored -> delivered.set(true), ignored -> delivered.set(true));
        active.set(false);
        UiTaskRunner.cancelAllForTests(owner);
        release.countDown();
        Thread.sleep(150);
        assertFalse(delivered.get());
    }

    @Test
    void simulatedSlowNetworkDoesNotDelayShellOrEdtInput() throws Exception {
        Object owner = new Object();
        AtomicReference<JButton> button = new AtomicReference<>();
        long started = System.nanoTime();
        SwingUtilities.invokeAndWait(() -> button.set(new JButton("Cancel")));
        long shellMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertTrue(shellMillis < 250, () -> "Shell took " + shellMillis + " ms");

        CountDownLatch dataDelivered = new CountDownLatch(1);
        CountDownLatch inputAccepted = new CountDownLatch(1);
        UiTaskRunner.submitForTests(owner, () -> true, "latency", () -> {
            Thread.sleep(500);
            return "loaded";
        }, ignored -> dataDelivered.countDown(), ignored -> dataDelivered.countDown());
        SwingUtilities.invokeLater(() -> {
            button.get().doClick();
            inputAccepted.countDown();
        });
        assertTrue(inputAccepted.await(200, TimeUnit.MILLISECONDS));
        assertTrue(dataDelivered.await(2, TimeUnit.SECONDS));
    }
}
