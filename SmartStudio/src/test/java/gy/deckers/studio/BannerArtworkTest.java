package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import publishing.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import java.awt.datatransfer.*;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import static org.junit.jupiter.api.Assertions.*;

class BannerArtworkTest {
    @TempDir Path temp;

    @Test void largeBannersResampleWithinBudgetWithoutChangingAspectRatio() {
        for (int feet : new int[]{6, 12, 24, 100}) {
            var picture = new BannerImageResolution.Picture(0, 0, "Banner",
                    new java.awt.Rectangle(0, 0, 600, 400), 1,
                    feet * 12L * 300, feet * 8L * 300);
            assertTrue(picture.canResample());
            var size = picture.outputSize(100_000_000L);
            assertTrue((long) size.width * size.height <= 100_000_000L);
            assertEquals(1.5, (double) size.width / size.height, .001);
            assertTrue(picture.outputPpi() > 0);
        }
    }

    @Test void smallerImagesStillReach300Ppi() {
        var picture = new BannerImageResolution.Picture(0, 0, "Small",
                new java.awt.Rectangle(0, 0, 100, 100), 50, 600, 600);
        assertEquals(new java.awt.Dimension(600, 600), picture.outputSize(1_000_000));
    }

    @Test void resampledPdfEmbeds300PpiAtPlacedSizeAfterCropping() throws Exception {
        var original = new BufferedImage(200, 150, BufferedImage.TYPE_INT_ARGB);
        original.setRGB(100, 50, 0xffff0000);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(original, "PNG", bytes);
        var banner = BannerDocuments.create(3, 2, "IN");
        var page = banner.pages.get(0);
        PublicationImport.addEncodedImage(banner, page, bytes.toByteArray(), 0, 0, 50.8, 25.4);
        var layer = page.objects.get(0);
        layer.cropX = .25;
        layer.cropWidth = .5;

        var pictures = BannerImageResolution.belowTarget(banner);
        assertEquals(1, pictures.size());
        assertEquals(50, pictures.get(0).ppi(), .001);
        var resampled = BannerImageResolution.resample(banner, pictures);
        var generated=resampled.assetFiles.get(resampled.pages.get(0).objects.get(0).asset);
        assertNotNull(generated);
        assertTrue(Files.isRegularFile(generated));
        assertTrue(BannerImageResolution.belowTarget(resampled).isEmpty());
        assertEquals(.5, layer.cropWidth);
        assertArrayEquals(bytes.toByteArray(), banner.assetBytes(layer.asset));

        Path pdf = temp.resolve("resampled.pdf");
        BannerDocuments.pdf(resampled, pdf);
        try (var doc = PDDocument.load(pdf.toFile())) {
            var images = BannerDocumentsTest.images(doc.getPage(0).getResources());
            assertEquals(1, images.size());
            assertEquals(600, images.get(0).getWidth());
            assertEquals(300, images.get(0).getHeight());
            assertEquals(216, doc.getPage(0).getMediaBox().getWidth(), .001);
            assertEquals(144, doc.getPage(0).getMediaBox().getHeight(), .001);
        }
        BannerImageResolution.cleanup(banner,resampled);
        assertFalse(Files.exists(generated));
        assertArrayEquals(bytes.toByteArray(),banner.assetBytes(layer.asset));
    }

