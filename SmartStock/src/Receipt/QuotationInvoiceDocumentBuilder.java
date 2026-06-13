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

public final class QuotationInvoiceDocumentBuilder {
    private static final int WIDTH = 92;
    private static final int ROWS_PER_PAGE = 14;
    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(Locale.US);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private QuotationInvoiceDocumentBuilder() {
    }

    public static String buildQuotation(long quotationId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            CompanyCustomizationManager.ReceiptSettings settings = CompanyCustomizationManager.loadReceiptSettings();
            CompanyCustomizationManager.QuotationInvoicePrintSettings printSettings = CompanyCustomizationManager.loadQuotationInvoicePrintSettings();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT quotation_number, customer_name, customer_phone, customer_email,
                           status, issue_date, valid_until, quotation_notes,
                           subtotal_amount, discount_amount, vat_amount, vat_rate_percent, vat_mode, total_amount,
                           location_name, created_by_name
                    FROM quotations
                    WHERE quotation_id = ?
                    """)) {
                ps.setLong(1, quotationId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Quotation not found.");
                    }
                    return buildQuotationHtml(conn, quotationId, rs, settings, printSettings);
                }
            }
        }
    }

    public static String buildInvoice(long invoiceId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            CompanyCustomizationManager.ReceiptSettings settings = CompanyCustomizationManager.loadReceiptSettings();
            CompanyCustomizationManager.QuotationInvoicePrintSettings printSettings = CompanyCustomizationManager.loadQuotationInvoicePrintSettings();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT invoice_number, quotation_number, customer_name, customer_phone, customer_email,
                           status, invoice_date, invoice_notes, subtotal_amount, discount_amount, vat_amount,
                           total_amount, amount_paid, balance_due, payment_status, vat_rate_percent, vat_mode, location_name, created_by_name
                    FROM invoices
                    WHERE invoice_id = ?
                    """)) {
                ps.setLong(1, invoiceId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Sales invoice not found.");
                    }
                    return buildInvoiceHtml(conn, invoiceId, rs, settings, printSettings);
                }
            }
        }
    }

    public static String buildDelivery(long deliveryEventId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            CompanyCustomizationManager.ReceiptSettings settings = CompanyCustomizationManager.loadReceiptSettings();
            CompanyCustomizationManager.QuotationInvoicePrintSettings printSettings = CompanyCustomizationManager.loadQuotationInvoicePrintSettings();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT de.invoice_id, de.delivery_number, de.delivery_method, de.receiver_name,
                           de.delivery_notes, de.remaining_balance, de.delivered_by_name, de.created_at,
                           so.invoice_number, so.customer_name, so.customer_phone, so.balance_due
                    FROM invoice_delivery_events de
                    JOIN invoices so ON so.invoice_id = de.invoice_id
                    WHERE de.invoice_delivery_event_id = ?
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

    public static String buildSampleQuotation(CompanyCustomizationManager.ReceiptSettings receiptSettings,
                                          CompanyCustomizationManager.QuotationInvoicePrintSettings printSettings) {
        List<DocumentLine> lines = sampleLines(false);
        return renderPagedDocument(receiptSettings, printSettings, printSettings.quotationTitle(), "Q-MAIN-POS1-000123", "06/07/2026",
                new String[][]{{"Quotation #", "Q-MAIN-POS1-000123"}, {"Status", "ISSUED"}, {"Issue Date", "06/07/2026"}, {"Valid Until", "07/07/2026"}},
                "Apex Property Group", "555-0198", "purchasing@apex.example", "Apex Property Group",
                lines, false, printSettings.quotationValidityNote(), null, "GRAND TOTAL", money("729.00"), false, false, null);
    }

    public static String buildSampleInvoice(CompanyCustomizationManager.ReceiptSettings receiptSettings,
                                          CompanyCustomizationManager.QuotationInvoicePrintSettings printSettings) {
        List<DocumentLine> lines = sampleLines(false);
        return renderPagedDocument(receiptSettings, printSettings, printSettings.invoiceTitle(), "INV-MAIN-POS1-000088", "06/07/2026",
                new String[][]{{"Invoice #", "INV-MAIN-POS1-000088"}, {"Quotation #", "Q-MAIN-POS1-000123"}, {"Status", "PARTIALLY_DELIVERED"}, {"Invoice Date", "06/07/2026"}},
                "Apex Property Group", "555-0198", "purchasing@apex.example", "Apex Property Group",
                lines, false, null, invoiceBalanceNote(money("250.00"), money("479.00"), "PARTIAL"),
                "GRAND TOTAL", money("729.00"), false, false, null);
    }

    public static String buildSampleDelivery(CompanyCustomizationManager.ReceiptSettings receiptSettings,
                                             CompanyCustomizationManager.QuotationInvoicePrintSettings printSettings) {
        List<DocumentLine> lines = List.of(new DocumentLine(
                12,
                6,
                6,
                "Commercial Filter Set",
                money("42.00"),
                money("521.14")
        ));
        return renderPagedDocument(receiptSettings, printSettings, printSettings.deliveryTitle(), "DEL-MAIN-POS1-000041", "06/07/2026 10:30 AM",
                new String[][]{{"Delivery #", "DEL-MAIN-POS1-000041"}, {"Invoice #", "INV-MAIN-POS1-000088"}, {"Method", "LOCAL_DELIVERY"}, {"Delivered At", "06/07/2026 10:30 AM"}},
                "Apex Property Group", "555-0198", "Jordan Lee", "Apex Property Group",
                lines, true, null, null, "REMAINING BALANCE", money("479.00"), true, true, "Jordan Lee");
    }

    private static String buildQuotationHtml(Connection conn, long quotationId, ResultSet header,
                                         CompanyCustomizationManager.ReceiptSettings receiptSettings,
                                         CompanyCustomizationManager.QuotationInvoicePrintSettings printSettings) throws SQLException {
        List<DocumentLine> lines = quotationLines(conn, quotationId);
        return renderPagedDocument(receiptSettings, printSettings, printSettings.quotationTitle(), header.getString("quotation_number"), date(header, "issue_date"),
                new String[][]{{"Quotation #", header.getString("quotation_number")}, {"Status", header.getString("status")}, {"Issue Date", date(header, "issue_date")}, {"Valid Until", date(header, "valid_until")}},
                header.getString("customer_name"), header.getString("customer_phone"), header.getString("customer_email"), header.getString("customer_name"),
                lines, false, printSettings.quotationValidityNote(), null, "GRAND TOTAL", header.getBigDecimal("total_amount"), false, false, null);
    }

    private static String buildInvoiceHtml(Connection conn, long invoiceId, ResultSet header,
                                         CompanyCustomizationManager.ReceiptSettings receiptSettings,
                                         CompanyCustomizationManager.QuotationInvoicePrintSettings printSettings) throws SQLException {
        List<DocumentLine> lines = invoiceLines(conn, invoiceId);
        return renderPagedDocument(receiptSettings, printSettings, printSettings.invoiceTitle(), header.getString("invoice_number"), date(header, "invoice_date"),
                new String[][]{{"Invoice #", header.getString("invoice_number")}, {"Quotation #", header.getString("quotation_number")}, {"Status", header.getString("status")}, {"Invoice Date", date(header, "invoice_date")}},
                header.getString("customer_name"), header.getString("customer_phone"), header.getString("customer_email"), header.getString("customer_name"),
                lines, false, null, invoiceBalanceNote(header.getBigDecimal("amount_paid"), header.getBigDecimal("balance_due"), header.getString("payment_status")),
                "GRAND TOTAL", header.getBigDecimal("total_amount"), false, false, null);
    }

    private static String buildDeliveryHtml(Connection conn, long deliveryEventId, ResultSet header,
                                            CompanyCustomizationManager.ReceiptSettings receiptSettings,
                                            CompanyCustomizationManager.QuotationInvoicePrintSettings printSettings) throws SQLException {
        List<DocumentLine> lines = deliveryLines(conn, deliveryEventId);
        return renderPagedDocument(receiptSettings, printSettings, printSettings.deliveryTitle(), header.getString("delivery_number"), clean(header.getString("created_at")),
                new String[][]{{"Delivery #", header.getString("delivery_number")}, {"Invoice #", header.getString("invoice_number")}, {"Method", header.getString("delivery_method")}, {"Delivered At", clean(header.getString("created_at"))}},
                header.getString("customer_name"), header.getString("customer_phone"), header.getString("receiver_name"), header.getString("customer_name"),
                lines, true, null, null, "REMAINING BALANCE", header.getBigDecimal("balance_due"), true, true, header.getString("receiver_name"));
    }

    private static List<DocumentLine> sampleLines(boolean includeDelivery) {
        return List.of(
                new DocumentLine(
                        12,
                        includeDelivery ? 6 : null,
                        includeDelivery ? 6 : null,
                        "Commercial Filter Set",
                        money("42.00"),
                        money("521.14")
                ),
                new DocumentLine(
                        2,
                        includeDelivery ? 0 : null,
                        includeDelivery ? 2 : null,
                        "Installation Kit",
                        money("95.00"),
                        money("207.86")
                )
        );
    }

    private static String renderPagedDocument(CompanyCustomizationManager.ReceiptSettings receiptSettings,
                                              CompanyCustomizationManager.QuotationInvoicePrintSettings printSettings,
                                              String documentTitle, String documentNumber, String documentDate,
                                              String[][] docFields, String customerName, String customerPhone,
                                              String customerEmailOrReceiver, String billTo,
                                              List<DocumentLine> lines, boolean includeDelivery,
                                              String validityNote, String balanceNote,
                                              String totalLabel, BigDecimal total,
                                              boolean showDeliveredBy, boolean receiverLabel, String receiverName) {
        int rowsPerPage = ROWS_PER_PAGE;
        int pageCount = Math.max(1, (int) Math.ceil(Math.max(lines.size(), 1) / (double) rowsPerPage));
        StringBuilder html = htmlStart();
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int from = Math.min(pageIndex * rowsPerPage, lines.size());
            int to = Math.min(from + rowsPerPage, lines.size());
            List<DocumentLine> pageLines = from >= to ? List.of() : lines.subList(from, to);
            boolean lastPage = pageIndex == pageCount - 1;
            appendPageStart(html);
            appendHtmlHeader(html, receiptSettings, documentTitle, documentNumber, documentDate, (pageIndex + 1) + " of " + pageCount);
            appendDocumentCustomerInfo(html, docFields, customerName, customerPhone, customerEmailOrReceiver);
            appendDocumentGrid(html, billTo, pageLines, includeDelivery, rowsPerPage, lastPage,
                    validityNote, balanceNote, totalLabel, total, printSettings,
                    showDeliveredBy, receiverLabel, receiverName, pageIndex + 2);
            appendPageEnd(html);
        }
        return finishHtml(html);
    }

    private static String finishHtml(StringBuilder html) {
        return html.append("</body></html>").toString();
    }

    private static List<DocumentLine> quotationLines(Connection conn, long quotationId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT item_name, quantity, 0 AS delivered_now, quantity AS remaining, unit_price, line_total
                FROM quotation_lines
                WHERE quotation_id = ?
                ORDER BY sort_order, quotation_line_id
                """)) {
            ps.setLong(1, quotationId);
            return documentLines(ps, false);
        }
    }

    private static List<DocumentLine> invoiceLines(Connection conn, long invoiceId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT item_name, quantity_invoiced AS quantity, quantity_delivered AS delivered_now,
                       quantity_invoiced - quantity_delivered AS remaining, unit_price, line_total
                FROM invoice_lines
                WHERE invoice_id = ?
                ORDER BY sort_order, invoice_line_id
                """)) {
            ps.setLong(1, invoiceId);
            return documentLines(ps, false);
        }
    }

    private static List<DocumentLine> deliveryLines(Connection conn, long deliveryEventId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT sol.item_name, sol.quantity_invoiced AS quantity, dl.quantity_delivered AS delivered_now,
                       dl.quantity_remaining AS remaining, sol.unit_price, sol.line_total
                FROM invoice_delivery_lines dl
                JOIN invoice_lines sol ON sol.invoice_line_id = dl.invoice_line_id
                WHERE dl.invoice_delivery_event_id = ?
                ORDER BY dl.invoice_delivery_line_id
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
                @page { size: 8.5in 11in; margin: 0; }
                body { font-family: Arial, sans-serif; margin: 0; background: #f3f4f6; color: #111; }
                .page { width: 100%; height: 100%; margin: 0; padding: 14px; background: white; box-sizing: border-box; display: flex; flex-direction: column; overflow: hidden; page-break-after: always; }
                .page:last-child { page-break-after: auto; }
                .document-body { flex: 1 1 auto; }
                .document-bottom { margin-top: auto; padding-top: 10px; }
                table { border-collapse: collapse; border-spacing: 0; width: 100%; }
                .joined { margin-top: -2px; }
                td, th { border: 2px solid #111; padding: 4px 5px; font-size: 11px; line-height: 1.15; }
                th { background: #d7d7d7; font-weight: bold; text-align: center; }
                .top td { border: 0; }
                .logo { font-size: 34px; color: #f05a00; font-weight: bold; font-style: italic; text-align: center; min-height: 76px; }
                .logo img { max-height: 76px; max-width: 330px; }
                .motto { font-size: 12px; font-weight: bold; font-style: italic; text-align: center; padding-top: 6px; padding-bottom: 4px; line-height: 1.12; }
                .tagline { font-size: 12px; font-weight: bold; font-style: italic; text-align: center; padding-top: 2px; }
                .doctype { font-size: 18px; color: #777; font-weight: bold; text-align: right; line-height: 1.05; }
                .doctype-line { white-space: nowrap; }
                .docmeta { font-size: 13px; font-weight: bold; text-align: right; }
                .contact td { border: 0; font-weight: bold; font-size: 11px; padding: 1px 4px; }
                .info td { font-size: 11px; border: 2px solid #111; vertical-align: top; }
                .info-label { font-weight: bold; color: #333; display: inline-block; min-width: 72px; }
                .document-grid { margin-top: 0; table-layout: fixed; }
                .bill-label { width: 15%; background: #d7d7d7; font-weight: bold; }
                .bill-name { font-size: 16px; font-weight: bold; }
                .num { text-align: right; white-space: nowrap; }
                .center { text-align: center; }
                .description { font-weight: bold; }
                .line-row td, .blank td { height: 21px; }
                .note { border: 2px solid #111; font-size: 11px; font-weight: bold; font-style: italic; padding: 5px 8px; }
                .joined-note { margin-top: -2px; }
                .grid-note { font-size: 11px; font-weight: bold; font-style: italic; padding: 5px 8px; }
                .signature-row td { height: 36px; vertical-align: bottom; }
                .total-label { font-weight: bold; font-size: 14px; text-align: center; }
                .total-amount { font-weight: bold; font-size: 15px; text-align: right; }
                .signature-label { font-weight: bold; width: 18%; }
                @media print {
                    body { background: white; }
                    .page { margin: 0; }
                }
                </style></head><body>
                """);
    }

    private static void appendPageStart(StringBuilder html) {
        html.append("<div class='page'><div class='document-body'>");
    }

    private static void appendPageEnd(StringBuilder html) {
        html.append("</div></div>");
    }

    private static void appendHtmlHeader(StringBuilder html, CompanyCustomizationManager.ReceiptSettings settings,
                                         String documentTitle, String documentNumber, String date, String page) {
        html.append("<table class='top'><tr><td style='width:70%'>");
        if (settings.showLogo() && !settings.logoPath().isBlank()) {
            html.append("<div class='logo'><img src='").append(escAttr(imageSrc(settings.logoPath()))).append("'></div>");
        } else {
            html.append("<div class='logo'>").append(esc(settings.companyName())).append("</div>");
        }
        html.append("</td><td style='width:30%; vertical-align:top;'>")
                .append("<div class='doctype'>").append(documentTitleHtml(documentTitle)).append("</div>")
                .append("<div class='docmeta'>#&nbsp;&nbsp;").append(esc(documentNumber)).append("</div>")
                .append("<div class='docmeta'>Date:&nbsp;&nbsp;").append(esc(date)).append("</div>")
                .append("<div class='docmeta'>Page:&nbsp;&nbsp;").append(esc(page)).append("</div>")
                .append("</td></tr></table>");
        appendMotto(html, settings);
        appendContactBlock(html, settings);
    }

    private static void appendMotto(StringBuilder html, CompanyCustomizationManager.ReceiptSettings settings) {
        String motto1 = clean(settings.mottoLine1());
        String motto2 = clean(settings.mottoLine2());
        if (motto1.isBlank() && motto2.isBlank() && !clean(settings.headerLine()).isBlank()) {
            motto1 = settings.headerLine();
        }
        if (motto1.isBlank() && motto2.isBlank()) {
            return;
        }
        html.append("<div class='motto'>");
        if (!motto1.isBlank()) {
            html.append("<div>").append(esc(motto1)).append("</div>");
        }
        if (!motto2.isBlank()) {
            html.append("<div>").append(esc(motto2)).append("</div>");
        }
        html.append("</div>");
    }

    private static String documentTitleHtml(String documentTitle) {
        String title = clean(documentTitle);
        if (title.equalsIgnoreCase("QUOTE / NOT FINAL SALE")) {
            return "<div class='doctype-line'>QUOTE</div><div class='doctype-line'>NOT FINAL SALE</div>";
        }
        return "<div class='doctype-line'>" + esc(title) + "</div>";
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
        html.append("<table class='joined'><tr><td class='bill-label'>Bill To:</td><td class='bill-name'>")
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

    private static void appendLineTable(StringBuilder html, List<DocumentLine> lines, boolean includeDelivery, int rowsPerPage) {
        html.append("<table class='joined'><tr><th style='width:12%'>Quantity</th>");
        if (includeDelivery) {
            html.append("<th style='width:12%'>Delivered</th><th style='width:12%'>Remaining</th>");
        }
        html.append("<th>Description</th><th style='width:16%'>Unit Price</th><th style='width:16%'>Amount</th></tr>");
        int rows = 0;
        for (DocumentLine line : lines) {
            rows++;
            html.append("<tr class='line-row'><td class='center'>").append(line.quantity()).append("</td>");
            if (includeDelivery) {
                html.append("<td class='center'>").append(line.deliveredNow() == null ? "" : line.deliveredNow()).append("</td>")
                        .append("<td class='center'>").append(line.remaining() == null ? "" : line.remaining()).append("</td>");
            }
            html.append("<td class='description'>").append(esc(line.description())).append("</td>")
                    .append("<td class='num'>").append(esc(money(line.unitPrice()))).append("</td>")
                    .append("<td class='num'>").append(esc(money(line.amount()))).append("</td></tr>");
        }
        int blankRows = Math.max(rowsPerPage - rows, 0);
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

    private static void appendDocumentGrid(StringBuilder html, String billTo, List<DocumentLine> lines, boolean includeDelivery,
                                           int rowsPerPage, boolean lastPage, String validityNote, String balanceNote,
                                           String totalLabel, BigDecimal total,
                                           CompanyCustomizationManager.QuotationInvoicePrintSettings settings,
                                           boolean showDeliveredBy, boolean receiverLabel, String receiverName,
                                           int nextPage) {
        int columns = includeDelivery ? 6 : 4;
        html.append("<table class='document-grid' cellspacing='0' cellpadding='0' border='0'>")
                .append("<tr><td class='bill-label'>Bill To:</td><td class='bill-name' colspan='")
                .append(columns - 1)
                .append("'>")
                .append(esc(billTo))
                .append("</td></tr>");
        html.append("<tr><th style='width:12%'>Quantity</th>");
        if (includeDelivery) {
            html.append("<th style='width:12%'>Delivered</th><th style='width:12%'>Remaining</th>");
        }
        html.append("<th>Description</th><th style='width:16%'>Unit Price</th><th style='width:16%'>Amount</th></tr>");
        int rows = 0;
        for (DocumentLine line : lines) {
            rows++;
            html.append("<tr class='line-row'><td class='center'>").append(line.quantity()).append("</td>");
            if (includeDelivery) {
                html.append("<td class='center'>").append(line.deliveredNow() == null ? "" : line.deliveredNow()).append("</td>")
                        .append("<td class='center'>").append(line.remaining() == null ? "" : line.remaining()).append("</td>");
            }
            html.append("<td class='description'>").append(esc(line.description())).append("</td>")
                    .append("<td class='num'>").append(esc(money(line.unitPrice()))).append("</td>")
                    .append("<td class='num'>").append(esc(money(line.amount()))).append("</td></tr>");
        }
        for (int i = 0; i < Math.max(rowsPerPage - rows, 0); i++) {
            html.append("<tr class='blank'>");
            for (int c = 0; c < columns; c++) {
                html.append("<td>&nbsp;</td>");
            }
            html.append("</tr>");
        }
        if (lastPage) {
            appendGridNoteRow(html, columns, validityNote);
            appendGridNoteRow(html, columns, balanceNote);
            if (!settings.footerNote().isBlank()) {
                appendGridNoteRow(html, columns, settings.footerNote());
            }
            appendGridSignatureRows(html, columns, totalLabel, total, settings.showSignatures(), showDeliveredBy, receiverLabel, receiverName);
        } else {
            appendGridNoteRow(html, columns, "Continued on page " + nextPage);
        }
        html.append("</table>");
    }

    private static void appendGridNoteRow(StringBuilder html, int columns, String note) {
        if (!clean(note).isBlank()) {
            html.append("<tr><td class='grid-note' colspan='").append(columns).append("'>")
                    .append(esc(note))
                    .append("</td></tr>");
        }
    }

    private static void appendGridSignatureRows(StringBuilder html, int columns, String totalLabel, BigDecimal total,
                                                boolean showSignatures, boolean showDeliveredBy,
                                                boolean receiverLabel, String receiverName) {
        int blankSpan = Math.max(1, columns - 3);
        html.append("<tr class='signature-row'>");
        if (showSignatures) {
            html.append("<td class='signature-label'>")
                    .append(showDeliveredBy ? "Delivered By:" : "Received By:")
                    .append("</td><td colspan='").append(blankSpan).append("'></td>");
        } else {
            html.append("<td colspan='").append(blankSpan + 1).append("'></td>");
        }
        html.append("<td class='total-label' rowspan='2'>").append(esc(totalLabel)).append("</td>")
                .append("<td class='total-amount' rowspan='2'>").append(esc(money(total))).append("</td></tr>")
                .append("<tr class='signature-row'>");
        if (showSignatures) {
            html.append("<td class='signature-label'>").append(receiverLabel ? "Received By:" : "Approved By:")
                    .append("</td><td colspan='").append(blankSpan).append("'>")
                    .append(esc(clean(receiverName))).append("</td>");
        } else {
            html.append("<td colspan='").append(blankSpan + 1).append("'></td>");
        }
        html.append("</tr>");
    }

    private static String invoiceBalanceNote(BigDecimal paid, BigDecimal balance, String status) {
        return "Paid: " + money(paid)
                + "    Balance Due: " + money(balance)
                + "    Status: " + clean(status);
    }

    private static void appendBottomNote(StringBuilder html, String note) {
        beginBottom(html);
        html.append("<div class='note joined-note'>").append(esc(note)).append("</div>");
    }

    private static void appendContinuedFooter(StringBuilder html, int nextPage) {
        beginBottom(html);
        html.append("<div class='note joined-note'>Continued on page ").append(nextPage).append("</div>");
    }

    private static void appendInvoiceBalance(StringBuilder html, BigDecimal paid, BigDecimal balance, String status) {
        beginBottom(html);
        html.append("<div class='note joined-note'>Paid: ").append(esc(money(paid)))
                .append("&nbsp;&nbsp;&nbsp; Balance Due: ").append(esc(money(balance)))
                .append("&nbsp;&nbsp;&nbsp; Status: ").append(esc(status)).append("</div>");
    }

    private static void appendTotalsAndSignatures(StringBuilder html, String totalLabel, BigDecimal total,
                                                  CompanyCustomizationManager.QuotationInvoicePrintSettings settings,
                                                  boolean showDeliveredBy, boolean receiverLabel, String receiverName) {
        beginBottom(html);
        if (!settings.footerNote().isBlank()) {
            html.append("<div class='note joined-note'>").append(esc(settings.footerNote())).append("</div>");
        }
        html.append("<table class='joined'><tr class='signature-row'>");
        if (settings.showSignatures()) {
            html.append("<td class='signature-label'>")
                    .append(showDeliveredBy ? "Delivered By:" : "Received By:")
                    .append("</td><td></td>");
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

    private static void appendQuotationLines(StringBuilder out, Connection conn, long quotationId) throws SQLException {
        appendWrapped(out, "Lines");
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT item_name, sku, quantity, unit_price, discount_percent, discount_amount,
                       vat_rate_percent, vat_amount, line_total, delivery_method, line_notes
                FROM quotation_lines
                WHERE quotation_id = ?
                ORDER BY sort_order, quotation_line_id
                """)) {
            ps.setLong(1, quotationId);
            appendLineRows(out, ps, "quantity");
        }
    }

    private static void appendInvoiceLines(StringBuilder out, Connection conn, long invoiceId) throws SQLException {
        appendWrapped(out, "Lines");
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT item_name, sku, quantity_invoiced AS quantity, quantity_delivered,
                       unit_price, discount_percent, discount_amount, vat_rate_percent, vat_amount,
                       line_total, delivery_method, line_notes
                FROM invoice_lines
                WHERE invoice_id = ?
                ORDER BY sort_order, invoice_line_id
                """)) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    appendWrapped(out, rs.getString("item_name") + "  SKU: " + clean(rs.getString("sku")));
                    appendWrapped(out, "  Qty: " + rs.getInt("quantity")
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
                FROM invoice_delivery_lines
                WHERE invoice_delivery_event_id = ?
                ORDER BY invoice_delivery_line_id
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

    private static void appendRemainingInvoiceLines(StringBuilder out, Connection conn, long invoiceId) throws SQLException {
        appendWrapped(out, "Remaining Items");
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT item_name, quantity_invoiced - quantity_delivered AS remaining
                FROM invoice_lines
                WHERE invoice_id = ?
                  AND quantity_invoiced > quantity_delivered
                ORDER BY sort_order, invoice_line_id
                """)) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    appendWrapped(out, rs.getString("item_name") + "  Remaining: " + rs.getInt("remaining"));
                }
                if (!any) {
                    appendWrapped(out, "All invoice lines have been delivered.");
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

    private static void appendFooterAndSignatures(StringBuilder out, CompanyCustomizationManager.QuotationInvoicePrintSettings settings) {
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
        out.append("Received By: ").append("_".repeat(54)).append('\n').append('\n');
        out.append("Approved By: ").append("_".repeat(54)).append('\n');
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

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
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
