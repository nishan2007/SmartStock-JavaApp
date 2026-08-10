package services;

import data.DB;
import models.CashDrawerContext;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class ServerCustomOrderDataService {
    private ServerCustomOrderDataService() {
    }

    public static List<CustomItemOption> listActiveItems() throws SQLException {
        try (Connection conn = DB.getConnection()) {
            return listActiveItems(conn);
        }
    }

    public static List<CustomItemOption> listActiveItems(Connection conn) throws SQLException {
        List<CustomItemOption> options = new ArrayList<>();
        String sql = """
                SELECT custom_item_id, item_name, sku, product_type, pricing_type, fixed_price,
                       area_price, area_price_unit, dimension_unit, max_width, max_length,
                       COALESCE(has_variants, FALSE) AS has_variants,
                       is_active
                FROM custom_order_items
                ORDER BY is_active DESC, item_name
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (!rs.getBoolean("is_active")) {
                    continue;
                }
                BigDecimal fixedPrice = rs.getBigDecimal("fixed_price");
                BigDecimal areaPrice = fixedPrice == null ? rs.getBigDecimal("area_price") : fixedPrice;
                if (fixedPrice == null && "AREA".equals(rs.getString("pricing_type"))) {
                    fixedPrice = areaPrice;
                }
                options.add(new CustomItemOption(
                        rs.getLong("custom_item_id"),
                        rs.getString("item_name"),
                        rs.getString("sku"),
                        rs.getString("product_type"),
                        rs.getString("pricing_type"),
                        fixedPrice,
                        rs.getBoolean("has_variants"),
                        areaPrice,
                        rs.getString("area_price_unit"),
                        rs.getString("dimension_unit"),
                        rs.getBigDecimal("max_width"),
                        rs.getBigDecimal("max_length")
                ));
            }
        }
        return options;
    }

    public static List<VariantOption> listActiveVariants(long customItemId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            return listActiveVariants(conn, customItemId);
        }
    }

    public static List<VariantOption> listActiveVariants(Connection conn, long customItemId) throws SQLException {
        List<VariantOption> variants = new ArrayList<>();
        String sql = """
                SELECT custom_variant_id, variant_name, sku, fixed_price
                FROM custom_order_item_variants
                WHERE custom_item_id = ?
                  AND is_active = TRUE
                ORDER BY variant_name
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, customItemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    variants.add(new VariantOption(
                            rs.getLong("custom_variant_id"),
                            rs.getString("variant_name"),
                            rs.getString("sku"),
                            rs.getBigDecimal("fixed_price")
                    ));
                }
            }
        }
        return variants;
    }

    public static List<PrintMaterialOption> listActivePrintMaterials() throws SQLException {
        try (Connection conn = DB.getConnection()) {
            return listActivePrintMaterials(conn);
        }
    }

    public static List<PrintMaterialOption> listActivePrintMaterials(Connection conn) throws SQLException {
        List<PrintMaterialOption> materials = new ArrayList<>();
        String sql = """
                SELECT print_material_id, material_name
                FROM custom_order_print_materials
                WHERE is_active = TRUE
                ORDER BY material_name
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                materials.add(new PrintMaterialOption(rs.getLong("print_material_id"), rs.getString("material_name")));
            }
        }
        return materials;
    }

    public static List<PrintSizePresetOption> listActivePrintSizePresets(long printMaterialId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            return listActivePrintSizePresets(conn, printMaterialId);
        }
    }

    public static List<PrintSizePresetOption> listActivePrintSizePresets(Connection conn, long printMaterialId) throws SQLException {
        List<PrintSizePresetOption> presets = new ArrayList<>();
        String sql = """
                SELECT print_size_preset_id, print_material_id, preset_name,
                       COALESCE(pricing_mode, 'FIXED_PRESET') AS pricing_mode, fixed_price
                FROM custom_order_print_size_presets
                WHERE is_active = TRUE
                  AND print_material_id = ?
                ORDER BY preset_name
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, printMaterialId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    presets.add(new PrintSizePresetOption(
                            rs.getLong("print_size_preset_id"),
                            rs.getLong("print_material_id"),
                            rs.getString("preset_name"),
                            rs.getString("pricing_mode"),
                            rs.getBigDecimal("fixed_price")
                    ));
                }
            }
        }
        return presets;
    }

    public static List<String> listActiveDesignPlacements() throws SQLException {
        try (Connection conn = DB.getConnection()) {
            return listActiveDesignPlacements(conn);
        }
    }

    public static List<String> listActiveDesignPlacements(Connection conn) throws SQLException {
        List<String> placements = new ArrayList<>();
        String sql = """
                SELECT placement_name
                FROM custom_order_design_placements
                WHERE is_active = TRUE
                ORDER BY sort_order, placement_name
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                placements.add(rs.getString("placement_name"));
            }
        }
        return placements;
    }

    public static List<CustomerOption> searchCustomers(String search) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            return searchCustomers(conn, search);
        }
    }

    public static List<CustomerOption> searchCustomers(Connection conn, String search) throws SQLException {
        List<CustomerOption> customers = new ArrayList<>();
        String searchText = search == null ? "" : search;
        String sql = """
                SELECT customer_id, name, phone
                FROM customer_accounts
                WHERE is_active = TRUE
                  AND (? = '' OR LOWER(name) LIKE LOWER(?) OR COALESCE(phone, '') LIKE ?)
                ORDER BY name
                LIMIT 100
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + searchText + "%";
            ps.setString(1, searchText);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    customers.add(new CustomerOption(
                            rs.getInt("customer_id"),
                            rs.getString("name"),
                            rs.getString("phone")
                    ));
                }
            }
        }
        return customers;
    }

    public static List<EmployeeOption> listActiveEmployees() throws SQLException {
        try (Connection conn = DB.getConnection()) {
            return listActiveEmployees(conn, null);
        }
    }

    public static List<EmployeeOption> listActiveEmployees(Connection conn, Integer locationId) throws SQLException {
        List<EmployeeOption> employees = new ArrayList<>();
        String sql = """
                SELECT u.user_id, COALESCE(NULLIF(u.full_name, ''), u.username) AS employee_name
                FROM users u
                WHERE COALESCE(u.is_active, TRUE) = TRUE
                  AND (? IS NULL OR EXISTS (
                      SELECT 1 FROM user_locations ul
                      WHERE ul.user_id = u.user_id AND ul.location_id = ?
                  ) OR NOT EXISTS (SELECT 1 FROM user_locations any_ul WHERE any_ul.user_id = u.user_id))
                ORDER BY employee_name
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableInteger(ps, 1, locationId);
            setNullableInteger(ps, 2, locationId);
            try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                employees.add(new EmployeeOption(rs.getInt("user_id"), rs.getString("employee_name")));
            }
            }
        }
        return employees;
    }

    public static int resolveOrderCustomerId(Connection conn, CustomerOption selectedCustomer, String name, String phone) throws SQLException {
        if (selectedCustomer != null && selectedCustomer.customerId() != null) {
            if (selectedCustomer.phone() == null || selectedCustomer.phone().isBlank()) {
                updateCustomerPhone(conn, selectedCustomer.customerId(), phone);
            }
            return selectedCustomer.customerId();
        }
        return createGeneralCustomerAccount(conn, name, phone);
    }

    private static void updateCustomerPhone(Connection conn, int customerId, String phone) throws SQLException {
        String sql = "UPDATE customer_accounts SET phone = ? WHERE customer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    public static String saveCustomOrder(OrderSaveRequest request) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String result = saveCustomOrder(conn, request);
                conn.commit();
                return result;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static String saveCustomOrder(Connection conn, OrderSaveRequest request) throws SQLException {
        String orderNumber = generateOrderNumber();
        String orderSql = """
                INSERT INTO custom_orders (
                    order_number, customer_id, customer_name, customer_phone, status, due_date,
                    order_notes, total_amount, amount_paid, balance_due, payment_method,
                    payment_reference, payment_status, taken_by_user_id, taken_by_name,
                    location_id, location_name, device_id, device_name,
                    cash_drawer_id, cash_drawer_name, cash_drawer_session_id,
                    minimum_deposit_required, deposit_override_reason,
                    deposit_override_by_user_id, deposit_override_by_name
                )
                VALUES (?, ?, ?, ?, 'NEW', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String lineSql = """
                INSERT INTO custom_order_lines (
                    custom_order_id, custom_item_id, item_name, pricing_type, unit_price,
                    line_total, customization_details, order_instructions, sort_order,
                    custom_variant_id, variant_name,
                    width_value, length_value, dimension_unit, area_value, area_unit, area_price,
                    base_item_price, print_material_id, print_material_name,
                    print_size_preset_id, print_size_name, print_charge, print_line_count,
                    original_line_total, line_discount_percent, line_discount_amount,
                    line_discount_by_user_id, line_discount_by_name, line_discount_reason,
                    minimum_deposit_percent, original_base_price, price_override_price,
                    price_override_reason, price_override_by_user_id, price_override_by_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String addonSql = """
                INSERT INTO custom_order_line_print_addons (
                    custom_order_line_id, print_material_id, print_material_name,
                    print_size_preset_id, print_size_name, pricing_mode,
                    print_description, print_charge, print_line_count, sort_order
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String updateCustomItemSoldSql = """
                UPDATE custom_order_items
                SET sold_quantity = COALESCE(sold_quantity, 0) + 1,
                    quantity_on_hand = CASE WHEN COALESCE(product_type, 'INVENTORY') = 'INVENTORY' AND COALESCE(has_variants, FALSE) = FALSE THEN quantity_on_hand - 1 ELSE quantity_on_hand END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_item_id = ?
                """;
        String updateCustomVariantSoldSql = """
                UPDATE custom_order_item_variants v
                SET sold_quantity = COALESCE(v.sold_quantity, 0) + 1,
                    quantity_on_hand = CASE WHEN COALESCE(i.product_type, 'INVENTORY') = 'INVENTORY' THEN v.quantity_on_hand - 1 ELSE v.quantity_on_hand END,
                    updated_at = CURRENT_TIMESTAMP
                FROM custom_order_items i
                WHERE v.custom_item_id = i.custom_item_id
                  AND v.custom_variant_id = ?
                """;
        String refreshVariantParentSql = """
                UPDATE custom_order_items i
                SET quantity_on_hand = COALESCE((SELECT SUM(quantity_on_hand) FROM custom_order_item_variants WHERE custom_item_id = i.custom_item_id AND is_active = TRUE), 0),
                    sold_quantity = COALESCE((SELECT SUM(sold_quantity) FROM custom_order_item_variants WHERE custom_item_id = i.custom_item_id), 0),
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_item_id = ?
                  AND has_variants = TRUE
                """;
        String paymentSql = """
                INSERT INTO custom_order_payments (
                    custom_order_id, payment_amount, payment_method, payment_reference,
                    taken_by_user_id, taken_by_name, payment_action, device_id, device_name,
                    cash_drawer_id, cash_drawer_name, cash_drawer_session_id
                )
                VALUES (?, ?, ?, ?, ?, ?, 'PAYMENT', ?, ?, ?, ?, ?)
                """;
        String accountTransactionSql = """
                INSERT INTO customer_account_transactions (
                    customer_id, custom_order_id, location_id, amount, transaction_type, note, user_name, device_id, device_name,
                    payment_method, payment_reference, cash_drawer_id, cash_drawer_name, cash_drawer_session_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement orderPs = conn.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement linePs = conn.prepareStatement(lineSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement addonPs = conn.prepareStatement(addonSql);
                 PreparedStatement itemSoldPs = conn.prepareStatement(updateCustomItemSoldSql);
                 PreparedStatement variantSoldPs = conn.prepareStatement(updateCustomVariantSoldSql);
                 PreparedStatement refreshParentPs = conn.prepareStatement(refreshVariantParentSql);
                 PreparedStatement paymentPs = conn.prepareStatement(paymentSql);
                 PreparedStatement accountTransactionPs = conn.prepareStatement(accountTransactionSql)) {
                DeviceContextService.requireOrdersAllowed(conn);
                int customerId = resolveOrderCustomerId(conn, request.selectedCustomer(), request.customerName(), request.customerPhone());
                boolean hasAccountBalanceDue = request.balanceDue().compareTo(BigDecimal.ZERO) > 0;
                if (hasAccountBalanceDue) {
                    validateAndChargeCustomerAccount(conn, customerId, request.balanceDue());
                }
                CashDrawerContext cashDrawer = new CashDrawerContext(null, null);
                boolean cashUpfrontPayment = request.amountPaid().compareTo(BigDecimal.ZERO) > 0
                        && "CASH".equalsIgnoreCase(blankToNull(request.paymentMethod()));
                if (cashUpfrontPayment) {
                    cashDrawer = CashDrawerService.requireActiveCashSession(conn);
                }
                orderPs.setString(1, orderNumber);
                orderPs.setInt(2, customerId);
                orderPs.setString(3, request.customerName());
                orderPs.setString(4, request.customerPhone());
                if (request.dueDate() == null) {
                    orderPs.setNull(5, Types.DATE);
                } else {
                    orderPs.setDate(5, Date.valueOf(request.dueDate()));
                }
                orderPs.setString(6, blankToNull(request.orderNotes()));
                orderPs.setBigDecimal(7, request.total());
                orderPs.setBigDecimal(8, request.amountPaid());
                orderPs.setBigDecimal(9, request.balanceDue());
                orderPs.setString(10, request.paymentMethod());
                orderPs.setString(11, blankToNull(request.paymentReference()));
                orderPs.setString(12, request.paymentStatus());
                setNullableInteger(orderPs, 13, request.takenByUserId());
                orderPs.setString(14, request.takenByName());
                setNullableInteger(orderPs, 15, request.locationId());
                orderPs.setString(16, blankToNull(request.locationName()));
                orderPs.setString(17, blankToNull(request.deviceId()));
                orderPs.setString(18, blankToNull(request.deviceName()));
                setNullableLong(orderPs, 19, cashDrawer.cashDrawerId());
                orderPs.setString(20, blankToNull(cashDrawer.drawerName()));
                setNullableLong(orderPs, 21, cashDrawer.sessionId());
                orderPs.setBigDecimal(22, defaultZero(request.minimumDepositRequired()));
                orderPs.setString(23, blankToNull(request.depositOverrideReason()));
                setNullableInteger(orderPs, 24, request.depositOverrideByUserId());
                orderPs.setString(25, blankToNull(request.depositOverrideByName()));
                orderPs.executeUpdate();

                long orderId;
                try (ResultSet rs = orderPs.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new SQLException("Failed to get custom order ID.");
                    }
                    orderId = rs.getLong(1);
                }

                if (request.amountPaid().compareTo(BigDecimal.ZERO) > 0) {
                    paymentPs.setLong(1, orderId);
                    paymentPs.setBigDecimal(2, request.amountPaid());
                    paymentPs.setString(3, request.paymentMethod());
                    paymentPs.setString(4, blankToNull(request.paymentReference()));
                    setNullableInteger(paymentPs, 5, request.takenByUserId());
                    paymentPs.setString(6, request.takenByName());
                    paymentPs.setString(7, blankToNull(request.deviceId()));
                    paymentPs.setString(8, blankToNull(request.deviceName()));
                    setNullableLong(paymentPs, 9, cashDrawer.cashDrawerId());
                    paymentPs.setString(10, blankToNull(cashDrawer.drawerName()));
                    setNullableLong(paymentPs, 11, cashDrawer.sessionId());
                    paymentPs.executeUpdate();
                }
                BigDecimal accountHistoryAmount = hasAccountBalanceDue ? request.balanceDue() : BigDecimal.ZERO;
                String accountHistoryType = hasAccountBalanceDue ? "CUSTOM_ORDER_CREDIT" : "CUSTOM_ORDER_PAID";
                String accountHistoryNote = hasAccountBalanceDue
                        ? "Custom order balance charged to account. payment_method=" + blankToNull(request.paymentMethod())
                        + ", amount_paid=" + request.amountPaid()
                        + ", balance_due=" + request.balanceDue()
                        + ", custom_order_id=" + orderId
                        + ", order_number=" + orderNumber
                        : "Custom order recorded. payment_method=" + blankToNull(request.paymentMethod())
                        + ", payment_status=" + request.paymentStatus()
                        + ", custom_order_id=" + orderId
                        + ", order_number=" + orderNumber;
                accountTransactionPs.setInt(1, customerId);
                accountTransactionPs.setLong(2, orderId);
                setNullableInteger(accountTransactionPs, 3, request.locationId());
                accountTransactionPs.setBigDecimal(4, accountHistoryAmount);
                accountTransactionPs.setString(5, accountHistoryType);
                accountTransactionPs.setString(6, accountHistoryNote);
                accountTransactionPs.setString(7, request.takenByName());
                accountTransactionPs.setString(8, blankToNull(request.deviceId()));
                accountTransactionPs.setString(9, blankToNull(request.deviceName()));
                accountTransactionPs.setString(10, blankToNull(request.paymentMethod()));
                accountTransactionPs.setString(11, blankToNull(request.paymentReference()));
                setNullableLong(accountTransactionPs, 12, cashDrawer.cashDrawerId());
                accountTransactionPs.setString(13, blankToNull(cashDrawer.drawerName()));
                setNullableLong(accountTransactionPs, 14, cashDrawer.sessionId());
                accountTransactionPs.executeUpdate();
                CustomOrderAuditService.recordAudit(conn, orderId, "CREATE", "order", null, orderNumber, null);
                CustomOrderAuditService.recordStatus(conn, orderId, null, "NEW", "Order created");

                int sortOrder = 1;
                for (OrderLineRequest line : request.lines()) {
                    setNullableLong(linePs, 2, line.customItemId());
                    linePs.setLong(1, orderId);
                    linePs.setString(3, line.itemName());
                    linePs.setString(4, line.pricingType());
                    linePs.setBigDecimal(5, line.unitPrice());
                    linePs.setBigDecimal(6, line.unitPrice());
                    linePs.setString(7, line.customizationDetails());
                    linePs.setString(8, blankToNull(line.orderInstructions()));
                    linePs.setInt(9, sortOrder++);
                    setNullableLong(linePs, 10, line.customVariantId());
                    linePs.setString(11, blankToNull(line.variantName()));
                    setNullableBigDecimal(linePs, 12, line.widthValue());
                    setNullableBigDecimal(linePs, 13, line.lengthValue());
                    linePs.setString(14, blankToNull(line.dimensionUnit()));
                    setNullableBigDecimal(linePs, 15, line.areaValue());
                    linePs.setString(16, blankToNull(line.areaUnit()));
                    setNullableBigDecimal(linePs, 17, line.areaPrice());
                    setNullableBigDecimal(linePs, 18, line.baseItemPrice());
                    setNullableLong(linePs, 19, line.printMaterialId());
                    linePs.setString(20, blankToNull(line.printMaterialName()));
                    setNullableLong(linePs, 21, line.printSizePresetId());
                    linePs.setString(22, blankToNull(line.printSizeName()));
                    setNullableBigDecimal(linePs, 23, line.printCharge());
                    linePs.setInt(24, line.printLineCount());
                    setNullableBigDecimal(linePs, 25, line.originalLineTotal());
                    setNullableBigDecimal(linePs, 26, line.lineDiscountPercent());
                    setNullableBigDecimal(linePs, 27, line.lineDiscountAmount());
                    setNullableInteger(linePs, 28, line.lineDiscountByUserId());
                    linePs.setString(29, blankToNull(line.lineDiscountByName()));
                    linePs.setString(30, blankToNull(line.lineDiscountReason()));
                    setNullableBigDecimal(linePs, 31, line.minimumDepositPercent());
                    setNullableBigDecimal(linePs, 32, line.originalBasePrice());
                    setNullableBigDecimal(linePs, 33, line.priceOverridePrice());
                    linePs.setString(34, blankToNull(line.priceOverrideReason()));
                    setNullableInteger(linePs, 35, line.priceOverrideByUserId());
                    linePs.setString(36, blankToNull(line.priceOverrideByName()));
                    linePs.executeUpdate();

                    long lineId;
                    try (ResultSet lineKeys = linePs.getGeneratedKeys()) {
                        if (!lineKeys.next()) {
                            throw new SQLException("Failed to get custom order line ID.");
                        }
                        lineId = lineKeys.getLong(1);
                    }
                    insertReservationIfInventory(conn, orderId, lineId, line);

                    int addonSortOrder = 1;
                    for (PrintAddonRequest addon : line.printAddons()) {
                        addonPs.setLong(1, lineId);
                        setNullableLong(addonPs, 2, addon.printMaterialId());
                        addonPs.setString(3, addon.materialName());
                        setNullableLong(addonPs, 4, addon.printSizePresetId());
                        addonPs.setString(5, addon.printSizeName());
                        addonPs.setString(6, addon.pricingMode());
                        addonPs.setString(7, blankToNull(addon.printDescription()));
                        addonPs.setBigDecimal(8, addon.printCharge());
                        addonPs.setInt(9, addon.printLineCount());
                        addonPs.setInt(10, addonSortOrder++);
                        addonPs.addBatch();
                    }

                    if (line.customItemId() != null) {
                        if (line.customVariantId() == null) {
                            itemSoldPs.setLong(1, line.customItemId());
                            itemSoldPs.addBatch();
                        } else {
                            variantSoldPs.setLong(1, line.customVariantId());
                            variantSoldPs.addBatch();
                            refreshParentPs.setLong(1, line.customItemId());
                            refreshParentPs.addBatch();
                        }
                    }
                }
                addonPs.executeBatch();
                itemSoldPs.executeBatch();
                variantSoldPs.executeBatch();
                refreshParentPs.executeBatch();
                SyncOutboxService.recordEvent(conn, "CUSTOM_ORDER_CREATED", Map.of(
                        "custom_order_id", orderId,
                        "order_number", orderNumber,
                        "customer_id", customerId,
                        "location_id", request.locationId() == null ? "" : request.locationId(),
                        "device_id", String.valueOf(request.deviceId()),
                        "total_amount", request.total(),
                        "amount_paid", request.amountPaid(),
                        "balance_due", request.balanceDue(),
                        "payment_status", request.paymentStatus()
                ));
                if (request.amountPaid().compareTo(BigDecimal.ZERO) > 0) {
                    SyncOutboxService.recordEvent(conn, "CUSTOM_ORDER_PAYMENT_CREATED", Map.of(
                            "custom_order_id", orderId,
                            "order_number", orderNumber,
                            "customer_id", customerId,
                            "payment_amount", request.amountPaid(),
                            "payment_method", String.valueOf(request.paymentMethod()),
                            "location_id", request.locationId() == null ? "" : request.locationId(),
                            "device_id", String.valueOf(request.deviceId())
                    ));
                }
            return orderNumber;
        }
    }

    private static String generateOrderNumber() {
        return "CO-" + System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    private static void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private static void setNullableBigDecimal(PreparedStatement ps, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.NUMERIC);
        } else {
            ps.setBigDecimal(index, value);
        }
    }

    private static void insertReservationIfInventory(Connection conn, long orderId, long lineId, OrderLineRequest line) throws SQLException {
        if (line.customItemId() == null) {
            return;
        }
        String sql = """
                INSERT INTO custom_order_inventory_reservations (
                    custom_order_id, custom_order_line_id, custom_item_id, custom_variant_id,
                    item_name, variant_name, reserved_qty, status
                )
                SELECT ?, ?, coi.custom_item_id, ?, ?, ?, 1, 'RESERVED'
                FROM custom_order_items coi
                WHERE coi.custom_item_id = ?
                  AND COALESCE(coi.product_type, 'INVENTORY') = 'INVENTORY'
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            ps.setLong(2, lineId);
            setNullableLong(ps, 3, line.customVariantId());
            ps.setString(4, line.itemName());
            ps.setString(5, blankToNull(line.variantName()));
            ps.setLong(6, line.customItemId());
            ps.executeUpdate();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static int createGeneralCustomerAccount(Connection conn, String name, String phone) throws SQLException {
        String sql = """
                INSERT INTO customer_accounts (
                    name, customer_type_id, phone, credit_limit,
                    current_balance, is_business, is_active
                )
                VALUES (?, ?, ?, 0, 0, FALSE, TRUE)
                RETURNING customer_id
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, 1);
            ps.setString(3, phone);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Failed to create customer account.");
                }
                return rs.getInt("customer_id");
            }
        }
    }

    private static void validateAndChargeCustomerAccount(Connection conn, int customerId, BigDecimal chargeAmount) throws SQLException {
        Integer locationId=ServerRequestIdentity.locationId();
        if(locationId==null)throw new SQLException("A store location is required to verify multi-store customer credit.");
        CustomerAccountLedgerService.requireCurrentMultiStoreBalance(conn,locationId);
        CustomerAccountLedgerService.repairCustomerBalance(conn, customerId);
        String lockSql = """
                SELECT current_balance, credit_limit, is_active
                FROM customer_accounts
                WHERE customer_id = ?
                FOR UPDATE
                """;
        BigDecimal currentBalance;
        BigDecimal creditLimit;
        boolean active;
        try (PreparedStatement ps = conn.prepareStatement(lockSql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Customer account was not found.");
                }
                currentBalance = defaultZero(rs.getBigDecimal("current_balance"));
                creditLimit = defaultZero(rs.getBigDecimal("credit_limit"));
                active = rs.getBoolean("is_active");
            }
        }

        if (!active) {
            throw new SQLException("Customer account is inactive.");
        }

        BigDecimal newBalance = currentBalance.add(chargeAmount);
        if (newBalance.compareTo(creditLimit) > 0) {
            throw new SQLException("Account payment exceeds customer credit limit. Available credit: $" + creditLimit.subtract(currentBalance));
        }

        try (PreparedStatement ps = conn.prepareStatement("UPDATE customer_accounts SET current_balance = ? WHERE customer_id = ?")) {
            ps.setBigDecimal(1, newBalance);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static LookupResult lookupCustomItem(String search) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            return lookupCustomItem(conn, search);
        }
    }

    public static LookupResult lookupCustomItem(Connection conn, String search) throws SQLException {
        String value = search == null ? "" : search.trim();
        if (value.isEmpty()) {
            return null;
        }
        String sql = """
                WITH matches AS (
                    SELECT coi.custom_item_id,
                           NULL::BIGINT AS custom_variant_id,
                           20 AS rank
                    FROM custom_order_items coi
                    WHERE coi.is_active = TRUE
                      AND UPPER(COALESCE(coi.sku, '')) = UPPER(?)
                    UNION ALL
                    SELECT coiv.custom_item_id,
                           coiv.custom_variant_id,
                           10 AS rank
                    FROM custom_order_item_variants coiv
                    JOIN custom_order_items coi ON coi.custom_item_id = coiv.custom_item_id
                    WHERE coi.is_active = TRUE
                      AND coiv.is_active = TRUE
                      AND UPPER(COALESCE(coiv.sku, '')) = UPPER(?)
                    UNION ALL
                    SELECT coi.custom_item_id,
                           NULL::BIGINT AS custom_variant_id,
                           30 AS rank
                    FROM custom_order_items coi
                    WHERE coi.is_active = TRUE
                      AND UPPER(COALESCE(coi.barcode, '')) = UPPER(?)
                    UNION ALL
                    SELECT coib.custom_item_id,
                           NULL::BIGINT AS custom_variant_id,
                           40 AS rank
                    FROM custom_order_item_barcodes coib
                    JOIN custom_order_items coi ON coi.custom_item_id = coib.custom_item_id
                    WHERE coi.is_active = TRUE
                      AND UPPER(coib.barcode) = UPPER(?)
                    UNION ALL
                    SELECT coiv.custom_item_id,
                           coiv.custom_variant_id,
                           15 AS rank
                    FROM custom_order_item_variants coiv
                    JOIN custom_order_items coi ON coi.custom_item_id = coiv.custom_item_id
                    WHERE coi.is_active = TRUE
                      AND coiv.is_active = TRUE
                      AND UPPER(COALESCE(coiv.barcode, '')) = UPPER(?)
                    UNION ALL
                    SELECT coi.custom_item_id,
                           NULL::BIGINT AS custom_variant_id,
                           60 AS rank
                    FROM custom_order_items coi
                    WHERE coi.is_active = TRUE
                      AND %s
                    UNION ALL
                    SELECT coiv.custom_item_id,
                           coiv.custom_variant_id,
                           50 AS rank
                    FROM custom_order_item_variants coiv
                    JOIN custom_order_items coi ON coi.custom_item_id = coiv.custom_item_id
                    WHERE coi.is_active = TRUE
                      AND coiv.is_active = TRUE
                      AND %s
                )
                SELECT custom_item_id, custom_variant_id
                FROM matches
                ORDER BY rank
                LIMIT 1
                """.formatted(
                        ProductSearchHelper.customItemPredicate("coi", value),
                        ProductSearchHelper.customVariantPredicate("coi", "coiv", value));
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 1; i <= 5; i++) {
                ps.setString(i, value);
            }
            int parameterIndex = ProductSearchHelper.bindTokens(ps, 6, value);
            ProductSearchHelper.bindTokens(ps, parameterIndex, value);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long itemId = rs.getLong("custom_item_id");
                    long variantId = rs.getLong("custom_variant_id");
                    return new LookupResult(itemId, rs.wasNull() ? null : variantId);
                }
            }
        }
        return null;
    }

    public record CustomItemOption(Long customItemId, String name, String sku, String productType, String pricingType,
                                   BigDecimal fixedPrice, boolean hasVariants, BigDecimal areaPrice,
                                   String areaPriceUnit, String dimensionUnit, BigDecimal maxWidth,
                                   BigDecimal maxLength) {
        @Override
        public String toString() {
            String label = sku == null || sku.isBlank() ? name : name + " [" + sku + "]";
            if (hasVariants) {
                return label + " (variants)";
            }
            if ("FIXED".equals(pricingType) && fixedPrice != null) {
                return label + " ($" + fixedPrice + ")";
            }
            if ("AREA".equals(pricingType)) {
                return label + " (area)";
            }
            return label + " (variable)";
        }
    }

    public record VariantOption(Long variantId, String name, String sku, BigDecimal fixedPrice) {
        @Override
        public String toString() {
            String label = sku == null || sku.isBlank() ? name : name + " [" + sku + "]";
            return fixedPrice == null ? label : label + " ($" + fixedPrice + ")";
        }
    }

    public record LookupResult(Long customItemId, Long customVariantId) {
    }

    public record PrintMaterialOption(Long printMaterialId, String materialName) {
        @Override
        public String toString() {
            return materialName;
        }
    }

    public record PrintSizePresetOption(Long printSizePresetId, Long printMaterialId, String presetName,
                                        String pricingMode, BigDecimal fixedPrice) {
        @Override
        public String toString() {
            String suffix = "PER_LINE".equals(pricingMode) ? " / line" : "";
            return fixedPrice == null ? presetName : presetName + " ($" + fixedPrice + suffix + ")";
        }
    }

    public record CustomerOption(Integer customerId, String name, String phone) {
        @Override
        public String toString() {
            if (customerId == null) {
                return name;
            }
            return name + (phone == null || phone.isBlank() ? " (no phone)" : " - " + phone);
        }
    }

    public record EmployeeOption(Integer userId, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    public record OrderSaveRequest(
            CustomerOption selectedCustomer,
            String customerName,
            String customerPhone,
            LocalDate dueDate,
            BigDecimal total,
            BigDecimal amountPaid,
            BigDecimal balanceDue,
            String paymentMethod,
            String paymentReference,
            String paymentStatus,
            Integer takenByUserId,
            String takenByName,
            Integer locationId,
            String locationName,
            String deviceId,
            String deviceName,
            BigDecimal minimumDepositRequired,
            String depositOverrideReason,
            Integer depositOverrideByUserId,
            String depositOverrideByName,
            String orderNotes,
            List<OrderLineRequest> lines,
            String depositApprovalToken
    ) {
    }

    public record OrderLineRequest(
            Long customItemId,
            Long customVariantId,
            String itemName,
            String variantName,
            String pricingType,
            BigDecimal unitPrice,
            String customizationDetails,
            String orderInstructions,
            BigDecimal widthValue,
            BigDecimal lengthValue,
            String dimensionUnit,
            BigDecimal areaValue,
            String areaUnit,
            BigDecimal areaPrice,
            BigDecimal baseItemPrice,
            Long printMaterialId,
            String printMaterialName,
            Long printSizePresetId,
            String printSizeName,
            BigDecimal printCharge,
            int printLineCount,
            BigDecimal originalLineTotal,
            BigDecimal lineDiscountPercent,
            BigDecimal lineDiscountAmount,
            Integer lineDiscountByUserId,
            String lineDiscountByName,
            String lineDiscountReason,
            BigDecimal minimumDepositPercent,
            BigDecimal originalBasePrice,
            BigDecimal priceOverridePrice,
            String priceOverrideReason,
            Integer priceOverrideByUserId,
            String priceOverrideByName,
            List<PrintAddonRequest> printAddons,
            String lineDiscountApprovalToken,
            String priceOverrideApprovalToken
    ) {
    }

    public record PrintAddonRequest(
            Long printMaterialId,
            String materialName,
            Long printSizePresetId,
            String printSizeName,
            String pricingMode,
            String printDescription,
            int printLineCount,
            BigDecimal printCharge
    ) {
    }
}
