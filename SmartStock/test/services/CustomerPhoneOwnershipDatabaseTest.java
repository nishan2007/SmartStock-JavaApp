package services;

import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Only an explicitly supplied, disposable empty database may be used. */
@EnabledIfSystemProperty(named="smartstock.test.phoneDatabaseUrl", matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/phone_ownership_test")
class CustomerPhoneOwnershipDatabaseTest {
    @Test void preservesLegacyDuplicatesAndPreventsReusingNumbers() throws Exception {
        try(var c=DriverManager.getConnection(System.getProperty("smartstock.test.phoneDatabaseUrl"),"postgres","")) {
            c.setAutoCommit(false);
            try(var s=c.createStatement()) {
                s.execute("CREATE TABLE public.customer_accounts(customer_id serial PRIMARY KEY,name text,phone text,is_active boolean DEFAULT true)");
                s.execute("INSERT INTO customer_accounts(name,phone) VALUES('Larex fixture','705-8194'),('Larex fixture','+5927058194')");
                String migration=SqlScriptRunner.readResource("database/migrations/v1_after/20261019120000_customer_phone_ownership.sql");
                SqlScriptRunner.runSql(c,migration);
                SqlScriptRunner.runSql(c,migration);
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
                assertThrows(SQLException.class,()->s.execute("INSERT INTO customer_accounts(name,phone) VALUES('Reuse deleted','6500991')"));
                c.rollback(point);
                point=c.setSavepoint();
                assertEquals("23505",assertThrows(SQLException.class,()->ServerCustomOrderDataService.resolveOrderCustomerId(c,null,"Larex fixture","7058194")).getSQLState());
                c.rollback(point);
                try(var rs=s.executeQuery("SELECT count(*) FROM customer_accounts WHERE name='Larex fixture'")) {
                    rs.next();assertEquals(0,rs.getInt(1)); // original renamed/deleted, no new duplicate
                }
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
                    cleanup.execute("DROP TABLE IF EXISTS public.customer_accounts CASCADE; DROP TABLE IF EXISTS public.customer_phone_ownership; DROP FUNCTION IF EXISTS public.enforce_customer_phone_ownership(); DROP FUNCTION IF EXISTS public.customer_phone_key(text)");
                    c.commit();
                }
            }
        }
    }
}
