package managers;

import data.DB;
import utils.ImageOptimizationHelper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

public class CompanyCustomizationManager {
    private static final Path CONFIG_PATH = Path.of(System.getProperty("user.home"), ".smartstock", "company_customization.properties");
    private static final Path LOGO_DIRECTORY = CONFIG_PATH.getParent();
    private static final String COMPANY_LOGO_BUCKET = getConfig("COMPANY_LOGO_BUCKET", "Product Images");
    private static final long MAX_ORIGINAL_LOGO_BYTES = 8L * 1024L * 1024L;
    private static final long MAX_LOGO_UPLOAD_BYTES = 300L * 1024L;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private CompanyCustomizationManager() {
    }

    public static Path getConfigPath() {
        return CONFIG_PATH;
    }

    public static ReceiptSettings loadReceiptSettings() {
        Integer locationId = SessionManager.getCurrentLocationId();
        if (Objects.equals(locationId, cachedLocationId) && cachedReceiptSettings != null) {
            return cachedReceiptSettings;
        }

        if (locationId != null) {
            try {
                ReceiptSettings dbSettings = loadReceiptSettingsFromDb(locationId);
                if (dbSettings != null) {
                    saveLocalReceiptSettings(dbSettings);
                    cachedLocationId = locationId;
                    cachedReceiptSettings = dbSettings;
                    return dbSettings;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        ReceiptSettings localSettings = loadLocalReceiptSettings();
        cachedLocationId = locationId;
        cachedReceiptSettings = localSettings;
        return localSettings;
    }

    public static ReceiptSettings getPreviewOverrideSettings() {
        return previewOverrideSettings;
    }

    public static void setPreviewOverrideSettings(ReceiptSettings settings) {
        previewOverrideSettings = settings;
    }

    public static void clearPreviewOverrideSettings() {
        previewOverrideSettings = null;
    }

    public static void saveReceiptSettings(ReceiptSettings settings) throws IOException, SQLException {
        Integer locationId = SessionManager.getCurrentLocationId();
        if (locationId == null) {
            saveLocalReceiptSettings(settings);
            return;
        }

        saveReceiptSettingsToDb(locationId, settings);
        saveLocalReceiptSettings(settings);
        cachedLocationId = locationId;
        cachedReceiptSettings = settings;
    }

    private static ReceiptSettings loadReceiptSettingsFromDb(int locationId) throws SQLException {
        String sql = """
                SELECT company_name,
                       COALESCE(receipt_header_line, '') AS receipt_header_line,
                       COALESCE(receipt_footer_line, 'Thank you') AS receipt_footer_line,
                       COALESCE(receipt_logo_url, '') AS receipt_logo_url,
                       COALESCE(show_logo, FALSE) AS show_logo,
                       COALESCE(show_sale_id, TRUE) AS show_sale_id,
                       COALESCE(show_device, TRUE) AS show_device,
                       COALESCE(show_customer, TRUE) AS show_customer,
                       COALESCE(show_sku, TRUE) AS show_sku,
                       COALESCE(show_item_discount, TRUE) AS show_item_discount,
                       COALESCE(show_payment_status, TRUE) AS show_payment_status
                FROM company_customization
                WHERE location_id = ?
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                return new ReceiptSettings(
                        rs.getString("company_name"),
                        rs.getString("receipt_header_line"),
                        rs.getString("receipt_footer_line"),
                        rs.getString("receipt_logo_url"),
                        rs.getBoolean("show_logo"),
                        rs.getBoolean("show_sale_id"),
                        rs.getBoolean("show_device"),
                        rs.getBoolean("show_customer"),
                        rs.getBoolean("show_sku"),
                        rs.getBoolean("show_item_discount"),
                        rs.getBoolean("show_payment_status")
                );
            }
        }
    }

    private static void saveReceiptSettingsToDb(int locationId, ReceiptSettings settings) throws SQLException {
        String sql = """
                INSERT INTO company_customization (
                    location_id,
                    company_name,
                    receipt_header_line,
                    receipt_footer_line,
                    receipt_logo_url,
                    show_logo,
                    show_sale_id,
                    show_device,
                    show_customer,
                    show_sku,
                    show_item_discount,
                    show_payment_status,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (location_id) DO UPDATE SET
                    company_name = EXCLUDED.company_name,
                    receipt_header_line = EXCLUDED.receipt_header_line,
                    receipt_footer_line = EXCLUDED.receipt_footer_line,
                    receipt_logo_url = EXCLUDED.receipt_logo_url,
                    show_logo = EXCLUDED.show_logo,
                    show_sale_id = EXCLUDED.show_sale_id,
                    show_device = EXCLUDED.show_device,
                    show_customer = EXCLUDED.show_customer,
                    show_sku = EXCLUDED.show_sku,
                    show_item_discount = EXCLUDED.show_item_discount,
                    show_payment_status = EXCLUDED.show_payment_status,
                    updated_at = NOW()
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setString(2, settings.companyName());
            ps.setString(3, settings.headerLine());
            ps.setString(4, settings.footerLine());
            ps.setString(5, settings.logoPath());
            ps.setBoolean(6, settings.showLogo());
            ps.setBoolean(7, settings.showSaleId());
            ps.setBoolean(8, settings.showDevice());
            ps.setBoolean(9, settings.showCustomer());
            ps.setBoolean(10, settings.showSku());
            ps.setBoolean(11, settings.showItemDiscount());
            ps.setBoolean(12, settings.showPaymentStatus());
            ps.executeUpdate();
        }
    }

    private static ReceiptSettings loadLocalReceiptSettings() {
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream inputStream = Files.newInputStream(CONFIG_PATH)) {
                properties.load(inputStream);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        return new ReceiptSettings(
                properties.getProperty("receipt.company_name", "SmartStock"),
                properties.getProperty("receipt.header_line", ""),
                properties.getProperty("receipt.footer_line", "Thank you"),
                properties.getProperty("receipt.logo_path", ""),
                Boolean.parseBoolean(properties.getProperty("receipt.show_logo", "false")),
                Boolean.parseBoolean(properties.getProperty("receipt.show_sale_id", "true")),
                Boolean.parseBoolean(properties.getProperty("receipt.show_device", "true")),
                Boolean.parseBoolean(properties.getProperty("receipt.show_customer", "true")),
                Boolean.parseBoolean(properties.getProperty("receipt.show_sku", "true")),
                Boolean.parseBoolean(properties.getProperty("receipt.show_item_discount", "true")),
                Boolean.parseBoolean(properties.getProperty("receipt.show_payment_status", "true"))
        );
    }

    private static void saveLocalReceiptSettings(ReceiptSettings settings) throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());
        Properties properties = new Properties();
        properties.setProperty("receipt.company_name", settings.companyName());
        properties.setProperty("receipt.header_line", settings.headerLine());
        properties.setProperty("receipt.footer_line", settings.footerLine());
        properties.setProperty("receipt.logo_path", settings.logoPath());
        properties.setProperty("receipt.show_logo", String.valueOf(settings.showLogo()));
        properties.setProperty("receipt.show_sale_id", String.valueOf(settings.showSaleId()));
        properties.setProperty("receipt.show_device", String.valueOf(settings.showDevice()));
        properties.setProperty("receipt.show_customer", String.valueOf(settings.showCustomer()));
        properties.setProperty("receipt.show_sku", String.valueOf(settings.showSku()));
        properties.setProperty("receipt.show_item_discount", String.valueOf(settings.showItemDiscount()));
        properties.setProperty("receipt.show_payment_status", String.valueOf(settings.showPaymentStatus()));

        try (OutputStream outputStream = Files.newOutputStream(CONFIG_PATH)) {
            properties.store(outputStream, "SmartStock company customization settings");
        }
    }

    public static String uploadReceiptLogo(Path sourcePath) throws Exception {
        return uploadCompanyLogo(sourcePath);
    }

    public static String uploadCompanyLogo(Path sourcePath) throws Exception {
        if (sourcePath == null || !Files.exists(sourcePath)) {
            throw new IOException("Logo file was not found.");
        }

        if (SessionManager.getCurrentLocationId() != null) {
            return uploadReceiptLogoToStorage(sourcePath.toFile(), SessionManager.getCurrentLocationId());
        }

        String extension = getExtension(sourcePath.getFileName().toString());
        if (extension.isBlank()) {
            extension = "png";
        }
        Path targetPath = LOGO_DIRECTORY.resolve("receipt-logo." + extension.toLowerCase(Locale.ROOT));
        Files.createDirectories(LOGO_DIRECTORY);
        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        return targetPath.toString();
    }

    public static BufferedImage loadReceiptLogo(ReceiptSettings settings) {
        if (settings == null || !settings.showLogo() || settings.logoPath().isBlank()) {
            return null;
        }

        return loadCompanyLogo(settings);
    }

    public static BufferedImage loadCompanyLogo(ReceiptSettings settings) {
        if (settings == null || settings.logoPath().isBlank()) {
            return null;
        }

        try {
            String logoPath = settings.logoPath();
            if (isRemoteImageUrl(logoPath)) {
                URL url = URI.create(logoPath).toURL();
                return ImageIO.read(url);
            } else {
                Path path = Path.of(logoPath);
                if (!Files.exists(path)) {
                    return null;
                }
                return ImageIO.read(path.toFile());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private static ReceiptSettings previewOverrideSettings;
    private static Integer cachedLocationId;
    private static ReceiptSettings cachedReceiptSettings;

    private static String getExtension(String fileName) {
        int dotIndex = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1);
    }

    private static String uploadReceiptLogoToStorage(File logoFile, int locationId) throws Exception {
        try (ImageOptimizationHelper.OptimizedImage optimizedImage = ImageOptimizationHelper.optimizeForUpload(
                logoFile,
                "receipt-logo",
                900,
                360,
                0.86f,
                MAX_ORIGINAL_LOGO_BYTES,
                MAX_LOGO_UPLOAD_BYTES
        )) {
            String accessToken = SupabaseSessionManager.getValidAccessToken();
            String objectPath = "company/location-" + locationId + "/receipt-logo-" + System.currentTimeMillis() + "-" + sanitizeFilename(optimizedImage.filename());
            String encodedBucket = encodePathSegment(COMPANY_LOGO_BUCKET);
            String encodedObjectPath = encodeObjectPath(objectPath);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SupabaseSessionManager.getSupabaseUrl()
                            + "/storage/v1/object/"
                            + encodedBucket
                            + "/"
                            + encodedObjectPath))
                    .timeout(Duration.ofSeconds(45))
                    .header("apikey", SupabaseSessionManager.getSupabasePublishableKey())
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", optimizedImage.contentType())
                    .header("x-upsert", "true")
                    .POST(HttpRequest.BodyPublishers.ofFile(optimizedImage.file().toPath()))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Supabase Storage returned HTTP "
                        + response.statusCode()
                        + " while uploading company logo to bucket "
                        + COMPANY_LOGO_BUCKET
                        + ": "
                        + response.body());
            }

            return SupabaseSessionManager.getSupabaseUrl()
                    + "/storage/v1/object/public/"
                    + encodedBucket
                    + "/"
                    + encodedObjectPath;
        }
    }

    private static boolean isRemoteImageUrl(String imageUrl) {
        return imageUrl != null && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"));
    }

    private static String sanitizeFilename(String filename) {
        String sanitized = filename == null ? "receipt-logo" : filename.trim();
        sanitized = sanitized.replaceAll("[^A-Za-z0-9._-]", "-");
        sanitized = sanitized.replaceAll("-+", "-");
        if (sanitized.isBlank() || ".".equals(sanitized) || "..".equals(sanitized)) {
            return "receipt-logo";
        }
        return sanitized;
    }

    private static String encodeObjectPath(String objectPath) {
        String[] parts = objectPath.split("/");
        StringBuilder encoded = new StringBuilder();
        for (String part : parts) {
            if (!encoded.isEmpty()) {
                encoded.append("/");
            }
            encoded.append(encodePathSegment(part));
        }
        return encoded.toString();
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String getConfig(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        if (value == null || value.isBlank()) {
            value = fallback;
        }
        return value;
    }

    public record ReceiptSettings(
            String companyName,
            String headerLine,
            String footerLine,
            String logoPath,
            boolean showLogo,
            boolean showSaleId,
            boolean showDevice,
            boolean showCustomer,
            boolean showSku,
            boolean showItemDiscount,
            boolean showPaymentStatus
    ) {
        public ReceiptSettings {
            companyName = clean(companyName, "SmartStock");
            headerLine = Objects.requireNonNullElse(headerLine, "").trim();
            footerLine = clean(footerLine, "Thank you");
            logoPath = Objects.requireNonNullElse(logoPath, "").trim();
        }

        private static String clean(String value, String fallback) {
            String cleaned = Objects.requireNonNullElse(value, "").trim();
            return cleaned.isBlank() ? fallback : cleaned;
        }
    }
}
