package services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

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

/** Server-only held-cart operations. No caller-provided store, user, or catalog metadata is trusted. */
final class LanHeldCartService {
    private static final Gson GSON = new Gson();
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private LanHeldCartService() { }

    static Map<String, Object> settings(Connection connection, int userId, int locationId) throws Exception {
        requirePermission(connection, userId, "MAKE_SALE");
        boolean vatEnabled = false;
        boolean departmentVat = false;
        BigDecimal fixedVatRate = BigDecimal.ZERO;
        BigDecimal discountLimit = BigDecimal.valueOf(5);
        boolean roundToNearestTwenty = true;
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT COALESCE(vat_enabled,FALSE),COALESCE(vat_use_department_rates,FALSE),
                       COALESCE(vat_fixed_rate_percent,0),COALESCE(sale_discount_limit_percent,5),
                       COALESCE(round_sales_to_nearest_twenty,TRUE)
                FROM company_customization WHERE location_id=?
                """)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    vatEnabled = rs.getBoolean(1);
                    departmentVat = rs.getBoolean(2);
                    fixedVatRate = percent(rs.getBigDecimal(3));
                    discountLimit = percent(rs.getBigDecimal(4));
                    roundToNearestTwenty = rs.getBoolean(5);
                }
            }
        }
        List<Map<String, Object>> rates = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT category_id,COALESCE(vat_rate_percent,0) FROM categories ORDER BY category_id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rates.add(Map.of("categoryId", rs.getInt(1), "ratePercent", percent(rs.getBigDecimal(2))));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("vatEnabled", vatEnabled);
        result.put("departmentVat", departmentVat);
        result.put("fixedVatRate", fixedVatRate);
        result.put("discountLimit", discountLimit);
        result.put("roundToNearestTwenty", roundToNearestTwenty);
        try(PreparedStatement ps=connection.prepareStatement("SELECT product_id FROM products WHERE sku='SMARTSTOCK-MISC' LIMIT 1");ResultSet rs=ps.executeQuery()){
            result.put("miscProductId",rs.next()?rs.getInt(1):null);
        }
        result.put("departmentRates", rates);
        return result;
    }

    static Map<String, Object> create(Connection connection, JsonObject body, UUID deviceId,
                                      int userId, String userName, int locationId,
                                      LanSalesService.ApprovalConsumer approvals) throws Exception {
        CreateRequest request = GSON.fromJson(body, CreateRequest.class);
        validateCreate(request);
        requirePermission(connection, userId, "MAKE_SALE");
        boolean canChangePrice = hasPermission(connection, userId, "CHANGE_SALE_ITEM_PRICE");
        boolean canDiscount = hasPermission(connection, userId, "APPLY_SALE_DISCOUNT");
        boolean canOverrideSaleDiscount = hasPermission(connection, userId, "SALE_DISCOUNT_OVERRIDE");
        BigDecimal saleDiscount = percent(request.saleDiscountPercent());
        BigDecimal discountLimit = loadDiscountLimit(connection, locationId);
        if (saleDiscount.compareTo(discountLimit) > 0) {
            if (!canOverrideSaleDiscount) {
                approvals.consume(request.saleDiscountApprovalToken(), "SALE_DISCOUNT_OVERRIDE",
                        "Sale Discount Override", request.saleDiscountOverrideReason());
            }
        } else if (saleDiscount.signum() > 0 && !canDiscount) {
            approvals.consume(request.saleDiscountApprovalToken(), "APPLY_SALE_DISCOUNT",
                    "Sale Discount Approval", request.saleDiscountOverrideReason());
        }
        if (request.customerId() != null) requireActiveCustomer(connection, request.customerId());

        List<ValidatedLine> lines = new ArrayList<>();
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal afterLineDiscounts = BigDecimal.ZERO;
        for (CreateLine line : request.lines()) {
            CatalogLine catalog = loadCatalog(connection, line.productId());
            String miscName = validateMiscLine(connection,userId,line,catalog);
            BigDecimal unitPrice = normalizeHeldUnitPrice(line.unitPrice(), line.miscItem());
            if (!line.miscItem()
                    && unitPrice.compareTo(normalizeHeldUnitPrice(catalog.price(), false)) != 0
                    && !canChangePrice) {
                approvals.consume(line.priceApprovalToken(), "CHANGE_SALE_ITEM_PRICE",
                        "Price Override", line.priceOverrideReason());
            }
            if (unitPrice.signum() < 0) throw rule(400, "VALIDATION_ERROR", "Item prices cannot be negative.");
            BigDecimal discount = percent(line.discountPercent());
            if (discount.signum() > 0 && !canDiscount) {
                approvals.consume(line.discountApprovalToken(), "APPLY_SALE_DISCOUNT",
                        "Item Discount Override", line.discountOverrideReason());
            }
            BigDecimal lineGross = money(unitPrice.multiply(BigDecimal.valueOf(line.quantity())));
            BigDecimal lineNet = money(lineGross.subtract(lineGross.multiply(discount)
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP)).max(BigDecimal.ZERO));
            gross = gross.add(lineGross);
            afterLineDiscounts = afterLineDiscounts.add(lineNet);
            lines.add(new ValidatedLine(catalog, line.quantity(), unitPrice, discount,miscName,line.miscItem()));
        }
        BigDecimal saleDiscountAmount = money(afterLineDiscounts.multiply(saleDiscount)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP));
        BigDecimal total = money(afterLineDiscounts.subtract(saleDiscountAmount).max(BigDecimal.ZERO));
        BigDecimal allDiscounts = money(gross.subtract(afterLineDiscounts).add(saleDiscountAmount));

        int heldCartId;
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO held_carts(location_id,user_id,user_name,customer_id,hold_name,payment_method,
                  subtotal_amount,discount_percent,discount_amount,total_amount,status)
                VALUES (?,?,?,?,?,?,?,?,?,?,'OPEN')
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, locationId); ps.setInt(2, userId); ps.setString(3, userName);
            setInt(ps, 4, request.customerId()); ps.setString(5, clean(request.holdName()));
            ps.setString(6, payment(request.paymentMethod())); ps.setBigDecimal(7, money(gross));
            ps.setBigDecimal(8, saleDiscount); ps.setBigDecimal(9, allDiscounts); ps.setBigDecimal(10, total);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Held cart could not be created.");
                heldCartId = keys.getInt(1);
            }
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO held_cart_items(held_cart_id,product_id,product_name,is_misc_item,description,sku,
                  unit_price,quantity,discount_percent,product_type)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """)) {
            for (ValidatedLine line : lines) {
                ps.setInt(1, heldCartId); ps.setInt(2, line.catalog().productId());
                ps.setString(3, line.displayName()); ps.setBoolean(4,line.miscItem());
                ps.setString(5, line.catalog().description()); ps.setString(6, line.catalog().sku());
                ps.setBigDecimal(7, line.unitPrice()); ps.setInt(8, line.quantity());
                ps.setBigDecimal(9, line.discountPercent()); ps.setString(10, line.catalog().productType()); ps.addBatch();
            }
            ps.executeBatch();
        }
        audit(connection, locationId, userId, userName, deviceId, "HELD_CART_CREATED",
                lines.size(), total, "held_cart_id=" + heldCartId + "; hold_name=" + clean(request.holdName()));
        return Map.of("heldCartId", heldCartId, "total", total, "itemCount", lines.size());
    }

    static List<Map<String, Object>> list(Connection connection, int userId, int locationId) throws Exception {
        requirePermission(connection, userId, "MAKE_SALE");
        List<Map<String, Object>> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT hc.held_cart_id,hc.created_at,COALESCE(hc.hold_name,''),COALESCE(hc.user_name,''),
                       COALESCE(ca.name,''),COUNT(hci.held_cart_item_id),COALESCE(hc.total_amount,0)
                FROM held_carts hc
                LEFT JOIN held_cart_items hci ON hci.held_cart_id=hc.held_cart_id
                LEFT JOIN customer_accounts ca ON ca.customer_id=hc.customer_id
                WHERE hc.location_id=? AND UPPER(COALESCE(hc.status,'OPEN'))='OPEN'
                GROUP BY hc.held_cart_id,hc.created_at,hc.hold_name,hc.user_name,ca.name,hc.total_amount
                ORDER BY hc.created_at DESC LIMIT 500
                """)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("heldCartId", rs.getInt(1)); row.put("createdAtEpochMillis", rs.getTimestamp(2).getTime());
                    row.put("holdName", rs.getString(3)); row.put("userName", rs.getString(4));
                    row.put("customerName", rs.getString(5)); row.put("itemCount", rs.getInt(6));
                    row.put("total", rs.getBigDecimal(7)); result.add(row);
                }
            }
        }
        return result;
    }

    static Map<String, Object> resume(Connection connection, int heldCartId, UUID deviceId,
                                      int userId, String userName, int locationId) throws Exception {
        requirePermission(connection, userId, "MAKE_SALE");
        Map<String, Object> result = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT customer_id,COALESCE(payment_method,''),COALESCE(discount_percent,0)
                FROM held_carts
                WHERE held_cart_id=? AND location_id=? AND UPPER(COALESCE(status,'OPEN'))='OPEN'
                FOR UPDATE
                """)) {
            ps.setInt(1, heldCartId); ps.setInt(2, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw rule(404, "HELD_CART_NOT_FOUND", "Held cart is no longer available.");
                result.put("heldCartId", heldCartId); result.put("customerId", rs.getObject(1));
                result.put("paymentMethod", rs.getString(2)); result.put("saleDiscountPercent", rs.getBigDecimal(3));
            }
        }
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT hci.product_id,hci.product_name,COALESCE(p.size,''),hci.description,hci.sku,
                       hci.unit_price,hci.quantity,COALESCE(hci.discount_percent,0),
                       COALESCE(hci.product_type,'INVENTORY'),p.category_id,COALESCE(p.price,0),hci.is_misc_item
                FROM held_cart_items hci JOIN products p ON p.product_id=hci.product_id
                WHERE hci.held_cart_id=? ORDER BY hci.held_cart_item_id
                """)) {
            ps.setInt(1, heldCartId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("productId", rs.getInt(1)); item.put("productName", rs.getString(2));
                    item.put("size", rs.getString(3)); item.put("description", rs.getString(4));
                    item.put("sku", rs.getString(5)); item.put("unitPrice", rs.getBigDecimal(6));
                    item.put("quantity", rs.getInt(7)); item.put("discountPercent", rs.getBigDecimal(8));
                    item.put("productType", rs.getString(9)); item.put("categoryId", rs.getObject(10));
                    item.put("catalogPrice", rs.getBigDecimal(11));
                    item.put("miscItem",rs.getBoolean(12));
                    items.add(item);
                    BigDecimal line = money(rs.getBigDecimal(6).multiply(BigDecimal.valueOf(rs.getInt(7))));
                    total = total.add(line.subtract(line.multiply(rs.getBigDecimal(8))
                            .divide(HUNDRED, 2, RoundingMode.HALF_UP)));
                }
            }
        }
        if (items.isEmpty()) throw rule(409, "HELD_CART_EMPTY", "Held cart has no available items.");
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE held_carts SET status='RESUMED',resumed_at=CURRENT_TIMESTAMP,
                  resumed_by_user_id=?,resumed_by_name=?,updated_at=CURRENT_TIMESTAMP
                WHERE held_cart_id=? AND location_id=? AND UPPER(COALESCE(status,'OPEN'))='OPEN'
                """)) {
            ps.setInt(1, userId); ps.setString(2, userName); ps.setInt(3, heldCartId); ps.setInt(4, locationId);
            if (ps.executeUpdate() != 1) throw rule(409, "HELD_CART_CHANGED", "Held cart changed before it could be resumed.");
        }
        result.put("items", items);
        audit(connection, locationId, userId, userName, deviceId, "HELD_CART_RESUMED",
                items.size(), money(total), "held_cart_id=" + heldCartId);
        return result;
    }

    private static CatalogLine loadCatalog(Connection connection, int productId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT product_id,COALESCE(name,''),COALESCE(description,''),COALESCE(sku,''),COALESCE(price,0),
                       CASE WHEN UPPER(COALESCE(product_type,'INVENTORY')) IN ('SERVICE','NON_INVENTORY')
                            THEN UPPER(product_type) ELSE 'INVENTORY' END
                FROM products WHERE product_id=?
                """)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw rule(400, "PRODUCT_NOT_FOUND", "A held-cart product no longer exists.");
                return new CatalogLine(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        money(rs.getBigDecimal(5)), rs.getString(6));
            }
        }
    }

    private static BigDecimal loadDiscountLimit(Connection connection, int locationId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT COALESCE(sale_discount_limit_percent,5)
                FROM company_customization WHERE location_id=?
                """)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? percent(rs.getBigDecimal(1)) : BigDecimal.valueOf(5);
            }
        }
    }

    private static String validateMiscLine(Connection c,int userId,CreateLine line,CatalogLine catalog)throws Exception{
        if(!line.miscItem()){
            if("SMARTSTOCK-MISC".equals(catalog.sku()))throw rule(400,"VALIDATION_ERROR","The miscellaneous item reference requires a misc sale item.");
            return null;
        }
        requirePermission(c,userId,"ADD_MISC_SALE_ITEM");
        if(!"SMARTSTOCK-MISC".equals(catalog.sku())||!"NON_INVENTORY".equals(catalog.productType()))
            throw rule(400,"VALIDATION_ERROR","The miscellaneous item reference is invalid.");
        String name=clean(line.miscItemName());
        if(name.isEmpty()||name.length()>200)throw rule(400,"VALIDATION_ERROR","Misc item name is required and must be 200 characters or fewer.");
        if(money(line.unitPrice()).signum()<=0)throw rule(400,"VALIDATION_ERROR","Misc item price must be greater than zero.");
        return name;
    }

    private static void validateCreate(CreateRequest request) throws RuleViolation {
        if (request == null || request.lines() == null || request.lines().isEmpty())
            throw rule(400, "VALIDATION_ERROR", "Cart is empty.");
        if (request.lines().size() > 200) throw rule(400, "VALIDATION_ERROR", "Cart has too many lines.");
        if (clean(request.holdName()).length() > 200) throw rule(400, "VALIDATION_ERROR", "Hold name is too long.");
        payment(request.paymentMethod());
        for (CreateLine line : request.lines()) {
            if (line.productId() <= 0 || line.quantity() <= 0 || line.quantity() > 100_000)
                throw rule(400, "VALIDATION_ERROR", "Each held item requires a valid product and quantity.");
        }
    }

    private static void requireActiveCustomer(Connection c, int customerId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM customer_accounts WHERE customer_id=? AND is_active=TRUE")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw rule(400, "CUSTOMER_NOT_FOUND", "Customer account is not active.");
            }
        }
    }

    private static void audit(Connection c, int locationId, int userId, String userName, UUID deviceId,
                              String action, int count, BigDecimal total, String note) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO sale_audit_log(location_id,action_type,action_scope,amount,quantity,note,user_id,user_name,device_id)
                VALUES (?,?,?,?,?,?,?,?,?)
                """)) {
            ps.setInt(1, locationId); ps.setString(2, action); ps.setString(3, "HELD_CART");
            ps.setBigDecimal(4, total); ps.setInt(5, count); ps.setString(6, note);
            ps.setInt(7, userId); ps.setString(8, userName); ps.setObject(9, deviceId); ps.executeUpdate();
        }
    }

    private static boolean hasPermission(Connection c, int userId, String key) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id
                JOIN permissions p ON p.permission_id=rp.permission_id
                WHERE u.user_id=? AND UPPER(p.permission_key)=? LIMIT 1
                """)) {
            ps.setInt(1, userId); ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private static void requirePermission(Connection c, int userId, String key) throws Exception {
        if (!hasPermission(c, userId, key)) throw rule(403, "PERMISSION_DENIED", "You do not have permission for this operation.");
    }

    private static String payment(String value) throws RuleViolation {
        String result = value == null ? "" : value.trim().toUpperCase();
        if (!result.isEmpty() && !List.of("CASH", "CARD", "MMG", "ACCOUNT", "CHEQUE", "BANK_TRANSFER").contains(result))
            throw rule(400, "VALIDATION_ERROR", "Unsupported payment method.");
        return result;
    }

    private static BigDecimal money(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP); }
    static BigDecimal normalizeHeldUnitPrice(BigDecimal value, boolean miscItem) {
        return miscItem ? money(value) : utils.CurrencyFormatter.normalize(value);
    }
    private static BigDecimal percent(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).max(BigDecimal.ZERO).min(HUNDRED).setScale(2, RoundingMode.HALF_UP); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static void setInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) ps.setNull(index, Types.INTEGER); else ps.setInt(index, value);
    }
    private static RuleViolation rule(int status, String code, String message) { return new RuleViolation(status, code, message); }

    static final class RuleViolation extends Exception {
        private final int status;
        private final String code;
        private final String safeMessage;

        RuleViolation(int status, String code, String safeMessage) {
            super(safeMessage);
            this.status = status;
            this.code = code;
            this.safeMessage = safeMessage;
        }

        int status() { return status; }
        String code() { return code; }
        String safeMessage() { return safeMessage; }
    }
    record CreateRequest(String holdName, String paymentMethod, Integer customerId,
                         BigDecimal saleDiscountPercent,
                         String saleDiscountApprovalToken, String saleDiscountOverrideReason,
                         List<CreateLine> lines) { }
    record CreateLine(int productId, int quantity, BigDecimal unitPrice, BigDecimal discountPercent,
                      String priceApprovalToken, String priceOverrideReason,
                      String discountApprovalToken, String discountOverrideReason,
                      String miscItemName,boolean miscItem) { }
    private record CatalogLine(int productId, String name, String description, String sku,
                               BigDecimal price, String productType) { }
    private record ValidatedLine(CatalogLine catalog, int quantity, BigDecimal unitPrice,
                                 BigDecimal discountPercent,String miscItemName,boolean miscItem) {
        String displayName(){return miscItem?miscItemName:catalog.name();}
    }
}
