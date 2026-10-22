package gy.deckers.studio;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import publishing.*;
import static org.junit.jupiter.api.Assertions.*;

class RisoSubPagesDialogTest {
    Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    JButton button(Container root,String name){for(var child:root.getComponents()){if(child instanceof JButton b&&b.getText().equals(name))return b;if(child instanceof Container c){var found=button(c,name);if(found!=null)return found;}}return null;}
    @Test void selectedObjectsCreateLinkedPageAndMovementUndoAndRemovalPreserveSource()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(()->{RisoSubPagesDialog dialog=null;try{
            Publication original=new LinkedRisoTest().document();var constructor=RisoSubPagesDialog.class.getDeclaredConstructor(Component.class,Publication.class,int.class,String.class);constructor.setAccessible(true);dialog=constructor.newInstance(new JPanel(),original,0,LinkedRiso.MAIN);
            JTable table=(JTable)field(dialog,"objects");table.setRowSelectionInterval(0,0);button(dialog,"Add sub-page").doClick();var p=(Publication)field(dialog,"document");String id=p.pages.get(0).risoPages.get(0).id;assertEquals(1,LinkedRiso.visible(p,0,id).size());assertTrue(original.pages.get(0).risoPages.isEmpty());
            table.setRowSelectionInterval(0,0);var canvas=(TemplateWorkspace)field(dialog,"canvas");canvas.getActionMap().get("shift RIGHT").actionPerformed(null);assertEquals(6,LinkedRiso.visible(p,0,id).get(0).x);assertEquals(1,canvas.selectedIndices().length);assertEquals(1,table.getSelectedRowCount());button(dialog,"Undo").doClick();p=(Publication)field(dialog,"document");assertEquals(5,LinkedRiso.visible(p,0,id).get(0).x);
            button(dialog,"Remove sub-page").doClick();p=(Publication)field(dialog,"document");assertEquals(2,p.pages.get(0).objects.size());assertTrue(p.pages.get(0).risoPages.isEmpty());assertEquals(2,LinkedRiso.visible(p,0,LinkedRiso.UNASSIGNED).size());
        }catch(Exception ex){throw new RuntimeException(ex);}finally{if(dialog!=null)dialog.dispose();}});
    }
}
