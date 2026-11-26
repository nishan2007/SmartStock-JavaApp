package ui.screens;

import com.google.gson.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.*;

/** Deterministic native Swing previews. Mock transport only; never accesses a store. */
public final class OverseasOrdersPreview {
    static JsonObject sample() {
        return JsonParser.parseString("""
            {"permissions":["OVERSEAS_ORDERS_VIEW","OVERSEAS_ORDERS_QUOTE","OVERSEAS_ORDERS_PAYMENT","OVERSEAS_ORDERS_SHIP","OVERSEAS_ORDERS_COLLECT","OVERSEAS_ORDERS_ADJUST"],
             "order":{"overseas_order_id":"00000000-0000-0000-0000-000000000001","custom_order_id":241,"customer_id":1,"customer_name":"SAYRESH","customer_phone":"+592 600 1234","store_name":"Skeldon","expected_date":"","notes":"","shipping":0.00,"total":40000.00,"paid":20000.00,"balance":20000.00,"state":"ISSUED","number":"OS-1-241","revision":3,
             "lines":[
               {"line_id":"00000000-0000-0000-0000-000000000002","description":"Indian wear sari","seller":"Amazon","product_url":"https://www.amazon.com/","options":"Red","quantity":1,"unit_price":11500.00,"usd_cost":null,"exchange_rate":null,"screenshot":"","purchased":0,"shipped":0,"arrived":0,"collected":0,"tracking":"PROCESSING"},
               {"line_id":"00000000-0000-0000-0000-000000000003","description":"Indian wear sari","seller":"Amazon","product_url":"https://www.amazon.com/","options":"Pink","quantity":1,"unit_price":11500.00,"usd_cost":null,"exchange_rate":null,"screenshot":"","purchased":0,"shipped":0,"arrived":0,"collected":0,"tracking":"PROCESSING"},
               {"line_id":"00000000-0000-0000-0000-000000000004","description":"Indian wear","seller":"Amazon","product_url":"https://www.amazon.com/","options":"Black","quantity":1,"unit_price":17000.00,"usd_cost":null,"exchange_rate":null,"screenshot":"","purchased":0,"shipped":0,"arrived":0,"collected":0,"tracking":"PROCESSING"}
             ]},"payments":[{"created_at":"2026-11-16 11:30","payment_action":"PAYMENT","payment_amount":20000,"payment_method":"CASH","taken_by_name":"Nishan"}],"events":[]}
            """).getAsJsonObject();
    }
    static JsonObject listing() {
        JsonObject out = new JsonObject(); out.add("permissions", sample().get("permissions"));
        JsonArray rows = new JsonArray(); String[] names={"SAYRESH","RAMESH","KAVITA","ANITA","DEV","PRIYA"};
        String[] tracking={"PROCESSING","WAITING_FOR_PAYMENT","ORDERED","ARRIVED","DRAFT","PROCESSING"};
        int[] totals={40000,28000,51000,18500,12000,32000},paid={20000,0,51000,10000,0,16000};
        for(int i=0;i<names.length;i++) { JsonObject r=new JsonObject(); r.addProperty("id", "00000000-0000-0000-0000-00000000000"+(i+1)); r.addProperty("number","OS-1-"+(241+i)); r.addProperty("customerName",names[i]);r.addProperty("tracking",tracking[i]);r.addProperty("total",totals[i]);r.addProperty("paid",paid[i]);r.addProperty("balance",totals[i]-paid[i]);rows.add(r); }
        out.add("rows",rows); return out;
    }
    static Object member(Object instance,String name)throws Exception {var f=instance.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(instance);}
    static void invoke(Object instance,String method,Class<?>[] types,Object...args)throws Exception {var m=instance.getClass().getDeclaredMethod(method,types);m.setAccessible(true);m.invoke(instance,args);}
    static JButton findButton(Container c,String text) {for(Component child:c.getComponents()){if(child instanceof JButton b&&b.getText().equals(text))return b;if(child instanceof Container nested){JButton b=findButton(nested,text);if(b!=null)return b;}}return null;}
    public static void main(String[] args)throws Exception {
        if(args.length<1)throw new IllegalArgumentException("Provide an output directory.");
        Path output=Path.of(args[0]);Files.createDirectories(output);
        managers.SessionManager.setCurrentLocationName("Skeldon");
        CountDownLatch done=new CountDownLatch(1); java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                OverseasOrders frame=new OverseasOrders(b->switch(b.get("action").getAsString()){case "LIST"->listing();case "CUSTOMER_PHONE"->JsonParser.parseString("{\"customers\":[],\"reserved\":false}").getAsJsonObject();case "SELLERS"->JsonParser.parseString("{\"sellers\":[\"Amazon\",\"Shein\"]}").getAsJsonObject();default->sample();});
                ui.helpers.ThemeManager.applyToWindow(frame);
                frame.getContentPane().setPreferredSize(new Dimension(args.length>1?Integer.parseInt(args[1]):1488,(args.length>2?Integer.parseInt(args[2]):1058)-44));frame.pack();frame.validate();
                javax.swing.Timer timer=new javax.swing.Timer(500,null);
                int[] phase={0};
                timer.addActionListener(e->{try {
                    if((boolean)member(frame,"working"))return;
                    if(phase[0]==0) { ((JTable)member(frame,"orders")).setRowSelectionInterval(0,0);capture(frame,output.resolve("list.png"));invoke(frame,"showDetail",new Class<?>[]{JsonObject.class},sample()); }
                    else if(phase[0]==1) {capture(frame,output.resolve("detail.png"));JsonObject draft=sample();draft.getAsJsonObject("order").addProperty("state","DRAFT");draft.getAsJsonObject("order").add("custom_order_id",JsonNull.INSTANCE);invoke(frame,"showDetail",new Class<?>[]{JsonObject.class},draft);}
                    else if(phase[0]==2) {capture(frame,output.resolve("quote.png"));invoke(frame,"editLine",new Class<?>[]{int.class},-1);@SuppressWarnings("unchecked") var fields=(java.util.Map<String,JTextField>)member(frame,"itemFields");fields.get("description").setText("Indian wear sari");fields.get("seller").setText("Amazon");fields.get("options").setText("Red · Size M");fields.get("product_url").setText("https://www.amazon.com/...");fields.get("unit_price").setText("11,500.00");}
                    else if(phase[0]==3) {capture(frame,output.resolve("item.png"));timer.stop();frame.dispose();done.countDown();}
                    phase[0]++;
                }catch(Throwable t){failure.set(t);timer.stop();frame.dispose();done.countDown();}});timer.start();
            } catch(Throwable t){failure.set(t);done.countDown();}
        });
        if(!done.await(30,TimeUnit.SECONDS))throw new IllegalStateException("Preview timed out");
        if(failure.get()!=null)throw new RuntimeException(failure.get());
        System.out.println("Captured four native overseas screens in "+output.toAbsolutePath());
    }
    private static void capture(JFrame frame,Path path)throws Exception {
        frame.validate();Container c=frame.getContentPane();BufferedImage image=new BufferedImage(c.getWidth(),c.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();c.paint(g);g.dispose();javax.imageio.ImageIO.write(image,"png",path.toFile());
        if(path.getFileName().toString().equals("quote.png")){StringBuilder dump=new StringBuilder();dump(c,dump,0);Files.writeString(path.resolveSibling("quote-layout.txt"),dump);}
    }
    private static void dump(Component c,StringBuilder out,int depth){if(c.isVisible()){out.append(" ".repeat(depth)).append(c.getClass().getSimpleName()).append(" ").append(c.getBounds()).append(" pref=").append(c.getPreferredSize()).append('\n');if(c instanceof Container container)for(Component child:container.getComponents())dump(child,out,depth+1);}}
}
