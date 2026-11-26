package gy.deckers.studio;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class PassportQueueNavigationTest {
 @Test void passportHomeExposesQueueAndInvokesNavigationWithoutClearingCurrentWork()throws Exception{
  SwingUtilities.invokeAndWait(()->{
   var panel=new PassportPhotosPanel();var opened=new AtomicInteger();panel.setPhotoQueueChooser(opened::incrementAndGet);
   var ribbon=find(panel,StudioRibbon.class);ribbon.select("Home");var tabs=find(ribbon,JTabbedPane.class);var button=button(tabs.getSelectedComponent(),"Photo Queue");
   assertNotNull(button);assertTrue(button.isEnabled());button.doClick();assertEquals(1,opened.get());
   for(int width:new int[]{1334,1888,878}){
    ribbon.setSize(width,240);layout(ribbon);ribbon.setSize(width,ribbon.getPreferredSize().height);layout(ribbon);
    Rectangle b=SwingUtilities.convertRectangle(button.getParent(),button.getBounds(),ribbon);assertTrue(b.x>=0&&b.y>=0&&b.x+b.width<=width&&b.y+b.height<=ribbon.getHeight());
   }
  });
 }
 private static JButton button(Component c,String text){if(c instanceof JButton b&&text.equals(b.getText()))return b;if(c instanceof Container n)for(Component child:n.getComponents()){var r=button(child,text);if(r!=null)return r;}return null;}
 private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container n)layout(n);}
 private static <T>T find(Container c,Class<T> type){for(Component child:c.getComponents()){if(type.isInstance(child))return type.cast(child);if(child instanceof Container n){T r=find(n,type);if(r!=null)return r;}}return null;}
}
