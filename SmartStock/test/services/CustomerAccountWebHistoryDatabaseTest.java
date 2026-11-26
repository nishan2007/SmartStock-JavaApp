package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CustomerAccountWebHistoryDatabaseTest {
    @Test void opensOwnedSalesAndOrdersAndRecordsOneAdjustment() throws Exception {
        String url=System.getProperty("smartstock.test.customerWebHistoryJdbc","");
        assumeTrue(url.equals("jdbc:postgresql://127.0.0.1:55467/postgres"));
        String database="customer_web_history_"+UUID.randomUUID().toString().replace("-","");
        try(var root=DriverManager.getConnection(url,"postgres","");var schema=root.createStatement()) {
            schema.execute("CREATE DATABASE "+database);
            try(var c=DriverManager.getConnection(url.replace("/postgres","/"+database),"postgres","")) {
                verify(c);
            } finally {schema.execute("DROP DATABASE "+database+" WITH (FORCE)");}
        }
    }

    private static void verify(Connection c) throws Exception {
            SchemaContractService.installLocalBaseline(c);
            c.setAutoCommit(false);
            try(var s=c.createStatement()) {
                s.execute("INSERT INTO locations(location_id,name) VALUES(901,'Main Store'),(902,'Other Store')");
                int role=id(s,"INSERT INTO roles(role_name) VALUES('History fixture') RETURNING role_id");
                s.execute("INSERT INTO role_permissions(role_id,permission_id) SELECT "+role+
                        ",permission_id FROM permissions WHERE permission_key='CUSTOMER_ACCOUNTS'");
                int user=id(s,"INSERT INTO users(username,password_hash,full_name,role_id) VALUES ('history-fixture','unused','Fixture Employee',"+
                        role+") RETURNING user_id");
                int customer=id(s,"INSERT INTO customer_accounts(name,credit_limit,is_active) VALUES('Fixture Account',600000,true) RETURNING customer_id");
                int otherCustomer=id(s,"INSERT INTO customer_accounts(name,is_active) VALUES('Other Account',true) RETURNING customer_id");
                int sale=id(s,"INSERT INTO sales(location_id,customer_id,receipt_number,total_amount,subtotal_amount,payment_method,payment_status) VALUES(901,"+
                        customer+",'S-10482',18500,18500,'ACCOUNT','UNPAID') RETURNING sale_id");

                int product=id(s,"INSERT INTO products(name,sku) VALUES('Polo shirt','WEB-FIXTURE-P') RETURNING product_id");
                s.execute("INSERT INTO sale_items(sale_id,product_id,item_name,quantity,unit_price) VALUES("+sale+","+product+",'Polo shirt',2,7500)");
                int order=id(s,"INSERT INTO custom_orders(location_id,customer_id,customer_name,customer_phone,order_number,status,"+
                        "total_amount,amount_paid,balance_due) VALUES(901,"+customer+",'Fixture Account','','O-2086','DELIVERED',36000,36000,0) RETURNING custom_order_id");
                s.execute("INSERT INTO custom_order_lines(custom_order_id,item_name,pricing_type,customization_details,unit_price,line_total) VALUES("+order+",'Embroidery','FIXED','',6000,6000)");
                s.execute("INSERT INTO customer_account_transactions(customer_id,location_id,amount,transaction_type,created_at,note) SELECT "+
                        customer+",901,1,'MANUAL_CHARGE',now()-make_interval(days=>n),'Old fixture '||n FROM generate_series(1,12)n");
                assertEquals(10,((List<?>)CustomerAccountWebHistory.list(c,customer,user,901,"",0).get("transactions")).size());
                assertEquals(1,((List<?>)CustomerAccountWebHistory.list(c,customer,user,901,"Old fixture 12",0).get("transactions")).size());
                assertEquals(1,((List<?>)CustomerAccountWebHistory.list(c,customer,user,901,"polo shirt",0).get("transactions")).size());
                assertEquals(1,((List<?>)CustomerAccountWebHistory.list(c,customer,user,901,"embroidery",0).get("transactions")).size());
                var receipt=(Map<?,?>)CustomerAccountWebHistory.detail(c,customer,user,901,901,"SALE:"+sale,Set.of()).get("sale");
                assertEquals(1,((List<?>)receipt.get("items")).size());
                var details=(Map<?,?>)CustomerAccountWebHistory.detail(c,customer,user,901,901,"CUSTOM_ORDER:"+order,Set.of()).get("order");
                assertEquals(1,((List<?>)details.get("lines")).size());
                assertEquals(false,details.get("canOpenFiles"));
                s.execute("INSERT INTO sync_cross_store_customer_history_cache(source_location_id,event_key,customer_id,event_type,"+
                        "source_id,document_number,source_created_at,store_name) VALUES(902,'CUSTOM_ORDER:2086',"+customer+
                        ",'CUSTOM_ORDER',2086,'REMOTE-2086',now(),'Other Store')");
                s.execute("INSERT INTO sync_cross_store_table_state VALUES(902,'custom_order_lines',repeat('a',64),1)");
                s.execute("INSERT INTO sync_cross_store_source_rows VALUES"+
                        "(902,'custom_orders','{\"custom_order_id\":2086}',repeat('a',64),jsonb_build_object('custom_order_id',2086,"+
                        "'customer_id',"+customer+",'location_id',902,'order_number','REMOTE-2086','status','DELIVERED')),"+
                        "(902,'custom_order_lines','{\"custom_order_line_id\":3846}',repeat('a',64),"+
                        "'{\"custom_order_id\":2086,\"custom_order_line_id\":3846,\"item_name\":\"Remote jersey\"}')");
                var cached=(Map<?,?>)CustomerAccountWebHistory.detail(c,customer,user,901,902,"REMOTE:902:CUSTOM_ORDER:2086",Set.of()).get("order");
                assertEquals(1,((List<?>)cached.get("lines")).size());
                assertEquals(1,((List<?>)CustomerAccountWebHistory.list(c,customer,user,901,"remote jersey",0).get("transactions")).size());
                assertThrows(LanCustomerAccountService.RuleViolation.class,()->
                        CustomerAccountWebHistory.detail(c,otherCustomer,user,901,901,"CUSTOM_ORDER:"+order,Set.of()));
                assertThrows(LanCustomerAccountService.RuleViolation.class,()->
                        CustomerAccountWebHistory.detail(c,customer,user,901,902,"SALE:"+sale,Set.of()));
                assertThrows(LanCustomerAccountService.RuleViolation.class,()->
                        CustomerAccountWebHistory.list(c,customer,-1,901,"",0));

                s.execute("INSERT INTO sync_cross_store_customer_history_status(source_location_id,store_name,status) VALUES(902,'Other Store','CURRENT')");
                JsonObject adjustment=new JsonObject();
                adjustment.addProperty("customerId",customer);
                adjustment.addProperty("action","CHARGE");
                adjustment.addProperty("amount",100);
                adjustment.addProperty("description","Browser fixture charge");
                UUID browser=UUID.randomUUID(),key=UUID.randomUUID();
                Object first=CustomerAccountWebAdjustment.record(c,user,"Fixture Employee",browser,901,adjustment,key);
                Object retry=CustomerAccountWebAdjustment.record(c,user,"Fixture Employee",browser,901,adjustment,key);
                assertEquals(LanJson.create().toJsonTree(first),retry);
                try(var r=s.executeQuery("SELECT count(*) FROM customer_account_transactions WHERE note='Browser fixture charge'")){
                    assertTrue(r.next());assertEquals(1,r.getInt(1));
                }
                assertThrows(SecurityException.class,()->CustomerAccountWebHistory.orderFile(c,customer,901,
                        "CUSTOM_ORDER:"+order,UUID.randomUUID(),0,Set.of("CUSTOMER_ACCOUNTS")));
            } finally {c.rollback();}
    }

    private static int id(Statement s,String sql)throws SQLException {
        try(var r=s.executeQuery(sql)){r.next();return r.getInt(1);}
    }
}
