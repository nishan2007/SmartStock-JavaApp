package ui.screens.customorders;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class CustomOrderDetailsHeaderTest {
 @Test void titleAndButtonsStaySeparateAtNormalAndNarrowWidths() throws Exception {
  SwingUtilities.invokeAndWait(() -> {
   for(int width:new int[]{600,380,260}) {
    JPanel controls=new JPanel(new OrderDetailsWrapLayout());
    controls.add(new JButton("Files & Design Approval"));
    controls.add(new JButton("Preview Slip"));
    controls.add(new JButton("Print ▾"));
    JPanel header=CustomOrdersLookupTabPanel.detailsHeader(controls);
    for(int pass=0;pass<4;pass++) {
     header.setSize(width,header.getPreferredSize().height);
     header.doLayout();controls.doLayout();
    }
    Component title=header.getComponent(0);
    assertFalse(title.getBounds().intersects(controls.getBounds()),"Title must have its own row");
    assertTrue(title.getWidth()>=title.getPreferredSize().width,"Title must remain readable");
    for(Component button:controls.getComponents()) {
     assertTrue(button.getX()>=0 && button.getX()+button.getWidth()<=controls.getWidth(),"Button must fit horizontally at "+width);
     assertTrue(button.getY()>=0 && button.getY()+button.getHeight()<=controls.getHeight(),"Wrapped buttons must fit vertically at "+width);
     assertEquals(button.getPreferredSize().width,button.getWidth());
    }
    BufferedImage preview=new BufferedImage(width,header.getHeight(),BufferedImage.TYPE_INT_RGB);
    var graphics=preview.createGraphics();header.paint(graphics);graphics.dispose();
    try {javax.imageio.ImageIO.write(preview,"png",Path.of("target","order-header-"+width+".png").toFile());}
    catch(java.io.IOException ex){throw new RuntimeException(ex);}
   }
  });
 }
}
