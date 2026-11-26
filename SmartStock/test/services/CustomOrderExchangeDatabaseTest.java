package services;

import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Full store schema in a disposable database; never loads store credentials. */
class CustomOrderExchangeDatabaseTest {
    private static final String DEVICE="ec000000-0000-0000-0000-000000000001";
    private static CustomOrderExchangeService.Request request(String fingerprint,boolean restock,String refundMethod) {
        return new CustomOrderExchangeService.Request(990,990,991,991L,restock,fingerprint,"Customer requested another plaque",refundMethod,"refund-123");
    }
    private static String preview(Connection c)throws Exception {
        return (String)CustomOrderExchangeService.preview(c,990,990,request(null,true,null)).get("fingerprint");
    }
    private static void exchange(Connection c,String fingerprint,boolean restock,String method)throws Exception {
        CustomOrderExchangeService.exchange(c,990,990,"Test Admin",DEVICE,"Test register",request(fingerprint,restock,method));
    }
    @Test void exchangesAreAuthorizedAtomicPricedAndAudited()throws Exception {
        String admin=System.getProperty("exchange.test.admin","");assumeTrue(!admin.isBlank());
        if(!admin.equals("jdbc:postgresql://127.0.0.1:55479/postgres"))throw new IllegalArgumentException("Use the disposable exchange cluster on port 55479.");
        String db="exchange_test_"+UUID.randomUUID().toString().replace("-","");
        try(Connection root=DriverManager.getConnection(admin,"exchange_test","")) {
            sql(root,"CREATE DATABASE "+db);
            try(Connection c=DriverManager.getConnection(admin.replace("/postgres","/"+db),"exchange_test","")) {
                SchemaContractService.installLocalBaseline(c);
                SchemaContractService.requireLocalReady(c);
                sql(c,"""
                    INSERT INTO locations(location_id,name) VALUES(990,'Exchange test');
                    INSERT INTO users(user_id,username,full_name,role_id) VALUES(990,'exchange-admin','Test Admin',1),(991,'exchange-manager','Test Manager',2);
                    INSERT INTO customer_accounts(customer_id,name,phone) VALUES(990,'Test Customer','6000000');
                    INSERT INTO company_customization(location_id) VALUES(990);
                    INSERT INTO devices(device_id,installation_id,device_name) VALUES('ec000000-0000-0000-0000-000000000001','exchange-test','Test register');
                    INSERT INTO custom_order_items(custom_item_id,item_name,product_type,pricing_type,has_variants,quantity_on_hand,sold_quantity) VALUES(990,'Original plaque','INVENTORY','FIXED',true,5,1),(991,'Replacement plaque','INVENTORY','FIXED',true,4,0);
                    INSERT INTO custom_order_item_variants(custom_variant_id,custom_item_id,variant_name,fixed_price,quantity_on_hand,sold_quantity,size,template_width,template_height,template_unit) VALUES(990,990,'Star',1000,5,1,'Large',3,6,'IN'),(991,991,'Pointed',1200,4,0,'Medium',5,8,'IN');
                    INSERT INTO custom_orders(custom_order_id,order_number,customer_id,customer_name,customer_phone,location_id,total_amount,amount_paid,balance_due,payment_status,pricing_state,status) VALUES(990,'EXCHANGE-TEST',990,'Test Customer','6000000',990,1000,1000,0,'PAID','FINAL','IN_PROGRESS');
                    INSERT INTO custom_order_lines(custom_order_line_id,custom_order_id,custom_item_id,custom_variant_id,item_name,variant_name,pricing_type,unit_price,line_total,base_item_price,original_base_price,area_price,original_line_total,production_status,order_instructions,customization_details) VALUES(990,990,990,990,'Original plaque','Star','FIXED',1000,1000,1000,1000,1000,1000,'STARTED','Keep the inscription','Test engraving');
                    INSERT INTO custom_order_inventory_reservations(custom_order_id,custom_order_line_id,custom_item_id,custom_variant_id,item_name,variant_name,reserved_qty,status) VALUES(990,990,990,990,'Original plaque','Star',1,'RESERVED');
                    INSERT INTO custom_order_payments(custom_order_id,payment_amount,payment_method,payment_action) VALUES(990,1000,'CARD','PAYMENT');
                    """);
                sql(c,"INSERT INTO custom_variant_inventory(custom_variant_id,location_id,quantity_on_hand,sold_quantity) SELECT custom_variant_id,990,quantity_on_hand,sold_quantity FROM custom_order_item_variants");
                c.setAutoCommit(false);

                var denied=assertThrows(SQLException.class,()->CustomOrderExchangeService.preview(c,990,991,request(null,true,null)));
                assertEquals("42501",denied.getSQLState());c.rollback();
                assertThrows(SQLException.class,()->CustomOrderExchangeService.exchange(c,990,991,"Manager",DEVICE,"POS",request(null,true,null)));c.rollback();
                assertThrows(SQLException.class,()->CustomOrderExchangeService.preview(c,991,990,request(null,true,null)));c.rollback();

                exchange(c,preview(c),true,null);
                assertEquals(991,scalar(c,"SELECT custom_variant_id FROM custom_order_lines WHERE custom_order_line_id=990"));
                assertEquals(6,scalar(c,"SELECT quantity_on_hand FROM custom_variant_inventory WHERE custom_variant_id=990 AND location_id=990"));
                assertEquals(0,scalar(c,"SELECT sold_quantity FROM custom_variant_inventory WHERE custom_variant_id=990 AND location_id=990"));
                assertEquals(3,scalar(c,"SELECT custom_stock_value(991,NULL,990,'quantity_on_hand')"));
                assertEquals(200,scalar(c,"SELECT balance_due FROM custom_orders WHERE custom_order_id=990"));
                assertEquals("NOT_STARTED",text(c,"SELECT production_status FROM custom_order_lines WHERE custom_order_line_id=990"));
                assertEquals("Keep the inscription",text(c,"SELECT order_instructions FROM custom_order_lines WHERE custom_order_line_id=990"));
                assertEquals(5,scalar(c,"SELECT template_width FROM custom_order_lines WHERE custom_order_line_id=990"));
                assertEquals(2,scalar(c,"SELECT count(*) FROM custom_order_item_movements"));
                assertEquals(1,scalar(c,"SELECT count(*) FROM custom_order_audit_log WHERE action_type='ITEM_EXCHANGE'"));
                assertEquals(1,scalar(c,"SELECT count(*) FROM custom_order_payments"));
                assertEquals("RELEASED",text(c,"SELECT status FROM custom_order_inventory_reservations WHERE custom_variant_id=990"));
                c.rollback();
                assertEquals(990,scalar(c,"SELECT custom_variant_id FROM custom_order_lines WHERE custom_order_line_id=990"));
                assertEquals(0,scalar(c,"SELECT count(*) FROM custom_order_item_movements"));

                exchange(c,preview(c),false,null);
                assertEquals(5,scalar(c,"SELECT quantity_on_hand FROM custom_variant_inventory WHERE custom_variant_id=990 AND location_id=990"));
                assertEquals(1,scalar(c,"SELECT sold_quantity FROM custom_variant_inventory WHERE custom_variant_id=990 AND location_id=990"));
                assertEquals("CONSUMED",text(c,"SELECT status FROM custom_order_inventory_reservations WHERE custom_variant_id=990"));
                assertEquals(1,scalar(c,"SELECT count(*) FROM custom_order_item_movements"));c.rollback();

                sql(c,"UPDATE custom_order_item_variants SET fixed_price=700 WHERE custom_variant_id=991");
                String cheap=preview(c);
                assertThrows(SQLException.class,()->exchange(c,cheap,true,null));
                assertEquals(5,scalar(c,"SELECT quantity_on_hand FROM custom_variant_inventory WHERE custom_variant_id=990 AND location_id=990"));
                assertThrows(SQLException.class,()->exchange(c,cheap,true,"CASH"));
                assertEquals(4,scalar(c,"SELECT quantity_on_hand FROM custom_variant_inventory WHERE custom_variant_id=991 AND location_id=990"));
                exchange(c,cheap,true,"CARD");
                assertEquals(700,scalar(c,"SELECT amount_paid FROM custom_orders WHERE custom_order_id=990"));
                assertEquals(0,scalar(c,"SELECT balance_due FROM custom_orders WHERE custom_order_id=990"));
                assertEquals(300,scalar(c,"SELECT payment_amount FROM custom_order_payments WHERE payment_action='REFUND'"));
                assertEquals(0,scalar(c,"SELECT count(*) FROM custom_order_payments WHERE payment_action='REFUND' AND cash_drawer_session_id IS NOT NULL"));c.rollback();

                sql(c,"UPDATE custom_order_item_variants SET fixed_price=1000 WHERE custom_variant_id=991");
                exchange(c,preview(c),true,null);
                assertEquals("PAID",text(c,"SELECT payment_status FROM custom_orders WHERE custom_order_id=990"));
                assertEquals(0,scalar(c,"SELECT balance_due FROM custom_orders WHERE custom_order_id=990"));c.rollback();

                String stale=preview(c);sql(c,"UPDATE custom_order_item_variants SET fixed_price=1300 WHERE custom_variant_id=991");
                assertThrows(SQLException.class,()->exchange(c,stale,true,null));c.rollback();
                sql(c,"UPDATE custom_variant_inventory SET quantity_on_hand=0 WHERE custom_variant_id=991 AND location_id=990");
                assertThrows(SQLException.class,()->preview(c));c.rollback();
                sql(c,"UPDATE custom_orders SET status='DELIVERED' WHERE custom_order_id=990");assertThrows(SQLException.class,()->preview(c));c.rollback();
                sql(c,"UPDATE custom_order_lines SET return_status='PARTIAL',returned_amount=100 WHERE custom_order_line_id=990");assertThrows(SQLException.class,()->preview(c));c.rollback();
                sql(c,"UPDATE custom_orders SET invoice_billed=true WHERE custom_order_id=990");assertThrows(SQLException.class,()->preview(c));c.rollback();
                sql(c,"UPDATE users SET is_active=false WHERE user_id=990");assertThrows(SQLException.class,()->preview(c));c.rollback();
                sql(c,"UPDATE customer_accounts SET require_charge_authorization=true WHERE customer_id=990");
                assertEquals(true,CustomOrderExchangeService.preview(c,990,990,request(null,true,null)).get("requireAuthorization"));
                assertThrows(IllegalArgumentException.class,()->exchange(c,preview(c),true,null));
                String authorizedPreview=preview(c);
                var authorized=new CustomOrderExchangeService.Request(990,990,991,991L,true,authorizedPreview,"Customer approved the replacement",null,null,new CustomOrderExchangeService.Authorization("Test representative",null,"Signature pad unavailable"));
                CustomOrderExchangeService.exchange(c,990,990,"Test Admin",DEVICE,"Test POS",authorized);
                assertEquals(1,scalar(c,"SELECT count(*) FROM customer_charge_authorizations WHERE document_type='CUSTOM_ORDER' AND document_id=990"));c.rollback();

                // A failure after stock changes must roll the entire exchange back.
                sql(c,"ALTER TABLE custom_order_audit_log ADD CONSTRAINT exchange_test_failure CHECK(action_type<>'ITEM_EXCHANGE') NOT VALID");
                assertThrows(SQLException.class,()->exchange(c,preview(c),true,null));c.rollback();
                assertEquals(5,scalar(c,"SELECT quantity_on_hand FROM custom_variant_inventory WHERE custom_variant_id=990 AND location_id=990"));
                assertEquals(4,scalar(c,"SELECT quantity_on_hand FROM custom_variant_inventory WHERE custom_variant_id=991 AND location_id=990"));
                assertEquals(990,scalar(c,"SELECT custom_variant_id FROM custom_order_lines WHERE custom_order_line_id=990"));

                // The same completed HTTP request key returns its first result without another stock issue.
                UUID device=UUID.fromString(DEVICE);String key=UUID.randomUUID().toString(),hash=LanSecurity.sha256("exchange request"),op="custom-orders.exchange.v1";
                assertNull(LanApiServer.loadIdempotentResult(c,device,key,op,hash));
                var result=CustomOrderExchangeService.exchange(c,990,990,"Test Admin",DEVICE,"Test POS",request(preview(c),true,null));
                LanApiServer.completeIdempotency(c,device,key,result);c.commit();
                assertEquals(true,LanApiServer.loadIdempotentResult(c,device,key,op,hash).get("updated"));
                assertEquals(3,scalar(c,"SELECT quantity_on_hand FROM custom_variant_inventory WHERE custom_variant_id=991 AND location_id=990"));
                assertThrows(Exception.class,()->LanApiServer.loadIdempotentResult(c,device,key,op,LanSecurity.sha256("different exchange")));c.rollback();

                // Two administrators accepting the same preview may only exchange this line once.
                var back=new CustomOrderExchangeService.Request(990,990,990,990L,true,null,"Switch back",null,null);
                String fingerprint=(String)CustomOrderExchangeService.preview(c,990,990,back).get("fingerprint");c.rollback();
                var gate=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
                java.util.concurrent.Callable<Boolean> attempt=()->{try(Connection other=DriverManager.getConnection(admin.replace("/postgres","/"+db),"exchange_test","")) {
                    other.setAutoCommit(false);gate.await();
                    try {CustomOrderExchangeService.exchange(other,990,990,"Test Admin",DEVICE,"Test POS",new CustomOrderExchangeService.Request(990,990,990,990L,true,fingerprint,"Switch back",null,null));other.commit();return true;}
                    catch(SQLException e){other.rollback();if("22023".equals(e.getSQLState()))return false;throw e;}
                }};
                try{var first=pool.submit(attempt);var second=pool.submit(attempt);gate.countDown();assertNotEquals(first.get(20,java.util.concurrent.TimeUnit.SECONDS),second.get(20,java.util.concurrent.TimeUnit.SECONDS));}
                finally{pool.shutdownNow();}
                assertEquals(5,scalar(c,"SELECT quantity_on_hand FROM custom_variant_inventory WHERE custom_variant_id=990 AND location_id=990"));
                assertEquals(4,scalar(c,"SELECT quantity_on_hand FROM custom_variant_inventory WHERE custom_variant_id=991 AND location_id=990"));
                assertEquals(4,scalar(c,"SELECT count(*) FROM custom_order_item_movements"));c.rollback();
            } finally{sql(root,"DROP DATABASE "+db+" WITH (FORCE)");}
        }
    }
    private static void sql(Connection c,String sql)throws SQLException{try(var s=c.createStatement()){s.execute(sql);}}
    private static int scalar(Connection c,String sql)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getInt(1);}}
    private static String text(Connection c,String sql)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getString(1);}}
}

