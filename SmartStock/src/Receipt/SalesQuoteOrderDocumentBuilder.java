package Receipt;

import data.DB;
import managers.CompanyCustomizationManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SalesQuoteOrderDocumentBuilder {
    private static final int WIDTH = 92;
    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(Locale.US);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private SalesQuoteOrderDocumentBuilder() {
    }

    public static String buildQuote(long quoteId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            CompanyCustomizationManager.ReceiptSettings settings = CompanyCustomizationManager.loadReceiptSettings();
            CompanyCustomizationManager.SalesQuoteOrderPrintSettings printSettings = CompanyCustomizationManager.loadSalesQuoteOrderPrintSettings();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT quote_number, customer_name, customer_phone, customer_email,
                           status, issue_date, valid_until, quote_notes,
                           subtotal_amount, discount_amount, vat_amount, vat_rate_percent, vat_mode, total_amount,
                           location_name, created_by_name
                    FROM sales_quotes
                    WHERE sales_quote_id = ?
                    """)) {
                ps.setLong(1, quoteId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Quote not found.");
                    }
                    return buildQuoteHtml(conn, quoteId, rs, settings, printSettings);
                }
            }
        }
    }

    public static String buildOrder(long orderId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            CompanyCustomizationManager.ReceiptSettings settings = CompanyCustomizationManager.loadReceiptSettings();
            CompanyCustomizationManager.SalesQuoteOrderPrintSettings printSettings = CompanyCustomizationManager.loadSalesQuoteOrderPrintSettings();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT order_number, quote_number, customer_name, customer_phone, customer_email,
                           status, order_date, order_notes, subtotal_amount, discount_amount, vat_amount,
                           total_amount, amount_paid, balance_due, payment_status, vat_rate_percent, vat_mode, location_name, created_by_name
                    FROM sales_orders
                    WHERE sales_order_id = ?
                    """)) {
                ps.setLong(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Sales order not found.");
                    }
                    return buildOrderHtml(conn, orderId, rs, settings, printSettings);
                }
            }
        }
    }

    public static String buildDelivery(long deliveryEventId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            CompanyCustomizationManager.ReceiptSettings settings = CompanyCustomizationManager.loadReceiptSettings();
            CompanyCustomizationManager.SalesQuoteOrderPrintSettings printSettings = CompanyCustomizationManager.loadSalesQuoteOrderPrintSettings();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT de.sales_order_id, de.delivery_number, de.delivery_method, de.receiver_name,
                           de.delivery_notes, de.remaining_balance, de.delivered_by_name, de.created_at,
                           so.order_number, so.customer_name, so.customer_phone, so.balance_due
                    FROM sales_order_delivery_events de
                    JOIN sales_orders so ON so.sales_order_id = de.sales_order_id
                    WHERE de.sales_order_delivery_event_id = ?
                    """)) {
                ps.setLong(1, deliveryEventId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Delivery bill not found.");
                    }
                    return buildDeliveryHtml(conn, deliveryEventId, rs, settings, printSettings);
                }
            }
        }
    }

    private static String buildQuoteHtml(Connection conn, long quoteId, ResultSet header,
                                         CompanyCustomizationManager.ReceiptSettings receiptSettings,
                                         CompanyCustomizationManager.SalesQuoteOrderPrintSettings printSettings) throws SQLException {
        List<DocumentLine> lines = quoteLines(conn, quoteId);
        StringBuilder html = htmlStart();
        appendHtmlHeader(html, receiptSettings, printSettings.quoteTitle(), header.getString("quote_number"), date(header, "issue_date"), "1");
        appendDocumentCustomerInfo(html,
                new String[][]{{"Quote #", header.getString("quote_number")}, {"Status", header.getString("status")}, {"Issue Date", date(header, "issue_date")}, {"Valid Until", date(header, "valid_until")}},
                header.getString("customer_name"), header.getString("customer_phone"), header.getString("customer_email"));
        appendBillTo(html, header.getString("customer_name"));
        appendLineTable(html, lines, false);
        appendValidity(html, printSettings.quoteValidityNote());
        appendTotalsAndSignatures(html, "GRAND TOTAL", header.getBigDecimal("total_amount"), printSettings, false, null);
        return html.append("</div></div></body></html>").toString();
    }

    private static String buildOrderHtml(Connection conn, long orderId, ResultSet header,
                                         CompanyCustomizationManager.ReceiptSettings receiptSettings,
                                         CompanyCustomizationManager.SalesQuoteOrderPrintSettings printSettings) throws SQLException {
        List<DocumentLine> lines = orderLines(conn, orderId);
        StringBuilder html = htmlStart();
        appendHtmlHeader(html, receiptSettings, printSettings.orderTitle(), header.getString("order_number"), date(header, "order_date"), "1");
        appendDocumentCustomerInfo(html,
                new String[][]{{"Order #", header.getString("order_number")}, {"Quote #", header.getString("quote_number")}, {"Status", header.getString("status")}, {"Order Date", date(header, "order_date")}},
                header.getString("customer_name"), header.getString("customer_phone"), header.getString("customer_email"));
        appendBillTo(html, header.getString("customer_name"));
        appendLineTable(html, lines, true);
        appendOrderBalance(html, header.getBigDecimal("amount_paid"), header.getBigDecimal("balance_due"), header.getString("payment_status"));
        appendTotalsAndSignatures(html, "GRAND TOTAL", header.getBigDecimal("total_amount"), printSettings, false, null);
        return html.append("</div></div></body></html>").toString();
    }

    private static String buildDeliveryHtml(Connection conn, long deliveryEventId, ResultSet header,
                                            CompanyCustomizationManager.ReceiptSettings receiptSettings,
                                            CompanyCustomizationManager.SalesQuoteOrderPrintSettings printSettings) throws SQLException {
        List<DocumentLine> lines = deliveryLines(conn, deliveryEventId);
        StringBuilder html = htmlStart();
        appendHtmlHeader(html, receiptSettings, printSettings.deliveryTitle(), header.getString("delivery_number"), clean(header.getString("created_at")), "1");
        appendDocumentCustomerInfo(html,
                new String[][]{{"Delivery #", header.getString("delivery_number")}, {"Order #", header.getString("order_number")}, {"Method", header.getString("delivery_method")}, {"Delivered At", clean(header.getString("created_at"))}},
                header.getString("customer_name"), header.getString("customer_phone"), header.getString("receiver_name"));
        appendBillTo(html, header.getString("customer_name"));
        appendLineTable(html, lines, true);
        appendTotalsAndSignatures(html, "REMAINING BALANCE", header.getBigDecimal("balance_due"), printSettings, true, header.getString("receiver_name"));
        return html.append("</div></div></body></html>").toString();
    }

    private static List<DocumentLine> quoteLines(Connection conn, long quoteId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT item_name, quantity, 0 AS delivered_now, quantity AS remaining, unit_price, line_total
                FROM sales_quote_lines
                WHERE sales_quote_id = ?
                ORDER BY sort_order, sales_quote_line_id
                """)) {
            ps.setLong(1, quoteId);
            return documentLines(ps, false);
        }
    }

    private static List<DocumentLine> orderLines(Connection conn, long orderId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT item_name, quantity_ordered AS quantity, quantity_delivered AS delivered_now,
                       quantity_ordered - quantity_delivered AS remaining, unit_price, line_total
                FROM sales_order_lines
                WHERE sales_order_id = ?
                ORDER BY sort_order, sales_order_line_id
                """)) {
            ps.setLong(1, orderId);
            return documentLines(ps, true);
        }
    }

    private static List<DocumentLine> deliveryLines(Connection conn, long deliveryEventId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT sol.item_name, sol.quantity_ordered AS quantity, dl.quantity_delivered AS delivered_now,
                       dl.quantity_remaining AS remaining, sol.unit_price, sol.line_total
                FROM sales_order_delivery_lines dl
                JOIN sales_order_lines sol ON sol.sales_order_line_id = dl.sales_order_line_id
                WHERE dl.sales_order_delivery_event_id = ?
                ORDER BY dl.sales_order_delivery_line_id
                """)) {
            ps.setLong(1, deliveryEventId);
            return documentLines(ps, true);
        }
    }

    private static List<DocumentLine> documentLines(PreparedStatement ps, boolean includeDelivery) throws SQLException {
        List<DocumentLine> lines = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lines.add(new DocumentLine(
                        rs.getInt("quantity"),
                        includeDelivery ? rs.getInt("delivered_now") : null,
                        includeDelivery ? rs.getInt("remaining") : null,
                        rs.getString("item_name"),
                        rs.getBigDecimal("unit_price"),
                        rs.getBigDecimal("line_total")
                ));
            }
        }
        return lines;
    }

    private static StringBuilder htmlStart() {
        return new StringBuilder("""
                <html><head><style>
                body { font-family: Arial, sans-serif; margin: 0; background: #f3f4f6; color: #111; }
                .page { width: 7.5in; min-height: 10in; margin: 18px auto; padding: .35in; background: white; box-sizing: border-box; display: flex; flex-direction: column; }
                .document-body { flex: 1 1 auto; }
                .document-bottom { margin-top: auto; padding-top: 18px; }
                table { border-collapse: collapse; width: 100%; }
                td, th { border: 2px solid #111; padding: 5px 6px; font-size: 12px; }
                th { background: #d7d7d7; font-weight: bold; text-align: center; }
                .top td { border: 0; }
                .logo { font-size: 34px; color: #f05a00; font-weight: bold; font-style: italic; text-align: center; }
                .tagline { font-size: 13px; font-weight: bold; font-style: italic; text-align: center; padding-top: 4px; }
                .doctype { font-size: 24px; color: #777; font-weight: bold; text-align: right; }
                .docmeta { font-size: 14px; font-weight: bold; text-align: right; }
                .contact td { border: 0; font-weight: bold; font-size: 12px; padding: 2px 4px; }
                .info td { font-size: 12px; border: 2px solid #111; vertical-align: top; }
                .info-label { font-weight: bold; color: #333; display: inline-block; min-width: 76px; }
                .bill-label { width: 15%; background: #d7d7d7; font-weight: bold; }
                .bill-name { font-size: 18px; font-weight: bold; }
                .num { text-align: right; white-space: nowrap; }
                .center { text-align: center; }
                .description { font-weight: bold; }
                .blank td { height: 20px; }
                .note { border: 2px solid #111; font-size: 12px; font-weight: bold; font-style: italic; padding: 6px 8px; }
                .signature-row td { height: 42px; vertical-align: bottom; }
                .total-label { font-weight: bold; font-size: 14px; text-align: center; }
                .total-amount { font-weight: bold; font-size: 15px; text-align: right; }
                .signature-label { font-weight: bold; width: 18%; }
                </style></head><body><div class="page"><div class="document-body">
                """);
    }

    private static void appendHtmlHeader(StringBuilder html, CompanyCustomizationManager.ReceiptSettings settings,
                                         String documentTitle, String documentNumber, String date, String page) {
        html.append("<table class='top'><tr><td style='width:70%'>");
        if (settings.showLogo() && !settings.logoPath().isBlank()) {
            html.append("<div class='logo'><img style='max-height:92px; max-width:330px;' src='").append(escAttr(imageSrc(settings.logoPath()))).append("'></div>");
        } else {
            html.append("<div class='logo'>").append(esc(settings.companyName())).append("</div>");
        }
        if (!settings.headerLine().isBlank()) {
            html.append("<div class='tagline'>").append(esc(settings.headerLine())).append("</div>");
        }
        html.append("</td><td style='width:30%; vertical-align:top;'>")
                .append("<div class='doctype'>").append(esc(documentTitle)).append("</div>")
                .append("<div class='docmeta'>#&nbsp;&nbsp;").append(esc(documentNumber)).append("</div>")
                .append("<div class='docmeta'>Date:&nbsp;&nbsp;").append(esc(date)).append("</div>")
                .append("<div class='docmeta'>Page:&nbsp;&nbsp;").append(esc(page)).append("</div>")
                .append("</td></tr></table>");
        appendContactBlock(html, settings);
    }

    private static void appendContactBlock(StringBuilder html, CompanyCustomizationManager.ReceiptSettings settings) {
        String address1 = fallback(settings.addressLine1(), "Lot 1 & 2 #81 Skeldon,");
        String address2 = fallback(settings.addressLine2(), "Corriverton,");
        String address3 = fallback(settings.addressLine3(), "Berbice, Guyana");
        String phone1 = fallback(settings.phoneLine1(), "(592) 643-2323     (592) 339-3200");
        String phone2 = fallback(settings.phoneLine2(), "(592) 638-4002     (592) 622-5093");
        String email1 = fallback(settings.emailLine1(), "deckershcn@yahoo.com");
        String email2 = fallback(settings.emailLine2(), "deckershcn@gmail.com");
        html.append("<table class='contact'>")
                .append("<tr><td>").append(esc(address1)).append("</td><td>").append(esc(phone1)).append("</td><td>").append(esc(email1)).append("</td></tr>")
                .append("<tr><td>").append(esc(address2)).append("</td><td>").append(esc(phone2)).append("</td><td>").append(esc(email2)).append("</td></tr>")
                .append("<tr><td>").append(esc(address3)).append("</td><td></td><td></td></tr>")
                .append("</table>");
    }

    private static void appendBillTo(StringBuilder html, String customerName) {
        html.append("<table><tr><td class='bill-label'>Bill To:</td><td class='bill-name'>")
                .append(esc(customerName))
                .append("</td></tr></table>");
    }

    private static void appendDocumentCustomerInfo(StringBuilder html, String[][] docFields,
                                                   String customerName, String customerPhone, String customerEmailOrReceiver) {
        html.append("<table class='info'><tr><td style='width:50%'>");
        for (String[] field : docFields) {
            if (!clean(field[1]).isBlank()) {
                html.append("<div><span class='info-label'>").append(esc(field[0])).append(":</span> ")
                        .append(esc(field[1])).append("</div>");
            }
        }
        html.append("</td><td style='width:50%'>")
                .append("<div><span class='info-label'>Customer:</span> ").append(esc(customerName)).append("</div>");
        if (!clean(customerPhone).isBlank()) {
            html.append("<div><span class='info-label'>Phone:</span> ").append(esc(customerPhone)).append("</div>");
        }
        if (!clean(customerEmailOrReceiver).isBlank()) {
            html.append("<div><span class='info-label'>Email/Receiver:</span> ").append(esc(customerEmailOrReceiver)).append("</div>");
        }
        html.append("</td></tr></table>");
    }

    private static void appendLineTable(StringBuilder html, List<DocumentLine> lines, boolean includeDelivery) {
        html.append("<table><tr><th style='width:12%'>Quantity</th>");
        if (includeDelivery) {
            html.append("<th style='width:12%'>Delivered</th><th style='width:12%'>Remaining</th>");
        }
        html.append("<th>Description</th><th style='width:16%'>Unit Price</th><th style='width:16%'>Amount</th></tr>");
        int rows = 0;
        for (DocumentLine line : lines) {
            rows++;
            html.append("<tr><td class='center'>").append(line.quantity()).append("</td>");
            if (includeDelivery) {
                html.append("<td class='center'>").append(line.deliveredNow() == null ? "" : line.deliveredNow()).append("</td>")
                        .append("<td class='center'>").append(line.remaining() == null ? "" : line.remaining()).append("</td>");
            }
            html.append("<td class='description'>").append(esc(line.description())).append("</td>")
                    .append("<td class='num'>").append(esc(money(line.unitPrice()))).append("</td>")
                    .append("<td class='num'>").append(esc(money(line.amount()))).append("</td></tr>");
        }
        int blankRows = Math.max(12 - rows, 2);
        int columns = includeDelivery ? 6 : 4;
        for (int i = 0; i < blankRows; i++) {
            html.append("<tr class='blank'>");
            for (int c = 0; c < columns; c++) {
                html.append("<td>&nbsp;</td>");
            }
            html.append("</tr>");
        }
        html.append("</table>");
    }

    private static void appendValidity(StringBuilder html, String validityNote) {
        if (!clean(validityNote).isBlank()) {
            beginBottom(html);
            html.append("<div class='note'>").append(esc(validityNote)).append("</div>");
        }
    }

    private static void appendOrderBalance(StringBuilder html, BigDecimal paid, BigDecimal balance, String status) {
        beginBottom(html);
        html.append("<div class='note'>Paid: ").append(esc(money(paid)))
                .append("&nbsp;&nbsp;&nbsp; Balance Due: ").append(esc(money(balance)))
                .append("&nbsp;&nbsp;&nbsp; Status: ").append(esc(status)).append("</div>");
    }

    private static void appendTotalsAndSignatures(StringBuilder html, String totalLabel, BigDecimal total,
                                                  CompanyCustomizationManager.SalesQuoteOrderPrintSettings settings,
                                                  boolean receiverLabel, String receiverName) {
        beginBottom(html);
        if (!settings.footerNote().isBlank()) {
            html.append("<div class='note'>").append(esc(settings.footerNote())).append("</div>");
        }
        html.append("<table><tr class='signature-row'>");
        if (settings.showSignatures()) {
            html.append("<td class='signature-label'>Delivered By:</td><td></td>");
        } else {
            html.append("<td></td>");
        }
        html.append("<td class='total-label' rowspan='2'>").append(esc(totalLabel)).append("</td>")
                .append("<td class='total-amount' rowspan='2'>").append(esc(money(total))).append("</td></tr><tr class='signature-row'>");
        if (settings.showSignatures()) {
            html.append("<td class='signature-label'>").append(receiverLabel ? "Received By:" : "Approved By:").append("</td><td>")
                    .append(esc(clean(receiverName))).append("</td>");
        } else {
            html.append("<td></td>");
        }
        html.append("</tr></table>");
    }

    private static void beginBottom(StringBuilder html) {
        if (html.indexOf("<!--BOTTOM-->") < 0) {
            html.append("</div><!--BOTTOM--><div class='document-bottom'>");
        }
    }

    private static String imageSrc(String path) {
        if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("file:")) {
            return path;
        }
        return new java.io.File(path).toURI().toString();
    }

    private static String fallback(String value, String fallback) {
        String clean = clean(value);
        return clean.isBlank() ? fallback : clean;
    }

    private static String esc(String value) {
        return clean(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String escAttr(String value) {
        return esc(value).replace("'", "&#39;");
    }

    private record DocumentLine(int quantity, Integer deliveredNow, Integer remaining,
                                String description, BigDecimal unitPrice, BigDecimal amount) {
    }

    private static void appendQuoteLines(StringBuilder out, Connection conn, long quoteId) throws SQLException {
        appendWrapped(out, "Lines");
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT item_name, sku, quantity, unit_price, discount_percent, discount_amount,
                       vat_rate_percent, vat_amount, line_total, delivery_method, line_notes
                FROM sales_quote_lines
                WHERE sales_quote_id = ?
                ORDER BY sort_order, sales_quote_line_id
                """)) {
            ps.setLong(1, quoteId);
            appendLineRows(out, ps, "quantity");
        }
    }

    private static void appendOrderLines(StringBuilder out, Connection conn, long orderId) throws SQLException {
        appendWrapped(out, "Lines");
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT item_name, sku, quantity_ordered AS quantity, quantity_delivered,
                       unit_price, discount_percent, discount_amount, vat_rate_percent, vat_amount,
                       line_total, delivery_method, line_notes
                FROM sales_order_lines
                WHERE sales_order_id = ?
                ORDER BY sort_order, sales_order_line_id
                """)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    appendWrapped(out, rs.getString("item_name") + "  SKU: " + clean(rs.getString("sku")));
                    appendWrapped(out, "  Qty ordered: " + rs.getInt("quantity") + "  Delivered: " + rs.getInt("quantity_delivered")
                            + "  Method: " + clean(rs.getString("delivery_method"))
                            + "  Unit: " + money(rs.getBigDecimal("unit_price"))
                            + "  Discount: " + money(rs.getBigDecimal("discount_amount"))
                            + "  VAT: " + money(rs.getBigDecimal("vat_amount"))
                            + "  Total: " + money(rs.getBigDecimal("line_total")));
                    if (!clean(rs.getString("line_notes")).isBlank()) {
                        appendWrapped(out, "  Notes: " + rs.getString("line_notes"));
                    }
                }
            }
        }
    }

    private static void appendDeliveryLines(StringBuilder out, Connection conn, long deliveryEventId) throws SQLException {
        appendWrapped(out, "Delivered Items");
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT item_name, quantity_delivered, quantity_remaining
                FROM sales_order_delivery_lines
                WHERE sales_order_delivery_event_id = ?
                ORDER BY sales_order_delivery_line_id
                """)) {
            ps.setLong(1, deliveryEventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    appendWrapped(out, rs.getString("item_name")
                            + "  Delivered: " + rs.getInt("quantity_delivered")
                            + "  Remaining: " + rs.getInt("quantity_remaining"));
                }
            }
        }
    }

    private static void appendRemainingOrderLines(StringBuilder out, Connection conn, long orderId) throws SQLException {
        appendWrapped(out, "Remaining Items");
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT item_name, quantity_ordered - quantity_delivered AS remaining
                FROM sales_order_lines
                WHERE sales_order_id = ?
                  AND quantity_ordered > quantity_delivered
                ORDER BY sort_order, sales_order_line_id
                """)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    appendWrapped(out, rs.getString("item_name") + "  Remaining: " + rs.getInt("remaining"));
                }
                if (!any) {
                    appendWrapped(out, "All order lines have been delivered.");
                }
            }
        }
    }

    private static void appendLineRows(StringBuilder out, PreparedStatement ps, String quantityColumn) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                appendWrapped(out, rs.getString("item_name") + "  SKU: " + clean(rs.getString("sku")));
                appendWrapped(out, "  Qty: " + rs.getInt(quantityColumn)
                        + "  Method: " + clean(rs.getString("delivery_method"))
                        + "  Unit: " + money(rs.getBigDecimal("unit_price"))
                        + "  Discount: " + money(rs.getBigDecimal("discount_amount"))
                        + "  VAT: " + money(rs.getBigDecimal("vat_amount"))
                        + "  Total: " + money(rs.getBigDecimal("line_total")));
                if (!clean(rs.getString("line_notes")).isBlank()) {
                    appendWrapped(out, "  Notes: " + rs.getString("line_notes"));
                }
            }
        }
    }

    private static void appendLetterhead(StringBuilder out, CompanyCustomizationManager.ReceiptSettings settings, String title) {
        appendCentered(out, settings.companyName());
        if (!settings.headerLine().isBlank()) {
            appendCentered(out, settings.headerLine());
        }
        appendCentered(out, title);
        appendRule(out);
    }

    private static void appendFooterAndSignatures(StringBuilder out, CompanyCustomizationManager.SalesQuoteOrderPrintSettings settings) {
        if (!settings.footerNote().isBlank()) {
            appendRule(out);
            appendWrapped(out, settings.footerNote());
        }
        if (settings.showSignatures()) {
            appendSignatures(out);
        }
    }

    private static void appendSignatures(StringBuilder out) {
        appendRule(out);
        out.append("Employee Signature: ").append("_".repeat(54)).append('\n').append('\n');
        out.append("Customer / Receiver Signature: ").append("_".repeat(42)).append('\n');
    }

    private static void appendField(StringBuilder out, String label, String value) {
        appendWrapped(out, label + ": " + clean(value));
    }

    private static void appendMoney(StringBuilder out, String label, BigDecimal value) {
        appendWrapped(out, label + ": " + money(value));
    }

    private static void appendRule(StringBuilder out) {
        out.append("-".repeat(WIDTH)).append('\n');
    }

    private static void appendCentered(StringBuilder out, String value) {
        String text = trim(clean(value), WIDTH);
        int left = Math.max((WIDTH - text.length()) / 2, 0);
        out.append(" ".repeat(left)).append(text).append('\n');
    }

    private static void appendWrapped(StringBuilder out, String value) {
        String text = clean(value);
        if (text.isBlank()) {
            return;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (candidate.length() <= WIDTH) {
                line = new StringBuilder(candidate);
            } else {
                out.append(trim(line.toString(), WIDTH)).append('\n');
                line = new StringBuilder(word);
            }
        }
        if (!line.isEmpty()) {
            out.append(trim(line.toString(), WIDTH)).append('\n');
        }
    }

    private static String date(ResultSet rs, String column) throws SQLException {
        java.sql.Date date = rs.getDate(column);
        return date == null ? "" : DATE.format(date.toLocalDate());
    }

    private static String vatLabel(ResultSet rs) throws SQLException {
        String mode = clean(rs.getString("vat_mode"));
        BigDecimal rate = rs.getBigDecimal("vat_rate_percent");
        if (mode.isBlank()) {
            return "Off";
        }
        return mode + " / " + (rate == null ? BigDecimal.ZERO : rate).stripTrailingZeros().toPlainString() + "%";
    }

    private static String money(BigDecimal value) {
        return MONEY.format(value == null ? BigDecimal.ZERO : value);
    }

    private static String trim(String value, int width) {
        return value.length() <= width ? value : value.substring(0, width);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
