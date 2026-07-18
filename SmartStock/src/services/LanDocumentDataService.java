package services;

import Receipt.CustomOrderSlipData;
import Receipt.ReceiptData;
import Receipt.ReceiptItem;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Server-owned document loaders shared by API and email-outbox operations. */
final class LanDocumentDataService {
    private LanDocumentDataService() { }

    static ReceiptData saleReceipt(Connection connection, int saleId, int userId, int locationId,
                                   BigDecimal cashCollected, BigDecimal changeDue) throws SQLException {
        requireAny(connection, userId, "MAKE_SALE", "VIEW_SALES", "PROCESS_RETURNS");
        String sql = """
                SELECT s.sale_id,COALESCE(s.receipt_number,''),s.created_at,
                       COALESCE(l.name,'Unknown Store'),
                       COALESCE(s.user_name,u.full_name,u.username,'Unknown'),
                       COALESCE(ca.name,''),COALESCE(ca.account_number,''),
                       COALESCE(s.payment_method,''),COALESCE(s.payment_status,'PAID'),
                       COALESCE(s.receipt_device_id,''),COALESCE(s.subtotal_amount,s.total_amount,0),
                       COALESCE(s.discount_percent,0),COALESCE(s.discount_amount,0),
                       COALESCE(s.vat_amount,0),COALESCE(s.vat_rate_percent,0),COALESCE(s.vat_mode,''),
                       COALESCE(s.total_amount,0),COALESCE(s.amount_paid,0),COALESCE(s.returned_amount,0)
                FROM sales s
                LEFT JOIN users u ON u.user_id=s.user_id
                LEFT JOIN locations l ON l.location_id=s.location_id
                LEFT JOIN customer_accounts ca ON ca.customer_id=s.customer_id
                WHERE s.sale_id=? AND s.location_id=?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, saleId);
            statement.setInt(2, locationId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw rule(404, "SALE_NOT_FOUND", "Sale was not found for this store.");
                List<ReceiptItem> items = saleItems(connection, saleId);
                return new ReceiptData(rows.getInt(1), rows.getString(2), rows.getTimestamp(3), rows.getString(4),
                        rows.getString(5), rows.getString(6), rows.getString(7), rows.getString(8), rows.getString(9),
                        rows.getString(10), rows.getBigDecimal(11), rows.getBigDecimal(12), rows.getBigDecimal(13),
                        rows.getBigDecimal(14), rows.getBigDecimal(15), rows.getString(16), rows.getBigDecimal(17),
                        rows.getBigDecimal(18), rows.getBigDecimal(19), cashCollected, changeDue, items);
            }
        }
    }

    private static List<ReceiptItem> saleItems(Connection connection, int saleId) throws SQLException {
        List<ReceiptItem> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COALESCE(p.name,'Deleted Item') || CASE WHEN COALESCE(p.size,'')='' THEN '' ELSE ' ('||p.size||')' END,
                       COALESCE(p.sku,''),COALESCE(si.quantity,0),COALESCE(si.original_unit_price,si.unit_price,0),
                       COALESCE(si.unit_price,0),COALESCE(si.discount_percent,0),
                       COALESCE(si.quantity,0)*COALESCE(si.unit_price,0)
                FROM sale_items si LEFT JOIN products p ON p.product_id=si.product_id
                WHERE si.sale_id=? ORDER BY si.sale_item_id
                """)) {
            statement.setInt(1, saleId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    items.add(new ReceiptItem(rows.getString(1), rows.getString(2), rows.getInt(3),
                            rows.getBigDecimal(4), rows.getBigDecimal(5), rows.getBigDecimal(6), rows.getBigDecimal(7)));
                }
            }
        }
        return items;
    }

    static CustomOrderSlipData customOrderSlip(Connection connection, String number, int userId, int locationId) throws SQLException {
        requireAny(connection, userId, "MANAGE_CUSTOM_ORDERS", "CREATE_CUSTOM_ORDER", "VIEW_ASSIGNED_CUSTOM_ORDERS");
        String sql = """
                SELECT co.order_number,co.customer_name,co.customer_phone,COALESCE(ca.account_number,''),
                       co.due_date,co.created_at,co.taken_by_name,co.location_name,co.device_name,
                       co.payment_method,co.payment_reference,co.payment_status,co.total_amount,co.amount_paid,
                       co.balance_due,co.order_notes,col.item_name,col.variant_name,col.customization_details,
                       col.order_instructions,col.line_total
                FROM custom_orders co
                LEFT JOIN customer_accounts ca ON ca.customer_id=co.customer_id
                LEFT JOIN custom_order_lines col ON col.custom_order_id=co.custom_order_id
                WHERE co.order_number=? AND co.location_id=?
                ORDER BY col.sort_order,col.custom_order_line_id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, number);
            statement.setInt(2, locationId);
            try (ResultSet rows = statement.executeQuery()) {
                CustomOrderSlipData header = null;
                List<CustomOrderSlipData.Line> lines = new ArrayList<>();
                while (rows.next()) {
                    if (header == null) {
                        java.sql.Date due = rows.getDate(5);
                        header = new CustomOrderSlipData(rows.getString(1),rows.getString(2),rows.getString(3),
                                rows.getString(4),due==null?null:due.toLocalDate(),rows.getTimestamp(6),rows.getString(7),
                                rows.getString(8),rows.getString(9),rows.getString(10),rows.getString(11),rows.getString(12),
                                zero(rows.getBigDecimal(13)),zero(rows.getBigDecimal(14)),zero(rows.getBigDecimal(15)),
                                rows.getString(16),lines);
                    }
                    if (rows.getString(17) != null && !rows.getString(17).isBlank()) {
                        lines.add(new CustomOrderSlipData.Line(rows.getString(17),rows.getString(18),rows.getString(19),
                                rows.getString(20),zero(rows.getBigDecimal(21))));
                    }
                }
                if (header == null) throw rule(404,"ORDER_NOT_FOUND","Custom order was not found.");
                return header;
            }
        }
    }

    private static void requireAny(Connection connection, int userId, String... keys) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM users u
                JOIN role_permissions rp ON rp.role_id=u.role_id
                JOIN permissions p ON p.permission_id=rp.permission_id
                WHERE u.user_id=? AND UPPER(p.permission_key)=ANY(?::text[]) LIMIT 1
                """)) {
            statement.setInt(1,userId);
            statement.setArray(2,connection.createArrayOf("text",keys));
            try(ResultSet rows=statement.executeQuery()) { if(rows.next()) return; }
        }
        throw rule(403,"PERMISSION_DENIED","You do not have permission to view this document.");
    }

    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static RuleViolation rule(int status,String code,String message) { return new RuleViolation(status,code,message); }

    static final class RuleViolation extends SQLException {
        private final int status;
        private final String code;
        private final String safeMessage;
        RuleViolation(int status,String code,String message){super(message);this.status=status;this.code=code;this.safeMessage=message;}
        int status(){return status;} String code(){return code;} String safeMessage(){return safeMessage;}
    }
}
