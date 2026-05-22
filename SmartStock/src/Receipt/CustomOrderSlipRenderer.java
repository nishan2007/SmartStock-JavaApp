package Receipt;

import managers.CompanyCustomizationManager;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CustomOrderSlipRenderer {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final NumberFormat MONEY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);

    private CustomOrderSlipRenderer() {
    }

    public static void paintSlip(Graphics2D graphics,
                                 int x,
                                 int y,
                                 int width,
                                 int height,
                                 CustomOrderSlipData data,
                                 CompanyCustomizationManager.ReceiptSettings receiptSettings,
                                 CompanyCustomizationManager.CustomOrderSlipSettings slipSettings,
                                 BufferedImage logo) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(x, y, width, height);
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1f));
            g.drawRect(x, y, width, height);

            int margin = Math.max(width / 28, 18);
            int left = x + margin;
            int right = x + width - margin;
            int currentY = y + margin;

            int logoWidth = 132;
            int logoHeight = 58;
            if (slipSettings.showLogo() && logo != null) {
                drawImageWithin(g, logo, left, currentY, logoWidth, logoHeight);
            } else {
                g.setFont(new Font("SansSerif", Font.BOLD, 18));
                drawCentered(g, receiptSettings.companyName(), left, currentY + 35, logoWidth);
            }

            int titleX = left + logoWidth + 18;
            g.setFont(new Font("SansSerif", Font.BOLD, 28));
            g.drawString(slipSettings.title(), titleX, currentY + 24);
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            if (!slipSettings.contactLine().isBlank()) {
                g.drawString(slipSettings.contactLine(), titleX, currentY + 43);
            }
            if (!slipSettings.emailLine().isBlank()) {
                g.drawString(slipSettings.emailLine(), titleX, currentY + 60);
            }
            currentY += 82;

            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            currentY = drawLabelLine(g, left, right, currentY, "CUSTOMER NAME:", data.customerName(), "DATE:", createdDate(data));
            if (slipSettings.showOrderNumber() || slipSettings.showDueDate()) {
                String leftValue = slipSettings.showOrderNumber() ? data.orderNumber() : "";
                String rightLabel = slipSettings.showDueDate() ? "DUE:" : "";
                String rightValue = slipSettings.showDueDate() && data.dueDate() != null ? DATE_FORMAT.format(data.dueDate()) : "";
                currentY = drawLabelLine(g, left, right, currentY, "CONTACT NUMBER:", slipSettings.showCustomerPhone() ? data.customerPhone() : "", rightLabel, rightValue);
                if (slipSettings.showOrderNumber()) {
                    currentY = drawFullLine(g, left, right, currentY, "ORDER NUMBER:", leftValue);
                }
            } else if (slipSettings.showCustomerPhone()) {
                currentY = drawFullLine(g, left, right, currentY, "CONTACT NUMBER:", data.customerPhone());
            }

            currentY += 4;
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString("Details:", left, currentY);
            currentY += 8;
            currentY = drawDetails(g, left, right, currentY, data, slipSettings);

            currentY = Math.max(currentY + 12, y + height - 92);
            if (slipSettings.showTakenBy()) {
                currentY = drawLabelLine(g, left, right, currentY, "DELIVERED BY:", data.takenByName(), "RECEIVED BY:", "");
            }
            if (slipSettings.showPaymentSummary()) {
                String payment = clean(data.paymentMethod()) + " / " + clean(data.paymentStatus());
                currentY = drawLabelLine(g, left, right, currentY, "PAYMENT METHOD:", payment, "BAL:", money(data.balanceDue()));
                currentY = drawLabelLine(g, left, right, currentY, "AMT:", money(data.amountPaid()), "TOTAL:", money(data.totalAmount()));
            }
            if (slipSettings.showPaymentReference() && data.paymentReference() != null && !data.paymentReference().isBlank()) {
                currentY = drawFullLine(g, left, right, currentY, "PAYMENT REF:", data.paymentReference());
            }
            if (slipSettings.showSignatures()) {
                currentY = drawLabelLine(g, left, right, currentY, "TEAM MEMBER SIGNATURE:", "", "CUSTOMER'S SIGNATURE:", "");
            }

            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            g.drawString(slipSettings.footerNote(), left, Math.min(y + height - 12, currentY + 12));
        } finally {
            g.dispose();
        }
    }

    private static int drawDetails(Graphics2D g, int left, int right, int y, CustomOrderSlipData data, CompanyCustomizationManager.CustomOrderSlipSettings settings) {
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        int lineHeight = 15;
        int currentY = y;
        if (settings.showLineItems()) {
            int index = 1;
            for (CustomOrderSlipData.Line line : data.lines()) {
                StringBuilder text = new StringBuilder(index++).append(". ").append(clean(line.itemName()));
                if (line.variantName() != null && !line.variantName().isBlank()) {
                    text.append(" / ").append(line.variantName());
                }
                if (settings.showPricing()) {
                    text.append(" - ").append(money(line.lineTotal()));
                }
                for (String wrapped : wrap(g, text.toString(), right - left)) {
                    currentY = drawRuledText(g, left, right, currentY, wrapped, lineHeight);
                }
                String detailText = joinNonBlank(line.details(), line.instructions());
                for (String wrapped : wrap(g, detailText, right - left - 18)) {
                    currentY = drawRuledText(g, left + 18, right, currentY, wrapped, lineHeight);
                }
            }
        }
        if (data.orderNotes() != null && !data.orderNotes().isBlank()) {
            for (String wrapped : wrap(g, "Notes: " + data.orderNotes(), right - left)) {
                currentY = drawRuledText(g, left, right, currentY, wrapped, lineHeight);
            }
        }
        for (int i = 0; i < settings.blankDetailLines(); i++) {
            currentY = drawRuledText(g, left, right, currentY, "", lineHeight);
        }
        return currentY;
    }

    private static int drawLabelLine(Graphics2D g, int left, int right, int y, String leftLabel, String leftValue, String rightLabel, String rightValue) {
        int mid = left + ((right - left) / 2);
        drawLabeledValue(g, left, mid - 14, y, leftLabel, leftValue);
        drawLabeledValue(g, mid + 14, right, y, rightLabel, rightValue);
        return y + 18;
    }

    private static int drawFullLine(Graphics2D g, int left, int right, int y, String label, String value) {
        drawLabeledValue(g, left, right, y, label, value);
        return y + 18;
    }

    private static void drawLabeledValue(Graphics2D g, int left, int right, int y, String label, String value) {
        if (label == null || label.isBlank()) {
            return;
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.drawString(label, left, y);
        int labelWidth = g.getFontMetrics().stringWidth(label) + 6;
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.drawString(clean(value), left + labelWidth, y);
        g.drawLine(left + labelWidth, y + 3, right, y + 3);
    }

    private static int drawRuledText(Graphics2D g, int left, int right, int y, String text, int lineHeight) {
        if (text != null && !text.isBlank()) {
            g.drawString(text, left + 2, y);
        }
        g.drawLine(left, y + 3, right, y + 3);
        return y + lineHeight;
    }

    private static void drawCentered(Graphics2D g, String text, int x, int y, int width) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, x + ((width - fm.stringWidth(text)) / 2), y);
    }

    private static void drawImageWithin(Graphics2D g, BufferedImage image, int x, int y, int maxWidth, int maxHeight) {
        double scale = Math.min((double) maxWidth / image.getWidth(), (double) maxHeight / image.getHeight());
        int width = Math.max(1, (int) Math.round(image.getWidth() * Math.min(scale, 1.0)));
        int height = Math.max(1, (int) Math.round(image.getHeight() * Math.min(scale, 1.0)));
        g.drawImage(image, x, y + ((maxHeight - height) / 2), width, height, null);
    }

    private static List<String> wrap(Graphics2D g, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (g.getFontMetrics().stringWidth(candidate) <= maxWidth || line.isEmpty()) {
                line = new StringBuilder(candidate);
            } else {
                lines.add(line.toString());
                line = new StringBuilder(word);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static String joinNonBlank(String first, String second) {
        String cleanFirst = clean(first);
        String cleanSecond = clean(second);
        if (cleanFirst.isBlank()) {
            return cleanSecond;
        }
        if (cleanSecond.isBlank()) {
            return cleanFirst;
        }
        return cleanFirst + " / " + cleanSecond;
    }

    private static String createdDate(CustomOrderSlipData data) {
        if (data.createdAt() == null) {
            return "";
        }
        return DATE_FORMAT.format(data.createdAt().toLocalDateTime().toLocalDate());
    }

    private static String money(BigDecimal value) {
        return MONEY_FORMAT.format(value == null ? BigDecimal.ZERO : value);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
