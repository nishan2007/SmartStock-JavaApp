package ui.screens;

import data.DatabaseMode;
import services.LanApiClient;
import services.OrdersWorkQueueService;
import ui.helpers.ThemeManager;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Unattended, paired-device-only work board. Never creates an employee session. */
public final class WorkQueueDisplay extends JFrame {
    private final OrdersWorkQueuePanel queue=new OrdersWorkQueuePanel(this,row->{},true);
    private final JLabel status=new JLabel("Connecting to store server…");
    private final JButton fullscreen=new JButton("Enter Full Screen");
    private final JButton setup=new JButton("Connection / Pair Display");
    private final JButton close=new JButton("Close App");
    private final JButton update=new JButton("Update Now");
    private final Timer refreshTimer=new Timer(15000,e->refresh());
    private final Timer updateTimer=new Timer(60*60*1000,e->checkUpdates());
    private ZoneId displayZone=ZoneId.systemDefault();
    private JPanel heading;
    private final Timer dateTimer=new Timer(1000,e->WorkQueue.updateHeaderDate(heading,LocalDate.now(displayZone)));
    private final JLabel updateStatus=new JLabel("Automatic updates enabled");
    private volatile boolean updateActive=true;
    private boolean updateCheckedAfterConnection;
    private final Timer scrollTimer=new Timer(80,e->scroll());
    private final Map<JScrollBar,Long> bottoms=new IdentityHashMap<>();
    private SwingWorker<OrdersWorkQueueService.Snapshot,Void> worker;
    private Instant lastSuccess;
    private long interaction=System.currentTimeMillis();
    private boolean full,closed,configuring;
    private Rectangle windowBounds;
    private final AWTEventListener pointer=e->{
        if(e instanceof MouseEvent mouse && mouse.getSource() instanceof Component c && SwingUtilities.isDescendingFrom(c,this))
            interaction=System.currentTimeMillis();
    };

