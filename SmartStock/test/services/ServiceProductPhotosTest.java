package services;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ServiceProductPhotosTest {
    @Test void olderSnapshotsWithNullOptionalCategoriesRemainReadable(){
        var snapshot=JsonParser.parseString("{customItemCategories:null,sublimationProducts:null,laserProducts:null}").getAsJsonObject();
        assertDoesNotThrow(()->ServiceProductPhotos.redact(snapshot));assertNull(ServiceProductPhotos.image(snapshot,1));
    }
    @Test void galleriesAreRedactedAndLookupIsBoundToPublishedParentVariantAndIndex(){
        var snapshot=JsonParser.parseString("""
            {sublimationProducts:[{id:1,_imageManifest:{id:'main'},_photoManifests:[{id:'main'},{id:'extra'}],variants:[
              {id:10,_imageManifest:{id:'pink'},_photoManifests:[{id:'pink'},{id:'pink-back'}]},
              {id:11,_photoManifests:[]}]}]}
            """).getAsJsonObject();
        assertEquals("extra",ServiceProductPhotos.image(snapshot,1,0,1).get("id").getAsString());
        assertEquals("pink-back",ServiceProductPhotos.image(snapshot,1,10,1).get("id").getAsString());
        assertNull(ServiceProductPhotos.image(snapshot,2,10,0));assertNull(ServiceProductPhotos.image(snapshot,1,11,0));
        assertNull(ServiceProductPhotos.image(snapshot,1,10,-1));assertNull(ServiceProductPhotos.image(snapshot,1,10,2));
        assertEquals(4,ServiceProductPhotos.manifests(snapshot).size());
        ServiceProductPhotos.redact(snapshot);assertFalse(snapshot.toString().contains("_photoManifests"));assertFalse(snapshot.toString().contains("_imageManifest"));
        var item=snapshot.getAsJsonArray("sublimationProducts").get(0).getAsJsonObject();assertEquals(2,item.get("photoCount").getAsInt());
        assertEquals(2,item.getAsJsonArray("variants").get(0).getAsJsonObject().get("photoCount").getAsInt());
    }
    @Test void mainPhotoLookupUsesPublishedServiceItemsAndRedactsPrivateMetadata(){
        for(String group:ServiceProductPhotos.GROUPS){
            var snapshot=JsonParser.parseString("{\""+group+"\":[{\"id\":42,\"_imageReference\":\"private-reference\",\"_imageManifest\":{\"category\":\"CUSTOM_ITEM\",\"privateUrl\":\"secret\"}},{\"id\":43}]}").getAsJsonObject();
            assertNotNull(ServiceProductPhotos.image(snapshot,42));
            assertNull(ServiceProductPhotos.image(snapshot,43));
            assertNull(ServiceProductPhotos.image(snapshot,999));
            var browser=snapshot.deepCopy();ServiceProductPhotos.redact(browser);
            assertFalse(browser.toString().contains("secret"));
            assertFalse(browser.toString().contains("private-reference"));
            assertTrue(browser.getAsJsonArray(group).get(0).getAsJsonObject().get("image").getAsBoolean());
            assertFalse(browser.getAsJsonArray(group).get(1).getAsJsonObject().get("image").getAsBoolean());
            assertNotNull(ServiceProductPhotos.image(snapshot,42));
        }
    }
}
