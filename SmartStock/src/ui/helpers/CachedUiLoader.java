package ui.helpers;

import ui.components.LoadingStatePanel;

import javax.swing.SwingUtilities;
import javax.swing.JComponent;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Connects session caching, background work, and a standard loading-state strip. */
public final class CachedUiLoader {
    private CachedUiLoader() { }

    public static <T> void load(Window owner, String key, Class<T> type, Duration ttl,
                                LoadingStatePanel state, Callable<T> background,
                                Consumer<T> apply) {
        load(owner, key, key, type, ttl, state, background, apply);
    }

    /** Separates the cancellation key from the filter-specific cache key. */
    public static <T> void load(Window owner, String jobKey, String cacheKey, Class<T> type, Duration ttl,
                                LoadingStatePanel state, Callable<T> background,
                                Consumer<T> apply) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> load(owner, jobKey, cacheKey, type, ttl, state, background, apply));
            return;
        }
        if (!owner.isDisplayable()) {
            WindowAdapter listener = new WindowAdapter() {
                @Override public void windowOpened(WindowEvent event) {
                    owner.removeWindowListener(this);
                    load(owner, jobKey, cacheKey, type, ttl, state, background, apply);
                }

                @Override public void windowClosed(WindowEvent event) {
                    owner.removeWindowListener(this);
                }
            };
            owner.addWindowListener(listener);
            return;
        }
        Optional<SessionDataCache.CachedValue<T>> cached = SessionDataCache.get(cacheKey, type, ttl);
        cached.ifPresent(value -> PerformanceDiagnostics.cacheHit(cacheKey, value.fresh()));
        cached.ifPresent(value -> apply.accept(value.value()));
        state.loading(cached.isPresent(), cached.map(SessionDataCache.CachedValue::loadedAt).orElse(Instant.now()));
        UiTaskRunner.submit(owner, jobKey, background, result -> {
            SessionDataCache.put(cacheKey, result);
            apply.accept(result);
            state.ready(Instant.now());
        }, failure -> state.failed(failure.getMessage(), cached.isPresent(),
                () -> load(owner, jobKey, cacheKey, type, ttl, state, background, apply)));
    }

    /** Uses a fresh cached value without starting another request; stale values remain cache-then-refresh. */
    public static <T> void loadIfStale(Window owner, String jobKey, String cacheKey, Class<T> type, Duration ttl,
                                       LoadingStatePanel state, Callable<T> background, Consumer<T> apply) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> loadIfStale(owner, jobKey, cacheKey, type, ttl, state, background, apply));
            return;
        }
        Optional<SessionDataCache.CachedValue<T>> cached = SessionDataCache.get(cacheKey, type, ttl);
        if (cached.isPresent() && cached.get().fresh()) {
            PerformanceDiagnostics.cacheHit(cacheKey, true);
            apply.accept(cached.get().value());
            state.ready(cached.get().loadedAt());
            return;
        }
        load(owner, jobKey, cacheKey, type, ttl, state, background, apply);
    }

    /** Starts once the component belongs to a displayable window, keeping constructors I/O-free. */
    public static <T> void loadAfterDisplay(JComponent component, String key, Class<T> type, Duration ttl,
                                            LoadingStatePanel state, Callable<T> background,
                                            Consumer<T> apply) {
        loadAfterDisplay(component, key, key, type, ttl, state, background, apply);
    }

    public static <T> void loadAfterDisplay(JComponent component, String jobKey, String cacheKey,
                                            Class<T> type, Duration ttl,
                                            LoadingStatePanel state, Callable<T> background,
                                            Consumer<T> apply) {
        AtomicBoolean started = new AtomicBoolean();
        Runnable attempt = new Runnable() {
            @Override public void run() {
                Window window = SwingUtilities.getWindowAncestor(component);
                if (window != null && window.isDisplayable() && started.compareAndSet(false, true)) {
                    load(window, jobKey + "@" + System.identityHashCode(component), cacheKey, type, ttl, state, background, apply);
                } else {
                    component.addHierarchyListener(event -> {
                        Window later = SwingUtilities.getWindowAncestor(component);
                        if (later != null && later.isDisplayable() && started.compareAndSet(false, true)) {
                            load(later, jobKey + "@" + System.identityHashCode(component), cacheKey, type, ttl, state, background, apply);
                        }
                    });
                }
            }
        };
        SwingUtilities.invokeLater(attempt);
    }
}
