package ui.screens;

import com.google.gson.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Optional off-screen desktop smoke check. Uses a mock transport, never a store connection. */
public final class OverseasOrdersPreview {
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Provide a PNG output path.");
        CountDownLatch done=new CountDownLatch(1);java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{
            try {
                JsonObject sample=JsonParser.parseString("""
                  {"permissions":["OVERSEAS_ORDERS_VIEW","OVERSEAS_ORDERS_QUOTE","OVERSEAS_ORDERS_PAYMENT","OVERSEAS_ORDERS_SHIP","OVERSEAS_ORDERS_COLLECT","OVERSEAS_ORDERS_ADJUST"],
                   "order":{"overseas_order_id":"00000000-0000-0000-0000-000000000001","custom_order_id":1,"customer_id":1,"customer_name":"Sample customer","customer_phone":"+5926145030","expected_date":"2026-11-01","notes":"Blue shoes, size 10. Customer requested through WhatsApp.","shipping":2500.00,"total":14500.00,"paid":5000.00,"balance":9500.00,"state":"ISSUED","number":"OS-1-101","revision":3,
                   "lines":[{"line_id":"00000000-0000-0000-0000-000000000002","description":"Running shoes","product_url":"https://example.com/shoes","options":"Blue / size 10","quantity":2,"unit_price":6000.00,"usd_cost":20.00,"exchange_rate":210.00,"screenshot":"","purchased":2,"shipped":2,"arrived":1,"collected":0}]},"payments":[],"events":[]}
                  """).getAsJsonObject();
                OverseasOrders frame=new OverseasOrders(b->{if(b.get("action").getAsString().equals("LIST")){JsonObject list=new JsonObject();list.add("permissions",sample.get("permissions"));list.add("rows",JsonParser.parseString("[{\"id\":\"00000000-0000-0000-0000-000000000001\",\"number\":\"OS-1-101\",\"customerName\":\"Sample customer\",\"customerPhone\":\"+5926145030\",\"state\":\"ISSUED\",\"total\":14500,\"paid\":5000,\"balance\":9500}]"));return list;}return sample.deepCopy();});
                frame.addNotify();frame.validate();
                Timer select=new Timer(700,e->{JTable table=findTable(frame.getContentPane());if(table!=null&&table.getRowCount()>0)table.setRowSelectionInterval(0,0);});select.setRepeats(false);select.start();
                Timer capture=new Timer(1500,e->{try{frame.validate();Container content=frame.getContentPane();BufferedImage image=new BufferedImage(content.getWidth(),content.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();content.printAll(graphics);graphics.dispose();javax.imageio.ImageIO.write(image,"png",Path.of(args[0]).toFile());}catch(Throwable t){failure.set(t);}finally{frame.dispose();done.countDown();}});capture.setRepeats(false);capture.start();
            }catch(Throwable t){failure.set(t);done.countDown();}
        });
        if(!done.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Desktop preview timed out.");
        if(failure.get()!=null)throw new RuntimeException(failure.get());
    }
    private static JTable findTable(Container container){for(Component c:container.getComponents()){if(c instanceof JTable t)return t;if(c instanceof Container nested){JTable t=findTable(nested);if(t!=null)return t;}}return null;}
}
