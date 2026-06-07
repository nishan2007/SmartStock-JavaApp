package services;

import data.DB;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class SalesQuoteOrderViewService {
    private SalesQuoteOrderViewService() {
    }

    public static List<QuoteSummary> listQuotes() throws SQLException {
        try (Connection conn = DB.getConnection()) {
            SalesQuoteOrderSchemaInstaller.ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT sales_quote_id, quote_number, customer_name, status, valid_until, total_amount
                    FROM sales_quotes
                    ORDER BY created_at DESC, sales_quote_id DESC
                    LIMIT 300
                    """);
                 ResultSet rs = ps.executeQuery()) {
                List<QuoteSummary> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new QuoteSummary(
                            rs.getLong("sales_quote_id"),
                            rs.getString("quote_number"),
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

    public static List<OrderSummary> listOrders() throws SQLException {
        try (Connection conn = DB.getConnection()) {
            SalesQuoteOrderSchemaInstaller.ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT sales_order_id, order_number, customer_name, status, payment_status, balance_due, quote_number
                    FROM sales_orders
                    ORDER BY created_at DESC, sales_order_id DESC
                    LIMIT 300
                    """);
                 ResultSet rs = ps.executeQuery()) {
                List<OrderSummary> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new OrderSummary(
                            rs.getLong("sales_order_id"),
                            rs.getString("order_number"),
                            rs.getString("customer_name"),
                            rs.getString("status"),
                            rs.getString("payment_status"),
                            rs.getBigDecimal("balance_due"),
                            rs.getString("quote_number")
                    ));
                }
                return rows;
            }
        }
    }

    public static List<DeliverySummary> listDeliveries() throws SQLException {
        try (Connection conn = DB.getConnection()) {
            SalesQuoteOrderSchemaInstaller.ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT de.sales_order_delivery_event_id, de.delivery_number, so.order_number,
                           so.customer_name, de.delivery_method, so.balance_due, de.created_at
                    FROM sales_order_delivery_events de
                    JOIN sales_orders so ON so.sales_order_id = de.sales_order_id
                    ORDER BY de.created_at DESC, de.sales_order_delivery_event_id DESC
                    LIMIT 300
                    """);
                 ResultSet rs = ps.executeQuery()) {
                List<DeliverySummary> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new DeliverySummary(
                            rs.getLong("sales_order_delivery_event_id"),
                            rs.getString("delivery_number"),
                            rs.getString("order_number"),
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
            SalesQuoteOrderSchemaInstaller.ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT a.created_at AS audit_created_at, 'Quote #' || q.quote_number AS document,
                           a.action_type, a.field_name, a.old_value, a.new_value, a.user_name, a.reason
                    FROM sales_quote_audit_log a
                    JOIN sales_quotes q ON q.sales_quote_id = a.sales_quote_id
                    UNION ALL
                    SELECT a.created_at AS audit_created_at, 'Order #' || o.order_number AS document,
                           a.action_type, a.field_name, a.old_value, a.new_value, a.user_name, a.reason
                    FROM sales_order_audit_log a
                    JOIN sales_orders o ON o.sales_order_id = a.sales_order_id
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

    public static List<ProductOption> listProducts() throws SQLException {
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT product_id, name, COALESCE(sku, '') AS sku, price
                     FROM products
                     WHERE is_active = TRUE
                     ORDER BY name
                     LIMIT 1000
                     """);
             ResultSet rs = ps.executeQuery()) {
            List<ProductOption> rows = new ArrayList<>();
            rows.add(new ProductOption(null, "Manual line", "", BigDecimal.ZERO));
            while (rs.next()) {
                rows.add(new ProductOption(
                        rs.getInt("product_id"),
                        rs.getString("name"),
                        rs.getString("sku"),
                        rs.getBigDecimal("price")
                ));
            }
            return rows;
        }
    }

    public static List<DeliverableLine> listDeliverableLines(long orderId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            SalesQuoteOrderSchemaInstaller.ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT sol.sales_order_line_id, sol.product_id, sol.item_name,
                           sol.quantity_ordered, sol.quantity_delivered,
                           sol.quantity_ordered - sol.quantity_delivered AS remaining,
                           so.location_id
                    FROM sales_order_lines sol
                    JOIN sales_orders so ON so.sales_order_id = sol.sales_order_id
                    WHERE sol.sales_order_id = ?
                      AND sol.quantity_ordered > sol.quantity_delivered
                    ORDER BY sol.sort_order, sol.sales_order_line_id
                    """)) {
                ps.setLong(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<DeliverableLine> rows = new ArrayList<>();
                    while (rs.next()) {
                        Integer productId = rs.getObject("product_id") == null ? null : rs.getInt("product_id");
                        Integer locationId = rs.getObject("location_id") == null ? null : rs.getInt("location_id");
                        rows.add(new DeliverableLine(
                                rs.getLong("sales_order_line_id"),
                                productId,
                                rs.getString("item_name"),
                                rs.getInt("quantity_ordered"),
                                rs.getInt("quantity_delivered"),
                                rs.getInt("remaining"),
                                SalesQuoteOrderService.availableStock(conn, productId, locationId)
                        ));
                    }
                    return rows;
                }
            }
        }
    }

    public record QuoteSummary(long quoteId, String quoteNumber, String customerName, String status,
                               java.sql.Date validUntil, BigDecimal totalAmount) {
    }

    public record OrderSummary(long orderId, String orderNumber, String customerName, String status,
                               String paymentStatus, BigDecimal balanceDue, String quoteNumber) {
    }

    public record DeliverySummary(long deliveryEventId, String deliveryNumber, String orderNumber,
                                  String customerName, String deliveryMethod, BigDecimal balanceDue, String createdAt) {
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

    public record ProductOption(Integer productId, String name, String sku, BigDecimal price) {
        @Override
        public String toString() {
            return productId == null ? name : name + (sku == null || sku.isBlank() ? "" : " (" + sku + ")");
        }
    }

    public record DeliverableLine(long orderLineId, Integer productId, String itemName, int quantityOrdered,
                                  int quantityDelivered, int remaining, int availableStock) {
    }
}
