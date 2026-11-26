package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import publishing.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class StudioLayersPanelTest {
    @Test void renderReferenceLayersWithSoftSelectionAndOutlineTypes()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            try{
                java.util.List<Publication.Element> elements=new ArrayList<>();
                String[] names={"Background","Logo","Contact Details","Position","Name"};
                for(int i=0;i<names.length;i++){var e=new Publication.Element();e.type=i==0?"RECT":i==1?"IMAGE":"TEXT";e.text=names[i];elements.add(e);}
                var panel=new StudioLayersPanel(()->elements,()->4,i->{},i->{},move->{},()->{},()->{});
                panel.setSize(280,370);layout(panel);
                assertEquals(0,panel.table.getSelectedRow());
                JScrollPane scroll=(JScrollPane)panel.table.getParent().getParent();assertFalse(scroll.getVerticalScrollBar().isVisible());
                BufferedImage image=new BufferedImage(280,370,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();panel.printAll(g);g.dispose();
                javax.imageio.ImageIO.write(image,"png",Path.of("target","layers-reference-preview.png").toFile());
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
    }

    @Test void floatingWindowKeepsCanvasEditableAndWiresOrderDeleteAndVisibility()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(()->{
            JFrame owner=new JFrame();JDialog window=null;
            try{
                var editor=new BusinessCardsPanel();owner.setContentPane(editor);owner.setSize(1100,700);
                var field=BusinessCardsPanel.class.getDeclaredField("document");field.setAccessible(true);Publication document=(Publication)field.get(editor);document.pages.get(0).objects.clear();
                for(int i=0;i<3;i++){var element=new Publication.Element();element.text="Item "+i;document.pages.get(0).objects.add(element);}
                var open=BusinessCardsPanel.class.getDeclaredMethod("showLayers");open.setAccessible(true);open.invoke(editor);
                field=BusinessCardsPanel.class.getDeclaredField("layersWindow");field.setAccessible(true);window=(JDialog)field.get(editor);assertFalse(window.isModal());assertTrue(window.isShowing());
                StudioLayersPanel panel=(StudioLayersPanel)window.getContentPane();panel.table.setRowSelectionInterval(1,1);
                command(panel,"Move layer up").doClick();assertEquals("Item 1",editor.output().pages.get(0).objects.get(2).text);
                panel.table.getActionMap().get("toggleLayer").actionPerformed(new ActionEvent(panel,0,"space"));assertTrue(editor.output().pages.get(0).objects.get(2).hidden);
                command(panel,"Delete layer").doClick();assertEquals(2,editor.output().pages.get(0).objects.size());
                command(panel,"Close layers").doClick();assertFalse(window.isDisplayable());
            }catch(Exception ex){throw new RuntimeException(ex);}finally{if(window!=null)window.dispose();owner.dispose();}
        });
    }
    @Test void scrollableTopmostListSelectsTogglesAndPreservesVisibilityInTemplate()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            try{
                var editor=new BusinessCardsPanel();var field=BusinessCardsPanel.class.getDeclaredField("document");field.setAccessible(true);Publication document=(Publication)field.get(editor);document.pages.get(0).objects.clear();
                for(int i=0;i<100;i++){var element=new Publication.Element();element.text="Layer "+i;document.pages.get(0).objects.add(element);}
                AtomicInteger selected=new AtomicInteger(-1),closed=new AtomicInteger();
                var panel=new StudioLayersPanel(()->document.pages.get(0).objects,selected::get,selected::set,editor::toggleLayerVisibility,move->{},()->{},closed::incrementAndGet);
                assertEquals("Layer 99",panel.table.getValueAt(0,2));panel.table.setRowSelectionInterval(0,0);assertEquals(99,selected.get());
                panel.table.getActionMap().get("toggleLayer").actionPerformed(new ActionEvent(panel,0,"space"));assertTrue(document.pages.get(0).objects.get(99).hidden);
                assertTrue(PublicationFiles.read(PublicationFiles.archive(editor.output())).pages.get(0).objects.get(99).hidden);
                editor.toggleLayerVisibility(99);assertFalse(document.pages.get(0).objects.get(99).hidden);
                panel.setSize(300,420);layout(panel);JScrollPane scroll=(JScrollPane)panel.table.getParent().getParent();assertTrue(scroll.getVerticalScrollBar().isVisible());
                assertTrue(panel.table.getRowHeight()>=40);assertEquals(100,panel.table.getRowCount());
                var image=new BufferedImage(300,420,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();panel.printAll(graphics);graphics.dispose();javax.imageio.ImageIO.write(image,"png",Path.of("target","layers-panel.png").toFile());
                editor.historyAction(false);assertTrue(editor.output().pages.get(0).objects.get(99).hidden,"Undo restores hidden state");
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
    @Test void hiddenObjectsDoNotInterceptCanvasClicks()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            var workspace=new TemplateWorkspace();workspace.setSize(424,424);AtomicInteger selected=new AtomicInteger(-1);
            workspace.configure(new TemplateWorkspace.Design(){
                public double width(){return 100;}public double height(){return 100;}public int size(){return 2;}
                public java.awt.geom.Rectangle2D bounds(int i){return new java.awt.geom.Rectangle2D.Double(10,10,20,20);}public boolean hidden(int i){return i==1;}
                public void move(int i,double x,double y){}public void paint(java.awt.Graphics2D graphics,double scale){}
            },selected::set,()->{});
            var image=new BufferedImage(424,424,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();workspace.paint(graphics);graphics.dispose();
            workspace.dispatchEvent(new MouseEvent(workspace,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),0,152,152,1,false,MouseEvent.BUTTON1));
            assertEquals(0,selected.get());
        });
    }
    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container n)layout(n);}
    private static JButton command(Container parent,String name){for(Component child:parent.getComponents()){if(child instanceof JButton button&&name.equals(button.getAccessibleContext().getAccessibleName()))return button;if(child instanceof Container nested){var result=command(nested,name);if(result!=null)return result;}}return null;}
}
