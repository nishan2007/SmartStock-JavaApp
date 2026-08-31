package services;

import utils.ImageCacheManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ImageCacheWarmupService {
    private ImageCacheWarmupService() {
    }

    public static int warmLocalCache(Connection conn) throws SQLException {
        Set<String> urls = new LinkedHashSet<>();
        collectUrls(conn, urls, "company_info", "company_logo_url");
        collectUrls(conn, urls, "company_customization", "badge_template_logo_url");
        collectUrls(conn, urls, "products", "image_url");
        collectUrls(conn, urls, "custom_order_items", "image_url");
        collectUrls(conn, urls, "custom_order_item_variants", "image_url");
        collectUrls(conn, urls, "users", "employee_photo_url");
        collectUrls(conn, urls, "customer_accounts", "customer_photo_url");

        int loaded = 0;
        for (String url : urls) {
            if (ImageCacheManager.loadImage(url) != null) {
                loaded++;
            }
        }
        return loaded;
    }

    private static void collectUrls(Connection conn, Set<String> urls, String tableName, String columnName) throws SQLException {
        if (!hasColumn(conn, tableName, columnName)) {
            return;
        }

        String sql = "SELECT DISTINCT " + quote(columnName) + " AS image_url FROM " + quote(tableName)
                + " WHERE NULLIF(TRIM(" + quote(columnName) + "), '') IS NOT NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String value = rs.getString("image_url");
                if (ImageCacheManager.isRemoteImageUrl(value)) {
                    urls.add(value.trim());
                }
            }
        }
    }

    private static boolean hasColumn(Connection conn, String tableName, String columnName) throws SQLException {
        String sql = """
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = ?
                  AND column_name = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
