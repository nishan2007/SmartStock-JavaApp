package services;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import services.CustomOrderPricingService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Only an explicitly selected disposable loopback database may be changed. */
class CustomOrderPendingPricingDatabaseTest {
    @Test void migrationsPaymentsFinalizationAndConcurrentChanges()throws Exception {
        String admin=System.getProperty("pricing.test.admin","");assumeTrue(!admin.isBlank());
        if(!admin.equals("jdbc:postgresql://127.0.0.1:55443/postgres"))throw new IllegalArgumentException("Use the disposable pricing cluster on port 55443.");
        String db="pricing_test_"+UUID.randomUUID().toString().replace("-","");String url=admin.replace("/postgres","/"+db);
        try(Connection root=DriverManager.getConnection(admin,"pricing_test","")) {
            sql(root,"CREATE DATABASE "+db);
            try(Connection c=DriverManager.getConnection(url,"pricing_test","")) {
                SchemaContractService.installLocalBaseline(c);
                assertTrue(SchemaContractService.validateLocal(c).ready());
                sql(c,"INSERT INTO locations(location_id,name) VALUES(990,'Pricing test'); INSERT INTO users(user_id,username,full_name,role_id) VALUES(990,'pricing-test','Test Manager',1); INSERT INTO customer_accounts(customer_id,name,phone) VALUES(990,'Test Customer','6000000'); INSERT INTO company_customization(location_id) VALUES(990)");
                // Repeated local and shared-catalog migrations are safe.
                for(int i=0;i<2;i++)for(String resource:List.of("database/migrations/v1_after/20261011120000_custom_order_pending_pricing.sql","database/migrations/v1_after/20261011120100_custom_order_pending_pricing_permissions.sql"))SqlScriptRunner.runSql(c,SqlScriptRunner.readResource(resource));
                assertEquals(2,scalar(c,"SELECT count(*) FROM permissions WHERE permission_key IN ('CUSTOM_ORDER_DEFER_PRICING','CUSTOM_ORDER_UNPRICED_DELIVERY_OVERRIDE')"));
                sql(c,"INSERT INTO custom_orders(custom_order_id,order_number,customer_id,customer_name,customer_phone,location_id) VALUES(989,'LEGACY',990,'Test Customer','6000000',990)");
                assertEquals("FINAL",text(c,"SELECT pricing_state FROM custom_orders WHERE custom_order_id=989"));
                pending(c,991,"PENDING-1");line(c,9911,991,"Fixed item","FIXED");
                permissions(c);
                c.setAutoCommit(false);
                assertThrows(SQLException.class,()->LanCustomOrderWorkflowService.payment(c,991,BigDecimal.TEN,"CARD","",990,990,"Test Manager",device(),"Test POS"));c.rollback();
                LanCustomOrderWorkflowService.payment(c,991,new BigDecimal("3000"),"CARD","deposit",990,990,"Test Manager",device(),"Test POS");c.commit();
                assertEquals("PRICE_PENDING",text(c,"SELECT payment_status FROM custom_orders WHERE custom_order_id=991"));
                assertEquals(3000,scalar(c,"SELECT amount_paid FROM custom_orders WHERE custom_order_id=991"));
                assertEquals(0,scalar(c,"SELECT balance_due FROM custom_orders WHERE custom_order_id=991"));
                assertThrows(SQLException.class,()->LanCustomOrderWorkflowService.lineReturn(c,991,List.of(new LanCustomOrderWorkflowService.ReturnRequest(9911,BigDecimal.TEN,true,"NO_RESTOCK")),"CARD","refund","Payment mistake",990,990,"Test Manager",device(),"Test POS"));c.rollback();
                LanCustomOrderWorkflowService.production(c,991,List.of(9911L),"READY","Produced before pricing",990,990,"Test Manager",device(),"Test POS");c.commit();
                assertEquals("READY",text(c,"SELECT production_status FROM custom_order_lines WHERE custom_order_line_id=9911"));
                assertThrows(SQLException.class,()->LanCustomOrderWorkflowService.deliver(c,991,List.of(9911L),"",990,990,"Test Manager",device(),"Test POS"));c.rollback();
                LanCustomOrderWorkflowService.deliver(c,991,List.of(9911L),"Manager approved",990,990,"Test Manager",device(),"Test POS",true);c.commit();
                assertEquals("DELIVERED",text(c,"SELECT status FROM custom_orders WHERE custom_order_id=991"));
                var details=CustomOrderPricingService.details(c,991,990);var work=work(details,"12000");
                CustomOrderPricingService.finalizePrice(c,991,990,details,work,null,990,"Test Manager",device(),"Test POS");c.commit();
                assertEquals("FINAL",text(c,"SELECT pricing_state FROM custom_orders WHERE custom_order_id=991"));assertEquals(9000,scalar(c,"SELECT balance_due FROM custom_orders WHERE custom_order_id=991"));
                assertEquals("DELIVERED",text(c,"SELECT delivery_status FROM custom_order_lines WHERE custom_order_line_id=9911"));
                assertEquals(0,scalar(c,"SELECT count(*) FROM custom_order_item_movements WHERE custom_order_id=991"));
                assertThrows(SQLException.class,()->CustomOrderPricingService.finalizePrice(c,991,990,details,work,null,990,"Test Manager",device(),"Test POS"));c.rollback();
                pending(c,992,"PENDING-2");line(c,9921,992,"Fixed item","FIXED");c.commit();
                LanCustomOrderWorkflowService.payment(c,992,new BigDecimal("3000"),"CARD","deposit",990,990,"Test Manager",device(),"Test POS");c.commit();
                var excess=CustomOrderPricingService.details(c,992,990);
                assertThrows(IllegalArgumentException.class,()->CustomOrderPricingService.finalizePrice(c,992,990,excess,work(excess,"2000"),null,990,"Test Manager",device(),"Test POS"));c.rollback();
                assertEquals("PENDING",text(c,"SELECT pricing_state FROM custom_orders WHERE custom_order_id=992"));
                LanCustomOrderWorkflowService.refundPendingDeposit(c,992,new BigDecimal("1000"),"CARD","refund","Excess deposit",990,990,"Test Manager",device(),"Test POS");c.commit();
                var settled=CustomOrderPricingService.details(c,992,990);CustomOrderPricingService.finalizePrice(c,992,990,settled,work(settled,"2000"),null,990,"Test Manager",device(),"Test POS");c.commit();
                assertEquals("PAID",text(c,"SELECT payment_status FROM custom_orders WHERE custom_order_id=992"));
                pending(c,993,"PENDING-3");line(c,9931,993,"Fixed item","FIXED");c.commit();
                var cancelDetails=CustomOrderPricingService.details(c,993,990);
                LanCustomOrderWorkflowService.payment(c,993,BigDecimal.TEN,"CARD","deposit",990,990,"Test Manager",device(),"Test POS");c.commit();
                LanCustomOrderWorkflowService.refundPendingDeposit(c,993,BigDecimal.TEN,"CARD","refund","Cancelled",990,990,"Test Manager",device(),"Test POS");c.commit();
                LanCustomOrderWorkflowService.returnAndCancel(c,993,List.of(new LanCustomOrderWorkflowService.ReturnRequest(9931,BigDecimal.ZERO,false,"NO_RESTOCK")),"CARD","refund","Cancelled",990,990,"Test Manager",device(),"Test POS");c.commit();assertEquals("CANCELLED",text(c,"SELECT status FROM custom_orders WHERE custom_order_id=993"));
                assertThrows(SQLException.class,()->CustomOrderPricingService.details(c,993,990));c.rollback();
                pending(c,994,"PENDING-4");line(c,9941,994,"Concurrent","FIXED");c.commit();
                // A payment arriving during finalization waits on the order lock and sees the final balance.
                var locked=CustomOrderPricingService.details(c,994,990);
                ExecutorService pool=Executors.newSingleThreadExecutor();CountDownLatch entered=new CountDownLatch(1);
                try {
                    Future<?> payment=pool.submit(()->{try(Connection concurrent=DriverManager.getConnection(url,"pricing_test","")){concurrent.setAutoCommit(false);entered.countDown();LanCustomOrderWorkflowService.payment(concurrent,994,new BigDecimal("20"),"CARD","concurrent",990,990,"Test Manager",device(),"Test POS");concurrent.commit();}catch(Exception ex){throw new RuntimeException(ex);}});
                    assertTrue(entered.await(5,TimeUnit.SECONDS));
                    CustomOrderPricingService.finalizePrice(c,994,990,locked,work(locked,"100"),null,990,"Test Manager",device(),"Test POS");c.commit();payment.get(10,TimeUnit.SECONDS);
                    assertEquals(80,scalar(c,"SELECT balance_due FROM custom_orders WHERE custom_order_id=994"));
                }finally{pool.shutdownNow();}
            } finally {
                sql(root,"SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='"+db+"' AND pid<>pg_backend_pid()");sql(root,"DROP DATABASE "+db);
            }
        }
    }
    private static void permissions(Connection c)throws Exception {
        sql(c,"INSERT INTO roles(role_id,role_name) VALUES(991,'Pricing test cashier'); INSERT INTO users(user_id,username,full_name,role_id) VALUES(991,'pricing-cashier','Test Cashier',991); INSERT INTO devices(device_id,installation_id,device_name) VALUES('"+device()+"','pricing-test','Test POS')");
        var ctor=LanApiServer.class.getDeclaredConstructors()[0];ctor.setAccessible(true);Object server=ctor.newInstance(new Object[ctor.getParameterCount()]);
        Object device=principal("DevicePrincipal",UUID.fromString(device()),"pricing-test",990,false,false);
        Object cashier=principal("SessionPrincipal",UUID.randomUUID(),991,990,"test"),manager=principal("SessionPrincipal",UUID.randomUUID(),990,990,"test");
        Object user=principal("AuthenticatedUser",991,"pricing-cashier","Test Cashier","","Pricing test cashier",990,"Test store","America/Guyana");
        var trust=Arrays.stream(LanApiServer.class.getDeclaredMethods()).filter(m->m.getName().equals("trustedCustomOrderRequest")).findFirst().orElseThrow();trust.setAccessible(true);
        var request=LanJson.create().fromJson("{\"customerName\":\"Test Customer\",\"customerPhone\":\"6000000\",\"pricingState\":\"PENDING\",\"pricingDeferReason\":\"Await production\",\"applyCustomerDiscount\":true,\"lines\":[{\"itemName\":\"Unmeasured manual item\",\"pricingType\":\"FIXED\"}]}",ServerCustomOrderDataService.OrderSaveRequest.class);
        var trusted=(ServerCustomOrderDataService.OrderSaveRequest)trust.invoke(server,c,device,manager,user,request);
        assertEquals("PRICE_PENDING",trusted.paymentStatus());assertEquals(0,trusted.total().signum());assertNull(trusted.lines().get(0).widthValue());
        UUID did=UUID.fromString(device());String key=UUID.randomUUID().toString(),op="custom-orders.workflow.finalize_price.v1",hash=LanSecurity.sha256("pricing-test");
        assertNull(LanApiServer.loadIdempotentResult(c,did,key,op,hash));LanApiServer.completeIdempotency(c,did,key,Map.of("updated",true));
        assertEquals(true,LanApiServer.loadIdempotentResult(c,did,key,op,hash).get("updated"));
        assertThrows(Exception.class,()->LanApiServer.loadIdempotentResult(c,did,key,op,LanSecurity.sha256("changed")));
        var defer=Arrays.stream(LanApiServer.class.getDeclaredMethods()).filter(m->m.getName().equals("approvePricingDeferral")).findFirst().orElseThrow();defer.setAccessible(true);
        assertApi("APPROVAL_REQUIRED",()->defer.invoke(server,c,device,cashier,user,"Await production",null));
        assertApi("APPROVAL_INVALID",()->defer.invoke(server,c,device,cashier,user,"Await production","invalid"));
        assertApi("VALIDATION_ERROR",()->defer.invoke(server,c,device,manager,user,"",null));
        var direct=(LanSalesService.Approval)defer.invoke(server,c,device,manager,user,"Await production",null);assertEquals(990,direct.approverUserId());
        approval(c,"defer-test","CUSTOM_ORDER_DEFER_PRICING","Defer Custom Order Pricing","Await production","now()+interval '5 minutes'");
        assertApi("APPROVAL_INVALID",()->defer.invoke(server,c,device,cashier,user,"Changed reason","defer-test"));
        var override=(LanSalesService.Approval)defer.invoke(server,c,device,cashier,user,"Await production","defer-test");assertEquals(990,override.approverUserId());
        assertApi("APPROVAL_INVALID",()->defer.invoke(server,c,device,cashier,user,"Await production","defer-test"));
        approval(c,"expired-test","CUSTOM_ORDER_DEFER_PRICING","Defer Custom Order Pricing","Await production","now()-interval '1 minute'");
        assertApi("APPROVAL_INVALID",()->defer.invoke(server,c,device,cashier,user,"Await production","expired-test"));
        var finalize=Arrays.stream(LanApiServer.class.getDeclaredMethods()).filter(m->m.getName().equals("finalizeCustomOrderPrice")).findFirst().orElseThrow();finalize.setAccessible(true);
        assertApi("PERMISSION_DENIED",()->finalize.invoke(server,c,device,cashier,user,991L,new com.google.gson.JsonObject()));
        var deliver=Arrays.stream(LanApiServer.class.getDeclaredMethods()).filter(m->m.getName().equals("approveUnpricedDelivery")).findFirst().orElseThrow();deliver.setAccessible(true);
        var body=new com.google.gson.JsonObject();body.addProperty("unpricedDeliveryReason","Customer collecting now");
        assertApi("APPROVAL_REQUIRED",()->deliver.invoke(server,c,device,manager,user,991L,body));
        approval(c,"delivery-test","CUSTOM_ORDER_UNPRICED_DELIVERY_OVERRIDE","Deliver Custom Order Before Pricing","Customer collecting now","now()+interval '5 minutes'");
        body.addProperty("unpricedDeliveryApprovalToken","delivery-test");assertEquals(true,deliver.invoke(server,c,device,cashier,user,991L,body));
        assertApi("APPROVAL_INVALID",()->deliver.invoke(server,c,device,cashier,user,991L,body));
        assertEquals(1,scalar(c,"SELECT count(*) FROM custom_order_audit_log WHERE custom_order_id=991 AND action_type='UNPRICED_DELIVERY_APPROVED'"));
    }
    private static Object principal(String name,Object...args)throws Exception {
        Class<?> type=Class.forName("services.LanApiServer$"+name);var ctor=Arrays.stream(type.getDeclaredConstructors()).filter(x->x.getParameterCount()==args.length).findFirst().orElseThrow();ctor.setAccessible(true);return ctor.newInstance(args);
    }
    private static void approval(Connection c,String token,String permission,String action,String reason,String expiry)throws SQLException {
        try(var p=c.prepareStatement("INSERT INTO lan_api_approvals(approval_hash,device_id,requester_user_id,approver_user_id,location_id,permission_key,action_key,resource_hash,expires_at) VALUES(?,?::uuid,991,990,990,?,?,?,"+expiry+")")) {
            p.setString(1,LanSecurity.sha256(token));p.setString(2,device());p.setString(3,permission);p.setString(4,action);p.setString(5,LanSecurity.sha256(action+"|"+reason));p.executeUpdate();
        }
    }
    private static void assertApi(String code,org.junit.jupiter.api.function.Executable action)throws Exception {
        var error=assertThrows(java.lang.reflect.InvocationTargetException.class,action).getCause();var field=error.getClass().getDeclaredField("code");field.setAccessible(true);assertEquals(code,field.get(error));
    }
    private static List<AuthorizedLine> work(PricingDetails details,String rate) {
        return details.lines().stream().map(saved->{var input=new PricingLine(saved.lineId(),null,null,new BigDecimal(rate),List.of(),BigDecimal.ZERO,"",null,"",null);return new AuthorizedLine(saved,input,CustomOrderPricingService.calculate(saved,input,BigDecimal.ZERO,false),null,null,null,null);}).toList();
    }
    private static void pending(Connection c,int id,String number)throws SQLException {sql(c,"INSERT INTO custom_orders(custom_order_id,order_number,customer_id,customer_name,customer_phone,location_id,pricing_state,payment_status) VALUES("+id+",'"+number+"',990,'Test Customer','6000000',990,'PENDING','PRICE_PENDING')");}
    private static void line(Connection c,int id,int order,String name,String type)throws SQLException {sql(c,"INSERT INTO custom_order_lines(custom_order_line_id,custom_order_id,item_name,pricing_type,unit_price,line_total,customization_details) VALUES("+id+","+order+",'"+name+"','"+type+"',0,0,'Test instructions')");}
    private static String device(){return "00000000-0000-0000-0000-000000000990";}
    private static void sql(Connection c,String sql)throws SQLException{try(var p=c.createStatement()){p.execute(sql);}}
    private static int scalar(Connection c,String sql)throws SQLException{try(var p=c.createStatement();var r=p.executeQuery(sql)){assertTrue(r.next());return r.getInt(1);}}
    private static String text(Connection c,String sql)throws SQLException{try(var p=c.createStatement();var r=p.executeQuery(sql)){assertTrue(r.next());return r.getString(1);}}
}
