package ui.screens.customorders;

import java.util.UUID;
import java.util.Set;
import java.util.concurrent.Callable;
import services.LanApiClient;

/** EDT-owned state retaining the original operation after an uncertain response. */
final class CustomOrderSubmission<T> {
    private static final Set<String> REJECTED_CODES=Set.of("VALIDATION_ERROR",
            "CUSTOM_ORDER_CASH_DRAWER_REQUIRED","CUSTOM_ORDER_DEVICE_NOT_ALLOWED",
            "APPROVAL_REQUIRED","APPROVAL_INVALID","PERMISSION_DENIED",
            "CUSTOMER_PHONE_IN_USE","CUSTOMER_NOT_FOUND","CUSTOM_ORDER_REJECTED");
    private boolean busy;
    private Callable<T> pending;
    private String key;
    boolean begin() { if (busy) return false; busy = true; return true; }
    String key() { if (key == null) key = UUID.randomUUID().toString(); return key; }
    Callable<T> pending() { return pending; }
    void prepare(Callable<T> operation) { pending = operation; }
    void failed() { busy = false; }
    /** A server rejection rolled back the order, so the next save must use the edited draft. */
    boolean failed(Throwable failure) {
        Throwable cause=failure;
        while(cause.getCause()!=null)cause=cause.getCause();
        if(cause instanceof LanApiClient.LanApiException api && !api.retryable() && REJECTED_CODES.contains(api.code())) {
            completed();
            return true;
        }
        failed();
        return false;
    }
    void completed() { pending = null; key = null; busy = false; }
    boolean unresolved() { return busy || pending != null; }
}
