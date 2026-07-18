package managers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import services.LanApiClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Register-side company-customization gateway.
 *
 * <p>The public records and pure formatting/image helpers are inherited from the
 * server repository for source compatibility. Every persistent read or write is
 * sent to the SmartStock LAN service; this class never opens a database
 * connection.</p>
 */
public final class CompanyCustomizationManager extends ServerCompanyCustomizationRepository {
    private static final Gson GSON = new Gson();
    private static ReceiptSettings previewOverrideSettings;

    private CompanyCustomizationManager() { }

    public static ReceiptSettings loadReceiptSettings() {
        return read("RECEIPT", ReceiptSettings.class);
    }

    public static void saveReceiptSettings(ReceiptSettings settings) throws IOException, SQLException {
        save("RECEIPT", settings);
    }

    public static CustomOrderSettings loadCustomOrderSettings() {
        return read("CUSTOM_ORDER", CustomOrderSettings.class);
    }

    public static void saveCustomOrderSettings(CustomOrderSettings settings) throws IOException, SQLException {
        save("CUSTOM_ORDER", settings);
    }

    public static SaleSafetySettings loadSaleSafetySettings() {
        return read("SALE_SAFETY", SaleSafetySettings.class);
    }

    public static void saveSaleSafetySettings(SaleSafetySettings settings) throws IOException, SQLException {
        save("SALE_SAFETY", settings);
    }

    public static CustomOrderSlipSettings loadCustomOrderSlipSettings() {
        return read("CUSTOM_ORDER_SLIP", CustomOrderSlipSettings.class);
    }

    public static void saveCustomOrderSlipSettings(CustomOrderSlipSettings settings) throws IOException, SQLException {
        save("CUSTOM_ORDER_SLIP", settings);
    }

    public static QuotationInvoicePrintSettings loadQuotationInvoicePrintSettings() {
        return read("QUOTATION_INVOICE", QuotationInvoicePrintSettings.class);
    }

    public static void saveQuotationInvoicePrintSettings(QuotationInvoicePrintSettings settings) throws IOException, SQLException {
        save("QUOTATION_INVOICE", settings);
    }

    public static BadgeTemplateSettings loadBadgeTemplateSettings() {
        return read("BADGE_TEMPLATE", BadgeTemplateSettings.class);
    }

    public static void saveBadgeTemplateSettings(BadgeTemplateSettings settings) throws IOException, SQLException {
        save("BADGE_TEMPLATE", settings);
    }

    public static List<PriceTagTemplateSettings> loadPriceTagTemplateSettings() {
        try {
            JsonObject response = LanApiClient.companyCustomizationRead("PRICE_TAGS", null);
            List<PriceTagTemplateSettings> values = GSON.fromJson(
                    response.get("settings"), new TypeToken<List<PriceTagTemplateSettings>>() { }.getType());
            return values == null ? List.of() : List.copyOf(values);
        } catch (Exception ex) {
            throw unavailable(ex);
        }
    }

    public static PriceTagTemplateSettings loadPriceTagTemplateSettings(int slot) {
        List<PriceTagTemplateSettings> settings = loadPriceTagTemplateSettings();
        if (settings.isEmpty()) {
            throw new IllegalStateException("No price-tag templates are configured.");
        }
        return settings.get(Math.max(0, Math.min(settings.size() - 1, slot)));
    }

    public static void savePriceTagTemplateSettings(PriceTagTemplateSettings settings) throws IOException, SQLException {
        List<PriceTagTemplateSettings> templates = new ArrayList<>(loadPriceTagTemplateSettings());
        int slot = 0;
        while (templates.size() <= slot) {
            templates.add(settings);
        }
        templates.set(slot, settings);
        savePriceTagTemplateSettings(templates);
    }

    public static void savePriceTagTemplateSettings(List<PriceTagTemplateSettings> settings) throws IOException, SQLException {
        save("PRICE_TAGS", settings);
    }

    public static BigDecimal loadChangeBasketTargetAmount() {
        Integer locationId = SessionManager.getCurrentLocationId();
        if (locationId == null) {
            return BigDecimal.valueOf(60000);
        }
        try {
            return loadChangeBasketTargetAmount(locationId);
        } catch (SQLException ex) {
            throw unavailable(ex);
        }
    }

    public static BigDecimal loadChangeBasketTargetAmount(int locationId) throws SQLException {
        try {
            JsonObject response = LanApiClient.companyCustomizationRead("CHANGE_BASKET_TARGET", locationId);
            return response.get("settings").getAsBigDecimal();
        } catch (Exception ex) {
            throw sql(ex);
        }
    }

    public static void saveChangeBasketTargetAmount(int locationId, BigDecimal targetAmount) throws SQLException {
        if (targetAmount == null || targetAmount.signum() < 0) {
            throw new IllegalArgumentException("Change basket target cannot be negative.");
        }
        try {
            LanApiClient.companyCustomizationSave("CHANGE_BASKET_TARGET", GSON.toJsonTree(targetAmount),
                    locationId, UUID.randomUUID().toString());
        } catch (Exception ex) {
            throw sql(ex);
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

    public static String uploadReceiptLogo(Path sourcePath) throws Exception {
        return uploadCompanyLogo(sourcePath);
    }

    public static String uploadCompanyLogo(Path sourcePath) throws Exception {
        return upload("COMPANY_LOGO", sourcePath);
    }

    public static String uploadBadgeTemplateImage(Path sourcePath) throws Exception {
        return upload("BADGE_TEMPLATE_IMAGE", sourcePath);
    }

    public static List<UploadedImageOption> listUploadedCompanyLogos() throws Exception {
        JsonObject response = LanApiClient.companyCustomizationRead("UPLOADED_IMAGES", null);
        List<UploadedImageOption> values = GSON.fromJson(
                response.get("settings"), new TypeToken<List<UploadedImageOption>>() { }.getType());
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String upload(String action, Path sourcePath) throws Exception {
        if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
            throw new IOException("Image file was not found.");
        }
        byte[] bytes = Files.readAllBytes(sourcePath);
        JsonObject payload = new JsonObject();
        payload.addProperty("fileName", sourcePath.getFileName().toString());
        payload.addProperty("contentBase64", Base64.getEncoder().encodeToString(bytes));
        JsonObject response = LanApiClient.companyCustomizationSave(action, payload, null,
                UUID.randomUUID().toString());
        return response.get("path").getAsString();
    }

    private static <T> T read(String action, Class<T> type) {
        try {
            return GSON.fromJson(LanApiClient.companyCustomizationRead(action, null).get("settings"), type);
        } catch (Exception ex) {
            throw unavailable(ex);
        }
    }

    private static void save(String action, Object settings) throws IOException, SQLException {
        try {
            LanApiClient.companyCustomizationSave(action, GSON.toJsonTree(settings), null,
                    UUID.randomUUID().toString());
        } catch (Exception ex) {
            throw sql(ex);
        }
    }

    private static IllegalStateException unavailable(Exception ex) {
        return new IllegalStateException("SmartStock Server is unavailable. Retry when the store server is reachable.", ex);
    }

    private static SQLException sql(Exception ex) {
        return ex instanceof SQLException sql ? sql : new SQLException(ex.getMessage(), ex);
    }
}
