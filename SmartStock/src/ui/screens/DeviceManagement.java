package ui.screens;

import data.DatabaseConfig;
import managers.NavigationManager;
import managers.AutoLogoutManager;
import managers.SessionManager;
import managers.SupabaseSessionManager;
import models.DeviceSessionRecord;
import models.ManagedDevice;
import services.LanApiClient;
import services.ServerManagementClient;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.UiTaskRunner;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
            "Approved",
            "Stay Signed In",
            "Blocked"
    });
    private final JComboBox<String> activityFilterCombo = new JComboBox<>(new String[]{
            "Show all activity",
            "Active within 1 day",
            "Active within 5 days",
            "Active within 15 days",
            "Active within 30 days",
            "Active within 90 days"
    });
    private final JTextArea detailsArea = new JTextArea();
    private final JTextArea notesArea = new JTextArea(3, 32);
    private final JTextField deviceNameField = new JTextField();
    private final JTextField receiptCodeField = new JTextField("0001");
    private final JCheckBox approvedBox = new JCheckBox("Approve this device to use SmartStock");
    private final JCheckBox staySignedInBox = new JCheckBox("Allow employee account to stay signed in after the app is closed");
    private final JCheckBox autoLogoutBox = new JCheckBox("Enable automatic logout after inactivity");
    private final JSpinner autoLogoutMinutesSpinner =
            new JSpinner(new SpinnerNumberModel(15, 1, 480, 1));
    private final JLabel autoLogoutOverrideLabel =
            new JLabel("Stay Signed In overrides automatic logout on this device.");
    private final JCheckBox allowSalesBox = new JCheckBox("Allow Sales");
    private final JCheckBox allowOrdersBox = new JCheckBox("Allow Orders");
    private final JLabel summaryLabel = new JLabel("Loading devices...");
    private final JButton saveApprovalButton = new JButton("Save Access Settings");
    private final JButton saveNameButton = new JButton("Save Name");
    private final JButton saveCodeButton = new JButton("Save Device Code");
    private final JButton blockButton = new JButton("Block Device");
    private final JButton refreshButton = new JButton("Refresh");
    private final JButton securityStatusButton = new JButton("Security Status");
    private final JButton serversButton = new JButton("Servers");
    private final JButton rotateCredentialButton = new JButton("Rotate Device Credential");
    private final JButton closeButton = new JButton("Close");

    private final List<ManagedDevice> allDevices = new ArrayList<>();
    private final List<ManagedDevice> filteredDevices = new ArrayList<>();
    private ManagedDevice selectedDevice;
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

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
        controlsPanel.add(new JLabel("Activity:"));
        controlsPanel.add(activityFilterCombo);
        controlsPanel.add(refreshButton);
        controlsPanel.add(serversButton);
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
        approvedBox.setOpaque(false);
        approvedBox.setFont(new Font("SansSerif", Font.BOLD, 13));
        staySignedInBox.setOpaque(false);
        staySignedInBox.setFont(new Font("SansSerif", Font.BOLD, 13));
        autoLogoutBox.setOpaque(false);
        autoLogoutBox.setFont(new Font("SansSerif", Font.BOLD, 13));
        autoLogoutMinutesSpinner.setEditor(new JSpinner.NumberEditor(autoLogoutMinutesSpinner, "0"));
        autoLogoutOverrideLabel.setForeground(new Color(180, 83, 9));
        autoLogoutOverrideLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
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
        JLabel notesLabel = new JLabel("Device access / block note");
        notesLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        JPanel noteHeaderPanel = new JPanel();
        noteHeaderPanel.setLayout(new BoxLayout(noteHeaderPanel, BoxLayout.Y_AXIS));
        noteHeaderPanel.setOpaque(false);
        noteHeaderPanel.add(namePanel);
        noteHeaderPanel.add(Box.createVerticalStrut(8));
        noteHeaderPanel.add(codePanel);
        noteHeaderPanel.add(Box.createVerticalStrut(8));
        noteHeaderPanel.add(approvedBox);
        noteHeaderPanel.add(Box.createVerticalStrut(6));
        noteHeaderPanel.add(staySignedInBox);
        noteHeaderPanel.add(Box.createVerticalStrut(6));
        noteHeaderPanel.add(autoLogoutBox);
        JPanel timeoutPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        timeoutPanel.setOpaque(false);
        timeoutPanel.add(new JLabel("Logout after"));
        timeoutPanel.add(autoLogoutMinutesSpinner);
        timeoutPanel.add(new JLabel("minute(s) of inactivity"));
        noteHeaderPanel.add(timeoutPanel);
        noteHeaderPanel.add(autoLogoutOverrideLabel);
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
        footerPanel.add(loadingState, BorderLayout.CENTER);
        footerPanel.add(actionPanel, BorderLayout.EAST);

        rootPanel.add(headerPanel, BorderLayout.NORTH);
        rootPanel.add(mainSplitPane, BorderLayout.CENTER);
        rootPanel.add(footerPanel, BorderLayout.SOUTH);
        add(rootPanel);

        filterCombo.addActionListener(e -> refilterDevices());
        activityFilterCombo.addActionListener(e -> refilterDevices());
        refreshButton.addActionListener(e -> loadDevices());
        securityStatusButton.addActionListener(e -> showSecurityStatus());
        serversButton.addActionListener(e -> loadServerManagement());
        rotateCredentialButton.addActionListener(e -> rotateSelectedCredential());
        closeButton.addActionListener(e -> NavigationManager.showMainMenu(this));
        saveApprovalButton.addActionListener(e -> saveApprovalSetting());
        approvedBox.addActionListener(e -> setButtonState());
        staySignedInBox.addActionListener(e -> setButtonState());
        autoLogoutBox.addActionListener(e -> setButtonState());
        saveNameButton.addActionListener(e -> saveDeviceName());
        saveCodeButton.addActionListener(e -> saveDeviceCode());
        blockButton.addActionListener(e -> blockSelectedDevice());
        deviceTable.getSelectionModel().addListSelectionListener(this::handleSelectionChanged);

        detailsArea.setText("Select a device to see its full details.");
        approvedBox.setSelected(false);
        staySignedInBox.setSelected(false);
        autoLogoutBox.setSelected(false);
        autoLogoutOverrideLabel.setVisible(false);
        setButtonState();
        loadDevices();
        WindowHelper.configurePosWindow(this);
    }

    private void loadDevices() {
        String preserveDeviceId = selectedDevice == null ? null : selectedDevice.getDeviceId();
        CachedUiLoader.load(this, "devices:list", DeviceSnapshot.class,
                SessionDataCache.SCREEN_TTL, loadingState,
                () -> new DeviceSnapshot(LanApiClient.loadManagedDevices()),
                snapshot -> applyDevices(snapshot, preserveDeviceId));
    }

    private void applyDevices(DeviceSnapshot snapshot, String preserveDeviceId) {
        allDevices.clear();
        allDevices.addAll(snapshot.devices());
        applyFilter();
        restoreSelection(preserveDeviceId);
    }

    private record DeviceSnapshot(List<ManagedDevice> devices) { }

    private void refilterDevices() {
        String preserveDeviceId = selectedDevice == null ? null : selectedDevice.getDeviceId();
        applyFilter();
        restoreSelection(preserveDeviceId);
    }

    private void applyFilter() {
        String selectedFilter = (String) filterCombo.getSelectedItem();
        int activityDays = selectedActivityDays();
        filteredDevices.clear();
        deviceTableModel.setRowCount(0);

        for (ManagedDevice device : allDevices) {
            if (!matchesFilter(device, selectedFilter)
                    || !wasActiveWithinDays(device.getLastSeen(), activityDays, Instant.now())) {
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
            approvedBox.setSelected(false);
            staySignedInBox.setSelected(false);
            autoLogoutBox.setSelected(false);
            autoLogoutMinutesSpinner.setValue(15);
            autoLogoutOverrideLabel.setVisible(false);
            allowSalesBox.setSelected(false);
            allowOrdersBox.setSelected(false);
            sessionTableModel.setRowCount(0);
        }

        updateSummaryLabel();
        setButtonState();
    }

    private int selectedActivityDays() {
        String selection = (String) activityFilterCombo.getSelectedItem();
        if (selection == null || "Show all activity".equals(selection)) return 0;
        if (selection.contains(" 1 day")) return 1;
        if (selection.contains(" 5 days")) return 5;
        if (selection.contains(" 15 days")) return 15;
        if (selection.contains(" 30 days")) return 30;
        if (selection.contains(" 90 days")) return 90;
        return 0;
    }

    static boolean wasActiveWithinDays(Timestamp lastSeen, int days, Instant now) {
        if (days <= 0) return true;
        if (lastSeen == null || now == null) return false;
        return !lastSeen.toInstant().isBefore(now.minus(days, ChronoUnit.DAYS));
    }

    private boolean matchesFilter(ManagedDevice device, String filter) {
        if (filter == null || "All Devices".equalsIgnoreCase(filter)) {
            return true;
        }
        return switch (filter) {
            case "Pending Approval" -> !device.isApproved() && !device.isBlocked();
            case "Approved" -> device.isApproved() && !device.isBlocked();
            case "Stay Signed In" -> device.isApproved() && device.isPersistentLoginAllowed() && !device.isBlocked();
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
            approvedBox.setSelected(false);
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
        approvedBox.setSelected(device.isApproved() && !device.isBlocked());
        staySignedInBox.setSelected(device.isPersistentLoginAllowed() && !device.isBlocked());
        autoLogoutBox.setSelected(device.isAutoLogoutEnabled() && !device.isBlocked());
        autoLogoutMinutesSpinner.setValue(device.getAutoLogoutMinutes());
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
                + "Approved At: " + formatTimestamp(device.getApprovedAt()) + "\n"
                + "Approved By: " + defaultText(device.getApprovedByName()) + "\n"
                + "Persistent Employee Login: " + yesNo(device.isPersistentLoginAllowed()) + "\n"
                + "Automatic Logout: " + yesNo(device.isAutoLogoutEnabled()) + "\n"
                + "Automatic Logout Minutes: " + device.getAutoLogoutMinutes() + "\n"
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
        UiTaskRunner.submit(this, "devices.session-history", () -> LanApiClient.loadDeviceSessions(deviceId), sessions -> {
            for (DeviceSessionRecord session : sessions) {
                sessionTableModel.addRow(new Object[]{
                        formatTimestamp(session.getLoginTime()),
                        formatTimestamp(session.getLogoutTime()),
                        defaultText(session.getUserName()),
                        defaultText(session.getStoreName()),
                        defaultText(session.getSessionStatus())
                });
            }
        }, failure -> loadingState.failed(failure.getMessage(), true,
                () -> loadSessionHistory(deviceId)));
    }

    private void saveApprovalSetting() {
        if (selectedDevice == null) {
            return;
        }
        boolean approved = approvedBox.isSelected();
        boolean allowStaySignedIn = approved && staySignedInBox.isSelected();
        boolean autoLogoutEnabled = autoLogoutBox.isSelected();
        int autoLogoutMinutes = ((Number) autoLogoutMinutesSpinner.getValue()).intValue();
        boolean allowSales = allowSalesBox.isSelected();
        boolean allowOrders = allowOrdersBox.isSelected();
        String message = "Save device access settings?\n\n"
                + "Device Approved: " + yesNo(approved) + "\n"
                + "Employee Stays Signed In: " + yesNo(allowStaySignedIn) + "\n"
                + "Automatic Logout: " + yesNo(autoLogoutEnabled) + "\n"
                + "Inactivity Timeout: " + autoLogoutMinutes + " minute(s)"
                + (allowStaySignedIn && autoLogoutEnabled ? " (overridden by Stay Signed In)" : "") + "\n"
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

        String targetDeviceId=selectedDevice.getDeviceId();
        LanApiClient.DeviceAdminUpdate request=new LanApiClient.DeviceAdminUpdate(
                "ACCESS",targetDeviceId,approved,allowStaySignedIn,autoLogoutEnabled,
                autoLogoutMinutes,allowSales,allowOrders,notesArea.getText(),null,null);
        updateDeviceAsync("devices.save-access",request,()->{
            if (targetDeviceId != null && targetDeviceId.equals(SessionManager.getCurrentDeviceId())) {
                if (approved && allowStaySignedIn) {
                    SupabaseSessionManager.savePersistedSession(
                            SessionManager.getCurrentUserId(),
                            SessionManager.getCurrentLocationId()
                    );
                } else {
                    SupabaseSessionManager.clearPersistedSession();
                }
                AutoLogoutManager.applyPolicy(new LanApiClient.SessionPolicy(
                        allowStaySignedIn, autoLogoutEnabled, autoLogoutMinutes));
            }
            loadDevices();
        },"Could not save device access settings.");
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

        LanApiClient.DeviceAdminUpdate request=new LanApiClient.DeviceAdminUpdate(
                "BLOCK",selectedDevice.getDeviceId(),false,false,false,15,
                false,false,notesArea.getText(),null,null);
        updateDeviceAsync("devices.block",request,()->{
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
        },"Could not block the device.");
    }

    private void saveDeviceCode() {
        if (selectedDevice == null) {
            return;
        }
        LanApiClient.DeviceAdminUpdate request=new LanApiClient.DeviceAdminUpdate(
                "RECEIPT_CODE",selectedDevice.getDeviceId(),false,false,false,15,
                false,false,null,null,receiptCodeField.getText());
        updateDeviceAsync("devices.save-code",request,()->{loadDevices();JOptionPane.showMessageDialog(this,"Device code saved.");},"Could not save device code.");
    }

    private void saveDeviceName() {
        if (selectedDevice == null) {
            return;
        }
        LanApiClient.DeviceAdminUpdate request=new LanApiClient.DeviceAdminUpdate(
                "NAME",selectedDevice.getDeviceId(),false,false,false,15,
                false,false,null,deviceNameField.getText(),null);
        updateDeviceAsync("devices.save-name",request,()->{loadDevices();JOptionPane.showMessageDialog(this,"Device name saved.");},"Could not save device name.");
    }

    private void rotateSelectedCredential() {
        if (selectedDevice == null || !selectedDevice.isApproved() || selectedDevice.isBlocked()) return;
        int choice = JOptionPane.showConfirmDialog(this,
                "Rotate this register's secure device credential?\n\nThe register will claim it automatically. Its current credential remains valid during the safe overlap window.",
                "Rotate Device Credential", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return;
        LanApiClient.DeviceAdminUpdate request=new LanApiClient.DeviceAdminUpdate(
                "ROTATE",selectedDevice.getDeviceId(),false,false,false,15,
                false,false,null,null,null);
        updateDeviceAsync("devices.rotate",request,()->{
            JOptionPane.showMessageDialog(this,
                    "Rotation queued. The server will issue and the register will claim the replacement automatically.",
                    "Device Credential", JOptionPane.INFORMATION_MESSAGE);
        },"Could not queue credential rotation.");
    }

    private void showSecurityStatus() {
        UiTaskRunner.submit(this,"devices.security-status",LanApiClient::loadDeviceSecurityStatus,this::showSecurityStatus,ex->JOptionPane.showMessageDialog(this,"Could not inspect database security.\n\n"+ex.getMessage(),"Security Status",JOptionPane.ERROR_MESSAGE));
    }

    private void showSecurityStatus(LanApiClient.DeviceSecurityStatus report) {
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
    }

    private void updateDeviceAsync(String jobKey, LanApiClient.DeviceAdminUpdate request,
                                   Runnable success, String failureMessage) {
        String key=mutationKey(request.toString());
        UiTaskRunner.submit(this,jobKey,()->{LanApiClient.updateManagedDevice(request,key);return Boolean.TRUE;},ignored->{clearMutationKey();SessionDataCache.invalidate("devices:list");success.run();},ex->JOptionPane.showMessageDialog(this,failureMessage+"\n\n"+ex.getMessage(),"Device Management",JOptionPane.ERROR_MESSAGE));
    }

    private void loadServerManagement() {
        loadingState.loading(false, Instant.now());
        UiTaskRunner.submit(this,"servers:list",ServerManagementClient::load,
                state->{loadingState.ready(Instant.now());showServerManagement(state);},
                ex->{loadingState.failed(ex.getMessage(),true,this::loadServerManagement);
                    JOptionPane.showMessageDialog(this,"Could not load server inventory.\n\n"+ex.getMessage(),"Server Management",JOptionPane.ERROR_MESSAGE);});
    }

    private void showServerManagement(LanApiClient.ServerAdminState state) {
        DefaultTableModel model=new DefaultTableModel(new Object[]{"Server","Role","Health","Endpoint","Version","Last Heartbeat","Last Sync","Last Materialization","Recovery Ready","Rows"},0){
            @Override public boolean isCellEditable(int row,int column){return false;}};
        for(LanApiClient.ServerRecord server:state.servers())model.addRow(new Object[]{
                defaultText(server.displayName()),server.role(),server.health(),server.endpointHost()+":"+server.endpointPort(),
                defaultText(server.appVersion()),formatRegistryTime(server.lastHeartbeatAt()),formatRegistryTime(server.lastSyncAt()),
                formatRegistryTime(server.lastMaterializationAt()),formatRegistryTime(server.recoveryValidatedAt()),
                server.materializedRowCount()==null?"":server.materializedRowCount()});
        JTable table=new JTable(model);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setRowHeight(27);
        TableRowSorter<DefaultTableModel> sorter=new TableRowSorter<>(model);table.setRowSorter(sorter);
        long primaryCount=state.servers().stream().filter(s->"PRIMARY".equals(s.role())||"DRAINING".equals(s.role())).count();
        long standbyCount=state.servers().stream().filter(s->"STANDBY".equals(s.role())).count();
        long retiredCount=state.servers().stream().filter(s->"RETIRED".equals(s.role())||"FENCED".equals(s.role())).count();
        JTextArea note=new JTextArea("Servers: "+state.servers().size()+"   |   Primary: "+primaryCount+"   |   Standby: "+standbyCount+"   |   Retired/Fenced: "+retiredCount+"\n"
                +"One writable primary is allowed per store. Standbys remain non-writable until a verified handoff or approved emergency recovery completes.");
        note.setEditable(false);note.setLineWrap(true);note.setWrapStyleWord(true);note.setOpaque(false);
        JComboBox<String> roleFilter=new JComboBox<>(new String[]{"All roles","PRIMARY","STANDBY","DRAINING","RETIRED","FENCED"});
        JComboBox<String> healthFilter=new JComboBox<>(new String[]{"All health","ONLINE","STALE","OFFLINE","DEGRADED","FENCED","RETIRED"});
        JPanel filters=new JPanel(new FlowLayout(FlowLayout.LEFT));filters.add(new JLabel("Role:"));filters.add(roleFilter);filters.add(new JLabel("Health:"));filters.add(healthFilter);
        JPanel header=new JPanel();header.setLayout(new BoxLayout(header,BoxLayout.Y_AXIS));header.add(note);header.add(filters);
        JPanel panel=new JPanel(new BorderLayout(8,8));panel.add(header,BorderLayout.NORTH);panel.add(new JScrollPane(table),BorderLayout.CENTER);
        Runnable applyFilters=()->{java.util.List<RowFilter<Object,Object>> active=new ArrayList<>();String role=(String)roleFilter.getSelectedItem(),health=(String)healthFilter.getSelectedItem();if(role!=null&&!role.startsWith("All"))active.add(RowFilter.regexFilter("^"+java.util.regex.Pattern.quote(role)+"$",1));if(health!=null&&!health.startsWith("All"))active.add(RowFilter.regexFilter("^"+java.util.regex.Pattern.quote(health)+"$",2));sorter.setRowFilter(active.isEmpty()?null:RowFilter.andFilter(active));};
        roleFilter.addActionListener(e->applyFilters.run());healthFilter.addActionListener(e->applyFilters.run());
        JButton history=new JButton("History");JButton rename=new JButton("Rename");JButton prepare=new JButton("Prepare Standby");JButton handoff=new JButton("Start Verified Handoff");
        JButton emergency=new JButton("Emergency Takeover");JButton retire=new JButton("Retire");JButton close=new JButton("Close");
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));actions.add(history);actions.add(rename);actions.add(prepare);actions.add(handoff);actions.add(emergency);actions.add(retire);actions.add(close);panel.add(actions,BorderLayout.SOUTH);
        JDialog dialog=new JDialog(this,"Store Server Management",true);dialog.setContentPane(panel);dialog.setSize(1080,470);dialog.setLocationRelativeTo(this);
        Runnable updateButtons=()->{int row=table.getSelectedRow();boolean selected=row>=0;LanApiClient.ServerRecord s=selected?state.servers().get(table.convertRowIndexToModel(row)):null;
            rename.setEnabled(selected&&!("RETIRED".equals(s.role())));retire.setEnabled(selected&&!("PRIMARY".equals(s.role())||"DRAINING".equals(s.role())));
            prepare.setEnabled(selected&&"STANDBY".equals(s.role()));
            handoff.setEnabled(selected&&"STANDBY".equals(s.role())&&state.currentServerInstanceId()!=null);
            emergency.setEnabled(selected&&"STANDBY".equals(s.role()));};
        table.getSelectionModel().addListSelectionListener(e->updateButtons.run());updateButtons.run();close.addActionListener(e->dialog.dispose());
        history.addActionListener(e->{StringBuilder text=new StringBuilder();for(LanApiClient.ServerEvent event:state.events())text.append(formatRegistryTime(event.createdAt())).append("  ").append(event.eventType()).append(event.actorName()==null||event.actorName().isBlank()?"":" — "+event.actorName()).append("\n").append(event.details()==null?"":event.details()).append("\n\n");JTextArea area=new JTextArea(text.length()==0?"No server events have been recorded.":text.toString(),18,72);area.setEditable(false);area.setCaretPosition(0);JOptionPane.showMessageDialog(dialog,new JScrollPane(area),"Server History",JOptionPane.INFORMATION_MESSAGE);});
        rename.addActionListener(e->{LanApiClient.ServerRecord s=state.servers().get(table.convertRowIndexToModel(table.getSelectedRow()));String name=JOptionPane.showInputDialog(dialog,"Server name",s.displayName());if(name!=null&&!name.isBlank())runServerMutation(dialog,new LanApiClient.ServerAdminUpdate("RENAME",s.serverInstanceId(),null,name.trim(),UUID.randomUUID().toString(),false));});
        retire.addActionListener(e->{LanApiClient.ServerRecord s=state.servers().get(table.convertRowIndexToModel(table.getSelectedRow()));if(JOptionPane.showConfirmDialog(dialog,"Retire "+s.displayName()+"?\n\nIts database and computer will not be deleted or powered off.","Retire Server",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.OK_OPTION)runServerMutation(dialog,new LanApiClient.ServerAdminUpdate("RETIRE",s.serverInstanceId(),null,null,UUID.randomUUID().toString(),false));});
        prepare.addActionListener(e->{LanApiClient.ServerRecord s=state.servers().get(table.convertRowIndexToModel(table.getSelectedRow()));runServerMutation(dialog,new LanApiClient.ServerAdminUpdate("PREPARE_STANDBY",s.serverInstanceId(),null,null,UUID.randomUUID().toString(),false));});
        handoff.addActionListener(e->{LanApiClient.ServerRecord target=state.servers().get(table.convertRowIndexToModel(table.getSelectedRow()));String source=state.currentServerInstanceId();if(JOptionPane.showConfirmDialog(dialog,"Move this store from the current primary to "+target.displayName()+"?\n\nThe primary will drain new changes, complete a final cloud materialization, and mark the standby ready.","Verified Server Handoff",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.OK_OPTION)runServerMutation(dialog,new LanApiClient.ServerAdminUpdate("BEGIN_HANDOFF",source,target.serverInstanceId(),null,UUID.randomUUID().toString(),false));});
        emergency.addActionListener(e->{LanApiClient.ServerRecord target=state.servers().get(table.convertRowIndexToModel(table.getSelectedRow()));LanApiClient.ServerRecord primary=state.servers().stream().filter(s->"PRIMARY".equals(s.role())).findFirst().orElse(null);JCheckBox ack=new JCheckBox("I understand transactions newer than the displayed cloud recovery point may be lost.");JPanel warning=new JPanel();warning.setLayout(new BoxLayout(warning,BoxLayout.Y_AXIS));warning.add(new JLabel("Use emergency takeover only when the primary cannot be reached."));warning.add(new JLabel("Recovery point: "+formatRegistryTime(primary==null?null:primary.lastMaterializationAt())));warning.add(Box.createVerticalStrut(8));warning.add(ack);if(JOptionPane.showConfirmDialog(dialog,warning,"Emergency Server Recovery",JOptionPane.OK_CANCEL_OPTION,JOptionPane.ERROR_MESSAGE)==JOptionPane.OK_OPTION){if(!ack.isSelected()){JOptionPane.showMessageDialog(dialog,"You must acknowledge the recovery warning.");return;}runServerMutation(dialog,new LanApiClient.ServerAdminUpdate("EMERGENCY_TAKEOVER",target.serverInstanceId(),null,null,UUID.randomUUID().toString(),true));}});
        dialog.setVisible(true);
    }

    private void runServerMutation(JDialog dialog,LanApiClient.ServerAdminUpdate request){
        dialog.dispose();String key=request.idempotencyKey();loadingState.loading(true,Instant.now());
        UiTaskRunner.submit(this,"servers:update",()->ServerManagementClient.update(request,key),
                ignored->{loadingState.ready(Instant.now());SessionDataCache.invalidate("devices:list");loadServerManagement();},
                ex->{loadingState.failed(ex.getMessage(),true,this::loadServerManagement);JOptionPane.showMessageDialog(this,"Server operation was not completed.\n\n"+ex.getMessage(),"Server Management",JOptionPane.ERROR_MESSAGE);});
    }

    private String formatRegistryTime(String value){
        if(value==null||value.isBlank())return "Not available";
        try{return Instant.parse(value).atZone(StoreTimeZoneHelper.getStoreZone()).format(DATE_TIME_FORMAT);}catch(Exception ex){return value;}
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
                if (device.isPersistentLoginAllowed()) staySignedIn++;
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
                        + " devices"
                        + (selectedActivityDays() > 0
                        ? " active within " + selectedActivityDays() + " day(s)"
                        : "")
                        + "   |   Stay Signed In: " + staySignedIn
                        + "   Sales: " + salesAllowed
                        + "   Orders: " + ordersAllowed
                        + "   Pending: " + pending
                        + "   Blocked: " + blocked
        );
    }

    private void setButtonState() {
        boolean hasSelection = selectedDevice != null;
        approvedBox.setEnabled(hasSelection && !selectedDevice.isBlocked());
        staySignedInBox.setEnabled(hasSelection && approvedBox.isSelected() && !selectedDevice.isBlocked());
        autoLogoutBox.setEnabled(hasSelection && !selectedDevice.isBlocked());
        autoLogoutMinutesSpinner.setEnabled(hasSelection && autoLogoutBox.isSelected()
                && !selectedDevice.isBlocked());
        autoLogoutOverrideLabel.setVisible(hasSelection && staySignedInBox.isSelected()
                && autoLogoutBox.isSelected() && !selectedDevice.isBlocked());
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
