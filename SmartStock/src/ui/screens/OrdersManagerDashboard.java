package ui.screens;

import utils.CurrencyFormatter;
import managers.PermissionManager;
import managers.SessionManager;
import services.CustomOrderDataService;
import services.CustomOrderDataService.EmployeeOption;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.ThemeManager;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.List;

public class OrdersManagerDashboard extends JFrame {
    private static final NumberFormat CURRENCY = CurrencyFormatter.create(Locale.US);
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");

    private final JLabel overdueLabel = metricLabel();
    private final JLabel dueTodayLabel = metricLabel();
    private final JLabel readyLabel = metricLabel();
    private final JLabel unpaidLabel = metricLabel();
    private final JLabel assignedLabel = metricLabel();
    private final JLabel refundsLabel = metricLabel();
    private final JLabel cancelledLabel = metricLabel();
    private final JLabel lowStockLabel = metricLabel();
    private final DefaultTableModel actionQueueModel;
    private JTable actionQueueTable;
    private final JComboBox<EmployeeOption> assignEmployeeBox = new JComboBox<>();
    private final JComboBox<String> assignStatusBox = new JComboBox<>(new String[]{"NEW", "ASSIGNED", "IN_PROGRESS", "READY", "COMPLETED"});
    private final JTextArea selectedOrderDetailsArea = new JTextArea();
    private final DefaultTableModel exceptionModel;
    private final DefaultTableModel lowStockModel;
    private final DefaultTableModel auditModel;
    private final JLabel storeLabel = new JLabel();
    private ZoneId storeZone = resolveStoreZone();
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

    public OrdersManagerDashboard() {
        setTitle("Orders Manager Dashboard");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(14, 14));
        setJMenuBar(AppMenuBar.create(this, "OrdersManagerDashboard"));

