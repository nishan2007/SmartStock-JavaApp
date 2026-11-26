package services;

import Receipt.ServerAccountPaymentReceiptBuilder;
import Receipt.AccountPaymentReceiptData;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import models.CashDrawerContext;

/** Server-only customer-account reads and profile mutations. */
final class LanCustomerAccountService {
    private static final Gson GSON=new Gson();
    private LanCustomerAccountService(){}

    static List<Map<String,Object>> list(Connection c,int userId)throws Exception{
        return list(c, userId, null);
    }

    static List<Map<String,Object>> list(Connection c, int userId, Integer location)throws Exception{
        require(c,userId,"CUSTOMER_ACCOUNTS");CustomerAccountLedgerService.repairAllBalances(c);
        List<Map<String,Object>>rows=new ArrayList<>();
        try(PreparedStatement ps=c.prepareStatement("""
                SELECT ca.customer_id,COALESCE(ca.account_number,''),
                  COALESCE((SELECT 'Deckers ' || l.name || ' Expense' FROM locations l
                            WHERE l.location_id=ca.internal_expense_location_id),ca.name),COALESCE(ca.phone,''),
                  COALESCE(ca.email,''),COALESCE(ca.credit_limit,0),COALESCE(ca.current_balance,0),
                  COALESCE(ca.credit_limit,0)-GREATEST(COALESCE(ca.current_balance,0)-(COALESCE((SELECT SUM(pm.amount-pm.amount_paid) FROM phone_minutes_sales pm WHERE pm.customer_id=ca.customer_id AND pm.payment_method='CREDIT' AND pm.reversed_at IS NULL),0)+COALESCE((SELECT SUM(mr.document_balance) FROM sync_cross_store_customer_history_cache mr WHERE mr.customer_id=ca.customer_id AND mr.event_type='MINUTES' AND mr.cache_status='CURRENT'),0)),0),
                  COALESCE((SELECT SUM(balance_due) FROM custom_orders o WHERE o.customer_id=ca.customer_id AND COALESCE(o.payment_status,'UNPAID')<>'PAID'),0)+COALESCE((SELECT SUM(document_balance) FROM sync_cross_store_customer_history_cache r WHERE r.customer_id=ca.customer_id AND r.event_type='CUSTOM_ORDER' AND r.cache_status='CURRENT'),0),
                  COALESCE(ca.current_balance,0)+COALESCE((SELECT SUM(balance_due) FROM custom_orders o WHERE o.customer_id=ca.customer_id AND COALESCE(o.payment_method,'')<>'ACCOUNT' AND COALESCE(o.payment_status,'UNPAID')<>'PAID'),0)+COALESCE((SELECT SUM(document_balance) FROM sync_cross_store_customer_history_cache r WHERE r.customer_id=ca.customer_id AND r.event_type='CUSTOM_ORDER' AND COALESCE(r.payment_method,'')<>'ACCOUNT' AND r.cache_status='CURRENT'),0),COALESCE(LOWER(BTRIM(ct.name)) IN ('business','businesses'),FALSE),
                  COALESCE(ca.is_active,TRUE),COALESCE(ca.account_notes,''),ca.customer_type_id,COALESCE(ct.name,''),
                  COALESCE(ct.customer_card_template_slot,4),ca.customer_since,COALESCE(ca.customer_photo_url,''),ca.customer_card_issued_on,ca.customer_card_expires_on,
                  COALESCE(ca.whatsapp_opt_in,FALSE),COALESCE(ca.whatsapp_consent_phone,''),COALESCE(ca.require_charge_authorization,FALSE),
                  COALESCE(ca.sales_discount_enabled,FALSE),COALESCE(ca.sales_discount_percent,0),
                  COALESCE(ca.invoice_discount_enabled,FALSE),COALESCE(ca.invoice_discount_percent,0),
                  COALESCE(ca.custom_order_discount_enabled,FALSE),COALESCE(ca.custom_order_discount_percent,0),ca.custom_order_deposit_exempt,COALESCE(ca.additional_phone_1,''),COALESCE(ca.additional_phone_2,''),COALESCE(ca.phone_label,''),COALESCE(ca.additional_phone_1_label,''),COALESCE(ca.additional_phone_2_label,'')
                FROM customer_accounts ca LEFT JOIN customer_types ct ON ct.customer_type_id=ca.customer_type_id
                WHERE NOT EXISTS(SELECT 1 FROM customer_account_merges m WHERE m.source_customer_id=ca.customer_id)
                  AND (ca.internal_expense_location_id IS NULL OR ca.internal_expense_location_id=?)
                ORDER BY ca.name
                """)){
            setInt(ps, 1, location);
            try(ResultSet rs=ps.executeQuery()){while(rs.next())rows.add(withMinutes(c,account(rs)));}
        }
        return rows;
    }

    static Map<String,Object> details(Connection c,int customerId,int userId)throws Exception{
        return details(c, customerId, userId, null);
    }

    static Map<String,Object> details(Connection c, int customerId, int userId, Integer location)throws Exception{
        require(c,userId,"CUSTOMER_ACCOUNTS");
        requireVisible(c, customerId, location);
        CustomerAccountLedgerService.repairCustomerBalance(c,customerId);
        try(PreparedStatement ps=c.prepareStatement("""
                SELECT ca.customer_id,COALESCE(ca.account_number,''),
                  COALESCE((SELECT 'Deckers ' || l.name || ' Expense' FROM locations l
                            WHERE l.location_id=ca.internal_expense_location_id),ca.name),COALESCE(ca.phone,''),
                  COALESCE(ca.email,''),COALESCE(ca.credit_limit,0),COALESCE(ca.current_balance,0),
                  COALESCE(ca.credit_limit,0)-GREATEST(COALESCE(ca.current_balance,0)-(COALESCE((SELECT SUM(pm.amount-pm.amount_paid) FROM phone_minutes_sales pm WHERE pm.customer_id=ca.customer_id AND pm.payment_method='CREDIT' AND pm.reversed_at IS NULL),0)+COALESCE((SELECT SUM(mr.document_balance) FROM sync_cross_store_customer_history_cache mr WHERE mr.customer_id=ca.customer_id AND mr.event_type='MINUTES' AND mr.cache_status='CURRENT'),0)),0),
                  COALESCE((SELECT SUM(balance_due) FROM custom_orders o WHERE o.customer_id=ca.customer_id AND COALESCE(o.payment_status,'UNPAID')<>'PAID'),0)+COALESCE((SELECT SUM(document_balance) FROM sync_cross_store_customer_history_cache r WHERE r.customer_id=ca.customer_id AND r.event_type='CUSTOM_ORDER' AND r.cache_status='CURRENT'),0),
                  COALESCE(ca.current_balance,0)+COALESCE((SELECT SUM(balance_due) FROM custom_orders o WHERE o.customer_id=ca.customer_id AND COALESCE(o.payment_method,'')<>'ACCOUNT' AND COALESCE(o.payment_status,'UNPAID')<>'PAID'),0)+COALESCE((SELECT SUM(document_balance) FROM sync_cross_store_customer_history_cache r WHERE r.customer_id=ca.customer_id AND r.event_type='CUSTOM_ORDER' AND COALESCE(r.payment_method,'')<>'ACCOUNT' AND r.cache_status='CURRENT'),0),COALESCE(LOWER(BTRIM(ct.name)) IN ('business','businesses'),FALSE),
                  COALESCE(ca.is_active,TRUE),COALESCE(ca.account_notes,''),ca.customer_type_id,COALESCE(ct.name,''),
                  COALESCE(ct.customer_card_template_slot,4),ca.customer_since,COALESCE(ca.customer_photo_url,''),ca.customer_card_issued_on,ca.customer_card_expires_on,
                  COALESCE(ca.whatsapp_opt_in,FALSE),COALESCE(ca.whatsapp_consent_phone,''),COALESCE(ca.require_charge_authorization,FALSE),
                  COALESCE(ca.sales_discount_enabled,FALSE),COALESCE(ca.sales_discount_percent,0),
                  COALESCE(ca.invoice_discount_enabled,FALSE),COALESCE(ca.invoice_discount_percent,0),
                  COALESCE(ca.custom_order_discount_enabled,FALSE),COALESCE(ca.custom_order_discount_percent,0),ca.custom_order_deposit_exempt,COALESCE(ca.additional_phone_1,''),COALESCE(ca.additional_phone_2,''),COALESCE(ca.phone_label,''),COALESCE(ca.additional_phone_1_label,''),COALESCE(ca.additional_phone_2_label,'')
                FROM customer_accounts ca LEFT JOIN customer_types ct ON ct.customer_type_id=ca.customer_type_id
                WHERE ca.customer_id=?
                """)){ps.setInt(1,customerId);try(ResultSet rs=ps.executeQuery()){
            if(!rs.next())throw rule(404,"CUSTOMER_NOT_FOUND","Customer account was not found.");return withMinutes(c,account(rs));}}
    }

