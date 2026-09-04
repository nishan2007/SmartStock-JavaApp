package ui.screens;

import com.google.gson.JsonObject;
import services.LanApiClient;
import ui.helpers.ResponsiveTask;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.UUID;

/** Administrator settings for the store-server WhatsApp Cloud API integration. */
final class WhatsAppSettingsDialog extends JDialog {
    private final int locationId;
    private final JCheckBox enabled=new JCheckBox("Enable WhatsApp sales documents");
    private final JTextField phoneId=new JTextField();
    private final JTextField apiVersion=new JTextField("v23.0");
    private final JComboBox<String> mode=new JComboBox<>(new String[]{"FULL","COMPACT","REFERENCE"});
    private final JTextField language=new JTextField("en_US");
    private final JTextField price=new JTextField("0.05");
    private final JTextField budget=new JTextField("5.00");
    private final JTextField contact=new JTextField();
    private final JTextArea templates=new JTextArea(9,36);
    WhatsAppSettingsDialog(Window owner,int locationId){super(owner,"WhatsApp Settings",ModalityType.APPLICATION_MODAL);this.locationId=locationId;setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(650,690);setLocationRelativeTo(owner);build();load();}
    private void build(){JPanel root=new JPanel(new BorderLayout(10,10));root.setBorder(new EmptyBorder(14,14,14,14));JPanel form=new JPanel(new GridBagLayout());GridBagConstraints g=new GridBagConstraints();g.insets=new Insets(4,4,4,4);g.fill=GridBagConstraints.HORIZONTAL;g.weightx=1;int r=0;row(form,g,r++,"Status",enabled);row(form,g,r++,"Cloud API phone number ID",phoneId);row(form,g,r++,"Graph API version",apiVersion);row(form,g,r++,"Message detail",mode);row(form,g,r++,"Template language",language);row(form,g,r++,"Estimated USD per message",price);row(form,g,r++,"Monthly budget warning (USD)",budget);row(form,g,r++,"Customer contact line",contact);templates.setLineWrap(true);templates.setWrapStyleWord(true);row(form,g,r++,"Approved template map (JSON)",new JScrollPane(templates));JLabel help=new JLabel("<html>The server token must be set as SMARTSTOCK_WHATSAPP_ACCESS_TOKEN.<br>Template keys use DOCUMENT_MODE, for example SALE_RECEIPT_FULL.</html>");help.setForeground(new Color(90,90,90));root.add(form,BorderLayout.CENTER);root.add(help,BorderLayout.NORTH);JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));JButton save=new JButton("Save");JButton close=new JButton("Close");buttons.add(save);buttons.add(close);root.add(buttons,BorderLayout.SOUTH);save.addActionListener(e->save());close.addActionListener(e->dispose());setContentPane(root);}
    private static void row(JPanel p,GridBagConstraints g,int y,String label,Component field){g.gridy=y;g.gridx=0;g.weightx=0;p.add(new JLabel(label+":"),g);g.gridx=1;g.weightx=1;p.add(field,g);}
    private void load(){try{JsonObject s=ResponsiveTask.await(this,"Loading WhatsApp settings...",()->LanApiClient.loadWhatsAppConfiguration(locationId));if(s==null)return;enabled.setSelected(s.get("enabled").getAsBoolean());phoneId.setText(s.get("phoneNumberId").getAsString());apiVersion.setText(s.get("apiVersion").getAsString());mode.setSelectedItem(s.get("messageMode").getAsString());language.setText(s.get("language").getAsString());price.setText(s.get("estimatedPrice").getAsString());budget.setText(s.get("monthlyBudget").getAsString());templates.setText(s.get("templates").getAsString());contact.setText(s.get("contactLine").getAsString());if(!s.get("tokenConfigured").getAsBoolean())JOptionPane.showMessageDialog(this,"The server access token is not configured yet.","WhatsApp",JOptionPane.WARNING_MESSAGE);}catch(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"WhatsApp Settings",JOptionPane.ERROR_MESSAGE);}}
    private void save(){try{JsonObject s=new JsonObject();s.addProperty("enabled",enabled.isSelected());s.addProperty("phoneNumberId",phoneId.getText().trim());s.addProperty("apiVersion",apiVersion.getText().trim());s.addProperty("messageMode",String.valueOf(mode.getSelectedItem()));s.addProperty("language",language.getText().trim());s.addProperty("estimatedPrice",new java.math.BigDecimal(price.getText().trim()));s.addProperty("monthlyBudget",new java.math.BigDecimal(budget.getText().trim()));s.addProperty("templates",templates.getText().trim());s.addProperty("contactLine",contact.getText().trim());ResponsiveTask.await(this,"Saving WhatsApp settings...",()->{LanApiClient.saveWhatsAppConfiguration(locationId,s,UUID.randomUUID().toString());return true;});JOptionPane.showMessageDialog(this,"WhatsApp settings saved.");}catch(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"WhatsApp Settings",JOptionPane.ERROR_MESSAGE);}}
}
