package ui.screens;

import data.DatabaseConfig;
import data.DatabaseCredentials;
import data.DatabaseMode;
import data.EnvironmentProfile;
import services.ServerProvisioningService;
import services.LanApiClient;
import services.SyncWorker;
import services.PostgresRuntimeService;
import services.LocalServerRepairService;
import services.ServerSupabaseCredentials;
import services.ServerStoreSetupService;
import services.SupabaseProjectConfig;
import services.ServerFirstAdministratorService;
import ui.helpers.ThemeManager;
import ui.helpers.ResponsiveTask;

import javax.swing.*;
import java.awt.*;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DatabaseSetup extends JFrame {
    private final JComboBox<DatabaseMode> modeBox = new JComboBox<>(DatabaseMode.values());
    private final JTextField jdbcUrlField = new JTextField();
    private final JTextField dbUserField = new JTextField();
    private final JPasswordField dbPasswordField = new JPasswordField();
    private final JTextField serverHostField = new JTextField();
    private final JSpinner serverPortSpinner = new JSpinner(new SpinnerNumberModel(8443, 1, 65535, 1));
    private final JTextField locationIdField = new JTextField();
    private final JPasswordField serverCloudCredentialField = new JPasswordField();
    private final JTextField supabaseProjectUrlField = new JTextField();
    private final JTextField supabasePublishableKeyField = new JTextField();
    private final JTextField lanSubnetField = new JTextField();
    private final JSpinner syncIntervalSpinner = new JSpinner(new SpinnerNumberModel(60, 15, 3600, 15));
    private final JLabel statusLabel = new JLabel("Status: Ready");
    private final JLabel localDbStatusLabel = new JLabel("Local Database: Checking...");
    private final JLabel lanServiceStatusLabel = new JLabel("LAN Service: Checking...");
    private final List<FormRow> formRows = new ArrayList<>();
    private JButton testButton;
    private JButton testCloudButton;
    private JButton installRuntimeButton;
    private JButton installSyncServiceButton;
    private JButton postgresStatusButton;
    private JButton loadCredentialsButton;
    private JButton repairSyncButton;
    private JButton provisionButton;
    private JButton startServerButton;
    private JButton findServerButton;
    private JButton saveServerCloudCredentialButton;
    private JButton startLocalDbButton;
    private JButton stopLocalDbButton;
    private JButton startLanServiceButton;
    private JButton stopLanServiceButton;
    private JButton refreshServicesButton;
    private JButton setupStoreButton;
    private JButton initializeSupabaseButton;
    private JButton setupFirstAdministratorButton;
    private JButton advancedSettingsButton;
    private JTabbedPane setupTabs;
    private boolean advancedSettingsVisible;

    public DatabaseSetup(JFrame owner) {
        this(owner, null);
    }

    public DatabaseSetup(JFrame owner, DatabaseMode requestedMode) {
        super("SmartStock Database Setup");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(800, 720);
        setLocationRelativeTo(owner);

        DatabaseConfig config = DatabaseConfig.load();
        modeBox.setSelectedItem(requestedMode == null ? config.mode() : requestedMode);
        jdbcUrlField.setText(config.jdbcUrl());
        dbUserField.setText(config.dbUser());
        dbPasswordField.setText(config.dbPassword());
        serverHostField.setText(config.serverHost());
        serverPortSpinner.setValue(config.mode() == DatabaseMode.SERVER ? config.serverPort() : LanApiClient.baseUri().getPort());
        locationIdField.setText(config.locationId() == null ? "" : String.valueOf(config.locationId()));
        SupabaseProjectConfig publicCloudConfig = SupabaseProjectConfig.load();
        supabaseProjectUrlField.setText(publicCloudConfig.url());
        supabasePublishableKeyField.setText(publicCloudConfig.publishableKey());
        lanSubnetField.setText(SupabaseProjectConfig.loadLanSubnet());
        syncIntervalSpinner.setValue(config.syncIntervalSeconds());

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        int row = 0;
        row = addRow(form, gbc, row, "Mode", modeBox);
        row = addRow(form, gbc, row, "Local Database Address", jdbcUrlField);
        row = addRow(form, gbc, row, "Local Database User", dbUserField);
        row = addRow(form, gbc, row, "Local Database Password", dbPasswordField);
        row = addRow(form, gbc, row, "Server Host", serverHostField);
        row = addRow(form, gbc, row, "Server Port", serverPortSpinner);
        row = addRow(form, gbc, row, "Store ID or Code", locationIdField);
        row = addRow(form, gbc, row, "Supabase Project URL", supabaseProjectUrlField);
        row = addRow(form, gbc, row, "Supabase Publishable Key", supabasePublishableKeyField);
        row = addRow(form, gbc, row, "Store LAN Subnet", lanSubnetField);
        serverCloudCredentialField.setToolTipText("Paste an sb_secret_ server key (recommended) or legacy service_role key. It is stored only in " + ServerSupabaseCredentials.secureStoreDescription() + ".");
        row = addRow(form, gbc, row, "Supabase Server Key", serverCloudCredentialField);
        row = addRow(form, gbc, row, "Sync Every (Seconds)", syncIntervalSpinner);
        jdbcUrlField.setToolTipText("Advanced: SmartStock normally creates this loopback PostgreSQL address automatically.");
        dbUserField.setToolTipText("Advanced: generated by the SmartStock server installer and stored securely.");
        dbPasswordField.setToolTipText("Advanced: generated by the SmartStock server installer and stored in the operating-system credential store.");
        supabaseProjectUrlField.setToolTipText("Public HTTPS URL of this environment's Supabase project.");
        supabasePublishableKeyField.setToolTipText("Public client key from this environment's Supabase project.");
        lanSubnetField.setToolTipText("Advanced Windows firewall setting. LocalSubnet allows registers on this computer's local private network; use CIDR only for a managed VLAN.");
        serverHostField.setToolTipText("Address registers use to reach the SmartStock server.");
        locationIdField.setToolTipText(
                "Enter an existing numeric location ID or four-digit store code. A new server can create its first store.");

        testButton = new JButton("Test Local Connection");
        testCloudButton = new JButton("Test Cloud Connection");
        installRuntimeButton = new JButton("Install Database");
        installSyncServiceButton = new JButton(PostgresRuntimeService.isWindowsRuntime()
                ? "Complete Windows Server Setup" : "Install LAN & Sync Service");
        postgresStatusButton = new JButton("View Database Details");
        loadCredentialsButton = new JButton("Load Credential File");
        repairSyncButton = new JButton("Repair Schema & Sync Now");
        provisionButton = new JButton("Initialize New Server");
        startServerButton = new JButton("Start All Server Services");
        findServerButton = new JButton("Find Network Server");
        saveServerCloudCredentialButton = new JButton("Save Supabase Server Key");
        startLocalDbButton = new JButton("Start Local DB");
        stopLocalDbButton = new JButton("Stop Local DB");
        startLanServiceButton = new JButton("Start LAN Service");
        stopLanServiceButton = new JButton("Stop LAN Service");
        refreshServicesButton = new JButton("Refresh Service Status");
        setupStoreButton = new JButton("Validate or Create Store");
        initializeSupabaseButton = new JButton("Initialize Supabase Project");
        setupFirstAdministratorButton = new JButton("Set Up First Administrator");
        advancedSettingsButton = new JButton("Show Advanced Settings");
        JButton saveButton = new JButton("Save Connection Settings");
        JButton closeButton = new JButton("Close");
        testButton.addActionListener(e -> testLocalConnection());
        testCloudButton.addActionListener(e -> testCloudConnection());
        installRuntimeButton.addActionListener(e -> installRuntime());
        installSyncServiceButton.addActionListener(e -> installSyncService());
        postgresStatusButton.addActionListener(e -> showPostgresStatus());
        loadCredentialsButton.addActionListener(e -> loadSavedCredentials());
        repairSyncButton.addActionListener(e -> repairSyncLocalServer());
        provisionButton.addActionListener(e -> provisionServer());
        startServerButton.addActionListener(e -> startServerMode());
        findServerButton.addActionListener(e -> findNetworkServer());
        saveServerCloudCredentialButton.addActionListener(e -> saveServerCloudCredential());
        startLocalDbButton.addActionListener(e -> runServerControl("Starting local database...", PostgresRuntimeService::startPostgres, false));
        stopLocalDbButton.addActionListener(e -> confirmAndRunServerControl(
                "Stop the local database? SmartStock and connected registers will be unavailable until it is restarted.",
                "Stopping local database...", PostgresRuntimeService::stopPostgres));
        startLanServiceButton.addActionListener(e -> runServerControl("Starting LAN service...", PostgresRuntimeService::startLanService, false));
        stopLanServiceButton.addActionListener(e -> confirmAndRunServerControl(
                "Stop the LAN service? Connected registers will immediately lose their SmartStock connection.",
                "Stopping LAN service...", PostgresRuntimeService::stopLanService));
        refreshServicesButton.addActionListener(e -> refreshServerServiceStatus());
        setupStoreButton.addActionListener(e -> setUpStore());
        initializeSupabaseButton.addActionListener(e -> initializeSupabaseProject());
        setupFirstAdministratorButton.addActionListener(e -> openFirstAdministratorSetup());
        advancedSettingsButton.addActionListener(e -> toggleAdvancedSettings());
        saveButton.addActionListener(e -> {
            if (selectedMode() == DatabaseMode.SERVER) setUpStore();
            else saveConfig();
        });
        closeButton.addActionListener(e -> dispose());
        modeBox.addActionListener(e -> updateModeVisibility());

        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        actionButtons.add(advancedSettingsButton);
        actionButtons.add(findServerButton);
        actionButtons.add(setupStoreButton);
        actionButtons.add(saveButton);
        actionButtons.add(closeButton);

        JPanel servicesPanel = new JPanel();
        servicesPanel.setLayout(new BoxLayout(servicesPanel, BoxLayout.Y_AXIS));
        servicesPanel.add(serviceSection("Local Database", localDbStatusLabel,
                "Stores SmartStock data on this server computer. Stop it only for maintenance.",
                operationRow(startLocalDbButton, "Start PostgreSQL on this computer."),
                operationRow(stopLocalDbButton, "Stop PostgreSQL; SmartStock becomes unavailable."),
                operationRow(installRuntimeButton, "Install PostgreSQL and required server tools."),
                operationRow(postgresStatusButton, "Show the PostgreSQL version and service details."),
                operationRow(testButton, "Verify SmartStock can connect to the local database.")));
        servicesPanel.add(Box.createVerticalStrut(10));
        servicesPanel.add(serviceSection("LAN & Background Sync", lanServiceStatusLabel,
                "Provides the secure register connection and runs background cloud synchronization.",
                operationRow(startLanServiceButton, "Connect registers and resume background synchronization."),
                operationRow(stopLanServiceButton, "Disconnect registers and pause background synchronization."),
                operationRow(installSyncServiceButton, PostgresRuntimeService.isWindowsRuntime()
                        ? "Save production settings, request Windows administrator approval, install automatic startup, and restrict port 8443 to the store LAN."
                        : "Install or update the automatic LAN and sync service.")));
        servicesPanel.add(Box.createVerticalStrut(10));
        servicesPanel.add(serviceSection("Supabase Cloud", null,
                "Used for hosted backups, employee files, updates, email, and synchronization.",
                operationRow(saveServerCloudCredentialButton, "Securely save this server's Supabase secret key."),
                operationRow(initializeSupabaseButton, "Install or update the packaged SmartStock cloud schema."),
                operationRow(testCloudButton, "Verify the server-only Supabase HTTPS API connection.")));
        servicesPanel.add(Box.createVerticalStrut(10));
        servicesPanel.add(serviceSection("Setup & Recovery", null,
                "Administrative tools for first-time setup or repairing this server.",
                operationRow(loadCredentialsButton, "Load credentials already saved on this computer."),
                operationRow(provisionButton, "Create and initialize a new SmartStock server database."),
                operationRow(setupFirstAdministratorButton, "Transfer an existing administrator or create a new one."),
                operationRow(repairSyncButton, "Repair required schema and run synchronization immediately."),
                operationRow(startServerButton, "Install and start the database, LAN service, and background sync."),
                operationRow(refreshServicesButton, "Refresh the Running/Stopped indicators.")));

        setupTabs = new JTabbedPane();
        setupTabs.addTab("Connection Settings", new JScrollPane(form));
        setupTabs.addTab("Server Services & Tools", new JScrollPane(servicesPanel));

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        root.add(setupTabs, BorderLayout.CENTER);
        JPanel statusPanel = new JPanel();
        statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.Y_AXIS));
        statusPanel.add(statusLabel);
        root.add(statusPanel, BorderLayout.NORTH);
        root.add(actionButtons, BorderLayout.SOUTH);
        setContentPane(root);
        ThemeManager.applyToWindow(this);
        updateModeVisibility();
    }

    private JPanel serviceSection(String title, JLabel state, String explanation, JPanel... operations) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder(title));
        JLabel explanationLabel = new JLabel("<html>" + explanation + "</html>");
        explanationLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(explanationLabel);
        if (state != null) {
            state.setAlignmentX(Component.LEFT_ALIGNMENT);
            state.setBorder(BorderFactory.createEmptyBorder(4, 0, 6, 0));
            section.add(state);
        } else {
            section.add(Box.createVerticalStrut(6));
        }
        for (JPanel operation : operations) {
            operation.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(operation);
            section.add(Box.createVerticalStrut(4));
        }
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, section.getPreferredSize().height));
        return section;
    }

    private JPanel operationRow(JButton button, String explanation) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        button.setPreferredSize(new Dimension(220, button.getPreferredSize().height));
        button.setToolTipText(explanation);
        JLabel description = new JLabel("<html>" + explanation + "</html>");
        row.add(button, BorderLayout.WEST);
        row.add(description, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                Math.max(button.getPreferredSize().height, description.getPreferredSize().height) + 4));
        return row;
    }

    private int addRow(JPanel form, GridBagConstraints gbc, int row, String label, JComponent field) {
        JLabel labelComponent = new JLabel(label);
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        form.add(labelComponent, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(field, gbc);
        formRows.add(new FormRow(labelComponent, field, label));
        return row + 1;
    }

    private void updateModeVisibility() {
        DatabaseMode mode = selectedMode();
        boolean server = mode == DatabaseMode.SERVER;
        boolean client = mode == DatabaseMode.CLIENT;
        boolean remote = mode == DatabaseMode.REMOTE_ADMIN;
        boolean serverAdvanced = server && advancedSettingsVisible;
        setupTabs.setEnabledAt(1, server);
        if (!server && setupTabs.getSelectedIndex() == 1) setupTabs.setSelectedIndex(0);
        int selectedPort = (Integer) serverPortSpinner.getValue();
        if (server && selectedPort == 8443) serverPortSpinner.setValue(5432);
        if (client && selectedPort == 5432) serverPortSpinner.setValue(8443);

        setRowVisible(modeBox, !server || serverAdvanced);
        setRowVisible(jdbcUrlField, serverAdvanced);
        setRowVisible(dbUserField, serverAdvanced);
        setRowVisible(dbPasswordField, serverAdvanced);
        setRowVisible(serverHostField, serverAdvanced || client || remote);
        setRowVisible(serverPortSpinner, serverAdvanced || client || remote);
        setRowVisible(locationIdField, serverAdvanced);
        setRowVisible(supabaseProjectUrlField, server);
        setRowVisible(supabasePublishableKeyField, server);
        setRowVisible(lanSubnetField, serverAdvanced);
        setRowVisible(serverCloudCredentialField, server);
        setRowVisible(syncIntervalSpinner, serverAdvanced);

        setRowLabel(jdbcUrlField, "Local Database Address");
        setRowLabel(dbUserField, "Local Database User");
        setRowLabel(dbPasswordField, "Local Database Password");
        setRowLabel(serverHostField, remote ? "Remote Gateway Address" : client ? "SmartStock Server Address" : "This Server Address");
        setRowLabel(serverPortSpinner, remote ? "Remote HTTPS Port" : client ? "SmartStock HTTPS Port" : "PostgreSQL Port");

        installRuntimeButton.setVisible(server);
        installSyncServiceButton.setVisible(server);
        postgresStatusButton.setVisible(server);
        repairSyncButton.setVisible(server);
        provisionButton.setVisible(server);
        startServerButton.setVisible(server);
        findServerButton.setVisible(client);
        testButton.setVisible(server);
        testCloudButton.setVisible(server);
        loadCredentialsButton.setVisible(server);
        saveServerCloudCredentialButton.setVisible(server);
        startLocalDbButton.setVisible(server);
        stopLocalDbButton.setVisible(server);
        startLanServiceButton.setVisible(server);
        stopLanServiceButton.setVisible(server);
        refreshServicesButton.setVisible(server);
        setupStoreButton.setVisible(server);
        initializeSupabaseButton.setVisible(server);
        setupFirstAdministratorButton.setVisible(server);
        advancedSettingsButton.setVisible(server);
        localDbStatusLabel.setVisible(server);
        lanServiceStatusLabel.setVisible(server);

        if (client) {
            statusLabel.setText(LanApiClient.isPaired()
                    ? "Status: This register is paired. Employees can log in normally."
                    : "Status: Enter the server host or find it automatically, then an administrator pairs this register once.");
        } else if (remote) {
            statusLabel.setText(LanApiClient.isPaired()
                    ? "Status: Remote Admin device enrolled. Sign in to select a store."
                    : "Status: Enter the public SmartStock gateway and enroll this trusted device once.");
        } else {
            statusLabel.setText(ServerSupabaseCredentials.isConfigured()
                    ? "Status: Server mode ready; Supabase server credential is securely configured."
                    : "Status: Server mode requires its Supabase server credential for Storage, images, updates, and other cloud operations.");
        }
        revalidate();
        repaint();
        if (server) refreshServerServiceStatus();
    }

    private void toggleAdvancedSettings() {
        advancedSettingsVisible = !advancedSettingsVisible;
        advancedSettingsButton.setText(advancedSettingsVisible
                ? "Hide Advanced Settings" : "Show Advanced Settings");
        updateModeVisibility();
        if (advancedSettingsVisible) {
            statusLabel.setText("Status: Advanced server settings are visible. "
                    + "SmartStock normally configures these automatically.");
        } else {
            statusLabel.setText(ServerSupabaseCredentials.isConfigured()
                    ? "Status: Server mode ready; Supabase server credential is securely configured."
                    : "Status: Enter the Supabase project values to continue server setup.");
        }
    }

    private void setRowVisible(JComponent field, boolean visible) {
        for (FormRow row : formRows) {
            if (row.field() == field) {
                row.label().setVisible(visible);
                row.field().setVisible(visible);
                return;
            }
        }
    }

    private void setRowLabel(JComponent field, String text) {
        for (FormRow row : formRows) {
            if (row.field() == field) {
                row.label().setText(text);
                return;
            }
        }
    }

    private DatabaseMode selectedMode() {
        Object selected = modeBox.getSelectedItem();
        return selected instanceof DatabaseMode mode ? mode : DatabaseMode.CLIENT;
    }

    private void testLocalConnection() {
        saveConfig();
        statusLabel.setText("Status: Testing local connection...");
        try {
            Boolean connected = ResponsiveTask.await(this, "Testing local database...", () -> {
                ServerProvisioningService.testLocalConnection();
                return Boolean.TRUE;
            });
            if (connected == null) return;
            statusLabel.setText("Status: Local database connected.");
        } catch (Exception ex) {
            statusLabel.setText("Status: Local connection failed.");
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Local Connection", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void testCloudConnection() {
        saveConfig();
        statusLabel.setText("Status: Testing cloud connection...");
        try {
            Boolean connected = ResponsiveTask.await(this, "Testing Supabase API...", () -> {
                ServerProvisioningService.testCloudConnection();
                return Boolean.TRUE;
            });
            if (connected == null) return;
            statusLabel.setText("Status: Supabase API connected.");
        } catch (Exception ex) {
            statusLabel.setText("Status: Cloud connection failed.");
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cloud Connection", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveConfig() {
        saveConfigWithLocation(parseLocationIdIfNumeric());
    }

    private boolean saveConfigWithLocation(Integer locationId) {
        char[] serverCredential = serverCloudCredentialField.getPassword();
        try {
            boolean apiClient = selectedMode() == DatabaseMode.CLIENT || selectedMode() == DatabaseMode.REMOTE_ADMIN;
            if (apiClient) LanApiClient.configureEndpoint(serverHostField.getText(), (Integer) serverPortSpinner.getValue());
            DatabaseConfig config = DatabaseConfig.fromForm(
                    selectedMode(),
                    apiClient ? "" : jdbcUrlField.getText().trim(),
                    apiClient ? "" : dbUserField.getText().trim(),
                    apiClient ? "" : new String(dbPasswordField.getPassword()),
                    serverHostField.getText().trim(),
                    (Integer) serverPortSpinner.getValue(),
                    locationId,
                    (Integer) syncIntervalSpinner.getValue()
            );
            config.save();
            if (selectedMode() == DatabaseMode.SERVER) {
                SupabaseProjectConfig.savePublicConfig(
                        EnvironmentProfile.active(),
                        supabaseProjectUrlField.getText().trim(),
                        supabasePublishableKeyField.getText().trim(),
                        lanSubnetField.getText().trim());
            }
            boolean savedServerCredential = selectedMode() == DatabaseMode.SERVER && serverCredential.length > 0;
            if (savedServerCredential) {
                ServerSupabaseCredentials.install(serverCredential);
                serverCloudCredentialField.setText("");
            }
            statusLabel.setText(savedServerCredential
                    ? "Status: Connection settings and Supabase server credential saved securely."
                    : "Status: Saved to " + DatabaseConfig.configPath());
            return true;
        } catch (Exception ex) {
            statusLabel.setText("Status: Save failed.");
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Save Database Setup", JOptionPane.ERROR_MESSAGE);
            return false;
        } finally {
            java.util.Arrays.fill(serverCredential, '\0');
        }
    }

    private void setUpStore() {
        setUpStore(() -> { });
    }

    private void setUpStore(Runnable onReady) {
        String identifier = locationIdField.getText() == null ? "" : locationIdField.getText().trim();
        if (!saveConfigWithLocation(null)) return;

        ServerStoreSetupService.Store existing;
        try {
            existing = ResponsiveTask.await(this, "Checking store assignment...",
                    () -> ServerStoreSetupService.find(identifier));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Initialize the server database first, then create or select its store.\n\n"
                            + rootCauseMessage(ex),
                    "Store Setup", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (existing != null) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Assign this server to:\n\n"
                            + existing.name() + "\n"
                            + "Store code: " + existing.storeCode() + "\n"
                            + "Location ID: " + existing.locationId() + "\n"
                            + "Timezone: " + existing.timezone(),
                    "Confirm Store Assignment", JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) assignStore(existing, onReady);
            return;
        }

        String description = identifier.isBlank()
                ? "No store exists or is selected. Create the first store now?"
                : "No store was found for \"" + identifier + "\". Create it now?";
        if (JOptionPane.showConfirmDialog(this, description, "Create Store",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }

        JTextField nameField = new JTextField();
        JTextField codeField = new JTextField(identifier.matches("[0-9]{4}") ? identifier : "");
        JComboBox<String> timezoneField = timezoneSelector();
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Store Name:"));
        form.add(nameField);
        form.add(new JLabel("Store Code (0001-9999):"));
        form.add(codeField);
        form.add(new JLabel("Timezone:"));
        form.add(timezoneField);
        if (JOptionPane.showConfirmDialog(this, form, "Create First Store",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            ServerStoreSetupService.Store created = ResponsiveTask.await(this, "Creating store...",
                    () -> ServerStoreSetupService.create(nameField.getText(), codeField.getText(),
                            selectedTimezone(timezoneField), ""));
            if (created != null) {
                assignStore(created, onReady);
                JOptionPane.showMessageDialog(this,
                        "Store created and assigned.\n\n"
                                + created.name() + " (" + created.storeCode() + ")\n"
                                + "Location ID: " + created.locationId(),
                        "Store Ready", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, rootCauseMessage(ex),
                    "Create Store", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static JComboBox<String> timezoneSelector() {
        List<String> zones = new ArrayList<>(ZoneId.getAvailableZoneIds());
        Collections.sort(zones);
        JComboBox<String> selector = new JComboBox<>(zones.toArray(String[]::new));
        selector.setEditable(true);
        selector.setSelectedItem(ZoneId.systemDefault().getId());
        selector.setPrototypeDisplayValue("America/Argentina/Buenos_Aires");
        return selector;
    }

    private static String selectedTimezone(JComboBox<String> selector) {
        Object value = selector.getEditor().getItem();
        return value == null ? "" : value.toString().trim();
    }

    private void assignStore(ServerStoreSetupService.Store store) {
        assignStore(store, () -> { });
    }

    private void assignStore(ServerStoreSetupService.Store store, Runnable onReady) {
        DatabaseConfig current=DatabaseConfig.load();
        if(selectedMode()==DatabaseMode.SERVER&&current.locationId()!=null
                &&current.locationId()!=store.locationId()){
            try{
                services.ServerStoreSwitchService.Preflight preflight=
                        services.ServerStoreSwitchService.preflight(
                                current.locationId(),store.locationId());
                if(!preflight.ready()){
                    JOptionPane.showMessageDialog(this,preflight.blockerMessage(),
                            "Store Switch Blocked",JOptionPane.WARNING_MESSAGE);return;
                }
                int confirm=JOptionPane.showConfirmDialog(this,
                        "Switch this server to "+store.name()+"?\n\n"
                                +preflight.registerCount()+" paired register(s) will switch automatically.\n"
                                +"Current sessions will end and old drawer assignments will be removed.",
                        "Confirm Server Store Switch",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE);
                if(confirm!=JOptionPane.YES_OPTION)return;
                services.ServerStoreSwitchService.switchServerStore(
                        current.locationId(),store.locationId());
            }catch(Exception ex){
                JOptionPane.showMessageDialog(this,rootCauseMessage(ex),
                        "Store Switch",JOptionPane.ERROR_MESSAGE);return;
            }
        }
        locationIdField.setText(String.valueOf(store.locationId()));
        if (saveConfigWithLocation(store.locationId())) {
            statusLabel.setText("Status: Assigned to " + store.name()
                    + " (" + store.storeCode() + "), location ID " + store.locationId() + ".");
            onReady.run();
        }
    }

    private void initializeSupabaseProject() {
        if (selectedMode() != DatabaseMode.SERVER) {
            JOptionPane.showMessageDialog(this,
                    "Supabase project initialization is available only in SERVER mode.");
            return;
        }
        if (!saveConfigWithLocation(parseLocationIdIfNumeric())) return;
        if (!ServerSupabaseCredentials.isConfigured()) {
            JOptionPane.showMessageDialog(this,
                    "Save the Supabase Server Key before initializing the project.",
                    "Initialize Supabase", JOptionPane.WARNING_MESSAGE);
            return;
        }
        SupabaseProjectInitializerDialog dialog =
                new SupabaseProjectInitializerDialog(this,
                        () -> setUpStore(this::openFirstAdministratorSetup));
        dialog.setVisible(true);
    }

    private void openFirstAdministratorSetup() {
        if (selectedMode() != DatabaseMode.SERVER) return;
        if (DatabaseConfig.load().locationId() == null) {
            setUpStore(this::openFirstAdministratorSetup);
            return;
        }
        if (ServerFirstAdministratorService.isComplete()) {
            JOptionPane.showMessageDialog(this,
                    "A linked active administrator already exists for this server.",
                    "First Administrator", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        FirstAdministratorSetupDialog dialog =
                new FirstAdministratorSetupDialog(this, () -> {
                    statusLabel.setText("Status: First administrator ready. "
                            + "Complete one online login to enable offline access.");
                });
        dialog.setVisible(true);
    }

    private void loadSavedCredentials() {
        DatabaseCredentials credentials = DatabaseCredentials.load();
        if (selectedMode() == DatabaseMode.CLIENT) return;
        if (!credentials.hasServerCredentials()) {
            JOptionPane.showMessageDialog(
                    this,
                    "No saved SmartStock database credentials were found at:\n"
                            + DatabaseCredentials.activeCredentialsPath()
                            + "\n\nRun the SmartStock installer first, or enter the real database username and password.",
                    "Saved Credentials",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        if (credentials.hasServerCredentials()) {
            dbUserField.setText(credentials.get("SMARTSTOCK_DB_USER"));
            dbPasswordField.setText(credentials.get("SMARTSTOCK_DB_PASSWORD"));
            String serverUrl = credentials.get("SMARTSTOCK_SERVER_JDBC_URL");
            if (serverUrl != null && !serverUrl.isBlank()) {
                jdbcUrlField.setText(serverUrl);
            }
        }
        statusLabel.setText("Status: Loaded saved credentials from " + DatabaseCredentials.activeCredentialsPath());
    }

    private void findNetworkServer() {
        statusLabel.setText("Status: Looking for the SmartStock HTTPS service...");
        findServerButton.setEnabled(false);
        SwingWorker<List<LanApiClient.DiscoveredServer>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<LanApiClient.DiscoveredServer> doInBackground() throws Exception {
                return LanApiClient.discoverServers();
            }

            @Override
            protected void done() {
                findServerButton.setEnabled(true);
                try {
                    List<LanApiClient.DiscoveredServer> servers = get();
                    if (servers.isEmpty()) {
                        statusLabel.setText("Status: No SmartStock service was discovered. Enter its hostname manually.");
                        return;
                    }
                    LanApiClient.DiscoveredServer selected = servers.size() == 1
                            ? servers.get(0)
                            : (LanApiClient.DiscoveredServer) JOptionPane.showInputDialog(
                                    DatabaseSetup.this, "Choose the SmartStock server:", "Find SmartStock Server",
                                    JOptionPane.PLAIN_MESSAGE, null, servers.toArray(), servers.get(0));
                    if (selected == null) return;
                    serverHostField.setText(selected.host());
                    serverPortSpinner.setValue(selected.port());
                    locationIdField.setText(selected.locationId() == null || selected.locationId() <= 0
                            ? "" : String.valueOf(selected.locationId()));
                    LanApiClient.configureEndpoint(selected.host(), selected.port());
                    statusLabel.setText(selected.locationId() == null || selected.locationId() <= 0
                            ? "Status: SmartStock service found, but it has no assigned store yet."
                            : "Status: SmartStock service and store found. Save, then an administrator can pair this register once.");
                } catch (Exception ex) {
                    statusLabel.setText("Status: Service discovery failed. Enter the server hostname manually.");
                    JOptionPane.showMessageDialog(DatabaseSetup.this, rootCauseMessage(ex), "Find SmartStock Server", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void provisionServer() {
        saveConfig();
        statusLabel.setText("Status: Provisioning server...");
        SwingWorker<ServerProvisioningService.ProvisionResult, Void> worker = new SwingWorker<>() {
            @Override
            protected ServerProvisioningService.ProvisionResult doInBackground() throws Exception {
                return ServerProvisioningService.provision(DatabaseConfig.load());
            }

            @Override
            protected void done() {
                try {
                    ServerProvisioningService.ProvisionResult result = get();
                    statusLabel.setText("Status: Server provisioned.");
                    JOptionPane.showMessageDialog(DatabaseSetup.this,
                            result.message(),
                            "Server Provisioned",
                            JOptionPane.INFORMATION_MESSAGE);
                    setUpStore();
                } catch (Exception ex) {
                    statusLabel.setText("Status: Provision failed.");
                    JOptionPane.showMessageDialog(DatabaseSetup.this,
                            rootCauseMessage(ex),
                            "Provision Server",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void repairSyncLocalServer() {
        saveConfig();
        statusLabel.setText("Status: Repairing and syncing local server...");
        SwingWorker<LocalServerRepairService.RepairResult, Void> worker = new SwingWorker<>() {
            @Override
            protected LocalServerRepairService.RepairResult doInBackground() throws Exception {
                return LocalServerRepairService.repairAndSync();
            }

            @Override
            protected void done() {
                try {
                    LocalServerRepairService.RepairResult result = get();
                    SyncWorker.SyncStatus status = result.syncStatus();
                    statusLabel.setText("Status: Repair/sync complete. Pending "
                            + status.pendingCount() + ", failed " + status.failedCount()
                            + ", conflicts " + status.conflictCount() + ".");
                    showCommandOutput("Repair/Sync Local Server", result.message());
                } catch (Exception ex) {
                    statusLabel.setText("Status: Repair/sync failed.");
                    JOptionPane.showMessageDialog(DatabaseSetup.this,
                            rootCauseMessage(ex),
                            "Repair/Sync Local Server",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void installRuntime() {
        String installDescription = PostgresRuntimeService.isWindowsRuntime()
                ? "SmartStock will ask for Windows administrator approval, install PostgreSQL 15 or newer if missing, and set it to start automatically."
                : "SmartStock will use Homebrew to install PostgreSQL 15 or newer if missing and start it automatically.";
        int confirm = JOptionPane.showConfirmDialog(
                this,
                installDescription + "\n\nJava is included with SmartStock. Maven is not required.\n\nContinue?",
                "Install PostgreSQL",
                JOptionPane.YES_NO_OPTION
        );
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        statusLabel.setText("Status: Installing PostgreSQL...");
        SwingWorker<PostgresRuntimeService.CommandResult, Void> worker = new SwingWorker<>() {
            @Override
            protected PostgresRuntimeService.CommandResult doInBackground() throws Exception {
                return PostgresRuntimeService.installOrUpdateRuntime();
            }

            @Override
            protected void done() {
                try {
                    PostgresRuntimeService.CommandResult result = get();
                    statusLabel.setText(result.success()
                            ? "Status: PostgreSQL is ready."
                            : "Status: PostgreSQL installation failed.");
                    showCommandOutput("Install PostgreSQL", result.output());
                } catch (Exception ex) {
                    statusLabel.setText("Status: PostgreSQL installation failed.");
                    JOptionPane.showMessageDialog(DatabaseSetup.this, rootCauseMessage(ex), "Install PostgreSQL", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void showPostgresStatus() {
        statusLabel.setText("Status: Checking PostgreSQL...");
        SwingWorker<PostgresRuntimeService.CommandResult, Void> worker = new SwingWorker<>() {
            @Override
            protected PostgresRuntimeService.CommandResult doInBackground() throws Exception {
                return PostgresRuntimeService.postgresStatus();
            }

            @Override
            protected void done() {
                try {
                    PostgresRuntimeService.CommandResult result = get();
                    statusLabel.setText(result.success() ? "Status: PostgreSQL status checked." : "Status: PostgreSQL check failed.");
                    showCommandOutput("PostgreSQL Status", result.output());
                } catch (Exception ex) {
                    statusLabel.setText("Status: PostgreSQL check failed.");
                    JOptionPane.showMessageDialog(DatabaseSetup.this, rootCauseMessage(ex), "PostgreSQL Status", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void installSyncService() {
        if (PostgresRuntimeService.isWindowsRuntime()) {
            installWindowsProductionServer();
            return;
        }
        saveConfig();
        statusLabel.setText("Status: Installing background sync service...");
        SwingWorker<PostgresRuntimeService.CommandResult, Void> worker = new SwingWorker<>() {
            @Override
            protected PostgresRuntimeService.CommandResult doInBackground() throws Exception {
                return PostgresRuntimeService.ensureSyncServiceInstalled();
            }

            @Override
            protected void done() {
                try {
                    PostgresRuntimeService.CommandResult result = get();
                    statusLabel.setText(result.success()
                            ? "Status: Background sync service installed."
                            : "Status: Background sync service install failed.");
                    showCommandOutput("Install Background Sync", result.output());
                } catch (Exception ex) {
                    statusLabel.setText("Status: Background sync service install failed.");
                    JOptionPane.showMessageDialog(DatabaseSetup.this, rootCauseMessage(ex), "Install Background Sync", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void installWindowsProductionServer() {
        if (selectedMode() != DatabaseMode.SERVER) {
            JOptionPane.showMessageDialog(this,
                    "Switch Mode to SERVER before completing Windows server setup.");
            return;
        }
        String projectUrl = supabaseProjectUrlField.getText().trim();
        String publishableKey = supabasePublishableKeyField.getText().trim();
        String lanSubnet = lanSubnetField.getText().trim();
        try {
            SupabaseProjectConfig.resolveForProfile(
                    EnvironmentProfile.active(), projectUrl, publishableKey);
            if (!PostgresRuntimeService.validLanSubnetForSetup(lanSubnet)) {
                throw new IllegalArgumentException(
                        "Enter LocalSubnet or a private network such as 192.168.1.0/24.");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, rootCauseMessage(ex),
                    "Production Server Setup", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "SmartStock will now:\n"
                        + "• Save this environment's Supabase project\n"
                        + "• Install automatic Windows startup\n"
                        + "• Allow port 8443 only from " + lanSubnet + "\n"
                        + "• Start the LAN and background sync service\n\n"
                        + "Windows will ask for administrator approval. Continue?",
                "Complete Windows Server Setup", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        saveConfig();
        try {
            DatabaseConfig database = DatabaseConfig.load();
            if (!database.hasPrimaryConnection() || database.locationId() == null
                    || !ServerSupabaseCredentials.isConfigured()) {
                throw new IllegalStateException(
                        "Complete the local database, Store Location ID, and Supabase Server Key fields first.");
            }
            SupabaseProjectConfig.savePublicConfig(EnvironmentProfile.active(),
                    projectUrl, publishableKey, lanSubnet);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, rootCauseMessage(ex),
                    "Production Server Setup", JOptionPane.ERROR_MESSAGE);
            return;
        }

        statusLabel.setText("Status: Waiting for Windows administrator approval...");
        installSyncServiceButton.setEnabled(false);
        SwingWorker<PostgresRuntimeService.CommandResult, Void> worker = new SwingWorker<>() {
            @Override
            protected PostgresRuntimeService.CommandResult doInBackground() throws Exception {
                return PostgresRuntimeService.installWindowsServer(
                        SupabaseProjectConfig.load(), EnvironmentProfile.active(), lanSubnet);
            }

            @Override
            protected void done() {
                installSyncServiceButton.setEnabled(true);
                try {
                    PostgresRuntimeService.CommandResult result = get();
                    statusLabel.setText(result.success()
                            ? "Status: Windows server setup completed."
                            : "Status: Windows server setup failed.");
                    showCommandOutput("Complete Windows Server Setup", result.output());
                    if (result.success()) {
                        JOptionPane.showMessageDialog(DatabaseSetup.this,
                                "Windows server setup is complete.\n\n"
                                        + "Restart this computer, then return here and use Refresh Service Status.",
                                "Production Server Ready", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ex) {
                    statusLabel.setText("Status: Windows server setup failed.");
                    JOptionPane.showMessageDialog(DatabaseSetup.this, rootCauseMessage(ex),
                            "Production Server Setup", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void saveServerCloudCredential() {
        if (selectedMode() != DatabaseMode.SERVER) {
            JOptionPane.showMessageDialog(this, "Switch Mode to SERVER before configuring the server cloud credential.");
            return;
        }
        char[] credential = serverCloudCredentialField.getPassword();
        try {
            SupabaseProjectConfig.savePublicConfig(
                    EnvironmentProfile.active(),
                    supabaseProjectUrlField.getText().trim(),
                    supabasePublishableKeyField.getText().trim(),
                    lanSubnetField.getText().trim());
            ServerSupabaseCredentials.install(credential);
            serverCloudCredentialField.setText("");
            statusLabel.setText("Status: Supabase server credential saved securely for this server computer.");
            JOptionPane.showMessageDialog(this,
                    "The Supabase server secret key is stored in " + ServerSupabaseCredentials.secureStoreDescription() + ".\n"
                            + "Repeat this setup on each computer that runs SmartStock Server mode.",
                    "Server Cloud Credential", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            statusLabel.setText("Status: Supabase server credential was not saved.");
            JOptionPane.showMessageDialog(this, rootCauseMessage(ex),
                    "Server Cloud Credential", JOptionPane.ERROR_MESSAGE);
        } finally {
            java.util.Arrays.fill(credential, '\0');
        }
    }

    private void confirmAndRunServerControl(String warning, String progress, ServiceCommand command) {
        int choice = JOptionPane.showConfirmDialog(this, warning, "Confirm Server Service Change",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) runServerControl(progress, command, true);
    }

    private void runServerControl(String progress, ServiceCommand command, boolean stopping) {
        if (selectedMode() != DatabaseMode.SERVER) {
            JOptionPane.showMessageDialog(this, "Switch Mode to SERVER before controlling server services.");
            return;
        }
        setServiceButtonsEnabled(false);
        statusLabel.setText("Status: " + progress);
        SwingWorker<PostgresRuntimeService.CommandResult, Void> worker = new SwingWorker<>() {
            @Override protected PostgresRuntimeService.CommandResult doInBackground() throws Exception { return command.run(); }
            @Override protected void done() {
                try {
                    PostgresRuntimeService.CommandResult result = get();
                    if (!result.success()) throw new IllegalStateException(result.output());
                    statusLabel.setText("Status: Server service change completed.");
                } catch (Exception ex) {
                    statusLabel.setText("Status: Server service change failed.");
                    JOptionPane.showMessageDialog(DatabaseSetup.this, rootCauseMessage(ex),
                            "Server Service", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setServiceButtonsEnabled(true);
                    refreshServerServiceStatus();
                }
            }
        };
        worker.execute();
    }

    private void refreshServerServiceStatus() {
        if (selectedMode() != DatabaseMode.SERVER) return;
        refreshServicesButton.setEnabled(false);
        SwingWorker<PostgresRuntimeService.CommandResult[], Void> worker = new SwingWorker<>() {
            @Override protected PostgresRuntimeService.CommandResult[] doInBackground() throws Exception {
                return new PostgresRuntimeService.CommandResult[]{
                        PostgresRuntimeService.postgresStatus(), PostgresRuntimeService.syncServiceStatus()};
            }
            @Override protected void done() {
                try {
                    PostgresRuntimeService.CommandResult[] results = get();
                    boolean dbRunning = serviceLooksRunning(results[0].output(), "postgres");
                    boolean lanRunning = serviceLooksRunning(results[1].output(), "smartstock");
                    localDbStatusLabel.setText("Local Database: " + (dbRunning ? "Running" : "Stopped"));
                    lanServiceStatusLabel.setText("LAN Service: " + (lanRunning ? "Running" : "Stopped"));
                    startLocalDbButton.setEnabled(!dbRunning);
                    stopLocalDbButton.setEnabled(dbRunning);
                    startLanServiceButton.setEnabled(!lanRunning);
                    stopLanServiceButton.setEnabled(lanRunning);
                } catch (Exception ex) {
                    localDbStatusLabel.setText("Local Database: Status unavailable");
                    lanServiceStatusLabel.setText("LAN Service: Status unavailable");
                } finally {
                    refreshServicesButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    static boolean serviceLooksRunning(String output, String serviceName) {
        String value = output == null ? "" : output.toLowerCase(java.util.Locale.ROOT);
        if ("postgres".equals(serviceName)) {
            return value.contains("started") || value.matches("(?s).*postgresql[^\\n]*\\brunning\\b.*");
        }
        return value.contains("state = running") || value.matches("(?s).*status:\\s+running.*");
    }

    private void setServiceButtonsEnabled(boolean enabled) {
        startLocalDbButton.setEnabled(enabled);
        stopLocalDbButton.setEnabled(enabled);
        startLanServiceButton.setEnabled(enabled);
        stopLanServiceButton.setEnabled(enabled);
        refreshServicesButton.setEnabled(enabled);
    }

    @FunctionalInterface
    private interface ServiceCommand {
        PostgresRuntimeService.CommandResult run() throws Exception;
    }

    private void showCommandOutput(String title, String output) {
        JTextArea area = new JTextArea(output == null || output.isBlank() ? "(no output)" : output, 18, 72);
        area.setEditable(false);
        area.setCaretPosition(0);
        JOptionPane.showMessageDialog(this, new JScrollPane(area), title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void startServerMode() {
        saveConfig();
        try {
            if (DatabaseConfig.load().mode() != DatabaseMode.SERVER) {
                throw new IllegalStateException("Switch Mode to SERVER before starting server mode.");
            }
            services.ServerSetupGuardService.Activation activation =
                    services.ServerSetupGuardService.prepareToStart();
            if (!activation.startServices()) {
                statusLabel.setText("Status: " + activation.message());
                JOptionPane.showMessageDialog(this, activation.message(), "Server Role",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            PostgresRuntimeService.CommandResult syncServiceResult = ResponsiveTask.await(this,
                    "Starting SmartStock server services...",
                    PostgresRuntimeService::ensureSyncServiceInstalled);
            if (syncServiceResult == null) return;
            if (!syncServiceResult.success()) {
                throw new IllegalStateException("Background sync service install failed.\n\n" + syncServiceResult.output());
            }
            SyncWorker.startIfServerMode();
            statusLabel.setText("Status: Server sync worker started; background sync installed.");
            JOptionPane.showMessageDialog(this,
                    "Server mode is active in this running app.\nBackground sync is installed for this machine.\nUse Sync Status to monitor pending, failed, and conflict counts.",
                    "Server Mode",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            statusLabel.setText("Status: Server start failed.");
            JOptionPane.showMessageDialog(this, rootCauseMessage(ex), "Start Server Mode", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String rootCauseMessage(Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName()
                : cause.getMessage();
    }

    private Integer parseLocationIdIfNumeric() {
        String text = locationIdField.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        String clean = text.trim();
        return clean.matches("[0-9]+") && !clean.matches("[0-9]{4}")
                ? Integer.parseInt(clean)
                : null;
    }

    private record FormRow(JLabel label, JComponent field, String originalLabel) {}

}
