package gy.deckers.studio;

import com.google.gson.JsonObject;
import services.LanApiClient;
import ui.design.DeckersSwing;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Opens canonical SMB files; no downloaded template cache is used. */
final class SmartDriveTemplatePicker {
    private static volatile Path lastRoot;
    private static volatile String rootServer;
    static Path networkRoot(String value)throws IOException {
        if(value==null||!value.matches("\\\\\\\\[A-Za-z][A-Za-z0-9-]{0,14}\\\\[A-Za-z0-9][A-Za-z0-9 _-]{0,79}"))
            throw new IOException("Invalid SmartDrive share root.");
        return Path.of(value);
    }
    static Path suggestedRoot(){return String.valueOf(LanApiClient.baseUri()).equals(rootServer)?lastRoot:null;}
    record Entry(String name,String store,Path file,publishing.StudioDriveIdentity identity) {
        @Override public String toString(){return name+" · Store "+store;}
    }
    record Selection(boolean enabled,boolean current,Path file,publishing.StudioDriveIdentity identity) {
        Selection(boolean enabled,boolean current,Path file){this(enabled,current,file,null);}
    }
    static List<Entry> entries(JsonObject catalog,String kind)throws IOException {
        var result=new ArrayList<Entry>();
        if(!catalog.has("templates") || !catalog.get("templates").isJsonArray())throw new IOException("Invalid SmartDrive catalog.");
        if(catalog.getAsJsonArray("templates").size()>10000)throw new IOException("SmartDrive catalog exceeds 10000 designs.");
        String extension=kind.equals("BADGE")?".sstbadge":".sspub";
        for(var value:catalog.getAsJsonArray("templates")) {
            var row=value.getAsJsonObject();
            String name=row.get("name").getAsString(),store=row.get("ownerStoreId").getAsString(),network=row.get("networkPath").getAsString();
            if(!row.get("kind").getAsString().equals(kind) || !store.matches("[1-9][0-9]*") ||
                    name.isBlank() || name.length()>512 || !network.startsWith("\\\\") || network.startsWith("\\\\?\\") || network.startsWith("\\\\.\\"))
                throw new IOException("Invalid SmartDrive design path.");
            String[] parts=network.substring(2).split("\\\\",-1);
            if(parts.length<4 || !parts[0].matches("[A-Za-z][A-Za-z0-9-]{0,14}") || !parts[2].equals(store) ||
                    !network.toLowerCase(java.util.Locale.ROOT).endsWith(extension))throw new IOException("Invalid SmartDrive design path.");
            for(String part:parts)if(part.isBlank() || part.equals(".") || part.equals("..") || part.matches(".*[<>:\"/|?*\\p{Cntrl}].*") || part.endsWith(".") || part.endsWith(" "))
                throw new IOException("Invalid SmartDrive design path.");
            publishing.StudioDriveIdentity identity;
            try { identity=new publishing.StudioDriveIdentity(row.get("id").getAsString(),row.get("storageStoreId").getAsString(),kind); }
            catch(RuntimeException invalid){throw new IOException("Invalid SmartDrive template identity.",invalid);}
            result.add(new Entry(name,store,Path.of(network),identity));
        }
        return List.copyOf(result);
    }
    static Selection choose(Component owner,String kind)throws Exception {
        Window parent=SwingUtilities.getWindowAncestor(owner);
        JDialog loading=new JDialog(parent,"SmartDrive",Dialog.ModalityType.APPLICATION_MODAL);
        loading.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        loading.add(new JLabel("Loading shared designs…",SwingConstants.CENTER));loading.setSize(320,100);loading.setLocationRelativeTo(owner);
        var worker=new SwingWorker<JsonObject,Void>() {
            protected JsonObject doInBackground()throws Exception{return LanApiClient.studioDriveCatalog(kind);}
            protected void done(){loading.dispose();}
        };
        worker.execute();loading.setVisible(true);
        JsonObject catalog=worker.get();
        if(!catalog.get("enabled").getAsBoolean())return new Selection(false,false,null);
        lastRoot=catalog.has("networkRoot")?networkRoot(catalog.get("networkRoot").getAsString()):null;
        rootServer=String.valueOf(LanApiClient.baseUri());
        List<Entry> entries=entries(catalog,kind);
        DefaultListModel<Entry> model=new DefaultListModel<>();entries.forEach(model::addElement);
        JList<Entry> list=new JList<>(model);list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JTextField search=new JTextField();search.setToolTipText("Search shared designs");search.getAccessibleContext().setAccessibleName("Search shared designs");
        search.getDocument().addDocumentListener(new DocumentListener(){
            private void refresh(){String text=search.getText().strip().toLowerCase(java.util.Locale.ROOT);model.clear();entries.stream().filter(e->e.toString().toLowerCase(java.util.Locale.ROOT).contains(text)).forEach(model::addElement);}
            public void insertUpdate(DocumentEvent e){refresh();}public void removeUpdate(DocumentEvent e){refresh();}public void changedUpdate(DocumentEvent e){refresh();}
        });
        JPanel content=DeckersSwing.panel();content.setLayout(new BorderLayout(8,8));
        content.add(search,BorderLayout.NORTH);content.add(new JScrollPane(list));
        content.add(new JLabel(entries.isEmpty()?"No shared designs are available for your stores.":"Opens the shared file directly. This computer must be enrolled for drive access."),BorderLayout.SOUTH);
        content.setPreferredSize(new Dimension(680,400));
        JOptionPane pane=new JOptionPane(content,JOptionPane.PLAIN_MESSAGE,JOptionPane.DEFAULT_OPTION);
        JButton open=StudioWorkspaceShell.button("Open selected",()->pane.setValue(1));open.setEnabled(false);
        list.addListSelectionListener(e->open.setEnabled(list.getSelectedValue()!=null));
        pane.setOptions(new Object[]{open,StudioWorkspaceShell.button("Continue current design",()->pane.setValue(2)),StudioWorkspaceShell.button("Cancel",()->pane.setValue(0))});
        JDialog dialog=pane.createDialog(owner,"SmartDrive shared designs");dialog.setResizable(true);dialog.getRootPane().setDefaultButton(open);
        try{dialog.setVisible(true);}finally{dialog.dispose();}
        if(Integer.valueOf(2).equals(pane.getValue()))return new Selection(true,true,null);
        Entry selected=Integer.valueOf(1).equals(pane.getValue())?list.getSelectedValue():null;
        return new Selection(true,false,selected==null?null:selected.file(),selected==null?null:selected.identity());
    }
}
