package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class StudioSubmissionQueueTest {
    @Test void linksCanBeCopiedWithoutStoringTheirRawBearerToken()throws Exception {
        UUID template=UUID.randomUUID(),seed=UUID.randomUUID();String key="synthetic-website-key";
        String token=StudioSubmissionQueue.linkToken(key,1,template,seed);
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
        assertEquals(token,StudioSubmissionQueue.linkToken(key,1,template,seed));
        assertNotEquals(token,StudioSubmissionQueue.linkToken(key,2,template,seed));
        assertNotEquals(token,StudioSubmissionQueue.linkToken(key,1,template,UUID.randomUUID()));
        for(String table:new String[]{"studio_template_submission_links","studio_template_submission_sessions","studio_template_submissions"})assertFalse(ReferenceDataSyncService.cloudMirrorTablesForApi().contains(table));
    }
    @Test void schemaIncludesCustomBindingsAndStablePhotoSlotsWithoutPersonalData()throws Exception {
        JsonArray elements=new JsonArray();elements.add(StudioSubmissionQueue.object("type","TEXT","field","customNumber","label","Custom number","text","Must not be published"));
        UUID slot=UUID.randomUUID();elements.add(StudioSubmissionQueue.object("type","PHOTO","photoId",slot.toString(),"label","Photo","width",18,"height",24));
        var head=StudioSubmissionQueue.object("sha256","current","content",StudioSubmissionQueue.object("name","Test","savePrintRecords",true,"elements",elements).toString());
        var schema=StudioSubmissionQueue.form(head);assertEquals(Set.of("customNumber"),schema.getAsJsonObject("fields").keySet());assertEquals(Set.of(slot.toString()),schema.getAsJsonObject("photos").keySet());
        assertFalse(schema.toString().contains("Must not be published"));assertEquals("current",schema.get("templateRevision").getAsString());
        assertEquals(64,schema.get("schema").getAsString().length());
    }
    @Test void validatesRequiredTextEveryPhotoFramingAndExtraFields()throws Exception{
        String first=UUID.randomUUID().toString(),second=UUID.randomUUID().toString();
        var fields=StudioSubmissionQueue.object("customNumber","Custom number");var slots=StudioSubmissionQueue.object(first,new JsonObject(),second,new JsonObject());var schema=StudioSubmissionQueue.object("fields",fields,"photos",slots);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes);String image=Base64.getEncoder().encodeToString(bytes.toByteArray());
        var photos=StudioSubmissionQueue.object(first,StudioSubmissionQueue.object("image",image),second,StudioSubmissionQueue.object("image",image));
        var payload=StudioSubmissionQueue.object("values",StudioSubmissionQueue.object("customNumber"," Synthetic "),"photos",photos);
        var normalized=StudioSubmissionQueue.normalized(payload,schema);assertEquals("Synthetic",normalized.getAsJsonObject("values").get("customNumber").getAsString());
        assertEquals(2,normalized.getAsJsonObject("photos").size());
        photos.getAsJsonObject(first).addProperty("zoom",5);payload.add("photos",photos);assertThrows(IllegalArgumentException.class,()->StudioSubmissionQueue.normalized(payload,schema));
        photos.remove(first);payload.add("photos",photos);assertThrows(IllegalStateException.class,()->StudioSubmissionQueue.normalized(payload,schema));
        payload.addProperty("templateArchive","forbidden");assertThrows(IllegalArgumentException.class,()->StudioSubmissionQueue.normalized(payload,schema));
    }
    @Test void codeHashIsSaltedAndCodePolicyIsBounded()throws Exception{
        String a=Base64.getEncoder().encodeToString(new byte[24]),b=Base64.getEncoder().encodeToString(new byte[25]);
        assertEquals(StudioSubmissionQueue.password("synthetic-code",a),StudioSubmissionQueue.password("synthetic-code",a));assertNotEquals(StudioSubmissionQueue.password("synthetic-code",a),StudioSubmissionQueue.password("synthetic-code",b));
        assertThrows(IllegalArgumentException.class,()->StudioSubmissionQueue.validateCode("short"));assertThrows(IllegalArgumentException.class,()->StudioSubmissionQueue.validateCode("x".repeat(129)));
    }
}
