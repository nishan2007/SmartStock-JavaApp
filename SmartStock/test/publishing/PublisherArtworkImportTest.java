package publishing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublisherArtworkImportTest {
  @TempDir Path folder;
  @Test void multiPageLabelLibrariesCanRetainMoreThan500IndependentAssets()throws Exception{
    var p=new Publication();var image=new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_RGB);
    for(int i=0;i<510;i++)PublicationImport.addImage(p,p.pages.get(0),image,0,0,1,1);
    assertEquals(510,PublicationFiles.read(PublicationFiles.archive(p)).assets.size());
  }
  @Test void unsupportedEmbeddedArtworkRetainsCompleteReferenceInsteadOfAborting() throws Exception {
    Path odg=folder.resolve("artwork.odg"),pdf=folder.resolve("artwork.pdf");
    String content="""
        <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
          xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
          xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"
          xmlns:xlink="http://www.w3.org/1999/xlink">
          <draw:page><draw:frame svg:x="0cm" svg:y="0cm" svg:width="8cm" svg:height="5cm">
            <draw:image xlink:href="Pictures/vector.wmf"/>
          </draw:frame></draw:page>
        </office:document-content>
        """;
    try(var zip=new ZipOutputStream(Files.newOutputStream(odg))){
      zip.putNextEntry(new ZipEntry("content.xml"));zip.write(content.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
      zip.putNextEntry(new ZipEntry("Pictures/vector.wmf"));zip.write(new byte[]{1,2,3,4});zip.closeEntry();
    }
    try(var doc=new PDDocument()){doc.addPage(new PDPage(new PDRectangle(252,144)));doc.save(pdf.toFile());}
    var result=PublicationImport.odg(odg,pdf);var p=result.publication();p.validate();
    assertEquals(2,p.pages.size());assertFalse(p.pages.get(0).reference);assertTrue(p.pages.get(1).reference);
    assertEquals("IMAGE",p.pages.get(1).objects.get(0).type);
    assertFalse(p.assets.isEmpty());assertTrue(result.warnings().stream().anyMatch(w->w.contains("unsupported")));
  }
}
