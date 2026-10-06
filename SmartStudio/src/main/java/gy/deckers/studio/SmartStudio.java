package gy.deckers.studio;

import com.google.gson.JsonObject;
import data.DatabaseConfig;
import data.DatabaseMode;
import services.LanApiClient;
import services.StudioBackgroundService;
import services.StudioQuality;
import ui.design.DeckersLogoManager;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** A separate staff client; all authentication and image processing live on SmartStock. */
public final class SmartStudio extends JFrame {
    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private final JLabel company = new JLabel("Deckers"), motto = new JLabel("SmartStudio • Powered by SmartStock"), logo = new JLabel();
    private final JLabel status = new JLabel("Connect to your store to get started.");
    private final JProgressBar progress = new JProgressBar();
    private final JTextField host = new JTextField("127.0.0.1"), port = new JTextField("8443"), identifier = new JTextField();
    private final JPasswordField phrase = new JPasswordField(), password = new JPasswordField();
    private final Preview original = new Preview("Original image"), result = new Preview("Transparent result");
    private final JComboBox<StudioQuality> quality = new JComboBox<>(StudioQuality.values());
    private final JCheckBox cleanEdges = new JCheckBox("Clean edges", true);
    private final JComboBox<String> previewBackground = new JComboBox<>(new String[]{"Checkerboard", "White", "Black"});
    private final JButton choose = button("Choose image", DeckersPalette.PURPLE), remove = button("Remove background", DeckersPalette.ORANGE), save = button("Save PNG", DeckersPalette.PURPLE);
    private final JButton copy = button("Copy result", DeckersPalette.PURPLE);
    private byte[] sourceBytes, outputBytes;
    private String fileName = "image";
    private boolean busy, signedIn;
    private long lastActivity = System.currentTimeMillis();
    private int idleMinutes;
    private Runnable imageReady = () -> { };
    private boolean checkOnly;
    private final Timer inactivity;
    private final AWTEventListener activityWatcher = e -> {
        if (e.getSource() instanceof Component c && SwingUtilities.getWindowAncestor(c) == this) lastActivity = System.currentTimeMillis();
    };

    public static void main(String[] args) {
        // Keep Studio sessions and credentials separate from the register.
        String home = System.getProperty("user.home");
        System.setProperty("smartstudio.register.home", home);
        System.setProperty("user.home", Path.of(home, ".smartstudio").toString());
        System.setProperty("smartstock.db.mode", "CLIENT");
        System.setProperty("smartstock.client.application", "smartstudio");
        if (System.getProperty("smartstock.environment") == null) System.setProperty("smartstock.environment", "production");
        boolean checkLaunch = args.length == 2 && "--check-launch".equals(args[0]);
        boolean checkImage = args.length == 3 && "--check-image".equals(args[0]);
        SwingUtilities.invokeLater(() -> {
            SmartStudio window = new SmartStudio(!(checkLaunch||checkImage));
            if (!(checkLaunch||checkImage)) { window.setVisible(true); return; }
            window.checkOnly=true;
            if(checkImage) {
                window.cards.show(window.pages,"studio");
                window.imageReady=() -> {
                    try {
                        if(window.original.image==null||!window.remove.isEnabled())throw new IllegalStateException("The image workflow did not enable removal.");
                        window.writePreview(Path.of(args[2]));
                    }catch(Exception ex){ex.printStackTrace();System.exit(1);}
                    finally{window.dispose();}
                };
                window.loadImage(Path.of(args[1]));return;
            }
            try {
                window.cards.show(window.pages,"login");
                window.writePreview(Path.of(args[1]));
            } catch (Exception ex) { ex.printStackTrace(); System.exit(1); }
            finally { window.dispose(); }
        });
    }

