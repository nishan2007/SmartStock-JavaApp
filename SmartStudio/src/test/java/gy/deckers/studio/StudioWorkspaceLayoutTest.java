package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class StudioWorkspaceLayoutTest {
    @TempDir Path library;
    @Test void inspectorCanResizeAndKeepsWidthAfterHiding()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            var shell=new StudioWorkspaceShell(new JPanel());shell.addSection("Layers",new JLabel("Layers"));shell.setSize(1100,600);layout(shell);click(shell,"Layers");layout(shell);
            JSplitPane split=find(shell,JSplitPane.class);assertNotNull(split);assertTrue(split.isContinuousLayout());int original=split.getRightComponent().getWidth();split.setDividerLocation(split.getDividerLocation()-100);layout(shell);int wider=split.getRightComponent().getWidth();assertTrue(wider>original+90);
            click(shell,"Hide panel");layout(shell);assertFalse(split.getRightComponent().isVisible());click(shell,"Layers");layout(shell);assertEquals(wider,split.getRightComponent().getWidth(),2);
        });
    }
    @Test void labelToolbarKeepsZoomVisibleAtMinimumWidth()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            BusinessCardsPanel panel=new BusinessCardsPanel(true);panel.setSize(780,580);layout(panel);
            JButton zoom=findButton(panel,"+");assertNotNull(zoom);Container toolbar=zoom.getParent();
            assertTrue(zoom.getX()+zoom.getWidth()<=toolbar.getWidth());assertTrue(zoom.getY()+zoom.getHeight()<=toolbar.getHeight());
            try{var field=BusinessCardsPanel.class.getDeclaredField("refresh");field.setAccessible(true);((Timer)field.get(panel)).stop();}catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
    private static JButton findButton(Container parent,String text){for(Component child:parent.getComponents()){if(child instanceof JButton b&&b.getText().equals(text))return b;if(child instanceof Container nested){JButton result=findButton(nested,text);if(result!=null)return result;}}return null;}

    @Test void closedInspectorLeavesCanvasDominantAndCanBeReopened() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            JPanel canvas=new JPanel();StudioWorkspaceShell shell=new StudioWorkspaceShell(canvas);
            shell.addSection("Properties",new JLabel("Object properties"));
            for(int width:new int[]{780,1060}){
                shell.setSize(width,500);layout(shell);
                assertTrue(canvas.getWidth()>width*.8);
                click(shell,"Properties");layout(shell);
                assertTrue(canvas.getWidth()>width*.4);
                click(shell,"Hide panel");layout(shell);
                assertTrue(canvas.getWidth()>width*.8);
            }
        });
    }

    @Test void inspectorEditsModelAndRejectsStaleFieldsAfterSelectionChange() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            JTable table=new JTable(new javax.swing.table.DefaultTableModel(new Object[][]{{"Alice"},{"Bob"}},new String[]{"Name"}));
            JComponent properties=StudioWorkspaceShell.properties(table);
            table.setRowSelectionInterval(0,0);JTextField first=find(properties,JTextField.class);
            first.setText("Updated");first.postActionEvent();assertEquals("Updated",table.getValueAt(0,0));
            table.setRowSelectionInterval(1,1);
            // The previously selected object's editor must not apply a delayed focus commit.
            first.setText("Stale");first.postActionEvent();assertEquals("Updated",table.getValueAt(0,0));
        });
    }

    @Test void renderBadgeCanvasAndInspectorAtDesktopSizes() throws Exception {
        String previous=System.getProperty("smartstudio.badge.folder");System.setProperty("smartstudio.badge.folder",library.toString());
        try{SwingUtilities.invokeAndWait(()->{
            BadgeCardsPanel panel=new BadgeCardsPanel();
            try{
                for(int width:new int[]{820,1100}){
                    panel.setSize(width,640);layout(panel);
                    TemplateWorkspace canvas=find(panel,TemplateWorkspace.class);
                    assertNotNull(canvas);assertTrue(canvas.getWidth()>width*.7);
                    render(panel,"badge-canvas-"+width);
                    click(panel,"Properties");layout(panel);render(panel,"badge-properties-"+width);
                    click(panel,"Hide panel");
                }
            }catch(Exception ex){throw new RuntimeException(ex);}
            finally{try{var field=BadgeCardsPanel.class.getDeclaredField("refresh");field.setAccessible(true);((Timer)field.get(panel)).stop();}catch(Exception ex){throw new RuntimeException(ex);}}
        });}finally{if(previous==null)System.clearProperty("smartstudio.badge.folder");else System.setProperty("smartstudio.badge.folder",previous);}
    }
    private static void render(JComponent panel,String name)throws Exception{
        BufferedImage image=new BufferedImage(panel.getWidth(),panel.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();panel.printAll(g);g.dispose();javax.imageio.ImageIO.write(image,"png",Path.of("target",name+".png").toFile());
    }
    private static void layout(Container parent){parent.doLayout();for(Component child:parent.getComponents())if(child instanceof Container nested)layout(nested);}
    private static void click(Container parent,String name){for(Component child:parent.getComponents()){if(child instanceof JButton b&&b.getText().equals(name)){b.doClick();return;}if(child instanceof Container nested)click(nested,name);}}
    private static <T> T find(Container parent,Class<T> type){for(Component child:parent.getComponents()){if(type.isInstance(child))return type.cast(child);if(child instanceof Container nested){T result=find(nested,type);if(result!=null)return result;}}return null;}
}
