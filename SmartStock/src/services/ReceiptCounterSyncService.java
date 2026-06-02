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
                INSERT INTO company_customization (location_id, company_name, next_receipt_counter)
                SELECT ?, 'SmartStock', ?
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
                        company_name TEXT NOT NULL DEFAULT 'SmartStock',
                        receipt_header_line TEXT NOT NULL DEFAULT '',
                        receipt_footer_line TEXT NOT NULL DEFAULT 'Thank you',
                        receipt_logo_url TEXT NOT NULL DEFAULT '',
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
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        UNIQUE (location_id)
                    )
                    """);
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS next_receipt_counter INTEGER NOT NULL DEFAULT 1");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS vat_enabled BOOLEAN NOT NULL DEFAULT FALSE");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS vat_use_department_rates BOOLEAN NOT NULL DEFAULT FALSE");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS vat_fixed_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0");
            stmt.executeUpdate("ALTER TABLE company_customization ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()");
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
