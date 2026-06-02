package services;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class SyncLockService {
    public static final String LOCK_NAME = "cloud_sync";
    private static final long ADVISORY_LOCK_ID = 0x51A7_570C_C10D_0001L;

    private SyncLockService() {
    }

    public static Optional<SyncLease> tryAcquire(Connection conn, String ownerLabel) throws SQLException {
        SyncSchemaInstaller.ensureSchema(conn);
        try (PreparedStatement ps = conn.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            ps.setLong(1, ADVISORY_LOCK_ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !rs.getBoolean(1)) {
                    return Optional.empty();
                }
            }
        }

        SyncLease lease = new SyncLease(conn, ownerId(), ownerLabel == null || ownerLabel.isBlank() ? "SmartStock Sync" : ownerLabel);
        lease.writeStatus();
        return Optional.of(lease);
    }

    public static LockInfo currentLock(Connection conn) throws SQLException {
        SyncSchemaInstaller.ensureSchema(conn);
        if (canAcquireLock(conn)) {
            clearStaleRow(conn);
            releaseProbeLock(conn);
            return LockInfo.idle();
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT owner_id, owner_label, acquired_at, heartbeat_at
                FROM sync_locks
                WHERE lock_name = ?
                """)) {
            ps.setString(1, LOCK_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return LockInfo.idle();
                }
                return new LockInfo(
                        true,
                        rs.getString("owner_id"),
                        rs.getString("owner_label"),
                        toInstant(rs.getTimestamp("acquired_at")),
                        toInstant(rs.getTimestamp("heartbeat_at"))
                );
            }
        }
    }

    private static boolean canAcquireLock(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            ps.setLong(1, ADVISORY_LOCK_ID);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static void releaseProbeLock(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            ps.setLong(1, ADVISORY_LOCK_ID);
            ps.executeQuery();
        }
    }

    private static void clearStaleRow(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM sync_locks WHERE lock_name = ?")) {
            ps.setString(1, LOCK_NAME);
            ps.executeUpdate();
        }
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String ownerId() {
        String runtime = ManagementFactory.getRuntimeMXBean().getName();
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            host = "unknown-host";
        }
        return host + "/" + runtime + "/" + UUID.randomUUID();
    }

    public static final class SyncLease implements AutoCloseable {
        private final Connection conn;
        private final String ownerId;
        private final String ownerLabel;
        private boolean closed;

        private SyncLease(Connection conn, String ownerId, String ownerLabel) {
            this.conn = conn;
            this.ownerId = ownerId;
            this.ownerLabel = ownerLabel;
        }

        public String ownerLabel() {
            return ownerLabel;
        }

        public void heartbeat() throws SQLException {
            try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE sync_locks
                    SET heartbeat_at = CURRENT_TIMESTAMP
                    WHERE lock_name = ?
                      AND owner_id = ?
                    """)) {
                ps.setString(1, LOCK_NAME);
                ps.setString(2, ownerId);
                ps.executeUpdate();
            }
        }

        private void writeStatus() throws SQLException {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO sync_locks (lock_name, owner_id, owner_label, acquired_at, heartbeat_at)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (lock_name)
                    DO UPDATE SET owner_id = EXCLUDED.owner_id,
                                  owner_label = EXCLUDED.owner_label,
                                  acquired_at = EXCLUDED.acquired_at,
                                  heartbeat_at = EXCLUDED.heartbeat_at
                    """)) {
                ps.setString(1, LOCK_NAME);
                ps.setString(2, ownerId);
                ps.setString(3, ownerLabel);
                ps.executeUpdate();
            }
        }

        @Override
        public void close() throws SQLException {
            if (closed) {
                return;
            }
            closed = true;
            try (PreparedStatement delete = conn.prepareStatement("""
                    DELETE FROM sync_locks
                    WHERE lock_name = ?
                      AND owner_id = ?
                    """)) {
                delete.setString(1, LOCK_NAME);
                delete.setString(2, ownerId);
                delete.executeUpdate();
            } finally {
                try (PreparedStatement unlock = conn.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                    unlock.setLong(1, ADVISORY_LOCK_ID);
                    unlock.executeQuery();
                }
            }
        }
    }

    public record LockInfo(
            boolean running,
            String ownerId,
            String ownerLabel,
            Instant acquiredAt,
            Instant heartbeatAt
    ) {
        static LockInfo idle() {
            return new LockInfo(false, null, null, null, null);
        }
    }
}
