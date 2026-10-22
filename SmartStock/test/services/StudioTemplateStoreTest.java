package services;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class StudioTemplateStoreTest {
 @TempDir Path folder;
 @Test void textFormattingFieldsCanBeSharedAndInvalidOutlineIsRejected()throws Exception{
  var store=new StudioTemplateStore(folder);var request=save(UUID.randomUUID().toString(),"Styled","");var content=JsonParser.parseString(request.get("content").getAsString()).getAsJsonObject();var element=JsonParser.parseString("{\"label\":\"Name\",\"type\":\"TEXT\",\"text\":\"Hello\",\"x\":1,\"y\":1,\"width\":20,\"height\":10,\"italic\":true,\"underline\":true,\"strike\":true,\"textOutline\":true,\"textOutlineColor\":\"#0000ff\",\"textOutlineWidth\":0.3}").getAsJsonObject();content.getAsJsonArray("elements").add(element);request.addProperty("content",content.toString());assertNotNull(store.execute(1,request).get("sha256"));element.addProperty("textOutlineWidth",-1);request.addProperty("content",content.toString());assertThrows(IllegalArgumentException.class,()->store.execute(1,request));
 }
 JsonObject save(String id,String name,String expected){JsonObject r=new JsonObject();r.addProperty("action","SAVE");r.addProperty("id",id);r.addProperty("name",name);r.addProperty("expected",expected);r.addProperty("content","{\"formatVersion\":1,\"name\":\""+name+"\",\"widthMm\":85.6,\"heightMm\":54,\"elements\":[]}");return r;}
 JsonObject action(String action){JsonObject r=new JsonObject();r.addProperty("action",action);return r;}
 @Test void deletionChecksRevisionRetainsRecoveryAndRemovesCatalogEntry()throws Exception{
  var store=new StudioTemplateStore(folder);String id=UUID.randomUUID().toString();
  var saved=store.execute(1,save(id,"Duplicate",""));String hash=saved.get("sha256").getAsString();
  var delete=action("DELETE");delete.addProperty("id",id);delete.addProperty("expected","wrong");delete.addProperty("expectedAssignment","");
  assertThrows(StudioTemplateStore.Conflict.class,()->store.execute(1,delete));
  delete.addProperty("expected",hash);delete.addProperty("expectedAssignment","stale");
  assertThrows(StudioTemplateStore.Conflict.class,()->store.execute(1,delete));delete.addProperty("expectedAssignment","");
  assertTrue(store.execute(1,delete).get("deleted").getAsBoolean());
  assertEquals(0,store.execute(1,action("LIST")).getAsJsonArray("templates").size());
  assertTrue(Files.exists(folder.resolve("store-1/"+hash+".sstbadge")));
  try(var records=Files.list(folder.resolve("store-1/deleted"))){assertEquals(1,records.count());}
  assertThrows(StudioTemplateStore.Conflict.class,()->store.execute(1,save(id,"Stale save",hash)));
 }
 @Test void storeIsolationAtomicVersionsAndDurableBackupQueue()throws Exception{
  StudioTemplateStore store=new StudioTemplateStore(folder);String id=UUID.randomUUID().toString();JsonObject first=store.execute(1,save(id,"School",""));String hash=first.get("sha256").getAsString();
  assertEquals(1,store.execute(1,action("LIST")).getAsJsonArray("templates").size());assertEquals(0,store.execute(2,action("LIST")).getAsJsonArray("templates").size());
  assertThrows(StudioTemplateStore.Conflict.class,()->store.execute(1,save(id,"Changed","")));
  JsonObject second=store.execute(1,save(id,"Changed",hash));assertNotEquals(hash,second.get("sha256").getAsString());assertTrue(Files.exists(folder.resolve("store-1/"+hash+".sstbadge")));
  assertFalse(store.execute(1,action("LIST")).getAsJsonArray("templates").get(0).getAsJsonObject().get("backedUp").getAsBoolean());
  try(var files=Files.list(folder.resolve("store-1"))){assertEquals(2,files.filter(p->p.toString().endsWith(".pending.json")).count());}
  JsonObject get=action("GET");get.addProperty("id",id);get.addProperty("sha256",hash);assertThrows(StudioTemplateStore.Conflict.class,()->store.execute(1,get));
 }
 @Test void pathsAndPersonalPhotosAreRejected()throws Exception{
  StudioTemplateStore store=new StudioTemplateStore(folder);assertThrows(IllegalArgumentException.class,()->store.execute(1,save("../../escape","School","")));
  JsonObject r=save(UUID.randomUUID().toString(),"School","");r.addProperty("content","{\"formatVersion\":1,\"elements\":[{\"personalPhoto\":\"private\"}]}");assertThrows(IllegalArgumentException.class,()->store.execute(1,r));
 }
 @Test void backupRetriesThenVerifiesRevisionsAndRecoveryCatalog()throws Exception{
  java.util.Map<String,byte[]> remote=new java.util.HashMap<>();java.util.concurrent.atomic.AtomicBoolean fail=new java.util.concurrent.atomic.AtomicBoolean(true);
  StudioTemplateStore store=new StudioTemplateStore(folder,new StudioTemplateStore.Cloud(){public void upload(String id,Path file)throws Exception{if(fail.get())throw new java.io.IOException("Offline");remote.put(id,Files.readAllBytes(file));}public boolean download(String id,Path file)throws Exception{Files.write(file,remote.get(id));return true;}});
  store.execute(1,save(UUID.randomUUID().toString(),"School",""));store.backupPending();
  assertTrue(store.execute(1,action("LIST")).get("backupError").getAsBoolean());assertTrue(remote.isEmpty());
  fail.set(false);store.backupPending();assertEquals(2,remote.size());JsonObject list=store.execute(1,action("LIST"));assertTrue(list.getAsJsonArray("templates").get(0).getAsJsonObject().get("backedUp").getAsBoolean());assertFalse(list.get("backupCatalogPending").getAsBoolean());
  String catalogId=store.catalogBackupId(1);JsonArray catalog=JsonParser.parseString(new String(remote.get(catalogId),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();assertEquals(1,catalog.size());assertTrue(remote.containsKey(catalog.get(0).getAsJsonObject().get("backupId").getAsString()));
 }
}
