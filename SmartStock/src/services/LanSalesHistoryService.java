package services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Store-scoped, read-only sales history used by registers. */
final class LanSalesHistoryService {
    private static final Gson GSON = new Gson();

    private LanSalesHistoryService() { }

    static List<Map<String, Object>> history(Connection connection, JsonObject body,
                                             int userId, int locationId) throws Exception {
        requirePermission(connection, userId, "VIEW_SALES");
        HistoryRequest request = GSON.fromJson(body, HistoryRequest.class);
        String search = request == null || request.search() == null ? "" : request.search().trim();
        if (search.length() > 300) throw rule(400, "VALIDATION_ERROR", "Search text is too long.");
        ZoneId zone = storeZone(connection, locationId);
        Instant from = parseBoundary(request == null ? null : request.fromDate(), zone, false);
        Instant to = parseBoundary(request == null ? null : request.toDate(), zone, true);
        if (from != null && to != null && !from.isBefore(to))
            throw rule(400, "VALIDATION_ERROR", "From Date must not be after To Date.");

        StringBuilder salesWhere = new StringBuilder("WHERE s.location_id=? ");
        StringBuilder returnsWhere = new StringBuilder("WHERE sr.location_id=? ");
        List<Object> salesArgs = new ArrayList<>(List.of(locationId));
        List<Object> returnArgs = new ArrayList<>(List.of(locationId));
        if (!search.isEmpty()) {
            String like = "%" + search + "%";
            salesWhere.append("AND (CAST(s.sale_id AS TEXT) ILIKE ? OR COALESCE(s.receipt_number,'') ILIKE ? OR ")
                    .append("COALESCE(s.user_name,u.full_name,u.username,'') ILIKE ? OR COALESCE(l.name,'') ILIKE ? OR ")
                    .append("COALESCE(s.payment_method,'') ILIKE ? OR COALESCE(s.payment_status,'PAID') ILIKE ?) ");
            for (int i = 0; i < 6; i++) salesArgs.add(like);
            returnsWhere.append("AND (CAST(sr.return_id AS TEXT) ILIKE ? OR CAST(sr.sale_id AS TEXT) ILIKE ? OR COALESCE(sr.return_receipt_number,'') ILIKE ? OR ")
                    .append("COALESCE(s.receipt_number,'') ILIKE ? OR COALESCE(sr.user_name,u.full_name,u.username,'') ILIKE ? OR ")
                    .append("COALESCE(l.name,'') ILIKE ? OR COALESCE(sr.refund_method,'') ILIKE ? OR 'RETURN' ILIKE ?) ");
            for (int i = 0; i < 8; i++) returnArgs.add(like);
        }
        appendDate(salesWhere, salesArgs, "s.created_at", from, to);
        appendDate(returnsWhere, returnArgs, "sr.created_at", from, to);
        String sql = """
                SELECT * FROM (
                  SELECT 'SALE' transaction_type,s.sale_id,NULL::BIGINT return_id,COALESCE(s.receipt_number,'') receipt_number,''::TEXT return_receipt_number,
                    s.created_at sort_created_at,COALESCE(s.user_name,u.full_name,u.username,'Unknown') cashier_name,
                    COALESCE(l.name,'Unknown') store_name,COUNT(si.sale_item_id) item_count,
                    COALESCE(s.payment_method,'') payment_method,COALESCE(s.payment_status,'PAID') payment_status,
                    COALESCE(s.amount_paid,0) amount_paid,COALESCE(s.returned_amount,0) returned_amount,
                    COALESCE(s.discount_amount,0) discount_amount,COALESCE(s.total_amount,0) total_amount,
                    GREATEST(COALESCE(s.total_amount,0)-COALESCE(s.returned_amount,0),0) net_amount
                  FROM sales s LEFT JOIN users u ON u.user_id=s.user_id LEFT JOIN locations l ON l.location_id=s.location_id
                  LEFT JOIN sale_items si ON si.sale_id=s.sale_id %s
                  GROUP BY s.sale_id,s.receipt_number,s.created_at,cashier_name,store_name,s.payment_method,s.payment_status,
                    s.amount_paid,s.returned_amount,s.discount_amount,s.total_amount
                  UNION ALL
                  SELECT 'RETURN',sr.sale_id,sr.return_id,COALESCE(s.receipt_number,''),COALESCE(sr.return_receipt_number,''),sr.created_at,
                    COALESCE(sr.user_name,u.full_name,u.username,'Unknown'),COALESCE(l.name,'Unknown'),
                    COALESCE(SUM(sri.quantity),0),COALESCE(sr.refund_method,''),'RETURN',-COALESCE(sr.refund_amount,0),
                    COALESCE(sr.refund_amount,0),0::NUMERIC,-COALESCE(sr.refund_amount,0),-COALESCE(sr.refund_amount,0)
                  FROM sale_returns sr LEFT JOIN sales s ON s.sale_id=sr.sale_id LEFT JOIN users u ON u.user_id=sr.user_id
                  LEFT JOIN locations l ON l.location_id=sr.location_id LEFT JOIN sale_return_items sri ON sri.return_id=sr.return_id %s
                  GROUP BY sr.return_id,sr.sale_id,s.receipt_number,sr.created_at,u.full_name,u.username,l.name,
                    sr.refund_method,sr.refund_amount
                ) transactions ORDER BY sort_created_at DESC,transaction_type LIMIT 1000
                """.formatted(salesWhere, returnsWhere);
        List<Object> args = new ArrayList<>(salesArgs); args.addAll(returnArgs);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("transactionType", rs.getString("transaction_type"));
                    row.put("saleId", rs.getInt("sale_id")); row.put("returnId", rs.getObject("return_id"));
                    row.put("receiptNumber", rs.getString("receipt_number"));
                    row.put("returnReceiptNumber", rs.getString("return_receipt_number"));
                    row.put("createdAtEpochMillis", rs.getTimestamp("sort_created_at").getTime());
                    row.put("cashierName", rs.getString("cashier_name")); row.put("storeName", rs.getString("store_name"));
                    row.put("itemCount", rs.getInt("item_count")); row.put("paymentMethod", rs.getString("payment_method"));
                    row.put("paymentStatus", rs.getString("payment_status")); row.put("amountPaid", rs.getBigDecimal("amount_paid"));
                    row.put("returnedAmount", rs.getBigDecimal("returned_amount")); row.put("discountAmount", rs.getBigDecimal("discount_amount"));
                    row.put("totalAmount", rs.getBigDecimal("total_amount")); row.put("netAmount", rs.getBigDecimal("net_amount"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    static Map<String, Object> details(Connection connection, int saleId,
                                       int userId, int locationId) throws Exception {
        requirePermission(connection, userId, "VIEW_SALES");
        Map<String, Object> result = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT COALESCE(subtotal_amount,total_amount,0),COALESCE(discount_percent,0),
                       COALESCE(discount_amount,0),COALESCE(total_amount,0)
                FROM sales WHERE sale_id=? AND location_id=?
                """)) {
            ps.setInt(1, saleId); ps.setInt(2, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw rule(404, "SALE_NOT_FOUND", "Sale was not found for this store.");
                result.put("saleId", saleId); result.put("subtotalAmount", rs.getBigDecimal(1));
                result.put("discountPercent", rs.getBigDecimal(2)); result.put("discountAmount", rs.getBigDecimal(3));
                result.put("totalAmount", rs.getBigDecimal(4));
            }
        }
        result.put("items", queryItems(connection, saleId));
        result.put("returns", queryReturns(connection, saleId));
        result.put("returnItems", queryReturnItems(connection, saleId));
        result.put("overrideAudit", hasPermission(connection, userId, "VIEW_SALE_AUDIT")
                ? queryAudit(connection, saleId) : List.of());
        return result;
    }

    private static List<Map<String, Object>> queryItems(Connection c, int saleId) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT COALESCE(p.product_id,0),CASE WHEN si.is_misc_item THEN COALESCE(si.item_name,'Misc Item') ELSE COALESCE(p.name,'Deleted Item')||
                  CASE WHEN COALESCE(p.size,'')='' THEN '' ELSE ' ('||p.size||')' END END,
                  COALESCE(si.quantity,0),COALESCE(SUM(sri.quantity),0),
                  COALESCE(si.original_unit_price,si.unit_price,0),COALESCE(si.discount_percent,0),
                  COALESCE(si.discount_amount,0),COALESCE(si.unit_price,0),
                  COALESCE(si.quantity,0)*COALESCE(si.unit_price,0)
                FROM sale_items si LEFT JOIN products p ON p.product_id=si.product_id
                LEFT JOIN sale_return_items sri ON sri.sale_item_id=si.sale_item_id
                WHERE si.sale_id=?
                GROUP BY si.sale_item_id,p.product_id,p.name,p.size,si.item_name,si.is_misc_item,si.quantity,si.original_unit_price,
                  si.discount_percent,si.discount_amount,si.unit_price ORDER BY si.sale_item_id
                """)) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) rows.add(map(
                    "productId", rs.getInt(1), "productName", rs.getString(2), "quantity", rs.getInt(3),
                    "returnedQuantity", rs.getInt(4), "originalUnitPrice", rs.getBigDecimal(5),
                    "discountPercent", rs.getBigDecimal(6), "discountAmount", rs.getBigDecimal(7),
                    "unitPrice", rs.getBigDecimal(8), "lineTotal", rs.getBigDecimal(9)));
            }
        }
        return rows;
    }

    private static List<Map<String, Object>> queryReturns(Connection c, int saleId) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT return_id,COALESCE(return_receipt_number,''),created_at,COALESCE(user_name,''),COALESCE(refund_method,''),
                       COALESCE(refund_amount,0),COALESCE(reason,'')
                FROM sale_returns WHERE sale_id=? ORDER BY created_at DESC,return_id DESC
                """)) {
            ps.setInt(1, saleId); try (ResultSet rs = ps.executeQuery()) { while (rs.next()) rows.add(map(
                    "returnId", rs.getLong(1), "returnReceiptNumber", rs.getString(2), "createdAtEpochMillis", rs.getTimestamp(3).getTime(),
                    "userName", rs.getString(4), "refundMethod", rs.getString(5),
                    "refundAmount", rs.getBigDecimal(6), "reason", rs.getString(7)));
            }
        }
        return rows;
    }

    private static List<Map<String, Object>> queryReturnItems(Connection c, int saleId) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT sri.return_id,sri.product_id,COALESCE(p.name,'Deleted Item')||
                  CASE WHEN COALESCE(p.size,'')='' THEN '' ELSE ' ('||p.size||')' END,
                  sri.quantity,COALESCE(sri.unit_price,0),sri.quantity*COALESCE(sri.unit_price,0)
                FROM sale_return_items sri LEFT JOIN products p ON p.product_id=sri.product_id
                JOIN sale_returns sr ON sr.return_id=sri.return_id
                WHERE sr.sale_id=? ORDER BY sr.created_at DESC,sri.return_item_id
                """)) {
            ps.setInt(1, saleId); try (ResultSet rs = ps.executeQuery()) { while (rs.next()) rows.add(map(
                    "returnId", rs.getLong(1), "productId", rs.getInt(2), "productName", rs.getString(3),
                    "quantity", rs.getInt(4), "unitPrice", rs.getBigDecimal(5), "lineTotal", rs.getBigDecimal(6)));
            }
        }
        return rows;
    }

    private static List<Map<String, Object>> queryAudit(Connection c, int saleId) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT created_at,COALESCE(action_type,''),COALESCE(action_scope,''),COALESCE(field_name,''),
                  COALESCE(old_value,''),COALESCE(new_value,''),amount,quantity,COALESCE(reason,''),
                  COALESCE(note,''),COALESCE(user_name,''),COALESCE(device_name,device_id::text,'')
                FROM sale_audit_log WHERE sale_id=? AND (UPPER(COALESCE(action_type,'')) LIKE '%OVERRIDE%'
                  OR UPPER(COALESCE(action_type,'')) IN ('SALE_DISCOUNT_APPLIED','PRICE_OVERRIDE','ITEM_DISCOUNT_APPLIED'))
                ORDER BY created_at DESC,sale_audit_id DESC
                """)) {
            ps.setInt(1, saleId); try (ResultSet rs = ps.executeQuery()) { while (rs.next()) rows.add(map(
                    "createdAtEpochMillis", rs.getTimestamp(1).getTime(), "actionType", rs.getString(2),
                    "actionScope", rs.getString(3), "fieldName", rs.getString(4), "oldValue", rs.getString(5),
                    "newValue", rs.getString(6), "amount", rs.getBigDecimal(7), "quantity", rs.getObject(8),
                    "reason", rs.getString(9), "note", rs.getString(10), "userName", rs.getString(11),
                    "deviceName", rs.getString(12)));
            }
        }
        return rows;
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }

    private static void appendDate(StringBuilder where, List<Object> args, String column,
                                   Instant from, Instant to) {
        if (from != null) { where.append("AND ").append(column).append(">=? "); args.add(Timestamp.from(from)); }
        if (to != null) { where.append("AND ").append(column).append("<? "); args.add(Timestamp.from(to)); }
    }

    private static Instant parseBoundary(String value, ZoneId zone, boolean endExclusive) throws RuleViolation {
        if (value == null || value.isBlank()) return null;
        try {
            LocalDate date = LocalDate.parse(value.trim());
            return (endExclusive ? date.plusDays(1) : date).atStartOfDay(zone).toInstant();
        } catch (DateTimeParseException ex) {
            throw rule(400, "VALIDATION_ERROR", "Dates must use yyyy-MM-dd format.");
        }
    }

    private static ZoneId storeZone(Connection c, int locationId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(timezone,'') FROM locations WHERE location_id=?")) {
            ps.setInt(1, locationId); try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) try { return ZoneId.of(rs.getString(1)); } catch (Exception ignored) { }
            }
        }
        return ZoneId.systemDefault();
    }

    private static void bind(PreparedStatement ps, List<Object> args) throws SQLException {
        for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
    }

    private static void requirePermission(Connection c, int userId, String key) throws Exception {
        if (!hasPermission(c, userId, key))
            throw rule(403, "PERMISSION_DENIED", "You do not have permission to view sales.");
    }

    private static boolean hasPermission(Connection c, int userId, String key) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id
                JOIN permissions p ON p.permission_id=rp.permission_id
                WHERE u.user_id=? AND UPPER(p.permission_key)=? LIMIT 1
                """)) {
            ps.setInt(1, userId); ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static RuleViolation rule(int status, String code, String message) { return new RuleViolation(status, code, message); }
    static final class RuleViolation extends Exception {
        private final int status; private final String code; private final String safeMessage;
        RuleViolation(int status, String code, String safeMessage) {
            super(safeMessage); this.status=status; this.code=code; this.safeMessage=safeMessage;
        }
        int status(){return status;} String code(){return code;} String safeMessage(){return safeMessage;}
    }
    private record HistoryRequest(String search, String fromDate, String toDate) { }
}
