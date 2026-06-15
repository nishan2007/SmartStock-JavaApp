package ui.screens;

import data.DB;
import data.DatabaseConfig;
import data.DatabaseCredentials;
import data.DatabaseMode;
import services.ServerProvisioningService;
import services.SyncWorker;
import services.PostgresRuntimeService;
import services.LocalServerRepairService;
import ui.helpers.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.sql.Connection;

public class DatabaseSetup extends JFrame {
    private final JComboBox<DatabaseMode> modeBox = new JComboBox<>(DatabaseMode.values());
    private final JTextField jdbcUrlField = new JTextField();
    private final JTextField dbUserField = new JTextField();
    private final JPasswordField dbPasswordField = new JPasswordField();
    private final JTextField serverHostField = new JTextField();
    private final JSpinner serverPortSpinner = new JSpinner(new SpinnerNumberModel(5432, 1, 65535, 1));
    private final JTextField locationIdField = new JTextField();
    private final JTextField cloudUrlField = new JTextField();
    private final JTextField cloudUserField = new JTextField();
    private final JPasswordField cloudPasswordField = new JPasswordField();
    private final JSpinner syncIntervalSpinner = new JSpinner(new SpinnerNumberModel(60, 15, 3600, 15));
    private final JLabel statusLabel = new JLabel("Status: Ready");
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

    public DatabaseSetup(JFrame owner) {
        super("SmartStock Database Setup");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(760, 620);
        setLocationRelativeTo(owner);

        DatabaseConfig config = DatabaseConfig.load();
        modeBox.setSelectedItem(config.mode());
        jdbcUrlField.setText(config.jdbcUrl());
        dbUserField.setText(config.dbUser());
        dbPasswordField.setText(config.dbPassword());
        serverHostField.setText(config.serverHost());
        serverPortSpinner.setValue(config.serverPort());
        serverHostField.getDocument().addDocumentListener(new SimpleDocumentListener(this::syncClientJdbcUrlIfNeeded));
        serverPortSpinner.addChangeListener(e -> syncClientJdbcUrlIfNeeded());
        locationIdField.setText(config.locationId() == null ? "" : String.valueOf(config.locationId()));
        cloudUrlField.setText(config.cloudJdbcUrl() == null ? "" : config.cloudJdbcUrl());
        cloudUserField.setText(config.cloudDbUser() == null ? "" : config.cloudDbUser());
        cloudPasswordField.setText(config.cloudDbPassword() == null ? "" : config.cloudDbPassword());
        syncIntervalSpinner.setValue(config.syncIntervalSeconds());

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        int row = 0;
        row = addRow(form, gbc, row, "Mode", modeBox);
        row = addRow(form, gbc, row, "Local JDBC URL", jdbcUrlField);
        row = addRow(form, gbc, row, "Local DB User", dbUserField);
        row = addRow(form, gbc, row, "Local DB Password", dbPasswordField);
        row = addRow(form, gbc, row, "Server Host", serverHostField);
        row = addRow(form, gbc, row, "Server Port", serverPortSpinner);
        row = addRow(form, gbc, row, "Store Location ID", locationIdField);
        row = addRow(form, gbc, row, "Cloud JDBC URL", cloudUrlField);
        row = addRow(form, gbc, row, "Cloud DB User", cloudUserField);
        row = addRow(form, gbc, row, "Cloud DB Password", cloudPasswordField);
        row = addRow(form, gbc, row, "Sync Interval Seconds", syncIntervalSpinner);

        testButton = new JButton("Test Local Connection");
        testCloudButton = new JButton("Test Cloud Connection");
        installRuntimeButton = new JButton("Install/Start PostgreSQL");
        installSyncServiceButton = new JButton("Install Background Sync");
        postgresStatusButton = new JButton("PostgreSQL Status");
        loadCredentialsButton = new JButton("Load Saved Credentials");
        repairSyncButton = new JButton("Repair/Sync Local Server");
        provisionButton = new JButton("Provision Server");
        startServerButton = new JButton("Start Server Mode");
        findServerButton = new JButton("Find Network Server");
        JButton saveButton = new JButton("Save Config");
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
        saveButton.addActionListener(e -> saveConfig());
        closeButton.addActionListener(e -> dispose());
        modeBox.addActionListener(e -> updateModeVisibility());

        JPanel runtimeButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        runtimeButtons.add(installRuntimeButton);
        runtimeButtons.add(installSyncServiceButton);
        runtimeButtons.add(postgresStatusButton);
        runtimeButtons.add(loadCredentialsButton);
        runtimeButtons.add(findServerButton);

        JPanel repairButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        repairButtons.add(repairSyncButton);
        repairButtons.add(provisionButton);
        repairButtons.add(startServerButton);

        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        actionButtons.add(saveButton);
        actionButtons.add(testButton);
        actionButtons.add(testCloudButton);
        actionButtons.add(closeButton);

        JPanel buttons = new JPanel(new GridLayout(3, 1, 0, 2));
        buttons.add(runtimeButtons);
        buttons.add(repairButtons);
        buttons.add(actionButtons);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        root.add(form, BorderLayout.CENTER);
        root.add(statusLabel, BorderLayout.NORTH);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        ThemeManager.applyToWindow(this);
        updateModeVisibility();
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
        boolean cloud = mode == DatabaseMode.CLOUD_DIRECT;

        setRowVisible(modeBox, true);
        setRowVisible(jdbcUrlField, server);
        setRowVisible(dbUserField, server);
        setRowVisible(dbPasswordField, server);
        setRowVisible(serverHostField, server || client);
        setRowVisible(serverPortSpinner, server || client);
        setRowVisible(locationIdField, server || client);
        setRowVisible(cloudUrlField, server || cloud);
        setRowVisible(cloudUserField, server || cloud);
        setRowVisible(cloudPasswordField, server || cloud);
        setRowVisible(syncIntervalSpinner, server);

        setRowLabel(jdbcUrlField, "Local JDBC URL");
        setRowLabel(dbUserField, client ? "Database User" : "Local DB User");
        setRowLabel(dbPasswordField, client ? "Database Password" : "Local DB Password");
        setRowLabel(serverHostField, client ? "Server IP / Hostname" : "Server Host");
        setRowLabel(serverPortSpinner, client ? "Server PostgreSQL Port" : "Server Port");

        installRuntimeButton.setVisible(server);
        installSyncServiceButton.setVisible(server);
        postgresStatusButton.setVisible(server);
        repairSyncButton.setVisible(server);
        provisionButton.setVisible(server);
        startServerButton.setVisible(server);
        findServerButton.setVisible(client);
        testButton.setVisible(server || client);
        testCloudButton.setVisible(server || cloud);
        loadCredentialsButton.setVisible(server);

        if (client) {
            applyDefaultClientCredentials();
            applyClientJdbcUrl();
            statusLabel.setText("Status: Client mode only needs the server hostname and store location.");
        } else if (cloud) {
            statusLabel.setText("Status: Cloud-direct mode only needs the cloud database fields.");
        } else {
            statusLabel.setText("Status: Server mode controls local PostgreSQL, cloud sync, and provisioning.");
        }
        revalidate();
        repaint();
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
        return selected instanceof DatabaseMode mode ? mode : DatabaseMode.CLOUD_DIRECT;
    }

