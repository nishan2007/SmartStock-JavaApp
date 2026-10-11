package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TemplateWorkspaceTest {
    @Test void dragOffPageAndBackKeepsLayerEditable() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BadgeTemplate t=new BadgeTemplate();t.widthMm=100;t.heightMm=100;
            BadgeTemplate.Element e=new BadgeTemplate.Element();e.label="Field";e.x=10;e.y=10;e.width=10;e.height=10;t.elements.add(e);
            TemplateWorkspace workspace=new TemplateWorkspace();workspace.setSize(424,424);
            workspace.configure(()->t,Map::of,()->null,row->{},()->{});
            BufferedImage image=new BufferedImage(424,424,BufferedImage.TYPE_INT_RGB);
            var g=image.createGraphics();workspace.paint(g);g.dispose();
            drag(workspace,142,142,72,142);
            assertEquals(-25,e.x,.01);
            g=image.createGraphics();workspace.paint(g);g.dispose();
            drag(workspace,72,142,142,142);
            assertEquals(10,e.x,.01);assertEquals(1,t.elements.size());t.validate();
        });
    }
    private void drag(JComponent c,int x,int y,int endX,int endY) {
        c.dispatchEvent(new MouseEvent(c,MouseEvent.MOUSE_PRESSED,0,0,x,y,1,false,MouseEvent.BUTTON1));
        c.dispatchEvent(new MouseEvent(c,MouseEvent.MOUSE_DRAGGED,0,MouseEvent.BUTTON1_DOWN_MASK,endX,endY,0,false,MouseEvent.NOBUTTON));
        c.dispatchEvent(new MouseEvent(c,MouseEvent.MOUSE_RELEASED,0,0,endX,endY,1,false,MouseEvent.BUTTON1));
    }
}
