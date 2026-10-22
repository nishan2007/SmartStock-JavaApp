package services;
import com.google.gson.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class StudioFontLibraryTest {
 @TempDir Path root;
 JsonObject request(String action){var r=new JsonObject();r.addProperty("action",action);return r;}
 @Test void administrationAndOwnershipAreRequired()throws Exception{
  var library=new StudioFontLibrary(root);assertThrows(SecurityException.class,()->library.execute(0,false,request("LIST")));
  var begin=request("BEGIN");begin.addProperty("name","test.ttf");begin.addProperty("size",4);
  assertThrows(SecurityException.class,()->library.execute(1,false,begin));
  String id=library.execute(1,true,begin).get("uploadId").getAsString();var chunk=request("CHUNK");chunk.addProperty("uploadId",id);chunk.addProperty("offset",0);chunk.addProperty("bytes","AAAAAA==");
  assertThrows(SecurityException.class,()->library.execute(2,true,chunk));library.execute(1,true,chunk);
  assertThrows(IllegalArgumentException.class,()->library.execute(1,true,chunk));
  var finish=request("FINISH");finish.addProperty("uploadId",id);assertThrows(Exception.class,()->library.execute(1,true,finish));assertEquals(0,library.execute(1,false,request("LIST")).getAsJsonArray("fonts").size());
 }
 @Test void downloadCannotEscapeStorage()throws Exception{var r=request("READ");r.addProperty("sha256","../catalog.json");r.addProperty("offset",0);assertThrows(IllegalArgumentException.class,()->new StudioFontLibrary(root).execute(1,false,r));}
 @Test void validFontIsRetainedAcrossServerRestartAndDuplicateUpload()throws Exception{
  Path source=Path.of(System.getProperty("os.name").startsWith("Windows")?"C:/Windows/Fonts/arial.ttf":"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");Assumptions.assumeTrue(Files.isRegularFile(source),"Requires a local TrueType font");byte[] bytes=Files.readAllBytes(source);var library=new StudioFontLibrary(root);
  for(int attempt=0;attempt<2;attempt++){
   var begin=request("BEGIN");begin.addProperty("name","original.ttf");begin.addProperty("license","Test font notice");begin.addProperty("size",bytes.length);String id=library.execute(1,true,begin).get("uploadId").getAsString();
   for(int offset=0;offset<bytes.length;offset+=StudioFontLibrary.CHUNK){var chunk=request("CHUNK");chunk.addProperty("uploadId",id);chunk.addProperty("offset",offset);chunk.addProperty("bytes",java.util.Base64.getEncoder().encodeToString(java.util.Arrays.copyOfRange(bytes,offset,Math.min(bytes.length,offset+StudioFontLibrary.CHUNK))));library.execute(1,true,chunk);}
   var finish=request("FINISH");finish.addProperty("uploadId",id);assertEquals(StudioFontLibrary.sha256(bytes),library.execute(1,true,finish).get("sha256").getAsString());
  }
  library=new StudioFontLibrary(root);assertEquals(1,library.execute(2,false,request("LIST")).getAsJsonArray("fonts").size());assertEquals("Test font notice",library.execute(2,false,request("LIST")).getAsJsonArray("fonts").get(0).getAsJsonObject().get("license").getAsString());var read=request("READ");read.addProperty("sha256",StudioFontLibrary.sha256(bytes));read.addProperty("offset",0);assertArrayEquals(java.util.Arrays.copyOf(bytes,Math.min(bytes.length,StudioFontLibrary.CHUNK)),java.util.Base64.getDecoder().decode(library.execute(2,false,read).get("bytes").getAsString()));
 }
}