    private void testLocalConnection() {
        saveConfig();
        statusLabel.setText("Status: Testing local connection...");
        try (Connection ignored = DB.getConnection()) {
            statusLabel.setText("Status: Local database connected.");
        } catch (Exception ex) {
            statusLabel.setText("Status: Local connection failed.");
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Local Connection", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void testCloudConnection() {
        saveConfig();
        statusLabel.setText("Status: Testing cloud connection...");
        try (Connection ignored = DB.getCloudConnection()) {
            statusLabel.setText("Status: Cloud database connected.");
        } catch (Exception ex) {
            statusLabel.setText("Status: Cloud connection failed.");
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cloud Connection", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveConfig() {
        try {
            if (selectedMode() == DatabaseMode.CLIENT) {
                applyDefaultClientCredentials();
                applyClientJdbcUrl();
            }
            DatabaseConfig config = DatabaseConfig.fromForm(
                    selectedMode(),
                    jdbcUrlField.getText().trim(),
                    dbUserField.getText().trim(),
                    new String(dbPasswordField.getPassword()),
                    serverHostField.getText().trim(),
                    (Integer) serverPortSpinner.getValue(),
                    parseLocationId(),
                    cloudUrlField.getText().trim(),
                    cloudUserField.getText().trim(),
                    new String(cloudPasswordField.getPassword()),
                    (Integer) syncIntervalSpinner.getValue()
            );
            config.save();
            statusLabel.setText("Status: Saved to " + DatabaseConfig.CONFIG_PATH);
        } catch (Exception ex) {
            statusLabel.setText("Status: Save failed.");
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Save Database Setup", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyClientJdbcUrl() {
        String host = serverHostField.getText() == null ? "" : serverHostField.getText().trim();
        int port = (Integer) serverPortSpinner.getValue();
        if (!host.isBlank()) {
            jdbcUrlField.setText(DatabaseCredentials.load().clientJdbcUrlOrDefault(host, port));
        }
    }

    private void applyDefaultClientCredentials() {
        DatabaseCredentials credentials = DatabaseCredentials.load();
        dbUserField.setText(credentials.clientDbUserOrDefault());
        dbPasswordField.setText(credentials.clientDbPasswordOrDefault());
    }

    private void loadSavedCredentials() {
        DatabaseCredentials credentials = DatabaseCredentials.load();
        if (selectedMode() == DatabaseMode.CLIENT) {
            applyDefaultClientCredentials();
            applyClientJdbcUrl();
            statusLabel.setText("Status: Loaded built-in SmartStock client credentials.");
            return;
        }
        if (!credentials.hasServerCredentials() && !credentials.hasClientCredentials()) {
            JOptionPane.showMessageDialog(
                    this,
                    "No saved SmartStock database credentials were found at:\n"
                            + DatabaseCredentials.CREDENTIALS_PATH
                            + "\n\nRun the SmartStock installer first, or enter the real database username and password.",
                    "Saved Credentials",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        DatabaseMode mode = selectedMode();
        if (mode == DatabaseMode.CLIENT && credentials.hasClientCredentials()) {
            dbUserField.setText(credentials.get("SMARTSTOCK_CLIENT_DB_USER"));
            dbPasswordField.setText(credentials.get("SMARTSTOCK_CLIENT_DB_PASSWORD"));
            String clientUrl = credentials.get("SMARTSTOCK_CLIENT_JDBC_URL");
            if (clientUrl != null && !clientUrl.isBlank()) {
                jdbcUrlField.setText(clientUrl);
                applyHostAndPortFromJdbcUrl(clientUrl);
            }
        } else if (credentials.hasServerCredentials()) {
            dbUserField.setText(credentials.get("SMARTSTOCK_DB_USER"));
            dbPasswordField.setText(credentials.get("SMARTSTOCK_DB_PASSWORD"));
            String serverUrl = credentials.get("SMARTSTOCK_SERVER_JDBC_URL");
            if (serverUrl != null && !serverUrl.isBlank()) {
                jdbcUrlField.setText(serverUrl);
            }
        }
        statusLabel.setText("Status: Loaded saved credentials from " + DatabaseCredentials.CREDENTIALS_PATH);
    }

    private void findNetworkServer() {
        int port = (Integer) serverPortSpinner.getValue();
        statusLabel.setText("Status: Scanning this network for PostgreSQL on port " + port + "...");
        findServerButton.setEnabled(false);
        SwingWorker<List<HostChoice>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<HostChoice> doInBackground() throws Exception {
                return scanForPostgresServers(port);
            }

            @Override
            protected void done() {
                findServerButton.setEnabled(true);
                try {
                    List<HostChoice> hosts = get();
                    if (hosts.isEmpty()) {
                        statusLabel.setText("Status: No PostgreSQL server found on this network.");
                        JOptionPane.showMessageDialog(
                                DatabaseSetup.this,
                                "No reachable PostgreSQL server was found on this local network.\n\n"
                                        + "Make sure the main SmartStock server Mac is on the same Wi-Fi/LAN, "
                                        + "PostgreSQL is running, port " + port + " is allowed through the firewall, "
                                        + "and the server is accepting network connections.",
                                "Find Network Server",
                                JOptionPane.WARNING_MESSAGE
                        );
                        return;
                    }

                    HostChoice selected = hosts.size() == 1
                            ? hosts.get(0)
                            : (HostChoice) JOptionPane.showInputDialog(
                                    DatabaseSetup.this,
                                    "Choose the SmartStock database server:",
                                    "Find Network Server",
                                    JOptionPane.PLAIN_MESSAGE,
                                    null,
                                    hosts.toArray(),
                                    hosts.get(0)
                            );
                    if (selected == null) {
                        statusLabel.setText("Status: Server scan cancelled.");
                        return;
                    }

                    serverHostField.setText(selected.connectHost());
                    applyDefaultClientCredentials();
                    applyClientJdbcUrl();
                    statusLabel.setText("Status: Selected database server " + selected.connectHost() + ":" + port + ".");
                } catch (Exception ex) {
                    statusLabel.setText("Status: Network scan failed.");
                    JOptionPane.showMessageDialog(DatabaseSetup.this, rootCauseMessage(ex), "Find Network Server", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private List<HostChoice> scanForPostgresServers(int port) throws Exception {
        Set<String> candidates = localSubnetCandidates();
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(64, Math.max(4, candidates.size())));
        try {
            List<Future<HostChoice>> futures = new ArrayList<>();
            for (String host : candidates) {
                futures.add(executor.submit(() -> canConnect(host, port) ? hostChoice(host, port) : null));
            }

            List<HostChoice> found = new ArrayList<>();
            for (Future<HostChoice> future : futures) {
                HostChoice host = future.get();
                if (host != null) {
                    found.add(host);
                }
            }
            found.sort((left, right) -> left.connectHost().compareToIgnoreCase(right.connectHost()));
            return found;
        } finally {
            executor.shutdownNow();
        }
    }

    private HostChoice hostChoice(String ipAddress, int port) {
        String hostname = resolvePreferredHostname(ipAddress, port);
        return new HostChoice(ipAddress, hostname == null || hostname.isBlank() ? ipAddress : hostname);
    }

    private String resolvePreferredHostname(String ipAddress, int port) {
        String localHostName = localBonjourNameForOwnAddress(ipAddress, port);
        if (localHostName != null) {
            return localHostName;
        }
        try {
            String canonical = InetAddress.getByName(ipAddress).getCanonicalHostName();
            if (canonical != null && !canonical.equals(ipAddress) && canConnect(canonical, port)) {
                return canonical;
            }
        } catch (Exception ignored) {
            // IP fallback is still valid when local name resolution is unavailable.
        }
        return ipAddress;
    }

    private String localBonjourNameForOwnAddress(String ipAddress, int port) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    InetAddress address = interfaceAddress.getAddress();
                    if (address instanceof Inet4Address && ipAddress.equals(address.getHostAddress())) {
                        String host = InetAddress.getLocalHost().getHostName();
                        if (host != null && !host.isBlank()) {
                            String bonjourHost = host.endsWith(".local") ? host : host + ".local";
                            if (canConnect(bonjourHost, port)) {
                                return bonjourHost;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Keep the IP when this Mac has no usable local hostname.
        }
        return null;
    }

    private Set<String> localSubnetCandidates() throws Exception {
        Set<String> candidates = new LinkedHashSet<>();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                continue;
            }
            for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                InetAddress address = interfaceAddress.getAddress();
                if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) {
                    continue;
                }

                byte[] bytes = address.getAddress();
                int first = bytes[0] & 0xff;
                int second = bytes[1] & 0xff;
                int third = bytes[2] & 0xff;
                for (int host = 1; host < 255; host++) {
                    String candidate = first + "." + second + "." + third + "." + host;
                    candidates.add(candidate);
                }
            }
        }
        return candidates;
    }

    private boolean canConnect(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 220);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private void syncClientJdbcUrlIfNeeded() {
        if (selectedMode() == DatabaseMode.CLIENT) {
            applyClientJdbcUrl();
        }
    }

    private void applyHostAndPortFromJdbcUrl(String jdbcUrl) {
        String prefix = "jdbc:postgresql://";
        if (jdbcUrl == null || !jdbcUrl.startsWith(prefix)) {
            return;
        }
        String hostAndPort = jdbcUrl.substring(prefix.length());
        int slashIndex = hostAndPort.indexOf('/');
        if (slashIndex >= 0) {
            hostAndPort = hostAndPort.substring(0, slashIndex);
        }
        int colonIndex = hostAndPort.lastIndexOf(':');
        if (colonIndex <= 0 || colonIndex == hostAndPort.length() - 1) {
            serverHostField.setText(hostAndPort);
            return;
        }
        serverHostField.setText(hostAndPort.substring(0, colonIndex));
        try {
            serverPortSpinner.setValue(Integer.parseInt(hostAndPort.substring(colonIndex + 1)));
        } catch (NumberFormatException ignored) {
            // Keep the current port if the saved URL has an unusual format.
        }
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
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "This will use Homebrew to install missing runtime dependencies on this Mac:\n"
                        + "Java 17, Maven, and PostgreSQL.\n\n"
                        + "Continue?",
                "Install SmartStock Runtime",
                JOptionPane.YES_NO_OPTION
        );
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        statusLabel.setText("Status: Installing runtime dependencies...");
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
                            ? "Status: Runtime dependencies ready."
                            : "Status: Runtime install failed.");
                    showCommandOutput("Install Runtime", result.output());
                } catch (Exception ex) {
                    statusLabel.setText("Status: Runtime install failed.");
                    JOptionPane.showMessageDialog(DatabaseSetup.this, rootCauseMessage(ex), "Install Runtime", JOptionPane.ERROR_MESSAGE);
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
            PostgresRuntimeService.CommandResult syncServiceResult = PostgresRuntimeService.ensureSyncServiceInstalled();
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

    private Integer parseLocationId() {
        String text = locationIdField.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        return Integer.parseInt(text.trim());
    }

    private record FormRow(JLabel label, JComponent field, String originalLabel) {}

    private record HostChoice(String ipAddress, String connectHost) {
        @Override
        public String toString() {
            return connectHost.equals(ipAddress) ? ipAddress : connectHost + " (" + ipAddress + ")";
        }
    }

    private interface SimpleChangeHandler {
        void changed();
    }

    private static class SimpleDocumentListener implements javax.swing.event.DocumentListener {
        private final SimpleChangeHandler handler;

        private SimpleDocumentListener(SimpleChangeHandler handler) {
            this.handler = handler;
        }

        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e) {
            handler.changed();
        }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e) {
            handler.changed();
        }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e) {
            handler.changed();
        }
    }
}
