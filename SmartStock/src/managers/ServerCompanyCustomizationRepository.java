package managers;

import services.SchemaContractService;

import data.DB;
import services.ServerRequestIdentity;
import services.StorageObjectNameBuilder;
import utils.ImageCacheManager;
import utils.ImageOptimizationHelper;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
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
import java.sql.Statement;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerCompanyCustomizationRepository {
    private static final Path CONFIG_PATH = Path.of(System.getProperty("user.home"), ".smartstock", "company_customization.properties");
    private static final Path LOGO_DIRECTORY = CONFIG_PATH.getParent();
    private static final String COMPANY_LOGO_BUCKET = getConfig("COMPANY_LOGO_BUCKET", "Product Images");
    private static final long MAX_ORIGINAL_LOGO_BYTES = 8L * 1024L * 1024L;
    private static final long MAX_LOGO_UPLOAD_BYTES = 300L * 1024L;
    private static final long MAX_BADGE_TEMPLATE_IMAGE_BYTES = 50L * 1024L * 1024L;
    private static final Pattern STORAGE_NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    protected ServerCompanyCustomizationRepository() {
    }

    public static Path getConfigPath() {
        return CONFIG_PATH;
    }

    public static ReceiptSettings loadReceiptSettings() {
        Integer locationId = ServerRequestIdentity.locationId();
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

    /** Loads authoritative branding and contact details for a specific store location. */
    public static ReceiptSettings loadReceiptSettingsForLocation(int locationId) throws SQLException {
        ReceiptSettings settings = loadReceiptSettingsFromDb(locationId);
        if (settings == null) {
            throw new SQLException("Receipt settings were not found for store location " + locationId + ".");
        }
        return settings;
    }

    /** Returns the optional fourth document-address line (Location Management's Address Line 3). */
    public static String loadDocumentAddressLine4ForLocation(int locationId) throws SQLException {
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT COALESCE(company_address_line3, '')
                     FROM locations
                     WHERE location_id = ?
                     """)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Objects.requireNonNullElse(rs.getString(1), "").trim() : "";
            }
        }
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
        Integer locationId = ServerRequestIdentity.locationId();
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
        Integer locationId = ServerRequestIdentity.locationId();
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
        Integer locationId = ServerRequestIdentity.locationId();
        if (locationId != null) {
            saveCustomOrderSettingsToDb(locationId, settings);
        }
        saveLocalCustomOrderSettings(settings);
        cachedCustomOrderLocationId = locationId;
        cachedCustomOrderSettings = settings;
    }

    public static SaleSafetySettings loadSaleSafetySettings() {
        Integer locationId = ServerRequestIdentity.locationId();
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
        Integer locationId = ServerRequestIdentity.locationId();
        if (locationId != null) {
            saveSaleSafetySettingsToDb(locationId, settings);
        }
        saveLocalSaleSafetySettings(settings);
        cachedSaleSafetyLocationId = locationId;
        cachedSaleSafetySettings = settings;
    }

    public static CustomOrderSlipSettings loadCustomOrderSlipSettings() {
        Integer locationId = ServerRequestIdentity.locationId();
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
        Integer locationId = ServerRequestIdentity.locationId();
        if (locationId != null) {
            saveCustomOrderSlipSettingsToDb(locationId, settings);
        }
        saveLocalCustomOrderSlipSettings(settings);
        cachedCustomOrderSlipLocationId = locationId;
        cachedCustomOrderSlipSettings = settings;
    }

    public static QuotationInvoicePrintSettings loadQuotationInvoicePrintSettings() {
        Integer locationId = ServerRequestIdentity.locationId();
        if (Objects.equals(locationId, cachedQuotationInvoicePrintLocationId) && cachedQuotationInvoicePrintSettings != null) {
            return cachedQuotationInvoicePrintSettings;
        }
        if (locationId != null) {
            try {
                QuotationInvoicePrintSettings dbSettings = loadQuotationInvoicePrintSettingsFromDb(locationId);
                if (dbSettings != null) {
                    saveLocalQuotationInvoicePrintSettings(dbSettings);
                    cachedQuotationInvoicePrintLocationId = locationId;
                    cachedQuotationInvoicePrintSettings = dbSettings;
                    return dbSettings;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        QuotationInvoicePrintSettings localSettings = loadLocalQuotationInvoicePrintSettings();
        cachedQuotationInvoicePrintLocationId = locationId;
        cachedQuotationInvoicePrintSettings = localSettings;
        return localSettings;
    }

    /** Loads quotation/invoice layout settings for the store that owns the document. */
    public static QuotationInvoicePrintSettings loadQuotationInvoicePrintSettingsForLocation(int locationId) throws SQLException {
        QuotationInvoicePrintSettings settings = loadQuotationInvoicePrintSettingsFromDb(locationId);
        return settings == null
                ? new QuotationInvoicePrintSettings(null, null, null, null, null, true)
                : settings;
    }

    public static void saveQuotationInvoicePrintSettings(QuotationInvoicePrintSettings settings) throws IOException, SQLException {
        Integer locationId = ServerRequestIdentity.locationId();
        if (locationId != null) {
            saveQuotationInvoicePrintSettingsToDb(locationId, settings);
        }
        saveLocalQuotationInvoicePrintSettings(settings);
        cachedQuotationInvoicePrintLocationId = locationId;
        cachedQuotationInvoicePrintSettings = settings;
    }

    public static BadgeTemplateSettings loadBadgeTemplateSettings() {
        Integer locationId = ServerRequestIdentity.locationId();
        if (locationId != null) {
            try {
                BadgeTemplateSettings dbSettings = loadBadgeTemplateSettingsFromDb(sharedBadgeTemplateLocationId(locationId));
                if (dbSettings != null) {
                    saveLocalBadgeTemplateSettings(dbSettings);
                    cachedBadgeTemplateLocationId = locationId;
                    cachedBadgeTemplateSettings = dbSettings;
                    return dbSettings;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        BadgeTemplateSettings localSettings = loadLocalBadgeTemplateSettings();
        cachedBadgeTemplateLocationId = locationId;
        cachedBadgeTemplateSettings = localSettings;
        return localSettings;
    }

    public static void saveBadgeTemplateSettings(BadgeTemplateSettings settings) throws IOException, SQLException {
        Integer locationId = ServerRequestIdentity.locationId();
        if (locationId != null) {
            saveBadgeTemplateSettingsToDb(locationId, settings);
        }
        saveLocalBadgeTemplateSettings(settings);
        cachedBadgeTemplateLocationId = locationId;
        cachedBadgeTemplateSettings = settings;
    }

    public static BadgeSecuritySettings loadBadgeSecuritySettings() {
        Integer locationId = ServerRequestIdentity.locationId();
        if (locationId == null) return new BadgeSecuritySettings(true);
        try (Connection conn = DB.getConnection()) {
            return new BadgeSecuritySettings(isBadgePinRequired(conn, locationId));
        } catch (SQLException ex) {
            throw new IllegalStateException("Badge login security settings could not be loaded.", ex);
        }
    }

    public static void saveBadgeSecuritySettings(BadgeSecuritySettings settings) throws IOException, SQLException {
        Integer locationId = ServerRequestIdentity.locationId();
        if (locationId == null) throw new SQLException("A store must be selected before badge security can be saved.");
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO company_customization (location_id, require_badge_pin_login, updated_at)
                     VALUES (?, ?, NOW())
                     ON CONFLICT (location_id) DO UPDATE SET
                         require_badge_pin_login = EXCLUDED.require_badge_pin_login,
                         updated_at = NOW()
                     """)) {
            ps.setInt(1, locationId);
            ps.setBoolean(2, settings.requireBadgePinLogin());
            ps.executeUpdate();
        }
    }

    public static boolean isBadgePinRequired(Connection conn, int locationId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COALESCE(require_badge_pin_login, TRUE)
                FROM company_customization
                WHERE location_id = ?
                """)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                return !rs.next() || rs.getBoolean(1);
            }
        }
    }

    public record BadgeSecuritySettings(boolean requireBadgePinLogin) { }

    /** Five shared price-tag designs, each with its own physical label size. */
    public static List<PriceTagTemplateSettings> loadPriceTagTemplateSettings() {
        Integer locationId = ServerRequestIdentity.locationId();
        try (Connection conn = DB.getConnection()) {
            if (locationId != null && data.DatabaseConfig.load().mode() == data.DatabaseMode.SERVER) {
                ensurePriceTagTemplateSchema(conn);
                try (PreparedStatement ps = conn.prepareStatement("SELECT price_tag_templates, price_tag_show_company, price_tag_show_sku, price_tag_show_barcode, price_tag_width_inches, price_tag_height_inches FROM company_customization WHERE location_id = ?")) {
                    ps.setInt(1, locationId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return decodePriceTagTemplates(rs.getString(1), legacyPriceTagTemplate(rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4), rs.getDouble(5), rs.getDouble(6)));
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        Properties p = loadProperties();
        return decodePriceTagTemplates(p.getProperty("price_tag.templates"), legacyPriceTagTemplate(Boolean.parseBoolean(p.getProperty("price_tag.show_company", "true")), Boolean.parseBoolean(p.getProperty("price_tag.show_sku", "true")), Boolean.parseBoolean(p.getProperty("price_tag.show_barcode", "true")), parseDouble(p.getProperty("price_tag.width_inches"), 2.25), parseDouble(p.getProperty("price_tag.height_inches"), 1.25)));
    }

    public static PriceTagTemplateSettings loadPriceTagTemplateSettings(int slot) { return loadPriceTagTemplateSettings().get(Math.max(0, Math.min(4, slot))); }

    public static void savePriceTagTemplateSettings(PriceTagTemplateSettings settings) throws IOException, SQLException {
        List<PriceTagTemplateSettings> templates = loadPriceTagTemplateSettings(); templates.set(0, settings); savePriceTagTemplateSettings(templates);
    }

    public static void savePriceTagTemplateSettings(List<PriceTagTemplateSettings> templates) throws IOException, SQLException {
        List<PriceTagTemplateSettings> complete = completePriceTagTemplates(templates); String encoded = encodePriceTagTemplates(complete);
        Integer locationId = ServerRequestIdentity.locationId();
        if (locationId != null && data.DatabaseConfig.load().mode() == data.DatabaseMode.SERVER) {
            try (Connection conn = DB.getConnection()) {
                ensurePriceTagTemplateSchema(conn);
                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO company_customization (location_id, price_tag_templates, updated_at)
                        VALUES (?, ?, NOW()) ON CONFLICT (location_id) DO UPDATE SET price_tag_templates = EXCLUDED.price_tag_templates, updated_at = NOW()
                        """)) {
                    ps.setInt(1, locationId); ps.setString(2, encoded); ps.executeUpdate();
                }
            }
        }
        Properties p = loadProperties(); p.setProperty("price_tag.templates", encoded);
        saveProperties(p);
    }

    private static void ensurePriceTagTemplateSchema(Connection conn) throws SQLException {
        SchemaContractService.requireLocalReady(conn);
    }

    private static PriceTagTemplateSettings legacyPriceTagTemplate(boolean company, boolean sku, boolean barcode, double width, double height) { return new PriceTagTemplateSettings("Standard", company, true, true, sku, barcode, true, true, width, height, ""); }
    private static List<PriceTagTemplateSettings> defaultPriceTagTemplates() { return new ArrayList<>(List.of(new PriceTagTemplateSettings("Standard", true,true,true,true,true,true,true,2.25,1.25,""),new PriceTagTemplateSettings("Small", true,true,true,true,true,true,true,1.5,.75,""),new PriceTagTemplateSettings("Large", true,true,true,true,true,true,true,3,2,""),new PriceTagTemplateSettings("Wide", true,true,true,true,true,true,true,4,1,""),new PriceTagTemplateSettings("Square", true,true,true,true,true,true,true,2,2,""))); }
    private static List<PriceTagTemplateSettings> completePriceTagTemplates(List<PriceTagTemplateSettings> templates) { List<PriceTagTemplateSettings> result=defaultPriceTagTemplates(); if(templates!=null)for(int i=0;i<Math.min(5,templates.size());i++)if(templates.get(i)!=null)result.set(i,templates.get(i)); return result; }
    private static String encodePriceTagTemplates(List<PriceTagTemplateSettings> templates) { StringBuilder out=new StringBuilder(); for(PriceTagTemplateSettings s:completePriceTagTemplates(templates)){if(!out.isEmpty())out.append(';');out.append(Base64.getUrlEncoder().withoutPadding().encodeToString(s.name().getBytes(StandardCharsets.UTF_8))).append('|').append(s.showCompany()).append('|').append(s.showName()).append('|').append(s.showPrice()).append('|').append(s.showSku()).append('|').append(s.showBarcode()).append('|').append(s.showSize()).append('|').append(s.showDescription()).append('|').append(s.widthInches()).append('|').append(s.heightInches()).append('|').append(Base64.getUrlEncoder().withoutPadding().encodeToString(s.layoutData().getBytes(StandardCharsets.UTF_8)));}return out.toString(); }
    private static List<PriceTagTemplateSettings> decodePriceTagTemplates(String value, PriceTagTemplateSettings legacy) { if(value==null||value.isBlank()){List<PriceTagTemplateSettings> d=defaultPriceTagTemplates();d.set(0,legacy);return d;} List<PriceTagTemplateSettings> d=defaultPriceTagTemplates();String[] rows=value.split(";");for(int i=0;i<Math.min(5,rows.length);i++){try{String[] p=rows[i].split("\\|");boolean newest=p.length>=11, modern=p.length>=9;String layout=newest?new String(Base64.getUrlDecoder().decode(p[10]),StandardCharsets.UTF_8):(modern?new String(Base64.getUrlDecoder().decode(p[8]),StandardCharsets.UTF_8):(p.length>6?new String(Base64.getUrlDecoder().decode(p[6]),StandardCharsets.UTF_8):""));d.set(i,new PriceTagTemplateSettings(new String(Base64.getUrlDecoder().decode(p[0]),StandardCharsets.UTF_8),Boolean.parseBoolean(p[1]),newest?Boolean.parseBoolean(p[2]):true,newest?Boolean.parseBoolean(p[3]):true,Boolean.parseBoolean(p[newest?4:2]),Boolean.parseBoolean(p[newest?5:3]),modern?Boolean.parseBoolean(p[newest?6:4]):true,modern?Boolean.parseBoolean(p[newest?7:5]):true,Double.parseDouble(p[newest?8:(modern?6:4)]),Double.parseDouble(p[newest?9:(modern?7:5)]),layout));}catch(Exception ignored){}}return d; }

    /** Pure LAN-client decoder for server-supplied price-tag settings. */
    public static List<PriceTagTemplateSettings> decodePriceTagTemplatesForLan(
            String encoded, boolean showCompany, boolean showSku, boolean showBarcode,
            double widthInches, double heightInches) {
        return decodePriceTagTemplates(encoded,
                legacyPriceTagTemplate(showCompany, showSku, showBarcode, widthInches, heightInches));
    }

    private static ReceiptSettings loadReceiptSettingsFromDb(int locationId) throws SQLException {
        String sql = """
                SELECT ci.company_name,
                       COALESCE(l.address, '') AS company_address_line1,
                       COALESCE(l.company_address_line1, '') AS company_address_line2,
                       COALESCE(l.company_address_line2, '') AS company_address_line3,
                       COALESCE(l.company_phone_line1, '') AS company_phone_line1,
                       COALESCE(l.company_phone_line2, '') AS company_phone_line2,
                       COALESCE(l.company_email_line1, '') AS company_email_line1,
                       COALESCE(l.company_email_line2, '') AS company_email_line2,
                       COALESCE(ci.company_motto_line1, '') AS company_motto_line1,
                       COALESCE(ci.company_motto_line2, '') AS company_motto_line2,
                       COALESCE(cc.receipt_header_line, '') AS receipt_header_line,
                       COALESCE(cc.receipt_footer_line, 'Thank you') AS receipt_footer_line,
                       COALESCE(ci.company_logo_url, '') AS receipt_logo_url,
                       COALESCE(cc.show_logo, FALSE) AS show_logo,
                       COALESCE(cc.show_sale_id, TRUE) AS show_sale_id,
                       COALESCE(cc.show_device, TRUE) AS show_device,
                       COALESCE(cc.show_customer, TRUE) AS show_customer,
                       COALESCE(cc.show_sku, TRUE) AS show_sku,
                       COALESCE(cc.show_item_discount, TRUE) AS show_item_discount,
                       COALESCE(cc.show_payment_status, TRUE) AS show_payment_status,
                       COALESCE(cc.vat_enabled, FALSE) AS vat_enabled,
                       COALESCE(cc.vat_use_department_rates, FALSE) AS vat_use_department_rates,
                       COALESCE(cc.vat_fixed_rate_percent, 0) AS vat_fixed_rate_percent,
                       COALESCE(cc.next_receipt_counter, 1) AS next_receipt_counter,
                       COALESCE(cc.change_basket_target_amount, 60000) AS change_basket_target_amount,
                       COALESCE(cc.always_print_sale_receipt, FALSE) AS always_print_sale_receipt,
                       COALESCE(cc.account_payment_receipt_title, 'CUSTOMER ACCOUNT PAYMENT') AS account_payment_receipt_title,
                       COALESCE(cc.account_payment_receipt_show_user, TRUE) AS account_payment_receipt_show_user,
                       COALESCE(cc.account_payment_receipt_show_customer, TRUE) AS account_payment_receipt_show_customer,
                       COALESCE(cc.account_payment_receipt_show_account_number, TRUE) AS account_payment_receipt_show_account_number,
                       COALESCE(cc.account_payment_receipt_show_method, TRUE) AS account_payment_receipt_show_method,
                       COALESCE(cc.account_payment_receipt_show_reference, TRUE) AS account_payment_receipt_show_reference,
                       COALESCE(cc.account_payment_receipt_show_device, TRUE) AS account_payment_receipt_show_device,
                       COALESCE(cc.account_payment_receipt_show_drawer, TRUE) AS account_payment_receipt_show_drawer,
                       COALESCE(cc.account_payment_receipt_show_allocations, TRUE) AS account_payment_receipt_show_allocations,
                       COALESCE(cc.account_payment_receipt_show_balance, TRUE) AS account_payment_receipt_show_balance,
                       COALESCE(cc.account_payment_receipt_show_barcode, TRUE) AS account_payment_receipt_show_barcode
                FROM company_info ci
                LEFT JOIN company_customization cc ON cc.location_id = ?
                LEFT JOIN locations l ON l.location_id = ?
                WHERE ci.company_info_id = 1
                """;

        try (Connection conn = DB.getConnection()) {
            ensureReceiptSettingsSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setInt(2, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                return new ReceiptSettings(
                        rs.getString("company_name"),
                        rs.getString("company_address_line1"),
                        rs.getString("company_address_line2"),
                        rs.getString("company_address_line3"),
                        rs.getString("company_phone_line1"),
                        rs.getString("company_phone_line2"),
                        rs.getString("company_email_line1"),
                        rs.getString("company_email_line2"),
                        rs.getString("company_motto_line1"),
                        rs.getString("company_motto_line2"),
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
                        rs.getBoolean("vat_enabled"),
                        rs.getBoolean("vat_use_department_rates"),
                        rs.getBigDecimal("vat_fixed_rate_percent"),
                        rs.getInt("next_receipt_counter"),
                        rs.getBigDecimal("change_basket_target_amount"),
                        rs.getBoolean("always_print_sale_receipt"),
                        new AccountPaymentReceiptSettings(
                                rs.getString("account_payment_receipt_title"),
                                rs.getBoolean("account_payment_receipt_show_user"),
                                rs.getBoolean("account_payment_receipt_show_customer"),
                                rs.getBoolean("account_payment_receipt_show_account_number"),
                                rs.getBoolean("account_payment_receipt_show_method"),
                                rs.getBoolean("account_payment_receipt_show_reference"),
                                rs.getBoolean("account_payment_receipt_show_device"),
                                rs.getBoolean("account_payment_receipt_show_drawer"),
                                rs.getBoolean("account_payment_receipt_show_allocations"),
                                rs.getBoolean("account_payment_receipt_show_balance"),
                                rs.getBoolean("account_payment_receipt_show_barcode")
                        )
                );
            }
            }
        }
    }

    private static void saveReceiptSettingsToDb(int locationId, ReceiptSettings settings) throws SQLException {
        String companySql = """
                INSERT INTO company_info (
                    company_info_id,
                    company_name,
                    company_motto_line1,
                    company_motto_line2,
                    company_logo_url,
                    updated_at
                )
                VALUES (1, ?, ?, ?, ?, NOW())
                ON CONFLICT (company_info_id) DO UPDATE SET
                    company_name = EXCLUDED.company_name,
                    company_motto_line1 = EXCLUDED.company_motto_line1,
                    company_motto_line2 = EXCLUDED.company_motto_line2,
                    company_logo_url = EXCLUDED.company_logo_url,
                    updated_at = NOW()
                """;
        String sql = """
                INSERT INTO company_customization (
                    location_id,
                    receipt_header_line,
                    receipt_footer_line,
                    show_logo,
                    show_sale_id,
                    show_device,
                    show_customer,
                    show_sku,
                    show_item_discount,
                    show_payment_status,
                    vat_enabled,
                    vat_use_department_rates,
                    vat_fixed_rate_percent,
                    next_receipt_counter,
                    change_basket_target_amount,
                    always_print_sale_receipt,
                    account_payment_receipt_title,
                    account_payment_receipt_show_user,
                    account_payment_receipt_show_customer,
                    account_payment_receipt_show_account_number,
                    account_payment_receipt_show_method,
                    account_payment_receipt_show_reference,
                    account_payment_receipt_show_device,
                    account_payment_receipt_show_drawer,
                    account_payment_receipt_show_allocations,
                    account_payment_receipt_show_balance,
                    account_payment_receipt_show_barcode,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (location_id) DO UPDATE SET
                    receipt_header_line = EXCLUDED.receipt_header_line,
                    receipt_footer_line = EXCLUDED.receipt_footer_line,
                    show_logo = EXCLUDED.show_logo,
                    show_sale_id = EXCLUDED.show_sale_id,
                    show_device = EXCLUDED.show_device,
                    show_customer = EXCLUDED.show_customer,
                    show_sku = EXCLUDED.show_sku,
                    show_item_discount = EXCLUDED.show_item_discount,
                    show_payment_status = EXCLUDED.show_payment_status,
                    vat_enabled = EXCLUDED.vat_enabled,
                    vat_use_department_rates = EXCLUDED.vat_use_department_rates,
                    vat_fixed_rate_percent = EXCLUDED.vat_fixed_rate_percent,
                    next_receipt_counter = EXCLUDED.next_receipt_counter,
                    change_basket_target_amount = EXCLUDED.change_basket_target_amount,
                    always_print_sale_receipt = EXCLUDED.always_print_sale_receipt,
                    account_payment_receipt_title = EXCLUDED.account_payment_receipt_title,
                    account_payment_receipt_show_user = EXCLUDED.account_payment_receipt_show_user,
                    account_payment_receipt_show_customer = EXCLUDED.account_payment_receipt_show_customer,
                    account_payment_receipt_show_account_number = EXCLUDED.account_payment_receipt_show_account_number,
                    account_payment_receipt_show_method = EXCLUDED.account_payment_receipt_show_method,
                    account_payment_receipt_show_reference = EXCLUDED.account_payment_receipt_show_reference,
                    account_payment_receipt_show_device = EXCLUDED.account_payment_receipt_show_device,
                    account_payment_receipt_show_drawer = EXCLUDED.account_payment_receipt_show_drawer,
                    account_payment_receipt_show_allocations = EXCLUDED.account_payment_receipt_show_allocations,
                    account_payment_receipt_show_balance = EXCLUDED.account_payment_receipt_show_balance,
                    account_payment_receipt_show_barcode = EXCLUDED.account_payment_receipt_show_barcode,
                    updated_at = NOW()
                """;

        try (Connection conn = DB.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(companySql)) {
                ps.setString(1, settings.companyName());
                ps.setString(2, settings.mottoLine1());
                ps.setString(3, settings.mottoLine2());
                ps.setString(4, settings.logoPath());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, locationId);
                ps.setString(2, settings.headerLine());
                ps.setString(3, settings.footerLine());
                ps.setBoolean(4, settings.showLogo());
                ps.setBoolean(5, settings.showSaleId());
                ps.setBoolean(6, settings.showDevice());
                ps.setBoolean(7, settings.showCustomer());
                ps.setBoolean(8, settings.showSku());
                ps.setBoolean(9, settings.showItemDiscount());
                ps.setBoolean(10, settings.showPaymentStatus());
                ps.setBoolean(11, settings.vatEnabled());
                ps.setBoolean(12, settings.vatUseDepartmentRates());
                ps.setBigDecimal(13, settings.vatFixedRatePercent());
                ps.setInt(14, settings.nextReceiptCounter());
                ps.setBigDecimal(15, settings.changeBasketTargetAmount());
                ps.setBoolean(16, settings.alwaysPrintSaleReceipt());
                AccountPaymentReceiptSettings paymentReceiptSettings = settings.accountPaymentReceiptSettings();
                ps.setString(17, paymentReceiptSettings.title());
                ps.setBoolean(18, paymentReceiptSettings.showUser());
                ps.setBoolean(19, paymentReceiptSettings.showCustomer());
                ps.setBoolean(20, paymentReceiptSettings.showAccountNumber());
                ps.setBoolean(21, paymentReceiptSettings.showMethod());
                ps.setBoolean(22, paymentReceiptSettings.showReference());
                ps.setBoolean(23, paymentReceiptSettings.showDevice());
                ps.setBoolean(24, paymentReceiptSettings.showDrawer());
                ps.setBoolean(25, paymentReceiptSettings.showAllocations());
                ps.setBoolean(26, paymentReceiptSettings.showBalance());
                ps.setBoolean(27, paymentReceiptSettings.showBarcode());
                ps.executeUpdate();
            }
        }
    }

    private static void ensureReceiptSettingsSchema(Connection conn) throws SQLException {
        SchemaContractService.requireLocalReady(conn);
    }

    public static java.math.BigDecimal loadChangeBasketTargetAmount() {
        return loadReceiptSettings().changeBasketTargetAmount();
    }

    public static java.math.BigDecimal loadChangeBasketTargetAmount(int locationId) throws SQLException {
        String sql = """
                SELECT COALESCE(change_basket_target_amount, 60000) AS change_basket_target_amount
                FROM company_customization
                WHERE location_id = ?
                """;
        try (Connection conn = DB.getConnection()) {
            ensureReceiptSettingsSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, locationId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        java.math.BigDecimal amount = rs.getBigDecimal("change_basket_target_amount");
                        return amount == null ? java.math.BigDecimal.valueOf(60000) : amount;
                    }
                }
            }
        }
        return java.math.BigDecimal.valueOf(60000);
    }

    public static void saveChangeBasketTargetAmount(int locationId, java.math.BigDecimal targetAmount) throws SQLException {
        java.math.BigDecimal cleanAmount = targetAmount == null ? java.math.BigDecimal.valueOf(60000) : targetAmount;
        if (cleanAmount.compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Change basket target cannot be negative.");
        }
        String sql = """
                INSERT INTO company_customization (
                    location_id,
                    change_basket_target_amount,
                    updated_at
                )
                VALUES (?, ?, NOW())
                ON CONFLICT (location_id) DO UPDATE SET
                    change_basket_target_amount = EXCLUDED.change_basket_target_amount,
                    updated_at = NOW()
                """;
        try (Connection conn = DB.getConnection()) {
            ensureReceiptSettingsSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, locationId);
                ps.setBigDecimal(2, cleanAmount);
                ps.executeUpdate();
            }
        }
        if (Objects.equals(cachedLocationId, locationId)) {
            cachedReceiptSettings = null;
        }
    }

    private static CustomOrderSettings loadCustomOrderSettingsFromDb(int locationId) throws SQLException {
        String sql = """
                SELECT COALESCE(custom_order_minimum_deposit_percent, 0) AS custom_order_minimum_deposit_percent,
                       COALESCE(custom_order_refund_approval_limit, 0) AS custom_order_refund_approval_limit,
                       COALESCE(round_custom_orders_to_nearest_twenty, TRUE) AS round_custom_orders_to_nearest_twenty
                FROM company_customization
                WHERE location_id = ?
                """;
        try (Connection conn = DB.getConnection()) {
            ensureReceiptSettingsSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new CustomOrderSettings(
                        rs.getBigDecimal("custom_order_minimum_deposit_percent"),
                        rs.getBigDecimal("custom_order_refund_approval_limit"),
                        rs.getBoolean("round_custom_orders_to_nearest_twenty")
                );
            }
        }
        }
    }

    private static void saveCustomOrderSettingsToDb(int locationId, CustomOrderSettings settings) throws SQLException {
        String sql = """
                INSERT INTO company_customization (
                    location_id,
                    custom_order_minimum_deposit_percent,
                    custom_order_refund_approval_limit,
                    round_custom_orders_to_nearest_twenty,
                    updated_at
                )
                VALUES (?, ?, ?, ?, NOW())
                ON CONFLICT (location_id) DO UPDATE SET
                    custom_order_minimum_deposit_percent = EXCLUDED.custom_order_minimum_deposit_percent,
                    custom_order_refund_approval_limit = EXCLUDED.custom_order_refund_approval_limit,
                    round_custom_orders_to_nearest_twenty = EXCLUDED.round_custom_orders_to_nearest_twenty,
                    updated_at = NOW()
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setBigDecimal(2, settings.minimumDepositPercent());
            ps.setBigDecimal(3, settings.refundApprovalLimit());
            ps.setBoolean(4, settings.roundToNearestTwenty());
            ps.executeUpdate();
        }
    }

    private static SaleSafetySettings loadSaleSafetySettingsFromDb(int locationId) throws SQLException {
        String sql = """
                SELECT COALESCE(sale_discount_limit_percent, 5) AS sale_discount_limit_percent,
                       COALESCE(sale_return_approval_limit, 0) AS sale_return_approval_limit,
                       COALESCE(require_cost_price_on_new_item, TRUE) AS require_cost_price_on_new_item,
                       COALESCE(round_sales_to_nearest_twenty, TRUE) AS round_sales_to_nearest_twenty
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
                        rs.getBigDecimal("sale_return_approval_limit"),
                        rs.getBoolean("require_cost_price_on_new_item"),
                        rs.getBoolean("round_sales_to_nearest_twenty")
                );
            }
        }
    }

    private static void saveSaleSafetySettingsToDb(int locationId, SaleSafetySettings settings) throws SQLException {
        String sql = """
                INSERT INTO company_customization (
                    location_id,
                    sale_discount_limit_percent,
                    sale_return_approval_limit,
                    require_cost_price_on_new_item,
                    round_sales_to_nearest_twenty,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, NOW())
                ON CONFLICT (location_id) DO UPDATE SET
                    sale_discount_limit_percent = EXCLUDED.sale_discount_limit_percent,
                    sale_return_approval_limit = EXCLUDED.sale_return_approval_limit,
                    require_cost_price_on_new_item = EXCLUDED.require_cost_price_on_new_item,
                    round_sales_to_nearest_twenty = EXCLUDED.round_sales_to_nearest_twenty,
                    updated_at = NOW()
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setBigDecimal(2, settings.discountLimitPercent());
            ps.setBigDecimal(3, settings.returnApprovalLimit());
            ps.setBoolean(4, settings.requireCostPriceOnNewItem());
            ps.setBoolean(5, settings.roundToNearestTwenty());
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
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
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

    private static QuotationInvoicePrintSettings loadQuotationInvoicePrintSettingsFromDb(int locationId) throws SQLException {
        String sql = """
                SELECT COALESCE(quotation_print_title, 'QUOTE / NOT FINAL SALE') AS quotation_title,
                       COALESCE(quotation_print_validity_note, 'This is a quote only and is not a final sale. Prices are valid until the valid-until date shown above unless superseded or cancelled.') AS quotation_validity_note,
                       COALESCE(invoice_print_title, 'INVOICE') AS invoice_title,
                       COALESCE(invoice_delivery_print_title, 'DELIVERY BILL') AS delivery_title,
                       COALESCE(quotation_invoice_print_footer_note, '') AS footer_note,
                       COALESCE(quotation_invoice_print_show_signatures, TRUE) AS show_signatures
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
                return new QuotationInvoicePrintSettings(
                        rs.getString("quotation_title"),
                        rs.getString("quotation_validity_note"),
                        rs.getString("invoice_title"),
                        rs.getString("delivery_title"),
                        rs.getString("footer_note"),
                        rs.getBoolean("show_signatures")
                );
            }
        }
    }

    private static void saveQuotationInvoicePrintSettingsToDb(int locationId, QuotationInvoicePrintSettings settings) throws SQLException {
        String sql = """
                INSERT INTO company_customization (
                    location_id,
                    quotation_print_title,
                    quotation_print_validity_note,
                    invoice_print_title,
                    invoice_delivery_print_title,
                    quotation_invoice_print_footer_note,
                    quotation_invoice_print_show_signatures,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (location_id) DO UPDATE SET
                    quotation_print_title = EXCLUDED.quotation_print_title,
                    quotation_print_validity_note = EXCLUDED.quotation_print_validity_note,
                    invoice_print_title = EXCLUDED.invoice_print_title,
                    invoice_delivery_print_title = EXCLUDED.invoice_delivery_print_title,
                    quotation_invoice_print_footer_note = EXCLUDED.quotation_invoice_print_footer_note,
                    quotation_invoice_print_show_signatures = EXCLUDED.quotation_invoice_print_show_signatures,
                    updated_at = NOW()
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setString(2, settings.quotationTitle());
            ps.setString(3, settings.quotationValidityNote());
            ps.setString(4, settings.invoiceTitle());
            ps.setString(5, settings.deliveryTitle());
            ps.setString(6, settings.footerNote());
            ps.setBoolean(7, settings.showSignatures());
            ps.executeUpdate();
        }
    }

    private static BadgeTemplateSettings loadBadgeTemplateSettingsFromDb(int locationId) throws SQLException {
        String sql = """
                SELECT COALESCE(cc.badge_template_company_name, ci.company_name, 'SmartStock') AS company_name,
                       COALESCE(NULLIF(cc.badge_template_logo_url, ''), ci.company_logo_url, '') AS logo_path,
                       COALESCE(badge_template_quote, '"Sales goes up and down, Service is Forever"') AS quote_line,
                       COALESCE(badge_template_signatory_name, 'Authorized Signature') AS signatory_name,
                       COALESCE(badge_template_signatory_title, 'Management') AS signatory_title,
                       COALESCE(badge_template_back_instructions, 'Scan, swipe, or tap this badge for SmartStock access.') AS back_instructions,
                       COALESCE(badge_template_show_quote, TRUE) AS show_quote,
                       COALESCE(badge_template_show_employee_id, TRUE) AS show_employee_id,
                       COALESCE(badge_template_show_issue_date, TRUE) AS show_issue_date,
                       COALESCE(badge_template_show_barcode, TRUE) AS show_barcode,
                       COALESCE(badge_template_show_badge_text, FALSE) AS show_badge_text,
                       COALESCE(badge_template_magstripe_enabled, FALSE) AS magstripe_enabled,
                       COALESCE(badge_template_magstripe_track1, '{badge_id}') AS magstripe_track1,
                       COALESCE(badge_template_magstripe_track2, '{badge_id}') AS magstripe_track2,
                       COALESCE(badge_template_magstripe_track3, '') AS magstripe_track3,
                       COALESCE(badge_template_magstripe_command, '') AS magstripe_command,
                       COALESCE(badge_template_nfc_enabled, FALSE) AS nfc_enabled,
                       COALESCE(badge_template_nfc_payload, '{badge_id}') AS nfc_payload,
                       COALESCE(badge_template_nfc_writer_command, '') AS nfc_writer_command,
                       COALESCE(badge_template_nfc_verify_command, '') AS nfc_verify_command,
                       COALESCE(badge_template_layout_data, '') AS layout_data
                FROM company_info ci
                LEFT JOIN company_customization cc ON cc.location_id = ?
                WHERE ci.company_info_id = 1
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new BadgeTemplateSettings(
                        rs.getString("company_name"),
                        rs.getString("logo_path"),
                        rs.getString("quote_line"),
                        rs.getString("signatory_name"),
                        rs.getString("signatory_title"),
                        rs.getString("back_instructions"),
                        rs.getBoolean("show_quote"),
                        rs.getBoolean("show_employee_id"),
                        rs.getBoolean("show_issue_date"),
                        rs.getBoolean("show_barcode"),
                        rs.getBoolean("show_badge_text"),
                        rs.getBoolean("magstripe_enabled"),
                        rs.getString("magstripe_track1"),
                        rs.getString("magstripe_track2"),
                        rs.getString("magstripe_track3"),
                        rs.getString("magstripe_command"),
                        rs.getBoolean("nfc_enabled"),
                        rs.getString("nfc_payload"),
                        rs.getString("nfc_writer_command"),
                        rs.getString("nfc_verify_command"),
                        rs.getString("layout_data")
                );
            }
            }
        }

    private static int sharedBadgeTemplateLocationId(int fallbackLocationId)throws SQLException{
        try(Connection conn=DB.getConnection();PreparedStatement ps=conn.prepareStatement("SELECT location_id FROM company_customization WHERE BTRIM(COALESCE(badge_template_layout_data,''))<>'' ORDER BY updated_at DESC NULLS LAST,location_id LIMIT 1");ResultSet rs=ps.executeQuery()){
            return rs.next()?rs.getInt(1):fallbackLocationId;
        }
    }

    private static void saveBadgeTemplateSettingsToDb(int locationId, BadgeTemplateSettings settings) throws SQLException {
        String sql = """
                INSERT INTO company_customization (
                    location_id,
                    badge_template_company_name,
                    badge_template_logo_url,
                    badge_template_quote,
                    badge_template_signatory_name,
                    badge_template_signatory_title,
                    badge_template_back_instructions,
                    badge_template_show_quote,
                    badge_template_show_employee_id,
                    badge_template_show_issue_date,
                    badge_template_show_barcode,
                    badge_template_show_badge_text,
                    badge_template_magstripe_enabled,
                    badge_template_magstripe_track1,
                    badge_template_magstripe_track2,
                    badge_template_magstripe_track3,
                    badge_template_magstripe_command,
                    badge_template_nfc_enabled,
                    badge_template_nfc_payload,
                    badge_template_nfc_writer_command,
                    badge_template_nfc_verify_command,
                    badge_template_layout_data,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (location_id) DO UPDATE SET
                    badge_template_company_name = EXCLUDED.badge_template_company_name,
                    badge_template_logo_url = EXCLUDED.badge_template_logo_url,
                    badge_template_quote = EXCLUDED.badge_template_quote,
                    badge_template_signatory_name = EXCLUDED.badge_template_signatory_name,
                    badge_template_signatory_title = EXCLUDED.badge_template_signatory_title,
                    badge_template_back_instructions = EXCLUDED.badge_template_back_instructions,
                    badge_template_show_quote = EXCLUDED.badge_template_show_quote,
                    badge_template_show_employee_id = EXCLUDED.badge_template_show_employee_id,
                    badge_template_show_issue_date = EXCLUDED.badge_template_show_issue_date,
                    badge_template_show_barcode = EXCLUDED.badge_template_show_barcode,
                    badge_template_show_badge_text = EXCLUDED.badge_template_show_badge_text,
                    badge_template_magstripe_enabled = EXCLUDED.badge_template_magstripe_enabled,
                    badge_template_magstripe_track1 = EXCLUDED.badge_template_magstripe_track1,
                    badge_template_magstripe_track2 = EXCLUDED.badge_template_magstripe_track2,
                    badge_template_magstripe_track3 = EXCLUDED.badge_template_magstripe_track3,
                    badge_template_magstripe_command = EXCLUDED.badge_template_magstripe_command,
                    badge_template_nfc_enabled = EXCLUDED.badge_template_nfc_enabled,
                    badge_template_nfc_payload = EXCLUDED.badge_template_nfc_payload,
                    badge_template_nfc_writer_command = EXCLUDED.badge_template_nfc_writer_command,
                    badge_template_nfc_verify_command = EXCLUDED.badge_template_nfc_verify_command,
                    badge_template_layout_data = EXCLUDED.badge_template_layout_data,
                    updated_at = NOW()
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setString(2, settings.companyName());
            ps.setString(3, settings.logoPath());
            ps.setString(4, settings.quoteLine());
            ps.setString(5, settings.signatoryName());
            ps.setString(6, settings.signatoryTitle());
            ps.setString(7, settings.backInstructions());
            ps.setBoolean(8, settings.showQuote());
            ps.setBoolean(9, settings.showEmployeeId());
            ps.setBoolean(10, settings.showIssueDate());
            ps.setBoolean(11, settings.showBarcode());
            ps.setBoolean(12, settings.showBadgeText());
            ps.setBoolean(13, settings.magStripeEnabled());
            ps.setString(14, settings.magStripeTrack1());
            ps.setString(15, settings.magStripeTrack2());
            ps.setString(16, settings.magStripeTrack3());
            ps.setString(17, settings.magStripeCommand());
            ps.setBoolean(18, settings.nfcEnabled());
            ps.setString(19, settings.nfcPayloadTemplate());
            ps.setString(20, settings.nfcWriterCommand());
            ps.setString(21, settings.nfcVerifyCommand());
            ps.setString(22, settings.layoutData());
            ps.executeUpdate();
            try(PreparedStatement shared=conn.prepareStatement("""
                    UPDATE company_customization target SET
                      badge_template_company_name=source.badge_template_company_name,badge_template_logo_url=source.badge_template_logo_url,
                      badge_template_quote=source.badge_template_quote,badge_template_signatory_name=source.badge_template_signatory_name,
                      badge_template_signatory_title=source.badge_template_signatory_title,badge_template_back_instructions=source.badge_template_back_instructions,
                      badge_template_show_quote=source.badge_template_show_quote,badge_template_show_employee_id=source.badge_template_show_employee_id,
                      badge_template_show_issue_date=source.badge_template_show_issue_date,badge_template_show_barcode=source.badge_template_show_barcode,
                      badge_template_show_badge_text=source.badge_template_show_badge_text,badge_template_magstripe_enabled=source.badge_template_magstripe_enabled,
                      badge_template_magstripe_track1=source.badge_template_magstripe_track1,badge_template_magstripe_track2=source.badge_template_magstripe_track2,
                      badge_template_magstripe_track3=source.badge_template_magstripe_track3,badge_template_magstripe_command=source.badge_template_magstripe_command,
                      badge_template_nfc_enabled=source.badge_template_nfc_enabled,badge_template_nfc_payload=source.badge_template_nfc_payload,
                      badge_template_nfc_writer_command=source.badge_template_nfc_writer_command,badge_template_nfc_verify_command=source.badge_template_nfc_verify_command,
                      badge_template_layout_data=source.badge_template_layout_data,updated_at=NOW()
                    FROM company_customization source WHERE source.location_id=? AND target.location_id<>?
                    """)){shared.setInt(1,locationId);shared.setInt(2,locationId);shared.executeUpdate();}
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
                properties.getProperty("receipt.address_line1", ""),
                properties.getProperty("receipt.address_line2", ""),
                properties.getProperty("receipt.address_line3", ""),
                properties.getProperty("receipt.phone_line1", ""),
                properties.getProperty("receipt.phone_line2", ""),
                properties.getProperty("receipt.email_line1", ""),
                properties.getProperty("receipt.email_line2", ""),
                properties.getProperty("receipt.motto_line1", ""),
                properties.getProperty("receipt.motto_line2", ""),
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
                Boolean.parseBoolean(properties.getProperty("receipt.vat_enabled", "false")),
                Boolean.parseBoolean(properties.getProperty("receipt.vat_use_department_rates", "false")),
                parsePercent(properties.getProperty("receipt.vat_fixed_rate_percent", "0")),
                parseIntInRange(properties.getProperty("receipt.next_receipt_counter", "1"), 1, 999999, 1),
                parseMoney(properties.getProperty("receipt.change_basket_target_amount", "60000")),
                Boolean.parseBoolean(properties.getProperty("receipt.always_print_sale_receipt", "false")),
                new AccountPaymentReceiptSettings(
                        properties.getProperty("account_payment_receipt.title", "CUSTOMER ACCOUNT PAYMENT"),
                        Boolean.parseBoolean(properties.getProperty("account_payment_receipt.show_user", "true")),
                        Boolean.parseBoolean(properties.getProperty("account_payment_receipt.show_customer", "true")),
                        Boolean.parseBoolean(properties.getProperty("account_payment_receipt.show_account_number", "true")),
                        Boolean.parseBoolean(properties.getProperty("account_payment_receipt.show_method", "true")),
                        Boolean.parseBoolean(properties.getProperty("account_payment_receipt.show_reference", "true")),
                        Boolean.parseBoolean(properties.getProperty("account_payment_receipt.show_device", "true")),
                        Boolean.parseBoolean(properties.getProperty("account_payment_receipt.show_drawer", "true")),
                        Boolean.parseBoolean(properties.getProperty("account_payment_receipt.show_allocations", "true")),
                        Boolean.parseBoolean(properties.getProperty("account_payment_receipt.show_balance", "true")),
                        Boolean.parseBoolean(properties.getProperty("account_payment_receipt.show_barcode", "true"))
                )
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
                parseMoney(properties.getProperty("custom_orders.refund_approval_limit", "0")),
                Boolean.parseBoolean(properties.getProperty("custom_orders.round_to_nearest_twenty", "true"))
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

    private static QuotationInvoicePrintSettings loadLocalQuotationInvoicePrintSettings() {
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream inputStream = Files.newInputStream(CONFIG_PATH)) {
                properties.load(inputStream);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return new QuotationInvoicePrintSettings(
                properties.getProperty("quotation_invoice.quotation_title", "QUOTE / NOT FINAL SALE"),
                properties.getProperty("quotation_invoice.quotation_validity_note", "This is a quote only and is not a final sale. Prices are valid until the valid-until date shown above unless superseded or cancelled."),
                properties.getProperty("quotation_invoice.invoice_title", "INVOICE"),
                properties.getProperty("quotation_invoice.delivery_title", "DELIVERY BILL"),
                properties.getProperty("quotation_invoice.footer_note", ""),
                Boolean.parseBoolean(properties.getProperty("quotation_invoice.show_signatures", "true"))
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
                parseMoney(properties.getProperty("sales.return_approval_limit", "0")),
                Boolean.parseBoolean(properties.getProperty("inventory.require_cost_price_on_new_item", "true")),
                Boolean.parseBoolean(properties.getProperty("sales.round_to_nearest_twenty", "true"))
        );
    }

    private static BadgeTemplateSettings loadLocalBadgeTemplateSettings() {
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream inputStream = Files.newInputStream(CONFIG_PATH)) {
                properties.load(inputStream);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return new BadgeTemplateSettings(
                properties.getProperty("badge_template.company_name", properties.getProperty("receipt.company_name", "SmartStock")),
                firstNonBlank(properties.getProperty("badge_template.logo_path"), properties.getProperty("receipt.logo_path"), ""),
                properties.getProperty("badge_template.quote", "\"Sales goes up and down, Service is Forever\""),
                properties.getProperty("badge_template.signatory_name", "Authorized Signature"),
                properties.getProperty("badge_template.signatory_title", "Management"),
                properties.getProperty("badge_template.back_instructions", "Scan, swipe, or tap this badge for SmartStock access."),
                Boolean.parseBoolean(properties.getProperty("badge_template.show_quote", "true")),
                Boolean.parseBoolean(properties.getProperty("badge_template.show_employee_id", "true")),
                Boolean.parseBoolean(properties.getProperty("badge_template.show_issue_date", "true")),
                Boolean.parseBoolean(properties.getProperty("badge_template.show_barcode", "true")),
                Boolean.parseBoolean(properties.getProperty("badge_template.show_badge_text", "false")),
                Boolean.parseBoolean(properties.getProperty("badge_template.magstripe_enabled", "false")),
                properties.getProperty("badge_template.magstripe_track1", "{badge_id}"),
                properties.getProperty("badge_template.magstripe_track2", "{badge_id}"),
                properties.getProperty("badge_template.magstripe_track3", ""),
                properties.getProperty("badge_template.magstripe_command", ""),
                Boolean.parseBoolean(properties.getProperty("badge_template.nfc_enabled", "false")),
                properties.getProperty("badge_template.nfc_payload", "{badge_id}"),
                properties.getProperty("badge_template.nfc_writer_command", ""),
                properties.getProperty("badge_template.nfc_verify_command", ""),
                properties.getProperty("badge_template.layout_data", "")
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
        properties.setProperty("receipt.address_line1", settings.addressLine1());
        properties.setProperty("receipt.address_line2", settings.addressLine2());
        properties.setProperty("receipt.address_line3", settings.addressLine3());
        properties.setProperty("receipt.phone_line1", settings.phoneLine1());
        properties.setProperty("receipt.phone_line2", settings.phoneLine2());
        properties.setProperty("receipt.email_line1", settings.emailLine1());
        properties.setProperty("receipt.email_line2", settings.emailLine2());
        properties.setProperty("receipt.motto_line1", settings.mottoLine1());
        properties.setProperty("receipt.motto_line2", settings.mottoLine2());
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
        properties.setProperty("receipt.vat_enabled", String.valueOf(settings.vatEnabled()));
        properties.setProperty("receipt.vat_use_department_rates", String.valueOf(settings.vatUseDepartmentRates()));
        properties.setProperty("receipt.vat_fixed_rate_percent", settings.vatFixedRatePercent().toPlainString());
        properties.setProperty("receipt.next_receipt_counter", String.valueOf(settings.nextReceiptCounter()));
        properties.setProperty("receipt.change_basket_target_amount", settings.changeBasketTargetAmount().toPlainString());
        properties.setProperty("receipt.always_print_sale_receipt", String.valueOf(settings.alwaysPrintSaleReceipt()));
        AccountPaymentReceiptSettings paymentReceiptSettings = settings.accountPaymentReceiptSettings();
        properties.setProperty("account_payment_receipt.title", paymentReceiptSettings.title());
        properties.setProperty("account_payment_receipt.show_user", String.valueOf(paymentReceiptSettings.showUser()));
        properties.setProperty("account_payment_receipt.show_customer", String.valueOf(paymentReceiptSettings.showCustomer()));
        properties.setProperty("account_payment_receipt.show_account_number", String.valueOf(paymentReceiptSettings.showAccountNumber()));
        properties.setProperty("account_payment_receipt.show_method", String.valueOf(paymentReceiptSettings.showMethod()));
        properties.setProperty("account_payment_receipt.show_reference", String.valueOf(paymentReceiptSettings.showReference()));
        properties.setProperty("account_payment_receipt.show_device", String.valueOf(paymentReceiptSettings.showDevice()));
        properties.setProperty("account_payment_receipt.show_drawer", String.valueOf(paymentReceiptSettings.showDrawer()));
        properties.setProperty("account_payment_receipt.show_allocations", String.valueOf(paymentReceiptSettings.showAllocations()));
        properties.setProperty("account_payment_receipt.show_balance", String.valueOf(paymentReceiptSettings.showBalance()));
        properties.setProperty("account_payment_receipt.show_barcode", String.valueOf(paymentReceiptSettings.showBarcode()));

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
        properties.setProperty("custom_orders.round_to_nearest_twenty", String.valueOf(settings.roundToNearestTwenty()));
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

    private static void saveLocalQuotationInvoicePrintSettings(QuotationInvoicePrintSettings settings) throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream inputStream = Files.newInputStream(CONFIG_PATH)) {
                properties.load(inputStream);
            }
        }
        properties.setProperty("quotation_invoice.quotation_title", settings.quotationTitle());
        properties.setProperty("quotation_invoice.quotation_validity_note", settings.quotationValidityNote());
        properties.setProperty("quotation_invoice.invoice_title", settings.invoiceTitle());
        properties.setProperty("quotation_invoice.delivery_title", settings.deliveryTitle());
        properties.setProperty("quotation_invoice.footer_note", settings.footerNote());
        properties.setProperty("quotation_invoice.show_signatures", String.valueOf(settings.showSignatures()));
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
        properties.setProperty("inventory.require_cost_price_on_new_item", String.valueOf(settings.requireCostPriceOnNewItem()));
        properties.setProperty("sales.round_to_nearest_twenty", String.valueOf(settings.roundToNearestTwenty()));
        try (OutputStream outputStream = Files.newOutputStream(CONFIG_PATH)) {
            properties.store(outputStream, "SmartStock company customization settings");
        }
    }

    private static void saveLocalBadgeTemplateSettings(BadgeTemplateSettings settings) throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream inputStream = Files.newInputStream(CONFIG_PATH)) {
                properties.load(inputStream);
            }
        }
        properties.setProperty("badge_template.company_name", settings.companyName());
        properties.setProperty("badge_template.logo_path", settings.logoPath());
        properties.setProperty("badge_template.quote", settings.quoteLine());
        properties.setProperty("badge_template.signatory_name", settings.signatoryName());
        properties.setProperty("badge_template.signatory_title", settings.signatoryTitle());
        properties.setProperty("badge_template.back_instructions", settings.backInstructions());
        properties.setProperty("badge_template.show_quote", String.valueOf(settings.showQuote()));
        properties.setProperty("badge_template.show_employee_id", String.valueOf(settings.showEmployeeId()));
        properties.setProperty("badge_template.show_issue_date", String.valueOf(settings.showIssueDate()));
        properties.setProperty("badge_template.show_barcode", String.valueOf(settings.showBarcode()));
        properties.setProperty("badge_template.show_badge_text", String.valueOf(settings.showBadgeText()));
        properties.setProperty("badge_template.magstripe_enabled", String.valueOf(settings.magStripeEnabled()));
        properties.setProperty("badge_template.magstripe_track1", settings.magStripeTrack1());
        properties.setProperty("badge_template.magstripe_track2", settings.magStripeTrack2());
        properties.setProperty("badge_template.magstripe_track3", settings.magStripeTrack3());
        properties.setProperty("badge_template.magstripe_command", settings.magStripeCommand());
        properties.setProperty("badge_template.nfc_enabled", String.valueOf(settings.nfcEnabled()));
        properties.setProperty("badge_template.nfc_payload", settings.nfcPayloadTemplate());
        properties.setProperty("badge_template.nfc_writer_command", settings.nfcWriterCommand());
        properties.setProperty("badge_template.nfc_verify_command", settings.nfcVerifyCommand());
        properties.setProperty("badge_template.layout_data", settings.layoutData());
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

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    public static String uploadReceiptLogo(Path sourcePath) throws Exception {
        return uploadCompanyLogo(sourcePath);
    }

    public static String uploadCompanyLogo(Path sourcePath) throws Exception {
        if (sourcePath == null || !Files.exists(sourcePath)) {
            throw new IOException("Logo file was not found.");
        }

        if (ServerRequestIdentity.locationId() != null) {
            return uploadReceiptLogoToStorage(sourcePath.toFile(), ServerRequestIdentity.locationId());
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

    public static String uploadBadgeTemplateImage(Path sourcePath) throws Exception {
        if (sourcePath == null || !Files.exists(sourcePath)) {
            throw new IOException("Badge template image was not found.");
        }

        if (ServerRequestIdentity.locationId() != null) {
            return uploadOriginalBadgeTemplateImageToStorage(sourcePath, ServerRequestIdentity.locationId());
        }

        String extension = getExtension(sourcePath.getFileName().toString());
        if (extension.isBlank()) {
            extension = "png";
        }
        Path targetPath = LOGO_DIRECTORY.resolve("badge-template-" + System.currentTimeMillis() + "." + extension.toLowerCase(Locale.ROOT));
        Files.createDirectories(LOGO_DIRECTORY);
        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        return targetPath.toString();
    }

    public static int badgeTemplateCount() {
        return 4;
    }

    public static String[] unpackBadgeTemplateLayouts(String layoutData) {
        String[] layouts = new String[badgeTemplateCount()];
        String clean = Objects.requireNonNullElse(layoutData, "").trim();
        if (!clean.startsWith("SSBT4|")) {
            layouts[0] = clean;
            for (int i = 1; i < layouts.length; i++) {
                layouts[i] = "";
            }
            return layouts;
        }
        for (int i = 0; i < layouts.length; i++) {
            layouts[i] = "";
        }
        for (String part : clean.split("\\|")) {
            int equals = part.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = part.substring(0, equals);
            String value = part.substring(equals + 1);
            if (key.matches("l[0-3]")) {
                layouts[Integer.parseInt(key.substring(1))] = decodeTemplatePart(value);
            }
        }
        return layouts;
    }

    public static int activeBadgeTemplateIndex(String layoutData) {
        String clean = Objects.requireNonNullElse(layoutData, "").trim();
        if (!clean.startsWith("SSBT4|")) {
            return 0;
        }
        for (String part : clean.split("\\|")) {
            if (part.startsWith("active=")) {
                try {
                    return Math.max(0, Math.min(badgeTemplateCount() - 1, Integer.parseInt(part.substring("active=".length()))));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    public static String activeBadgeTemplateLayout(String layoutData) {
        return badgeTemplateLayout(layoutData, activeBadgeTemplateIndex(layoutData));
    }

    public static String badgeTemplateLayout(String layoutData, int index) {
        String[] layouts = unpackBadgeTemplateLayouts(layoutData);
        int cleanIndex = Math.max(0, Math.min(layouts.length - 1, index));
        return layouts[cleanIndex];
    }

    public static String packBadgeTemplateLayouts(String[] layouts, int activeIndex) {
        String[] cleanLayouts = layouts == null ? new String[badgeTemplateCount()] : layouts;
        int cleanActive = Math.max(0, Math.min(badgeTemplateCount() - 1, activeIndex));
        StringBuilder builder = new StringBuilder("SSBT4|active=").append(cleanActive);
        for (int i = 0; i < badgeTemplateCount(); i++) {
            String layout = i < cleanLayouts.length ? Objects.requireNonNullElse(cleanLayouts[i], "") : "";
            builder.append("|l").append(i).append('=').append(encodeTemplatePart(layout));
        }
        return builder.toString();
    }

    public static String badgeTemplateDisplayName(int index) {
        return "Template " + (Math.max(0, index) + 1);
    }

    private static String encodeTemplatePart(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Objects.requireNonNullElse(value, "").getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeTemplatePart(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return "";
        }
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
            return ImageCacheManager.loadImage(settings.logoPath());
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public static List<UploadedImageOption> listUploadedCompanyLogos() throws Exception {
        Map<String, UploadedImageOption> options = new LinkedHashMap<>();
        addLogoOptionsFromDatabase(options);
        addLogoOptionsFromStorage(options);
        return new ArrayList<>(options.values());
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
    private static Integer cachedQuotationInvoicePrintLocationId;
    private static QuotationInvoicePrintSettings cachedQuotationInvoicePrintSettings;
    private static Integer cachedBadgeTemplateLocationId;
    private static BadgeTemplateSettings cachedBadgeTemplateSettings;

    private static String getExtension(String fileName) {
        int dotIndex = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1);
    }

    private static void addLogoOptionsFromDatabase(Map<String, UploadedImageOption> options) {
        String sql = """
                SELECT DISTINCT logo_url
                FROM (
                    SELECT NULLIF(TRIM(company_logo_url), '') AS logo_url
                    FROM company_info
                    UNION
                    SELECT NULLIF(TRIM(badge_template_logo_url), '') AS logo_url
                    FROM company_customization
                ) logos
                WHERE logo_url IS NOT NULL
                ORDER BY logo_url DESC
                """;
        try (Connection conn = DB.getConnection()) {
            ensureReceiptSettingsSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String url = rs.getString("logo_url");
                if (isRemoteImageUrl(url)) {
                    options.putIfAbsent(url, new UploadedImageOption(displayNameFromUrl(url), url));
                }
            }
            }
        } catch (Exception ex) {
            // The Storage list below can still recover uploaded logo files.
        }
    }

    private static void addLogoOptionsFromStorage(Map<String, UploadedImageOption> options) throws Exception {
        Integer locationId = ServerRequestIdentity.locationId();
        if (locationId != null) {
            addLogoOptionsFromStoragePrefix(options, "company/location-" + locationId);
        }
    }

    private static void addLogoOptionsFromStoragePrefix(Map<String, UploadedImageOption> options, String prefix) throws Exception {
        String accessToken = ServerRequestIdentity.supabaseAccessToken();
        String encodedBucket = encodePathSegment(COMPANY_LOGO_BUCKET);
        String requestBody = "{\"prefix\":\"" + jsonEscape(prefix) + "\",\"limit\":100,\"offset\":0,"
                + "\"sortBy\":{\"column\":\"created_at\",\"order\":\"desc\"}}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SupabaseSessionManager.getSupabaseUrl()
                        + "/storage/v1/object/list/"
                        + encodedBucket))
                .timeout(Duration.ofSeconds(20))
                .header("apikey", SupabaseSessionManager.getSupabasePublishableKey())
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Supabase Storage returned HTTP "
                    + response.statusCode()
                    + " while listing company logos: "
                    + response.body());
        }

        Matcher matcher = STORAGE_NAME_PATTERN.matcher(response.body());
        while (matcher.find()) {
            String name = unescapeJsonString(matcher.group(1));
            if (name.isBlank() || name.contains("/")) {
                continue;
            }
            String objectPath = prefix + "/" + name;
            String publicUrl = SupabaseSessionManager.getSupabaseUrl()
                    + "/storage/v1/object/public/"
                    + encodedBucket
                    + "/"
                    + encodeObjectPath(objectPath);
            options.putIfAbsent(publicUrl, new UploadedImageOption(name, publicUrl));
        }
    }

    private static String displayNameFromUrl(String url) {
        try {
            String path = URI.create(url).getPath();
            int slash = path == null ? -1 : path.lastIndexOf('/');
            if (slash >= 0 && slash < path.length() - 1) {
                return path.substring(slash + 1);
            }
        } catch (Exception ex) {
            // Use the full URL fallback below.
        }
        return url;
    }

    private static String jsonEscape(String value) {
        return Objects.requireNonNullElse(value, "")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private static String unescapeJsonString(String value) {
        return Objects.requireNonNullElse(value, "")
                .replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static String uploadReceiptLogoToStorage(File logoFile, int locationId) throws Exception {
        try (ImageOptimizationHelper.OptimizedImage optimizedImage = ImageOptimizationHelper.optimizeForUploadPreservingTransparency(
                logoFile,
                "receipt-logo",
                900,
                360,
                0.86f,
                MAX_ORIGINAL_LOGO_BYTES,
                MAX_LOGO_UPLOAD_BYTES
        )) {
            String filename = StorageObjectNameBuilder.filename(
                    optimizedImage.filename(), "png", Long.toString(System.currentTimeMillis()),
                    "location-" + locationId, "receipt-logo");
            String objectPath = "company/location-" + locationId + "/" + filename;
            try (java.sql.Connection conn = data.DB.getConnection()) {
                String reference = services.ServerImageAssetService.storeUpload(conn, "COMPANY_LOGO",
                        COMPANY_LOGO_BUCKET, objectPath, optimizedImage.contentType(), filename,
                        "PUBLIC", Files.readAllBytes(optimizedImage.file().toPath()));
                try { services.ServerImageAssetService.synchronize(conn); } catch (Exception ignored) { }
                return reference;
            }
        }
    }

    private static String uploadOriginalBadgeTemplateImageToStorage(Path imagePath, int locationId) throws Exception {
        long size = Files.size(imagePath);
        if (size > MAX_BADGE_TEMPLATE_IMAGE_BYTES) {
            throw new IOException("Badge template image is larger than " + (MAX_BADGE_TEMPLATE_IMAGE_BYTES / 1024 / 1024) + " MB.");
        }

        String filename = sanitizeFilename(imagePath.getFileName().toString());
        String contentType = Files.probeContentType(imagePath);
        if (contentType == null || contentType.isBlank()) {
            contentType = switch (getExtension(filename).toLowerCase(Locale.ROOT)) {
                case "jpg", "jpeg" -> "image/jpeg";
                case "gif" -> "image/gif";
                case "bmp" -> "image/bmp";
                case "webp" -> "image/webp";
                default -> "image/png";
            };
        }

        String descriptiveFilename = StorageObjectNameBuilder.filename(
                filename, "png", Long.toString(System.currentTimeMillis()),
                "location-" + locationId, "badge-logo");
        String objectPath = "company/location-" + locationId + "/" + descriptiveFilename;
        try (java.sql.Connection conn = data.DB.getConnection()) {
            String reference = services.ServerImageAssetService.storeUpload(conn, "BADGE_LOGO",
                    COMPANY_LOGO_BUCKET, objectPath, contentType, descriptiveFilename,
                    "PUBLIC", Files.readAllBytes(imagePath));
            try { services.ServerImageAssetService.synchronize(conn); } catch (Exception ignored) { }
            return reference;
        }
    }

    private static boolean isRemoteImageUrl(String imageUrl) {
        return ImageCacheManager.isRemoteImageUrl(imageUrl);
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

    private static Properties loadProperties() {
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream input = Files.newInputStream(CONFIG_PATH)) { properties.load(input); } catch (IOException ignored) { }
        }
        return properties;
    }

    private static void saveProperties(Properties properties) throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());
        try (OutputStream output = Files.newOutputStream(CONFIG_PATH)) { properties.store(output, "SmartStock company preferences"); }
    }

    private static double parseDouble(String value, double fallback) {
        try { return Double.parseDouble(value); } catch (Exception ignored) { return fallback; }
    }

    public record UploadedImageOption(String name, String url) {
        public UploadedImageOption {
            name = Objects.requireNonNullElse(name, "").trim();
            url = Objects.requireNonNullElse(url, "").trim();
        }

        @Override
        public String toString() {
            return name.isBlank() ? url : name;
        }
    }

    public record PriceTagTemplateSettings(String name, boolean showCompany, boolean showName, boolean showPrice, boolean showSku, boolean showBarcode, boolean showSize, boolean showDescription,
                                           double widthInches, double heightInches, String layoutData) {
        public PriceTagTemplateSettings {
            name = Objects.requireNonNullElse(name, "Template").trim();
            if (name.isBlank()) name = "Template";
            layoutData = Objects.requireNonNullElse(layoutData, "");
            widthInches = Math.max(.75, Math.min(6, widthInches <= 0 ? 2.25 : widthInches));
            heightInches = Math.max(.5, Math.min(4, heightInches <= 0 ? 1.25 : heightInches));
        }
    }

    public record ReceiptSettings(
            String companyName,
            String addressLine1,
            String addressLine2,
            String addressLine3,
            String phoneLine1,
            String phoneLine2,
            String emailLine1,
            String emailLine2,
            String mottoLine1,
            String mottoLine2,
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
            boolean vatEnabled,
            boolean vatUseDepartmentRates,
            java.math.BigDecimal vatFixedRatePercent,
            int nextReceiptCounter,
            java.math.BigDecimal changeBasketTargetAmount,
            boolean alwaysPrintSaleReceipt,
            AccountPaymentReceiptSettings accountPaymentReceiptSettings
    ) {
        public ReceiptSettings {
            companyName = clean(companyName, "SmartStock");
            addressLine1 = Objects.requireNonNullElse(addressLine1, "").trim();
            addressLine2 = Objects.requireNonNullElse(addressLine2, "").trim();
            addressLine3 = Objects.requireNonNullElse(addressLine3, "").trim();
            phoneLine1 = Objects.requireNonNullElse(phoneLine1, "").trim();
            phoneLine2 = Objects.requireNonNullElse(phoneLine2, "").trim();
            emailLine1 = Objects.requireNonNullElse(emailLine1, "").trim();
            emailLine2 = Objects.requireNonNullElse(emailLine2, "").trim();
            mottoLine1 = Objects.requireNonNullElse(mottoLine1, "").trim();
            mottoLine2 = Objects.requireNonNullElse(mottoLine2, "").trim();
            headerLine = Objects.requireNonNullElse(headerLine, "").trim();
            footerLine = clean(footerLine, "Thank you");
            logoPath = Objects.requireNonNullElse(logoPath, "").trim();
            vatFixedRatePercent = vatFixedRatePercent == null ? java.math.BigDecimal.ZERO : vatFixedRatePercent;
            if (vatFixedRatePercent.compareTo(java.math.BigDecimal.ZERO) < 0) {
                vatFixedRatePercent = java.math.BigDecimal.ZERO;
            } else if (vatFixedRatePercent.compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
                vatFixedRatePercent = java.math.BigDecimal.valueOf(100);
            }
            if (nextReceiptCounter < 1) {
                nextReceiptCounter = 1;
            }
            changeBasketTargetAmount = changeBasketTargetAmount == null ? java.math.BigDecimal.valueOf(60000) : changeBasketTargetAmount;
            if (changeBasketTargetAmount.compareTo(java.math.BigDecimal.ZERO) < 0) {
                changeBasketTargetAmount = java.math.BigDecimal.ZERO;
            }
            accountPaymentReceiptSettings = accountPaymentReceiptSettings == null
                    ? AccountPaymentReceiptSettings.defaults()
                    : accountPaymentReceiptSettings;
        }

        private static String clean(String value, String fallback) {
            String cleaned = Objects.requireNonNullElse(value, "").trim();
            return cleaned.isBlank() ? fallback : cleaned;
        }
    }

    public record AccountPaymentReceiptSettings(
            String title,
            boolean showUser,
            boolean showCustomer,
            boolean showAccountNumber,
            boolean showMethod,
            boolean showReference,
            boolean showDevice,
            boolean showDrawer,
            boolean showAllocations,
            boolean showBalance,
            boolean showBarcode
    ) {
        public AccountPaymentReceiptSettings {
            title = clean(title, "CUSTOMER ACCOUNT PAYMENT");
        }

        public static AccountPaymentReceiptSettings defaults() {
            return new AccountPaymentReceiptSettings(
                    "CUSTOMER ACCOUNT PAYMENT",
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true
            );
        }

        private static String clean(String value, String fallback) {
            String cleaned = Objects.requireNonNullElse(value, "").trim();
            return cleaned.isBlank() ? fallback : cleaned;
        }
    }

    public record CustomOrderSettings(java.math.BigDecimal minimumDepositPercent,
                                      java.math.BigDecimal refundApprovalLimit,
                                      boolean roundToNearestTwenty) {
        public CustomOrderSettings {
            minimumDepositPercent = minimumDepositPercent == null ? java.math.BigDecimal.ZERO : minimumDepositPercent;
            refundApprovalLimit = refundApprovalLimit == null ? java.math.BigDecimal.ZERO : refundApprovalLimit;
        }
    }

    public record SaleSafetySettings(java.math.BigDecimal discountLimitPercent, java.math.BigDecimal returnApprovalLimit,
                                      boolean requireCostPriceOnNewItem, boolean roundToNearestTwenty) {
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

    public record QuotationInvoicePrintSettings(
            String quotationTitle,
            String quotationValidityNote,
            String invoiceTitle,
            String deliveryTitle,
            String footerNote,
            boolean showSignatures
    ) {
        public QuotationInvoicePrintSettings {
            quotationTitle = clean(quotationTitle, "QUOTE / NOT FINAL SALE");
            quotationValidityNote = clean(quotationValidityNote, "This is a quote only and is not a final sale. Prices are valid until the valid-until date shown above unless superseded or cancelled.");
            invoiceTitle = clean(invoiceTitle, "INVOICE");
            if (invoiceTitle.equalsIgnoreCase("SALES ORDER CONFIRMATION")) invoiceTitle = "INVOICE";
            deliveryTitle = clean(deliveryTitle, "DELIVERY BILL");
            footerNote = Objects.requireNonNullElse(footerNote, "").trim();
        }

        private static String clean(String value, String fallback) {
            String cleaned = Objects.requireNonNullElse(value, "").trim();
            return cleaned.isBlank() ? fallback : cleaned;
        }
    }

    public record BadgeTemplateSettings(
            String companyName,
            String logoPath,
            String quoteLine,
            String signatoryName,
            String signatoryTitle,
            String backInstructions,
            boolean showQuote,
            boolean showEmployeeId,
            boolean showIssueDate,
            boolean showBarcode,
            boolean showBadgeText,
            boolean magStripeEnabled,
            String magStripeTrack1,
            String magStripeTrack2,
            String magStripeTrack3,
            String magStripeCommand,
            boolean nfcEnabled,
            String nfcPayloadTemplate,
            String nfcWriterCommand,
            String nfcVerifyCommand,
            String layoutData
    ) {
        public BadgeTemplateSettings {
            companyName = clean(companyName, "SmartStock");
            logoPath = Objects.requireNonNullElse(logoPath, "").trim();
            quoteLine = clean(quoteLine, "\"Sales goes up and down, Service is Forever\"");
            signatoryName = clean(signatoryName, "Authorized Signature");
            signatoryTitle = clean(signatoryTitle, "Management");
            backInstructions = clean(backInstructions, "Scan, swipe, or tap this badge for SmartStock access.");
            magStripeTrack1 = clean(magStripeTrack1, "{badge_id}");
            magStripeTrack2 = clean(magStripeTrack2, "{badge_id}");
            magStripeTrack3 = Objects.requireNonNullElse(magStripeTrack3, "").trim();
            magStripeCommand = Objects.requireNonNullElse(magStripeCommand, "").trim();
            nfcPayloadTemplate = clean(nfcPayloadTemplate, "{badge_id}");
            nfcWriterCommand = Objects.requireNonNullElse(nfcWriterCommand, "").trim();
            nfcVerifyCommand = Objects.requireNonNullElse(nfcVerifyCommand, "").trim();
            layoutData = Objects.requireNonNullElse(layoutData, "").trim();
        }

        private static String clean(String value, String fallback) {
            String cleaned = Objects.requireNonNullElse(value, "").trim();
            return cleaned.isBlank() ? fallback : cleaned;
        }

        public BadgeTemplateSettings withLayoutData(String updatedLayoutData) {
            return new BadgeTemplateSettings(
                    companyName,
                    logoPath,
                    quoteLine,
                    signatoryName,
                    signatoryTitle,
                    backInstructions,
                    showQuote,
                    showEmployeeId,
                    showIssueDate,
                    showBarcode,
                    showBadgeText,
                    magStripeEnabled,
                    magStripeTrack1,
                    magStripeTrack2,
                    magStripeTrack3,
                    magStripeCommand,
                    nfcEnabled,
                    nfcPayloadTemplate,
                    nfcWriterCommand,
                    nfcVerifyCommand,
                    updatedLayoutData
            );
        }
    }
}
