package ui.screens;

import data.DB;
import managers.SessionManager;
import ui.components.AppMenuBar;
import ui.helpers.StoreTimeZoneHelper;
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

public class EndOfDay extends JFrame {
    private final JTextField fromField = new JTextField();
    private final JTextField toField = new JTextField();
    private final JComboBox<FilterOption> employeeBox = new JComboBox<>();
    private final JComboBox<FilterOption> deviceBox = new JComboBox<>();
    private final JLabel storeLabel = new JLabel();
    private final JLabel transactionsLabel = metricLabel();
    private final JLabel totalSalesLabel = metricLabel();
    private final JLabel returnsLabel = metricLabel();
    private final JLabel netSalesLabel = metricLabel();
    private final JLabel paidLabel = metricLabel();
    private final JLabel unpaidLabel = metricLabel();
    private final JLabel cashLabel = metricLabel();
    private final JLabel cardLabel = metricLabel();
    private final JLabel accountLabel = metricLabel();
    private final DefaultTableModel salesModel;
    private ZoneId storeZone = resolveStoreZone();
    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(Locale.US);

    public EndOfDay() {
        setTitle("End of Day");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(14, 14));
        setJMenuBar(AppMenuBar.create(this, "EndOfDay"));

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.setBackground(new Color(245, 247, 250));

        salesModel = new DefaultTableModel(
                new Object[]{"Sale ID", "Receipt", "Time", "Employee", "Device", "Payment", "Status", "Paid", "Total"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable salesTable = new JTable(salesModel);
        salesTable.setRowHeight(27);

        root.add(buildHeaderPanel(), BorderLayout.NORTH);
        root.add(buildCenterPanel(salesTable), BorderLayout.CENTER);
        add(root, BorderLayout.CENTER);

        setDefaultRange();
        loadFilters();
        loadReport();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout(0, 12));
        headerPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("End of Day");
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

        JButton runButton = new JButton("Run Report");
        JButton todayButton = new JButton("Today");

        addFilter(filterPanel, 0, "From", fromField, 190);
        addFilter(filterPanel, 2, "To", toField, 190);
        addFilter(filterPanel, 4, "Employee", employeeBox, 220);
        addFilter(filterPanel, 6, "Device", deviceBox, 180);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 8;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 10, 0, 0);
        filterPanel.add(runButton, gbc);
        gbc.gridx = 9;
        filterPanel.add(todayButton, gbc);

        runButton.addActionListener(e -> loadReport());
        todayButton.addActionListener(e -> {
            setDefaultRange();
            employeeBox.setSelectedIndex(0);
            deviceBox.setSelectedIndex(0);
            loadReport();
        });
        fromField.addActionListener(e -> loadReport());
        toField.addActionListener(e -> loadReport());
        employeeBox.addActionListener(e -> loadReport());
        deviceBox.addActionListener(e -> loadReport());

