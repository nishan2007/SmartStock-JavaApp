package ui.helpers;

import javax.swing.SwingUtilities;

/** Detects accidental network/database access on Swing's event-dispatch thread. */
public final class BlockingCallGuard {
    private BlockingCallGuard() { }

    public static void check(String operation) {
        if (!SwingUtilities.isEventDispatchThread()) return;
        String message = "Blocking operation on Swing EDT: " + operation;
        if (Boolean.getBoolean("smartstock.failOnEdtBlocking")) throw new IllegalStateException(message);
        System.err.println("SmartStock performance warning: " + message);
    }
}
