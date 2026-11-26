package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class PrintPreviewTest {
    @TempDir Path folder;
    static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    static <T>T find(Container parent,Class<T> type){for(Component child:parent.getComponents()){if(type.isInstance(child))return type.cast(child);if(child instanceof Container nested){T result=find(nested,type);if(result!=null)return result;}}return null;}
    static JButton button(Container parent,String text){for(Component child:parent.getComponents()){if(child instanceof JButton b&&text.equals(b.getText()))return b;if(child instanceof Container nested){var b=button(nested,text);if(b!=null)return b;}}return null;}
    @Test void ribbonPreviewIsVisibleOfflineRendersNavigatesAndNeverSaves()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(()->{
            JFrame owner=new JFrame();javax.swing.Timer[] poll={null};AtomicReference<Throwable> failure=new AtomicReference<>();AtomicInteger stage=new AtomicInteger(),requests=new AtomicInteger();
            try{
                var editor=new BusinessCardsPanel(true);owner.setContentPane(editor);owner.setSize(1100,700);
                ((javax.swing.Timer)field(editor,"refresh")).stop();((javax.swing.Timer)field(editor,"thumbnailRefresh")).stop();
                publishing.Publication document=(publishing.Publication)field(editor,"document");
                var artwork=new publishing.Publication.Element();artwork.type="RECT";artwork.fill="#7022A8";artwork.stroke="#7022A8";artwork.x=2;artwork.y=2;artwork.width=20;artwork.height=10;document.pages.get(0).objects.add(artwork);
                ((JCheckBox)field(editor,"marks")).setSelected(false);
                ((JSpinner)field(editor,"copies")).setValue(100);((javax.swing.Timer)field(editor,"refresh")).stop();
                var library=new SharedPublicationLibrary(folder,"LABELS",request->{requests.incrementAndGet();throw new AssertionError("Preview contacted the store");});
                set(editor,"shared",library);set(editor,"currentSharedFile",folder.resolve("synthetic-label.sspub"));set(editor,"dirty",true);
                StudioRibbon ribbon=find(editor,StudioRibbon.class);ribbon.select("Print Setup");JTabbedPane tabs=find(ribbon,JTabbedPane.class);
                long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
                poll[0]=new javax.swing.Timer(60,event->{
                    JDialog dialog=null;for(Window window:owner.getOwnedWindows())if(window instanceof JDialog d&&d.isShowing()&&d.getTitle().equals("Labels — sheet preview"))dialog=d;
                    try{
                        if(System.nanoTime()>deadline)throw new AssertionError("Preview did not finish rendering");
                        if(dialog==null)return;
                        assertNull(button(dialog,"Continue to printer…"));assertNotNull(button(dialog,"Close"));
                        SheetPreview preview=find(dialog,SheetPreview.class);if(field(preview,"image")==null)return;
                        BufferedImage rendered=(BufferedImage)field(preview,"image");int purple=0;
                        for(int y=0;y<rendered.getHeight();y+=4)for(int x=0;x<rendered.getWidth();x+=4){Color colour=new Color(rendered.getRGB(x,y));if(colour.getRed()<150&&colour.getGreen()<70&&colour.getBlue()>100)purple++;}
                        assertTrue(purple>100,"The preview must contain the synthetic label artwork");
                        Rectangle usable=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();assertTrue(usable.contains(dialog.getBounds()));
                        if(stage.getAndIncrement()==0){JSpinner sheet=find(dialog,JSpinner.class);assertTrue(((Number)((SpinnerNumberModel)sheet.getModel()).getMaximum()).intValue()>1);sheet.setValue(2);return;}
                        BufferedImage image=new BufferedImage(dialog.getContentPane().getWidth(),dialog.getContentPane().getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();dialog.getContentPane().printAll(g);g.dispose();javax.imageio.ImageIO.write(image,"png",Path.of("target","print-preview-fixed.png").toFile());
                        button(dialog,"Close").doClick();poll[0].stop();
                    }catch(Throwable ex){failure.set(ex);poll[0].stop();for(Window window:owner.getOwnedWindows())window.dispose();}
                });poll[0].start();button((Container)tabs.getSelectedComponent(),"Preview").doClick();
                if(failure.get()!=null)throw new AssertionError(failure.get());assertTrue(stage.get()>=2);assertEquals(0,requests.get());assertEquals(true,field(editor,"dirty"));assertEquals(100,editor.output().print.quantity);
            }catch(Exception ex){throw new RuntimeException(ex);}finally{if(poll[0]!=null)poll[0].stop();for(Window window:owner.getOwnedWindows())window.dispose();owner.dispose();}
        });
    }
}
