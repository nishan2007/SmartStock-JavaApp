package ui.screens;

import com.google.gson.JsonObject;
import managers.PermissionManager;
import managers.SessionManager;
import services.LanApiClient;
import services.PcscNfcService;
import services.StoreKeys;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import ui.helpers.UiTaskRunner;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;

/** Store-local custody screen. The logged-in session is the actor; assisted actions retain both identities. */
public final class StoreKeysDialog extends JDialog {
    private final int location;
    private final Integer sessionUser=SessionManager.getCurrentUserId();
    private final DefaultTableModel model=tableModel("Key","Label","Status","Holder","Checked out");
    private final JTable table=new JTable(model);
    private final JLabel status=new JLabel("Loading store keys…");
    private final JLabel identity=new JLabel("Signed in: "+SessionManager.getCurrentUserDisplayName());
    private final JCheckBox assisted=new JCheckBox("Record for employee:");
    private final JComboBox<StoreKeys.Employee> employees=new JComboBox<>();
    private final List<JButton> buttons=new ArrayList<>();
    private final JButton retry=new JButton("Retry last save");
    private StoreKeys.Dashboard dashboard;
    private boolean busy;
    private String scannedToken;
    private UUID scannedKey;
    private Pending pending;
    private record Pending(String action,JsonObject body,String requestId) { }

