package services;

import com.google.gson.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorefrontIdentityDirectoryTest {
    private static final Instant NOW=Instant.parse("2026-09-25T12:00:00Z");
    private static JsonObject user(String email,boolean verified){
        JsonObject user=new JsonObject();user.addProperty("id",UUID.randomUUID().toString());user.addProperty("email",email);
        if(verified)user.addProperty("email_confirmed_at","2026-09-24T12:00:00Z");return user;
    }
    private static JsonArray users(JsonObject...users){JsonArray list=new JsonArray();for(var user:users)list.add(user);return list;}
    @Test void exactVerifiedEmailOnlyAndSafeFields(){
        JsonObject match=user("CUSTOMER@example.test",true);match.addProperty("access_token","must-not-leak");
        JsonObject result=StorefrontIdentityDirectory.verifiedMatch(users(user("other@example.test",true),match),"customer@example.test",NOW);
        assertEquals(match.get("id"),result.get("authId"));assertEquals(2,result.size());assertFalse(result.has("access_token"));
    }
    @Test void editableMetadataCannotEstablishEmailOwnership(){
        JsonObject other=user("other@example.test",true),metadata=new JsonObject();metadata.addProperty("email","customer@example.test");metadata.addProperty("email_verified",true);other.add("user_metadata",metadata);
        assertThrows(IllegalArgumentException.class,()->StorefrontIdentityDirectory.verifiedMatch(users(other),"customer@example.test",NOW));
        assertThrows(IllegalArgumentException.class,()->StorefrontIdentityDirectory.verifiedMatch(users(user("customer@example.test",false)),"customer@example.test",NOW));
    }
    @Test void ambiguousBannedAndDeletedAccountsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->StorefrontIdentityDirectory.verifiedMatch(users(user("customer@example.test",true),user("customer@example.test",true)),"customer@example.test",NOW));
        JsonObject banned=user("customer@example.test",true);banned.addProperty("banned_until",NOW.plusSeconds(100).toString());
        assertThrows(IllegalArgumentException.class,()->StorefrontIdentityDirectory.verifiedMatch(users(banned),"customer@example.test",NOW));
        JsonObject deleted=user("customer@example.test",true);deleted.addProperty("deleted_at",NOW.toString());
        assertThrows(IllegalArgumentException.class,()->StorefrontIdentityDirectory.verifiedMatch(users(deleted),"customer@example.test",NOW));
    }
    @Test void completeEmailRequired(){
        assertEquals("customer@example.test",StorefrontIdentityDirectory.email(" Customer@Example.Test "));
        assertThrows(IllegalArgumentException.class,()->StorefrontIdentityDirectory.email("customer"));
    }
}
