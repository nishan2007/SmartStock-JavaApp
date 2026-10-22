package gy.deckers.studio;

import com.google.gson.*;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;

/** Named store views; the server authorizes every read and assignment. */
final class StoreTemplateFolders {
    private volatile JsonArray stores=new JsonArray();
    private volatile int current=-1,view=-1;
    private final Path folder;
    StoreTemplateFolders(Path folder){this.folder=folder;try{accept(JsonParser.parseString(Files.readString(folder.resolve("store-folders.json"))).getAsJsonObject());}catch(Exception ignored){}}
    void accept(JsonObject catalog)throws Exception{
        if(!catalog.has("stores"))return;
        stores=catalog.getAsJsonArray("stores").deepCopy();current=catalog.get("defaultStoreId").getAsInt();if(view<0)view=current;
        Files.createDirectories(folder);Path temp=Files.createTempFile(folder,".stores-",".tmp");try{Files.writeString(temp,catalogForCache().toString());Files.move(temp,folder.resolve("store-folders.json"),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(temp);}
    }
    private JsonObject catalogForCache(){JsonObject result=new JsonObject();result.add("stores",stores);result.addProperty("defaultStoreId",current);return result;}
    JsonObject metadata(Path file){try{return JsonParser.parseString(Files.readString(file.resolveSibling(file.getFileName()+".meta.json"))).getAsJsonObject();}catch(Exception ex){return new JsonObject();}}
    Path file(JsonObject row,String extension){return folder.resolve((row.has("storageStoreId")?row.get("storageStoreId").getAsInt()+"_":"")+row.get("id").getAsString()+extension);}
    void target(JsonObject request,JsonObject row){if(row.has("storageStoreId"))request.add("storageStoreId",row.get("storageStoreId"));}
    boolean visible(Path file){JsonObject row=metadata(file);return (current<0||row.has("storageStoreId"))&&visible(row);}
    boolean own(JsonObject row){return visibleAt(row,current);}
    private boolean visibleAt(JsonObject row,int selected){
        if(!row.has("ownerStoreId"))return true;
        if(row.get("ownerStoreId").getAsInt()==selected)return true;
        if(row.has("sharedStoreIds"))for(var id:row.getAsJsonArray("sharedStoreIds"))if(id.getAsInt()==selected)return true;
        return false;
    }
    private boolean visible(JsonObject row){return view==0||visibleAt(row,view);}
    String label(Path file,String name){JsonObject row=metadata(file);return row.has("ownerStoreName")?name+" · "+row.get("ownerStoreName").getAsString()+(row.has("sharedStoreIds")&&!row.getAsJsonArray("sharedStoreIds").isEmpty()?" · shared":""):name;}
    void resetView(){view=current;}
    String viewName(){if(view==0)return "All accessible stores";for(var value:stores){var s=value.getAsJsonObject();if(s.get("id").getAsInt()==view)return s.get("name").getAsString();}return "My store";}
    JComponent selector(Runnable changed){
        JComboBox<String> choice=new JComboBox<>();java.util.List<Integer> ids=new ArrayList<>();boolean[] updating={false};String[] previous={""};
        Runnable update=()->{String signature=stores.toString();if(signature.equals(previous[0]))return;previous[0]=signature;updating[0]=true;try{choice.removeAllItems();ids.clear();choice.addItem("All accessible stores");ids.add(0);for(var value:stores){var store=value.getAsJsonObject();int id=store.get("id").getAsInt();choice.addItem(store.get("name").getAsString()+(id==current?" (my store)":""));ids.add(id);}int selected=ids.indexOf(view);choice.setSelectedIndex(selected<0?0:selected);choice.setEnabled(!stores.isEmpty());}finally{updating[0]=false;}};
        update.run();choice.addActionListener(e->{if(!updating[0]&&choice.getSelectedIndex()>=0){view=ids.get(choice.getSelectedIndex());changed.run();}});
        javax.swing.Timer timer=new javax.swing.Timer(1000,e->update.run());choice.addHierarchyListener(e->{if((e.getChangeFlags()&java.awt.event.HierarchyEvent.SHOWING_CHANGED)!=0){if(choice.isShowing())timer.start();else timer.stop();}});return choice;
    }

    private JPanel panel(java.awt.LayoutManager layout){JPanel panel=ui.design.DeckersSwing.panel();panel.setLayout(layout);return panel;}
    JsonObject assignment(Component owner,Path file){
        JsonObject row=metadata(file);if(!row.has("storageStoreId")){JOptionPane.showMessageDialog(owner,"Refresh the shared library before changing stores.");return null;}
        JPanel panel=panel(new BorderLayout(8,8));JComboBox<String> owners=new JComboBox<>();java.util.List<Integer> ids=new ArrayList<>();JPanel checks=panel(new GridLayout(0,1));java.util.List<JCheckBox> shared=new ArrayList<>();
        for(var value:stores){JsonObject s=value.getAsJsonObject();int id=s.get("id").getAsInt();ids.add(id);String name=s.get("name").getAsString();owners.addItem(name);JCheckBox box=new JCheckBox(name);if(row.has("sharedStoreIds"))for(var share:row.getAsJsonArray("sharedStoreIds"))if(share.getAsInt()==id)box.setSelected(true);shared.add(box);checks.add(box);}
        int selected=ids.indexOf(row.get("ownerStoreId").getAsInt());if(selected<0){JOptionPane.showMessageDialog(owner,"Access to the owner store is required to change template ownership or sharing.");return null;}owners.setSelectedIndex(selected);
        JPanel top=panel(new BorderLayout(8,8));top.add(new JLabel("Owner store"),BorderLayout.WEST);top.add(owners);panel.add(top,BorderLayout.NORTH);JPanel sharing=panel(new BorderLayout());sharing.add(new JLabel("Also show in these store folders"),BorderLayout.NORTH);sharing.add(new JScrollPane(checks));panel.add(sharing);
        if(JOptionPane.showConfirmDialog(owner,panel,"Template stores",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION||owners.getSelectedIndex()<0)return null;
        JsonObject request=new JsonObject();request.addProperty("action","ASSIGN");request.add("id",row.get("id"));target(request,row);request.addProperty("ownerStoreId",ids.get(owners.getSelectedIndex()));JsonArray shares=new JsonArray();for(int i=0;i<ids.size();i++)if(shared.get(i).isSelected())shares.add(ids.get(i));request.add("sharedStoreIds",shares);request.addProperty("expectedAssignment",row.has("assignmentVersion")?row.get("assignmentVersion").getAsString():"");return request;
    }
}
