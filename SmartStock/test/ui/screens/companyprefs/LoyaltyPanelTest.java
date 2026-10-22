package ui.screens.companyprefs;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class LoyaltyPanelTest {
    @Test void preferenceActionsFitNarrowContentWithoutContactingAStore()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var panel=new LoyaltyPanel(false);panel.setSize(560,640);layout(panel);
            for(Component c:all(panel))if(c instanceof JButton b){Rectangle bounds=SwingUtilities.convertRectangle(b.getParent(),b.getBounds(),panel);assertTrue(bounds.x>=0&&bounds.x+bounds.width<=panel.getWidth(),b.getText());assertTrue(bounds.y>=0&&bounds.y+bounds.height<=panel.getHeight(),b.getText());}
            assertTrue(all(panel).stream().anyMatch(c->c instanceof JLabel label&&label.getText().contains("Reward rate: 1%")));
            try{var image=new BufferedImage(560,640,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,560,640);panel.paint(g);g.dispose();Path out=Path.of("target/loyalty-previews");Files.createDirectories(out);ImageIO.write(image,"png",out.resolve("company-preferences.png").toFile());}catch(Exception e){throw new RuntimeException(e);}
        });
    }
    private static void layout(Container parent){parent.doLayout();for(Component c:parent.getComponents())if(c instanceof Container child)layout(child);}
    private static java.util.List<Component> all(Container parent){var result=new java.util.ArrayList<Component>();for(Component c:parent.getComponents()){result.add(c);if(c instanceof Container child)result.addAll(all(child));}return result;}
}
