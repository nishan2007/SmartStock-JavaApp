package services;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlaqueCatalogTest {
 private JsonObject fixture(){return JsonParser.parseString("{\"laserProducts\":[{\"id\":151,\"variants\":[{\"id\":380,\"templateWidth\":6,\"templateHeight\":6.5,\"_imageManifest\":{\"sha256\":\"abc\",\"privateUrl\":\"private\"}},{\"id\":381}]}]}").getAsJsonObject();}
 @Test void browserCatalogRetainsTemplateDimensionsWithoutPrivateImageMetadata(){var snapshot=fixture();PlaqueCatalog.redact(snapshot);var variant=snapshot.getAsJsonArray("laserProducts").get(0).getAsJsonObject().getAsJsonArray("variants").get(0).getAsJsonObject();assertFalse(variant.has("_imageManifest"));assertTrue(variant.get("image").getAsBoolean());assertEquals(6.5,variant.get("templateHeight").getAsDouble());assertFalse(snapshot.toString().contains("privateUrl"));}
 @Test void imageLookupRequiresBothPublishedItemAndVariant(){var snapshot=fixture();assertNotNull(PlaqueCatalog.image(snapshot,151,380));assertNull(PlaqueCatalog.image(snapshot,152,380));assertNull(PlaqueCatalog.image(snapshot,151,379));assertNull(PlaqueCatalog.image(snapshot,151,381));}
 @Test void publishedVariantPhotosPermitOnlyActiveRasterAssets(){for(String category:new String[]{"CUSTOM_ITEM","CUSTOM_VARIANT"}){assertTrue(ServerImageAssetService.storefrontPublicAssetAllowed(category,"ACTIVE","image/jpeg"));assertFalse(ServerImageAssetService.storefrontPublicAssetAllowed(category,"DELETED","image/jpeg"));assertFalse(ServerImageAssetService.storefrontPublicAssetAllowed(category,"ACTIVE","image/svg+xml"));assertFalse(ServerImageAssetService.storefrontAssetAllowed(category,"ACTIVE","image/jpeg"));}}}
