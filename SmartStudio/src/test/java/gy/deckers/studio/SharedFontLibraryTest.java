package gy.deckers.studio;
import com.google.gson.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import publishing.PublicationFonts;
import services.StudioFontLibrary;
import static org.junit.jupiter.api.Assertions.*;
class SharedFontLibraryTest {
 @TempDir Path root;
 @AfterEach void clear(){PublicationFonts.sharedFonts(java.util.List.of());}
 @Test void twoComputersVerifyTheSameBytesAndWorkOffline()throws Exception{
  Path source=Path.of(System.getProperty("os.name").startsWith("Windows")?"C:/Windows/Fonts/arial.ttf":"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");Assumptions.assumeTrue(Files.isRegularFile(source),"Requires a local TrueType font");byte[] bytes=Files.readAllBytes(source);var row=new JsonObject();row.addProperty("sha256",StudioFontLibrary.sha256(bytes));row.addProperty("size",bytes.length);var catalog=new JsonObject();catalog.addProperty("version",1);var rows=new JsonArray();rows.add(row);catalog.add("fonts",rows);
  SharedFontLibrary.Transport transport=r->{if(r.get("action").getAsString().equals("LIST"))return catalog;int offset=r.get("offset").getAsInt();var result=new JsonObject();result.addProperty("bytes",java.util.Base64.getEncoder().encodeToString(java.util.Arrays.copyOfRange(bytes,offset,Math.min(bytes.length,offset+StudioFontLibrary.CHUNK))));return result;};
  var first=new SharedFontLibrary(root.resolve("first"),transport);var second=new SharedFontLibrary(root.resolve("second"),transport);assertTrue(first.sync()>0);assertEquals(first.cached(),second.sync());assertArrayEquals(Files.readAllBytes(root.resolve("first").resolve(row.get("sha256").getAsString()+".font")),Files.readAllBytes(root.resolve("second").resolve(row.get("sha256").getAsString()+".font")));
  assertTrue(new SharedFontLibrary(root.resolve("second"),r->{throw new java.io.IOException("offline");}).cached()>0);
  Files.write(root.resolve("second").resolve(row.get("sha256").getAsString()+".font"),new byte[]{1});assertThrows(Exception.class,second::cached);
 }
 @Test void invalidCatalogueCannotTraverseCache()throws Exception{var c=new JsonObject();c.addProperty("version",1);var rows=new JsonArray();var r=new JsonObject();r.addProperty("sha256","../escape");r.addProperty("size",10);rows.add(r);c.add("fonts",rows);assertThrows(IllegalArgumentException.class,()->new SharedFontLibrary(root,x->c).sync());assertFalse(Files.exists(root.resolve("catalog.json")));}
 @Test void pendingBatchRetainsNoticesAndResumesWithoutDuplicateUploads()throws Exception{
  Path source=Path.of(System.getProperty("os.name").startsWith("Windows")?"C:/Windows/Fonts/arial.ttf":"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");Assumptions.assumeTrue(Files.isRegularFile(source));Path pending=Files.createDirectories(root.resolve("pending"));Files.copy(source,pending.resolve("font.ttf"));Files.writeString(pending.resolve("OFL.txt"),"Retained notice");byte[] bytes=Files.readAllBytes(source);var count=new java.util.concurrent.atomic.AtomicInteger();
  SharedFontLibrary.Transport transport=r->{var result=new JsonObject();switch(r.get("action").getAsString()){case "BEGIN"->{assertEquals("Retained notice",r.get("license").getAsString());result.addProperty("uploadId","test");}case "FINISH"->{count.incrementAndGet();result.addProperty("sha256",StudioFontLibrary.sha256(bytes));result.addProperty("size",bytes.length);}}return result;};
  assertEquals(1,new SharedFontLibrary(root.resolve("cache"),transport).importPending(pending));assertEquals(0,new SharedFontLibrary(root.resolve("cache"),transport).importPending(pending));assertEquals(1,count.get());assertTrue(Files.exists(pending.resolve("font.ttf")));
 }
}
