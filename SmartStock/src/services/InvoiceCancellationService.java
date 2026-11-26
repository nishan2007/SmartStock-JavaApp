package services;

import java.math.BigDecimal;
import models.CashDrawerContext;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import static services.ServerQuotationInvoiceService.*;

/** Transactional corrections. Original documents and ledger entries are retained. */
public final class InvoiceCancellationService {
    private InvoiceCancellationService() { }

    public static String permissionForAction(String action) {
        return switch (action) {
            case "CANCEL" -> "CANCEL_QUOTATIONS";
            case "CANCEL_INVOICE" -> "CANCEL_INVOICES";
            case "CANCEL_DELIVERY" -> "CANCEL_INVOICE_DELIVERIES";
            case "CANCEL_PAYMENT" -> "CANCEL_INVOICE_PAYMENTS";
            default -> null;
        };
    }

    private static SQLException rule(String message) { return new SQLException(message, "22023"); }

    private static InvoiceHeader invoice(Connection c, long id, String reason) throws SQLException {
        if (c.getAutoCommit()) throw rule("Cancellation requires a transaction.");
        if (reason == null || reason.isBlank()) throw rule("Enter a cancellation reason.");
        configureTransactionTimeouts(c);
        var invoice = lockInvoice(c, id);
        if (!java.util.Objects.equals(invoice.locationId(), ServerRequestIdentity.locationId()))
            throw rule("Select an invoice at the current location.");
        if ("CANCELLED".equals(invoice.status())) throw rule("This invoice is already cancelled.");
        return invoice;
    }

    private static boolean cancelled(Connection c, long invoice, String action, long id) throws SQLException {
        try (var p = c.prepareStatement("SELECT 1 FROM invoice_audit_log WHERE invoice_id=? AND action_type=? AND field_name='cancelled_document_id' AND old_value=?")) {
            p.setLong(1, invoice); p.setString(2, action); p.setString(3, Long.toString(id));
            try (var r = p.executeQuery()) { return r.next(); }
        }
    }

    private static void audit(Connection c, long invoice, String action, long id, String reason) throws SQLException {
        QuotationInvoiceAuditService.recordInvoiceAudit(c, invoice, action, "cancelled_document_id", id, "CANCELLED", reason);
        SyncOutboxService.recordEvent(c, action, Map.of("invoice_id", invoice, "document_id", id));
    }

    public static void cancelDelivery(Connection c, long eventId, String reason) throws SQLException {
        long invoiceId;
        try (var p = c.prepareStatement("SELECT invoice_id FROM invoice_delivery_events WHERE invoice_delivery_event_id=?")) {
            p.setLong(1, eventId);
            try (var r = p.executeQuery()) { if (!r.next()) throw rule("Delivery was not found."); invoiceId = r.getLong(1); }
        }
        var invoice = invoice(c, invoiceId, reason);
        if (cancelled(c, invoiceId, "DELIVERY_CANCELLED", eventId)) throw rule("This delivery is already cancelled.");
        try (var p = c.prepareStatement("SELECT invoice_line_id,quantity_delivered FROM invoice_delivery_lines WHERE invoice_delivery_event_id=? ORDER BY invoice_line_id")) {
            p.setLong(1, eventId);
            try (var r = p.executeQuery()) {
                while (r.next()) {
                    var line = lockInvoiceLine(c, r.getLong(1));
                    int quantity = r.getInt(2), remainingDelivered = line.quantityDelivered() - quantity;
                    if (line.salesInvoiceId() != invoiceId || remainingDelivered < 0) throw rule("Delivery quantities have changed; cancellation cannot be completed.");
                    try (var u = c.prepareStatement("UPDATE invoice_lines SET quantity_delivered=?,delivery_status=? WHERE invoice_line_id=?")) {
                        u.setInt(1, remainingDelivered); u.setString(2, remainingDelivered == 0 ? "PENDING" : remainingDelivered == line.quantityInvoiceed() ? "DELIVERED" : "PARTIAL");
                        u.setLong(3, line.salesInvoiceLineId()); u.executeUpdate();
                    }
                    if (line.productId() != null) {
                        try (var u = c.prepareStatement("UPDATE inventory SET quantity_on_hand=quantity_on_hand+?,updated_at=CURRENT_TIMESTAMP WHERE product_id=? AND location_id=?")) {
                            u.setInt(1, quantity); u.setInt(2, line.productId()); u.setInt(3, invoice.locationId());
                            if (u.executeUpdate() != 1) throw rule("Inventory row was not found for the returned item.");
                        }
                        try (var u = c.prepareStatement("""
                                INSERT INTO inventory_movements(product_id,location_id,change_qty,reason,note,user_name,
                                  invoice_id,invoice_line_id,invoice_delivery_event_id,device_id,device_name,user_id)
                                VALUES(?,?,?,'INVOICE_DELIVERY_CANCELLED',?,?,?,?,?,?,?,?)
                                """)) {
                            u.setInt(1, line.productId()); u.setInt(2, invoice.locationId()); u.setInt(3, quantity);
                            u.setString(4, reason); u.setString(5, ServerRequestIdentity.userName()); u.setLong(6, invoiceId);
                            u.setLong(7, line.salesInvoiceLineId()); u.setLong(8, eventId);
                            u.setString(9, DeviceContextService.currentDeviceId()); u.setString(10, ServerRequestIdentity.deviceName());
                            u.setObject(11, ServerRequestIdentity.userId()); u.executeUpdate();
                        }
                    }
                }
            }
        }
        String status = recomputeDeliveryStatus(c, invoiceId);
        updateInvoiceStatus(c, invoiceId, status);
        QuotationInvoiceAuditService.recordInvoiceStatus(c, invoiceId, invoice.status(), status, reason);
        audit(c, invoiceId, "DELIVERY_CANCELLED", eventId, reason);
    }

