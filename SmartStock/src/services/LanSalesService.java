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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-only atomic checkout implementation. */
final class LanSalesService {
    private static final Gson GSON = new Gson();
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private LanSalesService() {
    }

    static Map<String, Object> checkout(Connection connection, JsonObject body, UUID deviceId,
                                        int userId, String userName, int locationId,
                                        ApprovalConsumer approvalConsumer) throws Exception {
        CheckoutRequest request = GSON.fromJson(body, CheckoutRequest.class);
        validateRequest(request);
        requirePermission(connection, userId, "MAKE_SALE");

        CashDrawerContext drawer = CashDrawerService.resolveDrawerForDevice(
                connection, locationId, deviceId.toString());
        boolean cashPayment = "CASH".equals(request.paymentMethod());
        boolean accountPayment = "ACCOUNT".equals(request.paymentMethod());
        if (cashPayment) {
            if (!drawer.isAssigned()) {
                throw new RuleViolation(409, "CASH_DRAWER_REQUIRED",
                        "This register is not assigned to a cash drawer. An administrator must assign it in Company Preferences > Cash Drawer Manager, then open its draw from Operations > Balance Draw.", false);
            }
            if (!drawer.hasActiveSession()) {
                throw new RuleViolation(409, "CASH_SESSION_REQUIRED",
                        "No active draw session is open for " + drawer.drawerName()
                                + ". Open Operations > Balance Draw before taking cash.", false);
            }
        }

        SaleConfig config = loadConfig(connection, locationId);
        boolean canDiscount = hasPermission(connection, userId, "APPLY_SALE_DISCOUNT");
        boolean canChangePrice = hasPermission(connection, userId, "CHANGE_SALE_ITEM_PRICE");
        boolean canOverrideSaleDiscount = hasPermission(connection, userId, "SALE_DISCOUNT_OVERRIDE");
        BigDecimal saleDiscount = percent(request.saleDiscountPercent());
        Approval saleApproval = null;
        if (saleDiscount.compareTo(config.discountLimit()) > 0) {
            if (!canOverrideSaleDiscount) {
                saleApproval = approvalConsumer.consume(request.saleDiscountApprovalToken(),
                        "SALE_DISCOUNT_OVERRIDE", "Sale Discount Override", request.saleDiscountOverrideReason());
            }
        } else if (saleDiscount.signum() > 0 && !canDiscount) {
            saleApproval = approvalConsumer.consume(request.saleDiscountApprovalToken(),
                    "APPLY_SALE_DISCOUNT", "Sale Discount Approval", request.saleDiscountOverrideReason());
        }

        // Lock all products in one deterministic order. Registers can submit
        // carts in any UI order without creating opposite-order lock cycles.
        List<Integer> productIds = new ArrayList<>();
        for (CheckoutLine requested : request.lines()) {
            if (!productIds.contains(requested.productId())) productIds.add(requested.productId());
        }
        productIds.sort(Integer::compareTo);
        Map<Integer, CatalogLine> catalogs = new LinkedHashMap<>();
        for (Integer productId : productIds) {
            catalogs.put(productId, lockCatalogLine(connection, productId));
        }

        List<ValidatedLine> lines = new ArrayList<>();
        BigDecimal grossSubtotal = BigDecimal.ZERO;
        BigDecimal afterLineDiscounts = BigDecimal.ZERO;
        BigDecimal vatBeforeSaleDiscount = BigDecimal.ZERO;
        for (CheckoutLine requested : request.lines()) {
            CatalogLine catalog = catalogs.get(requested.productId());
            int quantity = requested.quantity();
            BigDecimal enteredPrice = money(requested.unitPrice());
            BigDecimal lineDiscount = percent(requested.discountPercent());
            Approval priceApproval = null;
            Approval discountApproval = null;
            if (enteredPrice.compareTo(catalog.catalogPrice()) != 0 && !canChangePrice) {
                priceApproval = approvalConsumer.consume(requested.priceApprovalToken(),
                        "CHANGE_SALE_ITEM_PRICE", "Price Override", requested.priceOverrideReason());
            }
            if (lineDiscount.signum() > 0 && !canDiscount) {
                discountApproval = approvalConsumer.consume(requested.discountApprovalToken(),
                        "APPLY_SALE_DISCOUNT", "Item Discount Override", requested.discountOverrideReason());
            }
            BigDecimal gross = money(enteredPrice.multiply(BigDecimal.valueOf(quantity)));
            BigDecimal lineDiscountAmount = money(gross.multiply(lineDiscount).divide(HUNDRED, 2, RoundingMode.HALF_UP));
            BigDecimal net = money(gross.subtract(lineDiscountAmount).max(BigDecimal.ZERO));
            BigDecimal vatRate = config.vatEnabled()
                    ? (config.departmentVat() ? catalog.departmentVatRate() : config.fixedVatRate())
                    : BigDecimal.ZERO;
            vatBeforeSaleDiscount = vatBeforeSaleDiscount.add(
                    money(net.multiply(vatRate).divide(HUNDRED, 2, RoundingMode.HALF_UP)));
            grossSubtotal = grossSubtotal.add(gross);
            afterLineDiscounts = afterLineDiscounts.add(net);
            lines.add(new ValidatedLine(catalog, quantity, enteredPrice, lineDiscount,
                    lineDiscountAmount, priceApproval, discountApproval));
        }

        BigDecimal saleMultiplier = BigDecimal.ONE.subtract(saleDiscount.divide(HUNDRED, 6, RoundingMode.HALF_UP));
        BigDecimal saleDiscountAmount = money(afterLineDiscounts.multiply(saleDiscount)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP));
        BigDecimal preVatTotal = money(afterLineDiscounts.subtract(saleDiscountAmount).max(BigDecimal.ZERO));
        BigDecimal vatAmount = money(vatBeforeSaleDiscount.multiply(saleMultiplier));
        BigDecimal total = money(preVatTotal.add(vatAmount));
        if (total.signum() <= 0) throw new SQLException("Sale total must be greater than zero.");
        BigDecimal cashCollected = money(request.cashCollected());
        if (cashPayment && cashCollected.compareTo(total) < 0) {
            throw new SQLException("Cash collected is less than the sale total.");
        }
        if (accountPayment) {
            if (request.customerId() == null) throw new SQLException("Select a customer account for account payment.");
            chargeCustomerAccount(connection, request.customerId(), total, userId, userName, deviceId, locationId);
        }

