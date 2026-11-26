package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import publishing.*;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BillBookRibbonOutputTest {
    static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static <T>T find(Container parent,Class<T> type){for(Component c:parent.getComponents()){if(type.isInstance(c))return type.cast(c);if(c instanceof Container n){T value=find(n,type);if(value!=null)return value;}}return null;}
    static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container n)layout(n);}
    @Test void visibleNumberingChoicesDrivePreviewOutputWithoutChangingSavedArtwork()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            try{
                var editor=new BusinessCardsPanel(false,false,true);StudioRibbon ribbon=find(editor,StudioRibbon.class);ribbon.select("Numbering");
                JComboBox<?> choice=(JComboBox<?>)field(editor,"billBookOutput");JTextField fields=(JTextField)field(editor,"billBookNumberFields");
                JTabbedPane tabs=find(ribbon,JTabbedPane.class);Component page=tabs.getSelectedComponent();assertTrue(SwingUtilities.isDescendingFrom(choice,page));assertTrue(SwingUtilities.isDescendingFrom(fields,page));
                ribbon.autoCompact(true);assertFalse(ribbon.isCollapsed());ribbon.setSize(880,300);layout(ribbon);ribbon.setSize(880,ribbon.getPreferredSize().height);layout(ribbon);assertTrue(choice.getHeight()>=40,"choice "+choice.getBounds());assertTrue(fields.getWidth()>=180,"fields "+fields.getBounds());
                SheetNumberingControls controls=(SheetNumberingControls)field(editor,"numberingControls");
                for(Component control:new Component[]{controls.order,controls.custom,controls.cutStack,controls.map})assertTrue(SwingUtilities.isDescendingFrom(control,page));
                for(int screen:new int[]{1366,1920})for(double scale:new double[]{1,1.25,1.5}){
                    ribbon.setSize((int)(screen/scale)-32,300);layout(ribbon);ribbon.setSize(ribbon.getWidth(),ribbon.getPreferredSize().height);layout(ribbon);
                    for(Component control:new Component[]{choice,fields,controls.order,controls.custom,controls.cutStack,controls.map}){
                        Rectangle bounds=SwingUtilities.convertRectangle(control.getParent(),control.getBounds(),page);
                        assertTrue(bounds.x>=0&&bounds.y>=0&&bounds.x+bounds.width<=page.getWidth()&&bounds.y+bounds.height<=page.getHeight(),control+" clipped at "+screen+" / "+scale+": "+bounds);
                    }
                }
                controls.order.setSelectedIndex(9);assertTrue(controls.custom.isEnabled());controls.order.setSelectedIndex(1);assertFalse(controls.custom.isEnabled());
                controls.cutStack.setSelected(true);assertTrue(editor.output().print.cutAndStack);controls.cutStack.setSelected(false);
                Publication document=(Publication)field(editor,"document");document.pages.get(0).objects.clear();
                var art=new Publication.Element();art.text="Synthetic invoice";document.pages.get(0).objects.add(art);var number=new Publication.Element();number.text="{{number}}";document.pages.get(0).objects.add(number);
                editor.loadRows(java.util.List.of("number"),java.util.List.of(Map.of("number","001")));fields.setText("number");
                choice.setSelectedIndex(1);Publication only=editor.printOutput();assertEquals(1,only.pages.get(0).objects.size());assertEquals("{{number}}",only.pages.get(0).objects.get(0).text);
                choice.setSelectedIndex(2);Publication without=editor.printOutput();assertTrue(without.pages.get(0).objects.stream().noneMatch(e->e.text.contains("{{number}}")));assertTrue(without.pages.get(0).objects.stream().anyMatch(e->e.text.equals("Synthetic invoice")));
                choice.setSelectedIndex(0);assertEquals(2,editor.printOutput().pages.get(0).objects.size());assertEquals("{{number}}",editor.output().pages.get(0).objects.get(1).text);
                ribbon.setSize(1334,300);layout(ribbon);ribbon.setSize(1334,ribbon.getPreferredSize().height);layout(ribbon);
                var image=new java.awt.image.BufferedImage(ribbon.getWidth(),ribbon.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();ribbon.printAll(graphics);graphics.dispose();javax.imageio.ImageIO.write(image,"png",java.nio.file.Path.of("target","numbering-ribbon.png").toFile());
                ((javax.swing.Timer)field(editor,"refresh")).stop();((javax.swing.Timer)field(editor,"thumbnailRefresh")).stop();
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
}
