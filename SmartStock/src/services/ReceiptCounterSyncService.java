package services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ReceiptCounterSyncService {
    private ReceiptCounterSyncService() {
    }

    public static SeedResult seedFromExistingReceipts(Connection local, Connection cloud, Integer configuredLocationId) throws SQLException {
        ensureCompanyCustomizationTable(local);
        Map<Integer, Integer> maxSequences = new LinkedHashMap<>();
        if (configuredLocationId != null) {
            maxSequences.put(configuredLocationId, 0);
        }
        mergeMaxSequences(maxSequences, local);
        mergeMaxSequences(maxSequences, cloud);

        int updated = 0;
        int highestNext = 1;
        for (Map.Entry<Integer, Integer> entry : maxSequences.entrySet()) {
            int locationId = entry.getKey();
            int nextCounter = Math.max(entry.getValue() + 1, 1);
            highestNext = Math.max(highestNext, nextCounter);
            if (upsertCounter(local, locationId, nextCounter)) {
                updated++;
            }
        }
        return new SeedResult(updated, highestNext);
    }

    private static void mergeMaxSequences(Map<Integer, Integer> maxSequences, Connection conn) throws SQLException {
        if (!tableExists(conn, "sales")) {
            return;
        }
        String sql = """
                SELECT location_id,
                       GREATEST(
                           COALESCE(MAX(receipt_sequence), 0),
                           COALESCE(MAX(
                               CASE
                                   WHEN COALESCE(receipt_number, '') ~ '^[0-9]{4}-[0-9]{4}-[0-9]{6}$'
                                   THEN RIGHT(receipt_number, 6)::INT
                                   ELSE NULL
                               END
                           ), 0)
                       ) AS max_sequence
                FROM sales
                WHERE location_id IS NOT NULL
                GROUP BY location_id
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int locationId = rs.getInt("location_id");
                int maxSequence = rs.getInt("max_sequence");
                maxSequences.merge(locationId, maxSequence, Math::max);
            }
        }
    }

    private static boolean upsertCounter(Connection local, int locationId, int nextCounter) throws SQLException {
        String sql = """
                INSERT INTO company_customization (location_id, next_receipt_counter)
                SELECT ?, ?
                WHERE EXISTS (SELECT 1 FROM locations WHERE location_id = ?)
                ON CONFLICT (location_id) DO UPDATE
                SET next_receipt_counter = GREATEST(company_customization.next_receipt_counter, EXCLUDED.next_receipt_counter),
                    updated_at = NOW()
                """;
        try (PreparedStatement ps = local.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setInt(2, nextCounter);
            ps.setInt(3, locationId);
            return ps.executeUpdate() > 0;
        }
    }

    private static void ensureCompanyCustomizationTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS company_customization (
                        customization_id SERIAL PRIMARY KEY,
                        location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
                        receipt_header_line TEXT NOT NULL DEFAULT '',
                        receipt_footer_line TEXT NOT NULL DEFAULT 'Thank you',
                        show_logo BOOLEAN NOT NULL DEFAULT FALSE,
                        show_sale_id BOOLEAN NOT NULL DEFAULT TRUE,
                        show_device BOOLEAN NOT NULL DEFAULT TRUE,
                        show_customer BOOLEAN NOT NULL DEFAULT TRUE,
                        show_sku BOOLEAN NOT NULL DEFAULT TRUE,
                        show_item_discount BOOLEAN NOT NULL DEFAULT TRUE,
                        show_payment_status BOOLEAN NOT NULL DEFAULT TRUE,
                        vat_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                        vat_use_department_rates BOOLEAN NOT NULL DEFAULT FALSE,
                        vat_fixed_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0,
                        next_receipt_counter INTEGER NOT NULL DEFAULT 1,
                        badge_template_company_name TEXT NOT NULL DEFAULT 'SmartStock',
                        badge_template_logo_url TEXT NOT NULL DEFAULT '',
                        badge_template_quote TEXT NOT NULL DEFAULT '"Sales goes up and down, Service is Forever"',
                        badge_template_signatory_name TEXT NOT NULL DEFAULT 'Authorized Signature',
                        badge_template_signatory_title TEXT NOT NULL DEFAULT 'Management',
                        badge_template_back_instructions TEXT NOT NULL DEFAULT 'Scan or swipe this badge for SmartStock access.',
                        badge_template_show_quote BOOLEAN NOT NULL DEFAULT TRUE,
                        badge_template_show_employee_id BOOLEAN NOT NULL DEFAULT TRUE,
                        badge_template_show_issue_date BOOLEAN NOT NULL DEFAULT TRUE,
                        badge_template_show_barcode BOOLEAN NOT NULL DEFAULT TRUE,
                        badge_template_show_badge_text BOOLEAN NOT NULL DEFAULT FALSE,
                        badge_template_magstripe_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                        badge_template_magstripe_track1 TEXT NOT NULL DEFAULT '{badge_id}',
                        badge_template_magstripe_track2 TEXT NOT NULL DEFAULT '{badge_id}',
                        badge_template_magstripe_track3 TEXT NOT NULL DEFAULT '',
                        badge_template_magstripe_command TEXT NOT NULL DEFAULT '',
                        badge_template_layout_data TEXT NOT NULL DEFAULT '',
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        UNIQUE (location_id)
                    )
                    """);
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS next_receipt_counter INTEGER NOT NULL DEFAULT 1");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS vat_enabled BOOLEAN NOT NULL DEFAULT FALSE");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS vat_use_department_rates BOOLEAN NOT NULL DEFAULT FALSE");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS vat_fixed_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_company_name TEXT NOT NULL DEFAULT 'SmartStock'");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_logo_url TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_quote TEXT NOT NULL DEFAULT '\"Sales goes up and down, Service is Forever\"'");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_signatory_name TEXT NOT NULL DEFAULT 'Authorized Signature'");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_signatory_title TEXT NOT NULL DEFAULT 'Management'");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_back_instructions TEXT NOT NULL DEFAULT 'Scan or swipe this badge for SmartStock access.'");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_show_quote BOOLEAN NOT NULL DEFAULT TRUE");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_show_employee_id BOOLEAN NOT NULL DEFAULT TRUE");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_show_issue_date BOOLEAN NOT NULL DEFAULT TRUE");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_show_barcode BOOLEAN NOT NULL DEFAULT TRUE");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_show_badge_text BOOLEAN NOT NULL DEFAULT FALSE");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_magstripe_enabled BOOLEAN NOT NULL DEFAULT FALSE");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_magstripe_track1 TEXT NOT NULL DEFAULT '{badge_id}'");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_magstripe_track2 TEXT NOT NULL DEFAULT '{badge_id}'");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_magstripe_track3 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_magstripe_command TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS badge_template_layout_data TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()");
            stmt.executeUpdate("ALTER TABLE company_customization DROP COLUMN IF EXISTS company_name");
            stmt.executeUpdate("ALTER TABLE company_customization DROP COLUMN IF EXISTS company_motto_line1");
            stmt.executeUpdate("ALTER TABLE company_customization DROP COLUMN IF EXISTS company_motto_line2");
            stmt.executeUpdate("ALTER TABLE company_customization DROP COLUMN IF EXISTS receipt_logo_url");
        }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, "public", table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    public record SeedResult(int locationsUpdated, int highestNextCounter) {
    }
}
