package ui.screens.companyprefs;

import com.google.gson.*;
import services.LanApiClient;
import ui.design.*;
import ui.helpers.SessionDataCache;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.UUID;

/** Shared custom-item categories, managed from Company Preferences. */
public final class CustomItemCategoriesPanel extends JPanel {
    private final DefaultTableModel model=new DefaultTableModel(new String[]{"Category"},0){public boolean isCellEditable(int r,int c){return false;}};
    private final JTable table=new JTable(model);
    private final JLabel status=new JLabel("Loading categories…");
    private JsonArray data=new JsonArray();
    private boolean busy;
    public CustomItemCategoriesPanel(){
        super(new BorderLayout(12,12));setBorder(BorderFactory.createEmptyBorder(18,18,18,18));
        JTextArea help=new JTextArea("Add custom item categories here, then select a category in Custom Items. Active items appear automatically under that group on the website after catalog sync.\nExisting groups: Shirts, Sublimation, Business Cards, Large Format Printing, Laser.");help.setEditable(false);help.setLineWrap(true);help.setWrapStyleWord(true);help.setOpaque(false);add(help,BorderLayout.NORTH);
        table.setAutoCreateRowSorter(true);add(new JScrollPane(table),BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout());JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT));
        addButton(actions,"Add category",()->edit(false));addButton(actions,"Rename category",()->edit(true));addButton(actions,"Refresh",this::load);bottom.add(actions,BorderLayout.NORTH);bottom.add(status,BorderLayout.SOUTH);add(bottom,BorderLayout.SOUTH);load();
    }
    private void addButton(JPanel panel,String title,Runnable action){JButton button=new JButton(title);DeckersSwing.styleUtilityButton(button,DeckersPalette.ORANGE);button.addActionListener(e->{if(!busy)action.run();});panel.add(button);}
    private void load(){JsonObject body=new JsonObject();body.addProperty("action","CUSTOM_CATEGORY_STATE");send(body,true);}
    private void edit(boolean rename){
        JsonObject selected=null;if(rename){int row=table.getSelectedRow();if(row<0){status.setText("Select a category to rename.");return;}selected=data.get(table.convertRowIndexToModel(row)).getAsJsonObject();}
        String name=(String)JOptionPane.showInputDialog(this,"Custom item category name:",rename?"Rename category":"Add category",JOptionPane.PLAIN_MESSAGE,null,null,selected==null?"":selected.get("name").getAsString());if(name==null)return;
        JsonObject body=new JsonObject();body.addProperty("action","CUSTOM_CATEGORY_SAVE");body.addProperty("id",selected==null?UUID.randomUUID().toString():selected.get("id").getAsString());body.addProperty("revision",selected==null?0:selected.get("revision").getAsLong());body.addProperty("name",name);send(body,false);
    }
    private void send(JsonObject body,boolean loading){busy=true;status.setText("Working…");new SwingWorker<JsonObject,Void>(){
        protected JsonObject doInBackground()throws Exception{return LanApiClient.storefrontAdmin(body,UUID.randomUUID().toString());}
        protected void done(){busy=false;try{JsonObject result=get();if(loading){data=result.getAsJsonArray("categories");model.setRowCount(0);for(var row:data)model.addRow(new Object[]{row.getAsJsonObject().get("name").getAsString()});status.setText("Custom item categories are up to date.");}else{SessionDataCache.invalidate("custom-catalog:");load();}}catch(Exception e){status.setText("Could not update categories.");JOptionPane.showMessageDialog(CustomItemCategoriesPanel.this,e.getCause()==null?e.getMessage():e.getCause().getMessage(),"Custom item categories",JOptionPane.ERROR_MESSAGE);}}
    }.execute();}
}
