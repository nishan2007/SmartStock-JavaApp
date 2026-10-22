package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BadgePrintRecordTest {
    @Test void newTemplateBindingsCreateBlankInputsAndReuseTheSameField()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            BadgeCardsPanel panel=new BadgeCardsPanel();
            try {
                var model=BadgeCardsPanel.class.getDeclaredField("template");model.setAccessible(true);
                BadgeTemplate design=new BadgeTemplate();model.set(panel,design);
                panel.bindTextField(-1,"Full name","fullName");
                panel.bindTextField(-1,"ID number","idNumber");
                var fields=BadgeCardsPanel.class.getDeclaredField("recordFields");fields.setAccessible(true);
                @SuppressWarnings("unchecked") Map<String,JTextField> inputs=(Map<String,JTextField>)fields.get(panel);
                assertEquals(java.util.Set.of("fullName","idNumber"),inputs.keySet());
                assertTrue(inputs.values().stream().allMatch(f->f.getText().isEmpty()));
                inputs.get("fullName").setText("Synthetic person");
                panel.bindTextField(-1,"Name repeated","fullName");
                assertEquals(2,inputs.size());assertEquals("Synthetic person",inputs.get("fullName").getText());
                assertTrue(design.elements.stream().allMatch(e->e.text.isEmpty()));
                String portable=BadgeTemplate.portableJson(design);
                assertTrue(portable.contains("fullName"));assertFalse(portable.contains("Synthetic person"));
                panel.resetFields();assertEquals("",inputs.get("fullName").getText());
                assertThrows(IllegalArgumentException.class,()->panel.bindTextField(-1,"Bad","full name"));
                assertEquals(3,design.elements.size());
            }catch(Exception ex){throw new RuntimeException(ex);}
            finally{try{var timer=BadgeCardsPanel.class.getDeclaredField("refresh");timer.setAccessible(true);((Timer)timer.get(panel)).stop();}catch(Exception ex){throw new RuntimeException(ex);}}
        });
    }
    @Test void loadingDetailsUsesCurrentLayoutAndOriginalPhotoWithoutRestoringOldDesign()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            BadgeCardsPanel panel=new BadgeCardsPanel();
            try {
                var model=BadgeCardsPanel.class.getDeclaredField("template");model.setAccessible(true);
                BadgeTemplate current=(BadgeTemplate)model.get(panel);
                var fields=BadgeCardsPanel.class.getDeclaredField("recordFields");fields.setAccessible(true);
                @SuppressWarnings("unchecked") Map<String,JTextField> inputs=(Map<String,JTextField>)fields.get(panel);
                inputs.get("fullName").setText("Synthetic record");
                var slot=current.elements.stream().filter(e->e.type.equals("PHOTO")).findFirst().orElseThrow();
                slot.personalPhoto=new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB);
                slot.personalPhoto.setRGB(0,0,0xff123456);slot.photoFraming=PhotoFraming.initial(slot.personalPhoto).positioned(1.3,.1,.2,1);
                var capture=BadgeCardsPanel.class.getDeclaredMethod("recordPayload");capture.setAccessible(true);
                var record=(com.google.gson.JsonObject)capture.invoke(panel);
                current.name="Updated current design";slot.x=9;slot.width=20;
                inputs.get("fullName").setText("Another person");panel.resetFields();
                panel.loadPrintRecord(record);
                assertSame(current,model.get(panel));assertEquals("Updated current design",current.name);
                assertEquals(9,slot.x);assertEquals(20,slot.width);
                assertEquals("Synthetic record",inputs.get("fullName").getText());
                assertEquals(0xff123456,slot.personalPhoto.getRGB(0,0));assertEquals(1.3,slot.photoFraming.zoom());assertEquals(1,slot.photoFraming.turns());
                assertEquals(java.util.Set.of("values","photos"),record.keySet());
                panel.resetFields();assertNull(slot.personalPhoto);assertTrue(inputs.values().stream().allMatch(f->f.getText().isEmpty()));
            }catch(Exception ex){throw new RuntimeException(ex);}
            finally{try{var timer=BadgeCardsPanel.class.getDeclaredField("refresh");timer.setAccessible(true);((Timer)timer.get(panel)).stop();}catch(Exception ex){throw new RuntimeException(ex);}}
        });
    }
}
