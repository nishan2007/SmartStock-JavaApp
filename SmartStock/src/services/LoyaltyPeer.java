package services;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;

/** Explicit server trust. Peer keys never travel through the register API or settings UI. */
final class LoyaltyPeer {
    private LoyaltyPeer() { }
    static JsonObject call(int authority,int origin,String action,JsonObject body)throws Exception {
        String prefix="SMARTSTOCK_LOYALTY_STORE_"+authority;
        String url=System.getenv(prefix+"_URL"),key=System.getenv(prefix+"_KEY"),pin=System.getenv(prefix+"_SHA256");
        if(url==null||key==null||key.length()<32)throw new IllegalStateException("Loyalty authority connectivity is not configured on this store server.");
        URI base=URI.create(url);
        if(!"https".equalsIgnoreCase(base.getScheme())||base.getHost()==null||base.getUserInfo()!=null||base.getQuery()!=null||base.getFragment()!=null)
            throw new IllegalStateException("Loyalty peer URLs must be authenticated HTTPS server origins.");
        var payload=new JsonObject();payload.addProperty("action",action);payload.addProperty("authorityLocationId",authority);payload.add("body",body);
        String content=payload.toString(),time=Long.toString(Instant.now().getEpochSecond());
        String signature=LanSecurity.hmacSha256(key,origin+"\n"+time+"\n"+content);
        var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER);
        if(pin!=null&&!pin.isBlank()) {
            String expected=pin.replace(":","").toLowerCase(java.util.Locale.ROOT);
            if(!expected.matches("[0-9a-f]{64}"))throw new IllegalStateException("Invalid loyalty server certificate fingerprint.");
            TrustManager[] trust={new X509TrustManager(){
                public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
                public void checkClientTrusted(X509Certificate[] chain,String auth)throws java.security.cert.CertificateException{throw new java.security.cert.CertificateException("Client trust is not supported.");}
                public void checkServerTrusted(X509Certificate[] chain,String auth)throws java.security.cert.CertificateException{
                    try{if(chain==null||chain.length==0)throw new java.security.cert.CertificateException("Missing server certificate.");chain[0].checkValidity();String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()));if(!expected.equals(actual))throw new java.security.cert.CertificateException("Loyalty server certificate does not match the configured pin.");}catch(java.security.cert.CertificateException e){throw e;}catch(Exception e){throw new java.security.cert.CertificateException(e);}
                }
            }};
            SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(null,trust,null);client.sslContext(ssl);
        }
        var request=HttpRequest.newBuilder(base.resolve("/v1/loyalty/peer")).timeout(Duration.ofSeconds(8))
                .header("Content-Type","application/json").header("X-Loyalty-Origin",Integer.toString(origin))
                .header("X-Loyalty-Time",time).header("X-Loyalty-Signature",signature)
                .POST(HttpRequest.BodyPublishers.ofString(content)).build();
        var response=client.build().send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)throw new IllegalStateException("The loyalty authority rejected the request (HTTP "+response.statusCode()+").");
        var envelope=JsonParser.parseString(response.body()).getAsJsonObject();
        return envelope.has("data")?envelope.getAsJsonObject("data"):envelope;
    }
    static boolean verify(int origin,String time,String signature,String content) {
        String key=System.getenv("SMARTSTOCK_LOYALTY_PEER_"+origin+"_KEY");
        return verify(key,origin,time,signature,content,Instant.now().getEpochSecond());
    }
    static boolean verify(String key,int origin,String time,String signature,String content,long now) {
        try {
            if(origin<1||key==null||key.length()<32||signature==null||time==null||Math.abs(Math.subtractExact(now,Long.parseLong(time)))>120)return false;
            String expected=LanSecurity.hmacSha256(key,origin+"\n"+time+"\n"+content);
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),signature.getBytes(StandardCharsets.UTF_8));
        }catch(RuntimeException e){return false;}
    }
}
