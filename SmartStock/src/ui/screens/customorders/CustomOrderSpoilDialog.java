package ui.screens.customorders;
import com.google.gson.*;
import services.LanApiClient;
import managers.PermissionManager;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

/** Staff-only history. No customer attachment or proof URLs are created. */
final class CustomOrderSpoilDialog extends JDialog {
    private final long orderId;
    private final DefaultListModel<JsonObject> reports=new DefaultListModel<>();
    private final JList<JsonObject> list=new JList<>(reports);
    private final JLabel status=new JLabel("Loading...");
    static void show(Component parent,long order){var dialog=new CustomOrderSpoilDialog(SwingUtilities.getWindowAncestor(parent),order);dialog.setLocationRelativeTo(parent);dialog.setVisible(true);}
    private CustomOrderSpoilDialog(Window owner,long order){super(owner,"Internal Spoil History",ModalityType.APPLICATION_MODAL);orderId=order;setSize(900,560);setLayout(new BorderLayout(8,8));
        list.setCellRenderer((items,row,index,selected,focus)->{JLabel label=new JLabel("Line "+row.get("lineId").getAsLong()+" | "+row.get("employee").getAsString()+" | "+row.get("createdAt").getAsString()+" | "+row.get("reason").getAsString()+(row.has("reversedAt")&&!row.get("reversedAt").isJsonNull()?" | REVERSED: "+row.get("reversalReason").getAsString():""));label.setOpaque(true);label.setBackground(selected?items.getSelectionBackground():items.getBackground());label.setForeground(selected?items.getSelectionForeground():items.getForeground());return label;});add(new JScrollPane(list),BorderLayout.CENTER);
        JPanel actions=new JPanel();JButton photos=new JButton("View Photos"),reverse=new JButton("Reverse Spoil"),close=new JButton("Close");actions.add(photos);if(PermissionManager.hasPermission("REVERSE_CUSTOM_ORDER_SPOILS"))actions.add(reverse);actions.add(close);add(actions,BorderLayout.SOUTH);add(status,BorderLayout.NORTH);
        photos.addActionListener(e->photos());reverse.addActionListener(e->reverse());close.addActionListener(e->dispose());refresh();
    }
    private JsonObject request(String action){JsonObject body=new JsonObject();body.addProperty("action",action);body.addProperty("orderId",orderId);return body;}
    private void refresh(){new SwingWorker<JsonObject,Void>(){protected JsonObject doInBackground()throws Exception{return LanApiClient.customOrderMediaRead(request("SPOILS"));}protected void done(){try{JsonObject data=get();reports.clear();for(var row:data.getAsJsonArray("reports"))reports.addElement(row.getAsJsonObject());status.setText(data.getAsJsonObject("order").get("number").getAsString()+" / "+reports.size()+" spoil report(s)");}catch(Exception e){error(e);}}}.execute();}
    private void photos(){var row=list.getSelectedValue();if(row==null)return;new SwingWorker<java.util.List<ImageIcon>,Void>(){protected java.util.List<ImageIcon> doInBackground()throws Exception{var result=new ArrayList<ImageIcon>();for(var entry:row.getAsJsonArray("photos")){JsonObject body=request("SPOIL_PHOTO");body.addProperty("photoId",entry.getAsJsonObject().get("id").getAsString());var data=LanApiClient.customOrderMediaRead(body);var image=ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(data.get("bytesBase64").getAsString())));double scale=Math.min(1,640d/Math.max(image.getWidth(),image.getHeight()));result.add(new ImageIcon(image.getScaledInstance((int)(image.getWidth()*scale),(int)(image.getHeight()*scale),Image.SCALE_SMOOTH)));}return result;}protected void done(){try{JPanel panel=new JPanel();panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));for(var image:get())panel.add(new JLabel(image));JScrollPane scroll=new JScrollPane(panel);scroll.setPreferredSize(new Dimension(700,600));JOptionPane.showMessageDialog(CustomOrderSpoilDialog.this,scroll,"Internal Spoil Photos",JOptionPane.PLAIN_MESSAGE);}catch(Exception e){error(e);}}}.execute();}
    private void reverse(){var row=list.getSelectedValue();if(row==null)return;if(row.has("reversedAt")&&!row.get("reversedAt").isJsonNull()){JOptionPane.showMessageDialog(this,"This spoil is already reversed.");return;}String reason=JOptionPane.showInputDialog(this,"Reason for reversal (replacement stock will be restored):");if(reason==null||reason.isBlank())return;JsonObject body=request("REVERSE_SPOIL");body.addProperty("reportId",row.get("id").getAsString());body.addProperty("reason",reason);new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{LanApiClient.customOrderMediaMutation(body);return null;}protected void done(){try{get();refresh();}catch(Exception e){error(e);}}}.execute();}
    private void error(Exception e){Throwable root=e;while(root.getCause()!=null)root=root.getCause();status.setText("Action failed.");JOptionPane.showMessageDialog(this,root.getMessage(),"Spoils",JOptionPane.ERROR_MESSAGE);}
}
