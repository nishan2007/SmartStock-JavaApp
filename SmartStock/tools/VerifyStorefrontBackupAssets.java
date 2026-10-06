// Read-only archive and public-image coverage check; never restores or uploads assets.
import data.DatabaseConfig;
import data.DatabaseMode;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipFile;

class VerifyStorefrontBackupAssets {
    private static String decode(String field) {
        return new String(Base64.getUrlDecoder().decode(field), java.nio.charset.StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !"production".equals(System.getProperty("smartstock.environment")))
            throw new IllegalArgumentException("Provide one backup file and the explicit production profile");
        DatabaseConfig config = DatabaseConfig.load();
        if (config.mode() != DatabaseMode.SERVER || !config.jdbcUrl().matches(
                "jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/[A-Za-z0-9_]+"))
            throw new IllegalStateException("Expected local server database");
        Map<String, String> packaged = new HashMap<>();
        int checked = 0;
        try (ZipFile zip = new ZipFile(Path.of(args[0]).toFile())) {
            var manifestEntry = zip.getEntry("assets.tsv");
            if (manifestEntry == null) throw new IllegalStateException("Backup asset manifest is missing");
            String manifest;
            try (InputStream stream = zip.getInputStream(manifestEntry)) {
                manifest = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            for (String line : manifest.split("\\R")) {
                if (line.isBlank()) continue;
                String[] fields = line.split("\\t", -1);
                if (fields.length != 6) throw new IllegalStateException("Invalid asset manifest row");
                String entryName = decode(fields[0]);
                if (!entryName.startsWith("assets/")) throw new IllegalStateException("Invalid asset entry");
                var entry = zip.getEntry(entryName);
                if (entry == null) throw new IllegalStateException("Manifest asset is absent");
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream stream = zip.getInputStream(entry)) {
                    byte[] buffer = new byte[65536];
                    for (int n; (n = stream.read(buffer)) != -1;) digest.update(buffer, 0, n);
                }
                String actual = HexFormat.of().formatHex(digest.digest());
                if (!actual.equalsIgnoreCase(fields[5])) throw new IllegalStateException("Manifest asset checksum mismatch");
                String key = decode(fields[2]) + "\0" + decode(fields[3]);
                if (packaged.putIfAbsent(key, actual) != null) throw new IllegalStateException("Duplicate asset target");
                checked++;
            }
        }
        int publicAssets = 0;
        try (var connection = DriverManager.getConnection(config.jdbcUrl(), config.dbUser(), config.dbPassword())) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement(); var result = statement.executeQuery("""
                    SELECT a.bucket_name,a.object_path,a.sha256
                    FROM storefront.products w JOIN products p ON p.product_id=w.product_id
                    JOIN image_assets a ON a.asset_id::text=substring(p.image_url from 18)
                    WHERE w.published AND p.is_active AND p.product_type='INVENTORY'
                      AND p.image_url ~ '^smartstock-asset:[0-9a-fA-F-]{36}$'
                    UNION ALL
                    SELECT a.bucket_name,a.object_path,a.sha256
                    FROM company_info ci JOIN image_assets a ON a.asset_id::text=substring(ci.company_logo_url from 18)
                    WHERE ci.company_info_id=1
                      AND ci.company_logo_url ~ '^smartstock-asset:[0-9a-fA-F-]{36}$'
                    """)) {
                while (result.next()) {
                    String key = result.getString(1) + "\0" + result.getString(2);
                    if (!result.getString(3).equalsIgnoreCase(packaged.getOrDefault(key, "")))
                        throw new IllegalStateException("Public image is absent or differs from backup");
                    publicAssets++;
                }
            }
            connection.rollback();
        }
        if (publicAssets < 2) throw new IllegalStateException("Expected public product image and logo were not found");
        System.out.println("Verified " + checked + " packaged asset checksums; " + publicAssets
                + " public product/logo images match the backup.");
    }
}
