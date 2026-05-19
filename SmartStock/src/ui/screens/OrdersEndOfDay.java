package ui.screens;

import data.DB;
import managers.SessionManager;
import ui.components.AppMenuBar;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.ThemeManager;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrdersEndOfDay extends JFrame {
    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(Locale.US);

    private final JTextField fromField = new JTextField();
    private final JTextField toField = new JTextField();
    private final JComboBox<FilterOption> employeeBox = new JComboBox<>();
    private final JComboBox<FilterOption> deviceBox = new JComboBox<>();
    private final JComboBox<FilterOption> paymentBox = new JComboBox<>();
    private final JLabel storeLabel = new JLabel();
    private final JLabel ordersLabel = metricLabel();
    private final JLabel orderTotalLabel = metricLabel();
    private final JLabel collectedLabel = metricLabel();
    private final JLabel cashLabel = metricLabel();
    private final JLabel cardLabel = metricLabel();
    private final JLabel chequeLabel = metricLabel();
    private final JLabel mmgLabel = metricLabel();
    private final JLabel accountLabel = metricLabel();
    private final JLabel balanceLabel = metricLabel();
    private final JLabel returnsLabel = metricLabel();
    private final JLabel refundPayoutLabel = metricLabel();
    private final JLabel readyLabel = metricLabel();
    private final JLabel deliveredLabel = metricLabel();
    private final DefaultTableModel paymentModel;
    private ZoneId storeZone = resolveStoreZone();

    public OrdersEndOfDay() {
        setTitle("Orders End Of Day");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(14, 14));
        setJMenuBar(AppMenuBar.create(this, "OrdersEndOfDay"));

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.setBackground(new Color(245, 247, 250));

        paymentModel = new DefaultTableModel(
                new Object[]{"Payment ID", "Order #", "Time", "Customer", "Employee", "Device", "Method", "Reference", "Amount", "Order Total", "Balance", "Status"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable paymentTable = new JTable(paymentModel);
        paymentTable.setRowHeight(27);

        root.add(buildHeaderPanel(), BorderLayout.NORTH);
        root.add(buildCenterPanel(paymentTable), BorderLayout.CENTER);
        add(root, BorderLayout.CENTER);

        setDefaultRange();
        loadFilters();
        loadReport();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout(0, 12));
        headerPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("Orders End Of Day");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setForeground(new Color(31, 41, 55));
        updateStoreLabel();

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(titleLabel, BorderLayout.WEST);
        titleRow.add(storeLabel, BorderLayout.EAST);

        JPanel filterPanel = new JPanel(new GridBagLayout());
        filterPanel.setBackground(Color.WHITE);
        filterPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(12, 12, 12, 12)
        ));

        paymentBox.addItem(new FilterOption(null, "All Methods"));
        paymentBox.addItem(new FilterOption("CASH", "Cash"));
        paymentBox.addItem(new FilterOption("CARD", "Card"));
        paymentBox.addItem(new FilterOption("CHEQUE", "Cheque"));
        paymentBox.addItem(new FilterOption("MMG", "MMG"));
        paymentBox.addItem(new FilterOption("ACCOUNT", "Account"));

        JButton runButton = new JButton("Run Report");
        JButton todayButton = new JButton("Today");

        addFilter(filterPanel, 0, "From", fromField, 190);
        addFilter(filterPanel, 2, "To", toField, 190);
        addFilter(filterPanel, 4, "Employee", employeeBox, 220);
        addFilter(filterPanel, 6, "Device", deviceBox, 180);
        addFilter(filterPanel, 8, "Payment", paymentBox, 160);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 10;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 10, 0, 0);
        filterPanel.add(runButton, gbc);
        gbc.gridx = 11;
        filterPanel.add(todayButton, gbc);

        runButton.addActionListener(e -> loadReport());
        todayButton.addActionListener(e -> {
            setDefaultRange();
            employeeBox.setSelectedIndex(0);
            deviceBox.setSelectedIndex(0);
            paymentBox.setSelectedIndex(0);
            loadReport();
        });
        fromField.addActionListener(e -> loadReport());
        toField.addActionListener(e -> loadReport());
        employeeBox.addActionListener(e -> loadReport());
        deviceBox.addActionListener(e -> loadReport());
        paymentBox.addActionListener(e -> loadReport());

        headerPanel.add(titleRow, BorderLayout.NORTH);
        headerPanel.add(filterPanel, BorderLayout.CENTER);
        return headerPanel;
    }

    private JPanel buildCenterPanel(JTable table) {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);

        JPanel metricsPanel = new JPanel(new GridLayout(3, 4, 10, 10));
        metricsPanel.setOpaque(false);
        metricsPanel.add(ordersLabel);
        metricsPanel.add(orderTotalLabel);
        metricsPanel.add(collectedLabel);
        metricsPanel.add(cashLabel);
        metricsPanel.add(cardLabel);
        metricsPanel.add(chequeLabel);
        metricsPanel.add(mmgLabel);
        metricsPanel.add(accountLabel);
        metricsPanel.add(balanceLabel);
        metricsPanel.add(returnsLabel);
        metricsPanel.add(refundPayoutLabel);
        metricsPanel.add(readyLabel);
        metricsPanel.add(deliveredLabel);

        panel.add(metricsPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private void addFilter(JPanel panel, int x, String label, JComponent field, int width) {
        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.gridx = x;
        labelGbc.gridy = 0;
        labelGbc.insets = new Insets(0, 0, 0, 6);
        panel.add(new JLabel(label + ":"), labelGbc);

        field.setPreferredSize(new Dimension(width, 30));
        GridBagConstraints fieldGbc = new GridBagConstraints();
        fieldGbc.gridx = x + 1;
        fieldGbc.gridy = 0;
        fieldGbc.insets = new Insets(0, 0, 0, 8);
        panel.add(field, fieldGbc);
    }

    private void setDefaultRange() {
        storeZone = resolveStoreZone();
        LocalDate today = LocalDate.now(storeZone);
        fromField.setText(today.atStartOfDay().format(INPUT_FORMAT));
        toField.setText(today.plusDays(1).atStartOfDay().format(INPUT_FORMAT));
    }

    private void loadFilters() {
        employeeBox.removeAllItems();
        deviceBox.removeAllItems();
        employeeBox.addItem(new FilterOption(null, "All Employees"));
        deviceBox.addItem(new FilterOption(null, "All Devices"));
        String employeeSql = """
                SELECT DISTINCT taken_by_user_id, COALESCE(taken_by_name, u.full_name, u.username, 'Unknown') AS employee_name
                FROM custom_order_payments p
                LEFT JOIN users u ON u.user_id = p.taken_by_user_id
                WHERE p.created_at >= CURRENT_TIMESTAMP - INTERVAL '90 days'
                ORDER BY employee_name
                """;
        String deviceSql = """
                SELECT DISTINCT device_name
                FROM (
                    SELECT COALESCE(p.device_name, p.device_id, '') AS device_name
                    FROM custom_order_payments p
                    WHERE p.created_at >= CURRENT_TIMESTAMP - INTERVAL '90 days'
                    UNION
                    SELECT COALESCE(co.device_name, co.device_id, '') AS device_name
                    FROM custom_orders co
                    WHERE co.created_at >= CURRENT_TIMESTAMP - INTERVAL '90 days'
                    UNION
                    SELECT COALESCE(a.device_name, a.device_id, '') AS device_name
                    FROM custom_order_audit_log a
                    WHERE a.created_at >= CURRENT_TIMESTAMP - INTERVAL '90 days'
                    UNION
                    SELECT COALESCE(r.device_name, r.device_id, '') AS device_name
                    FROM custom_order_line_returns r
                    WHERE r.created_at >= CURRENT_TIMESTAMP - INTERVAL '90 days'
                    UNION
                    SELECT COALESCE(d.device_name, d.device_id, '') AS device_name
                    FROM custom_order_line_deliveries d
                    WHERE d.delivered_at >= CURRENT_TIMESTAMP - INTERVAL '90 days'
                ) devices
                WHERE device_name <> ''
                ORDER BY device_name
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement employeePs = conn.prepareStatement(employeeSql);
             PreparedStatement devicePs = conn.prepareStatement(deviceSql)) {
            try (ResultSet rs = employeePs.executeQuery()) {
                while (rs.next()) {
                    employeeBox.addItem(new FilterOption(rs.getObject("taken_by_user_id"), rs.getString("employee_name")));
                }
            }
            try (ResultSet rs = devicePs.executeQuery()) {
                while (rs.next()) {
                    String deviceName = rs.getString("device_name");
                    deviceBox.addItem(new FilterOption(deviceName, deviceName));
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load order filters: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadReport() {
        paymentModel.setRowCount(0);
        storeZone = resolveStoreZone();
        updateStoreLabel();

        ZonedDateTime from = parseDateTime(fromField.getText().trim(), "From");
        ZonedDateTime to = parseDateTime(toField.getText().trim(), "To");
        if (from == null || to == null) {
            return;
        }
        if (!to.isAfter(from)) {
            JOptionPane.showMessageDialog(this, "To must be after From.");
            return;
        }

        StringBuilder sql = new StringBuilder("""
                SELECT p.custom_order_payment_id,
                       co.order_number,
                       (p.created_at AT TIME ZONE ?) AS local_created_at,
                       co.customer_name,
                       COALESCE(p.taken_by_name, u.full_name, u.username, 'Unknown') AS employee_name,
                       COALESCE(p.device_name, co.device_name, p.device_id, co.device_id, '') AS device_name,
                       p.payment_method,
                       COALESCE(p.payment_reference, '') AS payment_reference,
                       COALESCE(p.payment_action, 'PAYMENT') AS payment_action,
                       COALESCE(p.payment_amount, 0) AS payment_amount,
                       COALESCE(co.total_amount, 0) AS total_amount,
                       COALESCE(co.amount_paid, 0) AS amount_paid,
                       COALESCE(co.balance_due, 0) AS balance_due,
                       COALESCE(co.status, '') AS status,
                       COALESCE(co.payment_status, '') AS payment_status
                FROM custom_order_payments p
                JOIN custom_orders co ON co.custom_order_id = p.custom_order_id
                LEFT JOIN users u ON u.user_id = p.taken_by_user_id
                WHERE (p.created_at AT TIME ZONE ?) >= ?
                  AND (p.created_at AT TIME ZONE ?) < ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(storeZone.getId());
        parameters.add(storeZone.getId());
        parameters.add(Timestamp.valueOf(from.toLocalDateTime()));
        parameters.add(storeZone.getId());
        parameters.add(Timestamp.valueOf(to.toLocalDateTime()));

        Integer locationId = SessionManager.getCurrentLocationId();
        if (locationId != null) {
            sql.append(" AND co.location_id = ?");
            parameters.add(locationId);
        }
        FilterOption employee = (FilterOption) employeeBox.getSelectedItem();
        if (employee != null && employee.value() != null) {
            sql.append(" AND p.taken_by_user_id = ?");
            parameters.add(employee.value());
        }
        FilterOption device = (FilterOption) deviceBox.getSelectedItem();
        if (device != null && device.value() != null) {
            sql.append(" AND COALESCE(p.device_name, co.device_name, p.device_id, co.device_id, '') = ?");
            parameters.add(device.value());
        }
        FilterOption payment = (FilterOption) paymentBox.getSelectedItem();
        if (payment != null && payment.value() != null) {
            sql.append(" AND p.payment_method = ?");
            parameters.add(payment.value());
        }
        sql.append(" ORDER BY p.created_at ASC, p.custom_order_payment_id ASC");

        int payments = 0;
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal card = BigDecimal.ZERO;
        BigDecimal cheque = BigDecimal.ZERO;
        BigDecimal mmg = BigDecimal.ZERO;
        BigDecimal account = BigDecimal.ZERO;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < parameters.size(); i++) {
                ps.setObject(i + 1, parameters.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    payments++;
                    String action = rs.getString("payment_action");
                    BigDecimal signedAmount = defaultZero(rs.getBigDecimal("payment_amount"));
                    if ("REFUND".equals(action) || "REVERSAL".equals(action)) {
                        signedAmount = signedAmount.negate();
                    }
                    collected = collected.add(signedAmount);
                    String method = rs.getString("payment_method");
                    if ("CASH".equalsIgnoreCase(method)) {
                        cash = cash.add(signedAmount);
                    } else if ("CARD".equalsIgnoreCase(method)) {
                        card = card.add(signedAmount);
                    } else if ("CHEQUE".equalsIgnoreCase(method)) {
                        cheque = cheque.add(signedAmount);
                    } else if ("MMG".equalsIgnoreCase(method)) {
                        mmg = mmg.add(signedAmount);
                    } else if ("ACCOUNT".equalsIgnoreCase(method)) {
                        account = account.add(signedAmount);
                    }
                    paymentModel.addRow(new Object[]{
                            rs.getLong("custom_order_payment_id"),
                            rs.getString("order_number"),
                            formatLocalTime(rs.getTimestamp("local_created_at")),
                            rs.getString("customer_name"),
                            rs.getString("employee_name"),
                            rs.getString("device_name"),
                            method,
                            rs.getString("payment_reference"),
                            CURRENCY.format(signedAmount),
                            CURRENCY.format(defaultZero(rs.getBigDecimal("total_amount"))),
                            CURRENCY.format(defaultZero(rs.getBigDecimal("balance_due"))),
                            rs.getString("status") + " / " + rs.getString("payment_status")
                    });
                }
            }

            OrderSummary summary = loadOrderSummary(conn, from, to, device);
            ReturnSummary returnSummary = loadReturnSummary(conn, from, to, employee, device);
            ordersLabel.setText("Orders: " + summary.orderCount());
            orderTotalLabel.setText("Order Total: " + CURRENCY.format(summary.orderTotal()));
            collectedLabel.setText("Collected: " + CURRENCY.format(collected) + " (" + payments + ")");
            cashLabel.setText("Cash: " + CURRENCY.format(cash));
            cardLabel.setText("Card: " + CURRENCY.format(card));
            chequeLabel.setText("Cheque: " + CURRENCY.format(cheque));
            mmgLabel.setText("MMG: " + CURRENCY.format(mmg));
            accountLabel.setText("Account: " + CURRENCY.format(account));
            balanceLabel.setText("Balance Due: " + CURRENCY.format(summary.balanceDue()));
            returnsLabel.setText("Line Returns: " + CURRENCY.format(returnSummary.adjustmentTotal()) + " (" + returnSummary.returnCount() + ")");
            refundPayoutLabel.setText("Refund Payouts: " + CURRENCY.format(returnSummary.payoutTotal()));
            readyLabel.setText("Ready: " + summary.readyCount());
            deliveredLabel.setText("Delivered: " + summary.deliveredCount());
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load orders end of day: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private OrderSummary loadOrderSummary(Connection conn, ZonedDateTime from, ZonedDateTime to, FilterOption device) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) AS order_count,
                       COALESCE(SUM(total_amount), 0) AS order_total,
                       COALESCE(SUM(balance_due), 0) AS balance_due,
                       COUNT(*) FILTER (WHERE status = 'READY') AS ready_count,
                       COUNT(*) FILTER (WHERE status = 'DELIVERED') AS delivered_count
                FROM custom_orders
                WHERE (created_at AT TIME ZONE ?) >= ?
                  AND (created_at AT TIME ZONE ?) < ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(storeZone.getId());
        parameters.add(Timestamp.valueOf(from.toLocalDateTime()));
        parameters.add(storeZone.getId());
        parameters.add(Timestamp.valueOf(to.toLocalDateTime()));
        Integer locationId = SessionManager.getCurrentLocationId();
        if (locationId != null) {
            sql.append(" AND location_id = ?");
            parameters.add(locationId);
        }
        if (device != null && device.value() != null) {
            sql.append(" AND COALESCE(device_name, device_id, '') = ?");
            parameters.add(device.value());
        }
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < parameters.size(); i++) {
                ps.setObject(i + 1, parameters.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new OrderSummary(
                            rs.getInt("order_count"),
                            defaultZero(rs.getBigDecimal("order_total")),
                            defaultZero(rs.getBigDecimal("balance_due")),
                            rs.getInt("ready_count"),
                            rs.getInt("delivered_count")
                    );
                }
            }
        }
        return new OrderSummary(0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
    }

    private ReturnSummary loadReturnSummary(Connection conn, ZonedDateTime from, ZonedDateTime to, FilterOption employee, FilterOption device) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) AS return_count,
                       COALESCE(SUM(r.refund_amount), 0) AS adjustment_total,
                       COALESCE(SUM(r.balance_reduction), 0) AS balance_reduction_total,
                       COALESCE(SUM(r.payout_amount), 0) AS payout_total
                FROM custom_order_line_returns r
                JOIN custom_orders co ON co.custom_order_id = r.custom_order_id
                WHERE (r.created_at AT TIME ZONE ?) >= ?
                  AND (r.created_at AT TIME ZONE ?) < ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(storeZone.getId());
        parameters.add(Timestamp.valueOf(from.toLocalDateTime()));
        parameters.add(storeZone.getId());
        parameters.add(Timestamp.valueOf(to.toLocalDateTime()));
        Integer locationId = SessionManager.getCurrentLocationId();
        if (locationId != null) {
            sql.append(" AND co.location_id = ?");
            parameters.add(locationId);
        }
        if (employee != null && employee.value() != null) {
            sql.append(" AND r.created_by_user_id = ?");
            parameters.add(employee.value());
        }
        if (device != null && device.value() != null) {
            sql.append(" AND COALESCE(r.device_name, r.device_id, '') = ?");
            parameters.add(device.value());
        }
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < parameters.size(); i++) {
                ps.setObject(i + 1, parameters.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ReturnSummary(
                            rs.getInt("return_count"),
                            defaultZero(rs.getBigDecimal("adjustment_total")),
                            defaultZero(rs.getBigDecimal("balance_reduction_total")),
                            defaultZero(rs.getBigDecimal("payout_total"))
                    );
                }
            }
        }
        return new ReturnSummary(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private ZonedDateTime parseDateTime(String value, String label) {
        try {
            return LocalDateTime.parse(value, INPUT_FORMAT).atZone(storeZone);
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, label + " must use YYYY-MM-DD HH:MM in this store timezone (" + storeZone + ").");
            return null;
        }
    }

    private String formatLocalTime(Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        return StoreTimeZoneHelper.formatLocalTimestamp(timestamp, DISPLAY_FORMAT);
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

    private record FilterOption(Object value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record OrderSummary(int orderCount, BigDecimal orderTotal, BigDecimal balanceDue, int readyCount, int deliveredCount) {
    }

    private record ReturnSummary(int returnCount, BigDecimal adjustmentTotal, BigDecimal balanceReductionTotal, BigDecimal payoutTotal) {
    }
}