    public static void cancelPayment(Connection c, long transactionId, String reason, String reference) throws SQLException {
        long invoiceId;
        try (var p = c.prepareStatement("SELECT invoice_id FROM customer_account_transactions WHERE transaction_id=? AND transaction_type='PAYMENT' AND invoice_id IS NOT NULL")) {
            p.setLong(1, transactionId);
            try (var r = p.executeQuery()) { if (!r.next()) throw rule("Select an invoice payment to cancel."); invoiceId = r.getLong(1); }
        }
        var invoice = invoice(c, invoiceId, reason);
        if (cancelled(c, invoiceId, "PAYMENT_CANCELLED", transactionId)) throw rule("This payment is already cancelled.");
        BigDecimal amount; String method;
        try (var p = c.prepareStatement("SELECT ABS(amount),payment_method,credit_applied_amount,customer_id FROM customer_account_transactions WHERE transaction_id=? FOR UPDATE")) {
            p.setLong(1, transactionId);
            try (var r = p.executeQuery()) {
                r.next(); amount = r.getBigDecimal(1); method = r.getString(2);
                if (r.getInt(4) != invoice.customerId() || amount.signum() <= 0 || amount.compareTo(invoice.amountPaid()) > 0)
                    throw rule("This payment no longer matches the invoice's paid amount.");
                if (r.getBigDecimal(3).compareTo(amount) != 0) throw rule("This payment has unapplied credit; review its account allocations before cancelling.");
            }
        }
        // Only reverse payments wholly allocated to this invoice. Never consume another document's credit.
        try (var p = c.prepareStatement("SELECT COALESCE(SUM(amount),0),COUNT(*) FILTER(WHERE invoice_id IS DISTINCT FROM ?) FROM customer_account_payment_allocations WHERE payment_transaction_id=?")) {
            p.setLong(1, invoiceId); p.setLong(2, transactionId);
            try (var r = p.executeQuery()) { r.next(); if (r.getBigDecimal(1).compareTo(amount) != 0 || r.getInt(2) != 0) throw rule("This payment has allocations outside this invoice; review the customer account first."); }
        }
        if (!java.util.List.of("CASH", "CARD", "CHEQUE", "MMG").contains(method == null ? "" : method)) throw rule("This payment method cannot be reversed here.");
        if (!"CASH".equals(method) && (reference == null || reference.isBlank())) throw rule("Enter the payment reversal reference.");
        var drawer = cashDrawerForPayment(c, method);
        String note = "Cancellation of invoice payment #" + transactionId + ": " + reason;
        try (var p = c.prepareStatement("""
                INSERT INTO invoice_payments(invoice_id,customer_id,payment_amount,payment_method,payment_reference,
                  payment_action,taken_by_user_id,taken_by_name,location_id,device_id,device_name,
                  cash_drawer_id,cash_drawer_name,cash_drawer_session_id)
                VALUES(?,?,?,?,?,'REVERSAL',?,?,?,?,?,?,?,?)
                """)) {
            p.setLong(1, invoiceId); p.setInt(2, invoice.customerId()); p.setBigDecimal(3, amount); p.setString(4, method);
            p.setString(5, reference); p.setObject(6, ServerRequestIdentity.userId()); p.setString(7, ServerRequestIdentity.userName());
            p.setInt(8, invoice.locationId()); p.setString(9, DeviceContextService.currentDeviceId()); p.setString(10, ServerRequestIdentity.deviceName());
            p.setObject(11, drawer == null ? null : drawer.cashDrawerId()); p.setString(12, drawer == null ? null : drawer.drawerName());
            p.setObject(13, drawer == null ? null : drawer.sessionId()); p.executeUpdate();
        }
        ledgerCorrection(c, invoice, amount, "INVOICE_PAYMENT_REVERSAL", "INV-PAY-CANCEL-" + transactionId, note, method, reference, drawer);
        try (var p = c.prepareStatement("UPDATE invoices SET amount_paid=amount_paid-?,balance_due=balance_due+?,payment_status=CASE WHEN amount_paid-?=0 THEN 'UNPAID' ELSE 'PARTIAL' END WHERE invoice_id=?")) {
            p.setBigDecimal(1, amount); p.setBigDecimal(2, amount); p.setBigDecimal(3, amount); p.setLong(4, invoiceId); p.executeUpdate();
        }
        audit(c, invoiceId, "PAYMENT_CANCELLED", transactionId, reason);
    }

