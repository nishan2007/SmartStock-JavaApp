package gy.deckers.studio;

import gy.deckers.studio.pdfeditor.PdfDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import publishing.Publication;
import publishing.PublicationBarcode;
import publishing.PublicationFiles;
import publishing.PublicationRenderer;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StudioArtworkFeaturesTest {
    @TempDir Path folder;

    @Test void badgeTablesAndWordArtKeepEditableSourceAndCompatiblePixels() throws Exception {
        var grid=StudioTables.create(3,2,50,24);
        grid.merge(0,0,2,1);
        grid.text(0,0,"Café — details");
        var table=BadgeEditableArtwork.table(grid);
        var text=new Publication.Element();
        text.text="Bienvenue";
        text.wordArt="ARCH_UP";
        var wordArt=BadgeEditableArtwork.wordArt(text);
        var badge=new BadgeTemplate();
        badge.elements.add(layer(table));
        badge.backElements.add(layer(wordArt));
        Path file=folder.resolve("artwork.sstbadge");
        badge.save(file);

        var reopened=BadgeTemplate.load(file);
        var restoredTable=BadgeEditableArtwork.table(BadgeEditableArtwork.read(reopened.elements.get(0)));
        assertEquals("Café — details",restoredTable.cell(0,0).text);
        assertEquals(3,restoredTable.cell(0,0).rowSpan);
        assertEquals(2,restoredTable.cell(0,0).columnSpan);
        assertEquals(3,restoredTable.rows());
        var restoredArt=BadgeEditableArtwork.read(reopened.backElements.get(0));
        assertEquals("Bienvenue",restoredArt.objects().get(0).text);
        assertEquals("ARCH_UP",restoredArt.objects().get(0).wordArt);
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(wordArt.bytes())));
    }

    @Test void publicationPhotoPlaceholderPreservesFrameAcrossReplacementAndSave()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            try{
                for(boolean labels:new boolean[]{false,true}){
                    var panel=new BusinessCardsPanel(labels);
                    panel.setSize(1900,900);
                    layout(panel);
                    var ribbon=find(panel,StudioRibbon.class);
                    ribbon.setCollapsed(false);
                    ribbon.select("Insert");
                    layout(panel);
                    var button=button(panel,StudioCommand.PHOTO_FIELD);
                    assertNotNull(button);
                    button.doClick();
                    var field=BusinessCardsPanel.class.getDeclaredField("document");
                    field.setAccessible(true);
                    var publication=(Publication)field.get(panel);
                    int index=publication.pages.get(0).objects.size()-1;
                    var blank=publication.pages.get(0).objects.get(index);
                    assertTrue(blank.photoPlaceholder);
                    assertTrue(blank.asset.isEmpty());
                    var png=new BufferedImage(300,200,BufferedImage.TYPE_INT_ARGB);
                    var graphics=png.createGraphics();
                    var renderer=new PublicationRenderer(publication);
                    try{renderer.page(graphics,0,Map.of());}finally{graphics.dispose();}
                    assertTrue(renderer.warnings().stream().noneMatch(warning->warning.startsWith("Missing image")));

                    var portrait=solid(120,200,Color.BLUE);
                    panel.replacePhoto(index,portrait);
                    var first=((Publication)field.get(panel)).pages.get(0).objects.get(index);
                    first.rotation=24;
                    var framing=new PublicationPhotoFrame(2,.25,.75);
                    framing.apply(first,portrait);
                    var landscape=solid(200,120,Color.RED);
                    panel.replacePhoto(index,landscape);
                    var reopened=PublicationFiles.read(PublicationFiles.archive((Publication)field.get(panel)));
                    var replacement=reopened.pages.get(0).objects.get(index);
                    assertTrue(replacement.photoPlaceholder);
                    assertEquals(first.width,replacement.width);
                    assertEquals(first.height,replacement.height);
                    assertEquals(first.rotation,replacement.rotation);
                    var image=PublicationPhotoFrame.source(reopened,replacement);
                    assertEquals(Color.RED.getRGB(),image.getRGB(10,10));
                    var actual=PublicationPhotoFrame.from(replacement,image);
                    assertEquals(framing.zoom(),actual.zoom(),.0001);
                    assertEquals(framing.horizontal(),actual.horizontal(),.0001);
                    assertEquals(framing.vertical(),actual.vertical(),.0001);
                }
            }catch(Exception ex){throw new AssertionError(ex);}
        });
    }

    @Test void pdfCodeIsScannableAfterSaveAndReopen()throws Exception{
        for(boolean qr:new boolean[]{false,true}){
            var code=new Publication.Element();
            code.type=qr?"QR":"BARCODE";
            code.text="SMARTSTUDIO-123";
            var artwork=BadgeCodeArtwork.create(code);
            Path output=folder.resolve(qr?"qr.pdf":"barcode.pdf");
            float width=200,height=width*artwork.sourceHeight()/artwork.sourceWidth();
            try(var pdf=new PdfDocument()){
                pdf.blank(0);
                pdf.image(0,artwork.image(),40,40,width,height,artwork.name());
                assertEquals(artwork.name(),pdf.objects(0).get(0).kind());
                pdf.undo();
                assertTrue(pdf.objects(0).isEmpty());
                pdf.redo();
                pdf.save(output,new int[]{0});
            }
            try(var reopened=new PdfDocument()){
                reopened.append(output,"",0,false);
                var page=reopened.render(0,2);
                int top=(int)((reopened.bounds(0).getHeight()-40-height)*2);
                var scan=page.getSubimage(76,top-4,(int)(2*width)+8,(int)(2*height)+8);
                assertEquals(code.text,PublicationBarcode.decode(scan).orElseThrow().text());
            }
        }
    }

    @Test void imageHandoffUsesEditorArtworkAndUndoWithoutClipboard()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            try{
                var image=solid(160,100,Color.ORANGE);
                var bytes=new ByteArrayOutputStream();
                ImageIO.write(image,"png",bytes);
                var result=new StudioImageFile.Loaded(bytes.toByteArray(),image,"Processed image");
                var publication=new BusinessCardsPanel();
                int first=publication.output().pages.get(0).objects.size();
                publication.receiveArtwork(result);
                assertEquals(first+1,publication.output().pages.get(0).objects.size());
                publication.historyAction(false);
                assertEquals(first,publication.output().pages.get(0).objects.size());

                var badge=new BadgeCardsPanel();
                var field=BadgeCardsPanel.class.getDeclaredField("template");
                field.setAccessible(true);
                var template=(BadgeTemplate)field.get(badge);
                int original=template.backElements.size();
                template.editingBack=true;
                badge.receiveArtwork(result);
                var inserted=template.backElements.get(original);
                assertEquals("Processed image",inserted.label);
                assertEquals(Color.ORANGE.getRGB(),inserted.artwork().getRGB(0,0));
            }catch(Exception ex){throw new AssertionError(ex);}
        });
    }

    private static BadgeTemplate.Element layer(StudioImageFile.Loaded artwork){
        var layer=new BadgeTemplate.Element();
        layer.type="IMAGE";
        layer.label=artwork.name();
        layer.image=Base64.getEncoder().encodeToString(artwork.bytes());
        layer.width=55;
        layer.height=24;
        return layer;
    }

    private static BufferedImage solid(int width,int height,Color color){
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();
        try{graphics.setColor(color);graphics.fillRect(0,0,width,height);}finally{graphics.dispose();}
        return image;
    }

    private static JButton button(Container host,StudioCommand command){
        for(Component child:host.getComponents()){
            if(child instanceof JButton b&&b.getClientProperty(StudioCommand.class)==command)return b;
            if(child instanceof Container nested){
                var found=button(nested,command);
                if(found!=null)return found;
            }
        }
        return null;
    }

    private static <T>T find(Container host,Class<T> type){
        for(Component child:host.getComponents()){
            if(type.isInstance(child))return type.cast(child);
            if(child instanceof Container nested){
                T found=find(nested,type);
                if(found!=null)return found;
            }
        }
        return null;
    }

    private static void layout(Container host){
        host.doLayout();
        for(Component child:host.getComponents())if(child instanceof Container nested)layout(nested);
    }
}
