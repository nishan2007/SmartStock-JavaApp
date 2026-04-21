package Receipt;

import data.DB;
import ui.helpers.StoreTimeZoneHelper;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ReceiptBuilder {
    private ReceiptBuilder() {
    }

    public static ReceiptData loadSaleReceipt(int saleId, BigDecimal cashCollected, BigDecimal changeDue) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            ReceiptData sale = loadSaleHeader(conn, saleId, cashCollected, changeDue);
            List<ReceiptItem> items = loadSaleItems(conn, saleId);
            return new ReceiptData(
                    sale.getSaleId(),
                    sale.getReceiptNumber(),
                    sale.getSaleTime(),
                    sale.getStoreName(),
                    sale.getCashierName(),
                    sale.getCustomerName(),
                    sale.getAccountNumber(),
                    sale.getPaymentMethod(),
                    sale.getPaymentStatus(),
                    sale.getDeviceId(),
                    sale.getSubtotalAmount(),
                    sale.getDiscountPercent(),
                    sale.getDiscountAmount(),
                    sale.getTotalAmount(),
                    sale.getAmountPaid(),
                    sale.getReturnedAmount(),
                    sale.getCashCollected(),
                    sale.getChangeDue(),
                    items
            );
        }
    }

    private static ReceiptData loadSaleHeader(Connection conn, int saleId, BigDecimal cashCollected, BigDecimal changeDue) throws SQLException {
        String sql = """
                SELECT s.sale_id,
                       COALESCE(s.receipt_number, '') AS receipt_number,
                       (s.created_at AT TIME ZONE ?) AS local_created_at,
                       COALESCE(l.name, 'Unknown Store') AS store_name,
                       COALESCE(s.user_name, u.full_name, u.username, 'Unknown') AS cashier_name,
                       COALESCE(ca.name, '') AS customer_name,
                       COALESCE(ca.account_number, '') AS account_number,
                       COALESCE(s.payment_method, '') AS payment_method,
                       COALESCE(s.payment_status, 'PAID') AS payment_status,
                       COALESCE(s.receipt_device_id, '') AS receipt_device_id,
                       COALESCE(s.subtotal_amount, s.total_amount, 0) AS subtotal_amount,
                       COALESCE(s.discount_percent, 0) AS discount_percent,
                       COALESCE(s.discount_amount, 0) AS discount_amount,
                       COALESCE(s.total_amount, 0) AS total_amount,
                       COALESCE(s.amount_paid, 0) AS amount_paid,
                       COALESCE(s.returned_amount, 0) AS returned_amount
                FROM sales s
                LEFT JOIN users u ON u.user_id = s.user_id
                LEFT JOIN locations l ON l.location_id = s.location_id
                LEFT JOIN customer_accounts ca ON ca.customer_id = s.customer_id
                WHERE s.sale_id = ?
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, StoreTimeZoneHelper.getStoreZoneId());
            ps.setInt(2, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Sale was not found for receipt preview.");
                }

                return new ReceiptData(
                        rs.getInt("sale_id"),
                        rs.getString("receipt_number"),
                        rs.getTimestamp("local_created_at"),
                        rs.getString("store_name"),
                        rs.getString("cashier_name"),
                        rs.getString("customer_name"),
                        rs.getString("account_number"),
                        rs.getString("payment_method"),
                        rs.getString("payment_status"),
                        rs.getString("receipt_device_id"),
                        rs.getBigDecimal("subtotal_amount"),
                        rs.getBigDecimal("discount_percent"),
                        rs.getBigDecimal("discount_amount"),
                        rs.getBigDecimal("total_amount"),
                        rs.getBigDecimal("amount_paid"),
                        rs.getBigDecimal("returned_amount"),
                        cashCollected,
                        changeDue,
                        List.of()
                );
            }
        }
    }

    private static List<ReceiptItem> loadSaleItems(Connection conn, int saleId) throws SQLException {
        String sql = """
                SELECT COALESCE(p.name, 'Deleted Item') AS product_name,
                       COALESCE(p.sku, '') AS sku,
                       COALESCE(si.quantity, 0) AS quantity,
                       COALESCE(si.original_unit_price, si.unit_price, 0) AS original_unit_price,
                       COALESCE(si.unit_price, 0) AS unit_price,
                       COALESCE(si.discount_percent, 0) AS discount_percent,
                       COALESCE(si.quantity, 0) * COALESCE(si.unit_price, 0) AS line_total
                FROM sale_items si
                LEFT JOIN products p ON p.product_id = si.product_id
                WHERE si.sale_id = ?
                ORDER BY si.sale_item_id ASC
                """;

        List<ReceiptItem> items = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new ReceiptItem(
                            rs.getString("product_name"),
                            rs.getString("sku"),
                            rs.getInt("quantity"),
                            rs.getBigDecimal("original_unit_price"),
                            rs.getBigDecimal("unit_price"),
                            rs.getBigDecimal("discount_percent"),
                            rs.getBigDecimal("line_total")
                    ));
                }
            }
        }
        return items;
    }
}
