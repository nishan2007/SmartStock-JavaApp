package services;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="smartstock.test.permitDatabaseUrl",matches="jdbc:postgresql://127\\.0\\.0\\.1:55489/orealla_permit_test")
class StudioBadgePermitDatabaseTest {
    private final String photoId=UUID.randomUUID().toString();

    @Test void websiteSubmissionsSaveToTheDesktopHistoryAndReuseTheSameNumberOnRetry()throws Exception {
        int store=1_500_000+new Random().nextInt(100_000_000);
        UUID template=UUID.fromString("1025bcba-5b19-4e7d-a084-18f8840a3d38");
        String token=LanSecurity.randomToken();
        var elements=new com.google.gson.JsonArray();
        elements.add(StudioSubmissionQueue.object("type","TEXT","text","Legacy combined text"));
        elements.add(StudioSubmissionQueue.object("type","PHOTO","photoId",photoId,"label","Portrait","width",18,"height",24));
        var head=StudioSubmissionQueue.object("id",template.toString(),"name","Orealla Riders Permit","sha256","current",
                "content",StudioSubmissionQueue.object("name","Orealla Riders Permit","elements",elements).toString());
        try(var db=connect()){
            for(String sql:List.of("20261019140000_studio_badge_records.sql","20261019150000_studio_template_submissions.sql")){
                SqlScriptRunner.runSql(db,SqlScriptRunner.readResource("database/migrations/v1_after/"+sql));
            }
            try(var link=db.prepareStatement("INSERT INTO studio_template_submission_links(location_id,template_id,token_hash,updated_by) VALUES(?,?,?,10)")){
                link.setInt(1,store);link.setObject(2,template);link.setString(3,LanSecurity.sha256(token));link.executeUpdate();
            }
            var setup=StudioSubmissionQueue.object("action","SETUP","portal","OREALLA","token",token,"code","synthetic-code","confirm","synthetic-code");
            var reader=(StudioSubmissionQueue.TemplateReader)(location,id)->{
                assertEquals(store,location);assertEquals(template,id);return head;
            };
            var authorized=StudioSubmissionQueue.publicRequest(db,setup,reader);
            var form=authorized.getAsJsonObject("form");
            assertEquals(10,form.getAsJsonObject("fields").size());
            assertTrue(form.getAsJsonObject("photos").has(photoId));
            var submission=request();
            var body=StudioSubmissionQueue.object("action","SUBMIT","portal","OREALLA","token",token,
                    "session",authorized.get("session"),"schema",form.get("schema"),
                    "submissionId",submission.get("recordId"),"payload",submission.get("payload"));
            var portrait=body.getAsJsonObject("payload").getAsJsonObject("photos").getAsJsonObject(photoId);
            portrait.addProperty("zoom",1.4);portrait.addProperty("offsetX",.16);portrait.addProperty("background",0xffffff);
            portrait.add("adjustments",StudioSubmissionQueue.object("brightness",17,"contrast",-26,"saturation",31));
            var unauthenticated=body.deepCopy();unauthenticated.remove("session");
            assertThrows(SecurityException.class,()->StudioSubmissionQueue.publicRequest(db,unauthenticated,reader));
            assertThrows(SecurityException.class,()->StudioSubmissionQueue.authorizePermitRemoval(db,unauthenticated));
            assertDoesNotThrow(()->StudioSubmissionQueue.authorizePermitRemoval(db,body));
            var invalid=body.deepCopy();
            invalid.getAsJsonObject("payload").getAsJsonObject("photos").getAsJsonObject(photoId)
                    .getAsJsonObject("adjustments").addProperty("brightness",101);
            assertThrows(IllegalArgumentException.class,()->StudioSubmissionQueue.publicRequest(db,invalid,reader));
            assertEquals("OSV-0001",StudioSubmissionQueue.publicRequest(db,body,reader).get("permitNumber").getAsString());
            assertEquals("OSV-0001",StudioSubmissionQueue.publicRequest(db,body,reader).get("permitNumber").getAsString());
            assertEquals("OSV-0002",StudioBadgeRecords.execute(db,store,10,head,request()).get("permitNumber").getAsString());
            try(var check=db.prepareStatement("SELECT count(*) FROM studio_badge_print_records WHERE location_id=? AND template_id=?")){
                check.setInt(1,store);check.setObject(2,template);
                try(var rows=check.executeQuery()){assertTrue(rows.next());assertEquals(2,rows.getInt(1));}
            }
            try(var check=db.prepareStatement("SELECT payload->'photos'->? FROM studio_badge_print_records WHERE record_id=?")){
                check.setString(1,photoId);check.setObject(2,UUID.fromString(submission.get("recordId").getAsString()));
                try(var rows=check.executeQuery()){
                    assertTrue(rows.next());
                    assertEquals(portrait,com.google.gson.JsonParser.parseString(rows.getString(1)).getAsJsonObject());
                }
            }
        }
    }

