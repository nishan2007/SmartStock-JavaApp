package services;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real PostgreSQL transactions, in a disposable loopback cluster only. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StockLedgerServiceTest {
    @TempDir static Path temp;
    Path bin,cluster;
    String suffix,url;
    Process server;
    UUID device=UUID.randomUUID();

    @BeforeAll void start()throws Exception {
        bin=Path.of(System.getenv().getOrDefault("SMARTSTOCK_TEST_POSTGRES_BIN","C:/Program Files/PostgreSQL/17/bin"));
        suffix=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?".exe":"";
        assumeTrue(Files.isRegularFile(bin.resolve("initdb"+suffix)),"PostgreSQL test binaries unavailable");
        cluster=temp.resolve("cluster");
        Process init=new ProcessBuilder(bin.resolve("initdb"+suffix).toString(),"-D",cluster.toString(),"-A","trust","-U","ledger_test","--no-locale","--encoding=UTF8")
                .redirectErrorStream(true).redirectOutput(temp.resolve("init.log").toFile()).start();
        assertTrue(init.waitFor(30,TimeUnit.SECONDS));assertEquals(0,init.exitValue(),Files.readString(temp.resolve("init.log")));
        int port;try(ServerSocket socket=new ServerSocket(0)){port=socket.getLocalPort();}
        server=new ProcessBuilder(bin.resolve("postgres"+suffix).toString(),"-D",cluster.toString(),"-h","127.0.0.1","-p",String.valueOf(port),"-F")
                .redirectErrorStream(true).redirectOutput(temp.resolve("postgres.log").toFile()).start();
        url="jdbc:postgresql://127.0.0.1:"+port+"/postgres?connectTimeout=1";
        Connection ready=null;
        for(int i=0;i<100 && ready==null;i++){try{ready=open();}catch(SQLException e){Thread.sleep(100);}}
        assertNotNull(ready);
        try(Connection c=ready){
            SchemaContractService.installLocalBaseline(c);
            sql(c,"INSERT INTO locations(location_id,name,timezone,receipt_store_code) VALUES(501,'Ledger Test','America/Guyana','0501'),(502,'Other','America/Guyana','0502')");
            sql(c,"INSERT INTO roles(role_id,role_name) VALUES(501,'Ledger Test')");
            sql(c,"INSERT INTO role_permissions(role_id,permission_id) SELECT 501,permission_id FROM permissions WHERE permission_key IN ('RECORD_STOCK_EXPENSE','REVERSE_STOCK_LEDGER','RECEIVING_INVENTORY','RECEIVING_STOCK_OVERRIDE','VIEW_REPORTS','BALANCE_SHEET','VIEW_COST_PRICE')");
            sql(c,"INSERT INTO users(user_id,username,full_name,role_id) VALUES(501,'ledger_employee','Employee',501),(502,'ledger_denied','Denied',NULL)");
            sql(c,"INSERT INTO devices(device_id,installation_id,last_store_id,receipt_device_code) VALUES('"+device+"','ledger-test',501,'0501')");
            sql(c,"INSERT INTO products(product_id,name,sku,price,cost_price) VALUES(501,'Stapler','LEDGER501',100,60),(502,'Medal','LEDGER502',50,20)");
            sql(c,"INSERT INTO inventory(product_id,location_id,quantity_on_hand) VALUES(501,501,10),(502,501,10)");
        }
    }

    @AfterAll void stop()throws Exception {
        if(server==null)return;
        Process stop=new ProcessBuilder(bin.resolve("pg_ctl"+suffix).toString(),"-D",cluster.toString(),"-m","immediate","stop")
                .redirectErrorStream(true).redirectOutput(temp.resolve("stop.log").toFile()).start();
        stop.waitFor(15,TimeUnit.SECONDS);server.destroy();if(!server.waitFor(10,TimeUnit.SECONDS))server.destroyForcibly();
    }
    Connection open()throws SQLException{return DriverManager.getConnection(url,"ledger_test","");}
    static void sql(Connection c,String sql)throws SQLException{try(var s=c.createStatement()){s.execute(sql);}}
    static long number(Connection c,String sql)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery(sql)){assertTrue(r.next());return r.getLong(1);}}

    @Test void signedExpenseSnapshotsPricesAndReversalPreservesCashAndAudit()throws Exception {
        try(Connection c=open()){
            c.setAutoCommit(false);
            JsonObject request=expense(501,2,"Company Use");
            String id=(String)StockLedgerService.post(c,request,device,501,"Employee",501).get("entryId");
            assertEquals(8,number(c,"SELECT quantity_on_hand FROM inventory WHERE product_id=501"));
            assertEquals(200,number(c,"SELECT quantity*selling_price FROM stock_ledger_entries"));
            sql(c,"UPDATE products SET price=999,cost_price=888 WHERE product_id=501");
            assertEquals(100,number(c,"SELECT selling_price FROM stock_ledger_entries"));
            assertEquals(60,number(c,"SELECT unit_cost FROM stock_ledger_entries"));
            assertEquals(1,number(c,"SELECT count(*) FROM inventory_movements WHERE reason='STOCK_EXPENSE'"));
            for(String table:java.util.List.of("sales","expenses","bank_transactions","cash_drawer_sessions"))
                assertEquals(0,number(c,"SELECT count(*) FROM "+table),table);
            JsonObject reverse=new JsonObject();reverse.addProperty("entryId",id);reverse.addProperty("reason","Entered in error");
            assertThrows(LanInventoryService.RuleViolation.class,()->StockLedgerService.reverse(c,reverse,device,502,"Denied",501));
            assertThrows(LanInventoryService.RuleViolation.class,()->StockLedgerService.reverse(c,reverse,device,501,"Employee",502));
            StockLedgerService.reverse(c,reverse,device,501,"Employee",501);
            assertEquals(10,number(c,"SELECT quantity_on_hand FROM inventory WHERE product_id=501"));
            assertEquals(0,number(c,"SELECT sum(quantity*selling_price*CASE WHEN reversal_of IS NULL THEN 1 ELSE -1 END) FROM stock_ledger_entries"));
            assertEquals(1,number(c,"SELECT count(*) FROM stock_ledger_entries WHERE signature_png IS NOT NULL"));
            assertThrows(LanInventoryService.RuleViolation.class,()->StockLedgerService.reverse(c,reverse,device,501,"Employee",501));
            c.rollback();assertEquals(0,number(c,"SELECT count(*) FROM stock_ledger_entries"));
        }
    }

    @Test void rejectsMissingEvidencePermissionsAndInsufficientStock()throws Exception {
        try(Connection c=open()){
            c.setAutoCommit(false);JsonObject request=expense(501,2,"Donation");
            assertThrows(LanInventoryService.RuleViolation.class,()->StockLedgerService.post(c,request,device,502,"Denied",501));
            request.addProperty("reason","");assertThrows(LanInventoryService.RuleViolation.class,()->StockLedgerService.post(c,request,device,501,"Employee",501));
            request.addProperty("reason","Community donation");request.addProperty("quantity",11);
            assertThrows(LanInventoryService.RuleViolation.class,()->StockLedgerService.post(c,request,device,501,"Employee",501));
            request.addProperty("quantity",1);request.addProperty("signaturePngBase64",signature(false));
            assertThrows(LanInventoryService.RuleViolation.class,()->StockLedgerService.post(c,request,device,501,"Employee",501));
            assertEquals(10,number(c,"SELECT quantity_on_hand FROM inventory WHERE product_id=501"));
            assertEquals(0,number(c,"SELECT count(*) FROM stock_ledger_entries"));c.rollback();
        }
    }

    @Test void receivingRecordsOnlyNegativeCorrectionBeforeDeliveryAndRequiresPerLineReason()throws Exception {
        try(Connection c=open()){
            c.setAutoCommit(false);JsonObject body=receiving();
            var line=body.getAsJsonArray("lines").get(0).getAsJsonObject();line.remove("lossReason");
            assertThrows(LanInventoryService.RuleViolation.class,()->LanInventoryService.receive(c,body,device,501,"Employee",501,(a,b,d,e)->{throw new AssertionError("Unexpected manager approval");}));
            assertEquals(10,number(c,"SELECT quantity_on_hand FROM inventory WHERE product_id=501"));
            JsonObject valid=receiving();valid.getAsJsonArray("lines").get(0).getAsJsonObject().remove("lossNotes");
            LanInventoryService.receive(c,valid,device,501,"Employee",501,(a,b,d,e)->{throw new AssertionError("Unexpected manager approval");});
            assertEquals(12,number(c,"SELECT quantity_on_hand FROM inventory WHERE product_id=501"));
            assertEquals(16,number(c,"SELECT quantity_on_hand FROM inventory WHERE product_id=502"));
            assertEquals(1,number(c,"SELECT count(*) FROM stock_ledger_entries"));
            assertEquals(3,number(c,"SELECT quantity FROM stock_ledger_entries"));
            assertEquals(7,number(c,"SELECT counted_quantity FROM stock_ledger_entries"));
            assertEquals(10,number(c,"SELECT system_quantity FROM stock_ledger_entries"));
            assertEquals(1,number(c,"SELECT count(*) FROM stock_ledger_entries e JOIN receiving_batches b USING(receive_id)"));
            JsonObject reversal=new JsonObject();
            try(var q=c.createStatement();var r=q.executeQuery("SELECT entry_id FROM stock_ledger_entries")){r.next();reversal.addProperty("entryId",r.getString(1));}
            reversal.addProperty("reason","Found missing stock in storage");
            StockLedgerService.reverse(c,reversal,device,501,"Employee",501);
            assertEquals(15,number(c,"SELECT quantity_on_hand FROM inventory WHERE product_id=501"));
            c.rollback();assertEquals(10,number(c,"SELECT quantity_on_hand FROM inventory WHERE product_id=501"));
        }
    }

    @Test void reportScopesLocationPermissionsSignatureAndCost()throws Exception {
        try(Connection c=open()){
            c.setAutoCommit(false);
            var result=StockLedgerService.post(c,expense(501,1,"Donation"),device,501,"Employee",501);
            JsonObject range=new JsonObject();range.addProperty("fromEpochMillis",0);range.addProperty("toEpochMillis",System.currentTimeMillis()+60_000);
            assertThrows(LanInventoryService.RuleViolation.class,()->StockLedgerService.load(c,range,502,501));
            assertEquals(0,((java.util.List<?>)StockLedgerService.load(c,range,501,502).get("entries")).size());
            var rows=(java.util.List<?>)StockLedgerService.load(c,range,501,501).get("entries");
            assertEquals(1,rows.size());assertNotNull(((Map<?,?>)rows.get(0)).get("unit_cost"));
            sql(c,"DELETE FROM role_permissions WHERE role_id=501 AND permission_id=(SELECT permission_id FROM permissions WHERE permission_key='VIEW_COST_PRICE')");
            rows=(java.util.List<?>)StockLedgerService.load(c,range,501,501).get("entries");assertNull(((Map<?,?>)rows.get(0)).get("unit_cost"));
            JsonObject detail=new JsonObject();detail.addProperty("entryId",result.get("entryId").toString());
            assertFalse(StockLedgerService.detail(c,detail,501,501).get("signaturePngBase64").toString().isBlank());
            assertThrows(LanInventoryService.RuleViolation.class,()->StockLedgerService.detail(c,detail,501,502));
            c.rollback();
        }
    }

    @Test void migrationIsRepeatableAndStaleReceivingDoesNotChangeStock()throws Exception {
        try(Connection c=open()){
            c.setAutoCommit(false);
            sql(c,"DROP TABLE stock_ledger_entries");
            String migration=SqlScriptRunner.readResource("database/migrations/v1_after/20261021180000_stock_ledgers.sql");
            SqlScriptRunner.runSql(c,migration);SqlScriptRunner.runSql(c,migration);
            SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/migrations/v1_after/20261022120000_historical_stock_losses.sql"));
            assertEquals(1,number(c,"SELECT count(*) FROM pg_indexes WHERE indexname='stock_ledger_location_date_idx'"));
            JsonObject stale=receiving();stale.getAsJsonArray("lines").get(0).getAsJsonObject().addProperty("expectedSystemStock",9);
            var error=assertThrows(LanInventoryService.RuleViolation.class,()->LanInventoryService.receive(c,stale,device,501,"Employee",501,(a,b,d,e)->{throw new AssertionError();}));
            assertEquals(409,error.status());assertEquals(10,number(c,"SELECT quantity_on_hand FROM inventory WHERE product_id=501"));
            assertEquals(0,number(c,"SELECT count(*) FROM stock_ledger_entries"));c.rollback();
        }
    }

    @Test void historicalLossImportIsRepeatableAndNeverDeductsStockAgain()throws Exception {
        try(Connection c=open()){
            c.setAutoCommit(false);
            sql(c,"INSERT INTO inventory_movements(product_id,location_id,change_qty,reason,note,user_id,user_name,receive_id,created_at) VALUES(501,501,-3,'RECEIVING_STOCK_OVERRIDE','system_stock=10; counted_stock=7; reason=Missing on shelf',501,'Original employee','OLD-RECEIVE','2026-01-02 10:00-04'),(501,501,2,'RECEIVING_STOCK_OVERRIDE','Positive correction',501,'Employee','POSITIVE','2026-01-03 10:00-04'),(501,501,-1,'SALE','Sale',501,'Employee',NULL,now())");
            sql(c,"INSERT INTO custom_order_items(custom_item_id,item_name,sku,fixed_price,quantity_on_hand) VALUES(501,'Historical custom stock','OLD-CUSTOM',75,10)");
            sql(c,"INSERT INTO custom_order_item_movements(custom_item_id,location_id,change_qty,reason,note,user_name,receive_id,created_at) VALUES(501,501,-2,'RECEIVING_STOCK_OVERRIDE','Old discrepancy','Former employee','OLD-CUSTOM','2026-01-02 11:00-04')");
            String migration=SqlScriptRunner.readResource("database/migrations/v1_after/20261022120000_historical_stock_losses.sql");
            SqlScriptRunner.runSql(c,migration);sql(c,"SET CONSTRAINTS ALL IMMEDIATE");SqlScriptRunner.runSql(c,migration);
            assertEquals(2,number(c,"SELECT count(*) FROM stock_ledger_entries"));
            assertEquals(10,number(c,"SELECT quantity_on_hand FROM inventory WHERE product_id=501"));
            assertEquals(10,number(c,"SELECT quantity_on_hand FROM custom_order_items WHERE custom_item_id=501"));
            assertEquals(3,number(c,"SELECT quantity FROM stock_ledger_entries WHERE item_type='PRODUCT'"));
            assertEquals(7,number(c,"SELECT counted_quantity FROM stock_ledger_entries WHERE item_type='PRODUCT'"));
            assertEquals(2,number(c,"SELECT count(*) FROM stock_ledger_entries WHERE valuation_source='CURRENT_PRICE_ESTIMATE' AND created_at<'2026-02-01'"));
            assertEquals(1,number(c,"SELECT count(*) FROM stock_ledger_entries WHERE user_id IS NULL AND system_quantity IS NULL"));
            // A modern shortage must not be re-imported from its matching movement.
            var stock=LanInventoryService.lockStock(c,"PRODUCT",502,501);
            StockLedgerService.shortage(c,stock,9,"Damage",null,501,"Employee",device,501,"MODERN");
            sql(c,"INSERT INTO inventory_movements(product_id,location_id,change_qty,reason,user_id,receive_id) VALUES(502,501,-1,'RECEIVING_STOCK_OVERRIDE',501,'MODERN')");
            SqlScriptRunner.runSql(c,migration);
            assertEquals(3,number(c,"SELECT count(*) FROM stock_ledger_entries"));
            c.rollback();
        }
    }

    @Test @Order(Integer.MAX_VALUE) void retriesDoNotDoubleDeductAndConcurrentExpenseCannotOverspendStock()throws Exception {
        String key=UUID.randomUUID().toString();JsonObject request=expense(502,7,"Donation");String hash=LanSecurity.sha256(request.toString());
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try(Connection first=open()){
            first.setAutoCommit(false);
            assertNull(LanApiServer.loadIdempotentResult(first,device,key,"stock-ledger.post.v1",hash));
            var result=StockLedgerService.post(first,request,device,501,"Employee",501);
            LanApiServer.completeIdempotency(first,device,key,result);
            Future<Boolean> other=pool.submit(()->{
                try(Connection c=open()){
                    c.setAutoCommit(false);sql(c,"SET LOCAL lock_timeout='5s'");
                    try{StockLedgerService.post(c,request,device,501,"Employee",501);c.commit();return false;}
                    catch(LanInventoryService.RuleViolation e){c.rollback();return true;}
                }
            });
            first.commit();assertTrue(other.get(10,TimeUnit.SECONDS));
            assertNotNull(LanApiServer.loadIdempotentResult(first,device,key,"stock-ledger.post.v1",hash));first.commit();
            assertEquals(3,number(first,"SELECT quantity_on_hand FROM inventory WHERE product_id=502"));
            assertEquals(1,number(first,"SELECT count(*) FROM stock_ledger_entries WHERE item_id=502"));
        }finally{pool.shutdownNow();}
    }

    private JsonObject expense(int item,int qty,String category)throws Exception {
        JsonObject b=new JsonObject();b.addProperty("itemType","PRODUCT");b.addProperty("itemId",item);b.addProperty("quantity",qty);
        b.addProperty("category",category);b.addProperty("description","Office / community");b.addProperty("reason","Approved company use");b.addProperty("signaturePngBase64",signature(true));return b;
    }
    private static String signature(boolean ink)throws Exception {
        BufferedImage image=new BufferedImage(400,160,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,400,160);
        if(ink){g.setColor(Color.BLACK);g.setStroke(new BasicStroke(3));g.drawPolyline(new int[]{30,70,90,120,160,200,260},new int[]{100,40,110,60,100,50,90},7);}g.dispose();
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return Base64.getEncoder().encodeToString(out.toByteArray());
    }
    private static JsonObject receiving(){
        return com.google.gson.JsonParser.parseString("""
                {"overrideReason":"Physical count","lines":[
                {"itemType":"PRODUCT","itemId":501,"countedStock":7,"quantity":5,"lossReason":"Unexplained Shortage","lossNotes":"Counted shelf and storage twice"},
                {"itemType":"PRODUCT","itemId":502,"countedStock":11,"quantity":5}]}
                """).getAsJsonObject();
    }
}
