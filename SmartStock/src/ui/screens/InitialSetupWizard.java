package ui.screens;

import data.DatabaseMode;
import data.EnvironmentProfile;
import services.PostgresRuntimeService;
import services.ServerSupabaseCredentials;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import ui.helpers.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * First-run, mode-first setup. It deliberately asks what the computer does
 * before exposing any database or network configuration.
 */
public final class InitialSetupWizard extends JFrame {
    private final JFrame owner;
    private final CardLayout pages = new CardLayout();
    private final JPanel pageHost = new JPanel(pages);
    private final JLabel stepLabel = new JLabel("Step 1 of 3");
    private final JLabel titleLabel = new JLabel("Which environment is this?");
    private final JLabel subtitleLabel = new JLabel(
            "SmartStock will show only the setup needed for this computer.");
    private final JButton backButton = new JButton("Back");
    private final JButton continueButton = new JButton("Continue");
    private final JLabel javaStatus = new JLabel("Bundled Java: Checking...");
    private final JLabel postgresStatus = new JLabel("PostgreSQL: Checked only for Server mode");
    private final JButton installPostgresButton = new JButton("Install PostgreSQL");
    private DatabaseMode selectedMode;
    private String currentPage = "environment";

    public InitialSetupWizard(JFrame owner) {
        super("SmartStock Guided Setup");
        this.owner = owner;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(860, 620));
        setSize(940, 680);
        setLocationRelativeTo(owner);

        pageHost.add(buildEnvironmentPage(), "environment");
        pageHost.add(buildModePage(), "mode");
        pageHost.add(buildReviewPage(), "review");
        pageHost.setOpaque(false);

        JPanel root = DeckersSwing.panel();
        root.setLayout(new BorderLayout(0, 18));
        root.setBorder(new EmptyBorder(22, 24, 18, 24));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(pageHost, BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        setContentPane(root);
        ThemeManager.applyToWindow(this);
        showEnvironmentPage();
    }

    private JPanel buildEnvironmentPage() {
        JPanel panel = DeckersSwing.panel();
        panel.setLayout(new GridLayout(1, 2, 18, 0));
        panel.add(environmentCard(EnvironmentProfile.DEVELOPMENT,
                "Developer / Test",
                "Uses isolated test databases, test Supabase settings, test pairing, and test sessions.",
                DeckersPalette.PURPLE));
        panel.add(environmentCard(EnvironmentProfile.PRODUCTION,
                "Production",
                "Uses isolated live databases and credentials. It never falls back to the test Supabase project.",
                DeckersPalette.CORAL));
        return panel;
    }

