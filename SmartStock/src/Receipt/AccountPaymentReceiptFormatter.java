package Receipt;

import utils.CurrencyFormatter;
import managers.CompanyCustomizationManager;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;

public class AccountPaymentReceiptFormatter {
    private static final int RECEIPT_WIDTH = 40;
    private static final int LETTER_WIDTH = 86;
    private static final NumberFormat CURRENCY = CurrencyFormatter.create();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");

    private AccountPaymentReceiptFormatter() {
    }

    public static String formatText(AccountPaymentReceiptData receipt, CompanyCustomizationManager.ReceiptSettings settings) {
        return formatColumnText(receipt, RECEIPT_WIDTH, settings, settings == null ? null : settings.accountPaymentReceiptSettings());
    }

    public static String formatLetterText(AccountPaymentReceiptData receipt, CompanyCustomizationManager.ReceiptSettings settings) {
        return formatColumnText(receipt, LETTER_WIDTH, settings, settings == null ? null : settings.accountPaymentReceiptSettings());
    }

    public static String formatText(AccountPaymentReceiptData receipt, CompanyCustomizationManager.ReceiptSettings settings,
                                    CompanyCustomizationManager.AccountPaymentReceiptSettings paymentSettings) {
        return formatColumnText(receipt, RECEIPT_WIDTH, settings, paymentSettings);
    }

    public static String formatLetterText(AccountPaymentReceiptData receipt, CompanyCustomizationManager.ReceiptSettings settings,
                                          CompanyCustomizationManager.AccountPaymentReceiptSettings paymentSettings) {
        return formatColumnText(receipt, LETTER_WIDTH, settings, paymentSettings);
    }

    private static String formatColumnText(AccountPaymentReceiptData receipt, int width, CompanyCustomizationManager.ReceiptSettings settings,
                                           CompanyCustomizationManager.AccountPaymentReceiptSettings paymentSettings) {
        settings = settings == null ? CompanyCustomizationManager.loadReceiptSettings() : settings;
        paymentSettings = paymentSettings == null ? settings.accountPaymentReceiptSettings() : paymentSettings;
        StringBuilder builder = new StringBuilder();
        appendCentered(builder, settings.companyName(), width);
        appendCentered(builder, emptyDefault(receipt.getStoreName(), "Store"), width);
        if (!settings.headerLine().isBlank()) {
            appendCentered(builder, settings.headerLine(), width);
        }
        builder.append(repeat("-", width)).append('\n');
        appendCentered(builder, paymentSettings.title(), width);
        builder.append(repeat("-", width)).append('\n');
        appendPair(builder, "Payment ID", receipt.getPaymentId(), width);
        appendPair(builder, "Date", receipt.getPaymentTime() == null ? "" : TIME_FORMAT.format(receipt.getPaymentTime().toLocalDateTime()), width);
        if (paymentSettings.showUser()) {
            appendPair(builder, "User", receipt.getUserName(), width);
        }
        if (paymentSettings.showDevice() && !receipt.getDeviceName().isBlank()) {
            appendPair(builder, "Device", receipt.getDeviceName(), width);
        }
        if (paymentSettings.showDrawer() && !receipt.getCashDrawerName().isBlank()) {
            appendPair(builder, "Drawer", receipt.getCashDrawerName(), width);
        }
        if (paymentSettings.showCustomer() && !receipt.getCustomerName().isBlank()) {
            appendPair(builder, "Customer", receipt.getCustomerName(), width);
        }
        if (paymentSettings.showAccountNumber() && !receipt.getAccountNumber().isBlank()) {
            appendPair(builder, "Account", receipt.getAccountNumber(), width);
        }
        builder.append(repeat("-", width)).append('\n');
        if (paymentSettings.showMethod()) {
            appendPair(builder, "Method", receipt.getPaymentMethod(), width);
        }
        if (paymentSettings.showReference() && !receipt.getPaymentReference().isBlank()) {
            appendPair(builder, "Reference", receipt.getPaymentReference(), width);
        }
        appendPair(builder, "Amount Paid", money(receipt.getPaymentAmount()), width);
        if (paymentSettings.showBalance()) {
            appendPair(builder, "Balance Due", money(receipt.getAccountBalanceAfter()), width);
        }
        if (paymentSettings.showAllocations()) {
            builder.append(repeat("-", width)).append('\n');
            appendCentered(builder, "APPLIED TO", width);
            if (receipt.getAllocations().isEmpty()) {
                builder.append(trimToWidth("Account balance", width)).append('\n');
            } else {
                for (AccountPaymentReceiptData.AllocationLine line : receipt.getAllocations()) {
                    builder.append(trimToWidth(line.targetLabel(), width)).append('\n');
                    appendPair(builder, "  Applied", money(line.appliedAmount()), width);
                    appendPair(builder, "  Charge Total", money(line.chargeTotal()), width);
                    appendPair(builder, "  Paid", money(line.chargePaid()), width);
                    if (!line.paymentStatus().isBlank()) {
                        appendPair(builder, "  Status", line.paymentStatus(), width);
                    }
                    if (line.chargeDate() != null) {
                        appendPair(builder, "  Charge Date", TIME_FORMAT.format(line.chargeDate().toLocalDateTime()), width);
                    }
                }
            }
        }
        builder.append(repeat("-", width)).append('\n');
        appendCentered(builder, settings.footerLine(), width);
        builder.append('\n');
        return builder.toString();
    }

    public static byte[] formatEscPos(AccountPaymentReceiptData receipt, CompanyCustomizationManager.ReceiptSettings settings) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, 0x1B, 0x40);
        write(out, 0x1B, 0x4D, 0x00);
        write(out, 0x1D, 0x21, 0x00);
        appendEscPosLogo(out, settings);
        write(out, 0x1B, 0x61, 0x00);
        writeAscii(out, formatText(receipt, settings));
        if (settings.accountPaymentReceiptSettings().showBarcode()) {
            appendEscPosBarcode(out, receipt.getPaymentId());
        }
        write(out, 0x1B, 0x61, 0x01);
        writeAscii(out, "\n\n\n");
        write(out, 0x1D, 0x56, 0x42, 0x00);
        return out.toByteArray();
    }

    private static void appendCentered(StringBuilder builder, String value, int width) {
        for (String line : (value == null ? "" : value).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            String text = trimToWidth(line, width);
            int padding = Math.max((width - text.length()) / 2, 0);
            builder.append(" ".repeat(padding)).append(text).append('\n');
        }
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

    private static void appendEscPosBarcode(ByteArrayOutputStream out, String barcodeText) {
        if (!ReceiptBarcodeRenderer.hasScannableText(barcodeText)) {
            return;
        }
        BufferedImage barcode = ReceiptBarcodeRenderer.renderCode128(barcodeText, 384, 88);
        write(out, 0x1B, 0x61, 0x01);
        appendEscPosRasterImage(out, barcode);
        writeAscii(out, "\n");
        writeAscii(out, barcodeText + "\n");
        write(out, 0x1B, 0x61, 0x00);
    }

    private static void appendEscPosRasterImage(ByteArrayOutputStream out, BufferedImage image) {
        BufferedImage prepared = prepareMonochromeLogo(image, 384, 120);
        int width = prepared.getWidth();
        int height = prepared.getHeight();
        int bytesPerRow = (width + 7) / 8;
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
        return ((color.getRed() * 0.299) + (color.getGreen() * 0.587) + (color.getBlue() * 0.114)) < 160;
    }
}
