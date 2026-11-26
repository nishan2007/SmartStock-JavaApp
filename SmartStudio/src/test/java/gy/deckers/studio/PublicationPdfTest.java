package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import publishing.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class PublicationPdfTest {
    @TempDir Path folder;
    private Publication document(){
        var p=new Publication();p.width=25.4;p.height=12.7;
        p.print.width=76.2;p.print.height=50.8;p.print.left=p.print.right=p.print.top=p.print.bottom=6.35;
        p.print.rotate=false;p.print.gapX=p.print.gapY=0;p.print.quantity=1;
        return p;
    }
    @Test void preservesOriginalImageResolutionInsteadOfFlatteningTo300Dpi()throws Exception{
        var p=document();var image=new BufferedImage(1200,600,BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();g.setColor(java.awt.Color.GREEN);g.fillRect(0,0,1200,600);g.dispose();
        PublicationImport.addImage(p,p.pages.get(0),image,0,0,p.width,p.height);
        Path file=folder.resolve("original-resolution.pdf");PublicationPdf.write(p,file,0,0);
        try(var pdf=PDDocument.load(file.toFile())){
            assertEquals(1,pdf.getNumberOfPages());assertEquals(216,pdf.getPage(0).getMediaBox().getWidth(),.001);
            assertEquals("None",pdf.getDocumentCatalog().getViewerPreferences().getPrintScaling());
            assertEquals("Simplex",pdf.getDocumentCatalog().getViewerPreferences().getDuplex());
            assertTrue(hasOriginalImage(pdf.getPage(0).getResources()));
            var rendered=new PDFRenderer(pdf).renderImageWithDPI(0,100);
            int green=0;for(int y=0;y<rendered.getHeight();y++)for(int x=0;x<rendered.getWidth();x++)if((rendered.getRGB(x,y)&0xffffff)==0x00ff00)green++;
            assertEquals(5000,green,200,"One by half inch image must retain its physical dimensions");
        }
    }
    private boolean hasOriginalImage(PDResources resources)throws Exception{
        for(var name:resources.getXObjectNames()){
            var object=resources.getXObject(name);
            if(object instanceof PDImageXObject image&&image.getWidth()==1200&&image.getHeight()==600)return true;
            if(object instanceof PDFormXObject form&&hasOriginalImage(form.getResources()))return true;
        }return false;
    }
    @Test void vectorArtworkIsNotConvertedIntoAnImage()throws Exception{
        var p=document();var rect=new Publication.Element();rect.type="RECT";rect.width=20;rect.height=10;rect.fill="#00ff00";p.pages.get(0).objects.add(rect);
        Path file=folder.resolve("vector.pdf");PublicationPdf.write(p,file,0,0);
        try(var pdf=PDDocument.load(file.toFile())){assertNoImages(pdf.getPage(0).getResources());}
    }
    private void assertNoImages(PDResources resources)throws Exception{
        for(var name:resources.getXObjectNames()){
            var object=resources.getXObject(name);assertFalse(object instanceof PDImageXObject);
            if(object instanceof PDFormXObject form)assertNoImages(form.getResources());
        }
    }
    @Test void invalidRangePreservesExistingFile()throws Exception{
        Path file=folder.resolve("existing.pdf");Files.writeString(file,"previous");
        assertThrows(java.io.IOException.class,()->PublicationPdf.write(document(),file,1,0));
        assertEquals("previous",Files.readString(file));
    }
    @Test void repeatedCardsReuseOneOriginalImageResource()throws Exception{
        var p=document();p.print.quantity=2;
        var image=new BufferedImage(1200,600,BufferedImage.TYPE_INT_RGB);
        PublicationImport.addImage(p,p.pages.get(0),image,0,0,p.width,p.height);
        Path file=folder.resolve("repeated.pdf");PublicationPdf.write(p,file,0,0);
        try(var pdf=PDDocument.load(file.toFile())){
            var images=new java.util.HashSet<org.apache.pdfbox.cos.COSBase>();
            collectImages(pdf.getPage(0).getResources(),images);assertEquals(1,images.size());
        }
    }
    private void collectImages(PDResources resources,java.util.Set<org.apache.pdfbox.cos.COSBase> images)throws Exception{
        for(var name:resources.getXObjectNames()){
            var object=resources.getXObject(name);
            if(object instanceof PDImageXObject image)images.add(image.getCOSObject());
            if(object instanceof PDFormXObject form)collectImages(form.getResources(),images);
        }
    }
    @Test void printJobKeepsSelectedPagesDuplexAndPhysicalSheetSize()throws Exception{
        var p=document();p.pages.add(new Publication.Page());p.print.duplex=true;p.print.backMapping="SHORT_EDGE";
        Path file=folder.resolve("duplex.pdf");PublicationPdf.write(p,file,0,new PublicationRenderer(p).sheets()-1);
        try(var pdf=PDDocument.load(file.toFile())){
            assertEquals(2,pdf.getNumberOfPages());assertEquals(216,pdf.getPage(0).getMediaBox().getWidth(),.001);
            assertEquals("None",pdf.getDocumentCatalog().getViewerPreferences().getPrintScaling());
            assertEquals(PublicationPrintOptions.sides(p)==javax.print.attribute.standard.Sides.TWO_SIDED_SHORT_EDGE?"DuplexFlipShortEdge":"DuplexFlipLongEdge",pdf.getDocumentCatalog().getViewerPreferences().getDuplex());
        }
    }
}