    public static void cancelInvoice(Connection c, long invoiceId, String reason) throws SQLException {
        var invoice = invoice(c, invoiceId, reason);
        if (invoice.amountPaid().signum() != 0) throw rule("Cancel or reverse the invoice's payments before cancelling the invoice.");
        try (var p = c.prepareStatement("SELECT 1 FROM invoice_lines WHERE invoice_id=? AND quantity_delivered>0")) {
            p.setLong(1, invoiceId); try (var r = p.executeQuery()) { if (r.next()) throw rule("Cancel the invoice's deliveries before cancelling the invoice."); }
        }
        Long orderId = null;
        try (var p = c.prepareStatement("SELECT linked_custom_order_id FROM invoices WHERE invoice_id=?")) {
            p.setLong(1, invoiceId); try (var r = p.executeQuery()) { r.next(); orderId = (Long) r.getObject(1); }
        }
        if (orderId != null) {
            try (var p = c.prepareStatement("SELECT status FROM custom_orders WHERE custom_order_id=? FOR UPDATE")) {
                p.setLong(1, orderId); try (var r = p.executeQuery()) { if (!r.next()) throw rule("Linked production order was not found."); }
            }
            try (var p = c.prepareStatement("SELECT 1 FROM custom_order_lines WHERE custom_order_id=? AND delivery_status='DELIVERED'")) {
                p.setLong(1, orderId); try (var r = p.executeQuery()) { if (r.next()) throw rule("The linked production order has delivered items. Process its returns before cancelling this invoice."); }
            }
            try (var p = c.prepareStatement("UPDATE custom_orders SET status='CANCELLED',balance_due=0,updated_at=CURRENT_TIMESTAMP WHERE custom_order_id=? AND invoice_billed=TRUE")) {
                p.setLong(1, orderId); if (p.executeUpdate() != 1) throw rule("The linked order is not billed on this invoice.");
            }
            SyncOutboxService.recordEvent(c, "CUSTOM_ORDER_CANCELLED", Map.of("custom_order_id", orderId, "invoice_id", invoiceId));
        }
        BigDecimal charge;
        try (var p = c.prepareStatement("SELECT COALESCE(SUM(amount),0) FROM customer_account_transactions WHERE invoice_id=? AND transaction_type='INVOICE_CREDIT'")) {
            p.setLong(1, invoiceId); try (var r = p.executeQuery()) { r.next(); charge = r.getBigDecimal(1); }
        }
        if (charge.signum() > 0) ledgerCorrection(c, invoice, charge.negate(), "INVOICE_CANCELLATION", "INV-CANCEL-" + invoiceId, reason, "ACCOUNT", null, null);
        try (var p = c.prepareStatement("UPDATE invoices SET status='CANCELLED',balance_due=0,delivered_at=NULL WHERE invoice_id=?")) {
            p.setLong(1, invoiceId); p.executeUpdate();
        }
        QuotationInvoiceAuditService.recordInvoiceStatus(c, invoiceId, invoice.status(), "CANCELLED", reason);
        audit(c, invoiceId, "INVOICE_CANCELLED", invoiceId, reason);
    }

    private static void ledgerCorrection(Connection c, InvoiceHeader invoice, BigDecimal delta, String type, String key,
                                         String note, String method, String reference, CashDrawerContext drawer) throws SQLException {
        try (var p = c.prepareStatement("""
                INSERT INTO customer_account_transactions(customer_id,invoice_id,payment_id,location_id,amount,transaction_type,
                 note,user_name,device_id,device_name,payment_method,payment_reference,cash_drawer_id,cash_drawer_name,cash_drawer_session_id)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            p.setInt(1, invoice.customerId()); p.setLong(2, invoice.salesInvoiceId()); p.setString(3, key); p.setInt(4, invoice.locationId());
            p.setBigDecimal(5, delta); p.setString(6, type); p.setString(7, note); p.setString(8, ServerRequestIdentity.userName());
            p.setString(9, DeviceContextService.currentDeviceId()); p.setString(10, ServerRequestIdentity.deviceName());
            p.setString(11, method); p.setString(12, reference); p.setObject(13, drawer == null ? null : drawer.cashDrawerId());
            p.setString(14, drawer == null ? null : drawer.drawerName()); p.setObject(15, drawer == null ? null : drawer.sessionId()); p.executeUpdate();
        }
        try (var p = c.prepareStatement("UPDATE customer_accounts SET current_balance=GREATEST(current_balance+?,0),updated_at=CURRENT_TIMESTAMP WHERE customer_id=?")) {
            p.setBigDecimal(1, delta); p.setInt(2, invoice.customerId()); if (p.executeUpdate() != 1) throw rule("Customer account was not found.");
        }
    }
}
