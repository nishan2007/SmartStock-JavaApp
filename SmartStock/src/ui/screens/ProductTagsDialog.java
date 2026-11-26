package ui.screens;

import com.google.gson.*;
import services.LanApiClient;
import ui.helpers.UiTaskRunner;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.List;
import java.util.UUID;

/** Shared tag vocabulary and additive bulk edits; never replaces unrelated tags. */
public final class ProductTagsDialog extends JDialog {
    private final DefaultListModel<Tag> model=new DefaultListModel<>();
    private final JList<Tag> list=new JList<>(model);
    private final JTextField search=new JTextField();
    private final JTextField name=new JTextField(22);
    private final JCheckBox publicTag=new JCheckBox("Public — show on website");
    private final JLabel status=new JLabel("Loading tags…");
    private JsonArray tags=new JsonArray(),assignments=new JsonArray();
    private final List<Integer> products;
    private final Runnable changed;
    private final JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT));
    private boolean busy;

    public ProductTagsDialog(Window owner,List<Integer> products,Runnable changed) {
        super(owner,"Product Tags",ModalityType.APPLICATION_MODAL);
        this.products=List.copyOf(products);this.changed=changed;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(760,470);setLocationRelativeTo(owner);
        JPanel content=new JPanel(new BorderLayout(10,10));content.setBorder(BorderFactory.createEmptyBorder(15,15,15,15));setContentPane(content);
        JPanel top=new JPanel(new BorderLayout(5,5));top.add(new JLabel(products.isEmpty()?"Manage shared tags. Select inventory products to assign tags.":products.size()+" selected product(s). Add/remove affects only the chosen tag."),BorderLayout.NORTH);
        search.setToolTipText("Find an existing tag by name");top.add(search);content.add(top,BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);content.add(new JScrollPane(list));
        JPanel bottom=new JPanel(new BorderLayout(5,5));JPanel editor=new JPanel(new FlowLayout(FlowLayout.LEFT));editor.add(new JLabel("Name:"));editor.add(name);editor.add(publicTag);
        bottom.add(editor,BorderLayout.NORTH);bottom.add(actions);bottom.add(status,BorderLayout.SOUTH);content.add(bottom,BorderLayout.SOUTH);
        button("Create Internal tag",()->{JsonObject b=body("CREATE");b.addProperty("name",name.getText());mutate(b);});
        button("Save name / visibility",()->{JsonObject b=selected("SAVE");if(b!=null){b.addProperty("name",name.getText());b.addProperty("isPublic",publicTag.isSelected());mutate(b);}});
        button("Add to selected",()->assign("ADD"));button("Remove from selected",()->assign("REMOVE"));
        button("Merge into…",this::merge);
        list.addListSelectionListener(e->{Tag t=list.getSelectedValue();if(t!=null){name.setText(t.name());publicTag.setSelected(t.publicTag());status.setText(t.assigned()+" of selected products have this tag. "+t.count()+" products total.");}});
        search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){filter();}public void removeUpdate(DocumentEvent e){filter();}public void changedUpdate(DocumentEvent e){filter();}});
        reload();
    }
    private void button(String title,Runnable run){JButton b=new JButton(title);b.addActionListener(e->{if(!busy)run.run();});actions.add(b);}
    private JsonObject body(String action){JsonObject b=new JsonObject();b.addProperty("action",action);return b;}
    private JsonObject selected(String action){Tag t=list.getSelectedValue();if(t==null){status.setText("Choose a tag first.");return null;}JsonObject b=body(action);b.addProperty("tagId",t.id());return b;}
    private void assign(String action){if(products.isEmpty()){status.setText("Select inventory products first.");return;}JsonObject b=selected(action);if(b!=null){b.add("productIds",new Gson().toJsonTree(products));mutate(b);}}
    private void merge(){Tag current=list.getSelectedValue();if(current==null)return;java.util.ArrayList<Tag> options=new java.util.ArrayList<>();for(int i=0;i<model.size();i++)if(!model.get(i).id().equals(current.id()))options.add(model.get(i));
        Tag target=(Tag)JOptionPane.showInputDialog(this,"Move all assignments into the destination tag. Its visibility will apply.","Merge Tags",JOptionPane.QUESTION_MESSAGE,null,options.toArray(),null);
        if(target!=null){JsonObject b=selected("MERGE");b.addProperty("targetId",target.id());mutate(b);}}
    private void mutate(JsonObject body){busy=true;status.setText("Saving…");String key=UUID.randomUUID().toString();
        UiTaskRunner.submit(this,"tags.update",()->LanApiClient.updateProductTags(body,key),r->{busy=false;if(changed!=null)changed.run();reload();},this::failed);}
    private void reload(){busy=true;UiTaskRunner.submit(this,"tags.state",LanApiClient::productTagsState,r->{busy=false;tags=r.getAsJsonArray("tags");assignments=r.getAsJsonArray("assignments");filter();status.setText("New tags are Internal. Public tags also appear on the website.");},this::failed);}
    private void failed(Throwable ex){busy=false;status.setText("Could not complete tag request.");JOptionPane.showMessageDialog(this,ex.getMessage(),"Product Tags",JOptionPane.ERROR_MESSAGE);}
    private void filter(){model.clear();String q=search.getText().strip().toLowerCase(java.util.Locale.ROOT);for(JsonElement e:tags){JsonObject t=e.getAsJsonObject();if(!t.get("name").getAsString().toLowerCase(java.util.Locale.ROOT).contains(q))continue;int assigned=0;
        for(JsonElement a:assignments){JsonObject row=a.getAsJsonObject();if(row.get("tagId").getAsString().equals(t.get("id").getAsString())&&products.contains(row.get("productId").getAsInt()))assigned++;}
        model.addElement(new Tag(t.get("id").getAsString(),t.get("name").getAsString(),t.get("isPublic").getAsBoolean(),t.get("productCount").getAsInt(),assigned));}}
    private record Tag(String id,String name,boolean publicTag,int count,int assigned){public String toString(){return name+"  ["+(publicTag?"Public":"Internal")+"]"+(assigned>0?" — "+assigned+" selected":"");}}
}
