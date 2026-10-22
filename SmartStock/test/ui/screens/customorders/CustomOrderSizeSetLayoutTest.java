package ui.screens.customorders;

import org.junit.jupiter.api.Test;
import services.LanJson;
import services.CustomOrderDataService.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderSizeSetLayoutTest {
    @Test void editorRestoresQuantitiesFiltersColoursAndUpdatesPricePreview() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            try {
                var item=LanJson.create().fromJson("{\"customItemId\":1,\"name\":\"Custom shirt\",\"pricingType\":\"FIXED\",\"fixedPrice\":100,\"hasVariants\":true}",CustomItemOption.class);
                var small=new VariantOption(1L,"Small / Black","Small","Black","Small","Black","S",new BigDecimal("100"));
                var medium=new VariantOption(2L,"Medium / Black","Medium","Black","Medium","Black","M",new BigDecimal("120"));
                var red=new VariantOption(3L,"Small / Red","Small","Red","Small","Red","R",new BigDecimal("100"));
                var editor=new CustomOrderSizeSetEditor();
                editor.configure(item,List.of(small,medium,red),small,v->v.fixedPrice().add(new BigDecimal("25")));
                editor.restore(List.of(new CustomOrderSizeSetEditor.SizeQuantity(small,5),new CustomOrderSizeSetEditor.SizeQuantity(medium,4)));
                var content=editor.content();assertEquals(2,content.model().getRowCount());
                assertEquals(5,content.model().getValueAt(0,1));assertEquals("625",content.model().getValueAt(0,3));
                content.model().setValueAt(6,0,1);assertEquals("750",content.model().getValueAt(0,3));
                content.model().setValueAt("1.5",0,1);assertEquals("Invalid quantity",content.model().getValueAt(0,3));
                content.model().setValueAt(5,0,1);
                render(content.panel(),640,300,"size-set-editor.png");
                var handler=(CustomOrdersNewOrderTabPanel.Handler)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{CustomOrdersNewOrderTabPanel.Handler.class},(proxy,method,args)->{
                    if(method.getReturnType()==Runnable.class)return (Runnable)()->{};
                    if(method.getReturnType()==boolean.class)return true;return null;
                });
                var panel=new CustomOrdersNewOrderTabPanel(handler);
                panel.printDescriptionField.setText("Front logo");
                panel.printColorField.setText(" Gold ");
                assertEquals("Front logo | Colour: Gold",panel.printAddonDescription());
                panel.printColorField.setText("");
                assertEquals("Front logo",panel.printAddonDescription());
                panel.orderItemBox.addItem(item);panel.variantBox.addItem(small);
                panel.sizeSetEditor.configure(item,List.of(small,medium),small,v->v.fixedPrice().add(new BigDecimal("25")));
                panel.sizeSetEditor.restore(editor.selections());
                Object[] cartRow=new Object[panel.orderLineModel.getColumnCount()];
                cartRow[2]="Black dri-fit collar shirt VB";cartRow[3]="Black dri-fit collar shirt VB";
                cartRow[32]="Small";cartRow[33]="Black";cartRow[34]=1;cartRow[21]="Back name / Text: Alice";
                panel.orderLineModel.addRow(cartRow);
                var second=cartRow.clone();second[34]=2;second[21]="Back name / Text: Bob";panel.orderLineModel.addRow(second);
                var third=cartRow.clone();third[32]="Medium";third[21]="Back name / Text: Carol";panel.orderLineModel.addRow(third);
                panel.showCartLines();
                assertEquals("Small",panel.orderLineTable.getValueAt(0,panel.orderLineTable.getColumnModel().getColumnIndex("Size")));
                assertEquals("Black",panel.orderLineTable.getValueAt(0,panel.orderLineTable.getColumnModel().getColumnIndex("Colour")));
                for(int width:new int[]{1320,1000})render(panel,width,850,"size-set-order-"+width+".png");
                var textEditor=CustomOrderShirtText.editor(CustomOrderShirtText.slots(editor.selections(),null,null,1),java.util.Map.of("1:1","Alice","2:1","Carol"));
                render(textEditor.panel(),640,420,"shirt-names-editor.png");
                editor.configure(item,List.of(small,medium,red),medium,v->null);assertEquals(2,editor.selections().size());
                assertEquals("Price pending",editor.content().model().getValueAt(0,2));
                editor.configure(item,List.of(small,medium,red),red,v->v.fixedPrice());assertTrue(editor.selections().isEmpty());
                editor.configure(null,List.of(),null,v->BigDecimal.ZERO);
                assertTrue(editor.isVisible());assertFalse(editor.button.isEnabled());
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
    private static void render(JComponent component,int width,int height,String filename)throws Exception{
        component.setSize(width,height);layout(component);
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        try{component.printAll(g);}finally{g.dispose();}
        Path dir=Path.of("target","size-set-preview");Files.createDirectories(dir);javax.imageio.ImageIO.write(image,"png",dir.resolve(filename).toFile());
    }
    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container nested)layout(nested);}
}
