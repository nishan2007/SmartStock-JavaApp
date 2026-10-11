package ui.helpers;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** In-memory, session-scoped stale-while-revalidate data cache. */
public final class SessionDataCache {
    public static final Duration SCREEN_TTL = Duration.ofSeconds(30);
    public static final Duration NOTIFICATION_TTL = Duration.ofSeconds(60);
    public static final Duration REFERENCE_TTL = Duration.ofMinutes(5);

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final int MAX_ENTRIES = 512;
    private static volatile String scope = "anonymous";
    private static volatile String endpoint = "unconfigured";
    private static long generation;
    public static synchronized long generation(){return generation;}
    public static synchronized void putIfGeneration(long expected,String key,Object value){
        if(expected==generation)put(key,value);
    }

    private SessionDataCache() { }

    public static synchronized void setScope(String newScope) {
        String normalized = newScope == null || newScope.isBlank() ? "anonymous" : newScope;
        if (!normalized.equals(scope)) {
            scope = normalized;
            clear();
        }
    }

    public static synchronized void setEndpoint(String newEndpoint) {
        String normalized = newEndpoint == null || newEndpoint.isBlank() ? "unconfigured" : newEndpoint;
        if (!normalized.equals(endpoint)) {
            endpoint = normalized;
            clear();
        }
    }

    public static <T> Optional<CachedValue<T>> get(String key, Class<T> type, Duration ttl) {
        Entry entry = ENTRIES.get(scoped(key));
        if (entry == null || !type.isInstance(entry.value)) return Optional.empty();
        boolean fresh = entry.loadedAt.plus(ttl).isAfter(Instant.now());
        return Optional.of(new CachedValue<>(type.cast(entry.value), entry.loadedAt, fresh));
    }

    public static void put(String key, Object value) {
        if (value == null) return;
        ENTRIES.put(scoped(key), new Entry(value, Instant.now()));
        if (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.entrySet().stream().min(Map.Entry.comparingByValue(
                    java.util.Comparator.comparing(Entry::loadedAt)))
                    .ifPresent(oldest -> ENTRIES.remove(oldest.getKey(), oldest.getValue()));
        }
    }

    public static synchronized void invalidate(String keyPrefix) {
        String prefix = scoped(keyPrefix);
        invalidateMatching(key -> key.startsWith(prefix));
    }

    public static synchronized void invalidateMatching(Predicate<String> predicate) {
        generation++;
        ENTRIES.keySet().removeIf(predicate);
    }

    public static synchronized void clear() {
        generation++;
        ENTRIES.clear();
    }

    static int size() {
        return ENTRIES.size();
    }

    private static String scoped(String key) {
        return endpoint + "|" + scope + "|" + (key == null ? "" : key);
    }

    public record CachedValue<T>(T value, Instant loadedAt, boolean fresh) { }
    private record Entry(Object value, Instant loadedAt) { }
}
