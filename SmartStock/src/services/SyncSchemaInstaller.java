package services;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class SyncSchemaInstaller {
    private SyncSchemaInstaller() {
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        install(conn);
    }

    private static void install(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE EXTENSION IF NOT EXISTS pgcrypto");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_outbox (
                        event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        event_type TEXT NOT NULL,
                        location_id INTEGER,
                        device_id TEXT,
                        user_id INTEGER,
                        payload JSONB NOT NULL DEFAULT '{}'::jsonb,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        attempts INTEGER NOT NULL DEFAULT 0,
                        last_error TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        synced_at TIMESTAMPTZ,
                        origin_event_id UUID,
                        origin_location_id INTEGER,
                        origin_device_id TEXT,
                        origin_created_at TIMESTAMPTZ
                    )
                    """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS sync_outbox_status_created_idx ON sync_outbox(status, created_at)");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_applied_events (
                        origin_event_id UUID PRIMARY KEY,
                        event_type TEXT NOT NULL,
                        origin_location_id INTEGER,
                        origin_device_id TEXT,
                        applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        cloud_reference TEXT
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_id_map (
                        id_map_id BIGSERIAL PRIMARY KEY,
                        origin_event_id UUID,
                        table_name TEXT NOT NULL,
                        local_id TEXT NOT NULL,
                        cloud_id TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(table_name, local_id)
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_tombstones (
                        tombstone_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        table_name TEXT NOT NULL,
                        key_data JSONB NOT NULL DEFAULT '{}'::jsonb,
                        deleted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        origin_device_id TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(table_name, key_data)
                    )
                    """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS sync_tombstones_deleted_idx ON sync_tombstones(deleted_at DESC)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS sync_tombstones_table_idx ON sync_tombstones(table_name)");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_conflicts (
                        conflict_id BIGSERIAL PRIMARY KEY,
                        origin_event_id UUID,
                        event_type TEXT,
                        table_name TEXT,
                        local_id TEXT,
                        conflict_type TEXT NOT NULL,
                        local_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
                        cloud_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
                        status TEXT NOT NULL DEFAULT 'OPEN',
                        resolution_notes TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        resolved_at TIMESTAMPTZ,
                        resolved_by_user_id INTEGER,
                        resolved_by_name TEXT
                    )
                    """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS sync_conflicts_status_created_idx ON sync_conflicts(status, created_at)");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_audit_log (
                        sync_audit_id BIGSERIAL PRIMARY KEY,
                        action_type TEXT NOT NULL,
                        table_name TEXT,
                        local_id_before TEXT,
                        local_id_after TEXT,
                        cloud_id TEXT,
                        match_key TEXT,
                        status TEXT NOT NULL DEFAULT 'INFO',
                        details JSONB NOT NULL DEFAULT '{}'::jsonb,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS sync_audit_log_created_idx ON sync_audit_log(created_at DESC)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS sync_audit_log_table_created_idx ON sync_audit_log(table_name, created_at DESC)");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_locks (
                        lock_name TEXT PRIMARY KEY,
                        owner_id TEXT NOT NULL,
                        owner_label TEXT NOT NULL,
                        acquired_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        heartbeat_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_service_status (
                        service_id TEXT PRIMARY KEY,
                        status TEXT NOT NULL,
                        message TEXT,
                        started_at TIMESTAMPTZ,
                        last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
        SupabaseSecurityHardening.protectInternalTable(conn, "sync_outbox");
        SupabaseSecurityHardening.protectInternalTable(conn, "sync_applied_events");
        SupabaseSecurityHardening.protectInternalTable(conn, "sync_id_map");
        SupabaseSecurityHardening.protectInternalTable(conn, "sync_tombstones");
        SupabaseSecurityHardening.protectInternalTable(conn, "sync_conflicts");
        SupabaseSecurityHardening.protectInternalTable(conn, "sync_audit_log");
    }
}
