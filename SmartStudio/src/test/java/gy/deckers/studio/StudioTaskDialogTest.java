package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class StudioTaskDialogTest {
    @Test void recordsTableFillsDialogAndKeepsItsOwnScrolling()throws Exception{
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(()->{
            JFrame owner=new JFrame();BusinessCardsPanel editor=new BusinessCardsPanel();
            AtomicReference<Throwable> failure=new AtomicReference<>();
            Timer close=new Timer(300,e->{
                for(Window window:Window.getWindows())if(window instanceof JDialog dialog&&dialog.getTitle().equals("Edit records proof")&&dialog.isShowing()){
                    try{
                        var field=BusinessCardsPanel.class.getDeclaredField("table");field.setAccessible(true);
                        JTable table=(JTable)field.get(editor);
                        JScrollPane scroll=(JScrollPane)table.getParent().getParent();
                        assertTrue(scroll.getViewport().getHeight()>dialog.getContentPane().getHeight()*.65,"Records must use the dialog body");
                        assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED,scroll.getHorizontalScrollBarPolicy());
                        assertTrue(find(dialog.getContentPane(),"Close").isShowing());
                        var panel=dialog.getContentPane();BufferedImage image=new BufferedImage(panel.getWidth(),panel.getHeight(),BufferedImage.TYPE_INT_RGB);
                        var graphics=image.createGraphics();panel.printAll(graphics);graphics.dispose();
                        javax.imageio.ImageIO.write(image,"png",Path.of("target","records-dialog-fill.png").toFile());
                    }catch(Throwable ex){failure.set(ex);}finally{dialog.dispose();}
                }
            });
            close.setRepeats(false);close.start();
            try{
                var field=BusinessCardsPanel.class.getDeclaredField("table");field.setAccessible(true);
                JTable table=(JTable)field.get(editor);JComponent content=(JComponent)table.getParent().getParent().getParent();
                Container previous=content.getParent();
                StudioRibbon.showTask(owner,"Edit records proof",content);
                assertNull(failure.get(),()->String.valueOf(failure.get()));assertSame(previous,content.getParent());
            }catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}
            finally{close.stop();owner.dispose();editor.resetFields();}
        });
    }
    @Test void modalSettingsRemainOnScreenEscapeClosesAndEditorReturnsToOwner() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(()->{
            JFrame owner=new JFrame();JPanel holder=new JPanel();JPanel content=new JPanel(new FlowLayout());
            JButton trigger=StudioRibbon.button("Settings",()->{});
            for(int i=0;i<8;i++)content.add(StudioRibbon.button("Setting "+i,()->{}));
            holder.add(trigger);holder.add(content);owner.setContentPane(holder);
            AtomicReference<Throwable> failure=new AtomicReference<>();
            Timer close=new Timer(300,e->{
                for(Window window:Window.getWindows())if(window instanceof JDialog dialog&&dialog.getTitle().equals("Ribbon dialog proof")&&dialog.isShowing()){
                    try{
                        Rectangle bounds=dialog.getGraphicsConfiguration().getBounds();
                        assertTrue(bounds.contains(dialog.getBounds()));
                        assertTrue(SwingUtilities.isDescendingFrom(content,dialog));
                        JButton button=find(dialog.getContentPane(),"Close");assertNotNull(button);
                        assertTrue(button.getHeight()>=44);
                        assertTrue(button.getY()+button.getHeight()<=button.getParent().getHeight());
                        JComponent panel=(JComponent)dialog.getContentPane();
                        BufferedImage image=new BufferedImage(panel.getWidth(),panel.getHeight(),BufferedImage.TYPE_INT_RGB);
                        Graphics2D graphics=image.createGraphics();panel.printAll(graphics);graphics.dispose();
                        javax.imageio.ImageIO.write(image,"png",Path.of("target","ribbon-settings-dialog.png").toFile());
                        var escape=dialog.getRootPane().getActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0));
                        assertNotNull(escape);escape.actionPerformed(new ActionEvent(dialog,0,"escape"));
                    }catch(Throwable ex){failure.set(ex);}finally{dialog.dispose();}
                }
            });
            close.setRepeats(false);close.start();
            try{StudioRibbon.showTask(trigger,"Ribbon dialog proof",content);assertNull(failure.get(),()->String.valueOf(failure.get()));assertSame(holder,content.getParent());}
            finally{close.stop();owner.dispose();}
        });
    }
    private static JButton find(Container c,String title){for(Component child:c.getComponents()){if(child instanceof JButton b&&title.equals(b.getText()))return b;if(child instanceof Container n){JButton result=find(n,title);if(result!=null)return result;}}return null;}
}
