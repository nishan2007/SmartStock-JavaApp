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
        SchemaContractService.requireLocalReady(conn);
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, "public", table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    public record SeedResult(int locationsUpdated, int highestNextCounter) {
    }
}
