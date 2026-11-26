package services;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="smartstock.test.adDraftDatabaseUrl",matches="jdbc:postgresql://127\\.0\\.0\\.1:56560/ad_draft_test")
class ProductAdDraftDatabaseTest {
    private Connection c;
    private Connection connect() throws SQLException {return DriverManager.getConnection(System.getProperty("smartstock.test.adDraftDatabaseUrl"),"postgres","");}
    @BeforeEach void setup() throws Exception {
        c=connect();assertEquals("ad_draft_test",c.getCatalog());
        try(var s=c.createStatement()) {
            s.execute("DROP SCHEMA public CASCADE;CREATE SCHEMA public;CREATE TABLE products(product_id bigint PRIMARY KEY,name text DEFAULT 'Product',is_active boolean DEFAULT TRUE,sku text);INSERT INTO products(product_id,sku) VALUES(1,'ONE'),(2,'TWO')");
        }
        for(int i=0;i<2;i++)SqlScriptRunner.runResource(c,"database/migrations/v1_after/20261115120000_product_ad_drafts.sql");
        SqlScriptRunner.runResource(c,"database/migrations/v1_after/20261115130000_product_ad_rejection_reason.sql");
        c.setAutoCommit(false);
    }
    @AfterEach void close() throws Exception {if(c!=null)c.close();}
    @Test void draftsSurviveReconnectAndDecisionsRejectStaleRegisters() throws Exception {
        assertEquals(1,ProductAdDraftStore.save(c,1,"ad_square",0,7,new byte[]{1}));c.commit();
        try(var other=connect()) {
            var draft=ProductAdDraftStore.load(other,1,"ad_square");assertEquals("pending",draft.decision());assertEquals(1,draft.revision());
            assertNull(ProductAdDraftStore.load(other,2,"ad_square"));
            assertNull(ProductAdDraftStore.load(other,1,"ad_story"));
        }
        ProductAdDraftStore.lockProduct(c,1);ProductAdDraftStore.decide(c,1,"ad_square",1,8,"rejected","Fix product text");c.commit();
        assertThrows(IllegalStateException.class,()->ProductAdDraftStore.save(c,1,"ad_square",1,9,new byte[]{2}));c.rollback();
        assertThrows(IllegalStateException.class,()->ProductAdDraftStore.decide(c,1,"ad_square",1,9,"approved"));c.rollback();
        assertEquals("Fix product text",ProductAdDraftStore.load(c,1,"ad_square").rejectionReason());
        assertEquals(3,ProductAdDraftStore.save(c,1,"ad_square",2,9,new byte[]{2}));c.commit();
        assertEquals("pending",ProductAdDraftStore.load(c,1,"ad_square").decision());
        assertNull(ProductAdDraftStore.load(c,1,"ad_square").rejectionReason());
        try(var p=c.prepareStatement("SELECT uploaded_by_user_id,reviewed_by_user_id FROM product_ad_drafts");var r=p.executeQuery()){r.next();assertEquals(9,r.getInt(1));assertNull(r.getObject(2));}
    }
    @Test void concurrentReplacementsCannotSilentlyOverwriteEachOther() throws Exception {
        ProductAdDraftStore.save(c,1,"ad_story",0,1,new byte[]{1});c.commit();
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            java.util.List<Future<Boolean>> results=new java.util.ArrayList<>();
            for(int user:new int[]{2,3})results.add(pool.submit(()->{try(var other=connect()) {
                other.setAutoCommit(false);start.await();
                try{ProductAdDraftStore.save(other,1,"ad_story",1,user,new byte[]{2});other.commit();return true;}
                catch(IllegalStateException e){other.rollback();return false;}
            }}));
            start.countDown();int winners=0;for(var result:results)if(result.get(10,TimeUnit.SECONDS))winners++;
            assertEquals(1,winners);assertEquals(2,ProductAdDraftStore.load(c,1,"ad_story").revision());
        }finally{pool.shutdownNow();}
    }
    @Test void reviewQueueSeparatesDecisionsSkipsInactiveProductsAndKeepsFormatOrder() throws Exception {
        for(String kind:java.util.List.of("ad_story","ad_landscape","ad_square","ad_portrait"))ProductAdDraftStore.save(c,1,kind,0,1,new byte[]{1});
        ProductAdDraftStore.save(c,2,"ad_square",0,1,new byte[]{1});
        ProductAdDraftStore.decide(c,1,"ad_portrait",1,2,"rejected","Fix product text");
        try(var s=c.createStatement()){s.execute("UPDATE products SET is_active=FALSE WHERE product_id=2");}
        assertEquals(java.util.List.of("ad_square","ad_story","ad_landscape"),ProductAdDraftStore.queue(c,"pending").stream().map(LanApiClient.AdReviewItem::kind).toList());
        assertEquals(1,ProductAdDraftStore.queue(c,"rejected").size());
        assertTrue(ProductAdDraftStore.queue(c,"approved").isEmpty());
        assertThrows(IllegalArgumentException.class,()->ProductAdDraftStore.queue(c,"anything"));
    }
}
