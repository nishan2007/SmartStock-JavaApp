package ui.screens.customorders;

import java.util.UUID;
import java.util.concurrent.Callable;

/** EDT-owned state retaining the original operation after an uncertain response. */
final class CustomOrderSubmission<T> {
    private boolean busy;
    private Callable<T> pending;
    private String key;
    boolean begin() { if (busy) return false; busy = true; return true; }
    String key() { if (key == null) key = UUID.randomUUID().toString(); return key; }
    Callable<T> pending() { return pending; }
    void prepare(Callable<T> operation) { pending = operation; }
    void failed() { busy = false; }
    void completed() { pending = null; key = null; busy = false; }
    boolean unresolved() { return busy || pending != null; }
}
