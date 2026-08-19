package ui.screens;

import data.DatabaseConfig;
import data.DatabaseMode;
import data.EnvironmentProfile;
import services.CloudSyncManifest;
import services.LocalDatabaseBootstrapService;
import services.PostgresRuntimeService;
import services.ServerFirstAdministratorService;
import services.ServerProvisioningService;
import services.ServerStoreSetupService;
import services.ServerSupabaseCredentials;
import services.ServerSupabaseMigrationRunner;
import services.SupabaseProjectConfig;
import services.SupabaseProjectConnectionVerifier;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import ui.helpers.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resumable, task-oriented setup for a SmartStock store server.
 *
 * Technical connection values remain available through Advanced Settings, but
 * the normal path asks only for the hosted project, store, and administrator.
 */
public final class ServerSetupWizard extends JFrame {
    private static final int STEP_COUNT = 6;

    private final JFrame owner;
    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost = new JPanel(cards);
    private final JLabel stepLabel = new JLabel();
    private final JLabel titleLabel = new JLabel();
    private final JLabel subtitleLabel = new JLabel();
    private final JLabel statusLabel = new JLabel("Checking setup progress...");
    private final JButton backButton = new JButton("Back");
    private final JButton nextButton = new JButton("Continue");
    private final JButton advancedButton = new JButton("Advanced Settings");

    private final JTextField projectUrl = new JTextField();
    private final JTextField publishableKey = new JTextField();
    private final JPasswordField serverSecret = new JPasswordField();
    private final JLabel savedSecret = new JLabel();

    private final JPanel cloudCredentialPanel = new JPanel(new GridBagLayout());
    private final JTextField cloudConnection = new JTextField();
    private final JPasswordField cloudPassword = new JPasswordField();
    private final JLabel cloudState = new JLabel("Cloud schema has not been checked.");
    private boolean cloudReady;

    private final JLabel localJava = new JLabel();
    private final JLabel localPostgres = new JLabel();
    private final JLabel localDatabase = new JLabel();
    private final JLabel postgresAdminPasswordLabel =
            new JLabel("PostgreSQL Administrator Password (optional)");
    private final JPasswordField postgresAdminPassword = new JPasswordField();

    private final JComboBox<ServerStoreSetupService.Store> stores = new JComboBox<>();
    private final JLabel existingStoreLabel = new JLabel("Existing Store");
    private final JTextField storeName = new JTextField();
    private final JTextField storeCode = new JTextField();
    private final JTextField storeTimezone = new JTextField("America/New_York");
    private final JTextField storeAddress = new JTextField();
    private final JRadioButton selectStore = new JRadioButton("Use an existing store", true);
    private final JRadioButton createStore = new JRadioButton("Create a new store");

    private final JLabel adminState = new JLabel();
    private final JTextArea finalChecks = new JTextArea();
    private int step = 1;
    private boolean busy;

    public ServerSetupWizard(JFrame owner) {
        super("SmartStock Server Setup");
        this.owner = owner;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(900, 650));
        setSize(980, 720);
        setLocationRelativeTo(owner);

        cardHost.setOpaque(false);
        cardHost.add(buildProjectPage(), "1");
        cardHost.add(buildCloudPage(), "2");
        cardHost.add(buildLocalPage(), "3");
        cardHost.add(buildStorePage(), "4");
        cardHost.add(buildAdministratorPage(), "5");
        cardHost.add(buildFinishPage(), "6");

        JPanel root = DeckersSwing.panel();
        root.setLayout(new BorderLayout(0, 16));
        root.setBorder(new EmptyBorder(20, 24, 16, 24));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(cardHost, BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        setContentPane(root);
        loadSavedProject();
        ThemeManager.applyToWindow(this);
        showStep(1);
        determineResumeStep();
    }

