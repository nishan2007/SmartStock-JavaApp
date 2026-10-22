package services;

import java.sql.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="smartstock.test.mergeDatabaseUrl",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/customer_merge_test")
class CustomerAccountMergeDatabaseTest {
    @Test void mergesReferencesAndProtectsHistoricalIds()throws Exception {
        try(var c=DriverManager.getConnection(System.getProperty("smartstock.test.mergeDatabaseUrl"),"postgres","")) {
            // Only the explicitly named disposable database is used.
            try(var s=c.createStatement()){s.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public; DROP SCHEMA IF EXISTS storefront CASCADE");}
            SchemaContractService.installLocalBaseline(c);
            StorefrontSchema.ensure(c);
            try(var s=c.createStatement()) {
                s.execute("DROP FUNCTION storefront.redirect_merged_customer() CASCADE; DELETE FROM storefront.schema_version WHERE version=16");
            }
            StorefrontSchema.ensure(c); // v15 upgrade must match the fresh v16 catalog.
            assertTrue(SchemaContractService.validateLocal(c).ready());
            c.setAutoCommit(false);
            try(var s=c.createStatement()) {
                s.execute("INSERT INTO users(user_id,username,full_name,role_id,is_active) VALUES(990,'merge-admin','Merge Admin',1,true)");
                s.execute("INSERT INTO locations(location_id,name) VALUES(990,'Merge fixture store')");
                s.execute("INSERT INTO customer_accounts(customer_id,name,phone,current_balance,is_active) VALUES(990,'Larex fixture','7058194',100,false)");
                // Simulate duplicates from before the ownership rule.
                s.execute("ALTER TABLE customer_accounts DISABLE TRIGGER customer_phone_ownership_guard");
                s.execute("INSERT INTO customer_accounts(customer_id,name,phone,current_balance,require_charge_authorization) VALUES(991,' larex   fixture ','+5927058194',50,true)");
                s.execute("INSERT INTO customer_accounts(customer_id,name,phone) VALUES(992,'Different name','+5927058194')");
                s.execute("ALTER TABLE customer_accounts ENABLE TRIGGER customer_phone_ownership_guard");
                s.execute("INSERT INTO customer_account_transactions(customer_id,amount,transaction_type) VALUES(990,100,'MANUAL_CHARGE'),(991,70,'MANUAL_CHARGE'),(991,20,'PAYMENT')");
                s.execute("UPDATE customer_account_transactions SET credit_applied_amount=20 WHERE customer_id=991 AND transaction_type='PAYMENT'");
                s.execute("INSERT INTO sales(sale_id,location_id,customer_id,total_amount,amount_paid) VALUES(990,990,991,43,43)");
                s.execute("INSERT INTO custom_orders(custom_order_id,order_number,customer_id,customer_name,customer_phone,total_amount,amount_paid,balance_due) VALUES(990,'MERGE-ORDER',991,'Larex fixture','7058194',120,20,100)");
                s.execute("INSERT INTO invoices(invoice_id,invoice_number,customer_id,customer_name,total_amount,balance_due) VALUES(990,'MERGE-INVOICE',991,'Larex fixture',30,30)");
                s.execute("INSERT INTO quotations(quotation_id,quotation_number,customer_id,customer_name) VALUES(990,'MERGE-QUOTE',991,'Larex fixture')");
                s.execute("INSERT INTO sync_cross_store_customer_history_cache(source_location_id,event_key,customer_id,event_type,store_name,amount) VALUES(77,'SALE:1',991,'SALE','Other store',25)");
                // A future FK is discovered without adding it to a Java allowlist.
                s.execute("CREATE TABLE merge_reference_fixture(id integer PRIMARY KEY,owner integer REFERENCES customer_accounts(customer_id),amount numeric)");
                s.execute("INSERT INTO merge_reference_fixture VALUES(1,991,123.45)");
                s.execute("SELECT install_customer_merge_reference_guards()");
                s.execute("INSERT INTO storefront.customer_links(auth_id,location_id,customer_uuid,email) SELECT gen_random_uuid(),1,sync_uuid,'fixture@example.test' FROM customer_accounts WHERE customer_id=991");
                var plan=CustomerAccountMergeService.preview(c,990);
                assertEquals(1,plan.groups().size());assertEquals(990,plan.groups().get(0).survivorId());
                assertEquals(java.util.List.of(991),plan.groups().get(0).duplicateIds());
                assertEquals(0,new BigDecimal("150").compareTo(plan.groups().get(0).combinedBalance()));
                var changed=c.setSavepoint();
                s.execute("UPDATE customer_accounts SET email='changed@example.test' WHERE customer_id=991");
                assertEquals("40001",assertThrows(SQLException.class,()->CustomerAccountMergeService.merge(c,990,plan.fingerprint())).getSQLState());
                c.rollback(changed);
                var result=CustomerAccountMergeService.merge(c,990,plan.fingerprint());
                assertEquals(1,result.accountsRetired());
                assertEquals(990,integer(s,"SELECT owner FROM merge_reference_fixture"));
                assertEquals(990,integer(s,"SELECT customer_id FROM sales WHERE sale_id=990"));
                assertEquals(990,integer(s,"SELECT customer_id FROM custom_orders WHERE custom_order_id=990"));
                assertEquals(990,integer(s,"SELECT customer_id FROM invoices WHERE invoice_id=990"));
                assertEquals(990,integer(s,"SELECT customer_id FROM quotations WHERE quotation_id=990"));
                assertEquals(43,integer(s,"SELECT amount_paid FROM sales WHERE sale_id=990"));
                assertEquals(100,integer(s,"SELECT balance_due FROM custom_orders WHERE custom_order_id=990"));
                assertEquals(30,integer(s,"SELECT balance_due FROM invoices WHERE invoice_id=990"));
                try(var r=s.executeQuery("SELECT amount FROM merge_reference_fixture WHERE id=1")) {
                    r.next();assertEquals(0,new BigDecimal("123.45").compareTo(r.getBigDecimal(1)));
                }
                assertEquals(3,integer(s,"SELECT count(*) FROM customer_account_transactions WHERE customer_id=990"));
                assertEquals(0,integer(s,"SELECT count(*) FROM customer_account_transactions WHERE customer_id=991"));
                assertEquals(990,integer(s,"SELECT customer_id FROM sync_cross_store_customer_history_cache WHERE event_key='SALE:1'"));
                assertEquals(0,integer(s,"SELECT count(*) FROM customer_accounts WHERE customer_id=991 AND is_active"));
                assertEquals(150,integer(s,"SELECT current_balance FROM customer_accounts WHERE customer_id=990"));
                assertEquals(1,integer(s,"SELECT count(*) FROM customer_accounts WHERE customer_id=990 AND is_active"));
                assertEquals(1,integer(s,"SELECT count(*) FROM customer_accounts WHERE customer_id=990 AND require_charge_authorization"));
                assertEquals(1,integer(s,"SELECT count(*) FROM storefront.customer_links l JOIN customer_accounts a ON l.customer_uuid=a.sync_uuid WHERE a.customer_id=990"));
                // Stale inserts and updates resolve to the survivor, including other-store caches.
                s.execute("INSERT INTO merge_reference_fixture VALUES(2,991,7)");
                s.execute("UPDATE sync_cross_store_customer_history_cache SET customer_id=991 WHERE event_key='SALE:1'");
                assertEquals(990,integer(s,"SELECT owner FROM merge_reference_fixture WHERE id=2"));
                assertEquals(990,integer(s,"SELECT customer_id FROM sync_cross_store_customer_history_cache WHERE event_key='SALE:1'"));
                s.execute("UPDATE customer_accounts SET is_active=true,current_balance=999 WHERE customer_id=991");
                assertEquals(0,integer(s,"SELECT current_balance FROM customer_accounts WHERE customer_id=991"));
                assertEquals(1,integer(s,"SELECT count(*) FROM customer_accounts WHERE customer_id=991 AND phone IS NULL"));
                assertEquals(0,CustomerAccountMergeService.preview(c,990).groups().size());
                assertEquals(0,CustomerAccountMergeService.merge(c,990,CustomerAccountMergeService.preview(c,990).fingerprint()).accountsRetired());
                var beforeDeletion=c.setSavepoint();
                assertThrows(SQLException.class,()->s.execute("DELETE FROM customer_accounts WHERE customer_id=991"));
                c.rollback(beforeDeletion);
                beforeDeletion=c.setSavepoint();
                assertThrows(SQLException.class,()->s.execute("DELETE FROM customer_account_merges WHERE source_customer_id=991"));
                c.rollback(beforeDeletion);
                s.execute("INSERT INTO customer_accounts(customer_id,name) VALUES(999,'Unmerged deletion fixture')");
                assertEquals(1,s.executeUpdate("DELETE FROM customer_accounts WHERE customer_id=999"));
                try(var r=s.executeQuery("SELECT to_jsonb(m)::text FROM customer_account_merges m WHERE source_customer_id=991")) {
                    r.next();CustomerAccountMergeService.applyMapping(c,com.google.gson.JsonParser.parseString(r.getString(1)).getAsJsonObject());
                }
                assertEquals(150,integer(s,"SELECT current_balance FROM customer_accounts WHERE customer_id=990"));
                assertEquals("42501",assertThrows(SQLException.class,()->CustomerAccountMergeService.preview(c,-1)).getSQLState());
                // A batch with two groups must roll back even when a later group conflicts.
                s.execute("INSERT INTO customer_accounts(customer_id,name,phone) VALUES(1000,'A fixture','6500991'),(1010,'Z fixture','6750302')");
                s.execute("ALTER TABLE customer_accounts DISABLE TRIGGER customer_phone_ownership_guard");
                s.execute("INSERT INTO customer_accounts(customer_id,name,phone) VALUES(1001,'A fixture','650-0991'),(1011,'Z fixture','675-0302')");
                s.execute("ALTER TABLE customer_accounts ENABLE TRIGGER customer_phone_ownership_guard");
                s.execute("INSERT INTO storefront.customer_links(auth_id,location_id,customer_uuid,email) SELECT gen_random_uuid(),1,sync_uuid,'conflict@example.test' FROM customer_accounts WHERE customer_id IN(1010,1011)");
                var conflictPlan=CustomerAccountMergeService.preview(c,990);
                var beforeConflict=c.setSavepoint();
                assertEquals("23505",assertThrows(SQLException.class,()->CustomerAccountMergeService.merge(c,990,conflictPlan.fingerprint())).getSQLState());
                c.rollback(beforeConflict);
                assertEquals(0,integer(s,"SELECT count(*) FROM customer_account_merges WHERE source_customer_id IN(1001,1011)"));
                assertEquals(2,integer(s,"SELECT count(*) FROM customer_accounts WHERE customer_id IN(1001,1011) AND is_active"));
                // Balance inconsistencies block consolidation without losing ledger information.
                s.execute("UPDATE customer_accounts SET current_balance=7 WHERE customer_id=1001");
                var badBalance=CustomerAccountMergeService.preview(c,990);
                var beforeBalance=c.setSavepoint();
                assertEquals("55000",assertThrows(SQLException.class,()->CustomerAccountMergeService.merge(c,990,badBalance.fingerprint())).getSQLState());
                c.rollback(beforeBalance);
            }finally{c.rollback();}
        }
    }
    private static int integer(Statement s,String query)throws SQLException {try(var r=s.executeQuery(query)){r.next();return r.getInt(1);}}
}
