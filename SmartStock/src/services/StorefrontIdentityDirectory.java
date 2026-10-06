package services;

import com.google.gson.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static services.StorefrontService.text;

/** Server-only Auth directory access for authorized staff customer resolution. */
final class StorefrontIdentityDirectory {
    private StorefrontIdentityDirectory() { }

    static String email(String value) {
        String email=value.trim().toLowerCase(Locale.ROOT);
        if(email.length()>254||!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalArgumentException("Enter the customer's complete email address.");
        return email;
    }

    static JsonObject byEmail(String value)throws Exception {
        String email=email(value);
        // Auth's filter is a search, not an ownership check. Only exact verified
        // matches are returned, regardless of names or editable user metadata.
        JsonObject response=get("admin/users?page=1&per_page=100&filter="+URLEncoder.encode(email,StandardCharsets.UTF_8));
        JsonArray users=response.getAsJsonArray("users");
        if(users==null)throw new IllegalArgumentException("The website account directory is unavailable.");
        if(users.size()>=100)throw new IllegalArgumentException("Too many website accounts match this search. Ask the administrator to resolve the duplicate contacts.");
        return verifiedMatch(users,email,Instant.now());
    }

    static JsonObject byId(UUID id,String email)throws Exception {
        JsonArray users=new JsonArray();users.add(get("admin/users/"+id));
        return verifiedMatch(users,email(email),Instant.now());
    }

    static JsonObject verifiedMatch(JsonArray users,String email,Instant now) {
        JsonObject match=null;
        for(JsonElement element:users){
            JsonObject user=element.getAsJsonObject();
            if(!email.equalsIgnoreCase(text(user,"email").trim()))continue;
            if(match!=null)throw new IllegalArgumentException("More than one website identity uses this email. Ask the administrator to resolve it.");
            match=user;
        }
        if(match==null||text(match,"email_confirmed_at").isBlank())
            throw new IllegalArgumentException("No verified website account was found. Ask the customer to verify this email on the website first.");
        if(!text(match,"deleted_at").isBlank()||!text(match,"banned_until").isBlank()&&Instant.parse(text(match,"banned_until")).isAfter(now))
            throw new IllegalArgumentException("This website account is unavailable. Contact the administrator.");
        JsonObject safe=new JsonObject();safe.addProperty("authId",UUID.fromString(text(match,"id")).toString());safe.addProperty("email",email);return safe;
    }

    private static JsonObject get(String route)throws Exception {
        var config=SupabaseProjectConfig.load();
        var request=HttpRequest.newBuilder(URI.create(config.url()+"/auth/v1/"+route)).timeout(Duration.ofSeconds(15)).GET();
        var response=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
            .send(ServerSupabaseCredentials.applyTo(request).build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)throw new IllegalArgumentException("The website account directory is unavailable. Please try again.");
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
}
