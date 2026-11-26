package services;

import data.DB;
import managers.SessionManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class ServerQuotationInvoiceViewService {
    private ServerQuotationInvoiceViewService() {
    }

    public static List<QuotationSummary> listQuotations() throws SQLException {
        try (Connection conn = DB.getConnection()) {
            QuotationInvoiceSchemaInstaller.ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT quotation_id, quotation_number, customer_name, status, valid_until, total_amount
                    FROM quotations
                    ORDER BY created_at DESC, quotation_id DESC
                    LIMIT 300
                    """);
                 ResultSet rs = ps.executeQuery()) {
                List<QuotationSummary> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new QuotationSummary(
                            rs.getLong("quotation_id"),
                            rs.getString("quotation_number"),
                            rs.getString("customer_name"),
                            rs.getString("status"),
                            rs.getDate("valid_until"),
                            rs.getBigDecimal("total_amount")
                    ));
                }
                return rows;
            }
        }
    }

    public static List<InvoiceSummary> listInvoices() throws SQLException {
        try (Connection conn = DB.getConnection()) {
            QuotationInvoiceSchemaInstaller.ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT invoice_id, invoice_number, customer_name, status, payment_status, balance_due, quotation_number
                    FROM invoices
                    ORDER BY created_at DESC, invoice_id DESC
                    LIMIT 300
                    """);
                 ResultSet rs = ps.executeQuery()) {
                List<InvoiceSummary> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new InvoiceSummary(
                            rs.getLong("invoice_id"),
                            rs.getString("invoice_number"),
                            rs.getString("customer_name"),
                            rs.getString("status"),
                            rs.getString("payment_status"),
                            rs.getBigDecimal("balance_due"),
                            rs.getString("quotation_number")
                    ));
                }
                return rows;
            }
        }
    }

    public static List<DeliverySummary> listDeliveries() throws SQLException {
        try (Connection conn = DB.getConnection()) {
            QuotationInvoiceSchemaInstaller.ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT de.invoice_delivery_event_id, de.delivery_number, so.invoice_number,
                           so.customer_name, de.delivery_method, so.balance_due, de.created_at,
                           CASE WHEN EXISTS(SELECT 1 FROM invoice_audit_log a WHERE a.invoice_id=de.invoice_id AND a.action_type='DELIVERY_CANCELLED' AND a.field_name='cancelled_document_id' AND a.old_value=de.invoice_delivery_event_id::text) THEN 'CANCELLED' ELSE 'POSTED' END AS cancellation_status
                    FROM invoice_delivery_events de
                    JOIN invoices so ON so.invoice_id = de.invoice_id
                    ORDER BY de.created_at DESC, de.invoice_delivery_event_id DESC
                    LIMIT 300
                    """);
                 ResultSet rs = ps.executeQuery()) {
                List<DeliverySummary> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new DeliverySummary(
                            rs.getLong("invoice_delivery_event_id"),
                            rs.getString("delivery_number"),
                            rs.getString("invoice_number"),
                            rs.getString("customer_name"),
                            rs.getString("delivery_method"),
                            rs.getBigDecimal("balance_due"),
                            rs.getString("created_at"), rs.getString("cancellation_status")
                    ));
                }
                return rows;
            }
        }
    }

    public static List<PaymentSummary> listPayments() throws SQLException {
        try (Connection conn = DB.getConnection()) {
            QuotationInvoiceSchemaInstaller.ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT t.transaction_id, t.customer_id, i.invoice_number,
                           COALESCE(i.customer_name, ca.name, '') AS customer_name,
                           ABS(t.amount) AS payment_amount,
                           COALESCE(t.payment_method, '') AS payment_method,
                           COALESCE(t.payment_reference, '') AS payment_reference,
                           COALESCE(t.user_name, '') AS taken_by, t.created_at,
                           CASE WHEN EXISTS(SELECT 1 FROM invoice_audit_log a WHERE a.invoice_id=t.invoice_id AND a.action_type='PAYMENT_CANCELLED' AND a.field_name='cancelled_document_id' AND a.old_value=t.transaction_id::text) THEN 'CANCELLED' ELSE 'POSTED' END AS cancellation_status
                    FROM customer_account_transactions t
                    JOIN invoices i ON i.invoice_id = t.invoice_id
                    LEFT JOIN customer_accounts ca ON ca.customer_id = t.customer_id
                    WHERE t.transaction_type = 'PAYMENT'
                    ORDER BY t.created_at DESC, t.transaction_id DESC
                    LIMIT 500
                    """); ResultSet rs = ps.executeQuery()) {
                List<PaymentSummary> rows = new ArrayList<>();
                while (rs.next()) rows.add(new PaymentSummary(
                        rs.getLong("transaction_id"), rs.getInt("customer_id"),
                        rs.getString("invoice_number"), rs.getString("customer_name"),
                        rs.getBigDecimal("payment_amount"), rs.getString("payment_method"),
                        rs.getString("payment_reference"), rs.getString("taken_by"),
                        rs.getString("created_at"), rs.getString("cancellation_status")));
                return rows;
            }
        }
    }

    public static List<AuditEntry> listAudit() throws SQLException {
        try (Connection conn = DB.getConnection()) {
            QuotationInvoiceSchemaInstaller.ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT a.created_at AS audit_created_at, 'Quotation #' || q.quotation_number AS document,
                           a.action_type, a.field_name, a.old_value, a.new_value, a.user_name, a.reason
                    FROM quotation_audit_log a
                    JOIN quotations q ON q.quotation_id = a.quotation_id
                    UNION ALL
                    SELECT a.created_at AS audit_created_at, 'Invoice #' || o.invoice_number AS document,
                           a.action_type, a.field_name, a.old_value, a.new_value, a.user_name, a.reason
                    FROM invoice_audit_log a
                    JOIN invoices o ON o.invoice_id = a.invoice_id
                    ORDER BY audit_created_at DESC
                    LIMIT 400
                    """);
                 ResultSet rs = ps.executeQuery()) {
                List<AuditEntry> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new AuditEntry(
                            rs.getString("audit_created_at"),
                            rs.getString("document"),
                            rs.getString("action_type"),
                            rs.getString("field_name"),
                            rs.getString("old_value"),
                            rs.getString("new_value"),
                            rs.getString("user_name"),
                            rs.getString("reason")
                    ));
                }
                return rows;
            }
        }
    }

    public static List<PaymentSummary> listInvoicePayments(long invoiceId) throws SQLException {
        try (Connection conn = DB.getConnection(); PreparedStatement ps = conn.prepareStatement("""
                SELECT t.transaction_id, t.customer_id, i.invoice_number,
                       COALESCE(i.customer_name, ca.name, '') AS customer_name,
                       ABS(t.amount) AS payment_amount, COALESCE(t.payment_method, '') AS payment_method,
                       COALESCE(t.payment_reference, '') AS payment_reference,
                       COALESCE(t.user_name, '') AS taken_by, t.created_at,
                           CASE WHEN EXISTS(SELECT 1 FROM invoice_audit_log a WHERE a.invoice_id=t.invoice_id AND a.action_type='PAYMENT_CANCELLED' AND a.field_name='cancelled_document_id' AND a.old_value=t.transaction_id::text) THEN 'CANCELLED' ELSE 'POSTED' END AS cancellation_status
                FROM customer_account_transactions t
                JOIN invoices i ON i.invoice_id = t.invoice_id
                LEFT JOIN customer_accounts ca ON ca.customer_id = t.customer_id
                WHERE t.transaction_type = 'PAYMENT' AND t.invoice_id = ?
                ORDER BY t.created_at DESC, t.transaction_id DESC
                LIMIT 500
                """)) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                List<PaymentSummary> rows = new ArrayList<>();
                while (rs.next()) rows.add(new PaymentSummary(rs.getLong("transaction_id"), rs.getInt("customer_id"),
                        rs.getString("invoice_number"), rs.getString("customer_name"), rs.getBigDecimal("payment_amount"),
                        rs.getString("payment_method"), rs.getString("payment_reference"), rs.getString("taken_by"), rs.getString("created_at"), rs.getString("cancellation_status")));
                return rows;
            }
        }
    }

    public static List<CustomerOption> listCustomers() throws SQLException {
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT customer_id, account_number, name, COALESCE((SELECT LOWER(BTRIM(ct.name)) IN ('business','businesses') FROM customer_types ct WHERE ct.customer_type_id=customer_accounts.customer_type_id),FALSE) AS business_account,COALESCE(invoice_discount_enabled,FALSE),COALESCE(invoice_discount_percent,0),COALESCE(phone,'')
                     FROM customer_accounts
                     WHERE is_active = TRUE
                     ORDER BY business_account DESC, name
                     LIMIT 500
                     """);
             ResultSet rs = ps.executeQuery()) {
            List<CustomerOption> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new CustomerOption(
                        rs.getInt("customer_id"),
                        rs.getString("account_number"),
                        rs.getString("name"),
                        rs.getBoolean("business_account"),rs.getBoolean(5),rs.getBigDecimal(6),rs.getString(7)
                ));
            }
            return rows;
        }
    }

    public static List<CustomerOption> searchCustomers(String searchText) throws SQLException {
        String search = searchText == null ? "" : searchText.trim();
        String pattern = "%" + search + "%";
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT customer_id, account_number, name, COALESCE((SELECT LOWER(BTRIM(ct.name)) IN ('business','businesses') FROM customer_types ct WHERE ct.customer_type_id=customer_accounts.customer_type_id),FALSE) AS business_account,COALESCE(invoice_discount_enabled,FALSE),COALESCE(invoice_discount_percent,0),COALESCE(phone,'')
                     FROM customer_accounts
                     WHERE is_active = TRUE
                       AND (
                            ? = ''
                            OR name ILIKE ?
                            OR COALESCE(account_number, '') ILIKE ?
                            OR COALESCE(phone, '') ILIKE ?
                            OR COALESCE(email, '') ILIKE ?
                       )
                     ORDER BY business_account DESC, name
                     LIMIT 75
                     """)) {
            ps.setString(1, search);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            ps.setString(4, pattern);
            ps.setString(5, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                List<CustomerOption> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new CustomerOption(
                            rs.getInt("customer_id"),
                            rs.getString("account_number"),
                            rs.getString("name"),
                            rs.getBoolean("business_account"),rs.getBoolean(5),rs.getBigDecimal(6),rs.getString(7)
                    ));
                }
                return rows;
            }
        }
    }

    public static QuotationEditData loadQuotationForEdit(long quotationId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            QuotationInvoiceSchemaInstaller.ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT quotation_id, quotation_number, customer_id, customer_name, status, valid_until, production_due_date, quotation_notes
                    FROM quotations
                    WHERE quotation_id = ?
                    """)) {
                ps.setLong(1, quotationId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Sales quotation not found.");
                    }
                    QuotationEditData header = new QuotationEditData(
                            rs.getLong("quotation_id"),
                            rs.getString("quotation_number"),
                            rs.getInt("customer_id"),
                            rs.getString("customer_name"),
                            rs.getString("status"),
                            rs.getDate("valid_until"),
                            rs.getDate("production_due_date"),
                            rs.getString("quotation_notes"),
                            loadQuotationLines(conn, quotationId)
                    );
                    return header;
                }
            }
        }
    }

    private static List<QuotationEditLine> loadQuotationLines(Connection conn, long quotationId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT product_id, item_name, sku, quantity, unit_price, original_unit_price,
                       discount_percent, delivery_method, line_notes,
                       price_override_reason, price_override_by_user_id, price_override_by_name, custom_configuration
                FROM quotation_lines
                WHERE quotation_id = ?
                ORDER BY sort_order, quotation_line_id
                """)) {
            ps.setLong(1, quotationId);
            try (ResultSet rs = ps.executeQuery()) {
                List<QuotationEditLine> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new QuotationEditLine(
                            rs.getObject("product_id") == null ? null : rs.getInt("product_id"),
                            rs.getString("item_name"),
                            rs.getString("sku"),
                            rs.getInt("quantity"),
                            rs.getBigDecimal("unit_price"),
                            rs.getBigDecimal("original_unit_price"),
                            rs.getBigDecimal("discount_percent"),
                            rs.getString("delivery_method"),
                            rs.getString("line_notes"),
                            rs.getString("price_override_reason"),
                            rs.getObject("price_override_by_user_id") == null ? null : rs.getInt("price_override_by_user_id"),
                            rs.getString("price_override_by_name"),
                            rs.getString("custom_configuration")==null?null:LanJson.create().fromJson(rs.getString("custom_configuration"),ServerQuotationInvoiceService.CustomLineInput.class)
                    ));
                }
                return rows;
            }
        }
    }

    public static List<ProductOption> listProducts() throws SQLException {
        return searchProducts("");
    }

    public static List<ProductOption> searchProducts(String searchText) throws SQLException {
        String search = searchText == null ? "" : searchText.trim();
        Integer locationId = ServerRequestIdentity.locationId();
        String sql = """
                     SELECT p.product_id,
                            p.name,
                            COALESCE(p.sku, '') AS sku,
                            COALESCE(p.barcode, '') AS barcode,
                            COALESCE(p.description, '') AS description,
                            p.price
                     FROM products p
                     WHERE p.is_active = TRUE
                       AND %s
                     ORDER BY name
                     LIMIT 75
                     """.formatted(ProductSearchHelper.predicate("p", locationId, search));
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ProductSearchHelper.bindTokens(ps, 1, search);
            try (ResultSet rs = ps.executeQuery()) {
                List<ProductOption> rows = new ArrayList<>();
                rows.add(new ProductOption(null, "Manual line", "", "", "", BigDecimal.ZERO));
                while (rs.next()) {
                    rows.add(new ProductOption(
                            rs.getInt("product_id"),
                            rs.getString("name"),
                            rs.getString("sku"),
                            rs.getString("barcode"),
                            rs.getString("description"),
                            rs.getBigDecimal("price")
                    ));
                }
                return rows;
            }
        }
    }

    public static List<DeliverableLine> listDeliverableLines(long invoiceId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            QuotationInvoiceSchemaInstaller.ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT sol.invoice_line_id, sol.product_id, sol.item_name,
                           sol.quantity_invoiced, sol.quantity_delivered,
                           sol.quantity_invoiced - sol.quantity_delivered AS remaining,
                           so.location_id
                    FROM invoice_lines sol
                    JOIN invoices so ON so.invoice_id = sol.invoice_id
                    WHERE sol.invoice_id = ?
                      AND sol.custom_item_id IS NULL
                      AND sol.quantity_invoiced > sol.quantity_delivered
                    ORDER BY sol.sort_order, sol.invoice_line_id
                    """)) {
                ps.setLong(1, invoiceId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<DeliverableLine> rows = new ArrayList<>();
                    while (rs.next()) {
                        Integer productId = rs.getObject("product_id") == null ? null : rs.getInt("product_id");
                        Integer locationId = rs.getObject("location_id") == null ? null : rs.getInt("location_id");
                        rows.add(new DeliverableLine(
                                rs.getLong("invoice_line_id"),
                                productId,
                                rs.getString("item_name"),
                                rs.getInt("quantity_invoiced"),
                                rs.getInt("quantity_delivered"),
                                rs.getInt("remaining"),
                                ServerQuotationInvoiceService.availableStock(conn, productId, locationId)
                        ));
                    }
                    return rows;
                }
            }
        }
    }

    public static InvoiceFinancials loadInvoiceFinancials(long invoiceId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            QuotationInvoiceSchemaInstaller.ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT i.invoice_id, i.invoice_number, i.total_amount, i.amount_paid, i.balance_due,
                           COALESCE(ca.current_balance, 0) AS customer_balance,
                           COALESCE(ca.credit_limit, 0) AS credit_limit,
                           GREATEST(COALESCE(ca.credit_limit, 0) - GREATEST(COALESCE(ca.current_balance, 0) - (COALESCE((SELECT SUM(pm.amount-pm.amount_paid) FROM phone_minutes_sales pm WHERE pm.customer_id=ca.customer_id AND pm.payment_method='CREDIT' AND pm.reversed_at IS NULL),0)+COALESCE((SELECT SUM(mr.document_balance) FROM sync_cross_store_customer_history_cache mr WHERE mr.customer_id=ca.customer_id AND mr.event_type='MINUTES' AND mr.cache_status='CURRENT'),0)),0), 0) AS available_credit,
                           i.customer_id,
                           COALESCE(ca.name, '') AS customer_name,
                           COALESCE(ca.require_charge_authorization, FALSE) AS require_charge_authorization,
                           EXISTS (SELECT 1 FROM customer_account_transactions t
                                   WHERE t.invoice_id=i.invoice_id AND t.transaction_type='INVOICE_CREDIT') AS account_charged
                    FROM invoices i
                    LEFT JOIN customer_accounts ca ON ca.customer_id = i.customer_id
                    WHERE i.invoice_id = ?
                    """)) {
                ps.setLong(1, invoiceId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Sales invoice not found.");
                    }
                    BigDecimal total = rs.getBigDecimal("total_amount");
                    BigDecimal paid = rs.getBigDecimal("amount_paid");
                    BigDecimal due = rs.getBigDecimal("balance_due");
                    if (paid != null && paid.signum() == 0 && due != null && due.signum() > 0
                            && !rs.getBoolean("account_charged") && total != null
                            && ServerQuotationInvoiceService.payableTotal(total).signum() > 0) {
                        total = ServerQuotationInvoiceService.payableTotal(total);
                        due = total;
                    }
                    return new InvoiceFinancials(
                            rs.getLong("invoice_id"),
                            rs.getString("invoice_number"),
                            total,
                            paid,
                            due,
                            rs.getBigDecimal("customer_balance"),
                            rs.getBigDecimal("credit_limit"),
                            rs.getBigDecimal("available_credit"), rs.getInt("customer_id"),
                            rs.getString("customer_name"), rs.getBoolean("require_charge_authorization")
                    );
                }
            }
        }
    }

    public record QuotationSummary(long quotationId, String quotationNumber, String customerName, String status,
                               java.sql.Date validUntil, BigDecimal totalAmount) {
    }

    public record QuotationEditData(long quotationId, String quotationNumber, int customerId, String customerName,
                                String status, Date validUntil, Date productionDueDate,String notes, List<QuotationEditLine> lines) {
    }

    public record QuotationEditLine(Integer productId, String itemName, String sku, int quantity,
                                BigDecimal unitPrice, BigDecimal originalUnitPrice,
                                BigDecimal discountPercent, String deliveryMethod, String notes,
                                String priceOverrideReason, Integer priceOverrideByUserId,
                                String priceOverrideByName,ServerQuotationInvoiceService.CustomLineInput custom) {
    }

    public record InvoiceSummary(long invoiceId, String invoiceNumber, String customerName, String status,
                               String paymentStatus, BigDecimal balanceDue, String quotationNumber) {
    }

    public record DeliverySummary(long deliveryEventId, String deliveryNumber, String invoiceNumber,
                                  String customerName, String deliveryMethod, BigDecimal balanceDue, String createdAt, String status) {
    }

    public record PaymentSummary(long transactionId, int customerId, String invoiceNumber,
                                 String customerName, BigDecimal amount, String paymentMethod,
                                 String paymentReference, String takenBy, String createdAt, String status) {
    }

    public record InvoiceFinancials(long invoiceId, String invoiceNumber, BigDecimal totalAmount,
                                  BigDecimal amountPaid, BigDecimal balanceDue, BigDecimal customerBalance,
                                  BigDecimal creditLimit, BigDecimal availableCredit,int customerId,String customerName,boolean requireChargeAuthorization) {
    }

    public record AuditEntry(String createdAt, String document, String actionType, String fieldName,
                             String oldValue, String newValue, String userName, String reason) {
    }

    public record CustomerOption(int customerId, String accountNumber, String name, boolean business,boolean invoiceDiscountEnabled,BigDecimal invoiceDiscountPercent,String phone) {
        public CustomerOption(int customerId,String accountNumber,String name,boolean business){this(customerId,accountNumber,name,business,false,BigDecimal.ZERO,null);}
        public CustomerOption(int customerId,String accountNumber,String name,boolean business,boolean enabled,BigDecimal percent){this(customerId,accountNumber,name,business,enabled,percent,null);}
        @Override
        public String toString() {
            return (business ? "[Business] " : "") + name
                    + (accountNumber == null || accountNumber.isBlank() ? "" : " (" + accountNumber + ")");
        }
    }

    public record ProductOption(Integer productId, String name, String sku, String barcode, String description, BigDecimal price) {
        @Override
        public String toString() {
            if (productId == null) {
                return name;
            }
            String code = sku == null || sku.isBlank() ? barcode : sku;
            return name + (code == null || code.isBlank() ? "" : " (" + code + ")");
        }
    }

    public record DeliverableLine(long invoiceLineId, Integer productId, String itemName, int quantityInvoiceed,
                                  int quantityDelivered, int remaining, int availableStock) {
    }
}
