package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import static org.junit.jupiter.api.Assertions.*;

class CutWorkspaceTest {
    @Test void labelsAndVinylExposeProductionControlsAtDisplayBudgets()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            for(boolean vinyl:new boolean[]{false,true}){
                var editor=new BusinessCardsPanel(true,false,false,vinyl);StudioRibbon ribbon=find(editor,StudioRibbon.class);assertNotNull(ribbon);
                assertNotNull(button(editor,"Roll / sheet setup"));assertNotNull(button(editor,"Create cut job"));assertNotNull(button(editor,"Open saved cut job"));
                for(int screen:new int[]{1366,1920})for(double scale:new double[]{1,1.25,1.5}){
                    ribbon.select("Cut Setup");ribbon.setSize((int)(screen/scale)-32,240);layout(ribbon);ribbon.setSize(ribbon.getWidth(),ribbon.getPreferredSize().height);layout(ribbon);
                    JTabbedPane tabs=find(ribbon,JTabbedPane.class);assertEquals("Cut Setup",tabs.getTitleAt(tabs.getSelectedIndex()));
                    for(Component group:((Container)tabs.getSelectedComponent()).getComponents())assertTrue(group.getY()+group.getHeight()<=tabs.getSelectedComponent().getHeight(),"Cut Setup clips at "+screen+" / "+scale);
                }
                editor.closeProduction();
            }
        });
    }
    private static JButton button(Component c,String text){if(c instanceof JButton b&&text.equals(b.getText()))return b;if(c instanceof Container n)for(Component child:n.getComponents()){var found=button(child,text);if(found!=null)return found;}return null;}
    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container n)layout(n);}
    private static <T>T find(Container c,Class<T> type){for(Component child:c.getComponents()){if(type.isInstance(child))return type.cast(child);if(child instanceof Container n){T result=find(n,type);if(result!=null)return result;}}return null;}
}
