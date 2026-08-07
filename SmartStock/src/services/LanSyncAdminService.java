package services;

import data.DatabaseConfig;
import data.DatabaseMode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-only sync administration boundary used by approved registers. */
final class LanSyncAdminService {
    private LanSyncAdminService() { }

    static Map<String, Object> status(Connection connection, int userId) throws Exception {
        requirePermission(connection, userId);
        SyncSchemaInstaller.ensureSchema(connection);
        SyncWorker.SyncStatus worker = SyncWorker.latestStatus(connection);
        Map<String, Object> result = statusMap(worker);
        addImageCounts(connection, result);
        addRuntimeState(result, worker.serviceInfo());
        result.put("conflicts", conflicts(connection));
        result.put("audits", audits(connection));
        return result;
    }

    static Map<String, Object> runNow(Connection connection, int userId) throws Exception {
        requirePermission(connection, userId);
        if (DatabaseConfig.load().mode() != DatabaseMode.SERVER) {
            throw new RuleViolation(409, "SERVER_MODE_REQUIRED", "Cloud synchronization runs on the SmartStock server.");
        }
        Map<String,Object> result = statusMap(SyncWorker.runOnceNow());
        addImageCounts(connection, result);
        addRuntimeState(result, SyncServiceStatusService.current(connection));
        return result;
    }

    static Map<String, Object> resolve(Connection connection, long conflictId, int userId) throws Exception {
        requirePermission(connection, userId);
        if (conflictId <= 0) throw new RuleViolation(400, "VALIDATION_ERROR", "Select a valid sync conflict.");
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE sync_conflicts
                SET status='RESOLVED', resolved_at=CURRENT_TIMESTAMP,
                    resolution_notes='Resolved from SmartStock sync status screen'
                WHERE conflict_id=? AND status='OPEN'
                """)) {
            ps.setLong(1, conflictId);
            if (ps.executeUpdate() != 1) {
                throw new RuleViolation(409, "CONFLICT_CHANGED", "That sync conflict is no longer open.");
            }
        }
        return Map.of("conflictId", conflictId, "status", "RESOLVED");
    }

    private static List<Map<String, Object>> conflicts(Connection connection) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT conflict_id,event_type,conflict_type,status,created_at
                FROM sync_conflicts WHERE status='OPEN'
                ORDER BY created_at DESC LIMIT 200
                """); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(map(
                    "conflictId", rs.getLong(1), "eventType", rs.getString(2),
                    "conflictType", rs.getString(3), "status", rs.getString(4),
                    "createdAtEpochMillis", epoch(rs.getTimestamp(5))));
        }
        return rows;
    }

    private static List<Map<String, Object>> audits(Connection connection) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT created_at,action_type,table_name,local_id_before,local_id_after,
                       cloud_id,match_key,status,details
                FROM sync_audit_log ORDER BY created_at DESC,sync_audit_id DESC LIMIT 300
                """); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(map(
                    "createdAtEpochMillis", epoch(rs.getTimestamp(1)), "actionType", rs.getString(2),
                    "tableName", rs.getString(3), "localIdBefore", rs.getString(4),
                    "localIdAfter", rs.getString(5), "cloudId", rs.getString(6),
                    "matchKey", rs.getString(7), "status", rs.getString(8), "details", rs.getString(9)));
        }
        return rows;
    }

    private static Map<String, Object> statusMap(SyncWorker.SyncStatus status) {
        SyncLockService.LockInfo lock = status.currentSync();
        SyncServiceStatusService.ServiceInfo service = status.serviceInfo();
        return map(
                "cloudReachable", status.cloudReachable(), "message", status.message(),
                "lastSuccessEpochMillis", epoch(status.lastSuccess()), "lastPushed", status.lastPushed(),
                "pendingCount", status.pendingCount(), "failedCount", status.failedCount(),
                "conflictCount", status.conflictCount(), "lastError", status.lastError(),
                "lockRunning", lock != null && lock.running(),
                "lockOwner", lock == null ? "" : lock.ownerLabel(),
                "lockAcquiredEpochMillis", lock == null ? 0L : epoch(lock.acquiredAt()),
                "serviceStatus", service == null ? "Unknown" : service.status(),
                "serviceMessage", service == null ? "" : service.message(),
                "serviceLastSeenEpochMillis", service == null ? 0L : epoch(service.lastSeenAt()));
    }

    private static void addImageCounts(Connection connection, Map<String,Object> result) throws SQLException {
        ServerImageAssetService.Counts counts = ServerImageAssetService.counts(connection);
        result.put("imagePendingUploads", counts.pendingUploads());
        result.put("imageMissingLocal", counts.missingLocal());
        result.put("imageMissingCloud", counts.missingCloud());
        result.put("imageUnused", counts.unused());
        result.put("imageFailedPurges", counts.failedPurges());
    }

    private static void addRuntimeState(Map<String, Object> result,
                                        SyncServiceStatusService.ServiceInfo service) {
        result.put("serverWorkerStarted", SyncWorker.isStarted()
                || isFreshServiceLoop(service, Instant.now(),
                DatabaseConfig.load().syncIntervalSeconds()));
    }

    static boolean isFreshServiceLoop(SyncServiceStatusService.ServiceInfo service,
                                      Instant now, int intervalSeconds) {
        if (service == null || service.lastSeenAt() == null || now == null) return false;
        String status = service.status() == null ? "" : service.status().trim();
        if (status.equalsIgnoreCase("Stopped")
                || status.equalsIgnoreCase("Not Installed")
                || status.equalsIgnoreCase("Unknown")) return false;
        long freshnessSeconds = Math.max(60L, Math.max(15, intervalSeconds) * 2L + 30L);
        return !service.lastSeenAt().isBefore(now.minus(Duration.ofSeconds(freshnessSeconds)));
    }

    private static void requirePermission(Connection connection, int userId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id
                JOIN permissions p ON p.permission_id=rp.permission_id
                WHERE u.user_id=? AND UPPER(p.permission_key)='SYNC_NOTIFICATIONS' LIMIT 1
                """)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }
        }
        throw new RuleViolation(403, "PERMISSION_DENIED", "You do not have permission to manage synchronization.");
    }

    private static long epoch(Timestamp value) { return value == null ? 0L : value.getTime(); }
    private static long epoch(Instant value) { return value == null ? 0L : value.toEpochMilli(); }
    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i=0;i<values.length;i+=2) result.put((String) values[i], values[i+1]);
        return result;
    }

    static final class RuleViolation extends Exception {
        private final int status; private final String code; private final String safeMessage;
        RuleViolation(int status, String code, String safeMessage) {
            super(safeMessage); this.status=status; this.code=code; this.safeMessage=safeMessage;
        }
        int status(){return status;} String code(){return code;} String safeMessage(){return safeMessage;}
    }
}
