package gy.deckers.studio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import publishing.*;
import java.awt.Color;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RisoOutputTest {
    @TempDir Path temp;
    Publication document(){var p=new Publication();p.width=40;p.height=30;p.print.width=100;p.print.height=100;p.print.quantity=1;p.print.alignment="TOP_LEFT";p.print.cropMarks=false;p.print.left=p.print.right=p.print.top=p.print.bottom=15;var e=new Publication.Element();e.type="RECT";e.fill="#008000";e.strokeWidth=0;e.width=20;e.height=20;p.pages.get(0).objects.add(e);p.validate();return p;}
    List<RisoSeparation.Ink> inks(){return List.of(new RisoSeparation.Ink("Black",Color.BLACK,true),new RisoSeparation.Ink("Green",new Color(0,128,0),true));}
    @Test void pdfPreservesPhysicalSizeGrayscaleInkOrderAndOriginal()throws Exception{
        var p=document();String before=Publication.JSON.toJson(p);Path path=temp.resolve("masters.pdf");RisoOutput.pdf(p,inks(),false,path);
        try(var pdf=PDDocument.load(path.toFile())){assertEquals(2,pdf.getNumberOfPages());assertEquals(100*72/25.4,pdf.getPage(0).getMediaBox().getWidth(),.001);assertEquals("Black • Sheet 1",pdf.getDocumentCatalog().getPageLabels().getLabelsByPageIndices()[0]);assertEquals("Green • Sheet 1",pdf.getDocumentCatalog().getPageLabels().getLabelsByPageIndices()[1]);for(var page:pdf.getPages()){var names=page.getResources().getXObjectNames().iterator();var image=(PDImageXObject)page.getResources().getXObject(names.next());assertEquals(1,image.getColorSpace().getNumberOfComponents());assertEquals((int)Math.ceil(100/25.4*300),image.getWidth());}}
        assertEquals(before,Publication.JSON.toJson(p));
    }
    @Test void identicalRegistrationMarksAppearOnEveryInk(){var p=document();for(int i=0;i<2;i++){var image=RisoOutput.plate(p,0,254,inks(),i,true);assertEquals(0,image.getRaster().getSample(50,50,0));}p.print.left=0;p.print.top=0;assertThrows(IllegalArgumentException.class,()->RisoOutput.plate(p,0,30,inks(),0,true));}
    @Test void failedExportPreservesExistingFile()throws Exception{var p=document();p.print.left=0;p.print.top=0;Path path=temp.resolve("existing.pdf");Files.writeString(path,"original");assertThrows(IllegalArgumentException.class,()->RisoOutput.pdf(p,inks(),true,path));assertEquals("original",Files.readString(path));try(var files=Files.list(temp)){assertEquals(1,files.count());}}
}
