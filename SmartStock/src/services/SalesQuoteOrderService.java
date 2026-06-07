package services;

import data.DB;
import managers.CompanyCustomizationManager;
import managers.SessionManager;
import models.CashDrawerContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SalesQuoteOrderService {
    private SalesQuoteOrderService() {
    }

    public static QuoteResult createQuote(int customerId, LocalDate validUntil, String notes, List<QuoteLineInput> lines) throws SQLException {
        if (lines == null || lines.isEmpty()) {
            throw new SQLException("Add at least one quote line.");
        }
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                SalesQuoteOrderSchemaInstaller.ensureSchema(conn);
                int locationId = requireLocationId();
                CustomerInfo customer = loadCustomer(conn, customerId);
                LocalDate safeValidUntil = validUntil == null
                        ? LocalDate.now().plusDays(SalesQuoteOrderNumberManager.defaultQuoteValidityDays(conn, locationId))
                        : validUntil;
                String quoteNumber = SalesQuoteOrderNumberManager.nextQuoteNumber(conn, locationId);
                Totals totals = totals(conn, lines);
                long quoteId = insertQuote(conn, quoteNumber, customer, safeValidUntil, notes, totals, locationId);
                int sortOrder = 0;
                for (QuoteLineInput line : lines) {
                    insertQuoteLine(conn, quoteId, line, sortOrder++);
                }
                SalesQuoteOrderAuditService.recordQuoteStatus(conn, quoteId, null, "DRAFT", "Quote created.");
                SalesQuoteOrderAuditService.recordQuoteAudit(conn, quoteId, "QUOTE_CREATED", null, null, quoteNumber, notes);
                SyncOutboxService.recordEvent(conn, "SALES_QUOTE_CREATED", Map.of("sales_quote_id", quoteId, "quote_number", quoteNumber));
                conn.commit();
                return new QuoteResult(quoteId, quoteNumber);
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static void issueQuote(long quoteId) throws SQLException {
        updateQuoteStatus(quoteId, "ISSUED", "Quote issued to customer.");
    }

    public static void cancelQuote(long quoteId, String reason) throws SQLException {
        updateQuoteStatus(quoteId, "CANCELLED", blankToNull(reason) == null ? "Quote cancelled." : reason);
    }

    public static OrderResult acceptQuote(long quoteId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                SalesQuoteOrderSchemaInstaller.ensureSchema(conn);
                QuoteHeader quote = lockQuote(conn, quoteId);
                if (!"DRAFT".equals(quote.status()) && !"ISSUED".equals(quote.status())) {
                    throw new SQLException("Only draft or issued quotes can be accepted.");
                }
                if (quote.validUntil() != null && quote.validUntil().isBefore(LocalDate.now())) {
                    throw new SQLException("This quote expired on " + quote.validUntil() + ".");
                }
                int locationId = quote.locationId() == null ? requireLocationId() : quote.locationId();
                String orderNumber = SalesQuoteOrderNumberManager.nextOrderNumber(conn, locationId);
                long orderId = insertOrderFromQuote(conn, quote, orderNumber);
                copyQuoteLinesToOrder(conn, quoteId, orderId);
                updateQuoteAccepted(conn, quoteId);
                SalesQuoteOrderAuditService.recordQuoteStatus(conn, quoteId, quote.status(), "ACCEPTED", "Quote accepted and converted to sales order " + orderNumber + ".");
                SalesQuoteOrderAuditService.recordOrderStatus(conn, orderId, null, "OPEN", "Sales order created from quote " + quote.quoteNumber() + ".");
                SalesQuoteOrderAuditService.recordOrderAudit(conn, orderId, "ORDER_CREATED_FROM_QUOTE", "quote_number", null, quote.quoteNumber(), null);
                SyncOutboxService.recordEvent(conn, "SALES_QUOTE_ACCEPTED", Map.of("sales_quote_id", quoteId, "sales_order_id", orderId, "order_number", orderNumber));
                conn.commit();
                return new OrderResult(orderId, orderNumber);
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static void recordPayment(long orderId, BigDecimal amount, String method, String reference) throws SQLException {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new SQLException("Payment amount must be greater than zero.");
        }
        String safeMethod = normalizePaymentMethod(method);
        if ("ACCOUNT".equals(safeMethod)) {
            chargeOrderToAccount(orderId, "Placed on customer account.");
            return;
        }
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                SalesQuoteOrderSchemaInstaller.ensureSchema(conn);
                OrderHeader order = lockOrder(conn, orderId);
                if ("CANCELLED".equals(order.status())) {
                    throw new SQLException("Cancelled orders cannot be paid.");
                }
                BigDecimal applied = amount.min(order.balanceDue());
                CashDrawerContext drawer = cashDrawerForPayment(conn, safeMethod);
                insertPayment(conn, order, applied, safeMethod, reference, drawer);
                updateOrderPaymentTotals(conn, orderId, applied, safeMethod, reference);
                if (hasAccountCharge(conn, orderId)) {
                    reduceCustomerBalance(conn, order.customerId(), applied);
                    insertCustomerPaymentTransaction(conn, order, applied.negate(), safeMethod, reference, drawer);
                }
                SalesQuoteOrderAuditService.recordOrderAudit(conn, orderId, "PAYMENT_CREATED", "amount_paid", order.amountPaid(), order.amountPaid().add(applied), safeMethod);
                SyncOutboxService.recordEvent(conn, "SALES_ORDER_PAYMENT_CREATED", Map.of("sales_order_id", orderId, "payment_amount", applied));
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static void chargeOrderToAccount(long orderId, String reason) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                SalesQuoteOrderSchemaInstaller.ensureSchema(conn);
                OrderHeader order = lockOrder(conn, orderId);
                if (hasAccountCharge(conn, orderId)) {
                    throw new SQLException("This order is already on the customer account.");
                }
                if (order.balanceDue().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new SQLException("This order has no remaining balance to place on account.");
                }
                updateCustomerBalance(conn, order.customerId(), order.balanceDue());
                long transactionId = insertAccountCharge(conn, order, reason);
                insertAccountAllocation(conn, transactionId, order, order.balanceDue());
                markOrderPaymentMethod(conn, orderId, "ACCOUNT", null);
                SalesQuoteOrderAuditService.recordOrderAudit(conn, orderId, "ACCOUNT_CHARGE_CREATED", "balance_due", null, order.balanceDue(), reason);
                SyncOutboxService.recordEvent(conn, "SALES_ORDER_ACCOUNT_CHARGE_CREATED", Map.of("sales_order_id", orderId, "amount", order.balanceDue()));
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static DeliveryResult postDelivery(long orderId, String deliveryMethod, String receiverName,
                                              String notes, List<DeliveryLineInput> lines) throws SQLException {
        if (lines == null || lines.isEmpty()) {
            throw new SQLException("Select at least one line quantity to deliver.");
        }
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                SalesQuoteOrderSchemaInstaller.ensureSchema(conn);
                OrderHeader order = lockOrder(conn, orderId);
                if ("CANCELLED".equals(order.status()) || "DELIVERED".equals(order.status())) {
                    throw new SQLException("This order cannot receive another delivery.");
                }
                int locationId = order.locationId() == null ? requireLocationId() : order.locationId();
                String deliveryNumber = SalesQuoteOrderNumberManager.nextDeliveryNumber(conn, locationId);
                long eventId = insertDeliveryEvent(conn, order, deliveryNumber, normalizeDeliveryMethod(deliveryMethod), receiverName, notes);
                for (DeliveryLineInput line : lines) {
                    postDeliveryLine(conn, order, eventId, line);
                }
                String oldStatus = order.status();
                String newStatus = recomputeDeliveryStatus(conn, orderId);
                updateOrderStatus(conn, orderId, newStatus);
                SalesQuoteOrderAuditService.recordOrderStatus(conn, orderId, oldStatus, newStatus, "Delivery posted: " + deliveryNumber + ".");
                SalesQuoteOrderAuditService.recordOrderAudit(conn, orderId, "DELIVERY_POSTED", "delivery_number", null, deliveryNumber, notes);
                SyncOutboxService.recordEvent(conn, "SALES_ORDER_DELIVERY_POSTED", Map.of("sales_order_id", orderId, "delivery_number", deliveryNumber));
                conn.commit();
                return new DeliveryResult(eventId, deliveryNumber);
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static int availableStock(Connection conn, Integer productId, Integer locationId) throws SQLException {
        if (productId == null || locationId == null) {
            return 0;
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COALESCE(quantity_on_hand, 0) AS qty
                FROM inventory
                WHERE product_id = ? AND location_id = ?
                """)) {
            ps.setInt(1, productId);
            ps.setInt(2, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("qty") : 0;
            }
        }
    }

    private static void updateQuoteStatus(long quoteId, String newStatus, String reason) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                SalesQuoteOrderSchemaInstaller.ensureSchema(conn);
                QuoteHeader quote = lockQuote(conn, quoteId);
                try (PreparedStatement ps = conn.prepareStatement("UPDATE sales_quotes SET status = ? WHERE sales_quote_id = ?")) {
                    ps.setString(1, newStatus);
                    ps.setLong(2, quoteId);
                    ps.executeUpdate();
                }
                SalesQuoteOrderAuditService.recordQuoteStatus(conn, quoteId, quote.status(), newStatus, reason);
                SyncOutboxService.recordEvent(conn, "SALES_QUOTE_STATUS_CHANGED", Map.of("sales_quote_id", quoteId, "status", newStatus));
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private static long insertQuote(Connection conn, String quoteNumber, CustomerInfo customer, LocalDate validUntil,
                                    String notes, Totals totals, int locationId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sales_quotes (
                    quote_number, customer_id, customer_name, customer_phone, customer_email,
                    valid_until, quote_notes, subtotal_amount, discount_amount, vat_amount, vat_rate_percent, vat_mode, total_amount,
                    location_id, location_name, device_id, device_name, created_by_user_id, created_by_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING sales_quote_id
                """)) {
            ps.setString(1, quoteNumber);
            ps.setInt(2, customer.customerId());
            ps.setString(3, customer.name());
            ps.setString(4, blankToNull(customer.phone()));
            ps.setString(5, blankToNull(customer.email()));
            ps.setDate(6, Date.valueOf(validUntil));
            ps.setString(7, blankToNull(notes));
            ps.setBigDecimal(8, totals.subtotal());
            ps.setBigDecimal(9, totals.discount());
            ps.setBigDecimal(10, totals.vat());
            ps.setBigDecimal(11, totals.vatRate());
            ps.setString(12, totals.vatMode());
            ps.setBigDecimal(13, totals.total());
            ps.setInt(14, locationId);
            ps.setString(15, SessionManager.getCurrentLocationName());
            ps.setString(16, blankToNull(DeviceContextService.currentDeviceId()));
            ps.setString(17, blankToNull(DeviceContextService.currentDeviceName()));
            setNullableInteger(ps, 18, SessionManager.getCurrentUserId());
            ps.setString(19, SessionManager.getCurrentUserDisplayName());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("sales_quote_id");
                }
            }
        }
        throw new SQLException("Quote insert did not return an id.");
    }

    private static void insertQuoteLine(Connection conn, long quoteId, QuoteLineInput line, int sortOrder) throws SQLException {
        CompanyCustomizationManager.ReceiptSettings settings = CompanyCustomizationManager.loadReceiptSettings();
        LineAmounts amounts = lineAmounts(conn, settings, line);
        ProductTaxInfo taxInfo = productTaxInfo(conn, line.productId());
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sales_quote_lines (
                    sales_quote_id, product_id, item_name, sku, quantity, unit_price, original_unit_price, category_id,
                    discount_percent, discount_amount, vat_rate_percent, vat_amount, line_total, delivery_method, line_notes, sort_order
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setLong(1, quoteId);
            setNullableInteger(ps, 2, line.productId());
            ps.setString(3, line.itemName());
            ps.setString(4, blankToNull(line.sku()));
            ps.setInt(5, line.quantity());
            ps.setBigDecimal(6, money(line.unitPrice()));
            ps.setBigDecimal(7, money(line.unitPrice()));
            setNullableInteger(ps, 8, taxInfo.categoryId());
            ps.setBigDecimal(9, percent(line.discountPercent()));
            ps.setBigDecimal(10, amounts.discount());
            ps.setBigDecimal(11, amounts.vatRate());
            ps.setBigDecimal(12, amounts.vatAmount());
            ps.setBigDecimal(13, amounts.preVatTotal());
            ps.setString(14, normalizeDeliveryMethod(line.deliveryMethod()));
            ps.setString(15, blankToNull(line.notes()));
            ps.setInt(16, sortOrder);
            ps.executeUpdate();
        }
    }

    private static long insertOrderFromQuote(Connection conn, QuoteHeader quote, String orderNumber) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sales_orders (
                    order_number, sales_quote_id, quote_number, customer_id, customer_name, customer_phone, customer_email,
                    order_notes, subtotal_amount, discount_amount, vat_amount, vat_rate_percent, vat_mode, total_amount, balance_due,
                    location_id, location_name, device_id, device_name, created_by_user_id, created_by_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING sales_order_id
                """)) {
            ps.setString(1, orderNumber);
            ps.setLong(2, quote.salesQuoteId());
            ps.setString(3, quote.quoteNumber());
            ps.setInt(4, quote.customerId());
            ps.setString(5, quote.customerName());
            ps.setString(6, blankToNull(quote.customerPhone()));
            ps.setString(7, blankToNull(quote.customerEmail()));
            ps.setString(8, blankToNull(quote.quoteNotes()));
            ps.setBigDecimal(9, quote.subtotalAmount());
            ps.setBigDecimal(10, quote.discountAmount());
            ps.setBigDecimal(11, quote.vatAmount());
            ps.setBigDecimal(12, quote.vatRatePercent());
            ps.setString(13, blankToNull(quote.vatMode()));
            ps.setBigDecimal(14, quote.totalAmount());
            ps.setBigDecimal(15, quote.totalAmount());
            setNullableInteger(ps, 16, quote.locationId());
            ps.setString(17, blankToNull(quote.locationName()));
            ps.setString(18, blankToNull(DeviceContextService.currentDeviceId()));
            ps.setString(19, blankToNull(DeviceContextService.currentDeviceName()));
            setNullableInteger(ps, 20, SessionManager.getCurrentUserId());
            ps.setString(21, SessionManager.getCurrentUserDisplayName());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("sales_order_id");
                }
            }
        }
        throw new SQLException("Sales order insert did not return an id.");
    }

    private static void copyQuoteLinesToOrder(Connection conn, long quoteId, long orderId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sales_order_lines (
                    sales_order_id, sales_quote_line_id, product_id, item_name, sku,
                    quantity_ordered, unit_price, original_unit_price, category_id, discount_percent,
                    discount_amount, vat_rate_percent, vat_amount, line_total, delivery_method, line_notes, sort_order
                )
                SELECT ?, sales_quote_line_id, product_id, item_name, sku,
                       quantity, unit_price, original_unit_price, category_id, discount_percent,
                       discount_amount, vat_rate_percent, vat_amount, line_total, delivery_method, line_notes, sort_order
                FROM sales_quote_lines
                WHERE sales_quote_id = ?
                ORDER BY sort_order, sales_quote_line_id
                """)) {
            ps.setLong(1, orderId);
            ps.setLong(2, quoteId);
            ps.executeUpdate();
        }
    }

    private static void updateQuoteAccepted(Connection conn, long quoteId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE sales_quotes
                SET status = 'ACCEPTED',
                    accepted_at = CURRENT_TIMESTAMP,
                    accepted_by_user_id = ?,
                    accepted_by_name = ?
                WHERE sales_quote_id = ?
                """)) {
            setNullableInteger(ps, 1, SessionManager.getCurrentUserId());
            ps.setString(2, SessionManager.getCurrentUserDisplayName());
            ps.setLong(3, quoteId);
            ps.executeUpdate();
        }
    }

    private static void insertPayment(Connection conn, OrderHeader order, BigDecimal amount, String method,
                                      String reference, CashDrawerContext drawer) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sales_order_payments (
                    sales_order_id, customer_id, payment_amount, payment_method, payment_reference,
                    taken_by_user_id, taken_by_name, location_id, device_id, device_name,
                    cash_drawer_id, cash_drawer_name, cash_drawer_session_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setLong(1, order.salesOrderId());
            ps.setInt(2, order.customerId());
            ps.setBigDecimal(3, money(amount));
            ps.setString(4, method);
            ps.setString(5, blankToNull(reference));
            setNullableInteger(ps, 6, SessionManager.getCurrentUserId());
            ps.setString(7, SessionManager.getCurrentUserDisplayName());
            setNullableInteger(ps, 8, order.locationId());
            ps.setString(9, blankToNull(DeviceContextService.currentDeviceId()));
            ps.setString(10, blankToNull(DeviceContextService.currentDeviceName()));
            setNullableLong(ps, 11, drawer == null ? null : drawer.cashDrawerId());
            ps.setString(12, drawer == null ? null : drawer.drawerName());
            setNullableLong(ps, 13, drawer == null ? null : drawer.sessionId());
            ps.executeUpdate();
        }
    }

    private static void updateOrderPaymentTotals(Connection conn, long orderId, BigDecimal amount,
                                                 String method, String reference) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE sales_orders
                SET amount_paid = LEAST(total_amount, amount_paid + ?),
                    balance_due = GREATEST(total_amount - LEAST(total_amount, amount_paid + ?), 0),
                    payment_status = CASE
                        WHEN GREATEST(total_amount - LEAST(total_amount, amount_paid + ?), 0) <= 0 THEN 'PAID'
                        WHEN LEAST(total_amount, amount_paid + ?) > 0 THEN 'PARTIAL'
                        ELSE 'UNPAID'
                    END,
                    payment_method = ?,
                    payment_reference = COALESCE(NULLIF(?, ''), payment_reference)
                WHERE sales_order_id = ?
                """)) {
            BigDecimal safe = money(amount);
            ps.setBigDecimal(1, safe);
            ps.setBigDecimal(2, safe);
            ps.setBigDecimal(3, safe);
            ps.setBigDecimal(4, safe);
            ps.setString(5, method);
            ps.setString(6, blankToEmpty(reference));
            ps.setLong(7, orderId);
            ps.executeUpdate();
        }
    }

    private static void markOrderPaymentMethod(Connection conn, long orderId, String method, String reference) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE sales_orders
                SET payment_method = ?,
                    payment_reference = COALESCE(NULLIF(?, ''), payment_reference)
                WHERE sales_order_id = ?
                """)) {
            ps.setString(1, method);
            ps.setString(2, blankToEmpty(reference));
            ps.setLong(3, orderId);
            ps.executeUpdate();
        }
    }

    private static long insertAccountCharge(Connection conn, OrderHeader order, String reason) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO customer_account_transactions (
                    customer_id, sales_order_id, payment_id, location_id, amount, transaction_type,
                    note, user_name, device_id, device_name, payment_method, payment_reference
                )
                VALUES (?, ?, ?, ?, ?, 'SALES_ORDER_CREDIT', ?, ?, ?, ?, 'ACCOUNT', ?)
                RETURNING transaction_id
                """)) {
            ps.setInt(1, order.customerId());
            ps.setLong(2, order.salesOrderId());
            ps.setString(3, "SO-" + order.orderNumber());
            setNullableInteger(ps, 4, order.locationId());
            ps.setBigDecimal(5, order.balanceDue());
            ps.setString(6, blankToNull(reason));
            ps.setString(7, SessionManager.getCurrentUserDisplayName());
            ps.setString(8, blankToNull(DeviceContextService.currentDeviceId()));
            ps.setString(9, blankToNull(DeviceContextService.currentDeviceName()));
            ps.setString(10, order.orderNumber());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("transaction_id");
                }
            }
        }
        throw new SQLException("Customer account charge did not return a transaction id.");
    }

    private static void insertCustomerPaymentTransaction(Connection conn, OrderHeader order, BigDecimal amount,
                                                         String method, String reference, CashDrawerContext drawer) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO customer_account_transactions (
                    customer_id, sales_order_id, payment_id, location_id, amount, transaction_type,
                    note, user_name, device_id, device_name, payment_method, payment_reference,
                    cash_drawer_id, cash_drawer_name, cash_drawer_session_id
                )
                VALUES (?, ?, ?, ?, ?, 'PAYMENT', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setInt(1, order.customerId());
            ps.setLong(2, order.salesOrderId());
            ps.setString(3, "SOP-" + order.orderNumber() + "-" + System.currentTimeMillis());
            setNullableInteger(ps, 4, order.locationId());
            ps.setBigDecimal(5, amount);
            ps.setString(6, "Payment applied to sales order " + order.orderNumber());
            ps.setString(7, SessionManager.getCurrentUserDisplayName());
            ps.setString(8, blankToNull(DeviceContextService.currentDeviceId()));
            ps.setString(9, blankToNull(DeviceContextService.currentDeviceName()));
            ps.setString(10, method);
            ps.setString(11, blankToNull(reference));
            setNullableLong(ps, 12, drawer == null ? null : drawer.cashDrawerId());
            ps.setString(13, drawer == null ? null : drawer.drawerName());
            setNullableLong(ps, 14, drawer == null ? null : drawer.sessionId());
            ps.executeUpdate();
        }
    }

    private static void insertAccountAllocation(Connection conn, long transactionId, OrderHeader order, BigDecimal amount) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO customer_account_payment_allocations (payment_transaction_id, customer_id, sales_order_id, amount)
                VALUES (?, ?, ?, ?)
                """)) {
            ps.setLong(1, transactionId);
            ps.setInt(2, order.customerId());
            ps.setLong(3, order.salesOrderId());
            ps.setBigDecimal(4, amount);
            ps.executeUpdate();
        }
    }

    private static long insertDeliveryEvent(Connection conn, OrderHeader order, String deliveryNumber,
                                            String method, String receiverName, String notes) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sales_order_delivery_events (
                    sales_order_id, delivery_number, delivery_method, receiver_name, delivery_notes,
                    remaining_balance, delivered_by_user_id, delivered_by_name, location_id, device_id, device_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING sales_order_delivery_event_id
                """)) {
            ps.setLong(1, order.salesOrderId());
            ps.setString(2, deliveryNumber);
            ps.setString(3, method);
            ps.setString(4, blankToNull(receiverName));
            ps.setString(5, blankToNull(notes));
            ps.setBigDecimal(6, order.balanceDue());
            setNullableInteger(ps, 7, SessionManager.getCurrentUserId());
            ps.setString(8, SessionManager.getCurrentUserDisplayName());
            setNullableInteger(ps, 9, order.locationId());
            ps.setString(10, blankToNull(DeviceContextService.currentDeviceId()));
            ps.setString(11, blankToNull(DeviceContextService.currentDeviceName()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("sales_order_delivery_event_id");
                }
            }
        }
        throw new SQLException("Delivery event insert did not return an id.");
    }

    private static void postDeliveryLine(Connection conn, OrderHeader order, long eventId, DeliveryLineInput input) throws SQLException {
        OrderLine line = lockOrderLine(conn, input.salesOrderLineId());
        if (line.salesOrderId() != order.salesOrderId()) {
            throw new SQLException("Delivery line does not belong to this sales order.");
        }
        int remaining = line.quantityOrdered() - line.quantityDelivered();
        if (input.quantityDelivered() <= 0 || input.quantityDelivered() > remaining) {
            throw new SQLException("Delivery quantity for " + line.itemName() + " must be between 1 and " + remaining + ".");
        }
        if (line.productId() != null) {
            int available = availableStock(conn, line.productId(), order.locationId());
            if (available < input.quantityDelivered()) {
                throw new SQLException("Only " + available + " in stock for " + line.itemName() + "; cannot deliver " + input.quantityDelivered() + ".");
            }
            deductInventory(conn, order, line, input.quantityDelivered(), eventId);
        }
        int newDelivered = line.quantityDelivered() + input.quantityDelivered();
        int newRemaining = line.quantityOrdered() - newDelivered;
        String lineStatus = newRemaining <= 0 ? "DELIVERED" : "PARTIAL";
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE sales_order_lines
                SET quantity_delivered = ?,
                    delivery_status = ?
                WHERE sales_order_line_id = ?
                """)) {
            ps.setInt(1, newDelivered);
            ps.setString(2, lineStatus);
            ps.setLong(3, line.salesOrderLineId());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sales_order_delivery_lines (
                    sales_order_delivery_event_id, sales_order_id, sales_order_line_id,
                    product_id, item_name, quantity_delivered, quantity_remaining
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setLong(1, eventId);
            ps.setLong(2, order.salesOrderId());
            ps.setLong(3, line.salesOrderLineId());
            setNullableInteger(ps, 4, line.productId());
            ps.setString(5, line.itemName());
            ps.setInt(6, input.quantityDelivered());
            ps.setInt(7, newRemaining);
            ps.executeUpdate();
        }
    }

    private static void deductInventory(Connection conn, OrderHeader order, OrderLine line, int quantity, long deliveryEventId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE inventory
                SET quantity_on_hand = quantity_on_hand - ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE product_id = ? AND location_id = ?
                """)) {
            ps.setInt(1, quantity);
            ps.setInt(2, line.productId());
            ps.setInt(3, order.locationId());
            if (ps.executeUpdate() == 0) {
                throw new SQLException("No inventory row exists for " + line.itemName() + " at this location.");
            }
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO inventory_movements (
                    product_id, location_id, change_qty, reason, note, user_name,
                    sales_order_id, sales_order_line_id, sales_order_delivery_event_id,
                    device_id, device_name, user_id
                )
                VALUES (?, ?, ?, 'SALES_ORDER_DELIVERY', ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setInt(1, line.productId());
            ps.setInt(2, order.locationId());
            ps.setInt(3, -quantity);
            ps.setString(4, "Delivered for sales order " + order.orderNumber());
            ps.setString(5, SessionManager.getCurrentUserDisplayName());
            ps.setLong(6, order.salesOrderId());
            ps.setLong(7, line.salesOrderLineId());
            ps.setLong(8, deliveryEventId);
            ps.setString(9, blankToNull(DeviceContextService.currentDeviceId()));
            ps.setString(10, blankToNull(DeviceContextService.currentDeviceName()));
            setNullableInteger(ps, 11, SessionManager.getCurrentUserId());
            ps.executeUpdate();
        }
    }

    private static String recomputeDeliveryStatus(Connection conn, long orderId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT
                    SUM(quantity_delivered) AS delivered,
                    SUM(quantity_ordered) AS ordered
                FROM sales_order_lines
                WHERE sales_order_id = ?
                """)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int delivered = rs.getInt("delivered");
                    int ordered = rs.getInt("ordered");
                    if (ordered > 0 && delivered >= ordered) {
                        return "DELIVERED";
                    }
                    if (delivered > 0) {
                        return "PARTIALLY_DELIVERED";
                    }
                }
            }
        }
        return "OPEN";
    }

    private static void updateOrderStatus(Connection conn, long orderId, String status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE sales_orders
                SET status = ?,
                    delivered_at = CASE WHEN ? = 'DELIVERED' THEN CURRENT_TIMESTAMP ELSE delivered_at END
                WHERE sales_order_id = ?
                """)) {
            ps.setString(1, status);
            ps.setString(2, status);
            ps.setLong(3, orderId);
            ps.executeUpdate();
        }
    }

    private static CustomerInfo loadCustomer(Connection conn, int customerId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT customer_id, name, phone, email
                FROM customer_accounts
                WHERE customer_id = ? AND is_active = TRUE
                """)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new CustomerInfo(rs.getInt("customer_id"), rs.getString("name"), rs.getString("phone"), rs.getString("email"));
                }
            }
        }
        throw new SQLException("Customer account not found.");
    }

    private static QuoteHeader lockQuote(Connection conn, long quoteId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT *
                FROM sales_quotes
                WHERE sales_quote_id = ?
                FOR UPDATE
                """)) {
            ps.setLong(1, quoteId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Date validUntil = rs.getDate("valid_until");
                    return new QuoteHeader(
                            rs.getLong("sales_quote_id"),
                            rs.getString("quote_number"),
                            rs.getInt("customer_id"),
                            rs.getString("customer_name"),
                            rs.getString("customer_phone"),
                            rs.getString("customer_email"),
                            rs.getString("status"),
                            validUntil == null ? null : validUntil.toLocalDate(),
                            rs.getString("quote_notes"),
                            zero(rs.getBigDecimal("subtotal_amount")),
                            zero(rs.getBigDecimal("discount_amount")),
                            zero(rs.getBigDecimal("vat_amount")),
                            zero(rs.getBigDecimal("vat_rate_percent")),
                            rs.getString("vat_mode"),
                            zero(rs.getBigDecimal("total_amount")),
                            nullableInt(rs, "location_id"),
                            rs.getString("location_name")
                    );
                }
            }
        }
        throw new SQLException("Sales quote not found.");
    }

    private static OrderHeader lockOrder(Connection conn, long orderId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT *
                FROM sales_orders
                WHERE sales_order_id = ?
                FOR UPDATE
                """)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new OrderHeader(
                            rs.getLong("sales_order_id"),
                            rs.getString("order_number"),
                            rs.getInt("customer_id"),
                            rs.getString("status"),
                            zero(rs.getBigDecimal("total_amount")),
                            zero(rs.getBigDecimal("amount_paid")),
                            zero(rs.getBigDecimal("balance_due")),
                            nullableInt(rs, "location_id")
                    );
                }
            }
        }
        throw new SQLException("Sales order not found.");
    }

    private static OrderLine lockOrderLine(Connection conn, long lineId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT sales_order_line_id, sales_order_id, product_id, item_name,
                       quantity_ordered, quantity_delivered
                FROM sales_order_lines
                WHERE sales_order_line_id = ?
                FOR UPDATE
                """)) {
            ps.setLong(1, lineId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new OrderLine(
                            rs.getLong("sales_order_line_id"),
                            rs.getLong("sales_order_id"),
                            nullableInt(rs, "product_id"),
                            rs.getString("item_name"),
                            rs.getInt("quantity_ordered"),
                            rs.getInt("quantity_delivered")
                    );
                }
            }
        }
        throw new SQLException("Sales order line not found.");
    }

    private static boolean hasAccountCharge(Connection conn, long orderId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT 1
                FROM customer_account_transactions
                WHERE sales_order_id = ?
                  AND transaction_type = 'SALES_ORDER_CREDIT'
                LIMIT 1
                """)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void updateCustomerBalance(Connection conn, int customerId, BigDecimal delta) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE customer_accounts
                SET current_balance = current_balance + ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE customer_id = ?
                """)) {
            ps.setBigDecimal(1, money(delta));
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    private static void reduceCustomerBalance(Connection conn, int customerId, BigDecimal amount) throws SQLException {
        updateCustomerBalance(conn, customerId, money(amount).negate());
    }

    private static CashDrawerContext cashDrawerForPayment(Connection conn, String method) throws SQLException {
        if (!"CASH".equals(method)) {
            return null;
        }
        CashDrawerContext drawer = CashDrawerService.requireActiveCashSession(conn);
        return drawer.hasActiveSession() ? drawer : null;
    }

    private static Totals totals(Connection conn, List<QuoteLineInput> lines) throws SQLException {
        CompanyCustomizationManager.ReceiptSettings settings = CompanyCustomizationManager.loadReceiptSettings();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal vat = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (QuoteLineInput line : lines) {
            LineAmounts amounts = lineAmounts(conn, settings, line);
            subtotal = subtotal.add(money(line.unitPrice()).multiply(BigDecimal.valueOf(Math.max(line.quantity(), 0))));
            discount = discount.add(amounts.discount());
            vat = vat.add(amounts.vatAmount());
            total = total.add(amounts.preVatTotal());
        }
        BigDecimal preVatTotal = money(total);
        BigDecimal vatAmount = money(vat);
        BigDecimal effectiveRate = preVatTotal.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : vatAmount.multiply(BigDecimal.valueOf(100)).divide(preVatTotal, 2, RoundingMode.HALF_UP);
        String mode = settings.vatEnabled() ? (settings.vatUseDepartmentRates() ? "DEPARTMENT" : "FIXED") : "";
        return new Totals(money(subtotal), money(discount), vatAmount, effectiveRate, mode, preVatTotal.add(vatAmount).setScale(2, RoundingMode.HALF_UP));
    }

    private static LineAmounts lineAmounts(Connection conn, CompanyCustomizationManager.ReceiptSettings settings, QuoteLineInput line) throws SQLException {
        BigDecimal gross = money(line.unitPrice()).multiply(BigDecimal.valueOf(Math.max(line.quantity(), 0)));
        BigDecimal discount = gross.multiply(percent(line.discountPercent()).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        BigDecimal preVatTotal = gross.subtract(discount).max(BigDecimal.ZERO);
        BigDecimal vatRate = BigDecimal.ZERO;
        if (settings.vatEnabled()) {
            vatRate = settings.vatUseDepartmentRates()
                    ? productTaxInfo(conn, line.productId()).vatRatePercent()
                    : settings.vatFixedRatePercent();
        }
        BigDecimal vatAmount = preVatTotal.multiply(vatRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new LineAmounts(money(discount), money(preVatTotal), percent(vatRate), money(vatAmount));
    }

    private static ProductTaxInfo productTaxInfo(Connection conn, Integer productId) throws SQLException {
        if (productId == null) {
            return new ProductTaxInfo(null, BigDecimal.ZERO);
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT p.category_id, COALESCE(c.vat_rate_percent, 0) AS vat_rate_percent
                FROM products p
                LEFT JOIN categories c ON c.category_id = p.category_id
                WHERE p.product_id = ?
                """)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ProductTaxInfo(nullableInt(rs, "category_id"), zero(rs.getBigDecimal("vat_rate_percent")));
                }
            }
        }
        return new ProductTaxInfo(null, BigDecimal.ZERO);
    }

    private static int requireLocationId() throws SQLException {
        Integer locationId = SessionManager.getCurrentLocationId();
        if (locationId == null) {
            throw new SQLException("No active location is selected.");
        }
        return locationId;
    }

    private static String normalizePaymentMethod(String method) throws SQLException {
        String safe = method == null ? "" : method.trim().toUpperCase();
        if (List.of("CASH", "CARD", "CHEQUE", "MMG", "ACCOUNT").contains(safe)) {
            return safe;
        }
        throw new SQLException("Unsupported payment method: " + method);
    }

    private static String normalizeDeliveryMethod(String method) {
        String safe = method == null ? "" : method.trim().toUpperCase();
        return List.of("PICKUP", "LOCAL_DELIVERY", "SHIP", "INSTALLATION").contains(safe) ? safe : "PICKUP";
    }

    private static BigDecimal money(BigDecimal value) {
        return zero(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(BigDecimal value) {
        return zero(value).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public record QuoteLineInput(Integer productId, String itemName, String sku, int quantity,
                                 BigDecimal unitPrice, BigDecimal discountPercent,
                                 String deliveryMethod, String notes) {
    }

    public record DeliveryLineInput(long salesOrderLineId, int quantityDelivered) {
    }

    public record QuoteResult(long quoteId, String quoteNumber) {
    }

    public record OrderResult(long orderId, String orderNumber) {
    }

    public record DeliveryResult(long deliveryEventId, String deliveryNumber) {
    }

    private record CustomerInfo(int customerId, String name, String phone, String email) {
    }

    private record QuoteHeader(long salesQuoteId, String quoteNumber, int customerId, String customerName,
                               String customerPhone, String customerEmail, String status, LocalDate validUntil,
                               String quoteNotes, BigDecimal subtotalAmount, BigDecimal discountAmount,
                               BigDecimal vatAmount, BigDecimal vatRatePercent, String vatMode,
                               BigDecimal totalAmount, Integer locationId, String locationName) {
    }

    private record OrderHeader(long salesOrderId, String orderNumber, int customerId, String status,
                               BigDecimal totalAmount, BigDecimal amountPaid, BigDecimal balanceDue,
                               Integer locationId) {
    }

    private record OrderLine(long salesOrderLineId, long salesOrderId, Integer productId, String itemName,
                             int quantityOrdered, int quantityDelivered) {
    }

    private record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal vat,
                          BigDecimal vatRate, String vatMode, BigDecimal total) {
    }

    private record LineAmounts(BigDecimal discount, BigDecimal preVatTotal, BigDecimal vatRate, BigDecimal vatAmount) {
    }

    private record ProductTaxInfo(Integer categoryId, BigDecimal vatRatePercent) {
    }
}
