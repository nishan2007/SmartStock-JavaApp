package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import publishing.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

class BannerDocumentsTest {
    static java.util.List<org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject> images(org.apache.pdfbox.pdmodel.PDResources resources)throws Exception {
        var result=new java.util.ArrayList<org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject>();
        if(resources!=null)for(var name:resources.getXObjectNames()){
            var object=resources.getXObject(name);
            if(object instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject image)result.add(image);
            else if(object instanceof org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject form)result.addAll(images(form.getResources()));
        }return result;
    }
    @TempDir Path temp;
    static Publication sample(double feetWidth,double feetHeight) {
        Publication p=BannerDocuments.create(feetWidth,feetHeight,"FT");
        Publication.Element background=new Publication.Element();background.type="RECT";background.x=p.width*.1;background.y=p.height*.1;background.width=p.width*.8;background.height=p.height*.8;background.fill="#2244cc";
        p.pages.get(0).objects.add(background);
        Publication.Element text=new Publication.Element();text.text="Banner test";text.x=p.width*.2;text.y=p.height*.3;text.width=p.width*.6;text.height=p.height*.4;text.fontSize=Math.min(10000,p.height*.3);text.color="#ffffff";text.fitText=true;
        p.pages.get(0).objects.add(text);p.validate();return p;
    }
    @Test void createEditSaveReopenAndExportLargeBanners()throws Exception {
        for(double[] size:new double[][]{{20,4},{100,10},{10,100},{300,30},{30,300}}) {
            Publication p=sample(size[0],size[1]);
            p.pages.get(0).objects.get(1).text="Edited banner";
            Path archive=temp.resolve("banner-"+size[0]+".sspub");
            PublicationFiles.writeAtomic(archive,PublicationFiles.archive(p));
            Publication reopened=PublicationFiles.read(Files.readAllBytes(archive));
            assertEquals(p.width,reopened.width,1e-8);assertEquals(p.height,reopened.height,1e-8);
            assertEquals("FT",reopened.displayUnit);assertEquals("Edited banner",reopened.pages.get(0).objects.get(1).text);
            BufferedImage thumbnail=PublicationTemplateLibrary.preview(reopened,0);
            assertTrue(thumbnail.getWidth()<=651&&thumbnail.getHeight()<=441);
            Path pdf=temp.resolve("banner-"+size[0]+".pdf");BannerDocuments.pdf(reopened,pdf);
            try(var doc=PDDocument.load(pdf.toFile())) {
                var page=doc.getPage(0);
                assertEquals(p.width,page.getMediaBox().getWidth()*page.getUserUnit()*25.4/72,.02);
                assertEquals(p.height,page.getMediaBox().getHeight()*page.getUserUnit()*25.4/72,.02);
                assertTrue(images(page.getResources()).isEmpty(),"Vector design must not become a full-page bitmap");
                // Render a bounded proof using base PDF coordinates, never actual-size 300 DPI.
                float scale=800f/Math.max(page.getMediaBox().getWidth(),page.getMediaBox().getHeight());
                BufferedImage proof=new PDFRenderer(doc).renderImage(0,scale);
                assertTrue(proof.getWidth()<=801);assertTrue(proof.getHeight()<=801);
                assertEquals(new Color(0x2244cc).getRGB(),proof.getRGB(proof.getWidth()/2,Math.max(1,proof.getHeight()/5)));
                if(Boolean.getBoolean("smartstudio.bannerProofs")) {
                    Path proofs=Path.of("target","banner-validation");Files.createDirectories(proofs);
                    javax.imageio.ImageIO.write(proof,"png",proofs.resolve("banner-"+size[0]+".png").toFile());
                    Files.copy(pdf,proofs.resolve(pdf.getFileName()),StandardCopyOption.REPLACE_EXISTING);
                    Files.copy(archive,proofs.resolve(archive.getFileName()),StandardCopyOption.REPLACE_EXISTING);
                }
            }
            assertTrue(Files.size(pdf)<1_000_000,"Vector export size must not grow with physical area");
        }
    }
    @Test void unitsAndSizeValidation() {
        for(String unit:new String[]{"FT","IN","CM","MM"}) {
            Publication p=BannerDocuments.create(Publication.fromMm(30480,unit),Publication.fromMm(3048,unit),unit);
            assertEquals(30480,p.width,1e-7);assertEquals(3048,p.height,1e-7);
        }
        for(double value:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,1000001})assertThrows(IllegalArgumentException.class,()->BannerDocuments.create(value,10,"MM"));
    }
    @Test void exportedPicturesKeepTheirSourceResolutionAndMultiplePagesKeepPhysicalSize()throws Exception {
        Publication p=sample(100,10);
        BufferedImage picture=new BufferedImage(64,32,BufferedImage.TYPE_INT_RGB);
        Graphics2D paint=picture.createGraphics();paint.setColor(Color.RED);paint.fillRect(0,0,64,32);paint.dispose();
        PublicationImport.addImage(p,p.pages.get(0),picture,p.width*.8,p.height*.6,p.width*.1,p.height*.2);
        var second=new Publication.Page();second.name="Second banner";p.pages.add(second);
        Path pdf=temp.resolve("pictures.pdf");BannerDocuments.pdf(p,pdf);
        try(var doc=PDDocument.load(pdf.toFile())) {
            assertEquals(2,doc.getNumberOfPages());
            int pictures=0;
            for(var image:images(doc.getPage(0).getResources())) {
                assertTrue(image.getWidth()<=64);assertTrue(image.getHeight()<=32);pictures++;
            }
            assertTrue(pictures>0);
            for(var page:doc.getPages())assertEquals(p.width,page.getMediaBox().getWidth()*page.getUserUnit()*25.4/72,.02);
            BufferedImage proof=new PDFRenderer(doc).renderImage(0,800f/doc.getPage(0).getMediaBox().getWidth());
            assertEquals(Color.RED.getRGB(),proof.getRGB((int)(proof.getWidth()*.85),(int)(proof.getHeight()*.7)));
        }
    }
    @Test void failedExportPreservesExistingFileAndCleansTemporaryOutput()throws Exception {
        Path target=temp.resolve("existing.pdf");Files.writeString(target,"keep this");
        Publication p=sample(100,10);p.pages.get(0).reference=true;
        assertThrows(java.io.IOException.class,()->BannerDocuments.pdf(p,target));
        assertEquals("keep this",Files.readString(target));
        try(var files=Files.list(temp)){assertEquals(1,files.count());}
    }
    @Test void giantWordArtGradientExportsAsVectorShading()throws Exception {
        Publication p=sample(300,30);var word=p.pages.get(0).objects.get(1);word.wordArt="PLAIN";word.wordArtGradient=true;word.color="#ff0000";word.wordArtEndColor="#0000ff";
        Path pdf=temp.resolve("gradient.pdf");BannerDocuments.pdf(p,pdf);
        try(var doc=PDDocument.load(pdf.toFile())) {
            assertTrue(images(doc.getPage(0).getResources()).isEmpty());
            BufferedImage proof=new PDFRenderer(doc).renderImage(0,800f/doc.getPage(0).getMediaBox().getWidth());
            if(Boolean.getBoolean("smartstudio.bannerProofs"))javax.imageio.ImageIO.write(proof,"png",Path.of("target","banner-validation","gradient.png").toFile());
        }
    }
    @Test void hugeCanvasFitsAndZoomRemainsViewportBounded()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            Publication p=sample(300,30);TemplateWorkspace canvas=new TemplateWorkspace();canvas.setPageFocus(true);
            canvas.configure(new TemplateWorkspace.Design(){
                public double width(){return p.width;}public double height(){return p.height;}public int size(){return 0;}
                public java.awt.geom.Rectangle2D bounds(int i){throw new AssertionError();}public void move(int i,double x,double y){}
                public void paint(Graphics2D g,double scale){g.scale(scale,scale);new PublicationRenderer(p).page(g,0,java.util.Map.of());}
            },i->{},()->{});
            JScrollPane scroll=new JScrollPane(canvas);scroll.setSize(1000,500);scroll.doLayout();canvas.setSize(scroll.getViewport().getExtentSize());
            BufferedImage view=new BufferedImage(1000,500,BufferedImage.TYPE_INT_RGB);Graphics2D g=view.createGraphics();try{canvas.paint(g);}finally{g.dispose();}
            assertTrue(canvas.getPreferredSize().width<=1000);
            canvas.setZoom(8);assertTrue(canvas.getPreferredSize().width<=8000);canvas.setSize(canvas.getPreferredSize());
            long now=System.currentTimeMillis();
            canvas.dispatchEvent(new java.awt.event.MouseEvent(canvas,java.awt.event.MouseEvent.MOUSE_PRESSED,now,java.awt.event.InputEvent.BUTTON2_DOWN_MASK,100,100,500,500,1,false,java.awt.event.MouseEvent.BUTTON2));
            canvas.dispatchEvent(new java.awt.event.MouseEvent(canvas,java.awt.event.MouseEvent.MOUSE_DRAGGED,now,java.awt.event.InputEvent.BUTTON2_DOWN_MASK,50,50,450,450,0,false,java.awt.event.MouseEvent.NOBUTTON));
            assertEquals(new Point(50,50),scroll.getViewport().getViewPosition());
            canvas.dispatchEvent(new java.awt.event.MouseEvent(canvas,java.awt.event.MouseEvent.MOUSE_RELEASED,now,0,50,50,450,450,1,false,java.awt.event.MouseEvent.BUTTON2));
            canvas.setZoom(1);assertTrue(canvas.getPreferredSize().width<=1000);
        });
    }
    @Test void bannerEditorReusesHistoryAndPreservesSizeThroughTemplateFiles()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            BusinessCardsPanel panel=new BusinessCardsPanel(false,true);
            for(double[] dimensions:new double[][]{{30480,3048},{91440,9144}}) {
                panel.resizeTemplate(dimensions[0],dimensions[1],false);
                assertEquals(dimensions[0],panel.output().width,1e-7);
                panel.historyAction(false);panel.historyAction(true);
                Publication p=panel.output();
                try {
                    Path file=temp.resolve("editor.sspub");
                    BusinessCardsPanel.writeTemplate(file,p,null);
                    Publication reopened=BusinessCardsPanel.readTemplate(file,null,false);
                    assertEquals(dimensions[0],reopened.width,1e-7);assertEquals(dimensions[1],reopened.height,1e-7);
                }catch(Exception ex){throw new RuntimeException(ex);}
            }
            panel.setSize(1400,900);layout(panel);
            BufferedImage view=new BufferedImage(1400,900,BufferedImage.TYPE_INT_RGB);Graphics2D g=view.createGraphics();try{panel.printAll(g);}finally{g.dispose();}
            if(Boolean.getBoolean("smartstudio.bannerProofs"))try{Path proofs=Path.of("target","banner-validation");Files.createDirectories(proofs);javax.imageio.ImageIO.write(view,"png",proofs.resolve("workspace.png").toFile());}catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
    private static void layout(Container component){component.doLayout();for(Component child:component.getComponents())if(child instanceof Container nested)layout(nested);}
}
