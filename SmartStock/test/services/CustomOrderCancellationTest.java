package services;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderCancellationTest {
    @Test void cancellationRequiresEveryRemainingLineInFull()throws Exception {
        var lines=List.of(new LanCustomOrderWorkflowService.ReturnLine(1,"Shirt","",BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.TEN),
                new LanCustomOrderWorkflowService.ReturnLine(2,"Free add-on","",BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));
        var full=new LanCustomOrderWorkflowService.ReturnRequest(1,BigDecimal.TEN,false,"RESTOCK");
        var free=new LanCustomOrderWorkflowService.ReturnRequest(2,BigDecimal.ZERO,false,"NO_RESTOCK");
        LanCustomOrderWorkflowService.validateCancellationReturns(lines,List.of(full,free));
        assertThrows(SQLException.class,()->LanCustomOrderWorkflowService.validateCancellationReturns(lines,List.of(full)));
        assertThrows(SQLException.class,()->LanCustomOrderWorkflowService.validateCancellationReturns(lines,List.of(full,full)));
        assertThrows(SQLException.class,()->LanCustomOrderWorkflowService.validateCancellationReturns(lines,List.of(new LanCustomOrderWorkflowService.ReturnRequest(1,BigDecimal.ONE,false,"RESTOCK"),free)));
        assertThrows(SQLException.class,()->LanCustomOrderWorkflowService.validateCancellationReturns(lines,List.of(new LanCustomOrderWorkflowService.ReturnRequest(1,BigDecimal.TEN,true,"RESTOCK"),free)));
        assertThrows(SQLException.class,()->LanCustomOrderWorkflowService.validateCancellationReturns(lines,List.of(new LanCustomOrderWorkflowService.ReturnRequest(1,BigDecimal.TEN,false,null),free)));
    }
    @Test void combinedCancellationRejectsDeliveredOrdersBeforeReturningAnything() {
        Fixture f=new Fixture("DELIVERED",10,false,false);
        assertThrows(SQLException.class,()->LanCustomOrderWorkflowService.returnAndCancel(f.connection(),1,List.of(),"CASH","","Cancelled",1,3,"Admin","device","Register"));
        assertTrue(f.writes.isEmpty());
    }
    @Test void oneCallSettlesPaidAndUnpaidOrdersAndCancelsThem()throws Exception {
        for(int paid:new int[]{0,4,10}) {
            FinancialFixture f=new FinancialFixture(paid);
            var request=new LanCustomOrderWorkflowService.ReturnRequest(11,BigDecimal.TEN,false,"NO_RESTOCK");
            LanCustomOrderWorkflowService.returnAndCancel(f.connection(),1,List.of(request),"ACCOUNT","","Changed mind",1,3,"Admin","device","Register");
            assertEquals("CANCELLED",f.status);
            assertEquals(BigDecimal.ZERO,f.total);
            assertEquals(BigDecimal.ZERO,f.paid);
            assertEquals(BigDecimal.ZERO,f.balance);
            assertEquals(BigDecimal.valueOf(paid),f.payout);
            assertTrue(f.returned);
            assertEquals(1,f.statusChanges);
            // A repeated cancellation cannot refund or return stock again.
            LanCustomOrderWorkflowService.returnAndCancel(f.connection(),1,List.of(request),"ACCOUNT","","Changed mind",1,3,"Admin","device","Register");
            assertEquals(1,f.statusChanges);
        }
    }
    @Test void customerSuppliedItemsCancelWithoutInventingAStockId()throws Exception {
        for(String stockAction:List.of("NO_RESTOCK","RESTOCK")) {
            FinancialFixture f=new FinancialFixture(10);f.customerSupplied=true;
            var request=new LanCustomOrderWorkflowService.ReturnRequest(11,BigDecimal.TEN,false,stockAction);
            LanCustomOrderWorkflowService.returnAndCancel(f.connection(),1,List.of(request),"ACCOUNT","","Duplicate order",1,3,"Admin","device","Register");
            assertEquals("CANCELLED",f.status);
            assertTrue(f.nullItemRecorded);
            assertEquals(BigDecimal.TEN,f.payout);
        }
    }
    @Test void requiresReasonBeforeAccessingDatabase() {
        assertThrows(SQLException.class,()->cancel(null," "));
    }
    @Test void refusesPaidUnpaidDeliveredAndInvoiceBilledOrders() {
        for(Fixture f:List.of(new Fixture("NEW",10,false,false),new Fixture("DELIVERED",0,false,false),
                new Fixture("NEW",0,true,false),new Fixture("NEW",0,false,true))) {
            assertThrows(SQLException.class,()->cancel(f.connection(),"Customer cancelled"));
            assertTrue(f.writes.isEmpty());
        }
    }
    @Test void settledOrderRecordsCancellationAndRetryDoesNotWriteAgain()throws Exception {
        Fixture f=new Fixture("NEW",0,false,false);
        cancel(f.connection(),"Duplicate order");
        assertEquals(4,f.writes.size()); // order, reservations, status history, audit
        assertTrue(f.writes.get(0).contains("cancelled_by_user_id"));
        assertTrue(f.writes.get(2).contains("custom_order_status_history"));
        Fixture retry=new Fixture("CANCELLED",0,false,false);
        cancel(retry.connection(),"Duplicate order");
        assertTrue(retry.writes.isEmpty());
    }
    @Test void cancelledOrdersRejectProductionDeliveryAndPayment() {
        Fixture f=new Fixture("CANCELLED",0,false,false);
        assertThrows(SQLException.class,()->LanCustomOrderWorkflowService.production(f.connection(),1,List.of(2L),"READY","",1,3,"Admin","device","Register"));
        assertThrows(SQLException.class,()->LanCustomOrderWorkflowService.markOrderDelivered(f.connection(),1,1,3,"Admin","device","Register"));
        assertThrows(SQLException.class,()->LanCustomOrderWorkflowService.payment(f.connection(),1,BigDecimal.ONE,"ACCOUNT","",1,3,"Admin","device","Register"));
        assertTrue(f.writes.isEmpty());
    }
    private static void cancel(Connection c,String reason)throws SQLException {
        LanCustomOrderWorkflowService.cancel(c,1,reason,1,3,"Admin","device","Register");
    }
    private static final class Fixture {
        final String status;final int amount;final boolean billed,unreturned;final List<String>writes=new ArrayList<>();
        Fixture(String status,int amount,boolean billed,boolean unreturned){this.status=status;this.amount=amount;this.billed=billed;this.unreturned=unreturned;}
        Connection connection(){return proxy(Connection.class,(method,args)->{
            if(method.equals("prepareStatement"))return statement((String)args[0]);
            return null;
        });}
        PreparedStatement statement(String sql){return proxy(PreparedStatement.class,(method,args)->{
            if(method.equals("executeUpdate")){writes.add(sql);return 1;}
            if(method.equals("executeQuery")){
                boolean[] first={true};
                return proxy(ResultSet.class,(name,values)->{
                    if(name.equals("next")){boolean result=first[0]&&(!sql.contains("FROM custom_order_lines")||unreturned);first[0]=false;return result;}
                    if(name.equals("getBoolean"))return billed;
                    if(name.equals("getString"))return sql.startsWith("SELECT status")?status:"CO-1";
                    if(name.equals("getBigDecimal"))return BigDecimal.valueOf(amount);
                    if(name.equals("getInt"))return 4;
                    return null;
                });
            }
            return null;
        });}
    }
    private static final class FinancialFixture {
        String status="NEW";BigDecimal total=BigDecimal.TEN,paid,balance,payout=BigDecimal.ZERO;
        boolean returned,customerSupplied,nullItemRecorded;int statusChanges;
        FinancialFixture(int paid){this.paid=BigDecimal.valueOf(paid);balance=total.subtract(this.paid);}
        Connection connection(){return proxy(Connection.class,(name,args)->name.equals("prepareStatement")?statement((String)args[0]):null);}
        PreparedStatement statement(String sql){
            Map<Integer,Object>parameters=new HashMap<>();
            return proxy(PreparedStatement.class,(name,args)->{
                if(name.startsWith("set")){parameters.put((Integer)args[0],name.equals("setNull")?null:args[1]);return null;}
                if(name.equals("executeUpdate")) {
                    if(sql.startsWith("UPDATE custom_orders SET total_amount")){total=(BigDecimal)parameters.get(1);paid=(BigDecimal)parameters.get(2);balance=(BigDecimal)parameters.get(3);}
                    if(sql.startsWith("UPDATE custom_order_lines"))returned=true;
                    if(sql.startsWith("INSERT INTO custom_order_payments"))payout=(BigDecimal)parameters.get(2);
                    if(sql.startsWith("UPDATE custom_orders SET status='CANCELLED'")){status="CANCELLED";statusChanges++;}
                    return 1;
                }
                if(name.equals("executeQuery")) {
                    List<Object[]>rows=new ArrayList<>();
                    if(sql.startsWith("SELECT status"))rows.add(new Object[]{status,balance});
                    else if(sql.startsWith("SELECT pricing_state"))rows.add(new Object[]{"FINAL"});
                    else if(sql.startsWith("SELECT invoice_billed"))rows.add(new Object[]{false});
                    else if(sql.startsWith("SELECT order_number,customer_id"))rows.add(new Object[]{"CO-1",4,total,paid,balance});
                    else if(sql.startsWith("SELECT l.custom_order_line_id")){if(!returned)rows.add(new Object[]{11L,"Shirt","",BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.TEN});}
                    else if(sql.startsWith("SELECT custom_item_id"))rows.add(new Object[]{customerSupplied?null:1L,null,"Shirt","",BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.TEN});
                    else if(sql.startsWith("INSERT INTO custom_order_line_returns")){
                        if(customerSupplied){assertNull(parameters.get(3),"Customer-supplied returns must bind SQL NULL, never stock ID zero");nullItemRecorded=true;}
                        rows.add(new Object[]{100L});
                    }
                    else if(sql.startsWith("SELECT location_id"))rows.add(new Object[]{1});
                    else if(sql.startsWith("SELECT 1 FROM custom_order_lines")){if(!returned)rows.add(new Object[]{1});}
                    else throw new AssertionError("Unexpected query: "+sql);
                    int[]index={-1};boolean[]wasNull={false};
                    return proxy(ResultSet.class,(method,values)->{
                        if(method.equals("next"))return ++index[0]<rows.size();
                        if(method.equals("wasNull"))return wasNull[0];
                        if(method.startsWith("get")) {
                            Object value=rows.get(index[0])[(Integer)values[0]-1];wasNull[0]=value==null;
                            return switch(method){case "getLong"->value==null?0L:((Number)value).longValue();case "getInt"->((Number)value).intValue();default->value;};
                        }
                        return null;
                    });
                }
                return null;
            });
        }
    }
    interface Invocation {Object run(String method,Object[]args);}
    private static <T>T proxy(Class<T>type,Invocation invocation){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->invocation.run(m.getName(),a)));}
}
