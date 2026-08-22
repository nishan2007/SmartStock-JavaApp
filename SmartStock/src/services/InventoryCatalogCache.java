package services;

import managers.SessionManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Session-scoped, shared catalog used by inventory search screens. */
public final class InventoryCatalogCache {
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "smartstock-inventory-catalog-warmup");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private static Snapshot snapshot;
    private static CompletableFuture<Snapshot> activeLoad;
    private static Scope activeScope;

    private InventoryCatalogCache() { }

    public static synchronized Optional<Snapshot> current() {
        Scope scope = currentScope();
        return snapshot != null && snapshot.scope().equals(scope) ? Optional.of(snapshot) : Optional.empty();
    }

    public static CompletableFuture<Snapshot> warmIfNeeded() {
        synchronized (InventoryCatalogCache.class) {
            Optional<Snapshot> current = current();
            if (current.isPresent()) return CompletableFuture.completedFuture(current.get());
        }
        return refresh();
    }

    public static synchronized CompletableFuture<Snapshot> refresh() {
        Scope requestedScope = currentScope();
        if (!requestedScope.isUsable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("No store is selected for this session."));
        }
        if (activeLoad != null && !activeLoad.isDone() && requestedScope.equals(activeScope)) return activeLoad;

        activeScope = requestedScope;
        activeLoad = CompletableFuture.supplyAsync(() -> {
            try {
                List<LanApiClient.CatalogProduct> products = List.copyOf(LanApiClient.searchCatalog(""));
                return new Snapshot(requestedScope, products, Instant.now());
            } catch (Exception ex) {
                throw new java.util.concurrent.CompletionException(ex);
            }
        }, LOADER).thenApply(loaded -> {
            synchronized (InventoryCatalogCache.class) {
                if (loaded.scope().equals(currentScope())) snapshot = loaded;
            }
            return loaded;
        });
        return activeLoad;
    }

    /** Ensures a catalog mutation is followed by a load that starts after any older load finishes. */
    public static CompletableFuture<Snapshot> refreshAfterMutation() {
        CompletableFuture<Snapshot> prior;
        Scope requestedScope = currentScope();
        synchronized (InventoryCatalogCache.class) {
            prior = activeLoad != null && !activeLoad.isDone() && requestedScope.equals(activeScope)
                    ? activeLoad : null;
        }
        if (prior == null) return refresh();
        return prior.handle((ignored, failure) -> null).thenCompose(ignored -> refresh());
    }

    public static synchronized void invalidate() {
        snapshot = null;
    }

    public static synchronized Status status() {
        Optional<Snapshot> current = current();
        boolean loading = activeLoad != null && !activeLoad.isDone();
        return new Status(loading, current.map(value -> value.products().size()).orElse(0),
                current.map(Snapshot::loadedAt).orElse(null));
    }

    private static Scope currentScope() {
        return new Scope(SessionManager.getCurrentUserId(), SessionManager.getCurrentLocationId());
    }

    public record Snapshot(Scope scope, List<LanApiClient.CatalogProduct> products, Instant loadedAt) { }
    public record Status(boolean loading, int productCount, Instant loadedAt) { }
    public record Scope(Integer userId, Integer locationId) {
        boolean isUsable() { return userId != null && locationId != null; }
    }
}
