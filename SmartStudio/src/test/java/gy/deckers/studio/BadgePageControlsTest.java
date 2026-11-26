package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import static org.junit.jupiter.api.Assertions.*;

class BadgePageControlsTest {
    @Test void pageAndBackCommandsStayAboveCanvasAndSwitchTheEditablePage()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var editor=new BadgeCardsPanel();
            try {
                JPanel pages=findPages(editor);assertNotNull(pages);
                var layout=(BorderLayout)pages.getParent().getLayout();
                assertSame(pages,layout.getLayoutComponent(BorderLayout.NORTH));
                assertNull(findButton(editor,"Add back from template…"));
                assertNull(findButton(editor,"Import back into template"));
                assertNull(findButton(editor,"Blank back"));
                var field=BadgeCardsPanel.class.getDeclaredField("template");field.setAccessible(true);
                var template=(BadgeTemplate)field.get(editor);
                assertFalse(template.editingBack);
                findButton(pages,"Edit back").doClick();assertTrue(template.editingBack);
                JComboBox<?> selector=null;for(Component c:pages.getComponents())if(c instanceof JComboBox<?> box)selector=box;
                assertNotNull(selector);assertEquals("Back",selector.getSelectedItem());
                selector.setSelectedItem("Front");assertFalse(template.editingBack);
                StudioRibbon ribbon=findRibbon(editor);assertNotNull(ribbon);
                var tabsField=StudioRibbon.class.getDeclaredField("tabs");tabsField.setAccessible(true);
                var tabs=(JTabbedPane)tabsField.get(ribbon);
                Container insert=(Container)tabs.getComponentAt(tabs.indexOfTab("Insert"));
                Container view=(Container)tabs.getComponentAt(tabs.indexOfTab("View"));
                Container home=(Container)tabs.getComponentAt(tabs.indexOfTab("Home"));
                Container data=(Container)tabs.getComponentAt(tabs.indexOfTab("Data"));
                assertNull(findButton(insert,"All insert tools…"));
                for(String command:new String[]{"Text box","Add text field…","Add photo field","Image","Add barcode…","Add QR code…","Rectangle","Ellipse","Triangle","Line"})
                    assertNotNull(findButton(insert,command),command);
                for(String command:new String[]{"Copy layers","Paste","Remove selected layers","Replace selected image / photo","Crop / frame selected photo…","Clear selected photo","Bring forward","Send backward"})
                    assertNotNull(findButton(home,command),command);
                assertNotNull(findButton(data,"Bind selected text…"));
                findButton(view,"Front").doClick();assertFalse(template.editingBack);
                findButton(view,"Back").doClick();assertTrue(template.editingBack);
                int frontLayers=template.elements.size();
                int backLayers=template.backElements.size();
                findButton(insert,"Rectangle").doClick();
                assertEquals(frontLayers,template.elements.size());
                assertEquals(backLayers+1,template.backElements.size());
                assertEquals("RECT",template.backElements.get(backLayers).type);
                // The page strip stays visible when the ribbon collapses at smaller sizes.
                editor.setSize(900,600);editor.doLayout();assertTrue(pages.isVisible());
            }catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}
            finally{try{var refresh=BadgeCardsPanel.class.getDeclaredField("refresh");refresh.setAccessible(true);((Timer)refresh.get(editor)).stop();}catch(Exception ex){throw new RuntimeException(ex);}}
        });
    }
    private static JPanel findPages(Container parent){for(Component c:parent.getComponents()){if(c instanceof JPanel p&&"Badge pages".equals(p.getAccessibleContext().getAccessibleName()))return p;if(c instanceof Container child){var found=findPages(child);if(found!=null)return found;}}return null;}
    private static JButton findButton(Container parent,String text){for(Component c:parent.getComponents()){if(c instanceof JButton b&&text.equals(b.getText()))return b;if(c instanceof Container child){var found=findButton(child,text);if(found!=null)return found;}}return null;}
    private static StudioRibbon findRibbon(Container parent){for(Component c:parent.getComponents()){if(c instanceof StudioRibbon ribbon)return ribbon;if(c instanceof Container child){var found=findRibbon(child);if(found!=null)return found;}}return null;}
}
