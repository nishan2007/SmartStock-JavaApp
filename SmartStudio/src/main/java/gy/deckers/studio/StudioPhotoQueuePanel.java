package gy.deckers.studio;

import com.google.gson.*;
import services.LanApiClient;
import services.StudioBackgroundService;
import ui.design.DeckersSwing;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.image.BufferedImage;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Shared store queue, independent of any template or computer session. */
final class StudioPhotoQueuePanel extends JPanel {
    record Photo(String id,String name,BufferedImage image,long deleteAfter){}
    private final DefaultTableModel model=new DefaultTableModel(new String[]{"Name / reference","Uploaded","Status"},0){public boolean isCellEditable(int r,int c){return false;}};
    private final JTable table=new JTable(model);
    private final SheetPreview preview=new SheetPreview();
    private final JCheckBox showPrinted=new JCheckBox("Include printed photos");
    private final JLabel status=new JLabel("Upload photos from your phone, then choose who to print next.");
    private final Consumer<Photo> use;
    private final List<JsonObject> rows=new ArrayList<>();
    private final javax.swing.Timer refresh;
    private boolean busy,opening;
    private int generation,selectionVersion;
    private String lastListing="";
    StudioPhotoQueuePanel(Consumer<Photo> use){
        super(new BorderLayout(8,8));this.use=use;setOpaque(false);
        JPanel tools=DeckersSwing.panel();tools.setLayout(new FlowLayout(FlowLayout.LEFT));
        tools.add(StudioWorkspaceShell.button("Pair staff phone / QR…",this::issue));
        tools.add(StudioWorkspaceShell.button("Paired phones…",this::phones));
        tools.add(StudioWorkspaceShell.button("Refresh",this::reload));
        tools.add(StudioWorkspaceShell.button("Use in Passport Pictures",this::open));
        tools.add(StudioWorkspaceShell.button("Use original photo",()->open(true)));
        tools.add(StudioWorkspaceShell.button("Retry Best cutout",this::prepare));
        tools.add(StudioWorkspaceShell.button("Delete selected…",this::delete));tools.add(showPrinted);
        JScrollPane toolbar=new JScrollPane(tools,JScrollPane.VERTICAL_SCROLLBAR_NEVER,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);tools.setLayout(new BoxLayout(tools,BoxLayout.X_AXIS));toolbar.setBorder(null);toolbar.setPreferredSize(new Dimension(300,58));add(toolbar,BorderLayout.NORTH);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setRowHeight(28);table.getAccessibleContext().setAccessibleName("Store photo queue");
        table.setDefaultRenderer(Object.class,new javax.swing.table.DefaultTableCellRenderer(){
            {putClientProperty("html.disable",true);}
            public Component getTableCellRendererComponent(JTable t,Object v,boolean selected,boolean focus,int r,int c){super.getTableCellRendererComponent(t,v,selected,focus,r,c);setText(Objects.toString(v,""));return this;}
        });
        JScrollPane list=new JScrollPane(table);list.setMinimumSize(new Dimension(240,120));
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,list,preview);split.setResizeWeight(.55);split.setDividerLocation(440);add(split);
        JPanel footer=DeckersSwing.panel();footer.setLayout(new GridLayout(0,1));footer.add(new JLabel("Shared across this store's paired computers · Printed photos are removed after 24 hours."));footer.add(status);add(footer,BorderLayout.SOUTH);
        showPrinted.addActionListener(e->{lastListing="";reload();});table.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting())preview();});
        refresh=new javax.swing.Timer(4000,e->reload());
        addHierarchyListener(e->{if((e.getChangeFlags()&HierarchyEvent.SHOWING_CHANGED)!=0){if(isShowing()){reload();refresh.start();}else reset();}});
    }
    void reset(){generation++;selectionVersion++;lastListing="";busy=false;opening=false;refresh.stop();rows.clear();model.setRowCount(0);preview.setImage(null);}
    private String selected(){int row=table.getSelectedRow();return row>=0&&row<rows.size()?rows.get(row).get("id").getAsString():null;}
    private void reload(){
        if(busy||!isShowing())return;busy=true;int version=generation;String selected=selected();
        new SwingWorker<JsonObject,Void>(){
            protected JsonObject doInBackground()throws Exception{return request("LIST",null);}
            protected void done(){if(version!=generation)return;busy=false;try{
                var data=get().getAsJsonArray("photos");if(data.toString().equals(lastListing))return;lastListing=data.toString();rows.clear();model.setRowCount(0);int select=-1;
                for(var entry:data){var row=entry.getAsJsonObject();boolean printed=row.get("printedAt").getAsLong()>0;if(printed&&!showPrinted.isSelected())continue;
                    rows.add(row);String name=row.get("name").getAsString();String state=row.has("backgroundState")?row.get("backgroundState").getAsString():"pending";
                    String readiness=switch(state){case "ready"->"Best cutout ready";case "processing"->"Removing background…";case "failed"->"Original ready · cutout failed";case "disabled"->"Original ready · background removal off";default->"Original ready · Best cutout queued";};
                    model.addRow(new Object[]{name.isBlank()?"Photo "+row.get("id").getAsString().substring(0,8):name,date(row.get("createdAt").getAsLong()),printed?"Printed · deletes "+date(row.get("deleteAfter").getAsLong()):readiness});
                    if(row.get("id").getAsString().equals(selected))select=rows.size()-1;
                }
                if(select>=0)table.setRowSelectionInterval(select,select);else preview.setImage(null);
                status.setText(rows.size()+" photo(s) · Choose who to print next.");
            }catch(Exception ex){status.setText(message(ex));}}
        }.execute();
    }
    private void preview(){
        String id=selected();int selection=++selectionVersion;if(id==null){preview.setImage(null);return;}int version=generation;
        new SwingWorker<Photo,Void>(){
            protected Photo doInBackground()throws Exception{return fetch(id);}
            protected void done(){if(version!=generation||selection!=selectionVersion)return;try{preview.setImage(get().image());}catch(Exception ex){preview.setImage(null);status.setText(message(ex));}}
        }.execute();
    }
    private void open(){
        open(false);
    }
    private void open(boolean original){
        if(opening)return;
        String id=selected();if(id==null){status.setText("Select a queued photo first.");return;}int version=generation;
        opening=true;
        new SwingWorker<Photo,Void>(){
            protected Photo doInBackground()throws Exception{return fetch(id,original);}
            protected void done(){if(version!=generation)return;opening=false;try{use.accept(get());}catch(Exception ex){status.setText(message(ex));}}
        }.execute();
    }
    private void prepare(){
        String id=selected();if(id==null){status.setText("Select a queued photo first.");return;}int version=generation;
        new SwingWorker<JsonObject,Void>(){
            protected JsonObject doInBackground()throws Exception{return request("PREPARE",id);}
            protected void done(){if(version!=generation)return;try{get();lastListing="";reload();}catch(Exception ex){status.setText(message(ex));}}
        }.execute();
    }
    private void delete(){
        String id=selected();if(id==null)return;
        if(JOptionPane.showConfirmDialog(this,"Delete this photo from the shared store queue?","Delete queued photo",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        int version=generation;new SwingWorker<Void,Void>(){
            protected Void doInBackground()throws Exception{request("DELETE",id);return null;}
            protected void done(){if(version!=generation)return;try{get();reload();}catch(Exception ex){status.setText(message(ex));}}
        }.execute();
    }
    private void issue(){
        String phoneName=JOptionPane.showInputDialog(this,"Name this staff phone (for revoking access later)","Pair staff phone",JOptionPane.PLAIN_MESSAGE);if(phoneName==null)return;if(phoneName.isBlank()||phoneName.strip().length()>80){status.setText("Enter a phone name of at most 80 characters.");return;}
        int version=generation;status.setText("Pairing staff phone…");
        new SwingWorker<JsonObject,Void>(){
            protected JsonObject doInBackground()throws Exception{JsonObject body=new JsonObject();body.addProperty("action","PAIR_PHONE");body.addProperty("name",phoneName.strip());return LanApiClient.studioPhotoQueue(body);}
            protected void done(){if(version!=generation)return;try{
                String url=get().get("url").getAsString();JPanel panel=new JPanel(new BorderLayout(8,8));
                var matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(url,com.google.zxing.BarcodeFormat.QR_CODE,280,280);
                BufferedImage image=new BufferedImage(280,280,BufferedImage.TYPE_INT_RGB);for(int y=0;y<280;y++)for(int x=0;x<280;x++)image.setRGB(x,y,matrix.get(x,y)?Color.BLACK.getRGB():Color.WHITE.getRGB());
                panel.add(new JLabel("Scan to upload multiple people. No template needs to be open."),BorderLayout.NORTH);panel.add(new JLabel(new ImageIcon(image)));
                JPanel bottom=new JPanel(new GridLayout(0,1));JTextField link=new JTextField(url);link.setEditable(false);link.setColumns(35);bottom.add(link);bottom.add(new JLabel("Pair once · Return to pic.deckers.gy anytime · Staff can revoke access."));panel.add(bottom,BorderLayout.SOUTH);
                JOptionPane.showMessageDialog(StudioPhotoQueuePanel.this,panel,"Phone photo queue",JOptionPane.PLAIN_MESSAGE);status.setText("Phone uploads appear here automatically. You may close the queue or use another computer.");
            }catch(Exception ex){status.setText(message(ex));}}
        }.execute();
    }
    private void phones(){
        int version=generation;new SwingWorker<JsonObject,Void>(){
            protected JsonObject doInBackground()throws Exception{return request("PHONES",null);}
            protected void done(){if(version!=generation)return;try{
                JsonArray phones=get().getAsJsonArray("phones");if(phones.isEmpty()){status.setText("No staff phones are paired at this store.");return;}
                String[] names=new String[phones.size()];for(int i=0;i<names.length;i++){var phone=phones.get(i).getAsJsonObject();names[i]="Phone: "+phone.get("name").getAsString()+" · "+date(phone.get("issuedAt").getAsLong());}
                JComboBox<String> choice=new JComboBox<>(names);JPanel form=new JPanel(new BorderLayout(8,8));form.add(new JLabel("Revoke upload access for a staff phone"),BorderLayout.NORTH);form.add(choice);
                if(JOptionPane.showConfirmDialog(StudioPhotoQueuePanel.this,form,"Paired phones — revoke access",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION)return;
                String id=phones.get(choice.getSelectedIndex()).getAsJsonObject().get("id").getAsString();
                new SwingWorker<JsonObject,Void>(){protected JsonObject doInBackground()throws Exception{return request("REVOKE_PHONE",id);}protected void done(){if(version!=generation)return;try{get();status.setText("Phone upload access revoked. Existing queued photos are unchanged.");}catch(Exception ex){status.setText(message(ex));}}}.execute();
            }catch(Exception ex){status.setText(message(ex));}}
        }.execute();
    }
    static JsonObject request(String action,String id)throws Exception{JsonObject body=new JsonObject();body.addProperty("action",action);if(id!=null)body.addProperty("id",id);return LanApiClient.studioPhotoQueue(body);}
    private static Photo fetch(String id)throws Exception{
        return fetch(id,false);
    }
    private static Photo fetch(String id,boolean original)throws Exception{
        JsonObject body=new JsonObject();body.addProperty("action","GET");body.addProperty("id",id);body.addProperty("original",original);
        JsonObject row=LanApiClient.studioPhotoQueue(body);byte[] bytes=Base64.getDecoder().decode(row.get("bytesBase64").getAsString());StudioBackgroundService.validate(bytes);
        BufferedImage image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));if(image==null)throw new java.io.IOException("Photo cannot be decoded.");return new Photo(id,row.get("name").getAsString(),image,row.get("deleteAfter").getAsLong());
    }
    private static String date(long millis){return DateTimeFormatter.ofPattern("dd MMM HH:mm").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));}
    private static String message(Exception ex){return "Photo queue: "+(ex.getCause()==null?ex:ex.getCause()).getMessage();}
}
