package services;

import data.DB;
import data.DatabaseConfig;
import managers.SessionManager;
import models.CashDrawerContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

public final class ServerQuotationInvoiceService {
    private ServerQuotationInvoiceService() {
    }

    public static QuotationResult createQuotation(int customerId, LocalDate validUntil, String notes, List<QuotationLineInput> lines) throws SQLException {
        if (lines == null || lines.isEmpty()) {
            throw new SQLException("Add at least one quotation line.");
        }
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                configureTransactionTimeouts(conn);
                QuotationInvoiceSchemaInstaller.ensureSchema(conn);
                int locationId = requireLocationId();
                CustomerInfo customer = loadCustomer(conn, customerId);
                LocalDate safeValidUntil = validUntil == null
                        ? LocalDate.now().plusDays(QuotationInvoiceNumberManager.defaultQuotationValidityDays(conn, locationId))
                        : validUntil;
                String quotationNumber = QuotationInvoiceNumberManager.nextQuotationNumber(conn, locationId);
                Totals totals = totals(conn, locationId, lines);
                long quotationId = insertQuotation(conn, quotationNumber, customer, safeValidUntil, notes, totals, locationId);
                int sortInvoice = 0;
                for (QuotationLineInput line : lines) {
                    insertQuotationLine(conn, quotationId, line, locationId, sortInvoice++);
                }
                QuotationInvoiceAuditService.recordQuotationStatus(conn, quotationId, null, "DRAFT", "Quotation created.");
                QuotationInvoiceAuditService.recordQuotationAudit(conn, quotationId, "QUOTE_CREATED", null, null, quotationNumber, notes);
                SyncOutboxService.recordEvent(conn, "QUOTATION_CREATED", Map.of("quotation_id", quotationId, "quotation_number", quotationNumber));
                conn.commit();
                return new QuotationResult(quotationId, quotationNumber);
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static void issueQuotation(long quotationId) throws SQLException {
        updateQuotationStatus(quotationId, "ISSUED", "Quotation issued to customer.");
    }

    public static QuotationResult updateDraftQuotation(long quotationId, int customerId, LocalDate validUntil,
                                               String notes, List<QuotationLineInput> lines) throws SQLException {
        if (lines == null || lines.isEmpty()) {
            throw new SQLException("Add at least one quotation line.");
        }
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                configureTransactionTimeouts(conn);
                QuotationInvoiceSchemaInstaller.ensureSchema(conn);
                QuotationHeader quotation = lockQuotation(conn, quotationId);
                if (!"DRAFT".equals(quotation.status())) {
                    throw new SQLException("Only draft quotations can be edited.");
                }
                int locationId = quotation.locationId() == null ? requireLocationId() : quotation.locationId();
                CustomerInfo customer = loadCustomer(conn, customerId);
                LocalDate safeValidUntil = validUntil == null ? quotation.validUntil() : validUntil;
                if (safeValidUntil == null) {
                    safeValidUntil = LocalDate.now().plusDays(QuotationInvoiceNumberManager.defaultQuotationValidityDays(conn, locationId));
                }
                Totals totals = totals(conn, locationId, lines);
                updateQuotationHeader(conn, quotationId, customer, safeValidUntil, notes, totals);
                replaceQuotationLines(conn, quotationId, lines, locationId);
                QuotationInvoiceAuditService.recordQuotationAudit(conn, quotationId, "QUOTE_UPDATED", null, null, quotation.quotationNumber(), notes);
                SyncOutboxService.recordEvent(conn, "QUOTATION_UPDATED", Map.of("quotation_id", quotationId, "quotation_number", quotation.quotationNumber()));
                conn.commit();
                return new QuotationResult(quotationId, quotation.quotationNumber());
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static void cancelQuotation(long quotationId, String reason) throws SQLException {
        updateQuotationStatus(quotationId, "CANCELLED", blankToNull(reason) == null ? "Quotation cancelled." : reason);
    }

    public static InvoiceResult acceptQuotation(long quotationId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                configureTransactionTimeouts(conn);
                QuotationInvoiceSchemaInstaller.ensureSchema(conn);
                QuotationHeader quotation = lockQuotation(conn, quotationId);
                if (!"DRAFT".equals(quotation.status()) && !"ISSUED".equals(quotation.status())) {
                    throw new SQLException("Only draft or issued quotations can be accepted.");
                }
                if (quotation.validUntil() != null && quotation.validUntil().isBefore(LocalDate.now())) {
                    throw new SQLException("This quotation expired on " + quotation.validUntil() + ".");
                }
                int locationId = quotation.locationId() == null ? requireLocationId() : quotation.locationId();
                String invoiceNumber = QuotationInvoiceNumberManager.nextInvoiceNumber(conn, locationId);
                long invoiceId = insertInvoiceFromQuotation(conn, quotation, invoiceNumber);
                copyQuotationLinesToInvoice(conn, quotationId, invoiceId);
                updateQuotationAccepted(conn, quotationId);
                QuotationInvoiceAuditService.recordQuotationStatus(conn, quotationId, quotation.status(), "ACCEPTED", "Quotation accepted and converted to invoice " + invoiceNumber + ".");
                QuotationInvoiceAuditService.recordInvoiceStatus(conn, invoiceId, null, "OPEN", "Sales invoice created from quotation " + quotation.quotationNumber() + ".");
                QuotationInvoiceAuditService.recordInvoiceAudit(conn, invoiceId, "ORDER_CREATED_FROM_QUOTE", "quotation_number", null, quotation.quotationNumber(), null);
                SyncOutboxService.recordEvent(conn, "QUOTATION_ACCEPTED", Map.of("quotation_id", quotationId, "invoice_id", invoiceId, "invoice_number", invoiceNumber));
                conn.commit();
                return new InvoiceResult(invoiceId, invoiceNumber);
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static PaymentReceiptRef recordPayment(long invoiceId, BigDecimal amount, String method, String reference) throws SQLException {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new SQLException("Payment amount must be greater than zero.");
        }
        String safeMethod = normalizePaymentMethod(method);
        if ("ACCOUNT".equals(safeMethod)) {
            chargeInvoiceToAccount(invoiceId, "Placed on customer account.");
            return null;
        }
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                configureTransactionTimeouts(conn);
                QuotationInvoiceSchemaInstaller.ensureSchema(conn);
                InvoiceHeader invoice = lockInvoice(conn, invoiceId);
                if ("CANCELLED".equals(invoice.status())) {
                    throw new SQLException("Cancelled invoices cannot be paid.");
                }
                if (invoice.balanceDue().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new SQLException("This invoice has no remaining balance to pay.");
                }
                boolean accountCharged = hasAccountCharge(conn, invoiceId);
                if (!accountCharged) {
                    updateCustomerBalance(conn, invoice.customerId(), invoice.balanceDue());
                    long chargeTransactionId = insertAccountCharge(conn, invoice, "Placed on customer account before direct invoice payment.");
                    insertAccountAllocation(conn, chargeTransactionId, invoice, invoice.balanceDue());
                    markInvoicePaymentMethod(conn, invoiceId, "ACCOUNT", null);
                    accountCharged = true;
                }
                BigDecimal applied = amount.min(invoice.balanceDue());
                CashDrawerContext drawer = cashDrawerForPayment(conn, safeMethod);
                insertPayment(conn, invoice, applied, safeMethod, reference, drawer);
                updateInvoicePaymentTotals(conn, invoiceId, applied, safeMethod, reference);
                if (accountCharged) {
                    reduceCustomerBalance(conn, invoice.customerId(), applied);
                    long paymentTransactionId = insertCustomerPaymentTransaction(conn, invoice, applied.negate(), safeMethod, reference, drawer);
                    insertAccountAllocation(conn, paymentTransactionId, invoice, applied);
                    PaymentReceiptRef paymentReceipt = new PaymentReceiptRef(invoice.customerId(), paymentTransactionId);
                    QuotationInvoiceAuditService.recordInvoiceAudit(conn, invoiceId, "PAYMENT_CREATED", "amount_paid", invoice.amountPaid(), invoice.amountPaid().add(applied), safeMethod);
                    SyncOutboxService.recordEvent(conn, "INVOICE_PAYMENT_CREATED", Map.of("invoice_id", invoiceId, "payment_amount", applied));
                    conn.commit();
                    return paymentReceipt;
                }
                conn.commit();
                return null;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static void chargeInvoiceToAccount(long invoiceId, String reason) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                configureTransactionTimeouts(conn);
                QuotationInvoiceSchemaInstaller.ensureSchema(conn);
                InvoiceHeader invoice = lockInvoice(conn, invoiceId);
                if (hasAccountCharge(conn, invoiceId)) {
                    throw new SQLException("This invoice is already on the customer account.");
                }
                if (invoice.balanceDue().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new SQLException("This invoice has no remaining balance to place on account.");
                }
                updateCustomerBalance(conn, invoice.customerId(), invoice.balanceDue());
                long transactionId = insertAccountCharge(conn, invoice, reason);
                insertAccountAllocation(conn, transactionId, invoice, invoice.balanceDue());
                markInvoicePaymentMethod(conn, invoiceId, "ACCOUNT", null);
                QuotationInvoiceAuditService.recordInvoiceAudit(conn, invoiceId, "ACCOUNT_CHARGE_CREATED", "balance_due", null, invoice.balanceDue(), reason);
                SyncOutboxService.recordEvent(conn, "INVOICE_ACCOUNT_CHARGE_CREATED", Map.of("invoice_id", invoiceId, "amount", invoice.balanceDue()));
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static DeliveryResult postDelivery(long invoiceId, String deliveryMethod, String receiverName,
                                              String notes, List<DeliveryLineInput> lines) throws SQLException {
        if (lines == null || lines.isEmpty()) {
            throw new SQLException("Select at least one line quantity to deliver.");
        }
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                configureTransactionTimeouts(conn);
                QuotationInvoiceSchemaInstaller.ensureSchema(conn);
                InvoiceHeader invoice = lockInvoice(conn, invoiceId);
                if ("CANCELLED".equals(invoice.status()) || "DELIVERED".equals(invoice.status())) {
                    throw new SQLException("This invoice cannot receive another delivery.");
                }
                int locationId = invoice.locationId() == null ? requireLocationId() : invoice.locationId();
                String deliveryNumber = QuotationInvoiceNumberManager.nextDeliveryNumber(conn, locationId);
                long eventId = insertDeliveryEvent(conn, invoice, deliveryNumber, normalizeDeliveryMethod(deliveryMethod), receiverName, notes);
                for (DeliveryLineInput line : lines) {
                    postDeliveryLine(conn, invoice, eventId, line);
                }
                String oldStatus = invoice.status();
                String newStatus = recomputeDeliveryStatus(conn, invoiceId);
                updateInvoiceStatus(conn, invoiceId, newStatus);
                QuotationInvoiceAuditService.recordInvoiceStatus(conn, invoiceId, oldStatus, newStatus, "Delivery posted: " + deliveryNumber + ".");
                QuotationInvoiceAuditService.recordInvoiceAudit(conn, invoiceId, "DELIVERY_POSTED", "delivery_number", null, deliveryNumber, notes);
                SyncOutboxService.recordEvent(conn, "INVOICE_DELIVERY_POSTED", Map.of("invoice_id", invoiceId, "delivery_number", deliveryNumber));
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

    /* Connection-bound variants are used by the LAN API so the domain write,
       audit/outbox rows, and idempotency result commit atomically. */
    public static QuotationResult createQuotation(Connection conn,int customerId,LocalDate validUntil,String notes,
                                                   List<QuotationLineInput> lines)throws SQLException{
        if(lines==null||lines.isEmpty())throw new SQLException("Add at least one quotation line.");
        configureTransactionTimeouts(conn);QuotationInvoiceSchemaInstaller.ensureSchema(conn);int locationId=requireLocationId();
        CustomerInfo customer=loadCustomer(conn,customerId);LocalDate safe=validUntil==null?LocalDate.now().plusDays(QuotationInvoiceNumberManager.defaultQuotationValidityDays(conn,locationId)):validUntil;
        String number=QuotationInvoiceNumberManager.nextQuotationNumber(conn,locationId);Totals totals=totals(conn,locationId,lines);
        long id=insertQuotation(conn,number,customer,safe,notes,totals,locationId);int sort=0;for(QuotationLineInput line:lines)insertQuotationLine(conn,id,line,locationId,sort++);
        QuotationInvoiceAuditService.recordQuotationStatus(conn,id,null,"DRAFT","Quotation created.");
        QuotationInvoiceAuditService.recordQuotationAudit(conn,id,"QUOTE_CREATED",null,null,number,notes);
        SyncOutboxService.recordEvent(conn,"QUOTATION_CREATED",Map.of("quotation_id",id,"quotation_number",number));return new QuotationResult(id,number);
    }

    public static QuotationResult updateDraftQuotation(Connection conn,long quotationId,int customerId,LocalDate validUntil,
                                                        String notes,List<QuotationLineInput> lines)throws SQLException{
        if(lines==null||lines.isEmpty())throw new SQLException("Add at least one quotation line.");
        configureTransactionTimeouts(conn);QuotationInvoiceSchemaInstaller.ensureSchema(conn);QuotationHeader quotation=lockQuotation(conn,quotationId);
        if(!"DRAFT".equals(quotation.status()))throw new SQLException("Only draft quotations can be edited.");
        int locationId=quotation.locationId()==null?requireLocationId():quotation.locationId();CustomerInfo customer=loadCustomer(conn,customerId);
        LocalDate safe=validUntil==null?quotation.validUntil():validUntil;if(safe==null)safe=LocalDate.now().plusDays(QuotationInvoiceNumberManager.defaultQuotationValidityDays(conn,locationId));
        Totals totals=totals(conn,locationId,lines);updateQuotationHeader(conn,quotationId,customer,safe,notes,totals);replaceQuotationLines(conn,quotationId,lines,locationId);
        QuotationInvoiceAuditService.recordQuotationAudit(conn,quotationId,"QUOTE_UPDATED",null,null,quotation.quotationNumber(),notes);
        SyncOutboxService.recordEvent(conn,"QUOTATION_UPDATED",Map.of("quotation_id",quotationId,"quotation_number",quotation.quotationNumber()));return new QuotationResult(quotationId,quotation.quotationNumber());
    }

    public static void issueQuotation(Connection conn,long quotationId)throws SQLException{updateQuotationStatus(conn,quotationId,"ISSUED","Quotation issued to customer.");}
    public static void cancelQuotation(Connection conn,long quotationId,String reason)throws SQLException{updateQuotationStatus(conn,quotationId,"CANCELLED",blankToNull(reason)==null?"Quotation cancelled.":reason);}

    public static InvoiceResult acceptQuotation(Connection conn,long quotationId)throws SQLException{
        configureTransactionTimeouts(conn);QuotationInvoiceSchemaInstaller.ensureSchema(conn);QuotationHeader quotation=lockQuotation(conn,quotationId);
        if(!"DRAFT".equals(quotation.status())&&!"ISSUED".equals(quotation.status()))throw new SQLException("Only draft or issued quotations can be accepted.");
        if(quotation.validUntil()!=null&&quotation.validUntil().isBefore(LocalDate.now()))throw new SQLException("This quotation expired on "+quotation.validUntil()+".");
        int locationId=quotation.locationId()==null?requireLocationId():quotation.locationId();String number=QuotationInvoiceNumberManager.nextInvoiceNumber(conn,locationId);
        long invoiceId=insertInvoiceFromQuotation(conn,quotation,number);copyQuotationLinesToInvoice(conn,quotationId,invoiceId);updateQuotationAccepted(conn,quotationId);
        QuotationInvoiceAuditService.recordQuotationStatus(conn,quotationId,quotation.status(),"ACCEPTED","Quotation accepted and converted to invoice "+number+".");
        QuotationInvoiceAuditService.recordInvoiceStatus(conn,invoiceId,null,"OPEN","Sales invoice created from quotation "+quotation.quotationNumber()+".");
        QuotationInvoiceAuditService.recordInvoiceAudit(conn,invoiceId,"ORDER_CREATED_FROM_QUOTE","quotation_number",null,quotation.quotationNumber(),null);
        SyncOutboxService.recordEvent(conn,"QUOTATION_ACCEPTED",Map.of("quotation_id",quotationId,"invoice_id",invoiceId,"invoice_number",number));return new InvoiceResult(invoiceId,number);
    }

    public static PaymentReceiptRef recordPayment(Connection conn,long invoiceId,BigDecimal amount,String method,String reference)throws SQLException{
        if(amount==null||amount.signum()<=0)throw new SQLException("Payment amount must be greater than zero.");String safeMethod=normalizePaymentMethod(method);
        if("ACCOUNT".equals(safeMethod)){chargeInvoiceToAccount(conn,invoiceId,"Placed on customer account.");return null;}
        configureTransactionTimeouts(conn);QuotationInvoiceSchemaInstaller.ensureSchema(conn);InvoiceHeader invoice=lockInvoice(conn,invoiceId);
        if("CANCELLED".equals(invoice.status()))throw new SQLException("Cancelled invoices cannot be paid.");if(invoice.balanceDue().signum()<=0)throw new SQLException("This invoice has no remaining balance to pay.");
        boolean accountCharged=hasAccountCharge(conn,invoiceId);if(!accountCharged){updateCustomerBalance(conn,invoice.customerId(),invoice.balanceDue());long charge=insertAccountCharge(conn,invoice,"Placed on customer account before direct invoice payment.");insertAccountAllocation(conn,charge,invoice,invoice.balanceDue());markInvoicePaymentMethod(conn,invoiceId,"ACCOUNT",null);accountCharged=true;}
        BigDecimal applied=amount.min(invoice.balanceDue());CashDrawerContext drawer=cashDrawerForPayment(conn,safeMethod);insertPayment(conn,invoice,applied,safeMethod,reference,drawer);updateInvoicePaymentTotals(conn,invoiceId,applied,safeMethod,reference);
        if(accountCharged){reduceCustomerBalance(conn,invoice.customerId(),applied);long payment=insertCustomerPaymentTransaction(conn,invoice,applied.negate(),safeMethod,reference,drawer);insertAccountAllocation(conn,payment,invoice,applied);
            QuotationInvoiceAuditService.recordInvoiceAudit(conn,invoiceId,"PAYMENT_CREATED","amount_paid",invoice.amountPaid(),invoice.amountPaid().add(applied),safeMethod);SyncOutboxService.recordEvent(conn,"INVOICE_PAYMENT_CREATED",Map.of("invoice_id",invoiceId,"payment_amount",applied));return new PaymentReceiptRef(invoice.customerId(),payment);}return null;
    }

    public static void chargeInvoiceToAccount(Connection conn,long invoiceId,String reason)throws SQLException{
        configureTransactionTimeouts(conn);QuotationInvoiceSchemaInstaller.ensureSchema(conn);InvoiceHeader invoice=lockInvoice(conn,invoiceId);
        if(hasAccountCharge(conn,invoiceId))throw new SQLException("This invoice is already on the customer account.");if(invoice.balanceDue().signum()<=0)throw new SQLException("This invoice has no remaining balance to place on account.");
        updateCustomerBalance(conn,invoice.customerId(),invoice.balanceDue());long transaction=insertAccountCharge(conn,invoice,reason);insertAccountAllocation(conn,transaction,invoice,invoice.balanceDue());markInvoicePaymentMethod(conn,invoiceId,"ACCOUNT",null);
        QuotationInvoiceAuditService.recordInvoiceAudit(conn,invoiceId,"ACCOUNT_CHARGE_CREATED","balance_due",null,invoice.balanceDue(),reason);SyncOutboxService.recordEvent(conn,"INVOICE_ACCOUNT_CHARGE_CREATED",Map.of("invoice_id",invoiceId,"amount",invoice.balanceDue()));
    }

    public static DeliveryResult postDelivery(Connection conn,long invoiceId,String deliveryMethod,String receiverName,
                                              String notes,List<DeliveryLineInput> lines)throws SQLException{
        if(lines==null||lines.isEmpty())throw new SQLException("Select at least one line quantity to deliver.");configureTransactionTimeouts(conn);QuotationInvoiceSchemaInstaller.ensureSchema(conn);InvoiceHeader invoice=lockInvoice(conn,invoiceId);
        if("CANCELLED".equals(invoice.status())||"DELIVERED".equals(invoice.status()))throw new SQLException("This invoice cannot receive another delivery.");int locationId=invoice.locationId()==null?requireLocationId():invoice.locationId();
        String number=QuotationInvoiceNumberManager.nextDeliveryNumber(conn,locationId);long event=insertDeliveryEvent(conn,invoice,number,normalizeDeliveryMethod(deliveryMethod),receiverName,notes);for(DeliveryLineInput line:lines)postDeliveryLine(conn,invoice,event,line);
        String old=invoice.status(),updated=recomputeDeliveryStatus(conn,invoiceId);updateInvoiceStatus(conn,invoiceId,updated);QuotationInvoiceAuditService.recordInvoiceStatus(conn,invoiceId,old,updated,"Delivery posted: "+number+".");
        QuotationInvoiceAuditService.recordInvoiceAudit(conn,invoiceId,"DELIVERY_POSTED","delivery_number",null,number,notes);SyncOutboxService.recordEvent(conn,"INVOICE_DELIVERY_POSTED",Map.of("invoice_id",invoiceId,"delivery_number",number));return new DeliveryResult(event,number);
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

    private static void configureTransactionTimeouts(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET LOCAL lock_timeout = '5s'");
            stmt.execute("SET LOCAL statement_timeout = '20s'");
            stmt.execute("SET LOCAL idle_in_transaction_session_timeout = '60s'");
        }
    }

    private static void updateQuotationStatus(long quotationId, String newStatus, String reason) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                configureTransactionTimeouts(conn);
                QuotationInvoiceSchemaInstaller.ensureSchema(conn);
                QuotationHeader quotation = lockQuotation(conn, quotationId);
                try (PreparedStatement ps = conn.prepareStatement("UPDATE quotations SET status = ? WHERE quotation_id = ?")) {
                    ps.setString(1, newStatus);
                    ps.setLong(2, quotationId);
                    ps.executeUpdate();
                }
                QuotationInvoiceAuditService.recordQuotationStatus(conn, quotationId, quotation.status(), newStatus, reason);
                SyncOutboxService.recordEvent(conn, "QUOTATION_STATUS_CHANGED", Map.of("quotation_id", quotationId, "status", newStatus));
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private static void updateQuotationStatus(Connection conn,long quotationId,String newStatus,String reason)throws SQLException{
        configureTransactionTimeouts(conn);QuotationInvoiceSchemaInstaller.ensureSchema(conn);QuotationHeader quotation=lockQuotation(conn,quotationId);
        try(PreparedStatement ps=conn.prepareStatement("UPDATE quotations SET status = ? WHERE quotation_id = ?")){ps.setString(1,newStatus);ps.setLong(2,quotationId);ps.executeUpdate();}
        QuotationInvoiceAuditService.recordQuotationStatus(conn,quotationId,quotation.status(),newStatus,reason);
        SyncOutboxService.recordEvent(conn,"QUOTATION_STATUS_CHANGED",Map.of("quotation_id",quotationId,"status",newStatus));
    }

    private static long insertQuotation(Connection conn, String quotationNumber, CustomerInfo customer, LocalDate validUntil,
                                    String notes, Totals totals, int locationId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO quotations (
                    quotation_number, customer_id, customer_name, customer_phone, customer_email,
                    valid_until, quotation_notes, subtotal_amount, discount_amount, vat_amount, vat_rate_percent, vat_mode, total_amount,
                    location_id, location_name, device_id, device_name, created_by_user_id, created_by_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING quotation_id
                """)) {
            ps.setString(1, quotationNumber);
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
            ps.setString(15, ServerRequestIdentity.locationName());
            ps.setString(16, blankToNull(currentDocumentDeviceId()));
            ps.setString(17, blankToNull(currentDocumentDeviceName()));
            setNullableInteger(ps, 18, ServerRequestIdentity.userId());
            ps.setString(19, ServerRequestIdentity.userName());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("quotation_id");
                }
            }
        }
        throw new SQLException("Quotation insert did not return an id.");
    }

    private static void insertQuotationLine(Connection conn, long quotationId, QuotationLineInput line, int locationId, int sortInvoice) throws SQLException {
        SalesVatSettings settings = loadSalesVatSettings(conn, locationId);
        LineAmounts amounts = lineAmounts(conn, settings, line);
        ProductTaxInfo taxInfo = productTaxInfo(conn, line.productId());
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO quotation_lines (
                    quotation_id, product_id, item_name, sku, quantity, unit_price, original_unit_price, category_id,
                    price_override_reason, price_override_by_user_id, price_override_by_name,
                    discount_percent, discount_amount, vat_rate_percent, vat_amount, line_total, delivery_method, line_notes, sort_order
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setLong(1, quotationId);
            setNullableInteger(ps, 2, line.productId());
            ps.setString(3, line.itemName());
            ps.setString(4, blankToNull(line.sku()));
            ps.setInt(5, line.quantity());
            ps.setBigDecimal(6, money(line.unitPrice()));
            ps.setBigDecimal(7, money(line.originalUnitPrice() == null ? line.unitPrice() : line.originalUnitPrice()));
            setNullableInteger(ps, 8, taxInfo.categoryId());
            ps.setString(9, blankToNull(line.priceOverrideReason()));
            setNullableInteger(ps, 10, line.priceOverrideByUserId());
            ps.setString(11, blankToNull(line.priceOverrideByName()));
            ps.setBigDecimal(12, percent(line.discountPercent()));
            ps.setBigDecimal(13, amounts.discount());
            ps.setBigDecimal(14, amounts.vatRate());
            ps.setBigDecimal(15, amounts.vatAmount());
            ps.setBigDecimal(16, amounts.preVatTotal());
            ps.setString(17, normalizeDeliveryMethod(line.deliveryMethod()));
            ps.setString(18, blankToNull(line.notes()));
            ps.setInt(19, sortInvoice);
            ps.executeUpdate();
        }
        if (line.priceOverrideReason() != null && !line.priceOverrideReason().isBlank()) {
            QuotationInvoiceAuditService.recordQuotationAudit(
                    conn,
                    quotationId,
                    "PRICE_OVERRIDE_APPROVED",
                    "unit_price",
                    line.originalUnitPrice(),
                    line.unitPrice(),
                    "Approved by " + blankToEmpty(line.priceOverrideByName()) + ": " + line.priceOverrideReason()
            );
        }
    }

    private static void updateQuotationHeader(Connection conn, long quotationId, CustomerInfo customer,
                                          LocalDate validUntil, String notes, Totals totals) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE quotations
                SET customer_id = ?,
                    customer_name = ?,
                    customer_phone = ?,
                    customer_email = ?,
                    valid_until = ?,
                    quotation_notes = ?,
                    subtotal_amount = ?,
                    discount_amount = ?,
                    vat_amount = ?,
                    vat_rate_percent = ?,
                    vat_mode = ?,
                    total_amount = ?
                WHERE quotation_id = ?
                """)) {
            ps.setInt(1, customer.customerId());
            ps.setString(2, customer.name());
            ps.setString(3, blankToNull(customer.phone()));
            ps.setString(4, blankToNull(customer.email()));
            ps.setDate(5, Date.valueOf(validUntil));
            ps.setString(6, blankToNull(notes));
            ps.setBigDecimal(7, totals.subtotal());
            ps.setBigDecimal(8, totals.discount());
            ps.setBigDecimal(9, totals.vat());
            ps.setBigDecimal(10, totals.vatRate());
            ps.setString(11, totals.vatMode());
            ps.setBigDecimal(12, totals.total());
            ps.setLong(13, quotationId);
            ps.executeUpdate();
        }
    }

    private static void replaceQuotationLines(Connection conn, long quotationId, List<QuotationLineInput> lines, int locationId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM quotation_lines WHERE quotation_id = ?")) {
            ps.setLong(1, quotationId);
            ps.executeUpdate();
        }
        int sortInvoice = 0;
        for (QuotationLineInput line : lines) {
            insertQuotationLine(conn, quotationId, line, locationId, sortInvoice++);
        }
    }

    private static long insertInvoiceFromQuotation(Connection conn, QuotationHeader quotation, String invoiceNumber) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO invoices (
                    invoice_number, quotation_id, quotation_number, customer_id, customer_name, customer_phone, customer_email,
                    invoice_notes, subtotal_amount, discount_amount, vat_amount, vat_rate_percent, vat_mode, total_amount, balance_due,
                    location_id, location_name, device_id, device_name, created_by_user_id, created_by_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING invoice_id
                """)) {
            ps.setString(1, invoiceNumber);
            ps.setLong(2, quotation.salesQuotationId());
            ps.setString(3, quotation.quotationNumber());
            ps.setInt(4, quotation.customerId());
            ps.setString(5, quotation.customerName());
            ps.setString(6, blankToNull(quotation.customerPhone()));
            ps.setString(7, blankToNull(quotation.customerEmail()));
            ps.setString(8, blankToNull(quotation.quotationNotes()));
            ps.setBigDecimal(9, quotation.subtotalAmount());
            ps.setBigDecimal(10, quotation.discountAmount());
            ps.setBigDecimal(11, quotation.vatAmount());
            ps.setBigDecimal(12, quotation.vatRatePercent());
            ps.setString(13, blankToEmpty(quotation.vatMode()));
            ps.setBigDecimal(14, quotation.totalAmount());
            ps.setBigDecimal(15, quotation.totalAmount());
            setNullableInteger(ps, 16, quotation.locationId());
            ps.setString(17, blankToNull(quotation.locationName()));
            ps.setString(18, blankToNull(currentDocumentDeviceId()));
            ps.setString(19, blankToNull(currentDocumentDeviceName()));
            setNullableInteger(ps, 20, ServerRequestIdentity.userId());
            ps.setString(21, ServerRequestIdentity.userName());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("invoice_id");
                }
            }
        }
        throw new SQLException("Sales invoice insert did not return an id.");
    }

    private static void copyQuotationLinesToInvoice(Connection conn, long quotationId, long invoiceId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO invoice_lines (
                    invoice_id, quotation_line_id, product_id, item_name, sku,
                    quantity_invoiced, unit_price, original_unit_price, category_id, discount_percent,
                    price_override_reason, price_override_by_user_id, price_override_by_name,
                    discount_amount, vat_rate_percent, vat_amount, line_total, delivery_method, line_notes, sort_order
                )
                SELECT ?, quotation_line_id, product_id, item_name, sku,
                       quantity, unit_price, original_unit_price, category_id, discount_percent,
                       price_override_reason, price_override_by_user_id, price_override_by_name,
                       discount_amount, vat_rate_percent, vat_amount, line_total, delivery_method, line_notes, sort_order
                FROM quotation_lines
                WHERE quotation_id = ?
                ORDER BY sort_order, quotation_line_id
                """)) {
            ps.setLong(1, invoiceId);
            ps.setLong(2, quotationId);
            ps.executeUpdate();
        }
    }

