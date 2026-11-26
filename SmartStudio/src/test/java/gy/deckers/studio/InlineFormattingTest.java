package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class InlineFormattingTest {
    @Test void selectionControlsStayInlineAndApplyAtBothSizesAndAllScales() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            try {
                var style=new AtomicReference<>(new TextFormattingToolbar.Style("Arial",12,false,false,false,false,"#000000",false,"#000000",.2,null));
                var text=new TextFormattingToolbar(style::get,style::set,()->publishing.TextAlignment.LEFT);
                var picture=new AtomicReference<>(new ImageFormattingToolbar.Picture("image",80,40,0));
                var image=new ImageFormattingToolbar(picture::get,s->picture.set(new ImageFormattingToolbar.Picture("image",s.width(),s.height(),picture.get().transparency())),()->{});
                image.setTransparencyEditor(n->picture.set(new ImageFormattingToolbar.Picture("image",picture.get().width(),picture.get().height(),n)));
                var shell=new StudioWorkspaceShell(new JPanel());
                var context=new JPanel(new CardLayout());context.add(text,"text");context.add(image,"image");
                shell.addTextTools(context,move->{});
                StudioRibbon ribbon=shell.ribbon();
                for(int pixels:new int[]{1366,1920})for(double scale:new double[]{1,1.25,1.5})for(String tab:new String[]{"Text format","Picture format"}){
                    ribbon.setCollapsed(true);ribbon.select(tab);assertFalse(ribbon.isCollapsed());
                    ribbon.autoCompact(true);assertFalse(ribbon.isCollapsed());
                    int width=(int)(pixels/scale)-32;
                    ribbon.setSize(width,220);layout(ribbon);
                    ribbon.setSize(width,ribbon.getPreferredSize().height);layout(ribbon);
                    JComponent editor=tab.startsWith("Text")?text:image;
                    assertTrue(editor.isVisible(),"Reparenting from CardLayout must preserve editor visibility");
                    for(Component control:editor.getComponents()){
                        assertTrue(control.isShowing()||control.isVisible());
                        assertTrue(control.getX()>=0&&control.getX()+control.getWidth()<=editor.getWidth(),tab+" width "+width);
                        assertTrue(control.getY()>=0&&control.getY()+control.getHeight()<=editor.getHeight(),tab+" height "+width);
                    }
                    assertTrue(ribbon.getHeight()<260,"Formatting must leave room for the canvas: "+tab+" at "+width+" height "+ribbon.getHeight());
                    BufferedImage preview=new BufferedImage(width,ribbon.getHeight(),BufferedImage.TYPE_INT_RGB);
                    var g=preview.createGraphics();ribbon.printAll(g);g.dispose();
                    javax.imageio.ImageIO.write(preview,"png",Path.of("target","inline-"+tab.split(" ")[0]+"-"+pixels+"-"+scale+".png").toFile());
                }
                text.bold.doClick();assertTrue(style.get().bold());
                image.width.setValue(100./25.4);assertEquals(100,picture.get().width());assertEquals(50,picture.get().height());
                image.transparency.setValue(25);assertEquals(25,picture.get().transparency());
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container n)layout(n);}
}
