package ui.screens;

import data.DB;
import data.DatabaseConfig;
import managers.SupabaseSessionManager;
import ui.design.DeckersLogoManager;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.ThemeManager;

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
        startGreetingTimer();
        wireActions();
        continueBtn.setEnabled(false);
        ThemeManager.applyToWindow(this);
        SwingUtilities.invokeLater(this::refreshSystemStatus);
        SwingUtilities.invokeLater(this::continueIfStoredSessionExists);
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

        JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 10));
        buttons.setOpaque(false);
        styleWelcomeButton(continueBtn, DeckersPalette.LIME);
        buttons.add(continueBtn);
        panel.add(buttons, BorderLayout.CENTER);
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
        setupBtn.addActionListener(e -> {
            new DatabaseSetup(this).setVisible(true);
            SwingUtilities.invokeLater(this::refreshSystemStatus);
        });
        syncStatusBtn.addActionListener(e -> new SyncStatus().setVisible(true));
        continueBtn.addActionListener(e -> openLogin());
    }

    private void styleWelcomeButton(JButton button, Color accent) {
        DeckersSwing.styleUtilityButton(button, accent);
        button.setFont(new Font("SansSerif", Font.BOLD, 15));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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

    private void startGreetingTimer() {
        Timer timer = new Timer(60_000, e -> updateGreeting());
        timer.setInitialDelay(0);
        timer.start();
    }

    private void updateGreeting() {
        LocalDateTime now = LocalDateTime.now(StoreTimeZoneHelper.getStoreZone());
        String key = now.getHour() + ":" + (now.getMinute() / 10);
        if (key.equals(lastGreetingKey)) {
            return;
        }
        lastGreetingKey = key;
        int hour = now.getHour();
        int variant = (now.getMinute() / 10) % 3;
        if (hour < 5) {
            greetingLabel.setText("Welcome, Night Crew");
            subtitleLabel.setText(variant == 0 ? "Quiet shift, clean starts, steady systems."
                    : variant == 1 ? "SmartStock is ready whenever you are."
                    : "Late hours still deserve a smooth login.");
        } else if (hour < 12) {
            greetingLabel.setText(variant == 1 ? "Morning, SmartStock Team" : "Good Morning");
            subtitleLabel.setText(variant == 0 ? "Let us get the day opened cleanly."
                    : variant == 1 ? "Coffee checked, inventory ready."
                    : "Fresh day, clear counts, confident sales.");
        } else if (hour == 12) {
            greetingLabel.setText(variant == 0 ? "What's for Lunch? 🍽"
                    : variant == 1 ? "Lunch Time Already? 🥪"
                    : "Midday Check-In ☀");
            subtitleLabel.setText(variant == 0 ? "Take care of the rush, then take care of yourself."
                    : variant == 1 ? "A smooth system makes a better lunch break."
                    : "Half the day down, plenty of wins left.");
        } else if (hour < 16) {
            greetingLabel.setText(variant == 2 ? "Afternoon Flow" : "Good Afternoon");
            subtitleLabel.setText(variant == 0 ? "Keep the store moving with clean data."
                    : variant == 1 ? "Steady scans, steady stock, steady sales."
                    : "The afternoon shift is ready to roll.");
        } else if (hour < 18) {
            greetingLabel.setText(variant == 0 ? "Waiting for 5 PM?" : "Final Stretch");
            subtitleLabel.setText(variant == 0 ? "Close time is close, but SmartStock is awake."
                    : variant == 1 ? "Finish sharp, then head out proud."
                    : "One clean close makes tomorrow easier.");
        } else {
            greetingLabel.setText(variant == 1 ? "Evening Wrap-Up" : "Good Evening");
            subtitleLabel.setText(variant == 0 ? "Settle in and keep the close simple."
                    : variant == 1 ? "Evening pace, clean screens, calm totals."
                    : "Let us make the last tasks feel lighter.");
        }
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
        if (!SupabaseSessionManager.hasPersistedSession()) {
            return;
        }
        try (Connection ignored = DB.getConnection()) {
            // Continue only after the configured database is reachable.
        } catch (Exception ex) {
            statusLabel.setText("Status: Database setup required");
            refreshStatusBtn.setEnabled(true);
            continueBtn.setEnabled(false);
            JOptionPane.showMessageDialog(
                    this,
                    "Saved sign-in was found, but the database is not ready yet.\n\n"
                            + getRootCauseMessage(ex)
                            + "\n\nRun Initial Database Setup if this workstation is being installed.",
                    "Database Setup Required",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        statusLabel.setText("Status: Saved sign-in found. Click Log In to continue.");
        continueBtn.setEnabled(true);
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
        refreshModeLabel();
        refreshSystemStats();
        DatabaseConfig config = DatabaseConfig.load();
        setupBtn.setVisible(isInitialSetupRequired(config));
        localDbLabel.setText("Local DB: Checking...");
        onlineDbLabel.setText("Online DB: Checking...");
        localDbLabel.setForeground(DeckersPalette.muted());
        onlineDbLabel.setForeground(DeckersPalette.muted());
        statusLabel.setText("Status: Refreshing system status...");
        refreshStatusBtn.setEnabled(false);
        continueBtn.setEnabled(false);

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
        return !config.hasPrimaryConnection() || config.hasUnresolvedCredentialPlaceholders();
    }

    private String checkLocalDb(DatabaseConfig config) {
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
        if (!config.hasCloudConnection() && config.mode() != data.DatabaseMode.CLOUD_DIRECT) {
            return "Not configured";
        }
        try (Connection ignored = config.hasCloudConnection() ? DB.getCloudConnection() : DB.getConnection()) {
            return "Online";
        } catch (Exception ex) {
            return "Offline - " + getRootCauseMessage(ex);
        }
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