        ServerReceiptNumberManager.ReceiptNumber receipt = ServerReceiptNumberManager.nextReceipt(
                connection, locationId, deviceId);
        int saleId = insertSale(connection, request, deviceId, userId, userName, locationId, drawer,
                receipt, grossSubtotal, money(grossSubtotal.subtract(preVatTotal).add(vatAmount).subtract(vatAmount)),
                saleDiscount, vatAmount, total, saleApproval);
        insertSaleAudit(connection, saleId, null, request.customerId(), null, locationId, userId, userName,
                deviceId, "SALE_CREATED", "SALE", total,
                "receipt=" + receipt.receiptNumber() + "; payment_method=" + request.paymentMethod());

        for (ValidatedLine line : lines) {
            BigDecimal chargedUnitPrice = money(line.enteredPrice()
                    .multiply(BigDecimal.ONE.subtract(line.discountPercent().divide(HUNDRED, 6, RoundingMode.HALF_UP)))
                    .multiply(saleMultiplier));
            int saleItemId = insertSaleItem(connection, saleId, line, chargedUnitPrice);
            BigDecimal lineAmount = money(chargedUnitPrice.multiply(BigDecimal.valueOf(line.quantity())));
            insertSaleAudit(connection, saleId, saleItemId, request.customerId(), line.catalog().productId(), locationId,
                    userId, userName, deviceId, "SALE_ITEM_ADDED", "SALE_ITEM", lineAmount,
                    "product=" + line.catalog().name() + "; product_type=" + line.catalog().productType());
            if ("INVENTORY".equals(line.catalog().productType())) {
                applyInventory(connection, saleId, saleItemId, line, locationId, userId, userName, deviceId);
            }
        }