    private void writePreview(Path path)throws Exception {
        addNotify();validate();
        BufferedImage preview=new BufferedImage(getContentPane().getWidth(),getContentPane().getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D draw=preview.createGraphics();getContentPane().printAll(draw);draw.dispose();ImageIO.write(preview,"png",path.toFile());
    }

    SmartStudio(boolean checkConnection) {
        super("SmartStudio — Deckers");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(820, 580)); setSize(1100, 760); setLocationRelativeTo(null);
        JPanel root = DeckersSwing.panel(); root.setLayout(new BorderLayout(16,16)); root.setBorder(BorderFactory.createEmptyBorder(20,24,20,24));
        JPanel header = DeckersSwing.panel(); header.setLayout(new BorderLayout(16,0));
        var deckersLogo = DeckersLogoManager.loadDeckersLogoIcon(SmartStudio.class);
        if (deckersLogo != null) logo.setIcon(new ImageIcon(DeckersLogoManager.scaleToFit(deckersLogo.getImage(),160,60)));
        var studioIcon = SmartStudio.class.getResource("icons/SmartStudioIconLight.png");
        if (studioIcon != null) {
            Image image = new ImageIcon(studioIcon).getImage();
            setIconImages(java.util.List.of(DeckersLogoManager.scaleToFit(image,16,16),
                    DeckersLogoManager.scaleToFit(image,32,32),DeckersLogoManager.scaleToFit(image,48,48),
                    DeckersLogoManager.scaleToFit(image,256,256)));
        }
        header.add(logo, BorderLayout.WEST);
        JPanel titles = DeckersSwing.panel(); titles.setLayout(new GridLayout(2,1));
        company.setFont(new Font("SansSerif",Font.BOLD,26)); company.setForeground(DeckersPalette.text());
        motto.setForeground(DeckersPalette.muted()); titles.add(company); titles.add(motto); header.add(titles);
        header.add(DeckersSwing.totalLabel("SmartStudio", true),BorderLayout.EAST); root.add(header,BorderLayout.NORTH);
        pages.add(connectionPage(), "connect"); pages.add(loginPage(), "login"); pages.add(studioPage(), "studio"); root.add(pages);
        status.setForeground(DeckersPalette.muted());
        JPanel feedback=DeckersSwing.panel(); feedback.setLayout(new BorderLayout(12,0));
        progress.setIndeterminate(true);progress.setVisible(false);progress.setPreferredSize(new Dimension(140,18));
        feedback.add(status);feedback.add(progress,BorderLayout.EAST);root.add(feedback,BorderLayout.SOUTH);setContentPane(root);
        DatabaseConfig config = DatabaseConfig.load(); host.setText(config.serverHost());
        if (DatabaseConfig.hasConfigFile()) port.setText(String.valueOf(config.serverPort()));
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) {
            if (busy) { status.setText("Please wait for the current operation to finish."); return; }
            work("Closing…", () -> { LanApiClient.logoutWithoutWaiting(); return true; }, ignored -> dispose());
        }});
        Toolkit.getDefaultToolkit().addAWTEventListener(activityWatcher, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
        inactivity = new Timer(1000, e -> { if (signedIn && !busy && idleMinutes > 0 && System.currentTimeMillis()-lastActivity > idleMinutes*60_000L) signOut(); });
        inactivity.start();
        if (checkConnection) work("Checking store connection…", () -> {
            if (!LanApiClient.isPaired()) LanApiClient.reuseRegisterPairing(Path.of(System.getProperty("smartstudio.register.home", System.getProperty("user.home"))));
            LanApiClient.claimApprovedCredential();
            if(LanApiClient.isPaired())LanApiClient.verifyStudioPairing();
            return LanApiClient.isPaired() ? LanApiClient.studioBranding() : null;
        }, brand -> {
            if (brand != null) { applyBrand(brand); cards.show(pages,"login"); status.setText("Sign in with your SmartStock staff account."); }
            else {
                DatabaseConfig saved = DatabaseConfig.load();
                host.setText(saved.serverHost());
                if (DatabaseConfig.hasConfigFile()) port.setText(String.valueOf(saved.serverPort()));
                status.setText("If this computer is awaiting approval, enable Allow Studio in SmartStock, then click check approval.");
            }
        });
    }

    @Override public void dispose() {
        if (inactivity != null) inactivity.stop();
        Toolkit.getDefaultToolkit().removeAWTEventListener(activityWatcher);
        super.dispose();
    }

    private JPanel connectionPage() {
        JPanel form = form("Connect SmartStudio", "Use the store server address and its pairing phrase from SmartStock.");
        field(form,"Server address",host); field(form,"Secure API port",port); field(form,"Server pairing phrase",phrase);
        JButton discover = button("Find store servers",DeckersPalette.PURPLE);
        discover.addActionListener(e -> work("Finding store servers…",LanApiClient::discoverServers, servers -> {
            if (servers.isEmpty()) { status.setText("No server found. Enter its local address."); return; }
            var selected = (LanApiClient.DiscoveredServer) JOptionPane.showInputDialog(this,"Choose your store","Store servers",JOptionPane.PLAIN_MESSAGE,null,servers.toArray(),servers.get(0));
            if (selected != null) {host.setText(selected.host()); port.setText(String.valueOf(selected.port()));}
        }));
        JButton pair = button("Connect / check approval",DeckersPalette.ORANGE);
        pair.addActionListener(e -> {
            String address = host.getText().trim(), portText = port.getText().trim(); char[] code = phrase.getPassword(); phrase.setText("");
            work("Connecting securely…",() -> {
                try {
                    int apiPort = Integer.parseInt(portText);
                    if (apiPort < 1 || apiPort > 65535 || address.isBlank()) throw new IllegalArgumentException("Enter a server address and valid port.");
                    Integer assigned = address.equals(DatabaseConfig.load().serverHost()) ? DatabaseConfig.load().locationId() : null;
                    new DatabaseConfig(DatabaseMode.CLIENT,"","","",address,apiPort,assigned,60).save();
                    LanApiClient.configureEndpoint(address,apiPort);
                    boolean paired = code.length == 0 ? LanApiClient.claimApprovedCredential() || LanApiClient.isPaired() : "PAIRED".equals(LanApiClient.pairOnce(new String(code)).status());
                    if(paired)LanApiClient.verifyStudioPairing();
                    return paired ? LanApiClient.studioBranding() : null;
                } finally { Arrays.fill(code,'\0'); }
            }, brand -> {
                if (brand != null) { applyBrand(brand); cards.show(pages,"login"); status.setText("Connected. Sign in with your SmartStock staff account."); }
                else status.setText("Enable Allow Studio on this computer's Device Management row, then click check approval.");
            });
        });
        form.add(discover); form.add(Box.createVerticalStrut(12)); form.add(pair); return centered(form);
    }

    private JPanel loginPage() {
        JPanel form = form("Staff sign in", "Use the same account you use in SmartStock.");
        field(form,"Username, Email, or Badge ID",identifier); field(form,"Password or Employee PIN",password);
        JButton login = button("Sign in",DeckersPalette.ORANGE);
        login.addActionListener(e -> {
            String user = identifier.getText().trim(); char[] secret = password.getPassword(); password.setText("");
            work("Signing in…",() -> {
                try {
                    Integer location = DatabaseConfig.load().locationId();
                    if (location == null) throw new IllegalStateException("Pair this computer with your store first.");
                    LanApiClient.LoginResult session = LanApiClient.loginWithCredentials(user,secret,location);
                    return new LoginData(session,LanApiClient.studioBranding());
                } catch (Exception ex) { LanApiClient.logoutWithoutWaiting(); throw ex; }
                finally { Arrays.fill(secret,'\0'); }
            }, data -> {
                signedIn = true; idleMinutes = data.session.autoLogoutEnabled() ? Math.max(1,data.session.autoLogoutMinutes()) : 0;
                applyBrand(data.brand); cards.show(pages,"studio");
                status.setText("Signed in as " + data.session.user().fullName() + " • Choose a PNG or JPEG up to 6 MB.");
            });
        });
        password.addActionListener(e -> login.doClick()); form.add(login);
        JButton back = button("Connection settings",DeckersPalette.PURPLE); back.addActionListener(e -> cards.show(pages,"connect")); form.add(Box.createVerticalStrut(12)); form.add(back);
        return centered(form);
    }

    private JPanel studioPage() {
        JPanel panel = DeckersSwing.panel(); panel.setLayout(new BorderLayout(16,16));
        JPanel tools = DeckersSwing.panel(); tools.setLayout(new FlowLayout(FlowLayout.LEFT,12,0));
        tools.add(choose); tools.add(remove); tools.add(save); tools.add(copy);
        JButton logout = button("Sign out",DeckersPalette.PURPLE);
        JButton updates=button("Check for updates",DeckersPalette.PURPLE);
        updates.addActionListener(e -> checkUpdates());
        JPanel options = DeckersSwing.panel(); options.setLayout(new FlowLayout(FlowLayout.LEFT,12,6));
        options.add(DeckersSwing.metaLabel("Quality")); options.add(quality); quality.setSelectedItem(StudioQuality.BEST);
        quality.setToolTipText("Best quality uses a more detailed model and can take longer on the store server.");
        cleanEdges.setOpaque(false); cleanEdges.setForeground(DeckersPalette.text()); options.add(cleanEdges);
        cleanEdges.setToolTipText("Reduce background colour around uncertain edges. Turn off to compare the original cutout.");
        options.add(DeckersSwing.metaLabel("Preview background")); options.add(previewBackground);
        JPanel updateBar=DeckersSwing.panel();updateBar.setLayout(new FlowLayout(FlowLayout.RIGHT,12,0));updateBar.add(updates);updateBar.add(logout);
        original.setToolTipText("Drop one PNG or JPEG file here, including from a connected network drive.");
        original.setTransferHandler(new TransferHandler() {
            public boolean canImport(TransferSupport support) {
                if (busy || !StudioImageTransfer.supports(support.getTransferable())) return false;
                if (support.isDrop()) {
                    if ((support.getSourceDropActions() & COPY) == 0) return false;
                    support.setDropAction(COPY);
                }
                return true;
            }
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    // Native drop data expires when this callback returns.
                    var loader = StudioImageTransfer.capture(support.getTransferable());
                    loadSource(loader);
                    return true;
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> showError(ex));
                    return false;
                }
            }
        });
        previewBackground.addActionListener(e -> { result.backgroundMode=previewBackground.getSelectedIndex(); result.repaint(); });
        JPanel toolbar = DeckersSwing.panel(); toolbar.setLayout(new BorderLayout(0,8)); toolbar.add(tools,BorderLayout.NORTH); toolbar.add(options);
        toolbar.add(updateBar,BorderLayout.SOUTH);
        JPanel previews = new JPanel(new GridLayout(1,2,16,0)); previews.setOpaque(false); previews.add(original); previews.add(result);
        panel.add(toolbar,BorderLayout.NORTH); panel.add(previews); panel.add(DeckersSwing.metaLabel("Original-size transparent PNG • Best quality can take longer • Preview colours are not saved."),BorderLayout.SOUTH);
        choose.addActionListener(e -> selectImage()); remove.addActionListener(e -> {
            StudioQuality selected = (StudioQuality) quality.getSelectedItem(); boolean cleanup = cleanEdges.isSelected();
            work(selected == StudioQuality.BEST ? "Removing background with Best quality… This can take a few minutes." : "Removing background…",
                    () -> LanApiClient.removeStudioBackground(sourceBytes, selected, cleanup), bytes -> {
            try { BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes)); if (image == null) throw new IllegalStateException("The server returned an invalid image."); outputBytes = bytes; result.image=image; result.repaint(); status.setText(selected + (cleanup ? " • Edges cleaned" : "") + " • " + image.getWidth() + " × " + image.getHeight() + " • Ready to save PNG."); }
            catch (Exception ex) { showError(ex); }
        }); });
        copy.addActionListener(e -> {
            if (outputBytes == null || result.image == null) return;
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        StudioImageTransfer.result(result.image, outputBytes), null);
                status.setText("Result copied. Paste into an app that accepts images.");
            } catch (IllegalStateException ex) {
                showError(new IllegalStateException("The clipboard is busy. Please try Copy result again."));
            }
        });
        save.addActionListener(e -> saveImage()); logout.addActionListener(e -> signOut()); updateButtons(); return panel;
    }

    private void selectImage() {
        JFileChooser picker = new JFileChooser(); picker.setFileFilter(new FileNameExtensionFilter("PNG and JPEG images","png","jpg","jpeg"));
        if (picker.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = picker.getSelectedFile().toPath();
        loadImage(path);
    }

    private void checkUpdates() {
        work("Checking for SmartStudio updates…",StudioUpdates::check, release -> {
            if(release==null){status.setText("No newer SmartStudio release is available.");return;}
            if(JOptionPane.showConfirmDialog(this,"SmartStudio "+release.version()+" is available.\n"+
                    (release.releaseNotes()==null?"":release.releaseNotes())+"\nDownload this update?","SmartStudio update",
                    JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
            work("Downloading and verifying SmartStudio update…",()->StudioUpdates.download(release), installer -> {
                if(JOptionPane.showConfirmDialog(this,"The update is verified and ready.\nSmartStudio will close for installation.\nInstall now?",
                        "SmartStudio update",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION){status.setText("Update downloaded. Check for updates when ready to install.");return;}
                work("Starting SmartStudio update…",()->{StudioUpdates.install(installer,release);LanApiClient.logoutWithoutWaiting();return true;}, ignored->{
                    dispose();System.exit(0);
                });
            });
        });
    }

    void loadImage(Path path) {
        loadSource(() -> StudioImageFile.load(path));
    }

    private void loadSource(Callable<StudioImageFile.Loaded> loader) {
        if(busy)return;
        sourceBytes=null;outputBytes=null;original.image=null;result.image=null;original.repaint();result.repaint();
        work("Reading image…",loader, source -> {
            sourceBytes=source.bytes();outputBytes=null;fileName=source.name().replaceFirst("\\.[^.]+$","");
            original.image=source.image();result.image=null;original.repaint();result.repaint();
            status.setText("Image ready. Click Remove background.");
            SwingUtilities.invokeLater(imageReady);
        });
    }

    private void saveImage() {
        JFileChooser picker = new JFileChooser(); picker.setSelectedFile(new java.io.File(fileName+"-transparent.png"));
        if (picker.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path selected=picker.getSelectedFile().toPath(); Path path=selected.toString().toLowerCase().endsWith(".png") ? selected : Path.of(selected+".png");
        if (Files.exists(path) && JOptionPane.showConfirmDialog(this,"Replace " + path.getFileName() + "?","Save PNG",JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        work("Saving PNG…",() -> { Files.write(path,outputBytes); return path; }, saved -> status.setText("Saved " + saved.getFileName()));
    }

    private void signOut() { work("Signing out…",() -> { LanApiClient.logout(); return true; }, ignored -> { signedIn=false; sourceBytes=null; outputBytes=null; original.image=null; result.image=null; original.repaint(); result.repaint(); cards.show(pages,"login"); status.setText("Signed out."); }); }
    private void applyBrand(JsonObject brand) {
        company.setText(brand.get("name").getAsString()); motto.setText(brand.get("motto").getAsString());
        boolean bestAvailable = brand.has("studioBestQualityAvailable") && brand.get("studioBestQualityAvailable").getAsBoolean();
        quality.setModel(new DefaultComboBoxModel<>(bestAvailable ? StudioQuality.values() : new StudioQuality[]{StudioQuality.FAST}));
        quality.setSelectedItem(bestAvailable ? StudioQuality.BEST : StudioQuality.FAST);
        quality.setToolTipText(bestAvailable ? "Best quality can take longer on the store server." : "Ask an administrator to install Best quality in SmartStock: Status > AI Models.");
        if (brand.has("logo")) try { byte[] bytes=java.util.Base64.getDecoder().decode(brand.get("logo").getAsString().split(",",2)[1]); var image=ImageIO.read(new ByteArrayInputStream(bytes)); if(image!=null) logo.setIcon(new ImageIcon(DeckersLogoManager.scaleToFit(image,160,60))); } catch (Exception ignored) { }
    }
    private <T> void work(String message, Callable<T> task, Consumer<T> done) {
        if(busy)return; busy=true; status.setText(message);status.setForeground(DeckersPalette.muted());progress.setVisible(true);setEnabledTree(pages,false); updateButtons();
        new SwingWorker<T,Void>() {
            protected T doInBackground() throws Exception { return task.call(); }
            protected void done() { busy=false;progress.setVisible(false);setEnabledTree(pages,true);try {done.accept(get());}catch(Exception ex){showError(ex.getCause()==null?ex:ex.getCause());}updateButtons();lastActivity=System.currentTimeMillis(); }
        }.execute();
    }
    private void showError(Throwable ex) {
        if(checkOnly){ex.printStackTrace();dispose();System.exit(1);return;}
        if (ex instanceof LanApiClient.LanApiException api && (api.code().contains("SESSION") || api.code().equals("LOGIN_REQUIRED"))) {
            signedIn=false; sourceBytes=null; outputBytes=null; original.image=null; result.image=null; cards.show(pages,"login");
        }
        String message=ex.getMessage()==null?"The operation could not finish. Check your store connection.":ex.getMessage();
        status.setText(message.split("\\R",2)[0]);status.setForeground(DeckersPalette.CORAL);
        if(ex instanceof LanApiClient.LanApiException api && (api.code().equals("STUDIO_PAIRING_REQUIRED")||api.code().startsWith("DEVICE_")))cards.show(pages,"connect");
        JOptionPane.showMessageDialog(this,message,"SmartStudio",JOptionPane.ERROR_MESSAGE);
    }
    private void updateButtons() { choose.setEnabled(!busy); remove.setEnabled(!busy&&sourceBytes!=null); save.setEnabled(!busy&&outputBytes!=null); copy.setEnabled(!busy&&outputBytes!=null); }
    private static void setEnabledTree(Container parent,boolean enabled) { for(Component c:parent.getComponents()) { c.setEnabled(enabled); if(c instanceof Container child)setEnabledTree(child,enabled); } }
    private static JButton button(String text,Color accent) { JButton b=new JButton(text); DeckersSwing.styleUtilityButton(b,accent); return b; }
    private static JPanel form(String title,String description) { JPanel p=DeckersSwing.panel(); p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS)); p.setPreferredSize(new Dimension(490,440)); DeckersSwing.styleBand(p,DeckersPalette.ORANGE,new Insets(22,22,22,22)); JLabel heading=DeckersSwing.totalLabel(title,true); p.add(heading); p.add(Box.createVerticalStrut(12)); p.add(new JLabel(description)); p.add(Box.createVerticalStrut(18)); return p; }
    private static void field(JPanel panel,String label,JTextField field) { panel.add(DeckersSwing.metaLabel(label)); panel.add(Box.createVerticalStrut(6)); DeckersSwing.styleField(field); field.setMaximumSize(new Dimension(Integer.MAX_VALUE,36)); panel.add(field); panel.add(Box.createVerticalStrut(14)); }
    private static JPanel centered(JPanel form) { JPanel p=DeckersSwing.panel(); p.setLayout(new GridBagLayout()); p.add(form); return p; }
    private record LoginData(LanApiClient.LoginResult session,JsonObject brand) { }
    private static final class Preview extends JPanel {
        BufferedImage image; final String label; int backgroundMode;
        Preview(String label) { this.label=label; setBackground(DeckersPalette.surface()); setBorder(BorderFactory.createLineBorder(DeckersPalette.border())); }
        protected void paintComponent(Graphics g) { super.paintComponent(g); g.setColor(DeckersPalette.text()); g.setFont(new Font("SansSerif",Font.BOLD,16)); g.drawString(label,16,26);
            if(image==null) { g.setColor(DeckersPalette.muted()); g.setFont(new Font("SansSerif",Font.PLAIN,14)); g.drawString(getTransferHandler()!=null ? "Drop an image here or choose an image" : "Your image will appear here",16,62); return; }
            for(int y=40;y<getHeight();y+=16)for(int x=0;x<getWidth();x+=16){g.setColor(backgroundMode==1 ? Color.WHITE : backgroundMode==2 ? Color.BLACK : ((x/16+y/16)%2==0)?Color.WHITE:new Color(232,232,232));g.fillRect(x,y,16,16);}
            double scale=Math.min((getWidth()-24.0)/image.getWidth(),(getHeight()-64.0)/image.getHeight()); int w=(int)(image.getWidth()*scale),h=(int)(image.getHeight()*scale);
            Graphics2D draw=(Graphics2D)g.create(); draw.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR); draw.drawImage(image,(getWidth()-w)/2,40+(getHeight()-40-h)/2,w,h,null); draw.dispose();
        }
    }
}
