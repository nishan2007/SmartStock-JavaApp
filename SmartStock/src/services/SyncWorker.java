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
        try (Connection local = DB.getConnection()) {
            SyncSchemaInstaller.ensureSchema(local);
            EmailSchemaInstaller.ensureSchema(local);
            Optional<SyncLockService.SyncLease> lease = SyncLockService.tryAcquire(local, ownerLabel);
            if (lease.isEmpty()) {
                SyncLockService.LockInfo lock = SyncLockService.currentLock(local);
                latestStatus = latestStatus.withLock(lock).withMessage("Sync already running"
                        + (lock.ownerLabel() == null ? "." : " by " + lock.ownerLabel() + "."));
                return;
            }
            try (SyncLockService.SyncLease ignored = lease.get()) {
                int automaticClosures = TimeClockAutoCloseService.processExpiredOpenPunches(local);
                try (Connection cloud = DB.getCloudConnection()) {
                    SyncSchemaInstaller.ensureSchema(cloud);
                    EmailSchemaInstaller.ensureSchema(cloud);
                    ignored.heartbeat();
                    int timeClockSafetyPushes = ReferenceDataSyncService.pushTimeClockSafetyChanges(local, cloud);
                    ignored.heartbeat();
                    int holidaySyncChanges = ReferenceDataSyncService.syncScheduleHolidayChanges(local, cloud);
                    ignored.heartbeat();
                    int devicePushes = ReferenceDataSyncService.syncDevicesByUpdatedAt(local, cloud);
                    ignored.heartbeat();
                    int rowPushes = ReferenceDataSyncService.pushLocalOperationalChanges(local, cloud);
                    ignored.heartbeat();
                    int eventPushes = pushBatch(local, cloud);
                    int pushed = rowPushes + devicePushes + timeClockSafetyPushes
                            + holidaySyncChanges + eventPushes;
                    ImageCacheWarmupService.warmLocalCache(local);
                    SyncServiceStatusService.mark(local, "Running", "Cloud reachable");
                    String message = automaticClosures == 0 ? "Cloud reachable"
                            : "Cloud reachable; automatically closed " + automaticClosures + " stale time clock record"
                            + (automaticClosures == 1 ? "" : "s") + ".";
                    latestStatus = new SyncStatus(true, message, Instant.now(), pushed,
                            countPending(local), countFailed(local), countConflicts(local), null,
                            SyncLockService.LockInfo.idle(), SyncServiceStatusService.current(local));
                }
            }
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
