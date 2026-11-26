package services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.nio.file.*;
import java.sql.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CustomOrderAccountBalanceTest {
    @TempDir Path temp;
    @Test void accountOrdersConsumeCreditUntilPaidAndOrdinaryOrdersStaySeparate() throws Exception {
        Path bin=Path.of(System.getenv().getOrDefault("SMARTSTOCK_TEST_POSTGRES_BIN","C:/Program Files/PostgreSQL/17/bin"));
        String suffix=System.getProperty("os.name").toLowerCase().contains("win")?".exe":"";
        assumeTrue(Files.isRegularFile(bin.resolve("initdb"+suffix)));
        Path cluster=temp.resolve("cluster");
        Process init=new ProcessBuilder(bin.resolve("initdb"+suffix).toString(),"-D",cluster.toString(),"-A","trust","-U","account_test","--no-locale","--encoding=UTF8")
            .redirectErrorStream(true).redirectOutput(temp.resolve("init.log").toFile()).start();
        assertTrue(init.waitFor(30,TimeUnit.SECONDS));assertEquals(0,init.exitValue());
        int port;try(ServerSocket socket=new ServerSocket(0)){port=socket.getLocalPort();}
        Process server=new ProcessBuilder(bin.resolve("postgres"+suffix).toString(),"-D",cluster.toString(),"-h","127.0.0.1","-p",String.valueOf(port),"-F")
            .redirectErrorStream(true).redirectOutput(temp.resolve("postgres.log").toFile()).start();
        try {
            Connection ready=null;
            for(int i=0;i<100&&ready==null;i++){
                try{ready=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:"+port+"/postgres?connectTimeout=1","account_test","");}
                catch(SQLException e){Thread.sleep(100);}
            }
            assertNotNull(ready);
            try(Connection c=ready;Statement st=c.createStatement()){
                st.execute("CREATE TABLE customer_account_transactions(custom_order_id bigint,customer_id int,transaction_type text,amount numeric,credit_applied_amount numeric)");
                st.execute("CREATE TABLE custom_orders(custom_order_id bigint,customer_id int,payment_method text,pricing_state text,status text,invoice_billed boolean,balance_due numeric)");
                st.execute("CREATE TABLE sync_cross_store_customer_history_cache(source_location_id int,source_id bigint,customer_id int,event_key text,event_type text,amount numeric,credit_applied_amount numeric,document_balance numeric,payment_method text,cache_status text,document_status text)");
                st.execute("INSERT INTO customer_account_transactions VALUES(NULL,1,'MANUAL_CHARGE',50,0),(1,1,'CUSTOM_ORDER_BALANCE',100,0),(1,1,'CUSTOM_ORDER_REFUND',-40,0)");
                st.execute("INSERT INTO custom_orders VALUES(1,1,'ACCOUNT','FINAL','DELIVERED',false,100),(2,1,'CASH','FINAL','NEW',false,250),(3,1,'ACCOUNT','FINAL','CANCELLED',false,999),(4,1,'ACCOUNT','FINAL','NEW',true,888)");
                assertMoney(c,150);
                try(ResultSet rs=st.executeQuery("SELECT SUM("+CustomerAccountLedgerService.balanceDeltaSql("t")+") FROM customer_account_transactions t")){
                    assertTrue(rs.next());assertEquals(50,rs.getBigDecimal(1).intValueExact());
                }
                st.execute("UPDATE custom_orders SET balance_due=60 WHERE status='DELIVERED'");
                assertMoney(c,110);
                st.execute("INSERT INTO sync_cross_store_customer_history_cache VALUES(2,9,1,'CUSTOM_ORDER:9','CUSTOM_ORDER',0,0,30,'ACCOUNT','CURRENT','DELIVERED')");
                st.execute("INSERT INTO sync_cross_store_customer_history_cache VALUES(2,9,1,'LEDGER:90','CUSTOM_ORDER_REFUND',-20,0,0,'CASH','CURRENT',''),(2,9,1,'LEDGER:91','CUSTOM_ORDER_CREDIT',30,0,0,'ACCOUNT','CURRENT','')");
                assertMoney(c,140);
                st.execute("UPDATE custom_orders SET balance_due=0 WHERE status='DELIVERED'");
                assertMoney(c,80);
                st.execute("UPDATE sync_cross_store_customer_history_cache SET document_balance=0");
                assertMoney(c,50);
            }
        } finally {server.destroy();if(!server.waitFor(10,TimeUnit.SECONDS))server.destroyForcibly();}
    }
    private static void assertMoney(Connection c,int amount)throws SQLException {
        assertEquals(0,BigDecimal.valueOf(amount).compareTo(CustomerAccountLedgerService.calculateCustomerBalance(c,1)));
    }
}
