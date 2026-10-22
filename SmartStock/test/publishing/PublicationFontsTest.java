package publishing;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PublicationFontsTest {
 @Test void sharedBoldFaceUsesTheDownloadedBoldFile()throws Exception{
  var windows=System.getProperty("os.name").startsWith("Windows");
  var regular=java.nio.file.Path.of(windows?"C:/Windows/Fonts/arial.ttf":"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");
  var bold=java.nio.file.Path.of(windows?"C:/Windows/Fonts/arialbd.ttf":"/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf");
  org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isRegularFile(regular)&&java.nio.file.Files.isRegularFile(bold));
  var r=java.awt.Font.createFonts(regular.toFile())[0];var b=java.awt.Font.createFonts(bold.toFile())[0];
  try{PublicationFonts.sharedFonts(java.util.List.of(r,b));assertEquals(b.getPSName(),PublicationFonts.create(r.getFamily(java.util.Locale.ROOT),java.awt.Font.BOLD,12).getPSName());assertEquals(r.getPSName(),PublicationFonts.create(r.getFamily(java.util.Locale.ROOT),java.awt.Font.PLAIN,12).getPSName());}
  finally{PublicationFonts.sharedFonts(java.util.List.of());}
 }
 @Test void resolvesCorsivaNamingVariantWithoutChangingOtherTypefaceNames(){
  var installed=Set.of("Monotype Corsiva Regular","Bookman Old Style");
  assertEquals("Monotype Corsiva Regular",PublicationFonts.family("Monotype Corsiva",installed));
  assertEquals("Bookman Old Style",PublicationFonts.family("Bookman Old Style",installed));
  assertEquals("Missing script",PublicationFonts.family("Missing script",installed));
  assertEquals("Monotype Corsiva",PublicationFonts.family("Monotype Corsiva",Set.of("Monotype Corsiva","Monotype Corsiva Regular")));
 }
}
