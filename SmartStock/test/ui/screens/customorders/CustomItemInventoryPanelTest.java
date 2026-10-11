package ui.screens.customorders;

import com.google.gson.*;
import services.LanCustomOrderCatalogAdminService.State;
import services.LanJson;
import javax.swing.*;
import java.awt.event.MouseEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomItemInventoryPanelTest {
    @Test void descriptionFollowsNameAndTemplateSizesCanBeShownWithInheritance()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            String source="""
                {"items":[{"itemId":1,"name":"Shirt","description":"Cotton shirt","active":true,"hasVariants":true,
                "templateWidth":10.00,"templateHeight":12.00,"templateUnit":"IN","variants":[
                  {"variantId":1,"itemId":1,"name":"Inherited","active":true},
                  {"variantId":2,"itemId":1,"name":"Override","active":true,"templateWidth":8,"templateHeight":9,"templateUnit":"CM"}
                ]}],"materials":[],"presets":[],"placements":[]}
                """;
            var panel=new CustomItemInventoryPanel(null,()->{},(action,row)->{},id->{});
            panel.setState(LanJson.create().fromJson(source,State.class));
            assertEquals("Description",panel.table.getColumnName(1));
            assertEquals("Cotton shirt",panel.table.getValueAt(0,1));
            int template=panel.table.convertColumnIndexToView(14);
            assertEquals(0,panel.table.getColumnModel().getColumn(template).getMaxWidth());
            panel.showTemplate.doClick();
            assertTrue(panel.table.getColumnModel().getColumn(template).getMaxWidth()>0);
            assertEquals("10 × 12 IN, 8 × 9 CM",panel.table.getValueAt(0,template));
            toggle(panel);
            assertEquals("10 × 12 IN",panel.table.getValueAt(1,template));
            assertEquals("8 × 9 CM",panel.table.getValueAt(2,template));
            panel.table.setValueAt("New description",0,1);
            assertEquals("New description",panel.staged("I:1").get("description").getAsString());
            assertEquals("New description",panel.table.getValueAt(1,1));
            assertFalse(panel.table.isCellEditable(1,1));
            panel.showTemplate.doClick();
            assertEquals(0,panel.table.getColumnModel().getColumn(template).getMaxWidth());
        });
    }
    @Test void parentSummarizesDistinctAttributesAndTracksUnsavedVariantEdits()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            String source="""
                {"items":[{"itemId":1,"name":"Shirt","brand":"SPORTS","active":true,"hasVariants":true,"variants":[
                  {"variantId":1,"itemId":1,"name":"Small","size":"S","color":"Black","brand":"","active":true},
                  {"variantId":2,"itemId":1,"name":"Large","size":"L","color":"Black","brand":"SPORTS","active":true},
                  {"variantId":3,"itemId":1,"name":"Large copy","size":"L","color":"black","brand":"sports","active":true}
                ]}],"materials":[],"presets":[],"placements":[]}
                """;
            var panel=new CustomItemInventoryPanel(null,()->{},(action,row)->{},id->{});
            panel.setState(LanJson.create().fromJson(source,State.class));
            assertEquals("S, L",panel.table.getValueAt(0,panel.table.convertColumnIndexToView(1)));
            assertEquals("Black",panel.table.getValueAt(0,panel.table.convertColumnIndexToView(2)));
            assertEquals("SPORTS",panel.table.getValueAt(0,panel.table.convertColumnIndexToView(3)));
            toggle(panel);
            panel.table.setValueAt("Blue",2,panel.table.convertColumnIndexToView(2));
            assertEquals("Black, Blue",panel.table.getValueAt(0,panel.table.convertColumnIndexToView(2)));
            panel.table.setValueAt("OTHER",2,panel.table.convertColumnIndexToView(3));
            assertEquals("SPORTS, OTHER",panel.table.getValueAt(0,panel.table.convertColumnIndexToView(3)));
            toggle(panel);
            assertEquals("Black, Blue",panel.table.getValueAt(0,panel.table.convertColumnIndexToView(2)));
            assertEquals("SPORTS, OTHER",panel.table.getValueAt(0,panel.table.convertColumnIndexToView(3)));
        });
    }
    @Test void archiveIsSeparateFromInactiveAndHiddenUntilRequested()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            String source="""
                {"items":[
                  {"itemId":1,"name":"Inactive","active":false,"archived":false,"variants":[]},
                  {"itemId":2,"name":"Archived","active":true,"archived":true,"variants":[]}
                ],"materials":[],"presets":[],"placements":[]}
                """;
            var panel=new CustomItemInventoryPanel(null,()->{},(action,row)->{},id->{});
            panel.setState(LanJson.create().fromJson(source,State.class));
            assertEquals(1,panel.table.getRowCount());
            assertEquals("Inactive",panel.table.getValueAt(0,0));
            panel.archivedOnly.doClick();
            assertEquals(1,panel.table.getRowCount());
            assertEquals("Archived",panel.table.getValueAt(0,0));
            assertEquals(true,panel.table.getValueAt(0,panel.table.convertColumnIndexToView(8)));
            assertFalse(panel.table.isCellEditable(0,0));
            panel.archivedOnly.doClick();
            assertEquals("Inactive",panel.table.getValueAt(0,0));
        });
    }
    @Test void enterCommitsCellBeforeSavingAndAlsoSavesWhenTableHasFocus()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            var table=new JTable(new javax.swing.table.DefaultTableModel(new Object[][]{{"Original"}},new String[]{"Name"}));
            var saves=new java.util.concurrent.atomic.AtomicInteger();
            CustomItemInventoryPanel.installEnterSave(table,()->{
                assertFalse(table.isEditing());
                assertEquals("Updated",table.getValueAt(0,0));
                saves.incrementAndGet();
            });
            assertTrue(table.editCellAt(0,0));
            var field=(JTextField)table.getEditorComponent();field.setText("Updated");
            var enter=KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER,0);
            var key=field.getInputMap(JComponent.WHEN_FOCUSED).get(enter);
            field.getActionMap().get(key).actionPerformed(new java.awt.event.ActionEvent(field,0,"enter"));
            assertEquals(1,saves.get());
            key=table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(enter);
            table.getActionMap().get(key).actionPerformed(new java.awt.event.ActionEvent(table,0,"enter"));
            assertEquals(2,saves.get());
        });
    }
    @Test void dateDisplayAndChronologicalSortingKeepVariantsWithTheirParent()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            String source="""
                {"items":[
                  {"itemId":1,"name":"Older","createdAt":"2026-10-01T15:00:00Z","active":true,"hasVariants":true,"variants":[{"variantId":5,"itemId":1,"name":"Child","active":true,"createdAt":"2026-10-10T15:00:00Z"}]},
                  {"itemId":2,"name":"Newer","createdAt":"2026-10-09T15:00:00Z","active":true,"variants":[]},
                  {"itemId":3,"name":"Unknown","active":true,"variants":[]}
                ],"materials":[],"presets":[],"placements":[]}
                """;
            var panel=new CustomItemInventoryPanel(null,()->{},(action,row)->{},id->{});
            panel.setState(LanJson.create().fromJson(source,State.class));
            assertEquals(0,panel.table.getColumnModel().getColumn(panel.table.convertColumnIndexToView(11)).getMaxWidth());
            panel.showCreated.doClick();
            assertTrue(panel.table.getColumnModel().getColumn(panel.table.convertColumnIndexToView(11)).getMaxWidth()>0);
            assertTrue(panel.table.getValueAt(0,panel.table.convertColumnIndexToView(11)).toString().contains("1 Oct 2026"));
            assertFalse(panel.table.isCellEditable(0,panel.table.convertColumnIndexToView(11)));
            toggle(panel);
            panel.dateSort.setSelectedIndex(1);
            assertEquals("Newer",panel.table.getValueAt(0,0));
            assertTrue(panel.table.getValueAt(1,0).toString().contains("Older"));
            assertEquals("Child",panel.table.getValueAt(2,0).toString().trim());
            assertEquals("Unknown",panel.table.getValueAt(3,0));
            panel.dateSort.setSelectedIndex(2);
            assertTrue(panel.table.getValueAt(0,0).toString().contains("Older"));
            assertEquals("Child",panel.table.getValueAt(1,0).toString().trim());
            assertEquals("Newer",panel.table.getValueAt(2,0));
        });
    }
    @Test void variantsExpandAndEditsSurviveCollapseWithoutOverwritingOtherFields()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            String source="{\"items\":[{\"itemId\":1,\"name\":\"Shirt\",\"hasVariants\":true,\"quantity\":10,\"active\":true,\"productType\":\"INVENTORY\",\"pricingType\":\"FIXED\",\"imageUrl\":\"parent-photo\",\"variants\":[{\"variantId\":5,\"itemId\":1,\"name\":\"Large blue\",\"size\":\"L\",\"color\":\"Blue\",\"fixedPrice\":1500,\"quantity\":10,\"reorderLevel\":2,\"active\":true,\"imageUrl\":\"\",\"extraBarcodes\":[\"keep-barcode\"]}]}],\"materials\":[],\"presets\":[],\"placements\":[]}";
            var panel=new CustomItemInventoryPanel(null,()->{},(action,row)->{},id->{});
            panel.setState(LanJson.create().fromJson(source,State.class));
            assertEquals(1,panel.table.getRowCount());assertFalse(panel.table.isCellEditable(0,0));
            int posColumn=panel.table.convertColumnIndexToView(12);
            assertTrue(panel.table.isCellEditable(0,posColumn));
            panel.table.setValueAt(true,0,posColumn);
            assertTrue(panel.staged("I:1").get("sellInPos").getAsBoolean());
            toggle(panel);assertEquals(2,panel.table.getRowCount());assertTrue(panel.table.isCellEditable(1,panel.table.convertColumnIndexToView(5)));
            assertEquals(true,panel.table.getValueAt(1,posColumn));
            assertFalse(panel.table.isCellEditable(1,posColumn));
            panel.table.setValueAt("1750",1,panel.table.convertColumnIndexToView(5));
            toggle(panel);assertEquals(1,panel.table.getRowCount());toggle(panel);
            assertEquals("1750",panel.table.getValueAt(1,panel.table.convertColumnIndexToView(5)));
            var staged=panel.staged("V:5");assertEquals(5,staged.get("variantId").getAsLong());
            assertEquals("keep-barcode",staged.getAsJsonArray("extraBarcodes").get(0).getAsString());
            assertEquals("",staged.get("imageUrl").getAsString());
            assertEquals("Blue",staged.get("color").getAsString());
        });
    }
    private static void toggle(CustomItemInventoryPanel panel){
        var cell=panel.table.getCellRect(0,0,true);
        var event=new MouseEvent(panel.table,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,cell.x+4,cell.y+4,1,false,MouseEvent.BUTTON1);
        for(var listener:panel.table.getMouseListeners())listener.mouseClicked(event);
    }
}
