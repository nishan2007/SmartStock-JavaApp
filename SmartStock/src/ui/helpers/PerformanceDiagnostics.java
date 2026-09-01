package ui.helpers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/** Low-cardinality performance logging with no request bodies or personal data. */
public final class PerformanceDiagnostics {
    private static final long SLOW_MILLIS = Long.getLong("smartstock.slowOperationMillis", 500L);
    private static final long MAX_CHECKOUT_LOG_BYTES = 1_048_576L;

    private PerformanceDiagnostics() { }

    public static void record(String category, String operation, long startedNanos,
                              boolean success, int resultCount) {
        long elapsedMillis = Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
        long threshold = "navigation".equals(category) ? 250L : SLOW_MILLIS;
        if (elapsedMillis < threshold && success) return;
        String count = resultCount < 0 ? "" : " count=" + resultCount;
        System.err.printf(Locale.ROOT,
                "SmartStock timing category=%s operation=%s durationMs=%d success=%s%s%n",
                safe(category), safe(operation), elapsedMillis, success, count);
    }

    /** Records an operation even when it is fast, for low-volume business-critical paths. */
    public static void recordAlways(String category, String operation, long startedNanos,
                                    boolean success, int resultCount) {
        long elapsedMillis = Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
        String count = resultCount < 0 ? "" : " count=" + resultCount;
        String line = String.format(Locale.ROOT,
                "SmartStock timing category=%s operation=%s durationMs=%d success=%s%s%n",
                safe(category), safe(operation), elapsedMillis, success, count);
        System.err.print(line);
        if ("checkout".equals(category)) {
            try {
                appendCheckoutTiming(Path.of(System.getProperty("user.home"), ".smartstock",
                        "checkout-timing.log"), line, MAX_CHECKOUT_LOG_BYTES);
            } catch (RuntimeException ignored) {
                // Invalid paths or denied property access must never interrupt a committed sale.
            }
        }
    }

    /** Best-effort, bounded local diagnostics; never includes requests or exception messages. */
    static synchronized void appendCheckoutTiming(Path log, String line, long maxBytes) {
        try {
            Files.createDirectories(log.getParent());
            if (Files.exists(log) && Files.size(log) >= maxBytes) {
                Files.move(log, log.resolveSibling(log.getFileName() + ".1"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(log, Instant.now() + " " + line,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (java.io.IOException | RuntimeException ignored) {
            // Logging is optional: disk full, permissions, or rotation failures cannot fail a sale.
        }
    }

    public static void cacheHit(String operation, boolean fresh) {
        System.err.printf(Locale.ROOT,
                "SmartStock timing category=cache operation=%s hit=true fresh=%s%n",
                safe(operation), fresh);
    }

    /** Returns only a safe aggregate count; object contents are never logged. */
    public static int resultCount(Object result) {
        if (result == null) return 0;
        if (result instanceof Collection<?> collection) return collection.size();
        if (result instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                int nested = resultCount(value);
                if (nested >= 0) return nested;
            }
            return map.size();
        }
        if (result instanceof JsonArray array) return array.size();
        if (result instanceof JsonObject object) {
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (entry.getValue().isJsonArray()) return entry.getValue().getAsJsonArray().size();
            }
            return object.size();
        }
        if (result.getClass().isArray()) return Array.getLength(result);
        return -1;
    }

    private static String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("[^a-zA-Z0-9._/-]", "_");
    }
}
