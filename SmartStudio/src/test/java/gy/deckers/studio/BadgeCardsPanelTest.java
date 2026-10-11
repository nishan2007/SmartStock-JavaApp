package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BadgeCardsPanelTest {
    @TempDir Path temp;
    @Test void customBindingsBuildEntryFieldsAndSignOutClearsTheirValues()throws Exception {
        BadgeTemplate t=BadgeTemplate.builtin();t.elements.get(5).field="department";t.elements.get(5).label="Department";
        Path file=temp.resolve("Staff.sstbadge");t.save(file);String original=Files.readString(file);
        String previous=System.getProperty("smartstudio.badge.folder");System.setProperty("smartstudio.badge.folder",temp.toString());
        try {
            SwingUtilities.invokeAndWait(()->{
                BadgeCardsPanel panel=new BadgeCardsPanel();
                try {
                    var load=BadgeCardsPanel.class.getDeclaredMethod("loadTemplate",Path.class);load.setAccessible(true);load.invoke(panel,file);
                    var fieldsProperty=BadgeCardsPanel.class.getDeclaredField("recordFields");fieldsProperty.setAccessible(true);
                    @SuppressWarnings("unchecked") Map<String,JTextField> fields=(Map<String,JTextField>)fieldsProperty.get(panel);
                    assertTrue(fields.containsKey("department"));assertFalse(fields.containsKey("grade"));
                    fields.get("department").setText("Synthetic department");
                    var values=BadgeCardsPanel.class.getDeclaredMethod("values");values.setAccessible(true);
                    assertEquals("Synthetic department",((Map<?,?>)values.invoke(panel)).get("department"));
                    var modelProperty=BadgeCardsPanel.class.getDeclaredField("layers");modelProperty.setAccessible(true);
                    ((AbstractTableModel)modelProperty.get(panel)).setValueAt("employeeNumber",4,3);
                    assertTrue(fields.containsKey("employeeNumber"));assertFalse(fields.containsKey("fullName"));
                    assertEquals("Synthetic department",fields.get("department").getText());
                    panel.resetFields();assertTrue(((Map<?,?>)values.invoke(panel)).isEmpty());
                    assertEquals(original,Files.readString(file));
                } catch(Exception ex){throw new RuntimeException(ex);}
                finally {try {var timer=BadgeCardsPanel.class.getDeclaredField("refresh");timer.setAccessible(true);((Timer)timer.get(panel)).stop();}catch(Exception ex){throw new RuntimeException(ex);}}
            });
        } finally {if(previous==null)System.clearProperty("smartstudio.badge.folder");else System.setProperty("smartstudio.badge.folder",previous);}
    }
}
