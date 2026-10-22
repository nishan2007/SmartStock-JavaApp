package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class StudioPhotoQueueTest {
    private byte[] transparentCutout()throws Exception{var out=new java.io.ByteArrayOutputStream();var image=new java.awt.image.BufferedImage(100,150,java.awt.image.BufferedImage.TYPE_INT_ARGB);image.setRGB(50,70,0xff112233);javax.imageio.ImageIO.write(image,"png",out);return out.toByteArray();}
    @Test void preparedBestCutoutIsSharedButOriginalIsPreservedAndBothExpire()throws Exception{
        Time time=new Time();var queue=new StudioPhotoQueue(root,time);String token=queue.issue(1,7).get("token").getAsString(),id=UUID.randomUUID().toString();byte[] original=photo(),cutout=transparentCutout();queue.upload(1,token,id,"Person",original);
        assertTrue(queue.prepareNext(input->{assertArrayEquals(original,input);return cutout;}));
        JsonObject processed=queue.execute(1,request("GET",id));assertTrue(processed.get("usingCutout").getAsBoolean());assertArrayEquals(cutout,Base64.getDecoder().decode(processed.get("bytesBase64").getAsString()));
        JsonObject raw=request("GET",id);raw.addProperty("original",true);assertArrayEquals(original,Base64.getDecoder().decode(queue.execute(1,raw).get("bytesBase64").getAsString()));
        queue.execute(1,request("PRINTED",id));time.advance(Duration.ofHours(24));queue.cleanup();assertFalse(Files.exists(root.resolve("store-1/"+id+".photo")));assertFalse(Files.exists(root.resolve("store-1/"+id+".cutout.png")));
    }
    @Test void deletionDuringInferenceCannotResurrectAPhotoAndBusyWorkRetries()throws Exception{
        Time time=new Time();var queue=new StudioPhotoQueue(root,time);String token=queue.issue(1,7).get("token").getAsString(),id=UUID.randomUUID().toString();queue.upload(1,token,id,"Person",photo());
        queue.prepareNext(input->{throw new StudioBackgroundService.BusyException();});assertEquals("pending",queue.execute(1,request("STATUS",id)).get("backgroundState").getAsString());
        queue.prepareNext(input->{queue.execute(1,request("DELETE",id));return transparentCutout();});assertEquals(0,queue.execute(1,request("LIST",null)).getAsJsonArray("photos").size());assertFalse(Files.exists(root.resolve("store-1/"+id+".cutout.png")));
    }
    @Test void failedPreparationKeepsOriginalAndCanBeExplicitlyRetried()throws Exception{
        Time time=new Time();var queue=new StudioPhotoQueue(root,time);String token=queue.issue(1,7).get("token").getAsString(),id=UUID.randomUUID().toString();byte[] original=photo();queue.upload(1,token,id,"Person",original);
        queue.prepareNext(input->{throw new java.io.IOException("simulated inference failure");});assertEquals("failed",queue.execute(1,request("STATUS",id)).get("backgroundState").getAsString());assertArrayEquals(original,Base64.getDecoder().decode(queue.execute(1,request("GET",id)).get("bytesBase64").getAsString()));
        assertFalse(queue.prepareNext(input->{throw new AssertionError("Failed job should wait for retry");}));queue.execute(1,request("PREPARE",id));assertTrue(queue.prepareNext(input->transparentCutout()));
        assertEquals("ready",queue.execute(1,request("STATUS",id)).get("backgroundState").getAsString());
    }
    @Test void uncheckedUploadKeepsOriginalUntilExplicitPreparation()throws Exception{
        var queue=new StudioPhotoQueue(root,new Time());String token=queue.issue(1,7).get("token").getAsString(),id=UUID.randomUUID().toString();byte[] original=photo();
        queue.upload(1,token,id,"Original",original,false);
        assertEquals("disabled",queue.execute(1,request("STATUS",id)).get("backgroundState").getAsString());
        assertFalse(queue.prepareNext(input->{throw new AssertionError("Unchecked photo must not be processed");}));
        assertArrayEquals(original,Base64.getDecoder().decode(queue.execute(1,request("GET",id)).get("bytesBase64").getAsString()));
        queue.execute(1,request("PREPARE",id));assertTrue(queue.prepareNext(input->transparentCutout()));
        assertTrue(queue.execute(1,request("GET",id)).get("usingCutout").getAsBoolean());
    }
    @TempDir Path root;
    static class Time extends Clock {
        Instant now=Instant.parse("2026-10-17T12:00:00Z");
        public Instant instant(){return now;}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}
        void advance(Duration value){now=now.plus(value);}
    }
    byte[] photo()throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(100,150,BufferedImage.TYPE_INT_RGB),"jpg",out);return out.toByteArray();}
    JsonObject request(String action,String id){var request=new JsonObject();request.addProperty("action",action);if(id!=null)request.addProperty("id",id);return request;}
    @Test void multipleUploadsSurviveRestartAreSharedAcrossSessionsAndStayInTheirStore()throws Exception{
        Time time=new Time();var queue=new StudioPhotoQueue(root,time);String token=queue.issue(1,7).get("token").getAsString();byte[] bytes=photo();
        String first=UUID.randomUUID().toString(),second=UUID.randomUUID().toString();
        queue.upload(1,token,first,"Person one",bytes);queue.upload(1,token,second,"Person two",bytes);
        var restarted=new StudioPhotoQueue(root,time);assertEquals(2,restarted.execute(1,request("LIST",null)).getAsJsonArray("photos").size());
        assertArrayEquals(bytes,Base64.getDecoder().decode(restarted.execute(1,request("GET",first)).get("bytesBase64").getAsString()));
        assertEquals(0,restarted.execute(2,request("LIST",null)).getAsJsonArray("photos").size());
        assertThrows(IllegalArgumentException.class,()->restarted.execute(2,request("GET",first)));
        assertThrows(SecurityException.class,()->queue.upload(2,token,UUID.randomUUID().toString(),"Wrong store",bytes));
    }
    @Test void unprintedPhotosRemainAndPrintConfirmationSetsAnImmutableTwentyFourHourDeadline()throws Exception{
        Time time=new Time();var queue=new StudioPhotoQueue(root,time);String token=queue.issue(1,7).get("token").getAsString(),id=UUID.randomUUID().toString();queue.upload(1,token,id,"Person",photo());
        time.advance(Duration.ofDays(3));assertEquals(1,queue.execute(1,request("LIST",null)).getAsJsonArray("photos").size());
        long start=time.millis(),deadline=start+Duration.ofHours(24).toMillis();
        assertEquals(deadline,queue.execute(1,request("PRINTED",id)).get("deleteAfter").getAsLong());
        time.advance(Duration.ofHours(23));assertEquals(deadline,queue.execute(1,request("PRINTED",id)).get("deleteAfter").getAsLong());
        assertEquals(1,queue.execute(1,request("LIST",null)).getAsJsonArray("photos").size());
        time.advance(Duration.ofHours(1));var restarted=new StudioPhotoQueue(root,time);restarted.cleanup();
        assertEquals(0,restarted.execute(1,request("LIST",null)).getAsJsonArray("photos").size());assertFalse(Files.exists(root.resolve("store-1").resolve(id+".photo")));assertFalse(Files.exists(root.resolve("store-1").resolve(id+".json")));
        assertThrows(IllegalArgumentException.class,()->restarted.execute(1,request("GET",id)));
    }
    @Test void uploadRetriesDoNotDuplicateOrChangePhotosAndDeletedUploadsCannotReappear()throws Exception{
        Time time=new Time();var queue=new StudioPhotoQueue(root,time);String token=queue.issue(1,7).get("token").getAsString(),id=UUID.randomUUID().toString();byte[] bytes=photo();
        queue.upload(1,token,id,"Person",bytes);queue.upload(1,token,id,"Person",bytes);assertEquals(1,queue.execute(1,request("LIST",null)).getAsJsonArray("photos").size());
        assertThrows(IllegalArgumentException.class,()->queue.upload(1,token,id,"Changed",bytes));queue.execute(1,request("DELETE",id));assertThrows(IllegalArgumentException.class,()->queue.upload(1,token,id,"Person",bytes));
        assertFalse(Files.exists(root.resolve("store-1").resolve(id+".photo")));
    }
    @Test void expiredInvalidAndOversizedUploadsAreRejectedAndInterruptedFilesAreRemoved()throws Exception{
        Time time=new Time();var queue=new StudioPhotoQueue(root,time);String token=queue.issue(1,7).get("token").getAsString(),id=UUID.randomUUID().toString();byte[] bytes=photo();
        assertThrows(SecurityException.class,()->queue.upload(1,"invalid",id,"",bytes));assertThrows(IllegalArgumentException.class,()->queue.upload(1,token,id,"",new byte[2*1024*1024+1]));assertThrows(IllegalArgumentException.class,()->queue.upload(1,token,"../outside","",bytes));
        assertThrows(IllegalArgumentException.class,()->queue.upload(1,token,id,"",new byte[]{1,2,3}));
        Files.write(root.resolve("store-1").resolve(id+".photo"),bytes);Files.write(root.resolve("store-1/.pending-interrupted.tmp"),bytes);queue.cleanup();assertFalse(Files.exists(root.resolve("store-1").resolve(id+".photo")));assertFalse(Files.exists(root.resolve("store-1/.pending-interrupted.tmp")));
        time.advance(Duration.ofHours(12));assertThrows(SecurityException.class,()->queue.upload(1,token,id,"",bytes));
    }
    @Test void pairedPhoneSurvivesMonthsAndRestartUntilRevokedWithoutExposingTokens()throws Exception{
        Time time=new Time();var queue=new StudioPhotoQueue(root,time);var grant=queue.pair(1,7,"Counter phone");String token=grant.get("token").getAsString(),grantId=grant.get("id").getAsString();
        assertEquals(0,grant.get("expiresAt").getAsLong());time.advance(Duration.ofDays(180));var restarted=new StudioPhotoQueue(root,time);restarted.cleanup();
        var phones=restarted.execute(1,request("PHONES",null)).getAsJsonArray("phones");assertEquals(1,phones.size());assertFalse(phones.toString().contains(token));assertEquals("Counter phone",phones.get(0).getAsJsonObject().get("name").getAsString());
        assertThrows(IllegalArgumentException.class,()->restarted.execute(2,request("REVOKE_PHONE",grantId)));
        String photoId=UUID.randomUUID().toString();restarted.upload(1,token,photoId,"Person",photo());restarted.execute(1,request("REVOKE_PHONE",grantId));
        assertThrows(SecurityException.class,()->restarted.upload(1,token,UUID.randomUUID().toString(),"Person",photo()));
        assertEquals(1,restarted.execute(1,request("LIST",null)).getAsJsonArray("photos").size());assertEquals(0,restarted.execute(1,request("PHONES",null)).getAsJsonArray("phones").size());
    }
    @Test void pairedPhoneLimitAndNameValidationAreStoreScoped()throws Exception{
        var queue=new StudioPhotoQueue(root,new Time());assertThrows(IllegalArgumentException.class,()->queue.pair(1,7,""));assertThrows(IllegalArgumentException.class,()->queue.pair(1,7,"x".repeat(81)));
        for(int i=0;i<30;i++)queue.pair(1,7,"Phone "+i);
        assertThrows(IllegalStateException.class,()->queue.pair(1,7,"extra"));assertNotNull(queue.pair(2,7,"Other store"));
    }
}
