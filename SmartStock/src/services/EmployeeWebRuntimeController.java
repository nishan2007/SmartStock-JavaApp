package services;

import data.DB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Keeps the employee listener aligned with the active server's persisted console switch. */
public final class EmployeeWebRuntimeController implements AutoCloseable {
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(2);
    private final ScheduledExecutorService watcher;
    private EmployeeWebServer server;
    private CloudflareQuickTunnel quickTunnel;
    private String publicOrigin;
    private volatile boolean closed;
    private Connection lease;
    private Instant retryAfter = Instant.EPOCH;

    private Object runningGeneration;

    private EmployeeWebRuntimeController() {
        watcher = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "smartstock-employee-web-runtime");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static EmployeeWebRuntimeController start() {
        EmployeeWebRuntimeController controller = new EmployeeWebRuntimeController();
        controller.watcher.scheduleWithFixedDelay(controller::reconcile,
                0, HEARTBEAT_INTERVAL.toSeconds(), TimeUnit.SECONDS);
        return controller;
    }

    private synchronized void reconcile() {
        if (closed) return;
        try {
            if (ServerRoleGuard.state() != ServerRoleGuard.State.PRIMARY) {
                stopServer();
                releaseLease();
                return;
            }
            if (!acquireLease()) return;
            boolean enabled;
            Object requestedGeneration;
            try (Connection connection = DB.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT enabled,generation FROM employee_web_runtime WHERE runtime_id=1");
                 ResultSet rows = statement.executeQuery()) {
                enabled = rows.next() && rows.getBoolean(1);
                requestedGeneration = enabled ? rows.getObject(2) : null;
            }
            if (server != null && !java.util.Objects.equals(runningGeneration, requestedGeneration)) stopServer();
            if (quickTunnel != null && !quickTunnel.isAlive()) stopServer();
            if (enabled && server == null && !Instant.now().isBefore(retryAfter)) {
                startServer();
                runningGeneration = requestedGeneration;
            }
            if (!enabled && server != null) stopServer();
            if (closed) { stopServer(); return; }
            try (Connection connection = DB.getConnection(); PreparedStatement heartbeat = connection.prepareStatement(
                    "UPDATE employee_web_runtime SET gateway_heartbeat_at=?, gateway_running=?, public_origin=? WHERE runtime_id=1")) {
                heartbeat.setTimestamp(1, Timestamp.from(Instant.now()));
                heartbeat.setBoolean(2, server != null);
                heartbeat.setString(3, server == null ? null : publicOrigin);
                heartbeat.executeUpdate();
            }
        } catch (Exception exception) {
            stopServer();
            retryAfter = Instant.now().plusSeconds(30);
            releaseLease();
            System.err.println("Employee web runtime unavailable; retrying in 30 seconds ("
                    + exception.getClass().getSimpleName() + ").");
        }
    }

    // Session-level lock prevents a standalone gateway and installed service from owning
    // the same gateway. Explicit unlock is essential before returning a pooled connection.
    private boolean acquireLease() throws Exception {
        if (lease != null) {
            if (lease.isValid(2)) return true;
            releaseLease();
            stopServer();
        }
        Connection candidate = DB.getConnection();
        boolean acquired = false;
        try (PreparedStatement p = candidate.prepareStatement(
                "SELECT pg_try_advisory_lock(734982166)" ); ResultSet r = p.executeQuery()) {
            acquired = r.next() && r.getBoolean(1);
            if (acquired) lease = candidate;
            return acquired;
        } finally { if (!acquired) candidate.close(); }
    }

    private void releaseLease() {
        if (lease == null) return;
        try (PreparedStatement p = lease.prepareStatement("SELECT pg_advisory_unlock(734982166)")) {
            p.execute();
        } catch (Exception ignored) {
            try { lease.abort(Runnable::run); } catch (Exception ignoredAbort) { }
        } finally {
            try { lease.close(); } catch (Exception ignored) { }
            lease = null;
        }
    }

    private void startServer() throws Exception {
        String configuredOrigin = EmployeeWebConfig.publicOrigin();
        if (configuredOrigin == null || configuredOrigin.isBlank()) {
            int port = Integer.getInteger("smartstock.employee.web.port", EmployeeWebServer.DEFAULT_PORT);
            quickTunnel = CloudflareQuickTunnel.start(port);
            publicOrigin = quickTunnel.publicOrigin();
        } else publicOrigin = configuredOrigin.trim();
        try {
            server = EmployeeWebServer.start(publicOrigin);

        } catch (Exception exception) {
            if (quickTunnel != null) { quickTunnel.close(); quickTunnel = null; }
            publicOrigin = null;
            throw exception;
        }
    }

    private void stopServer() {
        if (server != null) {
            server.close();
            server = null;
        }
        if (quickTunnel != null) {
            quickTunnel.close();
            quickTunnel = null;
        }
        publicOrigin = null;
        runningGeneration = null;
    }

    @Override public void close() {
        closed = true;
        watcher.shutdownNow();
        synchronized (this) {
        stopServer();
        if (lease == null) return;
        try (Connection connection = DB.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE employee_web_runtime SET gateway_running=FALSE, gateway_heartbeat_at=? WHERE runtime_id=1")) {
            statement.setTimestamp(1, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        } catch (Exception ignored) { }
        finally { releaseLease(); }
        }
    }
}
