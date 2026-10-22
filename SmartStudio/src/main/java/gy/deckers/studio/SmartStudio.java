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
    private final Preview original = new Preview("Original image"), result = new Preview("Processed result");
    private final JComboBox<StudioQuality> quality = new JComboBox<>(StudioQuality.values());
    private final JCheckBox cleanEdges = new JCheckBox("Clean edges", true);
    private final JCheckBox removeBackground = new JCheckBox("Remove background", true);
    private final JComboBox<String> enhancement = new JComboBox<>(new String[]{"Original size", "2×", "4×"});
    private boolean enhancementAvailable;
    private boolean processingProgressAvailable;
    private String imageRequestId;
    private final JComboBox<String> processingMode=new JComboBox<>(new String[]{"Auto","CPU only","GPU preferred"});
    private final JButton cancelImage=button("Cancel processing",DeckersPalette.PURPLE);
    private final JComboBox<String> previewBackground = new JComboBox<>(new String[]{"Checkerboard", "White", "Black"});
    private final JComboBox<String> previewZoom = new JComboBox<>(new String[]{"Fit", "100%", "200%"});
    private final JButton choose = button("Choose image", DeckersPalette.PURPLE), remove = button("Process image", DeckersPalette.ORANGE), save = button("Save PNG", DeckersPalette.PURPLE);
    private final JButton copy = button("Copy result", DeckersPalette.PURPLE);
    private final JButton paste = button("Paste image", DeckersPalette.PURPLE);
    private byte[] sourceBytes, outputBytes;
    private String fileName = "image";
    private boolean busy, signedIn;
    private String capturedBadge;
    private javax.swing.Timer badgeListener;
    private boolean badgeReadPending;
    private String lastAutomaticBadge, lastBadgeError;
    private BadgeCardsPanel badgeCards;
    private BusinessCardsPanel businessCards;
    private BusinessCardsPanel labels;
    private BusinessCardsPanel banners;
    private BusinessCardsPanel billBooks;
    private PassportPhotosPanel passportPhotos;
    private StudioPhotoQueuePanel photoQueue;
    private Runnable chooseWorkspace=()->{};
    private Runnable imageReady = () -> { };
    private boolean checkOnly;

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
        processingMode.setSelectedIndex(2); // Prefer DirectML; server retains CPU fallback.
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(820, 580)); setSize(1100, 760); setLocationRelativeTo(null);
        JPanel root = DeckersSwing.panel(); root.setLayout(new BorderLayout(8,8)); root.setBorder(BorderFactory.createEmptyBorder(8,10,8,10));
        JPanel header = DeckersSwing.panel(); header.setLayout(new BorderLayout(16,0));
        var deckersLogo = DeckersLogoManager.loadDeckersLogoIcon(SmartStudio.class);
        if (deckersLogo != null) logo.setIcon(new ImageIcon(DeckersLogoManager.scaleToFit(deckersLogo.getImage(),80,30)));
        var studioIcon = SmartStudio.class.getResource("icons/SmartStudioIconLight.png");
        if (studioIcon != null) {
            Image image = new ImageIcon(studioIcon).getImage();
            setIconImages(java.util.List.of(DeckersLogoManager.scaleToFit(image,16,16),
                    DeckersLogoManager.scaleToFit(image,32,32),DeckersLogoManager.scaleToFit(image,48,48),
                    DeckersLogoManager.scaleToFit(image,256,256)));
        }
        header.add(logo, BorderLayout.WEST);
        JPanel titles = DeckersSwing.panel(); titles.setLayout(new GridLayout(2,1));
        company.setFont(new Font("SansSerif",Font.BOLD,16)); company.setForeground(DeckersPalette.text());
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
            if (badgeCards != null && !badgeCards.discard()) return;
            if (billBooks != null && !billBooks.discard()) return;
            if (businessCards != null && !businessCards.discard()) return;
            if(labels!=null&&!labels.discard())return;if(banners!=null&&!banners.discard())return;
            work("Closing…", () -> { LanApiClient.logoutWithoutWaiting(); return true; }, ignored -> dispose());
        }});
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
        if(badgeListener!=null)badgeListener.stop();
        if(badgeCards!=null)stopTemplateLibraries();
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
        form.add(discover); form.add(Box.createVerticalStrut(12)); form.add(pair);
        JButton offline=button("Work offline — cached templates",DeckersPalette.PURPLE);offline.addActionListener(e->{useTemplateLibraries(true);chooseWorkspace.run();cards.show(pages,"studio");status.setText("OFFLINE — edits are temporary and cannot be saved.");});form.add(offline);return centered(form);
    }

    private JPanel loginPage() {
        JPanel form = form("Staff sign in", "Use the same account you use in SmartStock.");
        field(form,"Username, Email, or Badge ID",identifier); field(form,"Password or Employee PIN",password);
        JButton login = button("Sign in",DeckersPalette.ORANGE);
        login.addActionListener(e -> {
            String user = capturedBadge==null?identifier.getText().trim():capturedBadge; char[] secret = password.getPassword(); password.setText("");
            work("Signing in…",() -> {
                try {
                    Integer location = DatabaseConfig.load().locationId();
                    if (location == null) throw new IllegalStateException("Pair this computer with your store first.");
                    LanApiClient.LoginResult session = LanApiClient.loginWithCredentials(user,secret,location);
                    return new LoginData(session,LanApiClient.studioBranding());
                } catch (Exception ex) { LanApiClient.logoutWithoutWaiting(); throw ex; }
                finally { Arrays.fill(secret,'\0'); }
            }, data -> {
                signedIn = true;
                capturedBadge=null;identifier.setText("");identifier.setEditable(true);
                applyBrand(data.brand);useTemplateLibraries(false); chooseWorkspace.run(); cards.show(pages,"studio");
                status.setText("Signed in as " + data.session.user().fullName() + " • Choose a template or image workspace.");
            });
        });
        password.addActionListener(e -> login.doClick()); form.add(login);
        JButton tap=button("NFC reader listens automatically",DeckersPalette.PURPLE);
        Consumer<StudioBadgeCapture.Captured> acceptBadge=data->{
            capturedBadge=data.identifier();identifier.setText("NFC badge detected");identifier.setEditable(false);password.setText("");
            LanApiClient.BadgeStatus policy=data.policy();
            if(!policy.pinRequired())login.doClick();
            else if(!policy.pinConfigured())setupBadgePin(capturedBadge);
            else {status.setText("Badge detected. Enter your Employee PIN, then Sign in.");password.requestFocusInWindow();}
        };
        tap.addActionListener(event->{lastAutomaticBadge=null;lastBadgeError=null;password.requestFocusInWindow();});form.add(tap);
        badgeListener=new javax.swing.Timer(750,event->{
            if(!form.isShowing()||signedIn||busy||badgeReadPending)return;
            Integer location=DatabaseConfig.load().locationId();if(location==null)return;
            badgeReadPending=true;
            String previousBadge=lastAutomaticBadge;
            new SwingWorker<StudioBadgeCapture.Captured,Void>(){
                protected StudioBadgeCapture.Captured doInBackground()throws Exception{
                    return StudioBadgeCapture.poll(location,previousBadge);
                }
                protected void done(){
                    badgeReadPending=false;
                    if(!form.isShowing()||signedIn||busy)return;
                    try{
                        var data=get();if(data==null)return;
                        lastBadgeError=null;
                        if(data.identifier().equals(lastAutomaticBadge))return;
                        lastAutomaticBadge=data.identifier();acceptBadge.accept(data);
                    }catch(Exception failure){
                        Throwable cause=failure.getCause()==null?failure:failure.getCause();
                        if(cause instanceof services.PcscNfcService.NoCardPresentException){lastAutomaticBadge=null;return;}
                        String message=cause.getMessage();
                        if(!java.util.Objects.equals(lastBadgeError,message)){lastBadgeError=message;status.setText("NFC: "+message);}
                    }
                }
            }.execute();
        });badgeListener.start();
        JButton manual=button("Use username and password",DeckersPalette.PURPLE);
        manual.addActionListener(event->{capturedBadge=null;identifier.setText("");identifier.setEditable(true);password.setText("");identifier.requestFocusInWindow();status.setText("Enter your staff username and password.");});form.add(manual);

        JButton offline=button("Work offline — cached templates",DeckersPalette.PURPLE);offline.addActionListener(e->{useTemplateLibraries(true);chooseWorkspace.run();cards.show(pages,"studio");status.setText("OFFLINE — edits are temporary and cannot be saved.");});form.add(offline);
        JButton back = button("Connection settings",DeckersPalette.PURPLE); back.addActionListener(e -> cards.show(pages,"connect")); form.add(Box.createVerticalStrut(12)); form.add(back);
        return centered(form);
    }

    private void setupBadgePin(String badge) {
        JPasswordField account=new JPasswordField(),pin=new JPasswordField(),confirm=new JPasswordField();
        JPanel panel=new JPanel(new GridLayout(0,2,6,6));
        panel.add(new JLabel("Account password"));panel.add(account);panel.add(new JLabel("New employee PIN"));panel.add(pin);panel.add(new JLabel("Confirm PIN"));panel.add(confirm);
        if(JOptionPane.showConfirmDialog(this,panel,"First badge login: set employee PIN",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION){status.setText("Badge PIN setup cancelled. Use your username and password to sign in.");return;}
        char[] passwordValue=account.getPassword(),pinValue=pin.getPassword(),confirmation=confirm.getPassword();
        account.setText("");pin.setText("");confirm.setText("");
        boolean matches=Arrays.equals(pinValue,confirmation);Arrays.fill(confirmation,'\0');
        if(!matches){Arrays.fill(passwordValue,'\0');Arrays.fill(pinValue,'\0');status.setText("PINs did not match. Tap the badge again to retry setup.");return;}
        work("Setting employee PIN…",()->{
            try {Integer location=DatabaseConfig.load().locationId();if(location==null)throw new IllegalStateException("Pair this computer first.");
                var session=LanApiClient.setupBadgePin(badge,passwordValue,pinValue,location);
                return new LoginData(session,LanApiClient.studioBranding());
            }catch(Exception ex){LanApiClient.logoutWithoutWaiting();throw ex;}
            finally{Arrays.fill(passwordValue,'\0');Arrays.fill(pinValue,'\0');}
        },data->{signedIn=true;capturedBadge=null;identifier.setText("");identifier.setEditable(true);applyBrand(data.brand);useTemplateLibraries(false);chooseWorkspace.run();cards.show(pages,"studio");status.setText("Signed in as "+data.session.user().fullName());});
    }

    private JPanel studioPage() {
        JPanel panel = DeckersSwing.panel(); panel.setLayout(new BorderLayout(16,16));
        JPanel tools = DeckersSwing.panel(); tools.setLayout(new FlowLayout(FlowLayout.LEFT,12,0));
        tools.add(choose); tools.add(paste); tools.add(remove); tools.add(save); tools.add(copy);
        JButton print=button("Print (Ctrl+P)",DeckersPalette.PURPLE);tools.add(print);
        print.addActionListener(e -> printImage());
        paste.setToolTipText("In WhatsApp Web, right-click the photo and choose Copy image, then paste here.");
        paste.addActionListener(e -> pasteImage());
        panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "paste-image");
        panel.getActionMap().put("paste-image",new AbstractAction(){public void actionPerformed(ActionEvent e){pasteImage();}});
        JButton logout = button("Sign out",DeckersPalette.PURPLE);
        JButton updates=button("Check for updates",DeckersPalette.PURPLE);
        updates.addActionListener(e -> checkUpdates());
        JPanel options = DeckersSwing.panel(); options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
        JPanel processOptions = DeckersSwing.panel(); processOptions.setLayout(new FlowLayout(FlowLayout.LEFT,12,6));
        JPanel viewOptions = DeckersSwing.panel(); viewOptions.setLayout(new FlowLayout(FlowLayout.LEFT,12,6));
        options.add(processOptions); options.add(viewOptions);
        removeBackground.setOpaque(false); removeBackground.setForeground(DeckersPalette.text());
        processOptions.add(removeBackground); processOptions.add(DeckersSwing.metaLabel("Enhance")); processOptions.add(enhancement);
        JPanel runtimeOptions=DeckersSwing.panel();runtimeOptions.setLayout(new FlowLayout(FlowLayout.LEFT,12,6));
        runtimeOptions.add(DeckersSwing.metaLabel("Processing"));runtimeOptions.add(processingMode);runtimeOptions.add(cancelImage);options.add(runtimeOptions);
        cancelImage.addActionListener(e->{String id=imageRequestId;if(id==null)return;cancelImage.setEnabled(false);status.setText("Cancellation requested…");
            new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{LanApiClient.studioProgress(id,true);return null;}
                protected void done(){try{get();}catch(Exception ex){showError(ex.getCause()==null?ex:ex.getCause());}}}.execute();});
        removeBackground.addActionListener(e -> updateButtons()); enhancement.addActionListener(e -> updateButtons());
        processOptions.add(DeckersSwing.metaLabel("Removal quality")); processOptions.add(quality); quality.setSelectedItem(StudioQuality.BEST);
        quality.setToolTipText("Best quality uses a more detailed model and can take longer on the store server.");
        cleanEdges.setOpaque(false); cleanEdges.setForeground(DeckersPalette.text()); processOptions.add(cleanEdges);
        cleanEdges.setToolTipText("Reduce background colour around uncertain edges. Turn off to compare the original cutout.");
        viewOptions.add(DeckersSwing.metaLabel("Preview background")); viewOptions.add(previewBackground);
        viewOptions.add(DeckersSwing.metaLabel("Zoom")); viewOptions.add(previewZoom);
        previewZoom.addActionListener(e -> {
            double zoom = previewZoom.getSelectedIndex() == 0 ? 0 : previewZoom.getSelectedIndex();
            original.zoom = zoom; result.zoom = zoom;
            original.revalidate(); result.revalidate(); original.repaint(); result.repaint();
        });
        JPanel updateBar=DeckersSwing.panel();updateBar.setLayout(new FlowLayout(FlowLayout.RIGHT,12,0));updateBar.add(updates);updateBar.add(logout);
        original.setToolTipText("Drop one PNG or JPEG file here, including from a connected network drive.");
        original.setTransferHandler(new TransferHandler() {
            public boolean canImport(TransferSupport support) {
                // Inspect advertised formats without requesting a native Transferable during dragOver.
                if (busy || !(support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)
                        || support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.imageFlavor)
                        || support.isDataFlavorSupported(StudioImageTransfer.PNG))) return false;
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
                    // Finish the native drop before changing controls or starting SwingWorker.
                    SwingUtilities.invokeLater(() -> loadSource(loader));
                    return true;
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> showError(ex));
                    return false;
                }
            }
        });
        StudioImageDrop.install(original, () -> busy, this::loadSource, this::showError);
        previewBackground.addActionListener(e -> { result.backgroundMode=previewBackground.getSelectedIndex(); result.repaint(); });
        JPanel previews = new JPanel(new GridLayout(1,1)); previews.setOpaque(false);
        JScrollPane originalScroll=new JScrollPane(original),resultScroll=new JScrollPane(result);previews.add(originalScroll);
        StudioWorkspaceShell imageShell=new StudioWorkspaceShell(previews);
        tools.remove(remove);tools.remove(save);tools.remove(copy);tools.remove(print);imageShell.addTools(tools);
        JPanel imageOutput=DeckersSwing.panel();imageOutput.setLayout(new GridLayout(0,1));imageOutput.add(save);imageOutput.add(copy);imageOutput.add(print);imageShell.addTools(StudioWorkspaceShell.menu("Output",imageOutput));
        JComboBox<String> imageView=new JComboBox<>(new String[]{"Original","Result","Compare"});imageView.setToolTipText("Image preview view");
        imageView.addActionListener(e->{previews.removeAll();int view=imageView.getSelectedIndex();previews.setLayout(new GridLayout(1,view==2?2:1,12,0));if(view!=1)previews.add(originalScroll);if(view!=0)previews.add(resultScroll);previews.revalidate();previews.repaint();});
        imageShell.addTools(imageView);
        StudioWorkspaceShell.stack(processOptions);StudioWorkspaceShell.stack(runtimeOptions);StudioWorkspaceShell.stack(viewOptions);
        options.add(remove);imageShell.addSection("Image tools",new JScrollPane(options));imageShell.showInspector(true);
        panel.add(imageShell);panel.add(DeckersSwing.metaLabel("Processing stays on the store server • Inspect faces and lettering before printing."),BorderLayout.SOUTH);
        choose.addActionListener(e -> selectImage()); remove.addActionListener(e -> {
            StudioQuality selected = (StudioQuality) quality.getSelectedItem(); boolean cleanup = cleanEdges.isSelected();
            boolean cutout = removeBackground.isSelected(); int scale = enhancement.getSelectedIndex() == 0 ? 1 : enhancement.getSelectedIndex() == 1 ? 2 : 4;
            String mode=new String[]{"AUTO","CPU","GPU"}[processingMode.getSelectedIndex()];
            imageRequestId=processingProgressAvailable&&scale>1?java.util.UUID.randomUUID().toString():null;
            String requestId=imageRequestId;
            work(scale > 1 ? "Preparing enhancement…" : "Removing background…",
                    () -> {try{return requestId==null?LanApiClient.transformStudioPhoto(sourceBytes,selected,cleanup,cutout,scale):
                        LanApiClient.transformStudioPhoto(sourceBytes,selected,cleanup,cutout,scale,mode,requestId,
                            info->SwingUtilities.invokeLater(()->showImageProgress(requestId,info)));}
                        finally{SwingUtilities.invokeLater(()->{if(java.util.Objects.equals(imageRequestId,requestId)){imageRequestId=null;cancelImage.setEnabled(false);progress.setIndeterminate(true);}});}}, bytes -> {
            try { BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes)); if (image == null) throw new IllegalStateException("The server returned an invalid image.");
                if (image.getWidth() != original.image.getWidth() * scale || image.getHeight() != original.image.getHeight() * scale)
                    throw new IllegalStateException("The server did not return the requested scale. Update the store server before enhancing.");
                outputBytes = bytes; result.image=image; result.revalidate(); result.repaint(); status.setText((cutout ? "Background removed • " : "") + scale + "× • " + image.getWidth() + " × " + image.getHeight()
                        + String.format(java.util.Locale.ROOT, " • %.1f × %.1f inches at 300 PPI • Ready to save PNG.", image.getWidth()/300.0, image.getHeight()/300.0)); }
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
        save.addActionListener(e -> saveImage()); logout.addActionListener(e -> signOut()); updateButtons();
        badgeCards = new BadgeCardsPanel();
        businessCards = new BusinessCardsPanel();
        billBooks=new BusinessCardsPanel(false,false,true);labels=new BusinessCardsPanel(true);banners=new BusinessCardsPanel(false,true);
        CardLayout editorLayout=new CardLayout();JPanel sections=DeckersSwing.panel();sections.setLayout(editorLayout);
        passportPhotos=new PassportPhotosPanel();sections.add(passportPhotos,"Passport Pictures");sections.add(panel,"Image Studio");sections.add(badgeCards,"Badge Cards");sections.add(businessCards,"Business Cards");sections.add(labels,"Labels");sections.add(banners,"Banners");sections.add(billBooks,"Bill Books");
        JPanel chooser=DeckersSwing.panel();chooser.setLayout(new GridBagLayout());
        JPanel choices=DeckersSwing.panel();choices.setLayout(new GridLayout(0,1,12,12));
        choices.add(DeckersSwing.totalLabel("Choose your workspace",true));
        JLabel active=new JLabel("Choose a template or image workspace");
        final String[] selected={"Choose"};
        photoQueue=new StudioPhotoQueuePanel(photo->{selected[0]="Passport Pictures";active.setText("Passport Pictures");editorLayout.show(sections,"Passport Pictures");passportPhotos.useQueued(photo);});
        sections.add(photoQueue,"Photo Queue");
        for(String type:new String[]{"Photo Queue","Badge Cards","Business Cards","Labels","Banners","Bill Books","Passport Pictures","Image Studio"})choices.add(StudioWorkspaceShell.button(type,()->{if(type.equals("Badge Cards")&&!badgeCards.chooseTemplate())return;if(type.equals("Business Cards")&&!businessCards.chooseTemplate())return;if(type.equals("Labels")&&!labels.chooseTemplate())return;if(type.equals("Bill Books")&&!billBooks.chooseTemplate())return;if(type.equals("Banners")&&!banners.chooseTemplate())return;selected[0]=type;active.setText(type);editorLayout.show(sections,type);}));
        choices.add(DeckersSwing.metaLabel("Open or change designs using the Template menu inside your workspace."));
        chooser.add(choices);sections.add(chooser,"Choose");editorLayout.show(sections,"Choose");
        chooseWorkspace=()->editorLayout.show(sections,"Choose");
        JPanel navigation=DeckersSwing.panel();navigation.setLayout(new BorderLayout(8,0));
        JPanel change=DeckersSwing.panel();change.setLayout(new FlowLayout(FlowLayout.LEFT,8,0));
        change.add(StudioWorkspaceShell.button("Change template / workspace",()->editorLayout.show(sections,"Choose")));change.add(active);navigation.add(change);
        JPanel account=DeckersSwing.panel();account.setLayout(new GridLayout(0,1));account.add(updates);JButton fonts=button("Shared fonts…",DeckersPalette.PURPLE);fonts.addActionListener(e->SharedFontLibrary.show(this));account.add(fonts);account.add(logout);navigation.add(StudioWorkspaceShell.menu("Account",account),BorderLayout.EAST);
        JPanel workspace = DeckersSwing.panel(); workspace.setLayout(new BorderLayout(8,8)); workspace.add(navigation,BorderLayout.NORTH); workspace.add(sections);
        workspace.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_P,InputEvent.CTRL_DOWN_MASK),"print");
        workspace.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_P,Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),"print");
        workspace.getActionMap().put("print",new AbstractAction(){public void actionPerformed(ActionEvent e){if(!chooser.isVisible()){if(selected[0].equals("Badge Cards"))badgeCards.printCard();else if(selected[0].equals("Business Cards"))businessCards.printCards();else if(selected[0].equals("Labels"))labels.printCards();else if(selected[0].equals("Bill Books"))billBooks.printCards();else if(selected[0].equals("Banners"))banners.printCards();else if(selected[0].equals("Passport Pictures"))passportPhotos.printPhotos();else if(!selected[0].equals("Photo Queue"))printImage();}}});
        return workspace;
    }

    private void printImage() {
        if(busy){showError(new IllegalStateException("Wait for image processing to finish before printing."));return;}
        BufferedImage image=result.image!=null?result.image:original.image;
        if(image==null){showError(new IllegalStateException("Choose an image before printing."));return;}
        try {StudioPrinting.print(this,image,fileName,0,0,status::setText);}
        catch(Exception ex){showError(ex);}
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
            original.image=source.image();result.image=null;original.revalidate();result.revalidate();original.repaint();result.repaint();
            status.setText("Image ready. Choose background removal, enhancement, or both, then Process image.");
            SwingUtilities.invokeLater(imageReady);
        });
    }

    private void saveImage() {
        JFileChooser picker = new JFileChooser(); picker.setSelectedFile(new java.io.File(fileName+"-processed.png"));
        if (picker.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path selected=picker.getSelectedFile().toPath(); Path path=selected.toString().toLowerCase().endsWith(".png") ? selected : Path.of(selected+".png");
        if (Files.exists(path) && JOptionPane.showConfirmDialog(this,"Replace " + path.getFileName() + "?","Save PNG",JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        work("Saving PNG…",() -> { Files.write(path,outputBytes); return path; }, saved -> status.setText("Saved " + saved.getFileName()));
    }

    private void useTemplateLibraries(boolean offline){SharedFontLibrary.start(offline);badgeCards.useSharedLibrary(offline);businessCards.useSharedLibrary(offline);labels.useSharedLibrary(offline);banners.useSharedLibrary(offline);billBooks.useSharedLibrary(offline);}
    private void stopTemplateLibraries(){SharedFontLibrary.stop();if(badgeCards!=null)badgeCards.stopSharedLibrary();if(businessCards!=null)businessCards.stopSharedLibrary();if(labels!=null)labels.stopSharedLibrary();if(banners!=null)banners.stopSharedLibrary();if(billBooks!=null)billBooks.stopSharedLibrary();}
    private void signOut() {photoQueue.reset();passportPhotos.reset();capturedBadge=null;identifier.setText("");identifier.setEditable(true);stopTemplateLibraries(); work("Signing out…",() -> { LanApiClient.logout(); return true; }, ignored -> { signedIn=false; sourceBytes=null; outputBytes=null; original.image=null; result.image=null; badgeCards.resetFields(); businessCards.resetFields(); labels.resetFields(); banners.resetFields(); billBooks.resetFields(); original.repaint(); result.repaint(); cards.show(pages,"login"); status.setText("Signed out."); }); }
    private void applyBrand(JsonObject brand) {
        company.setText(brand.get("name").getAsString()); motto.setText(brand.get("motto").getAsString());
        boolean bestAvailable = brand.has("studioBestQualityAvailable") && brand.get("studioBestQualityAvailable").getAsBoolean();
        enhancementAvailable = brand.has("studioEnhancementAvailable") && brand.get("studioEnhancementAvailable").getAsBoolean();
        processingProgressAvailable=brand.has("studioProgressAvailable")&&brand.get("studioProgressAvailable").getAsBoolean();
        enhancement.setSelectedIndex(0);
        enhancement.setToolTipText(enhancementAvailable ? "2× or 4× enlargement using Real-ESRGAN on the store CPU."
                : "Ask an administrator to install Image enhancement in SmartStock: Status > AI Models, then sign in again.");
        quality.setModel(new DefaultComboBoxModel<>(bestAvailable ? StudioQuality.values() : new StudioQuality[]{StudioQuality.FAST}));
        quality.setSelectedItem(bestAvailable ? StudioQuality.BEST : StudioQuality.FAST);
        quality.setToolTipText(bestAvailable ? "Best quality can take longer on the store server." : "Ask an administrator to install Best quality in SmartStock: Status > AI Models.");
        if (brand.has("logo")) try { byte[] bytes=java.util.Base64.getDecoder().decode(brand.get("logo").getAsString().split(",",2)[1]); var image=ImageIO.read(new ByteArrayInputStream(bytes)); if(image!=null) logo.setIcon(new ImageIcon(DeckersLogoManager.scaleToFit(image,160,60))); } catch (Exception ignored) { }
    }
    private <T> void work(String message, Callable<T> task, Consumer<T> done) {
        if(busy)return; busy=true; status.setText(message);status.setForeground(DeckersPalette.muted());progress.setVisible(true);setEnabledTree(pages,false); updateButtons();
        new SwingWorker<T,Void>() {
            protected T doInBackground() throws Exception { return task.call(); }
            protected void done() { busy=false;progress.setVisible(false);setEnabledTree(pages,true);try {done.accept(get());}catch(Exception ex){showError(ex.getCause()==null?ex:ex.getCause());}updateButtons(); }
        }.execute();
    }
    private void showImageProgress(String id,JsonObject info){
        if(!busy||!java.util.Objects.equals(imageRequestId,id)||!"running".equals(info.get("state").getAsString()))return;
        int total=info.get("total").getAsInt(),done=info.get("completed").getAsInt();
        progress.setIndeterminate(total==0);if(total>0){progress.setMaximum(total);progress.setValue(done);}
        String note=info.get("note").getAsString(),device=info.get("device").getAsString();
        long seconds=info.get("remainingSeconds").getAsLong();
        status.setText((note.isBlank()?"Enhancing":note)+(device.isBlank()?"":" • "+device)+(total==0?"":" • "+info.get("percent").getAsInt()+"% ("+done+"/"+total+" tiles)")+(seconds<0?"":" • about "+Math.max(1,(seconds+59)/60)+" min remaining"));
    }
    private void pasteImage() {
        if(busy || !signedIn)return;
        try {
            var source=Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if(source==null || !StudioImageTransfer.supports(source))
                throw new java.io.IOException("Copy the photo itself first: in WhatsApp Web, right-click the opened photo and choose Copy image, then click Paste image here.");
            loadSource(StudioImageTransfer.capture(source));
        } catch(Exception ex){showError(ex);}
    }

    private void showError(Throwable ex) {
        if(checkOnly){ex.printStackTrace();dispose();System.exit(1);return;}
        if (ex instanceof LanApiClient.LanApiException api && (api.code().contains("SESSION") || api.code().equals("LOGIN_REQUIRED"))) {
            stopTemplateLibraries();
            signedIn=false; sourceBytes=null; outputBytes=null; original.image=null; result.image=null; cards.show(pages,"login");
            badgeCards.resetFields(); businessCards.resetFields(); labels.resetFields(); banners.resetFields(); billBooks.resetFields();
        }
        String message=ex.getMessage()==null?"The operation could not finish. Check your store connection.":ex.getMessage();
        status.setText(message.split("\\R",2)[0]);status.setForeground(DeckersPalette.CORAL);
        if(ex instanceof LanApiClient.LanApiException api && (api.code().equals("STUDIO_PAIRING_REQUIRED")||api.code().startsWith("DEVICE_")))cards.show(pages,"connect");
        JOptionPane.showMessageDialog(this,message,"SmartStudio",JOptionPane.ERROR_MESSAGE);
    }
    private void updateButtons() {
        processingMode.setEnabled(!busy&&processingProgressAvailable);
        cancelImage.setEnabled(busy&&imageRequestId!=null);
        paste.setEnabled(!busy);
        choose.setEnabled(!busy); enhancement.setEnabled(!busy&&enhancementAvailable);
        quality.setEnabled(!busy&&removeBackground.isSelected()); cleanEdges.setEnabled(!busy&&removeBackground.isSelected());
        remove.setEnabled(!busy&&sourceBytes!=null&&(removeBackground.isSelected()||enhancement.getSelectedIndex()>0));
        save.setEnabled(!busy&&outputBytes!=null); copy.setEnabled(!busy&&outputBytes!=null);
    }
    private static void setEnabledTree(Container parent,boolean enabled) { for(Component c:parent.getComponents()) { c.setEnabled(enabled); if(c instanceof Container child)setEnabledTree(child,enabled); } }
    private static JButton button(String text,Color accent) { JButton b=new JButton(text); DeckersSwing.styleUtilityButton(b,accent); return b; }
    private static JPanel form(String title,String description) { JPanel p=DeckersSwing.panel(); p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS)); p.setPreferredSize(new Dimension(490,440)); DeckersSwing.styleBand(p,DeckersPalette.ORANGE,new Insets(22,22,22,22)); JLabel heading=DeckersSwing.totalLabel(title,true); p.add(heading); p.add(Box.createVerticalStrut(12)); p.add(new JLabel(description)); p.add(Box.createVerticalStrut(18)); return p; }
    private static void field(JPanel panel,String label,JTextField field) { panel.add(DeckersSwing.metaLabel(label)); panel.add(Box.createVerticalStrut(6)); DeckersSwing.styleField(field); field.setMaximumSize(new Dimension(Integer.MAX_VALUE,36)); panel.add(field); panel.add(Box.createVerticalStrut(14)); }
    private static JPanel centered(JPanel form) { JPanel p=DeckersSwing.panel(); p.setLayout(new GridBagLayout()); p.add(form); return p; }
    private record LoginData(LanApiClient.LoginResult session,JsonObject brand) { }
    private static final class Preview extends JPanel implements Scrollable {
        BufferedImage image; final String label; int backgroundMode; double zoom;
        Preview(String label) { this.label=label; setBackground(DeckersPalette.surface()); setBorder(BorderFactory.createLineBorder(DeckersPalette.border())); }
        protected void paintComponent(Graphics g) { super.paintComponent(g); g.setColor(DeckersPalette.text()); g.setFont(new Font("SansSerif",Font.BOLD,16)); g.drawString(label,16,26);
            if(image==null) { g.setColor(DeckersPalette.muted()); g.setFont(new Font("SansSerif",Font.PLAIN,14)); g.drawString(getTransferHandler()!=null ? "Drop an image here or choose an image" : "Your image will appear here",16,62); return; }
            Rectangle clip = g.getClipBounds();
            for(int y=Math.max(40,clip.y/16*16);y<Math.min(getHeight(),clip.y+clip.height);y+=16)for(int x=Math.max(0,clip.x/16*16);x<Math.min(getWidth(),clip.x+clip.width);x+=16){g.setColor(backgroundMode==1 ? Color.WHITE : backgroundMode==2 ? Color.BLACK : ((x/16+y/16)%2==0)?Color.WHITE:new Color(232,232,232));g.fillRect(x,y,16,16);}
            double scale=zoom > 0 ? zoom : Math.min((getWidth()-24.0)/image.getWidth(),(getHeight()-64.0)/image.getHeight()); int w=(int)(image.getWidth()*scale),h=(int)(image.getHeight()*scale);
            Graphics2D draw=(Graphics2D)g.create(); draw.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR); draw.drawImage(image,zoom > 0 ? 12 : (getWidth()-w)/2,zoom > 0 ? 40 : 40+(getHeight()-40-h)/2,w,h,null); draw.dispose();
        }
        public Dimension getPreferredSize() { return image != null && zoom > 0 ? new Dimension((int)(image.getWidth()*zoom)+24,(int)(image.getHeight()*zoom)+64) : new Dimension(360,300); }
        public Dimension getPreferredScrollableViewportSize() { return new Dimension(360,300); }
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 24; }
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return orientation == SwingConstants.HORIZONTAL ? visible.width : visible.height; }
        public boolean getScrollableTracksViewportWidth() { return zoom == 0; }
        public boolean getScrollableTracksViewportHeight() { return zoom == 0; }
    }
}
