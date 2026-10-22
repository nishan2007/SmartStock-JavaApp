package gy.deckers.studio;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class StoreTemplateFoldersTest {
 @TempDir Path root;
 JsonObject catalog(){return JsonParser.parseString("{\"defaultStoreId\":1,\"stores\":[{\"id\":2,\"name\":\"Rosehall\"},{\"id\":1,\"name\":\"Skeldon\"}]}").getAsJsonObject();}
 JsonObject row(int storage,int owner){return JsonParser.parseString("{\"id\":\"a0470f32-de95-4bd4-a864-57fe420aa9ad\",\"storageStoreId\":"+storage+",\"ownerStoreId\":"+owner+",\"ownerStoreName\":\"Skeldon\",\"sharedStoreIds\":[]}").getAsJsonObject();}
 @Test void defaultsToOwnNamedStoreAndSupportsOtherStoreAndSharedViews()throws Exception{
  var folders=new StoreTemplateFolders(root);folders.accept(catalog());assertEquals("Skeldon",folders.viewName());
  JsonObject own=row(1,1),other=row(2,2);Path a=folders.file(own,".sspub"),b=folders.file(other,".sspub");assertNotEquals(a,b);Files.writeString(a.resolveSibling(a.getFileName()+".meta.json"),own.toString());Files.writeString(b.resolveSibling(b.getFileName()+".meta.json"),other.toString());assertTrue(folders.visible(a));assertFalse(folders.visible(b));
  other.getAsJsonArray("sharedStoreIds").add(1);Files.writeString(b.resolveSibling(b.getFileName()+".meta.json"),other.toString());assertTrue(folders.visible(b));
  SwingUtilities.invokeAndWait(()->{JComboBox<?> selector=(JComboBox<?>)folders.selector(()->{});assertEquals("Skeldon (my store)",selector.getSelectedItem());selector.setSelectedIndex(1);assertEquals("Rosehall",folders.viewName());assertFalse(folders.visible(a));selector.setSelectedIndex(0);assertTrue(folders.visible(a));});
  assertEquals("Skeldon",new StoreTemplateFolders(root).viewName());
 }
 @Test void routesRequestsToOriginalStorageAfterOwnershipChanges()throws Exception{
  var folders=new StoreTemplateFolders(root);folders.accept(catalog());JsonObject metadata=row(1,2),request=new JsonObject();folders.target(request,metadata);assertEquals(1,request.get("storageStoreId").getAsInt());assertFalse(folders.own(metadata));metadata.getAsJsonArray("sharedStoreIds").add(1);assertTrue(folders.own(metadata));
 }
}
