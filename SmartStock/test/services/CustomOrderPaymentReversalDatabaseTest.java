package services;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Requires an explicitly selected disposable cluster; never connects to store databases. */
class CustomOrderPaymentReversalDatabaseTest {
    private static final String DEVICE="00000000-0000-0000-0000-000000000990";

    @Test void refundsPreserveOrdersAndRejectDuplicateOrInvalidPayments() throws Exception {
        String admin=System.getProperty("reversal.test.admin","");
        assumeTrue(!admin.isBlank());
        assertEquals("jdbc:postgresql://127.0.0.1:55447/postgres",admin);
        String db="reversal_test_"+UUID.randomUUID().toString().replace("-","");
        try(Connection root=DriverManager.getConnection(admin,"pricing_test","")) {
            sql(root,"CREATE DATABASE "+db);
            try(Connection c=DriverManager.getConnection(admin.replace("/postgres","/"+db),"pricing_test","")) {
                SchemaContractService.installLocalBaseline(c);
                assertTrue(SchemaContractService.validateLocal(c).ready());
                SchemaContractService.requireLocalReady(c);
                assertEquals(1,scalar(c,"SELECT count(*) FROM role_permissions rp JOIN roles r USING(role_id) JOIN permissions p USING(permission_id) WHERE p.permission_key='REVERSE_CUSTOM_ORDER_PAYMENTS' AND upper(r.role_name)='ADMIN'"));
                assertEquals(0,scalar(c,"SELECT count(*) FROM role_permissions rp JOIN roles r USING(role_id) JOIN permissions p USING(permission_id) WHERE p.permission_key='REVERSE_CUSTOM_ORDER_PAYMENTS' AND upper(r.role_name)<>'ADMIN'"));
                sql(c,"INSERT INTO locations(location_id,name) VALUES(990,'Reversal test'); INSERT INTO users(user_id,username,full_name,role_id) VALUES(990,'reversal-test','Test Admin',1); INSERT INTO customer_accounts(customer_id,name,phone) VALUES(990,'Test Customer','6000000'); INSERT INTO company_customization(location_id) VALUES(990)");
                sql(c,"INSERT INTO roles(role_id,role_name) VALUES(991,'Future refunds role'); INSERT INTO users(user_id,username,full_name,role_id) VALUES(991,'refunds-role-test','Future User',991)");
                var guard=LanApiServer.class.getDeclaredMethod("requireAnyPermission",Connection.class,int.class,String[].class);
                guard.setAccessible(true);
                var ctor=LanApiServer.class.getDeclaredConstructors()[0];
                ctor.setAccessible(true);
                Object server=ctor.newInstance(new Object[ctor.getParameterCount()]);
                assertThrows(java.lang.reflect.InvocationTargetException.class,()->guard.invoke(server,c,991,new String[]{"REVERSE_CUSTOM_ORDER_PAYMENTS"}));
                guard.invoke(server,c,990,new String[]{"REVERSE_CUSTOM_ORDER_PAYMENTS"});
                sql(c,"INSERT INTO role_permissions(role_id,permission_id) SELECT 991,permission_id FROM permissions WHERE permission_key='REVERSE_CUSTOM_ORDER_PAYMENTS'");
                guard.invoke(server,c,991,new String[]{"REVERSE_CUSTOM_ORDER_PAYMENTS"});
                sql(c,"INSERT INTO custom_orders(custom_order_id,order_number,customer_id,customer_name,customer_phone,location_id,total_amount,amount_paid,balance_due,payment_status) VALUES(990,'REVERSAL-TEST',990,'Test Customer','6000000',990,1000,600,400,'PARTIAL')");
                long payment=payment(c,990,200,"CARD","card-original");
                long other=payment(c,990,400,"CARD","card-other");
                c.setAutoCommit(false);
                reverse(c,payment,990,"refund-1");
                assertEquals(1000,scalar(c,"SELECT total_amount FROM custom_orders WHERE custom_order_id=990"));
                assertEquals(400,scalar(c,"SELECT amount_paid FROM custom_orders WHERE custom_order_id=990"));
                assertEquals(600,scalar(c,"SELECT balance_due FROM custom_orders WHERE custom_order_id=990"));
                assertEquals(1,scalar(c,"SELECT count(*) FROM custom_order_payments WHERE custom_order_id=990 AND payment_action='REFUND' AND payment_amount=200 AND payment_method='CARD'"));
                assertEquals(2,scalar(c,"SELECT count(*) FROM custom_order_payments WHERE custom_order_id=990 AND payment_action='PAYMENT' AND voided_at IS NULL"));
                var details=CustomOrderDetailsService.read(c,990,990);
                assertTrue(details.get("payments").toString().contains("reversed=true"));
                assertEquals(0,CustomerAccountLedgerService.calculateCustomerBalance(c,990).signum());
                c.commit();
                assertThrows(SQLException.class,()->reverse(c,payment,990,"refund-duplicate"));
                c.rollback();
                assertThrows(SQLException.class,()->reverse(c,other,991,"wrong-store"));
                c.rollback();
                assertThrows(SQLException.class,()->reverse(c,other,990,""));
                c.rollback();
                reverse(c,other,990,"refund-2");
                assertEquals("UNPAID",text(c,"SELECT payment_status FROM custom_orders WHERE custom_order_id=990"));
                c.rollback();
                assertEquals(400,scalar(c,"SELECT amount_paid FROM custom_orders WHERE custom_order_id=990"));
                sql(c,"UPDATE custom_orders SET invoice_billed=TRUE WHERE custom_order_id=990");
                assertThrows(SQLException.class,()->reverse(c,other,990,"invoice-billed"));
                c.rollback();
                sql(c,"UPDATE custom_order_payments SET payment_method='CASH' WHERE custom_order_payment_id="+other);
                assertThrows(SQLException.class,()->reverse(c,other,990,"cash-without-drawer"));
                c.rollback();
                sql(c,"UPDATE custom_order_payments SET payment_method='ACCOUNT' WHERE custom_order_payment_id="+other);
                assertThrows(SQLException.class,()->reverse(c,other,990,"account"));
                c.rollback();
                sql(c,"UPDATE custom_orders SET pricing_state='PENDING',payment_status='PRICE_PENDING',total_amount=0,balance_due=0 WHERE custom_order_id=990");
                reverse(c,other,990,"pending-refund");
                assertEquals(0,scalar(c,"SELECT balance_due FROM custom_orders WHERE custom_order_id=990"));
                assertEquals("PRICE_PENDING",text(c,"SELECT payment_status FROM custom_orders WHERE custom_order_id=990"));
                assertEquals(0,CustomerAccountLedgerService.calculateCustomerBalance(c,990).signum());
                c.rollback();
            } finally {sql(root,"DROP DATABASE "+db+" WITH (FORCE)");}
        }
    }

    private static void reverse(Connection c,long payment,int location,String reference)throws SQLException {
        LanCustomOrderWorkflowService.reversePayment(c,990,payment,reference,"Payment entered incorrectly",location,990,"Test Admin",DEVICE,"Test POS");
    }
    private static long payment(Connection c,long order,int amount,String method,String reference)throws SQLException {
        try(var p=c.prepareStatement("INSERT INTO custom_order_payments(custom_order_id,payment_amount,payment_method,payment_reference,payment_action) VALUES(?,?,?,?,'PAYMENT') RETURNING custom_order_payment_id")) {
            p.setLong(1,order);p.setInt(2,amount);p.setString(3,method);p.setString(4,reference);
            try(var r=p.executeQuery()){r.next();return r.getLong(1);}
        }
    }
    private static void sql(Connection c,String sql)throws SQLException {try(var p=c.createStatement()){p.execute(sql);}}
    private static int scalar(Connection c,String sql)throws SQLException {try(var p=c.createStatement();var r=p.executeQuery(sql)){assertTrue(r.next());return r.getInt(1);}}
    private static String text(Connection c,String sql)throws SQLException {try(var p=c.createStatement();var r=p.executeQuery(sql)){assertTrue(r.next());return r.getString(1);}}
}
