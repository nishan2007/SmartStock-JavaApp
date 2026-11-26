package gy.deckers.studio;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
class StudioWorkspaceChooserTest {
 @Test void iconCardsStayReadableAndOpenTheirNamedWorkspaceAtDisplayBudgets()throws Exception{
  SwingUtilities.invokeAndWait(()->{try{
   var opened=new ArrayList<String>();var chooser=new StudioWorkspaceChooser(opened::add);StudioVisuals.styleTree(chooser);
   for(int screen:new int[]{1366,1920})for(double scale:new double[]{1,1.25,1.5}){
    chooser.setSize((int)(screen/scale)-24,(int)(768/scale)-100);layout(chooser);
    var cards=(Container)((Container)chooser.getComponent(0)).getComponent(1);
    for(Component item:cards.getComponents()){
     var tile=(JButton)item;assertNotNull(tile.getIcon());assertTrue(tile.getHeight()>=90);assertTrue(tile.getFontMetrics(StudioVisuals.font(Font.BOLD,16)).stringWidth(tile.getText())+90<=tile.getWidth(),tile.getText()+" clips at "+screen+" / "+scale);
    }
    if(screen==1366&&scale==1){var image=new java.awt.image.BufferedImage(chooser.getWidth(),chooser.getHeight(),1);var g=image.createGraphics();chooser.printAll(g);g.dispose();javax.imageio.ImageIO.write(image,"png",java.nio.file.Path.of("target/workspace-chooser-redesign.png").toFile());}
   }
   var cards=(Container)((Container)chooser.getComponent(0)).getComponent(1);for(Component item:cards.getComponents())((JButton)item).doClick();assertEquals(java.util.List.of(StudioWorkspaceChooser.NAMES),opened);
  }catch(Exception e){throw new RuntimeException(e);}});
 }
 private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container n)layout(n);}
}
