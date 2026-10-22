package gy.deckers.studio;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class SharedBadgeLibraryTest {
 @TempDir Path folder;
 @Test void deletionSendsRevisionAndAssignmentAndRemovesCachedMembership()throws Exception{
  String id=UUID.randomUUID().toString(),content=new Gson().toJson(new BadgeTemplate());
  String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("name","Badge");row.addProperty("sha256",hash);row.addProperty("backedUp",false);row.addProperty("assignmentVersion","assignment");
  AtomicBoolean deleted=new AtomicBoolean();
  var cache=new SharedBadgeLibrary(folder,r->{String action=r.get("action").getAsString();JsonObject out=new JsonObject();
   if(action.equals("LIST")){JsonArray rows=new JsonArray();if(!deleted.get())rows.add(row.deepCopy());out.add("templates",rows);return out;}
   if(action.equals("GET")){out=row.deepCopy();out.addProperty("content",content);return out;}
   assertEquals("DELETE",action);assertEquals(id,r.get("id").getAsString());assertEquals(hash,r.get("expected").getAsString());assertEquals("assignment",r.get("expectedAssignment").getAsString());deleted.set(true);out.addProperty("deleted",true);return out;
  });
  cache.refresh();Path file=folder.resolve(id+".sstbadge");cache.delete(file);
  assertTrue(deleted.get());assertFalse(Files.exists(file.resolveSibling(file.getFileName()+".meta.json")));
  assertTrue(Files.exists(file));
 }
 @Test void storeMetadataRefreshesWithoutRedownloadingArtworkAndRemovedAccessHidesCache()throws Exception{
  String id=UUID.randomUUID().toString(),content=new Gson().toJson(new BadgeTemplate()),hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  JsonObject row=JsonParser.parseString("{\"storageStoreId\":1,\"ownerStoreId\":1,\"ownerStoreName\":\"Skeldon\",\"sharedStoreIds\":[],\"backedUp\":false}").getAsJsonObject();row.addProperty("id",id);row.addProperty("name","Badge");row.addProperty("sha256",hash);AtomicInteger reads=new AtomicInteger();AtomicBoolean include=new AtomicBoolean(true);
  var cache=new SharedBadgeLibrary(folder,r->{if(r.get("action").getAsString().equals("LIST")){JsonObject catalog=JsonParser.parseString("{\"defaultStoreId\":1,\"stores\":[{\"id\":1,\"name\":\"Skeldon\"},{\"id\":2,\"name\":\"Rosehall\"}]}").getAsJsonObject();JsonArray rows=new JsonArray();if(include.get())rows.add(row.deepCopy());catalog.add("templates",rows);return catalog;}assertEquals(1,r.get("storageStoreId").getAsInt());reads.incrementAndGet();var result=row.deepCopy();result.addProperty("content",content);return result;});
  cache.refresh();Path file=folder.resolve("1_"+id+".sstbadge");assertTrue(cache.stores.visible(file));assertEquals("Badge · Skeldon",cache.label(file));row.addProperty("ownerStoreId",2);row.addProperty("ownerStoreName","Rosehall");cache.refresh();assertFalse(cache.stores.visible(file));assertEquals(1,reads.get());include.set(false);cache.refresh();assertFalse(Files.exists(file.resolveSibling(file.getFileName()+".meta.json")));
 }
 @Test void cacheFetchesChangedContentOnlyAndNeverWritesOfflineEdits()throws Exception{
  String id=UUID.randomUUID().toString();String content=new Gson().toJson(new BadgeTemplate());String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("name","School");row.addProperty("sha256",hash);row.addProperty("backedUp",false);
  AtomicInteger downloads=new AtomicInteger(),saves=new AtomicInteger();AtomicBoolean connected=new AtomicBoolean(true);
  SharedBadgeLibrary cache=new SharedBadgeLibrary(folder,r->{if(!connected.get())throw new java.io.IOException("Server unavailable");switch(r.get("action").getAsString()){
   case "LIST":JsonObject catalog=new JsonObject();JsonArray entries=new JsonArray();entries.add(row);catalog.add("templates",entries);return catalog;
   case "GET":downloads.incrementAndGet();JsonObject data=row.deepCopy();data.addProperty("content",content);return data;
   default:saves.incrementAndGet();throw new AssertionError("Unexpected save");
  }});
  cache.refresh();assertTrue(cache.online);assertEquals(1,downloads.get());Path file=folder.resolve(id+".sstbadge");assertEquals("School",cache.label(file));
  cache.refresh();assertEquals(1,downloads.get());connected.set(false);assertThrows(Exception.class,cache::refresh);assertFalse(cache.online);
  BadgeTemplate edited=BadgeTemplate.load(file);edited.name="Offline edit";assertThrows(Exception.class,()->cache.save(edited,file,hash));assertEquals(0,saves.get());assertEquals(content,Files.readString(file));
 }
}
