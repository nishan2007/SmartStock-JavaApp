package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import publishing.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StudioPublicationTemplateStoreTest {
 @TempDir Path root;
 @Test void billBooksRemainEditableAndSeparateFromCardsAndLabels()throws Exception{
  var cloud=new Cloud();var bills=new StudioPublicationTemplateStore(root.resolve("bills"),cloud,"BILL_BOOKS");
  Publication p=PublicationTemplates.clean(new Publication());p.title="Bill book";
  var text=new Publication.Element();text.text="Receipt {{number}}";p.pages.get(0).objects.add(text);
  String id=UUID.randomUUID().toString();var saved=save(bills,1,7,id,"",p);
  assertEquals("BILL_BOOKS",saved.get("kind").getAsString());
  var reopened=PublicationFiles.read(Files.readAllBytes(root.resolve("bills/store-1/"+saved.get("sha256").getAsString()+".sspub")));
  assertEquals("TEXT",reopened.pages.get(0).objects.get(0).type);
  assertEquals("Receipt {{number}}",reopened.pages.get(0).objects.get(0).text);
  for(String category:List.of("BUSINESS_CARDS","LABELS")){
   var other=new StudioPublicationTemplateStore(root.resolve(category),cloud,category);
   assertEquals(0,((JsonObject)other.execute(1,7,request("LIST"))).getAsJsonArray("templates").size());
  }
 }
 static class Cloud implements StudioTemplateStore.Cloud {
  final Map<String,byte[]> files=new HashMap<>();boolean fail;
  public void upload(String id,Path file)throws Exception{if(fail)throw new java.io.IOException("Offline cloud");files.put(id,Files.readAllBytes(file));}
  public boolean download(String id,Path file)throws Exception{if(!files.containsKey(id))return false;Files.write(file,files.get(id));return true;}
 }
 JsonObject request(String action){var r=new JsonObject();r.addProperty("action",action);return r;}
 String begin(StudioPublicationTemplateStore store,int location,int user,String id,String expected,Publication p)throws Exception{
  var r=request("BEGIN");r.addProperty("id",id);r.addProperty("name",p.title);r.addProperty("expected",expected);r.addProperty("size",PublicationFiles.archive(p).length);
  return (String)((Map<?,?>)store.execute(location,user,r)).get("uploadId");
 }
 JsonObject save(StudioPublicationTemplateStore store,int location,int user,String id,String expected,Publication p)throws Exception{
  byte[] archive=PublicationFiles.archive(p);var begin=request("BEGIN");begin.addProperty("id",id);begin.addProperty("expected",expected);begin.addProperty("name",p.title);begin.addProperty("size",archive.length);
  String upload=(String)((Map<?,?>)store.execute(location,user,begin)).get("uploadId");
  for(int off=0;off<archive.length;off+=524288){var r=request("CHUNK");r.addProperty("uploadId",upload);r.addProperty("offset",off);r.addProperty("bytes",Base64.getEncoder().encodeToString(Arrays.copyOfRange(archive,off,Math.min(archive.length,off+524288))));store.execute(location,user,r);}
  var finish=request("FINISH");finish.addProperty("uploadId",upload);return (JsonObject)store.execute(location,user,finish);
 }
 @Test void archivesLiveOnServerAndVerifiedCloudBackupsRetainCategoryAndStoreIsolation()throws Exception{
  Cloud cloud=new Cloud();var cards=new StudioPublicationTemplateStore(root.resolve("cards"),cloud,"BUSINESS_CARDS");var labels=new StudioPublicationTemplateStore(root.resolve("labels"),cloud,"LABELS");
  Publication p=PublicationTemplates.clean(new Publication());p.title="Ginger label";String id=UUID.randomUUID().toString();
  JsonObject saved=save(labels,1,7,id,"",p);assertEquals("LABELS",saved.get("kind").getAsString());Path file=root.resolve("labels/store-1/"+saved.get("sha256").getAsString()+".sspub");assertTrue(Files.isRegularFile(file));PublicationTemplates.requireTemplate(PublicationFiles.read(Files.readAllBytes(file)));
  assertEquals(0,((JsonObject)cards.execute(1,7,request("LIST"))).getAsJsonArray("templates").size());assertEquals(0,((JsonObject)labels.execute(2,7,request("LIST"))).getAsJsonArray("templates").size());
  cloud.fail=true;labels.backupPending();assertFalse(((JsonObject)labels.execute(1,7,request("LIST"))).getAsJsonArray("templates").get(0).getAsJsonObject().get("backedUp").getAsBoolean());
  cloud.fail=false;labels.backupPending();var catalog=(JsonObject)labels.execute(1,7,request("LIST"));assertTrue(catalog.getAsJsonArray("templates").get(0).getAsJsonObject().get("backedUp").getAsBoolean());assertFalse(catalog.get("backupCatalogPending").getAsBoolean());assertEquals(2,cloud.files.size());
  var read=request("READ");read.addProperty("id",id);read.addProperty("sha256",saved.get("sha256").getAsString());read.addProperty("offset",0);var chunk=(Map<?,?>)labels.execute(1,7,read);assertArrayEquals(Files.readAllBytes(file),Base64.getDecoder().decode((String)chunk.get("bytes")));
 }
 @Test void staleSavesAndDownloadRevisionChangesAreRejected()throws Exception{
  var store=new StudioPublicationTemplateStore(root,new Cloud(),"LABELS");var p=PublicationTemplates.clean(new Publication());String id=UUID.randomUUID().toString();var first=save(store,1,7,id,"",p);
  p.title="New revision";save(store,1,7,id,first.get("sha256").getAsString(),p);
  assertThrows(StudioTemplateStore.Conflict.class,()->save(store,1,7,id,first.get("sha256").getAsString(),p));
  var r=request("READ");r.addProperty("id",id);r.addProperty("sha256",first.get("sha256").getAsString());r.addProperty("offset",0);assertThrows(StudioTemplateStore.Conflict.class,()->store.execute(1,7,r));
 }
 @Test void uploadOwnershipAndChunkOffsetsCannotBeBypassed()throws Exception{
  var store=new StudioPublicationTemplateStore(root,new Cloud(),"LABELS");var p=PublicationTemplates.clean(new Publication());String upload=begin(store,1,7,UUID.randomUUID().toString(),"",p);
  var r=request("CHUNK");r.addProperty("uploadId",upload);r.addProperty("offset",0);r.addProperty("bytes",Base64.getEncoder().encodeToString(new byte[]{1}));
  assertThrows(IllegalArgumentException.class,()->store.execute(1,8,r));assertThrows(Exception.class,()->store.execute(2,7,r));
  store.execute(1,7,r);assertThrows(IllegalArgumentException.class,()->store.execute(1,7,r));
 }
 @Test void namedPartitionsSupportSharingAndRecheckAccessDuringUploads()throws Exception{
  var cloud=new Cloud();var store=new StudioPublicationTemplateStore(root,cloud,"LABELS");store.configureStores(Map.of(1,"Skeldon",2,"Rosehall"));
  var p=PublicationTemplates.clean(new Publication());String id=UUID.randomUUID().toString();JsonObject first=save(store,1,7,id,"",p);
  assertTrue(Files.isDirectory(root.resolve("store-1-Skeldon")));assertFalse(Files.exists(root.resolve("store-1")));store.backupPending();assertEquals(2,cloud.files.size());
  JsonObject assignment=request("ASSIGN");assignment.addProperty("id",id);assignment.addProperty("ownerStoreId",1);assignment.addProperty("expectedAssignment","");JsonArray shared=new JsonArray();shared.add(2);assignment.add("sharedStoreIds",shared);JsonObject assigned=(JsonObject)store.execute(1,7,assignment);
  var rosehall=new StudioTemplateLibraryRouter(Map.of(1,"Skeldon",2,"Rosehall"),Set.of(2),2,(source,r)->store.execute(source,7,r));
  byte[] archive=PublicationFiles.archive(p);JsonObject begin=request("BEGIN");begin.addProperty("storageStoreId",1);begin.addProperty("id",id);begin.addProperty("expected",first.get("sha256").getAsString());begin.addProperty("name",p.title);begin.addProperty("size",archive.length);String upload=(String)((Map<?,?>)rosehall.execute(begin)).get("uploadId");
  JsonObject chunk=request("CHUNK");chunk.addProperty("storageStoreId",1);chunk.addProperty("uploadId",upload);chunk.addProperty("offset",0);chunk.addProperty("bytes",Base64.getEncoder().encodeToString(archive));rosehall.execute(chunk);
  assignment.addProperty("expectedAssignment",assigned.get("assignmentVersion").getAsString());assignment.add("sharedStoreIds",new JsonArray());store.execute(1,7,assignment);
  JsonObject finish=request("FINISH");finish.addProperty("storageStoreId",1);finish.addProperty("uploadId",upload);assertThrows(SecurityException.class,()->rosehall.execute(finish));
 }
 @Test void personalMergeRecordsAndOrderLinksCannotEnterTheLibrary()throws Exception{
  var store=new StudioPublicationTemplateStore(root,new Cloud(),"LABELS");var p=PublicationTemplates.clean(new Publication());p.mergeRows.add(Map.of("name","private record"));
  assertThrows(IllegalArgumentException.class,()->save(store,1,7,UUID.randomUUID().toString(),"",p));p.mergeRows.clear();p.orderId=1L;
  assertThrows(IllegalArgumentException.class,()->save(store,1,7,UUID.randomUUID().toString(),"",p));
 }
 @Test void linkedRisoMembershipsSurviveServerSaveReadAndCloudBackup()throws Exception{
  var cloud=new Cloud();var store=new StudioPublicationTemplateStore(root,cloud,"LABELS");var p=PublicationTemplates.clean(new Publication());p.version=2;var e=new Publication.Element();p.pages.get(0).objects.add(e);var sub=new Publication.RisoPage();sub.name="Green plate";sub.color="#008000";sub.objectIds.add(e.id);p.pages.get(0).risoPages.add(sub);
  var saved=save(store,1,7,UUID.randomUUID().toString(),"",p);Path file=root.resolve("store-1/"+saved.get("sha256").getAsString()+".sspub");var loaded=PublicationFiles.read(Files.readAllBytes(file));assertEquals(2,loaded.version);assertEquals(sub.objectIds,loaded.pages.get(0).risoPages.get(0).objectIds);store.backupPending();assertEquals(2,cloud.files.size());var backup=cloud.files.values().stream().map(bytes->JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8))).filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).filter(json->json.has("contentBase64")).findFirst().orElseThrow();var recovered=PublicationFiles.read(Base64.getDecoder().decode(backup.get("contentBase64").getAsString()));assertEquals(sub.objectIds,recovered.pages.get(0).risoPages.get(0).objectIds);
 }
}
