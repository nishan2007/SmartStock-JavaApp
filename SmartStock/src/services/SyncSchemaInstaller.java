package services;

import data.DatabaseConfig;
import data.DatabaseMode;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SyncSchemaInstaller {
    private static final Set<String> INSTALLED_DATABASES = ConcurrentHashMap.newKeySet();
    private static final Set<String> HARDENED_DATABASES = ConcurrentHashMap.newKeySet();

    private SyncSchemaInstaller() {
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        if (DatabaseConfig.load().mode() != DatabaseMode.SERVER) {
            return;
        }
        String key = databaseKey(conn);
        if (INSTALLED_DATABASES.contains(key)) {
            return;
        }
        synchronized (SyncSchemaInstaller.class) {
            if (INSTALLED_DATABASES.contains(key)) {
                return;
            }
            installTables(conn);
            INSTALLED_DATABASES.add(key);
        }
    }

    public static void ensureSecurityHardening(Connection conn) throws SQLException {
        if (DatabaseConfig.load().mode() != DatabaseMode.SERVER) {
            return;
        }
        String key = databaseKey(conn);
        if (HARDENED_DATABASES.contains(key)) {
            return;
        }
        synchronized (SyncSchemaInstaller.class) {
            if (HARDENED_DATABASES.contains(key)) {
                return;
            }
            SupabaseSecurityHardening.protectInternalTable(conn, "sync_outbox");
            SupabaseSecurityHardening.protectInternalTable(conn, "sync_cloud_state");
            SupabaseSecurityHardening.protectInternalTable(conn, "sync_inbox");
            SupabaseSecurityHardening.protectInternalTable(conn, "sync_row_mirror_state");
            SupabaseSecurityHardening.protectInternalTable(conn, "sync_row_mirror_completion");
            SupabaseSecurityHardening.protectInternalTable(conn, "sync_cross_store_inventory_cache");
            SupabaseSecurityHardening.protectInternalTable(conn, "sync_cross_store_inventory_status");
            SupabaseSecurityHardening.protectInternalTable(conn, "sync_cross_store_sales_cache");
            SupabaseSecurityHardening.protectInternalTable(conn, "sync_cross_store_sale_items_cache");
            SupabaseSecurityHardening.protectInternalTable(conn, "sync_cross_store_returns_cache");
            SupabaseSecurityHardening.protectInternalTable(conn, "sync_cross_store_return_items_cache");
            SupabaseSecurityHardening.protectInternalTable(conn, "sync_cross_store_sales_status");
            SupabaseSecurityHardening.protectInternalTable(conn, "cross_store_refund_requests");
            SupabaseSecurityHardening.protectInternalTable(conn, "cross_store_refund_lines");
            SupabaseSecurityHardening.protectInternalTable(conn, "cross_store_refund_reconciliation");
            SupabaseSecurityHardening.protectInternalTable(conn, "sync_transfer_metrics");
            SupabaseSecurityHardening.protectInternalTable(conn, "sync_applied_events");
            SupabaseSecurityHardening.protectInternalTable(conn, "sync_id_map");
            SupabaseSecurityHardening.protectInternalTable(conn, "sync_tombstones");
            SupabaseSecurityHardening.protectInternalTable(conn, "sync_conflicts");
            SupabaseSecurityHardening.protectInternalTable(conn, "sync_audit_log");
            SupabaseSecurityHardening.protectInternalTable(conn, "store_sync_status");
            SupabaseSecurityHardening.protectInternalTable(conn, "remote_admin_commands");
            HARDENED_DATABASES.add(key);
        }
    }

    private static String databaseKey(Connection conn) throws SQLException {
        String url = conn.getMetaData().getURL();
        String user = conn.getMetaData().getUserName();
        return (url == null ? "unknown" : url) + "|" + (user == null ? "" : user);
    }

    private static void installTables(Connection conn) throws SQLException {
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
                    CREATE TABLE IF NOT EXISTS sync_cloud_state (
                        state_id TEXT PRIMARY KEY,
                        cursor_value BIGINT NOT NULL DEFAULT 0,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_row_mirror_state (
                        location_id INTEGER NOT NULL,
                        table_name TEXT NOT NULL,
                        row_key TEXT NOT NULL,
                        row_hash TEXT NOT NULL,
                        materialized_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY(location_id,table_name,row_key)
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_row_mirror_completion (
                        location_id INTEGER PRIMARY KEY,
                        table_counts JSONB NOT NULL,
                        active_row_count BIGINT NOT NULL,
                        generation_id UUID,
                        completed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.executeUpdate("ALTER TABLE sync_row_mirror_completion ADD COLUMN IF NOT EXISTS generation_id UUID");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_cross_store_inventory_cache (
                        source_location_id INTEGER NOT NULL,
                        product_id INTEGER NOT NULL,
                        store_name TEXT NOT NULL,
                        sku TEXT,
                        barcode TEXT,
                        additional_barcodes TEXT,
                        product_name TEXT NOT NULL,
                        size TEXT,
                        description TEXT,
                        quantity_on_hand INTEGER NOT NULL DEFAULT 0,
                        reorder_level INTEGER NOT NULL DEFAULT 0,
                        source_updated_at TIMESTAMPTZ,
                        cache_refreshed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY(source_location_id,product_id)
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS sync_cross_store_inventory_store_name_idx
                    ON sync_cross_store_inventory_cache(source_location_id,product_name)
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_cross_store_inventory_status (
                        source_location_id INTEGER PRIMARY KEY,
                        store_name TEXT NOT NULL,
                        row_count INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL,
                        last_error TEXT,
                        refreshed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_cross_store_sales_cache(
                      source_location_id INTEGER NOT NULL,sale_id INTEGER NOT NULL,store_name TEXT NOT NULL,
                      receipt_number TEXT,customer_id INTEGER,user_name TEXT,payment_method TEXT,payment_status TEXT,
                      subtotal_amount NUMERIC(14,2) NOT NULL DEFAULT 0,discount_percent NUMERIC(8,4) NOT NULL DEFAULT 0,
                      discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0,total_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
                      amount_paid NUMERIC(14,2) NOT NULL DEFAULT 0,returned_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
                      source_created_at TIMESTAMPTZ,source_updated_at TIMESTAMPTZ,cache_refreshed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      cache_status TEXT NOT NULL DEFAULT 'CURRENT',PRIMARY KEY(source_location_id,sale_id))
                    """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS sync_cross_store_sales_search_idx ON sync_cross_store_sales_cache(source_location_id,source_created_at DESC,receipt_number)");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_cross_store_sale_items_cache(
                      source_location_id INTEGER NOT NULL,sale_id INTEGER NOT NULL,sale_item_id INTEGER NOT NULL,product_id INTEGER NOT NULL,
                      sku TEXT,product_name TEXT NOT NULL,product_type TEXT NOT NULL DEFAULT 'INVENTORY',quantity INTEGER NOT NULL DEFAULT 0,
                      unit_price NUMERIC(14,2) NOT NULL DEFAULT 0,original_unit_price NUMERIC(14,2) NOT NULL DEFAULT 0,
                      discount_percent NUMERIC(8,4) NOT NULL DEFAULT 0,discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
                      PRIMARY KEY(source_location_id,sale_item_id))
                    """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS sync_cross_store_sale_items_sale_idx ON sync_cross_store_sale_items_cache(source_location_id,sale_id)");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_cross_store_returns_cache(
                      source_location_id INTEGER NOT NULL,return_id BIGINT NOT NULL,sale_id INTEGER NOT NULL,user_name TEXT,refund_method TEXT,
                      refund_amount NUMERIC(14,2) NOT NULL DEFAULT 0,reason TEXT,source_created_at TIMESTAMPTZ,
                      PRIMARY KEY(source_location_id,return_id))
                    """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS sync_cross_store_returns_sale_idx ON sync_cross_store_returns_cache(source_location_id,sale_id)");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_cross_store_return_items_cache(
                      source_location_id INTEGER NOT NULL,return_item_id BIGINT NOT NULL,return_id BIGINT NOT NULL,sale_item_id INTEGER NOT NULL,
                      product_id INTEGER NOT NULL,quantity INTEGER NOT NULL DEFAULT 0,unit_price NUMERIC(14,2) NOT NULL DEFAULT 0,
                      PRIMARY KEY(source_location_id,return_item_id))
                    """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS sync_cross_store_return_items_sale_item_idx ON sync_cross_store_return_items_cache(source_location_id,sale_item_id)");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_cross_store_sales_status(
                      source_location_id INTEGER PRIMARY KEY,store_name TEXT NOT NULL,row_count INTEGER NOT NULL DEFAULT 0,
                      status TEXT NOT NULL,last_error TEXT,refreshed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS cross_store_refund_requests(
                      request_id UUID PRIMARY KEY,source_location_id INTEGER NOT NULL,receiving_location_id INTEGER NOT NULL,
                      source_sale_id INTEGER NOT NULL,cloud_request_sequence BIGINT,refund_method TEXT NOT NULL,
                      refund_amount NUMERIC(14,2) NOT NULL DEFAULT 0,reason TEXT NOT NULL,status TEXT NOT NULL,user_id INTEGER,
                      user_name TEXT,device_id UUID,cash_drawer_id BIGINT,cash_drawer_session_id BIGINT,last_error TEXT,
                      created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      UNIQUE(source_location_id,source_sale_id,request_id))
                    """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS cross_store_refund_requests_status_idx ON cross_store_refund_requests(status,created_at)");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS cross_store_refund_lines(
                      request_id UUID NOT NULL REFERENCES cross_store_refund_requests(request_id) ON DELETE CASCADE,
                      source_sale_item_id INTEGER NOT NULL,product_id INTEGER NOT NULL,quantity INTEGER NOT NULL CHECK(quantity>0),
                      unit_price NUMERIC(14,2) NOT NULL DEFAULT 0,disposition TEXT NOT NULL CHECK(disposition IN('RESTOCK','DISCARD')),
                      destination_location_id INTEGER,disposition_reason TEXT,confirmed_quantity INTEGER NOT NULL DEFAULT 0,
                      conflict_quantity INTEGER NOT NULL DEFAULT 0,destination_status TEXT NOT NULL DEFAULT 'PENDING',
                      PRIMARY KEY(request_id,source_sale_item_id))
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS cross_store_refund_reconciliation(
                      reconciliation_id BIGSERIAL PRIMARY KEY,request_id UUID NOT NULL REFERENCES cross_store_refund_requests(request_id),
                      source_sale_item_id INTEGER,source_location_id INTEGER NOT NULL,receiving_location_id INTEGER NOT NULL,product_id INTEGER,
                      conflict_quantity INTEGER NOT NULL DEFAULT 0,financial_loss NUMERIC(14,2) NOT NULL DEFAULT 0,status TEXT NOT NULL DEFAULT 'OPEN',
                      detail TEXT NOT NULL,resolved_by_user_id INTEGER,resolution_note TEXT,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,resolved_at TIMESTAMPTZ)
                    """);
            stmt.executeUpdate("ALTER TABLE sale_returns ADD COLUMN IF NOT EXISTS cross_store_request_id UUID");
            stmt.executeUpdate("ALTER TABLE sale_returns ADD COLUMN IF NOT EXISTS receiving_location_id INTEGER");
            stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS sale_returns_cross_store_request_uidx ON sale_returns(cross_store_request_id) WHERE cross_store_request_id IS NOT NULL");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_transfer_metrics (
                        metric_id BIGSERIAL PRIMARY KEY,
                        operation TEXT NOT NULL,
                        request_bytes BIGINT NOT NULL DEFAULT 0,
                        response_bytes BIGINT NOT NULL DEFAULT 0,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS sync_transfer_metrics_created_idx
                    ON sync_transfer_metrics(created_at DESC)
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sync_inbox (
                        cloud_sequence BIGINT PRIMARY KEY,
                        event_id UUID NOT NULL UNIQUE,
                        event_type TEXT NOT NULL,
                        location_id INTEGER,
                        device_id TEXT,
                        user_id INTEGER,
                        payload JSONB NOT NULL DEFAULT '{}'::jsonb,
                        origin_location_id INTEGER,
                        origin_device_id TEXT,
                        origin_created_at TIMESTAMPTZ,
                        status TEXT NOT NULL DEFAULT 'RECEIVED',
                        received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        applied_at TIMESTAMPTZ,
                        last_error TEXT
                    )
                    """);
            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS sync_inbox_status_sequence_idx ON sync_inbox(status, cloud_sequence)");
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
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS store_sync_status (
                        location_id INTEGER PRIMARY KEY REFERENCES locations(location_id) ON DELETE CASCADE,
                        status TEXT NOT NULL,
                        message TEXT,
                        last_success_at TIMESTAMPTZ,
                        last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS remote_admin_commands (
                        command_id UUID PRIMARY KEY,
                        location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
                        device_id UUID REFERENCES devices(device_id) ON DELETE SET NULL,
                        user_id INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
                        operation TEXT NOT NULL,
                        status TEXT NOT NULL,
                        details TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        applied_at TIMESTAMPTZ,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CHECK (status IN ('APPLIED_CLOUD', 'PENDING_STORE', 'APPLIED_STORE', 'REJECTED', 'CONFLICT'))
                    )
                    """);
        }
    }
}
