package Receipt;

import managers.CompanyCustomizationManager;
import utils.CurrencyFormatter;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;

public final class ReturnReceiptFormatter {
    private static final int RECEIPT_WIDTH = 40;
    private static final int LETTER_WIDTH = 86;
    private static final NumberFormat CURRENCY = CurrencyFormatter.create();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a").withZone(ZoneId.systemDefault());

    private ReturnReceiptFormatter() { }

    public static String formatText(ReturnReceiptData receipt, CompanyCustomizationManager.ReceiptSettings settings) {
        return format(receipt, settings, RECEIPT_WIDTH, false);
    }

    public static String formatText(ReturnReceiptData receipt, CompanyCustomizationManager.ReceiptSettings settings, boolean reprint) {
        return format(receipt, settings, RECEIPT_WIDTH, reprint);
    }

    public static String formatLetterText(ReturnReceiptData receipt, CompanyCustomizationManager.ReceiptSettings settings) {
        return format(receipt, settings, LETTER_WIDTH, false);
    }

    public static String formatLetterText(ReturnReceiptData receipt, CompanyCustomizationManager.ReceiptSettings settings, boolean reprint) {
        return format(receipt, settings, LETTER_WIDTH, reprint);
    }

    private static String format(ReturnReceiptData receipt, CompanyCustomizationManager.ReceiptSettings settings, int width, boolean reprint) {
        StringBuilder out = new StringBuilder();
        center(out, settings.companyName(), width);
        center(out, "RETURN RECEIPT", width);
        if (reprint) center(out, "DUPLICATE / REPRINT", width);
        if (!settings.headerLine().isBlank()) center(out, settings.headerLine(), width);
        rule(out, width);
        pair(out, "Return Receipt", receipt.returnReceiptNumber(), width);
        pair(out, "Return ID", String.valueOf(receipt.returnId()), width);
        pair(out, "Original Receipt", receipt.originalReceiptNumber(), width);
        if (settings.showSaleId()) pair(out, "Sale ID", String.valueOf(receipt.saleId()), width);
        pair(out, "Date", receipt.returnTime() == null ? "" : TIME.format(receipt.returnTime()), width);
        pair(out, "Cashier", receipt.cashierName(), width);
        rule(out, width);
        for (ReceiptItem item : receipt.items()) {
            out.append(trim(item.getName(), width)).append('\n');
            if (settings.showSku() && !item.getSku().isBlank()) out.append("  SKU: ").append(trim(item.getSku(), width - 7)).append('\n');
            pair(out, "  " + item.getQuantity() + " x " + money(item.getFinalUnitPrice()), money(item.getLineTotal()), width);
        }
        rule(out, width);
        pair(out, "Refund Total", money(receipt.refundAmount()), width);
        pair(out, "Refund Method", receipt.refundMethod(), width);
        if (!receipt.reason().isBlank()) pair(out, "Reason", receipt.reason(), width);
        rule(out, width);
        center(out, settings.footerLine(), width);
        out.append('\n');
        return out.toString();
    }

    private static String money(BigDecimal value) { return CURRENCY.format(value == null ? BigDecimal.ZERO : value); }
    private static void rule(StringBuilder out, int width) { out.append("-".repeat(width)).append('\n'); }
    private static void center(StringBuilder out, String value, int width) { for(String line:(value==null?"":value).replace("\r\n","\n").replace('\r','\n').split("\n",-1)){String text=trim(line,width);out.append(" ".repeat(Math.max((width-text.length())/2,0))).append(text).append('\n');} }
    private static void pair(StringBuilder out,String label,String value,int width){label=label==null?"":label;value=value==null?"":value;int spaces=width-label.length()-value.length();if(spaces<1)out.append(trim(label,Math.max(width-value.length()-1,1))).append(' ').append(trim(value,width-1)).append('\n');else out.append(label).append(" ".repeat(spaces)).append(value).append('\n');}
    private static String trim(String value,int width){String text=value==null?"":value;return text.length()<=width?text:text.substring(0,Math.max(width,0));}
}
