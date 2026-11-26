package ui.screens;
import org.junit.jupiter.api.Test;
import services.LanApiClient;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
class PhoneMinutesLayoutTest {
 @Test void rendersPopulatedScreenWithoutAStoreConnection()throws Exception {
  assumeFalse(GraphicsEnvironment.isHeadless());CountDownLatch loaded=new CountDownLatch(1);
  PhoneMinutes.Gateway gateway=new PhoneMinutes.Gateway(){
   public List<LanApiClient.CustomerAccountRecord> customers(){var json=services.LanJson.create();return List.of(json.fromJson("{customerId:1,accountNumber:'CA-0123',name:'Harbor Supplies',phone:'6000123',active:true,minutesDue:8500,totalDue:11700}",LanApiClient.CustomerAccountRecord.class),json.fromJson("{customerId:2,accountNumber:'CA-0248',name:'Harbor Cafe',phone:'6000789',active:true,minutesDue:2900,totalDue:3400}",LanApiClient.CustomerAccountRecord.class));}
   public List<LanApiClient.PhoneMinutesRecord> history(Integer customer,String search,int offset,String from,String to,String method){loaded.countDown();return previewRows();}
   public void save(Integer customer,String phone,BigDecimal amount,String method,String key){fail("Rendering must not save");}
   public void reverse(long id,String reason,String key){fail("Rendering must not reverse");}
  };
  PhoneMinutes[] frame=new PhoneMinutes[1];
  try {
   SwingUtilities.invokeAndWait(()->{frame[0]=new PhoneMinutes(null,gateway);frame[0].setExtendedState(Frame.NORMAL);frame[0].setSize(1280,800);frame[0].setVisible(true);});
   assertTrue(loaded.await(10,TimeUnit.SECONDS));
   for(int i=0;i<50;i++){boolean[] ready={false};SwingUtilities.invokeAndWait(()->ready[0]=findTable(frame[0]).getRowCount()==7);if(ready[0])break;Thread.sleep(50);}
   for(String query:List.of("supplies","CA-0123","6000123")){
    SwingUtilities.invokeAndWait(()->{var combo=findCustomer(frame[0]);var editor=(javax.swing.text.JTextComponent)combo.getEditor().getEditorComponent();editor.setText(query);var event=new java.awt.event.KeyEvent(editor,java.awt.event.KeyEvent.KEY_RELEASED,System.currentTimeMillis(),0,java.awt.event.KeyEvent.VK_A,'a');for(var listener:editor.getKeyListeners())listener.keyReleased(event);});
    SwingUtilities.invokeAndWait(()->{var combo=findCustomer(frame[0]);assertEquals(2,combo.getItemCount());assertTrue(combo.getItemAt(1).contains("Harbor Supplies"));assertEquals(query,combo.getEditor().getItem());combo.setSelectedIndex(1);combo.setPopupVisible(false);assertTrue(combo.getEditor().getItem().toString().contains("CA-0123"));});
   }
   Path out=Path.of("target","phone-minutes-preview");Files.createDirectories(out);
   SwingUtilities.invokeAndWait(()->{assertEquals(7,findTable(frame[0]).getRowCount());findTable(frame[0]).setRowSelectionInterval(2,2);findNamed(frame[0],"minutesPhone").setText("+5926000123");findNamed(frame[0],"minutesAmount").setText("1,000");});
   for(int width:new int[]{1440,1280}){
    SwingUtilities.invokeAndWait(()->{frame[0].setExtendedState(Frame.NORMAL);frame[0].setSize(width,width==1440?1024:800);frame[0].validate();});
    SwingUtilities.invokeAndWait(()->frame[0].validate());
    SwingUtilities.invokeAndWait(()->frame[0].validate());
    SwingUtilities.invokeAndWait(()->{try{var image=new BufferedImage(width,width==1440?1024:800,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();try{frame[0].printAll(graphics);var root=frame[0].getRootPane();var content=graphics.create(root.getX(),root.getY(),root.getWidth(),root.getHeight());try{root.paintAll(content);}finally{content.dispose();}}finally{graphics.dispose();}javax.imageio.ImageIO.write(image,"png",out.resolve("screen-"+width+".png").toFile());}catch(java.io.IOException ex){throw new RuntimeException(ex);}});
   }
  }finally{if(frame[0]!=null)SwingUtilities.invokeAndWait(()->frame[0].dispose());}
 }
 private static List<LanApiClient.PhoneMinutesRecord> previewRows(){
  Object[][] data={{1048,"Harbor Supplies","+5926000456",1500,300,1200,"CREDIT","18:09"},{1047,"Harbor Cafe","+5926000789",500,500,0,"CASH","17:46"},{1046,"Harbor Supplies","+5926000123",1000,0,1000,"CREDIT","17:19"},{1045,"Walk-in","+5926000999",500,500,0,"CASH","16:37"},{1044,"Harbor Cafe","+5926000789",100,0,100,"CREDIT","15:58"},{1043,"Harbor Supplies","+5926000456",2000,0,2000,"CREDIT","15:21"},{1042,"Walk-in","+5926000011",500,500,0,"CASH","14:07"}};
  var out=new java.util.ArrayList<LanApiClient.PhoneMinutesRecord>();for(var r:data)out.add(new LanApiClient.PhoneMinutesRecord(((Number)r[0]).longValue(),"MIN-"+r[0],(String)r[1],(String)r[2],BigDecimal.valueOf((int)r[3]),BigDecimal.valueOf((int)r[4]),BigDecimal.valueOf((int)r[5]),(String)r[6],java.time.Instant.parse("2026-11-05T"+r[7]+":00Z").toEpochMilli(),"Preview Cashier","Preview Store",false,"",BigDecimal.ZERO));return out;
 }
 private static ui.components.SearchableComboBox findCustomer(Container parent){for(Component child:parent.getComponents()){if(child instanceof ui.components.SearchableComboBox combo)return combo;if(child instanceof Container c){var result=findCustomer(c);if(result!=null)return result;}}return null;}
 private static JTextField findNamed(Container parent,String name){for(Component child:parent.getComponents()){if(child instanceof JTextField f&&name.equals(f.getName()))return f;if(child instanceof Container c){var result=findNamed(c,name);if(result!=null)return result;}}return null;}
 private static JTable findTable(Container parent){for(Component child:parent.getComponents()){if(child instanceof JTable table)return table;if(child instanceof Container c){JTable result=findTable(c);if(result!=null)return result;}}return null;}
}
