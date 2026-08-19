package services;

import com.google.gson.Gson;
import Receipt.AccountPaymentReceiptData;
import Receipt.AccountPaymentReceiptFormatter;
import Receipt.CustomOrderSlipData;
import Receipt.CustomOrderSlipFormatter;
import Receipt.ReceiptData;
import Receipt.ReceiptFormatter;
import data.DB;
import managers.ServerCompanyCustomizationRepository;
import utils.CurrencyFormatter;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ServerEmailOutboxService {
    private static final Gson GSON=LanJson.create();
    private static final ThreadLocal<Connection> REQUEST_CONNECTION = new ThreadLocal<>();
    private static final String AUTHORIZATION_REQUIRED_PREFIX = "GMAIL_AUTHORIZATION_REQUIRED:";

    private ServerEmailOutboxService() {
    }

    static void bindRequestConnection(Connection connection){REQUEST_CONNECTION.set(connection);}
    static void clearRequestConnection(){REQUEST_CONNECTION.remove();}

    private static ConnectionLease connectionLease()throws SQLException{
        Connection bound=REQUEST_CONNECTION.get();return bound==null?new ConnectionLease(DB.getConnection(),true):new ConnectionLease(bound,false);
    }

    public static QueueResult queueSaleReceipt(int saleId, String requestedRecipient, boolean requireEnabled) throws SQLException {
        try (ConnectionLease lease = connectionLease()) {
            Connection conn = lease.connection();
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
            ReceiptData receipt = LanDocumentDataService.saleReceipt(conn, saleId, ServerRequestIdentity.userId(), settings.locationId(), null, null);
            ServerCompanyCustomizationRepository.ReceiptSettings receiptSettings = ServerCompanyCustomizationRepository.loadReceiptSettings();
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
        try (ConnectionLease lease = connectionLease()) {
            Connection conn = lease.connection();
            EmailSchemaInstaller.ensureSchema(conn);
            CustomOrderEmailData order = loadCustomOrderEmailData(conn, orderNumber);
            if (isBlank(order.customerEmail())) {
                return QueueResult.skipped("No customer email is available for this custom order.");
            }
            StoreEmailSettings settings = loadSettings(conn, order.locationId());
            if (!settings.canSend("CUSTOM_ORDER_CONFIRMATION", requireEnabled)) {
                return QueueResult.skipped(settings.disabledReason("order confirmations"));
            }
            CustomOrderSlipData data = LanDocumentDataService.customOrderSlip(conn, orderNumber, ServerRequestIdentity.userId(), settings.locationId());
            ServerCompanyCustomizationRepository.ReceiptSettings receiptSettings = ServerCompanyCustomizationRepository.loadReceiptSettings();
            ServerCompanyCustomizationRepository.CustomOrderSlipSettings slipSettings = ServerCompanyCustomizationRepository.loadCustomOrderSlipSettings();
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
        try (ConnectionLease lease = connectionLease()) {
            Connection conn = lease.connection();
            EmailSchemaInstaller.ensureSchema(conn);
            String recipient = firstNonBlank(requestedRecipient, receipt.getCustomerEmail());
            if (isBlank(recipient)) {
                return QueueResult.skipped("No customer email is available for this payment receipt.");
            }
            StoreEmailSettings settings = loadSettings(conn, receipt.getLocationId());
            if (!settings.canSend("ACCOUNT_PAYMENT_RECEIPT", requireEnabled)) {
                return QueueResult.skipped(settings.disabledReason("receipts"));
            }
            ServerCompanyCustomizationRepository.ReceiptSettings receiptSettings = ServerCompanyCustomizationRepository.loadReceiptSettings();
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
                Receipt.ServerQuotationInvoiceDocumentBuilder.buildQuotation(quotationId));
    }

    public static QueueResult queueInvoice(long invoiceId, String requestedRecipient, boolean requireEnabled) throws SQLException {
        return queueSalesDocument(invoiceId, requestedRecipient, requireEnabled, "INVOICE", "invoice", "invoice", "Invoice",
                Receipt.ServerQuotationInvoiceDocumentBuilder.buildInvoice(invoiceId));
    }

    public static QueueResult queueDeliveryBill(long deliveryEventId, String requestedRecipient, boolean requireEnabled) throws SQLException {
        return queueSalesDocument(deliveryEventId, requestedRecipient, requireEnabled, "DELIVERY_BILL", "delivery bill", "delivery-bill", "Delivery Bill",
                Receipt.ServerQuotationInvoiceDocumentBuilder.buildDelivery(deliveryEventId));
    }

    public static QueueResult queueBalanceSheetSubmission(long submissionId) throws SQLException {
        return queueBalanceSheetSubmission(submissionId, 0);
    }

    public static QueueResult queueBalanceSheetSubmission(long submissionId, int revisionNo) throws SQLException {
        try (ConnectionLease lease = connectionLease()) {
            Connection conn = lease.connection();
            EmailSchemaInstaller.ensureSchema(conn);
            BalanceSheetEmailData data = loadBalanceSheetEmailData(conn, submissionId);
            StoreEmailSettings settings = loadSettings(conn, data.locationId());
            if (isBlank(data.recipientEmail())) {
                return QueueResult.skipped("No balance sheet email address is configured for this store.");
            }
            if (!settings.canSend("BALANCE_SHEET", false)) {
                return QueueResult.skipped(settings.disabledReason("balance sheet emails"));
            }

            boolean revised = revisionNo > 0;
            RevisionEmailData revision = revised ? loadRevisionEmailData(conn,submissionId,revisionNo,data.currentRevision()) : null;
            ServerBalanceSheetService.BalanceSheet sheet = revised ? revision.sheet() : ServerBalanceSheetService.loadSubmission(submissionId);
            String title = revised ? "Revised Balance Sheet - Revision " + revisionNo : "Submitted Balance Sheet";
            String period = sheet.periodStart() + (sheet.periodStart().equals(sheet.periodEnd()) ? "" : " to " + sheet.periodEnd());
            String editor=revised?firstNonBlank(revision.changedByName(),"Unknown user"):"";
            String revisionBanner = revised ? "Revision " + revisionNo + " - this replaces the previously emailed copy.\nEdited by: "+editor+"\nEdited at: "+revision.changedAt()+"\nReason: "+revision.reason()+"\n\n" : "";
            String html = (revised ? "<p><strong>Revision " + revisionNo + " - this replaces the previously emailed copy.</strong><br>Edited by: "+htmlEscape(editor)+"<br>Edited at: "+htmlEscape(revision.changedAt().toString())+"<br>Reason: "+htmlEscape(revision.reason())+"</p>" : "") + buildBalanceSheetHtml(data.locationName(), sheet);
            String text = revisionBanner + buildBalanceSheetText(data.locationName(), sheet);
            EmailDraft draft = new EmailDraft(
                    settings,
                    data.recipientEmail(),
                    title + " - " + safeSubjectPart(data.locationName()) + " - " + period,
                    text,
                    html,
                    "balance-sheet-" + safeFilePart(data.locationName()) + "-" + sheet.periodEnd() + ".html",
                    "text/html; charset=utf-8",
                    html,
                    "BALANCE_SHEET",
                    String.valueOf(submissionId)
            );
            long outboxId = insertDraft(conn, draft);
            recordEvent(conn, outboxId, "QUEUED", revised ? "Revised balance sheet queued (revision " + revisionNo + ")." : "Submitted balance sheet queued.");
            tryProcessOneAsync(outboxId);
            return QueueResult.queued(outboxId);
        }
    }

    public static List<SendResult> processQueued(int limit) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (ConnectionLease lease = connectionLease()) {
            Connection conn = lease.connection();
            EmailSchemaInstaller.ensureSchema(conn);
            recoverConflictingApiKeyFailures(conn);
            String sql = """
                    SELECT email_outbox_id
                    FROM email_outbox
                    WHERE status IN ('QUEUED', 'FAILED')
                      AND attempts < max_attempts
                      AND COALESCE(last_error, '') NOT LIKE 'GMAIL_AUTHORIZATION_REQUIRED:%'
                      AND (attempts=0 OR updated_at <= CURRENT_TIMESTAMP
                           - make_interval(secs => LEAST(300, CAST(power(2, attempts) AS INTEGER))))
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
        EmailMessage message;
        try (ConnectionLease lease = connectionLease()) {
            Connection conn = lease.connection();
            EmailSchemaInstaller.ensureSchema(conn);
            message = lockForSending(conn, outboxId);
            if (message == null) {
                return SendResult.skipped(outboxId, "Email is not queued for sending.");
            }
        }

        try {
            GmailOAuthService.SendResult sent = GmailOAuthService.send(new GmailOAuthService.GmailMessage(
                    message.senderEmail(), message.senderName(), message.recipientEmail(), message.bccEmail(),
                    message.subject(), message.bodyText(), message.bodyHtml(), message.attachmentName(),
                    message.attachmentContentType(), message.attachmentBody()));
            markSent(outboxId, sent.messageId());
            return SendResult.sent(outboxId, sent.messageId());
        } catch (GmailOAuthService.GmailException ex) {
            if (ex.authorizationRequired()) {
                markAuthorizationRequired(outboxId, ex.getMessage());
            } else {
                markFailed(outboxId, ex.category() + ": " + ex.getMessage());
            }
            return SendResult.failed(outboxId, ex.category() + ": " + ex.getMessage());
        } catch (IOException ex) {
            String error = "GMAIL_NETWORK_ERROR: " + ex.getMessage();
            markFailed(outboxId, error);
            return SendResult.failed(outboxId, error);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            String error = "Email sender request was interrupted.";
            markFailed(outboxId, error);
            return SendResult.failed(outboxId, error);
        } catch (RuntimeException ex) {
            String error = "Email sender authentication failed: " + ex.getMessage();
            markFailed(outboxId, error);
            return SendResult.failed(outboxId, error);
        }
    }

    public static int requeueAuthorizationFailures(String senderEmail) throws SQLException {
        String sender = GmailOAuthService.normalizeEmail(senderEmail);
        try (Connection conn = DB.getConnection(); PreparedStatement ps = conn.prepareStatement("""
                UPDATE email_outbox
                SET status='QUEUED', attempts=0, last_error=NULL
                WHERE LOWER(TRIM(sender_email))=?
                  AND status='FAILED'
                  AND last_error LIKE 'GMAIL_AUTHORIZATION_REQUIRED:%'
                RETURNING email_outbox_id
                """)) {
            ps.setString(1, sender);
            int count = 0;
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    count++;
                    recordEvent(conn, rs.getLong(1), "QUEUED", "Email requeued after Gmail authorization.");
                }
            }
            return count;
        }
    }

    private static void recoverConflictingApiKeyFailures(Connection conn)throws SQLException{
        List<Long> recovered=new ArrayList<>();
        try(PreparedStatement ps=conn.prepareStatement("""
                UPDATE email_outbox
                SET status='QUEUED',attempts=0,last_error=NULL
                WHERE status='FAILED'
                  AND attempts>=max_attempts
                  AND last_error LIKE '%Conflicting API keys%'
                RETURNING email_outbox_id
                """)){
            try(ResultSet rs=ps.executeQuery()){
                while(rs.next())recovered.add(rs.getLong(1));
            }
        }
        for(Long outboxId:recovered){
            recordEvent(conn,outboxId,"QUEUED","Email requeued for direct Gmail delivery.");
        }
    }

    private static QueueResult queueSalesDocument(long documentId, String requestedRecipient, boolean requireEnabled,
                                                  String documentType, String label, String filePrefix, String title,
                                                  String html) throws SQLException {
        try (ConnectionLease lease = connectionLease()) {
            Connection conn = lease.connection();
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

    private static BalanceSheetEmailData loadBalanceSheetEmailData(Connection conn, long submissionId) throws SQLException {
        String sql = """
                SELECT b.location_id,
                       COALESCE(NULLIF(b.location_name, ''), l.name, 'Store') AS location_name,
                       COALESCE(l.balance_sheet_recipient_email, '') AS recipient_email,
                       COALESCE(b.revision_no,0) AS revision_no
                FROM balance_sheet_submissions b
                LEFT JOIN locations l ON l.location_id = b.location_id
                WHERE b.balance_sheet_submission_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, submissionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Submitted balance sheet was not found for email.");
                }
                return new BalanceSheetEmailData(
                        nullableInt(rs, "location_id"),
                        rs.getString("location_name"),
                        rs.getString("recipient_email"),rs.getInt("revision_no")
                );
            }
        }
    }

    private static RevisionEmailData loadRevisionEmailData(Connection conn,long submissionId,int revisionNo,int currentRevision)throws SQLException{
        if(revisionNo!=currentRevision)throw new SQLException("The requested Balance Sheet revision is no longer current. Queue the latest revision instead.");
        try(PreparedStatement ps=conn.prepareStatement("SELECT changed_by_name,changed_at,reason,after_snapshot->'sheet' AS sheet FROM balance_sheet_submission_revisions WHERE balance_sheet_submission_id=? AND revision_no=?")){ps.setLong(1,submissionId);ps.setInt(2,revisionNo);try(ResultSet rs=ps.executeQuery()){if(rs.next())return new RevisionEmailData(rs.getString(1),rs.getTimestamp(2).toLocalDateTime(),rs.getString(3),GSON.fromJson(rs.getString(4),ServerBalanceSheetService.BalanceSheet.class));}}
        throw new SQLException("The requested Balance Sheet revision audit record was not found.");
    }

    private static StoreEmailSettings loadSettings(Connection conn, Integer locationId) throws SQLException {
        Integer safeLocationId = locationId == null ? ServerRequestIdentity.locationId() : locationId;
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
            setNullableInteger(ps, 14, ServerRequestIdentity.userId());
            ps.setString(15, blankToNull(ServerRequestIdentity.userName()));
            ps.setString(16, blankToNull(ServerRequestIdentity.deviceId()));
            ps.setString(17, blankToNull(ServerRequestIdentity.deviceName()));
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

    private static void markSent(long outboxId, String gmailMessageId) throws SQLException {
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
            recordEvent(conn, outboxId, "SENT", "Email sent through Gmail. Message ID: " + trimError(gmailMessageId));
            try (PreparedStatement updateLocation = conn.prepareStatement("""
                    UPDATE locations
                    SET email_connected_at = COALESCE(email_connected_at, CURRENT_TIMESTAMP),
                        email_last_tested_at = CURRENT_TIMESTAMP
                    WHERE location_id = (SELECT location_id FROM email_outbox WHERE email_outbox_id = ?)
                    """)) {
                updateLocation.setLong(1, outboxId);
                updateLocation.executeUpdate();
            }
        }
    }

    private static void markAuthorizationRequired(long outboxId, String error) throws SQLException {
        try (Connection conn = DB.getConnection(); PreparedStatement ps = conn.prepareStatement("""
                UPDATE email_outbox
                SET status='FAILED', attempts=0, last_error=?
                WHERE email_outbox_id=?
                """)) {
            String message = AUTHORIZATION_REQUIRED_PREFIX + " " + trimError(error);
            ps.setString(1, message);
            ps.setLong(2, outboxId);
            ps.executeUpdate();
            recordEvent(conn, outboxId, "AUTHORIZATION_REQUIRED", message);
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
            setNullableInteger(ps, 4, ServerRequestIdentity.userId());
            ps.setString(5, blankToNull(ServerRequestIdentity.userName()));
            ps.setString(6, blankToNull(ServerRequestIdentity.deviceId()));
            ps.setString(7, blankToNull(ServerRequestIdentity.deviceName()));
            ps.executeUpdate();
        }
    }

    private static void tryProcessOneAsync(long outboxId) {
        if(REQUEST_CONNECTION.get()!=null)return;
        processOneAsync(outboxId,ServerRequestIdentity.supabaseAccessToken());
    }

    static void processOneAsync(long outboxId,String accessToken) {
        Thread thread = new Thread(() -> {
            try {
                ServerRequestIdentity.bindSupabaseAccessToken(accessToken);
                processOne(outboxId);
            } catch (SQLException ignored) {
                // The queued row remains retryable from the Email Outbox workflow.
            } finally {
                ServerRequestIdentity.clear();
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

    private static String buildBalanceSheetHtml(String locationName, ServerBalanceSheetService.BalanceSheet sheet) {
        StringBuilder html = new StringBuilder("""
                <html><body style="font-family:Arial,sans-serif;color:#1f2937;max-width:900px;margin:0 auto;">
                """);
        html.append("<h1 style=\"margin-bottom:4px;\">Submitted Balance Sheet</h1>")
                .append("<div style=\"color:#4b5563;margin-bottom:18px;\">")
                .append(htmlEscape(locationName)).append(" &bull; ")
                .append(htmlEscape(sheet.periodStart().toString()));
        if (!sheet.periodStart().equals(sheet.periodEnd())) {
            html.append(" to ").append(htmlEscape(sheet.periodEnd().toString()));
        }
        html.append("</div>")
                .append("<table style=\"border-collapse:collapse;width:100%;margin-bottom:20px;\">")
                .append(summaryRow("Balance BF", money(sheet.balanceBf())))
                .append(summaryRow("Cash in Hand", money(sheet.cashInHand())))
                .append(summaryRow("Total Income", money(sheet.totalIncome())))
                .append(summaryRow("Total Receivables", money(sheet.totalReceivables())))
                .append(summaryRow("Total Expenses", money(sheet.totalExpenses())))
                .append(summaryRow("Total Payables", money(sheet.totalPayables())))
                .append(summaryRow("Balance CF", money(sheet.balanceCf())))
                .append("</table>");
        appendSheetSection(html, "Income Breakdown", sheet.income());
        appendSheetSection(html, "Receivables Breakdown", sheet.receivables());
        appendSheetSection(html, "Expenses Breakdown", sheet.expenses());
        appendSheetSection(html, "Payables Breakdown", sheet.payables());
        appendSheetSection(html, "Submitted Drawer Breakdown", sheet.drawerCash());
        appendSheetSection(html, "Device Sales Breakdown", sheet.deviceSales());
        appendSheetSection(html, "Device Order Breakdown", sheet.deviceOrders());
        appendSheetSection(html, "Device Payment Breakdown", sheet.devicePayments());
        appendSheetSection(html, "Account Payment Breakdown", sheet.accountPayments());
        appendBankSection(html, sheet.bankTransactions());
        appendChequeSection(html, sheet.pendingCheques());
        appendSheetSection(html, "Drawer Match Checks", sheet.drawerChecks());
        html.append("<h2 style=\"font-size:18px;\">Submission Details</h2>")
                .append("<p><strong>Submitted by:</strong> ").append(htmlEscape(sheet.submittedByName())).append("<br>")
                .append("<strong>Submitted at:</strong> ")
                .append(sheet.submittedAt() == null ? "" : htmlEscape(sheet.submittedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))))
                .append("</p>");
        if (!isBlank(sheet.notes())) {
            html.append("<p><strong>Notes:</strong><br>").append(htmlEscape(sheet.notes()).replace("\n", "<br>")).append("</p>");
        }
        return html.append("</body></html>").toString();
    }

    private static String buildBalanceSheetText(String locationName, ServerBalanceSheetService.BalanceSheet sheet) {
        StringBuilder text = new StringBuilder("SUBMITTED BALANCE SHEET\n")
                .append(locationName).append('\n')
                .append(sheet.periodStart());
        if (!sheet.periodStart().equals(sheet.periodEnd())) {
            text.append(" to ").append(sheet.periodEnd());
        }
        text.append("\n\nBalance BF: ").append(money(sheet.balanceBf()))
                .append("\nCash in Hand: ").append(money(sheet.cashInHand()))
                .append("\nTotal Income: ").append(money(sheet.totalIncome()))
                .append("\nTotal Receivables: ").append(money(sheet.totalReceivables()))
                .append("\nTotal Expenses: ").append(money(sheet.totalExpenses()))
                .append("\nTotal Payables: ").append(money(sheet.totalPayables()))
                .append("\nBalance CF: ").append(money(sheet.balanceCf()));
        appendTextSection(text, "Income Breakdown", sheet.income());
        appendTextSection(text, "Receivables Breakdown", sheet.receivables());
        appendTextSection(text, "Expenses Breakdown", sheet.expenses());
        appendTextSection(text, "Payables Breakdown", sheet.payables());
        appendTextSection(text, "Submitted Drawer Breakdown", sheet.drawerCash());
        appendTextSection(text, "Device Sales Breakdown", sheet.deviceSales());
        appendTextSection(text, "Device Order Breakdown", sheet.deviceOrders());
        appendTextSection(text, "Device Payment Breakdown", sheet.devicePayments());
        appendTextSection(text, "Account Payment Breakdown", sheet.accountPayments());
        text.append("\n\nBank Transactions\n");
        if (sheet.bankTransactions() == null || sheet.bankTransactions().isEmpty()) {
            text.append("- No entries");
        } else {
            for (ServerBalanceSheetService.BankTransactionLine line : sheet.bankTransactions()) {
                text.append("- ").append(firstNonBlank(line.transaction(), "Bank transaction"))
                        .append(" (").append(firstNonBlank(line.direction(), "")).append("): ")
                        .append(money(line.amount())).append('\n');
            }
        }
        text.append("\n\nCheques to Deposit\n");
        if (sheet.pendingCheques() == null || sheet.pendingCheques().isEmpty()) {
            text.append("- No entries");
        } else {
            for (ServerBalanceSheetService.ChequeDepositOption line : sheet.pendingCheques()) {
                text.append("- ").append(firstNonBlank(line.sourceLabel(), "Cheque"));
                if (!isBlank(line.payer())) {
                    text.append(" - ").append(line.payer());
                }
                if (!isBlank(line.reference())) {
                    text.append(" / ").append(line.reference());
                }
                text.append(": ").append(money(line.amount())).append('\n');
            }
        }
        appendTextSection(text, "Drawer Match Checks", sheet.drawerChecks());
        text.append("\n\nSubmitted by: ").append(firstNonBlank(sheet.submittedByName(), "Unknown"));
        if (sheet.submittedAt() != null) {
            text.append("\nSubmitted at: ").append(sheet.submittedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        }
        if (!isBlank(sheet.notes())) {
            text.append("\nNotes: ").append(sheet.notes());
        }
        return text.toString();
    }

    private static void appendSheetSection(StringBuilder html, String title, List<ServerBalanceSheetService.SheetLine> lines) {
        html.append("<h2 style=\"font-size:18px;margin-top:22px;\">").append(htmlEscape(title)).append("</h2>")
                .append("<table style=\"border-collapse:collapse;width:100%;\">");
        if (lines == null || lines.isEmpty()) {
            html.append(summaryRow("No entries", money(BigDecimal.ZERO)));
        } else {
            for (ServerBalanceSheetService.SheetLine line : lines) {
                html.append(summaryRow(line.label(), money(line.amount())));
            }
        }
        html.append("</table>");
    }

    private static void appendBankSection(StringBuilder html, List<ServerBalanceSheetService.BankTransactionLine> lines) {
        html.append("<h2 style=\"font-size:18px;margin-top:22px;\">Bank Transactions</h2><table style=\"border-collapse:collapse;width:100%;\">");
        if (lines == null || lines.isEmpty()) {
            html.append(summaryRow("No entries", money(BigDecimal.ZERO)));
        } else {
            for (ServerBalanceSheetService.BankTransactionLine line : lines) {
                html.append(summaryRow(firstNonBlank(line.transaction(), "Bank transaction") + " (" + firstNonBlank(line.direction(), "") + ")", money(line.amount())));
            }
        }
        html.append("</table>");
    }

    private static void appendChequeSection(StringBuilder html, List<ServerBalanceSheetService.ChequeDepositOption> lines) {
        html.append("<h2 style=\"font-size:18px;margin-top:22px;\">Cheques to Deposit</h2><table style=\"border-collapse:collapse;width:100%;\">");
        if (lines == null || lines.isEmpty()) {
            html.append(summaryRow("No entries", money(BigDecimal.ZERO)));
        } else {
            for (ServerBalanceSheetService.ChequeDepositOption line : lines) {
                String label = firstNonBlank(line.sourceLabel(), "Cheque");
                if (!isBlank(line.payer())) {
                    label += " - " + line.payer();
                }
                if (!isBlank(line.reference())) {
                    label += " / " + line.reference();
                }
                html.append(summaryRow(label, money(line.amount())));
            }
        }
        html.append("</table>");
    }

    private static void appendTextSection(StringBuilder text, String title, List<ServerBalanceSheetService.SheetLine> lines) {
        text.append("\n\n").append(title).append('\n');
        if (lines == null || lines.isEmpty()) {
            text.append("- No entries");
            return;
        }
        for (ServerBalanceSheetService.SheetLine line : lines) {
            text.append("- ").append(line.label()).append(": ").append(money(line.amount())).append('\n');
        }
    }

    private static String summaryRow(String label, String amount) {
        return "<tr><td style=\"border:1px solid #d1d5db;padding:8px;\">"
                + htmlEscape(label)
                + "</td><td style=\"border:1px solid #d1d5db;padding:8px;text-align:right;font-weight:600;\">"
                + htmlEscape(amount) + "</td></tr>";
    }

    private static String money(BigDecimal value) {
        return CurrencyFormatter.create(Locale.US).format(value == null ? BigDecimal.ZERO : value);
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

    private record BalanceSheetEmailData(Integer locationId, String locationName, String recipientEmail,int currentRevision) {
    }
    private record RevisionEmailData(String changedByName,java.time.LocalDateTime changedAt,String reason,ServerBalanceSheetService.BalanceSheet sheet){}

    private record ConnectionLease(Connection connection,boolean owned) implements AutoCloseable{
        @Override public void close()throws SQLException{if(owned)connection.close();}
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
        public static SendResult sent(long outboxId, String gmailMessageId) {
            return new SendResult(outboxId, "SENT", "Email sent. Gmail message ID: " + gmailMessageId);
        }

        public static SendResult failed(long outboxId, String message) {
            return new SendResult(outboxId, "FAILED", message);
        }

        public static SendResult skipped(long outboxId, String message) {
            return new SendResult(outboxId, "SKIPPED", message);
        }
    }
}
