package ui.screens;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="java.awt.headless",matches="false")
class WebStatusDialogTest {
    @Test void fastFirstResponseRendersWithoutWaitingForWindowOpened() throws Exception {
        AtomicReference<WebStatusDialog> window=new AtomicReference<>();
        AtomicInteger calls=new AtomicInteger();
        try {
            SwingUtilities.invokeAndWait(()->{
                var dialog=new WebStatusDialog(null,null,()->{calls.incrementAndGet();return snapshot();});
                // Explicit showing must load even when window listeners have been removed.
                for(var listener:dialog.getWindowListeners())dialog.removeWindowListener(listener);
                dialog.setLocation(-2000,-2000);window.set(dialog);dialog.setVisible(true);
            });
            awaitText(window.get(),"Server uptime");
            assertEquals(1,calls.get());
            SwingUtilities.invokeAndWait(()->{
                window.get().setVisible(true);
                assertEquals(1,calls.get());
                var image=new java.awt.image.BufferedImage(1080,790,java.awt.image.BufferedImage.TYPE_INT_RGB);
                var graphics=image.createGraphics();window.get().paint(graphics);graphics.dispose();
                try {javax.imageio.ImageIO.write(image,"png",new java.io.File("target/web-status-loaded.png"));}catch(Exception e){throw new RuntimeException(e);}
            });
        } finally {if(window.get()!=null)SwingUtilities.invokeAndWait(()->window.get().dispose());}
    }
    @Test void failedFirstResponseShowsErrorAndRefreshRecovers() throws Exception {
        AtomicReference<WebStatusDialog> window=new AtomicReference<>();AtomicInteger calls=new AtomicInteger();
        try {
            SwingUtilities.invokeAndWait(()->{
                var dialog=new WebStatusDialog(null,null,()->{if(calls.getAndIncrement()==0)throw new java.io.IOException("Server unreachable");return snapshot();});
                dialog.setLocation(-2000,-2000);window.set(dialog);dialog.setVisible(true);
            });
            awaitText(window.get(),"Could not load store status");
            SwingUtilities.invokeAndWait(()->findRefresh(window.get()).doClick());
            awaitText(window.get(),"Server uptime");
            assertEquals(2,calls.get());
        } finally {if(window.get()!=null)SwingUtilities.invokeAndWait(()->window.get().dispose());}
    }
    static JButton findRefresh(Container c){for(Component child:c.getComponents()){if(child instanceof JButton b && b.getText().equals("Refresh"))return b;if(child instanceof Container nested){JButton found=findRefresh(nested);if(found!=null)return found;}}return null;}
    static boolean contains(Container c,String text){for(Component child:c.getComponents()){if(child instanceof JLabel label&&label.getText().contains(text))return true;if(child instanceof Container nested&&contains(nested,text))return true;}return false;}
    static void awaitText(Container c,String text)throws Exception {long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);AtomicBoolean found=new AtomicBoolean();while(System.nanoTime()<deadline){SwingUtilities.invokeAndWait(()->found.set(contains(c,text)));if(found.get())return;Thread.sleep(25);}fail("Missing visible status: "+text);}
    static JsonObject snapshot(){return JsonParser.parseString("""
        {"usage":{"uptimeMs":123000,"heapUsed":1048576,"heapMax":2097152,"cpuPercent":2.5,"threads":20},
        "canControl":false,"checkedAt":1791320000000,"role":"PRIMARY","services":[
        {"id":"lan","name":"Register & SmartStudio API","description":"Authenticated HTTPS store API","running":true,"healthy":true,"url":"HTTPS 8443","controllable":false,"note":"Monitoring only","metrics":{"available":false}}]}
        """).getAsJsonObject();}
}
