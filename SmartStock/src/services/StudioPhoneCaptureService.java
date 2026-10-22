package services;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;

/** Store-authenticated temporary relay; no store credentials are sent to the phone. */
final class StudioPhoneCaptureService {
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static volatile StorefrontConfig config;
    static JsonObject queue(JsonObject request,int location,int user)throws Exception {
        if(!java.util.Set.of("ISSUE","PAIR_PHONE").contains(StorefrontService.text(request,"action")))return StudioPhotoQueue.current().execute(location,request);
        StorefrontConfig settings=config;
        if(settings==null){settings=StorefrontConfig.load();if(settings==null)throw new IllegalStateException("Configure the store website connection before using phone photos.");config=settings;}
        boolean paired="PAIR_PHONE".equals(StorefrontService.text(request,"action"));
        JsonObject grant=paired?StudioPhotoQueue.current().pair(location,user,StorefrontService.text(request,"name")):StudioPhotoQueue.current().issue(location,user);
        String token=grant.remove("token").getAsString();
        grant.addProperty("url",(paired?"https://pic.deckers.gy/":settings.origin()+"/shop/studio-photo-queue")+"#instance="+settings.instanceId()+"&token="+token+(paired?"&paired=1&store="+location:""));
        return grant;
    }
    static JsonObject execute(JsonObject request,UUID session,UUID device,int location)throws Exception {
        StorefrontConfig settings=config;
        if(settings==null){settings=StorefrontConfig.load();if(settings==null)throw new IllegalStateException("Configure the store website connection before using phone photos.");config=settings;}
        String action=StorefrontService.text(request,"action");
        if(!java.util.Set.of("CREATE","POLL","ACK","CANCEL").contains(action))throw new IllegalArgumentException("Choose a valid phone photo action.");
        JsonObject body=new JsonObject();body.addProperty("action",action);
        String id=action.equals("CREATE")?UUID.randomUUID().toString():UUID.fromString(StorefrontService.text(request,"id")).toString();
        body.addProperty("id",id);body.addProperty("owner",device+":"+session+":"+location);
        String token=null;
        if(action.equals("CREATE")){
            token=LanSecurity.randomToken();body.addProperty("tokenHash",LanSecurity.sha256(token));
            body.addProperty("label","Passport photo");
        }
        var message=HttpRequest.newBuilder(URI.create(settings.origin()+"/shop/internal/studio-capture"))
                .timeout(Duration.ofSeconds(20)).header("Content-Type","application/json")
                .header("X-Storefront-Key",settings.edgeKey()).header("X-Storefront-Instance",settings.instanceId())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        var response=HTTP.send(message,HttpResponse.BodyHandlers.ofInputStream());byte[] bytes;
        try(var in=response.body()){bytes=in.readNBytes(3*1024*1024+1);}
        if(bytes.length>3*1024*1024)throw new IllegalStateException("The phone photo response is too large.");
        if(response.statusCode()==410)throw new IllegalArgumentException("This phone photo link expired or was closed. Create a new link.");
        if(response.statusCode()!=200)throw new IllegalStateException("The photo relay is unavailable (HTTP "+response.statusCode()+"). Check that the website supports phone capture.");
        JsonObject result=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        result.addProperty("id",id);
        if(token!=null)result.addProperty("url",settings.origin()+"/shop/studio-capture#id="+id+"&instance="+settings.instanceId()+"&token="+token);
        return result;
    }
}