        actionQueueModel = readOnlyModel("ID", "Order #", "Status", "Due", "Customer", "Phone", "Store", "Assigned", "Balance");
        exceptionModel = readOnlyModel("Time", "Type", "Order #", "Customer", "Amount", "User", "Reason / Note");
        lowStockModel = readOnlyModel("Item", "Variant", "Qty", "Reorder At", "Stock");
        auditModel = readOnlyModel("Time", "Order #", "Action", "Field", "Old", "New", "User", "Device", "Reason");

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.setBackground(new Color(245, 247, 250));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildContent(), BorderLayout.CENTER);
        root.add(loadingState, BorderLayout.SOUTH);
        add(root, BorderLayout.CENTER);

        loadDashboard();
        loadEmployees();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 8));
        header.setOpaque(false);

        JLabel title = new JLabel("Orders Manager Dashboard");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(new Color(31, 41, 55));

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> loadDashboard());
        updateStoreLabel();

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(storeLabel);
        right.add(refreshButton);

        header.add(title, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel buildContent() {
        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setOpaque(false);
        content.add(buildMetricPanel(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Action Queue", buildActionQueuePanel());
        tabs.addTab("Refunds / Cancellations", tablePanel(exceptionModel));
        tabs.addTab("Low Stock", tablePanel(lowStockModel));
        tabs.addTab("Audit Log", tablePanel(auditModel));
        content.add(tabs, BorderLayout.CENTER);
        return content;
    }

    private JPanel buildActionQueuePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        actionQueueTable = new JTable(actionQueueModel);
        actionQueueTable.setRowHeight(27);
        actionQueueTable.getTableHeader().setReorderingAllowed(false);
        actionQueueTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableColumnModel columns = actionQueueTable.getColumnModel();
        columns.getColumn(0).setMinWidth(0);
        columns.getColumn(0).setMaxWidth(0);
        columns.getColumn(0).setPreferredWidth(0);
        actionQueueTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedActionOrder();
            }
        });

        JPanel assignmentPanel = new JPanel(new GridBagLayout());
        assignmentPanel.setBorder(BorderFactory.createTitledBorder("Assign Order"));
        assignmentPanel.setPreferredSize(new Dimension(340, 0));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        addAssignmentField(assignmentPanel, gbc, 0, "Assign To:", assignEmployeeBox);
        addAssignmentField(assignmentPanel, gbc, 1, "Status:", assignStatusBox);

        selectedOrderDetailsArea.setEditable(false);
        selectedOrderDetailsArea.setLineWrap(true);
        selectedOrderDetailsArea.setWrapStyleWord(true);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        assignmentPanel.add(new JScrollPane(selectedOrderDetailsArea), gbc);

        JButton saveButton = new JButton("Save Assignment");
        JButton refreshButton = new JButton("Refresh");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(refreshButton);
        buttons.add(saveButton);
        gbc.gridy = 3;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        assignmentPanel.add(buttons, gbc);

        saveButton.addActionListener(e -> saveDashboardAssignment());
        refreshButton.addActionListener(e -> loadDashboard());

        panel.add(new JScrollPane(actionQueueTable), BorderLayout.CENTER);
        panel.add(assignmentPanel, BorderLayout.EAST);
        return panel;
    }

    private void addAssignmentField(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
    }

    private JPanel buildMetricPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 4, 10, 10));
        panel.setOpaque(false);
        panel.add(overdueLabel);
        panel.add(dueTodayLabel);
        panel.add(readyLabel);
        panel.add(unpaidLabel);
        panel.add(assignedLabel);
        panel.add(refundsLabel);
        panel.add(cancelledLabel);
        panel.add(lowStockLabel);
        return panel;
    }

    private JScrollPane tablePanel(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(27);
        table.getTableHeader().setReorderingAllowed(false);
        return new JScrollPane(table);
    }

    private void loadDashboard() {
        storeZone = resolveStoreZone();
        updateStoreLabel();
        CachedUiLoader.load(this, "orders-dashboard", services.LanOrdersDashboardService.Dashboard.class,
                SessionDataCache.SCREEN_TTL, loadingState,
                services.LanApiClient::loadCustomOrderDashboard, this::applyDashboard);
    }

    private void applyDashboard(services.LanOrdersDashboardService.Dashboard dashboard) {
        var metrics=dashboard.metrics();
            overdueLabel.setText("Overdue Orders: "+metrics.overdue());dueTodayLabel.setText("Due Today: "+metrics.dueToday());readyLabel.setText("Ready Pickup: "+metrics.ready());assignedLabel.setText("Assigned Not Started: "+metrics.assigned());cancelledLabel.setText("Cancelled 7 Days: "+metrics.cancelled());unpaidLabel.setText("Unpaid Balance: "+CURRENCY.format(metrics.unpaid()));refundsLabel.setText("Refunds Today: "+CURRENCY.format(metrics.refunds()));lowStockLabel.setText("Low Stock Items: "+metrics.lowStock());
            actionQueueModel.setRowCount(0);for(var row:dashboard.actions())actionQueueModel.addRow(new Object[]{row.orderId(),row.orderNumber(),row.status(),row.dueDate(),row.customer(),row.phone(),row.store(),row.assigned(),CURRENCY.format(row.balance())});
            exceptionModel.setRowCount(0);for(var row:dashboard.exceptions())exceptionModel.addRow(new Object[]{formatTimestamp(new Timestamp(row.atEpochMillis())),row.type(),row.orderNumber(),row.customer(),CURRENCY.format(row.amount()),row.user(),row.reason()});
            lowStockModel.setRowCount(0);for(var row:dashboard.lowStock())lowStockModel.addRow(new Object[]{row.item(),row.variant(),row.quantity(),row.reorder(),row.status()});
            auditModel.setRowCount(0);for(var row:dashboard.audit())auditModel.addRow(new Object[]{formatTimestamp(new Timestamp(row.atEpochMillis())),row.orderNumber(),row.action(),row.field(),row.oldValue(),row.newValue(),row.user(),row.device(),row.reason()});
    }

    private void loadEmployees() {
        CachedUiLoader.load(this, "reference:active-employees", EmployeeSnapshot.class,
                SessionDataCache.REFERENCE_TTL, loadingState,
                () -> new EmployeeSnapshot(CustomOrderDataService.listActiveEmployees()), this::applyEmployees);
    }

    private void applyEmployees(EmployeeSnapshot snapshot) {
        assignEmployeeBox.removeAllItems();
        assignEmployeeBox.addItem(new EmployeeOption(null, "Unassigned"));
        for (EmployeeOption employee : snapshot.employees()) {
            assignEmployeeBox.addItem(employee);
        }
    }

    private record EmployeeSnapshot(List<EmployeeOption> employees) { }

    private void loadSelectedActionOrder() {
        if (actionQueueTable == null || actionQueueTable.getSelectedRow() < 0) {
            selectedOrderDetailsArea.setText("");
            return;
        }
        int modelRow = actionQueueTable.convertRowIndexToModel(actionQueueTable.getSelectedRow());
        long orderId = Long.parseLong(actionQueueModel.getValueAt(modelRow, 0).toString());
        String orderNumber = safeText(actionQueueModel.getValueAt(modelRow, 1));
        String status = safeText(actionQueueModel.getValueAt(modelRow, 2));
        String due = safeText(actionQueueModel.getValueAt(modelRow, 3));
        String customer = safeText(actionQueueModel.getValueAt(modelRow, 4));
        String phone = safeText(actionQueueModel.getValueAt(modelRow, 5));
        String store = safeText(actionQueueModel.getValueAt(modelRow, 6));
        String assigned = safeText(actionQueueModel.getValueAt(modelRow, 7));
        String balance = safeText(actionQueueModel.getValueAt(modelRow, 8));

        selectEmployeeByName(assigned);
        assignStatusBox.setSelectedItem(status.isBlank() ? "NEW" : status);
        selectedOrderDetailsArea.setText(
                "Order: " + orderNumber + "\n"
                        + "Customer: " + customer + "\n"
                        + "Phone: " + phone + "\n"
                        + "Due: " + due + "\n"
                        + "Store: " + store + "\n"
                        + "Balance: " + balance + "\n"
                        + "Order ID: " + orderId
        );
    }

    private void saveDashboardAssignment() {
        if (!PermissionManager.hasPermission("MANAGE_CUSTOM_ORDERS") && !PermissionManager.hasPermission("CUSTOM_ORDER_OVERRIDES")) {
            JOptionPane.showMessageDialog(this, "You do not have permission to assign custom orders.", "Access Denied", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (actionQueueTable == null || actionQueueTable.getSelectedRow() < 0) {
            JOptionPane.showMessageDialog(this, "Select an order first.");
            return;
        }
        int modelRow = actionQueueTable.convertRowIndexToModel(actionQueueTable.getSelectedRow());
        long orderId = Long.parseLong(actionQueueModel.getValueAt(modelRow, 0).toString());
        EmployeeOption employee = (EmployeeOption) assignEmployeeBox.getSelectedItem();
        boolean assigned = employee != null && employee.userId() != null;
        String status = assignStatusBox.getSelectedItem() == null ? "NEW" : assignStatusBox.getSelectedItem().toString();
        if (assigned && "NEW".equals(status)) {
            status = "ASSIGNED";
        }

        try {
            services.LanApiClient.assignCustomOrder(orderId,assigned?employee.userId():null,status,java.util.UUID.randomUUID().toString());
            SessionDataCache.invalidate("orders-dashboard");
            loadDashboard();
            JOptionPane.showMessageDialog(this, "Order assignment saved.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save assignment: " + ex.getMessage(), "Server Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void selectEmployeeByName(String name) {
        String normalized = safeText(name);
        for (int i = 0; i < assignEmployeeBox.getItemCount(); i++) {
            EmployeeOption option = assignEmployeeBox.getItemAt(i);
            if (normalized.equals(safeText(option.name()))) {
                assignEmployeeBox.setSelectedIndex(i);
                return;
            }
        }
        assignEmployeeBox.setSelectedIndex(0);
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        return StoreTimeZoneHelper.formatLocalTimestamp(timestamp, DATE_TIME_FORMAT);
    }

    private void updateStoreLabel() {
        String storeName = SessionManager.getCurrentLocationName();
        Integer locationId = SessionManager.getCurrentLocationId();
        String storeText = locationId == null ? "Store: Not selected" : "Store: " + (storeName == null ? locationId : storeName);
        storeLabel.setText(storeText + "    Store Timezone: " + storeZone);
    }

    private ZoneId resolveStoreZone() {
        String timezone = SessionManager.getCurrentLocationTimezone();
        if (timezone != null && !timezone.isBlank()) {
            try {
                return ZoneId.of(timezone.trim());
            } catch (Exception ignored) {
            }
        }
        return ZoneId.systemDefault();
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JLabel metricLabel() {
        JLabel label = new JLabel();
        label.setOpaque(true);
        boolean dark = ThemeManager.isDarkModeEnabled();
        label.setBackground(dark ? new Color(88, 88, 88) : Color.WHITE);
        label.setForeground(dark ? Color.WHITE : Color.BLACK);
        label.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(dark ? new Color(115, 115, 115) : new Color(220, 224, 230), 1),
                new EmptyBorder(10, 10, 10, 10)
        ));
        label.setFont(new Font("SansSerif", Font.BOLD, 13));
        return label;
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String safeText(Object value) {
        return value == null ? "" : value.toString();
    }
}
