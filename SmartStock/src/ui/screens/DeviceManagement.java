package ui.screens;

import data.DatabaseConfig;
import managers.NavigationManager;
import managers.SessionManager;
import managers.SupabaseSessionManager;
import models.DeviceSessionRecord;
import models.ManagedDevice;
import services.LanApiClient;
import ui.components.AppMenuBar;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeviceManagement extends JFrame {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
    private String pendingMutationKey;
    private String pendingMutationFingerprint;

    private final DefaultTableModel deviceTableModel = new DefaultTableModel(
            new Object[]{"Device", "Status", "Sales", "Orders", "Last User", "Store", "Last Seen", "Sessions"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final DefaultTableModel sessionTableModel = new DefaultTableModel(
            new Object[]{"Login", "Logout", "User", "Store", "Status"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable deviceTable = new JTable(deviceTableModel);
    private final JTable sessionTable = new JTable(sessionTableModel);
    private final JComboBox<String> filterCombo = new JComboBox<>(new String[]{
            "All Devices",
            "Pending Approval",
            "Stay Signed In",
            "Blocked"
    });
    private final JTextArea detailsArea = new JTextArea();
    private final JTextArea notesArea = new JTextArea(3, 32);
    private final JTextField deviceNameField = new JTextField();
    private final JTextField receiptCodeField = new JTextField("0001");
    private final JCheckBox staySignedInBox = new JCheckBox("Allow this device to stay signed in after the app is closed");
    private final JCheckBox allowSalesBox = new JCheckBox("Allow Sales");
    private final JCheckBox allowOrdersBox = new JCheckBox("Allow Orders");
    private final JLabel summaryLabel = new JLabel("Loading devices...");
    private final JButton saveApprovalButton = new JButton("Save Access Settings");
    private final JButton saveNameButton = new JButton("Save Name");
    private final JButton saveCodeButton = new JButton("Save Device Code");
    private final JButton blockButton = new JButton("Block Device");
    private final JButton refreshButton = new JButton("Refresh");
    private final JButton securityStatusButton = new JButton("Security Status");
    private final JButton rotateCredentialButton = new JButton("Rotate Device Credential");
    private final JButton closeButton = new JButton("Close");

    private final List<ManagedDevice> allDevices = new ArrayList<>();
    private final List<ManagedDevice> filteredDevices = new ArrayList<>();
    private ManagedDevice selectedDevice;

    public DeviceManagement() {
        setTitle("Device Management");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "DeviceManagement"));

        JPanel rootPanel = new JPanel(new BorderLayout(18, 18));
        rootPanel.setBorder(new EmptyBorder(22, 22, 22, 22));
        rootPanel.setBackground(new Color(245, 247, 250));

        JPanel headerPanel = new JPanel(new BorderLayout(12, 8));
        headerPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("Device Management");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setForeground(new Color(31, 41, 55));

        JLabel subtitleLabel = new JLabel("Review registered devices, inspect login history, and control sign-in, sales, and order access.");
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
        subtitleLabel.setForeground(new Color(75, 85, 99));

        JPanel titleStack = new JPanel();
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        titleStack.setOpaque(false);
        titleStack.add(titleLabel);
        titleStack.add(Box.createVerticalStrut(6));
        titleStack.add(subtitleLabel);

        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controlsPanel.setOpaque(false);
        controlsPanel.add(new JLabel("Filter:"));
        controlsPanel.add(filterCombo);
        controlsPanel.add(refreshButton);
        controlsPanel.add(securityStatusButton);

        headerPanel.add(titleStack, BorderLayout.WEST);
        headerPanel.add(controlsPanel, BorderLayout.EAST);

        deviceTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        deviceTable.setRowHeight(28);
        deviceTable.getTableHeader().setReorderingAllowed(false);

        sessionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sessionTable.setRowHeight(26);
        sessionTable.getTableHeader().setReorderingAllowed(false);

        JScrollPane deviceScrollPane = new JScrollPane(deviceTable);
        deviceScrollPane.setBorder(BorderFactory.createTitledBorder("Devices"));

        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        detailsArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        detailsArea.setBorder(new EmptyBorder(8, 8, 8, 8));

        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setFont(new Font("SansSerif", Font.PLAIN, 13));
        notesArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(209, 213, 219)),
                new EmptyBorder(8, 8, 8, 8)
        ));
        deviceNameField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        receiptCodeField.setFont(new Font("Monospaced", Font.BOLD, 13));
        staySignedInBox.setOpaque(false);
        staySignedInBox.setFont(new Font("SansSerif", Font.BOLD, 13));
        allowSalesBox.setOpaque(false);
        allowSalesBox.setFont(new Font("SansSerif", Font.BOLD, 13));
        allowOrdersBox.setOpaque(false);
        allowOrdersBox.setFont(new Font("SansSerif", Font.BOLD, 13));

        JPanel detailsPanel = new JPanel(new BorderLayout(10, 10));
        detailsPanel.setBorder(BorderFactory.createTitledBorder("Device Details"));
        detailsPanel.add(new JScrollPane(detailsArea), BorderLayout.CENTER);

        JPanel notesPanel = new JPanel(new BorderLayout(0, 8));
        notesPanel.setOpaque(false);
        JPanel namePanel = new JPanel(new BorderLayout(8, 0));
        namePanel.setOpaque(false);
        namePanel.add(new JLabel("Friendly Device Name"), BorderLayout.WEST);
        namePanel.add(deviceNameField, BorderLayout.CENTER);
        namePanel.add(saveNameButton, BorderLayout.EAST);
        JPanel codePanel = new JPanel(new BorderLayout(8, 0));
        codePanel.setOpaque(false);
        codePanel.add(new JLabel("Receipt Device Code"), BorderLayout.WEST);
        codePanel.add(receiptCodeField, BorderLayout.CENTER);
        codePanel.add(saveCodeButton, BorderLayout.EAST);
        JLabel notesLabel = new JLabel("Stay signed in / block note");
        notesLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        JPanel noteHeaderPanel = new JPanel();
        noteHeaderPanel.setLayout(new BoxLayout(noteHeaderPanel, BoxLayout.Y_AXIS));
        noteHeaderPanel.setOpaque(false);
        noteHeaderPanel.add(namePanel);
        noteHeaderPanel.add(Box.createVerticalStrut(8));
        noteHeaderPanel.add(codePanel);
        noteHeaderPanel.add(Box.createVerticalStrut(8));
        noteHeaderPanel.add(staySignedInBox);
        noteHeaderPanel.add(Box.createVerticalStrut(6));
        noteHeaderPanel.add(allowSalesBox);
        noteHeaderPanel.add(Box.createVerticalStrut(6));
        noteHeaderPanel.add(allowOrdersBox);
        noteHeaderPanel.add(Box.createVerticalStrut(8));
        noteHeaderPanel.add(notesLabel);
        notesPanel.add(noteHeaderPanel, BorderLayout.NORTH);
        notesPanel.add(new JScrollPane(notesArea), BorderLayout.CENTER);
        detailsPanel.add(notesPanel, BorderLayout.SOUTH);

        JScrollPane sessionScrollPane = new JScrollPane(sessionTable);
        sessionScrollPane.setBorder(BorderFactory.createTitledBorder("Recent Sessions"));

        JSplitPane rightSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, detailsPanel, sessionScrollPane);
        rightSplitPane.setResizeWeight(0.58);
        rightSplitPane.setBorder(BorderFactory.createEmptyBorder());

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, deviceScrollPane, rightSplitPane);
        mainSplitPane.setResizeWeight(0.50);
        mainSplitPane.setBorder(BorderFactory.createEmptyBorder());

        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setOpaque(false);

        summaryLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        summaryLabel.setForeground(new Color(75, 85, 99));

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(saveApprovalButton);
        actionPanel.add(rotateCredentialButton);
        actionPanel.add(blockButton);
        actionPanel.add(closeButton);

        footerPanel.add(summaryLabel, BorderLayout.WEST);
        footerPanel.add(actionPanel, BorderLayout.EAST);

        rootPanel.add(headerPanel, BorderLayout.NORTH);
        rootPanel.add(mainSplitPane, BorderLayout.CENTER);
        rootPanel.add(footerPanel, BorderLayout.SOUTH);
        add(rootPanel);

        filterCombo.addActionListener(e -> applyFilter());
        refreshButton.addActionListener(e -> loadDevices());
        securityStatusButton.addActionListener(e -> showSecurityStatus());
        rotateCredentialButton.addActionListener(e -> rotateSelectedCredential());
        closeButton.addActionListener(e -> NavigationManager.showMainMenu(this));
        saveApprovalButton.addActionListener(e -> saveApprovalSetting());
        saveNameButton.addActionListener(e -> saveDeviceName());
        saveCodeButton.addActionListener(e -> saveDeviceCode());
        blockButton.addActionListener(e -> blockSelectedDevice());
        deviceTable.getSelectionModel().addListSelectionListener(this::handleSelectionChanged);

        detailsArea.setText("Select a device to see its full details.");
        staySignedInBox.setSelected(false);
        setButtonState();
        loadDevices();
        WindowHelper.configurePosWindow(this);
    }

    private void loadDevices() {
        String preserveDeviceId = selectedDevice == null ? null : selectedDevice.getDeviceId();

        allDevices.clear();

        try {
            allDevices.addAll(LanApiClient.loadManagedDevices());
            applyFilter();
            restoreSelection(preserveDeviceId);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                    this,
                    "Could not load devices.\n\n" + ex.getMessage(),
                    "Device Management",
                    JOptionPane.ERROR_MESSAGE
            );
            summaryLabel.setText("Unable to load device records.");
        }
    }

    private void applyFilter() {
        String selectedFilter = (String) filterCombo.getSelectedItem();
        filteredDevices.clear();
        deviceTableModel.setRowCount(0);

        for (ManagedDevice device : allDevices) {
            if (!matchesFilter(device, selectedFilter)) {
                continue;
            }

            filteredDevices.add(device);
            deviceTableModel.addRow(new Object[]{
                    device.getDisplayName(),
                    device.getStatusLabel(),
                    device.isAllowSales() ? "Allowed" : "Off",
                    device.isAllowOrders() ? "Allowed" : "Off",
                    defaultText(device.getLastUserName()),
                    defaultText(device.getLastStoreName()),
                    formatTimestamp(device.getLastSeen()),
                    device.getSessionCount()
            });
        }

        if (filteredDevices.isEmpty()) {
            selectedDevice = null;
            detailsArea.setText("No devices matched the current filter.");
            notesArea.setText("");
            deviceNameField.setText("");
            staySignedInBox.setSelected(false);
            allowSalesBox.setSelected(false);
            allowOrdersBox.setSelected(false);
            sessionTableModel.setRowCount(0);
        }

        updateSummaryLabel();
        setButtonState();
    }

    private boolean matchesFilter(ManagedDevice device, String filter) {
        if (filter == null || "All Devices".equalsIgnoreCase(filter)) {
            return true;
        }
        return switch (filter) {
            case "Pending Approval" -> !device.isApproved() && !device.isBlocked();
            case "Stay Signed In" -> device.isApproved() && !device.isBlocked();
            case "Blocked" -> device.isBlocked();
            default -> true;
        };
    }

    private void restoreSelection(String deviceId) {
        if (filteredDevices.isEmpty()) {
            selectedDevice = null;
            setButtonState();
            return;
        }

        int rowToSelect = 0;
        if (deviceId != null) {
            for (int i = 0; i < filteredDevices.size(); i++) {
                if (deviceId.equals(filteredDevices.get(i).getDeviceId())) {
                    rowToSelect = i;
                    break;
                }
            }
        }

        deviceTable.getSelectionModel().setSelectionInterval(rowToSelect, rowToSelect);
        updateSelectedDevice(filteredDevices.get(rowToSelect));
    }

    private void handleSelectionChanged(ListSelectionEvent event) {
        if (event.getValueIsAdjusting()) {
            return;
        }

        int row = deviceTable.getSelectedRow();
        if (row < 0 || row >= filteredDevices.size()) {
            selectedDevice = null;
            detailsArea.setText("Select a device to see its full details.");
            notesArea.setText("");
            deviceNameField.setText("");
            staySignedInBox.setSelected(false);
            allowSalesBox.setSelected(false);
            allowOrdersBox.setSelected(false);
            sessionTableModel.setRowCount(0);
            setButtonState();
            return;
        }

        updateSelectedDevice(filteredDevices.get(row));
    }

    private void updateSelectedDevice(ManagedDevice device) {
        selectedDevice = device;
        detailsArea.setText(buildDetailsText(device));
        deviceNameField.setText(fieldText(device.getDeviceName()));
        receiptCodeField.setText(fieldText(device.getReceiptDeviceCode()));
        notesArea.setText(device.getStatusNotes() == null ? "" : device.getStatusNotes());
        staySignedInBox.setSelected(device.isApproved() && !device.isBlocked());
        allowSalesBox.setSelected(device.isAllowSales() && !device.isBlocked());
        allowOrdersBox.setSelected(device.isAllowOrders() && !device.isBlocked());
        loadSessionHistory(device.getDeviceId());
        setButtonState();
    }

    private String buildDetailsText(ManagedDevice device) {
        return "Status: " + device.getStatusLabel() + "\n"
                + "Device Name: " + defaultText(device.getDeviceName()) + "\n"
                + "Host Name: " + defaultText(device.getHostname()) + "\n"
                + "Installation ID: " + defaultText(device.getInstallationId()) + "\n"
                + "Device ID: " + defaultText(device.getDeviceId()) + "\n"
                + "Receipt Device Code: " + defaultText(device.getReceiptDeviceCode()) + "\n"
                + "Allow Sales: " + yesNo(device.isAllowSales()) + "\n"
                + "Allow Orders: " + yesNo(device.isAllowOrders()) + "\n"
                + "Last User: " + defaultText(device.getLastUserName()) + "\n"
                + "Last Store: " + defaultText(device.getLastStoreName()) + "\n"
                + "First Seen: " + formatTimestamp(device.getFirstSeen()) + "\n"
                + "Last Seen: " + formatTimestamp(device.getLastSeen()) + "\n"
                + "Sessions: " + device.getSessionCount() + " total, " + device.getActiveSessionCount() + " active\n"
                + "Latest Login: " + formatTimestamp(device.getLatestLoginTime()) + "\n"
                + "Latest Logout: " + formatTimestamp(device.getLatestLogoutTime()) + "\n"
                + "Latest Session Status: " + defaultText(device.getLatestSessionStatus()) + "\n"
                + "Stay Signed In Enabled At: " + formatTimestamp(device.getApprovedAt()) + "\n"
                + "Stay Signed In Enabled By: " + defaultText(device.getApprovedByName()) + "\n"
                + "Blocked At: " + formatTimestamp(device.getBlockedAt()) + "\n"
                + "Blocked By: " + defaultText(device.getBlockedByName()) + "\n"
                + "OS: " + defaultText(device.getOsName()) + " " + defaultText(device.getOsVersion()) + "\n"
                + "Architecture: " + defaultText(device.getOsArch()) + "\n"
                + "Java Version: " + defaultText(device.getJavaVersion()) + "\n"
                + "App Version: " + defaultText(device.getAppVersion()) + "\n"
                + "Local Username: " + defaultText(device.getLocalUsername()) + "\n"
                + "MAC Addresses: " + defaultText(device.getMacAddresses());
    }

    private void loadSessionHistory(String deviceId) {
        sessionTableModel.setRowCount(0);

        try {
            List<DeviceSessionRecord> sessions = LanApiClient.loadDeviceSessions(deviceId);
            for (DeviceSessionRecord session : sessions) {
                sessionTableModel.addRow(new Object[]{
                        formatTimestamp(session.getLoginTime()),
                        formatTimestamp(session.getLogoutTime()),
                        defaultText(session.getUserName()),
                        defaultText(session.getStoreName()),
                        defaultText(session.getSessionStatus())
                });
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                    this,
                    "Could not load session history.\n\n" + ex.getMessage(),
                    "Device Management",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void saveApprovalSetting() {
        if (selectedDevice == null) {
            return;
        }
        boolean allowStaySignedIn = staySignedInBox.isSelected();
        boolean allowSales = allowSalesBox.isSelected();
        boolean allowOrders = allowOrdersBox.isSelected();
        String message = "Save device access settings?\n\n"
                + "Stay Signed In: " + yesNo(allowStaySignedIn) + "\n"
                + "Allow Sales: " + yesNo(allowSales) + "\n"
                + "Allow Orders: " + yesNo(allowOrders) + "\n\n";

        int result = JOptionPane.showConfirmDialog(
                this,
                message + selectedDevice.getDisplayName(),
                "Save Access Settings",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        LanApiClient.DeviceAdminUpdate request=new LanApiClient.DeviceAdminUpdate("ACCESS",selectedDevice.getDeviceId(),allowStaySignedIn,
                allowSales,allowOrders,notesArea.getText(),null,null);
        try {
            LanApiClient.updateManagedDevice(request,mutationKey(request.toString()));clearMutationKey();
            if (selectedDevice.getDeviceId() != null
                    && selectedDevice.getDeviceId().equals(SessionManager.getCurrentDeviceId())) {
                if (allowStaySignedIn) {
                    SupabaseSessionManager.savePersistedSession(
                            SessionManager.getCurrentUserId(),
                            SessionManager.getCurrentLocationId()
                    );
                } else {
                    SupabaseSessionManager.clearPersistedSession();
                }
            }
            loadDevices();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                    this,
                    "Could not save device access settings.\n\n" + ex.getMessage(),
                    "Device Management",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void blockSelectedDevice() {
        if (selectedDevice == null) {
            return;
        }

        int result = JOptionPane.showConfirmDialog(
                this,
                "Block this device and end any active sessions?\n\n" + selectedDevice.getDisplayName(),
                "Block Device",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        boolean isCurrentDevice = selectedDevice.getDeviceId() != null
                && selectedDevice.getDeviceId().equals(SessionManager.getCurrentDeviceId());

        LanApiClient.DeviceAdminUpdate request=new LanApiClient.DeviceAdminUpdate("BLOCK",selectedDevice.getDeviceId(),false,false,false,notesArea.getText(),null,null);
        try {
            LanApiClient.updateManagedDevice(request,mutationKey(request.toString()));clearMutationKey();

            if (isCurrentDevice) {
                SessionManager.clearSessionState();
                SupabaseSessionManager.clearSession();
                SupabaseSessionManager.clearPersistedSession();
                JOptionPane.showMessageDialog(
                        this,
                        "This device has been blocked and will now be signed out.",
                        "Device Blocked",
                        JOptionPane.WARNING_MESSAGE
                );
                NavigationManager.logoutToLogin(this);
                return;
            }

            loadDevices();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                    this,
                    "Could not block the device.\n\n" + ex.getMessage(),
                    "Device Management",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void saveDeviceCode() {
        if (selectedDevice == null) {
            return;
        }
        LanApiClient.DeviceAdminUpdate request=new LanApiClient.DeviceAdminUpdate("RECEIPT_CODE",selectedDevice.getDeviceId(),false,false,false,null,null,receiptCodeField.getText());
        try {
            LanApiClient.updateManagedDevice(request,mutationKey(request.toString()));clearMutationKey();
            loadDevices();
            JOptionPane.showMessageDialog(this, "Device code saved.");
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                    this,
                    "Could not save device code.\n\n" + ex.getMessage(),
                    "Device Management",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void saveDeviceName() {
        if (selectedDevice == null) {
            return;
        }
        LanApiClient.DeviceAdminUpdate request=new LanApiClient.DeviceAdminUpdate("NAME",selectedDevice.getDeviceId(),false,false,false,null,deviceNameField.getText(),null);
        try {
            LanApiClient.updateManagedDevice(request,mutationKey(request.toString()));clearMutationKey();
            loadDevices();
            JOptionPane.showMessageDialog(this, "Device name saved.");
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                    this,
                    "Could not save device name.\n\n" + ex.getMessage(),
                    "Device Management",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void rotateSelectedCredential() {
        if (selectedDevice == null || !selectedDevice.isApproved() || selectedDevice.isBlocked()) return;
        int choice = JOptionPane.showConfirmDialog(this,
                "Rotate this register's secure device credential?\n\nThe register will claim it automatically. Its current credential remains valid during the safe overlap window.",
                "Rotate Device Credential", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return;
        LanApiClient.DeviceAdminUpdate request=new LanApiClient.DeviceAdminUpdate("ROTATE",selectedDevice.getDeviceId(),false,false,false,null,null,null);
        try {
            LanApiClient.updateManagedDevice(request,mutationKey(request.toString()));clearMutationKey();
            JOptionPane.showMessageDialog(this,
                    "Rotation queued. The server will issue and the register will claim the replacement automatically.",
                    "Device Credential", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not queue credential rotation.\n\n" + ex.getMessage(),
                    "Device Credential", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showSecurityStatus() {
        try {
            LanApiClient.DeviceSecurityStatus report = LanApiClient.loadDeviceSecurityStatus();
            StringBuilder text = new StringBuilder();
            text.append(report.healthy() ? "Security checks passed" : "Security attention required").append("\n\n")
                    .append("Encrypted database connection: ").append(yesNo(report.tls())).append('\n')
                    .append("Credential storage: ").append(report.credentialStore()).append('\n')
                    .append(report.pairingPhrase() == null ? "" : "Administrator pairing phrase (expires within 10 minutes): " + report.pairingPhrase() + "\n")
                    .append(report.lanCertificateFingerprint() == null ? "" : "LAN certificate fingerprint: " + report.lanCertificateFingerprint() + "\n")
                    .append("Device credentials: ").append(report.claimedCredentials()).append(" claimed, ")
                    .append(report.issuedCredentials()).append(" waiting to be claimed, ")
                    .append(report.pendingCredentials()).append(" pending\n")
                    .append("Blocked devices: ").append(report.blockedDevices()).append('\n')
                    .append("Broad authenticated policies: ").append(report.broadAuthenticatedPolicies()).append('\n')
                    .append("Granted tables without RLS: ").append(report.exposedTablesWithoutRls()).append('\n')
                    .append("Public privileged functions: ").append(report.publicSecurityDefiners()).append('\n')
                    .append("Latest security audit: ").append(report.latestAuditEpochMillis()<=0?"Not available":java.time.Instant.ofEpochMilli(report.latestAuditEpochMillis())).append('\n')
                    .append("Latest local backup: ").append(report.latestBackupEpochMillis()<=0?"Not detected":java.time.Instant.ofEpochMilli(report.latestBackupEpochMillis()));
            if (!report.warnings().isEmpty()) {
                text.append("\n\nAttention:\n");
                for (String warning : report.warnings()) text.append("• ").append(warning).append('\n');
            }
            JTextArea area = new JTextArea(text.toString(), 20, 68);
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            JOptionPane.showMessageDialog(this, new JScrollPane(area), "SmartStock Security Status",
                    report.healthy() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not inspect database security.\n\n" + ex.getMessage(),
                    "Security Status", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String mutationKey(String fingerprint){if(pendingMutationKey==null||!fingerprint.equals(pendingMutationFingerprint)){
        pendingMutationKey=UUID.randomUUID().toString();pendingMutationFingerprint=fingerprint;}return pendingMutationKey;}
    private void clearMutationKey(){pendingMutationKey=null;pendingMutationFingerprint=null;}

    private void updateSummaryLabel() {
        int pending = 0;
        int staySignedIn = 0;
        int blocked = 0;
        int salesAllowed = 0;
        int ordersAllowed = 0;

        for (ManagedDevice device : allDevices) {
            if (device.isBlocked()) {
                blocked++;
            } else if (device.isApproved()) {
                staySignedIn++;
            } else {
                pending++;
            }
            if (device.isAllowSales() && !device.isBlocked()) {
                salesAllowed++;
            }
            if (device.isAllowOrders() && !device.isBlocked()) {
                ordersAllowed++;
            }
        }

        summaryLabel.setText(
                "Showing " + filteredDevices.size() + " of " + allDevices.size()
                        + " devices   |   Stay Signed In: " + staySignedIn
                        + "   Sales: " + salesAllowed
                        + "   Orders: " + ordersAllowed
                        + "   Pending: " + pending
                        + "   Blocked: " + blocked
        );
    }

    private void setButtonState() {
        boolean hasSelection = selectedDevice != null;
        staySignedInBox.setEnabled(hasSelection && !selectedDevice.isBlocked());
        allowSalesBox.setEnabled(hasSelection && !selectedDevice.isBlocked());
        allowOrdersBox.setEnabled(hasSelection && !selectedDevice.isBlocked());
        saveApprovalButton.setEnabled(hasSelection && !selectedDevice.isBlocked());
        deviceNameField.setEnabled(hasSelection && !selectedDevice.isBlocked());
        saveNameButton.setEnabled(hasSelection && !selectedDevice.isBlocked());
        saveCodeButton.setEnabled(hasSelection && !selectedDevice.isBlocked());
        blockButton.setEnabled(hasSelection && !selectedDevice.isBlocked());
        rotateCredentialButton.setEnabled(hasSelection && selectedDevice.isApproved() && !selectedDevice.isBlocked());
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return "Not available";
        }
        return StoreTimeZoneHelper.formatStoreZonedTimestamp(timestamp, DATE_TIME_FORMAT);
    }

    private String defaultText(String value) {
        return value == null || value.isBlank() ? "Not available" : value;
    }

    private String fieldText(String value) {
        return value == null ? "" : value;
    }

    private String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }
}
