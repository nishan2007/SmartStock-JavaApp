package app;

import data.DB;
import data.DatabaseConfig;
import data.DatabaseMode;
import services.SyncServiceStatusService;
import services.SyncWorker;
import services.LanApiServer;
import services.ServerRoleGuard;

import java.sql.Connection;

public final class SyncServiceMain {
    private static volatile boolean running = true;
    private static volatile LanApiServer lanApiServer;

    private SyncServiceMain() {
    }

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            if (lanApiServer != null) {
                lanApiServer.close();
            }
            SyncServiceStatusService.mark("Stopped", "SmartStock background sync is stopping.");
        }, "smartstock-sync-shutdown"));

        System.out.println("Starting SmartStock background sync service.");
        try {
            if (DatabaseConfig.load().mode() == DatabaseMode.SERVER) {
                services.LocalDatabaseBootstrapService.reconcileConfiguredCredential();
            }
        } catch (Exception ex) {
            System.err.println("Saved local server credential reconciliation will retry during setup: "
                    + ex.getMessage());
        }
        SyncServiceStatusService.mark("Starting", "SmartStock background sync is starting.");
        while (running) {
            DatabaseConfig config = DatabaseConfig.load();
            int intervalSeconds = Math.max(15, config.syncIntervalSeconds());
            if (config.mode() != DatabaseMode.SERVER) {
                SyncServiceStatusService.mark("Waiting", "Database mode is " + config.mode() + "; background sync runs only in SERVER mode.");
                sleepSeconds(intervalSeconds);
                continue;
            }

            try (Connection connection = DB.getConnection()) {
                if (!services.ServerSetupGuardService.authorizeBackgroundService(connection)) {
                    stopLanApiForInactiveRole();
                    SyncServiceStatusService.mark("Waiting", ServerRoleGuard.safeMessage());
                    sleepSeconds(intervalSeconds);
                    continue;
                }
                SyncServiceStatusService.mark("Running", "Local database is online. Running cloud sync every " + intervalSeconds + " seconds.");
            } catch (Exception ex) {
                stopLanApiForInactiveRole();
                System.err.println("Server role or local database unavailable; waiting: " + ex.getMessage());
                SyncServiceStatusService.mark("Waiting", "Server role could not be verified: " + ex.getMessage());
                sleepSeconds(Math.min(intervalSeconds, 15));
                continue;
            }

            if (ServerRoleGuard.state() == ServerRoleGuard.State.RETIRED
                    || ServerRoleGuard.state() == ServerRoleGuard.State.FENCED
                    || ServerRoleGuard.state() == ServerRoleGuard.State.STANDBY) {
                stopLanApiForInactiveRole();
                SyncServiceStatusService.mark("Waiting", ServerRoleGuard.safeMessage());
                sleepSeconds(intervalSeconds);
                continue;
            }

            ensureLanApiStarted();

            SyncWorker.runOnceSafely(SyncServiceStatusService.processLabel("Background Sync"));
            SyncWorker.SyncStatus status = SyncWorker.latestStatus();
            if (status.lastError() == null) {
                SyncServiceStatusService.mark("Running", status.message());
            } else {
                SyncServiceStatusService.mark("Failed", status.lastError());
            }
            sleepSeconds(intervalSeconds);
        }
    }

    private static void ensureLanApiStarted() {
        if (lanApiServer != null) return;
        synchronized (SyncServiceMain.class) {
            if (lanApiServer != null) return;
            try {
                lanApiServer = LanApiServer.start();
                SyncServiceStatusService.mark("Running",
                        "SmartStock LAN service is online on HTTPS port " + LanApiServer.DEFAULT_PORT + ".");
            } catch (Exception ex) {
                System.err.println("SmartStock LAN service could not start: " + ex.getMessage());
                SyncServiceStatusService.mark("Failed", "LAN service could not start: " + ex.getMessage());
            }
        }
    }

    private static void stopLanApiForInactiveRole() {
        synchronized (SyncServiceMain.class) {
            if (lanApiServer != null) {
                lanApiServer.close();
                lanApiServer = null;
            }
        }
    }

    private static void sleepSeconds(int seconds) {
        try {
            Thread.sleep(Math.max(1, seconds) * 1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
