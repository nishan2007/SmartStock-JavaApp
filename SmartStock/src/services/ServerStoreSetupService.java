package services;

import data.DB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.time.ZoneId;

/** Resolves or creates the store assigned during first-time server setup. */
public final class ServerStoreSetupService {
    private ServerStoreSetupService() {
    }

    public static List<Store> list() throws SQLException {
        List<Store> stores = new ArrayList<>();
        try (Connection connection = DB.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT location_id, name, receipt_store_code, timezone
                     FROM locations
                     ORDER BY name, location_id
                     """);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) stores.add(store(rows));
        }
        return stores;
    }

    public static Store find(String identifier) throws SQLException {
        String clean = identifier == null ? "" : identifier.trim();
        try (Connection connection = DB.getConnection()) {
            if (clean.isBlank()) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT location_id, name, receipt_store_code, timezone
                        FROM locations
                        ORDER BY location_id
                        LIMIT 2
                        """);
                     ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) return null;
                    Store only = store(rows);
                    return rows.next() ? null : only;
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT location_id, name, receipt_store_code, timezone
                    FROM locations
                    WHERE UPPER(TRIM(receipt_store_code)) = UPPER(?)
                       OR location_id = CASE WHEN ? ~ '^[0-9]+$' THEN CAST(? AS INTEGER) ELSE -1 END
                    ORDER BY CASE WHEN UPPER(TRIM(receipt_store_code)) = UPPER(?) THEN 0 ELSE 1 END
                    LIMIT 1
                    """)) {
                statement.setString(1, clean);
                statement.setString(2, clean);
                statement.setString(3, clean);
                statement.setString(4, clean);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? store(rows) : null;
                }
            }
        }
    }

    public static Store create(String name, String storeCode, String timezone, String address)
            throws SQLException {
        String cleanName = name == null ? "" : name.trim();
        String cleanCode = normalizeStoreCode(storeCode);
        String cleanTimezone = timezone == null ? "" : timezone.trim();
        String cleanAddress = address == null ? "" : address.trim();
        if (cleanName.isBlank()) throw new IllegalArgumentException("Store name is required.");
        if (!validStoreCode(cleanCode)) {
            throw new IllegalArgumentException("Store code must contain four digits from 0001 to 9999.");
        }
        try {
            ZoneId.of(cleanTimezone);
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Enter a valid timezone such as America/New_York.", ex);
        }

        try (Connection connection = DB.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement existing = connection.prepareStatement(
                        "SELECT 1 FROM locations WHERE UPPER(TRIM(receipt_store_code)) = UPPER(?)")) {
                    existing.setString(1, cleanCode);
                    try (ResultSet rows = existing.executeQuery()) {
                        if (rows.next()) {
                            throw new IllegalArgumentException(
                                    "Store code " + cleanCode + " already exists.");
                        }
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO locations (name, receipt_store_code, timezone, address)
                        VALUES (?, ?, ?, NULLIF(?, ''))
                        RETURNING location_id, name, receipt_store_code, timezone
                        """)) {
                    insert.setString(1, cleanName);
                    insert.setString(2, cleanCode);
                    insert.setString(3, cleanTimezone);
                    insert.setString(4, cleanAddress);
                    try (ResultSet rows = insert.executeQuery()) {
                        if (!rows.next()) throw new SQLException("The new store ID was not returned.");
                        Store created = store(rows);
                        connection.commit();
                        return created;
                    }
                }
            } catch (Exception ex) {
                connection.rollback();
                if (ex instanceof SQLException sql) throw sql;
                if (ex instanceof RuntimeException runtime) throw runtime;
                throw new SQLException(ex);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    static boolean validStoreCode(String value) {
        return value != null && value.matches("[0-9]{4}") && !"0000".equals(value);
    }

    private static String normalizeStoreCode(String value) {
        return value == null ? "" : value.trim();
    }

    private static Store store(ResultSet rows) throws SQLException {
        return new Store(rows.getInt("location_id"), rows.getString("name"),
                rows.getString("receipt_store_code"), rows.getString("timezone"));
    }

    public record Store(int locationId, String name, String storeCode, String timezone) {
        @Override
        public String toString() {
            return name + " (" + storeCode + ")";
        }
    }
}
