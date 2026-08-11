package ui.screens;

import data.DatabaseConfig;
import data.DatabaseMode;
import data.EnvironmentProfile;
import services.LanApiClient;
import services.LanApiServer;
import services.PcscNfcService;
import services.PostgresRuntimeService;
import services.ServerStoreSetupService;
import services.ServerSupabaseCredentials;
import services.ServerFirstAdministratorService;
import managers.SupabaseSessionManager;
import ui.design.DeckersLogoManager;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.ThemeManager;
import ui.helpers.WelcomeGreetingHelper;
import utils.DeviceUtils;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;

public class WelcomeFrame extends JFrame {

    private final JLabel greetingLabel = new JLabel();
    private final JLabel subtitleLabel = new JLabel();
    private final JLabel statusLabel = new JLabel("Status: Ready to refresh");
    private final JLabel localDbLabel = new JLabel("Local DB: Not checked");
    private final JLabel onlineDbLabel = new JLabel("Online DB: Not checked");
    private final JLabel systemStatsLabel = new JLabel();
    private final JLabel modeLabel = new JLabel();
    private final JLabel deckersLogoLabel = new JLabel("Deckers", SwingConstants.CENTER);
    private final JLabel smartStockLogoLabel = new JLabel("SmartStock", SwingConstants.CENTER);
    private final JButton refreshStatusBtn = new JButton(createRefreshIcon());
    private final JButton startProcessesBtn = new JButton("Start Server Processes");
    private final JButton setupBtn = new JButton("Guided Setup");
    private final JButton environmentBtn = new JButton("Switch Environment");
    private final JButton pairRegisterBtn = new JButton("Administrator: Pair This Register");
    private final JButton serverAddressBtn = new JButton("Set SmartStock Server Address");
    private final JButton recoverRegisterBtn = new JButton("Recover at Another Store");
    private final JButton syncStatusBtn = new JButton("Sync Status");
    private final JButton continueBtn = new JButton("Log In");
    private Timer displayRefreshTimer;
    private Timer systemStatusRefreshTimer;
    private boolean systemStatusRefreshInProgress;
    private boolean startupDatabaseRecoveryPending = true;
    private boolean initialSetupWindowOpened;
    private volatile boolean nfcMonitorRunning;
    private volatile boolean loginAvailable;
    private final boolean openedAfterConnectionLoss;
    private String lastGreetingKey = "";
    private record SystemStatus(String localStatus, String onlineStatus, String message,
                                boolean canLogin, boolean setupRequired,
                                boolean setupVisible) {}

    public WelcomeFrame() {
        this(false);
    }

