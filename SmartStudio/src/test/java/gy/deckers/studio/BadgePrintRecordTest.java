package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import static org.junit.jupiter.api.Assertions.*;

class BadgePrintRecordTest {
    @Test void dataRibbonOpensScopedRecordsAndLoadsTheSelectedBadge(@TempDir Path folder)throws Exception{
        String templateId=UUID.randomUUID().toString(), recordId=UUID.randomUUID().toString();
        Path file=folder.resolve("2_"+templateId+".sstbadge");
        BadgeTemplate.builtin().save(file);
        var metadata=new JsonObject();
        metadata.addProperty("id",templateId);
        metadata.addProperty("storageStoreId",2);
        Files.writeString(file.resolveSibling(file.getFileName()+".meta.json"),metadata.toString());

        var values=new JsonObject();
        values.addProperty("fullName","Synthetic rider");
        values.addProperty("olderField","Preserved");
        var library=new SharedBadgeLibrary(folder,request->{
            assertEquals(templateId,request.get("id").getAsString());
            assertEquals(2,request.get("storageStoreId").getAsInt());
            var response=new JsonObject();
            if("RECORD_LIST".equals(request.get("action").getAsString())){
                var record=new JsonObject();
                record.addProperty("recordId",recordId);
                record.addProperty("createdAt","2026-11-25");
                record.add("values",values);
                var records=new JsonArray();
                records.add(record);
                response.add("records",records);
            }else{
                assertEquals("RECORD_GET",request.get("action").getAsString());
                assertEquals(recordId,request.get("recordId").getAsString());
                response.add("values",values);
                response.add("photos",new JsonObject());
            }
            return response;
        });
        var panelRef=new AtomicReference<BadgeCardsPanel>();
        var browserRef=new AtomicReference<StudioRecordBrowser<?>>();
        var ready=new CountDownLatch(1);
        SwingUtilities.invokeAndWait(()->{
            try{
                var panel=new BadgeCardsPanel();
                panelRef.set(panel);
                var shared=BadgeCardsPanel.class.getDeclaredField("shared");
                shared.setAccessible(true);
                shared.set(panel,library);
                panel.openDocument(file);
                var shell=find(panel,StudioWorkspaceShell.class);
                shell.ribbon().select("Data");
                var command=find(shell.ribbon(),AbstractButton.class,"DB");
                assertNotNull(command);
                command.doClick(0);
                var browser=find(shell,StudioRecordBrowser.class);
                assertNotNull(browser);
                browserRef.set(browser);
                browser.open.addPropertyChangeListener("enabled",e->{if(browser.open.isEnabled())ready.countDown();});
            }catch(Exception ex){throw new IllegalStateException(ex);}
        });
        try{
            assertTrue(ready.await(5,TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(()->{
                try{
                    var panel=panelRef.get();
                    var browser=browserRef.get();
                    assertEquals("Preserved",browser.table.getValueAt(0,browser.table.getColumn("olderField").getModelIndex()));
                    browser.open.doClick(0);
                    assertFalse(find(panel,StudioWorkspaceShell.class).recordsOpen());
                    var current=BadgeCardsPanel.class.getDeclaredMethod("values");
                    current.setAccessible(true);
                    assertEquals("Synthetic rider",((Map<?,?>)current.invoke(panel)).get("fullName"));
                }catch(Exception ex){throw new IllegalStateException(ex);}
            });
        }finally{
            SwingUtilities.invokeAndWait(()->{
                var panel=panelRef.get();
                find(panel,StudioWorkspaceShell.class).closeRecords();
                try{var timer=BadgeCardsPanel.class.getDeclaredField("refresh");timer.setAccessible(true);((Timer)timer.get(panel)).stop();}
                catch(Exception ex){throw new IllegalStateException(ex);}
            });
        }
    }

    private static <T> T find(Container parent,Class<T> type){
        return find(parent,type,null);
    }
    private static <T> T find(Container parent,Class<T> type,String label){
        for(Component child:parent.getComponents()){
            if(type.isInstance(child)&&(label==null||child instanceof AbstractButton button&&label.equals(button.getText())))
                return type.cast(child);
            if(child instanceof Container nested){
                T match=find(nested,type,label);
                if(match!=null)return match;
            }
        }
        return null;
    }

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