    private JPanel buildHeader() {
        JPanel panel = DeckersSwing.panel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        stepLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        stepLabel.setForeground(DeckersPalette.muted());
        stepLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
        subtitleLabel.setForeground(DeckersPalette.muted());
        subtitleLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        panel.add(stepLabel);
        panel.add(Box.createVerticalStrut(5));
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(5));
        panel.add(subtitleLabel);
        return panel;
    }

    private JPanel buildFooter() {
        JPanel footer = DeckersSwing.panel();
        footer.setLayout(new BorderLayout(12, 0));
        DeckersSwing.styleUtilityButton(backButton, DeckersPalette.PURPLE);
        DeckersSwing.styleUtilityButton(advancedButton, DeckersPalette.ORANGE);
        DeckersSwing.styleUtilityButton(nextButton, DeckersPalette.LIME);
        backButton.addActionListener(event -> showStep(Math.max(1, step - 1)));
        nextButton.addActionListener(event -> performCurrentStep());
        advancedButton.addActionListener(event -> openAdvancedSettings());
        JPanel right = DeckersSwing.panel();
        right.setLayout(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(advancedButton);
        right.add(nextButton);
        footer.add(backButton, BorderLayout.WEST);
        footer.add(statusLabel, BorderLayout.CENTER);
        footer.add(right, BorderLayout.EAST);
        return footer;
    }

    private JPanel buildProjectPage() {
        JPanel page = page();
        JPanel form = form();
        int row = 0;
        row = addRow(form, row, "Supabase Project URL", projectUrl);
        row = addRow(form, row, "Publishable Key", publishableKey);
        row = addRow(form, row, "Server Secret Key", serverSecret);
        GridBagConstraints gbc = constraints(0, row);
        gbc.gridwidth = 2;
        savedSecret.setForeground(DeckersPalette.muted());
        savedSecret.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        form.add(savedSecret, gbc);
        page.add(note("These values come from the selected Development or Production "
                + "Supabase project. The server secret is stored only in the operating "
                + "system credential store and is never sent to registers."), BorderLayout.NORTH);
        page.add(form, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildCloudPage() {
        JPanel page = page();
        cloudConnection.setToolTipText(
                "Use the Direct or Session Pooler connection on port 5432.");
        cloudPassword.setToolTipText("Used once and immediately cleared.");
        int row = 0;
        row = addRow(cloudCredentialPanel, row, "PostgreSQL Connection", cloudConnection);
        addRow(cloudCredentialPanel, row, "Database Password", cloudPassword);
        page.add(note("SmartStock first checks whether the cloud schema is already current. "
                + "For a new or outdated project, paste the Direct or Session Pooler "
                + "connection and enter the database password once. Port 6543 is not accepted."),
                BorderLayout.NORTH);
        JPanel center = DeckersSwing.panel();
        center.setLayout(new BorderLayout(0, 16));
        center.add(cloudState, BorderLayout.NORTH);
        center.add(cloudCredentialPanel, BorderLayout.CENTER);
        page.add(center, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildLocalPage() {
        JPanel page = page();
        JPanel checks = DeckersSwing.panel();
        DeckersSwing.styleBand(checks, DeckersPalette.ORANGE, new Insets(24, 24, 24, 24));
        checks.setLayout(new BoxLayout(checks, BoxLayout.Y_AXIS));
        checks.add(localJava);
        checks.add(Box.createVerticalStrut(14));
        checks.add(localPostgres);
        checks.add(Box.createVerticalStrut(14));
        checks.add(localDatabase);
        checks.add(Box.createVerticalStrut(18));
        checks.add(postgresAdminPasswordLabel);
        checks.add(Box.createVerticalStrut(7));
        checks.add(postgresAdminPassword);
        postgresAdminPassword.setToolTipText(
                "Leave blank for SmartStock to generate and securely use a one-time bootstrap password.");
        page.add(note("SmartStock checks Java and PostgreSQL, offers PostgreSQL installation "
                + "when needed, then creates or repairs the local database. Registers never "
                + "receive these local credentials."), BorderLayout.NORTH);
        page.add(checks, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildStorePage() {
        JPanel page = page();
        ButtonGroup group = new ButtonGroup();
        group.add(selectStore);
        group.add(createStore);
        selectStore.addActionListener(event -> refreshStoreControls());
        createStore.addActionListener(event -> refreshStoreControls());

        JPanel form = form();
        int row = 0;
        GridBagConstraints full = constraints(0, row++);
        full.gridwidth = 2;
        form.add(selectStore, full);
        row = addRow(form, row, existingStoreLabel, stores);
        full = constraints(0, row++);
        full.gridwidth = 2;
        form.add(createStore, full);
        row = addRow(form, row, "Store Name", storeName);
        row = addRow(form, row, "Four-digit Store Number / Code", storeCode);
        row = addRow(form, row, "Timezone", storeTimezone);
        addRow(form, row, "Address (optional)", storeAddress);
        page.add(note("Choose a store already restored into this local database, or create "
                + "the first store. SmartStock assigns the numeric database ID automatically."),
                BorderLayout.NORTH);
        page.add(form, BorderLayout.CENTER);
        refreshStoreControls();
        return page;
    }

    private JPanel buildAdministratorPage() {
        JPanel page = page();
        JButton open = new JButton("Transfer or Create First Administrator");
        DeckersSwing.styleUtilityButton(open, DeckersPalette.MAGENTA);
        open.addActionListener(event -> openAdministratorSetup());
        JPanel center = DeckersSwing.panel();
        DeckersSwing.styleBand(center, DeckersPalette.MAGENTA, new Insets(28, 28, 28, 28));
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        adminState.setFont(new Font("SansSerif", Font.BOLD, 16));
        center.add(adminState);
        center.add(Box.createVerticalStrut(22));
        center.add(open);
        page.add(note("Transfer an active Development administrator while preserving the "
                + "badge identity, or create a new administrator. Passwords are entered "
                + "privately and are never copied from another project."), BorderLayout.NORTH);
        page.add(center, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildFinishPage() {
        JPanel page = page();
        finalChecks.setEditable(false);
        finalChecks.setLineWrap(true);
        finalChecks.setWrapStyleWord(true);
        finalChecks.setFont(new Font("Monospaced", Font.PLAIN, 14));
        finalChecks.setBorder(new EmptyBorder(18, 18, 18, 18));
        finalChecks.setText("Ready to install and verify server services.");
        page.add(note("SmartStock installs automatic startup, starts the LAN and sync "
                + "services, verifies local and cloud access, and uses LocalSubnet for "
                + "the Windows firewall rule. You will then complete the first online login."),
                BorderLayout.NORTH);
        page.add(new JScrollPane(finalChecks), BorderLayout.CENTER);
        return page;
    }

    private void loadSavedProject() {
        try {
            SupabaseProjectConfig project = SupabaseProjectConfig.load();
            projectUrl.setText(project.url());
            publishableKey.setText(project.publishableKey());
        } catch (Exception ignored) {
        }
        savedSecret.setText(ServerSupabaseCredentials.isConfigured()
                ? "✓ A server secret is already securely saved. Leave this field blank to keep it."
                : "A server secret key is required.");
    }

    private void determineResumeStep() {
        setBusy(true, "Checking saved setup and finding the first incomplete step...");
        SwingWorker<Integer, Void> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() {
                try {
                    SupabaseProjectConfig.load();
                    if (!ServerSupabaseCredentials.isConfigured()) return 1;
                } catch (Exception ex) {
                    return 1;
                }
                try {
                    CloudSyncManifest.fetch();
                    cloudReady = true;
                } catch (Exception ex) {
                    return 2;
                }
                try {
                    DatabaseConfig config = DatabaseConfig.load();
                    if (config.mode() != DatabaseMode.SERVER || !config.hasPrimaryConnection()) return 3;
                    ServerProvisioningService.testLocalConnection();
                } catch (Exception ex) {
                    return 3;
                }
                try {
                    DatabaseConfig config = DatabaseConfig.load();
                    if (config.locationId() == null
                            || ServerStoreSetupService.find(String.valueOf(config.locationId())) == null) {
                        return 4;
                    }
                } catch (Exception ex) {
                    return 4;
                }
                if (!ServerFirstAdministratorService.isComplete()) {
                    try {
                        ServerStoreSetupService.restoreAssignedFromCloud();
                    } catch (Exception repairFailure) {
                        return 4;
                    }
                }
                if (!ServerFirstAdministratorService.isComplete()) return 5;
                try {
                    DatabaseConfig config = DatabaseConfig.load();
                    if (services.ServerSetupGuardService.assess(config.locationId()).current() == null) {
                        return 4;
                    }
                } catch (Exception registryFailure) {
                    return 4;
                }
                return 6;
            }

            @Override
            protected void done() {
                try {
                    showStep(get());
                    statusLabel.setText("Resumed at the first incomplete step.");
                } catch (Exception ex) {
                    showStep(1);
                    statusLabel.setText("Start with the Supabase project connection.");
                } finally {
                    setBusy(false, statusLabel.getText());
                }
            }
        };
        worker.execute();
    }

    private void showStep(int requested) {
        step = Math.max(1, Math.min(STEP_COUNT, requested));
        stepLabel.setText("SERVER SETUP  •  STEP " + step + " OF " + STEP_COUNT
                + "  •  " + EnvironmentProfile.active().displayName());
        titleLabel.setText(switch (step) {
            case 1 -> "Connect Supabase";
            case 2 -> "Initialize Cloud";
            case 3 -> "Prepare Local Database";
            case 4 -> "Create or Select Store";
            case 5 -> "Create First Administrator";
            default -> "Start and Verify Server";
        });
        subtitleLabel.setText(switch (step) {
            case 1 -> "Enter the three project values SmartStock uses during normal operation.";
            case 2 -> "Install the packaged schema only when this project needs it.";
            case 3 -> "SmartStock handles the local database and server-only access.";
            case 4 -> "No numeric store ID needs to be entered.";
            case 5 -> "Transfer an existing administrator or create a new one.";
            default -> "Finish automatic startup, LAN access, synchronization, and health checks.";
        });
        backButton.setVisible(step > 1);
        nextButton.setText(switch (step) {
            case 1 -> "Test and Continue";
            case 2 -> cloudReady ? "Continue" : "Initialize and Continue";
            case 3 -> "Prepare and Continue";
            case 4 -> "Save Store and Continue";
            case 5 -> "Continue";
            default -> "Start and Verify";
        });
        cards.show(cardHost, String.valueOf(step));
        if (step == 2) checkCloudSchema();
        if (step == 3) refreshLocalState();
        if (step == 4) loadStores();
        if (step == 5) refreshAdministratorState();
    }

    private void performCurrentStep() {
        if (busy) return;
        switch (step) {
            case 1 -> connectProject();
            case 2 -> initializeCloud();
            case 3 -> prepareLocalDatabase();
            case 4 -> saveStore();
            case 5 -> {
                if (!ServerFirstAdministratorService.isComplete()) {
                    openAdministratorSetup();
                } else {
                    showStep(6);
                }
            }
            default -> finishServer();
        }
    }

    private void connectProject() {
        char[] entered = serverSecret.getPassword();
        String secret = entered.length == 0 ? ServerSupabaseCredentials.get()
                : new String(entered).trim();
        setBusy(true, "Verifying the Supabase URL and both keys...");
        SwingWorker<SupabaseProjectConfig, Void> worker = new SwingWorker<>() {
            @Override
            protected SupabaseProjectConfig doInBackground() throws Exception {
                SupabaseProjectConfig project = SupabaseProjectConfig.resolveForProfile(
                        EnvironmentProfile.active(), projectUrl.getText().trim(),
                        publishableKey.getText().trim());
                SupabaseProjectConnectionVerifier.verify(project, secret);
                SupabaseProjectConfig.savePublicConfig(EnvironmentProfile.active(),
                        project.url(), project.publishableKey(), "LocalSubnet");
                if (entered.length > 0) ServerSupabaseCredentials.install(entered);
                return project;
            }

            @Override
            protected void done() {
                Arrays.fill(entered, '\0');
                serverSecret.setText("");
                try {
                    get();
                    savedSecret.setText("✓ Supabase project and server credential verified.");
                    statusLabel.setText("Supabase connection verified.");
                    showStep(2);
                } catch (Exception ex) {
                    showError("Connect Supabase", ex);
                } finally {
                    setBusy(false, statusLabel.getText());
                }
            }
        };
        worker.execute();
    }

    private void checkCloudSchema() {
        cloudState.setText("… Checking the SmartStock cloud schema...");
        cloudCredentialPanel.setVisible(false);
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    CloudSyncManifest.fetch();
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            }

            @Override
            protected void done() {
                try {
                    cloudReady = get();
                } catch (Exception ex) {
                    cloudReady = false;
                }
                cloudState.setText(cloudReady
                        ? "✓ SmartStock cloud schema is current and the API-only sync functions respond."
                        : "! This project needs SmartStock initialization or an update.");
                cloudCredentialPanel.setVisible(!cloudReady);
                nextButton.setText(cloudReady ? "Continue" : "Initialize and Continue");
                cardHost.revalidate();
            }
        };
        worker.execute();
    }

    private void initializeCloud() {
        if (cloudReady) {
            showStep(3);
            return;
        }
        char[] password = cloudPassword.getPassword();
        String connection = cloudConnection.getText().trim();
        if (connection.isBlank() || password.length == 0) {
            Arrays.fill(password, '\0');
            warn("Enter the Supabase Direct or Session Pooler connection and database password.");
            return;
        }
        setBusy(true, "Installing checksum-verified SmartStock migrations...");
        SwingWorker<ServerSupabaseMigrationRunner.Result, Void> worker = new SwingWorker<>() {
            @Override
            protected ServerSupabaseMigrationRunner.Result doInBackground() throws Exception {
                return ServerSupabaseMigrationRunner.migrate(
                        connection, password, SupabaseProjectConfig.load());
            }

            @Override
            protected void done() {
                Arrays.fill(password, '\0');
                cloudPassword.setText("");
                cloudConnection.setText("");
                try {
                    var result = get();
                    cloudReady = true;
                    statusLabel.setText(result.message());
                    showStep(3);
                } catch (Exception ex) {
                    showError("Initialize Supabase", ex);
                } finally {
                    setBusy(false, statusLabel.getText());
                }
            }
        };
        worker.execute();
    }

    private void refreshLocalState() {
        PostgresRuntimeService.ServerPrerequisites prerequisites =
                PostgresRuntimeService.checkServerPrerequisites();
        localJava.setText(prerequisites.javaReady()
                ? "✓ Java " + prerequisites.javaVersion() + " is ready"
                : "✕ Java 17 or newer is required");
        localPostgres.setText(prerequisites.postgresReady()
                ? "✓ PostgreSQL " + prerequisites.postgresVersion() + " is ready"
                : "! PostgreSQL 15 or newer will be installed");
        DatabaseConfig config = DatabaseConfig.load();
        localDatabase.setText(config.hasPrimaryConnection()
                ? "✓ Local database credentials are securely configured"
                : "! SmartStock will create its private local database account");
        boolean needsBootstrap = !config.hasPrimaryConnection();
        postgresAdminPasswordLabel.setVisible(needsBootstrap);
        postgresAdminPassword.setVisible(needsBootstrap);
    }

    private void prepareLocalDatabase() {
        char[] administratorPassword = postgresAdminPassword.getPassword();
        setBusy(true, "Preparing PostgreSQL and the local SmartStock database...");
        SwingWorker<ServerProvisioningService.ProvisionResult, Void> worker = new SwingWorker<>() {
            @Override
            protected ServerProvisioningService.ProvisionResult doInBackground() throws Exception {
                var prerequisites = PostgresRuntimeService.checkServerPrerequisites();
                boolean needsAutomaticCredential = administratorPassword.length == 0
                        && !LocalDatabaseBootstrapService.hasGeneratedAdministratorCredential();
                if (!prerequisites.postgresReady() || needsAutomaticCredential) {
                    var installed = PostgresRuntimeService.installOrUpdateRuntime();
                    if (!installed.success()) throw new IllegalStateException(installed.output());
                }
                DatabaseConfig config =
                        LocalDatabaseBootstrapService.ensureConfigured(administratorPassword);
                PostgresRuntimeService.startPostgres();
                return ServerProvisioningService.provision(config);
            }

            @Override
            protected void done() {
                Arrays.fill(administratorPassword, '\0');
                postgresAdminPassword.setText("");
                try {
                    var result = get();
                    localDatabase.setText("✓ Local SmartStock database is ready");
                    statusLabel.setText(lastLine(result.message()));
                    showStep(4);
                } catch (Exception ex) {
                    showError("Prepare Local Database", ex);
                } finally {
                    setBusy(false, statusLabel.getText());
                    refreshLocalState();
                }
            }
        };
        worker.execute();
    }

    private void loadStores() {
        stores.removeAllItems();
        stores.setEnabled(false);
        nextButton.setEnabled(false);
        statusLabel.setText("Loading existing stores from this environment...");
        SwingWorker<List<ServerStoreSetupService.Store>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<ServerStoreSetupService.Store> doInBackground() throws Exception {
                Map<Integer, ServerStoreSetupService.Store> available = new LinkedHashMap<>();
                for (var store : ServerStoreSetupService.list()) {
                    available.put(store.locationId(), store);
                }
                for (var store : ServerStoreSetupService.listCloud()) {
                    available.putIfAbsent(store.locationId(), store);
                }
                return List.copyOf(available.values());
            }

            @Override
            protected void done() {
                try {
                    List<ServerStoreSetupService.Store> available = get();
                    available.forEach(stores::addItem);
                    if (available.isEmpty()) {
                        createStore.setSelected(true);
                        if (storeCode.getText().isBlank()) storeCode.setText("0001");
                        statusLabel.setText("No existing stores were found. Create the first store below.");
                    } else {
                        selectStore.setSelected(true);
                        Integer assigned = DatabaseConfig.load().locationId();
                        for (int i = 0; i < stores.getItemCount(); i++) {
                            if (assigned != null && stores.getItemAt(i).locationId() == assigned) {
                                stores.setSelectedIndex(i);
                                break;
                            }
                        }
                        statusLabel.setText(available.size() == 1
                                ? "Found the existing store for this environment."
                                : "Select the existing store for this server.");
                    }
                } catch (Exception ex) {
                    createStore.setSelected(true);
                    if (storeCode.getText().isBlank()) storeCode.setText("0001");
                    statusLabel.setText("Existing stores could not be loaded: "
                            + rootCauseMessage(ex));
                } finally {
                    nextButton.setEnabled(true);
                    refreshStoreControls();
                }
            }
        };
        worker.execute();
    }

    private void saveStore() {
        try {
            ServerStoreSetupService.Store selected;
            if (createStore.isSelected()) {
                selected = ServerStoreSetupService.create(storeName.getText(),
                        storeCode.getText(), storeTimezone.getText(), storeAddress.getText());
            } else {
                selected = (ServerStoreSetupService.Store) stores.getSelectedItem();
                if (selected == null) throw new IllegalArgumentException(
                        "Select an existing store or choose Create a new store.");
                selected = ServerStoreSetupService.restoreFromCloud(selected);
            }
            DatabaseConfig current = DatabaseConfig.load();
            services.ServerStoreSwitchService.Preflight switchPreflight=null;
            if(current.locationId()!=null&&current.locationId()!=selected.locationId()){
                try(java.sql.Connection connection=data.DB.getConnection()){
                    switchPreflight=services.ServerStoreSwitchService.preflight(
                            connection,current.locationId(),selected.locationId());
                }
                if(!switchPreflight.ready())throw new IllegalStateException(switchPreflight.blockerMessage());
                int confirm=JOptionPane.showConfirmDialog(this,
                        "Switch this server from store "+current.locationId()+" to "
                                +selected.name()+"?\n\n"
                                +switchPreflight.registerCount()+" paired register(s) will switch automatically.\n"
                                +"Their current employee sessions will end and old cash-drawer assignments will be removed.\n"
                                +"Assign each register to a cash drawer at the new store before taking cash.",
                        "Confirm Server Store Switch",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE);
                if(confirm!=JOptionPane.YES_OPTION)return;
            }
            DatabaseConfig selectedConfig = new DatabaseConfig(DatabaseMode.SERVER, current.jdbcUrl(),
                    current.dbUser(), current.dbPassword(), current.serverHost(), current.serverPort(),
                    selected.locationId(), current.syncIntervalSeconds());
            selectedConfig.save();
            try {
                services.ServerSetupGuardService.Assessment assessment =
                        services.ServerSetupGuardService.assess(selected.locationId());
                java.util.List<services.LanApiClient.DiscoveredServer> lanServers =
                        services.ServerSetupGuardService.discoverStoreServers(selected.storeCode());
                if (assessment.primary()==null&&!lanServers.isEmpty()) {
                    throw new IllegalStateException("LAN discovery found an existing SmartStock server for this store, but it is not in the secured server registry. Open Server Setup on the existing server so it can register before adding this machine.");
                }
                boolean anotherPrimary = assessment.primary() != null
                        && (assessment.current() == null || !assessment.primary().serverInstanceId()
                        .equals(assessment.current().serverInstanceId()));
                if (anotherPrimary) {
                    Object[] options = {"Configure as Standby", "Prepare Replacement", "Cancel"};
                    int choice = JOptionPane.showOptionDialog(this,
                            "This store already has an " + assessment.primary().health().toLowerCase()
                                    + " primary server:\n\n" + assessment.primary().displayName()
                                    + " (" + assessment.primary().endpointHost() + ")\n\n"
                                    + "LAN discovery: " + (lanServers.isEmpty()?"not currently detected":"server detected") + "\n\n"
                                    + "SmartStock will not start a second writable server.",
                            "Existing Store Server", JOptionPane.DEFAULT_OPTION,
                            JOptionPane.WARNING_MESSAGE, null, options, options[0]);
                    if (choice < 0 || choice == 2) {
                        current.save();
                        return;
                    }
                    services.ServerSetupGuardService.registerForStore(selected.locationId(), true);
                    statusLabel.setText(choice == 1
                            ? "Replacement standby prepared. Start the verified handoff from the current primary server."
                            : "Recovery-ready standby registered for " + selected.name() + ".");
                } else {
                    services.ServerSetupGuardService.registerForStore(selected.locationId(), false);
                    statusLabel.setText("Store " + selected.name() + " is assigned to this primary server.");
                }
                try (java.sql.Connection connection = data.DB.getConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        int moved=0;
                        if(current.locationId()!=null&&current.locationId()!=selected.locationId()){
                            moved=services.ServerStoreSwitchService.switchPairedRegisters(
                                    connection,current.locationId(),selected.locationId());
                        }
                        services.DeviceCredentialService.assignLocalInstallationToStore(
                                connection, selected.locationId());
                        connection.commit();
                        if(moved>0)statusLabel.setText("Store "+selected.name()+" assigned; "
                                +moved+" paired register(s) will switch automatically.");
                    } catch (Exception ex) {
                        connection.rollback();
                        throw ex;
                    }
                }
            } catch (Exception ex) {
                current.save();
                throw ex;
            }
            showStep(5);
        } catch (Exception ex) {
            showError("Store Setup", ex);
        }
    }

    private void refreshStoreControls() {
        boolean hasExistingStores = stores.getItemCount() > 0;
        if (!hasExistingStores) createStore.setSelected(true);
        selectStore.setEnabled(hasExistingStores);
        selectStore.setVisible(hasExistingStores);
        existingStoreLabel.setVisible(hasExistingStores);
        stores.setVisible(hasExistingStores);
        boolean creating = createStore.isSelected() || !hasExistingStores;
        stores.setEnabled(!creating);
        storeName.setEnabled(creating);
        storeCode.setEnabled(creating);
        storeTimezone.setEnabled(creating);
        storeAddress.setEnabled(creating);
        cardHost.revalidate();
        cardHost.repaint();
    }

    private void refreshAdministratorState() {
        boolean complete = ServerFirstAdministratorService.isComplete();
        adminState.setText(complete
                ? "✓ A linked store administrator is ready"
                : "! Create or transfer the first administrator");
        nextButton.setText(complete ? "Continue" : "Set Up Administrator");
    }

    private void openAdministratorSetup() {
        FirstAdministratorSetupDialog dialog =
                new FirstAdministratorSetupDialog(this, () -> {
                    refreshAdministratorState();
                    if (ServerFirstAdministratorService.isComplete()) showStep(6);
                });
        dialog.setVisible(true);
    }

    private void finishServer() {
        setBusy(true, "Installing, starting, and checking server services...");
        finalChecks.setText("… Checking local database\n… Checking Supabase sync API\n"
                + "… Installing background service\n… Starting LAN service\n");
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                StringBuilder result = new StringBuilder();
                services.ServerSetupGuardService.Activation activation =
                        services.ServerSetupGuardService.prepareToStart();
                result.append("✓ ").append(activation.message()).append("\n");
                ServerProvisioningService.testLocalConnection();
                result.append("✓ Local database connection\n");
                ServerProvisioningService.testCloudConnection();
                result.append("✓ Supabase API-only sync connection\n");
                PostgresRuntimeService.CommandResult installed;
                if (PostgresRuntimeService.isWindowsRuntime()) {
                    installed = PostgresRuntimeService.installWindowsServer(
                            SupabaseProjectConfig.load(), EnvironmentProfile.active(),
                            "LocalSubnet");
                } else {
                    installed = PostgresRuntimeService.ensureSyncServiceInstalled();
                }
                if (!installed.success()) throw new IllegalStateException(installed.output());
                result.append("✓ Automatic background service installed\n");
                var started = PostgresRuntimeService.startLanService();
                if (!started.success()) throw new IllegalStateException(started.output());
                if (!activation.startServices()) {
                    return result.append("✓ Standby coordination heartbeat started; PostgreSQL data is preserved and LAN/sync traffic remains disabled.\n").toString();
                }
                var identity = services.LanTlsIdentity.loadOrCreate();
                boolean ready = false;
                for (int attempt = 0; attempt < 30; attempt++) {
                    if (services.LanApiClient.isServerReachable(
                            services.LanTlsIdentity.tlsHostName(),
                            services.LanApiServer.DEFAULT_PORT, identity.fingerprint())) {
                        ready = true;
                        break;
                    }
                    Thread.sleep(1_000);
                }
                if (!ready) {
                    var taskStatus = PostgresRuntimeService.syncServiceStatus();
                    throw new IllegalStateException("The Windows service was installed but its HTTPS "
                            + "health endpoint did not become ready within 30 seconds.\n\n"
                            + taskStatus.output());
                }
                result.append("✓ LAN and synchronization service started\n");
                result.append("✓ Store assignment and first administrator verified\n");
                result.append("✓ Register reconnection: an administrator must point each register to ")
                        .append(services.LanTlsIdentity.tlsHostName()).append(":")
                        .append(services.LanApiServer.DEFAULT_PORT)
                        .append(", verify the TLS identity, and complete the one-time pairing.\n");
                return result.toString();
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    boolean standby = result.contains("Standby coordination heartbeat started");
                    finalChecks.setText(result + (standby
                            ? "\nStandby preparation is complete. Return here after the primary marks the handoff ready."
                            : "\nSetup is ready. Continue to the first online administrator login."));
                    nextButton.setText(standby ? "Close" : "Continue to Login");
                    for (var listener : nextButton.getActionListeners()) {
                        nextButton.removeActionListener(listener);
                    }
                    nextButton.addActionListener(event -> { if (standby) dispose(); else completeWizard(); });
                    statusLabel.setText(standby ? "Standby server is safely prepared."
                            : "Server setup is ready for the first online login.");
                } catch (Exception ex) {
                    finalChecks.append("\n! " + rootCauseMessage(ex));
                    showError("Start Server", ex);
                } finally {
                    setBusy(false, statusLabel.getText());
                }
            }
        };
        worker.execute();
    }

    private void completeWizard() {
        if (owner instanceof WelcomeFrame welcome) welcome.refreshAfterSetup();
        dispose();
    }

    private void openAdvancedSettings() {
        DatabaseSetup setup = new DatabaseSetup(this, DatabaseMode.SERVER);
        setup.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                loadSavedProject();
                if (step == 3) refreshLocalState();
            }
        });
        setup.setVisible(true);
    }

    private void setBusy(boolean value, String message) {
        busy = value;
        backButton.setEnabled(!value);
        nextButton.setEnabled(!value);
        advancedButton.setEnabled(!value);
        if (message != null) statusLabel.setText(message);
    }

    private void showError(String title, Throwable throwable) {
        statusLabel.setText(title + " needs attention.");
        JOptionPane.showMessageDialog(this, rootCauseMessage(throwable),
                title, JOptionPane.ERROR_MESSAGE);
    }

    private void warn(String message) {
        JOptionPane.showMessageDialog(this, message,
                "Server Setup", JOptionPane.WARNING_MESSAGE);
    }

    private static String lastLine(String value) {
        if (value == null || value.isBlank()) return "Completed.";
        String[] lines = value.strip().split("\\R");
        return lines[lines.length - 1];
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank()
                ? cause.getClass().getSimpleName() : message;
    }

    private static JPanel page() {
        JPanel panel = DeckersSwing.panel();
        panel.setLayout(new BorderLayout(0, 20));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        return panel;
    }

    private static JPanel form() {
        JPanel panel = DeckersSwing.panel();
        panel.setLayout(new GridBagLayout());
        return panel;
    }

    private static int addRow(JPanel panel, int row, String label, Component field) {
        return addRow(panel, row, new JLabel(label), field);
    }

    private static int addRow(JPanel panel, int row, JLabel label, Component field) {
        GridBagConstraints left = constraints(0, row);
        left.weightx = 0;
        panel.add(label, left);
        GridBagConstraints right = constraints(1, row);
        right.weightx = 1;
        panel.add(field, right);
        return row + 1;
    }

    private static GridBagConstraints constraints(int column, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = column;
        gbc.gridy = row;
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        return gbc;
    }

    private static JTextArea note(String value) {
        JTextArea note = new JTextArea(value);
        note.setEditable(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setOpaque(false);
        note.setFont(new Font("SansSerif", Font.PLAIN, 15));
        note.setForeground(DeckersPalette.muted());
        note.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        return note;
    }
}
