package Receipt;

import data.DB;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CustomOrderSlipBuilder {
    private CustomOrderSlipBuilder() {
    }

    public static CustomOrderSlipData buildFromOrderNumber(String orderNumber) throws SQLException {
        String sql = """
                SELECT co.order_number, co.customer_name, co.customer_phone, co.due_date,
                       co.created_at, co.taken_by_name, co.location_name, co.device_name,
                       co.payment_method, co.payment_reference, co.payment_status,
                       co.total_amount, co.amount_paid, co.balance_due, co.order_notes,
                       col.item_name, col.variant_name, col.customization_details,
                       col.order_instructions, col.line_total
                FROM custom_orders co
                LEFT JOIN custom_order_lines col ON col.custom_order_id = co.custom_order_id
                WHERE co.order_number = ?
                ORDER BY col.sort_order, col.custom_order_line_id
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderNumber);
            try (ResultSet rs = ps.executeQuery()) {
                CustomOrderSlipData header = null;
                List<CustomOrderSlipData.Line> lines = new ArrayList<>();
                while (rs.next()) {
                    if (header == null) {
                        Date dueDate = rs.getDate("due_date");
                        header = new CustomOrderSlipData(
                                rs.getString("order_number"),
                                rs.getString("customer_name"),
                                rs.getString("customer_phone"),
                                dueDate == null ? null : dueDate.toLocalDate(),
                                rs.getTimestamp("created_at"),
                                rs.getString("taken_by_name"),
                                rs.getString("location_name"),
                                rs.getString("device_name"),
                                rs.getString("payment_method"),
                                rs.getString("payment_reference"),
                                rs.getString("payment_status"),
                                zeroIfNull(rs.getBigDecimal("total_amount")),
                                zeroIfNull(rs.getBigDecimal("amount_paid")),
                                zeroIfNull(rs.getBigDecimal("balance_due")),
                                rs.getString("order_notes"),
                                lines
                        );
                    }
                    String itemName = rs.getString("item_name");
                    if (itemName != null && !itemName.isBlank()) {
                        lines.add(new CustomOrderSlipData.Line(
                                itemName,
                                rs.getString("variant_name"),
                                rs.getString("customization_details"),
                                rs.getString("order_instructions"),
                                zeroIfNull(rs.getBigDecimal("line_total"))
                        ));
                    }
                }
                if (header == null) {
                    throw new SQLException("Custom order was not found: " + orderNumber);
                }
                return header;
            }
        }
    }

    public static CustomOrderSlipData sample() {
        return new CustomOrderSlipData(
                "CO-20260522-001",
                "Alex Customer",
                "555-0199",
                LocalDate.of(2026, 5, 30),
                Timestamp.valueOf("2026-05-22 10:30:00"),
                "Sample Cashier",
                "Main Store",
                "POS-01",
                "CASH",
                "",
                "PARTIAL",
                new BigDecimal("86.50"),
                new BigDecimal("30.00"),
                new BigDecimal("56.50"),
                "Use royal blue thread. Call before production if design is unclear.",
                List.of(
                        new CustomOrderSlipData.Line("Logo T-Shirt", "Large / Black", "Front print / 4 lines", "Place logo centered on chest.", new BigDecimal("42.50")),
                        new CustomOrderSlipData.Line("Custom Cap", "Navy", "Embroidery / Side placement", "Match thread to shirt.", new BigDecimal("44.00"))
                )
        );
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
