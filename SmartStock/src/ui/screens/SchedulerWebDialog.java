package ui.screens;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import data.DatabaseConfig;
import data.DatabaseMode;
import managers.PermissionManager;
import services.LanApiClient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URI;

/** Scheduler access QR for registers, with active-server controls when authorized. */
public final class SchedulerWebDialog extends JDialog {
    private final JLabel state=new JLabel("Checking gateway…"),port=new JLabel(" ");
    private final JTextField address=new JTextField();
    private final JLabel qr=new JLabel("Start the web app to display its QR code.",SwingConstants.CENTER);
    private final JButton open=new JButton("Open Link"),copy=new JButton("Copy Link"),devices=new JButton("Scheduler Devices"),start=new JButton("Start Web App"),stop=new JButton("Stop Web App");
    private final boolean canControl;
    private final Timer refreshTimer=new Timer(3000,e->refresh());
    private boolean loading; private String publicUrl;

    public SchedulerWebDialog(Window owner){super(owner,"Scheduler Web App",ModalityType.MODELESS);canControl=DatabaseConfig.load().mode()==DatabaseMode.SERVER&&PermissionManager.hasPermission("DEVICE_MANAGEMENT");setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(640,650);setMinimumSize(new Dimension(540,590));setLocationRelativeTo(owner);build();wire();refresh();}

    private void build(){
        JPanel root=new JPanel(new BorderLayout(12,12));root.setBorder(new EmptyBorder(18,20,18,20));
        JPanel info=new JPanel();info.setLayout(new BoxLayout(info,BoxLayout.Y_AXIS));
        JLabel title=new JLabel("Scheduler Web App");title.setFont(title.getFont().deriveFont(Font.BOLD,22f));
        JTextArea help=new JTextArea(canControl?"Start or stop remote scheduling. Scan the QR code on your phone, click Open Link, or copy the address. Stopping signs out every browser and makes the Cloudflare address unavailable.":"Scan the QR code on your phone, click Open Link, or copy the address. Start and stop controls remain available only on the active store server.");help.setEditable(false);help.setLineWrap(true);help.setWrapStyleWord(true);help.setOpaque(false);
        address.setEditable(false);address.setToolTipText("Click to open the Scheduler Web App");address.setMaximumSize(new Dimension(Integer.MAX_VALUE,address.getPreferredSize().height));
        JPanel links=new JPanel(new FlowLayout(FlowLayout.LEFT,6,4));links.add(open);links.add(copy);
        info.add(title);info.add(Box.createVerticalStrut(8));info.add(help);info.add(Box.createVerticalStrut(14));info.add(state);info.add(Box.createVerticalStrut(5));info.add(new JLabel("Public scheduler address:"));info.add(Box.createVerticalStrut(3));info.add(address);info.add(links);info.add(port);root.add(info,BorderLayout.NORTH);
        qr.setPreferredSize(new Dimension(290,290));qr.setBorder(new EmptyBorder(8,8,8,8));root.add(qr,BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));if(canControl){actions.add(devices);actions.add(start);actions.add(stop);}JButton close=new JButton("Close");close.addActionListener(e->dispose());actions.add(close);root.add(actions,BorderLayout.SOUTH);setContentPane(root);
    }

    private void wire(){devices.addActionListener(e->new SchedulerBrowserDevicesDialog(this).setVisible(true));start.addActionListener(e->run(LanApiClient::startSchedulerWeb));stop.addActionListener(e->run(LanApiClient::stopSchedulerWeb));open.addActionListener(e->openLink());copy.addActionListener(e->copyLink());address.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e)&&publicUrl!=null)openLink();}});}
    private void refresh(){if(!refreshTimer.isRunning())refreshTimer.start();run(LanApiClient::schedulerWebStatus);}
    @Override public void dispose(){refreshTimer.stop();super.dispose();}
    private void run(Load load){if(loading)return;loading=true;busy(true);new SwingWorker<LanApiClient.SchedulerWebStatus,Void>(){@Override protected LanApiClient.SchedulerWebStatus doInBackground()throws Exception{return load.get();}@Override protected void done(){loading=false;try{busy(false);render(get());}catch(Exception e){busy(false);publicUrl=null;state.setText("Status unavailable: "+root(e));renderLink();}}}.execute();}
    private void render(LanApiClient.SchedulerWebStatus s){state.setText(s.running()?"Status: Running":s.enabled()?"Status: Starting gateway…":"Status: Stopped");publicUrl=schedulerUrl(s.url());port.setText("Gateway listener port: "+s.port());start.setEnabled(canControl&&!s.enabled());stop.setEnabled(canControl&&s.enabled());renderLink();}
    private void renderLink(){boolean available=publicUrl!=null;address.setText(available?publicUrl:"Available after the web app starts");address.setCaretPosition(0);address.setCursor(Cursor.getPredefinedCursor(available?Cursor.HAND_CURSOR:Cursor.DEFAULT_CURSOR));open.setEnabled(available);copy.setEnabled(available);if(available){qr.setIcon(new ImageIcon(qrCode(publicUrl,270)));qr.setText("");qr.setToolTipText("Scan to open the Scheduler Web App");}else{qr.setIcon(null);qr.setText("Start the web app to display its QR code.");qr.setToolTipText(null);}}
    private void openLink(){if(publicUrl==null)return;try{if(!Desktop.isDesktopSupported()||!Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))throw new IllegalStateException("Opening web links is not supported on this computer.");Desktop.getDesktop().browse(URI.create(publicUrl));}catch(Exception e){JOptionPane.showMessageDialog(this,root(e),"Could Not Open Link",JOptionPane.ERROR_MESSAGE);}}
    private void copyLink(){if(publicUrl==null)return;try{Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(publicUrl),null);copy.setText("Copied!");Timer reset=new Timer(1600,e->copy.setText("Copy Link"));reset.setRepeats(false);reset.start();}catch(Exception e){JOptionPane.showMessageDialog(this,root(e),"Could Not Copy Link",JOptionPane.ERROR_MESSAGE);}}
    private void busy(boolean value){start.setEnabled(!value&&canControl);stop.setEnabled(!value&&canControl);open.setEnabled(!value&&publicUrl!=null);copy.setEnabled(!value&&publicUrl!=null);}

    static String schedulerUrl(String base){if(base==null||base.isBlank())return null;try{URI uri=URI.create(base.trim());if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)return null;String value=uri.toString();while(value.endsWith("/"))value=value.substring(0,value.length()-1);return value.endsWith("/scheduler")?value+"/":value+"/scheduler/";}catch(Exception ignored){return null;}}
    static BufferedImage qrCode(String value,int size){try{BitMatrix matrix=new MultiFormatWriter().encode(value,BarcodeFormat.QR_CODE,size,size);BufferedImage image=new BufferedImage(size,size,BufferedImage.TYPE_INT_RGB);for(int y=0;y<size;y++)for(int x=0;x<size;x++)image.setRGB(x,y,matrix.get(x,y)?Color.BLACK.getRGB():Color.WHITE.getRGB());return image;}catch(Exception e){throw new IllegalArgumentException("QR code could not be generated.",e);}}
    private static String root(Throwable e){Throwable x=e;while(x.getCause()!=null)x=x.getCause();return x.getMessage()==null?x.getClass().getSimpleName():x.getMessage();}
    @FunctionalInterface private interface Load{LanApiClient.SchedulerWebStatus get()throws Exception;}
}
