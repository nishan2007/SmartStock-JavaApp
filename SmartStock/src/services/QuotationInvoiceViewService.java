package services;

import data.DB;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class QuotationInvoiceViewService {
    private QuotationInvoiceViewService() {
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
                           so.customer_name, de.delivery_method, so.balance_due, de.created_at
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
                            rs.getString("created_at")
                    ));
                }
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

    public static List<CustomerOption> listCustomers() throws SQLException {
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT customer_id, account_number, name, is_business
                     FROM customer_accounts
                     WHERE is_active = TRUE
                     ORDER BY is_business DESC, name
                     LIMIT 500
                     """);
             ResultSet rs = ps.executeQuery()) {
            List<CustomerOption> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new CustomerOption(
                        rs.getInt("customer_id"),
                        rs.getString("account_number"),
                        rs.getString("name"),
                        rs.getBoolean("is_business")
                ));
            }
            return rows;
        }
    }

    public static QuotationEditData loadQuotationForEdit(long quotationId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            QuotationInvoiceSchemaInstaller.ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT quotation_id, quotation_number, customer_id, customer_name, status, valid_until, quotation_notes
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
                       price_override_reason, price_override_by_user_id, price_override_by_name
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
                            rs.getString("price_override_by_name")
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
        String pattern = "%" + search + "%";
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT p.product_id,
                            p.name,
                            COALESCE(p.sku, '') AS sku,
                            COALESCE(p.barcode, '') AS barcode,
                            p.price
                     FROM products p
                     WHERE p.is_active = TRUE
                       AND (
                            ? = ''
                            OR p.name ILIKE ?
                            OR COALESCE(p.sku, '') ILIKE ?
                            OR COALESCE(p.barcode, '') ILIKE ?
                            OR EXISTS (
                                SELECT 1
                                FROM product_barcodes pb
                                WHERE pb.product_id = p.product_id
                                  AND pb.barcode ILIKE ?
                            )
                       )
                     ORDER BY name
                     LIMIT 75
                     """)) {
            ps.setString(1, search);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            ps.setString(4, pattern);
            ps.setString(5, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                List<ProductOption> rows = new ArrayList<>();
                rows.add(new ProductOption(null, "Manual line", "", "", BigDecimal.ZERO));
                while (rs.next()) {
                    rows.add(new ProductOption(
                            rs.getInt("product_id"),
                            rs.getString("name"),
                            rs.getString("sku"),
                            rs.getString("barcode"),
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
                                QuotationInvoiceService.availableStock(conn, productId, locationId)
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
                    SELECT invoice_id, invoice_number, total_amount, amount_paid, balance_due
                    FROM invoices
                    WHERE invoice_id = ?
                    """)) {
                ps.setLong(1, invoiceId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Sales invoice not found.");
                    }
                    return new InvoiceFinancials(
                            rs.getLong("invoice_id"),
                            rs.getString("invoice_number"),
                            rs.getBigDecimal("total_amount"),
                            rs.getBigDecimal("amount_paid"),
                            rs.getBigDecimal("balance_due")
                    );
                }
            }
        }
    }

    public record QuotationSummary(long quotationId, String quotationNumber, String customerName, String status,
                               java.sql.Date validUntil, BigDecimal totalAmount) {
    }

    public record QuotationEditData(long quotationId, String quotationNumber, int customerId, String customerName,
                                String status, Date validUntil, String notes, List<QuotationEditLine> lines) {
    }

    public record QuotationEditLine(Integer productId, String itemName, String sku, int quantity,
                                BigDecimal unitPrice, BigDecimal originalUnitPrice,
                                BigDecimal discountPercent, String deliveryMethod, String notes,
                                String priceOverrideReason, Integer priceOverrideByUserId,
                                String priceOverrideByName) {
    }

    public record InvoiceSummary(long invoiceId, String invoiceNumber, String customerName, String status,
                               String paymentStatus, BigDecimal balanceDue, String quotationNumber) {
    }

    public record DeliverySummary(long deliveryEventId, String deliveryNumber, String invoiceNumber,
                                  String customerName, String deliveryMethod, BigDecimal balanceDue, String createdAt) {
    }

    public record InvoiceFinancials(long invoiceId, String invoiceNumber, BigDecimal totalAmount,
                                  BigDecimal amountPaid, BigDecimal balanceDue) {
    }

    public record AuditEntry(String createdAt, String document, String actionType, String fieldName,
                             String oldValue, String newValue, String userName, String reason) {
    }

    public record CustomerOption(int customerId, String accountNumber, String name, boolean business) {
        @Override
        public String toString() {
            return (business ? "[Business] " : "") + name
                    + (accountNumber == null || accountNumber.isBlank() ? "" : " (" + accountNumber + ")");
        }
    }

    public record ProductOption(Integer productId, String name, String sku, String barcode, BigDecimal price) {
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
