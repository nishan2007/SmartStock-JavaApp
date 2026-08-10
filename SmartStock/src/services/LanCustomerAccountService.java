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
        require(c,userId,"CUSTOMER_ACCOUNTS");CustomerAccountLedgerService.repairAllBalances(c);
        List<Map<String,Object>>rows=new ArrayList<>();
        try(PreparedStatement ps=c.prepareStatement("""
                SELECT ca.customer_id,COALESCE(ca.account_number,''),ca.name,COALESCE(ca.phone,''),
                  COALESCE(ca.email,''),COALESCE(ca.credit_limit,0),COALESCE(ca.current_balance,0),
                  COALESCE(ca.credit_limit,0)-COALESCE(ca.current_balance,0),COALESCE(ca.is_business,FALSE),
                  COALESCE(ca.is_active,TRUE),COALESCE(ca.account_notes,''),ca.customer_type_id,COALESCE(ct.name,'')
                FROM customer_accounts ca LEFT JOIN customer_types ct ON ct.customer_type_id=ca.customer_type_id
                ORDER BY ca.name
                """)){try(ResultSet rs=ps.executeQuery()){while(rs.next())rows.add(account(rs));}}
        return rows;
    }

    static Map<String,Object> details(Connection c,int customerId,int userId)throws Exception{
        require(c,userId,"CUSTOMER_ACCOUNTS");CustomerAccountLedgerService.repairCustomerBalance(c,customerId);
        try(PreparedStatement ps=c.prepareStatement("""
                SELECT ca.customer_id,COALESCE(ca.account_number,''),ca.name,COALESCE(ca.phone,''),
                  COALESCE(ca.email,''),COALESCE(ca.credit_limit,0),COALESCE(ca.current_balance,0),
                  COALESCE(ca.credit_limit,0)-COALESCE(ca.current_balance,0),COALESCE(ca.is_business,FALSE),
                  COALESCE(ca.is_active,TRUE),COALESCE(ca.account_notes,''),ca.customer_type_id,COALESCE(ct.name,'')
                FROM customer_accounts ca LEFT JOIN customer_types ct ON ct.customer_type_id=ca.customer_type_id
                WHERE ca.customer_id=?
                """)){ps.setInt(1,customerId);try(ResultSet rs=ps.executeQuery()){
            if(!rs.next())throw rule(404,"CUSTOMER_NOT_FOUND","Customer account was not found.");return account(rs);}}
    }

    static Map<String,Object> save(Connection c,JsonObject body,UUID deviceId,int userId)throws Exception{
        require(c,userId,"CUSTOMER_ACCOUNTS");Request r=parsed(body);String name=required(r.name(),200,"Customer name is required.");
        String phone=clean(r.phone(),100),email=clean(r.email(),320),notes=clean(r.accountNotes(),4000);
        BigDecimal credit=money(r.creditLimit());if(credit.signum()<0)throw rule(400,"VALIDATION_ERROR","Credit limit cannot be negative.");
        if(credit.signum()!=0&&!has(c,userId,"SET_CREDIT_LIMIT"))throw rule(403,"PERMISSION_DENIED","You do not have permission to set credit limits.");
        if(r.customerTypeId()!=null)requireReference(c,"customer_types","customer_type_id",r.customerTypeId(),"Customer type");
        int id;String number;
        if(r.customerId()==null){
            try(PreparedStatement ps=c.prepareStatement("""
                    INSERT INTO customer_accounts(name,customer_type_id,phone,email,credit_limit,current_balance,
                      is_business,is_active,account_notes) VALUES (?,?,?,?,?,0,?,?,?) RETURNING customer_id,account_number
                    """)){ps.setString(1,name);setInt(ps,2,r.customerTypeId());ps.setString(3,blank(phone));ps.setString(4,blank(email));
                ps.setBigDecimal(5,credit);ps.setBoolean(6,r.business());ps.setBoolean(7,r.active());ps.setString(8,blank(notes));
                try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new SQLException("Customer account could not be created.");id=rs.getInt(1);number=rs.getString(2);}}
        }else{
            id=r.customerId();String accountNumber=clean(r.accountNumber(),100);
            try(PreparedStatement lock=c.prepareStatement("SELECT account_number,credit_limit FROM customer_accounts WHERE customer_id=? FOR UPDATE")){
                lock.setInt(1,id);try(ResultSet rs=lock.executeQuery()){if(!rs.next())throw rule(404,"CUSTOMER_NOT_FOUND","Customer account was not found.");
                    String existingNumber=rs.getString(1);BigDecimal existingCredit=money(rs.getBigDecimal(2));
                    if(!has(c,userId,"EDIT_ACCOUNT_NUMBER"))accountNumber=existingNumber;
                    if(!has(c,userId,"SET_CREDIT_LIMIT"))credit=existingCredit;}}
            accountNumber=required(accountNumber,100,"Account number is required.");
            try(PreparedStatement ps=c.prepareStatement("""
                    UPDATE customer_accounts SET account_number=?,name=?,customer_type_id=?,phone=?,email=?,credit_limit=?,
                      is_business=?,is_active=?,account_notes=? WHERE customer_id=?
                    """)){ps.setString(1,accountNumber);ps.setString(2,name);setInt(ps,3,r.customerTypeId());ps.setString(4,blank(phone));
                ps.setString(5,blank(email));ps.setBigDecimal(6,credit);ps.setBoolean(7,r.business());ps.setBoolean(8,r.active());
                ps.setString(9,blank(notes));ps.setInt(10,id);ps.executeUpdate();number=accountNumber;}
        }
        audit(c,"LAN_CUSTOMER_ACCOUNT_SAVED",deviceId,userId,GSON.toJson(Map.of("customer_id",id,"account_number",number==null?"":number)));
        return map("customerId",id,"accountNumber",number==null?"":number);
    }

    static Map<String,Object> adjust(Connection c,JsonObject body,UUID deviceId,int userId,String userName,int locationId)throws Exception{
        require(c,userId,"CUSTOMER_ACCOUNTS");CustomerAccountLedgerService.ensureSchema(c);
        CustomerAccountLedgerService.requireCurrentMultiStoreBalance(c,locationId);
        Adjustment r;
        try{r=GSON.fromJson(body,Adjustment.class);}catch(Exception ex){throw rule(400,"VALIDATION_ERROR","Account adjustment details are invalid.");}
        if(r==null||r.customerId()<=0)throw rule(400,"VALIDATION_ERROR","Select a customer account.");
        BigDecimal amount=money(r.amount());if(amount.signum()<=0)throw rule(400,"VALIDATION_ERROR","Amount must be greater than zero.");
        String action=clean(r.action(),20).toUpperCase();boolean charge="CHARGE".equals(action);
        if(!charge&&!"PAYMENT".equals(action))throw rule(400,"VALIDATION_ERROR","Account adjustment type is invalid.");
        String method=charge?null:required(r.paymentMethod(),30,"Select a payment method.").toUpperCase();
        String reference=charge?null:clean(r.paymentReference(),200);
        if(!charge&&requiresReference(method)&&reference.isBlank())throw rule(400,"VALIDATION_ERROR","Payment reference is required for card, cheque, and MMG payments.");

        CustomerAccountLedgerService.repairCustomerBalance(c,r.customerId());
        BigDecimal balance,limit;
        try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(current_balance,0),COALESCE(credit_limit,0),COALESCE(is_active,TRUE) FROM customer_accounts WHERE customer_id=? FOR UPDATE")){
            ps.setInt(1,r.customerId());try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw rule(404,"CUSTOMER_NOT_FOUND","Customer account was not found.");
                balance=money(rs.getBigDecimal(1));limit=money(rs.getBigDecimal(2));if(!rs.getBoolean(3))throw rule(409,"ACCOUNT_INACTIVE","This customer account is inactive.");}}
        BigDecimal next=charge?balance.add(amount):balance.subtract(amount);
        if(next.signum()<0)throw rule(409,"PAYMENT_EXCEEDS_BALANCE","Payment is more than the current balance.");
        if(charge&&next.compareTo(limit)>0)throw rule(409,"CREDIT_LIMIT_EXCEEDED","Charge exceeds the customer's credit limit.");

        CashDrawerContext drawer=new CashDrawerContext(null,null);String deviceName=deviceName(c,deviceId);
        if(!charge&&"CASH".equals(method)){
            drawer=CashDrawerService.resolveDrawerForDevice(c,locationId,deviceId.toString());
            if(!drawer.isAssigned())throw rule(409,"CASH_DRAWER_REQUIRED","This register is not assigned to an active cash drawer for the selected store.");
            if(!drawer.hasActiveSession())throw rule(409,"CASH_SESSION_REQUIRED","No active draw session is open for "+drawer.drawerName()+".");
        }
        try(PreparedStatement ps=c.prepareStatement("UPDATE customer_accounts SET current_balance=?,updated_at=CURRENT_TIMESTAMP WHERE customer_id=?")){
            ps.setBigDecimal(1,next);ps.setInt(2,r.customerId());ps.executeUpdate();}
        long transactionId=insertAdjustment(c,r.customerId(),charge?amount:amount.negate(),charge?"MANUAL_CHARGE":"PAYMENT",
                charge?"Manual account charge":"Customer payment",method,reference,drawer,deviceId,deviceName,userName,locationId);
        String paymentId=charge?"":"PAY-"+String.format("%06d",transactionId);
        if(!charge){
            try(PreparedStatement ps=c.prepareStatement("UPDATE customer_account_transactions SET payment_id=? WHERE transaction_id=?")){
                ps.setString(1,paymentId);ps.setLong(2,transactionId);ps.executeUpdate();}
            String note=allocatePayment(c,r.customerId(),amount,transactionId,method,reference,drawer,deviceId,deviceName,userId,userName);
            try(PreparedStatement ps=c.prepareStatement("UPDATE customer_account_transactions SET note=? WHERE transaction_id=?")){
                ps.setString(1,note);ps.setLong(2,transactionId);ps.executeUpdate();}
        }
        audit(c,charge?"LAN_CUSTOMER_CHARGE_RECORDED":"LAN_CUSTOMER_PAYMENT_RECORDED",deviceId,userId,
                GSON.toJson(Map.of("customer_id",r.customerId(),"transaction_id",transactionId,"amount",amount,"balance_after",next)));
        return map("transactionId",transactionId,"paymentId",paymentId,"balanceAfter",next);
    }

    private static long insertAdjustment(Connection c,int customerId,BigDecimal amount,String type,String note,String method,String reference,
                                         CashDrawerContext drawer,UUID deviceId,String deviceName,String userName,int locationId)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("""
                INSERT INTO customer_account_transactions(customer_id,location_id,amount,transaction_type,note,user_name,device_id,device_name,
                  payment_method,payment_reference,cash_drawer_id,cash_drawer_name,cash_drawer_session_id)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING transaction_id
                """)){ps.setInt(1,customerId);ps.setInt(2,locationId);ps.setBigDecimal(3,amount);ps.setString(4,type);ps.setString(5,note);
            ps.setString(6,userName);ps.setObject(7,deviceId);ps.setString(8,deviceName);ps.setString(9,blank(method));ps.setString(10,blank(reference));
            setLong(ps,11,drawer.cashDrawerId());ps.setString(12,blank(drawer.drawerName()));setLong(ps,13,drawer.sessionId());
            try(ResultSet rs=ps.executeQuery()){if(rs.next())return rs.getLong(1);}}
        throw new SQLException("Failed to create account transaction.");
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
                  COALESCE(balance_due,COALESCE(total_amount,0)-COALESCE(amount_paid,0)) FROM custom_orders
                WHERE customer_id=? AND COALESCE(payment_status,'PAID')<>'PAID' AND COALESCE(balance_due,COALESCE(total_amount,0)-COALESCE(amount_paid,0))>0
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

    private static String deviceName(Connection c,UUID id)throws SQLException{try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(NULLIF(device_name,''),NULLIF(hostname,''),'LAN API Register') FROM devices WHERE device_id=?")){ps.setObject(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next()?rs.getString(1):"LAN API Register";}}}
    private static String paymentReference(long tx,String reference){return reference==null||reference.isBlank()?"Account payment transaction #"+tx:"Account payment transaction #"+tx+" / "+reference;}
    private static boolean requiresReference(String method){return "CARD".equals(method)||"CHEQUE".equals(method)||"MMG".equals(method);}
    private static void append(StringBuilder b,String s){if(!b.isEmpty())b.append("; ");b.append(s);}

    static Map<String,Object> transactions(Connection c,int customerId,int userId,int currentLocationId)throws Exception{
        require(c,userId,"CUSTOMER_ACCOUNTS");CustomerAccountLedgerService.ensureSchema(c);
        List<Map<String,Object>>rows=new ArrayList<>();
        String sql="""
                SELECT * FROM (
                  SELECT 'LEDGER:'||t.transaction_id AS event_id,t.transaction_id,COALESCE(t.payment_id,''),t.created_at,t.location_id,
                    COALESCE(l.name,''),COALESCE(t.user_name,''),COALESCE(t.device_name,t.device_id,''),COALESCE(t.cash_drawer_name,''),
                    COALESCE(t.transaction_type,''),CASE WHEN t.invoice_id IS NOT NULL THEN 'INVOICE' WHEN t.custom_order_id IS NOT NULL THEN 'CUSTOM_ORDER'
                      WHEN t.sale_id IS NOT NULL THEN 'SALE' WHEN t.sales_order_id IS NOT NULL THEN 'SALES_ORDER' ELSE 'ACCOUNT' END,
                    COALESCE(t.invoice_id,t.custom_order_id,t.sales_order_id,t.sale_id::bigint),
                    COALESCE(i.invoice_number,co.order_number,s.receipt_number,t.payment_id,''),COALESCE(t.payment_method,''),COALESCE(t.payment_reference,''),
                    t.sale_id,t.custom_order_id,t.invoice_id,NULL::bigint,COALESCE(t.amount,0),COALESCE(t.note,''),
                    COALESCE(s.payment_status,co.payment_status,i.payment_status,''),COALESCE(i.status,co.status,s.status,''),
                    COALESCE(s.total_amount,co.total_amount,i.total_amount,ABS(t.amount)),%s AS balance_delta
                  FROM customer_account_transactions t LEFT JOIN locations l ON l.location_id=t.location_id
                  LEFT JOIN sales s ON s.sale_id=t.sale_id LEFT JOIN custom_orders co ON co.custom_order_id=t.custom_order_id
                  LEFT JOIN invoices i ON i.invoice_id=t.invoice_id WHERE t.customer_id=?
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
            rows.add(historyMap(rs,false));}}}
        rows.addAll(CrossStoreCustomerHistoryService.rows(c,customerId,currentLocationId));
        Map<String,Map<String,Object>>unique=new LinkedHashMap<>();for(Map<String,Object> row:rows)unique.putIfAbsent(historyIdentity(row),row);
        rows=new ArrayList<>(unique.values());rows.sort((a,b)->Long.compare(((Number)b.get("createdAtEpochMillis")).longValue(),((Number)a.get("createdAtEpochMillis")).longValue()));
        BigDecimal charges=BigDecimal.ZERO,payments=BigDecimal.ZERO;for(Map<String,Object> row:rows){String type=String.valueOf(row.get("transactionType"));BigDecimal amount=money((BigDecimal)row.get("amount"));
            if(List.of("PAYMENT","RETURN","CUSTOM_ORDER_REFUND").contains(type))payments=payments.add(amount.abs());
            else if(List.of("SALE_CREDIT","CUSTOM_ORDER_CREDIT","INVOICE_CREDIT","MANUAL_CHARGE").contains(type))charges=charges.add(amount.abs());}
        return map("transactions",List.copyOf(rows),"count",rows.size(),"totalCharges",charges,"totalPayments",payments);
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
                       WHEN a.invoice_id IS NOT NULL THEN 'Invoice '||COALESCE(NULLIF(i.invoice_number,''),a.invoice_id::text) ELSE '' END,
                  COALESCE(s.total_amount,co.total_amount,i.total_amount,0),COALESCE(s.amount_paid,co.amount_paid,i.amount_paid,0),
                  COALESCE(s.payment_status,co.payment_status,i.payment_status,''),COALESCE(s.created_at,co.created_at,i.created_at)
                FROM customer_account_transactions t LEFT JOIN customer_account_payment_allocations a ON a.payment_transaction_id=t.transaction_id
                LEFT JOIN sales s ON a.sale_id=s.sale_id LEFT JOIN custom_orders co ON a.custom_order_id=co.custom_order_id
                LEFT JOIN invoices i ON a.invoice_id=i.invoice_id WHERE t.customer_id=? AND t.transaction_type='PAYMENT'
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
        return map("payments",rows,"paymentCount",paymentCount,"rowCount",rows.size(),"totalPayments",total,"totalApplied",applied);
    }

    static Map<String,Object> receipt(Connection c,int customerId,long transactionId,int userId)throws Exception{
        require(c,userId,"CUSTOMER_ACCOUNTS");
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

    private static Map<String,Object>account(ResultSet rs)throws SQLException{return map("customerId",rs.getInt(1),"accountNumber",rs.getString(2),
            "name",rs.getString(3),"phone",rs.getString(4),"email",rs.getString(5),"creditLimit",money(rs.getBigDecimal(6)),
            "currentBalance",money(rs.getBigDecimal(7)),"availableCredit",money(rs.getBigDecimal(8)),"business",rs.getBoolean(9),
            "active",rs.getBoolean(10),"accountNotes",rs.getString(11),"customerTypeId",nullableInt(rs,12),"customerTypeName",rs.getString(13));}
    private static Request parsed(JsonObject b)throws RuleViolation{try{Request r=GSON.fromJson(b,Request.class);if(r==null)throw rule(400,"VALIDATION_ERROR","Customer details are required.");return r;}catch(RuleViolation e){throw e;}catch(Exception e){throw rule(400,"VALIDATION_ERROR","Customer details are invalid.");}}
    private static void requireReference(Connection c,String table,String column,int id,String label)throws Exception{try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM "+table+" WHERE "+column+"=?")){ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw rule(400,"VALIDATION_ERROR",label+" was not found.");}}}
    private static void require(Connection c,int u,String p)throws Exception{if(!has(c,u,p))throw rule(403,"PERMISSION_DENIED","You do not have permission to manage customer accounts.");}
    private static boolean has(Connection c,int u,String p)throws SQLException{try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions p ON p.permission_id=rp.permission_id WHERE u.user_id=? AND UPPER(p.permission_key)=? LIMIT 1")){ps.setInt(1,u);ps.setString(2,p);try(ResultSet rs=ps.executeQuery()){return rs.next();}}}
    private static void audit(Connection c,String type,UUID d,int u,String details)throws SQLException{try(PreparedStatement ps=c.prepareStatement("INSERT INTO security_audit_events(event_type,device_id,actor_user_id,details) VALUES (?,?,?,?)")){ps.setString(1,type);ps.setObject(2,d);ps.setInt(3,u);ps.setString(4,details);ps.executeUpdate();}}
    private static String required(String v,int max,String m)throws RuleViolation{String x=clean(v,max);if(x.isBlank())throw rule(400,"VALIDATION_ERROR",m);return x;}
    private static String clean(String v,int max)throws RuleViolation{String x=v==null?"":v.trim();if(x.length()>max)throw rule(400,"VALIDATION_ERROR","A customer field is too long.");return x;}
    private static String blank(String v){return v==null||v.isBlank()?null:v;}
    private static BigDecimal money(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
    private static long epoch(Timestamp t){return t==null?0:t.getTime();}
    private static Integer nullableInt(ResultSet r,int i)throws SQLException{int v=r.getInt(i);return r.wasNull()?null:v;}
    private static Long nullableLong(ResultSet r,int i)throws SQLException{long v=r.getLong(i);return r.wasNull()?null:v;}
    private static void setInt(PreparedStatement p,int i,Integer v)throws SQLException{if(v==null)p.setNull(i,java.sql.Types.INTEGER);else p.setInt(i,v);}
    private static void setLong(PreparedStatement p,int i,Long v)throws SQLException{if(v==null)p.setNull(i,java.sql.Types.BIGINT);else p.setLong(i,v);}
    private static Map<String,Object>map(Object...v){Map<String,Object>m=new LinkedHashMap<>();for(int i=0;i<v.length;i+=2)m.put((String)v[i],v[i+1]);return m;}
    private static RuleViolation rule(int s,String c,String m){return new RuleViolation(s,c,m);}
    private record Request(Integer customerId,String accountNumber,String name,Integer customerTypeId,String phone,String email,
                           BigDecimal creditLimit,boolean business,boolean active,String accountNotes){}
    private record Adjustment(int customerId,BigDecimal amount,String action,String paymentMethod,String paymentReference){}
    static final class RuleViolation extends Exception{private final int status;private final String code;private final String safeMessage;
        RuleViolation(int s,String c,String m){super(m);status=s;code=c;safeMessage=m;}int status(){return status;}String code(){return code;}String safeMessage(){return safeMessage;}}
}
