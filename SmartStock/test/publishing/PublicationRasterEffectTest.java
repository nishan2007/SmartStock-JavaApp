package publishing;
import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PublicationRasterEffectTest {
    Publication document(){var p=new Publication();p.width=60;p.height=40;p.print.width=p.print.height=100;p.print.quantity=1;p.print.cropMarks=false;p.print.left=p.print.right=p.print.top=p.print.bottom=10;p.print.alignment="TOP_LEFT";var e=new Publication.Element();e.type="RECT";e.x=10;e.y=10;e.width=20;e.height=10;e.fill="#ff0000";e.stroke="none";p.pages.get(0).objects.add(e);return p;}
    @Test void isolatedEffectUsesSheetCoordinatesAndNormalRendererRecoversAfterwards(){
        var p=document();String before=Publication.JSON.toJson(p);var renderer=new PublicationRenderer(p);var normal=renderer.raster(0,254);AtomicInteger count=new AtomicInteger();
        var changed=renderer.raster(0,254,(e,image)->{count.incrementAndGet();for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int pixel=image.getRGB(x,y);if((pixel>>>24)>0)image.setRGB(x,y,(pixel&0xff000000)|0x008000);}return image;});
        assertEquals(1,count.get());assertEquals(Color.RED.getRGB(),normal.getRGB(250,250));assertEquals(new Color(0,128,0).getRGB(),changed.getRGB(250,250));assertEquals(Color.WHITE.getRGB(),changed.getRGB(150,150));assertEquals(Color.RED.getRGB(),renderer.raster(0,254).getRGB(250,250));assertEquals(before,Publication.JSON.toJson(p));
    }
    @Test void failingOrInvalidEffectsDoNotLeakIntoFutureRasterCalls(){var renderer=new PublicationRenderer(document());assertThrows(IllegalArgumentException.class,()->renderer.raster(0,100,(e,image)->new BufferedImage(1,1,2)));assertEquals(Color.RED.getRGB(),renderer.raster(0,254).getRGB(250,250));assertThrows(IllegalStateException.class,()->renderer.raster(0,100,(e,image)->{throw new IllegalStateException("Test failure");}));assertEquals(Color.RED.getRGB(),renderer.raster(0,254).getRGB(250,250));}
}
