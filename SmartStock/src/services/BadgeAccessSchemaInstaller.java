package services;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class BadgeAccessSchemaInstaller {
    private BadgeAccessSchemaInstaller() {
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS badge_rotated_at TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS badge_rotated_by_user_id INTEGER");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS badge_rotated_by_name TEXT");
            stmt.executeUpdate("ALTER TABLE IF EXISTS company_customization ADD COLUMN IF NOT EXISTS badge_template_nfc_enabled BOOLEAN NOT NULL DEFAULT FALSE");
            stmt.executeUpdate("ALTER TABLE IF EXISTS company_customization ADD COLUMN IF NOT EXISTS badge_template_nfc_payload TEXT NOT NULL DEFAULT '{badge_id}'");
            stmt.executeUpdate("ALTER TABLE IF EXISTS company_customization ADD COLUMN IF NOT EXISTS badge_template_nfc_writer_command TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE IF EXISTS company_customization ADD COLUMN IF NOT EXISTS badge_template_nfc_verify_command TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE IF EXISTS company_customization ALTER COLUMN badge_template_back_instructions SET DEFAULT 'Scan, swipe, or tap this badge for SmartStock access.'");
            stmt.executeUpdate("""
                    UPDATE company_customization
                    SET badge_template_back_instructions = 'Scan, swipe, or tap this badge for SmartStock access.',
                        updated_at = CURRENT_TIMESTAMP
                    WHERE badge_template_back_instructions = 'Scan or swipe this badge for SmartStock access.'
                    """);
        }
    }
}
