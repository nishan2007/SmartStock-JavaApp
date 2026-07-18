package services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

public final class ItemDetailsService {
    private ItemDetailsService() {
    }

    public static int ensureCustomDepartment(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO categories (name, description)
                SELECT 'Custom', 'Default department for custom items'
                WHERE NOT EXISTS (
                    SELECT 1 FROM categories WHERE UPPER(BTRIM(name)) = 'CUSTOM'
                )
                """)) {
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT category_id FROM categories WHERE UPPER(BTRIM(name)) = 'CUSTOM' ORDER BY category_id LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to resolve the Custom department.");
    }

    public static int resolveItemType(Connection conn, int categoryId, String name) throws SQLException {
        String value = requireValue(name, "Item Type");
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO item_types (category_id, name) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            ps.setInt(1, categoryId);
            ps.setString(2, value);
            ps.executeUpdate();
        }
        int id = findId(conn, "SELECT item_type_id FROM item_types WHERE category_id = ? AND UPPER(REGEXP_REPLACE(BTRIM(name), '\\s+', ' ', 'g')) = ?", categoryId, value, "item type");
        standardizeName(conn, "item_types", "item_type_id", id, value);
        return id;
    }

    public static int resolveBrand(Connection conn, String name) throws SQLException {
        String value = requireValue(name, "Item Brand");
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO item_brands (name) VALUES (?) ON CONFLICT DO NOTHING")) {
            ps.setString(1, value);
            ps.executeUpdate();
        }
        int id = findId(conn, "SELECT brand_id FROM item_brands WHERE UPPER(REGEXP_REPLACE(BTRIM(name), '\\s+', ' ', 'g')) = ?", value, "item brand");
        standardizeName(conn, "item_brands", "brand_id", id, value);
        return id;
    }

    public static int resolveShelfLocation(Connection conn, int locationId, String name) throws SQLException {
        String value = requireValue(name, "Shelf Location");
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO shelf_locations (location_id, name) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            ps.setInt(1, locationId);
            ps.setString(2, value);
            ps.executeUpdate();
        }
        int id = findId(conn, "SELECT shelf_location_id FROM shelf_locations WHERE location_id = ? AND UPPER(REGEXP_REPLACE(BTRIM(name), '\\s+', ' ', 'g')) = ?", locationId, value, "shelf location");
        standardizeName(conn, "shelf_locations", "shelf_location_id", id, value);
        return id;
    }

    public static void upsertShelfAssignment(Connection conn, int productId, int locationId,
                                             int shelfLocationId, Integer storageShelfLocationId) throws SQLException {
        String sql = """
                INSERT INTO product_shelf_assignments
                    (product_id, location_id, shelf_location_id, storage_shelf_location_id)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (product_id, location_id) DO UPDATE SET
                    shelf_location_id = EXCLUDED.shelf_location_id,
                    storage_shelf_location_id = EXCLUDED.storage_shelf_location_id,
                    updated_at = CURRENT_TIMESTAMP
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, locationId);
            ps.setInt(3, shelfLocationId);
            if (storageShelfLocationId == null) ps.setNull(4, java.sql.Types.INTEGER);
            else ps.setInt(4, storageShelfLocationId);
            ps.executeUpdate();
        }
    }

    private static int findId(Connection conn, String sql, int scopeId, String name, String label) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, scopeId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to resolve " + label + ": " + name);
    }

    private static int findId(Connection conn, String sql, String name, String label) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to resolve " + label + ": " + name);
    }

    private static String requireValue(String value, String label) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return normalized;
    }

    private static void standardizeName(Connection conn, String table, String idColumn, int id, String value) throws SQLException {
        String sql = "UPDATE " + table + " SET name = ?, updated_at = CURRENT_TIMESTAMP WHERE " + idColumn + " = ? AND name IS DISTINCT FROM ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            ps.setInt(2, id);
            ps.setString(3, value);
            ps.executeUpdate();
        }
    }
}
