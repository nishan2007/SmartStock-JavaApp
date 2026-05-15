package services;

import data.DB;

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

public final class CustomOrderDataService {
    private CustomOrderDataService() {
    }

    public static List<CustomItemOption> listActiveItems() throws SQLException {
        List<CustomItemOption> options = new ArrayList<>();
        String sql = """
                SELECT custom_item_id, item_name, product_type, pricing_type, fixed_price,
                       area_price, area_price_unit, dimension_unit, max_width, max_length,
                       COALESCE(has_variants, FALSE) AS has_variants,
                       is_active
                FROM custom_order_items
                ORDER BY is_active DESC, item_name
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
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
        List<VariantOption> variants = new ArrayList<>();
        String sql = """
                SELECT custom_variant_id, variant_name, fixed_price
                FROM custom_order_item_variants
                WHERE custom_item_id = ?
                  AND is_active = TRUE
                ORDER BY variant_name
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, customItemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    variants.add(new VariantOption(
                            rs.getLong("custom_variant_id"),
                            rs.getString("variant_name"),
                            rs.getBigDecimal("fixed_price")
                    ));
                }
            }
        }
        return variants;
    }

    public static List<PrintMaterialOption> listActivePrintMaterials() throws SQLException {
        List<PrintMaterialOption> materials = new ArrayList<>();
        String sql = """
                SELECT print_material_id, material_name
                FROM custom_order_print_materials
                WHERE is_active = TRUE
                ORDER BY material_name
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                materials.add(new PrintMaterialOption(rs.getLong("print_material_id"), rs.getString("material_name")));
            }
        }
        return materials;
    }

    public static List<PrintSizePresetOption> listActivePrintSizePresets(long printMaterialId) throws SQLException {
        List<PrintSizePresetOption> presets = new ArrayList<>();
        String sql = """
                SELECT print_size_preset_id, print_material_id, preset_name,
                       COALESCE(pricing_mode, 'FIXED_PRESET') AS pricing_mode, fixed_price
                FROM custom_order_print_size_presets
                WHERE is_active = TRUE
                  AND print_material_id = ?
                ORDER BY preset_name
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
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
        List<String> placements = new ArrayList<>();
        String sql = """
                SELECT placement_name
                FROM custom_order_design_placements
                WHERE is_active = TRUE
                ORDER BY sort_order, placement_name
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                placements.add(rs.getString("placement_name"));
            }
        }
        return placements;
    }

    public static List<CustomerOption> searchCustomers(String search) throws SQLException {
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
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
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
        List<EmployeeOption> employees = new ArrayList<>();
        String sql = """
                SELECT u.user_id, COALESCE(NULLIF(u.full_name, ''), u.username) AS employee_name
                FROM users u
                WHERE COALESCE(u.is_active, TRUE) = TRUE
                ORDER BY employee_name
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                employees.add(new EmployeeOption(rs.getInt("user_id"), rs.getString("employee_name")));
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
        String orderNumber = generateOrderNumber();
        String orderSql = """
                INSERT INTO custom_orders (
                    order_number, customer_id, customer_name, customer_phone, status, due_date,
                    order_notes, total_amount, amount_paid, balance_due, payment_method,
                    payment_reference, payment_status, taken_by_user_id, taken_by_name
                )
                VALUES (?, ?, ?, ?, 'NEW', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String lineSql = """
                INSERT INTO custom_order_lines (
                    custom_order_id, custom_item_id, item_name, pricing_type, unit_price,
                    line_total, customization_details, order_instructions, sort_order,
                    custom_variant_id, variant_name,
                    width_value, length_value, dimension_unit, area_value, area_unit, area_price,
                    base_item_price, print_material_id, print_material_name,
                    print_size_preset_id, print_size_name, print_charge, print_line_count
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                    taken_by_user_id, taken_by_name
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        String accountTransactionSql = """
                INSERT INTO customer_account_transactions (
                    customer_id, custom_order_id, amount, transaction_type, note, user_name
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement orderPs = conn.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement linePs = conn.prepareStatement(lineSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement addonPs = conn.prepareStatement(addonSql);
                 PreparedStatement itemSoldPs = conn.prepareStatement(updateCustomItemSoldSql);
                 PreparedStatement variantSoldPs = conn.prepareStatement(updateCustomVariantSoldSql);
                 PreparedStatement refreshParentPs = conn.prepareStatement(refreshVariantParentSql);
                 PreparedStatement paymentPs = conn.prepareStatement(paymentSql);
                 PreparedStatement accountTransactionPs = conn.prepareStatement(accountTransactionSql)) {
                int customerId = resolveOrderCustomerId(conn, request.selectedCustomer(), request.customerName(), request.customerPhone());
                boolean hasAccountBalanceDue = request.balanceDue().compareTo(BigDecimal.ZERO) > 0;
                if (hasAccountBalanceDue) {
                    validateAndChargeCustomerAccount(conn, customerId, request.balanceDue());
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
                orderPs.setNull(6, Types.VARCHAR);
                orderPs.setBigDecimal(7, request.total());
                orderPs.setBigDecimal(8, request.amountPaid());
                orderPs.setBigDecimal(9, request.balanceDue());
                orderPs.setString(10, request.paymentMethod());
                orderPs.setString(11, blankToNull(request.paymentReference()));
                orderPs.setString(12, request.paymentStatus());
                setNullableInteger(orderPs, 13, request.takenByUserId());
                orderPs.setString(14, request.takenByName());
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
                accountTransactionPs.setBigDecimal(3, accountHistoryAmount);
                accountTransactionPs.setString(4, accountHistoryType);
                accountTransactionPs.setString(5, accountHistoryNote);
                accountTransactionPs.setString(6, request.takenByName());
                accountTransactionPs.executeUpdate();

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
                    linePs.executeUpdate();

                    long lineId;
                    try (ResultSet lineKeys = linePs.getGeneratedKeys()) {
                        if (!lineKeys.next()) {
                            throw new SQLException("Failed to get custom order line ID.");
                        }
                        lineId = lineKeys.getLong(1);
                    }

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
                conn.commit();
                return orderNumber;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private static String generateOrderNumber() {
        return "CO-" + System.currentTimeMillis();
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static int createGeneralCustomerAccount(Connection conn, String name, String phone) throws SQLException {
        Integer generalTypeId = findCustomerTypeId(conn, "General");
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
            if (generalTypeId == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, generalTypeId);
            }
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

    private static Integer findCustomerTypeId(Connection conn, String name) throws SQLException {
        String sql = """
                SELECT customer_type_id
                FROM customer_types
                WHERE name = ?
                  AND is_active = TRUE
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("customer_type_id") : null;
            }
        }
    }

    public record CustomItemOption(Long customItemId, String name, String productType, String pricingType,
                                   BigDecimal fixedPrice, boolean hasVariants, BigDecimal areaPrice,
                                   String areaPriceUnit, String dimensionUnit, BigDecimal maxWidth,
                                   BigDecimal maxLength) {
        @Override
        public String toString() {
            if (hasVariants) {
                return name + " (variants)";
            }
            if ("FIXED".equals(pricingType) && fixedPrice != null) {
                return name + " ($" + fixedPrice + ")";
            }
            if ("AREA".equals(pricingType)) {
                return name + " (area)";
            }
            return name + " (variable)";
        }
    }

    public record VariantOption(Long variantId, String name, BigDecimal fixedPrice) {
        @Override
        public String toString() {
            return fixedPrice == null ? name : name + " ($" + fixedPrice + ")";
        }
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
            List<OrderLineRequest> lines
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
            List<PrintAddonRequest> printAddons
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
