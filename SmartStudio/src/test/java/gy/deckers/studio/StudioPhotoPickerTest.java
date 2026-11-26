package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import static org.junit.jupiter.api.Assertions.*;

class StudioPhotoPickerTest {
    @Test void templatePickerOffersPreviewAndSelectionWithoutQueueAdministration()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var picker=new StudioPhotoQueuePanel(photo->fail("No photo was selected"),true);
            assertNotNull(button(picker,"Insert selected photo"));
            assertNotNull(button(picker,"Use original photo"));
            assertNotNull(button(picker,"Refresh"));
            assertNull(button(picker,"Delete selected…"));
            assertNull(button(picker,"Pair staff phone / QR…"));
            assertTrue(contains(picker,SheetPreview.class));
            assertTrue(contains(picker,JTable.class));
            button(picker,"Insert selected photo").doClick();
            picker.reset();
        });
    }
    @Test void standaloneQueueKeepsPassportAndAdministrationActions()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var queue=new StudioPhotoQueuePanel(photo->{});
            assertNotNull(button(queue,"Use in Passport Pictures"));
            assertNotNull(button(queue,"Delete selected…"));
            queue.reset();
        });
    }
    private static JButton button(Component c,String text){
        if(c instanceof JButton b&&text.equals(b.getText()))return b;
        if(c instanceof Container n)for(Component child:n.getComponents()){var found=button(child,text);if(found!=null)return found;}
        return null;
    }
    private static boolean contains(Component c,Class<?> type){
        if(type.isInstance(c))return true;
        if(c instanceof Container n)for(Component child:n.getComponents())if(contains(child,type))return true;
        return false;
    }
}
