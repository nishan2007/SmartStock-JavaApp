package services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

/** Server-only connection to SmartDrive; bridge credentials never reach staff clients. */
final class SmartDriveLibraryGateway {
    private static final int MAX_CATALOG_BYTES=16*1024*1024;
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private SmartDriveLibraryGateway() { }
    static boolean configured() {
        String origin=System.getenv("SMARTDRIVE_STUDIO_ORIGIN");
        return origin!=null&&!origin.isBlank();
    }

    static URI endpoint(String origin) {
        URI value=URI.create(origin);
        if(!"https".equalsIgnoreCase(value.getScheme()) || value.getHost()==null || value.getUserInfo()!=null ||
                value.getQuery()!=null || value.getFragment()!=null ||
                !(value.getPath().isEmpty() || value.getPath().equals("/"))) {
            throw new IllegalArgumentException("Configure a dedicated HTTPS SmartDrive origin.");
        }
        return value.resolve("/internal/studio/catalog");
    }

    static JsonObject catalog(Set<Integer> stores,String kind)throws Exception {
        String origin=System.getenv("SMARTDRIVE_STUDIO_ORIGIN");
        if(origin==null || origin.isBlank()) { JsonObject off=new JsonObject();off.addProperty("enabled",false);return off; }
        if(!Set.of("BADGE","BUSINESS_CARDS","LABELS","BILL_BOOKS").contains(kind)) throw new IllegalArgumentException("Invalid template category.");
        JsonObject body=new JsonObject();body.addProperty("kind",kind);
        var ids=new com.google.gson.JsonArray();stores.stream().sorted().forEach(id->ids.add(Integer.toString(id)));body.add("storeIds",ids);
        JsonObject result=send(body,"catalog",MAX_CATALOG_BYTES);
        if(!result.has("templates") || !result.get("templates").isJsonArray() || result.getAsJsonArray("templates").size()>10000)
            throw new IOException("SmartDrive returned an invalid library.");
        result.addProperty("enabled",true);return result;
    }

    static JsonObject template(Set<Integer> stores,int storageStore,java.util.UUID id,boolean linkAuthorized)throws Exception {
        JsonObject body=new JsonObject();body.addProperty("store",Integer.toString(storageStore));body.addProperty("id",id.toString());
        body.addProperty("linkAuthorized",linkAuthorized);
        var ids=new com.google.gson.JsonArray();stores.stream().sorted().forEach(store->ids.add(Integer.toString(store)));body.add("storeIds",ids);
        JsonObject result=send(body,"template",32*1024*1024);
        verifyTemplate(result,storageStore,id);return result;
    }

    static void verifyTemplate(JsonObject result,int storageStore,java.util.UUID id)throws IOException {
        try {
            if(!id.toString().equals(result.get("id").getAsString()) || result.get("storageStoreId").getAsInt()!=storageStore ||
                    result.get("ownerStoreId").getAsInt()<1 || !result.get("sharedStoreIds").isJsonArray() ||
                    !result.get("sha256").getAsString().matches("[a-f0-9]{64}") ||
                    result.get("content").getAsString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>8*1024*1024 ||
                    !LanSecurity.sha256(result.get("content").getAsString()).equals(result.get("sha256").getAsString()))
                throw new IOException("SmartDrive returned a different template identity or revision.");
        }catch(RuntimeException invalid){throw new IOException("SmartDrive returned an invalid template.",invalid);}
    }

    private static JsonObject send(JsonObject body,String operation,int maximumBytes)throws Exception {
        String origin=System.getenv("SMARTDRIVE_STUDIO_ORIGIN");
        if(origin==null||origin.isBlank())throw new IOException("SmartDrive is not configured.");
        String token=System.getenv("SMARTDRIVE_STUDIO_BRIDGE_TOKEN");
        if(token==null || !token.matches("[a-f0-9]{64}")) throw new IOException("SmartDrive bridge credentials are unavailable.");
        var request=HttpRequest.newBuilder(endpoint(origin).resolve("/internal/studio/"+operation)).timeout(Duration.ofSeconds(30))
                .header("Content-Type","application/json").header("X-SmartDrive-Service",token);
        String accessId=System.getenv("SMARTDRIVE_ACCESS_CLIENT_ID"),accessSecret=System.getenv("SMARTDRIVE_ACCESS_CLIENT_SECRET");
        if(accessId!=null || accessSecret!=null) {
            if(accessId==null || accessSecret==null) throw new IOException("SmartDrive Access credentials are incomplete.");
            request.header("CF-Access-Client-Id",accessId).header("CF-Access-Client-Secret",accessSecret);
        }
        var response=HTTP.send(request.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofInputStream());
        try(var input=response.body()) {
            if(response.statusCode()!=200) throw new IOException("SmartDrive library is unavailable.");
            byte[] bytes=input.readNBytes(maximumBytes+1);
            if(bytes.length>maximumBytes)throw new IOException("SmartDrive library response is too large.");
            JsonObject result=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            return result;
        }
    }
}
