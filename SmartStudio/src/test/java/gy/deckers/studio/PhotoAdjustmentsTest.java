package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class PhotoAdjustmentsTest {
    private BufferedImage photo(){BufferedImage image=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,0x80604020);return image;}
    @Test void correctionsPreserveAlphaAndOriginal(){
        BufferedImage image=photo();
        int bright=new PhotoAdjustments(50,0,0,0).apply(image).getRGB(0,0);
        int dark=new PhotoAdjustments(-50,0,0,0).apply(image).getRGB(0,0);
        assertEquals(128,bright>>>24);assertEquals(0x80604020,image.getRGB(0,0));
        assertTrue((bright&255)>32);assertTrue((dark&255)<32);
        assertSame(image,PhotoAdjustments.NONE.apply(image));
    }
    @Test void desaturationAndWarmthHaveExpectedColorEffects(){
        int gray=new PhotoAdjustments(0,0,-100,0).apply(photo()).getRGB(0,0);
        assertEquals((gray>>>16)&255,(gray>>>8)&255);assertEquals(gray&255,(gray>>>8)&255);
        int warm=new PhotoAdjustments(0,0,0,50).apply(photo()).getRGB(0,0);
        assertTrue(((warm>>>16)&255)>96);assertTrue((warm&255)<32);
        int contrasted=new PhotoAdjustments(0,100,0,0).apply(photo()).getRGB(0,0);
        assertTrue(((contrasted>>>16)&255)<96);
    }
    @Test void framingAndStyleKeepCorrectionsAndResetRestoresPixels(){
        BufferedImage image=photo();PhotoAdjustments settings=new PhotoAdjustments(30,20,10,-10);
        PhotoFraming frame=PhotoFraming.initial(image).adjusted(settings).positioned(2,.2,-.1,1).styled(Color.WHITE,Color.BLACK).fit(30,40);
        assertEquals(settings,frame.adjustments());assertSame(image,frame.source());
        assertNotEquals(image.getRGB(0,0),frame.rendered().getRGB(0,0));
        assertSame(image,frame.adjusted(PhotoAdjustments.NONE).rendered());
        assertThrows(IllegalArgumentException.class,()->new PhotoAdjustments(101,0,0,0));
    }
}
