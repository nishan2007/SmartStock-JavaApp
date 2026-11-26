package publishing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class PublicationLayerVisibilityTest {
    @TempDir Path root;
    @Test void hiddenLayersPersistAndAreExcludedFromRasterAndPdf()throws Exception{
        Publication document=new Publication();document.width=document.height=50;
        document.print.width=document.print.height=50;document.print.left=document.print.right=document.print.top=document.print.bottom=0;document.print.quantity=1;
        var element=new Publication.Element();element.type="RECT";element.x=element.y=0;element.width=element.height=50;element.fill=element.stroke="#ff0000";document.pages.get(0).objects.add(element);
        assertEquals(0xff0000,new PublicationRenderer(document).raster(0,72).getRGB(40,40)&0xffffff);
        element.hidden=true;var saved=PublicationFiles.read(PublicationFiles.archive(document));
        assertTrue(saved.pages.get(0).objects.get(0).hidden);
        assertEquals(0xffffff,new PublicationRenderer(saved).raster(0,72).getRGB(40,40)&0xffffff);
        Path pdf=root.resolve("hidden.pdf");PublicationFiles.pdf(saved,pdf,0,0);
        try(var exported=org.apache.pdfbox.pdmodel.PDDocument.load(pdf.toFile())){assertEquals(0xffffff,new org.apache.pdfbox.rendering.PDFRenderer(exported).renderImage(0).getRGB(40,40)&0xffffff);}
        saved.pages.get(0).objects.get(0).hidden=false;
        assertEquals(0xff0000,new PublicationRenderer(saved).raster(0,72).getRGB(40,40)&0xffffff);
        assertFalse(Publication.JSON.fromJson("{}",Publication.Element.class).hidden,"Old files remain visible by default");
    }
}
