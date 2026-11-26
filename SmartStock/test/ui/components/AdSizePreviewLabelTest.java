package ui.components;

import org.junit.jupiter.api.Test;
import services.AdDraftRenderer;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AdSizePreviewLabelTest {
    @Test void everySizeHasAnIndependentRedFrameWithoutChangingSourcePixels()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            for(String kind:List.of("ad_square","ad_portrait","ad_story","ad_landscape")) {
                var label=new AdSizePreviewLabel("");label.setFormat(kind);label.setSize(600,500);
                var source=new BufferedImage(100,100,BufferedImage.TYPE_INT_RGB);Graphics2D fill=source.createGraphics();fill.setColor(Color.BLUE);fill.fillRect(0,0,100,100);fill.dispose();
                label.setIcon(new ImageIcon(source));var rendered=new BufferedImage(600,500,BufferedImage.TYPE_INT_RGB);Graphics2D g=rendered.createGraphics();label.paint(g);g.dispose();
                Rectangle frame=label.frameBounds();Dimension expected=AdDraftRenderer.size(kind);
                assertEquals((double)expected.width/expected.height,(double)frame.width/frame.height,.01,kind);
                assertEquals(Color.RED.getRGB(),rendered.getRGB(frame.x,frame.y+frame.height/2),kind);
                assertEquals(Color.BLUE.getRGB(),source.getRGB(0,50),"Frame must not be saved into source");
                assertEquals(Color.BLUE.getRGB(),rendered.getRGB(frame.x+frame.width/2,frame.y+frame.height/2));
                if(!kind.equals("ad_square"))assertNotEquals(Color.BLUE.getRGB(),rendered.getRGB(frame.x+4,frame.y+4),"A mismatched source must leave visible space within the frame");
                label.setSize(300,260);Rectangle smaller=label.frameBounds();assertTrue(smaller.width<frame.width||smaller.height<frame.height);
            }
        });
    }
}
