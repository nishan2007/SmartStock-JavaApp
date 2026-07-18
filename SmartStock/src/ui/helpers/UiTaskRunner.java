package ui.helpers;

import javax.swing.SwingUtilities;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Runs blocking screen work away from Swing's event-dispatch thread. */
public final class UiTaskRunner {
    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();
    private static final int THREAD_COUNT = Math.max(4, Math.min(8, Runtime.getRuntime().availableProcessors()));
    private static final ExecutorService EXECUTOR = boundedExecutor("smartstock-ui-loader-");
    private static final ExecutorService COMPOSITE_EXECUTOR = boundedExecutor("smartstock-ui-part-");
    private static final Map<Object, Map<String, TaskHandle>> TASKS = new ConcurrentHashMap<>();
    private static final java.util.Set<Window> LISTENER_INSTALLED = ConcurrentHashMap.newKeySet();

    private UiTaskRunner() { }

    /** Uses the same bounded pool for independent parts of a composite screen snapshot. */
    public static <T> CompletableFuture<T> supplyAsync(Callable<T> background) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return background.call();
            } catch (Exception ex) {
                throw new java.util.concurrent.CompletionException(ex);
            }
        }, COMPOSITE_EXECUTOR);
    }

    public static <T> void submit(Window owner, String key, Callable<T> background,
                                  Consumer<T> success, Consumer<Throwable> failure) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(success, "success");
        Objects.requireNonNull(failure, "failure");
        installDisposalCancellation(owner);
        submitInternal(owner,owner::isDisplayable,key,background,success,failure);
    }

    private static <T> void submitInternal(Object owner, BooleanSupplier active, String key,
                                           Callable<T> background, Consumer<T> success,
                                           Consumer<Throwable> failure) {
        Map<String, TaskHandle> ownerTasks = TASKS.computeIfAbsent(owner, ignored -> new ConcurrentHashMap<>());
        TaskHandle previous = ownerTasks.get(key);
        long generation = previous == null ? 1 : previous.generation + 1;
        if (previous != null) previous.future.cancel(true);

        FutureTask<Void> future = new FutureTask<>(() -> {
            long started = System.nanoTime();
            try {
                T result = background.call();
                PerformanceDiagnostics.record("ui-load", key, started, true,
                        PerformanceDiagnostics.resultCount(result));
                deliver(owner, active, key, generation, () -> success.accept(result));
            } catch (Throwable ex) {
                PerformanceDiagnostics.record("ui-load", key, started, false, -1);
                deliver(owner, active, key, generation, () -> failure.accept(rootCause(ex)));
            }
            return null;
        });
        ownerTasks.put(key, new TaskHandle(generation, future));
        try {
            EXECUTOR.execute(future);
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            deliver(owner, active, key, generation, () -> failure.accept(rejected));
        }
    }

    public static void cancel(Window owner, String key) {
        cancelInternal(owner,key);
    }

    private static void cancelInternal(Object owner, String key) {
        Map<String, TaskHandle> ownerTasks = TASKS.get(owner);
        if (ownerTasks == null) return;
        TaskHandle handle = ownerTasks.remove(key);
        if (handle != null) handle.future.cancel(true);
        if (ownerTasks.isEmpty()) TASKS.remove(owner, ownerTasks);
    }

    public static void cancelAll(Window owner) {
        cancelAllInternal(owner);
    }

    private static void cancelAllInternal(Object owner) {
        Map<String, TaskHandle> ownerTasks = TASKS.remove(owner);
        if (ownerTasks == null) return;
        ownerTasks.values().forEach(handle -> handle.future.cancel(true));
        ownerTasks.clear();
    }

    public static int activeTaskCount(Window owner) {
        Map<String, TaskHandle> ownerTasks = TASKS.get(owner);
        return ownerTasks == null ? 0 : ownerTasks.size();
    }

    static <T> void submitForTests(Object owner, BooleanSupplier active, String key,
                                   Callable<T> background, Consumer<T> success,
                                   Consumer<Throwable> failure) {
        submitInternal(owner,active,key,background,success,failure);
    }

    static void cancelAllForTests(Object owner) {
        cancelAllInternal(owner);
    }

    private static void installDisposalCancellation(Window owner) {
        if (!LISTENER_INSTALLED.add(owner)) return;
        Runnable install = () -> owner.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) {
                cancelAll(owner);
                LISTENER_INSTALLED.remove(owner);
            }
        });
        if (SwingUtilities.isEventDispatchThread()) install.run(); else SwingUtilities.invokeLater(install);
    }

    private static void deliver(Object owner, BooleanSupplier active, String key, long generation, Runnable callback) {
        SwingUtilities.invokeLater(() -> {
            Map<String, TaskHandle> ownerTasks = TASKS.get(owner);
            TaskHandle current = ownerTasks == null ? null : ownerTasks.get(key);
            if (current == null || current.generation != generation) return;
            ownerTasks.remove(key, current);
            if (ownerTasks.isEmpty()) TASKS.remove(owner, ownerTasks);
            if (!active.getAsBoolean()) return;
            callback.run();
        });
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current;
    }

    private static ThreadFactory daemonThreads(String prefix) {
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + THREAD_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static ExecutorService boundedExecutor(String prefix) {
        return new ThreadPoolExecutor(THREAD_COUNT, THREAD_COUNT, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(256), daemonThreads(prefix), new ThreadPoolExecutor.AbortPolicy());
    }

    private record TaskHandle(long generation, Future<?> future) { }
}
