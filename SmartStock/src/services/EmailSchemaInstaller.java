package services;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class EmailSchemaInstaller {
    private EmailSchemaInstaller() {
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            ensureLocationEmailColumns(stmt);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS email_outbox (
                        email_outbox_id BIGSERIAL PRIMARY KEY,
                        location_id INTEGER REFERENCES locations(location_id),
                        sender_email TEXT NOT NULL,
                        sender_name TEXT NOT NULL DEFAULT '',
                        recipient_email TEXT NOT NULL,
                        bcc_email TEXT,
                        subject TEXT NOT NULL,
                        body_text TEXT NOT NULL DEFAULT '',
                        body_html TEXT NOT NULL DEFAULT '',
                        attachment_name TEXT,
                        attachment_content_type TEXT,
                        attachment_body TEXT,
                        document_type TEXT NOT NULL,
                        document_id TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'QUEUED',
                        attempts INTEGER NOT NULL DEFAULT 0,
                        max_attempts INTEGER NOT NULL DEFAULT 3,
                        last_error TEXT,
                        sent_at TIMESTAMPTZ,
                        queued_by_user_id INTEGER REFERENCES users(user_id),
                        queued_by_name TEXT,
                        device_id TEXT,
                        device_name TEXT,
                        sync_uuid UUID NOT NULL DEFAULT gen_random_uuid(),
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.executeUpdate("CREATE EXTENSION IF NOT EXISTS pgcrypto");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS location_id INTEGER REFERENCES locations(location_id)");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS sender_email TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS sender_name TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS recipient_email TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS bcc_email TEXT");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS subject TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS body_text TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS body_html TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS attachment_name TEXT");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS attachment_content_type TEXT");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS attachment_body TEXT");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS document_type TEXT NOT NULL DEFAULT 'MESSAGE'");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS document_id TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'QUEUED'");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS attempts INTEGER NOT NULL DEFAULT 0");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS max_attempts INTEGER NOT NULL DEFAULT 3");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS last_error TEXT");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS sent_at TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS queued_by_user_id INTEGER REFERENCES users(user_id)");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS queued_by_name TEXT");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS device_id TEXT");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS device_name TEXT");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid()");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP");
            stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS email_outbox_sync_uuid_key ON email_outbox(sync_uuid)");
            stmt.executeUpdate("ALTER TABLE email_outbox DROP CONSTRAINT IF EXISTS email_outbox_status_chk");
            stmt.executeUpdate("""
                    ALTER TABLE email_outbox
                    ADD CONSTRAINT email_outbox_status_chk
                    CHECK (status IN ('QUEUED', 'SENDING', 'SENT', 'FAILED', 'CANCELLED'))
                    """);
            stmt.executeUpdate("ALTER TABLE email_outbox DROP CONSTRAINT IF EXISTS email_outbox_attempts_chk");
            stmt.executeUpdate("ALTER TABLE email_outbox ADD CONSTRAINT email_outbox_attempts_chk CHECK (attempts >= 0 AND max_attempts > 0)");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS email_outbox_events (
                        email_outbox_event_id BIGSERIAL PRIMARY KEY,
                        email_outbox_id BIGINT NOT NULL REFERENCES email_outbox(email_outbox_id) ON DELETE CASCADE,
                        event_type TEXT NOT NULL,
                        message TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        user_id INTEGER REFERENCES users(user_id),
                        user_name TEXT,
                        device_id TEXT,
                        device_name TEXT,
                        sync_uuid UUID NOT NULL DEFAULT gen_random_uuid()
                    )
                    """);
            stmt.executeUpdate("ALTER TABLE email_outbox_events ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid()");
            stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS email_outbox_events_sync_uuid_key ON email_outbox_events(sync_uuid)");
            stmt.executeUpdate("""
                    CREATE OR REPLACE FUNCTION set_email_outbox_updated_at()
                    RETURNS TRIGGER AS $$
                    BEGIN
                        IF TG_OP = 'INSERT' THEN
                            NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
                        ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
                            NEW.updated_at = CURRENT_TIMESTAMP;
                        END IF;
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql
                    """);
            stmt.executeUpdate("ALTER FUNCTION set_email_outbox_updated_at() SET search_path = public");
            stmt.executeUpdate("DROP TRIGGER IF EXISTS email_outbox_set_updated_at ON email_outbox");
            stmt.executeUpdate("""
                    CREATE TRIGGER email_outbox_set_updated_at
                    BEFORE INSERT OR UPDATE ON email_outbox
                    FOR EACH ROW
                    EXECUTE FUNCTION set_email_outbox_updated_at()
                    """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS email_outbox_status_idx ON email_outbox(status, created_at)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS email_outbox_document_idx ON email_outbox(document_type, document_id, created_at DESC)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS email_outbox_location_idx ON email_outbox(location_id, created_at DESC)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS email_outbox_events_outbox_idx ON email_outbox_events(email_outbox_id, created_at DESC)");
            stmt.executeUpdate("ALTER TABLE email_outbox ENABLE ROW LEVEL SECURITY");
            stmt.executeUpdate("ALTER TABLE email_outbox_events ENABLE ROW LEVEL SECURITY");
            stmt.executeUpdate("DROP POLICY IF EXISTS email_outbox_service_role_all ON email_outbox");
            stmt.executeUpdate("CREATE POLICY email_outbox_service_role_all ON email_outbox FOR ALL TO service_role USING (true) WITH CHECK (true)");
            stmt.executeUpdate("DROP POLICY IF EXISTS email_outbox_events_service_role_all ON email_outbox_events");
            stmt.executeUpdate("CREATE POLICY email_outbox_events_service_role_all ON email_outbox_events FOR ALL TO service_role USING (true) WITH CHECK (true)");
        }
    }

    private static void ensureLocationEmailColumns(Statement stmt) throws SQLException {
        stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS email_sender_address TEXT NOT NULL DEFAULT ''");
        stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS email_sender_name TEXT NOT NULL DEFAULT ''");
        stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS email_bcc_address TEXT NOT NULL DEFAULT ''");
        stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS email_receipts_enabled BOOLEAN NOT NULL DEFAULT FALSE");
        stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS email_order_confirmations_enabled BOOLEAN NOT NULL DEFAULT FALSE");
        stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS email_quotes_enabled BOOLEAN NOT NULL DEFAULT FALSE");
        stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS email_invoices_enabled BOOLEAN NOT NULL DEFAULT FALSE");
        stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS email_delivery_bills_enabled BOOLEAN NOT NULL DEFAULT FALSE");
        stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS email_connected_at TIMESTAMPTZ");
        stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS email_last_tested_at TIMESTAMPTZ");
    }
}
