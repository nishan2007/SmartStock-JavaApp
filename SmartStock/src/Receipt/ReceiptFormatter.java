package Receipt;

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

    public static String formatLetterText(ReceiptData receipt) {
        return formatColumnText(receipt, LETTER_WIDTH);
    }

    private static String formatColumnText(ReceiptData receipt, int width) {
        StringBuilder builder = new StringBuilder();
        appendCentered(builder, "SmartStock", width);
        appendCentered(builder, emptyDefault(receipt.getStoreName(), "Store"), width);
        builder.append(repeat("-", width)).append('\n');
        appendPair(builder, "Receipt", receipt.getReceiptNumber(), width);
        appendPair(builder, "Sale ID", String.valueOf(receipt.getSaleId()), width);
        appendPair(builder, "Date", receipt.getSaleTime() == null ? "" : TIME_FORMAT.format(receipt.getSaleTime().toLocalDateTime()), width);
        appendPair(builder, "Cashier", receipt.getCashierName(), width);
        appendPair(builder, "Device", receipt.getDeviceId(), width);
        if (!receipt.getCustomerName().isBlank()) {
            appendPair(builder, "Customer", receipt.getCustomerName(), width);
        }
        if (!receipt.getAccountNumber().isBlank()) {
            appendPair(builder, "Account", receipt.getAccountNumber(), width);
        }
        builder.append(repeat("-", width)).append('\n');

        for (ReceiptItem item : receipt.getItems()) {
            builder.append(trimToWidth(item.getName(), width)).append('\n');
            if (!item.getSku().isBlank()) {
                builder.append("  SKU: ").append(trimToWidth(item.getSku(), width - 7)).append('\n');
            }
            String qtyPrice = "  " + item.getQuantity() + " x " + money(item.getFinalUnitPrice());
            appendPair(builder, qtyPrice, money(item.getLineTotal()), width);
            if (item.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
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
        appendPair(builder, "Status", receipt.getPaymentStatus(), width);
        appendPair(builder, "Paid", money(receipt.getAmountPaid()), width);
        if (receipt.getCashCollected() != null) {
            appendPair(builder, "Cash Collected", money(receipt.getCashCollected()), width);
            appendPair(builder, "Change Due", money(receipt.getChangeDue()), width);
        }
        if (receipt.getReturnedAmount().compareTo(BigDecimal.ZERO) > 0) {
            appendPair(builder, "Returned", money(receipt.getReturnedAmount()), width);
        }
        builder.append(repeat("-", width)).append('\n');
        appendCentered(builder, "Thank you", width);
        builder.append('\n');
        return builder.toString();
    }

    public static byte[] formatEscPos(ReceiptData receipt) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, 0x1B, 0x40);
        write(out, 0x1B, 0x4D, 0x00);
        write(out, 0x1D, 0x21, 0x00);
        write(out, 0x1B, 0x61, 0x00);
        writeAscii(out, formatText(receipt));
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
}
