package Receipt;

import utils.CurrencyFormatter;
import managers.CompanyCustomizationManager;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CustomOrderSlipFormatter {
    private static final int RECEIPT_WIDTH = 40;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final NumberFormat MONEY_FORMAT = CurrencyFormatter.create(Locale.US);

    private CustomOrderSlipFormatter() {
    }

    public static String format40Column(CustomOrderSlipData data,
                                        CompanyCustomizationManager.ReceiptSettings receiptSettings,
                                        CompanyCustomizationManager.CustomOrderSlipSettings slipSettings) {
        StringBuilder builder = new StringBuilder();
        appendCentered(builder, receiptSettings.companyName(), RECEIPT_WIDTH);
        appendCentered(builder, slipSettings.title(), RECEIPT_WIDTH);
        if (!slipSettings.contactLine().isBlank()) {
            appendCentered(builder, slipSettings.contactLine(), RECEIPT_WIDTH);
        }
        if (!slipSettings.emailLine().isBlank()) {
            appendCentered(builder, slipSettings.emailLine(), RECEIPT_WIDTH);
        }
        appendRule(builder);

        appendField(builder, "CUSTOMER", data.customerName());
        if (slipSettings.showCustomerPhone()) {
            appendField(builder, "PHONE", data.customerPhone());
        }
        if (slipSettings.showCustomerAccount()) {
            appendField(builder, "ACCOUNT", data.customerAccountNumber());
        }
        appendField(builder, "DATE", createdDate(data));
        if (slipSettings.showDueDate() && data.dueDate() != null) {
            appendField(builder, "DUE", DATE_FORMAT.format(data.dueDate()));
        }
        if (slipSettings.showOrderNumber()) {
            appendField(builder, "ORDER", data.orderNumber());
        }
        if (slipSettings.showStore()) {
            appendField(builder, "STORE", data.locationName());
        }
        if (slipSettings.showCashier()) {
            appendField(builder, "CASHIER", data.takenByName());
        }
        if (slipSettings.showDevice()) {
            appendField(builder, "DEVICE", data.deviceName());
        }
        appendRule(builder);

        builder.append("DETAILS").append('\n');
        if (slipSettings.showLineItems()) {
            int index = 1;
            for (CustomOrderSlipData.Line line : data.lines()) {
                String item = index++ + ". " + clean(line.itemName());
                if (!clean(line.variantName()).isBlank()) {
                    item += " / " + clean(line.variantName());
                }
                if (slipSettings.showPricing()) {
                    item += " " + money(line.lineTotal());
                }
                appendWrapped(builder, item, RECEIPT_WIDTH);
                appendWrapped(builder, indent(joinNonBlank(line.details(), line.instructions())), RECEIPT_WIDTH);
            }
        }
        if (!clean(data.orderNotes()).isBlank()) {
            appendWrapped(builder, "Notes: " + clean(data.orderNotes()), RECEIPT_WIDTH);
        }
        appendRule(builder);

        if (slipSettings.showTakenBy()) {
            appendField(builder, "DELIVERED BY", data.takenByName());
            appendField(builder, "RECEIVED BY", "");
        }
        if (slipSettings.showPaymentSummary()) {
            appendField(builder, "PAYMENT", clean(data.paymentMethod()) + " / " + clean(data.paymentStatus()));
            appendPair(builder, "AMT", money(data.amountPaid()), "BAL", money(data.balanceDue()));
            appendField(builder, "TOTAL", money(data.totalAmount()));
        }
        if (slipSettings.showPaymentReference() && !clean(data.paymentReference()).isBlank()) {
            appendField(builder, "PAY REF", data.paymentReference());
        }
        if (slipSettings.showSignatures()) {
            appendSignature(builder, "TEAM MEMBER SIGNATURE");
            appendSignature(builder, "CUSTOMER SIGNATURE");
        }
        appendRule(builder);
        appendWrapped(builder, slipSettings.footerNote(), RECEIPT_WIDTH);
        builder.append('\n');
        return builder.toString();
    }

    public static byte[] formatEscPos40Column(CustomOrderSlipData data,
                                              CompanyCustomizationManager.ReceiptSettings receiptSettings,
                                              CompanyCustomizationManager.CustomOrderSlipSettings slipSettings) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, 0x1B, 0x40);
        write(out, 0x1B, 0x4D, 0x00);
        write(out, 0x1D, 0x21, 0x00);
        writeAscii(out, format40Column(data, receiptSettings, slipSettings));
        writeAscii(out, "\n\n\n");
        write(out, 0x1D, 0x56, 0x42, 0x00);
        return out.toByteArray();
    }

    private static void appendField(StringBuilder builder, String label, String value) {
        String prefix = label + ": ";
        List<String> lines = wrap(clean(value), RECEIPT_WIDTH - prefix.length());
        if (lines.isEmpty()) {
            builder.append(prefix).append(repeat("_", Math.max(RECEIPT_WIDTH - prefix.length(), 0))).append('\n');
            return;
        }
        builder.append(prefix).append(lines.get(0)).append('\n');
        for (int i = 1; i < lines.size(); i++) {
            builder.append(" ".repeat(Math.min(prefix.length(), RECEIPT_WIDTH))).append(lines.get(i)).append('\n');
        }
    }

    private static void appendPair(StringBuilder builder, String leftLabel, String leftValue, String rightLabel, String rightValue) {
        String left = leftLabel + ": " + clean(leftValue);
        String right = rightLabel + ": " + clean(rightValue);
        int spaces = RECEIPT_WIDTH - left.length() - right.length();
        if (spaces < 1) {
            appendWrapped(builder, left + "  " + right, RECEIPT_WIDTH);
        } else {
            builder.append(left).append(" ".repeat(spaces)).append(right).append('\n');
        }
    }

    private static void appendSignature(StringBuilder builder, String label) {
        appendWrapped(builder, label + ":", RECEIPT_WIDTH);
        builder.append(repeat("_", RECEIPT_WIDTH)).append('\n');
    }

    private static void appendRule(StringBuilder builder) {
        builder.append(repeat("-", RECEIPT_WIDTH)).append('\n');
    }

    private static void appendCentered(StringBuilder builder, String value, int width) {
        String text = trimToWidth(clean(value), width);
        int padding = Math.max((width - text.length()) / 2, 0);
        builder.append(" ".repeat(padding)).append(text).append('\n');
    }

    private static void appendWrapped(StringBuilder builder, String value, int width) {
        for (String line : wrap(value, width)) {
            builder.append(trimToWidth(line, width)).append('\n');
        }
    }

    private static List<String> wrap(String value, int width) {
        List<String> lines = new ArrayList<>();
        String text = clean(value);
        if (text.isBlank() || width <= 0) {
            return lines;
        }
        for (String rawLine : text.split("\\R")) {
            StringBuilder line = new StringBuilder();
            for (String word : rawLine.trim().split("\\s+")) {
                if (word.length() > width) {
                    if (!line.isEmpty()) {
                        lines.add(line.toString());
                        line = new StringBuilder();
                    }
                    for (int i = 0; i < word.length(); i += width) {
                        lines.add(word.substring(i, Math.min(i + width, word.length())));
                    }
                    continue;
                }
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (candidate.length() <= width) {
                    line = new StringBuilder(candidate);
                } else {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                }
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
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

    private static String indent(String value) {
        return clean(value).isBlank() ? "" : "  " + clean(value);
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

    private static String trimToWidth(String value, int width) {
        String text = clean(value);
        return text.length() <= width ? text : text.substring(0, width);
    }

    private static String repeat(String value, int width) {
        return value.repeat(Math.max(width, 0));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
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
}
