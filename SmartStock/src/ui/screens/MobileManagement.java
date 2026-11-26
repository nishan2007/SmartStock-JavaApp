package ui.screens;

import services.LanApiClient;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import ui.helpers.UiTaskRunner;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Device workspace: changing app grants never requires another phone pairing. */
public final class MobileManagement extends JDialog {
    private final DefaultListModel<LanApiClient.MobileManagedDevice> model=new DefaultListModel<>();
    private final JList<LanApiClient.MobileManagedDevice> devices=new JList<>(model);
    private final JTextField search=new JTextField(),name=new JTextField(),appSearch=new JTextField();
    private final JPanel rows=DeckersSwing.panel();
    private final JLabel title=new JLabel("Select a phone"),status=new JLabel("Loading mobile devices…");
    private final JButton repair=new JButton("Re-pair QR"),review=new JButton("Review changes"),discard=new JButton("Discard"),revoke=new JButton("Revoke phone"),pair=new JButton("Pair phone"),refresh=new JButton("Refresh");
    private final JButton deviceDetails=new JButton("Device details");
    private final JCheckBox rememberLogin=new JCheckBox("Keep employees signed in on this device");
    private final Map<String,JCheckBox> checks=new LinkedHashMap<>();
    private LanApiClient.MobileManagementState state;
    private LanApiClient.MobileManagedDevice selected;
    private boolean adjusting,busy;

