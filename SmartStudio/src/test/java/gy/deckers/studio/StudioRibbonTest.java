package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class StudioRibbonTest {
    @Test void ribbonActionsUseBundledIconsWithVisibleLabelsBelow()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            StudioRibbon ribbon=new StudioRibbon();
            for(String title:new String[]{"Add text","Add image","Forward","Backward","Align…","Distribute…","Barcode…","QR code…"}){
                JButton command=StudioRibbon.button(title,()->{});ribbon.add("Home","Insert",command);
                assertNotNull(command.getIcon(),title);
                assertEquals(SwingConstants.BOTTOM,command.getVerticalTextPosition());
                assertEquals(SwingConstants.CENTER,command.getHorizontalTextPosition());
                assertEquals(title,command.getText());assertTrue(command.getPreferredSize().height>=70);
                assertEquals(StudioVisuals.INK,command.getForeground());
            }
            assertEquals(new Color(0x4D235B),StudioVisuals.PLUM);
            assertEquals(new Color(0xFBF8F3),StudioVisuals.BACKGROUND);
        });
    }
    @Test void shortcutsUseExistingActionAndFollowItsAvailability() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            AtomicInteger calls=new AtomicInteger();
            JButton source=new JButton("Add text");source.addActionListener(e->calls.incrementAndGet());
            JButton home=StudioRibbon.shortcut(source,"Add text"),insert=StudioRibbon.shortcut(source,"Text box");
            home.doClick();insert.doClick();assertEquals(2,calls.get());
            source.setEnabled(false);assertFalse(home.isEnabled());assertFalse(insert.isEnabled());
            home.doClick();assertEquals(2,calls.get());
            source.setEnabled(true);insert.doClick();assertEquals(3,calls.get());
        });
    }

    @Test void tabsStayOrderedAndContextDoesNotStealFocus() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            StudioRibbon ribbon=new StudioRibbon();
            for(String tab:new String[]{"Home","Layout","Insert","Data","View","Picture format"})
                ribbon.add(tab,"Tools",StudioRibbon.button(tab,()->{}));
            JTabbedPane tabs=find(ribbon,JTabbedPane.class);
            assertEquals("Home",tabs.getTitleAt(0));assertEquals("Insert",tabs.getTitleAt(1));
            ribbon.available("Picture format",false);ribbon.select("Insert");
            ribbon.available("Picture format",true);assertEquals("Insert",tabs.getTitleAt(tabs.getSelectedIndex()));
            ribbon.select("Picture format");ribbon.available("Picture format",false);
            assertEquals("Home",tabs.getTitleAt(tabs.getSelectedIndex()));
            ribbon.setCollapsed(true);assertTrue(ribbon.isCollapsed());
            ribbon.setCollapsed(false);assertFalse(ribbon.isCollapsed());
        });
    }

    @Test void sixDisplayBudgetsKeepRibbonGroupsInsideTheirPage() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            for(int[] screen:new int[][]{{1366,768},{1920,1080}})for(double scale:new double[]{1,1.25,1.5}){
                StudioRibbon ribbon=new StudioRibbon();
                for(String group:new String[]{"Template","History","Insert","Arrange","Text"})
                    for(int i=0;i<3;i++)ribbon.add("Home",group,StudioRibbon.button(group+" command "+i,()->{}));
                ribbon.setSize((int)(screen[0]/scale)-32,164);layout(ribbon);
                JTabbedPane tabs=find(ribbon,JTabbedPane.class);Container page=(Container)tabs.getSelectedComponent();
                for(Component group:page.getComponents()){
                    assertTrue(group.getX()+group.getWidth()<=page.getWidth(),screen[0]+" / "+scale);
                    assertTrue(group.getWidth()>=100,"Group opener must remain readable");
                }
                assertTrue(ribbon.getHeight()<((screen[1]-64)/scale)/2);
            }
        });
    }

    @Test void narrowHeaderKeepsOutputActionsClearOfTabs() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var ribbon=new StudioRibbon();
            for(String tab:new String[]{"Home","Insert","Layout","Print Setup","Data","View","Cut Setup","Riso","Numbering","Text format"})
                ribbon.add(tab,"Tools",StudioRibbon.button(tab,()->{}));
            JPanel output=new JPanel();
            for(String title:new String[]{"Save","Print","Export PDF"}){
                JButton button=StudioRibbon.button(title,()->{});
                StudioVisuals.headerButton(button);output.add(button);
            }
            ribbon.outputActions(output);
            ribbon.setCollapsed(true);
            ribbon.setSize(870,100);layout(ribbon);
            JTabbedPane tabs=find(ribbon,JTabbedPane.class);
            assertTrue(tabs.getY()>=output.getParent().getHeight());
            assertEquals("Home",tabs.getTitleAt(0));
            assertEquals("View",tabs.getTitleAt(5));
        });
    }

    @Test void badgeFormatTabsReorderSelectedLayersAndLayoutCanCenterThem(@TempDir Path folder) throws Exception {
        var sample=new BadgeTemplate();
        for(String type:new String[]{"TEXT","PHOTO","RECT"}){
            var layer=new BadgeTemplate.Element();layer.type=type;layer.label=type;
            layer.x=5;layer.y=8;layer.width=20;layer.height=8;sample.elements.add(layer);
        }
        Path file=folder.resolve("arrange.sstbadge");sample.save(file);
        SwingUtilities.invokeAndWait(()->{
            try{
                var panel=new BadgeCardsPanel();panel.openDocument(file);
                var shell=find(panel,StudioWorkspaceShell.class);var tabs=find(shell.ribbon(),JTabbedPane.class);
                var tableField=BadgeCardsPanel.class.getDeclaredField("table");tableField.setAccessible(true);
                var table=(JTable)tableField.get(panel);
                var designField=BadgeCardsPanel.class.getDeclaredField("template");designField.setAccessible(true);
                var design=(BadgeTemplate)designField.get(panel);
                table.setRowSelectionInterval(0,0);shell.ribbon().select("Text format");
                int i=0;
                for(String label:new String[]{"Bring forward","Bring to front","Send backward","Send to back"}){
                    button(button((Container)tabs.getSelectedComponent(),"Layer order…").getComponentPopupMenu(),label).doClick();
                    assertEquals(new int[]{1,2,1,0}[i++],table.getSelectedRow());
                    assertEquals("Text format",tabs.getTitleAt(tabs.getSelectedIndex()));
                    assertEquals("TEXT",design.elements.get(table.getSelectedRow()).type);
                }
                table.setRowSelectionInterval(1,1);shell.ribbon().select("Picture format");
                var pictureMenu=button((Container)tabs.getSelectedComponent(),"Layer order…").getComponentPopupMenu();
                assertEquals(4,pictureMenu.getComponentCount());
                shell.ribbon().select("Layout");
                assertNotNull(button((Container)tabs.getSelectedComponent(),"Align…"));
                panel.arrangeLayers(StudioArrange.Operation.TEMPLATE_HORIZONTAL);
                var photo=design.elements.get(1);
                assertEquals(design.widthMm/2,photo.x+photo.width/2,.0001);
                assertEquals(8,photo.y);
                shell.ribbon().select("Data");
                assertNotNull(button((Container)tabs.getSelectedComponent(),"DB").getIcon());
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
    }

    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container n)layout(n);}
    private static AbstractButton button(Container c,String label){for(Component child:c.getComponents()){if(child instanceof AbstractButton b&&label.equals(b.getText()))return b;if(child instanceof Container n){AbstractButton result=button(n,label);if(result!=null)return result;}}return null;}
    private static <T>T find(Container c,Class<T> type){for(Component child:c.getComponents()){if(type.isInstance(child))return type.cast(child);if(child instanceof Container n){T result=find(n,type);if(result!=null)return result;}}return null;}
}
