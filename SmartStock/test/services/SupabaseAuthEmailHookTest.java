package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SupabaseAuthEmailHookTest {
    @TempDir Path dir;
    private final byte[] key=new byte[32];
    private String secret(){return "v1,whsec_"+Base64.getEncoder().encodeToString(key);}
    private String sign(String id,String timestamp,byte[] body)throws Exception{
        Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));
        m.update((id+"."+timestamp+".").getBytes(StandardCharsets.UTF_8));return "v1,"+Base64.getEncoder().encodeToString(m.doFinal(body));
    }
    @Test void signatureRequiresExactBodyAndFreshTimestamp()throws Exception{
        byte[] body="{\"example\":true}".getBytes(StandardCharsets.UTF_8);Instant now=Instant.ofEpochSecond(1800000000);
        String timestamp="1800000000",signature=sign("event_1",timestamp,body);
        assertDoesNotThrow(()->SupabaseAuthEmailHook.verify(secret(),"event_1",timestamp,signature,body,now));
        assertThrows(SecurityException.class,()->SupabaseAuthEmailHook.verify(secret(),"event_2",timestamp,signature,body,now));
        assertThrows(SecurityException.class,()->SupabaseAuthEmailHook.verify(secret(),"event_1",timestamp,signature,"{}".getBytes(),now));
        assertThrows(SecurityException.class,()->SupabaseAuthEmailHook.verify(secret(),"event_1",timestamp,signature,body,now.plusSeconds(301)));
        assertThrows(SecurityException.class,()->SupabaseAuthEmailHook.verify(secret(),"event_1",timestamp,null,body,now));
    }
    private JsonObject event(String action){
        return JsonParser.parseString("{\"user\":{\"id\":\"8484b834-f29e-4af2-bf42-80644d154f76\",\"email\":\"applicant@example.com\",\"new_email\":\"new@example.com\"},\"email_data\":{\"email_action_type\":\""+action+"\",\"token\":\"123456\",\"token_new\":\"654321\",\"token_hash\":\"aaaaaaaaaaaaaaaaaaaaaaaa\",\"token_hash_new\":\"bbbbbbbbbbbbbbbbbbbbbbbb\",\"redirect_to\":\"https://careers.example.com/register\"}}").getAsJsonObject();
    }
    private List<GmailOAuthService.GmailMessage> messages(JsonObject e){return SupabaseAuthEmailHook.messages(e,new SupabaseAuthEmailHook.Sender("sender@gmail.com","Company <Name>"),"https://careers.example.com/register","https://example.supabase.co");}
    @Test void portalGetsCodeWithoutConsumingLinkOrBcc(){
        var mail=messages(event("signup")).get(0);
        assertTrue(mail.bodyText().contains("123456"));assertFalse(mail.bodyHtml().contains("/auth/v1/verify"));
        assertEquals("",mail.bccEmail());assertTrue(mail.bodyHtml().contains("&lt;Name&gt;"));
        assertTrue(messages(event("recovery")).get(0).subject().contains("Reset"));
    }
    @Test void secureEmailChangeMapsBothCodesToCorrectRecipients(){
        var mails=messages(event("email_change"));assertEquals(2,mails.size());
        assertEquals("applicant@example.com",mails.get(0).toEmail());assertTrue(mails.get(0).bodyText().contains("123456"));
        assertTrue(mails.get(0).bodyText().contains("bbbbbbbbbbbbbbbbbbbbbbbb"));
        assertEquals("new@example.com",mails.get(1).toEmail());assertTrue(mails.get(1).bodyText().contains("654321"));
        assertTrue(mails.get(1).bodyText().contains("aaaaaaaaaaaaaaaaaaaaaaaa"));
    }
    @Test void retriesAndRestartDoNotResendConfirmedDelivery()throws Exception{
        AtomicInteger calls=new AtomicInteger();byte[] payload="signed payload".getBytes();
        try(var hook=new SupabaseAuthEmailHook(dir)){
            hook.deliverOnce("event_1",payload,messages(event("signup")),m->{calls.incrementAndGet();return null;});
            hook.deliverOnce("event_1",payload,messages(event("signup")),m->{calls.incrementAndGet();return null;});
            assertThrows(SecurityException.class,()->hook.deliverOnce("event_1","changed".getBytes(),messages(event("signup")),m->null));
        }
        try(var hook=new SupabaseAuthEmailHook(dir)){hook.deliverOnce("event_1",payload,messages(event("signup")),m->{calls.incrementAndGet();return null;});}
        assertEquals(1,calls.get());
    }
    @Test void failedDeliveryCanRetry()throws Exception{
        try(var hook=new SupabaseAuthEmailHook(dir)){
            assertThrows(java.io.IOException.class,()->hook.deliverOnce("event_1",new byte[0],messages(event("signup")),m->{throw new java.io.IOException();}));
            AtomicInteger sent=new AtomicInteger();hook.deliverOnce("event_1",new byte[0],messages(event("signup")),m->{sent.incrementAndGet();return null;});assertEquals(1,sent.get());
        }
    }
}
