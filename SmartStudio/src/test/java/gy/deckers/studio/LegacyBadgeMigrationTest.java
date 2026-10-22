package gy.deckers.studio;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyBadgeMigrationTest {
 @TempDir Path root;
 static final Gson JSON=new Gson();
 private Path template(Path folder,String filename,String name)throws Exception{
  Files.createDirectories(folder);BadgeTemplate design=new BadgeTemplate();design.name=name;
  Path file=folder.resolve(filename);design.save(file);return file;
 }
 static class Server implements SharedBadgeLibrary.Transport {
  final Map<String,JsonObject> heads=new LinkedHashMap<>();int saves;boolean deny,loseResponse;
  public JsonObject send(JsonObject request)throws Exception{
   String action=request.get("action").getAsString();
   if(action.equals("LIST")){JsonObject response=new JsonObject();JsonArray entries=new JsonArray();heads.values().forEach(entries::add);response.add("templates",entries);return response;}
   String id=request.get("id").getAsString();
   if(action.equals("GET")){if(!heads.containsKey(id))throw new java.io.IOException("Missing");return heads.get(id).deepCopy();}
   if(deny)throw new java.io.IOException("Permission denied");
   if(heads.containsKey(id))throw new java.io.IOException("Conflict");
   saves++;String content=request.get("content").getAsString();JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))));row.addProperty("content",content);heads.put(id,row);
   if(loseResponse){loseResponse=false;throw new java.io.IOException("Lost response");}return row;
  }
 }
 @Test void importsOnceAcrossRetriesAndComputersAndNeverChangesOriginals()throws Exception{
  Path source=root.resolve("old");Path file=template(source,"School.sstbadge","School");byte[] original=Files.readAllBytes(file);
  Server server=new Server();var migration=new LegacyBadgeMigration(root.resolve("cache"),server);
  var first=migration.run(source,()->true);assertTrue(first.finished());assertEquals(1,first.imported());
  assertTrue(migration.run(source,()->true).finished());assertEquals(1,server.saves);
  assertTrue(new LegacyBadgeMigration(root.resolve("another-computer"),server).run(source,()->true).finished());assertEquals(1,server.saves);
  template(source,"Added later.sstbadge","Later");migration.run(source,()->true);assertEquals(1,server.saves,"A completed one-time snapshot must not watch future local files");
  assertArrayEquals(original,Files.readAllBytes(file));
 }
 @Test void partialFailuresRetryWithoutRepeatingSuccessfulUploads()throws Exception{
  Path source=root.resolve("old");template(source,"Good.sstbadge","Good");Files.writeString(source.resolve("Broken.sstbadge"),"broken");
  Server server=new Server();var migration=new LegacyBadgeMigration(root.resolve("cache"),server);
  var first=migration.run(source,()->true);assertFalse(first.finished());assertEquals(1,first.pending());assertEquals(1,server.saves);
  template(source,"Broken.sstbadge","Repaired");var next=migration.run(source,()->true);assertTrue(next.finished());assertEquals(2,server.saves);
 }
 @Test void deniedPermissionsAndUnavailableFoldersDoNotCompleteMigration()throws Exception{
  Path source=root.resolve("old");Server server=new Server();server.deny=true;
  var migration=new LegacyBadgeMigration(root.resolve("cache"),server);
  assertFalse(migration.run(source,()->true).finished());template(source,"School.sstbadge","School");
  assertFalse(migration.run(source,()->true).finished());assertEquals(0,server.saves);
  server.deny=false;assertTrue(migration.run(source,()->true).finished());assertEquals(1,server.saves);
 }
 @Test void lostResponseAndEditedServerHeadArePreserved()throws Exception{
  Path source=root.resolve("old");template(source,"School.sstbadge","School");Server server=new Server();server.loseResponse=true;
  assertTrue(new LegacyBadgeMigration(root.resolve("cache"),server).run(source,()->true).finished());assertEquals(1,server.saves);
  JsonObject row=server.heads.values().iterator().next();row.addProperty("sha256","changed remotely");row.addProperty("content","newer server design");
  assertTrue(new LegacyBadgeMigration(root.resolve("another-computer"),server).run(source,()->true).finished());
  assertEquals(1,server.saves);assertEquals("newer server design",row.get("content").getAsString());
 }
 @Test void inactiveSessionCannotSendAnyRequests()throws Exception{
  Path source=root.resolve("old");template(source,"School.sstbadge","School");
  var migration=new LegacyBadgeMigration(root.resolve("cache"),r->{fail("Signed out session must not upload");return null;});
  assertFalse(migration.run(source,()->false).finished());
 }
 @Test void alreadySharedIdenticalTemplateWithAnOlderRandomIdentityIsNotDuplicated()throws Exception{
  Path source=root.resolve("old");Path file=template(source,"School.sstbadge","School");Server server=new Server();
  String content=BadgeTemplate.portableJson(BadgeTemplate.load(file));JsonObject existing=new JsonObject();existing.addProperty("id",UUID.randomUUID().toString());existing.addProperty("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))));server.heads.put(existing.get("id").getAsString(),existing);
  assertTrue(new LegacyBadgeMigration(root.resolve("cache"),server).run(source,()->true).finished());assertEquals(0,server.saves);
 }
}
