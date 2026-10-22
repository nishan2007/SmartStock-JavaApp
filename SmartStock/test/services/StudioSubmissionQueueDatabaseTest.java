package services;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

/** Only this explicitly named disposable localhost database is allowed. */
@EnabledIfSystemProperty(named="smartstock.test.submissionDatabaseUrl",matches="jdbc:postgresql://127\\.0\\.0\\.1:55467/template_submission_test")
class StudioSubmissionQueueDatabaseTest {
    private Connection c;
    private final UUID template=UUID.randomUUID(),photo=UUID.randomUUID();
    private final String token=LanSecurity.randomToken();
    private JsonObject head;
    private Connection connection()throws SQLException{return DriverManager.getConnection(System.getProperty("smartstock.test.submissionDatabaseUrl"),"postgres","");}
    @BeforeEach void setupDatabase()throws Exception {
        c=connection();assertEquals("template_submission_test",c.getCatalog());
        try(var s=c.createStatement()){
            s.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
            s.execute("CREATE TABLE roles(role_id integer PRIMARY KEY,role_name text);CREATE TABLE users(user_id integer PRIMARY KEY,role_id integer,is_active boolean DEFAULT TRUE);CREATE TABLE permissions(permission_id serial PRIMARY KEY,permission_key text UNIQUE,permission_name text,description text,permission_group text,permission_subgroup text);CREATE TABLE role_permissions(role_id integer,permission_id integer,updated_at timestamptz,PRIMARY KEY(role_id,permission_id));INSERT INTO roles VALUES(1,'ADMIN'),(2,'STAFF'),(3,'REVIEWER'),(4,'RESETTER');INSERT INTO users(user_id,role_id) VALUES(1,1),(2,2),(3,3),(4,4)");
        }
        for(String name:new String[]{"20261019140000_studio_badge_records.sql","20261019150000_studio_template_submissions.sql","20261019150100_studio_submission_permissions.sql"}){
            String sql=SqlScriptRunner.readResource("database/migrations/v1_after/"+name);SqlScriptRunner.runSql(c,sql);SqlScriptRunner.runSql(c,sql);
        }
        try(var s=c.createStatement()){s.execute("INSERT INTO role_permissions SELECT 3,permission_id,now() FROM permissions WHERE permission_key='PROCESS_TEMPLATE_SUBMISSIONS';INSERT INTO role_permissions SELECT 4,permission_id,now() FROM permissions WHERE permission_key='RESET_TEMPLATE_QUEUE_CODE'");}
        JsonArray elements=new JsonArray();elements.add(StudioSubmissionQueue.object("type","TEXT","field","fullName","label","Full name"));elements.add(StudioSubmissionQueue.object("type","TEXT","field","idNumber","label","ID number"));elements.add(StudioSubmissionQueue.object("type","PHOTO","photoId",photo.toString(),"label","Portrait","width",18,"height",24));
        JsonObject design=StudioSubmissionQueue.object("name","Synthetic template","savePrintRecords",true,"elements",elements);
        head=StudioSubmissionQueue.object("id",template.toString(),"name","Synthetic template","sha256","revision-current","content",design.toString());
        try(var p=c.prepareStatement("INSERT INTO studio_template_submission_links(location_id,template_id,token_hash) VALUES(17,?,?)")){p.setObject(1,template);p.setString(2,LanSecurity.sha256(token));p.executeUpdate();}
    }
    @AfterEach void close()throws Exception{if(c!=null)c.close();}
    private JsonObject call(Connection connection,JsonObject body)throws Exception{
        body.addProperty("token",token);return StudioSubmissionQueue.publicRequest(connection,body,(store,id)->{assertEquals(17,store);assertEquals(template,id);return head;});
    }
    private JsonObject call(JsonObject body)throws Exception{return call(c,body);}
    private JsonObject setup()throws Exception{return call(StudioSubmissionQueue.object("action","SETUP","code","organization-code","confirm","organization-code"));}
    private JsonObject login(String code)throws Exception{return call(StudioSubmissionQueue.object("action","LOGIN","code",code));}
    private JsonObject payload()throws Exception{
        var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB);var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
        return StudioSubmissionQueue.object("values",StudioSubmissionQueue.object("fullName","Synthetic person","idNumber","TEST-123"),"photos",StudioSubmissionQueue.object(photo.toString(),StudioSubmissionQueue.object("image",Base64.getEncoder().encodeToString(bytes.toByteArray()))));
    }
    private JsonObject submit(JsonObject session,UUID id,JsonObject payload)throws Exception{return call(StudioSubmissionQueue.object("action","SUBMIT","session",session.get("session"),"schema",session.getAsJsonObject("form").get("schema"),"submissionId",id.toString(),"payload",payload));}
    private JsonObject staff(int actor,JsonObject body)throws Exception{return StudioSubmissionQueue.staff(c,17,actor,head,body);}
    private long count(String table)throws Exception{try(var p=c.prepareStatement("SELECT count(*) FROM "+table);var r=p.executeQuery()){r.next();return r.getLong(1);}}

    @Test void firstUseIsAtomicAndConcurrentVisitorsCannotReplaceTheCode()throws Exception{
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            java.util.List<Future<Boolean>> jobs=new ArrayList<>();
            for(String code:new String[]{"first-company-code","other-company-code"})jobs.add(pool.submit(()->{try(var other=connection()){start.await();try{call(other,StudioSubmissionQueue.object("action","SETUP","code",code,"confirm",code));return true;}catch(IllegalStateException expected){return false;}}}));
            start.countDown();int winners=0;for(var job:jobs)if(job.get(15,TimeUnit.SECONDS))winners++;assertEquals(1,winners);
            assertEquals(1,count("studio_template_submission_sessions"));
            try(var p=c.prepareStatement("SELECT code_hash,salt FROM studio_template_submission_links");var r=p.executeQuery()){r.next();assertNotEquals("first-company-code",r.getString(1));assertTrue(Base64.getDecoder().decode(r.getString(2)).length>=16);}
        }finally{pool.shutdownNow();}
    }
    @Test void codeResetRequiresSpecificPermissionInvalidatesSessionsAndKeepsEntries()throws Exception{
        var session=setup();submit(session,UUID.randomUUID(),payload());
        var reset=StudioSubmissionQueue.object("action","QUEUE_RESET");
        assertThrows(SecurityException.class,()->staff(2,reset));assertThrows(SecurityException.class,()->staff(3,reset));
        staff(4,reset);assertEquals(1,count("studio_template_submissions"));
        assertThrows(SecurityException.class,()->call(StudioSubmissionQueue.object("action","FORM","session",session.get("session"))));
        assertTrue(call(StudioSubmissionQueue.object("action","INFO")).get("setupRequired").getAsBoolean());
        var replacement=setup();assertNotEquals(session.get("session"),replacement.get("session"));
    }
    @Test void wrongCodesPersistLockoutAndExpiredLockoutAllowsCorrectLogin()throws Exception{
        setup();for(int i=0;i<5;i++)assertThrows(SecurityException.class,()->login("incorrect-code"));
        assertThrows(SecurityException.class,()->login("organization-code"));
        try(var s=c.createStatement()){s.execute("UPDATE studio_template_submission_links SET locked_until=now()-interval '1 second'");}
        assertNotNull(login("organization-code").get("session"));
    }
    @Test void submissionsAreIdempotentBoundToCurrentSchemaAndNotPubliclyReadable()throws Exception{
        var session=setup();UUID id=UUID.randomUUID();JsonObject data=payload();submit(session,id,data);submit(session,id,data);assertEquals(1,count("studio_template_submissions"));
        data.getAsJsonObject("values").addProperty("fullName","Changed synthetic person");assertThrows(IllegalStateException.class,()->submit(session,id,data));
        var design=JsonParser.parseString(head.get("content").getAsString()).getAsJsonObject();design.getAsJsonArray("elements").add(StudioSubmissionQueue.object("type","TEXT","field","department","label","Department"));head.addProperty("content",design.toString());
        assertThrows(IllegalStateException.class,()->submit(session,UUID.randomUUID(),data));
        assertThrows(IllegalArgumentException.class,()->call(StudioSubmissionQueue.object("action","LIST","session",session.get("session"))));
        var wrong=StudioSubmissionQueue.object("action","INFO","token",LanSecurity.randomToken());assertThrows(SecurityException.class,()->StudioSubmissionQueue.publicRequest(c,wrong,(s,t)->{fail("Invalid token must not read templates");return null;}));
    }
    @Test void claimsPreventDuplicatePrintsAndPrinterAcceptanceLinksDurableRecord()throws Exception{
        var session=setup();UUID id=UUID.randomUUID();submit(session,id,payload());
        var request=StudioSubmissionQueue.object("action","QUEUE_CLAIM","submissionId",id.toString());JsonObject claim=staff(3,request);
        assertThrows(IllegalStateException.class,()->staff(1,request));
        var begin=StudioSubmissionQueue.object("action","QUEUE_BEGIN","submissionId",id.toString(),"claimToken",claim.get("claimToken"),"schema",claim.getAsJsonObject("form").get("schema"),"recordId",UUID.randomUUID().toString(),"payload",payload());
        begin.add("templateRevision",claim.getAsJsonObject("form").get("templateRevision"));
        var stale=begin.deepCopy();stale.addProperty("templateRevision","previous-format");assertThrows(IllegalStateException.class,()->staff(3,stale));assertEquals(0,count("studio_badge_print_records"));
        staff(3,begin);assertEquals(1,count("studio_badge_print_records"));assertThrows(IllegalStateException.class,()->staff(3,begin));
        var release=begin.deepCopy();release.addProperty("action","QUEUE_RELEASE");assertThrows(IllegalStateException.class,()->staff(3,release));
        var sent=begin.deepCopy();sent.addProperty("action","QUEUE_SENT");staff(3,sent);staff(3,sent);
        assertEquals(0,staff(3,StudioSubmissionQueue.object("action","QUEUE_LIST")).getAsJsonArray("submissions").size());
        assertEquals(1,staff(1,StudioSubmissionQueue.object("action","QUEUE_LIST")).getAsJsonArray("submissions").size());
        var retry=StudioSubmissionQueue.object("action","QUEUE_RETRY","submissionId",id.toString());assertThrows(SecurityException.class,()->staff(3,retry));staff(1,retry);
        assertNotNull(staff(3,request).get("claimToken"));assertEquals(1,count("studio_badge_print_records"));
    }
    @Test void disabledTemplatesRevokedLinksAndInvalidPhotosAreRejected()throws Exception{
        var session=setup();JsonObject data=payload();data.getAsJsonObject("photos").getAsJsonObject(photo.toString()).addProperty("image","invalid!");assertThrows(IllegalArgumentException.class,()->submit(session,UUID.randomUUID(),data));
        var design=JsonParser.parseString(head.get("content").getAsString()).getAsJsonObject();design.addProperty("savePrintRecords",false);head.addProperty("content",design.toString());
        assertThrows(IllegalStateException.class,()->call(StudioSubmissionQueue.object("action","INFO")));
        design.addProperty("savePrintRecords",true);head.addProperty("content",design.toString());staff(1,StudioSubmissionQueue.object("action","QUEUE_REVOKE"));
        assertThrows(SecurityException.class,()->call(StudioSubmissionQueue.object("action","INFO")));
    }
    @Test void paginationAndRejectionRemainScopedToOneTemplateAndStore()throws Exception{
        String json=StudioSubmissionQueue.normalized(payload(),StudioSubmissionQueue.form(head)).toString();
        try(var p=c.prepareStatement("INSERT INTO studio_template_submissions(location_id,template_id,submission_id,schema_hash,payload,request_hash) SELECT 17,?,md5(g::text)::uuid,'fixture',?::jsonb,'fixture' FROM generate_series(1,102) g")){
            p.setObject(1,template);p.setString(2,json);p.executeUpdate();
        }
        var first=staff(3,StudioSubmissionQueue.object("action","QUEUE_LIST","offset",0)).getAsJsonArray("submissions");assertEquals(100,first.size());
        assertEquals(2,staff(3,StudioSubmissionQueue.object("action","QUEUE_LIST","offset",100)).getAsJsonArray("submissions").size());
        assertEquals(0,StudioSubmissionQueue.staff(c,18,1,head,StudioSubmissionQueue.object("action","QUEUE_LIST")).getAsJsonArray("submissions").size());
        String id=first.get(0).getAsJsonObject().get("id").getAsString();var claim=staff(3,StudioSubmissionQueue.object("action","QUEUE_CLAIM","submissionId",id));
        var reject=StudioSubmissionQueue.object("action","QUEUE_REJECT","submissionId",id,"claimToken",claim.get("claimToken"),"reason","Synthetic rejection");
        assertThrows(IllegalStateException.class,()->staff(1,reject));staff(3,reject);
        try(var p=c.prepareStatement("SELECT state,note,claimed_by FROM studio_template_submissions WHERE submission_id=?")){
            p.setObject(1,UUID.fromString(id));try(var r=p.executeQuery()){r.next();assertEquals("REJECTED",r.getString(1));assertEquals("Synthetic rejection",r.getString(2));assertEquals(3,r.getInt(3));}
        }
    }
    @Test void twoRegistersCannotClaimTheSameEntry()throws Exception{
        var session=setup();UUID id=UUID.randomUUID();submit(session,id,payload());
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            java.util.List<Future<Boolean>> jobs=new ArrayList<>();
            for(int actor:new int[]{1,3})jobs.add(pool.submit(()->{try(var other=connection()){start.await();try{StudioSubmissionQueue.staff(other,17,actor,head,StudioSubmissionQueue.object("action","QUEUE_CLAIM","submissionId",id.toString()));return true;}catch(IllegalStateException expected){return false;}}}));
            start.countDown();int winners=0;for(var job:jobs)if(job.get(10,TimeUnit.SECONDS))winners++;assertEquals(1,winners);
        }finally{pool.shutdownNow();}
    }
    @Test void linkManagersCannotBypassTheSeparateResetPermissionByRotatingOrRevoking()throws Exception{
        setup();String originalHash;
        try(var s=c.createStatement()){
            s.execute("INSERT INTO permissions(permission_key) VALUES('MANAGE_CUSTOM_ORDER_ITEMS');INSERT INTO role_permissions SELECT 2,permission_id,now() FROM permissions WHERE permission_key='MANAGE_CUSTOM_ORDER_ITEMS'");
            try(var r=s.executeQuery("SELECT code_hash FROM studio_template_submission_links")){r.next();originalHash=r.getString(1);}
        }
        StudioSubmissionQueue.Website website=()->new StudioSubmissionQueue.WebsiteLink("https://synthetic.example","synthetic-instance","synthetic-website-key");
        var rotate=StudioSubmissionQueue.object("action","QUEUE_LINK");
        var link=StudioSubmissionQueue.staff(c,17,2,head,rotate,website);
        assertEquals(link,StudioSubmissionQueue.staff(c,17,2,head,StudioSubmissionQueue.object("action","QUEUE_COPY_LINK"),website));
        assertThrows(SecurityException.class,()->staff(2,StudioSubmissionQueue.object("action","QUEUE_RESET")));
        staff(2,StudioSubmissionQueue.object("action","QUEUE_REVOKE"));
        link=StudioSubmissionQueue.staff(c,17,2,head,rotate,website);String url=link.get("url").getAsString(),newToken=url.substring(url.indexOf("&token=")+7);
        var info=StudioSubmissionQueue.publicRequest(c,StudioSubmissionQueue.object("action","INFO","token",newToken),(store,id)->head);
        assertFalse(info.get("setupRequired").getAsBoolean());
        try(var s=c.createStatement();var r=s.executeQuery("SELECT code_hash FROM studio_template_submission_links")){r.next();assertEquals(originalHash,r.getString(1));}
        staff(2,StudioSubmissionQueue.object("action","QUEUE_REVOKE"));staff(4,StudioSubmissionQueue.object("action","QUEUE_RESET"));
        assertThrows(SecurityException.class,()->StudioSubmissionQueue.publicRequest(c,StudioSubmissionQueue.object("action","INFO","token",newToken),(store,id)->head));
    }
}
