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

    public static CustomOrderSettings loadCustomOrderSettings() {
        Integer locationId = SessionManager.getCurrentLocationId();
        if (Objects.equals(locationId, cachedCustomOrderLocationId) && cachedCustomOrderSettings != null) {
            return cachedCustomOrderSettings;
        }
        if (locationId != null) {
            try {
                CustomOrderSettings dbSettings = loadCustomOrderSettingsFromDb(locationId);
                if (dbSettings != null) {
                    saveLocalCustomOrderSettings(dbSettings);
                    cachedCustomOrderLocationId = locationId;
                    cachedCustomOrderSettings = dbSettings;
                    return dbSettings;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        CustomOrderSettings localSettings = loadLocalCustomOrderSettings();
        cachedCustomOrderLocationId = locationId;
        cachedCustomOrderSettings = localSettings;
        return localSettings;
    }

    public static void saveCustomOrderSettings(CustomOrderSettings settings) throws IOException, SQLException {
        Integer locationId = SessionManager.getCurrentLocationId();
        if (locationId != null) {
            saveCustomOrderSettingsToDb(locationId, settings);
        }
        saveLocalCustomOrderSettings(settings);
        cachedCustomOrderLocationId = locationId;
        cachedCustomOrderSettings = settings;
    }

    public static SaleSafetySettings loadSaleSafetySettings() {
        Integer locationId = SessionManager.getCurrentLocationId();
        if (Objects.equals(locationId, cachedSaleSafetyLocationId) && cachedSaleSafetySettings != null) {
            return cachedSaleSafetySettings;
        }
        if (locationId != null) {
            try {
                SaleSafetySettings dbSettings = loadSaleSafetySettingsFromDb(locationId);
                if (dbSettings != null) {
                    saveLocalSaleSafetySettings(dbSettings);
                    cachedSaleSafetyLocationId = locationId;
                    cachedSaleSafetySettings = dbSettings;
                    return dbSettings;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        SaleSafetySettings localSettings = loadLocalSaleSafetySettings();
        cachedSaleSafetyLocationId = locationId;
        cachedSaleSafetySettings = localSettings;
        return localSettings;
    }

    public static void saveSaleSafetySettings(SaleSafetySettings settings) throws IOException, SQLException {
        Integer locationId = SessionManager.getCurrentLocationId();
        if (locationId != null) {
            saveSaleSafetySettingsToDb(locationId, settings);
        }
        saveLocalSaleSafetySettings(settings);
        cachedSaleSafetyLocationId = locationId;
        cachedSaleSafetySettings = settings;
    }

    public static CustomOrderSlipSettings loadCustomOrderSlipSettings() {
        Integer locationId = SessionManager.getCurrentLocationId();
        if (Objects.equals(locationId, cachedCustomOrderSlipLocationId) && cachedCustomOrderSlipSettings != null) {
            return cachedCustomOrderSlipSettings;
        }
        if (locationId != null) {
            try {
                CustomOrderSlipSettings dbSettings = loadCustomOrderSlipSettingsFromDb(locationId);
                if (dbSettings != null) {
                    saveLocalCustomOrderSlipSettings(dbSettings);
                    cachedCustomOrderSlipLocationId = locationId;
                    cachedCustomOrderSlipSettings = dbSettings;
                    return dbSettings;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        CustomOrderSlipSettings localSettings = loadLocalCustomOrderSlipSettings();
        cachedCustomOrderSlipLocationId = locationId;
        cachedCustomOrderSlipSettings = localSettings;
        return localSettings;
    }

    public static void saveCustomOrderSlipSettings(CustomOrderSlipSettings settings) throws IOException, SQLException {
        Integer locationId = SessionManager.getCurrentLocationId();
        if (locationId != null) {
            saveCustomOrderSlipSettingsToDb(locationId, settings);
        }
        saveLocalCustomOrderSlipSettings(settings);
        cachedCustomOrderSlipLocationId = locationId;
        cachedCustomOrderSlipSettings = settings;
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
                       COALESCE(show_payment_status, TRUE) AS show_payment_status,
                       COALESCE(next_receipt_counter, 1) AS next_receipt_counter
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
                        rs.getBoolean("show_payment_status"),
                        rs.getInt("next_receipt_counter")
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
                    next_receipt_counter,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
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
                    next_receipt_counter = EXCLUDED.next_receipt_counter,
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
            ps.setInt(13, settings.nextReceiptCounter());
            ps.executeUpdate();
        }
    }

    private static CustomOrderSettings loadCustomOrderSettingsFromDb(int locationId) throws SQLException {
        String sql = """
                SELECT COALESCE(custom_order_minimum_deposit_percent, 0) AS custom_order_minimum_deposit_percent,
                       COALESCE(custom_order_refund_approval_limit, 0) AS custom_order_refund_approval_limit
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
                return new CustomOrderSettings(
                        rs.getBigDecimal("custom_order_minimum_deposit_percent"),
                        rs.getBigDecimal("custom_order_refund_approval_limit")
                );
            }
        }
    }

    private static void saveCustomOrderSettingsToDb(int locationId, CustomOrderSettings settings) throws SQLException {
        String sql = """
                INSERT INTO company_customization (
                    location_id,
                    company_name,
                    custom_order_minimum_deposit_percent,
                    custom_order_refund_approval_limit,
                    updated_at
                )
                VALUES (?, 'SmartStock', ?, ?, NOW())
                ON CONFLICT (location_id) DO UPDATE SET
                    custom_order_minimum_deposit_percent = EXCLUDED.custom_order_minimum_deposit_percent,
                    custom_order_refund_approval_limit = EXCLUDED.custom_order_refund_approval_limit,
                    updated_at = NOW()
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setBigDecimal(2, settings.minimumDepositPercent());
            ps.setBigDecimal(3, settings.refundApprovalLimit());
            ps.executeUpdate();
        }
    }

    private static SaleSafetySettings loadSaleSafetySettingsFromDb(int locationId) throws SQLException {
        String sql = """
                SELECT COALESCE(sale_discount_limit_percent, 5) AS sale_discount_limit_percent,
                       COALESCE(sale_return_approval_limit, 0) AS sale_return_approval_limit
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
                return new SaleSafetySettings(
                        rs.getBigDecimal("sale_discount_limit_percent"),
                        rs.getBigDecimal("sale_return_approval_limit")
                );
            }
        }
    }

    private static void saveSaleSafetySettingsToDb(int locationId, SaleSafetySettings settings) throws SQLException {
        String sql = """
                INSERT INTO company_customization (
                    location_id,
                    company_name,
                    sale_discount_limit_percent,
                    sale_return_approval_limit,
                    updated_at
                )
                VALUES (?, 'SmartStock', ?, ?, NOW())
                ON CONFLICT (location_id) DO UPDATE SET
                    sale_discount_limit_percent = EXCLUDED.sale_discount_limit_percent,
                    sale_return_approval_limit = EXCLUDED.sale_return_approval_limit,
                    updated_at = NOW()
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setBigDecimal(2, settings.discountLimitPercent());
            ps.setBigDecimal(3, settings.returnApprovalLimit());
            ps.executeUpdate();
        }
    }

    private static CustomOrderSlipSettings loadCustomOrderSlipSettingsFromDb(int locationId) throws SQLException {
        String sql = """
                SELECT COALESCE(custom_order_slip_enabled, TRUE) AS enabled,
                       COALESCE(custom_order_slip_auto_print, TRUE) AS auto_print,
                       COALESCE(custom_order_slip_title, 'CUSTOMER''S ORDER SLIP') AS title,
                       COALESCE(custom_order_slip_contact_line, '') AS contact_line,
                       COALESCE(custom_order_slip_email_line, '') AS email_line,
                       COALESCE(custom_order_slip_footer_note, 'NB: The management is NOT responsible for any LOSS or DAMAGE to your personal property.') AS footer_note,
                       COALESCE(custom_order_slip_blank_detail_lines, 8) AS blank_detail_lines,
                       COALESCE(custom_order_slip_show_logo, TRUE) AS show_logo,
                       COALESCE(custom_order_slip_show_order_number, TRUE) AS show_order_number,
                       COALESCE(custom_order_slip_show_due_date, TRUE) AS show_due_date,
                       COALESCE(custom_order_slip_show_customer_phone, TRUE) AS show_customer_phone,
                       COALESCE(custom_order_slip_show_customer_account, TRUE) AS show_customer_account,
                       COALESCE(custom_order_slip_show_store, TRUE) AS show_store,
                       COALESCE(custom_order_slip_show_device, TRUE) AS show_device,
                       COALESCE(custom_order_slip_show_cashier, TRUE) AS show_cashier,
                       COALESCE(custom_order_slip_show_line_items, TRUE) AS show_line_items,
                       COALESCE(custom_order_slip_show_pricing, TRUE) AS show_pricing,
                       COALESCE(custom_order_slip_show_payment_summary, TRUE) AS show_payment_summary,
                       COALESCE(custom_order_slip_show_payment_reference, TRUE) AS show_payment_reference,
                       COALESCE(custom_order_slip_show_taken_by, TRUE) AS show_taken_by,
                       COALESCE(custom_order_slip_show_signatures, TRUE) AS show_signatures
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
                return new CustomOrderSlipSettings(
                        rs.getBoolean("enabled"),
                        rs.getBoolean("auto_print"),
                        rs.getString("title"),
                        rs.getString("contact_line"),
                        rs.getString("email_line"),
                        rs.getString("footer_note"),
                        rs.getInt("blank_detail_lines"),
                        rs.getBoolean("show_logo"),
                        rs.getBoolean("show_order_number"),
                        rs.getBoolean("show_due_date"),
                        rs.getBoolean("show_customer_phone"),
                        rs.getBoolean("show_customer_account"),
                        rs.getBoolean("show_store"),
                        rs.getBoolean("show_device"),
                        rs.getBoolean("show_cashier"),
                        rs.getBoolean("show_line_items"),
                        rs.getBoolean("show_pricing"),
                        rs.getBoolean("show_payment_summary"),
                        rs.getBoolean("show_payment_reference"),
                        rs.getBoolean("show_taken_by"),
                        rs.getBoolean("show_signatures")
                );
            }
        }
    }

    private static void saveCustomOrderSlipSettingsToDb(int locationId, CustomOrderSlipSettings settings) throws SQLException {
        String sql = """
                INSERT INTO company_customization (
                    location_id,
                    company_name,
                    custom_order_slip_enabled,
                    custom_order_slip_auto_print,
                    custom_order_slip_title,
                    custom_order_slip_contact_line,
                    custom_order_slip_email_line,
                    custom_order_slip_footer_note,
                    custom_order_slip_blank_detail_lines,
                    custom_order_slip_show_logo,
                    custom_order_slip_show_order_number,
                    custom_order_slip_show_due_date,
                    custom_order_slip_show_customer_phone,
                    custom_order_slip_show_customer_account,
                    custom_order_slip_show_store,
                    custom_order_slip_show_device,
                    custom_order_slip_show_cashier,
                    custom_order_slip_show_line_items,
                    custom_order_slip_show_pricing,
                    custom_order_slip_show_payment_summary,
                    custom_order_slip_show_payment_reference,
                    custom_order_slip_show_taken_by,
                    custom_order_slip_show_signatures,
                    updated_at
                )
                VALUES (?, 'SmartStock', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (location_id) DO UPDATE SET
                    custom_order_slip_enabled = EXCLUDED.custom_order_slip_enabled,
                    custom_order_slip_auto_print = EXCLUDED.custom_order_slip_auto_print,
                    custom_order_slip_title = EXCLUDED.custom_order_slip_title,
                    custom_order_slip_contact_line = EXCLUDED.custom_order_slip_contact_line,
                    custom_order_slip_email_line = EXCLUDED.custom_order_slip_email_line,
                    custom_order_slip_footer_note = EXCLUDED.custom_order_slip_footer_note,
                    custom_order_slip_blank_detail_lines = EXCLUDED.custom_order_slip_blank_detail_lines,
                    custom_order_slip_show_logo = EXCLUDED.custom_order_slip_show_logo,
                    custom_order_slip_show_order_number = EXCLUDED.custom_order_slip_show_order_number,
                    custom_order_slip_show_due_date = EXCLUDED.custom_order_slip_show_due_date,
                    custom_order_slip_show_customer_phone = EXCLUDED.custom_order_slip_show_customer_phone,
                    custom_order_slip_show_customer_account = EXCLUDED.custom_order_slip_show_customer_account,
                    custom_order_slip_show_store = EXCLUDED.custom_order_slip_show_store,
                    custom_order_slip_show_device = EXCLUDED.custom_order_slip_show_device,
                    custom_order_slip_show_cashier = EXCLUDED.custom_order_slip_show_cashier,
                    custom_order_slip_show_line_items = EXCLUDED.custom_order_slip_show_line_items,
                    custom_order_slip_show_pricing = EXCLUDED.custom_order_slip_show_pricing,
                    custom_order_slip_show_payment_summary = EXCLUDED.custom_order_slip_show_payment_summary,
                    custom_order_slip_show_payment_reference = EXCLUDED.custom_order_slip_show_payment_reference,
                    custom_order_slip_show_taken_by = EXCLUDED.custom_order_slip_show_taken_by,
                    custom_order_slip_show_signatures = EXCLUDED.custom_order_slip_show_signatures,
                    updated_at = NOW()
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setBoolean(2, settings.enabled());
            ps.setBoolean(3, settings.autoPrint());
            ps.setString(4, settings.title());
            ps.setString(5, settings.contactLine());
            ps.setString(6, settings.emailLine());
            ps.setString(7, settings.footerNote());
            ps.setInt(8, settings.blankDetailLines());
            ps.setBoolean(9, settings.showLogo());
            ps.setBoolean(10, settings.showOrderNumber());
            ps.setBoolean(11, settings.showDueDate());
            ps.setBoolean(12, settings.showCustomerPhone());
            ps.setBoolean(13, settings.showCustomerAccount());
            ps.setBoolean(14, settings.showStore());
            ps.setBoolean(15, settings.showDevice());
            ps.setBoolean(16, settings.showCashier());
            ps.setBoolean(17, settings.showLineItems());
            ps.setBoolean(18, settings.showPricing());
            ps.setBoolean(19, settings.showPaymentSummary());
            ps.setBoolean(20, settings.showPaymentReference());
            ps.setBoolean(21, settings.showTakenBy());
            ps.setBoolean(22, settings.showSignatures());
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
                Boolean.parseBoolean(properties.getProperty("receipt.show_payment_status", "true")),
                parseIntInRange(properties.getProperty("receipt.next_receipt_counter", "1"), 1, 999999, 1)
        );
    }

    private static CustomOrderSettings loadLocalCustomOrderSettings() {
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream inputStream = Files.newInputStream(CONFIG_PATH)) {
                properties.load(inputStream);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return new CustomOrderSettings(
                parsePercent(properties.getProperty("custom_orders.minimum_deposit_percent", "0")),
                parseMoney(properties.getProperty("custom_orders.refund_approval_limit", "0"))
        );
    }

    private static CustomOrderSlipSettings loadLocalCustomOrderSlipSettings() {
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream inputStream = Files.newInputStream(CONFIG_PATH)) {
                properties.load(inputStream);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return new CustomOrderSlipSettings(
                Boolean.parseBoolean(properties.getProperty("custom_order_slip.enabled", "true")),
                Boolean.parseBoolean(properties.getProperty("custom_order_slip.auto_print", "true")),
                properties.getProperty("custom_order_slip.title", "CUSTOMER'S ORDER SLIP"),
                properties.getProperty("custom_order_slip.contact_line", ""),
                properties.getProperty("custom_order_slip.email_line", ""),
                properties.getProperty("custom_order_slip.footer_note", "NB: The management is NOT responsible for any LOSS or DAMAGE to your personal property."),
                parseIntInRange(properties.getProperty("custom_order_slip.blank_detail_lines", "8"), 0, 20, 8),
                Boolean.parseBoolean(properties.getProperty("custom_order_slip.show_logo", "true")),
                Boolean.parseBoolean(properties.getProperty("custom_order_slip.show_order_number", "true")),
                Boolean.parseBoolean(properties.getProperty("custom_order_slip.show_due_date", "true")),
                Boolean.parseBoolean(properties.getProperty("custom_order_slip.show_customer_phone", "true")),
                Boolean.parseBoolean(properties.getProperty("custom_order_slip.show_customer_account", "true")),
                Boolean.parseBoolean(properties.getProperty("custom_order_slip.show_store", "true")),
                Boolean.parseBoolean(properties.getProperty("custom_order_slip.show_device", "true")),
                Boolean.parseBoolean(properties.getProperty("custom_order_slip.show_cashier", "true")),
                Boolean.parseBoolean(properties.getProperty("custom_order_slip.show_line_items", "true")),
                Boolean.parseBoolean(properties.getProperty("custom_order_slip.show_pricing", "true")),
                Boolean.parseBoolean(properties.getProperty("custom_order_slip.show_payment_summary", "true")),
                Boolean.parseBoolean(properties.getProperty("custom_order_slip.show_payment_reference", "true")),
                Boolean.parseBoolean(properties.getProperty("custom_order_slip.show_taken_by", "true")),
                Boolean.parseBoolean(properties.getProperty("custom_order_slip.show_signatures", "true"))
        );
    }

    private static SaleSafetySettings loadLocalSaleSafetySettings() {
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream inputStream = Files.newInputStream(CONFIG_PATH)) {
                properties.load(inputStream);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return new SaleSafetySettings(
                parsePercent(properties.getProperty("sales.discount_limit_percent", "5")),
                parseMoney(properties.getProperty("sales.return_approval_limit", "0"))
        );
    }

    private static void saveLocalReceiptSettings(ReceiptSettings settings) throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream inputStream = Files.newInputStream(CONFIG_PATH)) {
                properties.load(inputStream);
            }
        }
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
        properties.setProperty("receipt.next_receipt_counter", String.valueOf(settings.nextReceiptCounter()));

        try (OutputStream outputStream = Files.newOutputStream(CONFIG_PATH)) {
            properties.store(outputStream, "SmartStock company customization settings");
        }
    }

    private static void saveLocalCustomOrderSettings(CustomOrderSettings settings) throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream inputStream = Files.newInputStream(CONFIG_PATH)) {
                properties.load(inputStream);
            }
        }
        properties.setProperty("custom_orders.minimum_deposit_percent", settings.minimumDepositPercent().toPlainString());
        properties.setProperty("custom_orders.refund_approval_limit", settings.refundApprovalLimit().toPlainString());
        try (OutputStream outputStream = Files.newOutputStream(CONFIG_PATH)) {
            properties.store(outputStream, "SmartStock company customization settings");
        }
    }

    private static void saveLocalCustomOrderSlipSettings(CustomOrderSlipSettings settings) throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream inputStream = Files.newInputStream(CONFIG_PATH)) {
                properties.load(inputStream);
            }
        }
        properties.setProperty("custom_order_slip.enabled", String.valueOf(settings.enabled()));
        properties.setProperty("custom_order_slip.auto_print", String.valueOf(settings.autoPrint()));
        properties.setProperty("custom_order_slip.title", settings.title());
        properties.setProperty("custom_order_slip.contact_line", settings.contactLine());
        properties.setProperty("custom_order_slip.email_line", settings.emailLine());
        properties.setProperty("custom_order_slip.footer_note", settings.footerNote());
        properties.setProperty("custom_order_slip.blank_detail_lines", String.valueOf(settings.blankDetailLines()));
        properties.setProperty("custom_order_slip.show_logo", String.valueOf(settings.showLogo()));
        properties.setProperty("custom_order_slip.show_order_number", String.valueOf(settings.showOrderNumber()));
        properties.setProperty("custom_order_slip.show_due_date", String.valueOf(settings.showDueDate()));
        properties.setProperty("custom_order_slip.show_customer_phone", String.valueOf(settings.showCustomerPhone()));
        properties.setProperty("custom_order_slip.show_customer_account", String.valueOf(settings.showCustomerAccount()));
        properties.setProperty("custom_order_slip.show_store", String.valueOf(settings.showStore()));
        properties.setProperty("custom_order_slip.show_device", String.valueOf(settings.showDevice()));
        properties.setProperty("custom_order_slip.show_cashier", String.valueOf(settings.showCashier()));
        properties.setProperty("custom_order_slip.show_line_items", String.valueOf(settings.showLineItems()));
        properties.setProperty("custom_order_slip.show_pricing", String.valueOf(settings.showPricing()));
        properties.setProperty("custom_order_slip.show_payment_summary", String.valueOf(settings.showPaymentSummary()));
        properties.setProperty("custom_order_slip.show_payment_reference", String.valueOf(settings.showPaymentReference()));
        properties.setProperty("custom_order_slip.show_taken_by", String.valueOf(settings.showTakenBy()));
        properties.setProperty("custom_order_slip.show_signatures", String.valueOf(settings.showSignatures()));
        try (OutputStream outputStream = Files.newOutputStream(CONFIG_PATH)) {
            properties.store(outputStream, "SmartStock company customization settings");
        }
    }

    private static void saveLocalSaleSafetySettings(SaleSafetySettings settings) throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream inputStream = Files.newInputStream(CONFIG_PATH)) {
                properties.load(inputStream);
            }
        }
        properties.setProperty("sales.discount_limit_percent", settings.discountLimitPercent().toPlainString());
        properties.setProperty("sales.return_approval_limit", settings.returnApprovalLimit().toPlainString());
        try (OutputStream outputStream = Files.newOutputStream(CONFIG_PATH)) {
            properties.store(outputStream, "SmartStock company customization settings");
        }
    }

    private static java.math.BigDecimal parsePercent(String value) {
        try {
            java.math.BigDecimal percent = new java.math.BigDecimal(value == null || value.isBlank() ? "0" : value.trim());
            if (percent.compareTo(java.math.BigDecimal.ZERO) < 0 || percent.compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
                return java.math.BigDecimal.ZERO;
            }
            return percent;
        } catch (NumberFormatException ex) {
            return java.math.BigDecimal.ZERO;
        }
    }

    private static java.math.BigDecimal parseMoney(String value) {
        try {
            java.math.BigDecimal amount = new java.math.BigDecimal(value == null || value.isBlank() ? "0" : value.trim());
            return amount.compareTo(java.math.BigDecimal.ZERO) < 0 ? java.math.BigDecimal.ZERO : amount;
        } catch (NumberFormatException ex) {
            return java.math.BigDecimal.ZERO;
        }
    }

    private static int parseIntInRange(String value, int min, int max, int fallback) {
        try {
            int parsed = Integer.parseInt(value == null || value.isBlank() ? String.valueOf(fallback) : value.trim());
            if (parsed < min || parsed > max) {
                return fallback;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            return fallback;
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
    private static Integer cachedCustomOrderLocationId;
    private static CustomOrderSettings cachedCustomOrderSettings;
    private static Integer cachedSaleSafetyLocationId;
    private static SaleSafetySettings cachedSaleSafetySettings;
    private static Integer cachedCustomOrderSlipLocationId;
    private static CustomOrderSlipSettings cachedCustomOrderSlipSettings;

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
            boolean showPaymentStatus,
            int nextReceiptCounter
    ) {
        public ReceiptSettings {
            companyName = clean(companyName, "SmartStock");
            headerLine = Objects.requireNonNullElse(headerLine, "").trim();
            footerLine = clean(footerLine, "Thank you");
            logoPath = Objects.requireNonNullElse(logoPath, "").trim();
            if (nextReceiptCounter < 1) {
                nextReceiptCounter = 1;
            }
        }

        private static String clean(String value, String fallback) {
            String cleaned = Objects.requireNonNullElse(value, "").trim();
            return cleaned.isBlank() ? fallback : cleaned;
        }
    }

    public record CustomOrderSettings(java.math.BigDecimal minimumDepositPercent, java.math.BigDecimal refundApprovalLimit) {
        public CustomOrderSettings {
            minimumDepositPercent = minimumDepositPercent == null ? java.math.BigDecimal.ZERO : minimumDepositPercent;
            refundApprovalLimit = refundApprovalLimit == null ? java.math.BigDecimal.ZERO : refundApprovalLimit;
        }
    }

    public record SaleSafetySettings(java.math.BigDecimal discountLimitPercent, java.math.BigDecimal returnApprovalLimit) {
        public SaleSafetySettings {
            discountLimitPercent = discountLimitPercent == null ? java.math.BigDecimal.valueOf(5) : discountLimitPercent;
            if (discountLimitPercent.compareTo(java.math.BigDecimal.ZERO) < 0) {
                discountLimitPercent = java.math.BigDecimal.ZERO;
            } else if (discountLimitPercent.compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
                discountLimitPercent = java.math.BigDecimal.valueOf(100);
            }
            returnApprovalLimit = returnApprovalLimit == null ? java.math.BigDecimal.ZERO : returnApprovalLimit;
            if (returnApprovalLimit.compareTo(java.math.BigDecimal.ZERO) < 0) {
                returnApprovalLimit = java.math.BigDecimal.ZERO;
            }
        }
    }

    public record CustomOrderSlipSettings(
            boolean enabled,
            boolean autoPrint,
            String title,
            String contactLine,
            String emailLine,
            String footerNote,
            int blankDetailLines,
            boolean showLogo,
            boolean showOrderNumber,
            boolean showDueDate,
            boolean showCustomerPhone,
            boolean showCustomerAccount,
            boolean showStore,
            boolean showDevice,
            boolean showCashier,
            boolean showLineItems,
            boolean showPricing,
            boolean showPaymentSummary,
            boolean showPaymentReference,
            boolean showTakenBy,
            boolean showSignatures
    ) {
        public CustomOrderSlipSettings {
            title = clean(title, "CUSTOMER'S ORDER SLIP");
            contactLine = Objects.requireNonNullElse(contactLine, "").trim();
            emailLine = Objects.requireNonNullElse(emailLine, "").trim();
            footerNote = clean(footerNote, "NB: The management is NOT responsible for any LOSS or DAMAGE to your personal property.");
            if (blankDetailLines < 0) {
                blankDetailLines = 0;
            } else if (blankDetailLines > 20) {
                blankDetailLines = 20;
            }
        }

        private static String clean(String value, String fallback) {
            String cleaned = Objects.requireNonNullElse(value, "").trim();
            return cleaned.isBlank() ? fallback : cleaned;
        }
    }
}
