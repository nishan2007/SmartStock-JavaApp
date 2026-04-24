package ui.screens;

import data.DB;
import managers.NavigationManager;
import managers.SessionManager;
import managers.SupabaseSessionManager;
import models.DeviceSessionRecord;
import models.ManagedDevice;
import services.DeviceManagementService;
import ui.components.AppMenuBar;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class DeviceManagement extends JFrame {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    private final DefaultTableModel deviceTableModel = new DefaultTableModel(
            new Object[]{"Device", "Status", "Last User", "Store", "Last Seen", "Sessions"}, 0
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
            "Blocked"
    });
    private final JTextArea detailsArea = new JTextArea();
    private final JTextArea notesArea = new JTextArea(3, 32);
    private final JCheckBox staySignedInBox = new JCheckBox("Allow this device to stay signed in after the app is closed");
    private final JLabel summaryLabel = new JLabel("Loading devices...");
    private final JButton saveApprovalButton = new JButton("Save Sign-In Setting");
    private final JButton blockButton = new JButton("Block Device");
    private final JButton refreshButton = new JButton("Refresh");
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

        JLabel subtitleLabel = new JLabel("Review registered devices, inspect login history, and approve or block access.");
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
        staySignedInBox.setOpaque(false);
        staySignedInBox.setFont(new Font("SansSerif", Font.BOLD, 13));

        JPanel detailsPanel = new JPanel(new BorderLayout(10, 10));
        detailsPanel.setBorder(BorderFactory.createTitledBorder("Device Details"));
        detailsPanel.add(new JScrollPane(detailsArea), BorderLayout.CENTER);

        JPanel notesPanel = new JPanel(new BorderLayout(0, 8));
        notesPanel.setOpaque(false);
        JLabel notesLabel = new JLabel("Approval / block note");
        notesLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        JPanel noteHeaderPanel = new JPanel();
        noteHeaderPanel.setLayout(new BoxLayout(noteHeaderPanel, BoxLayout.Y_AXIS));
        noteHeaderPanel.setOpaque(false);
        noteHeaderPanel.add(staySignedInBox);
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
        closeButton.addActionListener(e -> NavigationManager.showMainMenu(this));
        saveApprovalButton.addActionListener(e -> saveApprovalSetting());
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

        try (Connection conn = DB.getConnection()) {
            allDevices.addAll(DeviceManagementService.getAllDevices(conn));
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
            staySignedInBox.setSelected(false);
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
            case "Approved" -> device.isApproved() && !device.isBlocked();
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
            staySignedInBox.setSelected(false);
            sessionTableModel.setRowCount(0);
            setButtonState();
            return;
        }

        updateSelectedDevice(filteredDevices.get(row));
    }

    private void updateSelectedDevice(ManagedDevice device) {
        selectedDevice = device;
        detailsArea.setText(buildDetailsText(device));
        notesArea.setText(device.getStatusNotes() == null ? "" : device.getStatusNotes());
        staySignedInBox.setSelected(device.isApproved() && !device.isBlocked());
        loadSessionHistory(device.getDeviceId());
        setButtonState();
    }

    private String buildDetailsText(ManagedDevice device) {
        return "Status: " + device.getStatusLabel() + "\n"
                + "Device Name: " + defaultText(device.getDeviceName()) + "\n"
                + "Host Name: " + defaultText(device.getHostname()) + "\n"
                + "Installation ID: " + defaultText(device.getInstallationId()) + "\n"
                + "Device ID: " + defaultText(device.getDeviceId()) + "\n"
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

        try (Connection conn = DB.getConnection()) {
            List<DeviceSessionRecord> sessions = DeviceManagementService.getDeviceSessionHistory(conn, deviceId, 25);
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
        String message = allowStaySignedIn
                ? "Allow this device to stay signed in after the app is closed?\n\n"
                : "Require this device to sign in again after the app is closed?\n\n";

        int result = JOptionPane.showConfirmDialog(
                this,
                message + selectedDevice.getDisplayName(),
                "Save Sign-In Setting",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        try (Connection conn = DB.getConnection()) {
            DeviceManagementService.updateDeviceApproval(
                    conn,
                    selectedDevice.getDeviceId(),
                    SessionManager.getCurrentUserId(),
                    allowStaySignedIn,
                    notesArea.getText()
            );
            loadDevices();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                    this,
                    "Could not save the sign-in setting.\n\n" + ex.getMessage(),
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

        try (Connection conn = DB.getConnection()) {
            DeviceManagementService.blockDevice(
                    conn,
                    selectedDevice.getDeviceId(),
                    SessionManager.getCurrentUserId(),
                    notesArea.getText()
            );

            if (isCurrentDevice) {
                SessionManager.clearSessionState();
                SupabaseSessionManager.clearSession();
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

    private void updateSummaryLabel() {
        int pending = 0;
        int approved = 0;
        int blocked = 0;

        for (ManagedDevice device : allDevices) {
            if (device.isBlocked()) {
                blocked++;
            } else if (device.isApproved()) {
                approved++;
            } else {
                pending++;
            }
        }

        summaryLabel.setText(
                "Showing " + filteredDevices.size() + " of " + allDevices.size()
                        + " devices   |   Approved: " + approved
                        + "   Pending: " + pending
                        + "   Blocked: " + blocked
        );
    }

    private void setButtonState() {
        boolean hasSelection = selectedDevice != null;
        staySignedInBox.setEnabled(hasSelection && !selectedDevice.isBlocked());
        saveApprovalButton.setEnabled(hasSelection && !selectedDevice.isBlocked());
        blockButton.setEnabled(hasSelection && !selectedDevice.isBlocked());
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
}
