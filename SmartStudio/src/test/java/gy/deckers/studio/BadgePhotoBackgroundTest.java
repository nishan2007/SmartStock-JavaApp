package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class BadgePhotoBackgroundTest {
    @Test void replacementKeepsCropPositionRotationStyleAndCorrections(){
        var original=new BufferedImage(200,300,BufferedImage.TYPE_INT_RGB);
        var frame=PhotoFraming.initial(original).positioned(1.6,.2,-.3,1)
                .styled(Color.BLUE,Color.BLACK).adjusted(new PhotoAdjustments(10,20,0,0));
        var cutout=new BufferedImage(100,150,BufferedImage.TYPE_INT_ARGB);
        var updated=frame.withSource(cutout);
        assertSame(original,frame.source());assertSame(cutout,updated.source());
        assertEquals(frame.zoom(),updated.zoom());assertEquals(frame.offsetX(),updated.offsetX());
        assertEquals(frame.offsetY(),updated.offsetY());assertEquals(frame.turns(),updated.turns());
        assertEquals(frame.background(),updated.background());assertEquals(frame.outline(),updated.outline());
        assertEquals(frame.adjustments(),updated.adjustments());assertEquals(0,updated.rendered().getRGB(50,50)>>>24);
    }
    @Test void largePhotoGetsBoundedServerCompatibleProcessingCopy()throws Exception {
        var source=new BufferedImage(2400,3600,BufferedImage.TYPE_INT_RGB);
        byte[] bytes=BadgePhotoBackground.processingCopy(source);
        services.StudioBackgroundService.validate(bytes);
        var decoded=ImageIO.read(new ByteArrayInputStream(bytes));
        assertEquals(2048,decoded.getHeight());assertEquals(2./3,decoded.getWidth()/(double)decoded.getHeight(),.001);
        assertEquals(2400,source.getWidth());assertEquals(3600,source.getHeight());
    }
}