    public static void open(){
        WorkQueueDisplay frame=new WorkQueueDisplay();frame.setVisible(true);
        frame.setFullscreen(true);frame.refresh();frame.checkUpdates();
    }
    public WorkQueueDisplay(){
        super("SmartStock – Work Queue Display");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(1440,900);setMinimumSize(new Dimension(900,600));setLocationRelativeTo(null);
        JPanel root=new JPanel(new BorderLayout());
        Icon logo=null;var resource=getClass().getResource("/Images/Deckers.png");
        if(resource!=null)logo=new ImageIcon(ui.design.DeckersLogoManager.scaleToFit(new ImageIcon(resource).getImage(),168,50));
        heading=WorkQueue.createHeader("Paired store","Display",LocalDate.now(displayZone),logo);
        root.add(heading,BorderLayout.NORTH);root.add(queue);
        JPanel footer=new JPanel(new BorderLayout(12,0));footer.setBorder(BorderFactory.createEmptyBorder(8,16,8,16));
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));buttons.add(update);buttons.add(setup);buttons.add(close);buttons.add(fullscreen);
        JPanel messages=new JPanel(new GridLayout(3,1));messages.add(new JLabel("SmartStock version " + utils.DeviceUtils.getAppVersion()));messages.add(status);messages.add(updateStatus);footer.add(messages);footer.add(buttons,BorderLayout.SOUTH);root.add(footer,BorderLayout.SOUTH);setContentPane(root);
        fullscreen.addActionListener(e->setFullscreen(!full));close.addActionListener(e->dispose());setup.addActionListener(e->configure());
        update.setToolTipText("Check for updates now; an available update installs and restarts SmartStock.");
        update.addActionListener(e->services.AppUpdateService.updateDisplayManually(updateStatus::setText,()->updateActive));
        bind("F11",()->setFullscreen(!full));bind("ESCAPE",()->setFullscreen(false));
        Toolkit.getDefaultToolkit().addAWTEventListener(pointer,AWTEvent.MOUSE_EVENT_MASK|AWTEvent.MOUSE_MOTION_EVENT_MASK|AWTEvent.MOUSE_WHEEL_EVENT_MASK);
        refreshTimer.start();scrollTimer.start();updateTimer.start();dateTimer.start();ThemeManager.applyToWindow(this);
    }
    private void bind(String key,Runnable action){
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key),key);
        getRootPane().getActionMap().put(key,new AbstractAction(){public void actionPerformed(ActionEvent e){action.run();}});
    }
    void setFullscreen(boolean value){
        if(full==value || closed)return;
        // Borderless fullscreen avoids exclusive display-mode changes and stays on this monitor.
        Rectangle bounds=getGraphicsConfiguration().getBounds();
        if(value)windowBounds=getBounds();
        super.dispose();setUndecorated(value);full=value;
        setBounds(value?bounds:windowBounds);setVisible(true);
        fullscreen.setText(value?"Exit Full Screen":"Enter Full Screen");setup.setVisible(!value);close.setVisible(!value);update.setVisible(!value);
    }
    private void checkUpdates(){services.AppUpdateService.updateDisplayAutomatically(updateStatus::setText,()->updateActive);}
    private void configure(){
        if(configuring || worker!=null)return;
        configuring=true;
        try {
            if(!RegisterConnectionSetup.open(this,DatabaseMode.DISPLAY))return;
            queue.setSnapshot(List.of(),ZoneId.systemDefault());lastSuccess=null;
            String phrase=JOptionPane.showInputDialog(this,"Enter the temporary pairing phrase from the store server. Approve this device marked [Display] on the server.","Pair Display",JOptionPane.PLAIN_MESSAGE);
            if(phrase!=null && !phrase.isBlank()){
                worker=new SwingWorker<>(){
                    protected OrdersWorkQueueService.Snapshot doInBackground()throws Exception{LanApiClient.pairOnce(phrase);return null;}
                    protected void done(){worker=null;if(closed)return;try{get();status.setText("Pairing requested. Approve this display on the server; it will connect automatically.");}catch(Exception ex){status.setText("Pairing failed: "+cause(ex).getMessage());}}
                };worker.execute();
            }
        }finally{configuring=false;}
    }
    private void refresh(){
        if(closed || configuring || worker!=null)return;
        worker=new SwingWorker<>(){
            protected OrdersWorkQueueService.Snapshot doInBackground()throws Exception{
                if(!LanApiClient.isPaired())LanApiClient.claimApprovedCredential();
                LanApiClient.renewDeviceCredentialIfDue();
                return LanApiClient.loadDisplayWorkQueue();
            }
            protected void done(){
                worker=null;if(closed)return;
                try{var snapshot=get();displayZone=ZoneId.of(snapshot.timezone());WorkQueue.updateHeaderDate(heading,LocalDate.now(displayZone));queue.setSnapshot(snapshot.rows(),displayZone);lastSuccess=Instant.now();
                    if(!updateCheckedAfterConnection){updateCheckedAfterConnection=true;checkUpdates();}
                    status.setText("Connected • Updated "+DateTimeFormatter.ofPattern("h:mm:ss a").withZone(ZoneId.of(snapshot.timezone())).format(lastSuccess));
                }catch(Exception ex){Throwable error=cause(ex);
                    if(error instanceof LanApiClient.LanApiException api && authenticationFailure(api.code())){
                        queue.setSnapshot(List.of(),ZoneId.systemDefault());lastSuccess=null;status.setText("Pairing / administrator approval required. Exit fullscreen to configure.");
                    }else status.setText((lastSuccess==null?"":"STALE • Last updated "+DateTimeFormatter.ofPattern("h:mm:ss a").withZone(ZoneId.systemDefault()).format(lastSuccess)+" • ")+refreshFailure(error)+" • Retrying automatically");
                }
            }
        };worker.execute();
    }
    static boolean authenticationFailure(String code){return java.util.Set.of("DEVICE_CREDENTIAL_REQUIRED","DEVICE_CREDENTIAL_INVALID","DEVICE_CREDENTIAL_EXPIRED","DEVICE_REVOKED","DEVICE_BLOCKED","DISPLAY_DEVICE_REQUIRED","DISPLAY_STORE_REQUIRED","PAIRING_REQUIRED","PAIRING_PENDING","PAIRING_CHALLENGE_INVALID","DEVICE_IDENTITY_MISMATCH").contains(code);}
    static String refreshFailure(Throwable error){
        if(error instanceof LanApiClient.LanApiException api){
            return switch(api.code()){
                case "SERVER_UNREACHABLE" -> "Store server unreachable [SERVER_UNREACHABLE]";
                case "SERVER_NOT_VERIFIED" -> "Server verification required • Exit fullscreen to pair [SERVER_NOT_VERIFIED]";
                case "INTERNAL_ERROR" -> "Store server could not load queue [INTERNAL_ERROR]";
                default -> "Queue request failed ["+api.code()+"]";
            };
        }
        return "Unable to load queue ["+error.getClass().getSimpleName()+"]";
    }
    private static Throwable cause(Throwable e){while(e.getCause()!=null)e=e.getCause();return e;}
    private void scroll(){
        if(closed || configuring || System.currentTimeMillis()-interaction<15000)return;
        java.util.Set<JScrollBar> active=java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        scrollChildren(queue,active);bottoms.keySet().retainAll(active);
    }
    private void scrollChildren(Container parent,java.util.Set<JScrollBar> active){
        for(Component c:parent.getComponents()){
            if(c instanceof JScrollPane pane){JScrollBar bar=pane.getVerticalScrollBar();active.add(bar);
                if(bar.getMaximum()<=bar.getVisibleAmount())continue;
                long now=System.currentTimeMillis();
                if(bar.getValue()+bar.getVisibleAmount()>=bar.getMaximum()){
                    long reached=bottoms.computeIfAbsent(bar,k->now);if(now-reached>=4000){bar.setValue(0);bottoms.remove(bar);}
                }else{bottoms.remove(bar);bar.setValue(bar.getValue()+2);}
            }else if(c instanceof Container container)scrollChildren(container,active);
        }
    }
    @Override public void dispose(){
        closed=true;updateActive=false;dateTimer.stop();updateTimer.stop();refreshTimer.stop();scrollTimer.stop();Toolkit.getDefaultToolkit().removeAWTEventListener(pointer);
        if(worker!=null)worker.cancel(true);bottoms.clear();super.dispose();
    }
}
