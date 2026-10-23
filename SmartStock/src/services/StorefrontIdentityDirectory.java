package services;

import com.google.gson.*;
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
        String email=email(value);var config=StorefrontConfig.load();if(config==null)throw new IllegalArgumentException("Configure the customer website first.");
        var identity=new CustomerWebIdentityStore(config).read(email).identity();
        if(!identity.has("verified")||!identity.get("verified").getAsBoolean())throw new IllegalArgumentException("No verified store-hosted online account was found. Ask the customer to verify their email on the website first.");
        JsonObject safe=new JsonObject();safe.addProperty("authId",text(identity,"id"));safe.addProperty("email",email);return safe;
    }
    static JsonObject byId(UUID id,String email)throws Exception {
        var identity=byEmail(email);if(!id.toString().equals(text(identity,"authId")))throw new IllegalArgumentException("The verified online identity changed. Search again.");return identity;
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

}
