package ui.screens;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import services.OrdersWorkQueueService.Row;
import static org.junit.jupiter.api.Assertions.*;

class OrdersWorkQueuePanelTest {
    @Test void lanesShareSpaceByWorkloadIncludingEmptyAndNarrowBoards(){
        int[] widths=OrdersWorkQueuePanel.laneWidths(1600,7,2,11);
        assertEquals(310,widths[2]);assertTrue(widths[0]>widths[1]);
        assertArrayEquals(widths,OrdersWorkQueuePanel.laneWidths(1600,7,2,100));
        assertEquals(1600,java.util.Arrays.stream(widths).sum());
        assertArrayEquals(new int[]{445,445,310},OrdersWorkQueuePanel.laneWidths(1200,0,0,0));
        assertArrayEquals(new int[]{270,1020,310},OrdersWorkQueuePanel.laneWidths(1600,0,20,0));
        assertEquals(500,java.util.Arrays.stream(OrdersWorkQueuePanel.laneWidths(500,7,2,11)).sum());
    }
    @Test void realWorkloadResizesEveryLaneAndRespondsToFilters()throws Exception{
        javax.swing.SwingUtilities.invokeAndWait(()->{try{
            var panel=new OrdersWorkQueuePanel(null,r->{});var jobs=new java.util.ArrayList<Row>();
            for(int i=0;i<20;i++)jobs.add(new Row("CUSTOM_ORDER",i,"CO-1792764272941-"+i,i<7?"NEW":i<9?"IN_PROGRESS":"READY","Customer "+i,"123","Alex","Printed work",null,true,false,false,BigDecimal.TEN,i));
            panel.setSnapshot(jobs,java.time.ZoneId.of("America/Guyana"));panel.setSize(1672,820);for(int i=0;i<4;i++)layout(panel);
            var lanes=findLanes(panel);assertEquals(3,lanes.size());assertEquals(310,lanes.get(2).getWidth());assertTrue(lanes.get(0).getWidth()>lanes.get(1).getWidth());
            var readyCards=new java.util.ArrayList<javax.swing.JComponent>();collectCards(lanes.get(2),readyCards);assertEquals(11,readyCards.size());for(var card:readyCards)assertEquals(0,card.getX(),"Ready stays in one column");
            var output=java.nio.file.Path.of("target/work-queue-design/workload-7-2-11.png");java.nio.file.Files.createDirectories(output.getParent());var image=new java.awt.image.BufferedImage(1672,820,java.awt.image.BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();graphics.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);panel.printAll(graphics);graphics.dispose();javax.imageio.ImageIO.write(image,"png",output.toFile());
            panel.search.setText("Customer 0");for(int i=0;i<4;i++)layout(panel);lanes=findLanes(panel);assertTrue(lanes.get(0).getWidth()>lanes.get(1).getWidth());assertEquals(310,lanes.get(2).getWidth());
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    private static java.util.List<javax.swing.JComponent> findLanes(java.awt.Container parent){var result=new java.util.ArrayList<javax.swing.JComponent>();for(var child:parent.getComponents()){if(child instanceof javax.swing.JComponent component&&component.getClientProperty("queueJobCount")!=null)result.add(component);else if(child instanceof java.awt.Container nested)result.addAll(findLanes(nested));}return result;}
    private static void collectCards(java.awt.Container parent,java.util.List<javax.swing.JComponent> result){for(var child:parent.getComponents()){if(child instanceof javax.swing.JComponent component&&component.getClientProperty("row") instanceof Row)result.add(component);else if(child instanceof java.awt.Container nested)collectCards(nested,result);}}
    @Test void routesCompletedUnpricedWorkToProductionAndReadyWorkToPickup() {
        assertEquals(0,OrdersWorkQueuePanel.lane(row("CUSTOM_ORDER","NEW",false)));
        assertEquals(1,OrdersWorkQueuePanel.lane(row("SALES_TASK","COMPLETED",true)));
        assertEquals(2,OrdersWorkQueuePanel.lane(row("SALES_TASK","COMPLETED",false)));
        assertTrue(OrdersWorkQueuePanel.matchesAssignment(row("SALES_TASK","IN_PROGRESS",true),"Alex",true,"Alex"));
        assertFalse(OrdersWorkQueuePanel.matchesAssignment(row("SALES_TASK","IN_PROGRESS",true),"Assigned to: All staff",true,"Other staff"));
    }
    @Test void deadlineUsesStoreDayAndAsapSortsFirst() {
        var zone=java.time.ZoneId.of("America/Guyana");var today=java.time.LocalDate.of(2026,10,25);
        var timed=new Row("SALES_TASK",1,"ST-1","IN_PROGRESS","A",null,"Alex","Copies",today.atTime(23,30).atZone(zone).toInstant().toEpochMilli(),false,false,true,null,1);
        var asap=new Row("SALES_TASK",2,"ST-2","IN_PROGRESS","B",null,"Alex","Copies",null,false,false,true,null,2,true);
        assertTrue(OrdersWorkQueuePanel.matchesDeadline(timed,"Due today",today,zone));
        assertFalse(OrdersWorkQueuePanel.matchesDeadline(timed,"Due today",today.plusDays(1),zone));
        assertFalse(OrdersWorkQueuePanel.matchesDeadline(asap,"No deadline",today,zone));
        assertTrue(OrdersWorkQueuePanel.comparator("Sort: Due soonest").compare(asap,timed)<0);
    }
    @Test void rendersBoardAtReferenceAndCompactSizes() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(()->{try {
            var opened=new java.util.ArrayList<Row>();var panel=new OrdersWorkQueuePanel(null,opened::add);var rows=new java.util.ArrayList<Row>();
            String[] names={"Jordan Ali","Ravi Khan","Sharon Williams","Maya Singh","Daniel James","Neisha Singh","Kevin Joseph","Anika Ram","Mark Adams","Dev Singh","Lisa Khan","Ryan Persaud","Alana Thomas","Joel Williams","Maria Ali","Priya Persaud","Andre Thomas"};
            for(int i=0;i<17;i++){String kind=i%2==0?"CUSTOM_ORDER":"SALES_TASK";rows.add(new Row(kind,1040+i,(kind.equals("CUSTOM_ORDER")?"CO-":"ST-")+(1040+i),i<3?"NEW":i>=15?"READY":"IN_PROGRESS",names[i],"123",i%3==0?"H-Lal":i%3==1?"A-James":"S-Persaud",i%2==0?"24 custom printed mugs":"Colour prints & lamination",java.time.LocalDate.of(2026,10,28).atStartOfDay(java.time.ZoneId.of("America/Guyana")).toInstant().toEpochMilli(),true,false,false,i%4==0?java.math.BigDecimal.ZERO:new java.math.BigDecimal("15000"),i,false,null,i==4,i==3));}
            panel.setSnapshot(rows,java.time.ZoneId.of("America/Guyana"));var first=findCard(panel);first.getActionMap().get("open").actionPerformed(new java.awt.event.ActionEvent(first,0,"open"));assertTrue(OrdersWorkQueuePanel.sameJob(rows.get(0),opened.get(0)));
            var logo=ui.design.DeckersLogoManager.loadDeckersLogoIcon(getClass());
            var root=new javax.swing.JPanel(new java.awt.BorderLayout());root.add(WorkQueue.createHeader("Skeldon","H-Lal",java.time.LocalDate.of(2026,10,25),new javax.swing.ImageIcon(ui.design.DeckersLogoManager.scaleToFit(logo.getImage(),168,50))),java.awt.BorderLayout.NORTH);root.add(panel);root.addNotify();
            var out=java.nio.file.Path.of("target/work-queue-design");java.nio.file.Files.createDirectories(out);
            for(int width:new int[]{1672,1120}){root.setSize(width,width==1672?886:720);for(int pass=0;pass<3;pass++)layout(root);var image=new java.awt.image.BufferedImage(root.getWidth(),root.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();graphics.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);root.printAll(graphics);graphics.dispose();javax.imageio.ImageIO.write(image,"png",out.resolve("work-queue-"+width+".png").toFile());}
            root.removeNotify();panel.search.setText("Jordan");assertTrue(findLabel(panel,"1 active jobs"));panel.type.setSelectedItem("Sales tasks");assertTrue(findLabel(panel,"0 active jobs"));
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    private static javax.swing.JComponent findCard(java.awt.Container container){for(var child:container.getComponents()){if(child instanceof javax.swing.JComponent component&&component.getClientProperty("row") instanceof Row)return component;if(child instanceof java.awt.Container nested){var result=findCard(nested);if(result!=null)return result;}}return null;}
    private static void layout(java.awt.Container container){container.doLayout();for(var child:container.getComponents())if(child instanceof java.awt.Container nested)layout(nested);}
    private static boolean findLabel(java.awt.Container container,String prefix){for(var child:container.getComponents()){if(child instanceof javax.swing.JLabel label&&label.getText()!=null&&label.getText().startsWith(prefix))return true;if(child instanceof java.awt.Container nested&&findLabel(nested,prefix))return true;}return false;}
    private Row row(String kind,String status,boolean pending) {
        return new Row(kind,7,kind.equals("SALES_TASK")?"ST-7":"CO-7",status,"Customer","123","Alex",
                "Copy and laminate five sets",null,false,false,pending,pending?null:BigDecimal.TEN,0);
    }
    @Test void completedWorkSeparatesPricingFromPickup() {
        Row unpriced=row("SALES_TASK","COMPLETED",true),ready=row("SALES_TASK","COMPLETED",false);
        assertEquals("Awaiting pricing",unpriced.stage());assertNull(unpriced.balance());
        assertEquals("Ready for pickup",ready.stage());
        assertTrue(OrdersWorkQueuePanel.matches(unpriced,"laminate","All work","Awaiting pricing"));
        assertFalse(OrdersWorkQueuePanel.matches(unpriced,"","All work","Ready for pickup"));
        assertFalse(OrdersWorkQueuePanel.matches(ready,"","All work","To do"));
    }
    @Test void typeAndSearchFiltersKeepBothSourcesInTheSameList() {
        Row task=row("SALES_TASK","IN_PROGRESS",true),custom=row("CUSTOM_ORDER","NEW",false);
        assertTrue(OrdersWorkQueuePanel.matches(task,"ALEX","All work","To do"));
        assertTrue(OrdersWorkQueuePanel.matches(custom,"CO-7","Custom Orders","All pending"));
        assertFalse(OrdersWorkQueuePanel.matches(task,"","Custom Orders","All pending"));
        assertFalse(OrdersWorkQueuePanel.matches(custom,"","Sales Tasks","All pending"));
        assertFalse(OrdersWorkQueuePanel.sameJob(task,custom),"Equal numeric IDs must open different workflows.");
    }
}
