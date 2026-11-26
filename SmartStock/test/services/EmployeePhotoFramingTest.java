package services;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class EmployeePhotoFramingTest {
    @Test void portraitHeadRemainsVisibleInLegacyAvatar() {
        var source=new BufferedImage(400,1000,BufferedImage.TYPE_INT_RGB);var g=source.createGraphics();
        g.setColor(Color.WHITE);g.fillRect(0,0,400,1000);g.setColor(Color.RED);g.fillRect(100,0,200,100);g.dispose();
        var avatar=EmployeePhotoFraming.avatar(source,320,false,true);
        int red=0;for(int y=0;y<320;y++)for(int x=0;x<320;x++)if((avatar.getRGB(x,y)&0xffffff)==0xff0000)red++;
        assertTrue(red>500,"Top of portrait must survive the circular avatar");assertEquals(0,avatar.getRGB(0,0)>>>24);
    }
    @Test void editorAvatarMatchesTheCenteredCircleInsideBadge() {
        var source=new BufferedImage(660,760,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<760;y++)for(int x=0;x<660;x++)source.setRGB(x,y,new Color(x%256,y%256,123).getRGB());
        var framed=EmployeePhotoFraming.render(source,new EmployeePhotoFraming.Frame(1,0,0));
        var avatar=EmployeePhotoFraming.avatar(framed,660,true,false);
        assertEquals(source.getRGB(330,50),avatar.getRGB(330,0));
        assertEquals(source.getRGB(330,709),avatar.getRGB(330,659));
        assertEquals(source.getRGB(330,380),avatar.getRGB(330,330));
    }
    @Test void repeatedFramingUsesOriginalWithoutMutatingIt() {
        var source=new BufferedImage(400,800,BufferedImage.TYPE_INT_RGB);source.setRGB(200,100,Color.RED.getRGB());
        var f=new EmployeePhotoFraming.Frame(1.4,.12,-.1);
        var first=EmployeePhotoFraming.render(source,f);EmployeePhotoFraming.render(source,EmployeePhotoFraming.fit());
        var second=EmployeePhotoFraming.render(source,EmployeePhotoFraming.decode(EmployeePhotoFraming.encode(f)));
        assertArrayEquals(first.getRGB(0,0,660,760,null,0,660),second.getRGB(0,0,660,760,null,0,660));
        assertEquals(Color.RED.getRGB(),source.getRGB(200,100));
    }
    @Test void invalidFramesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new EmployeePhotoFraming.Frame(Double.NaN,0,0));
        assertThrows(IllegalArgumentException.class,()->new EmployeePhotoFraming.Frame(0,0,0));
        assertThrows(IllegalArgumentException.class,()->new EmployeePhotoFraming.Frame(1,4,0));
        assertThrows(RuntimeException.class,()->EmployeePhotoFraming.decode("{}"));
        assertThrows(RuntimeException.class,()->EmployeePhotoFraming.decode("x".repeat(301)));
    }
}
