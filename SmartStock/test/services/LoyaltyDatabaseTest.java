package services;

import com.google.gson.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Runs only against the explicitly named, disposable loopback database. */
@EnabledIfSystemProperty(named="smartstock.test.loyaltyDatabaseUrl",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/loyalty_test")
class LoyaltyDatabaseTest {
    static Connection open()throws SQLException {var c=DriverManager.getConnection(System.getProperty("smartstock.test.loyaltyDatabaseUrl"),"postgres","");c.setAutoCommit(false);return c;}
    @BeforeAll static void install()throws Exception {
        try(var c=open()) {
            try(var s=c.createStatement()){s.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");}c.commit();c.setAutoCommit(true);SchemaContractService.installLocalBaseline(c);SchemaContractService.requireLocalReady(c);c.setAutoCommit(false);
            try(var s=c.createStatement()){
                s.execute("INSERT INTO locations(location_id,name) VALUES(990,'Loyalty test store')");
                s.execute("INSERT INTO users(user_id,username,full_name,role_id,is_active) VALUES(990,'loyalty-admin','Loyalty Admin',1,true),(991,'loyalty-nonadmin','Nonadmin',2,true)");
                s.execute("INSERT INTO loyalty_settings(version,enabled,authority_location_id,spend_per_award,points_per_award,point_value,minimum_redemption,redemption_cap_percent) VALUES(1,true,990,100,1,1,100,20)");
            }c.commit();
        }
    }
    static JsonObject state(UUID doc,UUID customer,long revision,long earn,long restored,long pending){var b=new JsonObject();b.addProperty("documentUuid",doc.toString());b.addProperty("customerUuid",customer.toString());b.addProperty("revision",revision);b.addProperty("settingsVersion",1);b.addProperty("earnedPoints",earn);b.addProperty("restoredPoints",restored);b.addProperty("pendingPoints",pending);return b;}
    static JsonObject reservation(UUID doc,UUID customer,long points){var b=new JsonObject();b.addProperty("documentUuid",doc.toString());b.addProperty("customerUuid",customer.toString());b.addProperty("points",points);b.addProperty("eligible",new BigDecimal("10000"));b.addProperty("preview",false);return b;}
    static long points(Connection c,UUID customer)throws Exception{return LoyaltyService.authorityBalance(c,customer).get("points").getAsLong();}
    @Test void baselineAndMigrationAreIdempotent()throws Exception {try(var c=open()){assertTrue(SchemaContractService.validateLocal(c).ready());SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/migrations/v1_after/20261021190000_customer_loyalty.sql"));c.rollback();}}
    @Test void retriesAndOutOfOrderDeliveryDoNotDuplicateAwards()throws Exception {try(var c=open()){UUID doc=UUID.randomUUID(),customer=UUID.randomUUID();var event=state(doc,customer,2,100,0,0);LoyaltyService.apply(c,event,990);LoyaltyService.apply(c,event,990);LoyaltyService.apply(c,state(doc,customer,1,50,0,0),990);assertEquals(100,points(c,customer));LoyaltyService.apply(c,state(doc,customer,3,0,0,100),990);assertEquals(0,points(c,customer));assertEquals(100,LoyaltyService.authorityBalance(c,customer).get("pendingPoints").getAsLong());c.rollback();}}
    @Test void reservationsCommitOnceAndRefundRestoresOriginalLots()throws Exception {try(var c=open()){UUID customer=UUID.randomUUID(),source=UUID.randomUUID(),sale=UUID.randomUUID();LoyaltyService.apply(c,state(source,customer,1,1000,0,0),990);var request=reservation(sale,customer,100);var r=LoyaltyService.authorityReserve(c,request,991);assertEquals(new BigDecimal("100.00"),r.discount());assertEquals(900,points(c,customer));LoyaltyService.authorityReserve(c,request,991);assertEquals(900,points(c,customer));LoyaltyService.apply(c,state(sale,customer,1,9,0,0),991);assertEquals(909,points(c,customer));LoyaltyService.apply(c,state(sale,customer,2,0,100,0),991);assertEquals(1000,points(c,customer));LoyaltyService.apply(c,state(sale,customer,2,0,100,0),991);assertEquals(1000,points(c,customer));c.rollback();}}
    @Test void spentAwardReversalCreatesDebtAndNewEarningsOffsetIt()throws Exception {try(var c=open()){UUID customer=UUID.randomUUID(),source=UUID.randomUUID(),sale=UUID.randomUUID();LoyaltyService.apply(c,state(source,customer,1,100,0,0),990);LoyaltyService.authorityReserve(c,reservation(sale,customer,100),990);LoyaltyService.apply(c,state(sale,customer,1,0,0,0),990);LoyaltyService.apply(c,state(source,customer,2,0,0,0),990);assertEquals(-100,points(c,customer));assertThrows(SQLException.class,()->LoyaltyService.authorityReserve(c,reservation(UUID.randomUUID(),customer,100),990));LoyaltyService.apply(c,state(UUID.randomUUID(),customer,1,150,0,0),990);assertEquals(50,points(c,customer));c.rollback();}}
    @Test void releaseAndRollbackDoNotSpendPoints()throws Exception {try(var c=open()){UUID customer=UUID.randomUUID(),doc=UUID.randomUUID();LoyaltyService.apply(c,state(UUID.randomUUID(),customer,1,200,0,0),990);var save=c.setSavepoint();LoyaltyService.authorityReserve(c,reservation(doc,customer,100),991);assertEquals(100,points(c,customer));c.rollback(save);assertEquals(200,points(c,customer));LoyaltyService.authorityReserve(c,reservation(doc,customer,100),991);LoyaltyService.release(c,doc,990);assertEquals(100,points(c,customer));LoyaltyService.release(c,doc,991);assertEquals(200,points(c,customer));c.rollback();}}
    @Test void mergeMovesLotsReservationsAndHistoryWithoutDuplication()throws Exception {try(var c=open()){UUID source=UUID.randomUUID(),target=UUID.randomUUID(),doc=UUID.randomUUID();LoyaltyService.apply(c,state(UUID.randomUUID(),source,1,200,0,0),990);LoyaltyService.apply(c,state(UUID.randomUUID(),target,1,300,0,0),990);LoyaltyService.authorityReserve(c,reservation(doc,source,100),991);LoyaltyService.merge(c,source,target);assertEquals(400,points(c,target));assertEquals(400,points(c,source));LoyaltyService.apply(c,state(doc,source,1,1,0,0),991);assertEquals(401,points(c,target));LoyaltyService.merge(c,source,target);assertEquals(401,points(c,target));c.rollback();}}
    @Test void nonadministratorCannotChangeSettingsOrAdjustPoints()throws Exception {try(var c=open()){var s=LoyaltyService.settings(c);assertEquals("42501",assertThrows(SQLException.class,()->LoyaltyService.saveSettings(c,s,991,990)).getSQLState());assertEquals("42501",assertThrows(SQLException.class,()->LoyaltyService.adjust(c,990,100,"test",UUID.randomUUID(),991,990)).getSQLState());c.rollback();}}
    @Test void twoStoresCannotReserveTheSamePoints()throws Exception {
        UUID customer=UUID.randomUUID();try(var c=open()){LoyaltyService.apply(c,state(UUID.randomUUID(),customer,1,100,0,0),990);c.commit();}
        var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var tasks=new ArrayList<Future<Boolean>>();for(int origin:new int[]{991,992})tasks.add(executor.submit(()->{start.await();try(var c=open()){try{LoyaltyService.authorityReserve(c,reservation(UUID.randomUUID(),customer,100),origin);c.commit();return true;}catch(SQLException e){c.rollback();return false;}}}));start.countDown();int successes=0;for(var task:tasks)if(task.get(15,TimeUnit.SECONDS))successes++;assertEquals(1,successes);}finally{executor.shutdownNow();}
    }
    @Test void realCheckoutAndPartialReturnsPreserveMoneyAndPoints()throws Exception {
        try(var c=open()) {
            UUID device=UUID.randomUUID(),doc=UUID.randomUUID(),customer=UUID.randomUUID();
            try(var p=c.prepareStatement("INSERT INTO customer_accounts(customer_id,name,sync_uuid) VALUES(990,'Checkout loyalty fixture',?)")){p.setObject(1,customer);p.executeUpdate();}
            try(var p=c.prepareStatement("INSERT INTO devices(device_id,installation_id,last_store_id,is_approved,allow_sales) VALUES(?,?,990,true,true)")){p.setObject(1,device);p.setString(2,device.toString());p.executeUpdate();}
            try(var s=c.createStatement()){s.execute("INSERT INTO products(product_id,name,sku,price,product_type) VALUES(990,'Loyalty fixture','LOYALTY-FIXTURE',1000,'NON_INVENTORY')");s.execute("INSERT INTO company_customization(location_id,round_sales_to_nearest_twenty) VALUES(990,false) ON CONFLICT(location_id) DO UPDATE SET round_sales_to_nearest_twenty=false");}
            LoyaltyService.apply(c,state(UUID.randomUUID(),customer,1,500,0,0),990);
            JsonObject request=JsonParser.parseString("{\"paymentMethod\":\"CARD\",\"paymentReference\":\"test\",\"customerId\":990,\"saleDiscountPercent\":0,\"loyaltyPoints\":100,\"lines\":[{\"productId\":990,\"quantity\":3,\"unitPrice\":1000,\"discountPercent\":0}]} ").getAsJsonObject();request.addProperty("loyaltyDocumentUuid",doc.toString());
            var result=LanSalesService.checkout(c,request,device,990,"Admin",990,(a,b,d,e)->{throw new AssertionError("Unexpected approval");});assertEquals(new BigDecimal("2900.00"),result.get("total"));
            LoyaltyService.apply(c,LoyaltyWorker.payload(c,doc),990);assertEquals(429,points(c,customer));
            int sale=((Number)result.get("saleId")).intValue(),line;
            try(var p=c.prepareStatement("SELECT sale_item_id FROM sale_items WHERE sale_id=?")){p.setInt(1,sale);try(var r=p.executeQuery()){r.next();line=r.getInt(1);}}
            BigDecimal refunded=BigDecimal.ZERO;
            for(int i=0;i<3;i++) {
                var refund=new JsonObject();refund.addProperty("saleId",sale);refund.addProperty("refundMethod","CARD");refund.addProperty("reason","Loyalty integration return");var lines=new JsonArray();var item=new JsonObject();item.addProperty("saleItemId",line);item.addProperty("quantity",1);lines.add(item);refund.add("lines",lines);
                var returned=LanRefundService.refund(c,refund,device,990,"Admin",990,(a,b,d,e,f)->{throw new AssertionError("Unexpected return approval");});refunded=refunded.add((BigDecimal)returned.get("refundAmount"));LoyaltyService.apply(c,LoyaltyWorker.payload(c,doc),990);
            }
            assertEquals(new BigDecimal("2900.00"),refunded);assertEquals(500,points(c,customer));c.rollback();
        }
    }
    @Test void customOrdersWaitForCompletionAndLockRedemptionAfterPayment()throws Exception {
        try(var c=open()) {
            UUID customer=UUID.randomUUID(),doc=UUID.randomUUID();
            try(var p=c.prepareStatement("INSERT INTO customer_accounts(customer_id,name,sync_uuid,current_balance) VALUES(990,'Custom loyalty fixture',?,77)")){p.setObject(1,customer);p.executeUpdate();}
            try(var p=c.prepareStatement("INSERT INTO custom_orders(custom_order_id,order_number,customer_id,customer_name,customer_phone,location_id,total_amount,balance_due,sync_uuid) VALUES(990,'LOYALTY-ORDER',990,'Customer','',990,10000,10000,?)")){p.setObject(1,doc);p.executeUpdate();}
            try(var s=c.createStatement()){s.execute("INSERT INTO custom_order_lines(custom_order_line_id,custom_order_id,item_name,pricing_type,unit_price,line_total,customization_details) VALUES(990,990,'Custom item','FIXED',10000,10000,'Test')");}
            LoyaltyService.apply(c,state(UUID.randomUUID(),customer,1,500,0,0),990);LoyaltyCheckout.customOrder(c,990,100,990);var pending=LoyaltyWorker.payload(c,doc);assertEquals(0,pending.get("earnedPoints").getAsLong());assertEquals(99,pending.get("pendingPoints").getAsLong());LoyaltyService.apply(c,pending,990);assertEquals(400,points(c,customer));
            try(var s=c.createStatement();var r=s.executeQuery("SELECT current_balance FROM customer_accounts WHERE customer_id=990")){r.next();assertEquals(new BigDecimal("77.00"),r.getBigDecimal(1));}
            try(var s=c.createStatement()){s.execute("UPDATE custom_orders SET amount_paid=9900,balance_due=0,payment_status='PAID' WHERE custom_order_id=990");}
            assertEquals(0,LoyaltyWorker.payload(c,doc).get("earnedPoints").getAsLong());
            try(var s=c.createStatement()){s.execute("UPDATE custom_orders SET status='COMPLETED' WHERE custom_order_id=990");}
            LoyaltyService.apply(c,LoyaltyWorker.payload(c,doc),990);assertEquals(499,points(c,customer));assertThrows(SQLException.class,()->LoyaltyCheckout.customOrder(c,990,100,990));
            try(var s=c.createStatement()){s.execute("UPDATE custom_orders SET status='READY' WHERE custom_order_id=990");}
            LoyaltyService.apply(c,LoyaltyWorker.payload(c,doc),990);assertEquals(499,points(c,customer));
            try(var s=c.createStatement()){s.execute("UPDATE custom_orders SET status='CANCELLED' WHERE custom_order_id=990");}
            LoyaltyService.apply(c,LoyaltyWorker.payload(c,doc),990);assertEquals(500,points(c,customer));c.rollback();
        }
    }
    @Test void valueChangesPreserveAwardsAndPartialRefundRestoresEachOriginalValue()throws Exception {
        try(var c=open()){
            UUID customer=UUID.randomUUID(),sale=UUID.randomUUID();LoyaltyService.apply(c,state(UUID.randomUUID(),customer,1,100,0,0),990);
            var next=new LoyaltyPolicy.Settings(1,true,990,new BigDecimal("100.00"),1,new BigDecimal("2.00"),100,new BigDecimal("20"));LoyaltyService.saveSettings(c,next,990,990);
            var newer=state(UUID.randomUUID(),customer,1,100,0,0);newer.addProperty("settingsVersion",2);LoyaltyService.apply(c,newer,990);
            var redemption=LoyaltyService.authorityReserve(c,reservation(sale,customer,150),990);assertEquals(new BigDecimal("200.00"),redemption.discount());
            LoyaltyService.apply(c,state(sale,customer,1,0,0,0),990);var partial=state(sale,customer,2,0,75,0);partial.addProperty("restorationFraction",new BigDecimal("0.5"));LoyaltyService.apply(c,partial,990);
            var balance=LoyaltyService.authorityBalance(c,customer);assertEquals(125,balance.get("points").getAsLong());assertEquals(0,new BigDecimal("200").compareTo(balance.get("value").getAsBigDecimal()));
            assertThrows(SQLException.class,()->LoyaltyService.saveSettings(c,new LoyaltyPolicy.Settings(2,true,991,new BigDecimal("100"),1,new BigDecimal("1"),100,new BigDecimal("20")),990,990));c.rollback();
        }
    }
    @Test void recoveryKeepsUncertainReservationsAndReleasesOnlyAfterOriginTransactionEnds()throws Exception {
        UUID customer=UUID.randomUUID(),doc=UUID.randomUUID();
        try(var c=open()){LoyaltyService.apply(c,state(UUID.randomUUID(),customer,1,200,0,0),990);LoyaltyService.authorityReserve(c,reservation(doc,customer,100),991);c.commit();}
        try(var origin=open();var recovery=open()){
            LoyaltyCheckout.lockDocument(origin,doc);
            try(var intent=open();var p=intent.prepareStatement("INSERT INTO loyalty_remote_intents(document_uuid,authority_location_id,source_location_id) VALUES(?,990,991)")){p.setObject(1,doc);p.executeUpdate();intent.commit();}
            var calls=new java.util.concurrent.atomic.AtomicInteger();
            LoyaltyWorker.reconcileIntents(recovery,991,(a,b,d,e)->{calls.incrementAndGet();throw new java.io.IOException("Simulated offline authority");});assertEquals(0,calls.get());
            origin.rollback(); // Connection loss or server restart releases this transaction lock too.
            LoyaltyWorker.reconcileIntents(recovery,991,(a,b,d,e)->{calls.incrementAndGet();throw new java.io.IOException("Simulated uncertain response");});assertEquals(1,calls.get());
            try(var c=open()){assertEquals(100,points(c,customer));c.rollback();}
            LoyaltyWorker.reconcileIntents(recovery,991,(a,b,d,e)->{try(var c=open()){LoyaltyService.release(c,UUID.fromString(e.get("documentUuid").getAsString()),b);c.commit();}return new JsonObject();});
            try(var c=open()){assertEquals(200,points(c,customer));try(var p=c.prepareStatement("SELECT 1 FROM loyalty_remote_intents WHERE document_uuid=?")){p.setObject(1,doc);try(var r=p.executeQuery()){assertFalse(r.next());}}c.rollback();}
        }
    }
    @Test void invoiceBilledProductionDoesNotEarnFromItsZeroProductionBalance()throws Exception {
        try(var c=open()){
            UUID customer=UUID.randomUUID(),doc=UUID.randomUUID();
            try(var p=c.prepareStatement("INSERT INTO customer_accounts(customer_id,name,sync_uuid) VALUES(990,'Invoice loyalty fixture',?)")){p.setObject(1,customer);p.executeUpdate();}
            try(var s=c.createStatement()){s.execute("INSERT INTO invoices(invoice_id,invoice_number,customer_id,customer_name,total_amount,balance_due) VALUES(990,'LOYALTY-INVOICE',990,'Customer',10000,10000)");}
            try(var p=c.prepareStatement("INSERT INTO custom_orders(custom_order_id,order_number,customer_id,customer_name,customer_phone,location_id,total_amount,balance_due,status,invoice_billed,source_invoice_id,sync_uuid) VALUES(990,'LOYALTY-INVOICE-ORDER',990,'Customer','',990,10000,0,'COMPLETED',true,990,?)")){p.setObject(1,doc);p.executeUpdate();}
            try(var s=c.createStatement()){s.execute("INSERT INTO custom_order_lines(custom_order_line_id,custom_order_id,item_name,pricing_type,unit_price,line_total,customization_details) VALUES(990,990,'Custom item','FIXED',10000,10000,'Test')");}
            var unpaid=LoyaltyWorker.payload(c,doc);assertEquals(0,unpaid.get("earnedPoints").getAsLong());assertEquals(100,unpaid.get("pendingPoints").getAsLong());LoyaltyService.apply(c,unpaid,990);
            try(var s=c.createStatement()){s.execute("UPDATE invoices SET amount_paid=10000,balance_due=0,payment_status='PAID' WHERE invoice_id=990");}
            LoyaltyService.apply(c,LoyaltyWorker.payload(c,doc),990);assertEquals(100,points(c,customer));
            try(var s=c.createStatement()){s.execute("UPDATE invoices SET status='CANCELLED' WHERE invoice_id=990");}
            LoyaltyService.apply(c,LoyaltyWorker.payload(c,doc),990);assertEquals(0,points(c,customer));c.rollback();
        }
    }
    @Test void newPointValuesDoNotLeaveAnUnspendableValueAfterOffsettingDebt()throws Exception {
        try(var c=open()){
            UUID customer=UUID.randomUUID(),source=UUID.randomUUID(),sale=UUID.randomUUID();LoyaltyService.apply(c,state(source,customer,1,100,0,0),990);LoyaltyService.authorityReserve(c,reservation(sale,customer,100),990);LoyaltyService.apply(c,state(sale,customer,1,0,0,0),990);LoyaltyService.apply(c,state(source,customer,2,0,0,0),990);
            LoyaltyService.saveSettings(c,new LoyaltyPolicy.Settings(1,true,990,new BigDecimal("100"),1,new BigDecimal("2.00"),100,new BigDecimal("20")),990,990);
            var award=state(UUID.randomUUID(),customer,1,100,0,0);award.addProperty("settingsVersion",2);LoyaltyService.apply(c,award,990);var balance=LoyaltyService.authorityBalance(c,customer);assertEquals(0,balance.get("points").getAsLong());assertEquals(0,balance.get("value").getAsBigDecimal().signum());c.rollback();
        }
    }
}
