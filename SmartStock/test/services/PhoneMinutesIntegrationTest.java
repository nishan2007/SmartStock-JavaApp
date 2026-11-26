package services;

import Receipt.ServerAccountPaymentReceiptBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.net.ServerSocket;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PhoneMinutesIntegrationTest {
    @org.junit.jupiter.api.io.TempDir Path temp;
    private static final UUID DEVICE=UUID.fromString("a50a03f2-277f-4a51-8221-21f675f4ab00");
    @Test void isolatedStoreMinutesLifecycle()throws Exception {
        Path bin = Path.of(System.getenv().getOrDefault("SMARTSTOCK_TEST_POSTGRES_BIN",
                "C:/Program Files/PostgreSQL/17/bin"));
        String suffix = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? ".exe" : "";
        assumeTrue(Files.isRegularFile(bin.resolve("initdb" + suffix)));
        Path cluster = temp.resolve("cluster");
        Process init = new ProcessBuilder(bin.resolve("initdb" + suffix).toString(), "-D", cluster.toString(),
                "-A", "trust", "-U", "clock_test", "--no-locale", "--encoding=UTF8")
                .redirectErrorStream(true).redirectOutput(temp.resolve("init.log").toFile()).start();
        assertTrue(init.waitFor(30,TimeUnit.SECONDS));
        assertEquals(0,init.exitValue(),Files.readString(temp.resolve("init.log")));
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        Process server = new ProcessBuilder(bin.resolve("postgres" + suffix).toString(), "-D", cluster.toString(),
                "-h", "127.0.0.1", "-p", String.valueOf(port), "-F")
                .redirectErrorStream(true).redirectOutput(temp.resolve("postgres.log").toFile()).start();
        try {
            String url = "jdbc:postgresql://127.0.0.1:" + port + "/postgres?connectTimeout=1";
            Connection connected = null;
            for (int i=0;i<100 && connected==null;i++) {
                try { connected=DriverManager.getConnection(url,"clock_test",""); }
                catch(SQLException e) { Thread.sleep(100); }
            }
            assertNotNull(connected);

            try(Connection c=connected) {
                SchemaContractService.installLocalBaseline(c);
                assertTrue(SchemaContractService.validateLocal(c).ready());
                SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/migrations/v1_after/20261105120000_phone_minutes.sql"));
                assertTrue(SchemaContractService.validateLocal(c).ready(),"Migration must be repeatable");
                try (var statement = c.createStatement()) {
                    statement.execute("ALTER TABLE public.phone_minutes_sales DROP CONSTRAINT phone_minutes_destination_phone_by_customer, "
                            + "ADD CONSTRAINT phone_minutes_sales_destination_phone_check CHECK (destination_phone ~ '^\\+?[0-9]{7,15}$')");
                }
                SchemaContractService.requireLocalReady(c);
                assertTrue(SchemaContractService.validateLocal(c).ready());
                String previous=System.getProperty("smartstock.db.mode");System.setProperty("smartstock.db.mode","SERVER");
                try { lifecycle(c);probeHttps(c.getMetaData().getURL()); } finally {if(previous==null)System.clearProperty("smartstock.db.mode");else System.setProperty("smartstock.db.mode",previous);}
            }
        } finally { server.destroy(); assertTrue(server.waitFor(15,TimeUnit.SECONDS)); }
    }
    private void probeHttps(String url)throws Exception {
        Path home=temp.resolve("http-profile");java.nio.file.Files.createDirectories(home);
        Path config=home.resolve(".smartstock/profiles/development/database.properties");java.nio.file.Files.createDirectories(config.getParent());java.nio.file.Files.writeString(config,"mode=SERVER\nserver.host=127.0.0.1\nlocation.id=901\n");
        String javaCommand=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").toLowerCase().contains("win")?"java.exe":"java").toString();
        Process probe=new ProcessBuilder(javaCommand,"-Duser.home="+home,"-Dsmartstock.environment=development","-Dsmartstock.db.mode=SERVER","-Dsmartstock.db.url="+url,"-Dsmartstock.db.user=clock_test","-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),"services.PhoneMinutesHttpsProbe",url).redirectErrorStream(true).redirectOutput(temp.resolve("https-probe.log").toFile()).start();
        assertTrue(probe.waitFor(60,TimeUnit.SECONDS),"HTTPS probe timed out");assertEquals(0,probe.exitValue(),java.nio.file.Files.readString(temp.resolve("https-probe.log")));
        Path evidence=Path.of("target","phone-minutes-design");java.nio.file.Files.createDirectories(evidence);java.nio.file.Files.copy(temp.resolve("https-probe.log"),evidence.resolve("https-probe.log"),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    private void lifecycle(Connection c)throws Exception {
        c.setAutoCommit(false);
        try(var s=c.createStatement()) {
            s.execute("INSERT INTO locations(location_id,name,timezone) VALUES(901,'Minutes test','America/Guyana')");
            s.execute("INSERT INTO roles(role_id,role_name) VALUES(901,'Minutes denied')");
            s.execute("INSERT INTO users(user_id,username,full_name,role_id) VALUES(901,'minutes-admin','Minutes Admin',1),(902,'minutes-denied','Denied',901)");
            s.execute("INSERT INTO devices(device_id,installation_id,device_name) VALUES('"+DEVICE+"','minutes-test','Minutes Register')");
            s.execute("INSERT INTO customer_accounts(customer_id,name,credit_limit,current_balance,is_active) VALUES(901,'Minutes Customer',100,0,true),(902,'Other Customer',100,0,true),(903,'Inactive Customer',0,0,false)");
            s.execute("INSERT INTO cash_drawers(cash_drawer_id,location_id,drawer_name) VALUES(901,901,'Minutes Drawer')");
            s.execute("INSERT INTO cash_drawer_device_assignments(cash_drawer_id,location_id,device_id) VALUES(901,901,'"+DEVICE+"')");
            s.execute("INSERT INTO cash_drawer_sessions(cash_drawer_session_id,cash_drawer_id,location_id,device_id,drawer_name) VALUES(901,901,901,'"+DEVICE+"','Minutes Drawer')");
        }
        String key=UUID.randomUUID().toString(),operation="phone-minutes.create.v1",hash=LanSecurity.sha256("minutes-fixture");
        assertNull(LanApiServer.loadIdempotentResult(c,DEVICE,key,operation,hash));
        long first=create(c,901,"6000001",500,"CREDIT");
        LanApiServer.completeIdempotency(c,DEVICE,key,Map.of("minutesId",first));
        assertEquals(first,((Number)LanApiServer.loadIdempotentResult(c,DEVICE,key,operation,hash).get("minutesId")).longValue());
        rejected(c,()->LanApiServer.loadIdempotentResult(c,DEVICE,key,operation,LanSecurity.sha256("different")));
        assertMoney(500,CustomerAccountLedgerService.calculateCustomerBalance(c,901));
        assertMoney(500,PhoneMinutesService.minutesDue(c,901));
        assertMoney(100,(BigDecimal)LanCustomerAccountService.details(c,901,901).get("availableCredit"));
        Savepoint merchandise=c.setSavepoint();
        var charge=LanSalesService.class.getDeclaredMethod("chargeCustomerAccount",Connection.class,int.class,BigDecimal.class,int.class,String.class,UUID.class,int.class);charge.setAccessible(true);
        charge.invoke(null,c,901,BigDecimal.valueOf(100),901,"Admin",DEVICE,901);
        c.rollback(merchandise);
        assertThrows(java.lang.reflect.InvocationTargetException.class,()->charge.invoke(null,c,901,BigDecimal.valueOf(101),901,"Admin",DEVICE,901));c.rollback(merchandise);
        Savepoint staleStore=c.setSavepoint();
        try(var st=c.createStatement()){st.execute("INSERT INTO locations(location_id,name) VALUES(902,'Unsynced other store')");}
        assertFalse(CrossStoreCustomerHistoryService.allStoresCurrent(c,901));
        assertThrows(SQLException.class,()->CustomerAccountLedgerService.requireCurrentMultiStoreBalance(c,901));
        long offlineCredit=create(c,901,null,5140,"CREDIT");
        assertMoney(5640,CustomerAccountLedgerService.calculateCustomerBalance(c,901));
        reverse(c,offlineCredit);assertMoney(500,CustomerAccountLedgerService.calculateCustomerBalance(c,901));
        long offlineCash=create(c,901,"",10,"CASH");reverse(c,offlineCash);
        assertMoney(0,CashDrawerService.calculateNetCashActivity(c,901));
        c.rollback(staleStore);
        rejected(c, () -> create(c, null, "", 100, "CASH"));
        rejected(c, () -> create(c, 901, "123", 100, "CREDIT"));
        long walkin=create(c,null,"6000002",100,"CASH"),linked=create(c,901,"6000003",200,"CASH");
        rejected(c, () -> {
            try (var statement = c.prepareStatement("UPDATE phone_minutes_sales SET destination_phone = '' WHERE minutes_id = ?")) {
                statement.setLong(1, walkin);
                statement.executeUpdate();
            }
        });
        assertMoney(300,CashDrawerService.calculateNetCashActivity(c,901));
        assertMoney(500,CustomerAccountLedgerService.calculateCustomerBalance(c,901));
        assertEquals(3,PhoneMinutesService.history(c,json("{}"),901,901).size());
        assertEquals(1,PhoneMinutesService.history(c,json("{search:'6000001'}"),901,901).size());
        assertEquals(2,PhoneMinutesService.history(c,json("{customerId:901}"),901,901).size());
        assertTrue(PhoneMinutesService.history(c,json("{from:'1900-01-01',to:'1900-01-02'}"),901,901).isEmpty());
        var open=LanCustomerAccountService.openBalances(c,901,901);assertEquals("MINUTES",open.get(0).get("documentType"));assertTrue(open.get(0).get("documentNumber").toString().contains("6000001"));
        Map<String,Object> payment=pay(c,200,"CASH","[{documentType:'MINUTES',documentId:"+first+",amount:200}]");
        long tx=((Number)payment.get("transactionId")).longValue();assertMoney(300,CustomerAccountLedgerService.calculateCustomerBalance(c,901));assertMoney(300,PhoneMinutesService.minutesDue(c,901));
        var receipt=ServerAccountPaymentReceiptBuilder.loadPaymentReceipt(c,901,tx);assertEquals(1,receipt.getAllocations().size());assertTrue(receipt.getAllocations().get(0).targetLabel().contains("6000001"));assertMoney(200,receipt.getAllocations().get(0).appliedAmount());
        assertMoney(500,CashDrawerService.calculateNetCashActivity(c,901));
        assertMoney(100,(BigDecimal)LanCustomerAccountService.details(c,901,901).get("availableCredit"));
        reverse(c,first);assertMoney(0,PhoneMinutesService.minutesDue(c,901));assertMoney(0,CustomerAccountLedgerService.calculateCustomerBalance(c,901));assertMoney(200,PhoneMinutesService.availableCredit(c,901));
        // Refunds preserve the initial collection and create a separate negative tender event.
        reverse(c,walkin);assertMoney(400,CashDrawerService.calculateNetCashActivity(c,901));assertMoney(100,CashDrawerService.calculateReturnedCash(c,901));
        reverse(c,linked);assertMoney(200,CashDrawerService.calculateNetCashActivity(c,901));
        long next=create(c,901,"",400,"CREDIT");
        assertEquals("MIN-" + next, LanCustomerAccountService.openBalances(c,901,901).get(0).get("documentNumber"));
        pay(c,200,"APPLY_CREDIT","[{documentType:'MINUTES',documentId:"+next+",amount:200}]");
        assertMoney(0,PhoneMinutesService.availableCredit(c,901));assertMoney(200,CustomerAccountLedgerService.calculateCustomerBalance(c,901));assertMoney(200,PhoneMinutesService.minutesDue(c,901));assertMoney(200,CashDrawerService.calculateNetCashActivity(c,901));
        // Mixed merchandise and minutes allocations update each debt without consuming minutes credit capacity.
        try(var s=c.createStatement()){s.execute("INSERT INTO sales(sale_id,location_id,customer_id,total_amount,amount_paid,payment_method,payment_status) VALUES(901,901,901,80,0,'ACCOUNT','UNPAID')");s.execute("INSERT INTO customer_account_transactions(customer_id,location_id,sale_id,amount,transaction_type) VALUES(901,901,901,80,'SALE_CREDIT')");}
        CustomerAccountLedgerService.repairCustomerBalance(c,901);
        assertMoney(20,(BigDecimal)LanCustomerAccountService.details(c,901,901).get("availableCredit"));
        pay(c,100,"CASH","[{documentType:'MINUTES',documentId:"+next+",amount:50},{documentType:'SALE',documentId:901,amount:50}]");
        assertMoney(180,CustomerAccountLedgerService.calculateCustomerBalance(c,901));assertMoney(150,PhoneMinutesService.minutesDue(c,901));assertMoney(70,(BigDecimal)LanCustomerAccountService.details(c,901,901).get("availableCredit"));
        var zone=java.time.ZoneId.of("America/Guyana");var now=java.time.ZonedDateTime.now(zone);
        var filters=new ReportDataService.Filters(now.minusDays(1),now.plusDays(1),zone,901,Set.of(),Set.of(),Set.of(),Set.of(),Set.of(),Set.of());
        var report=ReportDataService.load(c,filters,true,false);assertMoney(400,report.metrics().get("Phone Minutes Sales"));assertMoney(300,report.metrics().get("Net Cash Movement"));
        assertTrue(report.revenueSeries().stream().anyMatch(series->series.name().equals("Phone Minutes")));
        try(var st=c.createStatement();var use=st.executeQuery("SELECT SUM(amount) FROM phone_minutes_credit_uses")){use.next();assertMoney(200,use.getBigDecimal(1));}
        Savepoint browserTest=c.setSavepoint();
        try(var st=c.createStatement()){st.execute("INSERT INTO user_locations(user_id,location_id) VALUES(901,901)");st.execute("UPDATE cash_drawer_sessions SET current_cashier_user_id=901 WHERE cash_drawer_session_id=901");}
        UUID browser=UUID.randomUUID();String webKey=UUID.randomUUID().toString();
        JsonObject web=json("{customerId:901,phone:'+5926000123',amount:5140,method:'CREDIT',idempotencyKey:'"+webKey+"'}");
        var webResult=(Map<?,?>)PhoneMinutesWebService.execute(c,901,"Admin",browser,901,"create",web);long webId=((Number)webResult.get("minutesId")).longValue();
        var replay=(Map<?,?>)PhoneMinutesWebService.execute(c,901,"Admin",browser,901,"create",web);assertEquals(webId,((Number)replay.get("minutesId")).longValue());assertMoney(5320,CustomerAccountLedgerService.calculateCustomerBalance(c,901));
        JsonObject changed=web.deepCopy();changed.addProperty("amount",6000);rejected(c,()->PhoneMinutesWebService.execute(c,901,"Admin",browser,901,"create",changed));
        PhoneMinutesWebService.execute(c,901,"Admin",browser,901,"reverse",json("{minutesId:"+webId+",reason:'Wrong destination',idempotencyKey:'"+UUID.randomUUID()+"'}"));assertMoney(180,CustomerAccountLedgerService.calculateCustomerBalance(c,901));
        JsonObject webCash=json("{phone:'6000444',amount:30,method:'CASH',drawerSessionId:901,idempotencyKey:'"+UUID.randomUUID()+"'}");
        var cashResult=(Map<?,?>)PhoneMinutesWebService.execute(c,901,"Admin",browser,901,"create",webCash);long webCashId=((Number)cashResult.get("minutesId")).longValue();assertMoney(330,CashDrawerService.calculateNetCashActivity(c,901));
        PhoneMinutesWebService.execute(c,901,"Admin",browser,901,"reverse",json("{minutesId:"+webCashId+",reason:'Fixture refund',drawerSessionId:901,idempotencyKey:'"+UUID.randomUUID()+"'}"));assertMoney(300,CashDrawerService.calculateNetCashActivity(c,901));
        assertFalse(PhoneMinutesWebService.drawers(c,901,901).isEmpty());assertTrue(PhoneMinutesWebService.drawers(c,902,901).isEmpty());
        rejected(c,()->PhoneMinutesWebService.execute(c,902,"Denied",browser,901,"create",web));
        rejected(c,()->PhoneMinutesWebService.execute(c,901,"Admin",browser,902,"create",web));
        rejected(c,()->PhoneMinutesService.browserDrawer(c,901,902,901L));
        c.rollback(browserTest);
        Savepoint remote=c.setSavepoint();
        try(var st=c.createStatement()) {
            st.execute("INSERT INTO sync_cross_store_customer_history_cache(source_location_id,event_key,customer_id,event_type,store_name,amount,document_balance,cache_status) VALUES(77,'LEDGER:1',901,'MINUTES_CREDIT','Other',300,0,'CURRENT'),(77,'MINUTES:1',901,'MINUTES','Other',300,300,'CURRENT'),(77,'LEDGER:2',901,'MINUTES_PAID','Other',200,0,'CURRENT')");
        }
        CustomerAccountLedgerService.repairCustomerBalance(c,901);assertMoney(480,CustomerAccountLedgerService.calculateCustomerBalance(c,901));assertMoney(450,PhoneMinutesService.minutesDue(c,901));assertMoney(70,(BigDecimal)LanCustomerAccountService.details(c,901,901).get("availableCredit"));c.rollback(remote);
        assertTrue(((List<?>)LanCustomerAccountService.transactions(c,901,901,901).get("transactions")).size()>5);
        assertTrue(((List<?>)LanCustomerAccountService.payments(c,901,901).get("payments")).size()>=4);
        // Each rejected mutation is rolled back exactly as the server handler does.
        rejected(c,()->create(c,null,"6000000",10,"CREDIT"));
        rejected(c,()->create(c,903,"6000000",10,"CREDIT"));
        rejected(c,()->PhoneMinutesService.create(c,json("{phone:'6000000',amount:10,method:'CASH'}"),DEVICE,902,"Denied",901));
        rejected(c,()->PhoneMinutesService.reverse(c,json("{minutesId:"+next+",reason:'denied'}"),DEVICE,902,"Denied",901));
        rejected(c,()->reverse(c,first));
        rejected(c,()->PhoneMinutesService.reverse(c,json("{minutesId:"+next+",reason:''}"),DEVICE,901,"Admin",901));
        rejected(c,()->pay(c,500,"CASH","[{documentType:'MINUTES',documentId:"+next+",amount:500}]"));
        rejected(c,()->pay(c,1,"APPLY_CREDIT","[{documentType:'MINUTES',documentId:"+next+",amount:1}]"));
        rejected(c,()->pay(c,1,"CASH","[{documentType:'MINUTES',documentId:"+next+",amount:1},{documentType:'MINUTES',documentId:"+next+",amount:1}]"));
        rejected(c,()->PhoneMinutesService.applyPayment(c,902,next,tx,BigDecimal.ONE));
        BigDecimal before=CustomerAccountLedgerService.calculateCustomerBalance(c,901);Savepoint point=c.setSavepoint();create(c,901,"6000005",99,"CREDIT");c.rollback(point);assertEquals(before,CustomerAccountLedgerService.calculateCustomerBalance(c,901));
        try(var s=c.createStatement()){s.execute("UPDATE cash_drawer_sessions SET status='CLOSED' WHERE cash_drawer_session_id=901");}
        rejected(c,()->create(c,null,"6000006",10,"CASH"));
        c.rollback();
        concurrency(c);
    }
    private void concurrency(Connection c)throws Exception {
        try(var st=c.createStatement()) {
            st.execute("INSERT INTO locations(location_id,name) VALUES(901,'Concurrency store')");
            st.execute("INSERT INTO users(user_id,username,full_name,role_id) VALUES(901,'concurrent-admin','Admin',1)");
            st.execute("INSERT INTO devices(device_id,installation_id) VALUES('"+DEVICE+"','concurrent-device')");
            st.execute("INSERT INTO customer_accounts(customer_id,name,credit_limit,is_active) VALUES(901,'Concurrent customer',10,true)");
        }
        long sale=create(c,901,"6000000",100,"CREDIT");c.commit();
        String url=c.getMetaData().getURL();
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start=new java.util.concurrent.CountDownLatch(1);
            List<java.util.concurrent.Future<Boolean>> attempts=new ArrayList<>();
            for(int i=0;i<2;i++)attempts.add(executor.submit(()->{start.await();try(var other=DriverManager.getConnection(url,"clock_test","")){other.setAutoCommit(false);try{pay(other,80,"CARD","[{documentType:'MINUTES',documentId:"+sale+",amount:80}]");other.commit();return true;}catch(Exception ex){other.rollback();return false;}}}));
            start.countDown();int success=0;for(var result:attempts)if(result.get(15,TimeUnit.SECONDS))success++;
            assertEquals(1,success,"Only one competing payment can consume the balance");assertMoney(20,CustomerAccountLedgerService.calculateCustomerBalance(c,901));assertMoney(20,PhoneMinutesService.minutesDue(c,901));
        }finally{executor.shutdownNow();}
    }
    private static long create(Connection c,Integer customer,String phone,int amount,String method)throws Exception {
        JsonObject b=json("{}");b.addProperty("customerId",customer);b.addProperty("phone",phone);b.addProperty("amount",amount);b.addProperty("method",method);return ((Number)PhoneMinutesService.create(c,b,DEVICE,901,"Admin",901).get("minutesId")).longValue();
    }
    private static void reverse(Connection c,long id)throws Exception {PhoneMinutesService.reverse(c,json("{minutesId:"+id+",reason:'Wrong destination'}"),DEVICE,901,"Admin",901);}
    private static Map<String,Object> pay(Connection c,int amount,String method,String allocations)throws Exception {return LanCustomerAccountService.adjust(c,json("{customerId:901,action:'PAYMENT',amount:"+amount+",paymentMethod:'"+method+"',paymentReference:'Test reference',allocations:"+allocations+"}"),DEVICE,901,"Admin",901);}
    private static JsonObject json(String text){return JsonParser.parseString(text).getAsJsonObject();}
    private static void assertMoney(int expected,BigDecimal actual){assertEquals(0,BigDecimal.valueOf(expected).compareTo(actual),"Expected "+expected+", got "+actual);}
    @FunctionalInterface private interface Action{void run()throws Exception;}
    private static void rejected(Connection c,Action action)throws Exception {Savepoint point=c.setSavepoint();try{assertThrows(Exception.class,action::run);}finally{c.rollback(point);}}
}
