package services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import managers.ServerReceiptNumberManager;
import models.CashDrawerContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Server-only sale lookup and atomic refund implementation. */
final class LanRefundService {
    private static final Gson GSON = new Gson();
    private static final Set<String> REFUND_METHODS = Set.of(
            "CASH", "CARD", "CHEQUE", "MMG", "ACCOUNT", "BANK_TRANSFER");

    private LanRefundService() {
    }

    static List<Map<String, Object>> search(Connection connection, String query,
                                             int userId, int locationId) throws Exception {
        requirePermission(connection, userId, "PROCESS_RETURNS");
        String term = query == null ? "" : query.trim();
        if (term.length() < 2 || term.length() > 300) {
            return List.of();
        }
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT s.sale_id, COALESCE(s.receipt_number, ''), s.created_at,
                       COALESCE(s.total_amount, 0), COALESCE(s.user_name, ''),
                       COALESCE(s.receipt_device_id, s.device_id, '')
                FROM sales s
                WHERE s.location_id = ?
                  AND (CAST(s.sale_id AS TEXT) = ?
                    OR COALESCE(s.receipt_number, '') ILIKE ?
                    OR COALESCE(s.receipt_number, '') ILIKE ?)
                ORDER BY CASE
                    WHEN CAST(s.sale_id AS TEXT) = ? THEN 0
                    WHEN COALESCE(s.receipt_number, '') ILIKE ? THEN 1
                    ELSE 2 END,
                    s.created_at DESC
                LIMIT 20
                """)) {
            ps.setInt(1, locationId);
            ps.setString(2, term);
            ps.setString(3, "%" + term + "%");
            ps.setString(4, "%" + term);
            ps.setString(5, term);
            ps.setString(6, "%" + term + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("saleId", rs.getInt(1));
                    row.put("receiptNumber", rs.getString(2));
                    row.put("createdAtEpochMillis", rs.getTimestamp(3).getTime());
                    row.put("totalAmount", money(rs.getBigDecimal(4)));
                    row.put("cashierName", rs.getString(5));
                    row.put("deviceId", rs.getString(6));
                    results.add(row);
                }
            }
        }
        return results;
    }

    static Map<String, Object> details(Connection connection, int saleId,
                                        int userId, int locationId) throws Exception {
        requirePermission(connection, userId, "PROCESS_RETURNS");
        Map<String, Object> result = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT s.sale_id, COALESCE(s.receipt_number, ''), s.customer_id,
                       COALESCE(s.payment_method, ''), COALESCE(s.payment_status, 'PAID'),
                       COALESCE(s.total_amount, 0), COALESCE(s.returned_amount, 0),
                       COALESCE(cc.sale_return_approval_limit, 0)
                FROM sales s
                LEFT JOIN company_customization cc ON cc.location_id = s.location_id
                WHERE s.sale_id = ? AND s.location_id = ?
                """)) {
            ps.setInt(1, saleId);
            ps.setInt(2, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new RuleViolation(404, "SALE_NOT_FOUND",
                            "Sale was not found for this store.", false);
                }
                result.put("saleId", rs.getInt(1));
                result.put("receiptNumber", rs.getString(2));
                result.put("customerId", nullableInteger(rs, 3));
                result.put("paymentMethod", rs.getString(4));
                result.put("paymentStatus", rs.getString(5));
                result.put("totalAmount", money(rs.getBigDecimal(6)));
                result.put("returnedAmount", money(rs.getBigDecimal(7)));
                result.put("returnApprovalLimit", money(rs.getBigDecimal(8)));
            }
        }

        List<Map<String, Object>> items = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT si.sale_item_id, si.product_id, COALESCE(p.sku, ''),
                       COALESCE(p.name, 'Unknown')
                         || CASE WHEN COALESCE(p.size, '') = '' THEN '' ELSE ' (' || p.size || ')' END,
                       CASE WHEN UPPER(COALESCE(si.product_type, p.product_type, 'INVENTORY'))
                                      IN ('SERVICE', 'NON_INVENTORY')
                            THEN UPPER(COALESCE(si.product_type, p.product_type)) ELSE 'INVENTORY' END,
                       COALESCE(si.quantity, 0), COALESCE(si.unit_price, 0),
                       COALESCE((SELECT SUM(sri.quantity) FROM sale_return_items sri
                                 WHERE sri.sale_item_id = si.sale_item_id), 0)
                FROM sale_items si
                LEFT JOIN products p ON p.product_id = si.product_id
                JOIN sales s ON s.sale_id = si.sale_id
                WHERE si.sale_id = ? AND s.location_id = ?
                ORDER BY si.sale_item_id
                """)) {
            ps.setInt(1, saleId);
            ps.setInt(2, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int sold = rs.getInt(6);
                    int returned = rs.getInt(8);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("saleItemId", rs.getInt(1));
                    item.put("productId", rs.getInt(2));
                    item.put("sku", rs.getString(3));
                    item.put("productName", rs.getString(4));
                    item.put("productType", rs.getString(5));
                    item.put("soldQuantity", sold);
                    item.put("returnedQuantity", returned);
                    item.put("availableQuantity", Math.max(0, sold - returned));
                    item.put("unitPrice", money(rs.getBigDecimal(7)));
                    items.add(item);
                }
            }
        }
        result.put("items", items);
        result.put("requesterCanOverride", hasPermission(connection, userId, "RETURN_OVERRIDE"));
        return result;
    }

    static Map<String, Object> refund(Connection connection, JsonObject body, UUID deviceId,
                                       int userId, String userName, int locationId,
                                       ApprovalConsumer approvalConsumer) throws Exception {
        RefundRequest request = GSON.fromJson(body, RefundRequest.class);
        validateRequest(request);
        requirePermission(connection, userId, "PROCESS_RETURNS");

        Sale sale = lockSale(connection, request.saleId(), locationId);
        TreeMap<Integer, Integer> requestedQuantities = requestedQuantities(request.lines());
        List<ValidatedLine> lines = lockAndValidateLines(
                connection, sale.saleId(), requestedQuantities);
        BigDecimal total = BigDecimal.ZERO;
        for (ValidatedLine line : lines) {
            total = total.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        total = money(total);
        if (total.signum() <= 0) {
            throw new RuleViolation(400, "INVALID_RETURN", "Return total must be greater than zero.", false);
        }

        BigDecimal approvalLimit = loadApprovalLimit(connection, locationId);
        Approval approval = null;
        boolean approvalRequired = approvalLimit.signum() > 0 && total.compareTo(approvalLimit) > 0;
        if (approvalRequired) {
            if (hasPermission(connection, userId, "RETURN_OVERRIDE")) {
                String selfApprovalReason = request.approvalReason() == null
                        || request.approvalReason().isBlank()
                        ? request.reason().trim() : request.approvalReason().trim();
                approval = new Approval(userId, userName, selfApprovalReason);
            } else {
                String resource = RefundApprovalIdentity.build(sale.saleId(), total, requestedQuantities);
                approval = approvalConsumer.consume(request.approvalToken(), "RETURN_OVERRIDE",
                        "Return Override", request.approvalReason(), resource);
            }
        }

        String refundMethod = request.refundMethod().trim().toUpperCase();
        CashDrawerContext drawer = new CashDrawerContext(null, null);
        if ("CASH".equals(refundMethod)) {
            drawer = CashDrawerService.resolveDrawerForDevice(
                    connection, locationId, deviceId.toString());
            if (!drawer.isAssigned()) {
                throw new RuleViolation(409, "CASH_DRAWER_REQUIRED",
                        "This register is not assigned to an active cash drawer.", false);
            }
            if (!drawer.hasActiveSession()) {
                throw new RuleViolation(409, "CASH_SESSION_REQUIRED",
                        "No active draw session is open for " + drawer.drawerName() + ".", false);
            }
        }

        String deviceName = loadDeviceName(connection, deviceId);
        ServerReceiptNumberManager.ReturnNumber returnNumber =
                ServerReceiptNumberManager.nextReturn(connection, locationId, deviceId);
        long returnId = insertReturn(connection, sale, locationId, userId, userName,
                refundMethod, total, request.reason().trim(), deviceId, deviceName, drawer, approval,
                returnNumber);
        insertReturnAudit(connection, sale, returnId, null, null, userId, userName, deviceId,
                deviceName, "RETURN_CREATED", "RETURN", total, null,
                request.reason().trim(), "refund_method=" + refundMethod);
        if (approval != null) {
            insertReturnAudit(connection, sale, returnId, null, null, userId, userName, deviceId,
                    deviceName, "RETURN_OVERRIDE_APPROVED", "RETURN", total, null,
                    approval.reason(), "approved_by=" + approval.approverName());
        }

        for (ValidatedLine line : lines) {
            long returnItemId = insertReturnItem(connection, returnId, line);
            BigDecimal lineAmount = money(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
            insertReturnAudit(connection, sale, returnId, returnItemId, line, userId, userName,
                    deviceId, deviceName, "RETURN_LINE_RECORDED", "RETURN_ITEM", lineAmount,
                    line.quantity(), request.reason().trim(), "refund_method=" + refundMethod);
            if ("INVENTORY".equals(line.productType())) {
                restoreInventory(connection, sale, returnId, line, locationId, userId,
                        userName, deviceId, deviceName);
                insertReturnAudit(connection, sale, returnId, returnItemId, line, userId, userName,
                        deviceId, deviceName, "RETURN_INVENTORY_RESTOCKED", "INVENTORY", null,
                        line.quantity(), request.reason().trim(), "Inventory restored from sale return.");
            }
        }

        updateSale(connection, sale.saleId(), total);
        if (sale.customerId() != null && "ACCOUNT".equalsIgnoreCase(sale.paymentMethod())) {
            BalanceChange balance = applyAccountCredit(connection, sale, total, returnId, locationId, userName,
                    deviceId, deviceName);
            insertReturnAudit(connection, sale, returnId, null, null, userId, userName, deviceId,
                    deviceName, "ACCOUNT_RETURN_APPLIED", "CUSTOMER_ACCOUNT", total, null,
                    request.reason().trim(), "balance_before=" + balance.before().toPlainString()
                            + "; balance_after=" + balance.after().toPlainString());
        }
        insertOutbox(connection, sale, returnId, locationId, userId, deviceId,
                refundMethod, total, drawer.sessionId(), returnNumber);
        insertImmutableAudit(connection, deviceId, userId, returnId, sale,
                locationId, total, approval);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("returnId", returnId);
        result.put("returnReceiptNumber", returnNumber.returnReceiptNumber());
        result.put("saleId", sale.saleId());
        result.put("refundAmount", total);
        result.put("refundMethod", refundMethod);
        result.put("approvalRequired", approvalRequired);
        result.put("approvedByName", approval == null ? "" : approval.approverName());
        return result;
    }

    private static void validateRequest(RefundRequest request) throws RuleViolation {
        if (request == null || request.saleId() <= 0) {
            throw new RuleViolation(400, "VALIDATION_ERROR", "A valid sale is required.", false);
        }
        if (request.lines() == null || request.lines().isEmpty() || request.lines().size() > 200) {
            throw new RuleViolation(400, "VALIDATION_ERROR", "Select at least one valid return item.", false);
        }
        String method = request.refundMethod() == null ? "" : request.refundMethod().trim().toUpperCase();
        if (!REFUND_METHODS.contains(method)) {
            throw new RuleViolation(400, "VALIDATION_ERROR", "Unsupported refund method.", false);
        }
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.isEmpty() || reason.length() > 2000) {
            throw new RuleViolation(400, "VALIDATION_ERROR", "A return reason is required.", false);
        }
        if (request.approvalReason() != null && request.approvalReason().length() > 2000) {
            throw new RuleViolation(400, "VALIDATION_ERROR", "The approval reason is too long.", false);
        }
    }

    private static TreeMap<Integer, Integer> requestedQuantities(List<RefundLine> requested)
            throws RuleViolation {
        TreeMap<Integer, Integer> result = new TreeMap<>();
        for (RefundLine line : requested) {
            if (line == null || line.saleItemId() <= 0 || line.quantity() <= 0
                    || line.quantity() > 100_000 || result.putIfAbsent(line.saleItemId(), line.quantity()) != null) {
                throw new RuleViolation(400, "VALIDATION_ERROR",
                        "Each return line must identify one sale item and a valid quantity.", false);
            }
        }
        return result;
    }

    private static Sale lockSale(Connection connection, int saleId, int locationId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT sale_id, customer_id, COALESCE(receipt_number, ''),
                       COALESCE(payment_method, ''), COALESCE(total_amount, 0),
                       COALESCE(amount_paid, 0), COALESCE(returned_amount, 0)
                FROM sales
                WHERE sale_id = ? AND location_id = ?
                FOR UPDATE
                """)) {
            ps.setInt(1, saleId);
            ps.setInt(2, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new RuleViolation(404, "SALE_NOT_FOUND",
                            "Sale was not found for this store.", false);
                }
                return new Sale(rs.getInt(1), nullableInteger(rs, 2), rs.getString(3),
                        rs.getString(4), money(rs.getBigDecimal(5)), money(rs.getBigDecimal(6)),
                        money(rs.getBigDecimal(7)));
            }
        }
    }

    private static List<ValidatedLine> lockAndValidateLines(Connection connection, int saleId,
                                                              TreeMap<Integer, Integer> quantities)
            throws Exception {
        List<ValidatedLine> result = new ArrayList<>();
        Set<Integer> found = new HashSet<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT si.sale_item_id, si.product_id, COALESCE(si.quantity, 0),
                       COALESCE(si.unit_price, 0),
                       CASE WHEN UPPER(COALESCE(si.product_type, p.product_type, 'INVENTORY'))
                                      IN ('SERVICE', 'NON_INVENTORY')
                            THEN UPPER(COALESCE(si.product_type, p.product_type)) ELSE 'INVENTORY' END,
                       COALESCE((SELECT SUM(sri.quantity) FROM sale_return_items sri
                                 WHERE sri.sale_item_id = si.sale_item_id), 0)
                FROM sale_items si
                LEFT JOIN products p ON p.product_id = si.product_id
                WHERE si.sale_id = ?
                ORDER BY si.sale_item_id
                FOR UPDATE OF si
                """)) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int saleItemId = rs.getInt(1);
                    Integer requested = quantities.get(saleItemId);
                    if (requested == null) {
                        continue;
                    }
                    int available = rs.getInt(3) - rs.getInt(6);
                    if (requested > available) {
                        throw new RuleViolation(409, "RETURN_QUANTITY_CHANGED",
                                "A selected return quantity is no longer available. Reload the sale and try again.", false);
                    }
                    found.add(saleItemId);
                    result.add(new ValidatedLine(saleItemId, rs.getInt(2), requested,
                            money(rs.getBigDecimal(4)), rs.getString(5)));
                }
            }
        }
        if (found.size() != quantities.size()) {
            throw new RuleViolation(400, "SALE_ITEM_INVALID",
                    "A selected item does not belong to this sale.", false);
        }
        return result;
    }

    private static BigDecimal loadApprovalLimit(Connection connection, int locationId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT COALESCE(sale_return_approval_limit, 0)
                FROM company_customization WHERE location_id = ?
                """)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? money(rs.getBigDecimal(1)) : BigDecimal.ZERO.setScale(2);
            }
        }
    }

    private static long insertReturn(Connection c, Sale sale, int locationId, int userId,
                                     String userName, String method, BigDecimal total, String reason,
                                     UUID deviceId, String deviceName, CashDrawerContext drawer,
                                     Approval approval, ServerReceiptNumberManager.ReturnNumber returnNumber) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO sale_returns (sale_id,location_id,user_id,user_name,refund_method,
                  refund_amount,reason,device_id,device_name,cash_drawer_id,cash_drawer_name,
                  cash_drawer_session_id,override_reason,override_by_user_id,override_by_name,
                  return_receipt_number,receipt_device_id,receipt_sequence)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, sale.saleId());
            ps.setInt(2, locationId);
            ps.setInt(3, userId);
            ps.setString(4, userName);
            ps.setString(5, method);
            ps.setBigDecimal(6, total);
            ps.setString(7, reason);
            ps.setString(8, deviceId.toString());
            ps.setString(9, deviceName);
            setLong(ps, 10, drawer.cashDrawerId());
            ps.setString(11, drawer.drawerName());
            setLong(ps, 12, drawer.sessionId());
            ps.setString(13, approval == null ? null : approval.reason());
            setInteger(ps, 14, approval == null ? null : approval.approverUserId());
            ps.setString(15, approval == null ? null : approval.approverName());
            ps.setString(16, returnNumber.returnReceiptNumber());
            ps.setString(17, returnNumber.deviceId());
            ps.setInt(18, returnNumber.sequence());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Failed to create return record.");
                }
                return keys.getLong(1);
            }
        }
    }

    private static long insertReturnItem(Connection c, long returnId, ValidatedLine line)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO sale_return_items (return_id,sale_item_id,product_id,quantity,unit_price)
                VALUES (?,?,?,?,?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, returnId);
            ps.setInt(2, line.saleItemId());
            ps.setInt(3, line.productId());
            ps.setInt(4, line.quantity());
            ps.setBigDecimal(5, line.unitPrice());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Failed to create return item.");
                }
                return keys.getLong(1);
            }
        }
    }

    private static void restoreInventory(Connection c, Sale sale, long returnId, ValidatedLine line,
                                         int locationId, int userId, String userName, UUID deviceId,
                                         String deviceName) throws SQLException {
        try (PreparedStatement ensure = c.prepareStatement("""
                     INSERT INTO inventory (product_id,location_id,quantity_on_hand,reorder_level)
                     VALUES (?,?,0,0) ON CONFLICT (product_id,location_id) DO NOTHING
                     """);
             PreparedStatement update = c.prepareStatement("""
                     UPDATE inventory SET quantity_on_hand=quantity_on_hand+?
                     WHERE product_id=? AND location_id=?
                     """);
             PreparedStatement movement = c.prepareStatement("""
                     INSERT INTO inventory_movements (product_id,location_id,change_qty,reason,note,
                       user_name,sale_id,sale_item_id,sale_return_id,device_id,device_name,user_id)
                     VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                     """)) {
            ensure.setInt(1, line.productId());
            ensure.setInt(2, locationId);
            ensure.executeUpdate();
            update.setInt(1, line.quantity());
            update.setInt(2, line.productId());
            update.setInt(3, locationId);
            if (update.executeUpdate() != 1) {
                throw new SQLException("Inventory could not be restored for a returned item.");
            }
            movement.setInt(1, line.productId());
            movement.setInt(2, locationId);
            movement.setInt(3, line.quantity());
            movement.setString(4, "RETURN");
            movement.setString(5, "return_id=" + returnId + "; sale_id=" + sale.saleId()
                    + "; receipt=" + sale.receiptNumber());
            movement.setString(6, userName);
            movement.setInt(7, sale.saleId());
            movement.setInt(8, line.saleItemId());
            movement.setLong(9, returnId);
            movement.setString(10, deviceId.toString());
            movement.setString(11, deviceName);
            movement.setInt(12, userId);
            movement.executeUpdate();
        }
    }

    private static void updateSale(Connection c, int saleId, BigDecimal amount) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE sales
                SET returned_amount=COALESCE(returned_amount,0)+?,
                    payment_status=CASE
                      WHEN payment_method='ACCOUNT'
                       AND COALESCE(amount_paid,0)>=GREATEST(COALESCE(total_amount,0)
                           -(COALESCE(returned_amount,0)+?),0)
                      THEN 'PAID' ELSE payment_status END
                WHERE sale_id=?
                """)) {
            ps.setBigDecimal(1, amount);
            ps.setBigDecimal(2, amount);
            ps.setInt(3, saleId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Sale return total could not be updated.");
            }
        }
    }

    private static BalanceChange applyAccountCredit(Connection c, Sale sale, BigDecimal total, long returnId,
                                                    int locationId, String userName, UUID deviceId,
                                                    String deviceName) throws SQLException {
        BigDecimal current;
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT current_balance FROM customer_accounts WHERE customer_id=? FOR UPDATE
                """)) {
            ps.setInt(1, sale.customerId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Customer account was not found.");
                }
                current = money(rs.getBigDecimal(1));
            }
        }
        BigDecimal next = money(current.subtract(total).max(BigDecimal.ZERO));
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE customer_accounts SET current_balance=? WHERE customer_id=?
                """)) {
            ps.setBigDecimal(1, next);
            ps.setInt(2, sale.customerId());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO customer_account_transactions
                  (customer_id,sale_id,location_id,amount,transaction_type,note,user_name,device_id,device_name)
                VALUES (?,?,?,?,'RETURN',?,?,?,?)
                """)) {
            ps.setInt(1, sale.customerId());
            ps.setInt(2, sale.saleId());
            ps.setInt(3, locationId);
            ps.setBigDecimal(4, total.negate());
            ps.setString(5, "Returned items. return_id=" + returnId + "; sale_id=" + sale.saleId());
            ps.setString(6, userName);
            ps.setString(7, deviceId.toString());
            ps.setString(8, deviceName);
            ps.executeUpdate();
        }
        return new BalanceChange(current, next);
    }

    private static void insertReturnAudit(Connection c, Sale sale, long returnId, Long returnItemId,
                                          ValidatedLine line, int userId, String userName, UUID deviceId,
                                          String deviceName, String action, String scope,
                                          BigDecimal amount, Integer quantity, String reason, String note)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO sale_audit_log (sale_id,sale_item_id,return_id,return_item_id,
                  customer_id,product_id,location_id,action_type,action_scope,amount,quantity,
                  reason,note,user_id,user_name,device_id,device_name)
                SELECT ?,?,?,?,?,?,s.location_id,?,?,?,?,?,?,?,?,?,?
                FROM sales s WHERE s.sale_id=?
                """)) {
            ps.setInt(1, sale.saleId());
            setInteger(ps, 2, line == null ? null : line.saleItemId());
            ps.setLong(3, returnId);
            setLong(ps, 4, returnItemId);
            setInteger(ps, 5, sale.customerId());
            setInteger(ps, 6, line == null ? null : line.productId());
            ps.setString(7, action);
            ps.setString(8, scope);
            if (amount == null) ps.setNull(9, Types.NUMERIC); else ps.setBigDecimal(9, amount);
            setInteger(ps, 10, quantity);
            ps.setString(11, reason);
            ps.setString(12, note);
            ps.setInt(13, userId);
            ps.setString(14, userName);
            ps.setString(15, deviceId.toString());
            ps.setString(16, deviceName);
            ps.setInt(17, sale.saleId());
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Return audit record could not be written.");
            }
        }
    }

    private static void insertOutbox(Connection c, Sale sale, long returnId, int locationId,
                                     int userId, UUID deviceId, String method, BigDecimal total,
                                     Long drawerSessionId,
                                     ServerReceiptNumberManager.ReturnNumber returnNumber)
            throws SQLException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("return_id", returnId);
        payload.put("sale_id", sale.saleId());
        payload.put("location_id", locationId);
        payload.put("user_id", userId);
        payload.put("device_id", deviceId.toString());
        payload.put("refund_method", method);
        payload.put("refund_amount", total);
        payload.put("cash_drawer_session_id", drawerSessionId == null ? "" : drawerSessionId);
        payload.put("return_receipt_number", returnNumber.returnReceiptNumber());
        payload.put("receipt_device_id", returnNumber.deviceId());
        payload.put("receipt_sequence", returnNumber.sequence());
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO sync_outbox (event_type,location_id,device_id,user_id,payload,
                  origin_location_id,origin_device_id)
                VALUES ('SALE_RETURN_CREATED',?,?,?,?::jsonb,?,?)
                """)) {
            ps.setInt(1, locationId);
            ps.setString(2, deviceId.toString());
            ps.setInt(3, userId);
            ps.setString(4, GSON.toJson(payload));
            ps.setInt(5, locationId);
            ps.setString(6, deviceId.toString());
            ps.executeUpdate();
        }
    }

    private static void insertImmutableAudit(Connection c, UUID deviceId, int userId, long returnId,
                                             Sale sale, int locationId, BigDecimal total,
                                             Approval approval) throws SQLException {
        String details = "return_id=" + returnId + "; sale_id=" + sale.saleId() + "; location_id="
                + locationId + "; refund_amount=" + total.toPlainString()
                + "; returned_before=" + sale.returnedAmount().toPlainString()
                + "; returned_after=" + sale.returnedAmount().add(total).toPlainString()
                + "; approved_by=" + (approval == null ? "" : approval.approverUserId());
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO security_audit_events (event_type,device_id,actor_user_id,details)
                VALUES ('LAN_SALE_RETURN_COMPLETED',?,?,?)
                """)) {
            ps.setObject(1, deviceId);
            ps.setInt(2, userId);
            ps.setString(3, details);
            ps.executeUpdate();
        }
    }

    private static String loadDeviceName(Connection c, UUID deviceId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT COALESCE(NULLIF(TRIM(device_name),''),hostname,'SmartStock Register')
                FROM devices WHERE device_id=?
                """)) {
            ps.setObject(1, deviceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "SmartStock Register";
            }
        }
    }

    private static boolean hasPermission(Connection c, int userId, String key) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT 1 FROM users u
                JOIN role_permissions rp ON rp.role_id=u.role_id
                JOIN permissions p ON p.permission_id=rp.permission_id
                WHERE u.user_id=? AND UPPER(p.permission_key)=? LIMIT 1
                """)) {
            ps.setInt(1, userId);
            ps.setString(2, key.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void requirePermission(Connection c, int userId, String key) throws Exception {
        if (!hasPermission(c, userId, key)) {
            throw new RuleViolation(403, "PERMISSION_DENIED",
                    "You do not have permission for this operation.", false);
        }
    }

    private static Integer nullableInteger(ResultSet rs, int column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static void setInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) ps.setNull(index, Types.INTEGER); else ps.setInt(index, value);
    }

    private static void setLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) ps.setNull(index, Types.BIGINT); else ps.setLong(index, value);
    }

    interface ApprovalConsumer {
        Approval consume(String token, String permission, String action, String reason,
                         String resourceIdentity) throws Exception;
    }

    record Approval(int approverUserId, String approverName, String reason) {
    }

    static final class RuleViolation extends Exception {
        private final int status;
        private final String code;
        private final boolean retryable;

        RuleViolation(int status, String code, String safeMessage, boolean retryable) {
            super(safeMessage);
            this.status = status;
            this.code = code;
            this.retryable = retryable;
        }

        int status() {
            return status;
        }

        String code() {
            return code;
        }

        String safeMessage() {
            return getMessage();
        }

        boolean retryable() {
            return retryable;
        }
    }

    private record RefundRequest(int saleId, String refundMethod, String reason,
                                 String approvalToken, String approvalReason,
                                 List<RefundLine> lines) {
    }

    private record RefundLine(int saleItemId, int quantity) {
    }

    private record Sale(int saleId, Integer customerId, String receiptNumber, String paymentMethod,
                        BigDecimal totalAmount, BigDecimal amountPaid, BigDecimal returnedAmount) {
    }

    private record ValidatedLine(int saleItemId, int productId, int quantity,
                                 BigDecimal unitPrice, String productType) {
    }

    private record BalanceChange(BigDecimal before, BigDecimal after) {
    }
}
