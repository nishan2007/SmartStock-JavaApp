package services;

import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Only an explicitly supplied, disposable empty database may be used. */
@EnabledIfSystemProperty(named="smartstock.test.phoneDatabaseUrl", matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/phone_ownership_test")
class CustomerPhoneOwnershipDatabaseTest {
    @Test void preservesCurrentDuplicatesAndReleasesRemovedNumbers() throws Exception {
        try(var c=DriverManager.getConnection(System.getProperty("smartstock.test.phoneDatabaseUrl"),"postgres","")) {
            c.setAutoCommit(false);
            try(var s=c.createStatement()) {
                s.execute("CREATE TABLE public.customer_accounts(customer_id serial PRIMARY KEY,name text,phone text,is_active boolean DEFAULT true,account_number text,email text,require_charge_authorization boolean DEFAULT false,custom_order_discount_enabled boolean DEFAULT false,custom_order_discount_percent numeric DEFAULT 0,custom_order_deposit_exempt boolean DEFAULT false)");
                s.execute("INSERT INTO customer_accounts(name,phone) VALUES('Larex fixture','705-8194'),('Larex fixture','+5927058194')");
                String migration=SqlScriptRunner.readResource("database/migrations/v1_after/20261019120000_customer_phone_ownership.sql");
                SqlScriptRunner.runSql(c,migration);
                SqlScriptRunner.runSql(c,migration);
                s.execute("CREATE FUNCTION public.canonical_customer_id(integer) RETURNS integer LANGUAGE sql IMMUTABLE AS 'SELECT $1'");
                // Simulate a number left reserved by the previous application version.
                s.execute("INSERT INTO customer_phone_ownership VALUES('+5926999000',1)");
                String release=SqlScriptRunner.readResource("database/migrations/v1_after/20261024120000_release_removed_customer_phones.sql");
                SqlScriptRunner.runSql(c,release);
                SqlScriptRunner.runSql(c,release);
                SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/migrations/v1_after/20261119120000_customer_additional_phones.sql"));
                s.execute("INSERT INTO customer_accounts(name,phone,additional_phone_1,additional_phone_2) VALUES('Three contacts','6600101','6600102','6600103')");
                var extraPoint=c.setSavepoint();
                assertEquals("23505",assertThrows(SQLException.class,()->s.execute("INSERT INTO customer_accounts(name,phone) VALUES('Conflicting main','6600102')")).getSQLState());
                c.rollback(extraPoint);
                assertEquals("23505",assertThrows(SQLException.class,()->s.execute("INSERT INTO customer_accounts(name,additional_phone_2) VALUES('Conflicting extra','6600103')")).getSQLState());
                c.rollback(extraPoint);
                assertEquals("23514",assertThrows(SQLException.class,()->s.execute("INSERT INTO customer_accounts(name,phone,additional_phone_1) VALUES('Repeated','6600201','+5926600201')")).getSQLState());
                c.rollback(extraPoint);
                s.execute("UPDATE customer_accounts SET phone='6600102',additional_phone_1='6600101',additional_phone_2=NULL WHERE name='Three contacts'");
                CustomerPhoneOwnership.requireAvailable(c,"6600103",null);
                s.execute("DELETE FROM customer_accounts WHERE name='Three contacts'");
                CustomerPhoneOwnership.requireAvailable(c,"6600101",null);
                CustomerPhoneOwnership.requireAvailable(c,"6999000",null);
                assertFalse(OverseasOrderService.customerPhone(c,"6999000").get("reserved").getAsBoolean());
                for(String phone:new String[]{"7058194","5927058194","+592 (705) 8194","005927058194"}) {
                    var point=c.setSavepoint();
                    try(var p=c.prepareStatement("INSERT INTO customer_accounts(name,phone) VALUES('Different name',?)")) {
                        p.setString(1,phone);
                        assertEquals("23505",assertThrows(SQLException.class,p::executeUpdate).getSQLState());
                    }finally{c.rollback(point);}
                }
                s.execute("UPDATE customer_accounts SET name='Legacy edited',phone='5927058194' WHERE customer_id=2");
                s.execute("UPDATE customer_accounts SET is_active=false,phone='+5926500991' WHERE customer_id=1");
                var point=c.setSavepoint();
                assertThrows(SQLException.class,()->CustomerPhoneOwnership.requireAvailable(c,"+5927058194",null));
                c.rollback(point);
                // A duplicate can keep its own legacy phone; another account cannot take it.
                CustomerPhoneOwnership.requireAvailable(c,"+5927058194",2);
                s.execute("INSERT INTO customer_accounts(name,phone) VALUES('No phone',NULL),('No phone','')");
                s.execute("DELETE FROM customer_accounts WHERE customer_id=1");
                point=c.setSavepoint();
                CustomerPhoneOwnership.requireAvailable(c,"6500991",null);
                s.execute("INSERT INTO customer_accounts(name,phone) VALUES('Reuse deleted','6500991')");
                c.rollback(point);
                point=c.setSavepoint();
                assertEquals("23505",assertThrows(SQLException.class,()->ServerCustomOrderDataService.resolveOrderCustomerId(c,null,"Larex fixture","7058194")).getSQLState());
                c.rollback(point);
                try(var rs=s.executeQuery("SELECT count(*) FROM customer_accounts WHERE name='Larex fixture'")) {
                    rs.next();assertEquals(0,rs.getInt(1)); // original renamed/deleted, no new duplicate
                }
                s.execute("UPDATE customer_accounts SET phone='6500992' WHERE customer_id=2");
                CustomerPhoneOwnership.requireAvailable(c,"7058194",null);
                var removed=OverseasOrderService.customerPhone(c,"7058194");
                assertFalse(removed.get("reserved").getAsBoolean());
                assertTrue(removed.getAsJsonArray("customers").isEmpty());
                s.execute("INSERT INTO customer_accounts(name,phone) VALUES('New account','7058194')");
                assertEquals("New account",OverseasOrderService.customerPhone(c,"7058194").getAsJsonArray("customers").get(0).getAsJsonObject().get("name").getAsString());
                s.execute("UPDATE customer_accounts SET phone=NULL WHERE customer_id=2");
                CustomerPhoneOwnership.requireAvailable(c,"6500992",null);
                // Publish the disposable schema so two independent registers can race.
                c.commit();
                s.execute("INSERT INTO customer_accounts(name,phone) VALUES('First register','6750302')");
                var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
                try {
                    var attempted=new java.util.concurrent.CountDownLatch(1);
                    var competing=executor.submit(()-> {
                        try(var other=DriverManager.getConnection(System.getProperty("smartstock.test.phoneDatabaseUrl"),"postgres","");var statement=other.createStatement()) {
                            statement.execute("SET statement_timeout='10s'");
                            attempted.countDown();
                            return assertThrows(SQLException.class,()->statement.execute("INSERT INTO customer_accounts(name,phone) VALUES('Second register','+5926750302')")).getSQLState();
                        }
                    });
                    assertTrue(attempted.await(5,java.util.concurrent.TimeUnit.SECONDS));
                    c.commit();
                    assertEquals("23505",competing.get(15,java.util.concurrent.TimeUnit.SECONDS));
                }finally{executor.shutdownNow();}
            }finally{
                c.rollback();
                // These objects belong solely to this empty, explicitly named test database.
                try(var cleanup=c.createStatement()) {
                    cleanup.execute("DROP TABLE IF EXISTS public.customer_accounts CASCADE; DROP TABLE IF EXISTS public.customer_phone_ownership; DROP FUNCTION IF EXISTS public.enforce_customer_phone_ownership(); DROP FUNCTION IF EXISTS public.release_removed_customer_phone(); DROP FUNCTION IF EXISTS public.canonical_customer_id(integer); DROP FUNCTION IF EXISTS public.customer_phone_key(text)");
                    c.commit();
                }
            }
        }
    }
}
