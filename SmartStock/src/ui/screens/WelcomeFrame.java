package ui.screens;

import data.DB;
import data.DatabaseConfig;
import managers.SupabaseSessionManager;
import ui.design.DeckersLogoManager;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.ThemeManager;
import ui.helpers.WelcomeGreetingHelper;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.sql.Connection;
import java.time.LocalDateTime;
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
    private final JButton refreshStatusBtn = new JButton("Refresh System Status");
    private final JButton setupBtn = new JButton("Initial Database Setup");
    private final JButton syncStatusBtn = new JButton("Sync Status");
    private final JButton continueBtn = new JButton("Log In");
    private Timer displayRefreshTimer;
    private Timer systemStatusRefreshTimer;
    private boolean systemStatusRefreshInProgress;
    private boolean initialSetupWindowOpened;
    private String lastGreetingKey = "";
    private record SystemStatus(String localStatus, String onlineStatus, String message, boolean canLogin) {}

    public WelcomeFrame() {
        super("SmartStock Welcome");
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
        refreshModeLabel();
        refreshSystemStats();
        updateGreeting();
        startDisplayRefreshTimer();
        startSystemStatusRefreshTimer();
        wireWindowTimers();
        wireActions();
        continueBtn.setEnabled(false);
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
        styleWelcomeButton(refreshStatusBtn, DeckersPalette.ORANGE);
        styleWelcomeButton(syncStatusBtn, DeckersPalette.MAGENTA);
        styleWelcomeButton(setupBtn, DeckersPalette.PURPLE);

        panel.add(title);
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
        panel.add(Box.createVerticalStrut(12));
        panel.add(refreshStatusBtn);
        panel.add(Box.createVerticalStrut(8));
        panel.add(syncStatusBtn);
        panel.add(Box.createVerticalStrut(8));
        panel.add(setupBtn);
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

        content.add(Box.createVerticalStrut(22));
        content.add(readyLabel);
        content.add(Box.createVerticalStrut(8));
        content.add(helperLabel);
        content.add(Box.createVerticalStrut(12));
        content.add(actionRow);
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

    private void wireActions() {
        refreshStatusBtn.addActionListener(e -> refreshSystemStatus());
        setupBtn.addActionListener(e -> openDatabaseSetup());
        syncStatusBtn.addActionListener(e -> new SyncStatus().setVisible(true));
        continueBtn.addActionListener(e -> openLogin());
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
            public void windowClosed(WindowEvent e) {
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
        String dbText = config.jdbcUrl() == null || config.jdbcUrl().isBlank() ? "Not configured" : config.jdbcUrl();
        modeLabel.setText("<html><b>Mode:</b> " + escapeHtml(config.mode().name())
                + "<br><b>Primary DB:</b> " + escapeHtml(dbText) + "</html>");
    }

    private void refreshSystemStats() {
        DatabaseConfig config = DatabaseConfig.load();
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMb = runtime.maxMemory() / (1024 * 1024);
        String location = config.locationId() == null ? "Not selected" : String.valueOf(config.locationId());
        String localTime = LocalDateTime.now(StoreTimeZoneHelper.getStoreZone()).format(DateTimeFormatter.ofPattern("hh:mm a"));
        systemStatsLabel.setText("<html><b>System Stats</b><br>"
                + "Store Location: " + escapeHtml(location)
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
        if (!SupabaseSessionManager.hasPersistedSession()) {
            return;
        }
        statusLabel.setText("Status: Checking saved sign-in...");
        continueBtn.setEnabled(false);
        refreshStatusBtn.setEnabled(false);

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                try (Connection ignored = DB.getConnection()) {
                    // Continue only after the configured database is reachable.
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
                    return;
                }

                statusLabel.setText("Status: Database setup required");
                continueBtn.setEnabled(false);
                JOptionPane.showMessageDialog(
                        WelcomeFrame.this,
                        "Saved sign-in was found, but the database is not ready yet.\n\n"
                                + errorMessage
                                + "\n\nRun Initial Database Setup if this workstation is being installed.",
                        "Database Setup Required",
                        JOptionPane.WARNING_MESSAGE
                );
            }
        };
        worker.execute();
    }

    private String getRootCauseMessage(Exception ex) {
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
        setupBtn.setVisible(isInitialSetupRequired(config));
        if (!DatabaseConfig.hasConfigFile()) {
            systemStatusRefreshInProgress = false;
            localDbLabel.setText("Local DB: Setup required");
            onlineDbLabel.setText("Online DB: Setup required");
            localDbLabel.setForeground(DeckersPalette.CORAL);
            onlineDbLabel.setForeground(DeckersPalette.CORAL);
            statusLabel.setText("Status: Initial Database Setup is required on this Mac.");
            continueBtn.setEnabled(false);
            refreshStatusBtn.setEnabled(true);
            return;
        }
        if (showCheckingState) {
            localDbLabel.setText("Local DB: Checking...");
            onlineDbLabel.setText("Online DB: Checking...");
            localDbLabel.setForeground(DeckersPalette.muted());
            onlineDbLabel.setForeground(DeckersPalette.muted());
            statusLabel.setText("Status: Refreshing system status...");
            continueBtn.setEnabled(false);
        }
        refreshStatusBtn.setEnabled(false);

        SwingWorker<SystemStatus, Void> worker = new SwingWorker<>() {
            @Override
            protected SystemStatus doInBackground() {
                String localStatus = checkLocalDb(config);
                String onlineStatus = checkOnlineDb(config);
                boolean canLogin = localStatus.startsWith("Online") || (config.mode() == data.DatabaseMode.CLOUD_DIRECT && onlineStatus.startsWith("Online"));
                String message = canLogin ? "Ready for login" : "Database connection needs attention";
                return new SystemStatus(localStatus, onlineStatus, message, canLogin);
            }

            @Override
            protected void done() {
                systemStatusRefreshInProgress = false;
                refreshStatusBtn.setEnabled(true);
                try {
                    SystemStatus result = get();
                    updateStatusLabel(localDbLabel, "Local DB: " + result.localStatus(), result.localStatus());
                    updateStatusLabel(onlineDbLabel, "Online DB: " + result.onlineStatus(), result.onlineStatus());
                    statusLabel.setText("Status: " + result.message());
                    continueBtn.setEnabled(result.canLogin());
                } catch (Exception ex) {
                    localDbLabel.setText("Local DB: Failed");
                    onlineDbLabel.setText("Online DB: Failed");
                    localDbLabel.setForeground(DeckersPalette.CORAL);
                    onlineDbLabel.setForeground(DeckersPalette.CORAL);
                    statusLabel.setText("Status: " + getRootCauseMessage(ex));
                    continueBtn.setEnabled(false);
                }
            }
        };

        worker.execute();
    }

    private boolean isInitialSetupRequired(DatabaseConfig config) {
        return !DatabaseConfig.hasConfigFile()
                || !config.hasPrimaryConnection()
                || config.hasUnresolvedCredentialPlaceholders();
    }

    private String checkLocalDb(DatabaseConfig config) {
        if (!DatabaseConfig.hasConfigFile()) {
            return "Setup required";
        }
        if (config.mode() == data.DatabaseMode.CLOUD_DIRECT) {
            return "Not used in cloud-direct mode";
        }
        if (!config.hasPrimaryConnection() || config.hasUnresolvedCredentialPlaceholders()) {
            return "Not configured";
        }
        try (Connection ignored = DB.getConnection()) {
            return "Online";
        } catch (Exception ex) {
            return "Offline - " + getRootCauseMessage(ex);
        }
    }

    private String checkOnlineDb(DatabaseConfig config) {
        if (!DatabaseConfig.hasConfigFile()) {
            return "Setup required";
        }
        if (!config.hasCloudConnection() && config.mode() != data.DatabaseMode.CLOUD_DIRECT) {
            return "Not configured";
        }
        try (Connection ignored = config.hasCloudConnection() ? DB.getCloudConnection() : DB.getConnection()) {
            return "Online";
        } catch (Exception ex) {
            return "Offline - " + getRootCauseMessage(ex);
        }
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
        statusLabel.setText("Status: Choose a database mode to finish setup.");
        continueBtn.setEnabled(false);
        refreshStatusBtn.setEnabled(true);
        openDatabaseSetup();
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
        new Login().setVisible(true);
        dispose();
    }
}
