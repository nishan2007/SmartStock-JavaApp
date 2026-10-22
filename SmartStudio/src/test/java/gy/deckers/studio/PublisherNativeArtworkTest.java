package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import publishing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PublisherNativeArtworkTest {
    @Test void restoresMatchingSourceAlphaButRetainsDifferentImages()throws Exception{
        var source=new BufferedImage(100,60,BufferedImage.TYPE_INT_ARGB);var g=source.createGraphics();g.setColor(Color.WHITE);g.fillOval(20,10,60,40);g.setColor(Color.RED);g.fillRect(40,20,20,20);g.dispose();
        var bytes=new ByteArrayOutputStream();javax.imageio.ImageIO.write(source,"png",bytes);byte[] original=bytes.toByteArray();
        var flattened=new BufferedImage(100,60,BufferedImage.TYPE_INT_RGB);g=flattened.createGraphics();g.drawImage(source,0,0,null);g.dispose();
        var p=new Publication();PublicationImport.addImage(p,p.pages.get(0),flattened,2,2,40,24);var asset=p.pages.get(0).objects.get(0).asset;
        assertEquals(1,PublisherNativeArtwork.restore(p,List.of(original)));assertArrayEquals(original,p.assetBytes(asset));
        assertEquals(0,PublisherNativeArtwork.restore(p,List.of(original)));
        var other=new BufferedImage(100,60,BufferedImage.TYPE_INT_RGB);g=other.createGraphics();g.setColor(Color.BLUE);g.fillRect(0,0,100,60);g.dispose();
        PublicationImport.addImage(p,p.pages.get(0),other,2,2,40,24);assertEquals(0,PublisherNativeArtwork.restore(p,List.of(original)));
        PublicationFiles.read(PublicationFiles.archive(p)).validate();
    }
    @Test void boundedPngExtractionRejectsTruncatedChunks()throws Exception{
        assertTrue(PublisherNativeArtwork.pngs(new byte[]{(byte)137,80,78,71,13,10,26,10,127,127,127,127,73,69,78,68,0,0,0,0}).isEmpty());
    }
}