    public StoreKeysDialog(Window owner,int location) {
        super(owner,"Store Keys",ModalityType.MODELESS);this.location=location;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(1050,650);setMinimumSize(new Dimension(800,480));setLocationRelativeTo(owner);
        JPanel root=new JPanel(new BorderLayout(10,10));root.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JPanel header=new JPanel(new BorderLayout());header.add(identity,BorderLayout.NORTH);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT));
        button(actions,"Refresh",()->refresh());button(actions,"Tap key card",this::scan);
        if(can("STORE_KEYS_SELF")||can("STORE_KEYS_ASSIST")){button(actions,"Sign out key",()->custody(false));button(actions,"Return key",()->custody(true));}
        if(can("STORE_KEYS_FORCE_RETURN"))button(actions,"Recover key",this::recover);
        if(can("STORE_KEYS_HISTORY")||can("STORE_KEYS_SELF"))button(actions,"History",this::history);
        header.add(actions,BorderLayout.CENTER);
        if(can("STORE_KEYS_ASSIST")) {
            JPanel staff=new JPanel(new FlowLayout(FlowLayout.LEFT));staff.add(assisted);employees.setPreferredSize(new Dimension(240,30));staff.add(employees);header.add(staff,BorderLayout.SOUTH);
        }
        DeckersSwing.styleBand(header,DeckersPalette.YELLOW,new Insets(8,10,8,10));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setAutoCreateRowSorter(true);DeckersSwing.styleTable(table,DeckersPalette.YELLOW);
        table.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&selected()!=null&&!selected().keyId().equals(scannedKey)){scannedToken=null;scannedKey=null;}});
        root.add(header,BorderLayout.NORTH);root.add(new JScrollPane(table),BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout());footer.add(status,BorderLayout.CENTER);
        JPanel config=new JPanel(new FlowLayout(FlowLayout.LEFT));
        if(can("STORE_KEYS_CONFIGURE")) {
            button(config,"Set key count",this::count);button(config,"Rename",this::rename);
            button(config,"Pair NFC card",this::pair);button(config,"Retire key",this::retire);
        }
        retry.setEnabled(false);retry.addActionListener(e->{if(pending!=null)savePending();});config.add(retry);
        footer.add(config,BorderLayout.SOUTH);root.add(footer,BorderLayout.SOUTH);setContentPane(root);refresh();
    }
    private boolean can(String p){return PermissionManager.hasPermission(p);}
    private static DefaultTableModel tableModel(String... columns){return new DefaultTableModel(columns,0){@Override public boolean isCellEditable(int r,int c){return false;}};}
    private void button(JPanel panel,String text,Runnable action){JButton b=new JButton(text);b.addActionListener(e->action.run());buttons.add(b);panel.add(b);}
    private boolean sameSession(){return Objects.equals(sessionUser,SessionManager.getCurrentUserId())&&Objects.equals(location,SessionManager.getCurrentLocationId());}
    private <T> void work(String message,Callable<T> task,java.util.function.Consumer<T> success) {
        if(busy)return;if(!sameSession()){error(new IllegalStateException("The signed-in employee or store changed. Reopen Store Keys."));return;}
        setBusy(true);status.setText(message);
        UiTaskRunner.submit(this,"store-keys",task,result->{setBusy(false);success.accept(result);},e->{setBusy(false);status.setText(pending==null?"Could not complete action.":"Save not confirmed. Retry the same save, or refresh to check the server.");error(e);});
    }
    private void setBusy(boolean value){busy=value;for(JButton b:buttons)b.setEnabled(!value);employees.setEnabled(!value);assisted.setEnabled(!value);retry.setEnabled(!value&&pending!=null);table.setEnabled(!value);}
    private void error(Throwable e){JOptionPane.showMessageDialog(this,e.getMessage(),"Store Keys",JOptionPane.ERROR_MESSAGE);}
    private void refresh(){work("Loading store keys…",()->LanApiClient.loadStoreKeys(location),d->{
        dashboard=d;pending=null;retry.setEnabled(false);scannedToken=null;scannedKey=null;model.setRowCount(0);
        for(StoreKeys.Key k:d.keys())model.addRow(new Object[]{k.keyNumber(),k.label(),k.status(),k.holderName()==null?(k.custodyId()==null?"":"Another employee"):k.holderName(),time(k.checkedOutAt())});
        StoreKeys.Employee old=(StoreKeys.Employee)employees.getSelectedItem();employees.removeAllItems();for(var employee:d.employees())employees.addItem(employee);
        if(old!=null)for(int i=0;i<employees.getItemCount();i++)if(employees.getItemAt(i).userId()==old.userId())employees.setSelectedIndex(i);
        long available=d.keys().stream().filter(k->"Available".equals(k.status())).count(),out=d.keys().stream().filter(k->k.custodyId()!=null).count();
        status.setText(available+" available · "+out+" checked out · NFC tags optional");
    });}
    private StoreKeys.Key selected(){int row=table.getSelectedRow();if(dashboard==null||row<0)return null;return dashboard.keys().get(table.convertRowIndexToModel(row));}
    private StoreKeys.Key needKey(){StoreKeys.Key k=selected();if(k==null)JOptionPane.showMessageDialog(this,"Select a key first.");return k;}
    private JsonObject request(StoreKeys.Key k){JsonObject b=new JsonObject();b.addProperty("locationId",location);if(k!=null){b.addProperty("keyId",k.keyId().toString());b.addProperty("version",k.version());if(k.custodyId()!=null)b.addProperty("custodyId",k.custodyId().toString());if(k.keyId().equals(scannedKey)&&scannedToken!=null)b.addProperty("cardToken",scannedToken);}return b;}
    private boolean confirm(String text){return JOptionPane.showConfirmDialog(this,text,"Confirm key transaction",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION;}
    private void save(String action,JsonObject b){if(pending!=null){JOptionPane.showMessageDialog(this,"Retry the unconfirmed save or refresh before starting another transaction.");return;}pending=new Pending(action,b.deepCopy(),UUID.randomUUID().toString());savePending();}
    private void savePending(){Pending p=pending;work("Saving key transaction…",()->{LanApiClient.changeStoreKey(p.action(),p.body(),p.requestId());return true;},ok->{pending=null;refresh();});}
    private void custody(boolean returning){
        StoreKeys.Key k=needKey();if(k==null)return;
        if(returning&&k.custodyId()==null){JOptionPane.showMessageDialog(this,"This key is not checked out.");return;}
        if(!returning&&!"Available".equals(k.status())){JOptionPane.showMessageDialog(this,"Choose an available key.");return;}
        Integer target=sessionUser;
        if(assisted.isSelected()){
            var employee=(StoreKeys.Employee)employees.getSelectedItem();if(employee==null)return;target=employee.userId();
            if(returning&&!Objects.equals(k.holderUserId(),target)){JOptionPane.showMessageDialog(this,"Select the employee who currently holds this key.");return;}
        }else if(returning&&!Objects.equals(k.holderUserId(),sessionUser)){JOptionPane.showMessageDialog(this,"Only return your own key, or record a manager-assisted return.");return;}
        String name=assisted.isSelected()?Objects.toString(employees.getSelectedItem()):SessionManager.getCurrentUserDisplayName();
        if(!confirm((returning?"Return ":"Sign out ")+k.label()+" for "+name+"?"))return;
        JsonObject b=request(k);if(!returning)b.addProperty("employeeId",target);save(returning?"return":"checkout",b);
    }
    private void recover(){StoreKeys.Key k=needKey();if(k==null)return;if(k.custodyId()==null){JOptionPane.showMessageDialog(this,"This key is not checked out.");return;}
        JTextArea reason=new JTextArea(4,35);reason.setLineWrap(true);reason.setWrapStyleWord(true);JCheckBox recovered=new JCheckBox("I have physically recovered this key.");
        JPanel panel=new JPanel(new BorderLayout(8,8));panel.add(new JLabel("Recover "+k.label()+" from "+k.holderName()+". Enter the reason:"),BorderLayout.NORTH);panel.add(new JScrollPane(reason));panel.add(recovered,BorderLayout.SOUTH);
        if(JOptionPane.showConfirmDialog(this,panel,"Return by management",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        if(reason.getText().isBlank()||!recovered.isSelected()){JOptionPane.showMessageDialog(this,"A reason and confirmation of physical recovery are required.");return;}
        JsonObject b=request(k);b.remove("cardToken");b.addProperty("reason",reason.getText().trim());b.addProperty("recovered",true);save("force-return",b);
    }
    private void scan(){work("Tap the NFC card attached to the key…",()->{String token=PcscNfcService.read(Duration.ofSeconds(20)).payload();UUID key=LanApiClient.resolveStoreKey(location,token);return Map.entry(key,token);},result->{
        for(int i=0;i<dashboard.keys().size();i++)if(dashboard.keys().get(i).keyId().equals(result.getKey())){int row=table.convertRowIndexToView(i);table.setRowSelectionInterval(row,row);scannedKey=result.getKey();scannedToken=result.getValue();status.setText("Key card recognized. Choose Sign out or Return and confirm.");return;}
        status.setText("Key list changed. Refresh and tap again.");
    });}
    private void count(){if(dashboard==null)return;int current=(int)dashboard.keys().stream().filter(k->!k.retired()).count();
        String value=JOptionPane.showInputDialog(this,"Number of active keys (currently "+current+"). To reduce, retire selected keys first:",current);if(value==null)return;
        try{int desired=Integer.parseInt(value.trim());if(desired<current||desired>1000)throw new NumberFormatException();JsonObject b=request(null);b.addProperty("count",desired);b.addProperty("expectedCount",current);save("count",b);}catch(NumberFormatException e){JOptionPane.showMessageDialog(this,"Enter a whole number from "+current+" to 1000. Retire returned keys individually to reduce the count.");}
    }
    private void rename(){StoreKeys.Key k=needKey();if(k==null)return;String label=JOptionPane.showInputDialog(this,"Key label:",k.label());if(label==null||label.isBlank())return;JsonObject b=request(k);b.addProperty("label",label.trim());save("label",b);}
    private void pair(){StoreKeys.Key k=needKey();if(k==null)return;if(k.retired()||k.custodyId()!=null){JOptionPane.showMessageDialog(this,"Return the key before pairing a card.");return;}
        if(pending!=null){JOptionPane.showMessageDialog(this,"Retry the pending save or refresh first.");return;}
        if(!confirm("Pair an NFC card with "+k.label()+"?\nPlace a blank card or an existing SmartStock key card on the reader.\nReplacing the pairing immediately invalidates the previous card."))return;
        work("Place the key card on the NFC reader…",()->PcscNfcService.prepareKeyCard("SSK1"+UUID.randomUUID().toString().replace("-","")),token->{JsonObject b=request(k);b.addProperty("cardToken",token);save("pair",b);});
    }
    private void retire(){StoreKeys.Key k=needKey();if(k==null)return;if(confirm("Retire "+k.label()+"? Its history will be retained. Checked-out keys must be returned first."))save("retire",request(k));}
    private String time(String value){if(value==null)return "";try{String zone=SessionManager.getCurrentLocationTimezone();return OffsetDateTime.parse(value.replace(' ','T')).atZoneSameInstant(ZoneId.of(zone==null?"UTC":zone)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));}catch(Exception e){return value;}}
    private void history(){
        JDialog dialog=new JDialog(this,"Key custody history",false);dialog.setSize(1050,500);dialog.setLocationRelativeTo(this);
        DefaultTableModel rows=tableModel("Time","Key","Action","Employee","Recorded by","Method","Reason");JTable historyTable=new JTable(rows);DeckersSwing.styleTable(historyTable);
        JTextField search=new JTextField(16),from=new JTextField(10),to=new JTextField(10);JLabel pageLabel=new JLabel();int[] page={0};
        JPanel filters=new JPanel(new GridLayout(2,1));JPanel searchRow=new JPanel(new FlowLayout(FlowLayout.LEFT)),dateRow=new JPanel(new FlowLayout(FlowLayout.LEFT));filters.add(searchRow);filters.add(dateRow);searchRow.add(new JLabel("Key / employee / action:"));searchRow.add(search);dateRow.add(new JLabel("From YYYY-MM-DD:"));dateRow.add(from);dateRow.add(new JLabel("To:"));dateRow.add(to);
        JButton load=new JButton("Search"),prev=new JButton("Previous"),next=new JButton("Next");searchRow.add(load);searchRow.add(prev);searchRow.add(next);searchRow.add(pageLabel);
        Runnable fetch=()->{if(!sameSession()){dialog.dispose();return;}JsonObject b=request(null);b.addProperty("search",search.getText().trim());b.addProperty("from",from.getText().trim());b.addProperty("to",to.getText().trim());b.addProperty("offset",page[0]*200);load.setEnabled(false);prev.setEnabled(false);next.setEnabled(false);
            UiTaskRunner.submit(dialog,"key-history",()->LanApiClient.loadStoreKeyHistory(b),h->{rows.setRowCount(0);for(var e:h.events())rows.addRow(new Object[]{time(e.createdAt()),e.keyLabel(),e.eventType().equals("FORCED_RETURN")?"Returned by management":e.eventType(),e.holderName(),e.actorName(),e.method(),e.reason()});load.setEnabled(true);prev.setEnabled(page[0]>0);next.setEnabled(h.events().size()==200);pageLabel.setText("Page "+(page[0]+1));},e->{load.setEnabled(true);error(e);});};
        load.addActionListener(e->{page[0]=0;fetch.run();});prev.addActionListener(e->{page[0]--;fetch.run();});next.addActionListener(e->{page[0]++;fetch.run();});
        dialog.add(filters,BorderLayout.NORTH);dialog.add(new JScrollPane(historyTable));dialog.setVisible(true);fetch.run();
    }
}
