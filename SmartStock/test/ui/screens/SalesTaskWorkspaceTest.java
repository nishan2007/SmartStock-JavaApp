package ui.screens;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SalesTaskWorkspaceTest {
    @Test void themeKeepsChevronStepsTransparentAndWithinTheirBounds()throws Exception{
        SwingUtilities.invokeAndWait(()->{try{
            JPanel bar=new JPanel(new GridLayout(1,4));bar.setBackground(Color.WHITE);
            String[] titles={"Customer & deadline","Work & items","Final pricing","Pickup & payment"};
            for(int i=0;i<4;i++){var button=new SalesTaskVisuals.Step(i+1,titles[i],"Step instructions");button.active=i==0;bar.add(button);}
            var theme=ui.helpers.ThemeManager.class.getDeclaredMethod("applyToComponent",Component.class);theme.setAccessible(true);theme.invoke(null,bar);
            bar.setSize(1048,70);bar.doLayout();
            for(var component:bar.getComponents()){var button=(SalesTaskVisuals.Step)component;assertFalse(button.isOpaque());assertFalse(button.isContentAreaFilled());}
            var output=new BufferedImage(1048,70,BufferedImage.TYPE_INT_RGB);var graphics=output.createGraphics();bar.printAll(graphics);graphics.dispose();
            // The next step's notch and the active step's trailing corner must remain clear.
            assertEquals(bar.getBackground().getRGB(),output.getRGB(264,10));
            assertEquals(bar.getBackground().getRGB(),output.getRGB(258,2));
            Path directory=Path.of("target/sales-task-design");Files.createDirectories(directory);javax.imageio.ImageIO.write(output,"png",directory.resolve("step-bar.png").toFile());
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    private static final ZoneId ZONE=ZoneId.of("America/Guyana");
    static JsonObject options(){return json("{employees:[{user_id:1,name:'Alex'}],products:[{product_id:10,name:'Copying (A4 B&W)',sku:'COPY-A4',price:40},{product_id:11,name:'Lamination (A4)',sku:'LAM-A4',price:200}]}");}
    static JsonObject detail(){return json("""
        {task:{task_id:1042,version:1,customer_id:42,customer_name:'Maya Singh',phone:'600-0142',due_asap:true,due_at:null,
        assigned_user_id:1,status:'IN_PROGRESS',billing_status:'PROVISIONAL',final_total:null,sale_id:null,
        instructions:'50 black-and-white A4 copies. Laminate 10 sheets. Keep originals together.',
        checkout_request:'{"lines":[{"productId":10,"miscItemName":"A4 black and white copy","quantity":50,"unitPrice":40,"discountPercent":0},{"productId":11,"miscItemName":"A4 lamination","quantity":10,"unitPrice":200,"discountPercent":0}]}'},
        customerAccountNumber:'CA-01042',paid:1000,files:[{file_id:1,filename:'originals.pdf',size:253952}],payments:[{payment_id:1,created_at:'2026-10-25',amount:1000,payment_action:'PAYMENT',payment_method:'CASH',user_name:'Alex'}],events:[]}
        """);}
    private static JsonObject json(String value){return JsonParser.parseString(value).getAsJsonObject();}
    static class Actions implements SalesTaskWorkspace.Handler {
        final List<String> actions=new ArrayList<>();
        public void select(long id){actions.add("SELECT:"+id);}public void refresh(String q){actions.add("REFRESH");}
        public void save(String action){actions.add(action);}public void action(String action){actions.add(action);}public boolean discardChanges(){return true;}
    }
    @Test void inventoryPriceAndEditableQuantityProduceTheSavedEstimate()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var events=new Actions();var view=new SalesTaskWorkspace(events);view.setOptions(options(),ZONE);view.showTask(detail());
            assertEquals(1,view.currentStep());assertEquals("Mark work completed",view.primary.getText());
            assertEquals("Not finalized",view.balance.getText());assertFalse(view.isDirty());
            assertEquals(6,view.itemTable.convertColumnIndexToModel(0));assertEquals(7,view.itemTable.convertColumnIndexToModel(6));assertEquals(7,view.itemTable.getColumnCount());
            view.items.setValueAt(60,0,2);JsonObject draft=view.draft();assertEquals(60,draft.getAsJsonObject("checkout").getAsJsonArray("lines").get(0).getAsJsonObject().get("quantity").getAsInt());
            assertEquals(40,draft.getAsJsonObject("checkout").getAsJsonArray("lines").get(0).getAsJsonObject().get("unitPrice").getAsInt());assertTrue(draft.get("dueAsap").getAsBoolean());
            view.primary.doClick();assertTrue(events.actions.contains("COMPLETE"));
            view.goTo(3);assertEquals(1,view.currentStep(),"Pickup is locked before final pricing");
        });
    }
    @Test void completedAndFinalizedWorkUseTheCorrectNextStepAndReadOnlyBill()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var view=new SalesTaskWorkspace(new Actions());view.setOptions(options(),ZONE);var detail=detail();detail.getAsJsonObject("task").addProperty("status","COMPLETED");view.showTask(detail);
            assertEquals(2,view.currentStep());assertEquals("Finalize bill",view.primary.getText());
            detail.getAsJsonObject("task").addProperty("billing_status","FINALIZED");detail.getAsJsonObject("task").addProperty("final_total",4000);
            view.showTask(detail);view.goTo(3);assertEquals("Collect & take payment",view.primary.getText());assertFalse(view.items.isCellEditable(0,2));assertFalse(view.save.isEnabled());
        });
    }
    @Test void newTaskUsesInlineCustomerAndWorkStepsWithoutLinkingThePreviousTask()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var events=new Actions();var view=new SalesTaskWorkspace(events);view.setOptions(options(),ZONE);view.showTask(detail());view.newTask();
            assertTrue(view.isNew());assertEquals(0,view.currentStep());view.customerName.setText("Walk-in Customer");view.primary.doClick();assertEquals(1,view.currentStep());
            view.instructions.setText("Copy 50 pages");view.product.setSelectedItem("Copying (A4 B&W)");
            view.quantity.setValue(50);view.addCatalogueItem();
            assertEquals(new java.math.BigDecimal("40"),view.draft().getAsJsonObject("checkout").getAsJsonArray("lines").get(0).getAsJsonObject().get("unitPrice").getAsBigDecimal());
            var draft=view.draft();assertTrue(draft.get("customerId").isJsonNull());assertFalse(draft.has("taskId"));view.primary.doClick();assertTrue(events.actions.contains("SAVE"));
        });
    }
    @Test void duplicateInventoryNamesKeepTheirOwnPrices()throws Exception {
        SwingUtilities.invokeAndWait(()->{var data=options();var duplicate=data.getAsJsonArray("products").get(0).getAsJsonObject().deepCopy();duplicate.addProperty("product_id",12);duplicate.addProperty("sku","COPY-COLOUR");duplicate.addProperty("price",80);data.getAsJsonArray("products").add(duplicate);var view=new SalesTaskWorkspace(new Actions());view.setOptions(data,ZONE);view.customerName.setText("Customer");view.instructions.setText("Colour copies");view.product.setSelectedItem("Copying (A4 B&W) [COPY-COLOUR]");view.addCatalogueItem();var line=view.draft().getAsJsonObject("checkout").getAsJsonArray("lines").get(0).getAsJsonObject();assertEquals(12,line.get("productId").getAsInt());assertEquals(80,line.get("unitPrice").getAsInt());});
    }
    @Test void anExistingWorkspaceRemainsReadableWhenTheAppChangesToDarkMode()throws Exception {
        SwingUtilities.invokeAndWait(()->{try{var cached=ui.helpers.ThemeManager.class.getDeclaredField("cachedDarkMode");cached.setAccessible(true);Object original=cached.get(null);try{cached.set(null,false);var view=new SalesTaskWorkspace(new Actions());view.setOptions(options(),ZONE);view.showTask(detail());JPanel root=new JPanel(new BorderLayout());root.add(SalesTasks.createBrandHeader("SmartStock",null,"Skeldon","Alex",java.time.LocalDate.of(2026,10,25)),BorderLayout.NORTH);root.add(view);var theme=ui.helpers.ThemeManager.class.getDeclaredMethod("applyToComponent",Component.class);theme.setAccessible(true);theme.invoke(null,root);cached.set(null,true);theme.invoke(null,root);assertEquals(ui.design.DeckersPalette.surface(),view.itemTable.getBackground());assertTrue(view.instructions.getForeground().getRed()>150);assertEquals(Color.WHITE,view.primary.getForeground());root.addNotify();root.setSize(1488,987);Files.createDirectories(designPath(""));capture(root,"dark");Rectangle actionBounds=SwingUtilities.convertRectangle(view.primary.getParent(),view.primary.getBounds(),root);assertTrue(actionBounds.x+actionBounds.width<=root.getWidth(),actionBounds.toString());Files.writeString(designPath("dark-geometry.txt"),actionBounds+" root="+root.getBounds()+" ui="+view.primary.getUI()+" opaque="+view.primary.isOpaque()+" parent="+view.primary.getParent().getBounds());}finally{cached.set(null,original);}}catch(Exception ex){throw new RuntimeException(ex);}});
    }
    @Test void rendersTheSelectedDesignAtDesktopAndCompactSizes()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            try {
                var view=new SalesTaskWorkspace(new Actions());view.setOptions(options(),ZONE);view.showTask(detail());
                JsonArray jobs=new JsonArray();jobs.add(detail().getAsJsonObject("task"));for(int i=0;i<3;i++){var t=detail().getAsJsonObject("task").deepCopy();t.addProperty("task_id",new long[]{1043,1040,1038}[i]);t.addProperty("customer_name",List.of("Jordan Ali","Priya Persaud","Daniel James").get(i));t.addProperty("instructions",List.of("Print booklet","Copy & bind","Colour prints").get(i));t.addProperty("due_asap",false);t.addProperty("due_at",List.of("2026-10-25T15:00-04:00","2026-10-25T12:30-04:00","2026-10-26T10:00-04:00").get(i));t.addProperty("status",i==1?"COMPLETED":i==2?"IN_PROGRESS":"RECEIVED");if(i==1)t.addProperty("billing_status","FINALIZED");jobs.add(t);}view.setJobs(jobs,1042L);
                var image=ui.design.DeckersLogoManager.loadDeckersLogoIcon(getClass());Icon logo=image==null?null:new ImageIcon(ui.design.DeckersLogoManager.scaleToFit(image.getImage(),190,64));
                JPanel root=new JPanel(new BorderLayout());root.add(SalesTasks.createBrandHeader("SmartStock",logo,"Skeldon","Alex",java.time.LocalDate.of(2026,10,25)),BorderLayout.NORTH);root.add(view,BorderLayout.CENTER);
                var applyTheme=ui.helpers.ThemeManager.class.getDeclaredMethod("applyToComponent",Component.class);applyTheme.setAccessible(true);applyTheme.invoke(null,root);
                assertFalse(view.primary.isContentAreaFilled(),"The application theme must preserve the drawn primary button");assertEquals(Color.WHITE,view.primary.getForeground());
                Files.createDirectories(designPath(""));Files.writeString(designPath("render-provenance.txt"),SalesTaskWorkspace.class.getProtectionDomain().getCodeSource().getLocation().toString());
                view.product.setSelectedItem("Copying (A4 B&W)");view.quantity.setValue(50);
                root.addNotify();
                for(int width:new int[]{1488,1024}){root.setSize(width,width==1488?987:768);layout(root);BufferedImage output=new BufferedImage(root.getWidth(),root.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=output.createGraphics();g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);root.printAll(g);g.dispose();Path path=designPath("workspace-"+width+".png");Files.createDirectories(path.getParent());javax.imageio.ImageIO.write(output,"png",path.toFile());assertTrue(view.primary.getWidth()>100);assertTrue(view.itemTable.getWidth()>350);}
                root.setSize(1488,987);
                view.goTo(0);capture(root,"customer");
                var completed=detail();completed.getAsJsonObject("task").addProperty("status","COMPLETED");view.showTask(completed);view.goTo(2);capture(root,"pricing");
                completed.getAsJsonObject("task").addProperty("billing_status","FINALIZED");completed.getAsJsonObject("task").addProperty("final_total",4000);view.showTask(completed);view.goTo(3);capture(root,"pickup");
                BufferedImage reference=javax.imageio.ImageIO.read(Path.of("docs/sales-task-design/reference.png").toFile());BufferedImage actual=javax.imageio.ImageIO.read(designPath("workspace-1488.png").toFile());BufferedImage comparison=new BufferedImage(actual.getWidth()*2,actual.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D compare=comparison.createGraphics();compare.drawImage(reference,0,0,actual.getWidth(),actual.getHeight(),0,reference.getHeight()-actual.getHeight(),reference.getWidth(),reference.getHeight(),null);compare.drawImage(actual,actual.getWidth(),0,null);compare.dispose();javax.imageio.ImageIO.write(comparison,"png",designPath("comparison.png").toFile());
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
    private static Path designPath(String name){return Path.of(System.getProperty("smartstock.salesTaskDesignOutput","target/sales-task-design")).resolve(name);}
    static void layout(Container c){for(int pass=0;pass<2;pass++){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container container)layoutOnce(container);}}
    private static void layoutOnce(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container container)layoutOnce(container);}
    private static void capture(JPanel root,String name)throws Exception{layout(root);BufferedImage image=new BufferedImage(root.getWidth(),root.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);root.printAll(g);g.dispose();javax.imageio.ImageIO.write(image,"png",designPath(name+".png").toFile());}
}
