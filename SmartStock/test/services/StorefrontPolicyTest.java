package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class StorefrontPolicyTest {
    @Test void publicCatalogDoesNotExposePrivateStorageReferencesOrMutateSnapshot(){
        var s=snapshot();String reference="https://storage.example.test/private.jpg?token=secret";
        s.getAsJsonArray("products").get(0).getAsJsonObject().addProperty("image",reference);
        s.getAsJsonArray("products").get(0).getAsJsonObject().add("_imageManifest",JsonParser.parseString("{\"objectPath\":\"private-storage-path\"}"));
        var catalog=StorefrontService.catalog(s);
        assertFalse(catalog.toString().contains(reference));assertFalse(catalog.has("customers"));assertFalse(catalog.has("links"));
        assertFalse(catalog.toString().contains("private-storage-path"));
        assertTrue(s.getAsJsonArray("products").get(0).getAsJsonObject().has("_imageManifest"));
        assertEquals("available",catalog.getAsJsonArray("products").get(0).getAsJsonObject().get("image").getAsString());
        assertFalse(catalog.getAsJsonArray("products").get(0).getAsJsonObject().has("available"));
        assertEquals("In Stock",catalog.getAsJsonArray("products").get(0).getAsJsonObject().get("availability").getAsString());
        assertTrue(catalog.getAsJsonArray("products").get(0).getAsJsonObject().get("canOrder").getAsBoolean());
        assertEquals(5,s.getAsJsonArray("products").get(0).getAsJsonObject().get("available").getAsInt());
        assertEquals(reference,s.getAsJsonArray("products").get(0).getAsJsonObject().get("image").getAsString());
    }
    @Test void publicAvailabilityUsesStoreStockWithoutRevealingCounts(){
        var s=snapshot();var product=s.getAsJsonArray("products").get(0).getAsJsonObject();
        product.addProperty("available",2);var low=StorefrontService.catalog(s).getAsJsonArray("products").get(0).getAsJsonObject();
        assertEquals("Low Stock",low.get("availability").getAsString());assertTrue(low.get("canOrder").getAsBoolean());assertFalse(low.has("available"));
        product.addProperty("available",0);var empty=StorefrontService.catalog(s).getAsJsonArray("products").get(0).getAsJsonObject();
        assertEquals("Out of Stock",empty.get("availability").getAsString());assertFalse(empty.get("canOrder").getAsBoolean());
    }
    @Test void storefrontImagesExcludePrivateCategoriesDeletedAssetsAndActiveContent(){
        assertTrue(ServerImageAssetService.storefrontAssetAllowed("PRODUCT","ACTIVE","image/jpeg"));
        for(String category:new String[]{"EMPLOYEE_PHOTO","CUSTOM_ITEM","CUSTOM_VARIANT","COMPANY_LOGO"})
            assertFalse(ServerImageAssetService.storefrontAssetAllowed(category,"ACTIVE","image/jpeg"));
        for(String state:new String[]{"UNUSED","DELETE_PENDING","DELETED"})
            assertFalse(ServerImageAssetService.storefrontAssetAllowed("PRODUCT",state,"image/jpeg"));
        for(String type:new String[]{"image/svg+xml","text/html","application/octet-stream"})
            assertFalse(ServerImageAssetService.storefrontAssetAllowed("PRODUCT","ACTIVE",type));
    }
    static JsonObject snapshot(){return JsonParser.parseString("""
      {"locationId":1,"capturedAt":"2026-09-25T00:00:00Z","settings":{"enabled":true,"currency":"GYD"},
       "tax":{"vat_enabled":true,"vat_use_department_rates":false,"vat_fixed_rate_percent":14,"round_sales_to_nearest_twenty":false},
       "customers":[{"uuid":"00000000-0000-0000-0000-000000000001","email":"customer@example.test","name":"Customer","active":true,"discountEnabled":true,"discount":10}],
       "links":[],"products":[{"id":1,"name":"Test product","price":100,"available":5,"vatRate":0}]}
      """).getAsJsonObject();}
    static JsonObject cart(){return JsonParser.parseString("{\"lines\":[{\"id\":1,\"quantity\":2}],\"expectedTotal\":205.20}").getAsJsonObject();}
    @Test void totalsApplyDiscountBeforeTax(){var q=StorefrontService.quote(snapshot(),cart(),UUID.randomUUID(),"customer@example.test");assertEquals(0,new BigDecimal("205.20").compareTo(q.get("total").getAsBigDecimal()));assertEquals(0,new BigDecimal("25.20").compareTo(q.get("vat").getAsBigDecimal()));}
    @Test void fractionalAndOverflowingCartNumbersAreNotSilentlyTruncated(){
        for(String value:new String[]{"1.5","4294967297","true","{}"}){
            var c=cart();c.getAsJsonArray("lines").get(0).getAsJsonObject().add("quantity",JsonParser.parseString(value));
            assertThrows(IllegalArgumentException.class,()->StorefrontService.quote(snapshot(),c,UUID.randomUUID(),"customer@example.test"));
        }
        var c=cart();c.getAsJsonArray("lines").get(0).getAsJsonObject().addProperty("id",new BigDecimal("1.9"));
        assertThrows(IllegalArgumentException.class,()->StorefrontService.quote(snapshot(),c,UUID.randomUUID(),"customer@example.test"));
    }
    @Test void staleSnapshotSubtractsUnmirroredCommitments(){var c=cart();c.add("_pending",JsonParser.parseString("{\"1\":4}"));assertThrows(IllegalArgumentException.class,()->StorefrontService.quote(snapshot(),c,UUID.randomUUID(),"customer@example.test"));}
    @Test void duplicateAndUnpublishedItemsAreRejected(){var c=cart();c.getAsJsonArray("lines").add(c.getAsJsonArray("lines").get(0).deepCopy());assertThrows(IllegalArgumentException.class,()->StorefrontService.quote(snapshot(),c,UUID.randomUUID(),"customer@example.test"));var unknown=cart();unknown.getAsJsonArray("lines").get(0).getAsJsonObject().addProperty("id",9);assertThrows(IllegalArgumentException.class,()->StorefrontService.quote(snapshot(),unknown,UUID.randomUUID(),"customer@example.test"));}
    @Test void sharedEmailDoesNotMergeAccounts(){var s=snapshot();s.getAsJsonArray("customers").add(s.getAsJsonArray("customers").get(0).deepCopy());assertThrows(IllegalArgumentException.class,()->StorefrontService.customer(s,UUID.randomUUID(),"customer@example.test"));}
    @Test void aNewIdentityCannotTakeOverAnExistingCustomerLink(){
        var s=snapshot();UUID owner=UUID.randomUUID();var link=new JsonObject();link.addProperty("auth",owner.toString());link.addProperty("customer","00000000-0000-0000-0000-000000000001");s.getAsJsonArray("links").add(link);
        assertThrows(IllegalArgumentException.class,()->StorefrontService.customer(s,UUID.randomUUID(),"customer@example.test"));
        assertEquals("Customer",StorefrontService.customer(s,owner,"customer@example.test").get("name").getAsString());
    }
    @Test void explicitLinkDisambiguatesEmail(){var s=snapshot();var second=s.getAsJsonArray("customers").get(0).deepCopy().getAsJsonObject();second.addProperty("uuid","00000000-0000-0000-0000-000000000002");s.getAsJsonArray("customers").add(second);UUID auth=UUID.randomUUID();JsonObject link=new JsonObject();link.addProperty("auth",auth.toString());link.addProperty("customer","00000000-0000-0000-0000-000000000001");s.getAsJsonArray("links").add(link);assertEquals("Customer",StorefrontService.customer(s,auth,"customer@example.test").get("name").getAsString());}
    @Test void deadlineStartsAtReadyAndAcceptsWeek(){Instant ready=Instant.parse("2026-09-25T10:00:00Z");assertEquals(Instant.parse("2026-10-02T10:00:00Z"),StorefrontPolicy.deadline(ready,168));assertThrows(IllegalArgumentException.class,()->StorefrontPolicy.deadline(ready,0));}
    @Test void collectionRequiresReadyAndTerminalStatesStayTerminal(){assertThrows(IllegalArgumentException.class,()->StorefrontPolicy.transition("CONFIRMED","COLLECTED"));assertDoesNotThrow(()->StorefrontPolicy.transition("READY","COLLECTED"));assertThrows(IllegalArgumentException.class,()->StorefrontPolicy.transition("COLLECTED","CANCELLED"));}
    @Test void sessionsRejectTamperingExpiryAndOtherKey()throws Exception{byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);JsonObject session=new JsonObject();session.addProperty("expires",Instant.now().plusSeconds(60).getEpochSecond());session.addProperty("access","test-token");String cookie=StorefrontSession.seal(session,key);assertEquals("test-token",StorefrontSession.open(cookie,key).get("access").getAsString());byte[] altered=java.util.Base64.getUrlDecoder().decode(cookie);altered[20]^=1;assertThrows(Exception.class,()->StorefrontSession.open(java.util.Base64.getUrlEncoder().encodeToString(altered),key));assertThrows(Exception.class,()->StorefrontSession.open(cookie,new byte[32]));session.addProperty("expires",1);String expired=StorefrontSession.seal(session,key);assertThrows(Exception.class,()->StorefrontSession.open(expired,key));}
}
