// Run only on the store server with -Dsmartstock.environment=production.
// Reads publication metadata directly; does not run SmartStock schema setup or snapshot generation.
import data.DatabaseConfig;
import data.DatabaseMode;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.*;
import java.util.HexFormat;

public final class PublicStorefrontContentAudit {
    private static void report(Connection c, String label, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            System.out.println(label + ":");
            while (r.next()) {
                StringBuilder row = new StringBuilder("  ");
                for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) {
                    if (i > 1) row.append(" | ");
                    row.append(r.getMetaData().getColumnLabel(i)).append("=").append(r.getString(i));
                }
                System.out.println(row);
            }
        }
    }

    private static boolean hasColumn(Connection c, String schema, String table, String column) throws SQLException {
        try (ResultSet r = c.getMetaData().getColumns(null, schema, table, column)) { return r.next(); }
    }

    private static boolean hasTable(Connection c, String schema, String table) throws SQLException {
        try (ResultSet r = c.getMetaData().getTables(null, schema, table, new String[]{"TABLE"})) { return r.next(); }
    }

    private static boolean verifyLocalImages(Connection c) throws Exception {
        Path root = Path.of(System.getProperty("smartstock.image.store",
                Path.of(System.getProperty("user.home"), ".smartstock", "profiles", "production", "image-store").toString()))
                .toAbsolutePath().normalize();
        boolean ready = true;
        String sql = "SELECT w.location_id,p.product_id,p.image_url,a.bucket_name,a.object_path,a.category,a.lifecycle_status,a.content_type,a.sha256 "
                + "FROM storefront.products w JOIN products p ON p.product_id=w.product_id LEFT JOIN image_assets a ON a.asset_id::text="
                + "CASE WHEN p.image_url ~ '^smartstock-asset:[0-9a-fA-F-]{36}$' THEN substring(p.image_url from 18) ELSE NULL END "
                + "WHERE w.published AND p.is_active AND p.product_type='INVENTORY' "
                + "UNION ALL SELECT 0,0,ci.company_logo_url,a.bucket_name,a.object_path,a.category,a.lifecycle_status,a.content_type,a.sha256 "
                + "FROM company_info ci LEFT JOIN image_assets a ON a.asset_id::text="
                + "CASE WHEN ci.company_logo_url ~ '^smartstock-asset:[0-9a-fA-F-]{36}$' THEN substring(ci.company_logo_url from 18) ELSE NULL END "
                + "WHERE ci.company_info_id=1 ORDER BY 1,2";
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            while (r.next()) {
                boolean logo = r.getInt(1) == 0;
                String label = logo ? "Company logo" : "Store " + r.getInt(1) + " product " + r.getInt(2);
                String reference = r.getString(3), bucket = r.getString(4), object = r.getString(5), hash = r.getString(9);
                boolean approved = reference != null && reference.matches("smartstock-asset:[0-9a-fA-F-]{36}")
                        && (logo ? "COMPANY_LOGO" : "PRODUCT").equals(r.getString(6)) && "ACTIVE".equals(r.getString(7))
                        && ("image/png".equals(r.getString(8)) || "image/jpeg".equals(r.getString(8))
                            || "image/webp".equals(r.getString(8)) || "image/gif".equals(r.getString(8)))
                        && hash != null && hash.matches("[a-f0-9]{64}") && bucket != null && object != null;
                if (!approved) {
                    System.out.println(label + " local image: unverified registry entry"); ready = false; continue;
                }
                Path file = root.resolve(bucket).resolve(object).normalize();
                if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                    System.out.println(label + " local image: missing"); ready = false; continue;
                }
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long size = 0;
                try (InputStream input = Files.newInputStream(file)) {
                    byte[] buffer = new byte[65536]; int read;
                    while ((read = input.read(buffer)) != -1) {
                        size += read;
                        if (size > 12L * 1024 * 1024) break;
                        digest.update(buffer, 0, read);
                    }
                }
                boolean valid = size > 0 && size <= 12L * 1024 * 1024 && hash.equals(HexFormat.of().formatHex(digest.digest()));
                System.out.println(label + " local image: " + (valid ? "checksum valid" : "checksum or size invalid"));
                if (!valid) ready = false;
                if (valid && Boolean.getBoolean("storefront.audit.exportPublicImages")) {
                    Path outputDirectory = Path.of("SmartStock", "target", "storefront-content-review");
                    Files.createDirectories(outputDirectory);
                    String extension = switch (r.getString(8)) {
                        case "image/png" -> ".png";
                        case "image/webp" -> ".webp";
                        case "image/gif" -> ".gif";
                        default -> ".jpg";
                    };
                    Path output = outputDirectory.resolve(logo ? "company-logo" + extension : "store-" + r.getInt(1) + "-product-" + r.getInt(2) + extension);
                    Files.copy(file, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    System.out.println(label + " review copy: " + output.toAbsolutePath().normalize());
                }
            }
        }
        return ready;
    }

    public static void main(String[] args) throws Exception {
        if (!"production".equals(System.getProperty("smartstock.environment")))
            throw new IllegalStateException("Explicit production profile required");
        DatabaseConfig config = DatabaseConfig.load();
        if (config.mode() != DatabaseMode.SERVER || !config.jdbcUrl().matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/[A-Za-z0-9_]+"))
            throw new IllegalStateException("Expected local server database");
        boolean ready = false;
        try (Connection c = DriverManager.getConnection(config.jdbcUrl(), config.dbUser(), config.dbPassword())) {
            c.setReadOnly(true);
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) { s.execute("SET TRANSACTION READ ONLY"); }
            ready = true;
            report(c, "schema", "SELECT COALESCE(max(version),0) AS version FROM storefront.schema_version");
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COALESCE(max(version),0) FROM storefront.schema_version")) {
                if (r.next() && r.getInt(1) != 15) {
                    System.err.println("Launch gate failed: storefront schema is not the reviewed version 15.");
                    ready = false;
                }
            }
            String campaign = hasColumn(c,"storefront","settings","campaign_topic") ? ",campaign_topic,campaign_headline,campaign_description" : "";
            report(c, "store settings", "SELECT location_id,enabled,currency,welcome"+campaign+",updated_at FROM storefront.settings ORDER BY location_id");
            report(c, "public store identity", "SELECT l.location_id,l.name AS store_name,l.address,l.timezone,ci.company_name,ci.company_motto_line1,ci.company_motto_line2,l.company_phone_line1,l.company_phone_line2,l.company_email_line1,l.company_email_line2,l.company_address_line1,l.company_address_line2,COALESCE(NULLIF(ci.company_logo_url,''),'')<>'' AS has_logo FROM locations l CROSS JOIN company_info ci WHERE ci.company_info_id=1 ORDER BY l.location_id");
            report(c, "public logo registry", "SELECT CASE WHEN ci.company_logo_url LIKE 'smartstock-asset:%' THEN 'asset' WHEN NULLIF(btrim(ci.company_logo_url),'') IS NOT NULL THEN 'legacy' ELSE 'missing' END AS reference_type,a.category,a.lifecycle_status,a.content_type,a.local_status,a.cloud_status,length(a.sha256)=64 AS has_checksum FROM company_info ci LEFT JOIN image_assets a ON a.asset_id::text=CASE WHEN ci.company_logo_url ~ '^smartstock-asset:[0-9a-fA-F-]{36}$' THEN substring(ci.company_logo_url from 18) ELSE NULL END WHERE ci.company_info_id=1");
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT count(*) FROM storefront.settings WHERE enabled")) {
                if (r.next() && r.getInt(1) > 0) {
                    System.err.println("Launch gate failed: at least one store has online ordering enabled.");
                    ready = false;
                }
            }
            String price = hasColumn(c,"storefront","products","website_price") ? "COALESCE(w.promotional_price,w.website_price,p.price)" : "p.price";
            report(c, "published products", "SELECT w.location_id,p.product_id,p.name,p.sku,COALESCE(cat.name,'Essentials') AS category,COALESCE(p.size,'') AS size,COALESCE(p.color,'') AS color,"+price+" AS public_price,w.featured,COALESCE(NULLIF(w.description,''),p.description,'') AS description,COALESCE(i.quantity_on_hand,0) AS stock,p.image_url IS NOT NULL AND btrim(p.image_url)<>'' AS has_image,w.updated_at FROM storefront.products w JOIN products p ON p.product_id=w.product_id LEFT JOIN categories cat ON cat.category_id=p.category_id LEFT JOIN inventory i ON i.product_id=p.product_id AND i.location_id=w.location_id WHERE w.published AND p.is_active AND p.product_type='INVENTORY' ORDER BY w.location_id,p.name,p.product_id");
            report(c, "published image registry", "SELECT w.location_id,p.product_id,CASE WHEN p.image_url LIKE 'smartstock-asset:%' THEN 'asset' WHEN NULLIF(btrim(p.image_url),'') IS NOT NULL THEN 'legacy' ELSE 'missing' END AS reference_type,a.category,a.lifecycle_status,a.content_type,a.local_status,a.cloud_status,length(a.sha256)=64 AS has_checksum FROM storefront.products w JOIN products p ON p.product_id=w.product_id LEFT JOIN image_assets a ON a.asset_id::text=CASE WHEN p.image_url ~ '^smartstock-asset:[0-9a-fA-F-]{36}$' THEN substring(p.image_url from 18) ELSE NULL END WHERE w.published AND p.is_active AND p.product_type='INVENTORY' ORDER BY w.location_id,p.product_id");
            if (!verifyLocalImages(c)) { System.err.println("Launch gate failed: a published product image could not be verified locally."); ready = false; }
            if (hasTable(c,"storefront","projects")) report(c, "published projects", "SELECT location_id,project_id,title,summary,category,status,cover_reference<>'' AS has_cover,updated_at FROM storefront.projects WHERE status IN ('PUBLISHED','FEATURED') ORDER BY location_id,title");
            else { System.out.println("published projects: schema absent"); ready = false; }
            if (hasTable(c,"storefront","service_availability")) report(c, "unavailable services", "SELECT location_id,slug FROM storefront.service_availability WHERE NOT available ORDER BY location_id,slug");
            else { System.out.println("unavailable services: schema absent"); ready = false; }
            c.rollback();
        } catch (SQLException e) {
            System.err.println("Read-only content audit failed: " + e.getSQLState() + " (" + e.getClass().getSimpleName() + ")");
            System.exit(1);
        }
        if (!ready) System.exit(2);
    }
}
