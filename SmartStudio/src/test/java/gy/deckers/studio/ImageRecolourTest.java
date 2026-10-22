package gy.deckers.studio;
import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
class ImageRecolourTest {
    ImageRecolour.Settings settings(ImageRecolour.Mode mode,int tolerance,boolean shades){return new ImageRecolour.Settings(mode,Color.BLACK,Color.RED,tolerance,shades);}
    @Test void tintPreservesTransparencyWhiteShadingAndInput(){
        var original=new BufferedImage(4,1,BufferedImage.TYPE_INT_ARGB);original.setRGB(0,0,0x80000000);original.setRGB(1,0,0xffffffff);original.setRGB(2,0,0xff808080);original.setRGB(3,0,0x00123456);
        var result=ImageRecolour.apply(original,settings(ImageRecolour.Mode.TINT,0,true));assertEquals(0x80ff0000,result.getRGB(0,0));assertEquals(0xffffffff,result.getRGB(1,0));assertEquals(0xffff8080,result.getRGB(2,0));assertEquals(0x00123456,result.getRGB(3,0));assertEquals(0x80000000,original.getRGB(0,0));assertEquals(original.getWidth(),result.getWidth());
    }
    @Test void replaceAffectsOnlyChosenColourWithinTolerance(){var source=new BufferedImage(3,1,BufferedImage.TYPE_INT_ARGB);source.setRGB(0,0,0xff000000);source.setRGB(1,0,0xff101010);source.setRGB(2,0,0xff00ff00);var exact=ImageRecolour.apply(source,settings(ImageRecolour.Mode.REPLACE,0,false));assertEquals(0xffff0000,exact.getRGB(0,0));assertEquals(0xff101010,exact.getRGB(1,0));var tolerant=ImageRecolour.apply(source,settings(ImageRecolour.Mode.REPLACE,10,false));assertEquals(0xffff0000,tolerant.getRGB(1,0));assertEquals(0xff00ff00,tolerant.getRGB(2,0));}
    @Test void grayscaleAndPngRoundTripPreserveAlpha()throws Exception{var source=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);source.setRGB(0,0,0x70123456);var result=ImageRecolour.apply(source,settings(ImageRecolour.Mode.GRAYSCALE,0,true));var c=new Color(result.getRGB(0,0),true);assertEquals(c.getRed(),c.getGreen());assertEquals(c.getGreen(),c.getBlue());assertEquals(0x70,c.getAlpha());assertEquals(result.getRGB(0,0),ImageIO.read(new ByteArrayInputStream(ImageRecolour.png(result))).getRGB(0,0));}
}
