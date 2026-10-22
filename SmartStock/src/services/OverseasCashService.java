package services;

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Server-owned cash compartment and store fund. Call within the caller's transaction. */
public final class OverseasCashService {
    private OverseasCashService() {}
    public record DrawerCash(boolean enabled, BigDecimal expected, BigDecimal counted, String reason) {}
    public record FundLine(String date, String description, BigDecimal received, BigDecimal expense) {}
    public record FundReport(BigDecimal broughtForward, BigDecimal received, BigDecimal expenses,
                             BigDecimal carriedForward, List<FundLine> lines, CashVerification verification) {
        public FundReport(BigDecimal bf,BigDecimal received,BigDecimal expenses,BigDecimal cf,List<FundLine> lines){this(bf,received,expenses,cf,lines,null);}
    }
    public record CashVerification(BigDecimal expected,BigDecimal counted,BigDecimal variance,
                                   Map<String,Integer> denominations,String reason,String verifiedBy,String verifiedAt) {}
    public static final List<Integer> COUNT_DENOMINATIONS=List.of(5000,2000,1000,500,100,50,20);
    /** Totals and difference are always calculated here, never accepted from the client. */
    public static FundReport verify(FundReport report,JsonObject input,String user,String timestamp)throws SQLException {
        if(report==null||input==null||!input.has("denominations")||!input.get("denominations").isJsonObject())throw new SQLException("Enter the overseas cash denomination count.");
        BigDecimal expected;
        try{expected=input.get("expected").getAsBigDecimal();}catch(Exception e){throw new SQLException("Reload the overseas cash balance before verifying.");}
        if(expected.compareTo(report.carriedForward())!=0)throw new SQLException("The overseas cash balance changed. Refresh the Balance Sheet and verify your count again.");
        Map<String,Integer> counts=new LinkedHashMap<>();BigDecimal counted=BigDecimal.ZERO;
        for(var e:input.getAsJsonObject("denominations").entrySet()) {
            int denomination,quantity;
            try{denomination=Integer.parseInt(e.getKey());quantity=e.getValue().getAsBigDecimal().intValueExact();}catch(Exception ex){throw new SQLException("Cash quantities must be non-negative whole numbers.");}
            if(!COUNT_DENOMINATIONS.contains(denomination)||!e.getKey().equals(Integer.toString(denomination))||quantity<0)throw new SQLException("Invalid overseas cash denomination or quantity.");
            counts.put(e.getKey(),quantity);counted=counted.add(BigDecimal.valueOf(denomination).multiply(BigDecimal.valueOf(quantity)));
        }
        if(counts.size()!=COUNT_DENOMINATIONS.size())throw new SQLException("Enter a quantity for every overseas cash denomination (zero if none).");
        money(counted);BigDecimal variance=counted.subtract(report.carriedForward());
        String reason=input.has("reason")&&!input.get("reason").isJsonNull()?input.get("reason").getAsString().trim():"";
        if(reason.length()>2000||variance.signum()!=0&&reason.isBlank())throw new SQLException("A reason is required for an overseas cash shortage or overage (maximum 2000 characters).");
        return new FundReport(report.broughtForward(),report.received(),report.expenses(),report.carriedForward(),report.lines(),
            new CashVerification(report.carriedForward(),counted,variance,Map.copyOf(counts),reason,user,timestamp));
    }
    static BigDecimal money(BigDecimal value) throws SQLException {
        if(value==null||value.signum()<0||value.scale()>2||value.precision()-value.scale()>12)
            throw new SQLException("Enter a non-negative cash amount with at most two decimal places.");
        return value.setScale(2);
    }
    public static DrawerCash drawer(Connection c,long id)throws SQLException {
        try(var p=c.prepareStatement("SELECT handles_overseas_cash,overseas_counted,overseas_variance_reason FROM cash_drawer_sessions WHERE cash_drawer_session_id=?")) {
            p.setLong(1,id);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Drawer session was not found.");
                return new DrawerCash(r.getBoolean(1),expected(c,id),r.getBigDecimal(2),r.getString(3));}
        }
    }
    static BigDecimal expected(Connection c,long id)throws SQLException {
        try(var p=c.prepareStatement("SELECT COALESCE(SUM(CASE WHEN payment_action IN ('REFUND','REVERSAL') THEN -payment_amount ELSE payment_amount END),0) FROM custom_order_payments WHERE cash_drawer_session_id=? AND overseas_cash_separate")) {
            p.setLong(1,id);try(var r=p.executeQuery()){r.next();return r.getBigDecimal(1);}
        }
    }
    /** Classification is made once at insertion, while holding the same session lock as close. */
    static boolean classify(Connection c,long order,String method,Long session,BigDecimal amount,String action)throws SQLException {
        if(!"CASH".equals(method))return false;
        try(var p=c.prepareStatement("SELECT order_type FROM custom_orders WHERE custom_order_id=?")) {
            p.setLong(1,order);try(var r=p.executeQuery()){if(!r.next()||!"OVERSEAS".equals(r.getString(1)))return false;}
        }
        if(session==null)throw new SQLException("Open a drawer enabled for overseas cash first.");
        try(var p=c.prepareStatement("SELECT handles_overseas_cash,status FROM cash_drawer_sessions WHERE cash_drawer_session_id=? FOR UPDATE")) {
            p.setLong(1,session);try(var r=p.executeQuery()){if(!r.next()||!r.getBoolean(1)||!"OPEN".equals(r.getString(2)))
                throw new SQLException("This draw is not enabled for overseas-order cash. Use an enabled drawer.");}
        }
        if(Set.of("REFUND","REVERSAL").contains(action)&&amount.compareTo(expected(c,session))>0)
            throw new SQLException("Insufficient overseas cash in this draw for the refund. Replenish through new collections or use another refund method.");
        return true;
    }
    static void configure(Connection c,long id,boolean enabled)throws SQLException {
        try(var p=c.prepareStatement("SELECT handles_overseas_cash FROM cash_drawers WHERE cash_drawer_id=? FOR UPDATE")) {
            p.setLong(1,id);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Drawer was not found.");if(r.getBoolean(1)==enabled)return;}
        }
        try(var p=c.prepareStatement("SELECT 1 FROM cash_drawer_sessions WHERE cash_drawer_id=? AND status='OPEN'")) {
            p.setLong(1,id);try(var r=p.executeQuery()){if(r.next())throw new SQLException("Close the draw before changing its overseas cash setting.");}
        }
        try(var p=c.prepareStatement("UPDATE cash_drawers SET handles_overseas_cash=? WHERE cash_drawer_id=?")){p.setBoolean(1,enabled);p.setLong(2,id);p.executeUpdate();}
        SyncOutboxService.recordEvent(c,"OVERSEAS_CASH_DRAWER_CONFIGURED",Map.of("cash_drawer_id",id,"enabled",enabled));
    }
    static void prepareClose(Connection c,long id,JsonObject body)throws SQLException {
        var session=CashDrawerService.getSessionForUpdate(c,id);
        if(session==null||!session.isOpen())throw new SQLException("The draw is already closed or unavailable.");
        DrawerCash state=drawer(c,id);if(!state.enabled())return;
        BigDecimal count;
        try{count=money(body.get("overseasCounted").getAsBigDecimal());}catch(Exception e){throw new SQLException("Count the separate overseas-order cash before closing.",e);}
        String reason=body.has("overseasReason")&&!body.get("overseasReason").isJsonNull()?body.get("overseasReason").getAsString().trim():"";
        if(reason.length()>2000||count.compareTo(state.expected())!=0&&reason.isBlank())throw new SQLException("A reason is required for the overseas cash shortage or overage (maximum 2000 characters).");
        try(var p=c.prepareStatement("UPDATE cash_drawer_sessions SET overseas_expected=?,overseas_counted=?,overseas_variance_reason=? WHERE cash_drawer_session_id=?")) {
            p.setBigDecimal(1,state.expected());p.setBigDecimal(2,count);p.setString(3,reason);p.setLong(4,id);p.executeUpdate();
        }
    }
    static String finishClose(Connection c,models.CashDrawerSession s,Integer user,String name)throws SQLException {
        DrawerCash state=drawer(c,s.sessionId());if(!state.enabled())return "";
        if(state.counted()==null)throw new SQLException("The separate overseas cash count is required.");
        lockFund(c,s.locationId());
        try(var p=c.prepareStatement("INSERT INTO overseas_cash_fund(entry_id,location_id,cash_drawer_session_id,entry_type,amount,reason,user_id,user_name,device_id) VALUES(?,?,?,'HANDOVER',?,?,?,?,?)")) {
            p.setObject(1,UUID.randomUUID());p.setInt(2,s.locationId());p.setLong(3,s.sessionId());p.setBigDecimal(4,state.counted());p.setString(5,"Draw handover. "+Objects.toString(state.reason(),""));p.setObject(6,user);p.setString(7,Objects.toString(name,""));p.setString(8,s.deviceId());p.executeUpdate();
        }
        SyncOutboxService.recordEvent(c,"OVERSEAS_CASH_HANDED_OVER",Map.of("cash_drawer_session_id",s.sessionId(),"location_id",s.locationId(),"counted",state.counted(),"expected",state.expected(),"reason",Objects.toString(state.reason(),"")));
        return "\nOverseas expected: "+state.expected()+"\nOverseas counted / handed over: "+state.counted()+"\nOverseas variance: "+state.counted().subtract(state.expected())+"\nOverseas reason: "+Objects.toString(state.reason(),"");
    }
    static void lockFund(Connection c,int location)throws SQLException {
        try(var p=c.prepareStatement("SELECT location_id FROM locations WHERE location_id=? FOR UPDATE")){p.setInt(1,location);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Store was not found.");}}
    }
    public static void withdraw(Connection c,BigDecimal amount,String reason)throws SQLException {
        Integer location=ServerRequestIdentity.locationId();if(location==null)throw new SQLException("Choose a store.");
        amount=money(amount);if(amount.signum()==0||reason==null||reason.isBlank()||reason.length()>2000)throw new SQLException("A positive amount and withdrawal reason/payee are required.");
        lockFund(c,location);
        try(var p=c.prepareStatement("SELECT COALESCE(SUM(CASE WHEN entry_type='HANDOVER' THEN amount ELSE -amount END),0) FROM overseas_cash_fund WHERE location_id=?")) {
            p.setInt(1,location);try(var r=p.executeQuery()){r.next();if(amount.compareTo(r.getBigDecimal(1))>0)throw new SQLException("Withdrawal exceeds the overseas cash held by this store.");}
        }
        UUID id=UUID.randomUUID();try(var p=c.prepareStatement("INSERT INTO overseas_cash_fund(entry_id,location_id,entry_type,amount,reason,user_id,user_name) VALUES(?,?,'WITHDRAWAL',?,?,?,?)")) {
            p.setObject(1,id);p.setInt(2,location);p.setBigDecimal(3,amount);p.setString(4,reason.trim());p.setObject(5,ServerRequestIdentity.userId());p.setString(6,Objects.toString(ServerRequestIdentity.userName(),""));p.executeUpdate();
        }
        SyncOutboxService.recordEvent(c,"OVERSEAS_CASH_WITHDRAWN",Map.of("entry_id",id.toString(),"location_id",location,"amount",amount,"reason",reason));
    }
    public static FundReport report(Connection c,Integer location,LocalDate from,LocalDate to,String zone)throws SQLException {
        BigDecimal bf=BigDecimal.ZERO,in=BigDecimal.ZERO,out=BigDecimal.ZERO;List<FundLine> lines=new ArrayList<>();
        try(var p=c.prepareStatement("SELECT (created_at AT TIME ZONE ?)::date,entry_type,amount,reason,user_name,cash_drawer_session_id FROM overseas_cash_fund WHERE (?::integer IS NULL OR location_id=?) AND (created_at AT TIME ZONE ?)::date<=? ORDER BY created_at,entry_id")) {
            p.setString(1,zone);p.setObject(2,location);p.setObject(3,location);p.setString(4,zone);p.setObject(5,to);
            try(var r=p.executeQuery()){while(r.next()) {
                BigDecimal a=r.getBigDecimal(3);boolean received="HANDOVER".equals(r.getString(2));LocalDate date=r.getDate(1).toLocalDate();
                if(date.isBefore(from)){bf=bf.add(received?a:a.negate());continue;}
                if(received)in=in.add(a);else out=out.add(a);
                lines.add(new FundLine(date.toString(),(received?"Draw #"+r.getLong(6)+" handover: ":"Withdrawal: ")+r.getString(4)+" — "+r.getString(5),received?a:BigDecimal.ZERO,received?BigDecimal.ZERO:a));
            }}
        }
        return new FundReport(bf,in,out,bf.add(in).subtract(out),List.copyOf(lines));
    }
}
