package ui.components;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class FitImageLabelTest {
    @Test void fitsEveryFormatAfterResizeWithoutClippingOrRequestingImageSizedLayout() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            for(Dimension sourceSize:new Dimension[]{new Dimension(100,100),new Dimension(80,100),new Dimension(90,160),new Dimension(160,90)}) {
                var source=new BufferedImage(sourceSize.width,sourceSize.height,BufferedImage.TYPE_INT_RGB);
                var fill=source.createGraphics();fill.setColor(Color.RED);fill.fillRect(0,0,sourceSize.width,sourceSize.height);fill.dispose();
                var label=new FitImageLabel("");label.setIcon(new ImageIcon(source));
                for(Dimension viewport:new Dimension[]{new Dimension(500,300),new Dimension(180,240)}) {
                    label.setSize(viewport);
                    var output=new BufferedImage(viewport.width,viewport.height,BufferedImage.TYPE_INT_RGB);
                    var g=output.createGraphics();label.paint(g);g.dispose();
                    int minX=viewport.width,minY=viewport.height,maxX=-1,maxY=-1;
                    for(int y=0;y<viewport.height;y++)for(int x=0;x<viewport.width;x++)if(output.getRGB(x,y)==Color.RED.getRGB()){
                        minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                    }
                    assertTrue(maxX>minX&&maxY>minY);
                    assertTrue(minX>=6&&minY>=6&&maxX<viewport.width-6&&maxY<viewport.height-6);
                    assertEquals((double)sourceSize.width/sourceSize.height,(double)(maxX-minX+1)/(maxY-minY+1),.02);
                }
                assertEquals(0,label.getIcon().getIconWidth());
            }
        });
    }
}