    public WelcomeFrame(boolean openedAfterConnectionLoss) {
        super("SmartStock Welcome");
        this.openedAfterConnectionLoss = openedAfterConnectionLoss;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(960, 650);
        setMinimumSize(new Dimension(860, 600));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(18, 18));
        root.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
        root.setBackground(DeckersPalette.background());
        root.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);

        JPanel heroPanel = new JPanel(new BorderLayout(18, 0));
        DeckersSwing.styleBand(heroPanel, DeckersPalette.ORANGE, new Insets(22, 22, 22, 22));

        JPanel logoPanel = new JPanel(new BorderLayout());
        logoPanel.setOpaque(false);
        deckersLogoLabel.setPreferredSize(new Dimension(300, 130));
        deckersLogoLabel.setForeground(DeckersPalette.text());
        deckersLogoLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        deckersLogoLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        setDeckersLogo();
        logoPanel.add(deckersLogoLabel, BorderLayout.CENTER);

        JPanel greetingPanel = new JPanel();
        greetingPanel.setOpaque(false);
        greetingPanel.setLayout(new BoxLayout(greetingPanel, BoxLayout.Y_AXIS));
        greetingLabel.setFont(new Font("SansSerif", Font.BOLD, 30));
        greetingLabel.setForeground(DeckersPalette.text());
        greetingLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
        subtitleLabel.setForeground(DeckersPalette.muted());
        subtitleLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        smartStockLogoLabel.setPreferredSize(new Dimension(170, 82));
        smartStockLogoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        smartStockLogoLabel.setForeground(DeckersPalette.muted());
        smartStockLogoLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        setSmartStockLogo();
        greetingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        greetingPanel.add(smartStockLogoLabel);
        greetingPanel.add(Box.createVerticalStrut(10));
        greetingPanel.add(greetingLabel);
        greetingPanel.add(Box.createVerticalStrut(6));
        greetingPanel.add(subtitleLabel);

        heroPanel.add(logoPanel, BorderLayout.WEST);
        heroPanel.add(greetingPanel, BorderLayout.CENTER);

        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 16, 0));
        centerPanel.setOpaque(false);
        centerPanel.add(buildStatusPanel());
        centerPanel.add(buildActionPanel());

        root.add(heroPanel, BorderLayout.NORTH);
        root.add(centerPanel, BorderLayout.CENTER);

        setContentPane(root);
        boolean apiClientMode = DatabaseConfig.load().mode() == DatabaseMode.CLIENT
                || DatabaseConfig.load().mode() == DatabaseMode.REMOTE_ADMIN;
        pairRegisterBtn.setVisible(apiClientMode && !LanApiClient.isPaired());
        LanApiClient.RegisterTransfer transfer=LanApiClient.transferState();
        if(LanApiClient.isTransferPending()&&transfer!=null){pairRegisterBtn.setText("Pair at "+(transfer.destinationStoreName()==null||transfer.destinationStoreName().isBlank()?"Destination Store":transfer.destinationStoreName()));statusLabel.setText("Status: Transfer pending. Connect this register to the destination store network and pair it.");}
        serverAddressBtn.setVisible(apiClientMode);
        recoverRegisterBtn.setVisible(apiClientMode&&LanApiClient.isPaired()&&!LanApiClient.isTransferPending());
        setupBtn.setVisible(isInitialSetupRequired(DatabaseConfig.load()));
        startProcessesBtn.setVisible(false);
        refreshModeLabel();
        refreshSystemStats();
        updateGreeting();
        startDisplayRefreshTimer();
        startSystemStatusRefreshTimer();
        wireWindowTimers();
        wireActions();
        continueBtn.setEnabled(false);
        if (openedAfterConnectionLoss) {
            setTitle("SmartStock Welcome - Server Connection Lost");
            statusLabel.setText("Status: Connection lost. SmartStock is locked until the server reconnects.");
        }
        ThemeManager.applyToWindow(this);
        if (DatabaseConfig.hasConfigFile()) {
            SwingUtilities.invokeLater(this::refreshSystemStatus);
            SwingUtilities.invokeLater(this::continueIfStoredSessionExists);
        } else {
            SwingUtilities.invokeLater(this::showInitialDatabaseSetup);
        }
    }

    private JPanel buildStatusPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        DeckersSwing.styleBand(panel, DeckersPalette.LIME, new Insets(18, 18, 18, 18));

        JLabel title = sectionTitle("System Status");
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        header.add(title, BorderLayout.WEST);

        styleWelcomeButton(refreshStatusBtn, DeckersPalette.ORANGE);
        refreshStatusBtn.setToolTipText("Refresh system status");
        refreshStatusBtn.getAccessibleContext().setAccessibleName("Refresh system status");
        refreshStatusBtn.setHorizontalAlignment(SwingConstants.CENTER);
        refreshStatusBtn.setMargin(new Insets(0, 0, 2, 0));
        refreshStatusBtn.setFocusable(false);
        refreshStatusBtn.setPreferredSize(new Dimension(32, 32));
        refreshStatusBtn.setMinimumSize(new Dimension(32, 32));
        refreshStatusBtn.setMaximumSize(new Dimension(32, 32));
        refreshStatusBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.add(refreshStatusBtn, BorderLayout.EAST);
        modeLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        modeLabel.setForeground(DeckersPalette.text());
        modeLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        localDbLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        localDbLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        onlineDbLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        onlineDbLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        systemStatsLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        systemStatsLabel.setForeground(DeckersPalette.muted());
        systemStatsLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        statusLabel.setForeground(DeckersPalette.text());
        statusLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        styleWelcomeButton(syncStatusBtn, DeckersPalette.MAGENTA);
        styleWelcomeButton(setupBtn, DeckersPalette.PURPLE);
        styleWelcomeButton(environmentBtn, DeckersPalette.CORAL);
        styleWelcomeButton(pairRegisterBtn, DeckersPalette.PURPLE);
        styleWelcomeButton(serverAddressBtn, DeckersPalette.LIME);
        styleWelcomeButton(recoverRegisterBtn, DeckersPalette.CORAL);

        panel.add(header);
        panel.add(Box.createVerticalStrut(12));
        panel.add(modeLabel);
        panel.add(Box.createVerticalStrut(12));
        panel.add(localDbLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(onlineDbLabel);
        panel.add(Box.createVerticalStrut(12));
        panel.add(systemStatsLabel);
        panel.add(Box.createVerticalStrut(12));
        panel.add(statusLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(syncStatusBtn);
        panel.add(Box.createVerticalStrut(8));
        panel.add(setupBtn);
        panel.add(Box.createVerticalStrut(8));
        panel.add(environmentBtn);
        panel.add(Box.createVerticalStrut(8));
        panel.add(pairRegisterBtn);
        panel.add(Box.createVerticalStrut(8));
        panel.add(serverAddressBtn);
        panel.add(Box.createVerticalStrut(8));
        panel.add(recoverRegisterBtn);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel buildActionPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        DeckersSwing.styleBand(panel, DeckersPalette.MAGENTA, new Insets(18, 18, 18, 18));
        panel.add(sectionTitle("Get Started"), BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel readyLabel = new JLabel("Ready to continue");
        readyLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        readyLabel.setForeground(DeckersPalette.text());
        readyLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        readyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel helperLabel = new JLabel("Sign in to open SmartStock and continue working.");
        helperLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        helperLabel.setForeground(DeckersPalette.muted());
        helperLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        helperLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel actionRow = new JPanel(new BorderLayout());
        actionRow.setOpaque(false);
        actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        stylePrimaryLoginButton();
        Dimension actionRowSize = new Dimension(Integer.MAX_VALUE, continueBtn.getPreferredSize().height);
        actionRow.setPreferredSize(actionRowSize);
        actionRow.setMaximumSize(actionRowSize);
        actionRow.add(continueBtn, BorderLayout.WEST);

        styleWelcomeButton(startProcessesBtn, DeckersPalette.ORANGE);
        startProcessesBtn.setHorizontalAlignment(SwingConstants.CENTER);
        startProcessesBtn.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(Box.createVerticalStrut(22));
        content.add(readyLabel);
        content.add(Box.createVerticalStrut(8));
        content.add(helperLabel);
        content.add(Box.createVerticalStrut(12));
        content.add(actionRow);
        content.add(Box.createVerticalStrut(12));
        content.add(startProcessesBtn);
        content.add(Box.createVerticalGlue());
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 18));
        label.setForeground(DeckersPalette.text());
        label.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        return label;
    }

    private static Icon createRefreshIcon() {
        return new Icon() {
            @Override
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D drawing = (Graphics2D) graphics.create();
                drawing.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                drawing.setColor(component.getForeground());
                drawing.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                drawing.drawArc(x + 2, y + 2, 14, 14, 40, 285);
                drawing.drawLine(x + 16, y + 10, x + 12, y + 10);
                drawing.drawLine(x + 16, y + 10, x + 15, y + 14);
                drawing.dispose();
            }

            @Override
            public int getIconWidth() {
                return 18;
            }

            @Override
            public int getIconHeight() {
                return 18;
            }
        };
    }

    private void wireActions() {
        refreshStatusBtn.addActionListener(e -> refreshSystemStatus());
        startProcessesBtn.addActionListener(e -> startServerProcesses());
        setupBtn.addActionListener(e -> openGuidedSetup());
        environmentBtn.addActionListener(e -> openEnvironmentSetup());
        pairRegisterBtn.addActionListener(e -> pairRegister());
        serverAddressBtn.addActionListener(e -> configureServerAddress());
        recoverRegisterBtn.addActionListener(e -> recoverAtAnotherStore());
        syncStatusBtn.addActionListener(e -> new SyncStatus().setVisible(true));
        continueBtn.addActionListener(e -> openLogin());
    }

    private void pairRegister() {
        boolean remote = DatabaseConfig.load().mode() == DatabaseMode.REMOTE_ADMIN;
        String phrase = JOptionPane.showInputDialog(
                this,
                (remote ? "On the Remote Admin gateway, obtain the current enrollment phrase.\n"
                        : "On the SmartStock server, open Device Management > Security Status.\n")
                        + "Enter the temporary administrator pairing phrase shown there:",
                remote ? "One-Time Remote Admin Enrollment" : "One-Time Register Pairing",
                JOptionPane.PLAIN_MESSAGE
        );
        if (phrase == null || phrase.isBlank()) return;
        pairRegisterBtn.setEnabled(false);
        statusLabel.setText("Status: Pairing this register...");
        new SwingWorker<LanApiClient.PairingResult, Void>() {
            @Override protected LanApiClient.PairingResult doInBackground() throws Exception {
                LanApiClient.RegisterTransfer transfer=LanApiClient.transferState();
                return LanApiClient.pairOnce(phrase,transfer!=null&&transfer.emergency()?transfer.reason():null);
            }

            @Override protected void done() {
                pairRegisterBtn.setEnabled(true);
                try {
                    LanApiClient.PairingResult result = get();
                    if ("PAIRED".equals(result.status())) {
                        pairRegisterBtn.setVisible(false);
                        statusLabel.setText("Status: Register paired. Employees can log in normally.");
                        JOptionPane.showMessageDialog(WelcomeFrame.this,
                                "This register is paired. Employees will not be asked for this phrase again.");
                    } else {
                        statusLabel.setText("Status: Waiting for administrator device approval.");
                        JOptionPane.showMessageDialog(WelcomeFrame.this,
                                "Enrollment was sent. Approve this register from Device Management on the server.\n"
                                        + "SmartStock will claim approval automatically.");
                    }
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    String message = getRootCauseMessage(cause);
                    statusLabel.setText("Status: Pairing was not completed.");
                    JOptionPane.showMessageDialog(WelcomeFrame.this,
                            "This register could not be paired.\n\n" + message
                                    + (remote ? "\n\nConfirm the public gateway address and request a new gateway enrollment phrase."
                                    : "\n\nConfirm both Macs are on the same network, then request a new phrase from Device Management > Security Status."),
                            remote ? "Remote Admin Enrollment" : "Register Pairing", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void configureServerAddress() {
        String current = LanApiClient.baseUri().getHost() + ":" + LanApiClient.baseUri().getPort();
        String value = JOptionPane.showInputDialog(
                this,
                "Enter the SmartStock server address shown on the server Mac.\n"
                        + "Example: 192.168.10.47:8443",
                current);
        if (value == null || value.isBlank()) return;
        String clean = value.trim();
        int separator = clean.lastIndexOf(':');
        String host = separator > 0 ? clean.substring(0, separator).trim() : clean;
        int port = LanApiServer.DEFAULT_PORT;
        try {
            if (separator > 0) port = Integer.parseInt(clean.substring(separator + 1).trim());
            LanApiClient.configureEndpoint(host, port);
            statusLabel.setText("Status: SmartStock server set to " + host + ":" + port + ". Refreshing...");
            refreshSystemStatus();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, getRootCauseMessage(ex),
                    "SmartStock Server Address", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void recoverAtAnotherStore(){
        JTextArea reason=new JTextArea(3,36);reason.setLineWrap(true);reason.setWrapStyleWord(true);JCheckBox acknowledge=new JCheckBox("I understand the old store may accept its existing credential until it reconnects and synchronizes.");JPanel panel=new JPanel();panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));panel.add(new JLabel("Use this only when the original store server cannot be reached."));panel.add(Box.createVerticalStrut(8));panel.add(new JLabel("Required recovery reason:"));panel.add(new JScrollPane(reason));panel.add(Box.createVerticalStrut(8));panel.add(acknowledge);
        if(JOptionPane.showConfirmDialog(this,panel,"Emergency Register Recovery",JOptionPane.OK_CANCEL_OPTION,JOptionPane.ERROR_MESSAGE)!=JOptionPane.OK_OPTION)return;
        if(reason.getText().isBlank()||!acknowledge.isSelected()){JOptionPane.showMessageDialog(this,"Enter a reason and acknowledge the recovery warning.","Emergency Register Recovery",JOptionPane.WARNING_MESSAGE);return;}
        try{LanApiClient.beginEmergencyRecovery(reason.getText());pairRegisterBtn.setText("Pair at Destination Store");pairRegisterBtn.setVisible(true);recoverRegisterBtn.setVisible(false);statusLabel.setText("Status: Emergency recovery pending. Set or discover the destination server, then enter its temporary pairing phrase.");}catch(Exception ex){JOptionPane.showMessageDialog(this,getRootCauseMessage(ex),"Emergency Register Recovery",JOptionPane.ERROR_MESSAGE);}
    }

    private void openDatabaseSetup() {
        DatabaseSetup setup = new DatabaseSetup(this);
        setup.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                refreshSystemStatus();
                continueIfStoredSessionExists();
            }
        });
        setup.setVisible(true);
        setup.toFront();
        setup.requestFocus();
    }

    private void openGuidedSetup() {
        DatabaseConfig config = DatabaseConfig.load();
        if (DatabaseConfig.hasConfigFile() && config.mode() == DatabaseMode.SERVER) {
            ServerSetupWizard setup = new ServerSetupWizard(this);
            setup.setVisible(true);
            setup.toFront();
            setup.requestFocus();
            return;
        }
        openEnvironmentSetup();
    }

    private void openEnvironmentSetup() {
        InitialSetupWizard setup = new InitialSetupWizard(this);
        setup.setVisible(true);
        setup.toFront();
        setup.requestFocus();
    }

    void refreshAfterSetup() {
        refreshModeLabel();
        refreshSystemStats();
        refreshSystemStatus();
    }

    private void styleWelcomeButton(JButton button, Color accent) {
        DeckersSwing.styleUtilityButton(button, accent);
        button.setFont(new Font("SansSerif", Font.BOLD, 15));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void stylePrimaryLoginButton() {
        continueBtn.setText("Log In >");
        styleWelcomeButton(continueBtn, DeckersPalette.LIME);
        continueBtn.setHorizontalAlignment(SwingConstants.CENTER);
        Dimension buttonSize = new Dimension(112, 38);
        continueBtn.setPreferredSize(buttonSize);
        continueBtn.setMinimumSize(buttonSize);
        continueBtn.setMaximumSize(buttonSize);
    }

    private void setDeckersLogo() {
        ImageIcon icon = DeckersLogoManager.loadDeckersLogoIcon(getClass());
        if (icon == null || icon.getIconWidth() <= 0) {
            return;
        }
        Image scaled = DeckersLogoManager.scaleToFit(icon.getImage(), 290, 118);
        deckersLogoLabel.setText("");
        deckersLogoLabel.setIcon(new ImageIcon(scaled));
    }

    private void setSmartStockLogo() {
        ImageIcon icon = DeckersLogoManager.loadSmartStockLogoIcon(getClass());
        if (icon == null || icon.getIconWidth() <= 0) {
            return;
        }
        Image scaled = DeckersLogoManager.scaleToFit(icon.getImage(), 160, 76);
        smartStockLogoLabel.setText("");
        smartStockLogoLabel.setIcon(new ImageIcon(scaled));
    }

    private void startDisplayRefreshTimer() {
        if (displayRefreshTimer != null && displayRefreshTimer.isRunning()) {
            return;
        }
        displayRefreshTimer = new Timer(60_000, e -> {
            updateGreeting();
            refreshSystemStats();
        });
        displayRefreshTimer.setInitialDelay(0);
        displayRefreshTimer.start();
    }

    private void startSystemStatusRefreshTimer() {
        if (systemStatusRefreshTimer != null && systemStatusRefreshTimer.isRunning()) {
            return;
        }
        systemStatusRefreshTimer = new Timer(60_000, e -> refreshSystemStatus(false));
        systemStatusRefreshTimer.setInitialDelay(60_000);
        systemStatusRefreshTimer.start();
    }

    private void wireWindowTimers() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                startWelcomeNfcMonitor();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                nfcMonitorRunning = false;
                stopWelcomeTimers();
            }
        });
    }

    private void stopWelcomeTimers() {
        if (displayRefreshTimer != null) {
            displayRefreshTimer.stop();
            displayRefreshTimer = null;
        }
        if (systemStatusRefreshTimer != null) {
            systemStatusRefreshTimer.stop();
            systemStatusRefreshTimer = null;
        }
    }

    private void updateGreeting() {
        LocalDateTime now = LocalDateTime.now(StoreTimeZoneHelper.getStoreZone());
        String key = now.getHour() + ":" + (now.getMinute() / 10);
        if (key.equals(lastGreetingKey)) {
            return;
        }
        lastGreetingKey = key;
        WelcomeGreetingHelper.Greeting greeting = WelcomeGreetingHelper.currentGreeting();
        greetingLabel.setText(greeting.title());
        subtitleLabel.setText(greeting.subtitle());
    }

    private void refreshModeLabel() {
        DatabaseConfig config = DatabaseConfig.load();
        String dbText = config.mode() == DatabaseMode.REMOTE_ADMIN
                ? LanApiClient.baseUri().toString()
                : config.jdbcUrl() == null || config.jdbcUrl().isBlank() ? "Not configured" : config.jdbcUrl();
        modeLabel.setText("<html><b>Environment:</b> "
                + escapeHtml(EnvironmentProfile.active().displayName())
                + "<br><b>Mode:</b> " + escapeHtml(config.mode().name())
                + "<br><b>" + (config.mode() == DatabaseMode.REMOTE_ADMIN ? "Gateway" : "Primary DB")
                + ":</b> " + escapeHtml(dbText) + "</html>");
    }

    private void refreshSystemStats() {
        DatabaseConfig config = DatabaseConfig.load();
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMb = runtime.maxMemory() / (1024 * 1024);
        String location = config.locationId() == null ? "Not selected" : String.valueOf(config.locationId());
        String localTime = LocalDateTime.now(StoreTimeZoneHelper.getStoreZone()).format(DateTimeFormatter.ofPattern("hh:mm a"));
        systemStatsLabel.setText("<html><b>System Stats</b><br>"
                + "SmartStock Version: " + escapeHtml(DeviceUtils.getAppVersion())
                + "<br>Store Location: " + escapeHtml(location)
                + "<br>Sync Interval: " + config.syncIntervalSeconds() + "s"
                + "<br>Memory: " + usedMb + " MB / " + maxMb + " MB"
                + "<br>Java: " + escapeHtml(System.getProperty("java.version", "Unknown"))
                + "<br>Local Time: " + escapeHtml(localTime)
                + "</html>");
    }

    private void continueIfStoredSessionExists() {
        if (!DatabaseConfig.hasConfigFile()) {
            return;
        }
        if(LanApiClient.isTransferPending())return;
        if (!SupabaseSessionManager.hasPersistedSession()) {
            return;
        }
        statusLabel.setText("Status: Checking saved sign-in...");
        continueBtn.setEnabled(false);
        loginAvailable = false;
        refreshStatusBtn.setEnabled(false);

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                try {
                    LanApiClient.checkHealth();
                    return null;
                } catch (Exception ex) {
                    return getRootCauseMessage(ex);
                }
            }

            @Override
            protected void done() {
                refreshStatusBtn.setEnabled(true);
                String errorMessage;
                try {
                    errorMessage = get();
                } catch (Exception ex) {
                    errorMessage = getRootCauseMessage(ex);
                }

                if (errorMessage == null || errorMessage.isBlank()) {
                    statusLabel.setText("Status: Saved sign-in found. Click Log In to continue.");
                    continueBtn.setEnabled(true);
                    loginAvailable = true;
                    return;
                }

                statusLabel.setText("Status: Database setup required");
                continueBtn.setEnabled(false);
                loginAvailable = false;
                JOptionPane.showMessageDialog(
                        WelcomeFrame.this,
                        "Saved sign-in was found, but the database is not ready yet.\n\n"
                                + errorMessage
                                + "\n\nRun Guided Setup if this workstation is being installed.",
                        "Database Setup Required",
                        JOptionPane.WARNING_MESSAGE
                );
            }
        };
        worker.execute();
    }

    private String getRootCauseMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName()
                : cause.getMessage();
    }

    private void refreshSystemStatus() {
        refreshSystemStatus(true);
    }

    private void refreshSystemStatus(boolean showCheckingState) {
        if (systemStatusRefreshInProgress) {
            return;
        }
        systemStatusRefreshInProgress = true;
        refreshModeLabel();
        refreshSystemStats();
        DatabaseConfig config = DatabaseConfig.load();
        String connectionLabel = config.mode() == DatabaseMode.REMOTE_ADMIN ? "Cloud Gateway: "
                : config.mode() == DatabaseMode.CLIENT ? "SmartStock Server: " : "Local DB: ";
        boolean startingServerDatabase = startupDatabaseRecoveryPending
                && config.mode() == DatabaseMode.SERVER;
        startupDatabaseRecoveryPending = false;
        setupBtn.setVisible(isInitialSetupRequired(config));
        if (!DatabaseConfig.hasConfigFile()) {
            systemStatusRefreshInProgress = false;
            localDbLabel.setText(connectionLabel + "Setup required");
            onlineDbLabel.setText("Online DB: Setup required");
            localDbLabel.setForeground(DeckersPalette.CORAL);
            onlineDbLabel.setForeground(DeckersPalette.CORAL);
            statusLabel.setText("Status: Guided Setup is required on this computer.");
            continueBtn.setEnabled(false);
            loginAvailable = false;
            refreshStatusBtn.setEnabled(true);
            startProcessesBtn.setVisible(false);
            return;
        }
        if(config.mode()==DatabaseMode.CLIENT&&LanApiClient.isTransferPending()){
            systemStatusRefreshInProgress=false;LanApiClient.RegisterTransfer transfer=LanApiClient.transferState();String destination=transfer==null||transfer.destinationStoreName()==null||transfer.destinationStoreName().isBlank()?"destination store":transfer.destinationStoreName();localDbLabel.setText(connectionLabel+"Transfer pending");onlineDbLabel.setText("Online DB: Waiting for destination pairing");statusLabel.setText("Status: Connect to "+destination+" and pair this register.");pairRegisterBtn.setVisible(true);recoverRegisterBtn.setVisible(false);continueBtn.setEnabled(false);loginAvailable=false;refreshStatusBtn.setEnabled(true);return;
        }
        if (showCheckingState) {
            localDbLabel.setText(connectionLabel
                    + (startingServerDatabase ? "Starting database..." : "Checking..."));
            onlineDbLabel.setText("Online DB: Checking...");
            localDbLabel.setForeground(DeckersPalette.muted());
            onlineDbLabel.setForeground(DeckersPalette.muted());
            statusLabel.setText(startingServerDatabase
                    ? "Status: Starting database..."
                    : "Status: Refreshing system status...");
            continueBtn.setEnabled(false);
            loginAvailable = false;
        }
        refreshStatusBtn.setEnabled(false);

        SwingWorker<SystemStatus, Void> worker = new SwingWorker<>() {
            @Override
            protected SystemStatus doInBackground() {
                String localStatus = startingServerDatabase
                        ? startServerDatabaseAndCheck(config)
                        : checkLocalDb(config);
                if (config.mode() == DatabaseMode.SERVER
                        && localStatus.startsWith("Online")
                        && !LanApiClient.isPaired()) {
                    try {
                        LanApiClient.ensureLocalServerCredential();
                    } catch (Exception ex) {
                        localStatus = "Online - server UI credential unavailable: "
                                + getRootCauseMessage(ex);
                    }
                }
                String onlineStatus = checkOnlineDb(config);
                boolean setupRequired = isInitialSetupRequired(config);
                if (!setupRequired && config.mode() == DatabaseMode.SERVER) {
                    try {
                        ServerStoreSetupService.Store store =
                                ServerStoreSetupService.find(String.valueOf(config.locationId()));
                        setupRequired = store == null || store.locationId() != config.locationId();
                        if (!setupRequired) setupRequired = !ServerFirstAdministratorService.isComplete();
                    } catch (Exception ex) {
                        setupRequired = true;
                    }
                }
                boolean firstLoginPending = !setupRequired
                        && config.mode() == DatabaseMode.SERVER
                        && ServerFirstAdministratorService.requiresFirstOnlineLogin();
                boolean canLogin = !setupRequired
                        && localStatus.startsWith("Online")
                        && LanApiClient.isPaired();
                String message = canLogin
                        ? firstLoginPending
                        ? "First administrator must sign in online once"
                        : "Ready for login"
                        : setupRequired
                        ? "Guided Setup is incomplete"
                        : "SmartStock Server Service needs attention";
                return new SystemStatus(localStatus, onlineStatus, message, canLogin,
                        setupRequired, setupRequired || firstLoginPending);
            }

            @Override
            protected void done() {
                systemStatusRefreshInProgress = false;
                refreshStatusBtn.setEnabled(true);
                try {
                    SystemStatus result = get();
                    setupBtn.setVisible(result.setupVisible());
                    updateStatusLabel(localDbLabel, connectionLabel + result.localStatus(), result.localStatus());
                    updateStatusLabel(onlineDbLabel, "Online DB: " + result.onlineStatus(), result.onlineStatus());
                    if (openedAfterConnectionLoss && !result.canLogin()) {
                        statusLabel.setText("Status: Connection lost. SmartStock is locked until the server reconnects.");
                    } else if (openedAfterConnectionLoss) {
                        statusLabel.setText("Status: Server connection restored. Log in to continue.");
                    } else {
                        statusLabel.setText("Status: " + result.message());
                    }
                    continueBtn.setEnabled(result.canLogin());
                    loginAvailable = result.canLogin();
                    startProcessesBtn.setVisible(config.mode() == DatabaseMode.SERVER
                            && !result.canLogin() && !result.setupRequired());
                } catch (Exception ex) {
                    localDbLabel.setText(connectionLabel + "Failed");
                    onlineDbLabel.setText("Online DB: Failed");
                    localDbLabel.setForeground(DeckersPalette.CORAL);
                    onlineDbLabel.setForeground(DeckersPalette.CORAL);
                    statusLabel.setText("Status: " + getRootCauseMessage(ex));
                    continueBtn.setEnabled(false);
                    loginAvailable = false;
                    startProcessesBtn.setVisible(config.mode() == DatabaseMode.SERVER);
                }
            }
        };

        worker.execute();
    }

    private void startServerProcesses() {
        if (DatabaseConfig.load().mode() != DatabaseMode.SERVER) return;
        startProcessesBtn.setEnabled(false);
        refreshStatusBtn.setEnabled(false);
        continueBtn.setEnabled(false);
        loginAvailable = false;
        statusLabel.setText("Status: Starting PostgreSQL and SmartStock Server Service...");
        new SwingWorker<PostgresRuntimeService.CommandResult, Void>() {
            @Override
            protected PostgresRuntimeService.CommandResult doInBackground() throws Exception {
                return PostgresRuntimeService.startServerProcesses();
            }

            @Override
            protected void done() {
                startProcessesBtn.setEnabled(true);
                refreshStatusBtn.setEnabled(true);
                try {
                    PostgresRuntimeService.CommandResult result = get();
                    if (!result.success()) {
                        statusLabel.setText("Status: Server processes could not be started.");
                        JOptionPane.showMessageDialog(WelcomeFrame.this, result.output(),
                                "Start Server Processes", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    statusLabel.setText("Status: Server processes started. Checking login service...");
                    Timer verify = new Timer(2_000, e -> refreshSystemStatus());
                    verify.setRepeats(false);
                    verify.start();
                } catch (Exception ex) {
                    statusLabel.setText("Status: Server processes could not be started.");
                    JOptionPane.showMessageDialog(WelcomeFrame.this,
                            getRootCauseMessage(ex), "Start Server Processes",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private boolean isInitialSetupRequired(DatabaseConfig config) {
        return isSetupRequired(config, DatabaseConfig.hasConfigFile(),
                ServerSupabaseCredentials.isConfigured());
    }

    static boolean isSetupRequired(DatabaseConfig config, boolean configFileExists) {
        return isSetupRequired(config, configFileExists, true);
    }

    static boolean isSetupRequired(DatabaseConfig config, boolean configFileExists,
                                   boolean serverCredentialConfigured) {
        if (!configFileExists || config == null) return true;
        if (config.mode() == DatabaseMode.CLIENT || config.mode() == DatabaseMode.REMOTE_ADMIN) {
            return config.locationId() == null || config.serverHost() == null || config.serverHost().isBlank();
        }
        return config.locationId() == null
                || !config.hasPrimaryConnection()
                || !serverCredentialConfigured
                || config.hasUnresolvedCredentialPlaceholders();
    }

    private String checkLocalDb(DatabaseConfig config) {
        if (!DatabaseConfig.hasConfigFile()) {
            return "Setup required";
        }
        try {
            LanApiClient.checkHealth();
            return "Online";
        } catch (Exception ex) {
            return "Offline - " + getRootCauseMessage(ex);
        }
    }

    private String startServerDatabaseAndCheck(DatabaseConfig config) {
        String status = checkLocalDb(config);
        if (status.startsWith("Online")) {
            return status;
        }
        try {
            PostgresRuntimeService.CommandResult database = PostgresRuntimeService.startPostgres();
            if (!database.success()) {
                return "Offline - " + database.output();
            }
            PostgresRuntimeService.startLanService();
            for (int attempt = 0; attempt < 10; attempt++) {
                try {
                    Thread.sleep(1_000L);
                    LanApiClient.checkHealth();
                    return "Online";
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return "Offline - Database startup was interrupted";
                } catch (Exception ignored) {
                    // PostgreSQL and the LAN service can take a few seconds to accept requests.
                }
            }
            return checkLocalDb(config);
        } catch (Exception ex) {
            return "Offline - " + getRootCauseMessage(ex);
        }
    }

    private String checkOnlineDb(DatabaseConfig config) {
        if (!DatabaseConfig.hasConfigFile()) {
            return "Setup required";
        }
        return "Managed by server";
    }

    private void showInitialDatabaseSetup() {
        if (initialSetupWindowOpened || DatabaseConfig.hasConfigFile()) {
            return;
        }
        initialSetupWindowOpened = true;
        setupBtn.setVisible(true);
        localDbLabel.setText("Local DB: Setup required");
        onlineDbLabel.setText("Online DB: Setup required");
        localDbLabel.setForeground(DeckersPalette.CORAL);
        onlineDbLabel.setForeground(DeckersPalette.CORAL);
        statusLabel.setText("Status: Choose how this computer will be used.");
        continueBtn.setEnabled(false);
        loginAvailable = false;
        refreshStatusBtn.setEnabled(true);
        openGuidedSetup();
    }

    private void updateStatusLabel(JLabel label, String text, String status) {
        label.setText(text);
        if (status.startsWith("Online")) {
            label.setForeground(DeckersPalette.LIME);
        } else if (status.startsWith("Not")) {
            label.setForeground(DeckersPalette.muted());
        } else {
            label.setForeground(DeckersPalette.CORAL);
        }
    }

    private String escapeHtml(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void openLogin() {
        openLogin(null);
    }

    private void openLogin(PcscNfcService.ReadResult card) {
        if (!loginAvailable) return;
        nfcMonitorRunning = false;
        new Login(card).setVisible(true);
        dispose();
    }

    private void startWelcomeNfcMonitor() {
        if (nfcMonitorRunning) return;
        nfcMonitorRunning = true;
        Thread monitor = new Thread(() -> {
            while (nfcMonitorRunning && isDisplayable()) {
                try {
                    if (!PcscNfcService.hasReader()) {
                        Thread.sleep(750);
                        continue;
                    }
                    PcscNfcService.ReadResult card = PcscNfcService.read(Duration.ofSeconds(2));
                    SwingUtilities.invokeLater(() -> {
                        if (nfcMonitorRunning && isDisplayable() && loginAvailable) openLogin(card);
                    });
                    if (loginAvailable) return;
                } catch (PcscNfcService.NoCardPresentException ignored) {
                    // Keep listening while the welcome screen is open.
                } catch (Exception ex) {
                    try { Thread.sleep(1_500); } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "smartstock-nfc-welcome");
        monitor.setDaemon(true);
        monitor.start();
    }
}
