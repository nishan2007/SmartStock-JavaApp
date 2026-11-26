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
                pending(c,987,"ADDED-LINES");
                sql(c,"INSERT INTO custom_order_items(custom_item_id,item_name,product_type,pricing_type,fixed_price,quantity_on_hand) VALUES(987,'Catalogue shirt','INVENTORY','FIXED',100,3)");
                sql(c,"INSERT INTO custom_item_inventory(custom_item_id,location_id,quantity_on_hand) VALUES(987,990,3)");
                c.setAutoCommit(false);
                CustomOrderPricingService.updatePendingNotes(c,987,990,"","Keep sizes labelled",990,"Test Manager",device(),"Test POS");
                assertEquals("Keep sizes labelled",text(c,"SELECT order_notes FROM custom_orders WHERE custom_order_id=987"));
                assertThrows(SQLException.class,()->CustomOrderPricingService.updatePendingNotes(c,987,990,"","Stale edit",990,"Test Manager",device(),"Test POS"));
                c.rollback();
                assertEquals("",Objects.toString(text(c,"SELECT order_notes FROM custom_orders WHERE custom_order_id=987"),""));
                var additions=List.of(new AddedLine(-1,987L,null,"Untrusted client name","fake size",null,"Chest logo",null),new AddedLine(-2,null,null,"Manual service","L","White","Extra instructions",null));
                var mapping=CustomOrderPricingService.appendPendingLines(c,987,990,additions,990,"Test Manager",device(),"Test POS");
                assertEquals(2,mapping.size());
                assertEquals(2,scalar(c,"SELECT quantity_on_hand FROM custom_item_inventory WHERE custom_item_id=987 AND location_id=990"));
                assertEquals("Catalogue shirt",text(c,"SELECT item_name FROM custom_order_lines WHERE custom_order_line_id="+mapping.get(-1L)));
                assertEquals(1,scalar(c,"SELECT COUNT(*) FROM custom_order_inventory_reservations WHERE custom_order_id=987"));
                c.rollback();
                assertEquals(3,scalar(c,"SELECT quantity_on_hand FROM custom_item_inventory WHERE custom_item_id=987 AND location_id=990"));
                assertEquals(0,scalar(c,"SELECT COUNT(*) FROM custom_order_lines WHERE custom_order_id=987"));
                mapping=CustomOrderPricingService.appendPendingLines(c,987,990,additions,990,"Test Manager",device(),"Test POS");
                var addedDetails=CustomOrderPricingService.details(c,987,990);
                CustomOrderPricingService.finalizePrice(c,987,990,addedDetails,work(addedDetails,"100"),null,990,"Test Manager",device(),"Test POS");c.commit();
                assertEquals(200,scalar(c,"SELECT total_amount FROM custom_orders WHERE custom_order_id=987"));
                assertEquals(2,scalar(c,"SELECT quantity_on_hand FROM custom_item_inventory WHERE custom_item_id=987 AND location_id=990"));
                assertThrows(SQLException.class,()->CustomOrderPricingService.appendPendingLines(c,987,990,additions,990,"Test Manager",device(),"Test POS"));c.rollback();
                c.setAutoCommit(true);
                pending(c,986,"REGULAR-INVENTORY-ADDITION");
                sql(c,"INSERT INTO products(product_id,name,sku,price,is_active) VALUES(986,'Regular shirt','PRICE-ADD',100,true); INSERT INTO inventory(product_id,location_id,quantity_on_hand) VALUES(986,990,2)");
                c.setAutoCommit(false);
                var inventoryAdded=List.of(new AddedLine(-1,null,null,"Untrusted name",null,null,"Print front",986));
                var inventoryMap=CustomOrderPricingService.appendPendingLines(c,986,990,inventoryAdded,990,"Test Manager",device(),"Test POS");
                assertEquals(1,scalar(c,"SELECT quantity_on_hand FROM inventory WHERE product_id=986 AND location_id=990"));
                assertEquals("Regular shirt",text(c,"SELECT item_name FROM custom_order_lines WHERE custom_order_line_id="+inventoryMap.get(-1L)));
                c.rollback();
                assertEquals(2,scalar(c,"SELECT quantity_on_hand FROM inventory WHERE product_id=986 AND location_id=990"));
                assertEquals(0,scalar(c,"SELECT COUNT(*) FROM custom_order_lines WHERE custom_order_id=986"));
                c.setAutoCommit(true);
                // Repeated local and shared-catalog migrations are safe.
                for(int i=0;i<2;i++)for(String resource:List.of("database/migrations/v1_after/20261011120000_custom_order_pending_pricing.sql","database/migrations/v1_after/20261011120100_custom_order_pending_pricing_permissions.sql"))SqlScriptRunner.runSql(c,SqlScriptRunner.readResource(resource));
                assertEquals(2,scalar(c,"SELECT count(*) FROM permissions WHERE permission_key IN ('CUSTOM_ORDER_DEFER_PRICING','CUSTOM_ORDER_UNPRICED_DELIVERY_OVERRIDE')"));
                sql(c,"INSERT INTO custom_orders(custom_order_id,order_number,customer_id,customer_name,customer_phone,location_id) VALUES(989,'LEGACY',990,'Test Customer','6000000',990)");
                assertEquals("FINAL",text(c,"SELECT pricing_state FROM custom_orders WHERE custom_order_id=989"));
                pending(c,991,"PENDING-1");line(c,9911,991,"Fixed item","FIXED");
                permissions(c);
                c.setAutoCommit(false);
                pending(c,985,"ONE-TIME");
                sql(c,"UPDATE custom_orders SET customer_id=NULL,customer_name='One-time customer',customer_phone='' WHERE custom_order_id=985");
                line(c,9851,985,"Fixed item","FIXED");
                LanCustomOrderWorkflowService.payment(c,985,BigDecimal.TEN,"CARD","deposit",990,990,"Test Manager",device(),"Test POS");
                var oneTime = CustomOrderPricingService.details(c,985,990);
                CustomOrderPricingService.finalizePrice(c,985,990,oneTime,work(oneTime,"100"),null,990,"Test Manager",device(),"Test POS");
                LanCustomOrderWorkflowService.payment(c,985,new BigDecimal("90"),"CARD","balance",990,990,"Test Manager",device(),"Test POS");
                assertEquals("PAID",text(c,"SELECT payment_status FROM custom_orders WHERE custom_order_id=985"));
                assertEquals(0,scalar(c,"SELECT count(*) FROM customer_account_transactions WHERE custom_order_id=985"));
                c.commit();
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
                businessCredit(c);
            } finally {
                sql(root,"SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='"+db+"' AND pid<>pg_backend_pid()");sql(root,"DROP DATABASE "+db);
            }
        }
    }

    private static void businessCredit(Connection c) throws Exception {
        sql(c,"""
                INSERT INTO customer_types(customer_type_id,name) VALUES(995,'Business')
                    ON CONFLICT DO NOTHING;
                INSERT INTO customer_accounts(customer_id,name,customer_type_id,credit_limit)
                    SELECT 995,'Test Business',customer_type_id,100
                    FROM customer_types WHERE lower(name)='business' LIMIT 1;
                INSERT INTO sync_cross_store_customer_history_status(source_location_id,store_name,status)
                    SELECT location_id,name,'CURRENT' FROM locations WHERE location_id<>990
                    ON CONFLICT(source_location_id) DO UPDATE SET status='CURRENT';
                """);
        pending(c,995,"BUSINESS-PENDING");
        sql(c,"UPDATE custom_orders SET customer_id=995 WHERE custom_order_id=995");
        line(c,9951,995,"Business order","FIXED");
        LanCustomOrderWorkflowService.payment(c,995,new BigDecimal("25"),"CARD","deposit",990,990,
                "Test Manager",device(),"Test POS");
        var details=CustomOrderPricingService.details(c,995,990);
        CustomOrderPricingService.finalizePrice(c,995,990,details,work(details,"100"),null,990,
                "Test Manager",device(),"Test POS");
        c.commit();
        assertEquals("ACCOUNT",text(c,"SELECT payment_method FROM custom_orders WHERE custom_order_id=995"));
        assertEquals(25,scalar(c,"SELECT amount_paid FROM custom_orders WHERE custom_order_id=995"));
        assertEquals("CARD",text(c,"SELECT payment_method FROM custom_order_payments WHERE custom_order_id=995"));
        assertEquals(75,CustomerAccountLedgerService.calculateCustomerBalance(c,995).intValueExact());

        sql(c,"""
                INSERT INTO custom_orders(custom_order_id,order_number,customer_id,customer_name,customer_phone,
                    location_id,total_amount,amount_paid,balance_due,payment_status)
                VALUES(996,'BUSINESS-EXISTING',995,'Test Business','6000000',990,25,0,25,'UNPAID');
                """);
        c.commit();
        var atLimit=assertThrows(SQLException.class,()->LanCustomOrderWorkflowService.chargeBalance(c,996,990,
                990,"Test Manager",device(),"Test POS",CustomOrderCredit::reject));
        assertTrue(atLimit.getMessage().contains("would reach"));
        c.rollback();
        var overLimit=assertThrows(SQLException.class,()->CustomOrderCredit.check(c,995,990,
                new BigDecimal("26"),false,CustomOrderCredit::reject));
        assertTrue(overLimit.getMessage().contains("would exceed"));
        c.rollback();
        assertEquals(75,CustomerAccountLedgerService.calculateCustomerBalance(c,995).intValueExact());
        LanCustomOrderWorkflowService.chargeBalance(c,996,990,990,"Test Manager",device(),"Test POS",
                message -> new LanSalesService.Approval(990,"Test Manager","Order approved"));
        LanCustomOrderWorkflowService.chargeBalance(c,996,990,990,"Test Manager",device(),"Test POS",
                CustomOrderCredit::reject);
        c.commit();
        assertEquals(100,CustomerAccountLedgerService.calculateCustomerBalance(c,995).intValueExact());
        assertEquals(1,scalar(c,"SELECT count(*) FROM custom_order_audit_log WHERE custom_order_id=996 AND action_type='ACCOUNT_CHARGE'"));
        assertEquals(1,scalar(c,"SELECT count(*) FROM custom_order_audit_log WHERE custom_order_id=996 AND action_type='CREDIT_LIMIT_OVERRIDE'"));
        assertEquals(0,scalar(c,"SELECT count(*) FROM custom_order_payments WHERE custom_order_id=996"));
        LanCustomOrderWorkflowService.payment(c,996,new BigDecimal("5"),"CARD","payment",990,990,
                "Test Manager",device(),"Test POS");
        c.commit();
        assertEquals(95,CustomerAccountLedgerService.calculateCustomerBalance(c,995).intValueExact());
    }
    private static void permissions(Connection c)throws Exception {
        sql(c,"INSERT INTO roles(role_id,role_name) VALUES(991,'Pricing test cashier'); INSERT INTO users(user_id,username,full_name,role_id) VALUES(991,'pricing-cashier','Test Cashier',991); INSERT INTO devices(device_id,installation_id,device_name) VALUES('"+device()+"','pricing-test','Test POS')");
        var ctor=LanApiServer.class.getDeclaredConstructors()[0];ctor.setAccessible(true);Object server=ctor.newInstance(new Object[ctor.getParameterCount()]);
        Object device=principal("DevicePrincipal",UUID.fromString(device()),"pricing-test",990,false,false);
        Object grant=principal("SessionGrant","test",java.time.Instant.now().plusSeconds(60),null);
        Object cashier=principal("SessionPrincipal",UUID.randomUUID(),991,990,grant),manager=principal("SessionPrincipal",UUID.randomUUID(),990,990,grant);
        Object user=principal("AuthenticatedUser",991,"pricing-cashier","Test Cashier","","Pricing test cashier",990,"Test store","America/Guyana");
        var trust=Arrays.stream(LanApiServer.class.getDeclaredMethods()).filter(m->m.getName().equals("trustedCustomOrderRequest")).findFirst().orElseThrow();trust.setAccessible(true);
        var request=LanJson.create().fromJson("{\"customerName\":\"Test Customer\",\"customerPhone\":\"6000000\",\"pricingState\":\"PENDING\",\"pricingDeferReason\":\"Await production\",\"applyCustomerDiscount\":true,\"lines\":[{\"itemName\":\"Unmeasured manual item\",\"pricingType\":\"FIXED\"}]}",ServerCustomOrderDataService.OrderSaveRequest.class);
        var trusted=(ServerCustomOrderDataService.OrderSaveRequest)trust.invoke(server,c,device,manager,user,request,false);
        assertApi("ADMIN_REQUIRED",()->trust.invoke(server,c,device,cashier,user,request,true));
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
        var credit=Arrays.stream(LanApiServer.class.getDeclaredMethods())
                .filter(m->m.getName().equals("approveCustomOrderCredit")).findFirst().orElseThrow();
        credit.setAccessible(true);
        var creditBody=new com.google.gson.JsonObject();
        String message="This charge would reach the credit limit.";
        assertApi("CUSTOM_ORDER_CREDIT_APPROVAL_REQUIRED",()->credit.invoke(server,c,device,cashier,creditBody,message));
        approval(c,"credit-test","SET_CREDIT_LIMIT",CustomOrderCredit.APPROVAL_ACTION,"Approved charge",
                "now()+interval '5 minutes'");
        creditBody.addProperty("creditApprovalReason","Approved charge");
        creditBody.addProperty("creditApprovalToken","credit-test");
        assertEquals(990,((LanSalesService.Approval)credit.invoke(server,c,device,cashier,creditBody,message)).approverUserId());
        assertApi("APPROVAL_INVALID",()->credit.invoke(server,c,device,cashier,creditBody,message));
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
