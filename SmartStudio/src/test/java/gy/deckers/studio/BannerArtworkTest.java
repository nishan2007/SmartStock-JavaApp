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
    @Test void artworkByteLimitHasSpecificMessage() {
        var error=assertThrows(IOException.class,()->StudioImageFile.artwork(new byte[StudioImageFile.ARTWORK_MAX_BYTES+1],"large.png"));
        assertTrue(error.getMessage().contains("100 MiB"));
    }
    @Test void exactHundredMiBFileIsAcceptedAndStoredOutsideHeapSnapshots()throws Exception {
        Path file=temp.resolve("boundary.png");ImageIO.write(new BufferedImage(32,32,BufferedImage.TYPE_INT_RGB),"png",file.toFile());
        // Valid PNG with trailing padding exercises the exact compressed-file boundary.
        try(RandomAccessFile output=new RandomAccessFile(file.toFile(),"rw")){output.setLength(StudioImageFile.ARTWORK_MAX_BYTES);}
        var loaded=StudioImageFile.loadArtwork(file);assertEquals(100*1024*1024,loaded.bytes().length);
        Publication p=BannerDocuments.create(100,10,"FT");PublicationImport.addEncodedImage(p,p.pages.get(0),loaded.bytes(),0,0,100,100);
        assertTrue(p.assets.isEmpty());assertEquals(1,p.assetFiles.size());assertEquals(p.assetFiles,p.copy().assetFiles);
        Path template=temp.resolve("large.sspub");BusinessCardsPanel.writeTemplate(template,p,null,true);
        Publication reopened=BusinessCardsPanel.readTemplate(template,null,false,true);
        assertEquals(100L*1024*1024,reopened.assetSize(reopened.assetIds().iterator().next()));
        var copy=StudioLayerClipboard.publication(reopened,0,new int[]{0});
        Publication pasted=StudioLayerClipboard.paste(BannerDocuments.create(100,10,"FT"),0,copy);
        assertTrue(pasted.assets.isEmpty());assertEquals(reopened.assetFiles.values().iterator().next(),pasted.assetFiles.values().iterator().next());
    }
}
