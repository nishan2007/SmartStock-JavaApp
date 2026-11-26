package services;

import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Runs only against this explicitly named disposable database, never a configured store. */
@EnabledIfSystemProperty(named="smartstock.test.customerTypeDatabaseUrl", matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/customer_type_classification_test")
class CustomerTypeClassificationDatabaseTest {
    @Test void migrationPreservesBusinessAccountsDropsFlagAndNumbersNewAccountsFromType() throws Exception {
        try(Connection c=DriverManager.getConnection(System.getProperty("smartstock.test.customerTypeDatabaseUrl"),"postgres",""); Statement s=c.createStatement()) {
            c.setAutoCommit(false);
            try {
                s.execute("CREATE TABLE customer_types(customer_type_id serial PRIMARY KEY,name text NOT NULL,description text,is_active boolean DEFAULT true,auto_print_sale_receipt boolean DEFAULT true,customer_card_template_slot integer DEFAULT 4)");
                s.execute("CREATE TABLE customer_accounts(customer_id serial PRIMARY KEY,account_number text,name text,customer_type_id integer REFERENCES customer_types,credit_limit numeric DEFAULT 0,current_balance numeric DEFAULT 0,is_business boolean DEFAULT false,updated_at timestamptz DEFAULT now())");
                s.execute("CREATE SEQUENCE customer_account_ba_number_seq; CREATE SEQUENCE customer_account_ca_number_seq");
                s.execute("INSERT INTO customer_types(name) VALUES ('Individual'),('Businesses')");
                s.execute("INSERT INTO customer_accounts(account_number,name,customer_type_id,is_business,credit_limit,current_balance) VALUES ('BA-000042','Legacy business',1,true,25000,4500),('CA-000011','Individual',1,false,10000,1000),('CA-000012','Business type',2,false,20000,1500)");
                String migration=SqlScriptRunner.readResource("database/migrations/v1_after/20261116160000_customer_type_account_classification.sql");
                SqlScriptRunner.runSql(c,migration);
                SqlScriptRunner.runSql(c,migration);
                try(ResultSet rs=s.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='customer_accounts' AND column_name='is_business'")){rs.next();assertEquals(0,rs.getInt(1));}
                try(ResultSet rs=s.executeQuery("SELECT account_number,customer_type_id,credit_limit,current_balance FROM customer_accounts WHERE customer_id=1")) {
                    assertTrue(rs.next());assertEquals("BA-000042",rs.getString(1));assertEquals(2,rs.getInt(2));assertEquals(25000,rs.getBigDecimal(3).intValueExact());assertEquals(4500,rs.getBigDecimal(4).intValueExact());
                }
                s.execute("CREATE TRIGGER assign_customer_number BEFORE INSERT OR UPDATE ON customer_accounts FOR EACH ROW EXECUTE FUNCTION assign_customer_account_number()");
                s.execute("INSERT INTO customer_accounts(name,customer_type_id) VALUES ('New business',2),('New individual',1)");
                try(ResultSet rs=s.executeQuery("SELECT name,account_number FROM customer_accounts WHERE name LIKE 'New %' ORDER BY name")) {
                    assertTrue(rs.next());assertEquals("BA-000001",rs.getString(2));assertTrue(rs.next());assertEquals("CA-000001",rs.getString(2));
                }
                s.execute("UPDATE customer_accounts SET customer_type_id=1 WHERE customer_id=1");
                try(ResultSet rs=s.executeQuery("SELECT account_number FROM customer_accounts WHERE customer_id=1")){rs.next();assertEquals("BA-000042",rs.getString(1));}
                var legacy=com.google.gson.JsonParser.parseString("[{\"customer_id\":99,\"account_number\":\"BA-000099\",\"name\":\"Recovered business\",\"customer_type_id\":1,\"is_business\":true,\"current_balance\":600}]").getAsJsonArray();
                assertEquals(1,CloudRecoveryService.restoreRows(c,"customer_accounts",legacy));
                try(ResultSet rs=s.executeQuery("SELECT customer_type_id,current_balance FROM customer_accounts WHERE customer_id=99")){rs.next();assertEquals(2,rs.getInt(1));assertEquals(600,rs.getBigDecimal(2).intValueExact());}
                assertTrue(legacy.get(0).getAsJsonObject().has("is_business"),"Recovery must not modify its evidence input");
            } finally { c.rollback(); }
        }
    }
    @Test void migrationCreatesBusinessTypeWhenLegacyStoreHasNone() throws Exception {
        try(Connection c=DriverManager.getConnection(System.getProperty("smartstock.test.customerTypeDatabaseUrl"),"postgres","");Statement s=c.createStatement()) {
            c.setAutoCommit(false);
            try {
                s.execute("CREATE TABLE customer_types(customer_type_id serial PRIMARY KEY,name text NOT NULL,description text,is_active boolean DEFAULT true,auto_print_sale_receipt boolean DEFAULT true,customer_card_template_slot integer DEFAULT 4)");
                s.execute("CREATE TABLE customer_accounts(customer_id serial PRIMARY KEY,account_number text,name text,customer_type_id integer REFERENCES customer_types,is_business boolean DEFAULT false,updated_at timestamptz DEFAULT now())");
                s.execute("CREATE SEQUENCE customer_account_ba_number_seq; CREATE SEQUENCE customer_account_ca_number_seq");
                s.execute("INSERT INTO customer_types(name) VALUES ('Individual'); INSERT INTO customer_accounts(name,customer_type_id,is_business) VALUES ('Legacy',1,true)");
                SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/migrations/v1_after/20261116160000_customer_type_account_classification.sql"));
                try(ResultSet rs=s.executeQuery("SELECT ct.name,ct.customer_card_template_slot FROM customer_accounts ca JOIN customer_types ct USING(customer_type_id)")){assertTrue(rs.next());assertEquals("Business",rs.getString(1));assertEquals(2,rs.getInt(2));}
            }finally{c.rollback();}
        }
    }
}
