package ui.screens;

import services.EmployeePhotoFraming;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class EmployeePhotoFrameEditorTest {
    @Test void rendersGuidesAndZoomResetUsesOriginal()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var source=new BufferedImage(480,800,BufferedImage.TYPE_INT_RGB);var g=source.createGraphics();
            g.setColor(new Color(230,221,242));g.fillRect(0,0,480,800);g.setColor(new Color(184,131,97));g.fillOval(140,30,200,250);g.setColor(new Color(112,34,168));g.fillRoundRect(80,280,320,520,80,80);g.dispose();
            var editor=new EmployeePhotoFrameEditor(source,EmployeePhotoFraming.fit());editor.setSize(720,550);layout(editor);
            JSlider slider=find(editor,JSlider.class);slider.setValue(160);assertEquals(1.6,editor.frame().zoom());
            JButton reset=find(editor,JButton.class);reset.doClick();assertEquals(EmployeePhotoFraming.fit(),editor.frame());
            var image=new BufferedImage(720,550,BufferedImage.TYPE_INT_RGB);g=image.createGraphics();editor.printAll(g);g.dispose();
            try{Path p=Path.of("target/employee-photo-framing/editor.png");Files.createDirectories(p.getParent());javax.imageio.ImageIO.write(image,"png",p.toFile());}catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container cc)layout(cc);}
    private static <T> T find(Container c,Class<T> type){for(Component child:c.getComponents()){if(type.isInstance(child))return type.cast(child);if(child instanceof Container cc){T result=find(cc,type);if(result!=null)return result;}}return null;}
}