    private JPanel environmentCard(EnvironmentProfile profile, String title,
                                   String description, Color accent) {
        JPanel card = DeckersSwing.panel();
        DeckersSwing.styleBand(card, accent, new Insets(28, 24, 28, 24));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        JLabel titleText = new JLabel(title);
        titleText.setFont(new Font("SansSerif", Font.BOLD, 23));
        JLabel descriptionText = new JLabel(
                "<html><div style='width:300px'>" + description + "</div></html>");
        descriptionText.setFont(new Font("SansSerif", Font.PLAIN, 15));
        JButton choose = new JButton("Use " + title);
        DeckersSwing.styleUtilityButton(choose, accent);
        choose.addActionListener(event -> {
            try {
                EnvironmentProfile previous = EnvironmentProfile.active();
                EnvironmentProfile.activate(profile);
                if (previous != profile) {
                    data.DatabaseConfig selected = data.DatabaseConfig.load();
                    boolean startSelectedServer = data.DatabaseConfig.hasConfigFile()
                            && selected.mode() == DatabaseMode.SERVER
                            && selected.hasPrimaryConnection()
                            && !selected.hasUnresolvedCredentialPlaceholders()
                            && ServerSupabaseCredentials.isConfigured();
                    PostgresRuntimeService.CommandResult handoff =
                            PostgresRuntimeService.switchLanServiceEnvironment(startSelectedServer);
                    String handoffMessage = handoff.success()
                            ? ""
                            : "\n\nThe previous service was stopped, but the selected environment service "
                            + "needs attention:\n" + handoff.output();
                    JOptionPane.showMessageDialog(this,
                            "SmartStock is now set to " + profile.displayName() + ".\n\n"
                                    + (startSelectedServer
                                    ? "The LAN service now uses this environment's saved local database.\n"
                                    : "This environment has not been set up, so its LAN service remains stopped.\n")
                                    + "The app will close so sessions and device pairing cannot carry over from "
                                    + previous.displayName() + ".\n\n"
                                    + "Reopen SmartStock to continue setup."
                                    + handoffMessage,
                            "Environment Switched", JOptionPane.INFORMATION_MESSAGE);
                    for (Window window : Window.getWindows()) {
                        if (window.isDisplayable()) window.dispose();
                    }
                    return;
                }
                showModePage();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, rootCauseMessage(ex),
                        "Environment Selection", JOptionPane.ERROR_MESSAGE);
            }
        });
        card.add(titleText);
        card.add(Box.createVerticalStrut(16));
        card.add(descriptionText);
        card.add(Box.createVerticalGlue());
        card.add(choose);
        return card;
    }

    private JPanel buildHeader() {
        JPanel panel = DeckersSwing.panel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        stepLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        stepLabel.setForeground(DeckersPalette.muted());
        stepLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setForeground(DeckersPalette.text());
        titleLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
        subtitleLabel.setForeground(DeckersPalette.muted());
        subtitleLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        panel.add(stepLabel);
        panel.add(Box.createVerticalStrut(6));
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(6));
        panel.add(subtitleLabel);
        return panel;
    }

    private JPanel buildModePage() {
        JPanel panel = DeckersSwing.panel();
        panel.setLayout(new GridLayout(1, 3, 16, 0));
        panel.add(modeCard(
                DatabaseMode.SERVER,
                "Store Server",
                "Runs the local database, connects registers, and synchronizes with Supabase.",
                new String[]{"Bundled Java", "PostgreSQL 15+", "Windows service and firewall"},
                DeckersPalette.ORANGE
        ));
        panel.add(modeCard(
                DatabaseMode.CLIENT,
                "Register",
                "Connects to this store's server for everyday employee work.",
                new String[]{"No PostgreSQL", "No database credentials", "One-time admin pairing"},
                DeckersPalette.LIME
        ));
        panel.add(modeCard(
                DatabaseMode.REMOTE_ADMIN,
                "Remote Admin",
                "Provides secure management access while away from the store.",
                new String[]{"No PostgreSQL", "Trusted-device enrollment", "Physical actions blocked"},
                DeckersPalette.MAGENTA
        ));
        return panel;
    }

    private JPanel modeCard(DatabaseMode mode, String title, String description,
                            String[] points, Color accent) {
        JPanel card = DeckersSwing.panel();
        DeckersSwing.styleBand(card, accent, new Insets(22, 20, 22, 20));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel titleText = new JLabel(title);
        titleText.setFont(new Font("SansSerif", Font.BOLD, 21));
        titleText.setForeground(DeckersPalette.text());
        titleText.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        JLabel descriptionText = new JLabel("<html><div style='width:210px'>" + description
                + "</div></html>");
        descriptionText.setFont(new Font("SansSerif", Font.PLAIN, 14));
        descriptionText.setForeground(DeckersPalette.muted());
        descriptionText.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        JButton choose = new JButton("Choose " + title);
        DeckersSwing.styleUtilityButton(choose, accent);
        choose.addActionListener(event -> {
            selectedMode = mode;
            showReviewPage();
        });

        card.add(titleText);
        card.add(Box.createVerticalStrut(12));
        card.add(descriptionText);
        card.add(Box.createVerticalStrut(20));
        for (String point : points) {
            JLabel line = new JLabel("✓  " + point);
            line.setFont(new Font("SansSerif", Font.PLAIN, 13));
            line.setForeground(DeckersPalette.text());
            line.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
            card.add(line);
            card.add(Box.createVerticalStrut(9));
        }
        card.add(Box.createVerticalGlue());
        card.add(choose);
        return card;
    }

    private JPanel buildReviewPage() {
        JPanel panel = DeckersSwing.panel();
        panel.setLayout(new BorderLayout(16, 16));
        JPanel summary = DeckersSwing.panel();
        DeckersSwing.styleBand(summary, DeckersPalette.PURPLE, new Insets(22, 22, 22, 22));
        summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
        summary.setName("setupReviewSummary");
        panel.add(summary, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildFooter() {
        JPanel footer = DeckersSwing.panel();
        footer.setLayout(new BorderLayout());
        DeckersSwing.styleUtilityButton(backButton, DeckersPalette.PURPLE);
        DeckersSwing.styleUtilityButton(continueButton, DeckersPalette.LIME);
        backButton.addActionListener(event -> {
            if ("review".equals(currentPage)) showModePage();
            else showEnvironmentPage();
        });
        continueButton.addActionListener(event -> openModeSetup());
        footer.add(backButton, BorderLayout.WEST);
        footer.add(continueButton, BorderLayout.EAST);
        return footer;
    }

    private void showEnvironmentPage() {
        currentPage = "environment";
        selectedMode = null;
        stepLabel.setText("Step 1 of 3");
        titleLabel.setText("Choose Developer/Test or Production");
        subtitleLabel.setText("Each environment keeps separate data, Supabase settings, credentials, pairing, and sessions.");
        backButton.setVisible(false);
        continueButton.setVisible(false);
        pages.show(pageHost, "environment");
    }

    private void showModePage() {
        currentPage = "mode";
        selectedMode = null;
        stepLabel.setText("Step 2 of 3");
        titleLabel.setText("How will this computer be used?");
        subtitleLabel.setText("Environment: " + EnvironmentProfile.active().displayName()
                + ". Now choose this computer's role.");
        backButton.setVisible(true);
        continueButton.setVisible(false);
        pages.show(pageHost, "mode");
    }

    private void showReviewPage() {
        currentPage = "review";
        stepLabel.setText("Step 3 of 3");
        titleLabel.setText(switch (selectedMode) {
            case SERVER -> "Prepare this Store Server";
            case CLIENT -> "Connect this Register";
            case REMOTE_ADMIN -> "Enroll Remote Admin";
        });
        subtitleLabel.setText("Review what SmartStock will configure, then continue.");
        refreshReviewContent();
        backButton.setVisible(true);
        continueButton.setVisible(true);
        continueButton.setText(switch (selectedMode) {
            case SERVER -> "Continue to Server Setup";
            case CLIENT -> "Continue to Register Setup";
            case REMOTE_ADMIN -> "Continue to Remote Admin Setup";
        });
        pages.show(pageHost, "review");
        if (selectedMode == DatabaseMode.SERVER) checkServerRequirements();
    }

    private void refreshReviewContent() {
        JPanel summary = findNamedPanel(pageHost, "setupReviewSummary");
        summary.removeAll();
        String intro;
        String[] steps;
        if (selectedMode == DatabaseMode.SERVER) {
            intro = "SmartStock will prepare the local database and the secure service used by registers.";
            steps = new String[]{
                    "Use the Java runtime bundled with SmartStock",
                    "Check for PostgreSQL 15 or newer and offer installation if missing",
                    "Collect the Supabase project and server-only connection details",
                    "Create or repair the local SmartStock database",
                    "Install automatic startup and the private-LAN firewall rule",
                    "Run connection, sync, and recovery readiness checks",
                    "Maven is not installed or required"
            };
        } else if (selectedMode == DatabaseMode.CLIENT) {
            intro = "This computer will connect to a Store Server without database credentials.";
            steps = new String[]{
                    "Find the Store Server automatically or enter its hostname",
                    "Request one-time administrator pairing",
                    "Assign this register to the store",
                    "Test login, scanner, printer, and cash drawer",
                    "PostgreSQL and Maven are not installed"
            };
        } else {
            intro = "This computer will use the public SmartStock gateway for management.";
            steps = new String[]{
                    "Enter or use the preconfigured gateway address",
                    "Enroll this computer as a trusted Remote Admin device",
                    "Sign in and load only assigned stores",
                    "Block cash drawers, printers, and other physical store actions",
                    "PostgreSQL and Maven are not installed"
            };
        }

        JLabel introLabel = new JLabel("<html><div style='width:680px'>" + intro + "</div></html>");
        introLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
        introLabel.setForeground(DeckersPalette.text());
        introLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        summary.add(introLabel);
        summary.add(Box.createVerticalStrut(18));
        if (selectedMode == DatabaseMode.SERVER) {
            summary.add(javaStatus);
            summary.add(Box.createVerticalStrut(7));
            summary.add(postgresStatus);
            DeckersSwing.styleUtilityButton(installPostgresButton, DeckersPalette.ORANGE);
            for (var listener : installPostgresButton.getActionListeners()) {
                installPostgresButton.removeActionListener(listener);
            }
            installPostgresButton.addActionListener(event -> installPostgres());
            installPostgresButton.setVisible(false);
            summary.add(Box.createVerticalStrut(10));
            summary.add(installPostgresButton);
            summary.add(Box.createVerticalStrut(18));
        }
        for (String step : steps) {
            JLabel line = new JLabel("✓  " + step);
            line.setFont(new Font("SansSerif", Font.PLAIN, 14));
            line.setForeground(DeckersPalette.text());
            line.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
            summary.add(line);
            summary.add(Box.createVerticalStrut(10));
        }
        summary.revalidate();
        summary.repaint();
    }

    private void checkServerRequirements() {
        javaStatus.setText(Runtime.version().feature() >= 17
                ? "✓  Bundled Java " + Runtime.version().feature() + " is ready"
                : "✕  SmartStock requires its bundled Java 17 runtime");
        postgresStatus.setText("…  Checking PostgreSQL...");
        SwingWorker<PostgresRuntimeService.ServerPrerequisites, Void> worker = new SwingWorker<>() {
            @Override
            protected PostgresRuntimeService.ServerPrerequisites doInBackground() {
                return PostgresRuntimeService.checkServerPrerequisites();
            }

            @Override
            protected void done() {
                try {
                    var status = get();
                    postgresStatus.setText(status.postgresReady()
                            ? "✓  PostgreSQL " + status.postgresVersion() + " is ready"
                            : "!  PostgreSQL 15+ is missing; SmartStock will offer installation");
                    installPostgresButton.setVisible(!status.postgresReady());
                } catch (Exception ex) {
                    postgresStatus.setText("!  PostgreSQL could not be checked yet");
                    installPostgresButton.setVisible(true);
                }
            }
        };
        worker.execute();
    }

    private void installPostgres() {
        int answer = JOptionPane.showConfirmDialog(this,
                "SmartStock will install PostgreSQL and configure it to start automatically.\n"
                        + (PostgresRuntimeService.isWindowsRuntime()
                        ? "Windows will ask for administrator approval."
                        : "Homebrew will be used on this Mac.")
                        + "\n\nJava is already included with SmartStock. Maven will not be installed.",
                "Install PostgreSQL", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;

        installPostgresButton.setEnabled(false);
        continueButton.setEnabled(false);
        postgresStatus.setText("…  Installing PostgreSQL...");
        SwingWorker<PostgresRuntimeService.CommandResult, Void> worker = new SwingWorker<>() {
            @Override
            protected PostgresRuntimeService.CommandResult doInBackground() throws Exception {
                return PostgresRuntimeService.installOrUpdateRuntime();
            }

            @Override
            protected void done() {
                installPostgresButton.setEnabled(true);
                continueButton.setEnabled(true);
                try {
                    PostgresRuntimeService.CommandResult result = get();
                    if (!result.success()) {
                        throw new IllegalStateException(result.output());
                    }
                    JOptionPane.showMessageDialog(InitialSetupWizard.this,
                            "PostgreSQL is installed and running.",
                            "Database Ready", JOptionPane.INFORMATION_MESSAGE);
                    checkServerRequirements();
                } catch (Exception ex) {
                    postgresStatus.setText("!  PostgreSQL installation needs attention");
                    JOptionPane.showMessageDialog(InitialSetupWizard.this,
                            rootCauseMessage(ex), "Install PostgreSQL", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private void openModeSetup() {
        if (selectedMode == null) return;
        JFrame setup = selectedMode == DatabaseMode.SERVER
                ? new ServerSetupWizard(owner == null ? this : owner)
                : new DatabaseSetup(owner == null ? this : owner, selectedMode);
        setup.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                if (owner instanceof WelcomeFrame welcome) {
                    welcome.refreshAfterSetup();
                }
            }
        });
        setup.setVisible(true);
        setup.toFront();
        setup.requestFocus();
        dispose();
    }

    private static JPanel findNamedPanel(Container root, String name) {
        for (Component component : root.getComponents()) {
            if (component instanceof JPanel panel && name.equals(panel.getName())) return panel;
            if (component instanceof Container container) {
                JPanel found = findNamedPanelOrNull(container, name);
                if (found != null) return found;
            }
        }
        throw new IllegalStateException("Setup review panel was not found.");
    }

    private static JPanel findNamedPanelOrNull(Container root, String name) {
        for (Component component : root.getComponents()) {
            if (component instanceof JPanel panel && name.equals(panel.getName())) return panel;
            if (component instanceof Container container) {
                JPanel found = findNamedPanelOrNull(container, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
