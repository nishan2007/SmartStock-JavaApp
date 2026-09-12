package services;

import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Server-only Auth and private Storage adapter. */
public class EmploymentPortalCloud {
    public static final String BUCKET="employment-applications";
    private final SupabaseProjectConfig config=SupabaseProjectConfig.load();
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
    public JsonObject auth(String route,String token,JsonObject body)throws Exception {
        HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(config.url()+"/auth/v1/"+route)).timeout(Duration.ofSeconds(30)).header("apikey",config.publishableKey()).header("Content-Type","application/json");
        if(token!=null)b.header("Authorization","Bearer "+token);
        if(body==null)b.GET();else if("user".equals(route))b.PUT(HttpRequest.BodyPublishers.ofString(body.toString()));else b.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        HttpResponse<String> r=http.send(b.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if(r.statusCode()/100!=2)throw new AuthFailure(r.statusCode()==429?"Too many attempts. Please wait before trying again.":r.statusCode()>=500?"Account email delivery is temporarily unavailable. Please retry shortly.":"The account request was not accepted. Check your details, verification code, or try password recovery.");
        return r.body().isBlank()?new JsonObject():JsonParser.parseString(r.body()).getAsJsonObject();
    }
    public static final class AuthFailure extends Exception {public AuthFailure(String message){super(message);}}
    public void upload(String path,String type,byte[] bytes)throws Exception {
        HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(config.url()+"/storage/v1/object/"+BUCKET+"/"+path)).timeout(Duration.ofSeconds(60))
            .header("Content-Type",type).header("x-upsert","true").POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
        var r=http.send(ServerSupabaseCredentials.applyTo(b).build(),HttpResponse.BodyHandlers.discarding());
        if(r.statusCode()/100!=2)throw new java.io.IOException("Private document storage unavailable.");
    }
    public byte[] download(String path)throws Exception {
        if(!path.matches("[0-9a-f-]{36}/[0-9a-f-]{36}\\.(pdf|png|jpg)"))throw new IllegalArgumentException("Invalid document path.");
        var b=HttpRequest.newBuilder(URI.create(config.url()+"/storage/v1/object/authenticated/"+BUCKET+"/"+path)).timeout(Duration.ofSeconds(60)).GET();
        var r=http.send(ServerSupabaseCredentials.applyTo(b).build(),HttpResponse.BodyHandlers.ofInputStream());
        try(var in=r.body()){if(r.statusCode()!=200)throw new java.io.IOException("Document unavailable.");byte[] data=in.readNBytes(EmploymentApplicationService.MAX_FILE_BYTES+1);if(data.length>EmploymentApplicationService.MAX_FILE_BYTES)throw new java.io.IOException("Document is too large.");return data;}
    }
    /** Undo a legacy ban only when local ownership and immutable admin metadata agree. */
    public void reconcileLegacy(java.sql.Connection c,String email)throws Exception {
        try(var p=c.prepareStatement("SELECT registration_id,auth_user_id FROM employee_registrations WHERE LOWER(email)=LOWER(?) AND portal_identity=FALSE AND employee_id IS NULL AND status IN ('PENDING','REJECTED','UNDER_REVIEW','INTERVIEW','INFORMATION_REQUESTED')")){
            p.setString(1,email);try(var r=p.executeQuery()){if(!r.next()||r.getObject(2)==null)return;
                String id=r.getObject(1).toString(),auth=r.getObject(2).toString();
                var b=HttpRequest.newBuilder(URI.create(config.url()+"/auth/v1/admin/users/"+auth)).timeout(Duration.ofSeconds(20)).GET();
                var response=http.send(ServerSupabaseCredentials.applyTo(b).build(),HttpResponse.BodyHandlers.ofString());
                if(response.statusCode()!=200)return;
                JsonObject user=JsonParser.parseString(response.body()).getAsJsonObject(),metadata=user.getAsJsonObject("app_metadata");
                if(metadata==null||!id.equals(EmploymentApplicationService.value(metadata,"employee_registration_id"))||!email.equalsIgnoreCase(EmploymentApplicationService.value(user,"email")))return;
                String banned=EmploymentApplicationService.value(user,"banned_until");
                if(!banned.isBlank()&&java.time.Instant.parse(banned).isBefore(java.time.Instant.now().plus(Duration.ofDays(90L*365))))return;
                var update=HttpRequest.newBuilder(URI.create(config.url()+"/auth/v1/admin/users/"+auth)).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json").PUT(HttpRequest.BodyPublishers.ofString("{\"ban_duration\":\"none\"}"));
                var changed=http.send(ServerSupabaseCredentials.applyTo(update).build(),HttpResponse.BodyHandlers.discarding());
                if(changed.statusCode()/100==2)try(var q=c.prepareStatement("UPDATE employee_registrations SET portal_identity=TRUE WHERE registration_id=?")){q.setObject(1,UUID.fromString(id));q.executeUpdate();}
            }
        }
    }
}
