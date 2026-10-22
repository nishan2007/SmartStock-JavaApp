package ui.helpers;

import services.LanApiClient;
import com.google.gson.JsonObject;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.function.LongConsumer;

/** Customer balance and optional whole-point selection. Network work stays off the EDT. */
public final class LoyaltyDialog {
    private LoyaltyDialog() { }
    public static void open(Component owner,int customer,LongConsumer select) {
        new SwingWorker<JsonObject,Void>(){
            protected JsonObject doInBackground()throws Exception{return LanApiClient.loyaltyBalance(customer);}
            protected void done(){try{
                JsonObject b=get();String message="Available: "+b.get("points").getAsLong()+" points = G$"+b.get("value").getAsBigDecimal().toPlainString()+"\nPending earnings: "+b.get("pendingPoints").getAsLong()+" points\nReserved: "+b.get("reservedPoints").getAsLong()+" points";
                if(b.has("localPendingDocuments")&&b.get("localPendingDocuments").getAsLong()>0)message+="\nStore updates awaiting synchronization: "+b.get("localPendingDocuments").getAsLong();
                String[] choices=select==null?new String[]{"History","Adjust points","Close"}:new String[]{"Redeem points","Remove redemption","History","Cancel"};
                int choice=JOptionPane.showOptionDialog(owner,message,"Customer loyalty",JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,choices,choices[0]);
                if(select!=null&&choice==0){String input=JOptionPane.showInputDialog(owner,"Whole points to redeem (minimum "+b.getAsJsonObject("settings").get("minimumRedemption").getAsLong()+"):");if(input!=null){long points=Long.parseLong(input.trim());if(points<=0)throw new IllegalArgumentException("Enter positive whole points.");select.accept(points);}}
                else if(select!=null&&choice==1)select.accept(0);
                else if((select==null&&choice==0)||(select!=null&&choice==2))history(owner,b);
                else if(select==null&&choice==1)adjust(owner,customer);
            }catch(Exception e){error(owner,e);}}
        }.execute();
    }
    private static void history(Component owner,JsonObject b){var model=new DefaultTableModel(new String[]{"Date","Action","Points","Value per point","Reason"},0){public boolean isCellEditable(int r,int c){return false;}};for(var e:b.getAsJsonArray("history")){var row=e.getAsJsonObject();model.addRow(new Object[]{row.get("at").getAsString(),row.get("kind").getAsString(),row.get("points").getAsLong(),row.get("pointValue").getAsBigDecimal(),row.get("reason").getAsString()});}var scroll=new JScrollPane(new JTable(model));scroll.setPreferredSize(new Dimension(760,350));JOptionPane.showMessageDialog(owner,scroll,"Loyalty history",JOptionPane.PLAIN_MESSAGE);}
    private static void adjust(Component owner,int customer){
        String count=JOptionPane.showInputDialog(owner,"Administrator adjustment: signed whole points (for example 100 or -100):");if(count==null)return;
        String reason=JOptionPane.showInputDialog(owner,"Reason for this adjustment:");if(reason==null||reason.isBlank())return;
        final long points;try{points=Long.parseLong(count.trim());}catch(Exception e){error(owner,e);return;}
        String key=java.util.UUID.randomUUID().toString();new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{var r=new JsonObject();r.addProperty("action","ADJUST");r.addProperty("customerId",customer);r.addProperty("points",points);r.addProperty("reason",reason);LanApiClient.loyaltyUpdate(r,key);return null;}protected void done(){try{get();open(owner,customer,null);}catch(Exception e){error(owner,e);}}}.execute();
    }
    public static void error(Component owner,Throwable e){while(e.getCause()!=null)e=e.getCause();JOptionPane.showMessageDialog(owner,e.getMessage(),"Loyalty",JOptionPane.ERROR_MESSAGE);}
}
