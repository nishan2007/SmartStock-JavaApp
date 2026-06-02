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

        JButton testButton = new JButton("Test Local Connection");
        JButton testCloudButton = new JButton("Test Cloud Connection");
        JButton installRuntimeButton = new JButton("Install/Start PostgreSQL");
        JButton installSyncServiceButton = new JButton("Install Background Sync");
        JButton postgresStatusButton = new JButton("PostgreSQL Status");
        JButton loadCredentialsButton = new JButton("Load Saved Credentials");
        JButton repairSyncButton = new JButton("Repair/Sync Local Server");
        JButton provisionButton = new JButton("Provision Server");
        JButton startServerButton = new JButton("Start Server Mode");
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
        saveButton.addActionListener(e -> saveConfig());
        closeButton.addActionListener(e -> dispose());

        JPanel runtimeButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        runtimeButtons.add(installRuntimeButton);
        runtimeButtons.add(installSyncServiceButton);
        runtimeButtons.add(postgresStatusButton);
        runtimeButtons.add(loadCredentialsButton);

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
    }

    private int addRow(JPanel form, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        form.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(field, gbc);
        return row + 1;
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
            DatabaseConfig config = DatabaseConfig.fromForm(
                    (DatabaseMode) modeBox.getSelectedItem(),
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

    private void loadSavedCredentials() {
        DatabaseCredentials credentials = DatabaseCredentials.load();
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
        DatabaseMode mode = (DatabaseMode) modeBox.getSelectedItem();
        if (mode == DatabaseMode.CLIENT && credentials.hasClientCredentials()) {
            dbUserField.setText(credentials.get("SMARTSTOCK_CLIENT_DB_USER"));
            dbPasswordField.setText(credentials.get("SMARTSTOCK_CLIENT_DB_PASSWORD"));
            String clientUrl = credentials.get("SMARTSTOCK_CLIENT_JDBC_URL");
            if (clientUrl != null && !clientUrl.isBlank()) {
                jdbcUrlField.setText(clientUrl);
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
}