        if (request.customerId() != null) {
            insertCustomerTransaction(connection, request.customerId(), saleId,
                    accountPayment ? total : BigDecimal.ZERO,
                    accountPayment ? "SALE_CREDIT" : "SALE_PAID", userName, deviceId, locationId);
        }
        insertOutbox(connection, saleId, receipt.receiptNumber(), locationId, userId, deviceId,
                request.paymentMethod(), accountPayment ? "UNPAID" : "PAID", total, drawer.sessionId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("saleId", saleId);
        result.put("receiptNumber", receipt.receiptNumber());
        result.put("total", total);
        result.put("cashCollected", cashPayment ? cashCollected : null);
        result.put("changeDue", cashPayment ? money(cashCollected.subtract(total)) : null);
        result.put("cashDrawerName", drawer.drawerName());
        return result;
    }

    private static void validateRequest(CheckoutRequest request) throws SQLException {
        if (request == null || request.lines() == null || request.lines().isEmpty()) throw new SQLException("Cart is empty.");
        if (request.lines().size() > 200) throw new SQLException("Cart has too many lines.");
        String payment = request.paymentMethod() == null ? "" : request.paymentMethod().trim().toUpperCase();
        if (!List.of("CASH", "CARD", "MMG", "ACCOUNT", "CHEQUE", "BANK_TRANSFER").contains(payment)) {
            throw new SQLException("Unsupported payment method.");
        }
        if (requiresPaymentReference(payment) && blank(request.paymentReference())) {
            throw new SQLException("A card, cheque, or MMG reference is required.");
        }
        for (CheckoutLine line : request.lines()) {
            if (line.productId() <= 0 || line.quantity() <= 0 || line.quantity() > 100_000) {
                throw new SQLException("Each cart line requires a valid product and quantity.");
            }
        }
    }

