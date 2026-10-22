package ui.screens;

import com.google.gson.*;
import services.LanApiClient;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;

/** Active-server controls. The server independently enforces administrator permission. */
public final class EmployeeWebDialog extends JDialog {
    private final JLabel state=new JLabel("Loading…"),qr=new JLabel("Generate a QR to activate a browser",SwingConstants.CENTER);
    private final JTextField address=new JTextField(),name=new JTextField("Employee phone",18);
    private final JButton start=new JButton("Start"),stop=new JButton("Stop"),issue=new JButton("Generate Activation QR"),revoke=new JButton("Revoke Browser"),refresh=new JButton("Refresh");
    private final DefaultTableModel model=new DefaultTableModel(new String[]{"Browser","Last used","Status"},0){@Override public boolean isCellEditable(int r,int c){return false;}};
    private final JTable table=new JTable(model);
    private JsonArray devices=new JsonArray();
    private final Timer timer=new Timer(4000,e->load());
    private boolean loading;
    private Instant expires;
    private final boolean orders;

    public EmployeeWebDialog(Window owner) {
        this(owner,false);
    }
    public EmployeeWebDialog(Window owner,boolean orders) {
        super(owner,orders?"Pair Orders Device":"Employee Web App",ModalityType.MODELESS);
        this.orders=orders;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(820,720);setMinimumSize(new Dimension(650,600));setLocationRelativeTo(owner);
        JPanel root=new JPanel(new BorderLayout(12,12));root.setBorder(new EmptyBorder(16,16,16,16));
        JPanel heading=new JPanel(new GridLayout(0,1,6,6));
        heading.add(new JLabel(orders?"Orders Management — approved browsers, access from anywhere":"Employee Web App — approved browsers"));heading.add(state);address.setEditable(false);heading.add(address);
        heading.add(new JLabel("Activation permits this browser. Employees must still sign in with their own account."));root.add(heading,BorderLayout.NORTH);
        JPanel center=new JPanel(new BorderLayout(8,8));JPanel pairing=new JPanel(new BorderLayout(6,6));
        JPanel pairActions=new JPanel(new FlowLayout(FlowLayout.LEFT));pairActions.add(new JLabel("Browser name:"));pairActions.add(name);pairActions.add(issue);
        pairing.add(pairActions,BorderLayout.NORTH);qr.setPreferredSize(new Dimension(280,280));pairing.add(qr,BorderLayout.CENTER);center.add(pairing,BorderLayout.NORTH);
        table.setRowHeight(28);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);center.add(new JScrollPane(table),BorderLayout.CENTER);root.add(center,BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));actions.add(refresh);actions.add(revoke);actions.add(start);actions.add(stop);
        JButton close=new JButton("Close");close.addActionListener(e->dispose());actions.add(close);root.add(actions,BorderLayout.SOUTH);setContentPane(root);
        start.addActionListener(e->mutate("START",null));stop.addActionListener(e->mutate("STOP",null));refresh.addActionListener(e->load());
        issue.addActionListener(e->issue());revoke.addActionListener(e->revoke());timer.start();load();
    }
    private void load() {
        if(loading)return;loading=true;
        new SwingWorker<JsonObject[],Void>() {
            @Override protected JsonObject[] doInBackground()throws Exception{return new JsonObject[]{LanApiClient.employeeWebAdmin("STATUS",null),LanApiClient.employeeWebAdmin("LIST",null)};}
            @Override protected void done(){loading=false;try{
                JsonObject[] result=get();JsonObject s=result[0];boolean running=s.get("running").getAsBoolean(),enabled=s.get("enabled").getAsBoolean();
                state.setText(running?"Running — activation QR codes last 10 minutes":enabled?"Starting gateway…":"Stopped");address.setText(orders?"https://ordersmanagement.deckers.gy":s.get("url").getAsString());
                start.setEnabled(!enabled);stop.setEnabled(enabled);issue.setEnabled(running);
                String selected=selectedDevice();devices=result[1].getAsJsonArray("devices");model.setRowCount(0);
                for(int i=0;i<devices.size();i++){JsonObject d=devices.get(i).getAsJsonObject();model.addRow(new Object[]{d.get("name").getAsString(),d.get("lastSeenAt").getAsString(),d.get("revoked").getAsBoolean()?"Revoked":"Active"});if(d.get("deviceId").getAsString().equals(selected))table.setRowSelectionInterval(i,i);}
                if(!running||expires!=null&&!expires.isAfter(Instant.now()))clearQr();
            }catch(Exception e){state.setText("Unavailable: "+root(e));issue.setEnabled(false);clearQr();}}
        }.execute();
    }
    private void issue(){JsonObject b=new JsonObject();b.addProperty("name",name.getText());if(orders)b.addProperty("workspace","orders");issue.setEnabled(false);
        new SwingWorker<JsonObject,Void>(){
            @Override protected JsonObject doInBackground()throws Exception{return LanApiClient.employeeWebAdmin("ISSUE",b);}
            @Override protected void done(){try{JsonObject r=get();expires=Instant.parse(r.get("expiresAt").getAsString());qr.setIcon(new ImageIcon(SchedulerWebDialog.qrCode(r.get("url").getAsString(),270)));qr.setText("Scan once; expires in 10 minutes");}catch(Exception e){error(e);}finally{load();}}
        }.execute();
    }
    private String selectedDevice(){int i=table.getSelectedRow();return i<0||i>=devices.size()?null:devices.get(i).getAsJsonObject().get("deviceId").getAsString();}
    private void revoke(){String id=selectedDevice();if(id==null)return;JsonObject b=new JsonObject();b.addProperty("deviceId",id);mutate("REVOKE",b);}
    private void mutate(String action,JsonObject b){start.setEnabled(false);stop.setEnabled(false);revoke.setEnabled(false);
        new SwingWorker<Void,Void>(){
            @Override protected Void doInBackground()throws Exception{LanApiClient.employeeWebAdmin(action,b);return null;}
            @Override protected void done(){try{get();if(action.equals("STOP"))clearQr();}catch(Exception e){error(e);}finally{revoke.setEnabled(true);load();}}
        }.execute();
    }
    private void clearQr(){qr.setIcon(null);qr.setText("Generate a new QR to activate a browser");expires=null;}
    private void error(Exception e){JOptionPane.showMessageDialog(this,root(e),"Employee Web App",JOptionPane.ERROR_MESSAGE);}
    private static String root(Throwable e){while(e.getCause()!=null)e=e.getCause();return e.getMessage()==null?"Request failed":e.getMessage();}
    @Override public void dispose(){timer.stop();super.dispose();}
}
