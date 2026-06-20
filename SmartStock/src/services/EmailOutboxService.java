package services;

import Receipt.AccountPaymentReceiptData;
import Receipt.AccountPaymentReceiptFormatter;
import Receipt.CustomOrderSlipBuilder;
import Receipt.CustomOrderSlipData;
import Receipt.CustomOrderSlipFormatter;
import Receipt.QuotationInvoiceDocumentBuilder;
import Receipt.ReceiptBuilder;
import Receipt.ReceiptData;
import Receipt.ReceiptFormatter;
import data.DB;
import managers.CompanyCustomizationManager;
import managers.SessionManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EmailOutboxService {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();
    private static final String EMAIL_FUNCTION_URL = getConfig("SMARTSTOCK_EMAIL_FUNCTION_URL", "");
    private static final String EMAIL_FUNCTION_KEY = getConfig("SMARTSTOCK_EMAIL_FUNCTION_KEY", "");

    private EmailOutboxService() {
    }

    public static QueueResult queueSaleReceipt(int saleId, String requestedRecipient, boolean requireEnabled) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            EmailSchemaInstaller.ensureSchema(conn);
            SaleEmailData sale = loadSaleEmailData(conn, saleId);
            String recipient = firstNonBlank(requestedRecipient, sale.customerEmail());
            if (isBlank(recipient)) {
                return QueueResult.skipped("No customer email is available for this receipt.");
            }
            StoreEmailSettings settings = loadSettings(conn, sale.locationId());
            if (!settings.canSend("SALE_RECEIPT", requireEnabled)) {
                return QueueResult.skipped(settings.disabledReason("receipts"));
            }
            ReceiptData receipt = ReceiptBuilder.loadSaleReceipt(saleId, null, null);
            CompanyCustomizationManager.ReceiptSettings receiptSettings = CompanyCustomizationManager.loadReceiptSettings();
            String receiptText = ReceiptFormatter.formatText(receipt, receiptSettings);
            EmailDraft draft = new EmailDraft(
                    settings,
                    recipient,
                    "Receipt " + safeSubjectPart(receipt.getReceiptNumber()),
                    "Thank you for shopping with us.\n\nYour receipt is attached below.\n\n" + receiptText,
                    preformattedHtml("Thank you for shopping with us.", receiptText),
                    "receipt-" + safeFilePart(receipt.getReceiptNumber()) + ".txt",
                    "text/plain; charset=utf-8",
                    receiptText,
                    "SALE_RECEIPT",
                    String.valueOf(saleId)
            );
            long outboxId = insertDraft(conn, draft);
            recordEvent(conn, outboxId, "QUEUED", "Sale receipt queued.");
            tryProcessOneAsync(outboxId);
            return QueueResult.queued(outboxId);
        }
    }

    public static QueueResult queueCustomOrderConfirmation(String orderNumber, boolean requireEnabled) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            EmailSchemaInstaller.ensureSchema(conn);
            CustomOrderEmailData order = loadCustomOrderEmailData(conn, orderNumber);
            if (isBlank(order.customerEmail())) {
                return QueueResult.skipped("No customer email is available for this custom order.");
            }
            StoreEmailSettings settings = loadSettings(conn, order.locationId());
            if (!settings.canSend("CUSTOM_ORDER_CONFIRMATION", requireEnabled)) {
                return QueueResult.skipped(settings.disabledReason("order confirmations"));
            }
            CustomOrderSlipData data = CustomOrderSlipBuilder.buildFromOrderNumber(orderNumber);
            CompanyCustomizationManager.ReceiptSettings receiptSettings = CompanyCustomizationManager.loadReceiptSettings();
            CompanyCustomizationManager.CustomOrderSlipSettings slipSettings = CompanyCustomizationManager.loadCustomOrderSlipSettings();
            String slipText = CustomOrderSlipFormatter.format40Column(data, receiptSettings, slipSettings);
            EmailDraft draft = new EmailDraft(
                    settings,
                    order.customerEmail(),
                    "Order Confirmation " + safeSubjectPart(orderNumber),
                    "Your custom order has been received.\n\n" + slipText,
                    preformattedHtml("Your custom order has been received.", slipText),
                    "order-confirmation-" + safeFilePart(orderNumber) + ".txt",
                    "text/plain; charset=utf-8",
                    slipText,
                    "CUSTOM_ORDER_CONFIRMATION",
                    orderNumber
            );
            long outboxId = insertDraft(conn, draft);
            recordEvent(conn, outboxId, "QUEUED", "Custom order confirmation queued.");
            tryProcessOneAsync(outboxId);
            return QueueResult.queued(outboxId);
        }
    }

    public static QueueResult queueAccountPaymentReceipt(AccountPaymentReceiptData receipt, String requestedRecipient, boolean requireEnabled) throws SQLException {
        if (receipt == null) {
            throw new SQLException("Payment receipt data is required.");
        }
        try (Connection conn = DB.getConnection()) {
            EmailSchemaInstaller.ensureSchema(conn);
            String recipient = firstNonBlank(requestedRecipient, receipt.getCustomerEmail());
            if (isBlank(recipient)) {
                return QueueResult.skipped("No customer email is available for this payment receipt.");
            }
            StoreEmailSettings settings = loadSettings(conn, receipt.getLocationId());
            if (!settings.canSend("ACCOUNT_PAYMENT_RECEIPT", requireEnabled)) {
                return QueueResult.skipped(settings.disabledReason("receipts"));
            }
            CompanyCustomizationManager.ReceiptSettings receiptSettings = CompanyCustomizationManager.loadReceiptSettings();
            String receiptText = AccountPaymentReceiptFormatter.formatText(receipt, receiptSettings);
            EmailDraft draft = new EmailDraft(
                    settings,
                    recipient,
                    "Payment Receipt " + safeSubjectPart(receipt.getPaymentId()),
                    "Thank you. Your payment receipt is attached below.\n\n" + receiptText,
                    preformattedHtml("Thank you. Your payment receipt is attached below.", receiptText),
                    "payment-receipt-" + safeFilePart(receipt.getPaymentId()) + ".txt",
                    "text/plain; charset=utf-8",
                    receiptText,
                    "ACCOUNT_PAYMENT_RECEIPT",
                    String.valueOf(receipt.getTransactionId())
            );
            long outboxId = insertDraft(conn, draft);
            recordEvent(conn, outboxId, "QUEUED", "Account payment receipt queued.");
            tryProcessOneAsync(outboxId);
            return QueueResult.queued(outboxId);
        }
    }

    public static QueueResult queueQuotation(long quotationId, String requestedRecipient, boolean requireEnabled) throws SQLException {
        return queueSalesDocument(quotationId, requestedRecipient, requireEnabled, "QUOTATION", "quotation", "quote", "Quotation",
                QuotationInvoiceDocumentBuilder.buildQuotation(quotationId));
    }

    public static QueueResult queueInvoice(long invoiceId, String requestedRecipient, boolean requireEnabled) throws SQLException {
        return queueSalesDocument(invoiceId, requestedRecipient, requireEnabled, "INVOICE", "invoice", "invoice", "Invoice",
                QuotationInvoiceDocumentBuilder.buildInvoice(invoiceId));
    }

    public static QueueResult queueDeliveryBill(long deliveryEventId, String requestedRecipient, boolean requireEnabled) throws SQLException {
        return queueSalesDocument(deliveryEventId, requestedRecipient, requireEnabled, "DELIVERY_BILL", "delivery bill", "delivery-bill", "Delivery Bill",
                QuotationInvoiceDocumentBuilder.buildDelivery(deliveryEventId));
    }

    public static List<SendResult> processQueued(int limit) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Connection conn = DB.getConnection()) {
            EmailSchemaInstaller.ensureSchema(conn);
            String sql = """
                    SELECT email_outbox_id
                    FROM email_outbox
                    WHERE status IN ('QUEUED', 'FAILED')
                      AND attempts < max_attempts
                    ORDER BY created_at
                    LIMIT ?
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, Math.max(limit, 1));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ids.add(rs.getLong("email_outbox_id"));
                    }
                }
            }
        }
        List<SendResult> results = new ArrayList<>();
        for (Long id : ids) {
            results.add(processOne(id));
        }
        return results;
    }

    public static SendResult processOne(long outboxId) throws SQLException {
        if (isBlank(EMAIL_FUNCTION_URL)) {
            return SendResult.skipped(outboxId, "SMARTSTOCK_EMAIL_FUNCTION_URL is not configured.");
        }
        EmailMessage message;
        try (Connection conn = DB.getConnection()) {
            EmailSchemaInstaller.ensureSchema(conn);
            message = lockForSending(conn, outboxId);
            if (message == null) {
                return SendResult.skipped(outboxId, "Email is not queued for sending.");
            }
        }

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(EMAIL_FUNCTION_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(message), StandardCharsets.UTF_8));
            if (!isBlank(EMAIL_FUNCTION_KEY)) {
                requestBuilder.header("Authorization", "Bearer " + EMAIL_FUNCTION_KEY);
                requestBuilder.header("apikey", EMAIL_FUNCTION_KEY);
            }
            HttpResponse<String> response = HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                markSent(outboxId);
                return SendResult.sent(outboxId);
            }
            String error = "Email sender returned HTTP " + response.statusCode() + ": " + trimError(response.body());
            markFailed(outboxId, error);
            return SendResult.failed(outboxId, error);
        } catch (IOException ex) {
            String error = "Email sender request failed: " + ex.getMessage();
            markFailed(outboxId, error);
            return SendResult.failed(outboxId, error);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            String error = "Email sender request was interrupted.";
            markFailed(outboxId, error);
            return SendResult.failed(outboxId, error);
        }
    }

    private static QueueResult queueSalesDocument(long documentId, String requestedRecipient, boolean requireEnabled,
                                                  String documentType, String label, String filePrefix, String title,
                                                  String html) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            EmailSchemaInstaller.ensureSchema(conn);
            SalesDocumentEmailData data = loadSalesDocumentData(conn, documentType, documentId);
            String recipient = firstNonBlank(requestedRecipient, data.customerEmail());
            if (isBlank(recipient)) {
                return QueueResult.skipped("No customer email is available for this " + label + ".");
            }
            StoreEmailSettings settings = loadSettings(conn, data.locationId());
            if (!settings.canSend(documentType, requireEnabled)) {
                return QueueResult.skipped(settings.disabledReason(label + " emails"));
            }
            String number = firstNonBlank(data.documentNumber(), String.valueOf(documentId));
            EmailDraft draft = new EmailDraft(
                    settings,
                    recipient,
                    title + " " + safeSubjectPart(number),
                    "Please find your " + label + " attached.\n\n" + stripHtml(html),
                    html,
                    filePrefix + "-" + safeFilePart(number) + ".html",
                    "text/html; charset=utf-8",
                    html,
                    documentType,
                    String.valueOf(documentId)
            );
            long outboxId = insertDraft(conn, draft);
            recordEvent(conn, outboxId, "QUEUED", title + " queued.");
            tryProcessOneAsync(outboxId);
            return QueueResult.queued(outboxId);
        }
    }

    private static SaleEmailData loadSaleEmailData(Connection conn, int saleId) throws SQLException {
        String sql = """
                SELECT s.location_id, COALESCE(ca.email, '') AS customer_email
                FROM sales s
                LEFT JOIN customer_accounts ca ON ca.customer_id = s.customer_id
                WHERE s.sale_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Sale was not found for email receipt.");
                }
                return new SaleEmailData(nullableInt(rs, "location_id"), rs.getString("customer_email"));
            }
        }
    }

    private static CustomOrderEmailData loadCustomOrderEmailData(Connection conn, String orderNumber) throws SQLException {
        String sql = """
                SELECT co.location_id, COALESCE(ca.email, '') AS customer_email
                FROM custom_orders co
                LEFT JOIN customer_accounts ca ON ca.customer_id = co.customer_id
                WHERE co.order_number = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Custom order was not found for email confirmation.");
                }
                return new CustomOrderEmailData(nullableInt(rs, "location_id"), rs.getString("customer_email"));
            }
        }
    }

    private static SalesDocumentEmailData loadSalesDocumentData(Connection conn, String documentType, long documentId) throws SQLException {
        String sql = switch (documentType) {
            case "QUOTATION" -> """
                    SELECT location_id, quotation_number AS document_number, COALESCE(customer_email, '') AS customer_email
                    FROM quotations
                    WHERE quotation_id = ?
                    """;
            case "INVOICE" -> """
                    SELECT location_id, invoice_number AS document_number, COALESCE(customer_email, '') AS customer_email
                    FROM invoices
                    WHERE invoice_id = ?
                    """;
            case "DELIVERY_BILL" -> """
                    SELECT de.location_id, de.delivery_number AS document_number, COALESCE(i.customer_email, '') AS customer_email
                    FROM invoice_delivery_events de
                    JOIN invoices i ON i.invoice_id = de.invoice_id
                    WHERE de.invoice_delivery_event_id = ?
                    """;
            default -> throw new SQLException("Unsupported email document type: " + documentType);
        };
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, documentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Document was not found for email.");
                }
                return new SalesDocumentEmailData(nullableInt(rs, "location_id"), rs.getString("document_number"), rs.getString("customer_email"));
            }
        }
    }

    private static StoreEmailSettings loadSettings(Connection conn, Integer locationId) throws SQLException {
        Integer safeLocationId = locationId == null ? SessionManager.getCurrentLocationId() : locationId;
        String sql = """
                SELECT location_id, COALESCE(email_sender_address, '') AS sender_email,
                       COALESCE(email_sender_name, '') AS sender_name,
                       COALESCE(email_bcc_address, '') AS bcc_email,
                       COALESCE(email_receipts_enabled, FALSE) AS receipts_enabled,
                       COALESCE(email_order_confirmations_enabled, FALSE) AS order_confirmations_enabled,
                       COALESCE(email_quotes_enabled, FALSE) AS quotes_enabled,
                       COALESCE(email_invoices_enabled, FALSE) AS invoices_enabled,
                       COALESCE(email_delivery_bills_enabled, FALSE) AS delivery_bills_enabled
                FROM locations
                WHERE (?::integer IS NULL OR location_id = ?)
                ORDER BY location_id
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (safeLocationId == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(1, safeLocationId);
                ps.setInt(2, safeLocationId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No store email settings were found.");
                }
                return new StoreEmailSettings(
                        rs.getInt("location_id"),
                        rs.getString("sender_email"),
                        rs.getString("sender_name"),
                        rs.getString("bcc_email"),
                        rs.getBoolean("receipts_enabled"),
                        rs.getBoolean("order_confirmations_enabled"),
                        rs.getBoolean("quotes_enabled"),
                        rs.getBoolean("invoices_enabled"),
                        rs.getBoolean("delivery_bills_enabled")
                );
            }
        }
    }

    private static long insertDraft(Connection conn, EmailDraft draft) throws SQLException {
        String sql = """
                INSERT INTO email_outbox (
                    location_id, sender_email, sender_name, recipient_email, bcc_email,
                    subject, body_text, body_html, attachment_name, attachment_content_type,
                    attachment_body, document_type, document_id, queued_by_user_id,
                    queued_by_name, device_id, device_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING email_outbox_id
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, draft.settings().locationId());
            ps.setString(2, draft.settings().senderEmail());
            ps.setString(3, draft.settings().senderName());
            ps.setString(4, draft.recipientEmail());
            ps.setString(5, blankToNull(draft.settings().bccEmail()));
            ps.setString(6, draft.subject());
            ps.setString(7, draft.bodyText());
            ps.setString(8, draft.bodyHtml());
            ps.setString(9, draft.attachmentName());
            ps.setString(10, draft.attachmentContentType());
            ps.setString(11, draft.attachmentBody());
            ps.setString(12, draft.documentType());
            ps.setString(13, draft.documentId());
            setNullableInteger(ps, 14, SessionManager.getCurrentUserId());
            ps.setString(15, blankToNull(SessionManager.getCurrentUserDisplayName()));
            ps.setString(16, blankToNull(DeviceContextService.currentDeviceId()));
            ps.setString(17, blankToNull(DeviceContextService.currentDeviceName()));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong("email_outbox_id");
            }
        }
    }

    private static EmailMessage lockForSending(Connection conn, long outboxId) throws SQLException {
        conn.setAutoCommit(false);
        try {
            EmailMessage message;
            String selectSql = """
                    SELECT *
                    FROM email_outbox
                    WHERE email_outbox_id = ?
                      AND status IN ('QUEUED', 'FAILED')
                      AND attempts < max_attempts
                    FOR UPDATE
                    """;
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setLong(1, outboxId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return null;
                    }
                    message = new EmailMessage(
                            rs.getLong("email_outbox_id"),
                            rs.getString("sender_email"),
                            rs.getString("sender_name"),
                            rs.getString("recipient_email"),
                            rs.getString("bcc_email"),
                            rs.getString("subject"),
                            rs.getString("body_text"),
                            rs.getString("body_html"),
                            rs.getString("attachment_name"),
                            rs.getString("attachment_content_type"),
                            rs.getString("attachment_body")
                    );
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE email_outbox
                    SET status = 'SENDING',
                        attempts = attempts + 1,
                        last_error = NULL
                    WHERE email_outbox_id = ?
                    """)) {
                ps.setLong(1, outboxId);
                ps.executeUpdate();
            }
            recordEvent(conn, outboxId, "SENDING", "Sending through Gmail sender.");
            conn.commit();
            return message;
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private static void markSent(long outboxId) throws SQLException {
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     UPDATE email_outbox
                     SET status = 'SENT',
                         sent_at = CURRENT_TIMESTAMP,
                         last_error = NULL
                     WHERE email_outbox_id = ?
                     """)) {
            ps.setLong(1, outboxId);
            ps.executeUpdate();
            recordEvent(conn, outboxId, "SENT", "Email sent.");
        }
    }

    private static void markFailed(long outboxId, String error) throws SQLException {
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     UPDATE email_outbox
                     SET status = CASE WHEN attempts >= max_attempts THEN 'FAILED' ELSE 'QUEUED' END,
                         last_error = ?
                     WHERE email_outbox_id = ?
                     """)) {
            ps.setString(1, trimError(error));
            ps.setLong(2, outboxId);
            ps.executeUpdate();
            recordEvent(conn, outboxId, "FAILED", trimError(error));
        }
    }

    private static void recordEvent(Connection conn, long outboxId, String eventType, String message) throws SQLException {
        String sql = """
                INSERT INTO email_outbox_events (
                    email_outbox_id, event_type, message, user_id, user_name, device_id, device_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, outboxId);
            ps.setString(2, eventType);
            ps.setString(3, message);
            setNullableInteger(ps, 4, SessionManager.getCurrentUserId());
            ps.setString(5, blankToNull(SessionManager.getCurrentUserDisplayName()));
            ps.setString(6, blankToNull(DeviceContextService.currentDeviceId()));
            ps.setString(7, blankToNull(DeviceContextService.currentDeviceName()));
            ps.executeUpdate();
        }
    }

    private static void tryProcessOneAsync(long outboxId) {
        if (isBlank(EMAIL_FUNCTION_URL)) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                processOne(outboxId);
            } catch (SQLException ignored) {
                // The queued row remains retryable from the Email Outbox workflow.
            }
        }, "smartstock-email-send-" + outboxId);
        thread.setDaemon(true);
        thread.start();
    }

    private static String preformattedHtml(String intro, String text) {
        return "<html><body>"
                + "<p>" + htmlEscape(intro) + "</p>"
                + "<pre style=\"font-family:Menlo,Consolas,monospace;white-space:pre-wrap;\">"
                + htmlEscape(text)
                + "</pre></body></html>";
    }

    private static String stripHtml(String html) {
        return html == null ? "" : html.replaceAll("(?is)<style.*?</style>", "")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n\\s+", "\n")
                .trim();
    }

    private static String toJson(EmailMessage message) {
        return "{"
                + "\"outbox_id\":" + message.outboxId()
                + ",\"from_email\":" + jsonValue(message.senderEmail())
                + ",\"from_name\":" + jsonValue(message.senderName())
                + ",\"to_email\":" + jsonValue(message.recipientEmail())
                + ",\"bcc_email\":" + jsonValue(message.bccEmail())
                + ",\"subject\":" + jsonValue(message.subject())
                + ",\"body_text\":" + jsonValue(message.bodyText())
                + ",\"body_html\":" + jsonValue(message.bodyHtml())
                + ",\"attachment_name\":" + jsonValue(message.attachmentName())
                + ",\"attachment_content_type\":" + jsonValue(message.attachmentContentType())
                + ",\"attachment_body\":" + jsonValue(message.attachmentBody())
                + "}";
    }

    private static String jsonValue(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    private static String htmlEscape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String safeSubjectPart(String value) {
        String clean = firstNonBlank(value, "").replaceAll("\\s+", " ").trim();
        return clean.isBlank() ? "" : clean;
    }

    private static String safeFilePart(String value) {
        String clean = firstNonBlank(value, "document").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
        clean = clean.replaceAll("^-+|-+$", "");
        return clean.isBlank() ? "document" : clean;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimError(String value) {
        String clean = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return clean.length() <= 500 ? clean : clean.substring(0, 500);
    }

    private static String getConfig(String key, String fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(key);
        }
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record StoreEmailSettings(int locationId, String senderEmail, String senderName, String bccEmail,
                                      boolean receiptsEnabled, boolean orderConfirmationsEnabled,
                                      boolean quotesEnabled, boolean invoicesEnabled, boolean deliveryBillsEnabled) {
        private boolean canSend(String documentType, boolean requireEnabled) {
            if (isBlank(senderEmail)) {
                return false;
            }
            if (!requireEnabled) {
                return true;
            }
            return switch (documentType) {
                case "SALE_RECEIPT", "ACCOUNT_PAYMENT_RECEIPT" -> receiptsEnabled;
                case "CUSTOM_ORDER_CONFIRMATION" -> orderConfirmationsEnabled;
                case "QUOTATION" -> quotesEnabled;
                case "INVOICE" -> invoicesEnabled;
                case "DELIVERY_BILL" -> deliveryBillsEnabled;
                default -> false;
            };
        }

        private String disabledReason(String label) {
            if (isBlank(senderEmail)) {
                return "This store does not have an email sender address configured.";
            }
            return "Automatic " + label + " are not enabled for this store.";
        }
    }

    private record EmailDraft(StoreEmailSettings settings, String recipientEmail, String subject, String bodyText,
                              String bodyHtml, String attachmentName, String attachmentContentType,
                              String attachmentBody, String documentType, String documentId) {
    }

    private record EmailMessage(long outboxId, String senderEmail, String senderName, String recipientEmail,
                                String bccEmail, String subject, String bodyText, String bodyHtml,
                                String attachmentName, String attachmentContentType, String attachmentBody) {
    }

    private record SaleEmailData(Integer locationId, String customerEmail) {
    }

    private record CustomOrderEmailData(Integer locationId, String customerEmail) {
    }

    private record SalesDocumentEmailData(Integer locationId, String documentNumber, String customerEmail) {
    }

    public record QueueResult(boolean queued, boolean skipped, long outboxId, String message) {
        public static QueueResult queued(long outboxId) {
            return new QueueResult(true, false, outboxId, "Email queued.");
        }

        public static QueueResult skipped(String message) {
            return new QueueResult(false, true, 0, message);
        }
    }

    public record SendResult(long outboxId, String status, String message) {
        public static SendResult sent(long outboxId) {
            return new SendResult(outboxId, "SENT", "Email sent.");
        }

        public static SendResult failed(long outboxId, String message) {
            return new SendResult(outboxId, "FAILED", message);
        }

        public static SendResult skipped(long outboxId, String message) {
            return new SendResult(outboxId, "SKIPPED", message);
        }
    }
}