    static void requireVisible(Connection c, int customerId, Integer location) throws Exception {
        try (var ps = c.prepareStatement("""
                SELECT internal_expense_location_id FROM customer_accounts WHERE customer_id=?
                """)) {
            ps.setInt(1, customerId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    Integer store = rs.getObject(1, Integer.class);
                    if (store != null && !store.equals(location)) {
                        throw rule(404, "CUSTOMER_NOT_FOUND", "Customer account was not found.");
                    }
                }
            }
        }
    }

    static Map<String,Object> auditCardOutput(Connection c,JsonObject body,UUID deviceId,int userId)throws Exception{
        require(c,userId,"CUSTOMER_ACCOUNTS");int customerId=body.has("customerId")?body.get("customerId").getAsInt():0;
        int slot=body.has("templateSlot")?body.get("templateSlot").getAsInt():0;
        String action=body.has("action")?clean(body.get("action").getAsString(),20).toUpperCase():"";
        if(customerId<=0||slot<1||slot>5||!("PRINT".equals(action)||"PDF_EXPORT".equals(action)))throw rule(400,"VALIDATION_ERROR","Customer-card audit details are invalid.");
        requireVisible(c, customerId, null);
        requireReference(c,"customer_accounts","customer_id",customerId,"Customer account");
        audit(c,"PRINT".equals(action)?"CUSTOMER_CARD_PRINTED":"CUSTOMER_CARD_PDF_EXPORTED",deviceId,userId,
                GSON.toJson(Map.of("customer_id",customerId,"template_slot",slot)));
        return map("recorded",true);
    }

    static Map<String,Object> issueCard(Connection c,JsonObject body,UUID deviceId,int userId)throws Exception{
        require(c,userId,"CUSTOMER_ACCOUNTS");int customerId=body.has("customerId")?body.get("customerId").getAsInt():0;
        boolean renewal=body.has("renewal")&&body.get("renewal").getAsBoolean();java.time.LocalDate expiry;
        try{expiry=java.time.LocalDate.parse(body.has("expiresOn")?body.get("expiresOn").getAsString():"");}
        catch(Exception ex){throw rule(400,"VALIDATION_ERROR","Select a valid customer-card expiry date.");}
        java.time.LocalDate issued=java.time.LocalDate.now();
        if(customerId<=0||expiry.isBefore(issued))throw rule(400,"VALIDATION_ERROR","Customer-card expiry must be today or later.");
        try(PreparedStatement ps=c.prepareStatement("UPDATE customer_accounts SET customer_card_issued_on=?,customer_card_expires_on=?,updated_at=CURRENT_TIMESTAMP WHERE customer_id=? AND is_active=TRUE AND BTRIM(COALESCE(account_number,''))<>''")){
            ps.setObject(1,issued);ps.setObject(2,expiry);ps.setInt(3,customerId);
            if(ps.executeUpdate()!=1)throw rule(409,"CUSTOMER_CARD_NOT_ISSUABLE","The active customer account needs an account number before a card can be issued.");
        }
        audit(c,renewal?"CUSTOMER_CARD_RENEWED":"CUSTOMER_CARD_ISSUED",deviceId,userId,GSON.toJson(Map.of("customer_id",customerId,"issued_on",issued.toString(),"expires_on",expiry.toString())));
        return map("issuedOn",issued.toString(),"expiresOn",expiry.toString());
    }

    static Map<String,Object> save(Connection c,JsonObject body,UUID deviceId,int userId)throws Exception{
        require(c,userId,"CUSTOMER_ACCOUNTS");
        return saveAuthorized(c,body,deviceId,userId);
    }

    static QuotationInvoiceViewService.CustomerOption createQuotationCustomer(
            Connection c, String name, String phone, UUID deviceId, int userId) throws Exception {
        name = required(name,200,"Customer name is required.");
        phone = required(phone,100,"Customer phone number is required.");
        try {
            phone = utils.CustomerPhoneNumber.normalize(phone);
        } catch (IllegalArgumentException ex) {
            throw rule(400,"VALIDATION_ERROR",ex.getMessage());
        }
        JsonObject account = new JsonObject();
        account.addProperty("name",name);
        account.addProperty("phone",phone);
        account.addProperty("customerTypeId",1);
        account.addProperty("active",true);
        Map<String,Object> saved = saveAuthorized(c,account,deviceId,userId);
        return new QuotationInvoiceViewService.CustomerOption((int) saved.get("customerId"),
                (String) saved.get("accountNumber"),name,false,false,BigDecimal.ZERO,phone);
    }

    private static Map<String,Object> saveAuthorized(Connection c,JsonObject body,UUID deviceId,int userId)throws Exception{
        Request r=parsed(body);String name=required(r.name(),200,"Customer name is required.");
        String phone=clean(r.phone(),100),email=clean(r.email(),320),notes=clean(r.accountNotes(),4000),photo=r.customerPhotoUrl()==null?null:clean(r.customerPhotoUrl(),2000);
        try { phone=utils.CustomerPhoneNumber.normalize(phone); }
        catch(IllegalArgumentException ex){throw rule(400,"VALIDATION_ERROR",ex.getMessage());}
        try { CustomerPhoneOwnership.requireAvailable(c,phone,r.customerId()); }
        catch(SQLException ex){if("23505".equals(ex.getSQLState()))throw rule(409,"CUSTOMER_PHONE_IN_USE",CustomerPhoneOwnership.MESSAGE);throw ex;}
        String first=null,second=null,mainLabel=null,firstLabel=null,secondLabel=null;
        if(r.customerId()!=null)try(var ps=c.prepareStatement("SELECT additional_phone_1,additional_phone_2,phone_label,additional_phone_1_label,additional_phone_2_label FROM customer_accounts WHERE customer_id=?")){ps.setInt(1,r.customerId());try(var rs=ps.executeQuery()){if(rs.next()){first=rs.getString(1);second=rs.getString(2);mainLabel=rs.getString(3);firstLabel=rs.getString(4);secondLabel=rs.getString(5);}}}
        try {
            if(body.has("additionalPhone1"))first=utils.CustomerPhoneNumber.normalize(clean(r.additionalPhone1(),100));
            if(body.has("additionalPhone2"))second=utils.CustomerPhoneNumber.normalize(clean(r.additionalPhone2(),100));
        }catch(IllegalArgumentException ex){throw rule(400,"VALIDATION_ERROR",ex.getMessage());}
        if(body.has("phoneLabel"))mainLabel=clean(r.phoneLabel(),50);
        if(body.has("additionalPhone1Label"))firstLabel=clean(r.additionalPhone1Label(),50);
        if(body.has("additionalPhone2Label"))secondLabel=clean(r.additionalPhone2Label(),50);
        java.util.Set<String> numbers=new java.util.HashSet<>();
        for(String n:new String[]{phone,first,second}) {
            if(n==null||n.isBlank())continue;
            if(!numbers.add(n))throw rule(400,"VALIDATION_ERROR","Enter a different number in each phone field.");
            try{CustomerPhoneOwnership.requireAvailable(c,n,r.customerId());}
            catch(SQLException ex){if("23505".equals(ex.getSQLState()))throw rule(409,"CUSTOMER_PHONE_IN_USE",CustomerPhoneOwnership.MESSAGE);throw ex;}
        }
        if(r.whatsappOptIn())try{ServerWhatsAppService.normalizePhone(phone);}catch(ServerWhatsAppService.Rule ex){throw rule(400,ex.code,ex.getMessage());}
        int currentYear=java.time.Year.now().getValue();Integer since=r.customerSince();if(r.customerId()==null&&since==null)since=currentYear;
        if(since!=null&&(since<1900||since>currentYear))throw rule(400,"VALIDATION_ERROR","Customer Since must be a four-digit year from 1900 through "+currentYear+".");
        BigDecimal credit=money(r.creditLimit());if(credit.signum()<0)throw rule(400,"VALIDATION_ERROR","Credit limit cannot be negative.");
        BigDecimal salesDiscount=percent(r.salesDiscountPercent()),invoiceDiscount=percent(r.invoiceDiscountPercent()),customOrderDiscount=percent(r.customOrderDiscountPercent());
        boolean salesEnabled=r.salesDiscountEnabled(),invoiceEnabled=r.invoiceDiscountEnabled(),customOrderEnabled=r.customOrderDiscountEnabled(),canManageDiscounts=has(c,userId,"MANAGE_CUSTOMER_DISCOUNTS");
        boolean changesDiscounts=salesEnabled||salesDiscount.signum()>0||invoiceEnabled||invoiceDiscount.signum()>0||customOrderEnabled||customOrderDiscount.signum()>0;
        if(r.customerId()==null&&changesDiscounts&&!canManageDiscounts)throw rule(403,"PERMISSION_DENIED","You do not have permission to manage customer discounts.");
        if(r.customerId()==null&&credit.signum()!=0&&!has(c,userId,"SET_CREDIT_LIMIT"))throw rule(403,"PERMISSION_DENIED","You do not have permission to set credit limits.");
        if(r.customerTypeId()!=null)requireReference(c,"customer_types","customer_type_id",r.customerTypeId(),"Customer type");
        // Baseline customer-card contract: customer_since,customer_photo_url) VALUES; WhatsApp consent columns follow it.
        boolean exempt=Boolean.TRUE.equals(r.customOrderDepositExempt());
        boolean canExempt=has(c,userId,"CUSTOM_ORDER_DEPOSIT_OVERRIDE")||has(c,userId,"CUSTOM_ORDER_OVERRIDES");
        if(r.customerId()==null&&exempt&&!canExempt)throw rule(403,"PERMISSION_DENIED","You do not have permission to waive custom-order deposits.");
        int id;String number;
        if(r.customerId()==null){
            try(PreparedStatement ps=c.prepareStatement("""
                    INSERT INTO customer_accounts(name,customer_type_id,phone,email,credit_limit,current_balance,
                      is_active,account_notes,customer_since,customer_photo_url,whatsapp_opt_in,whatsapp_consent_phone,whatsapp_consent_at,whatsapp_consent_by_user_id,require_charge_authorization,
                      sales_discount_enabled,sales_discount_percent,invoice_discount_enabled,invoice_discount_percent,custom_order_discount_enabled,custom_order_discount_percent,custom_order_deposit_exempt)
                    VALUES (?,?,?,?,?,0,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING customer_id,account_number
                    """)){ps.setString(1,name);setInt(ps,2,r.customerTypeId());ps.setString(3,blank(phone));ps.setString(4,blank(email));
                ps.setBigDecimal(5,credit);ps.setBoolean(6,r.active());ps.setString(7,blank(notes));
                setInt(ps,8,since);ps.setString(9,blank(photo));ps.setBoolean(10,r.whatsappOptIn());ps.setString(11,r.whatsappOptIn()?blank(phone):null);ps.setObject(12,r.whatsappOptIn()?java.time.OffsetDateTime.now():null);if(r.whatsappOptIn())ps.setInt(13,userId);else ps.setNull(13,java.sql.Types.INTEGER);
                ps.setBoolean(14,r.requireChargeAuthorization());ps.setBoolean(15,salesEnabled);ps.setBigDecimal(16,salesDiscount);ps.setBoolean(17,invoiceEnabled);ps.setBigDecimal(18,invoiceDiscount);ps.setBoolean(19,customOrderEnabled);ps.setBigDecimal(20,customOrderDiscount);ps.setBoolean(21,exempt);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new SQLException("Customer account could not be created.");id=rs.getInt(1);number=rs.getString(2);}}
        }else{
            id=r.customerId();String accountNumber=clean(r.accountNumber(),100);
            try(PreparedStatement lock=c.prepareStatement("SELECT account_number,credit_limit,COALESCE(customer_photo_url,''),sales_discount_enabled,sales_discount_percent,invoice_discount_enabled,invoice_discount_percent,custom_order_discount_enabled,custom_order_discount_percent,custom_order_deposit_exempt FROM customer_accounts WHERE customer_id=? AND internal_expense_location_id IS NULL FOR UPDATE")){
                lock.setInt(1,id);try(ResultSet rs=lock.executeQuery()){if(!rs.next())throw rule(404,"CUSTOMER_NOT_FOUND","Customer account was not found.");
                    boolean existingExempt=rs.getBoolean(10);
                    if(r.customOrderDepositExempt()!=null&&exempt!=existingExempt&&!canExempt)throw rule(403,"PERMISSION_DENIED","You do not have permission to waive custom-order deposits.");
                    if(r.customOrderDepositExempt()==null)exempt=existingExempt;
                    String existingNumber=rs.getString(1);BigDecimal existingCredit=money(rs.getBigDecimal(2));
                    if(!has(c,userId,"EDIT_ACCOUNT_NUMBER"))accountNumber=existingNumber;
                    if(!has(c,userId,"SET_CREDIT_LIMIT"))credit=existingCredit;if(photo==null)photo=rs.getString(3);if(!canManageDiscounts){salesEnabled=rs.getBoolean(4);salesDiscount=rs.getBigDecimal(5);invoiceEnabled=rs.getBoolean(6);invoiceDiscount=rs.getBigDecimal(7);customOrderEnabled=rs.getBoolean(8);customOrderDiscount=rs.getBigDecimal(9);}}}
            accountNumber=required(accountNumber,100,"Account number is required.");
            try(PreparedStatement ps=c.prepareStatement("""
                    UPDATE customer_accounts SET account_number=?,name=?,customer_type_id=?,phone=?,email=?,credit_limit=?,
                      is_active=?,account_notes=?,customer_since=?,customer_photo_url=?,whatsapp_opt_in=?,
                      whatsapp_consent_phone=CASE WHEN ? THEN ? ELSE NULL END,whatsapp_consent_at=CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END,
                      whatsapp_consent_by_user_id=CASE WHEN ? THEN ? ELSE NULL END,require_charge_authorization=?,
                      sales_discount_enabled=?,sales_discount_percent=?,invoice_discount_enabled=?,invoice_discount_percent=?,custom_order_discount_enabled=?,custom_order_discount_percent=?,custom_order_deposit_exempt=?,additional_phone_1=?,additional_phone_2=?,phone_label=?,additional_phone_1_label=?,additional_phone_2_label=? WHERE customer_id=?
                    """)){ps.setString(1,accountNumber);ps.setString(2,name);setInt(ps,3,r.customerTypeId());ps.setString(4,blank(phone));
                ps.setString(5,blank(email));ps.setBigDecimal(6,credit);ps.setBoolean(7,r.active());
                ps.setString(8,blank(notes));setInt(ps,9,since);ps.setString(10,blank(photo));ps.setBoolean(11,r.whatsappOptIn());ps.setBoolean(12,r.whatsappOptIn());ps.setString(13,blank(phone));ps.setBoolean(14,r.whatsappOptIn());ps.setBoolean(15,r.whatsappOptIn());ps.setInt(16,userId);ps.setBoolean(17,r.requireChargeAuthorization());ps.setBoolean(18,salesEnabled);ps.setBigDecimal(19,salesDiscount);ps.setBoolean(20,invoiceEnabled);ps.setBigDecimal(21,invoiceDiscount);ps.setBoolean(22,customOrderEnabled);ps.setBigDecimal(23,customOrderDiscount);ps.setBoolean(24,exempt);ps.setString(25,blank(first));ps.setString(26,blank(second));ps.setString(27,blank(mainLabel));ps.setString(28,blank(firstLabel));ps.setString(29,blank(secondLabel));ps.setInt(30,id);ps.executeUpdate();number=accountNumber;}
        }
        if (r.customerId() == null) {
            try (var ps = c.prepareStatement("""
                    UPDATE customer_accounts
                    SET additional_phone_1=?, additional_phone_2=?, phone_label=?,
                        additional_phone_1_label=?, additional_phone_2_label=?
                    WHERE customer_id=?
                    """)) {
                ps.setString(1,blank(first));
                ps.setString(2,blank(second));
                ps.setString(3,blank(mainLabel));
                ps.setString(4,blank(firstLabel));
                ps.setString(5,blank(secondLabel));
                ps.setInt(6,id);
                ps.executeUpdate();
            }
        }
        audit(c,"LAN_CUSTOMER_ACCOUNT_SAVED",deviceId,userId,GSON.toJson(Map.of("customer_id",id,"account_number",number==null?"":number,"custom_order_deposit_exempt",exempt)));
        audit(c,r.whatsappOptIn()?"WHATSAPP_CUSTOMER_OPTED_IN":"WHATSAPP_CUSTOMER_OPTED_OUT",deviceId,userId,GSON.toJson(Map.of("customer_id",id)));
        return map("customerId",id,"accountNumber",number==null?"":number);
    }

    static Map<String,Object> adjust(Connection c,JsonObject body,UUID deviceId,int userId,String userName,int locationId)throws Exception{
        return adjust(c,body,deviceId,userId,userName,locationId,null);
    }

    static Map<String,Object> adjust(Connection c,JsonObject body,UUID deviceId,int userId,String userName,int locationId,
                                     PhoneMinutesService.BrowserOrigin browser)throws Exception{
        require(c,userId,"CUSTOMER_ACCOUNTS");CustomerAccountLedgerService.ensureSchema(c);
        CustomerAccountLedgerService.requireCurrentMultiStoreBalance(c,locationId);
        Adjustment r;
        try{r=GSON.fromJson(body,Adjustment.class);}catch(Exception ex){throw rule(400,"VALIDATION_ERROR","Account adjustment details are invalid.");}
        if(r==null||r.customerId()<=0)throw rule(400,"VALIDATION_ERROR","Select a customer account.");
        BigDecimal amount=money(r.amount());if(amount.signum()<=0)throw rule(400,"VALIDATION_ERROR","Amount must be greater than zero.");
        if(amount.stripTrailingZeros().scale()>0)throw rule(400,"VALIDATION_ERROR","Customer account amounts must use whole GYD values.");
        String action=clean(r.action(),20).toUpperCase();boolean charge="CHARGE".equals(action);
        if(!charge&&!"PAYMENT".equals(action))throw rule(400,"VALIDATION_ERROR","Account adjustment type is invalid.");
        String description=chargeDescription(action,r.description());
        String method=charge?null:required(r.paymentMethod(),30,"Select a payment method.").toUpperCase();
        String reference=charge?null:clean(r.paymentReference(),200);
        boolean useCredit=!charge&&"APPLY_CREDIT".equals(method);
        if(!charge&&!List.of("CASH","CARD","CHEQUE","MMG","BANK_TRANSFER","APPLY_CREDIT").contains(method))throw rule(400,"VALIDATION_ERROR","Select a supported payment method.");
        if(!charge&&requiresReference(method)&&reference.isBlank())throw rule(400,"VALIDATION_ERROR","Payment reference is required for card, cheque, and MMG payments.");

        PhoneMinutesService.lockCustomer(c,r.customerId(),true);
        CustomerAccountLedgerService.repairCustomerBalance(c,r.customerId());
        BigDecimal balance,limit;
        try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(current_balance,0),COALESCE(credit_limit,0),COALESCE(is_active,TRUE) FROM customer_accounts WHERE customer_id=? FOR UPDATE")){
            ps.setInt(1,r.customerId());try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw rule(404,"CUSTOMER_NOT_FOUND","Customer account was not found.");
                balance=money(rs.getBigDecimal(1));limit=money(rs.getBigDecimal(2));if(!rs.getBoolean(3))throw rule(409,"ACCOUNT_INACTIVE","This customer account is inactive.");}}
        List<PaymentAllocation> requested=r.allocations()==null?List.of():r.allocations();
        BigDecimal allocated=requested.stream().map(a->a==null?BigDecimal.ZERO:money(a.amount())).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(!charge&&(requested.isEmpty()||allocated.compareTo(amount)!=0))throw rule(409,"ALLOCATION_MISMATCH","Selected allocation amounts must equal the payment amount.");
        if(!charge){java.util.Set<String> unique=new java.util.HashSet<>();for(var a:requested){if(a==null||a.amount()==null||a.amount().signum()<=0||a.amount().stripTrailingZeros().scale()>0)throw rule(400,"VALIDATION_ERROR","Every allocation must be a positive whole-GYD amount.");String key=String.valueOf(a.documentType()).toUpperCase()+":"+a.documentId();if(!unique.add(key))throw rule(400,"VALIDATION_ERROR","Select each document only once.");if("ACCOUNT".equalsIgnoreCase(a.documentType())){BigDecimal available=BigDecimal.ZERO;for(var b:openBalances(c,r.customerId(),userId))if("ACCOUNT".equals(b.get("documentType")))available=(BigDecimal)b.get("balanceDue");if(a.amount().compareTo(available)>0)throw rule(409,"BALANCE_CHANGED","Unattributed account balance changed.");}}}
        if(!charge&&requested.stream().anyMatch(a->"CUSTOM_ORDER".equalsIgnoreCase(a.documentType()))
                && !List.of("CASH","CARD","CHEQUE","MMG").contains(method))
            throw rule(400,"VALIDATION_ERROR","Select cash, card, cheque or MMG for a custom order payment.");
        BigDecimal creditApplied=charge?BigDecimal.ZERO:requested.stream().filter(a->!"CUSTOM_ORDER".equalsIgnoreCase(a.documentType())).map(a->money(a.amount())).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal next=charge?balance.add(amount):balance.subtract(creditApplied);
        if(next.signum()<0)throw rule(409,"PAYMENT_EXCEEDS_CREDIT_BALANCE","The amount allocated to account credit exceeds the credit balance.");
        if(charge&&next.subtract(PhoneMinutesService.minutesDue(c,r.customerId())).max(BigDecimal.ZERO).compareTo(limit)>0)throw rule(409,"CREDIT_LIMIT_EXCEEDED","Charge exceeds the customer's credit limit.");

        CashDrawerContext drawer=new CashDrawerContext(null,null);
        String deviceName=browser==null?deviceName(c,deviceId):browser.name();
        UUID origin=browser==null?deviceId:browser.browser();
        if(!charge&&"CASH".equals(method)){
            drawer=browser==null?CashDrawerService.resolveDrawerForDevice(c,locationId,deviceId.toString())
                    :PhoneMinutesService.browserDrawer(c,locationId,userId,browser.drawerSessionId());
            if(!drawer.isAssigned())throw rule(409,"CASH_DRAWER_REQUIRED","This register is not assigned to an active cash drawer for the selected store.");
            if(!drawer.hasActiveSession())throw rule(409,"CASH_SESSION_REQUIRED","No active draw session is open for "+drawer.drawerName()+".");
        }
        try(PreparedStatement ps=c.prepareStatement("UPDATE customer_accounts SET current_balance=?,updated_at=CURRENT_TIMESTAMP WHERE customer_id=?")){
            ps.setBigDecimal(1,next);ps.setInt(2,r.customerId());ps.executeUpdate();}
        if(useCredit)method="ACCOUNT_CREDIT";
        long transactionId=insertAdjustment(c,r.customerId(),charge?amount:amount.negate(),creditApplied,charge?"MANUAL_CHARGE":"PAYMENT",
                charge?description:"Customer payment",method,reference,drawer,origin,deviceName,userName,locationId);
        if(useCredit)PhoneMinutesService.consumeCredit(c,r.customerId(),amount,transactionId);
        String paymentId=charge?"":"PAY-"+String.format("%06d",transactionId);
        if(!charge){
            try(PreparedStatement ps=c.prepareStatement("UPDATE customer_account_transactions SET payment_id=? WHERE transaction_id=?")){
                ps.setString(1,paymentId);ps.setLong(2,transactionId);ps.executeUpdate();}
            String note=applySelectedAllocations(c,r.customerId(),requested,transactionId,method,reference,drawer,origin,deviceName,userId,userName);
            try(PreparedStatement ps=c.prepareStatement("UPDATE customer_account_transactions SET note=? WHERE transaction_id=?")){
                ps.setString(1,note);ps.setLong(2,transactionId);ps.executeUpdate();}
        }
        next=CustomerAccountLedgerService.repairCustomerBalance(c,r.customerId());
        audit(c,charge?"LAN_CUSTOMER_CHARGE_RECORDED":"LAN_CUSTOMER_PAYMENT_RECORDED",deviceId,userId,
                GSON.toJson(Map.of("customer_id",r.customerId(),"transaction_id",transactionId,"amount",amount,"balance_after",next)));
        return map("transactionId",transactionId,"paymentId",paymentId,"balanceAfter",next);
    }

    static long insertAdjustment(Connection c,int customerId,BigDecimal amount,BigDecimal creditApplied,String type,String note,String method,String reference,
                                         CashDrawerContext drawer,UUID deviceId,String deviceName,String userName,int locationId)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("""
                INSERT INTO customer_account_transactions(customer_id,location_id,amount,credit_applied_amount,transaction_type,note,user_name,device_id,device_name,
                  payment_method,payment_reference,cash_drawer_id,cash_drawer_name,cash_drawer_session_id)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING transaction_id
                """)){ps.setInt(1,customerId);ps.setInt(2,locationId);ps.setBigDecimal(3,amount);ps.setBigDecimal(4,creditApplied);ps.setString(5,type);ps.setString(6,note);
            ps.setString(7,userName);ps.setObject(8,deviceId);ps.setString(9,deviceName);ps.setString(10,blank(method));ps.setString(11,blank(reference));
            setLong(ps,12,drawer.cashDrawerId());ps.setString(13,blank(drawer.drawerName()));setLong(ps,14,drawer.sessionId());
            try(ResultSet rs=ps.executeQuery()){if(rs.next())return rs.getLong(1);}}
        throw new SQLException("Failed to create account transaction.");
    }

    static List<Map<String,Object>> openBalances(Connection c,int customerId,int userId)throws Exception{
        require(c,userId,"CUSTOMER_ACCOUNTS");CustomerAccountLedgerService.repairCustomerBalance(c,customerId);
        // Cross-store ledger entries are not manual charges at this store.
        BigDecimal localCredit=BigDecimal.ZERO;
        try(PreparedStatement ps=c.prepareStatement("SELECT GREATEST(COALESCE(SUM("+CustomerAccountLedgerService.balanceDeltaSql("t")+"),0),0) FROM customer_account_transactions t WHERE customer_id=?")){
            ps.setInt(1,customerId);try(ResultSet rs=ps.executeQuery()){if(rs.next())localCredit=money(rs.getBigDecimal(1));}
        }
        return readOpenBalances(c,customerId,localCredit);
    }

    static List<Map<String,Object>> readOpenBalances(Connection c,int customerId,BigDecimal localCredit)throws SQLException{
        List<Map<String,Object>>out=new ArrayList<>();
        String sql="""
          SELECT document_type,document_id,document_number,created_at,total,paid,balance FROM (
            SELECT 'SALE' document_type,sale_id::bigint document_id,COALESCE(receipt_number,'Sale #'||sale_id) document_number,created_at,
              GREATEST(COALESCE(total_amount,0)-COALESCE(returned_amount,0),0) total,COALESCE(amount_paid,0) paid,
              GREATEST(GREATEST(COALESCE(total_amount,0)-COALESCE(returned_amount,0),0)-COALESCE(amount_paid,0),0) balance
            FROM sales WHERE customer_id=? AND payment_method='ACCOUNT' AND COALESCE(payment_status,'PAID')<>'PAID'
            UNION ALL SELECT 'INVOICE',invoice_id,COALESCE(NULLIF(invoice_number,''),'Invoice '||invoice_id),created_at,COALESCE(total_amount,0),COALESCE(amount_paid,0),COALESCE(balance_due,0)
            FROM invoices i WHERE customer_id=? AND COALESCE(payment_status,'UNPAID')<>'PAID' AND COALESCE(balance_due,0)>0
              AND COALESCE(status,'OPEN')<>'CANCELLED'
              AND EXISTS(SELECT 1 FROM customer_account_transactions t WHERE t.customer_id=i.customer_id
                AND t.invoice_id=i.invoice_id AND t.transaction_type='INVOICE_CREDIT')
            UNION ALL SELECT 'CUSTOM_ORDER',custom_order_id,COALESCE(NULLIF(order_number,''),'Custom Order '||custom_order_id),created_at,COALESCE(total_amount,0),COALESCE(amount_paid,0),COALESCE(balance_due,0)
            FROM custom_orders WHERE customer_id=? AND order_type='CUSTOM' AND COALESCE(payment_status,'UNPAID')<>'PAID' AND COALESCE(balance_due,0)>0
              AND NOT COALESCE(invoice_billed,FALSE) AND COALESCE(status,'')<>'CANCELLED'
            UNION ALL SELECT 'MINUTES',minutes_id,'MIN-'||minutes_id||COALESCE(' / '||NULLIF(destination_phone, ''), ''),created_at,amount,amount_paid,amount-amount_paid
            FROM phone_minutes_sales WHERE customer_id=? AND payment_method='CREDIT' AND reversed_at IS NULL
          ) x WHERE balance>0 ORDER BY created_at,document_type,document_id
          """;
        BigDecimal documentedCredit=BigDecimal.ZERO;try(PreparedStatement ps=c.prepareStatement(sql)){for(int i=1;i<=4;i++)ps.setInt(i,customerId);try(ResultSet rs=ps.executeQuery()){while(rs.next()){String type=rs.getString(1);BigDecimal due=money(rs.getBigDecimal(7));out.add(map("documentType",type,"documentId",rs.getLong(2),"documentNumber",rs.getString(3),"createdAtEpochMillis",epoch(rs.getTimestamp(4)),"total",money(rs.getBigDecimal(5)),"paid",money(rs.getBigDecimal(6)),"balanceDue",due));if(!"CUSTOM_ORDER".equals(type))documentedCredit=documentedCredit.add(due);}}}
        BigDecimal unattributed=localCredit.subtract(documentedCredit).max(BigDecimal.ZERO);if(unattributed.signum()>0)out.add(0,map("documentType","ACCOUNT","documentId",0L,"documentNumber","Manual / Unattributed Account Credit","createdAtEpochMillis",0L,"total",unattributed,"paid",BigDecimal.ZERO,"balanceDue",unattributed));
        return out;
    }

    private static String allocatePayment(Connection c,int customerId,BigDecimal amount,long transactionId,String method,String reference,
                                          CashDrawerContext drawer,UUID deviceId,String deviceName,int userId,String userName)throws SQLException{
        BigDecimal remaining=amount;StringBuilder applied=new StringBuilder();
        try(PreparedStatement select=c.prepareStatement("""
                SELECT sale_id,GREATEST(COALESCE(total_amount,0)-COALESCE(returned_amount,0),0),COALESCE(amount_paid,0)
                FROM sales WHERE customer_id=? AND payment_method='ACCOUNT' AND COALESCE(payment_status,'PAID')<>'PAID'
                ORDER BY created_at,sale_id FOR UPDATE
                """);PreparedStatement update=c.prepareStatement("UPDATE sales SET amount_paid=?,payment_status=? WHERE sale_id=?");
            PreparedStatement allocation=c.prepareStatement("INSERT INTO customer_account_payment_allocations(payment_transaction_id,customer_id,sale_id,amount) VALUES(?,?,?,?)")){
            select.setInt(1,customerId);try(ResultSet rs=select.executeQuery()){while(rs.next()&&remaining.signum()>0){int id=rs.getInt(1);
                BigDecimal total=money(rs.getBigDecimal(2)),paid=money(rs.getBigDecimal(3)),due=total.subtract(paid);
                if(due.signum()<=0){update.setBigDecimal(1,total);update.setString(2,"PAID");update.setInt(3,id);update.executeUpdate();continue;}
                BigDecimal used=remaining.min(due),newPaid=paid.add(used);update.setBigDecimal(1,newPaid);update.setString(2,newPaid.compareTo(total)>=0?"PAID":"UNPAID");update.setInt(3,id);update.executeUpdate();
                allocation.setLong(1,transactionId);allocation.setInt(2,customerId);allocation.setInt(3,id);allocation.setBigDecimal(4,used);allocation.executeUpdate();append(applied,"sale #"+id+" "+used);remaining=remaining.subtract(used);}}}
        remaining=allocateCustomOrders(c,customerId,remaining,transactionId,method,reference,drawer,deviceId,deviceName,userId,userName,applied);
        remaining=allocateInvoices(c,customerId,remaining,transactionId,method,reference,drawer,deviceId,deviceName,userId,userName,applied);
        if(applied.isEmpty())return "Customer payment. No unpaid account sales, custom orders, or invoices were available to apply this payment to.";
        if(remaining.signum()>0)append(applied,"unapplied "+remaining);return "Customer payment applied to "+applied;
    }

    private static BigDecimal allocateCustomOrders(Connection c,int customerId,BigDecimal remaining,long tx,String method,String reference,
                                                    CashDrawerContext drawer,UUID deviceId,String deviceName,int userId,String userName,StringBuilder applied)throws SQLException{
        try(PreparedStatement select=c.prepareStatement("""
                SELECT custom_order_id,COALESCE(NULLIF(order_number,''),custom_order_id::text),COALESCE(total_amount,0),COALESCE(amount_paid,0),
                  COALESCE(balance_due,COALESCE(total_amount,0)-COALESCE(amount_paid,0)),location_id FROM custom_orders
                WHERE customer_id=? AND order_type='CUSTOM' AND COALESCE(payment_status,'PAID')<>'PAID' AND COALESCE(balance_due,COALESCE(total_amount,0)-COALESCE(amount_paid,0))>0
                ORDER BY created_at,custom_order_id FOR UPDATE
                """);PreparedStatement update=c.prepareStatement("""
                UPDATE custom_orders SET amount_paid=COALESCE(amount_paid,0)+?,balance_due=GREATEST(COALESCE(balance_due,COALESCE(total_amount,0)-COALESCE(amount_paid,0))-?,0),
                  payment_status=CASE WHEN GREATEST(COALESCE(balance_due,COALESCE(total_amount,0)-COALESCE(amount_paid,0))-?,0)<=0 THEN 'PAID' ELSE 'PARTIAL' END
                WHERE custom_order_id=?
                """);PreparedStatement payment=c.prepareStatement("""
                INSERT INTO custom_order_payments(custom_order_id,payment_amount,payment_method,payment_reference,taken_by_user_id,taken_by_name,payment_action,
                  device_id,device_name,cash_drawer_id,cash_drawer_name,cash_drawer_session_id) VALUES(?,?,?,?,?,?,'PAYMENT',?,?,?,?,?)
                """);PreparedStatement allocation=c.prepareStatement("INSERT INTO customer_account_payment_allocations(payment_transaction_id,customer_id,custom_order_id,amount) VALUES(?,?,?,?)")){
            select.setInt(1,customerId);try(ResultSet rs=select.executeQuery()){while(rs.next()&&remaining.signum()>0){long id=rs.getLong(1);String number=rs.getString(2);
                BigDecimal total=money(rs.getBigDecimal(3)),paid=money(rs.getBigDecimal(4)),due=money(rs.getBigDecimal(5));if(due.signum()<=0)due=total.subtract(paid);if(due.signum()<=0)continue;
                BigDecimal used=remaining.min(due);for(int i=1;i<=3;i++)update.setBigDecimal(i,used);update.setLong(4,id);update.executeUpdate();
                payment.setLong(1,id);payment.setBigDecimal(2,used);payment.setString(3,method);payment.setString(4,paymentReference(tx,reference));payment.setInt(5,userId);payment.setString(6,userName);
                payment.setObject(7,deviceId);payment.setString(8,deviceName);setLong(payment,9,drawer.cashDrawerId());payment.setString(10,blank(drawer.drawerName()));setLong(payment,11,drawer.sessionId());payment.executeUpdate();
                LanCustomOrderWorkflowService.reconcileSettledDelivery(c,id,rs.getInt(6),userId,userName,deviceId.toString(),deviceName);
                allocation.setLong(1,tx);allocation.setInt(2,customerId);allocation.setLong(3,id);allocation.setBigDecimal(4,used);allocation.executeUpdate();append(applied,"custom order "+number+" "+used);remaining=remaining.subtract(used);}}}
        return remaining;
    }

    private static BigDecimal allocateInvoices(Connection c,int customerId,BigDecimal remaining,long tx,String method,String reference,
                                                CashDrawerContext drawer,UUID deviceId,String deviceName,int userId,String userName,StringBuilder applied)throws SQLException{
        try(PreparedStatement select=c.prepareStatement("""
                SELECT invoice_id,COALESCE(NULLIF(invoice_number,''),invoice_id::text),COALESCE(total_amount,0),COALESCE(amount_paid,0),
                  COALESCE(balance_due,COALESCE(total_amount,0)-COALESCE(amount_paid,0)) FROM invoices
                WHERE customer_id=? AND COALESCE(payment_status,'UNPAID')<>'PAID' AND COALESCE(balance_due,COALESCE(total_amount,0)-COALESCE(amount_paid,0))>0
                ORDER BY created_at,invoice_id FOR UPDATE
                """);PreparedStatement update=c.prepareStatement("""
                UPDATE invoices SET amount_paid=LEAST(COALESCE(total_amount,0),COALESCE(amount_paid,0)+?),
                  balance_due=GREATEST(COALESCE(total_amount,0)-LEAST(COALESCE(total_amount,0),COALESCE(amount_paid,0)+?),0),
                  payment_status=CASE WHEN GREATEST(COALESCE(total_amount,0)-LEAST(COALESCE(total_amount,0),COALESCE(amount_paid,0)+?),0)<=0 THEN 'PAID' ELSE 'PARTIAL' END,
                  payment_method=?,payment_reference=COALESCE(NULLIF(?,''),payment_reference) WHERE invoice_id=?
                """);PreparedStatement payment=c.prepareStatement("""
                INSERT INTO invoice_payments(invoice_id,customer_id,payment_amount,payment_method,payment_reference,taken_by_user_id,taken_by_name,location_id,
                  device_id,device_name,cash_drawer_id,cash_drawer_name,cash_drawer_session_id)
                SELECT invoice_id,customer_id,?,?,?,?,?,location_id,?,?,?,?,? FROM invoices WHERE invoice_id=?
                """);PreparedStatement allocation=c.prepareStatement("INSERT INTO customer_account_payment_allocations(payment_transaction_id,customer_id,invoice_id,amount) VALUES(?,?,?,?)")){
            select.setInt(1,customerId);try(ResultSet rs=select.executeQuery()){while(rs.next()&&remaining.signum()>0){long id=rs.getLong(1);String number=rs.getString(2);
                BigDecimal total=money(rs.getBigDecimal(3)),paid=money(rs.getBigDecimal(4)),due=money(rs.getBigDecimal(5));if(due.signum()<=0)due=total.subtract(paid);if(due.signum()<=0)continue;
                BigDecimal used=remaining.min(due);for(int i=1;i<=3;i++)update.setBigDecimal(i,used);update.setString(4,method);update.setString(5,reference);update.setLong(6,id);update.executeUpdate();
                payment.setBigDecimal(1,used);payment.setString(2,method);payment.setString(3,paymentReference(tx,reference));payment.setInt(4,userId);payment.setString(5,userName);
                payment.setObject(6,deviceId);payment.setString(7,deviceName);setLong(payment,8,drawer.cashDrawerId());payment.setString(9,blank(drawer.drawerName()));setLong(payment,10,drawer.sessionId());payment.setLong(11,id);payment.executeUpdate();
                allocation.setLong(1,tx);allocation.setInt(2,customerId);allocation.setLong(3,id);allocation.setBigDecimal(4,used);allocation.executeUpdate();append(applied,"invoice "+number+" "+used);remaining=remaining.subtract(used);}}}
        return remaining;
    }

    private static String applySelectedAllocations(Connection c,int customerId,List<PaymentAllocation> requests,long tx,String method,String reference,
                                                   CashDrawerContext drawer,UUID deviceId,String deviceName,int userId,String userName)throws Exception{
        StringBuilder applied=new StringBuilder();for(PaymentAllocation request:requests){BigDecimal amount=money(request.amount());
            if(amount.signum()<=0||amount.stripTrailingZeros().scale()>0)throw rule(400,"VALIDATION_ERROR","Every allocation must be a positive whole-GYD amount.");
            String type=clean(request.documentType(),30).toUpperCase();if(!"ACCOUNT".equals(type)&&(request.documentId()==null||request.documentId()<=0))throw rule(400,"VALIDATION_ERROR","Every allocation must identify a document.");
            long id=request.documentId()==null?0:request.documentId();if("CUSTOM_ORDER".equals(type)){String number;BigDecimal due;try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(NULLIF(order_number,''),custom_order_id::text),COALESCE(balance_due,0) FROM custom_orders WHERE custom_order_id=? AND customer_id=? AND order_type='CUSTOM' AND NOT COALESCE(invoice_billed,FALSE) AND status<>'CANCELLED' FOR UPDATE")){ps.setLong(1,id);ps.setInt(2,customerId);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw rule(409,"BALANCE_CHANGED","A selected custom order is no longer available.");number=rs.getString(1);due=money(rs.getBigDecimal(2));}}
                if(amount.compareTo(due)>0)throw rule(409,"BALANCE_CHANGED","A custom-order allocation exceeds its current balance.");
                try(PreparedStatement ps=c.prepareStatement("UPDATE custom_orders SET amount_paid=COALESCE(amount_paid,0)+?,balance_due=GREATEST(COALESCE(balance_due,0)-?,0),payment_status=CASE WHEN COALESCE(balance_due,0)-?<=0 THEN 'PAID' ELSE 'PARTIAL' END,updated_at=CURRENT_TIMESTAMP WHERE custom_order_id=?")){ps.setBigDecimal(1,amount);ps.setBigDecimal(2,amount);ps.setBigDecimal(3,amount);ps.setLong(4,id);ps.executeUpdate();}
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO custom_order_payments(custom_order_id,payment_amount,payment_method,payment_reference,taken_by_user_id,taken_by_name,payment_action,device_id,device_name,cash_drawer_id,cash_drawer_name,cash_drawer_session_id) VALUES(?,?,?,?,?,?,'PAYMENT',?,?,?,?,?)")){ps.setLong(1,id);ps.setBigDecimal(2,amount);ps.setString(3,method);ps.setString(4,paymentReference(tx,reference));ps.setInt(5,userId);ps.setString(6,userName);ps.setObject(7,deviceId);ps.setString(8,deviceName);setLong(ps,9,drawer.cashDrawerId());ps.setString(10,blank(drawer.drawerName()));setLong(ps,11,drawer.sessionId());ps.executeUpdate();}
                insertAllocation(c,tx,customerId,type,id,amount);append(applied,"custom order "+number+" "+amount);
            }else if("SALE".equals(type)){BigDecimal total,paid;try(PreparedStatement ps=c.prepareStatement("SELECT GREATEST(COALESCE(total_amount,0)-COALESCE(returned_amount,0),0),COALESCE(amount_paid,0) FROM sales WHERE sale_id=? AND customer_id=? AND payment_method='ACCOUNT' FOR UPDATE")){ps.setLong(1,id);ps.setInt(2,customerId);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw rule(409,"BALANCE_CHANGED","A selected sale is no longer available.");total=money(rs.getBigDecimal(1));paid=money(rs.getBigDecimal(2));}}
                if(amount.compareTo(total.subtract(paid))>0)throw rule(409,"BALANCE_CHANGED","A sale allocation exceeds its current balance.");try(PreparedStatement ps=c.prepareStatement("UPDATE sales SET amount_paid=?,payment_status=CASE WHEN ?>=GREATEST(COALESCE(total_amount,0)-COALESCE(returned_amount,0),0) THEN 'PAID' ELSE 'UNPAID' END WHERE sale_id=?")){ps.setBigDecimal(1,paid.add(amount));ps.setBigDecimal(2,paid.add(amount));ps.setLong(3,id);ps.executeUpdate();}insertAllocation(c,tx,customerId,type,id,amount);append(applied,"sale #"+id+" "+amount);
            }else if("INVOICE".equals(type)){String number;BigDecimal due;try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(NULLIF(invoice_number,''),invoice_id::text),COALESCE(balance_due,0) FROM invoices WHERE invoice_id=? AND customer_id=? FOR UPDATE")){ps.setLong(1,id);ps.setInt(2,customerId);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw rule(409,"BALANCE_CHANGED","A selected invoice is no longer available.");number=rs.getString(1);due=money(rs.getBigDecimal(2));}}
                if(amount.compareTo(due)>0)throw rule(409,"BALANCE_CHANGED","An invoice allocation exceeds its current balance.");try(PreparedStatement ps=c.prepareStatement("UPDATE invoices SET amount_paid=COALESCE(amount_paid,0)+?,balance_due=GREATEST(COALESCE(balance_due,0)-?,0),payment_status=CASE WHEN COALESCE(balance_due,0)-?<=0 THEN 'PAID' ELSE 'PARTIAL' END,updated_at=CURRENT_TIMESTAMP WHERE invoice_id=?")){ps.setBigDecimal(1,amount);ps.setBigDecimal(2,amount);ps.setBigDecimal(3,amount);ps.setLong(4,id);ps.executeUpdate();}
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO invoice_payments(invoice_id,customer_id,payment_amount,payment_method,payment_reference,taken_by_user_id,taken_by_name,location_id,device_id,device_name,cash_drawer_id,cash_drawer_name,cash_drawer_session_id) SELECT invoice_id,customer_id,?,?,?,?,?,location_id,?,?,?,?,? FROM invoices WHERE invoice_id=?")){ps.setBigDecimal(1,amount);ps.setString(2,method);ps.setString(3,paymentReference(tx,reference));ps.setInt(4,userId);ps.setString(5,userName);ps.setObject(6,deviceId);ps.setString(7,deviceName);setLong(ps,8,drawer.cashDrawerId());ps.setString(9,blank(drawer.drawerName()));setLong(ps,10,drawer.sessionId());ps.setLong(11,id);ps.executeUpdate();}
                insertAllocation(c,tx,customerId,type,id,amount);append(applied,"invoice "+number+" "+amount);
            }else if("MINUTES".equals(type)){PhoneMinutesService.applyPayment(c,customerId,id,tx,amount);append(applied,"minutes MIN-"+id+" "+amount);
            }else if("ACCOUNT".equals(type)){insertAllocation(c,tx,customerId,type,0,amount);append(applied,"account credit "+amount);
            }else throw rule(400,"VALIDATION_ERROR","An allocation document type is invalid.");}
        return "Customer payment applied to "+applied;
    }

    private static void insertAllocation(Connection c,long tx,int customerId,String type,long id,BigDecimal amount)throws SQLException{if("ACCOUNT".equals(type)){try(PreparedStatement ps=c.prepareStatement("INSERT INTO customer_account_payment_allocations(payment_transaction_id,customer_id,amount) VALUES(?,?,?)")){ps.setLong(1,tx);ps.setInt(2,customerId);ps.setBigDecimal(3,amount);ps.executeUpdate();}return;}String column=switch(type){case"SALE"->"sale_id";case"CUSTOM_ORDER"->"custom_order_id";case"INVOICE"->"invoice_id";default->throw new SQLException("Invalid allocation type.");};try(PreparedStatement ps=c.prepareStatement("INSERT INTO customer_account_payment_allocations(payment_transaction_id,customer_id,"+column+",amount) VALUES(?,?,?,?)")){ps.setLong(1,tx);ps.setInt(2,customerId);ps.setLong(3,id);ps.setBigDecimal(4,amount);ps.executeUpdate();}}

    static String deviceName(Connection c,UUID id)throws SQLException{try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(NULLIF(device_name,''),NULLIF(hostname,''),'LAN API Register') FROM devices WHERE device_id=?")){ps.setObject(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next()?rs.getString(1):"LAN API Register";}}}
    private static String paymentReference(long tx,String reference){return reference==null||reference.isBlank()?"Account payment transaction #"+tx:"Account payment transaction #"+tx+" / "+reference;}
    private static boolean requiresReference(String method){return "CARD".equals(method)||"CHEQUE".equals(method)||"MMG".equals(method);}
    private static void append(StringBuilder b,String s){if(!b.isEmpty())b.append("; ");b.append(s);}

    static Map<String,Object> transactions(Connection c,int customerId,int userId,int currentLocationId)throws Exception{
        return transactions(c,customerId,userId,currentLocationId,true);
    }

    @SuppressWarnings("unchecked")
    static List<Map<String,Object>> historyRows(Connection c,int customerId,int userId,int currentLocationId)throws Exception{
        return (List<Map<String,Object>>) transactions(c,customerId,userId,currentLocationId,false).get("transactions");
    }

    private static Map<String,Object> transactions(Connection c,int customerId,int userId,int currentLocationId,
                                                    boolean includeSignatures)throws Exception{
        require(c,userId,"CUSTOMER_ACCOUNTS");
        if(includeSignatures)CustomerAccountLedgerService.ensureSchema(c);
        List<Map<String,Object>>rows=new ArrayList<>();
        String sql="""
                SELECT * FROM (
                  SELECT 'LEDGER:'||t.transaction_id AS event_id,t.transaction_id,COALESCE(t.payment_id,''),t.created_at,t.location_id,
                    COALESCE(l.name,''),COALESCE(t.user_name,''),COALESCE(t.device_name,t.device_id,''),COALESCE(t.cash_drawer_name,''),
                    COALESCE(t.transaction_type,''),CASE WHEN t.minutes_id IS NOT NULL THEN 'MINUTES' WHEN t.invoice_id IS NOT NULL THEN 'INVOICE' WHEN t.custom_order_id IS NOT NULL THEN 'CUSTOM_ORDER'
                      WHEN t.sale_id IS NOT NULL THEN 'SALE' WHEN t.sales_order_id IS NOT NULL THEN 'SALES_ORDER' ELSE 'ACCOUNT' END,
                    COALESCE(t.minutes_id,t.invoice_id,t.custom_order_id,t.sales_order_id,t.sale_id::bigint),
                    COALESCE('MIN-'||m.minutes_id||COALESCE(' / '||NULLIF(m.destination_phone, ''), ''),i.invoice_number,co.order_number,s.receipt_number,t.payment_id,''),COALESCE(t.payment_method,''),COALESCE(t.payment_reference,''),
                    t.sale_id,t.custom_order_id,t.invoice_id,NULL::bigint,COALESCE(t.amount,0),COALESCE(t.note,''),
                    COALESCE(s.payment_status,co.payment_status,i.payment_status,CASE WHEN m.reversed_at IS NOT NULL THEN 'REVERSED' WHEN m.amount_paid>=m.amount THEN 'PAID' WHEN m.amount_paid>0 THEN 'PARTIAL' ELSE 'UNPAID' END,''),COALESCE(i.status,co.status,s.status,CASE WHEN m.reversed_at IS NOT NULL THEN 'REVERSED' ELSE 'ACTIVE' END,''),
                    COALESCE(s.total_amount,co.total_amount,i.total_amount,ABS(t.amount)),%s AS balance_delta
                  FROM customer_account_transactions t LEFT JOIN locations l ON l.location_id=t.location_id
                  LEFT JOIN sales s ON s.sale_id=t.sale_id LEFT JOIN custom_orders co ON co.custom_order_id=t.custom_order_id
                  LEFT JOIN invoices i ON i.invoice_id=t.invoice_id LEFT JOIN phone_minutes_sales m ON m.minutes_id=t.minutes_id WHERE t.customer_id=?
                  UNION ALL
                  SELECT 'SALE:'||s.sale_id,NULL::bigint,'',s.created_at,s.location_id,COALESCE(l.name,''),COALESCE(s.user_name,''),
                    COALESCE(s.device_id,''),COALESCE(s.cash_drawer_name,''),'SALE','SALE',s.sale_id::bigint,COALESCE(s.receipt_number,''),
                    COALESCE(s.payment_method,''),COALESCE(s.payment_reference,''),s.sale_id,NULL::bigint,NULL::bigint,NULL::bigint,
                    COALESCE(s.total_amount,0),'',COALESCE(s.payment_status,''),COALESCE(s.status,''),COALESCE(s.total_amount,0),COALESCE(s.total_amount,0)
                  FROM sales s LEFT JOIN locations l ON l.location_id=s.location_id WHERE s.customer_id=?
                  UNION ALL
                  SELECT 'CUSTOM_ORDER:'||o.custom_order_id,NULL::bigint,'',o.created_at,o.location_id,COALESCE(o.location_name,l.name,''),COALESCE(o.taken_by_name,''),
                    COALESCE(o.device_name,o.device_id,''),COALESCE(o.cash_drawer_name,''),'CUSTOM_ORDER','CUSTOM_ORDER',o.custom_order_id,COALESCE(o.order_number,''),
                    COALESCE(o.payment_method,''),COALESCE(o.payment_reference,''),NULL::integer,o.custom_order_id,NULL::bigint,NULL::bigint,
                    COALESCE(o.total_amount,0),COALESCE(o.order_notes,''),COALESCE(o.payment_status,''),COALESCE(o.status,''),COALESCE(o.total_amount,0),COALESCE(o.total_amount,0)
                  FROM custom_orders o LEFT JOIN locations l ON l.location_id=o.location_id WHERE o.customer_id=?
                  UNION ALL
                  SELECT 'QUOTATION:'||q.quotation_id,NULL::bigint,'',q.created_at,q.location_id,COALESCE(q.location_name,l.name,''),COALESCE(q.created_by_name,''),
                    COALESCE(q.device_name,q.device_id,''),'','QUOTATION','QUOTATION',q.quotation_id,COALESCE(q.quotation_number,''),'','',NULL::integer,NULL::bigint,NULL::bigint,q.quotation_id,
                    COALESCE(q.total_amount,0),COALESCE(q.quotation_notes,''),'',COALESCE(q.status,''),COALESCE(q.total_amount,0),0
                  FROM quotations q LEFT JOIN locations l ON l.location_id=q.location_id WHERE q.customer_id=?
                  UNION ALL
                  SELECT 'INVOICE:'||i.invoice_id,NULL::bigint,'',i.created_at,i.location_id,COALESCE(i.location_name,l.name,''),COALESCE(i.created_by_name,''),
                    COALESCE(i.device_name,i.device_id,''),COALESCE(i.cash_drawer_name,''),'INVOICE','INVOICE',i.invoice_id,COALESCE(i.invoice_number,''),
                    COALESCE(i.payment_method,''),COALESCE(i.payment_reference,''),NULL::integer,NULL::bigint,i.invoice_id,NULL::bigint,
                    COALESCE(i.total_amount,0),COALESCE(i.invoice_notes,''),COALESCE(i.payment_status,''),COALESCE(i.status,''),COALESCE(i.total_amount,0),COALESCE(i.balance_due,0)
                  FROM invoices i LEFT JOIN locations l ON l.location_id=i.location_id WHERE i.customer_id=?
                ) history ORDER BY created_at DESC,event_id DESC
                """.formatted(CustomerAccountLedgerService.balanceDeltaSql("t"));
        try(PreparedStatement ps=c.prepareStatement(sql)){for(int n=1;n<=5;n++)ps.setInt(n,customerId);try(ResultSet rs=ps.executeQuery()){while(rs.next()){
            Map<String,Object> row=historyMap(rs,false);Long transactionId=(Long)row.get("transactionId");if(includeSignatures&&transactionId!=null)row.put("authorization",authorization(c,transactionId));rows.add(row);}}}
        rows.addAll(CrossStoreCustomerHistoryService.rows(c,customerId,currentLocationId));
        Map<String,Map<String,Object>>unique=new LinkedHashMap<>();for(Map<String,Object> row:rows)unique.putIfAbsent(historyIdentity(row),row);
        rows=new ArrayList<>(unique.values());rows.sort((a,b)->Long.compare(((Number)b.get("createdAtEpochMillis")).longValue(),((Number)a.get("createdAtEpochMillis")).longValue()));
        BigDecimal charges=BigDecimal.ZERO,payments=BigDecimal.ZERO;for(Map<String,Object> row:rows){String type=String.valueOf(row.get("transactionType"));BigDecimal amount=money((BigDecimal)row.get("amount"));
            if(List.of("PAYMENT","RETURN","CUSTOM_ORDER_REFUND","MINUTES_REVERSAL").contains(type))payments=payments.add(amount.abs());
            else if(List.of("SALE_CREDIT","CUSTOM_ORDER_CREDIT","INVOICE_CREDIT","MANUAL_CHARGE","MINUTES_CREDIT").contains(type))charges=charges.add(amount.abs());}
        rows=new ArrayList<>(CustomerHistoryPresentation.consolidate(rows));
        return map("transactions",List.copyOf(rows),"count",rows.size(),"totalCharges",charges,"totalPayments",payments);
    }

    private static Map<String,Object> authorization(Connection c,long transactionId)throws SQLException{
        CustomerChargeAuthorizationService.purgeExpired(c);
        try(PreparedStatement p=c.prepareStatement("SELECT representative_name,signature_png,unsigned_reason,captured_at,signature_purged_at,COALESCE(captured_by_name,''),COALESCE(device_name,'') FROM customer_charge_authorizations WHERE transaction_id=?")){p.setLong(1,transactionId);try(ResultSet r=p.executeQuery()){if(!r.next())return null;byte[]png=r.getBytes(2);return map("representativeName",r.getString(1),"signaturePngBase64",png==null?null:java.util.Base64.getEncoder().encodeToString(png),"unsignedReason",r.getString(3),"capturedAtEpochMillis",epoch(r.getTimestamp(4)),"imageExpired",r.getTimestamp(5)!=null,"capturedByName",r.getString(6),"deviceName",r.getString(7));}}
    }

    private static String historyIdentity(Map<String,Object> row){String event=String.valueOf(row.get("eventId"));int marker=event.indexOf(':',7);
        if(event.startsWith("REMOTE:")&&marker>0)event=event.substring(marker+1);Object location=row.get("locationId");
        return String.valueOf(location)+"|"+event;}

    private static Map<String,Object> historyMap(ResultSet rs,boolean remote)throws SQLException{return map(
            "eventId",rs.getString(1),"transactionId",nullableLong(rs,2),"paymentId",rs.getString(3),"createdAtEpochMillis",epoch(rs.getTimestamp(4)),
            "locationId",nullableInt(rs,5),"storeName",rs.getString(6),"userName",rs.getString(7),"deviceName",rs.getString(8),"cashDrawerName",rs.getString(9),
            "transactionType",rs.getString(10),"documentType",rs.getString(11),"documentId",nullableLong(rs,12),"documentNumber",rs.getString(13),
            "paymentMethod",rs.getString(14),"paymentReference",rs.getString(15),"saleId",nullableInt(rs,16),"customOrderId",nullableLong(rs,17),
            "invoiceId",nullableLong(rs,18),"quotationId",nullableLong(rs,19),"amount",money(rs.getBigDecimal(20)),"note",rs.getString(21),
            "paymentStatus",rs.getString(22),"documentStatus",rs.getString(23),"chargeTotal",money(rs.getBigDecimal(24)),"remote",remote);}

    static Map<String,Object> payments(Connection c,int customerId,int userId)throws Exception{
        require(c,userId,"CUSTOMER_ACCOUNTS");CustomerAccountLedgerService.ensureSchema(c);
        List<Map<String,Object>>rows=new ArrayList<>();BigDecimal total=BigDecimal.ZERO,applied=BigDecimal.ZERO;String last="";int paymentCount=0;
        try(PreparedStatement ps=c.prepareStatement("""
                SELECT COALESCE(t.payment_id,'PAY-'||LPAD(t.transaction_id::text,6,'0')),t.transaction_id,t.created_at,
                  COALESCE(t.user_name,''),COALESCE(t.payment_method,''),COALESCE(t.payment_reference,''),
                  COALESCE(t.device_name,t.device_id,''),COALESCE(t.cash_drawer_name,''),ABS(COALESCE(t.amount,0)),
                  a.sale_id,a.custom_order_id,a.invoice_id,a.amount,
                  CASE WHEN a.sale_id IS NOT NULL THEN 'Sale #'||a.sale_id
                       WHEN a.custom_order_id IS NOT NULL THEN 'Custom Order '||COALESCE(NULLIF(co.order_number,''),a.custom_order_id::text)
                       WHEN a.minutes_id IS NOT NULL THEN 'Minutes MIN-'||a.minutes_id||COALESCE(' / '||NULLIF(m.destination_phone, ''), '')
                       WHEN a.invoice_id IS NOT NULL THEN 'Invoice '||COALESCE(NULLIF(i.invoice_number,''),a.invoice_id::text) ELSE '' END,
                  COALESCE(s.total_amount,co.total_amount,i.total_amount,m.amount,0),COALESCE(s.amount_paid,co.amount_paid,i.amount_paid,m.amount_paid,0),
                  COALESCE(s.payment_status,co.payment_status,i.payment_status,CASE WHEN m.reversed_at IS NOT NULL THEN 'REVERSED' WHEN m.amount_paid>=m.amount THEN 'PAID' ELSE 'PARTIAL' END,''),COALESCE(s.created_at,co.created_at,i.created_at,m.created_at)
                FROM customer_account_transactions t LEFT JOIN customer_account_payment_allocations a ON a.payment_transaction_id=t.transaction_id
                LEFT JOIN sales s ON a.sale_id=s.sale_id LEFT JOIN custom_orders co ON a.custom_order_id=co.custom_order_id
                LEFT JOIN invoices i ON a.invoice_id=i.invoice_id LEFT JOIN phone_minutes_sales m ON m.minutes_id=a.minutes_id WHERE t.customer_id=? AND t.transaction_type='PAYMENT'
                ORDER BY t.created_at DESC,t.transaction_id DESC,a.sale_id,a.custom_order_id,a.invoice_id
                """)){ps.setInt(1,customerId);try(ResultSet rs=ps.executeQuery()){while(rs.next()){
            String paymentId=rs.getString(1);BigDecimal amount=money(rs.getBigDecimal(9)),appliedAmount=rs.getBigDecimal(13);
            if(!paymentId.equals(last)){total=total.add(amount);paymentCount++;last=paymentId;}if(appliedAmount!=null)applied=applied.add(appliedAmount);
            rows.add(map("paymentId",paymentId,"transactionId",rs.getLong(2),"paymentDateEpochMillis",epoch(rs.getTimestamp(3)),
                    "userName",rs.getString(4),"paymentMethod",rs.getString(5),"paymentReference",rs.getString(6),
                    "deviceName",rs.getString(7),"cashDrawerName",rs.getString(8),"paymentAmount",amount,
                    "target",rs.getString(14),"appliedAmount",appliedAmount,"chargeTotal",money(rs.getBigDecimal(15)),
                    "chargePaid",money(rs.getBigDecimal(16)),"paymentStatus",rs.getString(17),
                    "chargeDateEpochMillis",epoch(rs.getTimestamp(18))));}}}
        try(var p=c.prepareStatement("""
            SELECT p.*,t.final_total,t.billing_status,t.status,t.created_at AS task_created_at,
                COALESCE((SELECT sum(CASE WHEN x.payment_action='REFUND' THEN -x.amount ELSE x.amount END)
                    FROM sales_task_payments x WHERE x.task_id=t.task_id),0) AS task_paid
            FROM sales_task_payments p JOIN sales_tasks t ON t.task_id=p.task_id
            WHERE p.customer_id=? ORDER BY p.created_at DESC,p.payment_id DESC
            """)) {
            p.setInt(1,customerId);try(var r=p.executeQuery()){while(r.next()) {
                BigDecimal amount=r.getBigDecimal("amount");if("REFUND".equals(r.getString("payment_action")))amount=amount.negate();
                total=total.add(amount);applied=applied.add(amount);paymentCount++;
                rows.add(map("paymentId","ST-"+r.getLong("task_id")+"/P-"+r.getLong("payment_id"),"transactionId",-r.getLong("payment_id"),
                    "paymentDateEpochMillis",epoch(r.getTimestamp("created_at")),"userName",r.getString("user_name"),"paymentMethod",r.getString("payment_method"),
                    "paymentReference",r.getString("payment_reference"),"deviceName",r.getString("device_name"),"cashDrawerName",r.getString("cash_drawer_name"),
                    "paymentAmount",amount,"target","Sales Task ST-"+r.getLong("task_id"),"appliedAmount",amount,"chargeTotal",r.getBigDecimal("final_total"),
                    "chargePaid",r.getBigDecimal("task_paid"),"paymentStatus","PROVISIONAL".equals(r.getString("billing_status"))?"PRICE_PENDING":r.getString("status"),
                    "chargeDateEpochMillis",epoch(r.getTimestamp("task_created_at"))));
            }}
        }
        rows.sort((left,right)->Long.compare(((Number)right.get("paymentDateEpochMillis")).longValue(),((Number)left.get("paymentDateEpochMillis")).longValue()));
        return map("payments",rows,"paymentCount",paymentCount,"rowCount",rows.size(),"totalPayments",total,"totalApplied",applied);
    }

    static Map<String,Object> receipt(Connection c,int customerId,long transactionId,int userId)throws Exception{
        require(c,userId,"CUSTOMER_ACCOUNTS");
        if(transactionId<0)return SalesTaskService.customerPaymentReceipt(c,customerId,-transactionId);
        AccountPaymentReceiptData r=ServerAccountPaymentReceiptBuilder.loadPaymentReceipt(c,customerId,transactionId);
        List<Map<String,Object>>allocations=new ArrayList<>();
        for(AccountPaymentReceiptData.AllocationLine line:r.getAllocations())allocations.add(map(
                "targetLabel",line.targetLabel(),"appliedAmount",line.appliedAmount(),"chargeTotal",line.chargeTotal(),
                "chargePaid",line.chargePaid(),"paymentStatus",line.paymentStatus(),
                "chargeDateEpochMillis",epoch(line.chargeDate())));
        return map("transactionId",r.getTransactionId(),"locationId",r.getLocationId(),"paymentId",r.getPaymentId(),
                "paymentTimeEpochMillis",epoch(r.getPaymentTime()),"storeName",r.getStoreName(),"userName",r.getUserName(),
                "customerName",r.getCustomerName(),"accountNumber",r.getAccountNumber(),"customerEmail",r.getCustomerEmail(),
                "paymentMethod",r.getPaymentMethod(),"paymentReference",r.getPaymentReference(),"deviceName",r.getDeviceName(),
                "cashDrawerName",r.getCashDrawerName(),"paymentAmount",r.getPaymentAmount(),
                "accountBalanceAfter",r.getAccountBalanceAfter(),"allocations",allocations);
    }

    private static Map<String,Object> withMinutes(Connection c,Map<String,Object> account)throws SQLException {
        int id=((Number)account.get("customerId")).intValue();account.put("minutesDue",PhoneMinutesService.minutesDue(c,id));account.put("accountCreditAvailable",PhoneMinutesService.availableCredit(c,id));return account;
    }
    private static Map<String,Object>account(ResultSet rs)throws SQLException{return map("customerId",rs.getInt(1),"accountNumber",rs.getString(2),
            "name",rs.getString(3),"phone",rs.getString(4),"email",rs.getString(5),"creditLimit",money(rs.getBigDecimal(6)),
            "currentBalance",money(rs.getBigDecimal(7)),"availableCredit",money(rs.getBigDecimal(8)),
            "customOrderDue",money(rs.getBigDecimal(9)),"totalDue",money(rs.getBigDecimal(10)),"business",rs.getBoolean(11),
            "active",rs.getBoolean(12),"accountNotes",rs.getString(13),"customerTypeId",nullableInt(rs,14),"customerTypeName",rs.getString(15),
            "customerCardTemplateSlot",rs.getInt(16),"customerSince",nullableInt(rs,17),"customerPhotoUrl",rs.getString(18),
            "customerCardIssuedOn",rs.getObject(19,java.time.LocalDate.class),"customerCardExpiresOn",rs.getObject(20,java.time.LocalDate.class),
            "whatsappOptIn",rs.getBoolean(21),"whatsappConsentPhone",rs.getString(22),"requireChargeAuthorization",rs.getBoolean(23),
            "salesDiscountEnabled",rs.getBoolean(24),"salesDiscountPercent",rs.getBigDecimal(25),
            "invoiceDiscountEnabled",rs.getBoolean(26),"invoiceDiscountPercent",rs.getBigDecimal(27),
            "customOrderDiscountEnabled",rs.getBoolean(28),"customOrderDiscountPercent",rs.getBigDecimal(29),"customOrderDepositExempt",rs.getBoolean(30),"additionalPhone1",rs.getString(31),"additionalPhone2",rs.getString(32),"phoneLabel",rs.getString(33),"additionalPhone1Label",rs.getString(34),"additionalPhone2Label",rs.getString(35));}
    private static Request parsed(JsonObject b)throws RuleViolation{try{Request r=GSON.fromJson(b,Request.class);if(r==null)throw rule(400,"VALIDATION_ERROR","Customer details are required.");return r;}catch(RuleViolation e){throw e;}catch(Exception e){throw rule(400,"VALIDATION_ERROR","Customer details are invalid.");}}
    private static void requireReference(Connection c,String table,String column,int id,String label)throws Exception{try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM "+table+" WHERE "+column+"=?")){ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw rule(400,"VALIDATION_ERROR",label+" was not found.");}}}
    static void require(Connection c,int u,String p)throws Exception{if(!has(c,u,p))throw rule(403,"PERMISSION_DENIED","You do not have permission to manage customer accounts.");}
    private static boolean has(Connection c,int u,String p)throws SQLException{try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions p ON p.permission_id=rp.permission_id WHERE u.user_id=? AND UPPER(p.permission_key)=? LIMIT 1")){ps.setInt(1,u);ps.setString(2,p);try(ResultSet rs=ps.executeQuery()){return rs.next();}}}
    static void audit(Connection c,String type,UUID d,int u,String details)throws SQLException{try(PreparedStatement ps=c.prepareStatement("INSERT INTO security_audit_events(event_type,device_id,actor_user_id,details) VALUES (?,?,?,?)")){ps.setString(1,type);ps.setObject(2,d);ps.setInt(3,u);ps.setString(4,details);ps.executeUpdate();}}
    static String chargeDescription(String action,String description)throws RuleViolation{
        return "CHARGE".equalsIgnoreCase(action)?required(description,1000,"Enter what the charge is for."):null;
    }
    private static String required(String v,int max,String m)throws RuleViolation{String x=clean(v,max);if(x.isBlank())throw rule(400,"VALIDATION_ERROR",m);return x;}
    private static String clean(String v,int max)throws RuleViolation{String x=v==null?"":v.trim();if(x.length()>max)throw rule(400,"VALIDATION_ERROR","A customer field is too long.");return x;}
    private static String blank(String v){return v==null||v.isBlank()?null:v;}
    private static BigDecimal money(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
    private static BigDecimal percent(BigDecimal v)throws RuleViolation{BigDecimal p=v==null?BigDecimal.ZERO:v;if(p.signum()<0||p.compareTo(BigDecimal.valueOf(100))>0)throw rule(400,"VALIDATION_ERROR","Customer discounts must be between 0 and 100%.");return p.setScale(4,java.math.RoundingMode.HALF_UP);}
    private static long epoch(Timestamp t){return t==null?0:t.getTime();}
    private static Integer nullableInt(ResultSet r,int i)throws SQLException{int v=r.getInt(i);return r.wasNull()?null:v;}
    private static Long nullableLong(ResultSet r,int i)throws SQLException{long v=r.getLong(i);return r.wasNull()?null:v;}
    private static void setInt(PreparedStatement p,int i,Integer v)throws SQLException{if(v==null)p.setNull(i,java.sql.Types.INTEGER);else p.setInt(i,v);}
    private static void setLong(PreparedStatement p,int i,Long v)throws SQLException{if(v==null)p.setNull(i,java.sql.Types.BIGINT);else p.setLong(i,v);}
    private static Map<String,Object>map(Object...v){Map<String,Object>m=new LinkedHashMap<>();for(int i=0;i<v.length;i+=2)m.put((String)v[i],v[i+1]);return m;}
    private static RuleViolation rule(int s,String c,String m){return new RuleViolation(s,c,m);}
    private record Request(Integer customerId,String accountNumber,String name,Integer customerTypeId,String phone,String email,
                           BigDecimal creditLimit,boolean active,String accountNotes,Integer customerSince,String customerPhotoUrl,boolean whatsappOptIn,boolean requireChargeAuthorization,
                           boolean salesDiscountEnabled,BigDecimal salesDiscountPercent,boolean invoiceDiscountEnabled,BigDecimal invoiceDiscountPercent,
                           boolean customOrderDiscountEnabled,BigDecimal customOrderDiscountPercent,Boolean customOrderDepositExempt,String additionalPhone1,String additionalPhone2,String phoneLabel,String additionalPhone1Label,String additionalPhone2Label){}
    private record Adjustment(int customerId,BigDecimal amount,String action,String paymentMethod,String paymentReference,List<PaymentAllocation> allocations,String description){}
    private record PaymentAllocation(String documentType,Long documentId,BigDecimal amount){}
    static final class RuleViolation extends Exception{private final int status;private final String code;private final String safeMessage;
        RuleViolation(int s,String c,String m){super(m);status=s;code=c;safeMessage=m;}int status(){return status;}String code(){return code;}String safeMessage(){return safeMessage;}}
}
