package app;

import data.DB;
import data.DatabaseConfig;
import data.DatabaseMode;
import services.SyncServiceStatusService;
import services.SyncWorker;

import java.sql.Connection;

public final class SyncServiceMain {
    private static volatile boolean running = true;

    private SyncServiceMain() {
    }

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            SyncServiceStatusService.mark("Stopped", "SmartStock background sync is stopping.");
        }, "smartstock-sync-shutdown"));

        System.out.println("Starting SmartStock background sync service.");
        SyncServiceStatusService.mark("Starting", "SmartStock background sync is starting.");
        while (running) {
            DatabaseConfig config = DatabaseConfig.load();
            int intervalSeconds = Math.max(15, config.syncIntervalSeconds());
            if (config.mode() != DatabaseMode.SERVER) {
                SyncServiceStatusService.mark("Waiting", "Database mode is " + config.mode() + "; background sync runs only in SERVER mode.");
                sleepSeconds(intervalSeconds);
                continue;
            }

            try (Connection ignored = DB.getConnection()) {
                SyncServiceStatusService.mark("Running", "Local database is online. Running cloud sync every " + intervalSeconds + " seconds.");
            } catch (Exception ex) {
                System.err.println("Local database unavailable; waiting: " + ex.getMessage());
                sleepSeconds(Math.min(intervalSeconds, 15));
                continue;
            }

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

    private static void sleepSeconds(int seconds) {
        try {
            Thread.sleep(Math.max(1, seconds) * 1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
