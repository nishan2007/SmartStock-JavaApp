package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import java.security.MessageDigest;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CustomOrderHeldCartTest {
    @Test void validatesAttachmentManifestsAndChunkBoundaries()throws Exception{
        var draft=draft(new byte[0],UUID.randomUUID().toString());
        CustomOrderHeldCartService.validate("Test",draft);
        assertThrows(SQLException.class,()->CustomOrderHeldCartService.validate(" ",draft));
        draft.getAsJsonArray("lines").get(0).getAsJsonObject().getAsJsonArray("files").add(UUID.randomUUID().toString());
        assertThrows(SQLException.class,()->CustomOrderHeldCartService.validate("Test",draft));
        assertEquals(524288,CustomOrderHeldCartService.chunkSize(524289,0));
        assertEquals(1,CustomOrderHeldCartService.chunkSize(524289,1));
        assertThrows(SQLException.class,()->CustomOrderHeldCartService.chunkSize(524289,2));
        assertThrows(SQLException.class,()->CustomOrderHeldCartService.chunkSize(10,-1));
    }
    @Test void storeWideRoundTripAttachmentIntegrityAndSingleClaim()throws Exception{
        String admin=System.getProperty("pricing.test.admin","");assumeTrue(!admin.isBlank());
        assertEquals("jdbc:postgresql://127.0.0.1:55443/postgres",admin,"Only the disposable loopback cluster may be used.");
        String db="hold_test_"+UUID.randomUUID().toString().replace("-","");String url=admin.replace("/postgres","/"+db);
        try(Connection root=DriverManager.getConnection(admin,"pricing_test","")){
            sql(root,"CREATE DATABASE "+db);
            try(Connection c=DriverManager.getConnection(url,"pricing_test","")){
                SchemaContractService.installLocalBaseline(c);assertTrue(SchemaContractService.validateLocal(c).ready());
                sql(c,"INSERT INTO locations(location_id,name) VALUES(990,'Hold test'),(991,'Other store'); INSERT INTO users(user_id,username,full_name,role_id) VALUES(990,'hold-test','Test Cashier',1),(991,'other-cashier','Other Cashier',1)");
                c.setAutoCommit(false);
                String abandoned=UUID.randomUUID().toString();var abandonedCreate=body("CREATE",abandoned);abandonedCreate.addProperty("name","Abandoned draft");abandonedCreate.add("draft",draft(new byte[0],UUID.randomUUID().toString()));
                run(c,abandonedCreate,990,990,"register-a");c.commit();
                assertThrows(SQLException.class,()->run(c,body("ABANDON",abandoned),990,991,"register-b"));c.rollback();
                assertEquals("STAGING",run(c,body("ABANDON",abandoned),990,990,"register-a").get("status"));c.commit();
                assertEquals("STAGING",run(c,body("ABANDON",abandoned),990,990,"register-a").get("status"));c.commit();assertEquals(0,count(c,"custom_order_held_carts"));c.commit();
                byte[] content=new byte[524301];new Random(42).nextBytes(content);String file=UUID.randomUUID().toString(),id=UUID.randomUUID().toString();var payload=draft(content,file);
                var create=body("CREATE",id);create.addProperty("name","10 shirts");create.add("draft",payload);
                run(c,create,990,990,"register-a");c.commit();
                assertTrue(((List<?>)run(c,body("LIST",null),990,991,"register-b").get("holds")).isEmpty());c.commit();
                assertThrows(SQLException.class,()->run(c,body("FINISH",id),990,990,"register-a"));c.rollback();
                assertThrows(SQLException.class,()->run(c,create,990,991,"register-b"));c.rollback();
                for(int chunk=0;chunk<2;chunk++){
                    byte[] bytes=Arrays.copyOfRange(content,chunk*524288,Math.min(content.length,(chunk+1)*524288));
                    var upload=body("UPLOAD",id);upload.addProperty("fileId",file);upload.addProperty("chunk",chunk);upload.addProperty("bytes",Base64.getEncoder().encodeToString(bytes));
                    run(c,upload,990,990,"register-a");run(c,upload,990,990,"register-a");c.commit();
                    bytes[0]^=1;upload.addProperty("bytes",Base64.getEncoder().encodeToString(bytes));assertThrows(SQLException.class,()->run(c,upload,990,990,"register-a"));c.rollback();
                }
                run(c,body("FINISH",id),990,990,"register-a");c.commit();run(c,body("FINISH",id),990,990,"register-a");c.commit();
                assertEquals("HELD",run(c,create,990,990,"register-a").get("status"));c.commit();
                assertEquals("HELD",run(c,body("ABANDON",id),990,990,"register-a").get("status"));c.commit();
                assertEquals(1,((List<?>)run(c,body("LIST",null),990,991,"register-b").get("holds")).size());c.commit();
                assertThrows(SQLException.class,()->run(c,body("LOAD",id),991,991,"register-b"));c.rollback();
                assertEquals(payload,run(c,body("LOAD",id),990,991,"register-b").get("draft"));c.commit();
                for(int chunk=0;chunk<2;chunk++){
                    var download=body("DOWNLOAD",id);download.addProperty("fileId",file);download.addProperty("chunk",chunk);
                    byte[] bytes=Base64.getDecoder().decode((String)run(c,download,990,991,"register-b").get("bytes"));assertArrayEquals(Arrays.copyOfRange(content,chunk*524288,Math.min(content.length,(chunk+1)*524288)),bytes);c.commit();
                }
                run(c,body("RESUME",id),990,991,"register-b");
                ExecutorService pool=Executors.newSingleThreadExecutor();CountDownLatch started=new CountDownLatch(1);
                try{
                    var second=pool.submit(()->{try(Connection other=DriverManager.getConnection(url,"pricing_test","")){other.setAutoCommit(false);started.countDown();assertThrows(SQLException.class,()->run(other,body("RESUME",id),990,990,"register-c"));other.rollback();}catch(Exception e){throw new RuntimeException(e);}});
                    assertTrue(started.await(5,TimeUnit.SECONDS));c.commit();second.get(10,TimeUnit.SECONDS);
                }finally{pool.shutdownNow();}
                assertTrue(((List<?>)run(c,body("LIST",null),990,990,"register-a").get("holds")).isEmpty());c.commit();
                assertEquals(0,count(c,"custom_orders"));assertEquals(0,count(c,"custom_order_payments"));assertEquals(0,count(c,"custom_order_held_files"));c.commit();
            }finally{sql(root,"SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='"+db+"' AND pid<>pg_backend_pid()");sql(root,"DROP DATABASE "+db);}
        }
    }
    static JsonObject draft(byte[] bytes,String file)throws Exception{
        var result=JsonParser.parseString("{\"customer\":{\"name\":\"Test\"},\"lines\":[{\"request\":{\"itemName\":\"Shirt\"},\"files\":[]}],\"attachments\":[]}").getAsJsonObject();
        var a=new JsonObject();a.addProperty("id",file);a.addProperty("name","design.pdf");a.addProperty("size",bytes.length);a.addProperty("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));result.getAsJsonArray("attachments").add(a);result.getAsJsonArray("lines").get(0).getAsJsonObject().getAsJsonArray("files").add(file);return result;
    }
    static JsonObject body(String action,String id){var b=new JsonObject();b.addProperty("action",action);if(id!=null)b.addProperty("id",id);return b;}
    static Map<String,Object> run(Connection c,JsonObject b,int location,int user,String device)throws Exception{return CustomOrderHeldCartService.execute(c,b,location,user,device);}
    static void sql(Connection c,String sql)throws SQLException{try(var s=c.createStatement()){s.execute(sql);}}
    static int count(Connection c,String table)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM "+table)){r.next();return r.getInt(1);}}
}