        headerPanel.add(titleRow, BorderLayout.NORTH);
        headerPanel.add(filterPanel, BorderLayout.CENTER);
        return headerPanel;
    }

    private JPanel buildCenterPanel(JTable salesTable) {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);

        JPanel metricsPanel = new JPanel(new GridLayout(1, 9, 10, 0));
        metricsPanel.setOpaque(false);
        metricsPanel.add(transactionsLabel);
        metricsPanel.add(totalSalesLabel);
        metricsPanel.add(returnsLabel);
        metricsPanel.add(netSalesLabel);
        metricsPanel.add(paidLabel);
        metricsPanel.add(unpaidLabel);
        metricsPanel.add(cashLabel);
        metricsPanel.add(cardLabel);
        metricsPanel.add(accountLabel);

        panel.add(metricsPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(salesTable), BorderLayout.CENTER);
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

        Integer locationId = SessionManager.getCurrentLocationId();
        if (locationId == null) {
            return;
        }

        String employeeSql = """
                SELECT DISTINCT user_id, employee_name
                FROM (
                    SELECT s.user_id, COALESCE(s.user_name, u.full_name, u.username, 'Unknown') AS employee_name
                    FROM sales s
                    LEFT JOIN users u ON u.user_id = s.user_id
                    WHERE s.location_id = ?
                    UNION
                    SELECT sr.user_id, COALESCE(sr.user_name, u.full_name, u.username, 'Unknown') AS employee_name
                    FROM sale_returns sr
                    LEFT JOIN users u ON u.user_id = sr.user_id
                    WHERE sr.location_id = ?
                ) employee_filter
                ORDER BY employee_name
                """;
        String deviceSql = """
                SELECT DISTINCT device_id
                FROM (
                    SELECT COALESCE(receipt_device_id, '') AS device_id
                    FROM sales
                    WHERE location_id = ?
                    UNION
                    SELECT COALESCE(device_id, '') AS device_id
                    FROM sale_returns
                    WHERE location_id = ?
                ) device_filter
                WHERE device_id <> ''
                ORDER BY device_id
                """;

        try (Connection conn = DB.getConnection();
            PreparedStatement employeePs = conn.prepareStatement(employeeSql);
             PreparedStatement devicePs = conn.prepareStatement(deviceSql)) {
            employeePs.setInt(1, locationId);
            employeePs.setInt(2, locationId);
            try (ResultSet rs = employeePs.executeQuery()) {
                while (rs.next()) {
                    employeeBox.addItem(new FilterOption(rs.getObject("user_id"), rs.getString("employee_name")));
                }
            }

            devicePs.setInt(1, locationId);
            devicePs.setInt(2, locationId);
            try (ResultSet rs = devicePs.executeQuery()) {
                while (rs.next()) {
                    String deviceId = rs.getString("device_id");
                    deviceBox.addItem(new FilterOption(deviceId, deviceId));
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load filters: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadReport() {
        salesModel.setRowCount(0);
        storeZone = resolveStoreZone();
        updateStoreLabel();

        Integer locationId = SessionManager.getCurrentLocationId();
        if (locationId == null) {
            JOptionPane.showMessageDialog(this, "No store is selected for this session.");
            return;
        }

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
                SELECT s.sale_id,
                       COALESCE(s.receipt_number, '') AS receipt_number,
                       s.created_at,
                       (s.created_at AT TIME ZONE ?) AS local_created_at,
                       COALESCE(s.user_name, u.full_name, u.username, 'Unknown') AS employee_name,
                       COALESCE(s.receipt_device_id, '') AS device_id,
                       COALESCE(s.payment_method, '') AS payment_method,
                       COALESCE(s.payment_status, 'PAID') AS payment_status,
                       COALESCE(s.amount_paid, 0) AS amount_paid,
                       COALESCE(s.total_amount, 0) AS total_amount
                FROM sales s
                LEFT JOIN users u ON u.user_id = s.user_id
                WHERE s.location_id = ?
                  AND (s.created_at AT TIME ZONE ?) >= ?
                  AND (s.created_at AT TIME ZONE ?) < ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(storeZone.getId());
        parameters.add(locationId);
        parameters.add(storeZone.getId());
        parameters.add(Timestamp.valueOf(from.toLocalDateTime()));
        parameters.add(storeZone.getId());
        parameters.add(Timestamp.valueOf(to.toLocalDateTime()));

        FilterOption employee = (FilterOption) employeeBox.getSelectedItem();
        if (employee != null && employee.value() != null) {
            sql.append(" AND s.user_id = ?");
            parameters.add(employee.value());
        }

        FilterOption device = (FilterOption) deviceBox.getSelectedItem();
        if (device != null && device.value() != null) {
            sql.append(" AND COALESCE(s.receipt_device_id, '') = ?");
            parameters.add(device.value());
        }

        sql.append(" ORDER BY s.created_at ASC, s.sale_id ASC");

        int transactions = 0;
        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal returns = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal card = BigDecimal.ZERO;
        BigDecimal account = BigDecimal.ZERO;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < parameters.size(); i++) {
                ps.setObject(i + 1, parameters.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    transactions++;
                    BigDecimal amountPaid = defaultZero(rs.getBigDecimal("amount_paid"));
                    BigDecimal totalAmount = defaultZero(rs.getBigDecimal("total_amount"));
                    String paymentMethod = rs.getString("payment_method");
                    totalSales = totalSales.add(totalAmount);
                    paid = paid.add(amountPaid);
                    if ("CASH".equalsIgnoreCase(paymentMethod)) {
                        cash = cash.add(amountPaid);
                    } else if ("CARD".equalsIgnoreCase(paymentMethod) || "CHEQUE".equalsIgnoreCase(paymentMethod)) {
                        card = card.add(amountPaid);
                    } else if ("ACCOUNT".equalsIgnoreCase(paymentMethod)) {
                        account = account.add(totalAmount.subtract(amountPaid));
                    }

                    salesModel.addRow(new Object[]{
                            rs.getInt("sale_id"),
                            rs.getString("receipt_number"),
                            formatLocalTime(rs.getTimestamp("local_created_at")),
                            rs.getString("employee_name"),
                            rs.getString("device_id"),
                            paymentMethod,
                            rs.getString("payment_status"),
                            CURRENCY.format(amountPaid),
                            CURRENCY.format(totalAmount)
                    });
                }
            }

            returns = loadReturnTotal(conn, locationId, from, to, employee, device);

            transactionsLabel.setText("Transactions: " + transactions);
            totalSalesLabel.setText("Total Sales: " + CURRENCY.format(totalSales));
            returnsLabel.setText("Returns: " + CURRENCY.format(returns));
            netSalesLabel.setText("Net Sales: " + CURRENCY.format(totalSales.subtract(returns)));
            paidLabel.setText("Paid: " + CURRENCY.format(paid));
            unpaidLabel.setText("Unpaid: " + CURRENCY.format(totalSales.subtract(paid)));
            cashLabel.setText("Cash: " + CURRENCY.format(cash));
            cardLabel.setText("Card/Check: " + CURRENCY.format(card));
            accountLabel.setText("Account: " + CURRENCY.format(account));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load end of day report: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private BigDecimal loadReturnTotal(Connection conn, int locationId, ZonedDateTime from, ZonedDateTime to, FilterOption employee, FilterOption device) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT COALESCE(SUM(refund_amount), 0) AS return_total
                FROM sale_returns
                WHERE location_id = ?
                  AND (created_at AT TIME ZONE ?) >= ?
                  AND (created_at AT TIME ZONE ?) < ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(locationId);
        parameters.add(storeZone.getId());
        parameters.add(Timestamp.valueOf(from.toLocalDateTime()));
        parameters.add(storeZone.getId());
        parameters.add(Timestamp.valueOf(to.toLocalDateTime()));

        if (employee != null && employee.value() != null) {
            sql.append(" AND user_id = ?");
            parameters.add(employee.value());
        }
        if (device != null && device.value() != null) {
            sql.append(" AND COALESCE(device_id, '') = ?");
            parameters.add(device.value());
        }

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < parameters.size(); i++) {
                ps.setObject(i + 1, parameters.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return defaultZero(rs.getBigDecimal("return_total"));
                }
            }
        }
        return BigDecimal.ZERO;
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
        label.setBackground(Color.WHITE);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
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
}
