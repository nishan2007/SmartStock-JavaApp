package services;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import managers.CompanyCustomizationManager;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import utils.ImageCacheManager;

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.OrientationRequested;
import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;

public final class BadgePrintService {
    private static final int CARD_WIDTH = 638;
    private static final int CARD_HEIGHT = 1013;
    private static final int PRINT_RENDER_SCALE = 4;
    private static final double CARD_WIDTH_INCHES = 2.125;
    private static final double CARD_HEIGHT_INCHES = 3.375;
    private static final Rectangle DEFAULT_BARCODE_RECT = new Rectangle(315, 10, 300, 990);
    private static double actualSizePreviewCalibration = 1.0;
    private static final Color DECKERS_ORANGE = new Color(255, 112, 0);
    private static final Color DECKERS_GREEN = new Color(32, 92, 0);
    private static final Color BORDER_BLUE = new Color(0, 55, 96);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final LinkedHashMap<String, BadgeElement> DEFAULT_ELEMENTS = new LinkedHashMap<>();

    static {
        DEFAULT_ELEMENTS.put("front.background", new BadgeElement("front.background", "Front Background", "front", new Rectangle(0, 0, CARD_WIDTH, CARD_HEIGHT)));
        DEFAULT_ELEMENTS.put("front.logo", new BadgeElement("front.logo", "Logo", "front", new Rectangle(108, 72, 425, 165)));
        DEFAULT_ELEMENTS.put("front.templateImage", new BadgeElement("front.templateImage", "Front Extra Image", "front", new Rectangle(445, 820, 105, 105)));
        DEFAULT_ELEMENTS.put("front.quote", new BadgeElement("front.quote", "Quote", "front", new Rectangle(90, 270, 460, 95)));
        DEFAULT_ELEMENTS.put("front.photo", new BadgeElement("front.photo", "Photo", "front", new Rectangle(92, 533, 165, 190)));
        DEFAULT_ELEMENTS.put("front.roleBand", new BadgeElement("front.roleBand", "Role Color Block", "front", new Rectangle(0, 508, CARD_WIDTH, 54)));
        DEFAULT_ELEMENTS.put("front.nameBand", new BadgeElement("front.nameBand", "Name Color Block", "front", new Rectangle(0, 562, CARD_WIDTH, 70)));
        DEFAULT_ELEMENTS.put("front.role", new BadgeElement("front.role", "Role", "front", new Rectangle(286, 508, 338, 54)));
        DEFAULT_ELEMENTS.put("front.name", new BadgeElement("front.name", "Name", "front", new Rectangle(286, 562, 338, 70)));
        DEFAULT_ELEMENTS.put("front.custom1", new BadgeElement("front.custom1", "Front Custom Text 1", "front", new Rectangle(55, 835, 528, 34)));
        DEFAULT_ELEMENTS.put("front.custom2", new BadgeElement("front.custom2", "Front Custom Text 2", "front", new Rectangle(55, 880, 528, 34)));
        DEFAULT_ELEMENTS.put("back.background", new BadgeElement("back.background", "Back Background", "back", new Rectangle(0, 0, CARD_WIDTH, CARD_HEIGHT)));
        DEFAULT_ELEMENTS.put("back.templateImage", new BadgeElement("back.templateImage", "Back Extra Image", "back", new Rectangle(48, 500, 130, 130)));
        DEFAULT_ELEMENTS.put("back.instructions", new BadgeElement("back.instructions", "Instructions", "back", new Rectangle(50, 75, 538, 38)));
        DEFAULT_ELEMENTS.put("back.employeeNumber", new BadgeElement("back.employeeNumber", "Employee Number", "back", new Rectangle(48, 250, 315, 28)));
        DEFAULT_ELEMENTS.put("back.issueDate", new BadgeElement("back.issueDate", "Issue Date", "back", new Rectangle(48, 286, 315, 28)));
        DEFAULT_ELEMENTS.put("back.expiryDate", new BadgeElement("back.expiryDate", "Expiry Date", "back", new Rectangle(48, 322, 315, 28)));
        DEFAULT_ELEMENTS.put("back.custom1", new BadgeElement("back.custom1", "Back Custom Text 1", "back", new Rectangle(48, 135, 315, 42)));
        DEFAULT_ELEMENTS.put("back.custom2", new BadgeElement("back.custom2", "Back Custom Text 2", "back", new Rectangle(48, 190, 315, 42)));
        DEFAULT_ELEMENTS.put("back.watermark", new BadgeElement("back.watermark", "Back Watermark", "back", new Rectangle(50, 365, 330, 100)));
        DEFAULT_ELEMENTS.put("back.poweredBy", new BadgeElement("back.poweredBy", "Powered By", "back", new Rectangle(48, 940, 270, 28)));
        DEFAULT_ELEMENTS.put("back.printCount", new BadgeElement("back.printCount", "Print Count", "back", new Rectangle(48, 970, 270, 18)));
        DEFAULT_ELEMENTS.put("back.barcode", new BadgeElement("back.barcode", "Barcode", "back", new Rectangle(DEFAULT_BARCODE_RECT)));
        DEFAULT_ELEMENTS.put("back.signature", new BadgeElement("back.signature", "Signature", "back", new Rectangle(75, 790, 205, 80)));
        DEFAULT_ELEMENTS.put("back.badgeText", new BadgeElement("back.badgeText", "Badge ID Text", "back", new Rectangle(48, 884, 270, 26)));
    }

    private BadgePrintService() {
    }

    public static EmployeeBadgeData loadEmployeeBadgeData(int userId) throws Exception {
        return LanApiClient.loadEmployeeBadgeData(userId);
    }