    @Test void allocatesUniqueNumbersAcrossConnectionsAndRetriesWithoutCreatingAnotherRecord()throws Exception {
        try(var setup=connect()){
            SqlScriptRunner.runSql(setup,SqlScriptRunner.readResource("database/migrations/v1_after/20261019140000_studio_badge_records.sql"));
        }
        var elements=new com.google.gson.JsonArray();
        elements.add(StudioSubmissionQueue.object("type","PHOTO","photoId",photoId));
        var template=StudioSubmissionQueue.object("id",UUID.randomUUID().toString(),"content",
                StudioSubmissionQueue.object("name","Orealla Riders Permit","elements",elements).toString());
        var first=request();
        var second=request();
        var workers=Executors.newFixedThreadPool(2);
        var start=new CountDownLatch(1);
        try{
            var a=workers.submit(()->{start.await();return create(template,first);});
            var b=workers.submit(()->{start.await();return create(template,second);});
            start.countDown();
            String firstNumber=a.get(10,TimeUnit.SECONDS);
            assertEquals(Set.of("OSV-0001","OSV-0002"),Set.of(firstNumber,b.get(10,TimeUnit.SECONDS)));
            assertEquals(firstNumber,create(template,first));
            assertEquals("OSV-0003",create(template,request()));
            var changed=first.deepCopy();
            changed.getAsJsonObject("payload").getAsJsonObject("values").addProperty("name","Different person");
            assertThrows(IllegalArgumentException.class,()->create(template,changed));
            var differentTemplate=template.deepCopy();
            differentTemplate.addProperty("id",UUID.randomUUID().toString());
            assertEquals("OSV-0001",create(differentTemplate,request()));
        }finally{workers.shutdownNow();}
    }

    private JsonObject request()throws Exception {
        var values=new JsonObject();
        for(String key:List.of("name","village","eyeColor","height","sex","dateOfBirth","issueDate","expiryDate","classes","placeOfIssue"))
            values.addProperty(key,"Synthetic "+key);
        var bytes=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes);
        var photo=StudioSubmissionQueue.object("image",Base64.getEncoder().encodeToString(bytes.toByteArray()),
                "zoom",1,"offsetX",0,"offsetY",0,"turns",0);
        var payload=StudioSubmissionQueue.object("values",values,"photos",StudioSubmissionQueue.object(photoId,photo));
        return StudioSubmissionQueue.object("action","RECORD_CREATE","recordId",UUID.randomUUID().toString(),"payload",payload);
    }

    private static String create(JsonObject template,JsonObject request)throws Exception {
        try(var connection=connect()){
            return StudioBadgeRecords.execute(connection,1,10,template,request).get("permitNumber").getAsString();
        }
    }

    private static Connection connect()throws Exception {
        return DriverManager.getConnection(System.getProperty("smartstock.test.permitDatabaseUrl"),"postgres","");
    }
}
