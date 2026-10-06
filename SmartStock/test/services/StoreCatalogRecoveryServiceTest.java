package services;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class StoreCatalogRecoveryServiceTest {
 @Test void importsOnlyCatalogImagesAndResetsSourceLocalPresence() {
  JsonArray rows=new JsonArray();
  for(String category:new String[]{"PRODUCT","CUSTOM_ITEM","CUSTOM_VARIANT","EMPLOYEE_PHOTO","COMPANY_LOGO"}) {
   JsonObject r=new JsonObject();r.addProperty("category",category);r.addProperty("lifecycle_status","ACTIVE");r.addProperty("local_status","PRESENT");r.addProperty("remote_item_id","graph-item");rows.add(r);
  }
  JsonObject deleted=rows.get(0).getAsJsonObject().deepCopy();deleted.addProperty("lifecycle_status","DELETED");rows.add(deleted);
  JsonArray imported=StoreCatalogRecoveryService.catalogImages(rows);
  assertEquals(3,imported.size());
  for(JsonElement e:imported){assertEquals("MISSING",e.getAsJsonObject().get("local_status").getAsString());assertEquals("graph-item",e.getAsJsonObject().get("remote_item_id").getAsString());}
  assertEquals("PRESENT",rows.get(0).getAsJsonObject().get("local_status").getAsString());
 }
 @Test void decodesCloudMirrorJsonWithoutChangingNativeArrays() throws Exception {
  JsonArray rows=new JsonArray(); JsonObject row=new JsonObject(); row.addProperty("additional_barcodes","[\"123\"]"); row.add("additional_image_urls",new JsonArray()); rows.add(row);
  JsonObject decoded=StoreCatalogRecoveryService.decodeJsonColumns(rows,java.util.Set.of("additional_barcodes","additional_image_urls")).get(0).getAsJsonObject();
  assertEquals("123",decoded.getAsJsonArray("additional_barcodes").get(0).getAsString()); assertTrue(decoded.get("additional_image_urls").isJsonArray()); assertTrue(row.get("additional_barcodes").isJsonPrimitive());
 }
 @Test void planCannotImportStockTransactionsOrCredentials() {
  assertFalse(StoreCatalogRecoveryService.TABLES.contains("inventory"));
  assertFalse(StoreCatalogRecoveryService.TABLES.contains("sales"));
  assertFalse(StoreCatalogRecoveryService.TABLES.contains("users"));
  assertFalse(StoreCatalogRecoveryService.TABLES.contains("image_cloud_configuration"));
 }
}
