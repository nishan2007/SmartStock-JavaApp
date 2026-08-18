package ui.screens;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import services.LanApiClient;
import data.DatabaseConfig;
import data.DatabaseMode;
import managers.PermissionManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Server-console controller for the opt-in mobile item web app. */
public final class MobileItemWebDialog extends JDialog {
    private final JLabel state=new JLabel("Checking server…");
    private final JLabel address=new JLabel(" ");
    private final JLabel ports=new JLabel(" ");
    private final JLabel expiry=new JLabel(" ");
    private final JLabel qr=new JLabel(" ",SwingConstants.CENTER);
    private final JButton start=new JButton("Start Web App");
    private final JButton stop=new JButton("Stop Web App");
    private final JButton renew=new JButton("New Activation QR");
    private final boolean canControl;

    public MobileItemWebDialog(Window owner){super(owner,"Mobile Item Web App",ModalityType.MODELESS);canControl=DatabaseConfig.load().mode()==DatabaseMode.SERVER&&PermissionManager.hasPermission("DEVICE_MANAGEMENT");setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(530,650);setMinimumSize(new Dimension(460,570));setLocationRelativeTo(owner);build();wire();refresh();}
    private void build(){JPanel root=new JPanel(new BorderLayout(12,12));root.setBorder(new EmptyBorder(18,20,18,20));JPanel info=new JPanel();info.setLayout(new BoxLayout(info,BoxLayout.Y_AXIS));JLabel title=new JLabel("Mobile Item Web App");title.setFont(title.getFont().deriveFont(Font.BOLD,22f));JTextArea help=new JTextArea(canControl?"Start the private iPhone item-entry website, then scan its activation QR.":"Scan the activation QR, then log in with normal SmartStock credentials. Start and stop controls remain available only on the server.");help.setEditable(false);help.setLineWrap(true);help.setWrapStyleWord(true);help.setOpaque(false);info.add(title);info.add(Box.createVerticalStrut(8));info.add(help);info.add(Box.createVerticalStrut(14));info.add(state);info.add(address);info.add(ports);info.add(expiry);root.add(info,BorderLayout.NORTH);qr.setPreferredSize(new Dimension(330,330));root.add(qr,BorderLayout.CENTER);JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));if(canControl)actions.add(start);actions.add(renew);if(canControl)actions.add(stop);JButton close=new JButton("Close");close.addActionListener(e->dispose());actions.add(close);root.add(actions,BorderLayout.SOUTH);setContentPane(root);}
    private void wire(){start.addActionListener(e->run(LanApiClient::startMobileItemWeb));stop.addActionListener(e->run(LanApiClient::stopMobileItemWeb));renew.addActionListener(e->run(LanApiClient::renewMobileItemWebActivation));}
    private void refresh(){run(LanApiClient::mobileItemWebStatus);}
    private void run(Load load){busy(true);new SwingWorker<LanApiClient.MobileItemWebStatus,Void>(){protected LanApiClient.MobileItemWebStatus doInBackground()throws Exception{return load.get();}protected void done(){try{busy(false);render(get());}catch(Exception e){busy(false);state.setText("Status unavailable: "+root(e));qr.setIcon(null);}}}.execute();}
    private void render(LanApiClient.MobileItemWebStatus s){state.setText(s.running()?"Status: Running":"Status: Stopped");address.setText(s.url()==null||s.url().isBlank()?" ":"Web address: "+s.url());ports.setText("Web UI "+(s.uiRunning()?"online":"offline")+" on "+s.uiPort()+" · API "+(s.apiRunning()?"online":"offline")+" on "+s.apiPort());start.setEnabled(canControl&&!s.running());stop.setEnabled(canControl&&s.running());renew.setEnabled(s.running());if(s.activationUrl()!=null&&!s.activationUrl().isBlank()){qr.setIcon(new ImageIcon(qr(s.activationUrl(),310)));qr.setText("");expiry.setText("Activation expires: "+format(s.activationExpiresAt()));}else{qr.setIcon(null);qr.setText(s.running()?"Select New Activation QR to authorize an iPhone.":canControl?"Start the web app to create an activation QR.":"The web app is stopped. Ask a device manager at the server to start it.");expiry.setText(" ");}}
    private void busy(boolean value){start.setEnabled(!value);stop.setEnabled(!value);renew.setEnabled(!value);}
    private static BufferedImage qr(String value,int size){try{BitMatrix m=new MultiFormatWriter().encode(value,BarcodeFormat.QR_CODE,size,size);BufferedImage image=new BufferedImage(size,size,BufferedImage.TYPE_INT_RGB);for(int y=0;y<size;y++)for(int x=0;x<size;x++)image.setRGB(x,y,m.get(x,y)?Color.BLACK.getRGB():Color.WHITE.getRGB());return image;}catch(Exception e){throw new IllegalArgumentException("QR code could not be generated.",e);}}
    private static String format(String value){try{return DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault()).format(Instant.parse(value));}catch(Exception e){return value==null?"":value;}}
    private static String root(Throwable e){Throwable x=e;while(x.getCause()!=null)x=x.getCause();return x.getMessage()==null?x.getClass().getSimpleName():x.getMessage();}
    @FunctionalInterface private interface Load{LanApiClient.MobileItemWebStatus get()throws Exception;}
}
