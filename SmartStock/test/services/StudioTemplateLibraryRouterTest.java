package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StudioTemplateLibraryRouterTest {
 @TempDir Path root;
 @Test void sharedAccessDoesNotAllowDeletingOwnerTemplate()throws Exception{
  var store=new StudioTemplateStore(root);var owner=router(store,1,1,2);String id=UUID.randomUUID().toString();
  var saved=(JsonObject)owner.execute(save(id,"Shared"));var assigned=(JsonObject)owner.execute(assign(id,1,1,"",2));
  var delete=action("DELETE");delete.addProperty("id",id);delete.addProperty("storageStoreId",1);delete.add("expected",saved.get("sha256"));delete.add("expectedAssignment",assigned.get("assignmentVersion"));
  assertThrows(SecurityException.class,()->router(store,2,2).execute(delete));
  assertTrue(((JsonObject)owner.execute(delete)).get("deleted").getAsBoolean());assertEquals(0,list(owner,0).size());
 }
 final Map<Integer,String> names=Map.of(1,"Skeldon",2,"Rosehall",3,"Other store");
 JsonObject action(String value){JsonObject r=new JsonObject();r.addProperty("action",value);return r;}
 JsonObject save(String id,String name){JsonObject r=action("SAVE");r.addProperty("id",id);r.addProperty("name",name);r.addProperty("expected","");r.addProperty("content","{\"formatVersion\":1,\"name\":\""+name+"\",\"widthMm\":85.6,\"heightMm\":54,\"elements\":[]}");return r;}
 StudioTemplateLibraryRouter router(StudioTemplateStore store,int current,Integer...allowed){return new StudioTemplateLibraryRouter(names,Set.of(allowed),current,store::execute);}
 JsonArray list(StudioTemplateLibraryRouter router,Integer view)throws Exception{JsonObject r=action("LIST");r.addProperty("storeFoldersSupported",true);if(view!=null)r.addProperty("viewStoreId",view);return ((JsonObject)router.execute(r)).getAsJsonArray("templates");}
 JsonObject assign(String id,int storage,int owner,String version,int...shares){JsonObject r=action("ASSIGN");r.addProperty("id",id);r.addProperty("storageStoreId",storage);r.addProperty("ownerStoreId",owner);r.addProperty("expectedAssignment",version);JsonArray a=new JsonArray();for(int share:shares)a.add(share);r.add("sharedStoreIds",a);return r;}
 @Test void defaultsToOwnStoreAndSharesWithoutDuplicatingOrFlattening()throws Exception{
  StudioTemplateStore store=new StudioTemplateStore(root);String id=UUID.randomUUID().toString();var skeldon=router(store,1,1,2);var rosehall=router(store,2,2);
  JsonObject first=(JsonObject)skeldon.execute(save(id,"School"));assertEquals(1,first.get("createdStoreId").getAsInt());assertEquals("Skeldon",first.get("ownerStoreName").getAsString());
  assertEquals(1,list(skeldon,null).size());assertEquals(0,list(rosehall,null).size());assertEquals(1,list(skeldon,0).size());assertEquals(0,list(skeldon,2).size());
  JsonObject shared=(JsonObject)skeldon.execute(assign(id,1,1,"",2));assertEquals(1,list(rosehall,null).size());assertEquals(1,store.execute(1,action("LIST")).getAsJsonArray("templates").size());assertEquals(0,store.execute(2,action("LIST")).getAsJsonArray("templates").size());
  JsonObject get=action("GET");get.addProperty("id",id);get.addProperty("storageStoreId",1);assertTrue(((JsonObject)rosehall.execute(get)).has("content"));
  String version=shared.get("assignmentVersion").getAsString();JsonObject moved=(JsonObject)skeldon.execute(assign(id,1,2,version));assertEquals(1,moved.get("createdStoreId").getAsInt());assertEquals(2,moved.get("ownerStoreId").getAsInt());assertEquals(0,list(skeldon,null).size());assertEquals(1,list(rosehall,null).size());
  assertThrows(StudioTemplateStore.Conflict.class,()->skeldon.execute(assign(id,1,1,version)));
 }
 @Test void crossStoreReadsWritesAndAssignmentsRequireStoreAccess()throws Exception{
  StudioTemplateStore store=new StudioTemplateStore(root);String id=UUID.randomUUID().toString();router(store,1,1,2).execute(save(id,"Private"));var rosehall=router(store,2,2);
  JsonObject get=action("GET");get.addProperty("id",id);get.addProperty("storageStoreId",1);assertThrows(SecurityException.class,()->rosehall.execute(get));assertThrows(SecurityException.class,()->list(rosehall,1));
  assertThrows(SecurityException.class,()->rosehall.execute(assign(id,1,2,"")));assertThrows(SecurityException.class,()->router(store,1,1,2).execute(assign(id,1,1,"",3)));
  JsonObject forbidden=save(UUID.randomUUID().toString(),"No");forbidden.addProperty("storageStoreId",1);assertThrows(SecurityException.class,()->rosehall.execute(forbidden));
 }
 @Test void assignmentSurvivesContentEditsAndVerifiedRecoveryCatalog()throws Exception{
  Map<String,byte[]> remote=new HashMap<>();StudioTemplateStore store=new StudioTemplateStore(root,new StudioTemplateStore.Cloud(){public void upload(String id,Path p)throws Exception{remote.put(id,Files.readAllBytes(p));}public boolean download(String id,Path p)throws Exception{Files.write(p,remote.get(id));return true;}});
  var router=router(store,1,1,2);String id=UUID.randomUUID().toString();JsonObject first=(JsonObject)router.execute(save(id,"Original"));router.execute(assign(id,1,2,"",1));JsonObject edit=save(id,"Edited");edit.addProperty("expected",first.get("sha256").getAsString());JsonObject updated=(JsonObject)router.execute(edit);assertEquals(2,updated.get("ownerStoreId").getAsInt());assertEquals(1,updated.get("createdStoreId").getAsInt());
  store.backupPending();JsonArray catalog=JsonParser.parseString(new String(remote.get(store.catalogBackupId(1)),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();assertEquals(2,catalog.get(0).getAsJsonObject().get("ownerStoreId").getAsInt());assertFalse(store.execute(1,action("LIST")).get("backupCatalogPending").getAsBoolean());
 }
 @Test void staffCanSaveSharedTemplateButCannotChangeItsStoreAssignment()throws Exception{
  StudioTemplateStore store=new StudioTemplateStore(root);String id=UUID.randomUUID().toString();var owner=router(store,1,1,2);var staff=router(store,2,2);
  JsonObject first=(JsonObject)owner.execute(save(id,"Original"));owner.execute(assign(id,1,1,"",2));
  JsonObject edit=save(id,"Staff edit");edit.addProperty("storageStoreId",1);edit.addProperty("expected",first.get("sha256").getAsString());
  JsonObject updated=(JsonObject)staff.execute(edit);assertEquals("Staff edit",updated.get("name").getAsString());assertEquals(1,updated.get("ownerStoreId").getAsInt());
  assertThrows(SecurityException.class,()->staff.execute(assign(id,1,2,updated.get("assignmentVersion").getAsString())));
  assertThrows(StudioTemplateStore.Conflict.class,()->staff.execute(edit));
 }
 @Test void namedDiskFoldersStayStableAcrossStoreRenames()throws Exception{
  StudioTemplateStore store=new StudioTemplateStore(root);store.configureStores(names);var router=router(store,1,1,2);router.execute(save(UUID.randomUUID().toString(),"Named"));
  assertTrue(Files.isDirectory(root.resolve("store-1-Skeldon")));store.configureStores(Map.of(1,"Skeldon renamed",2,"Rosehall"));assertEquals(1,list(router,null).size());assertFalse(Files.exists(root.resolve("store-1-Skeldon renamed")));
 }
}