    private static SaleConfig loadConfig(Connection connection, int locationId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT COALESCE(vat_enabled, FALSE), COALESCE(vat_use_department_rates, FALSE),
                       COALESCE(vat_fixed_rate_percent, 0), COALESCE(sale_discount_limit_percent, 5)
                FROM company_customization WHERE location_id = ?
                """)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new SaleConfig(rs.getBoolean(1), rs.getBoolean(2),
                        percent(rs.getBigDecimal(3)), percent(rs.getBigDecimal(4)));
            }
        }
        return new SaleConfig(false, false, BigDecimal.ZERO, BigDecimal.valueOf(5));
    }

    private static CatalogLine lockCatalogLine(Connection connection, int productId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT p.product_id, p.name, COALESCE(p.price, 0),
                       CASE WHEN UPPER(COALESCE(p.product_type, 'INVENTORY')) IN ('SERVICE','NON_INVENTORY')
                            THEN UPPER(p.product_type) ELSE 'INVENTORY' END,
                       COALESCE(c.vat_rate_percent, 0)
                FROM products p LEFT JOIN categories c ON c.category_id = p.category_id
                WHERE p.product_id = ? FOR UPDATE OF p
                """)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("A cart product no longer exists.");
                return new CatalogLine(rs.getInt(1), rs.getString(2), money(rs.getBigDecimal(3)),
                        rs.getString(4), percent(rs.getBigDecimal(5)));
            }
        }
    }

    private static int insertSale(Connection c, CheckoutRequest r, UUID deviceId, int userId, String userName,
                                  int locationId, CashDrawerContext drawer, ServerReceiptNumberManager.ReceiptNumber receipt,
                                  BigDecimal subtotal, BigDecimal discountAmount, BigDecimal discountPercent,
                                  BigDecimal vat, BigDecimal total, Approval saleApproval) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO sales (location_id,user_id,customer_id,total_amount,status,payment_method,payment_status,
                  amount_paid,user_name,receipt_number,receipt_device_id,receipt_sequence,subtotal_amount,
                  discount_percent,discount_amount,vat_amount,vat_rate_percent,vat_mode,payment_reference,
                  transaction_source,device_id,cash_drawer_id,cash_drawer_name,cash_drawer_session_id,
                  discount_override_reason,discount_override_by_user_id,discount_override_by_name,completed_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setInt(i++, locationId); ps.setInt(i++, userId); setInt(ps, i++, r.customerId());
            ps.setBigDecimal(i++, total); ps.setString(i++, "COMPLETED"); ps.setString(i++, r.paymentMethod());
            ps.setString(i++, "ACCOUNT".equals(r.paymentMethod()) ? "UNPAID" : "PAID");
            ps.setBigDecimal(i++, "ACCOUNT".equals(r.paymentMethod()) ? BigDecimal.ZERO : total);
            ps.setString(i++, userName); ps.setString(i++, receipt.receiptNumber());
            ps.setString(i++, receipt.deviceId()); ps.setInt(i++, receipt.sequence()); ps.setBigDecimal(i++, subtotal);
            ps.setBigDecimal(i++, discountPercent); ps.setBigDecimal(i++, discountAmount); ps.setBigDecimal(i++, vat);
            ps.setBigDecimal(i++, total.signum() == 0 ? BigDecimal.ZERO : vat.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP));
            ps.setString(i++, vat.signum() == 0 ? "" : "SERVER"); ps.setString(i++, text(r.paymentReference()));
            ps.setString(i++, "LAN_API"); ps.setString(i++, deviceId.toString()); setLong(ps, i++, drawer.cashDrawerId());
            ps.setString(i++, drawer.drawerName()); setLong(ps, i++, drawer.sessionId());
            ps.setString(i++, text(r.saleDiscountOverrideReason()));
            setInt(ps, i++, saleApproval == null ? null : saleApproval.approverUserId());
            ps.setString(i, saleApproval == null ? null : saleApproval.approverName());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Failed to create sale.");
                return keys.getInt(1);
            }
        }
    }

    private static int insertSaleItem(Connection c, int saleId, ValidatedLine line, BigDecimal charged) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO sale_items (sale_id,product_id,quantity,unit_price,original_unit_price,
                  discount_percent,discount_amount,price_override_reason,price_override_by_user_id,
                  price_override_by_name,product_type) VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, saleId); ps.setInt(2, line.catalog().productId()); ps.setInt(3, line.quantity());
            ps.setBigDecimal(4, charged); ps.setBigDecimal(5, line.catalog().catalogPrice());
            ps.setBigDecimal(6, line.discountPercent()); ps.setBigDecimal(7, line.lineDiscountAmount());
            ps.setString(8, line.priceApproval() == null ? null : line.priceApproval().reason());
            setInt(ps, 9, line.priceApproval() == null ? null : line.priceApproval().approverUserId());
            ps.setString(10, line.priceApproval() == null ? null : line.priceApproval().approverName());
            ps.setString(11, line.catalog().productType()); ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Failed to create sale item.");
                return keys.getInt(1);
            }
        }
    }

    private static void applyInventory(Connection c, int saleId, int saleItemId, ValidatedLine line,
                                       int locationId, int userId, String userName, UUID deviceId) throws SQLException {
        try (PreparedStatement ensure = c.prepareStatement("INSERT INTO inventory (product_id,location_id,quantity_on_hand,reorder_level) VALUES (?,?,0,0) ON CONFLICT (product_id,location_id) DO NOTHING");
             PreparedStatement update = c.prepareStatement("UPDATE inventory SET quantity_on_hand=quantity_on_hand-? WHERE product_id=? AND location_id=?");
             PreparedStatement movement = c.prepareStatement("""
                     INSERT INTO inventory_movements (product_id,location_id,change_qty,reason,note,user_name,
                       sale_id,sale_item_id,device_id,device_name,user_id) VALUES (?,?,?,?,?,?,?,?,?,?,?)
                     """)) {
            ensure.setInt(1, line.catalog().productId()); ensure.setInt(2, locationId); ensure.executeUpdate();
            update.setInt(1, line.quantity()); update.setInt(2, line.catalog().productId()); update.setInt(3, locationId); update.executeUpdate();
            movement.setInt(1, line.catalog().productId()); movement.setInt(2, locationId); movement.setInt(3, -line.quantity());
            movement.setString(4, "SALE"); movement.setString(5, "sale_id=" + saleId); movement.setString(6, userName);
            movement.setInt(7, saleId); movement.setInt(8, saleItemId); movement.setString(9, deviceId.toString());
            movement.setString(10, null); movement.setInt(11, userId); movement.executeUpdate();
        }
    }

    private static void chargeCustomerAccount(Connection c, int customerId, BigDecimal amount, int userId,
                                              String userName, UUID deviceId, int locationId) throws SQLException {
        try (PreparedStatement lock = c.prepareStatement("SELECT credit_limit,current_balance FROM customer_accounts WHERE customer_id=? AND is_active=TRUE FOR UPDATE")) {
            lock.setInt(1, customerId);
            try (ResultSet rs = lock.executeQuery()) {
                if (!rs.next()) throw new SQLException("Customer account is not active.");
                BigDecimal next = money(rs.getBigDecimal(2)).add(amount);
                if (next.compareTo(money(rs.getBigDecimal(1))) > 0) throw new SQLException("Customer account credit limit would be exceeded.");
                try (PreparedStatement update = c.prepareStatement("UPDATE customer_accounts SET current_balance=? WHERE customer_id=?")) {
                    update.setBigDecimal(1, next); update.setInt(2, customerId); update.executeUpdate();
                }
            }
        }
    }

    private static void insertCustomerTransaction(Connection c, int customerId, int saleId, BigDecimal amount,
                                                  String type, String userName, UUID deviceId,
                                                  int locationId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO customer_account_transactions (customer_id,sale_id,location_id,transaction_type,amount,
                  note,user_name,device_id,created_at) VALUES (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """)) {
            ps.setInt(1, customerId); ps.setInt(2, saleId); ps.setInt(3, locationId);
            ps.setString(4, type); ps.setBigDecimal(5, amount);
            ps.setString(6, type + ". sale_id=" + saleId); ps.setString(7, userName);
            ps.setString(8, deviceId.toString()); ps.executeUpdate();
        }
    }

    private static void insertSaleAudit(Connection c, Integer saleId, Integer saleItemId, Integer customerId,
                                        Integer productId, int locationId, int userId, String userName, UUID deviceId,
                                        String action, String scope, BigDecimal amount, String note) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO sale_audit_log (sale_id,sale_item_id,customer_id,product_id,location_id,
                  action_type,action_scope,amount,note,user_id,user_name,device_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            setInt(ps,1,saleId); setInt(ps,2,saleItemId); setInt(ps,3,customerId); setInt(ps,4,productId);
            ps.setInt(5,locationId); ps.setString(6,action); ps.setString(7,scope); ps.setBigDecimal(8,amount);
            ps.setString(9,note); ps.setInt(10,userId); ps.setString(11,userName); ps.setString(12,deviceId.toString()); ps.executeUpdate();
        }
    }

    private static void insertOutbox(Connection c, int saleId, String receipt, int locationId, int userId,
                                     UUID deviceId, String payment, String status, BigDecimal total,
                                     Long drawerSessionId) throws SQLException {
        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("sale_id",saleId); payload.put("receipt_number",receipt); payload.put("location_id",locationId);
        payload.put("user_id",userId); payload.put("device_id",deviceId.toString()); payload.put("payment_method",payment);
        payload.put("payment_status",status); payload.put("total_amount",total);
        payload.put("cash_drawer_session_id",drawerSessionId == null ? "" : drawerSessionId);
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO sync_outbox (event_type,location_id,device_id,user_id,payload,origin_location_id,origin_device_id)
                VALUES ('SALE_COMPLETED',?,?,?,?::jsonb,?,?)
                """)) {
            ps.setInt(1,locationId); ps.setString(2,deviceId.toString()); ps.setInt(3,userId); ps.setString(4,GSON.toJson(payload));
            ps.setInt(5,locationId); ps.setString(6,deviceId.toString()); ps.executeUpdate();
        }
    }

    private static boolean hasPermission(Connection c, int userId, String key) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id
                JOIN permissions p ON p.permission_id=rp.permission_id
                WHERE u.user_id=? AND UPPER(p.permission_key)=? LIMIT 1
                """)) {
            ps.setInt(1,userId); ps.setString(2,key); try (ResultSet rs=ps.executeQuery()) { return rs.next(); }
        }
    }

    private static void requirePermission(Connection c, int userId, String key) throws SQLException {
        if (!hasPermission(c,userId,key)) throw new SQLException("You do not have permission for this operation.");
    }

    private static BigDecimal money(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).setScale(2,RoundingMode.HALF_UP); }
    private static BigDecimal percent(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).max(BigDecimal.ZERO).min(HUNDRED).setScale(2,RoundingMode.HALF_UP); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    static boolean requiresPaymentReference(String paymentMethod) {
        return "CARD".equals(paymentMethod)
                || "CHEQUE".equals(paymentMethod)
                || "MMG".equals(paymentMethod);
    }
    private static String text(String value) { return blank(value) ? null : value.trim(); }
    private static void setInt(PreparedStatement ps,int i,Integer v)throws SQLException{if(v==null)ps.setNull(i,Types.INTEGER);else ps.setInt(i,v);}
    private static void setLong(PreparedStatement ps,int i,Long v)throws SQLException{if(v==null)ps.setNull(i,Types.BIGINT);else ps.setLong(i,v);}

    interface ApprovalConsumer { Approval consume(String token,String permission,String action,String reason)throws Exception; }
    static final class RuleViolation extends Exception {
        private final int status;
        private final String code;
        private final String safeMessage;
        private final boolean retryable;
        RuleViolation(int status,String code,String safeMessage,boolean retryable){super(safeMessage);this.status=status;this.code=code;this.safeMessage=safeMessage;this.retryable=retryable;}
        int status(){return status;} String code(){return code;} String safeMessage(){return safeMessage;} boolean retryable(){return retryable;}
    }
    record Approval(int approverUserId,String approverName,String reason) { }
    private record SaleConfig(boolean vatEnabled,boolean departmentVat,BigDecimal fixedVatRate,BigDecimal discountLimit) { }
    private record CatalogLine(int productId,String name,BigDecimal catalogPrice,String productType,BigDecimal departmentVatRate) { }
    private record ValidatedLine(CatalogLine catalog,int quantity,BigDecimal enteredPrice,BigDecimal discountPercent,
                                 BigDecimal lineDiscountAmount,Approval priceApproval,Approval discountApproval) { }
    record CheckoutRequest(String paymentMethod,String paymentReference,Integer customerId,BigDecimal saleDiscountPercent,
                           BigDecimal cashCollected,String saleDiscountApprovalToken,String saleDiscountOverrideReason,
                           List<CheckoutLine> lines) { }
    record CheckoutLine(int productId,int quantity,BigDecimal unitPrice,BigDecimal discountPercent,
                        String priceApprovalToken,String priceOverrideReason,String discountApprovalToken,
                        String discountOverrideReason) { }
}
