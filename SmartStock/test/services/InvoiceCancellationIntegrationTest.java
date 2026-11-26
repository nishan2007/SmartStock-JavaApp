package services;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class InvoiceCancellationIntegrationTest {
    private Connection fixture() throws Exception {
        String url = System.getProperty("smartstock.test.jdbc", "");
        assumeTrue(!url.isBlank());
        Connection c = DriverManager.getConnection(url, System.getProperty("smartstock.test.dbUser", ""),
                System.getProperty("smartstock.test.dbPassword", ""));
        c.setAutoCommit(false);
        ServerRequestIdentity.bind(1, 1, "Test", "Admin", "test-device", "Test register");
        try (var s = c.createStatement()) {
            s.execute("""
                    CREATE TEMP TABLE invoices(invoice_id bigint PRIMARY KEY,invoice_number text,customer_id int,status text,
                     total_amount numeric,amount_paid numeric,balance_due numeric,location_id int,linked_custom_order_id bigint,
                     payment_status text,delivered_at timestamptz);
                    CREATE TEMP TABLE invoice_lines(invoice_line_id bigint PRIMARY KEY,invoice_id bigint,product_id int,item_name text,
                     quantity_invoiced int,quantity_delivered int,delivery_status text);
                    CREATE TEMP TABLE invoice_delivery_events(invoice_delivery_event_id bigint,invoice_id bigint);
                    CREATE TEMP TABLE invoice_delivery_lines(invoice_delivery_event_id bigint,invoice_line_id bigint,quantity_delivered int);
                    CREATE TEMP TABLE inventory(product_id int,location_id int,quantity_on_hand int,updated_at timestamptz);
                    CREATE TEMP TABLE inventory_movements(product_id int,location_id int,change_qty int,reason text,note text,user_name text,
                     invoice_id bigint,invoice_line_id bigint,invoice_delivery_event_id bigint,device_id text,device_name text,user_id int);
                    CREATE TEMP TABLE customer_accounts(customer_id int PRIMARY KEY,current_balance numeric,updated_at timestamptz);
                    CREATE TEMP TABLE customer_account_transactions(transaction_id bigserial PRIMARY KEY,customer_id int,invoice_id bigint,
                     payment_id text,location_id int,amount numeric,transaction_type text,note text,user_name text,device_id text,device_name text,
                     payment_method text,payment_reference text,cash_drawer_id bigint,cash_drawer_name text,cash_drawer_session_id bigint,
                     credit_applied_amount numeric DEFAULT 0);
                    CREATE TEMP TABLE customer_account_payment_allocations(payment_transaction_id bigint,invoice_id bigint,amount numeric);
                    CREATE TEMP TABLE invoice_payments(invoice_id bigint,customer_id int,payment_amount numeric,payment_method text,
                     payment_reference text,payment_action text,taken_by_user_id int,taken_by_name text,location_id int,device_id text,device_name text,
                     cash_drawer_id bigint,cash_drawer_name text,cash_drawer_session_id bigint);
                    CREATE TEMP TABLE invoice_audit_log(invoice_id bigint,action_type text,field_name text,old_value text,new_value text,
                     reason text,user_id int,user_name text,device_id text,device_name text);
                    CREATE TEMP TABLE invoice_status_history(invoice_id bigint,old_status text,new_status text,reason text,
                     user_id int,user_name text,device_id text,device_name text);
                    CREATE TEMP TABLE sync_outbox(event_type text,location_id int,device_id text,user_id int,payload jsonb,origin_location_id int,origin_device_id text);
                    CREATE TEMP TABLE custom_orders(custom_order_id bigint,status text,balance_due numeric,invoice_billed boolean,updated_at timestamptz);
                    CREATE TEMP TABLE custom_order_lines(custom_order_id bigint,delivery_status text);
                    INSERT INTO invoices VALUES(1,'INV-1',10,'OPEN',100,40,60,1,NULL,'PARTIAL',NULL);
                    INSERT INTO customer_accounts VALUES(10,60,NULL);
                    INSERT INTO customer_account_transactions(customer_id,invoice_id,amount,transaction_type,credit_applied_amount,payment_method)
                     VALUES(10,1,100,'INVOICE_CREDIT',0,'ACCOUNT'),(10,1,-40,'PAYMENT',40,'CARD');
                    INSERT INTO customer_account_payment_allocations VALUES(2,1,40);
                    INSERT INTO invoice_payments(invoice_id,payment_amount,payment_action,payment_method) VALUES(1,40,'PAYMENT','CARD');
                    """);
        }
        return c;
    }

    private int scalar(Connection c, String sql) throws Exception {
        try (var s = c.createStatement(); var r = s.executeQuery(sql)) { r.next(); return r.getBigDecimal(1).intValueExact(); }
    }

    @Test void paymentReversalAndInvoiceCancellationBalanceTheLedgerAndCannotRepeat() throws Exception {
        try (var c = fixture()) {
            assertThrows(SQLException.class, () -> InvoiceCancellationService.cancelInvoice(c, 1, "Wrong invoice"));
            InvoiceCancellationService.cancelPayment(c, 2, "Wrong payment", "CARD-VOID-1");
            assertEquals(0, scalar(c, "SELECT amount_paid FROM invoices"));
            assertEquals(100, scalar(c, "SELECT balance_due FROM invoices"));
            assertEquals(100, scalar(c, "SELECT current_balance FROM customer_accounts"));
            assertEquals(0, scalar(c, "SELECT SUM(CASE WHEN payment_action='REVERSAL' THEN -payment_amount ELSE payment_amount END) FROM invoice_payments"));
            assertThrows(SQLException.class, () -> InvoiceCancellationService.cancelPayment(c, 2, "Repeat", "VOID-2"));
            InvoiceCancellationService.cancelInvoice(c, 1, "Wrong invoice");
            assertEquals(0, scalar(c, "SELECT balance_due FROM invoices"));
            assertEquals(0, scalar(c, "SELECT current_balance FROM customer_accounts"));
            assertEquals(0, scalar(c, "SELECT SUM(amount) FROM customer_account_transactions"));
            assertThrows(SQLException.class, () -> InvoiceCancellationService.cancelInvoice(c, 1, "Repeat"));
            c.rollback();
        } finally { ServerRequestIdentity.clear(); }
    }

    @Test void deliveryCancellationRestoresStockAndOnlyItsDeliveredQuantities() throws Exception {
        try (var c = fixture()) {
            try (var s = c.createStatement()) {
                s.execute("INSERT INTO invoice_lines VALUES(11,1,20,'Item',5,5,'DELIVERED'); INSERT INTO invoice_delivery_events VALUES(100,1),(101,1); INSERT INTO invoice_delivery_lines VALUES(100,11,2),(101,11,3); INSERT INTO inventory VALUES(20,1,10,NULL); UPDATE invoices SET status='DELIVERED',delivered_at=now()");
            }
            assertThrows(SQLException.class, () -> InvoiceCancellationService.cancelInvoice(c, 1, "Cancel"));
            InvoiceCancellationService.cancelDelivery(c, 100, "Returned two");
            assertEquals(12, scalar(c, "SELECT quantity_on_hand FROM inventory"));
            assertEquals(3, scalar(c, "SELECT quantity_delivered FROM invoice_lines"));
            assertEquals(1, scalar(c, "SELECT COUNT(*) FROM invoices WHERE status='PARTIALLY_DELIVERED' AND delivered_at IS NULL"));
            assertThrows(SQLException.class, () -> InvoiceCancellationService.cancelDelivery(c, 100, "Repeat"));
            InvoiceCancellationService.cancelDelivery(c, 101, "Returned three");
            assertEquals(15, scalar(c, "SELECT quantity_on_hand FROM inventory"));
            assertEquals(0, scalar(c, "SELECT quantity_delivered FROM invoice_lines"));
            assertEquals(5, scalar(c, "SELECT SUM(change_qty) FROM inventory_movements"));
            c.rollback();
        } finally { ServerRequestIdentity.clear(); }
    }

    @Test void wrongLocationMissingReasonAndSharedAllocationsCannotReverseMoney() throws Exception {
        try (var c = fixture()) {
            assertThrows(SQLException.class, () -> InvoiceCancellationService.cancelPayment(c, 2, "", "REF"));
            ServerRequestIdentity.bind(1, 2, "Other", "Admin", "device", "Other register");
            assertThrows(SQLException.class, () -> InvoiceCancellationService.cancelPayment(c, 2, "Wrong", "REF"));
            ServerRequestIdentity.bind(1, 1, "Test", "Admin", "device", "Register");
            try (var s = c.createStatement()) { s.execute("UPDATE customer_account_payment_allocations SET invoice_id=2"); }
            assertThrows(SQLException.class, () -> InvoiceCancellationService.cancelPayment(c, 2, "Wrong", "REF"));
            assertEquals(40, scalar(c, "SELECT amount_paid FROM invoices"));
            assertEquals(60, scalar(c, "SELECT current_balance FROM customer_accounts"));
            c.rollback();
        } finally { ServerRequestIdentity.clear(); }
    }

    @Test void linkedProductionStopsWithItsInvoiceButDeliveredProductionCannotBeCancelled() throws Exception {
        try (var c = fixture()) {
            try (var s = c.createStatement()) {
                s.execute("UPDATE invoices SET amount_paid=0,balance_due=100,linked_custom_order_id=77; INSERT INTO custom_orders VALUES(77,'NEW',0,TRUE,NULL); INSERT INTO custom_order_lines VALUES(77,'DELIVERED')");
            }
            assertThrows(SQLException.class, () -> InvoiceCancellationService.cancelInvoice(c, 1, "Cancel"));
            try (var s = c.createStatement()) { s.execute("UPDATE custom_order_lines SET delivery_status='PENDING'"); }
            InvoiceCancellationService.cancelInvoice(c, 1, "Customer cancelled");
            assertEquals(1, scalar(c, "SELECT COUNT(*) FROM custom_orders WHERE status='CANCELLED'"));
            c.rollback();
        } finally { ServerRequestIdentity.clear(); }
    }

    @Test void permissionsAreIndependentAndOnlyAdminReceivesNewDefaults() throws Exception {
        assertEquals("CANCEL_QUOTATIONS", InvoiceCancellationService.permissionForAction("CANCEL"));
        assertEquals("CANCEL_INVOICES", InvoiceCancellationService.permissionForAction("CANCEL_INVOICE"));
        assertEquals("CANCEL_INVOICE_DELIVERIES", InvoiceCancellationService.permissionForAction("CANCEL_DELIVERY"));
        assertEquals("CANCEL_INVOICE_PAYMENTS", InvoiceCancellationService.permissionForAction("CANCEL_PAYMENT"));
        try (var c = fixture()) {
            try (var s = c.createStatement()) {
                s.execute("CREATE TEMP TABLE permissions(permission_id serial PRIMARY KEY,permission_key text UNIQUE,permission_name text,description text,permission_group text,permission_subgroup text); CREATE TEMP TABLE roles(role_id int,role_name text); CREATE TEMP TABLE role_permissions(role_id int,permission_id int,updated_at timestamptz,PRIMARY KEY(role_id,permission_id)); INSERT INTO roles VALUES(1,'ADMIN'),(2,'Cashier'),(3,'Manager')");
            }
            String resource = "database/migrations/v1_after/20261125140000_document_cancellation_permissions.sql";
            assertTrue(SchemaContractService.localContractResources().contains(resource));
            assertTrue(SchemaContractService.cloudPostV1MigrationResources().contains(resource));
            String sql = SqlScriptRunner.readResource(resource).replace("public.", "");
            SqlScriptRunner.runSql(c, sql);
            SqlScriptRunner.runSql(c, sql);
            assertEquals(4, scalar(c, "SELECT COUNT(*) FROM permissions"));
            assertEquals(4, scalar(c, "SELECT COUNT(*) FROM role_permissions WHERE role_id=1"));
            assertEquals(0, scalar(c, "SELECT COUNT(*) FROM role_permissions WHERE role_id<>1"));
            String api = java.nio.file.Files.readString(java.nio.file.Path.of("src/services/LanApiServer.java"));
            String route = api.substring(api.indexOf("private ApiResult quotationMutation"), api.indexOf("private static ApiException quotationMutationFailure"));
            assertTrue(route.contains("requireAnyPermission(c,s.userId(),cancellationPermission)"));
            assertTrue(route.indexOf("requireAnyPermission(c,s.userId(),cancellationPermission)") < route.indexOf("loadIdempotentResult"));
            c.rollback();
        } finally { ServerRequestIdentity.clear(); }
    }
}
