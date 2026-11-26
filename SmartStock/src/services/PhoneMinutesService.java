package services;

import com.google.gson.JsonObject;
import models.CashDrawerContext;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Store-server authority for minutes already sent; never calls a telecom provider. */
final class PhoneMinutesService {
    private PhoneMinutesService() { }

    static String phone(String value) throws SQLException {
        String phone=value==null?"":value.trim().replaceAll("[ ()-]", "");
        if(!phone.matches("\\+?[0-9]{7,15}"))throw new SQLException("Enter a destination phone number containing 7 to 15 digits.","22023");
        return phone;
    }
    static BigDecimal amount(BigDecimal amount)throws SQLException {
        if(amount==null||amount.signum()<=0||amount.stripTrailingZeros().scale()>0||amount.compareTo(new BigDecimal("9999999999"))>0)
            throw new SQLException("Enter a positive whole-GYD amount.","22023");
        return amount;
    }
    static String dueSql(String customerExpression) {
        return "(COALESCE((SELECT SUM(pm.amount-pm.amount_paid) FROM phone_minutes_sales pm WHERE pm.customer_id="+customerExpression+" AND pm.payment_method='CREDIT' AND pm.reversed_at IS NULL),0)+COALESCE((SELECT SUM(mr.document_balance) FROM sync_cross_store_customer_history_cache mr WHERE mr.customer_id="+customerExpression+" AND mr.event_type='MINUTES' AND mr.cache_status='CURRENT'),0))";
    }
    static BigDecimal minutesDue(Connection c,int customer)throws SQLException {
        try(var p=c.prepareStatement("SELECT COALESCE((SELECT SUM(amount-amount_paid) FROM phone_minutes_sales WHERE customer_id=? AND payment_method='CREDIT' AND reversed_at IS NULL),0)+COALESCE((SELECT SUM(document_balance) FROM sync_cross_store_customer_history_cache WHERE customer_id=? AND event_type='MINUTES' AND cache_status='CURRENT'),0)")) {
            p.setInt(1,customer);p.setInt(2,customer);try(var r=p.executeQuery()){r.next();return r.getBigDecimal(1);}
        }
    }
    static BigDecimal availableCredit(Connection c,int customer)throws SQLException {
        try(var p=c.prepareStatement("SELECT COALESCE(SUM(credit_remaining),0) FROM phone_minutes_sales WHERE customer_id=?")) {
            p.setInt(1,customer);try(var r=p.executeQuery()){r.next();return r.getBigDecimal(1);}
        }
    }
    static void lockCustomer(Connection c,int customer,boolean active)throws SQLException {
        try(var p=c.prepareStatement("SELECT is_active FROM customer_accounts WHERE customer_id=? FOR UPDATE")) {
            p.setInt(1,customer);try(var r=p.executeQuery()){if(!r.next()||(active&&!r.getBoolean(1)))throw new SQLException("Select an active customer account.","22023");}
        }
    }
    private static CashDrawerContext drawer(Connection c,int location,UUID device)throws SQLException {
        var drawer=CashDrawerService.resolveDrawerForDevice(c,location,device.toString());
        if(!drawer.isAssigned()||!drawer.hasActiveSession())throw new SQLException("An assigned cash drawer with an active session is required.","22023");
        return drawer;
    }
    record BrowserOrigin(UUID browser,String name,Long drawerSessionId) { }
    static CashDrawerContext browserDrawer(Connection c,int location,int user,Long session)throws SQLException {
        if(session==null)throw new SQLException("Choose your open cash drawer before recording cash minutes.","22023");
        var locked=CashDrawerService.getSessionForUpdate(c,session);
        if(locked==null||!locked.isOpen()||locked.locationId()!=location||!java.util.Objects.equals(locked.currentCashierUserId(),user)||CashDrawerHandoverService.pending(c,session)!=null)
            throw new SQLException("This cash drawer is not open and assigned to you at this store.","22023");
        try(var p=c.prepareStatement("SELECT 1 FROM cash_drawers WHERE cash_drawer_id=? AND location_id=? AND is_active")){p.setLong(1,locked.cashDrawerId());p.setInt(2,location);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("The cash drawer is no longer active.","22023");}}
        return new CashDrawerContext(locked.cashDrawerId(),locked.drawerName(),session);
    }
    static Map<String,Object> create(Connection c,JsonObject body,UUID device,int user,String name,int location)throws Exception {return create(c,body,device,user,name,location,null);}
    static Map<String,Object> create(Connection c,JsonObject body,UUID device,int user,String name,int location,BrowserOrigin browser)throws Exception {
        LanCustomerAccountService.require(c,user,"CUSTOMER_ACCOUNTS");
        BigDecimal amount=amount(body.has("amount")?body.get("amount").getAsBigDecimal():null);
        String method=body.has("method")?body.get("method").getAsString():"";
        if(!Set.of("CASH","CREDIT").contains(method))throw new SQLException("Choose Cash or Credit.","22023");
        Integer customer=body.has("customerId")&&!body.get("customerId").isJsonNull()?body.get("customerId").getAsInt():null;
        if("CREDIT".equals(method)&&customer==null)throw new SQLException("Credit minutes require a customer account.","22023");
        var phoneValue = body.get("phone");
        String enteredPhone = phoneValue == null || phoneValue.isJsonNull() ? "" : phoneValue.getAsString();
        String phone = customer != null && enteredPhone.isBlank() ? "" : phone(enteredPhone);
        // Minutes have no credit ceiling: stale remote balances must not block local recording.
        if(customer!=null){lockCustomer(c,customer,true);CustomerAccountLedgerService.repairCustomerBalance(c,customer);}
        CashDrawerContext drawer="CASH".equals(method)?(browser==null?drawer(c,location,device):browserDrawer(c,location,user,browser.drawerSessionId())):new CashDrawerContext(null,null);
        String deviceName=browser==null?LanCustomerAccountService.deviceName(c,device):browser.name();UUID identity=browser==null?device:browser.browser();
        long id;
        try(var p=c.prepareStatement("INSERT INTO phone_minutes_sales(location_id,customer_id,destination_phone,amount,amount_paid,payment_method,user_id,user_name,device_id,device_name,cash_drawer_id,cash_drawer_name,cash_drawer_session_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING minutes_id")) {
            p.setInt(1,location);p.setObject(2,customer);p.setString(3,phone);p.setBigDecimal(4,amount);p.setBigDecimal(5,"CASH".equals(method)?amount:BigDecimal.ZERO);p.setString(6,method);p.setInt(7,user);p.setString(8,name);p.setString(9,identity.toString());p.setString(10,deviceName);p.setObject(11,drawer.cashDrawerId());p.setString(12,drawer.drawerName());p.setObject(13,drawer.sessionId());try(var r=p.executeQuery()){r.next();id=r.getLong(1);}
        }
        if(customer!=null){long tx=LanCustomerAccountService.insertAdjustment(c,customer,amount,BigDecimal.ZERO,"CREDIT".equals(method)?"MINUTES_CREDIT":"MINUTES_PAID","Phone minutes MIN-"+id+(phone.isEmpty()?"":" to "+phone),method,"MIN-"+id,drawer,identity,deviceName,name,location);link(c,tx,id);CustomerAccountLedgerService.repairCustomerBalance(c,customer);}
        LanCustomerAccountService.audit(c,"PHONE_MINUTES_CREATED",device,user,"MIN-"+id+" / "+method+" / "+amount);
        return Map.of("minutesId",id,"reference","MIN-"+id);
    }
    private static void link(Connection c,long tx,long id)throws SQLException {try(var p=c.prepareStatement("UPDATE customer_account_transactions SET minutes_id=? WHERE transaction_id=?")){p.setLong(1,id);p.setLong(2,tx);p.executeUpdate();}}

    static Map<String,Object> reverse(Connection c,JsonObject body,UUID device,int user,String name,int location)throws Exception {return reverse(c,body,device,user,name,location,null);}
    static Map<String,Object> reverse(Connection c,JsonObject body,UUID device,int user,String name,int location,BrowserOrigin browser)throws Exception {
        LanCustomerAccountService.require(c,user,"CUSTOMER_ACCOUNTS");LanCustomerAccountService.require(c,user,"REVERSE_PHONE_MINUTES");
        long id=body.get("minutesId").getAsLong();String reason=body.has("reason")?body.get("reason").getAsString().trim():"";
        if(reason.isBlank()||reason.length()>1000)throw new SQLException("Enter a reversal reason (up to 1000 characters).","22023");
        Integer customer;
        try(var p=c.prepareStatement("SELECT customer_id FROM phone_minutes_sales WHERE minutes_id=? AND location_id=?")){p.setLong(1,id);p.setInt(2,location);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Minutes sale was not found at this store.","22023");customer=(Integer)r.getObject(1);}}
        if(customer!=null)lockCustomer(c,customer,false);
        BigDecimal amount,paid;String method,phone;
        try(var p=c.prepareStatement("SELECT amount,amount_paid,payment_method,destination_phone,reversed_at FROM phone_minutes_sales WHERE minutes_id=? FOR UPDATE")){p.setLong(1,id);try(var r=p.executeQuery()){r.next();if(r.getTimestamp(5)!=null)throw new SQLException("This minutes sale is already reversed.","22023");amount=r.getBigDecimal(1);paid=r.getBigDecimal(2);method=r.getString(3);phone=r.getString(4);}}
        CashDrawerContext drawer="CASH".equals(method)?(browser==null?drawer(c,location,device):browserDrawer(c,location,user,browser.drawerSessionId())):new CashDrawerContext(null,null);String deviceName=browser==null?LanCustomerAccountService.deviceName(c,device):browser.name();UUID identity=browser==null?device:browser.browser();
        try(var p=c.prepareStatement("UPDATE phone_minutes_sales SET reversed_at=now(),reversed_by=?,reversed_by_name=?,reversal_reason=?,reversal_device_id=?,reversal_device_name=?,reversal_cash_drawer_id=?,reversal_cash_drawer_name=?,reversal_cash_drawer_session_id=?,credit_remaining=? WHERE minutes_id=?")) {
            p.setInt(1,user);p.setString(2,name);p.setString(3,reason);p.setString(4,identity.toString());p.setString(5,deviceName);p.setObject(6,drawer.cashDrawerId());p.setString(7,drawer.drawerName());p.setObject(8,drawer.sessionId());p.setBigDecimal(9,"CREDIT".equals(method)?paid:BigDecimal.ZERO);p.setLong(10,id);p.executeUpdate();
        }
        if(customer!=null){long tx=LanCustomerAccountService.insertAdjustment(c,customer,"CREDIT".equals(method)?amount.subtract(paid).negate():BigDecimal.ZERO,BigDecimal.ZERO,"MINUTES_REVERSAL","Reversed MIN-"+id+(phone.isEmpty()?"":" to "+phone)+": "+reason,method,"MIN-"+id+"/REV",drawer,identity,deviceName,name,location);link(c,tx,id);CustomerAccountLedgerService.repairCustomerBalance(c,customer);}
        LanCustomerAccountService.audit(c,"PHONE_MINUTES_REVERSED",device,user,"MIN-"+id+": "+reason);
        return Map.of("reversed",true,"releasedCredit","CREDIT".equals(method)?paid:BigDecimal.ZERO);
    }
    static void applyPayment(Connection c,int customer,long id,long tx,BigDecimal amount)throws SQLException {
        try(var p=c.prepareStatement("UPDATE phone_minutes_sales SET amount_paid=amount_paid+? WHERE minutes_id=? AND customer_id=? AND payment_method='CREDIT' AND reversed_at IS NULL AND amount-amount_paid>=?")){p.setBigDecimal(1,amount);p.setLong(2,id);p.setInt(3,customer);p.setBigDecimal(4,amount);if(p.executeUpdate()!=1)throw new SQLException("The selected minutes balance changed. Refresh and try again.","22023");}
        try(var p=c.prepareStatement("INSERT INTO customer_account_payment_allocations(payment_transaction_id,customer_id,minutes_id,amount) VALUES(?,?,?,?)")){p.setLong(1,tx);p.setInt(2,customer);p.setLong(3,id);p.setBigDecimal(4,amount);p.executeUpdate();}
    }
    static void consumeCredit(Connection c,int customer,BigDecimal amount,long paymentTransaction)throws SQLException {
        BigDecimal remaining=amount;
        try(var p=c.prepareStatement("SELECT minutes_id,credit_remaining FROM phone_minutes_sales WHERE customer_id=? AND credit_remaining>0 ORDER BY minutes_id FOR UPDATE")){p.setInt(1,customer);try(var r=p.executeQuery()){while(r.next()&&remaining.signum()>0){BigDecimal used=remaining.min(r.getBigDecimal(2));try(var u=c.prepareStatement("UPDATE phone_minutes_sales SET credit_remaining=credit_remaining-? WHERE minutes_id=?")){u.setBigDecimal(1,used);u.setLong(2,r.getLong(1));u.executeUpdate();}try(var log=c.prepareStatement("INSERT INTO phone_minutes_credit_uses(minutes_id,payment_transaction_id,amount) VALUES(?,?,?)")){log.setLong(1,r.getLong(1));log.setLong(2,paymentTransaction);log.setBigDecimal(3,used);log.executeUpdate();}remaining=remaining.subtract(used);}}}
        if(remaining.signum()>0)throw new SQLException("The available minutes reversal credit changed. Refresh and try again.","22023");
    }
    static List<Map<String,Object>> history(Connection c,JsonObject body,int user,int location)throws Exception {
        LanCustomerAccountService.require(c,user,"CUSTOMER_ACCOUNTS");
        Integer customer=body.has("customerId")&&!body.get("customerId").isJsonNull()?body.get("customerId").getAsInt():null;
        String search=body.has("search")?body.get("search").getAsString().trim():"";if(search.length()>100)throw new SQLException("Search is too long.","22023");
        int offset=body.has("offset")?body.get("offset").getAsInt():0;if(offset<0)throw new SQLException("Invalid history offset.","22023");
        java.time.LocalDate from,to;try{String start=body.has("from")?body.get("from").getAsString():"",end=body.has("to")?body.get("to").getAsString():"";from=start.isBlank()?java.time.LocalDate.of(1900,1,1):java.time.LocalDate.parse(start);to=end.isBlank()?java.time.LocalDate.of(9999,12,30):java.time.LocalDate.parse(end);if(to.isBefore(from))throw new IllegalArgumentException();}catch(Exception e){throw new SQLException("Enter a valid date range (YYYY-MM-DD).","22023");}
        String filterMethod=body.has("filterMethod")?body.get("filterMethod").getAsString():"";if(!Set.of("","CASH","CREDIT").contains(filterMethod))throw new SQLException("Invalid payment filter.","22023");
        List<Map<String,Object>> rows=new ArrayList<>();
        try(var p=c.prepareStatement("SELECT m.*,COALESCE(c.name,'Walk-in') customer_name,COALESCE(l.name,'') store_name FROM phone_minutes_sales m LEFT JOIN customer_accounts c ON c.customer_id=m.customer_id LEFT JOIN locations l ON l.location_id=m.location_id WHERE m.location_id=? AND (?::integer IS NULL OR m.customer_id=?) AND (m.destination_phone ILIKE ? OR c.name ILIKE ? OR ('MIN-'||m.minutes_id) ILIKE ?) AND m.created_at >= ?::date::timestamp AT TIME ZONE COALESCE(l.timezone,'America/Guyana') AND m.created_at < (?::date+1)::timestamp AT TIME ZONE COALESCE(l.timezone,'America/Guyana') AND (?='' OR m.payment_method=?) ORDER BY m.created_at DESC,m.minutes_id DESC LIMIT 1000 OFFSET ?")) {
            p.setInt(1,location);p.setObject(2,customer);p.setObject(3,customer);p.setObject(7,from);p.setObject(8,to);p.setString(9,filterMethod);p.setString(10,filterMethod);p.setInt(11,offset);for(int i=4;i<=6;i++)p.setString(i,"%"+search+"%");
            try(var r=p.executeQuery()){while(r.next()){Map<String,Object> row=new LinkedHashMap<>();row.put("minutesId",r.getLong("minutes_id"));row.put("reference","MIN-"+r.getLong("minutes_id"));row.put("customerName",r.getString("customer_name"));row.put("phone",r.getString("destination_phone"));row.put("amount",r.getBigDecimal("amount"));row.put("paid",r.getBigDecimal("amount_paid"));row.put("balanceDue",r.getTimestamp("reversed_at")==null?r.getBigDecimal("amount").subtract(r.getBigDecimal("amount_paid")):BigDecimal.ZERO);row.put("method",r.getString("payment_method"));row.put("createdAtEpochMillis",r.getTimestamp("created_at").getTime());row.put("userName",r.getString("user_name"));row.put("storeName",r.getString("store_name"));row.put("reversed",r.getTimestamp("reversed_at")!=null);row.put("reversalReason",r.getString("reversal_reason"));row.put("creditRemaining",r.getBigDecimal("credit_remaining"));rows.add(row);}}
        }return rows;
    }
}