    private static void updateQuotationAccepted(Connection conn, long quotationId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE quotations
                SET status = 'ACCEPTED',
                    accepted_at = CURRENT_TIMESTAMP,
                    accepted_by_user_id = ?,
                    accepted_by_name = ?
                WHERE quotation_id = ?
                """)) {
            setNullableInteger(ps, 1, ServerRequestIdentity.userId());
            ps.setString(2, ServerRequestIdentity.userName());
            ps.setLong(3, quotationId);
            ps.executeUpdate();
        }
    }

    private static void insertPayment(Connection conn, InvoiceHeader invoice, BigDecimal amount, String method,
                                      String reference, CashDrawerContext drawer) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO invoice_payments (
                    invoice_id, customer_id, payment_amount, payment_method, payment_reference,
                    taken_by_user_id, taken_by_name, location_id, device_id, device_name,
                    cash_drawer_id, cash_drawer_name, cash_drawer_session_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setLong(1, invoice.salesInvoiceId());
            ps.setInt(2, invoice.customerId());
            ps.setBigDecimal(3, money(amount));
            ps.setString(4, method);
            ps.setString(5, blankToNull(reference));
            setNullableInteger(ps, 6, ServerRequestIdentity.userId());
            ps.setString(7, ServerRequestIdentity.userName());
            setNullableInteger(ps, 8, invoice.locationId());
            ps.setString(9, blankToNull(currentDocumentDeviceId()));
            ps.setString(10, blankToNull(currentDocumentDeviceName()));
            setNullableLong(ps, 11, drawer == null ? null : drawer.cashDrawerId());
            ps.setString(12, drawer == null ? null : drawer.drawerName());
            setNullableLong(ps, 13, drawer == null ? null : drawer.sessionId());
            ps.executeUpdate();
        }
    }

    private static void updateInvoicePaymentTotals(Connection conn, long invoiceId, BigDecimal amount,
                                                 String method, String reference) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE invoices
                SET amount_paid = LEAST(total_amount, amount_paid + ?),
                    balance_due = GREATEST(total_amount - LEAST(total_amount, amount_paid + ?), 0),
                    payment_status = CASE
                        WHEN GREATEST(total_amount - LEAST(total_amount, amount_paid + ?), 0) <= 0 THEN 'PAID'
                        WHEN LEAST(total_amount, amount_paid + ?) > 0 THEN 'PARTIAL'
                        ELSE 'UNPAID'
                    END,
                    payment_method = ?,
                    payment_reference = COALESCE(NULLIF(?, ''), payment_reference)
                WHERE invoice_id = ?
                """)) {
            BigDecimal safe = money(amount);
            ps.setBigDecimal(1, safe);
            ps.setBigDecimal(2, safe);
            ps.setBigDecimal(3, safe);
            ps.setBigDecimal(4, safe);
            ps.setString(5, method);
            ps.setString(6, blankToEmpty(reference));
            ps.setLong(7, invoiceId);
            ps.executeUpdate();
        }
    }

    private static void markInvoicePaymentMethod(Connection conn, long invoiceId, String method, String reference) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE invoices
                SET payment_method = ?,
                    payment_reference = COALESCE(NULLIF(?, ''), payment_reference)
                WHERE invoice_id = ?
                """)) {
            ps.setString(1, method);
            ps.setString(2, blankToEmpty(reference));
            ps.setLong(3, invoiceId);
            ps.executeUpdate();
        }
    }

    private static long insertAccountCharge(Connection conn, InvoiceHeader invoice, String reason) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO customer_account_transactions (
                    customer_id, invoice_id, payment_id, location_id, amount, transaction_type,
                    note, user_name, device_id, device_name, payment_method, payment_reference
                )
                VALUES (?, ?, ?, ?, ?, 'INVOICE_CREDIT', ?, ?, ?, ?, 'ACCOUNT', ?)
                RETURNING transaction_id
                """)) {
            ps.setInt(1, invoice.customerId());
            ps.setLong(2, invoice.salesInvoiceId());
            ps.setString(3, "INV-" + invoice.invoiceNumber());
            setNullableInteger(ps, 4, invoice.locationId());
            ps.setBigDecimal(5, invoice.balanceDue());
            ps.setString(6, blankToNull(reason));
            ps.setString(7, ServerRequestIdentity.userName());
            ps.setString(8, blankToNull(currentDocumentDeviceId()));
            ps.setString(9, blankToNull(currentDocumentDeviceName()));
            ps.setString(10, invoice.invoiceNumber());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("transaction_id");
                }
            }
        }
        throw new SQLException("Customer account charge did not return a transaction id.");
    }

    private static long insertCustomerPaymentTransaction(Connection conn, InvoiceHeader invoice, BigDecimal amount,
                                                         String method, String reference, CashDrawerContext drawer) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO customer_account_transactions (
                    customer_id, invoice_id, payment_id, location_id, amount, transaction_type,
                    note, user_name, device_id, device_name, payment_method, payment_reference,
                    cash_drawer_id, cash_drawer_name, cash_drawer_session_id
                )
                VALUES (?, ?, ?, ?, ?, 'PAYMENT', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING transaction_id
                """)) {
            ps.setInt(1, invoice.customerId());
            ps.setLong(2, invoice.salesInvoiceId());
            ps.setString(3, "SOP-" + invoice.invoiceNumber() + "-" + System.currentTimeMillis());
            setNullableInteger(ps, 4, invoice.locationId());
            ps.setBigDecimal(5, amount);
            ps.setString(6, "Payment applied to invoice " + invoice.invoiceNumber());
            ps.setString(7, ServerRequestIdentity.userName());
            ps.setString(8, blankToNull(currentDocumentDeviceId()));
            ps.setString(9, blankToNull(currentDocumentDeviceName()));
            ps.setString(10, method);
            ps.setString(11, blankToNull(reference));
            setNullableLong(ps, 12, drawer == null ? null : drawer.cashDrawerId());
            ps.setString(13, drawer == null ? null : drawer.drawerName());
            setNullableLong(ps, 14, drawer == null ? null : drawer.sessionId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("transaction_id");
                }
            }
        }
        throw new SQLException("Customer account payment did not return a transaction id.");
    }

    private static void insertAccountAllocation(Connection conn, long transactionId, InvoiceHeader invoice, BigDecimal amount) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO customer_account_payment_allocations (payment_transaction_id, customer_id, invoice_id, amount)
                VALUES (?, ?, ?, ?)
                """)) {
            ps.setLong(1, transactionId);
            ps.setInt(2, invoice.customerId());
            ps.setLong(3, invoice.salesInvoiceId());
            ps.setBigDecimal(4, amount);
            ps.executeUpdate();
        }
    }

    private static long insertDeliveryEvent(Connection conn, InvoiceHeader invoice, String deliveryNumber,
                                            String method, String receiverName, String notes) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO invoice_delivery_events (
                    invoice_id, delivery_number, delivery_method, receiver_name, delivery_notes,
                    remaining_balance, delivered_by_user_id, delivered_by_name, location_id, device_id, device_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING invoice_delivery_event_id
                """)) {
            ps.setLong(1, invoice.salesInvoiceId());
            ps.setString(2, deliveryNumber);
            ps.setString(3, method);
            ps.setString(4, blankToNull(receiverName));
            ps.setString(5, blankToNull(notes));
            ps.setBigDecimal(6, invoice.balanceDue());
            setNullableInteger(ps, 7, ServerRequestIdentity.userId());
            ps.setString(8, ServerRequestIdentity.userName());
            setNullableInteger(ps, 9, invoice.locationId());
            ps.setString(10, blankToNull(currentDocumentDeviceId()));
            ps.setString(11, blankToNull(currentDocumentDeviceName()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("invoice_delivery_event_id");
                }
            }
        }
        throw new SQLException("Delivery event insert did not return an id.");
    }

    private static void postDeliveryLine(Connection conn, InvoiceHeader invoice, long eventId, DeliveryLineInput input) throws SQLException {
        InvoiceLine line = lockInvoiceLine(conn, input.salesInvoiceLineId());
        if (line.salesInvoiceId() != invoice.salesInvoiceId()) {
            throw new SQLException("Delivery line does not belong to this invoice.");
        }
        int remaining = line.quantityInvoiceed() - line.quantityDelivered();
        if (input.quantityDelivered() <= 0 || input.quantityDelivered() > remaining) {
            throw new SQLException("Delivery quantity for " + line.itemName() + " must be between 1 and " + remaining + ".");
        }
        if (line.productId() != null) {
            int available = availableStock(conn, line.productId(), invoice.locationId());
            if (available < input.quantityDelivered()) {
                throw new SQLException("Only " + available + " in stock for " + line.itemName() + "; cannot deliver " + input.quantityDelivered() + ".");
            }
            deductInventory(conn, invoice, line, input.quantityDelivered(), eventId);
        }
        int newDelivered = line.quantityDelivered() + input.quantityDelivered();
        int newRemaining = line.quantityInvoiceed() - newDelivered;
        String lineStatus = newRemaining <= 0 ? "DELIVERED" : "PARTIAL";
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE invoice_lines
                SET quantity_delivered = ?,
                    delivery_status = ?
                WHERE invoice_line_id = ?
                """)) {
            ps.setInt(1, newDelivered);
            ps.setString(2, lineStatus);
            ps.setLong(3, line.salesInvoiceLineId());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO invoice_delivery_lines (
                    invoice_delivery_event_id, invoice_id, invoice_line_id,
                    product_id, item_name, quantity_delivered, quantity_remaining
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setLong(1, eventId);
            ps.setLong(2, invoice.salesInvoiceId());
            ps.setLong(3, line.salesInvoiceLineId());
            setNullableInteger(ps, 4, line.productId());
            ps.setString(5, line.itemName());
            ps.setInt(6, input.quantityDelivered());
            ps.setInt(7, newRemaining);
            ps.executeUpdate();
        }
    }

    private static void deductInventory(Connection conn, InvoiceHeader invoice, InvoiceLine line, int quantity, long deliveryEventId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE inventory
                SET quantity_on_hand = quantity_on_hand - ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE product_id = ? AND location_id = ?
                """)) {
            ps.setInt(1, quantity);
            ps.setInt(2, line.productId());
            ps.setInt(3, invoice.locationId());
            if (ps.executeUpdate() == 0) {
                throw new SQLException("No inventory row exists for " + line.itemName() + " at this location.");
            }
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO inventory_movements (
                    product_id, location_id, change_qty, reason, note, user_name,
                    invoice_id, invoice_line_id, invoice_delivery_event_id,
                    device_id, device_name, user_id
                )
                VALUES (?, ?, ?, 'INVOICE_DELIVERY', ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setInt(1, line.productId());
            ps.setInt(2, invoice.locationId());
            ps.setInt(3, -quantity);
            ps.setString(4, "Delivered for invoice " + invoice.invoiceNumber());
            ps.setString(5, ServerRequestIdentity.userName());
            ps.setLong(6, invoice.salesInvoiceId());
            ps.setLong(7, line.salesInvoiceLineId());
            ps.setLong(8, deliveryEventId);
            ps.setString(9, blankToNull(currentDocumentDeviceId()));
            ps.setString(10, blankToNull(currentDocumentDeviceName()));
            setNullableInteger(ps, 11, ServerRequestIdentity.userId());
            ps.executeUpdate();
        }
    }

    private static String recomputeDeliveryStatus(Connection conn, long invoiceId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT
                    SUM(quantity_delivered) AS delivered,
                    SUM(quantity_invoiced) AS invoiceed
                FROM invoice_lines
                WHERE invoice_id = ?
                """)) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int delivered = rs.getInt("delivered");
                    int invoiceed = rs.getInt("invoiceed");
                    if (invoiceed > 0 && delivered >= invoiceed) {
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

    private static void updateInvoiceStatus(Connection conn, long invoiceId, String status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE invoices
                SET status = ?,
                    delivered_at = CASE WHEN ? = 'DELIVERED' THEN CURRENT_TIMESTAMP ELSE delivered_at END
                WHERE invoice_id = ?
                """)) {
            ps.setString(1, status);
            ps.setString(2, status);
            ps.setLong(3, invoiceId);
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

    private static QuotationHeader lockQuotation(Connection conn, long quotationId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT *
                FROM quotations
                WHERE quotation_id = ?
                FOR UPDATE
                """)) {
            ps.setLong(1, quotationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Date validUntil = rs.getDate("valid_until");
                    return new QuotationHeader(
                            rs.getLong("quotation_id"),
                            rs.getString("quotation_number"),
                            rs.getInt("customer_id"),
                            rs.getString("customer_name"),
                            rs.getString("customer_phone"),
                            rs.getString("customer_email"),
                            rs.getString("status"),
                            validUntil == null ? null : validUntil.toLocalDate(),
                            rs.getString("quotation_notes"),
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
        throw new SQLException("Sales quotation not found.");
    }

    private static InvoiceHeader lockInvoice(Connection conn, long invoiceId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT *
                FROM invoices
                WHERE invoice_id = ?
                FOR UPDATE
                """)) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new InvoiceHeader(
                            rs.getLong("invoice_id"),
                            rs.getString("invoice_number"),
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
        throw new SQLException("Sales invoice not found.");
    }

    private static InvoiceLine lockInvoiceLine(Connection conn, long lineId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT invoice_line_id, invoice_id, product_id, item_name,
                       quantity_invoiced, quantity_delivered
                FROM invoice_lines
                WHERE invoice_line_id = ?
                FOR UPDATE
                """)) {
            ps.setLong(1, lineId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new InvoiceLine(
                            rs.getLong("invoice_line_id"),
                            rs.getLong("invoice_id"),
                            nullableInt(rs, "product_id"),
                            rs.getString("item_name"),
                            rs.getInt("quantity_invoiced"),
                            rs.getInt("quantity_delivered")
                    );
                }
            }
        }
        throw new SQLException("Sales invoice line not found.");
    }

    private static boolean hasAccountCharge(Connection conn, long invoiceId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT 1
                FROM customer_account_transactions
                WHERE invoice_id = ?
                  AND transaction_type = 'INVOICE_CREDIT'
                LIMIT 1
                """)) {
            ps.setLong(1, invoiceId);
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

    private static Totals totals(Connection conn, int locationId, List<QuotationLineInput> lines) throws SQLException {
        SalesVatSettings settings = loadSalesVatSettings(conn, locationId);
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal vat = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (QuotationLineInput line : lines) {
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
        return new Totals(money(subtotal), money(discount), vatAmount, effectiveRate, mode, money(preVatTotal.add(vatAmount)));
    }

    private static LineAmounts lineAmounts(Connection conn, SalesVatSettings settings, QuotationLineInput line) throws SQLException {
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

    private static SalesVatSettings loadSalesVatSettings(Connection conn, int locationId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COALESCE(vat_enabled, FALSE) AS vat_enabled,
                       COALESCE(vat_use_department_rates, FALSE) AS vat_use_department_rates,
                       COALESCE(vat_fixed_rate_percent, 0) AS vat_fixed_rate_percent
                FROM company_customization
                WHERE location_id = ?
                """)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new SalesVatSettings(
                            rs.getBoolean("vat_enabled"),
                            rs.getBoolean("vat_use_department_rates"),
                            zero(rs.getBigDecimal("vat_fixed_rate_percent"))
                    );
                }
            }
        }
        return new SalesVatSettings(false, false, BigDecimal.ZERO);
    }

    private static String currentDocumentDeviceId() {
        return DeviceContextService.currentDeviceId();
    }

    private static String currentDocumentDeviceName() {
        return DeviceContextService.currentDeviceName();
    }

    private static int requireLocationId() throws SQLException {
        Integer locationId = ServerRequestIdentity.locationId();
        if (locationId != null) {
            return locationId;
        }
        locationId = DatabaseConfig.load().locationId();
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
        return utils.CurrencyFormatter.normalize(value);
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

    public record QuotationLineInput(Integer productId, String itemName, String sku, int quantity,
                                 BigDecimal unitPrice, BigDecimal originalUnitPrice,
                                 BigDecimal discountPercent, String deliveryMethod, String notes,
                                 String priceOverrideReason, Integer priceOverrideByUserId,
                                 String priceOverrideByName, String priceOverrideApprovalToken) {
    }

    public record DeliveryLineInput(long salesInvoiceLineId, int quantityDelivered) {
    }

    public record QuotationResult(long quotationId, String quotationNumber) {
    }

    public record InvoiceResult(long invoiceId, String invoiceNumber) {
    }

    public record DeliveryResult(long deliveryEventId, String deliveryNumber) {
    }

    public record PaymentReceiptRef(int customerId, long transactionId) {
    }

    private record CustomerInfo(int customerId, String name, String phone, String email) {
    }

    private record QuotationHeader(long salesQuotationId, String quotationNumber, int customerId, String customerName,
                               String customerPhone, String customerEmail, String status, LocalDate validUntil,
                               String quotationNotes, BigDecimal subtotalAmount, BigDecimal discountAmount,
                               BigDecimal vatAmount, BigDecimal vatRatePercent, String vatMode,
                               BigDecimal totalAmount, Integer locationId, String locationName) {
    }

    private record InvoiceHeader(long salesInvoiceId, String invoiceNumber, int customerId, String status,
                               BigDecimal totalAmount, BigDecimal amountPaid, BigDecimal balanceDue,
                               Integer locationId) {
    }

    private record InvoiceLine(long salesInvoiceLineId, long salesInvoiceId, Integer productId, String itemName,
                             int quantityInvoiceed, int quantityDelivered) {
    }

    private record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal vat,
                          BigDecimal vatRate, String vatMode, BigDecimal total) {
    }

    private record LineAmounts(BigDecimal discount, BigDecimal preVatTotal, BigDecimal vatRate, BigDecimal vatAmount) {
    }

    private record ProductTaxInfo(Integer categoryId, BigDecimal vatRatePercent) {
    }

    private record SalesVatSettings(boolean vatEnabled, boolean vatUseDepartmentRates, BigDecimal vatFixedRatePercent) {
    }
}
