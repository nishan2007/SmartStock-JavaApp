package services;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderDeliveryReadyTest {
    @Test void wholeOrderDeliveryMakesPendingProductionReadyWithHistory() throws Exception {
        Fixture f=new Fixture();
        LanCustomOrderWorkflowService.markOrderDelivered(f.connection(),1,1,3,"Amit","device","POS-05");
        assertEquals("READY",f.production);
        assertEquals("DELIVERED",f.status);
        assertEquals(1,f.history.size());
        assertEquals("NOT_STARTED",f.history.get(0).get(7));
        assertEquals("READY",f.history.get(0).get(8));
        assertEquals("Amit",f.history.get(0).get(11));
        LanCustomOrderWorkflowService.markOrderDelivered(f.connection(),1,1,3,"Amit","device","POS-05");
        assertEquals(1,f.history.size());
    }
    @Test void lineDeliveryOnlyReadiesTheSelectedLine() throws Exception {
        Fixture f=new Fixture();
        LanCustomOrderWorkflowService.deliver(f.connection(),1,List.of(11L),"",1,3,"Amit","device","POS-05");
        assertEquals("READY",f.production);
        assertTrue(f.delivery);
        assertTrue(f.selectedOnly);
        assertEquals("NEW",f.status); // another line remains undelivered
    }
    @Test void unpaidWholeOrderCannotChangeProduction() {
        Fixture f=new Fixture();f.balance=BigDecimal.TEN;
        assertThrows(SQLException.class,()->LanCustomOrderWorkflowService.markOrderDelivered(f.connection(),1,1,3,"Amit","device","POS-05"));
        assertEquals("NOT_STARTED",f.production);
        assertTrue(f.history.isEmpty());
    }
    @Test void pendingPricingRequiresApprovalBeforeAnyDeliveryWrites() {
        Fixture f=new Fixture();f.pricing="PENDING";
        assertThrows(SQLException.class,()->LanCustomOrderWorkflowService.markOrderDelivered(f.connection(),1,1,3,"Amit","device","POS-05"));
        assertThrows(SQLException.class,()->LanCustomOrderWorkflowService.deliver(f.connection(),1,List.of(11L),"",1,3,"Amit","device","POS-05"));
        assertFalse(f.delivery);assertTrue(f.history.isEmpty());assertEquals("NEW",f.status);
    }
    @Test void approvedPendingDeliveryCanProceedWithoutFinalizingThePrice()throws Exception {
        Fixture f=new Fixture();f.pricing="PENDING";
        LanCustomOrderWorkflowService.deliver(f.connection(),1,List.of(11L),"Approved delivery",1,3,"Amit","device","POS-05",true);
        assertTrue(f.delivery);assertEquals("PENDING",f.pricing);
    }
    static class Fixture {
        String production="NOT_STARTED",status="NEW",pricing="FINAL";
        BigDecimal balance=BigDecimal.ZERO;
        boolean delivery,selectedOnly;
        List<Map<Integer,Object>> history=new ArrayList<>();
        Connection connection(){return proxy(Connection.class,(m,a)->m.equals("prepareStatement")?statement((String)a[0]):null);}
        PreparedStatement statement(String sql){
            Map<Integer,Object> params=new HashMap<>();
            return proxy(PreparedStatement.class,(m,a)->{
                if(m.startsWith("set")){params.put((Integer)a[0],m.equals("setNull")?null:a[1]);return null;}
                if(m.equals("executeUpdate")){
                    if(sql.startsWith("UPDATE custom_order_lines SET production_status"))production=(String)params.get(1);
                    if(sql.startsWith("UPDATE custom_order_lines SET delivery_status"))delivery=true;
                    if(sql.startsWith("UPDATE custom_orders SET status='DELIVERED'"))status="DELIVERED";
                    if(sql.startsWith("INSERT INTO custom_order_line_production_history"))history.add(new HashMap<>(params));
                    return 1;
                }
                if(m.equals("executeQuery")){
                    List<Object[]> rows=new ArrayList<>();
                    if(sql.startsWith("SELECT pricing_state"))rows.add(new Object[]{pricing});
                    else if(sql.startsWith("SELECT status"))rows.add(new Object[]{status,balance});
                    else if(sql.startsWith("SELECT COALESCE(production_status"))rows.add(new Object[]{production});
                    else if(sql.startsWith("SELECT custom_order_line_id")){
                        assertTrue(sql.contains("return_status,'NONE')<>'FULL'"));
                        selectedOnly=params.containsKey(2);
                        if(!production.equals("READY"))rows.add(new Object[]{11L});
                    } else if(sql.startsWith("SELECT custom_item_id")) {
                        rows.add(sql.contains("delivery_status")?new Object[]{null,null,"Banner","",delivery?"DELIVERED":"PENDING","NONE"}:new Object[]{null,null,"Banner","",production});
                    } else if(sql.startsWith("SELECT COUNT"))rows.add(new Object[]{1});
                    else throw new AssertionError(sql);
                    int[] index={-1};boolean[] wasNull={false};
                    return proxy(ResultSet.class,(n,v)->{
                        if(n.equals("next"))return ++index[0]<rows.size();
                        if(n.equals("wasNull"))return wasNull[0];
                        if(n.startsWith("get")){
                            Object value=rows.get(index[0])[(Integer)v[0]-1];wasNull[0]=value==null;
                            return switch(n){case "getLong"->value==null?0L:((Number)value).longValue();case "getInt"->((Number)value).intValue();default->value;};
                        }
                        return null;
                    });
                }
                return null;
            });
        }
    }
    interface Call {Object run(String method,Object[] args);}
    static <T>T proxy(Class<T> type,Call call){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->call.run(m.getName(),a)));}
}
