package services;

import data.DB;
import data.DatabaseConfig;
import data.DatabaseMode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SyncWorker {
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "smartstock-sync-worker");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile boolean started;
    private static volatile SyncStatus latestStatus = SyncStatus.empty();

    private SyncWorker() {
    }

    public static synchronized void startIfServerMode() {
        DatabaseConfig config = DatabaseConfig.load();
        if (started || config.mode() != DatabaseMode.SERVER) {
            return;
        }
        if (!config.hasPrimaryConnection() || config.hasUnresolvedCredentialPlaceholders()) {
            latestStatus = latestStatus.withFailure(config.missingPrimaryConnectionMessage());
            return;
        }
        started = true;
        int interval = Math.max(15, config.syncIntervalSeconds());
        EXECUTOR.scheduleWithFixedDelay(SyncWorker::runOnceSafely, 2, interval, TimeUnit.SECONDS);
    }

    public static boolean isStarted() {
        return started;
    }

    public static void runOnceSafely() {
        runOnceSafely("Scheduled Sync Worker");
    }

    public static void runOnceSafely(String ownerLabel) {
        try {
            runOnce(ownerLabel);
        } catch (Exception ex) {
            latestStatus = latestStatus.withFailure(ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static SyncStatus runOnceNow() {
        runOnceSafely("Manual Sync");
        return latestStatus();
    }

    public static SyncStatus latestStatus() {
        try (Connection conn = DB.getConnection()) {
            return latestStatus(conn);
        } catch (Exception ex) {
            return latestStatus.withFailure(ex.getMessage());
        }
    }

    /** Server-side variant used by the LAN API without opening a second connection. */
    static SyncStatus latestStatus(Connection conn) throws SQLException {
        return latestStatus
                .withCounts(countPending(conn), countFailed(conn), countConflicts(conn))
                .withLock(SyncLockService.currentLock(conn))
                .withServiceInfo(SyncServiceStatusService.current(conn));
    }

    private static void runOnce(String ownerLabel) throws SQLException {
        DatabaseConfig config = DatabaseConfig.load();
        if (config.mode() != DatabaseMode.SERVER) {
            latestStatus = latestStatus.withMessage("Sync worker is inactive outside server mode.");
            return;
        }
        if (!config.hasPrimaryConnection() || config.hasUnresolvedCredentialPlaceholders()) {
            latestStatus = latestStatus.withFailure(config.missingPrimaryConnectionMessage());
            return;
        }
        ServerRoleGuard.State role = ServerRoleGuard.state();
        if (role != ServerRoleGuard.State.PRIMARY && role != ServerRoleGuard.State.DRAINING) {
            latestStatus = latestStatus.withMessage(ServerRoleGuard.safeMessage());
            return;
        }
        if (CloudServerRegistryService.currentInstanceId() == null) {
            latestStatus = latestStatus.withFailure("The store server registry identity must be verified before synchronization.");
            return;
        }
        try (Connection local = DB.getConnection()) {
            SyncSchemaInstaller.ensureSchema(local);
            EmailSchemaInstaller.ensureSchema(local);
            ServerImageAssetService.ensureSchema(local);
            try {
                CloudServerRegistryService.heartbeatCurrent(local,null);
            } catch (Exception registryFailure) {
                // A verified current primary remains local-first when coordination is unavailable.
                System.err.println("Store server registry preflight failed: " + registryFailure.getMessage());
            }
            role=ServerRoleGuard.state();
            if(role!=ServerRoleGuard.State.PRIMARY&&role!=ServerRoleGuard.State.DRAINING){
                latestStatus=latestStatus.withMessage(ServerRoleGuard.safeMessage());
                return;
            }
            Optional<SyncLockService.SyncLease> lease = SyncLockService.tryAcquire(local, ownerLabel);
            if (lease.isEmpty()) {
                SyncLockService.LockInfo lock = SyncLockService.currentLock(local);
                latestStatus = latestStatus.withLock(lock).withMessage("Sync already running"
                        + (lock.ownerLabel() == null ? "." : " by " + lock.ownerLabel() + "."));
                return;
            }
            try (SyncLockService.SyncLease ignored = lease.get()) {
                int automaticClosures = TimeClockAutoCloseService.processExpiredOpenPunches(local);
                ServerImageAssetService.SyncResult imageSync =
                        ServerImageAssetService.synchronize(local);
                ignored.heartbeat();
                int pushed;
                int downloadedEvents = 0;
                int mirroredRows = 0;
                if (!ServerSupabaseCredentials.isConfigured() || config.locationId() == null) {
                    throw new SQLException(
                            "Supabase Server Key and Store Location ID are required for cloud synchronization.");
                }
                try {
                    CloudSyncManifest.fetch();
                } catch (java.io.IOException ex) {
                    throw new SQLException(
                            "Cloud schema v1 could not be verified; synchronization is disabled.", ex);
                }
                CloudRowMirrorService.MirrorResult mirror =
                        CloudRowMirrorService.synchronize(local, config.locationId());
                mirroredRows = mirror.uploaded();
                CrossStoreTransferSyncService.announcePending(local,config.locationId());
                CrossStoreReferenceSyncService.announceChanges(local,config.locationId());
                try {
                    RegisterTransferService.synchronizeCompleted(local,config.locationId());
                } catch (SQLException registerTransferFailure) {
                    // Register-device handoff is supplementary and must not block
                    // operational store, schedule, transfer, or payroll events.
                    System.err.println("Register transfer refresh failed: "
                            + registerTransferFailure.getMessage());
                }
                ignored.heartbeat();
                CrossStoreInventoryService.RefreshResult crossStore =
                        CrossStoreInventoryService.refreshAll(local, config.locationId());
                ignored.heartbeat();
                CrossStoreSalesService.RefreshResult crossStoreSales =
                        CrossStoreSalesService.refreshAll(local, config.locationId());
                ignored.heartbeat();
                CrossStoreCustomerHistoryService.RefreshResult customerHistory =
                        CrossStoreCustomerHistoryService.refreshAll(local, config.locationId());
                ignored.heartbeat();
                CrossStoreRefundService.QueueResult crossStoreRefunds =
                        CrossStoreRefundService.synchronize(local,config.locationId());
                ignored.heartbeat();
                CloudSyncApi.ExchangeResult exchange =
                        CloudSyncApi.exchange(local, config.locationId());
                CrossStoreTransferSyncService.applyInbox(local,config.locationId());
                CrossStoreReferenceSyncService.applyInbox(local);
                pushed = mirror.uploaded() + exchange.acknowledged();
                downloadedEvents = exchange.downloaded();
                ImageCacheWarmupService.warmLocalCache(local);
                SyncServiceStatusService.mark(local, "Running", "Cloud reachable");
                String imageMessage = imageSync.uploaded() + imageSync.repaired() == 0 ? ""
                        : "; images uploaded=" + imageSync.uploaded()
                        + ", repaired=" + imageSync.repaired();
                String mirrorMessage = mirroredRows == 0 ? ""
                        : "; materialized rows=" + mirroredRows;
                String deltaMessage = downloadedEvents == 0 ? ""
                        : "; cloud deltas downloaded=" + downloadedEvents;
                String crossStoreMessage = crossStore.storesRefreshed() == 0 ? ""
                        : "; cross-store inventory rows=" + crossStore.rowsRefreshed();
                if (crossStore.storesFailed() > 0) {
                    crossStoreMessage += "; cross-store inventory stale stores=" + crossStore.storesFailed();
                }
                String crossStoreSalesMessage = "; cross-store sales=" + crossStoreSales.salesRefreshed();
                if (crossStoreSales.storesFailed() > 0) {
                    crossStoreSalesMessage += "; cross-store sales stale stores=" + crossStoreSales.storesFailed();
                }
                String crossStoreRefundMessage=crossStoreRefunds.sourceApplied()+crossStoreRefunds.destinationsApplied()==0?""
                        :"; cross-store refunds applied="+(crossStoreRefunds.sourceApplied()+crossStoreRefunds.destinationsApplied());
                String message = (automaticClosures == 0 ? "Cloud reachable"
                        : "Cloud reachable; automatically closed " + automaticClosures
                        + " stale time clock record"
                        + (automaticClosures == 1 ? "" : "s"))
                        + imageMessage + mirrorMessage + deltaMessage + crossStoreMessage + crossStoreSalesMessage + crossStoreRefundMessage + ".";
                latestStatus = new SyncStatus(true, message, Instant.now(), pushed,
                        countPending(local), countFailed(local), countConflicts(local), null,
                        SyncLockService.LockInfo.idle(), SyncServiceStatusService.current(local));
                try {
                    CloudServerRegistryService.heartbeatCurrent(local, null);
                } catch (Exception registryFailure) {
                    // Registry availability must never stop the current primary's POS or cloud backup work.
                    System.err.println("Store server registry heartbeat failed: " + registryFailure.getMessage());
                }
            }
        }
    }

    private static void markCloudStoreOnline(Connection cloud, Integer locationId) throws SQLException {
        if (locationId == null) return;
        try (PreparedStatement ps = cloud.prepareStatement("""
                INSERT INTO store_sync_status(location_id,status,message,last_success_at,last_seen_at,updated_at)
                VALUES (?, 'Online', 'Store synchronized successfully', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT(location_id) DO UPDATE SET status=EXCLUDED.status,message=EXCLUDED.message,
                    last_success_at=CURRENT_TIMESTAMP,last_seen_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
                """)) {
            ps.setInt(1, locationId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = cloud.prepareStatement("""
                UPDATE remote_admin_commands SET status='APPLIED_STORE',applied_at=CURRENT_TIMESTAMP,
                    updated_at=CURRENT_TIMESTAMP WHERE location_id=? AND status='PENDING_STORE'
                """)) {
            ps.setInt(1, locationId);
            ps.executeUpdate();
        }
    }

    private static int pushBatch(Connection local, Connection cloud) throws SQLException {
        String selectSql = """
                SELECT event_id, event_type, location_id, device_id, user_id, payload, created_at
                FROM sync_outbox
                WHERE status IN ('PENDING', 'FAILED')
                ORDER BY created_at, event_id
                LIMIT 50
                """;
        int pushed = 0;
        try (PreparedStatement ps = local.prepareStatement(selectSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID eventId = (UUID) rs.getObject("event_id");
                try {
                    upsertCloudEvent(cloud, rs);
                    markSynced(local, eventId);
                    pushed++;
                } catch (SQLException ex) {
                    markFailed(local, eventId, ex.getMessage());
                }
            }
        }
        return pushed;
    }

    private static void upsertCloudEvent(Connection cloud, ResultSet rs) throws SQLException {
        String appliedSql = """
                INSERT INTO sync_applied_events (
                    origin_event_id, event_type, origin_location_id, origin_device_id, cloud_reference
                )
                VALUES (?, ?, ?, ?, 'sync_outbox')
                ON CONFLICT (origin_event_id) DO NOTHING
                """;
        try (PreparedStatement ps = cloud.prepareStatement(appliedSql)) {
            ps.setObject(1, rs.getObject("event_id"));
            ps.setString(2, rs.getString("event_type"));
            setNullableInteger(ps, 3, (Integer) rs.getObject("location_id"));
            ps.setString(4, rs.getString("device_id"));
            ps.executeUpdate();
        }

        String eventSql = """
                INSERT INTO sync_outbox (
                    event_id, event_type, location_id, device_id, user_id, payload,
                    status, attempts, created_at, synced_at,
                    origin_event_id, origin_location_id, origin_device_id, origin_created_at
                )
                VALUES (?, ?, ?, ?, ?, ?::jsonb, 'RECEIVED_FROM_STORE', 0, CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """;
        try (PreparedStatement ps = cloud.prepareStatement(eventSql)) {
            ps.setObject(1, rs.getObject("event_id"));
            ps.setString(2, rs.getString("event_type"));
            setNullableInteger(ps, 3, (Integer) rs.getObject("location_id"));
            ps.setString(4, rs.getString("device_id"));
            setNullableInteger(ps, 5, (Integer) rs.getObject("user_id"));
            ps.setString(6, rs.getString("payload"));
            ps.setObject(7, rs.getObject("event_id"));
            setNullableInteger(ps, 8, (Integer) rs.getObject("location_id"));
            ps.setString(9, rs.getString("device_id"));
            ps.setTimestamp(10, rs.getTimestamp("created_at"));
            ps.executeUpdate();
        }
    }

    private static void markSynced(Connection conn, UUID eventId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE sync_outbox
                SET status = 'SYNCED_TO_CLOUD_OUTBOX', synced_at = CURRENT_TIMESTAMP, last_error = NULL
                WHERE event_id = ?
                """)) {
            ps.setObject(1, eventId);
            ps.executeUpdate();
        }
    }

    private static void markFailed(Connection conn, UUID eventId, String error) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE sync_outbox
                SET status = 'FAILED', attempts = attempts + 1, last_error = ?
                WHERE event_id = ?
                """)) {
            ps.setString(1, error);
            ps.setObject(2, eventId);
            ps.executeUpdate();
        }
    }

    private static int countPending(Connection conn) throws SQLException {
        return count(conn, "SELECT COUNT(*) FROM sync_outbox WHERE status IN ('PENDING', 'FAILED')");
    }

    private static int countFailed(Connection conn) throws SQLException {
        return count(conn, "SELECT COUNT(*) FROM sync_outbox WHERE status = 'FAILED'");
    }

    private static int countConflicts(Connection conn) throws SQLException {
        return count(conn, "SELECT COUNT(*) FROM sync_conflicts WHERE status = 'OPEN'");
    }

    private static int count(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    public record SyncStatus(
            boolean cloudReachable,
            String message,
            Instant lastSuccess,
            int lastPushed,
            int pendingCount,
            int failedCount,
            int conflictCount,
            String lastError,
            SyncLockService.LockInfo currentSync,
            SyncServiceStatusService.ServiceInfo serviceInfo
    ) {
        static SyncStatus empty() {
            return new SyncStatus(false, "Not run yet", null, 0, 0, 0, 0, null,
                    SyncLockService.LockInfo.idle(), null);
        }

        SyncStatus withFailure(String error) {
            return new SyncStatus(false, "Cloud sync failed", lastSuccess, lastPushed, pendingCount, failedCount, conflictCount, error,
                    currentSync(), serviceInfo());
        }

        SyncStatus withMessage(String message) {
            return new SyncStatus(cloudReachable, message, lastSuccess, lastPushed, pendingCount, failedCount, conflictCount, lastError,
                    currentSync(), serviceInfo());
        }

        SyncStatus withCounts(int pending, int failed, int conflicts) {
            return new SyncStatus(cloudReachable, message, lastSuccess, lastPushed, pending, failed, conflicts, lastError,
                    currentSync(), serviceInfo());
        }

        SyncStatus withLock(SyncLockService.LockInfo lockInfo) {
            return new SyncStatus(cloudReachable, message, lastSuccess, lastPushed, pendingCount, failedCount, conflictCount, lastError,
                    lockInfo, serviceInfo());
        }

        SyncStatus withServiceInfo(SyncServiceStatusService.ServiceInfo info) {
            return new SyncStatus(cloudReachable, message, lastSuccess, lastPushed, pendingCount, failedCount, conflictCount, lastError,
                    currentSync(), info);
        }
    }
}