    @Test void artworkAboveSixMegabytesKeepsExactSourceThroughInsertSaveReopenAndExport()throws Exception {
        BufferedImage original=new BufferedImage(2200,1200,BufferedImage.TYPE_INT_RGB);
        Random random=new Random(42);int[] row=new int[2200];
        for(int y=0;y<1200;y++){for(int x=0;x<2200;x++)row[x]=random.nextInt(0x1000000);original.setRGB(0,y,2200,1,row,0,2200);}
        Path file=temp.resolve("high quality.png");ImageIO.write(original,"png",file.toFile());
        byte[] source=Files.readAllBytes(file);assertTrue(source.length>6*1024*1024);
        assertArrayEquals(source,StudioImageFile.load(file).bytes());
        var loaded=StudioImageFile.loadArtwork(file);
        assertEquals(2200,loaded.sourceWidth());assertEquals(1200,loaded.sourceHeight());
        assertTrue(loaded.image().getWidth()<=2048);assertArrayEquals(source,loaded.bytes());
        var output=new java.util.concurrent.atomic.AtomicReference<Publication>();
        javax.swing.SwingUtilities.invokeAndWait(()->{try{
            BusinessCardsPanel panel=new BusinessCardsPanel(false,true);panel.insertImageLayer(loaded);output.set(panel.output());
        }catch(Exception ex){throw new RuntimeException(ex);}});
        Publication publication=output.get();
        Path template=temp.resolve("banner.sspub");BusinessCardsPanel.writeTemplate(template,publication,null);
        Publication reopened=BusinessCardsPanel.readTemplate(template,null,false,true);
        assertArrayEquals(source,reopened.assetBytes(reopened.assetIds().iterator().next()));
        Path pdf=temp.resolve("banner.pdf");BannerDocuments.pdf(reopened,pdf);
        try(var doc=PDDocument.load(pdf.toFile())) {
            boolean found=false;for(var image:BannerDocumentsTest.images(doc.getPage(0).getResources())){
                assertEquals(2200,image.getWidth());assertEquals(1200,image.getHeight());
                assertEquals(original.getRGB(100,100),image.getImage().getRGB(100,100));found=true;
            }assertTrue(found);
        }
        Transferable encoded=new Transferable(){
            public DataFlavor[] getTransferDataFlavors(){return new DataFlavor[]{StudioImageTransfer.PNG,DataFlavor.imageFlavor};}
            public boolean isDataFlavorSupported(DataFlavor flavor){return flavor.equals(StudioImageTransfer.PNG)||flavor.equals(DataFlavor.imageFlavor);}
            public Object getTransferData(DataFlavor flavor){if(flavor.equals(DataFlavor.imageFlavor))throw new AssertionError("Banner paste must prefer original encoded artwork");return new ByteArrayInputStream(source);}
        };
        assertArrayEquals(source,StudioImageTransfer.capture(encoded,true).call().bytes());
    }
    @Test void artworkAboveTwelveMegapixelsUsesSmallPreview()throws Exception {
        BufferedImage original=new BufferedImage(5000,3000,BufferedImage.TYPE_INT_RGB);
        Path path=temp.resolve("15 megapixel.jpg");ImageIO.write(original,"jpeg",path.toFile());original.flush();
        var loaded=StudioImageFile.loadArtwork(path);
        assertEquals(5000,loaded.sourceWidth());assertEquals(3000,loaded.sourceHeight());
        assertTrue(loaded.image().getWidth()<=2048);assertTrue(loaded.image().getHeight()<=2048);
        BufferedImage preview=PublicationImport.decodeImage(loaded.bytes(),1200);
        assertTrue(preview.getWidth()<=1200);assertTrue(preview.getHeight()<=1200);
    }

    @Test void artworkAboveHundredMiBIsAcceptedAndStoredOutsideHeapSnapshots()throws Exception {
        Path file=temp.resolve("boundary.png");ImageIO.write(new BufferedImage(32,32,BufferedImage.TYPE_INT_RGB),"png",file.toFile());
        // Valid PNG with trailing padding exercises import and save beyond the former file-size cap.
        try(RandomAccessFile output=new RandomAccessFile(file.toFile(),"rw")){output.setLength(101L*1024*1024);}
        var loaded=StudioImageFile.loadArtwork(file);assertEquals(101*1024*1024,loaded.bytes().length);
        Publication p=BannerDocuments.create(100,10,"FT");PublicationImport.addEncodedImage(p,p.pages.get(0),loaded.bytes(),0,0,100,100);
        assertTrue(p.assets.isEmpty());assertEquals(1,p.assetFiles.size());assertEquals(p.assetFiles,p.copy().assetFiles);
        Path template=temp.resolve("large.sspub");BusinessCardsPanel.writeTemplate(template,p,null,true);
        Publication reopened=BusinessCardsPanel.readTemplate(template,null,false,true);
        assertEquals(101L*1024*1024,reopened.assetSize(reopened.assetIds().iterator().next()));
        var copy=StudioLayerClipboard.publication(reopened,0,new int[]{0});
        Publication pasted=StudioLayerClipboard.paste(BannerDocuments.create(100,10,"FT"),0,copy);
        assertTrue(pasted.assets.isEmpty());assertEquals(reopened.assetFiles.values().iterator().next(),pasted.assetFiles.values().iterator().next());
    }
}
