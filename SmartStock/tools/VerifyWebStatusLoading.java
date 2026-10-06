package ui.screens;

import com.google.gson.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Native Swing regression check. Run with compiled application dependencies. No live API calls. */
class VerifyWebStatusLoading {
    public static void main(String[] args)throws Exception{
        check(false);check(true);System.out.println("Web Status delayed-open success and error checks passed.");
    }
    static void check(boolean fail)throws Exception{
        AtomicInteger calls=new AtomicInteger();AtomicReference<WebStatusDialog> window=new AtomicReference<>();
        CountDownLatch requested=new CountDownLatch(1);
        SwingUtilities.invokeAndWait(()->window.set(new WebStatusDialog(null,null,()->{
            calls.incrementAndGet();requested.countDown();if(fail)throw new IllegalStateException("Test connection unavailable");
            JsonObject data=new JsonObject(),usage=new JsonObject();
            usage.addProperty("uptimeMs",123000);usage.addProperty("heapUsed",1000);usage.addProperty("heapMax",2000);
            usage.addProperty("cpuPercent",1);usage.addProperty("threads",4);data.add("usage",usage);
            data.add("services",new JsonArray());data.addProperty("canControl",true);data.addProperty("role","PRIMARY");data.addProperty("checkedAt",System.currentTimeMillis());return data;
        })));
        try{
            // Reproduce a caller creating the dialog well before it is opened.
            Thread.sleep(250);if(calls.get()!=0)throw new AssertionError("Requested data before the window opened");
            SwingUtilities.invokeAndWait(()->{window.get().addNotify();window.get().dispatchEvent(new WindowEvent(window.get(),WindowEvent.WINDOW_OPENED));});
            if(!requested.await(5,TimeUnit.SECONDS))throw new AssertionError("Initial load did not start");
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);AtomicBoolean ready=new AtomicBoolean();
            while(!ready.get()&&System.nanoTime()<deadline){
                SwingUtilities.invokeAndWait(()->ready.set(contains(window.get().getContentPane(),fail?"Status unavailable":"Updated ")));
                if(!ready.get())Thread.sleep(25);
            }
            if(!ready.get())throw new AssertionError("Response was discarded; screen stayed loading");
            if(calls.get()!=1)throw new AssertionError("Unexpected duplicate initial request");
        }finally{SwingUtilities.invokeAndWait(()->window.get().dispose());}
    }
    static boolean contains(Container parent,String text){for(Component c:parent.getComponents()){
        if(c instanceof JLabel label&&label.getText().startsWith(text))return true;
        if(c instanceof Container next&&contains(next,text))return true;
    }return false;}
}
