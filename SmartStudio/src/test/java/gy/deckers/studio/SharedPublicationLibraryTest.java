package gy.deckers.studio;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import publishing.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SharedPublicationLibraryTest {
 @TempDir Path root;
 @Test void billBooksReachAnotherComputerAndLaterConversionsAreDiscovered()throws Exception{
  Server server=new Server();Path first=local("Bill book one"),later=local("Bill book two");
  var publisher=new SharedPublicationLibrary(root.resolve("publisher"),"BILL_BOOKS",server);
  publisher.refresh();publisher.migrate(List.of(first),()->true,true);
  var otherComputer=new SharedPublicationLibrary(root.resolve("other-bill-computer"),"BILL_BOOKS",server);
  otherComputer.refresh();assertEquals(1,otherComputer.files().size());
  var imported=PublicationFiles.read(Files.readAllBytes(otherComputer.files().get(0)));
  assertEquals("Bill book one",imported.title);assertTrue(imported.mergeRows.isEmpty());
  publisher.migrate(List.of(first,later),()->true,true);otherComputer.refresh();
  assertEquals(2,otherComputer.files().size());assertEquals(2,server.saves);
  publisher.migrate(List.of(first,later),()->true,true);assertEquals(2,server.saves);
 }
 @Test void deletionSendsSelectedRevisionAndRemovesCachedMembership()throws Exception{
  var archive=new Publication();archive.title="Duplicate";archive.template=true;byte[] bytes=PublicationFiles.archive(archive);
  String id=UUID.randomUUID().toString(),hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
  var row=new JsonObject();row.addProperty("id",id);row.addProperty("name","Duplicate");row.addProperty("sha256",hash);row.addProperty("size",bytes.length);row.addProperty("backedUp",true);row.addProperty("storageStoreId",1);row.addProperty("ownerStoreId",1);row.addProperty("assignmentVersion","sharing-1");
  boolean[] deleted={false};
  var library=new SharedPublicationLibrary(root.resolve("delete-cache"),"LABELS",request->{
   String action=request.get("action").getAsString();var result=new JsonObject();
   if(action.equals("LIST")){var rows=new JsonArray();if(!deleted[0])rows.add(row);result.add("templates",rows);result.addProperty("kind","LABELS");result.add("stores",new JsonArray());result.addProperty("defaultStoreId",1);return result;}
   if(action.equals("READ")){result.addProperty("bytes",Base64.getEncoder().encodeToString(bytes));result.addProperty("size",bytes.length);result.addProperty("sha256",hash);return result;}
   assertEquals("DELETE",action);assertEquals(id,request.get("id").getAsString());assertEquals(hash,request.get("expected").getAsString());assertEquals("sharing-1",request.get("expectedAssignment").getAsString());assertEquals(1,request.get("storageStoreId").getAsInt());deleted[0]=true;result.addProperty("deleted",true);return result;
  });
  library.refresh();assertEquals(1,library.files().size());library.delete(library.files().get(0));assertTrue(deleted[0]);assertTrue(library.files().isEmpty());
 }
 static class Server implements SharedPublicationLibrary.Transport {
  final Map<String,JsonObject> heads=new LinkedHashMap<>(),uploads=new HashMap<>();final Map<String,byte[]> archives=new HashMap<>();final Map<String,ByteArrayOutputStream> chunks=new HashMap<>();int saves,reads,blocks;boolean fail,corrupt,loseResponse;
  public JsonObject send(JsonObject r)throws Exception{
   if(fail)throw new IOException("Offline");String action=r.get("action").getAsString();JsonObject out=new JsonObject();
   if(action.equals("LIST")){out.addProperty("kind",r.get("kind").getAsString());JsonArray rows=new JsonArray();heads.values().forEach(v->rows.add(v.deepCopy()));out.add("templates",rows);return out;}
   if(action.equals("GET")){JsonObject row=heads.get(r.get("id").getAsString());if(row==null)throw new IOException("Missing");return row.deepCopy();}
   if(action.equals("READ")){reads++;String id=r.get("id").getAsString();var row=heads.get(id);if(!row.get("sha256").getAsString().equals(r.get("sha256").getAsString()))throw new IOException("Changed");byte[] all=archives.get(id);int offset=r.get("offset").getAsInt();byte[] data=Arrays.copyOfRange(all,offset,Math.min(all.length,offset+524288));if(corrupt)data[0]^=1;out.addProperty("bytes",Base64.getEncoder().encodeToString(data));out.addProperty("size",all.length);out.addProperty("sha256",row.get("sha256").getAsString());return out;}
   if(action.equals("BEGIN")){String upload=UUID.randomUUID().toString();uploads.put(upload,r.deepCopy());chunks.put(upload,new ByteArrayOutputStream());out.addProperty("uploadId",upload);return out;}
   String upload=r.get("uploadId").getAsString();
   if(action.equals("ABORT")){uploads.remove(upload);chunks.remove(upload);return out;}
   if(action.equals("CHUNK")){blocks++;assertEquals(chunks.get(upload).size(),r.get("offset").getAsInt());chunks.get(upload).write(Base64.getDecoder().decode(r.get("bytes").getAsString()));return out;}
   var meta=uploads.get(upload);String id=meta.get("id").getAsString(),expected=meta.get("expected").getAsString();
   if(!expected.equals(heads.containsKey(id)?heads.get(id).get("sha256").getAsString():""))throw new IOException("Conflict");
   byte[] bytes=chunks.get(upload).toByteArray();var p=PublicationFiles.read(bytes);PublicationTemplates.requireTemplate(p);
   out.addProperty("id",id);out.addProperty("name",p.title);out.addProperty("sha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));out.addProperty("contentSha256",PublicationTemplates.fingerprint(p));out.addProperty("size",bytes.length);out.addProperty("backedUp",false);heads.put(id,out.deepCopy());archives.put(id,bytes);saves++;
   if(loseResponse){loseResponse=false;throw new IOException("Lost response");}return out;
  }
 }
 Path local(String name)throws Exception{Path source=root.resolve(name+".sspub");Publication p=new Publication();p.title=name;p.mergeRows.add(Map.of("name","private merge row"));Files.write(source,PublicationFiles.archive(p));return source;}
 @Test void automaticallyMigratesOnceWithoutMergeRecordsOrDuplicatesAcrossComputers()throws Exception{
  Path source=local("School card");byte[] before=Files.readAllBytes(source);Server server=new Server();var first=new SharedPublicationLibrary(root.resolve("computer-1"),"BUSINESS_CARDS",server);first.refresh();
  assertEquals("Existing templates migrated to the server.",first.migrate(List.of(source),()->true));assertEquals(1,server.saves);assertEquals(1,first.files().size());assertArrayEquals(before,Files.readAllBytes(source));
  var stored=PublicationFiles.read(server.archives.values().iterator().next());assertTrue(stored.mergeRows.isEmpty());assertNull(stored.orderId);
  first.migrate(List.of(source,local("Added later")),()->true);assertEquals(1,server.saves);
  var second=new SharedPublicationLibrary(root.resolve("computer-2"),"BUSINESS_CARDS",server);second.refresh();second.migrate(List.of(source),()->true);assertEquals(1,server.saves);assertEquals(1,second.files().size());
 }
 @Test void lostSaveResponsesAndNewerServerEditsArePreserved()throws Exception{
  Path source=local("School");Server server=new Server();server.loseResponse=true;var library=new SharedPublicationLibrary(root.resolve("cache"),"LABELS",server);library.refresh();library.migrate(List.of(source),()->true);assertEquals(1,server.saves);
  Path current=library.files().get(0);String oldHash=BadgeTemplateLibrary.fingerprint(current);Publication edit=PublicationFiles.read(Files.readAllBytes(current));edit.title="Remote edit";library.save(edit,current,oldHash,()->true);assertEquals(2,server.saves);
  var other=new SharedPublicationLibrary(root.resolve("other"),"LABELS",server);other.refresh();other.migrate(List.of(source),()->true);assertEquals(2,server.saves);
  assertThrows(IOException.class,()->library.save(edit,current,oldHash,()->true));
 }
 @Test void offlineSavesAreRejectedAndCorruptDownloadsCannotReplaceTheCache()throws Exception{
  Server server=new Server();var library=new SharedPublicationLibrary(root.resolve("cache"),"LABELS",server);library.refresh();library.migrate(List.of(local("Label")),()->true);
  Path current=library.files().get(0);byte[] original=Files.readAllBytes(current);Publication p=PublicationFiles.read(original);
  server.fail=true;assertThrows(IOException.class,library::refresh);assertFalse(library.online);assertThrows(IOException.class,()->library.save(p,current,BadgeTemplateLibrary.fingerprint(current),()->true));assertArrayEquals(original,Files.readAllBytes(current));
  server.fail=false;server.corrupt=true;var fresh=new SharedPublicationLibrary(root.resolve("fresh"),"LABELS",server);assertThrows(IOException.class,fresh::refresh);assertTrue(fresh.files().isEmpty());
 }
 @Test void oldServersCannotTriggerMigrationOrUploadPublicationDataAsBadges()throws Exception{
  var library=new SharedPublicationLibrary(root.resolve("cache"),"LABELS",request->{assertEquals("LIST",request.get("action").getAsString());var out=new JsonObject();out.add("templates",new JsonArray());return out;});
  assertThrows(IOException.class,library::refresh);assertFalse(library.online);assertFalse(Files.exists(library.folder().resolve("local-template-migration-v1.json")));
 }
 @Test void largeArchiveUsesBoundedChunksAndSignedOutSessionCannotSave()throws Exception{
  Server server=new Server();var library=new SharedPublicationLibrary(root.resolve("cache"),"LABELS",server);library.refresh();
  Publication p=new Publication();byte[] data=new byte[900000];new Random(42).nextBytes(data);p.assets.put(UUID.randomUUID().toString(),Base64.getEncoder().encodeToString(data));
  var saved=library.save(p,null,"",()->true);assertTrue(server.blocks>=2);assertEquals(saved.sha256(),BadgeTemplateLibrary.fingerprint(saved.file()));
  assertThrows(IOException.class,()->library.save(p,null,"",()->false));
 }
 @Test void migrationRetriesInvalidFilesWithoutRepeatingCompletedTemplates()throws Exception{
  Path source=local("Good"),broken=root.resolve("Broken.sspub");Files.writeString(broken,"incomplete");Server server=new Server();var library=new SharedPublicationLibrary(root.resolve("cache"),"LABELS",server);library.refresh();
  assertTrue(library.migrate(List.of(source,broken),()->true).contains("pending"));assertEquals(1,server.saves);
  Files.write(broken,PublicationFiles.archive(new Publication()));library.migrate(List.of(source,broken),()->true);assertEquals(2,server.saves);
 }
}

