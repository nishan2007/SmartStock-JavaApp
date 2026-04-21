package Receipt;

import managers.CompanyCustomizationManager;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;

public class ReceiptFormatter {
    private static final int RECEIPT_WIDTH = 40;
    private static final int LETTER_WIDTH = 86;
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");

    private ReceiptFormatter() {
    }

    public static String formatText(ReceiptData receipt) {
        return formatColumnText(receipt, RECEIPT_WIDTH);
    }

    public static String formatText(ReceiptData receipt, CompanyCustomizationManager.ReceiptSettings settings) {
        return formatColumnText(receipt, RECEIPT_WIDTH, settings);
    }

    public static String formatLetterText(ReceiptData receipt) {
        return formatColumnText(receipt, LETTER_WIDTH);
    }

    public static String formatLetterText(ReceiptData receipt, CompanyCustomizationManager.ReceiptSettings settings) {
        return formatColumnText(receipt, LETTER_WIDTH, settings);
    }

    private static String formatColumnText(ReceiptData receipt, int width) {
        return formatColumnText(receipt, width, getReceiptSettings());
    }

    private static String formatColumnText(ReceiptData receipt, int width, CompanyCustomizationManager.ReceiptSettings settings) {
        settings = settings == null ? getReceiptSettings() : settings;
        StringBuilder builder = new StringBuilder();
        appendCentered(builder, settings.companyName(), width);
        appendCentered(builder, emptyDefault(receipt.getStoreName(), "Store"), width);
        if (!settings.headerLine().isBlank()) {
            appendCentered(builder, settings.headerLine(), width);
        }
        builder.append(repeat("-", width)).append('\n');
        appendPair(builder, "Receipt", receipt.getReceiptNumber(), width);
        if (settings.showSaleId()) {
            appendPair(builder, "Sale ID", String.valueOf(receipt.getSaleId()), width);
        }
        appendPair(builder, "Date", receipt.getSaleTime() == null ? "" : TIME_FORMAT.format(receipt.getSaleTime().toLocalDateTime()), width);
        appendPair(builder, "Cashier", receipt.getCashierName(), width);
        if (settings.showDevice()) {
            appendPair(builder, "Device", receipt.getDeviceId(), width);
        }
        if (settings.showCustomer() && !receipt.getCustomerName().isBlank()) {
            appendPair(builder, "Customer", receipt.getCustomerName(), width);
        }
        if (settings.showCustomer() && !receipt.getAccountNumber().isBlank()) {
            appendPair(builder, "Account", receipt.getAccountNumber(), width);
        }
        builder.append(repeat("-", width)).append('\n');

        for (ReceiptItem item : receipt.getItems()) {
            builder.append(trimToWidth(item.getName(), width)).append('\n');
            if (settings.showSku() && !item.getSku().isBlank()) {
                builder.append("  SKU: ").append(trimToWidth(item.getSku(), width - 7)).append('\n');
            }
            String qtyPrice = "  " + item.getQuantity() + " x " + money(item.getFinalUnitPrice());
            appendPair(builder, qtyPrice, money(item.getLineTotal()), width);
            if (settings.showItemDiscount() && item.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
                builder.append("  Item Discount: ").append(item.getDiscountPercent().stripTrailingZeros().toPlainString()).append("%").append('\n');
            }
        }

        builder.append(repeat("-", width)).append('\n');
        appendPair(builder, "Subtotal", money(receipt.getSubtotalAmount()), width);
        if (receipt.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            appendPair(builder, "Discount", money(receipt.getDiscountAmount()), width);
        }
        appendPair(builder, "Total", money(receipt.getTotalAmount()), width);
        appendPair(builder, "Payment", receipt.getPaymentMethod(), width);
        if (settings.showPaymentStatus()) {
            appendPair(builder, "Status", receipt.getPaymentStatus(), width);
        }
        appendPair(builder, "Paid", money(receipt.getAmountPaid()), width);
        if (receipt.getCashCollected() != null) {
            appendPair(builder, "Cash Collected", money(receipt.getCashCollected()), width);
            appendPair(builder, "Change Due", money(receipt.getChangeDue()), width);
        }
        if (receipt.getReturnedAmount().compareTo(BigDecimal.ZERO) > 0) {
            appendPair(builder, "Returned", money(receipt.getReturnedAmount()), width);
        }
        builder.append(repeat("-", width)).append('\n');
        appendCentered(builder, settings.footerLine(), width);
        builder.append('\n');
        return builder.toString();
    }

