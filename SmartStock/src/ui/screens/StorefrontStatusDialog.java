package ui.screens;

import com.google.gson.JsonObject;
import managers.PermissionManager;
import services.LanApiClient;
import ui.design.DeckersSwing;
import ui.design.DeckersPalette;
import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.util.UUID;

/** Server-reported website health. No credentials or direct database access on registers. */
public final class StorefrontStatusDialog extends JDialog {
    private final JTextArea details=new JTextArea();
    private final JLabel state=new JLabel("Checking website…");
    private final JButton enable=new JButton("Enable online ordering"),disable=new JButton("Disable online ordering"),refresh=new JButton("Refresh");
    private final Timer timer=new Timer(10000,e->load(null));
    private boolean busy;
    public StorefrontStatusDialog(Window owner){
        super(owner,"Website Status",ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(650,500);setMinimumSize(new Dimension(520,400));setLocationRelativeTo(owner);
        JPanel panel=new JPanel(new BorderLayout(16,16));panel.setBorder(BorderFactory.createEmptyBorder(22,22,22,22));
        details.setEditable(false);details.setLineWrap(true);details.setWrapStyleWord(true);details.setFont(UIManager.getFont("Label.font"));
        panel.add(state,BorderLayout.NORTH);panel.add(new JScrollPane(details),BorderLayout.CENTER);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));buttons.add(refresh);buttons.add(enable);buttons.add(disable);panel.add(buttons,BorderLayout.SOUTH);setContentPane(panel);
        DeckersSwing.styleUtilityButton(enable,DeckersPalette.ORANGE);DeckersSwing.styleUtilityButton(disable,DeckersPalette.CORAL);DeckersSwing.styleUtilityButton(refresh,DeckersPalette.PURPLE);
        refresh.addActionListener(e->load(null));enable.addActionListener(e->load(true));disable.addActionListener(e->load(false));
        addWindowListener(new java.awt.event.WindowAdapter(){public void windowOpened(java.awt.event.WindowEvent e){timer.start();}public void windowClosed(java.awt.event.WindowEvent e){timer.stop();}});
        load(null);
    }
    private void load(Boolean enabled){
        if(busy)return;busy=true;enable.setEnabled(false);disable.setEnabled(false);refresh.setEnabled(false);
        new SwingWorker<JsonObject,Void>(){
            protected JsonObject doInBackground()throws Exception{
                if(enabled!=null){JsonObject command=new JsonObject();command.addProperty("action","SET_ENABLED");command.addProperty("enabled",enabled);LanApiClient.storefrontAdmin(command,UUID.randomUUID().toString());}
                return LanApiClient.storefrontStatus();
            }
            protected void done(){busy=false;if(!isDisplayable())return;refresh.setEnabled(true);try{render(get());}catch(Exception e){state.setText("Website status unavailable");details.setText("Could not read or update website status. Refresh to check the server.\n"+root(e));}}
        }.execute();
    }
    private void render(JsonObject response){
        JsonObject runtime=response.getAsJsonObject("runtime"),settings=response.getAsJsonObject("settings");
        boolean enabled=settings.has("enabled")&&settings.get("enabled").getAsBoolean();
        boolean running=runtime.get("running").getAsBoolean(),primary="PRIMARY".equals(text(runtime,"role"));
        long success=runtime.get("lastSuccess").getAsLong();boolean fresh=success>0&&System.currentTimeMillis()-success<60000;
        state.setText(!primary?"Website server fenced":!running?"Website listener not configured or stopped":!fresh?"Website gateway not confirmed reachable":enabled?"Website connected · ordering enabled":"Website connected · ordering disabled");
        details.setText("Store ordering: "+(enabled?"Enabled":"Disabled")+"\nWebsite: "+(text(runtime,"origin").isBlank()?"Not configured":text(runtime,"origin"))+"\nListener: "+(running?"Running on HTTPS 8449":"Not running")+"\nServer role: "+text(runtime,"role")+"\nLast gateway sync: "+date(success)+"\nLast sync attempt: "+date(runtime.get("lastAttempt").getAsLong())+"\nSync error: "+(text(runtime,"lastError").isBlank()?"None reported":text(runtime,"lastError"))+"\nPending order events: "+text(response.getAsJsonObject("queue"),"pending")+"\nFailed customer emails: "+text(response.getAsJsonObject("notifications"),"failed")+"\n\nControls apply to this store's online ordering. They do not stop the tunnel or other stores. Disabling prevents new orders on this primary immediately; backups learn the change after synchronization. While disconnected, backups may still use the last synced setting. Existing orders remain available for staff to fulfill.\n\nGateway sync confirms this server's connection, not end-to-end public browsing or email delivery. Status refreshes every 10 seconds.");
        boolean permitted=PermissionManager.hasPermission("COMPANY_PREFERENCES")&&primary;
        enable.setEnabled(permitted&&!enabled&&running&&fresh);disable.setEnabled(permitted&&enabled);
    }
    private static String text(JsonObject value,String key){return value.has(key)&&!value.get(key).isJsonNull()?value.get(key).getAsString():"";}
    private static String date(long epoch){return epoch==0?"Never":Instant.ofEpochMilli(epoch).toString();}
    private static String root(Throwable e){while(e.getCause()!=null)e=e.getCause();return e.getMessage()==null?"Request failed":e.getMessage();}
}
