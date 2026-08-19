package services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/** Microsoft Graph implementation scoped to a dedicated business user's application folder. */
final class OneDriveImageCloudProvider implements ImageCloudProvider {
    private static final String GRAPH="https://graph.microsoft.com/v1.0";
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private volatile Token cachedToken;
    private volatile AppRoot cachedRoot;

    void reset(){cachedToken=null;cachedRoot=null;}

    @Override public String name(){return "ONEDRIVE";}
    @Override public boolean configured(){return OneDriveImageStorageConfig.load().configured();}

    @Override public RemoteObject upload(UUID id,String category,String sourcePath,String contentType,byte[] bytes)throws Exception{
        if(bytes==null||bytes.length==0)throw new IOException("The OneDrive upload is empty.");
        AppRoot root=appRoot(); String path=remotePath(id,category,sourcePath);
        HttpRequest request=authorized(graphDrive()+"/items/"+encode(root.itemId())+":/"+encodePath(path)+":/content")
                .timeout(Duration.ofSeconds(90)).header("Content-Type",contentType).PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        HttpResponse<String> response=send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),"upload");
        JsonObject row=JsonParser.parseString(response.body()).getAsJsonObject();
        long size=row.has("size")?row.get("size").getAsLong():bytes.length;
        if(size!=bytes.length)throw new IOException("OneDrive verified a different image byte size.");
        return new RemoteObject(root.driveId(),text(row,"id"),path,text(row,"eTag"),size);
    }

    @Override public byte[] download(UUID id,String category,String sourcePath,String remoteItemId,String remotePath)throws Exception{
        String endpoint=!blank(remoteItemId)?graphDrive()+"/items/"+encode(remoteItemId)+"/content"
                :graphDrive()+"/items/"+encode(appRoot().itemId())+":/"+encodePath(path(id,category,sourcePath,remotePath))+":/content";
        HttpResponse<byte[]> response=sendAllowNotFound(authorized(endpoint).timeout(Duration.ofSeconds(90)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray(),"download");
        if(response.statusCode()==404)return null;
        require(response.statusCode(),response.body()==null?"":new String(response.body(),StandardCharsets.UTF_8),"download");
        return response.body();
    }

    @Override public void delete(UUID id,String category,String sourcePath,String remoteItemId,String remotePath)throws Exception{
        String endpoint=!blank(remoteItemId)?graphDrive()+"/items/"+encode(remoteItemId)
                :graphDrive()+"/items/"+encode(appRoot().itemId())+":/"+encodePath(path(id,category,sourcePath,remotePath));
        HttpResponse<String> response=sendAllowNotFound(authorized(endpoint).timeout(Duration.ofSeconds(60)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),"delete");
        if(response.statusCode()!=404)require(response.statusCode(),response.body(),"delete");
    }

    @Override public ProbeResult probe()throws Exception{
        AppRoot root=appRoot(); UUID id=UUID.randomUUID(); byte[] bytes="smartstock-onedrive-probe".getBytes(StandardCharsets.UTF_8);
        RemoteObject object=upload(id,"PRODUCT","probe.txt","text/plain",bytes);
        try{
            byte[] read=download(id,"PRODUCT","probe.txt",object.itemId(),object.path());
            if(!MessageDigest.isEqual(bytes,read))throw new IOException("OneDrive probe bytes did not match.");
        }finally{delete(id,"PRODUCT","probe.txt",object.itemId(),object.path());}
        return new ProbeResult(true,"OneDrive application folder is ready in drive "+root.driveId()+".");
    }

    static String remotePath(UUID id,String category,String sourcePath){
        String folder=switch(category==null?"":category.toUpperCase(Locale.ROOT)){
            case "CUSTOM_ITEM"->"custom-items"; case "CUSTOM_VARIANT"->"custom-variants"; default->"products";};
        return folder+"/"+id+"."+extension(sourcePath);
    }

    private String path(UUID id,String category,String sourcePath,String remotePath){return blank(remotePath)?remotePath(id,category,sourcePath):remotePath;}
    private AppRoot appRoot()throws Exception{
        AppRoot root=cachedRoot;if(root!=null)return root;
        HttpResponse<String> response=send(authorized(graphDrive()+"/special/approot").GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),"resolve application folder");
        JsonObject row=JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject parent=row.getAsJsonObject("parentReference");
        root=new AppRoot(text(parent,"driveId"),text(row,"id"));cachedRoot=root;return root;
    }
    private String graphDrive(){return GRAPH+"/drives/"+encode(OneDriveImageStorageConfig.load().driveId());}
    private HttpRequest.Builder authorized(String url)throws Exception{return HttpRequest.newBuilder(URI.create(url)).header("Authorization","Bearer "+token());}
    private String token()throws Exception{
        Token token=cachedToken;if(token!=null&&token.expiresAt().isAfter(Instant.now().plusSeconds(60)))return token.value();
        OneDriveImageStorageConfig.Settings s=OneDriveImageStorageConfig.load();
        if(!s.configured())throw new IOException("OneDrive image storage is not configured on this server.");
        String assertion=assertion(s);String form="client_id="+form(s.clientId())+"&scope="+form("https://graph.microsoft.com/.default")
                +"&grant_type=client_credentials&client_assertion_type="+form("urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                +"&client_assertion="+form(assertion);
        HttpRequest request=HttpRequest.newBuilder(URI.create("https://login.microsoftonline.com/"+encode(s.tenantId())+"/oauth2/v2.0/token"))
                .timeout(Duration.ofSeconds(30)).header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form,StandardCharsets.UTF_8)).build();
        HttpResponse<String> response=HTTP.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        require(response.statusCode(),response.body(),"authentication");JsonObject json=JsonParser.parseString(response.body()).getAsJsonObject();
        token=new Token(text(json,"access_token"),Instant.now().plusSeconds(json.get("expires_in").getAsLong()));cachedToken=token;return token.value();
    }
    private static String assertion(OneDriveImageStorageConfig.Settings s)throws Exception{
        X509Certificate cert=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(
                new ByteArrayInputStream(pem(s.certificatePem(),"CERTIFICATE")));
        PrivateKey key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pem(s.privateKeyPem(),"PRIVATE KEY")));
        long now=Instant.now().getEpochSecond();String endpoint="https://login.microsoftonline.com/"+s.tenantId()+"/oauth2/v2.0/token";
        String header=b64("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"x5t\":\""+b64(MessageDigest.getInstance("SHA-1").digest(cert.getEncoded()))+"\"}");
        String body=b64("{\"aud\":\""+endpoint+"\",\"iss\":\""+s.clientId()+"\",\"sub\":\""+s.clientId()+"\",\"jti\":\""+UUID.randomUUID()+"\",\"nbf\":"+(now-10)+",\"exp\":"+(now+600)+"}");
        Signature signature=Signature.getInstance("SHA256withRSA");signature.initSign(key);signature.update((header+"."+body).getBytes(StandardCharsets.US_ASCII));
        return header+"."+body+"."+b64(signature.sign());
    }
    private static byte[] pem(String value,String label){return Base64.getMimeDecoder().decode(value.replace("-----BEGIN "+label+"-----","").replace("-----END "+label+"-----","").replaceAll("\\s",""));}
    private static <T> HttpResponse<T> send(HttpRequest request,HttpResponse.BodyHandler<T> handler,String operation)throws Exception{
        HttpResponse<T> response=sendAllowNotFound(request,handler,operation);require(response.statusCode(),String.valueOf(response.body()),operation);return response;}
    private static <T> HttpResponse<T> sendAllowNotFound(HttpRequest request,HttpResponse.BodyHandler<T> handler,String operation)throws Exception{
        HttpResponse<T> response=null;
        for(int attempt=0;attempt<3;attempt++){
            response=HTTP.send(request,handler);
            if(!isRetryableStatus(response.statusCode())||attempt==2)return response;
            long delay=response.headers().firstValue("Retry-After").flatMap(OneDriveImageCloudProvider::seconds).orElse(1L);
            Thread.sleep(Math.min(5L,Math.max(1L,delay))*1000L);
        }
        throw new IOException("OneDrive image "+operation+" did not return a response.");
    }
    static boolean isRetryableStatus(int status){return status==429||status==408||status>=500;}
    private static java.util.Optional<Long> seconds(String value){try{return java.util.Optional.of(Long.parseLong(value));}catch(Exception ex){return java.util.Optional.empty();}}
    private static void require(int status,String body,String operation)throws IOException{if(status<200||status>=300)throw new IOException("OneDrive image "+operation+" failed with HTTP "+status+": "+trim(body));}
    private static String text(JsonObject o,String key)throws IOException{if(o==null||!o.has(key)||o.get(key).isJsonNull()||o.get(key).getAsString().isBlank())throw new IOException("Microsoft Graph response omitted "+key+".");return o.get(key).getAsString();}
    private static String extension(String path){String name=path==null?"":path;int dot=name.lastIndexOf('.');String ext=dot<0?"img":name.substring(dot+1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");return ext.isBlank()||ext.length()>5?"img":ext;}
    private static String encode(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8).replace("+","%20");}
    private static String encodePath(String s){return java.util.Arrays.stream(s.split("/")).map(OneDriveImageCloudProvider::encode).reduce((a,b)->a+"/"+b).orElse("");}
    private static String form(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    private static String b64(String s){return b64(s.getBytes(StandardCharsets.UTF_8));}
    private static String b64(byte[] bytes){return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private static boolean blank(String s){return s==null||s.isBlank();}
    private static String trim(String s){if(s==null)return "";return s.length()>500?s.substring(0,500):s;}
    private record Token(String value,Instant expiresAt){}
    private record AppRoot(String driveId,String itemId){}
}
