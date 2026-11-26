package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import publishing.*;
import static org.junit.jupiter.api.Assertions.*;

class StudioPageActionsTest {
    @Test void pageActionsAreVisibleAddBlankPagesAndUndoWithoutAddingFields()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            try{
                for(boolean labels:new boolean[]{false,true}){
                    var editor=new BusinessCardsPanel(labels);PublicationPages navigation=(PublicationPages)field(editor,"pagePreviews");
                    navigation.setSize(180,500);layout(navigation);
                    JButton add=button(navigation,"Add page"),delete=button(navigation,"Delete page");assertNotNull(add);assertNotNull(delete);assertFalse(delete.isEnabled());assertTrue(add.getHeight()>=40);assertTrue(add.getWidth()>=64);
                    add.doClick();assertEquals(2,editor.output().pages.size());assertEquals(1,field(editor,"pageIndex"));assertTrue(delete.isEnabled());
                    assertTrue(editor.output().pages.get(1).objects.isEmpty());assertTrue(editor.output().mergeColumns.isEmpty());assertEquals(2,navigation.list.getModel().getSize());
                    var saved=PublicationFiles.read(PublicationFiles.archive(editor.output()));assertEquals(2,saved.pages.size());
                    editor.historyAction(false);assertEquals(1,editor.output().pages.size());assertFalse(delete.isEnabled());
                    editor.historyAction(true);assertEquals(2,editor.output().pages.size());assertTrue(delete.isEnabled());
                }
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
    @Test void deleteConfirmsRemovesSelectedPageAndCanBeUndone()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(()->{
            try{
                var editor=new BusinessCardsPanel(true);PublicationPages navigation=(PublicationPages)field(editor,"pagePreviews");button(navigation,"Add page").doClick();
                Timer confirm=new Timer(150,e->{for(Window window:Window.getWindows())if(window instanceof JDialog dialog&&dialog.isShowing()){JOptionPane pane=find(dialog,JOptionPane.class);if(pane!=null)pane.setValue(JOptionPane.YES_OPTION);}});confirm.start();
                try{button(navigation,"Delete page").doClick();}finally{confirm.stop();}
                assertEquals(1,editor.output().pages.size());assertEquals(1,navigation.list.getModel().getSize());assertFalse(button(navigation,"Delete page").isEnabled());
                editor.historyAction(false);assertEquals(2,editor.output().pages.size());assertEquals(2,navigation.list.getModel().getSize());
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container n)layout(n);}
    private static JButton button(Container c,String title){for(Component child:c.getComponents()){if(child instanceof JButton b&&title.equals(b.getText()))return b;if(child instanceof Container n){var result=button(n,title);if(result!=null)return result;}}return null;}
    private static <T>T find(Container c,Class<T> type){for(Component child:c.getComponents()){if(type.isInstance(child))return type.cast(child);if(child instanceof Container n){T result=find(n,type);if(result!=null)return result;}}return null;}
}
