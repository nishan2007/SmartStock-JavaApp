package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import publishing.*;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

class SheetNumberingControlsTest {
    @Test void controlsSurviveTemplateArchiveAndRestore()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            try{
                var panel=new BusinessCardsPanel(false,false,true);
                var field=BusinessCardsPanel.class.getDeclaredField("numberingControls");field.setAccessible(true);
                var controls=(SheetNumberingControls)field.get(panel);
                controls.order.setSelectedIndex(6);controls.cutStack.setSelected(true);
                var output=panel.output();
                assertEquals("COLUMNS_TOP_RIGHT",output.print.positionOrder);assertTrue(output.print.cutAndStack);
                var saved=PublicationFiles.read(PublicationFiles.archive(output));
                controls.order.setSelectedIndex(0);controls.cutStack.setSelected(false);
                panel.restoreSheetSize(saved.print);
                assertEquals("COLUMNS_TOP_RIGHT",panel.output().print.positionOrder);assertTrue(panel.output().print.cutAndStack);
                controls.order.setSelectedIndex(9);assertTrue(controls.custom.isEnabled());controls.custom.setText("2,1");
                var custom=PublicationFiles.read(PublicationFiles.archive(panel.output()));
                assertEquals("CUSTOM",custom.print.positionOrder);assertEquals("2,1",custom.print.customPositionOrder);
                var timer=BusinessCardsPanel.class.getDeclaredField("refresh");timer.setAccessible(true);((Timer)timer.get(panel)).stop();
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
}
