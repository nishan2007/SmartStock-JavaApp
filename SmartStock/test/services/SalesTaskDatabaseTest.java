package services;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in disposable database. Never loads a store's database configuration. */
@EnabledIfSystemProperty(named="smartstock.test.salesTaskDatabaseUrl",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/sales_tasks_test")
class SalesTaskDatabaseTest {
    private static final String DEVICE="00000000-0000-0000-0000-000000000992";
    private static final Set<String> PERMISSIONS=Set.of("MANAGE_SALES_TASKS","MAKE_SALE","PROCESS_RETURNS","ADD_MISC_SALE_ITEM","CHANGE_SALE_ITEM_PRICE","APPLY_SALE_DISCOUNT","SALE_DISCOUNT_OVERRIDE");
    private static final SalesTaskService.Actor ACTOR=new SalesTaskService.Actor(992,992,"Task Tester",UUID.fromString(DEVICE),"Test register",PERMISSIONS);
    private static final LanSalesService.ApprovalConsumer NO_APPROVAL=(token,permission,action,reason)->{throw new SQLException("Approval required: "+permission);};
    private Connection c;
    @BeforeAll static void install()throws Exception {
        try(var c=connect();var s=c.createStatement()) {
            s.execute("DROP SCHEMA IF EXISTS storefront CASCADE; DROP SCHEMA public CASCADE; CREATE SCHEMA public");
            SchemaContractService.installLocalBaseline(c);
            s.execute("INSERT INTO locations(location_id,name,timezone,receipt_store_code) VALUES(992,'Task test store','America/Guyana','0992'),(993,'Other store','America/Guyana','0993')");
            s.execute("INSERT INTO users(user_id,username,full_name,role_id,is_active) VALUES(992,'task-test','Task Tester',1,true)");
            s.execute("INSERT INTO user_locations(user_id,location_id) VALUES(992,992)");
            s.execute("INSERT INTO devices(device_id,installation_id,device_name,last_store_id,allow_orders,is_blocked) VALUES('"+DEVICE+"','task-test','Task test register',992,true,false)");
            s.execute("INSERT INTO company_customization(location_id,round_sales_to_nearest_twenty) VALUES(992,false)");
            s.execute("INSERT INTO customer_accounts(customer_id,name,account_number,credit_limit) VALUES(992,'Task Customer','TASK-992',100000)");
            s.execute("INSERT INTO products(product_id,name,sku,product_type,price) VALUES(992,'Paper','TASK-PAPER','INVENTORY',10),(993,'Task miscellaneous','SMARTSTOCK-MISC','NON_INVENTORY',0)");
            s.execute("INSERT INTO inventory(product_id,location_id,quantity_on_hand,reorder_level) VALUES(992,992,100,0)");
            s.execute("INSERT INTO cash_drawers(cash_drawer_id,location_id,drawer_name) VALUES(992,992,'Task test drawer')");
            s.execute("INSERT INTO cash_drawer_sessions(cash_drawer_session_id,cash_drawer_id,location_id,device_id,drawer_name) VALUES(992,992,992,'"+DEVICE+"','Task test drawer')");
            s.execute("INSERT INTO cash_drawer_device_assignments(cash_drawer_id,location_id,device_id) VALUES(992,992,'"+DEVICE+"')");
            SchemaContractService.requireLocalReady(c);
            assertTrue(SchemaContractService.validateLocal(c).ready());
        }
    }
    @BeforeEach void begin()throws Exception{c=connect();c.setAutoCommit(false);}
    @AfterEach void end()throws Exception{c.rollback();c.close();}
    private static Connection connect()throws SQLException{return DriverManager.getConnection(System.getProperty("smartstock.test.salesTaskDatabaseUrl"),"postgres","");}
    private long create()throws Exception{return ((Number)SalesTaskService.mutate(c,json("{action:'CREATE',customerId:992,customerName:'Customer',instructions:'5 copies each, then laminate'}"),ACTOR,NO_APPROVAL).get("taskId")).longValue();}
    private JsonObject cmd(long id,String action)throws Exception{JsonObject b=json("{action:'"+action+"',taskId:"+id+",version:"+scalar("SELECT version FROM sales_tasks WHERE task_id="+id)+"}");return b;}
    private void change(long id,String action)throws Exception{SalesTaskService.mutate(c,cmd(id,action),ACTOR,NO_APPROVAL);}
    private void payment(long id,String action,String amount)throws Exception{JsonObject b=cmd(id,action);b.addProperty("amount",new BigDecimal(amount));b.addProperty("method","CARD");b.addProperty("reference","TEST-REF");b.addProperty("reason","test refund");SalesTaskService.mutate(c,b,ACTOR,NO_APPROVAL);}
    private void finalizeBill(long id)throws Exception{JsonObject b=cmd(id,"FINALIZE");b.add("checkout",json("{lines:[{productId:992,quantity:5,unitPrice:10,discountPercent:0},{productId:993,quantity:1,unitPrice:25,miscItem:true,miscItemName:'Laminating',discountPercent:0}]}"));SalesTaskService.mutate(c,b,ACTOR,NO_APPROVAL);}
    private void collect(long id,String method)throws Exception{JsonObject b=cmd(id,"COLLECT");b.addProperty("method",method);b.addProperty("reference","FINAL-REF");SalesTaskService.mutate(c,b,ACTOR,NO_APPROVAL);}
    private BigDecimal scalar(String sql)throws Exception{try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getBigDecimal(1);}}
    private static JsonObject json(String s){return JsonParser.parseString(s).getAsJsonObject();}
    private void equal(String expected,BigDecimal value){assertEquals(0,new BigDecimal(expected).compareTo(value));}

    @Test void flexibleIntakeAndIndependentCompletion()throws Exception {
        long id=create();equal("0",scalar("SELECT count(*) FROM sales WHERE location_id=992"));
        var detail=SalesTaskService.read(c,json("{action:'DETAILS',taskId:"+id+"}"),ACTOR);
        assertEquals("TASK-992",detail.get("customerAccountNumber"));
        assertTrue(((JsonObject)detail.get("task")).get("final_total").isJsonNull());
        equal("0",(BigDecimal)detail.get("paid"));change(id,"COMPLETE");
        assertThrows(SQLException.class,()->collect(id,"CARD"));
        equal("100",scalar("SELECT quantity_on_hand FROM inventory WHERE product_id=992 AND location_id=992"));
    }
    @Test void mixedFinalBillMultipleAdvancesAndCollectionCountTenderOnce()throws Exception {
        long id=create();payment(id,"PAYMENT","10");payment(id,"PAYMENT","15");change(id,"START");change(id,"COMPLETE");finalizeBill(id);
        equal("75",scalar("SELECT final_total FROM sales_tasks WHERE task_id="+id));
        equal("100",scalar("SELECT quantity_on_hand FROM inventory WHERE product_id=992 AND location_id=992"));
        collect(id,"CARD");equal("95",scalar("SELECT quantity_on_hand FROM inventory WHERE product_id=992 AND location_id=992"));
        equal("75",scalar("SELECT sum(amount_paid) FROM sales_tender_activity WHERE location_id=992"));
        equal("75",scalar("SELECT total_amount FROM sales WHERE location_id=992"));
        assertThrows(SQLException.class,()->collect(id,"CARD"));equal("1",scalar("SELECT count(*) FROM sales WHERE location_id=992"));
    }
    @Test void accountCollectionChargesOnlyRemainder()throws Exception {
        long id=create();payment(id,"PAYMENT","25");change(id,"COMPLETE");finalizeBill(id);
        assertThrows(SQLException.class,()->collect(id,"ACCOUNT"));
        try(var s=c.createStatement()){s.execute("INSERT INTO sync_cross_store_customer_history_status(source_location_id,store_name,status) SELECT location_id,name,'CURRENT' FROM locations WHERE location_id<>992 ON CONFLICT(source_location_id) DO UPDATE SET status='CURRENT'");}
        collect(id,"ACCOUNT");
        equal("50",scalar("SELECT current_balance FROM customer_accounts WHERE customer_id=992"));
        equal("25",scalar("SELECT amount_paid FROM sales WHERE location_id=992"));
        equal("25",scalar("SELECT sum(amount_paid) FROM sales_tender_activity WHERE location_id=992"));
    }
    @Test void excessAdvanceMustBeRefundedAndCancellationKeepsHistory()throws Exception {
        long id=create();payment(id,"PAYMENT","100");change(id,"COMPLETE");finalizeBill(id);
        assertThrows(SQLException.class,()->collect(id,"CARD"));payment(id,"REFUND","25");collect(id,"CARD");
        equal("75",scalar("SELECT sum(amount_paid) FROM sales_tender_activity WHERE location_id=992"));
        long cancelled=create();payment(cancelled,"PAYMENT","10");JsonObject b=cmd(cancelled,"CANCEL");b.addProperty("reason","Customer cancelled");
        JsonObject blocked=b;assertThrows(SQLException.class,()->SalesTaskService.mutate(c,blocked,ACTOR,NO_APPROVAL));payment(cancelled,"REFUND","10");b=cmd(cancelled,"CANCEL");b.addProperty("reason","Customer cancelled");SalesTaskService.mutate(c,b,ACTOR,NO_APPROVAL);
        equal("2",scalar("SELECT count(*) FROM sales_task_payments WHERE task_id="+cancelled));
    }
    @Test void staleEditsCrossStoreAndMissingPermissionsAreRejected()throws Exception {
        long id=create();JsonObject stale=cmd(id,"START");change(id,"START");assertThrows(SQLException.class,()->SalesTaskService.mutate(c,stale,ACTOR,NO_APPROVAL));
        var other=new SalesTaskService.Actor(993,992,"Tester",ACTOR.device(),"Test",PERMISSIONS);
        assertThrows(SQLException.class,()->SalesTaskService.read(c,json("{action:'DETAILS',taskId:"+id+"}"),other));
        var unprivileged=new SalesTaskService.Actor(992,992,"Tester",ACTOR.device(),"Test",Set.of());
        assertThrows(SQLException.class,()->SalesTaskService.read(c,json("{action:'LIST'}"),unprivileged));
    }
    @Test void failedCollectionRollsBackSalePaymentsAndStock()throws Exception {
        long id=create();payment(id,"PAYMENT","10");change(id,"COMPLETE");finalizeBill(id);
        try(var s=c.createStatement()){s.execute("UPDATE inventory SET quantity_on_hand=1 WHERE product_id=992 AND location_id=992");}
        Savepoint before=c.setSavepoint();assertThrows(SQLException.class,()->collect(id,"CARD"));c.rollback(before);
        equal("0",scalar("SELECT count(*) FROM sales WHERE location_id=992"));equal("1",scalar("SELECT count(*) FROM sales_task_payments WHERE task_id="+id));
    }
    @Test void attachmentsAreBoundToTaskAndAuditRetainsRevisions()throws Exception {
        long id=create();JsonObject b=cmd(id,"ATTACH");b.addProperty("filename","instructions.txt");b.addProperty("content",Base64.getEncoder().encodeToString("Customer original".getBytes(java.nio.charset.StandardCharsets.UTF_8)));SalesTaskService.mutate(c,b,ACTOR,NO_APPROVAL);
        equal("1",scalar("SELECT count(*) FROM sales_task_files WHERE task_id="+id));change(id,"COMPLETE");finalizeBill(id);
        JsonArray backup=new JsonArray();
        try(var s=c.createStatement();var r=s.executeQuery("SELECT to_jsonb(f)::text FROM sales_task_files f WHERE task_id="+id)){r.next();backup.add(JsonParser.parseString(r.getString(1)));}
        try(var s=c.createStatement()){s.execute("DELETE FROM sales_task_files WHERE task_id="+id);}
        assertEquals(1,CloudRecoveryService.restoreRows(c,"sales_task_files",backup));
        try(var s=c.createStatement();var r=s.executeQuery("SELECT convert_from(content,'UTF8') FROM sales_task_files WHERE task_id="+id)){r.next();assertEquals("Customer original",r.getString(1));}
        b=cmd(id,"REOPEN_PRICING");b.addProperty("reason","Add binding");SalesTaskService.mutate(c,b,ACTOR,NO_APPROVAL);
        equal("1",scalar("SELECT count(*) FROM sales_task_events WHERE task_id="+id+" AND action='FINALIZE' AND snapshot->>'billing_status'='FINALIZED'"));
    }
    @Test void cashAdvancesRefundsAndPickupReconcileExactlyOnce()throws Exception {
        long id=create();JsonObject b=cmd(id,"PAYMENT");b.addProperty("amount",30);b.addProperty("method","CASH");SalesTaskService.mutate(c,b,ACTOR,NO_APPROVAL);
        equal("30",CashDrawerService.calculateNetCashActivity(c,992));
        b=cmd(id,"REFUND");b.addProperty("amount",10);b.addProperty("method","CASH");b.addProperty("reason","Partial refund");SalesTaskService.mutate(c,b,ACTOR,NO_APPROVAL);
        equal("20",CashDrawerService.calculateNetCashActivity(c,992));equal("10",CashDrawerService.calculateReturnedCash(c,992));
        change(id,"COMPLETE");finalizeBill(id);collect(id,"CASH");equal("75",CashDrawerService.calculateNetCashActivity(c,992));
        var receipt=SalesTaskService.customerPaymentReceipt(c,992,scalar("SELECT min(payment_id) FROM sales_task_payments WHERE task_id="+id).longValue());
        equal("30",(BigDecimal)receipt.get("paymentAmount"));
        var history=LanCustomerAccountService.payments(c,992,992);assertEquals(3,history.get("paymentCount"));equal("75",(BigDecimal)history.get("totalPayments"));
    }
    @Test void retryKeyReplaysPaymentAndRejectsDifferentPayload()throws Exception {
        long id=create();JsonObject b=cmd(id,"PAYMENT");b.addProperty("amount",10);b.addProperty("method","CARD");b.addProperty("reference","RETRY-REF");
        String key=UUID.randomUUID().toString(),operation="sales-tasks.update.v1",hash=LanSecurity.sha256(b.toString());
        assertNull(LanApiServer.loadIdempotentResult(c,ACTOR.device(),key,operation,hash));
        var result=SalesTaskService.mutate(c,b,ACTOR,NO_APPROVAL);LanApiServer.completeIdempotency(c,ACTOR.device(),key,result);
        var replay=LanApiServer.loadIdempotentResult(c,ACTOR.device(),key,operation,hash);assertEquals(id,((Number)replay.get("taskId")).longValue());
        assertThrows(Exception.class,()->LanApiServer.loadIdempotentResult(c,ACTOR.device(),key,operation,"DIFFERENT"));
        equal("1",scalar("SELECT count(*) FROM sales_task_payments WHERE task_id="+id));
    }
    @Test void simultaneousPickupCreatesOnlyOneSale()throws Exception {
        var actor=new SalesTaskService.Actor(993,992,"Tester",ACTOR.device(),"Test",PERMISSIONS);
        long id=((Number)SalesTaskService.mutate(c,json("{action:'CREATE',customerName:'Concurrency test',instructions:'Print'}"),actor,NO_APPROVAL).get("taskId")).longValue();
        SalesTaskService.mutate(c,cmd(id,"COMPLETE"),actor,NO_APPROVAL);
        JsonObject finalBill=cmd(id,"FINALIZE");finalBill.add("checkout",json("{lines:[{productId:993,quantity:1,unitPrice:25,miscItem:true,miscItemName:'Printing'}]}"));SalesTaskService.mutate(c,finalBill,actor,NO_APPROVAL);
        JsonObject collect=cmd(id,"COLLECT");collect.addProperty("method","CARD");collect.addProperty("reference","CONCURRENT");c.commit();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> pickup=()->{try(var connection=connect()){connection.setAutoCommit(false);start.await();try{SalesTaskService.mutate(connection,collect.deepCopy(),actor,NO_APPROVAL);connection.commit();return true;}catch(SQLException e){connection.rollback();assertTrue(e.getMessage().contains("changed"));return false;}}};
        try {var first=pool.submit(pickup);var second=pool.submit(pickup);start.countDown();assertNotEquals(first.get(20,java.util.concurrent.TimeUnit.SECONDS),second.get(20,java.util.concurrent.TimeUnit.SECONDS));}
        finally{pool.shutdownNow();}
        equal("1",scalar("SELECT count(*) FROM sales WHERE location_id=993"));equal("1",scalar("SELECT count(*) FROM sales_task_payments WHERE task_id="+id));
    }
    @Test void optionsAndFinalizedTaxRatesUseStoreConfiguration()throws Exception {
        assertNotNull(SalesTaskService.read(c,json("{action:'OPTIONS'}"),ACTOR).get("products"));
        long id=create();change(id,"COMPLETE");
        try(var s=c.createStatement()){s.execute("UPDATE company_customization SET vat_enabled=true,vat_use_department_rates=false,vat_fixed_rate_percent=10 WHERE location_id=992");}
        finalizeBill(id);equal("80",scalar("SELECT final_total FROM sales_tasks WHERE task_id="+id));
        try(var s=c.createStatement()){s.execute("UPDATE company_customization SET vat_fixed_rate_percent=20 WHERE location_id=992");}
        collect(id,"CARD");equal("80",scalar("SELECT total_amount FROM sales WHERE location_id=992"));equal("5",scalar("SELECT vat_amount FROM sales WHERE location_id=992"));
    }
    @Test void initialAdvanceIsAtomicWithIntake()throws Exception {
        JsonObject intake=json("{action:'CREATE',customerName:'Walk-in',instructions:'Copy originals',advance:15,method:'CASH'}");
        long id=((Number)SalesTaskService.mutate(c,intake,ACTOR,NO_APPROVAL).get("taskId")).longValue();
        equal("15",scalar("SELECT sum(amount) FROM sales_task_payments WHERE task_id="+id));
        Savepoint before=c.setSavepoint();intake.addProperty("method","CARD");
        assertThrows(SQLException.class,()->SalesTaskService.mutate(c,intake,ACTOR,NO_APPROVAL));c.rollback(before);
        equal("1",scalar("SELECT count(*) FROM sales_tasks WHERE location_id=992"));
    }
    @Test void failureSavingRetryResultRollsBackTheEntirePickup()throws Exception {
        long id=create();payment(id,"PAYMENT","10");change(id,"COMPLETE");finalizeBill(id);
        Savepoint before=c.setSavepoint();collect(id,"CARD");equal("1",scalar("SELECT count(*) FROM sales WHERE location_id=992"));
        assertThrows(SQLException.class,()->LanApiServer.completeIdempotency(c,ACTOR.device(),"unclaimed-test-key",Map.of("taskId",id)));
        c.rollback(before);equal("0",scalar("SELECT count(*) FROM sales WHERE location_id=992"));
        equal("100",scalar("SELECT quantity_on_hand FROM inventory WHERE product_id=992 AND location_id=992"));
        equal("1",scalar("SELECT count(*) FROM sales_task_payments WHERE task_id="+id));
        equal("1",scalar("SELECT count(*) FROM sales_tasks WHERE task_id="+id+" AND status='COMPLETED' AND sale_id IS NULL"));
    }
    @Test void combinedOrdersQueueScopesEachSourceAndOnlyShowsPendingWork()throws Exception {
        long task=create();
        var otherActor=new SalesTaskService.Actor(993,992,"Tester",ACTOR.device(),"Test",PERMISSIONS);
        long other=((Number)SalesTaskService.mutate(c,json("{action:'CREATE',customerName:'Other store work',instructions:'Print'}"),otherActor,NO_APPROVAL).get("taskId")).longValue();
        try(var s=c.createStatement()) {
            s.execute("""
                INSERT INTO custom_orders(order_number,customer_id,customer_name,customer_phone,location_id,
                    order_type,status,assigned_to_user_id,assigned_to_name,order_notes,due_date)
                VALUES('QUEUE-ASSIGNED',992,'Queue Customer','',992,'CUSTOM','IN_PROGRESS',992,'Task Tester','Make a sign',CURRENT_DATE-1),
                    ('QUEUE-UNASSIGNED',992,'Queue Customer','',992,'CUSTOM','NEW',NULL,NULL,'Unassigned work',NULL),
                    ('QUEUE-DELIVERED',992,'Queue Customer','',992,'CUSTOM','DELIVERED',992,'Task Tester','Finished',NULL),
                    ('QUEUE-CANCELLED',992,'Queue Customer','',992,'CUSTOM','CANCELLED',992,'Task Tester','Cancelled',NULL)
                """);
        }
        var zone=java.time.ZoneId.of("America/Guyana");
        var both=OrdersWorkQueueService.load(c,992,992,Set.of("MANAGE_CUSTOM_ORDERS","MANAGE_SALES_TASKS"),zone).rows();
        assertEquals(3,both.size());assertEquals("QUEUE-ASSIGNED",both.get(0).number());assertTrue(both.get(0).overdue());
        assertTrue(both.stream().anyMatch(r->r.kind().equals("SALES_TASK")&&r.id()==task&&r.balance()==null));
        assertFalse(both.stream().anyMatch(r->r.kind().equals("SALES_TASK")&&r.id()==other));
        assertEquals(2,OrdersWorkQueueService.load(c,992,992,Set.of("MANAGE_CUSTOM_ORDERS"),zone).rows().size());
        assertEquals(1,OrdersWorkQueueService.load(c,992,992,Set.of("MANAGE_SALES_TASKS"),zone).rows().size());
        var assigned=OrdersWorkQueueService.load(c,992,992,Set.of("VIEW_ASSIGNED_CUSTOM_ORDERS"),zone).rows();
        assertEquals(1,assigned.size());assertEquals("QUEUE-ASSIGNED",assigned.get(0).number());
        assertThrows(SQLException.class,()->OrdersWorkQueueService.load(c,992,992,Set.of(),zone));
        change(task,"COMPLETE");
        assertEquals("Awaiting pricing",OrdersWorkQueueService.load(c,992,992,Set.of("MANAGE_SALES_TASKS"),zone).rows().get(0).stage());
        finalizeBill(task);
        assertEquals("Ready for pickup",OrdersWorkQueueService.load(c,992,992,Set.of("MANAGE_SALES_TASKS"),zone).rows().get(0).stage());
        collect(task,"CARD");assertTrue(OrdersWorkQueueService.load(c,992,992,Set.of("MANAGE_SALES_TASKS"),zone).rows().isEmpty());
    }
    @Test void asapPersistsAndSortsBeforeScheduledWorkWithoutAnArtificialDate()throws Exception {
        long regular=create();
        JsonObject request=json("{action:'CREATE',customerName:'Urgent',instructions:'Copy now',dueAsap:true,dueAt:'2026-12-31T12:00-04:00'}");
        long id=((Number)SalesTaskService.mutate(c,request,ACTOR,NO_APPROVAL).get("taskId")).longValue();
        var detail=SalesTaskService.read(c,json("{action:'DETAILS',taskId:"+id+"}"),ACTOR);
        var task=(JsonObject)detail.get("task");assertTrue(task.get("due_asap").getAsBoolean());assertTrue(task.get("due_at").isJsonNull());
        var queue=OrdersWorkQueueService.load(c,992,992,Set.of("MANAGE_SALES_TASKS"),java.time.ZoneId.of("America/Guyana")).rows();
        assertEquals(id,queue.get(0).id());assertTrue(queue.get(0).asap());assertFalse(queue.get(0).overdue());
        JsonObject edit=cmd(id,"EDIT");edit.addProperty("customerName","Urgent");edit.addProperty("instructions","Copy later");edit.addProperty("dueAsap",false);edit.addProperty("dueAt","2026-12-31T12:00-04:00");
        SalesTaskService.mutate(c,edit,ACTOR,NO_APPROVAL);
        task=(JsonObject)SalesTaskService.read(c,json("{action:'DETAILS',taskId:"+id+"}"),ACTOR).get("task");
        assertFalse(task.get("due_asap").getAsBoolean());assertFalse(task.get("due_at").isJsonNull());
    }
    @Test void customerLookupFindsAccountsBeyondTheInitialDropdownByTheirContactDetails()throws Exception {
        try(var s=c.createStatement()) {
            s.execute("INSERT INTO customer_accounts(customer_id,name,account_number) SELECT i,'AA lookup seed '||i,'LOOKUP-'||i FROM generate_series(3000,5001) i");
            s.execute("INSERT INTO customer_accounts(customer_id,name,account_number,phone,email) VALUES(5999,'ZZ Search Customer','SEARCH-5999','+5927058194','lookup-test@example.invalid')");
            s.execute("INSERT INTO customer_accounts(customer_id,name,account_number,is_active) VALUES(6000,'ZZ Hidden Customer','SEARCH-HIDDEN',false)");
        }
        for(String search:List.of("ZZ Search","7058194","SEARCH-5999","lookup-test@example.invalid")) {
            var request=json("{action:'CUSTOMERS'}");request.addProperty("search",search);
            var found=SalesTaskService.read(c,request,ACTOR);
            var rows=(List<?>)found.get("customers");assertEquals(1,rows.size());assertEquals(5999,((Number)((Map<?,?>)rows.get(0)).get("customer_id")).intValue());
        }
        var hidden=SalesTaskService.read(c,json("{action:'CUSTOMERS',search:'SEARCH-HIDDEN'}"),ACTOR);
        assertTrue(((List<?>)hidden.get("customers")).isEmpty());
        var denied=new SalesTaskService.Actor(992,992,"Tester",ACTOR.device(),"Test",Set.of());
        assertThrows(SQLException.class,()->SalesTaskService.read(c,json("{action:'CUSTOMERS',search:'SEARCH-5999'}"),denied));
    }
}