    public static EmployeeBadgeData loadEmployeeBadgeData(Connection conn, int userId,
                                                          int locationId) throws Exception {
        String sql = """
                SELECT u.user_id,
                       u.username,
                       u.full_name,
                       u.first_name,
                       u.middle_name,
                       u.last_name,
                       COALESCE(u.email, '') AS email,
                       COALESCE(u.phone, '') AS phone,
                       COALESCE(u.badge_id, '') AS badge_id,
                       COALESCE(u.employee_photo_url, '') AS employee_photo_url,
                       COALESCE(u.badge_print_count, 0) AS badge_print_count,
                       COALESCE(r.role_name, 'USER') AS role_name,
                       COALESCE(l.name, '') AS location_name
                FROM users u
                LEFT JOIN roles r ON u.role_id = r.role_id
                LEFT JOIN user_locations ul ON ul.user_id = u.user_id
                LEFT JOIN locations l ON l.location_id = ul.location_id
                WHERE u.user_id = ? AND ul.location_id = ?
                ORDER BY l.name NULLS LAST
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Employee was not found.");
                }
                return new EmployeeBadgeData(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("full_name"),
                        rs.getString("first_name"),
                        rs.getString("middle_name"),
                        rs.getString("last_name"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getString("badge_id"),
                        rs.getString("employee_photo_url"),
                        rs.getInt("badge_print_count"),
                        rs.getString("role_name"),
                        rs.getString("location_name")
                );
            }
        }
    }

    public static BufferedImage renderFront(EmployeeBadgeData employee, CompanyCustomizationManager.BadgeTemplateSettings settings) {
        BufferedImage image = createCardImage(1);
        Graphics2D g = image.createGraphics();
        configure(g);
        paintCardShell(g);
        paintFront(g, employee, settings);
        g.dispose();
        return image;
    }

    private static BufferedImage renderFront(EmployeeBadgeData employee, CompanyCustomizationManager.BadgeTemplateSettings settings, int renderScale) {
        BufferedImage image = createCardImage(renderScale);
        Graphics2D g = image.createGraphics();
        configure(g);
        g.scale(renderScale, renderScale);
        paintCardShell(g);
        paintFront(g, employee, settings);
        g.dispose();
        return image;
    }

    public static List<BadgeElement> elementsForSide(String side) {
        List<BadgeElement> elements = new ArrayList<>();
        for (BadgeElement element : DEFAULT_ELEMENTS.values()) {
            if (element.side().equals(side)) {
                elements.add(element);
            }
        }
        return elements;
    }

    public static List<BadgeElement> elementsForSideInLayerOrder(CompanyCustomizationManager.BadgeTemplateSettings settings, String side) {
        List<BadgeElement> elements = elementsForSide(side);
        elements.sort(Comparator
                .comparingInt((BadgeElement element) -> elementZOrder(settings, element.id()))
                .thenComparingInt(element -> defaultZOrder(element.id())));
        return elements;
    }

    public static BadgeElement elementForId(String elementId) {
        return DEFAULT_ELEMENTS.get(elementId);
    }

    public static Rectangle layoutRect(CompanyCustomizationManager.BadgeTemplateSettings settings, String elementId) {
        BadgeElement fallback = DEFAULT_ELEMENTS.get(elementId);
        Rectangle rect = fallback == null ? new Rectangle(0, 0, 100, 40) : new Rectangle(fallback.defaultRect());
        String layout = settings == null ? "" : settings.layoutData();
        for (String entry : layout.split(";")) {
            String trimmed = entry.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            int equals = trimmed.indexOf('=');
            if (equals <= 0 || !elementId.equals(trimmed.substring(0, equals))) {
                continue;
            }
            String[] parts = trimmed.substring(equals + 1).split(",");
            if (parts.length < 4) {
                continue;
            }
            try {
                rect = new Rectangle(
                        Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim()),
                        Integer.parseInt(parts[3].trim())
                );
            } catch (NumberFormatException ignored) {
                return rect;
            }
        }
        return "back.barcode".equals(elementId) ? sanitizeBarcodeRect(rect) : sanitizeRect(rect);
    }

    public static String updateLayoutRect(String layout, String elementId, Rectangle updatedRect) {
        LinkedHashMap<String, LayoutEntry> entries = parseLayoutEntries(layout);
        LayoutEntry existing = entries.get(elementId);
        entries.put(elementId, new LayoutEntry(
                sanitizeRect(updatedRect),
                existing == null ? BadgeTextStyle.defaultFor(elementId) : existing.style(),
                existing == null ? 0 : existing.rotation(),
                existing == null ? "" : existing.text(),
                existing == null ? "" : existing.imagePath(),
                existing == null ? 1f : existing.opacity(),
                existing == null ? defaultColorHex(elementId) : existing.colorHex(),
                existing == null ? "" : existing.nameLayout(),
                existing == null ? null : existing.visible(),
                existing == null ? null : existing.zOrder()
        ));
        return serializeLayout(entries);
    }

    public static int elementRotation(CompanyCustomizationManager.BadgeTemplateSettings settings, String elementId) {
        LayoutEntry entry = parseLayoutEntries(settings == null ? "" : settings.layoutData()).get(elementId);
        return entry == null ? 0 : normalizeRotation(entry.rotation());
    }

    public static String updateElementRotation(String layout, String elementId, int rotation) {
        if (!DEFAULT_ELEMENTS.containsKey(elementId)) {
            return layout == null ? "" : layout;
        }
        LinkedHashMap<String, LayoutEntry> entries = parseLayoutEntries(layout);
        LayoutEntry existing = entries.get(elementId);
        Rectangle rect = existing == null ? layoutRect(null, elementId) : existing.rect();
        BadgeTextStyle style = existing == null ? BadgeTextStyle.defaultFor(elementId) : existing.style();
        entries.put(elementId, new LayoutEntry(
                rect,
                style,
                normalizeRotation(rotation),
                existing == null ? "" : existing.text(),
                existing == null ? "" : existing.imagePath(),
                existing == null ? 1f : existing.opacity(),
                existing == null ? defaultColorHex(elementId) : existing.colorHex(),
                existing == null ? "" : existing.nameLayout(),
                existing == null ? null : existing.visible(),
                existing == null ? null : existing.zOrder()
        ));
        return serializeLayout(entries);
    }

    public static BadgeTextStyle textStyle(CompanyCustomizationManager.BadgeTemplateSettings settings, String elementId) {
        if (!isTextElement(elementId)) {
            return BadgeTextStyle.defaultFor(elementId);
        }
        LayoutEntry entry = parseLayoutEntries(settings == null ? "" : settings.layoutData()).get(elementId);
        return entry == null ? BadgeTextStyle.defaultFor(elementId) : entry.style().withDefaults(elementId);
    }

    public static String updateTextStyle(String layout, String elementId, BadgeTextStyle updatedStyle) {
        if (!isTextElement(elementId)) {
            return layout == null ? "" : layout;
        }
        LinkedHashMap<String, LayoutEntry> entries = parseLayoutEntries(layout);
        LayoutEntry existing = entries.get(elementId);
        Rectangle rect = existing == null ? layoutRect(null, elementId) : existing.rect();
        entries.put(elementId, new LayoutEntry(
                rect,
                updatedStyle.withDefaults(elementId),
                existing == null ? 0 : existing.rotation(),
                existing == null ? "" : existing.text(),
                existing == null ? "" : existing.imagePath(),
                existing == null ? 1f : existing.opacity(),
                existing == null ? defaultColorHex(elementId) : existing.colorHex(),
                existing == null ? "" : existing.nameLayout(),
                existing == null ? null : existing.visible(),
                existing == null ? null : existing.zOrder()
        ));
        return serializeLayout(entries);
    }

    public static String updateTextAlignment(String layout, String elementId, String alignment) {
        if (!isTextElement(elementId)) {
            return layout == null ? "" : layout;
        }
        LinkedHashMap<String, LayoutEntry> entries = parseLayoutEntries(layout);
        LayoutEntry existing = entries.get(elementId);
        Rectangle rect = existing == null ? layoutRect(null, elementId) : existing.rect();
        BadgeTextStyle style = existing == null ? BadgeTextStyle.defaultFor(elementId) : existing.style().withDefaults(elementId);
        entries.put(elementId, new LayoutEntry(
                rect,
                new BadgeTextStyle(style.family(), style.style(), style.maxSize(), alignment, style.weight(), style.allCaps(), style.textOutline(), style.textOutlineColorHex(), style.boxOutline(), style.boxOutlineColorHex()),
                existing == null ? 0 : existing.rotation(),
                existing == null ? "" : existing.text(),
                existing == null ? "" : existing.imagePath(),
                existing == null ? 1f : existing.opacity(),
                existing == null ? defaultColorHex(elementId) : existing.colorHex(),
                existing == null ? "" : existing.nameLayout(),
                existing == null ? null : existing.visible(),
                existing == null ? null : existing.zOrder()
        ));
        return serializeLayout(entries);
    }

    public static String customText(CompanyCustomizationManager.BadgeTemplateSettings settings, String elementId) {
        if (!isCustomTextElement(elementId)) {
            return "";
        }
        LayoutEntry entry = parseLayoutEntries(settings == null ? "" : settings.layoutData()).get(elementId);
        String savedText = entry == null ? "" : Objects.requireNonNullElse(entry.text(), "");
        return savedText.isBlank() ? defaultCustomText(elementId) : savedText;
    }

    public static String updateCustomText(String layout, String elementId, String text) {
        if (!isCustomTextElement(elementId)) {
            return layout == null ? "" : layout;
        }
        LinkedHashMap<String, LayoutEntry> entries = parseLayoutEntries(layout);
        LayoutEntry existing = entries.get(elementId);
        Rectangle rect = existing == null ? layoutRect(null, elementId) : existing.rect();
        BadgeTextStyle style = existing == null ? BadgeTextStyle.defaultFor(elementId) : existing.style().withDefaults(elementId);
        entries.put(elementId, new LayoutEntry(
                rect,
                style,
                existing == null ? 0 : existing.rotation(),
                Objects.requireNonNullElse(text, "").trim(),
                existing == null ? "" : existing.imagePath(),
                existing == null ? 1f : existing.opacity(),
                existing == null ? defaultColorHex(elementId) : existing.colorHex(),
                existing == null ? "" : existing.nameLayout(),
                existing == null ? null : existing.visible(),
                existing == null ? null : existing.zOrder()
        ));
        return serializeLayout(entries);
    }

    public static String signatureImagePath(CompanyCustomizationManager.BadgeTemplateSettings settings) {
        return elementImagePath(settings, "back.signature");
    }

    public static String updateSignatureImagePath(String layout, String imagePath) {
        return updateElementImagePath(layout, "back.signature", imagePath);
    }

    public static boolean isImageElement(String elementId) {
        return switch (Objects.requireNonNullElse(elementId, "")) {
            case "front.background", "front.templateImage", "back.background", "back.templateImage", "back.signature" -> true;
            default -> false;
        };
    }

    public static String elementImagePath(CompanyCustomizationManager.BadgeTemplateSettings settings, String elementId) {
        if (!isImageElement(elementId)) {
            return "";
        }
        LayoutEntry entry = parseLayoutEntries(settings == null ? "" : settings.layoutData()).get(elementId);
        return entry == null ? "" : Objects.requireNonNullElse(entry.imagePath(), "");
    }

    public static String updateElementImagePath(String layout, String elementId, String imagePath) {
        if (!isImageElement(elementId)) {
            return layout == null ? "" : layout;
        }
        LinkedHashMap<String, LayoutEntry> entries = parseLayoutEntries(layout);
        LayoutEntry existing = entries.get(elementId);
        Rectangle rect = existing == null ? layoutRect(null, elementId) : existing.rect();
        BadgeTextStyle style = existing == null ? BadgeTextStyle.defaultFor(elementId) : existing.style().withDefaults(elementId);
        entries.put(elementId, new LayoutEntry(
                rect,
                style,
                existing == null ? 0 : existing.rotation(),
                existing == null ? "" : existing.text(),
                Objects.requireNonNullElse(imagePath, "").trim(),
                existing == null ? 1f : existing.opacity(),
                existing == null ? defaultColorHex(elementId) : existing.colorHex(),
                existing == null ? "" : existing.nameLayout(),
                existing == null ? null : existing.visible(),
                existing == null ? null : existing.zOrder()
        ));
        return serializeLayout(entries);
    }

    public static float elementOpacity(CompanyCustomizationManager.BadgeTemplateSettings settings, String elementId) {
        if (!isImageElement(elementId)) {
            return 1f;
        }
        LayoutEntry entry = parseLayoutEntries(settings == null ? "" : settings.layoutData()).get(elementId);
        return entry == null ? 1f : sanitizeOpacity(entry.opacity());
    }

    public static String updateElementOpacity(String layout, String elementId, float opacity) {
        if (!isImageElement(elementId)) {
            return layout == null ? "" : layout;
        }
        LinkedHashMap<String, LayoutEntry> entries = parseLayoutEntries(layout);
        LayoutEntry existing = entries.get(elementId);
        entries.put(elementId, new LayoutEntry(
                existing == null ? layoutRect(null, elementId) : existing.rect(),
                existing == null ? BadgeTextStyle.defaultFor(elementId) : existing.style().withDefaults(elementId),
                existing == null ? 0 : existing.rotation(),
                existing == null ? "" : existing.text(),
                existing == null ? "" : existing.imagePath(),
                sanitizeOpacity(opacity),
                existing == null ? defaultColorHex(elementId) : existing.colorHex(),
                existing == null ? "" : existing.nameLayout(),
                existing == null ? null : existing.visible(),
                existing == null ? null : existing.zOrder()
        ));
        return serializeLayout(entries);
    }

    public static boolean isColorElement(String elementId) {
        return isTextElement(elementId) || "front.photo".equals(elementId);
    }

    public static Color elementColor(CompanyCustomizationManager.BadgeTemplateSettings settings, String elementId) {
        LayoutEntry entry = parseLayoutEntries(settings == null ? "" : settings.layoutData()).get(elementId);
        return parseColor(entry == null ? defaultColorHex(elementId) : entry.colorHex(), defaultElementColor(elementId));
    }

    public static String updateElementColor(String layout, String elementId, Color color) {
        if (!isColorElement(elementId)) {
            return layout == null ? "" : layout;
        }
        LinkedHashMap<String, LayoutEntry> entries = parseLayoutEntries(layout);
        LayoutEntry existing = entries.get(elementId);
        entries.put(elementId, new LayoutEntry(
                existing == null ? layoutRect(null, elementId) : existing.rect(),
                existing == null ? BadgeTextStyle.defaultFor(elementId) : existing.style().withDefaults(elementId),
                existing == null ? 0 : existing.rotation(),
                existing == null ? "" : existing.text(),
                existing == null ? "" : existing.imagePath(),
                existing == null ? 1f : existing.opacity(),
                colorToHex(color == null ? defaultElementColor(elementId) : color),
                existing == null ? "" : existing.nameLayout(),
                existing == null ? null : existing.visible(),
                existing == null ? null : existing.zOrder()
        ));
        return serializeLayout(entries);
    }

    public static String nameLayout(CompanyCustomizationManager.BadgeTemplateSettings settings) {
        LayoutEntry entry = parseLayoutEntries(settings == null ? "" : settings.layoutData()).get("front.name");
        String layout = entry == null ? "" : Objects.requireNonNullElse(entry.nameLayout(), "").trim().toLowerCase(Locale.ROOT);
        return "two".equals(layout) ? "two" : "one";
    }

    public static String updateNameLayout(String layout, String nameLayout) {
        String elementId = "front.name";
        LinkedHashMap<String, LayoutEntry> entries = parseLayoutEntries(layout);
        LayoutEntry existing = entries.get(elementId);
        String cleanLayout = "two".equalsIgnoreCase(Objects.requireNonNullElse(nameLayout, "")) ? "two" : "";
        entries.put(elementId, new LayoutEntry(
                existing == null ? layoutRect(null, elementId) : existing.rect(),
                existing == null ? BadgeTextStyle.defaultFor(elementId) : existing.style().withDefaults(elementId),
                existing == null ? 0 : existing.rotation(),
                existing == null ? "" : existing.text(),
                existing == null ? "" : existing.imagePath(),
                existing == null ? 1f : existing.opacity(),
                existing == null ? defaultColorHex(elementId) : existing.colorHex(),
                cleanLayout,
                existing == null ? null : existing.visible(),
                existing == null ? null : existing.zOrder()
        ));
        return serializeLayout(entries);
    }

    public static boolean elementVisible(CompanyCustomizationManager.BadgeTemplateSettings settings, String elementId) {
        LayoutEntry entry = parseLayoutEntries(settings == null ? "" : settings.layoutData()).get(elementId);
        if (entry != null && entry.visible() != null) {
            return entry.visible();
        }
        if (settings == null) {
            return true;
        }
        return switch (Objects.requireNonNullElse(elementId, "")) {
            case "front.quote" -> settings.showQuote();
            case "back.employeeNumber" -> settings.showEmployeeId();
            case "back.issueDate" -> settings.showIssueDate();
            case "back.barcode" -> settings.showBarcode();
            case "back.badgeText" -> settings.showBadgeText();
            case "front.templateImage", "back.templateImage" -> false;
            default -> true;
        };
    }

    public static int elementZOrder(CompanyCustomizationManager.BadgeTemplateSettings settings, String elementId) {
        LayoutEntry entry = parseLayoutEntries(settings == null ? "" : settings.layoutData()).get(elementId);
        return entry == null || entry.zOrder() == null ? defaultZOrder(elementId) : entry.zOrder();
    }

    public static String updateElementZOrder(String layout, String elementId, int zOrder) {
        if (!DEFAULT_ELEMENTS.containsKey(elementId)) {
            return layout == null ? "" : layout;
        }
        LinkedHashMap<String, LayoutEntry> entries = parseLayoutEntries(layout);
        LayoutEntry existing = entries.get(elementId);
        entries.put(elementId, new LayoutEntry(
                existing == null ? layoutRect(null, elementId) : existing.rect(),
                existing == null ? BadgeTextStyle.defaultFor(elementId) : existing.style().withDefaults(elementId),
                existing == null ? 0 : existing.rotation(),
                existing == null ? "" : existing.text(),
                existing == null ? "" : existing.imagePath(),
                existing == null ? 1f : existing.opacity(),
                existing == null ? defaultColorHex(elementId) : existing.colorHex(),
                existing == null ? "" : existing.nameLayout(),
                existing == null ? null : existing.visible(),
                zOrder
        ));
        return serializeLayout(entries);
    }

    public static String updateElementVisible(String layout, String elementId, boolean visible) {
        if (!DEFAULT_ELEMENTS.containsKey(elementId)) {
            return layout == null ? "" : layout;
        }
        LinkedHashMap<String, LayoutEntry> entries = parseLayoutEntries(layout);
        LayoutEntry existing = entries.get(elementId);
        entries.put(elementId, new LayoutEntry(
                existing == null ? layoutRect(null, elementId) : existing.rect(),
                existing == null ? BadgeTextStyle.defaultFor(elementId) : existing.style().withDefaults(elementId),
                existing == null ? 0 : existing.rotation(),
                existing == null ? "" : existing.text(),
                existing == null ? "" : existing.imagePath(),
                existing == null ? 1f : existing.opacity(),
                existing == null ? defaultColorHex(elementId) : existing.colorHex(),
                existing == null ? "" : existing.nameLayout(),
                visible,
                existing == null ? null : existing.zOrder()
        ));
        return serializeLayout(entries);
    }

    public static boolean isTextElement(String elementId) {
        return switch (Objects.requireNonNullElse(elementId, "")) {
            case "front.logo", "front.quote", "front.role", "front.name",
                 "back.employeeNumber", "back.issueDate", "back.expiryDate",
                 "front.custom1", "front.custom2", "back.custom1", "back.custom2", "back.watermark", "back.poweredBy", "back.printCount",
                 "back.instructions", "back.signature", "back.badgeText" -> true;
            default -> false;
        };
    }

    public static boolean isCustomTextElement(String elementId) {
        return switch (Objects.requireNonNullElse(elementId, "")) {
            case "front.custom1", "front.custom2", "back.custom1", "back.custom2", "back.watermark", "back.poweredBy" -> true;
            default -> false;
        };
    }

    public static String resetLayout() {
        return "";
    }

    private static String defaultCustomText(String elementId) {
        return switch (Objects.requireNonNullElse(elementId, "")) {
            case "back.watermark" -> "DECKERS";
            case "back.poweredBy" -> "Powered by SmartStock";
            default -> "";
        };
    }

    private static int defaultZOrder(String elementId) {
        return switch (Objects.requireNonNullElse(elementId, "")) {
            case "front.background", "back.background" -> 0;
            case "front.templateImage", "back.templateImage" -> 10;
            case "front.logo", "back.barcode" -> 20;
            case "front.quote", "back.badgeText" -> 30;
            case "front.roleBand", "back.instructions" -> 40;
            case "front.nameBand", "back.employeeNumber" -> 50;
            case "front.photo", "back.issueDate" -> 60;
            case "front.role", "back.expiryDate" -> 70;
            case "front.name", "back.custom1" -> 80;
            case "front.custom1", "back.custom2" -> 90;
            case "front.custom2", "back.watermark" -> 100;
            case "back.poweredBy" -> 110;
            case "back.printCount" -> 120;
            case "back.signature" -> 130;
            default -> 1000;
        };
    }

    public static BufferedImage renderBack(EmployeeBadgeData employee, CompanyCustomizationManager.BadgeTemplateSettings settings) {
        return renderBack(employee, settings, defaultExpiryDate());
    }

    public static BufferedImage renderBack(EmployeeBadgeData employee, CompanyCustomizationManager.BadgeTemplateSettings settings, LocalDate expiryDate) {
        BufferedImage image = createCardImage(1);
        Graphics2D g = image.createGraphics();
        configure(g);
        paintCardShell(g);
        paintBack(g, employee, settings, expiryDate == null ? defaultExpiryDate() : expiryDate);
        g.dispose();
        return image;
    }

    private static BufferedImage renderBack(EmployeeBadgeData employee, CompanyCustomizationManager.BadgeTemplateSettings settings, LocalDate expiryDate, int renderScale) {
        BufferedImage image = createCardImage(renderScale);
        Graphics2D g = image.createGraphics();
        configure(g);
        g.scale(renderScale, renderScale);
        paintCardShell(g);
        paintBack(g, employee, settings, expiryDate == null ? defaultExpiryDate() : expiryDate);
        g.dispose();
        return image;
    }

    public static void printBadge(Component parent, EmployeeBadgeData employee, CompanyCustomizationManager.BadgeTemplateSettings settings) throws Exception {
        printBadge(parent, employee, settings, BadgePrintSide.BOTH, defaultExpiryDate());
    }

    public static void printBadge(Component parent, EmployeeBadgeData employee, CompanyCustomizationManager.BadgeTemplateSettings settings, BadgePrintSide side) throws Exception {
        printBadge(parent, employee, settings, side, defaultExpiryDate());
    }

    public static void printBadge(Component parent, EmployeeBadgeData employee, CompanyCustomizationManager.BadgeTemplateSettings settings, BadgePrintSide side, LocalDate expiryDate) throws Exception {
        BadgePrintSide printSide = side == null ? BadgePrintSide.BOTH : side;
        LocalDate badgeExpiryDate = expiryDate == null ? defaultExpiryDate() : expiryDate;
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Employee Badge " + printSide.label() + " - " + employee.displayName());
        job.setPrintable(new BadgePrintable(employee, settings, printSide, badgeExpiryDate), createBadgePageFormat(job));

        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        attributes.add(OrientationRequested.PORTRAIT);
        attributes.add(new MediaPrintableArea(0, 0, (float) CARD_WIDTH_INCHES, (float) CARD_HEIGHT_INCHES, MediaPrintableArea.INCH));
        if (job.printDialog(attributes)) {
            job.print(attributes);
            incrementBadgePrintCount(employee.userId());
        }
    }

    public static void saveBadgePdf(Path outputPath, EmployeeBadgeData employee, CompanyCustomizationManager.BadgeTemplateSettings settings, BadgePrintSide side, LocalDate expiryDate) throws IOException {
        if (outputPath == null) {
            throw new IllegalArgumentException("Choose where to save the badge PDF.");
        }
        BadgePrintSide printSide = side == null ? BadgePrintSide.BOTH : side;
        LocalDate badgeExpiryDate = expiryDate == null ? defaultExpiryDate() : expiryDate;
        float widthPoints = (float) (CARD_WIDTH_INCHES * 72.0);
        float heightPoints = (float) (CARD_HEIGHT_INCHES * 72.0);
        PDRectangle badgePageSize = new PDRectangle(widthPoints, heightPoints);

        try (PDDocument document = new PDDocument()) {
            if (printSide == BadgePrintSide.FRONT || printSide == BadgePrintSide.BOTH) {
                addBadgePdfPage(document, badgePageSize, renderFront(employee, settings, PRINT_RENDER_SCALE));
            }
            if (printSide == BadgePrintSide.BACK || printSide == BadgePrintSide.BOTH) {
                addBadgePdfPage(document, badgePageSize, renderBack(employee, settings, badgeExpiryDate, PRINT_RENDER_SCALE));
            }
            document.save(outputPath.toFile());
        }
    }

    private static void addBadgePdfPage(PDDocument document, PDRectangle badgePageSize, BufferedImage badgeImage) throws IOException {
        PDPage page = new PDPage(badgePageSize);
        document.addPage(page);
        PDImageXObject image = LosslessFactory.createFromImage(document, badgeImage);
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.drawImage(image, 0, 0, badgePageSize.getWidth(), badgePageSize.getHeight());
        }
    }

    private static PageFormat createBadgePageFormat(PrinterJob job) {
        double widthPoints = CARD_WIDTH_INCHES * 72.0;
        double heightPoints = CARD_HEIGHT_INCHES * 72.0;
        Paper paper = new Paper();
        paper.setSize(widthPoints, heightPoints);
        paper.setImageableArea(0, 0, widthPoints, heightPoints);

        PageFormat pageFormat = job.defaultPage();
        pageFormat.setOrientation(PageFormat.PORTRAIT);
        pageFormat.setPaper(paper);
        return pageFormat;
    }

    public static LocalDate defaultExpiryDate() {
        return LocalDate.now().plusYears(1);
    }

    public static void incrementBadgePrintCount(int userId) throws Exception {
        LanApiClient.incrementEmployeeBadgePrintCount(userId, java.util.UUID.randomUUID().toString());
    }

    public static void incrementBadgePrintCount(Connection conn, int userId, int locationId) throws Exception {
        String sql = """
                UPDATE users
                SET badge_print_count = COALESCE(badge_print_count, 0) + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND EXISTS (
                    SELECT 1 FROM user_locations ul WHERE ul.user_id = users.user_id AND ul.location_id = ?
                )
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, locationId);
            if (ps.executeUpdate() != 1) throw new IllegalArgumentException("Employee was not found in this store.");
        }
    }

    public static void previewBadge(Component parent, EmployeeBadgeData employee, CompanyCustomizationManager.BadgeTemplateSettings settings) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Badge Preview", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(12, 12));
        dialog.getRootPane().setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JTabbedPane tabs = new JTabbedPane();
        BufferedImage frontImage = renderFront(employee, settings);
        BufferedImage backImage = renderBack(employee, settings);
        JLabel frontLabel = new JLabel();
        JLabel backLabel = new JLabel();
        updateActualSizePreviewIcons(parent, frontLabel, backLabel, frontImage, backImage);
        tabs.addTab("Front", new JScrollPane(frontLabel));
        tabs.addTab("Back", new JScrollPane(backLabel));
        dialog.add(tabs, BorderLayout.CENTER);
        JLabel sizeLabel = new JLabel("Actual size target: 2.125 x 3.375 in");
        sizeLabel.setForeground(new Color(75, 85, 99));
        JButton calibrateButton = new JButton("Calibrate Size");
        JButton closeButton = new JButton("Close");
        calibrateButton.addActionListener(e -> {
            Dimension currentSize = actualSizePreviewDimension(parent);
            double currentWidthInches = currentSize.width / Math.max(1.0, effectiveScreenDpi(parent));
            Object input = JOptionPane.showInputDialog(
                    dialog,
                    "Hold a real badge/card against the preview.\nEnter the preview card width you see on screen, in inches:",
                    "Calibrate Badge Preview",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    null,
                    String.format(Locale.ROOT, "%.3f", currentWidthInches)
            );
            if (input == null) {
                return;
            }
            try {
                double measuredWidth = Double.parseDouble(input.toString().trim());
                if (measuredWidth > 0.2) {
                    actualSizePreviewCalibration *= CARD_WIDTH_INCHES / measuredWidth;
                    updateActualSizePreviewIcons(parent, frontLabel, backLabel, frontImage, backImage);
                    dialog.pack();
                    dialog.setLocationRelativeTo(parent);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "Enter the measured width as a number of inches.", "Calibrate Badge Preview", JOptionPane.ERROR_MESSAGE);
            }
        });
        closeButton.addActionListener(e -> dialog.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(sizeLabel);
        buttons.add(calibrateButton);
        buttons.add(closeButton);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    public static String writeMagStripe(EmployeeBadgeData employee, CompanyCustomizationManager.BadgeTemplateSettings settings) throws Exception {
        if (!settings.magStripeEnabled()) {
            throw new IllegalStateException("Magnetic stripe writing is disabled in Company Preferences.");
        }
        String commandTemplate = Objects.requireNonNullElse(settings.magStripeCommand(), "").trim();
        if (commandTemplate.isBlank()) {
            throw new IllegalStateException("Set a magnetic stripe writer command in Company Preferences first.");
        }

        String track1 = buildTrackData(settings.magStripeTrack1(), employee, settings);
        String track2 = buildTrackData(settings.magStripeTrack2(), employee, settings);
        String track3 = buildTrackData(settings.magStripeTrack3(), employee, settings);
        String command = commandTemplate
                .replace("{badge_id}", employee.badgeId())
                .replace("{employee_id}", String.valueOf(employee.userId()))
                .replace("{full_name}", employee.displayName())
                .replace("{track1}", track1)
                .replace("{track2}", track2)
                .replace("{track3}", track3);

        List<String> args = splitWriterCommand(command);
        if (args.isEmpty()) {
            throw new IllegalStateException("Magnetic stripe writer command is empty.");
        }
        Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Magnetic stripe writer exited with code " + exitCode + ".\n" + output);
        }
        return output.isBlank() ? "Magnetic stripe write command completed." : output.trim();
    }

    public static String buildTrackData(String template, EmployeeBadgeData employee, CompanyCustomizationManager.BadgeTemplateSettings settings) {
        String fallback = "{badge_id}";
        String value = Objects.requireNonNullElse(template, "").isBlank() ? fallback : template;
        return value
                .replace("{badge_id}", employee.badgeId())
                .replace("{employee_id}", String.valueOf(employee.userId()))
                .replace("{full_name}", employee.displayName())
                .replace("{first_name}", Objects.requireNonNullElse(employee.firstName(), ""))
                .replace("{last_name}", Objects.requireNonNullElse(employee.lastName(), ""))
                .replace("{role}", employee.roleName())
                .replace("{company}", settings.companyName())
                .replace("{issued_date}", LocalDate.now().toString());
    }

    private static void updateActualSizePreviewIcons(Component parent, JLabel frontLabel, JLabel backLabel, BufferedImage frontImage, BufferedImage backImage) {
        Dimension size = actualSizePreviewDimension(parent);
        frontLabel.setHorizontalAlignment(SwingConstants.CENTER);
        backLabel.setHorizontalAlignment(SwingConstants.CENTER);
        frontLabel.setIcon(new ImageIcon(scale(frontImage, size.width, size.height)));
        backLabel.setIcon(new ImageIcon(scale(backImage, size.width, size.height)));
        frontLabel.setPreferredSize(new Dimension(size.width + 18, size.height + 18));
        backLabel.setPreferredSize(new Dimension(size.width + 18, size.height + 18));
    }

    private static Dimension actualSizePreviewDimension(Component parent) {
        double dpi = effectiveScreenDpi(parent);
        int width = Math.max(80, (int) Math.round(CARD_WIDTH_INCHES * dpi * actualSizePreviewCalibration));
        int height = Math.max(128, (int) Math.round(CARD_HEIGHT_INCHES * dpi * actualSizePreviewCalibration));
        return new Dimension(width, height);
    }

    private static double effectiveScreenDpi(Component parent) {
        int toolkitDpi = Toolkit.getDefaultToolkit().getScreenResolution();
        double scaleX = 1.0;
        GraphicsConfiguration configuration = parent == null ? null : parent.getGraphicsConfiguration();
        if (configuration != null) {
            scaleX = Math.max(1.0, configuration.getDefaultTransform().getScaleX());
        }
        double dpi = toolkitDpi;
        if (toolkitDpi <= 96 && scaleX > 1.0) {
            dpi *= scaleX;
        }
        return Math.max(72.0, dpi);
    }

    private static void paintFront(Graphics2D g, EmployeeBadgeData employee, CompanyCustomizationManager.BadgeTemplateSettings settings) {
        for (BadgeElement element : elementsForSideInLayerOrder(settings, "front")) {
            paintFrontElement(g, employee, settings, element.id());
        }
    }

    private static void paintBack(Graphics2D g, EmployeeBadgeData employee, CompanyCustomizationManager.BadgeTemplateSettings settings, LocalDate expiryDate) {
        for (BadgeElement element : elementsForSideInLayerOrder(settings, "back")) {
            paintBackElement(g, employee, settings, expiryDate, element.id());
        }
    }

    private static void paintFrontElement(Graphics2D g, EmployeeBadgeData employee, CompanyCustomizationManager.BadgeTemplateSettings settings, String elementId) {
        if (!elementVisible(settings, elementId)) {
            return;
        }
        switch (elementId) {
            case "front.background" -> paintBackground(g, settings, elementId);
            case "front.templateImage" -> paintTemplateImage(g, settings, elementId);
            case "front.logo" -> paintLogo(g, settings);
            case "front.quote" -> {
                Rectangle rect = layoutRect(settings, elementId);
                g.setColor(elementColor(settings, elementId));
                drawCenteredFit(g, quoteLine(settings.quoteLine()), rect, textStyle(settings, elementId), elementRotation(settings, elementId));
            }
            case "front.roleBand" -> {
                g.setColor(DECKERS_GREEN);
                fillRotatedRect(g, layoutRect(settings, elementId), elementRotation(settings, elementId));
            }
            case "front.nameBand" -> {
                g.setColor(DECKERS_ORANGE);
                fillRotatedRect(g, layoutRect(settings, elementId), elementRotation(settings, elementId));
            }
            case "front.photo" -> {
                BufferedImage photo = loadImage(employee.photoPath());
                Rectangle rect = layoutRect(settings, elementId);
                int rotation = elementRotation(settings, elementId);
                Rectangle drawRect = rotatedContentRect(rect, rotation);
                Color borderColor = elementColor(settings, elementId);
                withRotation(g, rect, rotation, copy -> paintPhoto(copy, photo, drawRect, borderColor));
            }
            case "front.role" -> {
                g.setColor(elementColor(settings, elementId));
                drawCenteredFit(g, employee.roleName().toUpperCase(Locale.ROOT), layoutRect(settings, elementId), textStyle(settings, elementId), elementRotation(settings, elementId));
            }
            case "front.name" -> {
                g.setColor(elementColor(settings, elementId));
                Rectangle rect = layoutRect(settings, elementId);
                if ("two".equals(nameLayout(settings))) {
                    drawCenteredLinesFit(g, employee.displayNameLines(), rect, textStyle(settings, elementId), elementRotation(settings, elementId));
                } else {
                    drawCenteredFit(g, employee.displayName(), rect, textStyle(settings, elementId), elementRotation(settings, elementId));
                }
            }
            case "front.custom1", "front.custom2" -> paintCustomText(g, settings, elementId);
            default -> {
            }
        }
    }

    private static void paintBackElement(Graphics2D g, EmployeeBadgeData employee, CompanyCustomizationManager.BadgeTemplateSettings settings, LocalDate expiryDate, String elementId) {
        if (!elementVisible(settings, elementId)) {
            return;
        }
        switch (elementId) {
            case "back.background" -> paintBackground(g, settings, elementId);
            case "back.templateImage" -> paintTemplateImage(g, settings, elementId);
            case "back.barcode" -> paintBarcode(g, employee, settings);
            case "back.badgeText" -> {
                g.setColor(elementColor(settings, elementId));
                drawCenteredFit(g, employee.badgeId(), layoutRect(settings, elementId), textStyle(settings, elementId), elementRotation(settings, elementId));
            }
            case "back.instructions" -> {
                g.setColor(elementColor(settings, elementId));
                drawCenteredFit(g, settings.backInstructions(), layoutRect(settings, elementId), textStyle(settings, elementId), elementRotation(settings, elementId));
            }
            case "back.employeeNumber" -> paintEmployeeNumber(g, employee, settings);
            case "back.issueDate" -> paintIssueDate(g, settings);
            case "back.expiryDate" -> paintExpiryDate(g, settings, expiryDate);
            case "back.custom1", "back.custom2", "back.watermark", "back.poweredBy" -> paintCustomText(g, settings, elementId);
            case "back.printCount" -> paintPrintCount(g, employee, settings);
            case "back.signature" -> paintSignature(g, settings);
            default -> {
            }
        }
    }

    private static void paintLogo(Graphics2D g, CompanyCustomizationManager.BadgeTemplateSettings settings) {
        String elementId = "front.logo";
        Rectangle rect = layoutRect(settings, elementId);
        BufferedImage logo = loadImage(settings.logoPath());
        if (logo != null) {
            int rotation = elementRotation(settings, elementId);
            Rectangle drawRect = rotatedContentRect(rect, rotation);
            withRotation(g, rect, rotation, copy ->
                    drawImageFit(copy, logo, drawRect.x, drawRect.y, drawRect.width, drawRect.height)
            );
            return;
        }
        g.setColor(elementColor(settings, elementId));
        drawCenteredFit(g, settings.companyName(), rect, textStyle(settings, elementId), elementRotation(settings, elementId));
    }

    private static void paintBarcode(Graphics2D g, EmployeeBadgeData employee, CompanyCustomizationManager.BadgeTemplateSettings settings) {
        String elementId = "back.barcode";
        Rectangle rect = layoutRect(settings, elementId);
        int rotation = normalizeRotation(90 + elementRotation(settings, elementId));
        Rectangle drawRect = rotatedContentRect(rect, rotation);
        BufferedImage barcode = renderCode128(employee.badgeId(), Math.max(1120, drawRect.width), Math.max(270, drawRect.height));
        withRotation(g, rect, rotation, copy ->
                drawBarcodeFit(copy, barcode, drawRect.x, drawRect.y, drawRect.width, drawRect.height)
        );
    }

    private static void paintSignature(Graphics2D g, CompanyCustomizationManager.BadgeTemplateSettings settings) {
        String elementId = "back.signature";
        Rectangle rect = layoutRect(settings, elementId);
        BufferedImage signatureImage = loadImage(signatureImagePath(settings));
        if (signatureImage != null) {
            int rotation = elementRotation(settings, elementId);
            Rectangle drawRect = rotatedContentRect(rect, rotation);
            withRotation(g, rect, rotation, copy ->
                    drawImageFit(copy, signatureImage, drawRect.x, drawRect.y, drawRect.width, drawRect.height)
            );
            return;
        }
        g.setColor(elementColor(settings, elementId));
        int rotation = elementRotation(settings, elementId);
        Rectangle drawRect = rotatedContentRect(rect, rotation);
        int lineY = drawRect.y + Math.max(48, drawRect.height - 30);
        withRotation(g, rect, rotation, copy -> {
            copy.setColor(elementColor(settings, elementId));
            copy.setStroke(new BasicStroke(2f));
            copy.drawLine(drawRect.x + 7, lineY, drawRect.x + drawRect.width - 20, lineY);
            drawLeftFit(copy, settings.signatoryName(), new Rectangle(drawRect.x, drawRect.y, drawRect.width, Math.max(18, lineY - drawRect.y - 2)), textStyle(settings, elementId));
            drawLeftFit(copy, settings.signatoryTitle(), new Rectangle(drawRect.x + 25, lineY + 6, drawRect.width - 25, Math.max(14, drawRect.y + drawRect.height - lineY - 8)), new BadgeTextStyle("SansSerif", Font.PLAIN, 14, "left"));
        });
    }

    private static void paintCustomText(Graphics2D g, CompanyCustomizationManager.BadgeTemplateSettings settings, String elementId) {
        if (!elementVisible(settings, elementId)) {
            return;
        }
        String text = customText(settings, elementId);
        if (text.isBlank()) {
            return;
        }
        Color color = elementColor(settings, elementId);
        g.setColor("back.watermark".equals(elementId) ? withAlpha(color, 34) : color);
        Rectangle rect = layoutRect(settings, elementId);
        drawCenteredFit(g, text, rect, textStyle(settings, elementId), elementRotation(settings, elementId));
    }

    private static void paintBackground(Graphics2D g, CompanyCustomizationManager.BadgeTemplateSettings settings, String elementId) {
        BufferedImage background = loadImage(elementImagePath(settings, elementId));
        if (background == null) {
            return;
        }
        Rectangle rect = layoutRect(settings, elementId);
        int rotation = elementRotation(settings, elementId);
        Rectangle drawRect = rotatedContentRect(rect, rotation);
        Composite previousComposite = g.getComposite();
        try {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, elementOpacity(settings, elementId)));
            withRotation(g, rect, rotation, copy ->
                    drawImageCover(copy, background, drawRect.x, drawRect.y, drawRect.width, drawRect.height)
            );
        } finally {
            g.setComposite(previousComposite);
        }
    }

    private static void paintTemplateImage(Graphics2D g, CompanyCustomizationManager.BadgeTemplateSettings settings, String elementId) {
        if (!elementVisible(settings, elementId)) {
            return;
        }
        BufferedImage image = loadImage(elementImagePath(settings, elementId));
        if (image == null) {
            return;
        }
        Rectangle rect = layoutRect(settings, elementId);
        int rotation = elementRotation(settings, elementId);
        Rectangle drawRect = rotatedContentRect(rect, rotation);
        Composite previousComposite = g.getComposite();
        try {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, elementOpacity(settings, elementId)));
            withRotation(g, rect, rotation, copy ->
                    drawImageFit(copy, image, drawRect.x, drawRect.y, drawRect.width, drawRect.height)
            );
        } finally {
            g.setComposite(previousComposite);
        }
    }

    private static void paintEmployeeNumber(Graphics2D g, EmployeeBadgeData employee, CompanyCustomizationManager.BadgeTemplateSettings settings) {
        if (!elementVisible(settings, "back.employeeNumber")) {
            return;
        }
        g.setColor(elementColor(settings, "back.employeeNumber"));
        Rectangle rect = layoutRect(settings, "back.employeeNumber");
        drawCenteredFit(g, "Employee #" + employee.userId(), rect, textStyle(settings, "back.employeeNumber"), elementRotation(settings, "back.employeeNumber"));
    }

    private static void paintIssueDate(Graphics2D g, CompanyCustomizationManager.BadgeTemplateSettings settings) {
        if (!elementVisible(settings, "back.issueDate")) {
            return;
        }
        g.setColor(elementColor(settings, "back.issueDate"));
        Rectangle rect = layoutRect(settings, "back.issueDate");
        drawCenteredFit(g, "Issued " + DATE_FORMATTER.format(LocalDate.now()), rect, textStyle(settings, "back.issueDate"), elementRotation(settings, "back.issueDate"));
    }

    private static void paintExpiryDate(Graphics2D g, CompanyCustomizationManager.BadgeTemplateSettings settings, LocalDate expiryDate) {
        if (!elementVisible(settings, "back.expiryDate")) {
            return;
        }
        g.setColor(elementColor(settings, "back.expiryDate"));
        Rectangle rect = layoutRect(settings, "back.expiryDate");
        LocalDate cleanExpiryDate = expiryDate == null ? defaultExpiryDate() : expiryDate;
        drawCenteredFit(g, "Expires " + DATE_FORMATTER.format(cleanExpiryDate), rect, textStyle(settings, "back.expiryDate"), elementRotation(settings, "back.expiryDate"));
    }

    private static void paintPrintCount(Graphics2D g, EmployeeBadgeData employee, CompanyCustomizationManager.BadgeTemplateSettings settings) {
        if (!elementVisible(settings, "back.printCount")) {
            return;
        }
        g.setColor(elementColor(settings, "back.printCount"));
        Rectangle rect = layoutRect(settings, "back.printCount");
        drawCenteredFit(g, "Print " + Math.max(1, employee.nextPrintCount()), rect, textStyle(settings, "back.printCount"), elementRotation(settings, "back.printCount"));
    }

    private static BufferedImage createCardImage(int renderScale) {
        int scale = Math.max(1, renderScale);
        BufferedImage image = new BufferedImage(CARD_WIDTH * scale, CARD_HEIGHT * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        configure(g);
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();
        return image;
    }

    private static void paintCardShell(Graphics2D g) {
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(0, 0, CARD_WIDTH, CARD_HEIGHT, 40, 40));
        g.setColor(new Color(241, 245, 249));
        for (int x = 20; x < CARD_WIDTH; x += 12) {
            g.drawLine(x, 0, x, CARD_HEIGHT);
        }
        for (int y = 20; y < CARD_HEIGHT; y += 12) {
            g.drawLine(0, y, CARD_WIDTH, y);
        }
        g.setColor(new Color(229, 231, 235));
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Double(1, 1, CARD_WIDTH - 2, CARD_HEIGHT - 2, 40, 40));
    }

    private static void configure(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static BufferedImage loadImage(String pathOrUrl) {
        String value = Objects.requireNonNullElse(pathOrUrl, "").trim();
        if (value.isBlank()) {
            return null;
        }
        return ImageCacheManager.loadImage(value);
    }

    private static void drawImageFit(Graphics2D g, BufferedImage image, int x, int y, int width, int height) {
        double scale = Math.min(width / (double) image.getWidth(), height / (double) image.getHeight());
        int drawW = (int) Math.round(image.getWidth() * scale);
        int drawH = (int) Math.round(image.getHeight() * scale);
        int drawX = x + (width - drawW) / 2;
        int drawY = y + (height - drawH) / 2;
        drawHighQualityImage(g, image, drawX, drawY, drawW, drawH);
    }

    private static void drawBarcodeFit(Graphics2D g, BufferedImage image, int x, int y, int width, int height) {
        double scale = Math.min(width / (double) image.getWidth(), height / (double) image.getHeight());
        int drawW = (int) Math.round(image.getWidth() * scale);
        int drawH = (int) Math.round(image.getHeight() * scale);
        int drawX = x + (width - drawW) / 2;
        int drawY = y + (height - drawH) / 2;
        Object previousInterpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        Object previousAntialias = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.drawImage(image, drawX, drawY, drawW, drawH, null);
        } finally {
            if (previousInterpolation != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, previousInterpolation);
            }
            if (previousAntialias != null) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, previousAntialias);
            }
        }
    }

    private static void drawImageCover(Graphics2D g, BufferedImage image, int x, int y, int width, int height) {
        Shape oldClip = g.getClip();
        g.setClip(x, y, width, height);
        double scale = Math.max(width / (double) image.getWidth(), height / (double) image.getHeight());
        int drawW = (int) Math.round(image.getWidth() * scale);
        int drawH = (int) Math.round(image.getHeight() * scale);
        int drawX = x + (width - drawW) / 2;
        int drawY = y + (height - drawH) / 2;
        drawHighQualityImage(g, image, drawX, drawY, drawW, drawH);
        g.setClip(oldClip);
    }

    private static void drawHighQualityImage(Graphics2D g, BufferedImage image, int x, int y, int width, int height) {
        if (image == null || width <= 0 || height <= 0) {
            return;
        }
        if (image.getWidth() <= width * 2 && image.getHeight() <= height * 2) {
            g.drawImage(image, x, y, width, height, null);
            return;
        }
        BufferedImage scaled = progressiveScale(image, width, height);
        g.drawImage(scaled, x, y, null);
    }

    private static BufferedImage progressiveScale(BufferedImage source, int targetWidth, int targetHeight) {
        int currentWidth = source.getWidth();
        int currentHeight = source.getHeight();
        BufferedImage current = source;
        while (currentWidth / 2 >= targetWidth && currentHeight / 2 >= targetHeight) {
            currentWidth = Math.max(targetWidth, currentWidth / 2);
            currentHeight = Math.max(targetHeight, currentHeight / 2);
            current = resizeImage(current, currentWidth, currentHeight);
        }
        return currentWidth == targetWidth && currentHeight == targetHeight
                ? current
                : resizeImage(current, targetWidth, targetHeight);
    }

    private static BufferedImage resizeImage(BufferedImage source, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        configure(g);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    private static void paintPhoto(Graphics2D g, BufferedImage photo, Rectangle photoRect, Color borderColor) {
        int photoX = photoRect.x;
        int photoY = photoRect.y;
        int photoW = photoRect.width;
        int photoH = photoRect.height;
        g.setColor(borderColor == null ? BORDER_BLUE : borderColor);
        g.fillRect(photoX - 5, photoY - 5, photoW + 10, photoH + 10);
        g.setColor(Color.WHITE);
        g.fillRect(photoX, photoY, photoW, photoH);
        if (photo != null) {
            drawImageCover(g, photo, photoX, photoY, photoW, photoH);
        } else {
            g.setColor(new Color(236, 240, 244));
            g.fillRect(photoX, photoY, photoW, photoH);
            g.setColor(new Color(100, 116, 139));
            g.setFont(new Font("SansSerif", Font.BOLD, 17));
            drawCentered(g, "PHOTO", photoY + Math.max(17, photoH / 2 + 8), photoW, photoX);
        }
    }

    private static void fillRotatedRect(Graphics2D g, Rectangle rect, int rotation) {
        Rectangle drawRect = rotatedContentRect(rect, rotation);
        withRotation(g, rect, rotation, copy -> copy.fillRect(drawRect.x, drawRect.y, drawRect.width, drawRect.height));
    }

    private static void withRotation(Graphics2D g, Rectangle rect, int rotation, GraphicsPainter painter) {
        int cleanRotation = normalizeRotation(rotation);
        if (cleanRotation == 0) {
            painter.paint(g);
            return;
        }
        Graphics2D copy = (Graphics2D) g.create();
        try {
            copy.rotate(Math.toRadians(cleanRotation), rect.getCenterX(), rect.getCenterY());
            painter.paint(copy);
        } finally {
            copy.dispose();
        }
    }

    private static Rectangle rotatedContentRect(Rectangle rect, int rotation) {
        int cleanRotation = normalizeRotation(rotation);
        if (cleanRotation != 90 && cleanRotation != 270) {
            return new Rectangle(rect);
        }
        int x = (int) Math.round(rect.getCenterX() - rect.height / 2.0);
        int y = (int) Math.round(rect.getCenterY() - rect.width / 2.0);
        return new Rectangle(x, y, rect.height, rect.width);
    }

    private static BufferedImage scale(BufferedImage source, int width, int height) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        configure(g);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return scaled;
    }

    private static void drawCentered(Graphics2D g, String text, int baselineY, int width) {
        drawCentered(g, text, baselineY, width, 0);
    }

    private static void drawCentered(Graphics2D g, String text, int baselineY, int width, int x) {
        String safeText = Objects.requireNonNullElse(text, "");
        FontRenderContext context = g.getFontRenderContext();
        int textWidth = (int) Math.round(g.getFont().getStringBounds(safeText, context).getWidth());
        g.drawString(safeText, x + Math.max(0, (width - textWidth) / 2), baselineY);
    }

    private static void drawAligned(Graphics2D g, String text, int baselineY, Rectangle rect, BadgeTextStyle style) {
        String safeText = Objects.requireNonNullElse(text, "");
        FontRenderContext context = g.getFontRenderContext();
        int textWidth = (int) Math.round(g.getFont().getStringBounds(safeText, context).getWidth());
        BadgeTextStyle cleanStyle = style == null ? BadgeTextStyle.defaultFor("") : style;
        int x = switch (normalizeAlignment(cleanStyle.alignment())) {
            case "left" -> rect.x;
            case "right" -> rect.x + Math.max(0, rect.width - textWidth);
            default -> rect.x + Math.max(0, (rect.width - textWidth) / 2);
        };
        if (!cleanStyle.textOutline()) {
            g.drawString(safeText, x, baselineY);
            return;
        }
        Color fill = g.getColor();
        Color outline = parseColor(cleanStyle.textOutlineColorHex(), Color.BLACK);
        GlyphVector glyphs = g.getFont().createGlyphVector(context, safeText);
        Shape shape = glyphs.getOutline(x, baselineY);
        Stroke previousStroke = g.getStroke();
        g.setColor(outline);
        g.setStroke(new BasicStroke(Math.max(1.4f, g.getFont().getSize2D() / 16f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shape);
        g.setStroke(previousStroke);
        g.setColor(fill);
        g.fill(shape);
    }

    private static void drawTextBoxOutline(Graphics2D g, Rectangle rect, BadgeTextStyle style) {
        BadgeTextStyle cleanStyle = style == null ? BadgeTextStyle.defaultFor("") : style;
        if (!cleanStyle.boxOutline()) {
            return;
        }
        Color previousColor = g.getColor();
        Stroke previousStroke = g.getStroke();
        g.setColor(parseColor(cleanStyle.boxOutlineColorHex(), previousColor));
        g.setStroke(new BasicStroke(2f));
        g.drawRect(rect.x, rect.y, Math.max(1, rect.width - 1), Math.max(1, rect.height - 1));
        g.setStroke(previousStroke);
        g.setColor(previousColor);
    }

    private static String styledText(String text, BadgeTextStyle style) {
        String safeText = Objects.requireNonNullElse(text, "");
        return style != null && style.allCaps() ? safeText.toUpperCase(Locale.ROOT) : safeText;
    }

    private static void drawCenteredFit(Graphics2D g, String text, int baselineY, int width, int x, int maxSize, int minSize, int style) {
        String safeText = Objects.requireNonNullElse(text, "");
        String family = g.getFont().getFamily();
        FontRenderContext context = g.getFontRenderContext();
        Font chosen = new Font(family, style, maxSize);
        for (int size = maxSize; size >= minSize; size--) {
            Font candidate = new Font(family, style, size);
            if (candidate.getStringBounds(safeText, context).getWidth() <= width) {
                chosen = candidate;
                break;
            }
        }
        Font previous = g.getFont();
        g.setFont(chosen);
        drawCentered(g, safeText, baselineY, width, x);
        g.setFont(previous);
    }

    private static void drawCenteredFit(Graphics2D g, String text, Rectangle rect, BadgeTextStyle style) {
        drawCenteredFit(g, text, rect, style, 0);
    }

    private static void drawCenteredFit(Graphics2D g, String text, Rectangle rect, BadgeTextStyle style, int rotation) {
        Rectangle drawRect = rotatedContentRect(rect, rotation);
        withRotation(g, rect, rotation, copy -> drawCenteredFitUnrotated(copy, text, drawRect, style));
    }

    private static void drawCenteredFitUnrotated(Graphics2D g, String text, Rectangle rect, BadgeTextStyle style) {
        String safeText = styledText(text, style);
        Font chosen = fitFont(g, List.of(safeText), rect.width, rect.height, style);
        FontMetrics metrics = g.getFontMetrics(chosen);
        int baselineY = rect.y + Math.max(metrics.getAscent(), (rect.height - metrics.getHeight()) / 2 + metrics.getAscent());
        Font previous = g.getFont();
        g.setFont(chosen);
        drawTextBoxOutline(g, rect, style);
        drawAligned(g, safeText, baselineY, rect, style);
        g.setFont(previous);
    }

    private static void drawCenteredLinesFit(Graphics2D g, List<String> lines, Rectangle rect, BadgeTextStyle style) {
        drawCenteredLinesFit(g, lines, rect, style, 0);
    }

    private static void drawCenteredLinesFit(Graphics2D g, List<String> lines, Rectangle rect, BadgeTextStyle style, int rotation) {
        Rectangle drawRect = rotatedContentRect(rect, rotation);
        withRotation(g, rect, rotation, copy -> drawCenteredLinesFitUnrotated(copy, lines, drawRect, style));
    }

    private static void drawCenteredLinesFitUnrotated(Graphics2D g, List<String> lines, Rectangle rect, BadgeTextStyle style) {
        List<String> safeLines = lines.stream()
                .map(line -> styledText(line, style).trim())
                .filter(line -> !line.isBlank())
                .toList();
        if (safeLines.isEmpty()) {
            return;
        }
        Font chosen = fitFont(g, safeLines, rect.width, rect.height, style);
        FontMetrics metrics = g.getFontMetrics(chosen);
        int lineHeight = Math.max(1, metrics.getHeight());
        int totalHeight = lineHeight * safeLines.size();
        int y = rect.y + Math.max(metrics.getAscent(), (rect.height - totalHeight) / 2 + metrics.getAscent());
        Font previous = g.getFont();
        g.setFont(chosen);
        drawTextBoxOutline(g, rect, style);
        for (String line : safeLines) {
            drawAligned(g, line, y, rect, style);
            y += lineHeight;
        }
        g.setFont(previous);
    }

    private static void drawLeftFit(Graphics2D g, String text, Rectangle rect, BadgeTextStyle style) {
        String safeText = styledText(text, style);
        Font chosen = fitFont(g, List.of(safeText), rect.width, rect.height, style);
        FontMetrics metrics = g.getFontMetrics(chosen);
        int baselineY = rect.y + Math.max(metrics.getAscent(), (rect.height - metrics.getHeight()) / 2 + metrics.getAscent());
        Font previous = g.getFont();
        g.setFont(chosen);
        drawTextBoxOutline(g, rect, style);
        drawAligned(g, safeText, baselineY, rect, style);
        g.setFont(previous);
    }

    private static Font fitFont(Graphics2D g, List<String> lines, int width, int height, BadgeTextStyle style) {
        BadgeTextStyle cleanStyle = style == null ? BadgeTextStyle.defaultFor("") : style;
        int maxSize = Math.max(cleanStyle.minSize(), cleanStyle.maxSize());
        FontRenderContext context = g.getFontRenderContext();
        for (int size = maxSize; size >= cleanStyle.minSize(); size--) {
            Font candidate = createBadgeFont(cleanStyle, size);
            FontMetrics metrics = g.getFontMetrics(candidate);
            int totalHeight = metrics.getHeight() * Math.max(1, lines.size());
            boolean fits = totalHeight <= Math.max(1, height);
            for (String line : lines) {
                if (candidate.getStringBounds(Objects.requireNonNullElse(line, ""), context).getWidth() > Math.max(1, width)) {
                    fits = false;
                    break;
                }
            }
            if (fits) {
                return candidate;
            }
        }
        return createBadgeFont(cleanStyle, cleanStyle.minSize());
    }

    private static Font createBadgeFont(BadgeTextStyle style, int size) {
        BadgeTextStyle cleanStyle = style == null ? BadgeTextStyle.defaultFor("") : style;
        String fontName = resolveWeightedFontName(cleanStyle);
        int styleBits = cleanStyle.style() & Font.ITALIC;
        Font base = new Font(fontName, styleBits, size);
        Map<TextAttribute, Object> attributes = new LinkedHashMap<>();
        attributes.put(TextAttribute.FAMILY, fontName);
        attributes.put(TextAttribute.SIZE, (float) size);
        attributes.put(TextAttribute.WEIGHT, fontWeightValue(effectiveFontWeight(cleanStyle)));
        if ((cleanStyle.style() & Font.ITALIC) != 0) {
            attributes.put(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE);
        }
        return base.deriveFont(attributes);
    }

    private static String resolveWeightedFontName(BadgeTextStyle style) {
        String family = Objects.requireNonNullElse(style.family(), "SansSerif").trim();
        String weight = effectiveFontWeight(style);
        if ("regular".equals(weight)) {
            return family;
        }
        String suffix = switch (weight) {
            case "medium" -> "Medium";
            case "semibold" -> "SemiBold";
            case "bold" -> "Bold";
            case "extrabold" -> "ExtraBold";
            case "black" -> "Black";
            default -> "";
        };
        if (suffix.isBlank()) {
            return family;
        }
        String compactFamily = family.replace(" ", "");
        String[] candidates = {
                family + "-" + suffix,
                compactFamily + "-" + suffix,
                family + " " + suffix,
                compactFamily + " " + suffix,
                family + " " + suffix.replace("SemiBold", "Semi Bold").replace("ExtraBold", "Extra Bold"),
                family + "-" + suffix.replace("SemiBold", "Semi-Bold").replace("ExtraBold", "Extra-Bold")
        };
        for (String candidate : candidates) {
            if (fontAvailable(candidate)) {
                return candidate;
            }
        }
        return family;
    }

    private static boolean fontAvailable(String fontName) {
        Font candidate = new Font(fontName, Font.PLAIN, 12);
        String requested = fontName.replace(" ", "").replace("-", "").toLowerCase(Locale.ROOT);
        String resolvedName = candidate.getFontName().replace(" ", "").replace("-", "").toLowerCase(Locale.ROOT);
        String resolvedFamily = candidate.getFamily().replace(" ", "").replace("-", "").toLowerCase(Locale.ROOT);
        return resolvedName.contains(requested) || resolvedFamily.contains(requested);
    }

    private static String effectiveFontWeight(BadgeTextStyle style) {
        String normalized = normalizeFontWeight(style.weight());
        if ("regular".equals(normalized) && (style.style() & Font.BOLD) != 0) {
            return "bold";
        }
        return normalized;
    }

    private static Float fontWeightValue(String weight) {
        return switch (normalizeFontWeight(weight)) {
            case "medium" -> TextAttribute.WEIGHT_MEDIUM;
            case "semibold" -> TextAttribute.WEIGHT_SEMIBOLD;
            case "bold" -> TextAttribute.WEIGHT_BOLD;
            case "extrabold" -> TextAttribute.WEIGHT_EXTRABOLD;
            case "black" -> TextAttribute.WEIGHT_ULTRABOLD;
            default -> TextAttribute.WEIGHT_REGULAR;
        };
    }

    private static void drawLeftFit(Graphics2D g, String text, int x, int baselineY, int width, int maxSize, int minSize, int style) {
        String safeText = Objects.requireNonNullElse(text, "");
        String family = g.getFont().getFamily();
        FontRenderContext context = g.getFontRenderContext();
        Font chosen = new Font(family, style, maxSize);
        for (int size = maxSize; size >= minSize; size--) {
            Font candidate = new Font(family, style, size);
            if (candidate.getStringBounds(safeText, context).getWidth() <= width) {
                chosen = candidate;
                break;
            }
        }
        Font previous = g.getFont();
        g.setFont(chosen);
        g.drawString(safeText, x, baselineY);
        g.setFont(previous);
    }

    private static LinkedHashMap<String, LayoutEntry> parseLayoutEntries(String layout) {
        layout = CompanyCustomizationManager.activeBadgeTemplateLayout(layout);
        LinkedHashMap<String, LayoutEntry> entries = new LinkedHashMap<>();
        for (String entry : Objects.requireNonNullElse(layout, "").split(";")) {
            String trimmed = entry.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            int equals = trimmed.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String elementId = trimmed.substring(0, equals);
            if (!DEFAULT_ELEMENTS.containsKey(elementId)) {
                continue;
            }
            String[] parts = trimmed.substring(equals + 1).split(",");
            if (parts.length < 4) {
                continue;
            }
            try {
                Rectangle rect = sanitizeRect(new Rectangle(
                        Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim()),
                        Integer.parseInt(parts[3].trim())
                ));
                BadgeTextStyle style = BadgeTextStyle.defaultFor(elementId);
                int rotation = 0;
                String text = "";
                String imagePath = "";
                float opacity = 1f;
                String colorHex = defaultColorHex(elementId);
                String nameLayout = "";
                Boolean visible = null;
                Integer zOrder = null;
                for (int i = 4; i < parts.length; i++) {
                    LayoutPart layoutPart = applyLayoutPart(style, rotation, parts[i]);
                    style = layoutPart.style();
                    rotation = layoutPart.rotation();
                    text = layoutPart.text() == null ? text : layoutPart.text();
                    imagePath = layoutPart.imagePath() == null ? imagePath : layoutPart.imagePath();
                    opacity = layoutPart.opacity() == null ? opacity : layoutPart.opacity();
                    colorHex = layoutPart.colorHex() == null ? colorHex : layoutPart.colorHex();
                    nameLayout = layoutPart.nameLayout() == null ? nameLayout : layoutPart.nameLayout();
                    visible = layoutPart.visible() == null ? visible : layoutPart.visible();
                    zOrder = layoutPart.zOrder() == null ? zOrder : layoutPart.zOrder();
                }
                entries.put(elementId, new LayoutEntry(rect, style.withDefaults(elementId), normalizeRotation(rotation), text, imagePath, sanitizeOpacity(opacity), colorHex, nameLayout, visible, zOrder));
            } catch (NumberFormatException ignored) {
                // Ignore malformed entries and keep remaining layout values.
            }
        }
        return entries;
    }

    private static String serializeLayout(Map<String, LayoutEntry> entries) {
        StringBuilder builder = new StringBuilder();
        for (BadgeElement element : DEFAULT_ELEMENTS.values()) {
            LayoutEntry entry = entries.get(element.id());
            if (entry == null) {
                continue;
            }
            Rectangle rect = entry.rect();
            BadgeTextStyle style = entry.style().withDefaults(element.id());
            int rotation = normalizeRotation(entry.rotation());
            String text = Objects.requireNonNullElse(entry.text(), "").trim();
            String imagePath = Objects.requireNonNullElse(entry.imagePath(), "").trim();
            float opacity = sanitizeOpacity(entry.opacity());
            String colorHex = normalizeColorHex(entry.colorHex(), defaultColorHex(element.id()));
            String nameLayout = "front.name".equals(element.id()) && "two".equalsIgnoreCase(Objects.requireNonNullElse(entry.nameLayout(), ""))
                    ? "two"
                    : "";
            Boolean visible = entry.visible();
            Integer zOrder = entry.zOrder();
            boolean defaultRect = rect.equals(element.defaultRect());
            boolean defaultStyle = !isTextElement(element.id()) || style.equals(BadgeTextStyle.defaultFor(element.id()));
            boolean defaultRotation = rotation == 0;
            boolean defaultText = !isCustomTextElement(element.id()) || text.isBlank();
            boolean defaultImage = !isImageElement(element.id()) || imagePath.isBlank();
            boolean defaultOpacity = !isImageElement(element.id()) || Math.abs(opacity - 1f) < 0.001f;
            boolean defaultColor = !isColorElement(element.id()) || colorHex.equals(defaultColorHex(element.id()));
            boolean defaultNameLayout = !"front.name".equals(element.id()) || nameLayout.isBlank();
            boolean defaultVisible = visible == null;
            boolean defaultZOrder = zOrder == null || zOrder == defaultZOrder(element.id());
            if (defaultRect && defaultStyle && defaultRotation && defaultText && defaultImage && defaultOpacity && defaultColor && defaultNameLayout && defaultVisible && defaultZOrder) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(';');
            }
            Rectangle clean = sanitizeRect(rect);
            builder.append(element.id())
                    .append('=')
                    .append(clean.x).append(',')
                    .append(clean.y).append(',')
                    .append(clean.width).append(',')
                    .append(clean.height);
            if (isTextElement(element.id()) && !defaultStyle) {
                builder.append(",font=").append(encode(style.family()))
                        .append(",style=").append(style.style())
                        .append(",size=").append(style.maxSize())
                        .append(",align=").append(style.alignment())
                        .append(",weight=").append(style.weight())
                        .append(",caps=").append(style.allCaps() ? "1" : "0")
                        .append(",textOutline=").append(style.textOutline() ? "1" : "0")
                        .append(",textOutlineColor=").append(style.textOutlineColorHex())
                        .append(",boxOutline=").append(style.boxOutline() ? "1" : "0")
                        .append(",boxOutlineColor=").append(style.boxOutlineColorHex());
            }
            if (!defaultRotation) {
                builder.append(",rot=").append(rotation);
            }
            if (isCustomTextElement(element.id()) && !text.isBlank()) {
                builder.append(",text=").append(encode(text));
            }
            if (isImageElement(element.id()) && !imagePath.isBlank()) {
                builder.append(",image=").append(encode(imagePath));
            }
            if (isImageElement(element.id()) && !defaultOpacity) {
                builder.append(",opacity=").append(String.format(Locale.ROOT, "%.2f", opacity));
            }
            if (isColorElement(element.id()) && !defaultColor) {
                builder.append(",color=").append(colorHex);
            }
            if ("front.name".equals(element.id()) && !defaultNameLayout) {
                builder.append(",nameLayout=").append(nameLayout);
            }
            if (!defaultVisible) {
                builder.append(",visible=").append(visible ? "1" : "0");
            }
            if (!defaultZOrder) {
                builder.append(",z=").append(zOrder);
            }
        }
        return builder.toString();
    }

    private static LayoutPart applyLayoutPart(BadgeTextStyle style, int rotation, String rawPart) {
        int equals = rawPart.indexOf('=');
        if (equals <= 0) {
            return new LayoutPart(style, rotation, null, null, null, null, null, null, null);
        }
        String key = rawPart.substring(0, equals).trim();
        String value = rawPart.substring(equals + 1).trim();
        try {
            return switch (key) {
                case "font" -> new LayoutPart(new BadgeTextStyle(decode(value), style.style(), style.maxSize(), style.alignment(), style.weight(), style.allCaps(), style.textOutline(), style.textOutlineColorHex(), style.boxOutline(), style.boxOutlineColorHex()), rotation, null, null, null, null, null, null, null);
                case "style" -> new LayoutPart(new BadgeTextStyle(style.family(), Integer.parseInt(value), style.maxSize(), style.alignment(), style.weight(), style.allCaps(), style.textOutline(), style.textOutlineColorHex(), style.boxOutline(), style.boxOutlineColorHex()), rotation, null, null, null, null, null, null, null);
                case "size" -> new LayoutPart(new BadgeTextStyle(style.family(), style.style(), Integer.parseInt(value), style.alignment(), style.weight(), style.allCaps(), style.textOutline(), style.textOutlineColorHex(), style.boxOutline(), style.boxOutlineColorHex()), rotation, null, null, null, null, null, null, null);
                case "align" -> new LayoutPart(new BadgeTextStyle(style.family(), style.style(), style.maxSize(), value, style.weight(), style.allCaps(), style.textOutline(), style.textOutlineColorHex(), style.boxOutline(), style.boxOutlineColorHex()), rotation, null, null, null, null, null, null, null);
                case "weight" -> new LayoutPart(new BadgeTextStyle(style.family(), style.style(), style.maxSize(), style.alignment(), value, style.allCaps(), style.textOutline(), style.textOutlineColorHex(), style.boxOutline(), style.boxOutlineColorHex()), rotation, null, null, null, null, null, null, null);
                case "caps" -> new LayoutPart(new BadgeTextStyle(style.family(), style.style(), style.maxSize(), style.alignment(), style.weight(), layoutBoolean(value), style.textOutline(), style.textOutlineColorHex(), style.boxOutline(), style.boxOutlineColorHex()), rotation, null, null, null, null, null, null, null);
                case "textOutline" -> new LayoutPart(new BadgeTextStyle(style.family(), style.style(), style.maxSize(), style.alignment(), style.weight(), style.allCaps(), layoutBoolean(value), style.textOutlineColorHex(), style.boxOutline(), style.boxOutlineColorHex()), rotation, null, null, null, null, null, null, null);
                case "textOutlineColor" -> new LayoutPart(new BadgeTextStyle(style.family(), style.style(), style.maxSize(), style.alignment(), style.weight(), style.allCaps(), style.textOutline(), normalizeColorHex(value, "#000000"), style.boxOutline(), style.boxOutlineColorHex()), rotation, null, null, null, null, null, null, null);
                case "boxOutline" -> new LayoutPart(new BadgeTextStyle(style.family(), style.style(), style.maxSize(), style.alignment(), style.weight(), style.allCaps(), style.textOutline(), style.textOutlineColorHex(), layoutBoolean(value), style.boxOutlineColorHex()), rotation, null, null, null, null, null, null, null);
                case "boxOutlineColor" -> new LayoutPart(new BadgeTextStyle(style.family(), style.style(), style.maxSize(), style.alignment(), style.weight(), style.allCaps(), style.textOutline(), style.textOutlineColorHex(), style.boxOutline(), normalizeColorHex(value, "#111827")), rotation, null, null, null, null, null, null, null);
                case "rot" -> new LayoutPart(style, normalizeRotation(Integer.parseInt(value)), null, null, null, null, null, null, null);
                case "text" -> new LayoutPart(style, rotation, decode(value), null, null, null, null, null, null);
                case "image" -> new LayoutPart(style, rotation, null, decode(value), null, null, null, null, null);
                case "opacity" -> new LayoutPart(style, rotation, null, null, sanitizeOpacity(Float.parseFloat(value)), null, null, null, null);
                case "color" -> new LayoutPart(style, rotation, null, null, null, normalizeColorHex(value, null), null, null, null);
                case "nameLayout" -> new LayoutPart(style, rotation, null, null, null, null, "two".equalsIgnoreCase(value) ? "two" : "", null, null);
                case "visible" -> new LayoutPart(style, rotation, null, null, null, null, null, layoutBoolean(value), null);
                case "z" -> new LayoutPart(style, rotation, null, null, null, null, null, null, Integer.parseInt(value));
                default -> new LayoutPart(style, rotation, null, null, null, null, null, null, null);
            };
        } catch (Exception ex) {
            return new LayoutPart(style, rotation, null, null, null, null, null, null, null);
        }
    }

    private static float sanitizeOpacity(float opacity) {
        if (Float.isNaN(opacity) || Float.isInfinite(opacity)) {
            return 1f;
        }
        return Math.max(0.05f, Math.min(1f, opacity));
    }

    private static boolean layoutBoolean(String value) {
        return !"0".equals(value) && !"false".equalsIgnoreCase(Objects.requireNonNullElse(value, ""));
    }

    private static Color defaultElementColor(String elementId) {
        return switch (Objects.requireNonNullElse(elementId, "")) {
            case "front.logo", "front.quote" -> DECKERS_ORANGE;
            case "front.role", "front.name" -> Color.WHITE;
            case "front.photo" -> BORDER_BLUE;
            case "back.printCount" -> new Color(75, 85, 99);
            case "back.signature" -> Color.BLACK;
            default -> new Color(31, 41, 55);
        };
    }

    private static String defaultColorHex(String elementId) {
        return colorToHex(defaultElementColor(elementId));
    }

    private static String colorToHex(Color color) {
        Color clean = color == null ? new Color(31, 41, 55) : color;
        return String.format(Locale.ROOT, "#%02X%02X%02X", clean.getRed(), clean.getGreen(), clean.getBlue());
    }

    private static String normalizeColorHex(String value, String fallback) {
        String clean = Objects.requireNonNullElse(value, "").trim();
        if (!clean.startsWith("#")) {
            clean = "#" + clean;
        }
        if (clean.matches("#[0-9A-Fa-f]{6}")) {
            return clean.toUpperCase(Locale.ROOT);
        }
        return fallback == null ? null : fallback;
    }

    private static Color parseColor(String value, Color fallback) {
        String clean = normalizeColorHex(value, null);
        if (clean == null) {
            return fallback == null ? new Color(31, 41, 55) : fallback;
        }
        try {
            return new Color(Integer.parseInt(clean.substring(1), 16));
        } catch (NumberFormatException ex) {
            return fallback == null ? new Color(31, 41, 55) : fallback;
        }
    }

    private static Color withAlpha(Color color, int alpha) {
        Color clean = color == null ? new Color(31, 41, 55) : color;
        return new Color(clean.getRed(), clean.getGreen(), clean.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private static int normalizeRotation(int rotation) {
        int normalized = Math.floorMod(rotation, 360);
        if (normalized == 0 || normalized == 90 || normalized == 180 || normalized == 270) {
            return normalized;
        }
        int nearestQuarterTurn = (int) Math.round(normalized / 90.0) * 90;
        return Math.floorMod(nearestQuarterTurn, 360);
    }

    private static String normalizeAlignment(String alignment) {
        return switch (Objects.requireNonNullElse(alignment, "center").trim().toLowerCase(Locale.ROOT)) {
            case "left" -> "left";
            case "right" -> "right";
            default -> "center";
        };
    }

    private static String normalizeFontWeight(String weight) {
        String clean = Objects.requireNonNullElse(weight, "regular")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("-", "");
        return switch (clean) {
            case "medium" -> "medium";
            case "semibold", "demibold" -> "semibold";
            case "bold" -> "bold";
            case "extrabold", "ultrabold", "heavy" -> "extrabold";
            case "black" -> "black";
            default -> "regular";
        };
    }

    private static Rectangle sanitizeRect(Rectangle rect) {
        int width = Math.max(24, Math.min(CARD_WIDTH, rect.width));
        int height = Math.max(18, Math.min(CARD_HEIGHT, rect.height));
        int x = Math.max(0, Math.min(CARD_WIDTH - width, rect.x));
        int y = Math.max(0, Math.min(CARD_HEIGHT - height, rect.y));
        return new Rectangle(x, y, width, height);
    }

    private static Rectangle sanitizeBarcodeRect(Rectangle rect) {
        int width = Math.max(270, Math.min(330, rect.width));
        int height = Math.max(960, Math.min(CARD_HEIGHT, rect.height));
        int centerX = rect.x + rect.width / 2;
        int centerY = rect.y + rect.height / 2;
        int x = Math.max(0, Math.min(CARD_WIDTH - width, centerX - width / 2));
        int y = Math.max(0, Math.min(CARD_HEIGHT - height, centerY - height / 2));
        return new Rectangle(x, y, width, height);
    }

    private static String quoteLine(String quote) {
        String clean = Objects.requireNonNullElse(quote, "").trim();
        if (clean.isBlank()) {
            clean = "\"Sales goes up and down, Service is Forever\"";
        }
        return clean;
    }

    private static String encode(String value) {
        return URLEncoder.encode(Objects.requireNonNullElse(value, ""), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String decode(String value) {
        return URLDecoder.decode(Objects.requireNonNullElse(value, ""), StandardCharsets.UTF_8);
    }

    private static BufferedImage renderCode128(String text, int width, int height) {
        String value = BadgeCredentialService.normalizeBadge(text);
        if (value.isBlank()) {
            value = "BADGE";
        }
        int safeWidth = Math.max(180, width);
        int safeHeight = Math.max(70, height);
        BitMatrix matrix = new Code128Writer().encode(
                value,
                BarcodeFormat.CODE_128,
                safeWidth,
                safeHeight,
                Map.of(EncodeHintType.MARGIN, 10)
        );
        BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                image.setRGB(x, y, matrix.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }
        return image;
    }

    public static List<String> splitWriterCommand(String command) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        char quoteChar = 0;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if ((c == '"' || c == '\'') && (!inQuote || quoteChar == c)) {
                inQuote = !inQuote;
                quoteChar = inQuote ? c : 0;
            } else if (Character.isWhitespace(c) && !inQuote) {
                if (!current.isEmpty()) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static final class BadgePrintable implements Printable {
        private final EmployeeBadgeData employee;
        private final CompanyCustomizationManager.BadgeTemplateSettings settings;
        private final BadgePrintSide side;
        private final LocalDate expiryDate;

        private BadgePrintable(EmployeeBadgeData employee, CompanyCustomizationManager.BadgeTemplateSettings settings, BadgePrintSide side, LocalDate expiryDate) {
            this.employee = employee;
            this.settings = settings;
            this.side = side == null ? BadgePrintSide.BOTH : side;
            this.expiryDate = expiryDate == null ? defaultExpiryDate() : expiryDate;
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
            if (pageIndex >= side.pageCount()) {
                return NO_SUCH_PAGE;
            }
            BufferedImage image = switch (side) {
                case FRONT -> renderFront(employee, settings, PRINT_RENDER_SCALE);
                case BACK -> renderBack(employee, settings, expiryDate, PRINT_RENDER_SCALE);
                case BOTH -> pageIndex == 0
                        ? renderFront(employee, settings, PRINT_RENDER_SCALE)
                        : renderBack(employee, settings, expiryDate, PRINT_RENDER_SCALE);
            };
            Graphics2D g = (Graphics2D) graphics.create();
            configure(g);
            double scale = Math.min(pageFormat.getImageableWidth() / CARD_WIDTH, pageFormat.getImageableHeight() / CARD_HEIGHT);
            double x = pageFormat.getImageableX() + (pageFormat.getImageableWidth() - CARD_WIDTH * scale) / 2;
            double y = pageFormat.getImageableY() + (pageFormat.getImageableHeight() - CARD_HEIGHT * scale) / 2;
            int drawWidth = (int) Math.round(CARD_WIDTH * scale);
            int drawHeight = (int) Math.round(CARD_HEIGHT * scale);
            g.drawImage(image, (int) Math.round(x), (int) Math.round(y), drawWidth, drawHeight, null);
            g.dispose();
            return PAGE_EXISTS;
        }
    }

    public enum BadgePrintSide {
        FRONT("Front only"),
        BACK("Back only"),
        BOTH("Front and back");

        private final String label;

        BadgePrintSide(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public int pageCount() {
            return this == BOTH ? 2 : 1;
        }

        public boolean includesBack() {
            return this == BACK || this == BOTH;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record EmployeeBadgeData(
            int userId,
            String username,
            String fullName,
            String firstName,
            String middleName,
            String lastName,
            String email,
            String phone,
            String badgeId,
            String photoPath,
            int badgePrintCount,
            String roleName,
            String locationName
    ) {
        public EmployeeBadgeData {
            username = Objects.requireNonNullElse(username, "");
            fullName = Objects.requireNonNullElse(fullName, "");
            firstName = Objects.requireNonNullElse(firstName, "");
            middleName = Objects.requireNonNullElse(middleName, "");
            lastName = Objects.requireNonNullElse(lastName, "");
            email = Objects.requireNonNullElse(email, "");
            phone = Objects.requireNonNullElse(phone, "");
            badgeId = BadgeCredentialService.normalizeBadge(badgeId);
            photoPath = Objects.requireNonNullElse(photoPath, "");
            badgePrintCount = Math.max(0, badgePrintCount);
            roleName = Objects.requireNonNullElse(roleName, "USER");
            locationName = Objects.requireNonNullElse(locationName, "");
        }

        public String displayName() {
            if (!firstName.isBlank() || !lastName.isBlank()) {
                String middleInitial = middleName.isBlank() ? "" : " " + middleName.trim().substring(0, 1).toUpperCase(Locale.ROOT) + ".";
                return (firstName.trim() + middleInitial + " " + lastName.trim()).trim();
            }
            if (!fullName.isBlank()) {
                return fullName;
            }
            return username.trim();
        }

        public List<String> displayNameLines() {
            if (!firstName.isBlank() || !lastName.isBlank()) {
                String middleInitial = middleName.isBlank() ? "" : " " + middleName.trim().substring(0, 1).toUpperCase(Locale.ROOT) + ".";
                String firstLine = (firstName.trim() + middleInitial).trim();
                String secondLine = lastName.trim();
                if (firstLine.isBlank()) {
                    return List.of(secondLine);
                }
                if (secondLine.isBlank()) {
                    return List.of(firstLine);
                }
                return List.of(firstLine, secondLine);
            }
            String display = displayName();
            int lastSpace = display.lastIndexOf(' ');
            if (lastSpace > 0 && lastSpace < display.length() - 1) {
                return List.of(display.substring(0, lastSpace).trim(), display.substring(lastSpace + 1).trim());
            }
            return List.of(display);
        }

        public int nextPrintCount() {
            return badgePrintCount + 1;
        }
    }

    public record BadgeElement(String id, String label, String side, Rectangle defaultRect) {
        public BadgeElement {
            defaultRect = new Rectangle(defaultRect);
        }
    }

    @FunctionalInterface
    private interface GraphicsPainter {
        void paint(Graphics2D g);
    }

    private record LayoutEntry(Rectangle rect, BadgeTextStyle style, int rotation, String text, String imagePath, float opacity, String colorHex, String nameLayout, Boolean visible, Integer zOrder) {
        private LayoutEntry(Rectangle rect, BadgeTextStyle style, int rotation, String text, String imagePath, float opacity, String colorHex, String nameLayout) {
            this(rect, style, rotation, text, imagePath, opacity, colorHex, nameLayout, null);
        }

        private LayoutEntry(Rectangle rect, BadgeTextStyle style, int rotation, String text, String imagePath, float opacity, String colorHex, String nameLayout, Boolean visible) {
            this(rect, style, rotation, text, imagePath, opacity, colorHex, nameLayout, visible, null);
        }
    }

    private record LayoutPart(BadgeTextStyle style, int rotation, String text, String imagePath, Float opacity, String colorHex, String nameLayout, Boolean visible, Integer zOrder) {
    }

    public record BadgeTextStyle(String family, int style, int maxSize, String alignment, String weight, boolean allCaps, boolean textOutline, String textOutlineColorHex, boolean boxOutline, String boxOutlineColorHex) {
        private static final int MIN_SIZE = 8;

        public BadgeTextStyle(String family, int style, int maxSize) {
            this(family, style, maxSize, "center");
        }

        public BadgeTextStyle(String family, int style, int maxSize, String alignment) {
            this(family, style, maxSize, alignment, "regular");
        }

        public BadgeTextStyle(String family, int style, int maxSize, String alignment, String weight) {
            this(family, style, maxSize, alignment, weight, false, false, "#000000", false, "#111827");
        }

        public BadgeTextStyle {
            family = Objects.requireNonNullElse(family, "SansSerif").isBlank() ? "SansSerif" : family.trim();
            style = switch (style) {
                case Font.BOLD, Font.ITALIC, Font.BOLD | Font.ITALIC -> style;
                default -> Font.PLAIN;
            };
            maxSize = Math.max(MIN_SIZE, Math.min(96, maxSize));
            alignment = normalizeAlignment(alignment);
            weight = normalizeFontWeight(weight);
            textOutlineColorHex = normalizeColorHex(textOutlineColorHex, "#000000");
            boxOutlineColorHex = normalizeColorHex(boxOutlineColorHex, "#111827");
        }

        public int minSize() {
            return MIN_SIZE;
        }

        public BadgeTextStyle withDefaults(String elementId) {
            BadgeTextStyle fallback = defaultFor(elementId);
            String cleanFamily = family == null || family.isBlank() ? fallback.family() : family;
            int cleanMaxSize = maxSize <= 0 ? fallback.maxSize() : maxSize;
            String cleanAlignment = alignment == null || alignment.isBlank() ? fallback.alignment() : alignment;
            String cleanWeight = weight == null || weight.isBlank() ? fallback.weight() : weight;
            String cleanTextOutlineColor = normalizeColorHex(textOutlineColorHex, fallback.textOutlineColorHex());
            String cleanBoxOutlineColor = normalizeColorHex(boxOutlineColorHex, fallback.boxOutlineColorHex());
            return new BadgeTextStyle(cleanFamily, style, cleanMaxSize, cleanAlignment, cleanWeight, allCaps, textOutline, cleanTextOutlineColor, boxOutline, cleanBoxOutlineColor);
        }

        public static BadgeTextStyle defaultFor(String elementId) {
            return switch (Objects.requireNonNullElse(elementId, "")) {
                case "front.logo" -> new BadgeTextStyle("Serif", Font.BOLD | Font.ITALIC, 62, "center");
                case "front.quote" -> new BadgeTextStyle("Serif", Font.BOLD, 32, "center");
                case "front.role" -> new BadgeTextStyle("SansSerif", Font.BOLD, 34, "center");
                case "front.name" -> new BadgeTextStyle("SansSerif", Font.PLAIN, 44, "center");
                case "back.employeeNumber" -> new BadgeTextStyle("SansSerif", Font.BOLD, 13, "left");
                case "back.issueDate", "back.expiryDate" -> new BadgeTextStyle("SansSerif", Font.PLAIN, 13, "left");
                case "back.badgeText" -> new BadgeTextStyle("SansSerif", Font.BOLD, 20, "center");
                case "back.instructions" -> new BadgeTextStyle("SansSerif", Font.PLAIN, 16, "center");
                case "back.signature" -> new BadgeTextStyle("Serif", Font.ITALIC, 38, "left");
                case "back.watermark" -> new BadgeTextStyle("SansSerif", Font.BOLD, 64, "center");
                case "back.poweredBy" -> new BadgeTextStyle("SansSerif", Font.PLAIN, 14, "center");
                case "back.printCount" -> new BadgeTextStyle("SansSerif", Font.PLAIN, 10, "left");
                default -> new BadgeTextStyle("SansSerif", Font.PLAIN, 20, "center");
            };
        }
    }
}
