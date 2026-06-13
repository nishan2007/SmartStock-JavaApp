package ui.screens;

import managers.PermissionManager;
import managers.NavigationManager;
import models.AppNotification;
import services.NotificationService;
import ui.helpers.ThemeManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class NotificationsDialog extends JDialog {
    private final JFrame parentFrame;
    private final JComboBox<String> filterBox = new JComboBox<>(new String[]{"All", "Urgent", "Snoozed", "Read", "Cleared"});
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Severity", "Source", "Title", "Message"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final JLabel summaryLabel = new JLabel("Loading notifications...");
    private List<AppNotification> notifications = new ArrayList<>();
    private List<AppNotification> filtered = new ArrayList<>();

    public NotificationsDialog(JFrame parentFrame) {
        super(parentFrame, "Notifications", false);
        this.parentFrame = parentFrame;
        setSize(920, 520);
        setLocationRelativeTo(parentFrame);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        table.setRowHeight(30);
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JButton refreshButton = new JButton("Refresh");
        JButton openButton = new JButton("Open");
        JButton markReadButton = new JButton("Mark Read");
        JButton snoozeButton = new JButton("Snooze 1 Hour");
        JButton clearButton = new JButton("Clear");

        refreshButton.addActionListener(e -> refresh());
        openButton.addActionListener(e -> openSelected());
        markReadButton.addActionListener(e -> markReadSelected());
        snoozeButton.addActionListener(e -> snoozeSelected());
        clearButton.addActionListener(e -> clearSelected());
        filterBox.addActionListener(e -> applyFilter());

        JPanel topPanel = new JPanel(new BorderLayout(12, 0));
        topPanel.add(summaryLabel, BorderLayout.CENTER);
        topPanel.add(filterBox, BorderLayout.EAST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(openButton);
        buttons.add(markReadButton);
        buttons.add(snoozeButton);
        buttons.add(clearButton);
        buttons.add(refreshButton);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(topPanel, BorderLayout.NORTH);
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        ThemeManager.applyToWindow(this);
        refresh();
    }

    private void refresh() {
        notifications = NotificationService.loadNotifications();
        applyFilter();
    }

    private void applyFilter() {
        filtered = new ArrayList<>();
        String filter = String.valueOf(filterBox.getSelectedItem());
        for (AppNotification notification : notifications) {
            if ("Urgent".equals(filter) && !notification.isUrgentVisible()) {
                continue;
            }
            if ("Snoozed".equals(filter) && !notification.isSnoozed()) {
                continue;
            }
            if ("Read".equals(filter) && !notification.isRead()) {
                continue;
            }
            if ("Cleared".equals(filter) && !notification.isDismissed()) {
                continue;
            }
            if ("All".equals(filter) && (notification.isSnoozed() || notification.isDismissed())) {
                continue;
            }
            filtered.add(notification);
        }
        tableModel.setRowCount(0);
        int unread = 0;
        int urgent = 0;
        for (AppNotification notification : notifications) {
            if (notification.isUnreadVisible()) {
                unread++;
            }
            if (notification.isUrgentVisible()) {
                urgent++;
            }
        }
        for (AppNotification notification : filtered) {
            tableModel.addRow(new Object[]{
                    notification.severity(),
                    notification.source(),
                    notification.title(),
                    notification.message()
            });
        }
        summaryLabel.setText(unread + " unread notification(s), " + urgent + " urgent.");
    }

    private List<AppNotification> selectedNotifications() {
        int[] rows = table.getSelectedRows();
        List<AppNotification> selected = new ArrayList<>();
        for (int row : rows) {
            int modelRow = table.convertRowIndexToModel(row);
            if (modelRow >= 0 && modelRow < filtered.size()) {
                selected.add(filtered.get(modelRow));
            }
        }
        return selected;
    }

    private AppNotification selectedNotification() {
        List<AppNotification> selected = selectedNotifications();
        if (selected.isEmpty()) {
            return null;
        }
        return selected.get(0);
    }

    private void openSelected() {
        AppNotification notification = selectedNotification();
        if (notification == null) {
            JOptionPane.showMessageDialog(this, "Select a notification first.");
            return;
        }
        dispose();
        navigate(notification.actionTarget());
    }

    private void markReadSelected() {
        List<AppNotification> selected = selectedNotifications();
        if (selected.isEmpty()) {
            return;
        }
        try {
            for (AppNotification notification : selected) {
                NotificationService.markRead(notification.notificationKey());
            }
            refresh();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Notifications", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void snoozeSelected() {
        List<AppNotification> selected = selectedNotifications();
        if (selected.isEmpty()) {
            return;
        }
        try {
            for (AppNotification notification : selected) {
                NotificationService.snooze(notification.notificationKey(), 60);
            }
            refresh();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Notifications", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearSelected() {
        List<AppNotification> selected = selectedNotifications();
        if (selected.isEmpty()) {
            return;
        }
        try {
            for (AppNotification notification : selected) {
                NotificationService.clear(notification.notificationKey());
            }
            refresh();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Notifications", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void navigate(JFrame parent, String actionTarget) {
        if (parent == null || actionTarget == null || actionTarget.isBlank()) {
            return;
        }
        if (!canOpenTarget(actionTarget)) {
            JOptionPane.showMessageDialog(parent,
                    "You do not have permission to open that screen.",
                    "Notifications",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        switch (actionTarget) {
            case "BalanceDraw" -> NavigationManager.openBalanceDraw(parent);
            case "CustomOrderItems" -> NavigationManager.openCustomOrderItems(parent);
            case "DeviceManagement" -> NavigationManager.openDeviceManagement(parent);
            case "MaintenanceManagement" -> NavigationManager.openMaintenanceManagement(parent);
            case "Orders", "OrdersManagerDashboard" -> NavigationManager.openOrdersManagerDashboard(parent);
            case "SyncStatus" -> new SyncStatus().setVisible(true);
            case "ViewInventory" -> NavigationManager.openViewInventory(parent);
            default -> NavigationManager.showMainMenu(parent);
        }
    }

    private static boolean canOpenTarget(String actionTarget) {
        return switch (actionTarget) {
            case "BalanceDraw" -> PermissionManager.hasPermission("BALANCE_DRAWER");
            case "CustomOrderItems" -> PermissionManager.canAccessScreen("CustomOrderItems");
            case "DeviceManagement" -> PermissionManager.hasPermission("DEVICE_MANAGEMENT");
            case "MaintenanceManagement" -> PermissionManager.canAccessScreen("MaintenanceManagement");
            case "Orders", "OrdersManagerDashboard" -> PermissionManager.hasPermission("ORDERS_MANAGER_DASHBOARD")
                    || PermissionManager.hasPermission("MANAGE_CUSTOM_ORDERS");
            case "SyncStatus" -> PermissionManager.hasPermission("SYNC_NOTIFICATIONS");
            case "ViewInventory" -> PermissionManager.canAccessScreen("ViewInventory");
            default -> true;
        };
    }

    private void navigate(String actionTarget) {
        navigate(parentFrame, actionTarget);
    }
}
