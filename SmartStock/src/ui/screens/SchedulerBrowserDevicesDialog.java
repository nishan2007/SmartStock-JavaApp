package ui.screens;

import services.LanApiClient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/** Enables remembered Scheduler login only for browsers selected by an administrator. */
final class SchedulerBrowserDevicesDialog extends JDialog {
    private static final DateTimeFormatter WHEN=DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a").withZone(ZoneId.systemDefault());
    private final DefaultTableModel model=new DefaultTableModel(new Object[]{"Browser","Employee","Stay signed in","Expires","Last used"},0){@Override public boolean isCellEditable(int r,int c){return false;}};
    private final JTable table=new JTable(model);
    private final JLabel status=new JLabel("Loading Scheduler devices…");
    private final JButton toggle=new JButton("Enable Stay Signed In"),refresh=new JButton("Refresh");
    private List<LanApiClient.SchedulerBrowserDevice> devices=List.of();

    SchedulerBrowserDevicesDialog(Window owner){super(owner,"Scheduler Web Devices",ModalityType.MODELESS);setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(820,430);setLocationRelativeTo(owner);build();wire();load();}
    private void build(){JPanel root=new JPanel(new BorderLayout(10,10));root.setBorder(new EmptyBorder(16,16,16,16));JTextArea help=new JTextArea("Browsers appear here automatically after a successful Scheduler login. Access does not require approval. Enable Stay Signed In only for a trusted phone or computer; it lasts 30 days and remains tied to the listed employee.");help.setEditable(false);help.setOpaque(false);help.setLineWrap(true);help.setWrapStyleWord(true);root.add(help,BorderLayout.NORTH);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setRowHeight(28);root.add(new JScrollPane(table),BorderLayout.CENTER);JPanel bottom=new JPanel(new BorderLayout());bottom.add(status,BorderLayout.WEST);JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));buttons.add(refresh);buttons.add(toggle);JButton close=new JButton("Close");close.addActionListener(e->dispose());buttons.add(close);bottom.add(buttons,BorderLayout.EAST);root.add(bottom,BorderLayout.SOUTH);setContentPane(root);toggle.setEnabled(false);}
    private void wire(){refresh.addActionListener(e->load());table.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting())selection();});toggle.addActionListener(e->toggle());}
    private void load(){busy(true);new SwingWorker<List<LanApiClient.SchedulerBrowserDevice>,Void>(){@Override protected List<LanApiClient.SchedulerBrowserDevice>doInBackground()throws Exception{return LanApiClient.loadSchedulerBrowserDevices();}@Override protected void done(){try{devices=get();render();status.setText(devices.size()+" Scheduler browser(s)");}catch(Exception e){status.setText("Could not load devices: "+root(e));}finally{busy(false);}}}.execute();}
    private void render(){model.setRowCount(0);for(var d:devices)model.addRow(new Object[]{d.deviceName(),d.employeeName()+" ("+d.username()+")",d.staySignedIn()?"On":"Off",when(d.expiresAt()),when(d.lastSeenAt())});selection();}
    private void selection(){int row=table.getSelectedRow();boolean selected=row>=0&&row<devices.size();toggle.setEnabled(selected);if(selected)toggle.setText(devices.get(row).staySignedIn()?"Turn Off Stay Signed In":"Enable Stay Signed In");}
    private void toggle(){int row=table.getSelectedRow();if(row<0||row>=devices.size())return;var d=devices.get(row);boolean enable=!d.staySignedIn();String message=enable?"Allow this browser to restore "+d.employeeName()+" without another password for 30 days?":"Turn off saved login for this browser and end its active Scheduler sessions?";if(JOptionPane.showConfirmDialog(this,message,"Scheduler Device",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;busy(true);new SwingWorker<Void,Void>(){@Override protected Void doInBackground()throws Exception{LanApiClient.updateSchedulerBrowserDevice(d.deviceId(),enable,UUID.randomUUID().toString());return null;}@Override protected void done(){try{get();load();}catch(Exception e){busy(false);JOptionPane.showMessageDialog(SchedulerBrowserDevicesDialog.this,root(e),"Could Not Update Device",JOptionPane.ERROR_MESSAGE);}}}.execute();}
    private void busy(boolean value){refresh.setEnabled(!value);if(value)toggle.setEnabled(false);else selection();}
    private static String when(String value){if(value==null||value.isBlank())return "—";try{return WHEN.format(Instant.parse(value));}catch(Exception e){return value;}}
    private static String root(Throwable e){Throwable x=e;while(x.getCause()!=null)x=x.getCause();return x.getMessage()==null?x.getClass().getSimpleName():x.getMessage();}
}
