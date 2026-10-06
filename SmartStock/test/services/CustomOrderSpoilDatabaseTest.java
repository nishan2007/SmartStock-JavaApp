package services;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
/** Runs only against an explicitly requested disposable loopback cluster. */
class CustomOrderSpoilDatabaseTest {
 @Test void stockEvidenceReplayRollbackAndReversal()throws Exception{
  String admin=System.getProperty("spoils.test.admin","");assumeTrue(!admin.isEmpty());
  if(!admin.equals("jdbc:postgresql://127.0.0.1:55441/postgres"))throw new IllegalArgumentException("Use disposable spoil cluster on port 55441.");
  String db="spoil_test_"+UUID.randomUUID().toString().replace("-","");
  try(Connection root=DriverManager.getConnection(admin,"spoil_test","")){
   sql(root,"CREATE DATABASE "+db);
   try(Connection c=DriverManager.getConnection(admin.replace("/postgres","/"+db),"spoil_test","")){
    sql(c,"""
     CREATE TABLE permissions(permission_id serial PRIMARY KEY,permission_key text UNIQUE,permission_name text,description text,permission_group text,permission_subgroup text);
     CREATE TABLE roles(role_id serial PRIMARY KEY,role_name text);INSERT INTO roles(role_name) VALUES('ADMIN');
     CREATE TABLE role_permissions(role_id int,permission_id int,updated_at timestamptz,PRIMARY KEY(role_id,permission_id));
     CREATE TABLE custom_orders(custom_order_id bigint PRIMARY KEY,location_id int,order_number text,customer_name text,status text,created_at timestamptz DEFAULT now());
     CREATE TABLE custom_order_items(custom_item_id bigint PRIMARY KEY,product_type text,quantity_on_hand numeric,sold_quantity numeric DEFAULT 0,updated_at timestamptz);
     CREATE TABLE custom_order_item_variants(custom_variant_id bigint PRIMARY KEY,custom_item_id bigint,quantity_on_hand numeric,sold_quantity numeric DEFAULT 0,is_active bool DEFAULT true,updated_at timestamptz);
     CREATE TABLE custom_order_lines(custom_order_line_id bigint PRIMARY KEY,custom_order_id bigint,custom_item_id bigint,custom_variant_id bigint,variant_name text,item_name text,order_instructions text,production_status text,delivery_status text DEFAULT 'PENDING',return_status text DEFAULT 'NONE',sort_order int DEFAULT 0);
     CREATE TABLE custom_order_item_movements(movement_id serial PRIMARY KEY,custom_item_id bigint,custom_variant_id bigint,location_id int,change_qty numeric,reason text,note text,user_name text,user_id int,device_name text,custom_order_id bigint,custom_order_line_id bigint);
     CREATE TABLE custom_order_audit_log(custom_order_id bigint,action_type text,field_name text,new_value text,reason text,user_id int,user_name text);
     INSERT INTO custom_orders VALUES(1,1,'CO-1','Customer','IN_PROGRESS',now()),(2,2,'CO-2','Other','IN_PROGRESS',now());
     INSERT INTO custom_order_items(custom_item_id,product_type,quantity_on_hand) VALUES(1,'INVENTORY',1),(2,'INVENTORY',3),(3,'SERVICE',0),(4,'INVENTORY',5);
     INSERT INTO custom_order_item_variants(custom_variant_id,custom_item_id,quantity_on_hand) VALUES(21,2,3);
     INSERT INTO custom_order_lines(custom_order_line_id,custom_order_id,custom_item_id,custom_variant_id,item_name) VALUES(11,1,1,NULL,'Bottle'),(12,1,2,21,'Shirt'),(13,1,3,NULL,'Service'),(14,1,NULL,NULL,'Customer supplied'),(15,1,4,NULL,'Concurrent item');
     """);
    String migration=SqlScriptRunner.readResource("database/migrations/v1_after/20261008170000_custom_order_spoils.sql");SqlScriptRunner.runSql(c,migration);SqlScriptRunner.runSql(c,migration);
    c.setAutoCommit(false);var photos=List.of(new CustomOrderSpoilService.Photo(new byte[]{1,2,3}));UUID first=UUID.randomUUID();
    CustomOrderSpoilService.submit(c,1,5,"Employee",first,1,11,"Smudged",photos);c.commit();assertEquals(0,number(c,"SELECT quantity_on_hand FROM custom_order_items WHERE custom_item_id=1"));
    CustomOrderSpoilService.submit(c,1,5,"Employee",first,1,11,"Smudged",photos);c.commit();assertEquals(1,number(c,"SELECT count(*) FROM custom_order_item_movements"));
    assertThrows(IllegalArgumentException.class,()->CustomOrderSpoilService.submit(c,1,5,"Employee",first,1,11,"Different reason",photos));c.rollback();
    var negative=CustomOrderSpoilService.submit(c,1,5,"Employee",UUID.randomUUID(),1,11,"Second spoil",photos);c.commit();assertEquals(true,negative.get("shortage"));assertEquals(-1,number(c,"SELECT quantity_on_hand FROM custom_order_items WHERE custom_item_id=1"));
    UUID variant=UUID.randomUUID();CustomOrderSpoilService.submit(c,1,5,"Employee",variant,1,12,"Variant spoil",photos);c.commit();assertEquals(2,number(c,"SELECT quantity_on_hand FROM custom_order_item_variants WHERE custom_variant_id=21"));assertEquals(2,number(c,"SELECT quantity_on_hand FROM custom_order_items WHERE custom_item_id=2"));
    for(long line:new long[]{13,14}){var result=CustomOrderSpoilService.submit(c,1,5,"Employee",UUID.randomUUID(),1,line,"Service evidence",photos);assertEquals(false,result.get("stockDeducted"));c.commit();}
    UUID concurrent=UUID.randomUUID();
    var gate=new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.Callable<Void> attempt=()->{try(Connection other=DriverManager.getConnection(admin.replace("/postgres","/"+db),"spoil_test","")){other.setAutoCommit(false);gate.await();CustomOrderSpoilService.submit(other,1,5,"Employee",concurrent,1,15,"Concurrent retry",photos);other.commit();return null;}};
    var pool=java.util.concurrent.Executors.newFixedThreadPool(2);try{var a=pool.submit(attempt);var b=pool.submit(attempt);gate.countDown();a.get(15,java.util.concurrent.TimeUnit.SECONDS);b.get(15,java.util.concurrent.TimeUnit.SECONDS);}finally{pool.shutdownNow();}
    assertEquals(4,number(c,"SELECT quantity_on_hand FROM custom_order_items WHERE custom_item_id=4"));
    assertEquals(0,number(c,"SELECT SUM(sold_quantity) FROM custom_order_items"));
    UUID failed=UUID.randomUUID();CustomOrderSpoilService.submit(c,1,5,"Employee",failed,1,11,"Rollback",photos);c.rollback();assertEquals(-1,number(c,"SELECT quantity_on_hand FROM custom_order_items WHERE custom_item_id=1"));
    assertThrows(IllegalArgumentException.class,()->CustomOrderSpoilService.submit(c,2,5,"Employee",UUID.randomUUID(),1,11,"Wrong store",photos));c.rollback();
    sql(c,"UPDATE custom_orders SET status='DELIVERED' WHERE custom_order_id=1");c.commit();assertThrows(IllegalArgumentException.class,()->CustomOrderSpoilService.submit(c,1,5,"Employee",UUID.randomUUID(),1,11,"Closed",photos));c.rollback();
    CustomOrderSpoilService.reverse(c,1,6,"Manager",variant,"Recorded by mistake");c.commit();CustomOrderSpoilService.reverse(c,1,6,"Manager",variant,"Retry");c.commit();assertEquals(3,number(c,"SELECT quantity_on_hand FROM custom_order_item_variants WHERE custom_variant_id=21"));
    assertEquals(6,number(c,"SELECT count(*) FROM custom_order_spoil_photos"));assertEquals(5,number(c,"SELECT count(*) FROM custom_order_item_movements"));
    var state=CustomOrderSpoilService.state(c,1,1);assertEquals(6,((List<?>)state.get("reports")).size());
    UUID photo;try(var p=c.prepareStatement("SELECT photo_id FROM custom_order_spoil_photos LIMIT 1");var r=p.executeQuery()){r.next();photo=(UUID)r.getObject(1);}
    assertThrows(IllegalArgumentException.class,()->CustomOrderSpoilService.photo(c,2,photo));assertNotNull(CustomOrderSpoilService.photo(c,1,photo));
    c.commit();String client="spoil_client_"+UUID.randomUUID().toString().replace("-","");sql(root,"CREATE ROLE "+client+" NOLOGIN");
    try{
     sql(c,"GRANT USAGE ON SCHEMA public TO "+client);sql(c,"GRANT SELECT ON custom_order_spoils,custom_order_spoil_photos TO "+client);c.commit();
     sql(c,"SET ROLE "+client);assertEquals(0,number(c,"SELECT count(*) FROM custom_order_spoils"));assertEquals(0,number(c,"SELECT count(*) FROM custom_order_spoil_photos"));sql(c,"RESET ROLE");c.commit();
    }finally{sql(c,"RESET ROLE");sql(c,"REVOKE SELECT ON custom_order_spoils,custom_order_spoil_photos FROM "+client);sql(c,"REVOKE USAGE ON SCHEMA public FROM "+client);c.commit();sql(root,"DROP ROLE "+client);}

   }finally{sql(root,"DROP DATABASE "+db+" WITH (FORCE)");}
  }
 }
 @Test void freshLocalSchemaAndExistingStoreUpgrade()throws Exception{
  String admin=System.getProperty("spoils.test.admin","");assumeTrue(!admin.isEmpty());
  if(!admin.equals("jdbc:postgresql://127.0.0.1:55441/postgres"))throw new IllegalArgumentException("Use disposable spoil cluster.");
  String db="spoil_baseline_"+UUID.randomUUID().toString().replace("-","");
  try(Connection root=DriverManager.getConnection(admin,"spoil_test","")){
   sql(root,"CREATE DATABASE "+db);
   try(Connection c=DriverManager.getConnection(admin.replace("/postgres","/"+db),"spoil_test","")){
    SchemaContractService.installLocalBaseline(c);
    assertTrue(SchemaContractService.validateLocal(c).ready());
    assertEquals(2,number(c,"SELECT count(*) FROM permissions WHERE permission_key IN ('RECORD_CUSTOM_ORDER_SPOILS','REVERSE_CUSTOM_ORDER_SPOILS')"));
    sql(c,"DROP TABLE custom_order_spoil_photos,custom_order_spoils");
    SchemaContractService.ensureCustomOrderSpoilsUpgrade(c);
    assertTrue(SchemaContractService.validateLocal(c).ready());
   }finally{sql(root,"DROP DATABASE "+db+" WITH (FORCE)");}
  }
 }
 private static void sql(Connection c,String text)throws Exception{try(var s=c.createStatement()){s.execute(text);}}
 private static int number(Connection c,String text)throws Exception{try(var s=c.createStatement();var r=s.executeQuery(text)){r.next();return r.getInt(1);}}
}
