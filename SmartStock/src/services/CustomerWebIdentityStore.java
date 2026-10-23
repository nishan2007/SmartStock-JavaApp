package services;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Strongly current encrypted synchronization. The gateway never receives plaintext hashes. */
final class CustomerWebIdentityStore {
    private final StorefrontConfig config;
    @FunctionalInterface interface Transport {JsonObject exchange(JsonObject body)throws Exception;}
    @FunctionalInterface interface Persistence {void save(String email,long revision,JsonObject identity)throws Exception;}
    private final Transport transport;
    private final Persistence persistence;
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build();
    CustomerWebIdentityStore(StorefrontConfig config){this.config=Objects.requireNonNull(config);transport=this::remoteExchange;persistence=this::databaseCache;}
    CustomerWebIdentityStore(StorefrontConfig config,Transport transport,Persistence persistence){this.config=Objects.requireNonNull(config);this.transport=transport;this.persistence=persistence;}
    record Record(long revision,JsonObject identity,String phoneKey) { }
    String key(String contact){return LanSecurity.hmacSha256(Base64.getEncoder().encodeToString(config.sessionKey()),"customer-web-key-v1:"+contact);}
    Record read(String email)throws Exception {
        var body=new JsonObject();body.addProperty("action","READ");body.addProperty("emailKey",key(email));var reply=exchange(body);
        long revision=reply.get("revision").getAsLong();JsonObject identity=revision==0?new JsonObject():open(reply.get("payload").getAsString(),key(email));
        if(revision>0&&identity.get("revision").getAsLong()!=revision)throw new IllegalStateException("Customer identity synchronization failed.");
        if(revision>0)cache(email,revision,identity);
        return new Record(revision,identity,reply.has("phoneKey")&&!reply.get("phoneKey").isJsonNull()?reply.get("phoneKey").getAsString():null);
    }
    long write(String email,Record old,JsonObject identity,boolean staffRelink)throws Exception {
        return write(email,old,identity,staffRelink,null,null,null);
    }
    long write(String email,Record old,JsonObject identity,boolean staffRelink,String previousEmail,Record previous,JsonObject revoked)throws Exception {
        long next=Math.addExact(old.revision(),1);identity.addProperty("revision",next);
        var body=new JsonObject();body.addProperty("action","WRITE");body.addProperty("emailKey",key(email));body.addProperty("revision",old.revision());body.addProperty("payload",seal(identity,key(email)));body.addProperty("staffRelink",staffRelink);
        body.addProperty("reviewPending","PENDING".equals(StorefrontService.text(identity,"linkReview")));
        body.addProperty("launchClaim","PENDING".equals(StorefrontService.text(identity,"linkReview"))&&identity.has("verified")&&identity.get("verified").getAsBoolean()&&(!old.identity().has("verified")||!old.identity().get("verified").getAsBoolean()));
        String phone=StorefrontService.text(identity,"phone");if(identity.has("verified")&&identity.get("verified").getAsBoolean()&&!phone.isBlank())body.addProperty("phoneKey",key(phone));else if(old.phoneKey()!=null)body.addProperty("phoneKey",old.phoneKey());
        if(previous!=null){revoked.addProperty("revision",previous.revision()+1);var transfer=new JsonObject();transfer.addProperty("emailKey",key(previousEmail));transfer.addProperty("revision",previous.revision());transfer.addProperty("payload",seal(revoked,key(previousEmail)));body.add("transfer",transfer);}
        long revision=exchange(body).get("revision").getAsLong();cache(email,revision,identity);return revision;
    }
    void rate(String email,String ip)throws Exception {
        var b=new JsonObject();b.addProperty("action","RATE");b.addProperty("emailKey",key(email));b.addProperty("ipKey",key("ip:"+ip));if(!exchange(b).get("allowed").getAsBoolean())throw new Limited();
    }
    JsonObject lookup(String phone,String email)throws Exception {var b=new JsonObject();b.addProperty("action","LOOKUP");b.addProperty("phone",phone);b.addProperty("email",email);return exchange(b);}
    JsonObject policy()throws Exception {var b=new JsonObject();b.addProperty("action","POLICY_READ");return exchange(b);}
    JsonObject policy(long revision,boolean enabled)throws Exception {var b=new JsonObject();b.addProperty("action","POLICY_WRITE");b.addProperty("revision",revision);b.addProperty("allowUnreviewedLinks",enabled);return exchange(b);}
    JsonArray reviews()throws Exception {
        var b=new JsonObject();b.addProperty("action","REVIEW_LIST");var result=new JsonArray();
        for(var item:exchange(b).getAsJsonArray("records")){var row=item.getAsJsonObject();var identity=open(row.get("payload").getAsString(),row.get("emailKey").getAsString());
            if(identity.get("revision").getAsLong()!=row.get("revision").getAsLong())throw new IllegalStateException("Review state changed. Refresh.");
            if(identity.has("verified")&&identity.get("verified").getAsBoolean()&&"PENDING".equals(StorefrontService.text(identity,"linkReview"))){var safe=new JsonObject();for(String field:List.of("email","name","phone","customerUuid","revision","linkRequestedAt"))if(identity.has(field))safe.add(field,identity.get(field));result.add(safe);}}
        return result;
    }
    private JsonObject exchange(JsonObject body)throws Exception {return transport.exchange(body);}
    private JsonObject remoteExchange(JsonObject body)throws Exception {
        int location=Objects.requireNonNull(data.DatabaseConfig.load().locationId());
        URI url=URI.create(config.origin()+"/shop/internal/customer-auth?storeId="+location);
        if(!"https".equals(url.getScheme()))throw new IllegalStateException("Customer authentication requires HTTPS.");
        var request=HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(12)).header("Content-Type","application/json").header("X-Storefront-Key",config.edgeKey()).header("X-Storefront-Instance",config.instanceId()).POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        var response=HTTP.send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()==409)throw new Conflict();if(response.statusCode()!=200)throw new IllegalStateException("The current customer identity state is unavailable. Please retry shortly.");
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    private void cache(String email,long revision,JsonObject identity)throws Exception {persistence.save(email,revision,identity);}
    private void databaseCache(String email,long revision,JsonObject identity)throws Exception {
        try(var c=data.DB.getConnection();var p=c.prepareStatement("INSERT INTO customer_auth.identities(email_key,revision,identity_data) VALUES(?,?,?::jsonb) ON CONFLICT(email_key) DO UPDATE SET revision=EXCLUDED.revision,identity_data=EXCLUDED.identity_data,synchronized_at=now() WHERE EXCLUDED.revision>=customer_auth.identities.revision")){p.setString(1,key(email));p.setLong(2,revision);p.setString(3,identity.toString());p.executeUpdate();}
    }
    String seal(JsonObject identity,String emailKey)throws Exception {
        byte[] nonce=new byte[12];new java.security.SecureRandom().nextBytes(nonce);var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(config.sessionKey(),"AES"),new GCMParameterSpec(128,nonce));c.updateAAD(("customer-web-identity-v1:"+emailKey).getBytes(StandardCharsets.UTF_8));byte[] encrypted=c.doFinal(identity.toString().getBytes(StandardCharsets.UTF_8)),result=new byte[nonce.length+encrypted.length];System.arraycopy(nonce,0,result,0,nonce.length);System.arraycopy(encrypted,0,result,nonce.length,encrypted.length);return Base64.getEncoder().encodeToString(result);
    }
    JsonObject open(String payload,String emailKey)throws Exception {
        byte[] bytes=Base64.getDecoder().decode(payload);if(bytes.length<29||bytes.length>32768)throw new IllegalArgumentException("Invalid identity envelope.");var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(config.sessionKey(),"AES"),new GCMParameterSpec(128,Arrays.copyOf(bytes,12)));c.updateAAD(("customer-web-identity-v1:"+emailKey).getBytes(StandardCharsets.UTF_8));return JsonParser.parseString(new String(c.doFinal(bytes,12,bytes.length-12),StandardCharsets.UTF_8)).getAsJsonObject();
    }
    static final class Conflict extends IllegalStateException {Conflict(){super("Your account changed or this phone is already linked. Refresh and retry, or contact your store.");}}
    static final class Limited extends IllegalStateException {Limited(){super("Too many account attempts. Try again in ten minutes.");}}
}
