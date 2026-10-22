package ui.screens.companyprefs;

import com.google.gson.*;
import services.LanApiClient;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.UUID;

/** Explicit classification for delivery, gift cards and other non-rewardable catalogue entries. */
final class LoyaltyEligibilityDialog {
    private LoyaltyEligibilityDialog() { }
    static void open(Component owner){
        String search=JOptionPane.showInputDialog(owner,"Search products and custom items (up to 200 matches):","");if(search==null)return;
        new SwingWorker<JsonObject,Void>(){
            protected JsonObject doInBackground()throws Exception{var r=new JsonObject();r.addProperty("action","ELIGIBILITY");r.addProperty("search",search);return LanApiClient.loyaltyRead(r);}
            protected void done(){try{var data=get();var model=new DefaultTableModel(new Object[]{"Type","ID","Item","Earn/redeem eligible"},0){public boolean isCellEditable(int r,int c){return c==3;}public Class<?> getColumnClass(int c){return c==3?Boolean.class:Object.class;}};
                for(var e:data.getAsJsonArray("items")){var row=e.getAsJsonObject();model.addRow(new Object[]{row.get("kind").getAsString(),row.get("id").getAsLong(),row.get("name").getAsString(),row.get("eligible").getAsBoolean()});}
                JTable table=new JTable(model);var scroll=new JScrollPane(table);scroll.setPreferredSize(new Dimension(760,400));if(JOptionPane.showConfirmDialog(owner,scroll,"Uncheck delivery and gift card products",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;if(table.isEditing())table.getCellEditor().stopCellEditing();
                var request=new JsonObject();request.addProperty("action","ELIGIBILITY");var changes=new JsonArray();for(int i=0;i<model.getRowCount();i++){var row=new JsonObject();row.addProperty("kind",model.getValueAt(i,0).toString());row.addProperty("id",((Number)model.getValueAt(i,1)).longValue());row.addProperty("eligible",(Boolean)model.getValueAt(i,3));changes.add(row);}request.add("items",changes);String key=UUID.randomUUID().toString();
                new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{LanApiClient.loyaltyUpdate(request,key);return null;}protected void done(){try{get();JOptionPane.showMessageDialog(owner,"Loyalty eligibility saved for future purchases.");}catch(Exception e){ui.helpers.LoyaltyDialog.error(owner,e);}}}.execute();
            }catch(Exception e){ui.helpers.LoyaltyDialog.error(owner,e);}}
        }.execute();
    }
}