    public MobileManagement(Window owner) { this(owner,true); }
    MobileManagement(Window owner,LanApiClient.MobileManagementState preview) {
        this(owner,false);state=preview;filter();
    }
    private MobileManagement(Window owner,boolean load) {
        super(owner,"Mobile Management",ModalityType.MODELESS);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1100,760);setMinimumSize(new Dimension(820,600));setLocationRelativeTo(owner);
        JPanel root=DeckersSwing.panel();root.setLayout(new BorderLayout(18,18));root.setBorder(new EmptyBorder(20,20,20,20));
        JPanel heading=DeckersSwing.panel();heading.setLayout(new BorderLayout());
        JLabel label=new JLabel("Mobile management");label.setFont(label.getFont().deriveFont(Font.BOLD,34f));
        heading.add(label,BorderLayout.NORTH);heading.add(new JLabel("Pair once. Choose which staff apps each phone can open."),BorderLayout.SOUTH);
        root.add(heading,BorderLayout.NORTH);
        JPanel left=DeckersSwing.panel();left.setLayout(new BorderLayout(10,10));left.setPreferredSize(new Dimension(285,400));
        search.setToolTipText("Search phones");DeckersSwing.styleField(search);
        JPanel deviceSearch=DeckersSwing.panel();deviceSearch.setLayout(new BorderLayout(0,6));deviceSearch.add(new JLabel("Search phones"),BorderLayout.NORTH);deviceSearch.add(search,BorderLayout.CENTER);left.add(deviceSearch,BorderLayout.NORTH);
        devices.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);devices.setFixedCellHeight(66);
        devices.setCellRenderer((list,value,index,chosen,focus)->{
            JLabel cell=new JLabel();cell.setText("<html><b>"+escape(value.name())+"</b><br>"+(value.revoked()?"Revoked":value.apps().size()+" enabled apps")+"</html>");
            cell.setBorder(new EmptyBorder(8,12,8,12));cell.setOpaque(true);cell.setBackground(chosen?DeckersPalette.sectionFill(DeckersPalette.PURPLE):DeckersPalette.fieldBackground());cell.setForeground(DeckersPalette.text());return cell;
        });
        left.add(new JScrollPane(devices),BorderLayout.CENTER);left.add(pair,BorderLayout.SOUTH);
        JPanel editor=DeckersSwing.panel();editor.setLayout(new BorderLayout(12,12));
        JPanel detail=DeckersSwing.panel();detail.setLayout(new GridLayout(0,1,0,8));
        title.setFont(title.getFont().deriveFont(Font.BOLD,26f));detail.add(title);detail.add(new JLabel("Device name"));DeckersSwing.styleField(name);detail.add(name);
        rememberLogin.setOpaque(false);rememberLogin.addActionListener(e->buttons());
        rememberLogin.setToolTipText("Manager-controlled. Changing this setting signs out existing workspace sessions.");
        detail.add(rememberLogin);
        detail.add(new JLabel("Off: sign out on close/reload. Policy changes require a fresh sign-in."));
        detail.add(new JLabel("Enabled web apps · employee role permissions still apply"));
        detail.add(new JLabel("Search web apps"));
        appSearch.setToolTipText("Search web apps");DeckersSwing.styleField(appSearch);detail.add(appSearch);
        Component[] detailRows=detail.getComponents();detail.removeAll();detail.setLayout(new BoxLayout(detail,BoxLayout.Y_AXIS));
        for(Component component:detailRows){
            if(component instanceof JComponent item)item.setAlignmentX(Component.LEFT_ALIGNMENT);
            if(component instanceof JTextField field){field.setPreferredSize(new Dimension(150,34));field.setMaximumSize(new Dimension(Integer.MAX_VALUE,34));}
            detail.add(component);detail.add(Box.createVerticalStrut(5));
        }
        editor.add(detail,BorderLayout.NORTH);
        rows.setLayout(new BoxLayout(rows,BoxLayout.Y_AXIS));editor.add(new JScrollPane(rows),BorderLayout.CENTER);
        JPanel actions=new JPanel(new GridLayout(1,4,8,0));actions.setOpaque(false);actions.add(repair);actions.add(revoke);actions.add(discard);actions.add(review);editor.add(actions,BorderLayout.SOUTH);
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,left,editor);split.setResizeWeight(.28);split.setBorder(null);root.add(split,BorderLayout.CENTER);
        JPanel footer=DeckersSwing.panel();footer.setLayout(new BorderLayout());footer.add(status,BorderLayout.CENTER);JPanel footerActions=DeckersSwing.panel();footerActions.setLayout(new FlowLayout(FlowLayout.RIGHT,8,0));footerActions.add(deviceDetails);footerActions.add(refresh);footer.add(footerActions,BorderLayout.EAST);root.add(footer,BorderLayout.SOUTH);setContentPane(root);
        search.getAccessibleContext().setAccessibleName("Search phones");name.getAccessibleContext().setAccessibleName("Device name");appSearch.getAccessibleContext().setAccessibleName("Search web apps");
        search.getDocument().addDocumentListener(listener(this::filter));appSearch.getDocument().addDocumentListener(listener(this::filterApps));
        name.getDocument().addDocumentListener(listener(this::buttons));
        devices.addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&!adjusting)select(devices.getSelectedValue());});
        deviceDetails.addActionListener(e->showDeviceDetails());
        repair.addActionListener(e->repair());
        pair.addActionListener(e->pair());refresh.addActionListener(e->{if(confirmDiscard()){render();load();}});discard.addActionListener(e->render());review.addActionListener(e->save(false));revoke.addActionListener(e->{if(selected!=null&&selected.revoked())restore();else save(true);});
        addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){if(!busy&&confirmDiscard())dispose();}});
        DeckersSwing.styleBand(heading,DeckersPalette.ORANGE,new Insets(8,12,8,12));
        for(JButton button:List.of(pair,discard,revoke,repair,deviceDetails))DeckersSwing.styleUtilityButton(button,DeckersPalette.ORANGE);
        for(JButton button:List.of(review,refresh))DeckersSwing.styleUtilityButton(button,DeckersPalette.PURPLE);
        buttons();if(load)load();
    }
    private static DocumentListener listener(Runnable action){return new DocumentListener(){public void insertUpdate(DocumentEvent e){action.run();}public void removeUpdate(DocumentEvent e){action.run();}public void changedUpdate(DocumentEvent e){action.run();}};}
    private Set<String> chosen(){Set<String> result=new LinkedHashSet<>();checks.forEach((key,box)->{if(box.isSelected())result.add(key);});return result;}
    private boolean dirty(){return selected!=null&&(!name.getText().trim().equals(selected.name())||!chosen().equals(new HashSet<>(selected.apps()))||rememberLogin.isSelected()!=selected.rememberLogin());}
    private boolean confirmDiscard(){return !dirty()||JOptionPane.showConfirmDialog(this,"Discard unsaved mobile device changes?","Unsaved changes",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION;}
    private void select(LanApiClient.MobileManagedDevice next){
        if(!confirmDiscard()){adjusting=true;devices.setSelectedValue(selected,true);adjusting=false;return;}
        selected=next;render();
    }
    private void filter(){
        if(state==null||busy)return;
        if(dirty())return;
        String query=search.getText().trim().toLowerCase(Locale.ROOT),id=selected==null?null:selected.deviceId();
        adjusting=true;model.clear();for(var device:state.devices())if(device.name().toLowerCase(Locale.ROOT).contains(query))model.addElement(device);
        for(int i=0;i<model.size();i++)if(model.get(i).deviceId().equals(id))devices.setSelectedIndex(i);
        if(devices.getSelectedIndex()<0&&!model.isEmpty())devices.setSelectedIndex(0);
        selected=devices.getSelectedValue();adjusting=false;render();
    }
    private void filterApps(){String query=appSearch.getText().trim().toLowerCase(Locale.ROOT);checks.forEach((key,box)->box.setVisible(box.getText().toLowerCase(Locale.ROOT).contains(query)));rows.revalidate();rows.repaint();}
    private void render(){
        adjusting=true;rememberLogin.setSelected(selected!=null&&selected.rememberLogin());checks.clear();rows.removeAll();name.setText(selected==null?"":selected.name());title.setText(selected==null?"Select a phone":selected.name());
        if(state!=null)for(var app:state.apps()) {
            JCheckBox box=new JCheckBox(app.group()+" · "+app.name());box.setOpaque(false);box.setFont(box.getFont().deriveFont(17f));box.setBorder(new EmptyBorder(12,10,12,10));
            box.setSelected(selected!=null&&selected.apps().contains(app.key()));box.addActionListener(e->buttons());checks.put(app.key(),box);rows.add(box);
        }
        if(selected!=null)status.setText((selected.revoked()?"Revoked · ":"Approved · ")+"Last activity: "+formatTime(selected.lastSeenAt()));
        if(selected==null&&state!=null)status.setText(state.devices().isEmpty()?"No paired phones. Select Pair phone to get started.":"No devices match your search.");
        adjusting=false;buttons();filterApps();
    }
    private void buttons(){
        deviceDetails.setEnabled(!busy&&selected!=null);
        boolean editable=!busy&&selected!=null&&!selected.revoked();name.setEnabled(editable);rememberLogin.setEnabled(editable);checks.values().forEach(box->box.setEnabled(editable));
        review.setEnabled(editable&&dirty());discard.setEnabled(editable&&dirty());revoke.setText(selected!=null&&selected.revoked()?"Restore device":"Revoke phone");revoke.setToolTipText(selected!=null&&selected.revoked()?"Unrevoke this device with its existing enabled apps.":"Revoke this phone from staff web apps.");revoke.setEnabled(!busy&&selected!=null);repair.setEnabled(editable&&!dirty());pair.setEnabled(!busy&&state!=null);refresh.setEnabled(!busy);devices.setEnabled(!busy);search.setEnabled(!busy&&!dirty());
    }
    private void load(){busy=true;buttons();status.setText("Loading mobile devices…");UiTaskRunner.submit(this,"mobile-state",LanApiClient::loadMobileManagement,state->{this.state=state;busy=false;filter();if(selected==null)status.setText(state.devices().isEmpty()?"No paired phones. Select Pair phone to get started.":"No devices match your search.");buttons();},this::error);}
    private void error(Throwable error){busy=false;buttons();status.setText("The request could not be completed.");JOptionPane.showMessageDialog(this,error.getMessage(),"Mobile Management",JOptionPane.ERROR_MESSAGE);}
    private void save(boolean revokeDevice){
        if(selected==null||busy)return;
        String newName=name.getText().trim();if(newName.isEmpty()||newName.length()>120){JOptionPane.showMessageDialog(this,"Enter a device name of 1 to 120 characters.");return;}
        Set<String> allowed=chosen();StringBuilder changes=new StringBuilder(revokeDevice?"Revoke this phone from every staff web app?":"Save changes for "+newName+"?");
        if(!newName.equals(selected.name()))changes.append("\nRename: ").append(selected.name()).append(" → ").append(newName);
        for(var app:state.apps())if(allowed.contains(app.key())!=selected.apps().contains(app.key()))changes.append("\n").append(allowed.contains(app.key())?"Enable: ":"Disable: ").append(app.name());
        if(rememberLogin.isSelected()!=selected.rememberLogin())changes.append("\nLogin: ").append(rememberLogin.isSelected()?"Keep signed in":"Sign out on close/reload").append(" (existing sessions will sign out)");
        if(JOptionPane.showConfirmDialog(this,changes.toString(),revokeDevice?"Revoke phone":"Review changes",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        var device=selected;boolean keepLogin=rememberLogin.isSelected();busy=true;buttons();UiTaskRunner.submit(this,"mobile-save",()->{LanApiClient.saveMobileDevice(device.deviceId(),device.revision(),newName,allowed,revokeDevice,keepLogin,UUID.randomUUID().toString());return true;},ok->{selected=null;load();},this::error);
    }
    static String deviceDetailsText(LanApiClient.MobileManagedDevice device){
        var report=device.deviceInfo();
        StringBuilder text=new StringBuilder(device.name()+"\n\n");
        if(report==null||report.isEmpty())text.append("No device information reported yet.\nOpen the updated Deckers app and sign in on this device to collect details.");
        else {
            for(String[] field:new String[][]{{"deviceFamily","Device type"},{"model","Model"},{"platform","Operating system"},{"osVersion","OS version"},{"browser","Browser"},{"browserVersion","Browser version"},{"displayMode","App mode"},{"screenWidth","Screen width (CSS pixels)"},{"screenHeight","Screen height (CSS pixels)"},{"pixelRatio","Pixel ratio"},{"touchPoints","Touch points"},{"language","Language"},{"timeZone","Time zone"}}) {
                Object value=report.get(field[0]);String display=value==null?"":value.toString();
                if(value instanceof Number n&&n.doubleValue()==n.longValue())display=Long.toString(n.longValue());
                text.append(field[1]).append(": ").append(display.isBlank()?"Not provided by browser":display).append('\n');
            }
            text.append("Updated: ").append(formatTime(device.deviceInfoUpdatedAt())).append("\n\nBrowser-reported diagnostics; model/OS may be reduced or unavailable.\nThese details are not verified hardware identity.\n\nUser agent: ").append(report.getOrDefault("userAgent","Not provided"));
        }
        return text.toString();
    }
    private void showDeviceDetails(){
        if(selected==null)return;
        JTextArea view=new JTextArea(deviceDetailsText(selected));view.setEditable(false);view.setLineWrap(true);view.setWrapStyleWord(true);view.setCaretPosition(0);
        view.setBorder(new EmptyBorder(12,12,12,12));JScrollPane scroll=new JScrollPane(view);scroll.setPreferredSize(new Dimension(570,460));
        JOptionPane.showMessageDialog(this,scroll,"Mobile device details",JOptionPane.INFORMATION_MESSAGE);
    }
    private void restore(){
        if(selected==null||!selected.revoked()||busy)return;
        var device=selected;
        if(JOptionPane.showConfirmDialog(this,"Restore "+device.name()+"?\nPreviously approved browsers will regain access to its existing enabled apps.\nEmployee role permissions still apply.","Restore device",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        busy=true;buttons();
        UiTaskRunner.submit(this,"mobile-restore",()->{LanApiClient.restoreMobileDevice(device.deviceId(),device.revision(),UUID.randomUUID().toString());return true;},ok->{load();},this::error);
    }
    private void repair(){
        if(selected==null||selected.revoked()||busy||dirty())return;
        var device=selected;busy=true;buttons();
        UiTaskRunner.submit(this,"mobile-repair",()->LanApiClient.issueMobileRepair(device.deviceId(),UUID.randomUUID().toString()),result->{busy=false;buttons();showQr(result,true);},this::error);
    }
    private void pair(){
        if(!confirmDiscard())return;
        render();
        JTextField deviceName=new JTextField();DeckersSwing.styleField(deviceName);
        JPanel form=DeckersSwing.panel();form.setLayout(new BoxLayout(form,BoxLayout.Y_AXIS));form.add(new JLabel("Phone name"));form.add(deviceName);form.add(new JLabel("Choose initial web apps (new apps added later start disabled)"));
        Map<String,JCheckBox> choices=new LinkedHashMap<>();for(var app:state.apps()){JCheckBox box=new JCheckBox(app.group()+" · "+app.name());box.setOpaque(false);choices.put(app.key(),box);form.add(box);}
        JScrollPane scroll=new JScrollPane(form);scroll.setPreferredSize(new Dimension(540,480));
        if(JOptionPane.showConfirmDialog(this,scroll,"Pair phone",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        String phoneName=deviceName.getText().trim();if(phoneName.isEmpty()||phoneName.length()>120){JOptionPane.showMessageDialog(this,"Enter a device name of 1 to 120 characters.");return;}
        Set<String> apps=new LinkedHashSet<>();choices.forEach((key,box)->{if(box.isSelected())apps.add(key);});busy=true;buttons();
        UiTaskRunner.submit(this,"mobile-pair",()->LanApiClient.issueMobilePairing(phoneName,apps,UUID.randomUUID().toString()),result->{busy=false;buttons();showQr(result);},this::error);
    }
    private void showQr(LanApiClient.MobilePairing pairing){showQr(pairing,false);}
    private void showQr(LanApiClient.MobilePairing pairing,boolean repair){
        try {
            var matrix=new MultiFormatWriter().encode(pairing.url(),BarcodeFormat.QR_CODE,320,320);BufferedImage image=new BufferedImage(320,320,BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<320;y++)for(int x=0;x<320;x++)image.setRGB(x,y,matrix.get(x,y)?Color.BLACK.getRGB():Color.WHITE.getRGB());
            JPanel content=new JPanel(new BorderLayout(10,10));content.add(new JLabel(new ImageIcon(image)),BorderLayout.CENTER);
            content.add(new JLabel("<html>"+(repair?"On this phone, choose Scan re-pair QR code.<br>Its enabled apps and employee permissions stay the same.":"Scan once using the phone's browser.")+"<br>Expires: "+escape(formatTime(pairing.expiresAt()))+"<br>Save the launcher as a bookmark after pairing.</html>"),BorderLayout.NORTH);
            JButton copy=new JButton("Copy approval link");copy.addActionListener(e->Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(pairing.url()),null));content.add(copy,BorderLayout.SOUTH);
            JOptionPane.showMessageDialog(this,content,repair?"Re-pair phone · single-use QR":"Pair phone · single-use QR",JOptionPane.PLAIN_MESSAGE);selected=null;load();
        }catch(Exception e){error(e);}
    }
    private static String formatTime(String value){try{return java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a").withZone(ui.helpers.StoreTimeZoneHelper.getStoreZone()).format(java.time.Instant.parse(value));}catch(Exception e){return value;}}
    private static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
}
