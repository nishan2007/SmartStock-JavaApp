package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import publishing.*;
import javax.swing.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class InchMeasurementTest {
    @Test void inchMarginsSaveInPhysicalUnitsAndReopenWithoutResizing()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            try{
                var panel=new BusinessCardsPanel(true);
                assertEquals("IN",panel.output().displayUnit);
                var left=BusinessCardsPanel.class.getDeclaredField("leftMargin");left.setAccessible(true);
                var gap=BusinessCardsPanel.class.getDeclaredField("gap");gap.setAccessible(true);
                var verticalGap=BusinessCardsPanel.class.getDeclaredField("verticalGap");verticalGap.setAccessible(true);
                ((JSpinner)left.get(panel)).setValue(.25);((JSpinner)gap.get(panel)).setValue(-.125);
                ((JSpinner)verticalGap.get(panel)).setValue(-.35);
                var saved=PublicationFiles.read(PublicationFiles.archive(panel.output()));
                assertEquals(6.35,saved.print.left,.0000001);assertEquals(-3.175,saved.print.gapX,.0000001);
                assertEquals(-8.89,saved.print.gapY,.0000001);
                var reopened=new BusinessCardsPanel(true);reopened.restoreSheetSize(saved.print);
                assertEquals(.25,((Number)((JSpinner)left.get(reopened)).getValue()).doubleValue(),.0000001);
                assertEquals(-.125,((Number)((JSpinner)gap.get(reopened)).getValue()).doubleValue(),.0000001);
                assertEquals(-.35,((Number)((JSpinner)verticalGap.get(reopened)).getValue()).doubleValue(),.0000001);
                var style=new AtomicReference<>(new TextFormattingToolbar.Style("Arial",12,false,false,false,false,"#000000",true,"#000000",.2,null));
                var text=new TextFormattingToolbar(style::get,style::set,()->publishing.TextAlignment.LEFT);text.bold.doClick();
                assertEquals(.2,style.get().outlineWidth(),.0000001,"Changing bold must not round the converted outline width");
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
}
