package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import data.DB;
import data.DatabaseConfig;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
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

    public static List<Store> listCloud() throws SQLException {
        JsonArray rows = cloudLocationRows();
        List<Store> stores = new ArrayList<>();
        for (JsonElement element : rows) {
            JsonObject row = element.getAsJsonObject();
            int id = requiredPositiveInt(row, "location_id");
            String name = requiredText(row, "name", 200);
            String code = requiredText(row, "receipt_store_code", 20);
            String timezone = requiredText(row, "timezone", 100);
            if (!validStoreCode(code)) {
                throw new SQLException("Cloud store " + id + " has an invalid four-digit store code.");
            }
            try {
                ZoneId.of(timezone);
            } catch (Exception ex) {
                throw new SQLException("Cloud store " + id + " has an invalid timezone.", ex);
            }
            stores.add(new Store(id, name, code, timezone));
        }
        stores.sort(Comparator.comparing(Store::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(Store::locationId));
        return stores;
    }

    public static Store restoreFromCloud(Store selected) throws SQLException {
        if (selected == null) throw new IllegalArgumentException("Select an existing store.");
        JsonArray selectedRows = new JsonArray();
        for (JsonElement element : cloudLocationRows()) {
            JsonObject row = element.getAsJsonObject();
            if (requiredPositiveInt(row, "location_id") == selected.locationId()) {
                selectedRows.add(row);
                break;
            }
        }
        if (selectedRows.isEmpty()) {
            throw new SQLException("The selected store is no longer available in this environment.");
        }
        try (Connection connection = DB.getConnection()) {
            CloudRecoveryService.restoreRows(connection, "locations", selectedRows);
            CloudSyncManifest mirror = CloudSyncManifest.fetchStoreSnapshot(selected.locationId());
            CloudRecoveryService.restoreStoreMirror(connection, selected.locationId(), mirror);
            try (PreparedStatement sequence = connection.prepareStatement("""
                    SELECT setval(pg_get_serial_sequence('locations','location_id'),
                                  GREATEST(COALESCE((SELECT MAX(location_id) FROM locations), 1), 1),
                                  EXISTS (SELECT 1 FROM locations))
                    """)) {
                sequence.execute();
            }
        } catch (IOException ex) {
            throw new SQLException("Could not restore the selected store from Supabase.", ex);
        }
        Store restored = find(String.valueOf(selected.locationId()));
        if (restored == null) throw new SQLException("The selected store could not be restored locally.");
        return restored;
    }

    /** Repairs an interrupted existing-store restore using the saved assignment. */
    public static Store restoreAssignedFromCloud() throws SQLException {
        Integer locationId = DatabaseConfig.load().locationId();
        if (locationId == null) throw new SQLException("No existing store is assigned locally.");
        Store selected = listCloud().stream()
                .filter(store -> store.locationId() == locationId)
                .findFirst()
                .orElseThrow(() -> new SQLException(
                        "The assigned store is no longer available in this environment."));
        return restoreFromCloud(selected);
    }

    static List<Store> parseCloudStores(String json) throws SQLException {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonArray()) throw new SQLException("Cloud stores response was not a list.");
            List<Store> stores = new ArrayList<>();
            for (JsonElement element : parsed.getAsJsonArray()) {
                JsonObject row = element.getAsJsonObject();
                stores.add(new Store(requiredPositiveInt(row, "location_id"),
                        requiredText(row, "name", 200),
                        requiredText(row, "receipt_store_code", 20),
                        requiredText(row, "timezone", 100)));
            }
            return stores;
        } catch (RuntimeException ex) {
            throw new SQLException("Supabase returned invalid store information.", ex);
        }
    }

    private static JsonArray cloudLocationRows() throws SQLException {
        JsonArray all = new JsonArray();
        int offset = 0;
        try {
            while (true) {
                SupabaseServerApi.Response response =
                        SupabaseServerApi.getTablePage("locations", offset, 1_000);
                if (!response.successful()) {
                    throw new SQLException(SupabaseServerApi.failureMessage(
                            "Loading existing stores", response));
                }
                JsonElement parsed = JsonParser.parseString(response.body());
                if (!parsed.isJsonArray()) throw new SQLException("Cloud stores response was not a list.");
                JsonArray page = parsed.getAsJsonArray();
                page.forEach(all::add);
                if (page.size() < 1_000) break;
                offset += page.size();
            }
            return all;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SQLException("Loading existing stores was interrupted.", ex);
        } catch (IOException | RuntimeException ex) {
            throw new SQLException("Could not load existing stores from Supabase.", ex);
        }
    }

    private static int requiredPositiveInt(JsonObject row, String field) throws SQLException {
        if (!row.has(field) || row.get(field).isJsonNull()) {
            throw new SQLException("Cloud store is missing " + field + ".");
        }
        int value = row.get(field).getAsInt();
        if (value <= 0) throw new SQLException("Cloud store has an invalid " + field + ".");
        return value;
    }

    private static String requiredText(JsonObject row, String field, int maxLength)
            throws SQLException {
        if (!row.has(field) || row.get(field).isJsonNull()) {
            throw new SQLException("Cloud store is missing " + field + ".");
        }
        String value = row.get(field).getAsString().trim();
        if (value.isBlank() || value.length() > maxLength) {
            throw new SQLException("Cloud store has an invalid " + field + ".");
        }
        return value;
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
