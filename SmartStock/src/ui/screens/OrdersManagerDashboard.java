package ui.screens;

import data.DB;
import managers.PermissionManager;
import managers.SessionManager;
import services.CustomOrderAuditService;
import services.CustomOrderDataService;
import services.CustomOrderDataService.EmployeeOption;
import ui.components.AppMenuBar;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.ThemeManager;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class OrdersManagerDashboard extends JFrame {
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(Locale.US);
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
        try (Connection conn = DB.getConnection()) {
            loadMetrics(conn);
            loadActionQueue(conn);
            loadExceptions(conn);
            loadLowStock(conn);
            loadAuditLog(conn);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load orders manager dashboard: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadEmployees() {
        assignEmployeeBox.removeAllItems();
        assignEmployeeBox.addItem(new EmployeeOption(null, "Unassigned"));
        try {
            for (EmployeeOption employee : CustomOrderDataService.listActiveEmployees()) {
                assignEmployeeBox.addItem(employee);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load employees: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadMetrics(Connection conn) throws SQLException {
        overdueLabel.setText("Overdue Orders: " + countOrders(conn, "due_date < CURRENT_DATE AND status NOT IN ('DELIVERED', 'CANCELLED')"));
        dueTodayLabel.setText("Due Today: " + countOrders(conn, "due_date = CURRENT_DATE AND status NOT IN ('DELIVERED', 'CANCELLED')"));
        readyLabel.setText("Ready Pickup: " + countOrders(conn, "status = 'READY'"));
        assignedLabel.setText("Assigned Not Started: " + countOrders(conn, "status = 'ASSIGNED'"));
        cancelledLabel.setText("Cancelled 7 Days: " + countOrders(conn, "status = 'CANCELLED' AND cancelled_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'"));
        unpaidLabel.setText("Unpaid Balance: " + CURRENCY.format(sumOrders(conn, "COALESCE(balance_due, 0)", "status <> 'CANCELLED' AND COALESCE(balance_due, 0) > 0")));
        refundsLabel.setText("Refunds Today: " + CURRENCY.format(sumRefundsToday(conn)));
        lowStockLabel.setText("Low Stock Items: " + countLowStock(conn));
    }

    private int countOrders(Connection conn, String predicate) throws SQLException {
        String sql = "SELECT COUNT(*) FROM custom_orders WHERE " + withLocationPredicate(predicate);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindLocation(ps, 1);
            bindLocation(ps, 2);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private BigDecimal sumOrders(Connection conn, String expression, String predicate) throws SQLException {
        String sql = "SELECT COALESCE(SUM(" + expression + "), 0) FROM custom_orders WHERE " + withLocationPredicate(predicate);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindLocation(ps, 1);
            bindLocation(ps, 2);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? defaultZero(rs.getBigDecimal(1)) : BigDecimal.ZERO;
            }
        }
    }

    private BigDecimal sumRefundsToday(Connection conn) throws SQLException {
        String sql = """
                SELECT COALESCE(SUM(p.payment_amount), 0)
                FROM custom_order_payments p
                JOIN custom_orders co ON co.custom_order_id = p.custom_order_id
                WHERE p.payment_action = 'REFUND'
                  AND (p.created_at AT TIME ZONE ?) >= ?
                  AND (p.created_at AT TIME ZONE ?) < ?
                  AND (? IS NULL OR co.location_id = ?)
                """;
        LocalDate today = LocalDate.now(storeZone);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, storeZone.getId());
            ps.setTimestamp(2, Timestamp.valueOf(today.atStartOfDay()));
            ps.setString(3, storeZone.getId());
            ps.setTimestamp(4, Timestamp.valueOf(today.plusDays(1).atStartOfDay()));
            bindLocation(ps, 5);
            bindLocation(ps, 6);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? defaultZero(rs.getBigDecimal(1)) : BigDecimal.ZERO;
            }
        }
    }

    private int countLowStock(Connection conn) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM (
                    SELECT 1
                    FROM custom_order_items
                    WHERE is_active = TRUE
                      AND COALESCE(has_variants, FALSE) = FALSE
                      AND COALESCE(reorder_level, 0) > 0
                      AND COALESCE(quantity_on_hand, 0) <= COALESCE(reorder_level, 0)
                    UNION ALL
                    SELECT 1
                    FROM custom_order_item_variants v
                    JOIN custom_order_items i ON i.custom_item_id = v.custom_item_id
                    WHERE i.is_active = TRUE
                      AND v.is_active = TRUE
                      AND COALESCE(v.reorder_level, 0) > 0
                      AND COALESCE(v.quantity_on_hand, 0) <= COALESCE(v.reorder_level, 0)
                ) low_stock
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void loadActionQueue(Connection conn) throws SQLException {
        actionQueueModel.setRowCount(0);
        String sql = """
                SELECT custom_order_id, order_number, status, due_date, customer_name, customer_phone,
                       COALESCE(location_name, '') AS location_name,
                       assigned_to_user_id,
                       COALESCE(assigned_to_name, '') AS assigned_to_name,
                       COALESCE(balance_due, 0) AS balance_due
                FROM custom_orders
                WHERE status NOT IN ('DELIVERED', 'CANCELLED')
                  AND (
                      due_date <= CURRENT_DATE
                      OR status IN ('NEW', 'ASSIGNED', 'READY')
                      OR COALESCE(balance_due, 0) > 0
                  )
                  AND (? IS NULL OR location_id = ?)
                ORDER BY
                  CASE WHEN due_date < CURRENT_DATE THEN 0 WHEN due_date = CURRENT_DATE THEN 1 ELSE 2 END,
                  due_date NULLS LAST,
                  created_at DESC
                LIMIT 100
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindLocation(ps, 1);
            bindLocation(ps, 2);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    actionQueueModel.addRow(new Object[]{
                            rs.getLong("custom_order_id"),
                            rs.getString("order_number"),
                            rs.getString("status"),
                            rs.getDate("due_date"),
                            rs.getString("customer_name"),
                            rs.getString("customer_phone"),
                            rs.getString("location_name"),
                            rs.getString("assigned_to_name"),
                            CURRENCY.format(defaultZero(rs.getBigDecimal("balance_due")))
                    });
                }
            }
        }
    }

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

        String lockSql = """
                SELECT status, assigned_to_name, COALESCE(balance_due, 0) AS balance_due
                FROM custom_orders
                WHERE custom_order_id = ?
                  AND (? IS NULL OR location_id = ?)
                FOR UPDATE
                """;
        String updateSql = """
                UPDATE custom_orders
                SET assigned_to_user_id = ?, assigned_to_name = ?,
                    assigned_by_user_id = ?, assigned_by_name = ?,
                    assigned_at = CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE assigned_at END,
                    status = ?,
                    completed_at = CASE WHEN ? = 'COMPLETED' THEN CURRENT_TIMESTAMP ELSE completed_at END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_order_id = ?
                """;
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement lockPs = conn.prepareStatement(lockSql);
                 PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                lockPs.setLong(1, orderId);
                bindLocation(lockPs, 2);
                bindLocation(lockPs, 3);
                String oldStatus;
                String oldAssignedTo;
                BigDecimal balanceDue;
                try (ResultSet rs = lockPs.executeQuery()) {
                    if (!rs.next()) {
                        JOptionPane.showMessageDialog(this, "Order was not found for the selected store.");
                        conn.rollback();
                        return;
                    }
                    oldStatus = rs.getString("status");
                    oldAssignedTo = rs.getString("assigned_to_name");
                    balanceDue = defaultZero(rs.getBigDecimal("balance_due"));
                }
                if ("DELIVERED".equals(status) && balanceDue.compareTo(BigDecimal.ZERO) > 0) {
                    JOptionPane.showMessageDialog(this, "This order still has a balance due. Complete payment before marking it delivered.");
                    conn.rollback();
                    return;
                }
                if (assigned) {
                    updatePs.setInt(1, employee.userId());
                    updatePs.setString(2, employee.name());
                } else {
                    updatePs.setNull(1, Types.INTEGER);
                    updatePs.setNull(2, Types.VARCHAR);
                }
                bindNullableInteger(updatePs, 3, SessionManager.getCurrentUserId());
                updatePs.setString(4, SessionManager.getCurrentUserDisplayName());
                updatePs.setBoolean(5, assigned);
                updatePs.setString(6, status);
                updatePs.setString(7, status);
                updatePs.setLong(8, orderId);
                updatePs.executeUpdate();

                if (!safeText(oldStatus).equals(status)) {
                    CustomOrderAuditService.recordStatus(conn, orderId, oldStatus, status, "Updated from orders manager dashboard");
                }
                String newAssignedTo = assigned ? employee.name() : null;
                if (!safeText(oldAssignedTo).equals(safeText(newAssignedTo))) {
                    CustomOrderAuditService.recordAudit(conn, orderId, "ASSIGNMENT", "assigned_to_name", oldAssignedTo, newAssignedTo, "Assigned from orders manager dashboard");
                }
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
            loadDashboard();
            JOptionPane.showMessageDialog(this, "Order assignment saved.");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save assignment: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadExceptions(Connection conn) throws SQLException {
        exceptionModel.setRowCount(0);
        String sql = """
                SELECT p.created_at, 'REFUND' AS event_type, co.order_number, co.customer_name,
                       COALESCE(p.payment_amount, 0) AS amount,
                       COALESCE(p.taken_by_name, '') AS user_name,
                       COALESCE(p.void_reason, p.payment_reference, '') AS reason
                FROM custom_order_payments p
                JOIN custom_orders co ON co.custom_order_id = p.custom_order_id
                WHERE p.payment_action = 'REFUND'
                  AND p.created_at >= CURRENT_TIMESTAMP - INTERVAL '30 days'
                  AND (? IS NULL OR co.location_id = ?)
                UNION ALL
                SELECT co.cancelled_at AS created_at, 'CANCELLED' AS event_type, co.order_number, co.customer_name,
                       COALESCE(co.total_amount, 0) AS amount,
                       COALESCE(co.cancelled_by_name, '') AS user_name,
                       COALESCE(co.cancellation_reason, '') AS reason
                FROM custom_orders co
                WHERE co.status = 'CANCELLED'
                  AND co.cancelled_at >= CURRENT_TIMESTAMP - INTERVAL '30 days'
                  AND (? IS NULL OR co.location_id = ?)
                ORDER BY created_at DESC
                LIMIT 100
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindLocation(ps, 1);
            bindLocation(ps, 2);
            bindLocation(ps, 3);
            bindLocation(ps, 4);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    exceptionModel.addRow(new Object[]{
                            formatTimestamp(rs.getTimestamp("created_at")),
                            rs.getString("event_type"),
                            rs.getString("order_number"),
                            rs.getString("customer_name"),
                            CURRENCY.format(defaultZero(rs.getBigDecimal("amount"))),
                            rs.getString("user_name"),
                            rs.getString("reason")
                    });
                }
            }
        }
    }

    private void loadLowStock(Connection conn) throws SQLException {
        lowStockModel.setRowCount(0);
        String sql = """
                SELECT item_name, '' AS variant_name, quantity_on_hand, reorder_level
                FROM custom_order_items
                WHERE is_active = TRUE
                  AND COALESCE(has_variants, FALSE) = FALSE
                  AND COALESCE(reorder_level, 0) > 0
                  AND COALESCE(quantity_on_hand, 0) <= COALESCE(reorder_level, 0)
                UNION ALL
                SELECT i.item_name, v.variant_name, v.quantity_on_hand, v.reorder_level
                FROM custom_order_item_variants v
                JOIN custom_order_items i ON i.custom_item_id = v.custom_item_id
                WHERE i.is_active = TRUE
                  AND v.is_active = TRUE
                  AND COALESCE(v.reorder_level, 0) > 0
                  AND COALESCE(v.quantity_on_hand, 0) <= COALESCE(v.reorder_level, 0)
                ORDER BY item_name, variant_name
                LIMIT 100
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                BigDecimal qty = defaultZero(rs.getBigDecimal("quantity_on_hand"));
                BigDecimal reorder = defaultZero(rs.getBigDecimal("reorder_level"));
                lowStockModel.addRow(new Object[]{
                        rs.getString("item_name"),
                        rs.getString("variant_name"),
                        qty,
                        reorder,
                        qty.compareTo(BigDecimal.ZERO) <= 0 ? "Out" : "Low"
                });
            }
        }
    }

    private void loadAuditLog(Connection conn) throws SQLException {
        auditModel.setRowCount(0);
        String sql = """
                SELECT a.created_at, co.order_number, a.action_type, COALESCE(a.field_name, '') AS field_name,
                       COALESCE(a.old_value, '') AS old_value,
                       COALESCE(a.new_value, '') AS new_value,
                       COALESCE(a.user_name, '') AS user_name,
                       COALESCE(a.device_name, a.device_id, '') AS device_name,
                       COALESCE(a.reason, '') AS reason
                FROM custom_order_audit_log a
                JOIN custom_orders co ON co.custom_order_id = a.custom_order_id
                WHERE (? IS NULL OR co.location_id = ?)
                ORDER BY a.created_at DESC
                LIMIT 150
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindLocation(ps, 1);
            bindLocation(ps, 2);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    auditModel.addRow(new Object[]{
                            formatTimestamp(rs.getTimestamp("created_at")),
                            rs.getString("order_number"),
                            rs.getString("action_type"),
                            rs.getString("field_name"),
                            rs.getString("old_value"),
                            rs.getString("new_value"),
                            rs.getString("user_name"),
                            rs.getString("device_name"),
                            rs.getString("reason")
                    });
                }
            }
        }
    }

    private String withLocationPredicate(String predicate) {
        return "(" + predicate + ") AND (? IS NULL OR location_id = ?)";
    }

    private void bindLocation(PreparedStatement ps, int index) throws SQLException {
        Integer locationId = SessionManager.getCurrentLocationId();
        if (locationId == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, locationId);
        }
    }

    private void bindNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
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