    public static byte[] formatEscPos(ReceiptData receipt) {
        return formatEscPos(receipt, getReceiptSettings());
    }

    public static byte[] formatEscPos(ReceiptData receipt, CompanyCustomizationManager.ReceiptSettings settings) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, 0x1B, 0x40);
        write(out, 0x1B, 0x4D, 0x00);
        write(out, 0x1D, 0x21, 0x00);
        appendEscPosLogo(out, settings);
        write(out, 0x1B, 0x61, 0x00);
        writeAscii(out, formatText(receipt, settings));
        write(out, 0x1B, 0x61, 0x01);
        writeAscii(out, "\n\n\n");
        write(out, 0x1D, 0x56, 0x42, 0x00);
        return out.toByteArray();
    }

    private static void appendCentered(StringBuilder builder, String value, int width) {
        String text = trimToWidth(value, width);
        int padding = Math.max((width - text.length()) / 2, 0);
        builder.append(" ".repeat(padding)).append(text).append('\n');
    }

    private static void appendPair(StringBuilder builder, String label, String value, int width) {
        String cleanLabel = label == null ? "" : label;
        String cleanValue = value == null ? "" : value;
        int spaces = width - cleanLabel.length() - cleanValue.length();
        if (spaces < 1) {
            builder.append(trimToWidth(cleanLabel, Math.max(width - cleanValue.length() - 1, 1)))
                    .append(' ')
                    .append(cleanValue)
                    .append('\n');
        } else {
            builder.append(cleanLabel).append(" ".repeat(spaces)).append(cleanValue).append('\n');
        }
    }

    private static String money(BigDecimal value) {
        return CURRENCY.format(value == null ? BigDecimal.ZERO : value);
    }

    private static String repeat(String value, int width) {
        return value.repeat(width);
    }

    private static String trimToWidth(String value, int width) {
        String text = value == null ? "" : value;
        return text.length() <= width ? text : text.substring(0, width);
    }

    private static String emptyDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void writeAscii(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        out.write(bytes, 0, bytes.length);
    }

    private static void write(ByteArrayOutputStream out, int... values) {
        for (int value : values) {
            out.write(value);
        }
    }

    private static void appendEscPosLogo(ByteArrayOutputStream out, CompanyCustomizationManager.ReceiptSettings settings) {
        BufferedImage logo = CompanyCustomizationManager.loadReceiptLogo(settings);
        if (logo == null) {
            return;
        }

        BufferedImage prepared = prepareMonochromeLogo(logo, 384, 160);
        int width = prepared.getWidth();
        int height = prepared.getHeight();
        int bytesPerRow = (width + 7) / 8;

        write(out, 0x1B, 0x61, 0x01);
        write(out, 0x1D, 0x76, 0x30, 0x00, bytesPerRow & 0xFF, (bytesPerRow >> 8) & 0xFF, height & 0xFF, (height >> 8) & 0xFF);

        for (int y = 0; y < height; y++) {
            for (int xByte = 0; xByte < bytesPerRow; xByte++) {
                int value = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int x = (xByte * 8) + bit;
                    if (x < width && isDark(prepared.getRGB(x, y))) {
                        value |= 0x80 >> bit;
                    }
                }
                out.write(value);
            }
        }
        writeAscii(out, "\n");
    }

    private static BufferedImage prepareMonochromeLogo(BufferedImage logo, int maxWidth, int maxHeight) {
        double scale = Math.min((double) maxWidth / logo.getWidth(), (double) maxHeight / logo.getHeight());
        scale = Math.min(scale, 1.0);
        int width = Math.max((int) Math.round(logo.getWidth() * scale), 1);
        int height = Math.max((int) Math.round(logo.getHeight() * scale), 1);

        BufferedImage prepared = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = prepared.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.drawImage(logo, 0, 0, width, height, null);
        graphics.dispose();
        return prepared;
    }

    private static boolean isDark(int rgb) {
        Color color = new Color(rgb);
        int luminance = (int) ((0.299 * color.getRed()) + (0.587 * color.getGreen()) + (0.114 * color.getBlue()));
        return luminance < 160;
    }

    private static CompanyCustomizationManager.ReceiptSettings getReceiptSettings() {
        CompanyCustomizationManager.ReceiptSettings previewSettings = CompanyCustomizationManager.getPreviewOverrideSettings();
        return previewSettings == null ? CompanyCustomizationManager.loadReceiptSettings() : previewSettings;
    }
}
