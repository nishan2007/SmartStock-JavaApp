package ui.screens;

import com.google.gson.*;
import services.LanApiClient;
import ui.design.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Responsive health cards, read through the authenticated store server. */
public final class WebStatusDialog extends JDialog {
    private final JPanel cards=DeckersSwing.panel(),summary=DeckersSwing.panel();
    private final JLabel feedback=DeckersSwing.metaLabel("Checking the store server…");
    private final JButton refresh=button("Refresh",DeckersPalette.PURPLE);
    private final JCheckBox auto=new JCheckBox("Refresh every 10 seconds",true);
    private final javax.swing.Timer timer=new javax.swing.Timer(10000,e->{if(auto.isSelected())load(null,null);});
    private boolean busy;
    private final java.util.concurrent.Callable<JsonObject> statusLoader;
    public WebStatusDialog(Window owner){
        this(owner,null);
    }
    WebStatusDialog(Window owner,JsonObject preview){
        this(owner,preview,LanApiClient::webStatus);
    }
    WebStatusDialog(Window owner,JsonObject preview,java.util.concurrent.Callable<JsonObject> statusLoader){
        super(owner,"Web Status — SmartStock",ModalityType.MODELESS);
        this.statusLoader=statusLoader;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(1080,790);setMinimumSize(new Dimension(680,520));setLocationRelativeTo(owner);
        JPanel root=DeckersSwing.panel();root.setLayout(new BorderLayout(16,16));root.setBorder(BorderFactory.createEmptyBorder(22,24,20,24));
        JPanel header=DeckersSwing.panel();header.setLayout(new BorderLayout());
        JPanel heading=DeckersSwing.panel();heading.setLayout(new GridLayout(2,1,0,6));
        heading.add(DeckersSwing.totalLabel("Web Status",true));heading.add(DeckersSwing.metaLabel("Connections, uptime and service controls for your store"));header.add(heading);
        JPanel actions=DeckersSwing.panel();actions.setLayout(new FlowLayout(FlowLayout.RIGHT));auto.setOpaque(false);auto.setForeground(DeckersPalette.text());actions.add(auto);actions.add(refresh);header.add(actions,BorderLayout.SOUTH);
        JPanel top=DeckersSwing.panel();top.setLayout(new BorderLayout(0,18));top.add(header,BorderLayout.NORTH);summary.setLayout(new GridLayout(1,4,12,12));top.add(summary);root.add(top,BorderLayout.NORTH);
        cards.setLayout(new BoxLayout(cards,BoxLayout.Y_AXIS));JScrollPane scroll=new JScrollPane(cards);scroll.setBorder(BorderFactory.createEmptyBorder());scroll.getVerticalScrollBar().setUnitIncrement(20);root.add(scroll);
        JPanel footer=DeckersSwing.panel();footer.setLayout(new BorderLayout(0,8));footer.add(feedback,BorderLayout.NORTH);
        footer.add(DeckersSwing.metaLabel("Controls require Device Management on the active server. Traffic totals are since server start."));root.add(footer,BorderLayout.SOUTH);setContentPane(root);
        refresh.addActionListener(e->load(null,null));
        addComponentListener(new ComponentAdapter(){public void componentResized(ComponentEvent e){
            int rows=getWidth()<850?2:1;GridLayout layout=(GridLayout)summary.getLayout();
            if(layout.getRows()!=rows){layout.setRows(rows);layout.setColumns(rows==2?2:4);summary.revalidate();}
        }});
        addWindowListener(new WindowAdapter(){public void windowOpened(WindowEvent e){if(preview==null){load(null,null);timer.start();}}public void windowClosed(WindowEvent e){timer.stop();}});
        // The first worker must start after the window exists. Otherwise a fast response
        // is discarded by done() before the caller shows the dialog.
        if(preview!=null)render(preview);
    }
    private void load(String service,String action){
        if(busy)return;busy=true;refresh.setEnabled(false);setButtons(cards,false);feedback.setForeground(DeckersPalette.muted());feedback.setText(action==null?"Checking the store server…":actionLabel(action)+" in progress…");
        new SwingWorker<JsonObject,Void>(){
            protected JsonObject doInBackground()throws Exception{return service==null?statusLoader.call():LanApiClient.webControl(service,action,UUID.randomUUID().toString());}
            protected void done(){busy=false;if(!isDisplayable())return;refresh.setEnabled(true);
                try{render(get());}catch(Exception ex){Throwable failure=ex;while(failure.getCause()!=null)failure=failure.getCause();
                    feedback.setForeground(DeckersPalette.CORAL);feedback.setText("Status unavailable — "+Objects.toString(failure.getMessage(),"Refresh to reconnect."));
                    // Old health is stale; keep controls disabled until a successful refresh.
                }
            }
        }.execute();
    }
    private void render(JsonObject data){
        JsonObject usage=data.getAsJsonObject("usage");summary.removeAll();
        summary.add(metric("Server uptime",duration(number(usage,"uptimeMs")),DeckersPalette.PURPLE));
        summary.add(metric("Memory in use",size(number(usage,"heapUsed"))+" / "+size(number(usage,"heapMax")),DeckersPalette.ORANGE));
        double cpu=usage.get("cpuPercent").getAsDouble();summary.add(metric("Server CPU",cpu<0?"Measuring…":String.format(Locale.ROOT,"%.1f%%",cpu),DeckersPalette.LIME));
        summary.add(metric("Active threads",Long.toString(number(usage,"threads")),DeckersPalette.MAGENTA));
        cards.removeAll();boolean control=data.get("canControl").getAsBoolean();
        if(!control){JLabel notice=DeckersSwing.metaLabel("Viewing status — open this screen on the active server to control services.");cards.add(notice);cards.add(Box.createVerticalStrut(14));}
        boolean first=true,storeHeader=false;
        for(JsonElement element:data.getAsJsonArray("services")){
            JsonObject row=element.getAsJsonObject();boolean publicCheck=java.util.Set.of("downloads","downloadAuth").contains(text(row,"id"));
            if(first||(!publicCheck&&!storeHeader)){
                JLabel section=DeckersSwing.totalLabel(publicCheck?"Public connections":"Store services",true);section.setAlignmentX(Component.LEFT_ALIGNMENT);cards.add(section);cards.add(Box.createVerticalStrut(12));
                first=false;if(!publicCheck)storeHeader=true;
            }
            cards.add(serviceCard(row));
        }
        feedback.setForeground(DeckersPalette.muted());feedback.setText("Updated "+DateTimeFormatter.ofPattern("h:mm:ss a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(number(data,"checkedAt")))+" · Server role: "+text(data,"role"));
        summary.revalidate();summary.repaint();cards.revalidate();cards.repaint();
    }
    private JPanel serviceCard(JsonObject row){
        boolean running=row.get("running").getAsBoolean(),healthy=!row.has("healthy")||row.get("healthy").getAsBoolean();Color accent=running?(healthy?DeckersPalette.LIME:DeckersPalette.ORANGE):DeckersPalette.CORAL;
        JPanel card=DeckersSwing.panel();card.setLayout(new BorderLayout(14,10));DeckersSwing.styleBand(card,accent,new Insets(14,16,14,16));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);card.setMaximumSize(new Dimension(Integer.MAX_VALUE,205));
        JPanel info=DeckersSwing.panel();info.setOpaque(false);info.setLayout(new BoxLayout(info,BoxLayout.Y_AXIS));
        boolean connection=java.util.Set.of("downloads","downloadAuth").contains(text(row,"id"));
        JLabel title=DeckersSwing.totalLabel(text(row,"name")+"   ·   "+(running?(healthy?(connection?"Connected":"Running"):"Needs attention"):"Stopped / unavailable"),false);title.setFont(new Font("SansSerif",Font.BOLD,14));title.setForeground(DeckersPalette.text());info.add(title);
        info.add(Box.createVerticalStrut(5));info.add(wrapped(text(row,"description")));
        String url=text(row,"url");if(!url.isBlank())info.add(wrapped(url));
        JsonObject metrics=row.getAsJsonObject("metrics");long started=number(row,"startedAt");if(started==0)started=number(metrics,"startedAt");
        if(!connection&&running&&started>0)info.add(wrapped("Uptime "+duration(Math.max(0,System.currentTimeMillis()-started))));
        if(metrics.has("available")&&metrics.get("available").getAsBoolean())info.add(wrapped(number(metrics,"requests")+" requests  ·  "+size(number(metrics,"responseBytes"))+" sent  ·  "+number(metrics,"errors")+" server errors"));
        else if(!connection)info.add(wrapped("Request totals are unavailable for this connection."));
        info.add(Box.createVerticalStrut(5));info.add(wrapped(text(row,"note")));card.add(info);
        JPanel actions=DeckersSwing.panel();actions.setOpaque(false);actions.setLayout(new GridLayout(3,1,0,7));
        for(String action:java.util.List.of("START","RESTART","STOP")){
            JButton button=button(actionLabel(action),action.equals("STOP")?DeckersPalette.CORAL:DeckersPalette.PURPLE);
            button.setEnabled(row.get("controllable").getAsBoolean()&&(!action.equals("START")||!running)&&(!action.equals("STOP")||running));
            button.addActionListener(e->{if(!action.equals("START")&&JOptionPane.showConfirmDialog(this,
                actionLabel(action)+" "+text(row,"name")+"?\nPeople using this service may be disconnected.","Web Status",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION)return;
                load(text(row,"id"),action);});actions.add(button);
        }
        if(row.get("controllable").getAsBoolean())card.add(actions,BorderLayout.EAST);
        JPanel spaced=DeckersSwing.panel();spaced.setLayout(new BorderLayout());spaced.add(card);spaced.setBorder(BorderFactory.createEmptyBorder(0,0,12,0));return spaced;
    }
    private static JPanel metric(String name,String value,Color accent){JPanel panel=DeckersSwing.panel();panel.setLayout(new GridLayout(2,1,0,6));DeckersSwing.styleBand(panel,accent,new Insets(12,14,12,14));panel.add(DeckersSwing.metaLabel(name));panel.add(DeckersSwing.totalLabel(value,false));return panel;}
    private static JLabel wrapped(String text){JLabel label=new JLabel("<html>"+escape(text)+"</html>");label.setForeground(DeckersPalette.text());label.setFont(new Font("SansSerif",Font.PLAIN,13));return label;}
    private static String escape(String text){return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private static JButton button(String label,Color accent){JButton button=new JButton(label);DeckersSwing.styleUtilityButton(button,accent);return button;}
    private static void setButtons(Container container,boolean enabled){for(Component child:container.getComponents()){if(child instanceof JButton)child.setEnabled(enabled);if(child instanceof Container next)setButtons(next,enabled);}}
    private static String text(JsonObject data,String key){return data.has(key)&&!data.get(key).isJsonNull()?data.get(key).getAsString():"";}
    private static long number(JsonObject data,String key){return data.has(key)&&!data.get(key).isJsonNull()?data.get(key).getAsLong():0;}
    private static String size(long bytes){return bytes>=1024L*1024*1024?String.format(Locale.ROOT,"%.1f GB",bytes/(1024.0*1024*1024)):bytes<1024*1024?String.format(Locale.ROOT,"%.1f KB",bytes/1024.0):String.format(Locale.ROOT,"%.1f MB",bytes/(1024.0*1024));}
    private static String duration(long milliseconds){Duration d=Duration.ofMillis(milliseconds);return d.toDays()>0?d.toDays()+"d "+d.toHoursPart()+"h":d.toHours()>0?d.toHours()+"h "+d.toMinutesPart()+"m":d.toMinutes()+"m "+d.toSecondsPart()+"s";}
    private static String actionLabel(String action){return action.charAt(0)+action.substring(1).toLowerCase(Locale.ROOT);}
}
