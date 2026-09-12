package ui.helpers;

import org.junit.jupiter.api.Test;
import services.LanApiClient;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GroupedProductRowsTest {
    private static LanApiClient.ProductGroup group(){return new LanApiClient.ProductGroup("group","Bottle",List.of("Color"),1,List.of(
            new LanApiClient.VariantMember(11,Map.of("Color","Blue"),true,"blue.png","Blue bottle",8,new BigDecimal("25")),
            new LanApiClient.VariantMember(12,Map.of("Color","Red"),true,"red.png","Red bottle",5,new BigDecimal("30"))));}
    @Test void headersCannotBeSoldAndExpansionPreservesIndividualQuantities() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            DefaultTableModel model=new DefaultTableModel(new Object[]{"ID","Name","Price","Quantity"},0);
            JTable table=new JTable(model);GroupedProductRows rows=new GroupedProductRows(table,0,1,2,3);
            model.addRow(new Object[]{11,"Blue bottle",new BigDecimal("25"),8});model.addRow(new Object[]{12,"Red bottle",new BigDecimal("30"),5});
            rows.capture(List.of(group()),"");assertEquals(1,model.getRowCount());assertTrue(rows.isParent(0));assertEquals(13L,model.getValueAt(0,3));
            assertEquals(2,rows.pictures(0).size());table.setRowSelectionInterval(0,0);assertTrue(rows.expandSelected());
            assertEquals(3,model.getRowCount());assertEquals(11,model.getValueAt(1,0));assertEquals(8,model.getValueAt(1,3));assertFalse(rows.isParent(1));assertEquals(1,rows.pictures(1).size());
            table.setRowSelectionInterval(1,1);assertFalse(rows.expandSelected());
            table.setRowHeight(30);table.setSize(880,100);table.doLayout();
            java.awt.image.BufferedImage preview=new java.awt.image.BufferedImage(880,100,java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D graphics=preview.createGraphics();table.paint(graphics);graphics.dispose();
            try{javax.imageio.ImageIO.write(preview,"png",new java.io.File("target/variant-table-preview.png"));}catch(java.io.IOException ex){throw new RuntimeException(ex);}
            assertEquals("Blue bottle",model.getValueAt(1,1),"Rendering must not change the editable product name");
        });
    }
    @Test void aFilteredGroupExplicitlyReportsPartialResults() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            DefaultTableModel model=new DefaultTableModel(new Object[]{"ID","Name","Price","Quantity"},0);JTable table=new JTable(model);
            GroupedProductRows rows=new GroupedProductRows(table,0,1,2,3);model.addRow(new Object[]{11,"Blue bottle",new BigDecimal("25"),8});
            rows.capture(List.of(group()),"blue");assertEquals(2,model.getRowCount());assertTrue(model.getValueAt(0,1).toString().contains("1 of 2"));
            assertEquals(13L,model.getValueAt(0,3));assertEquals(11,model.getValueAt(1,0));
        });
    }
}
