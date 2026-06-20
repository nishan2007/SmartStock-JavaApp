package ui.screens;

import utils.CurrencyFormatter;
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

public class Reports extends JFrame {
    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");
    private static final NumberFormat CURRENCY = CurrencyFormatter.create(Locale.US);

    private final JTextField fromField = new JTextField();
    private final JTextField toField = new JTextField();
    private final JLabel storeLabel = new JLabel();

    private final DefaultTableModel salesModel = readOnlyModel(
            "Sale ID", "Receipt", "Time", "Employee", "Device", "Drawer", "Payment", "Status", "Paid", "Total"
    );
    private final DefaultTableModel orderModel = readOnlyModel(
            "Payment ID", "Order #", "Time", "Customer", "Employee", "Device", "Drawer", "Method", "Amount", "Order Total", "Balance", "Status"
    );
    private final DefaultTableModel invoiceModel = readOnlyModel(
            "Order ID", "Order #", "Date", "Customer", "Status", "Payment", "Total", "Paid", "Balance", "Created By", "Device", "Drawer"
    );

    private final JLabel salesTransactionsLabel = metricLabel();
    private final JLabel salesGrossLabel = metricLabel();
    private final JLabel salesReturnsLabel = metricLabel();
    private final JLabel salesNetLabel = metricLabel();
    private final JLabel salesPaidLabel = metricLabel();
    private final JLabel salesUnpaidLabel = metricLabel();
    private final JLabel salesCashLabel = metricLabel();
    private final JLabel salesCardLabel = metricLabel();
    private final JLabel salesMmgLabel = metricLabel();
    private final JLabel salesAccountLabel = metricLabel();

    private final JLabel orderPaymentsLabel = metricLabel();
    private final JLabel orderTotalLabel = metricLabel();
    private final JLabel orderCollectedLabel = metricLabel();
    private final JLabel orderBalanceLabel = metricLabel();
    private final JLabel orderCashLabel = metricLabel();
    private final JLabel orderCardLabel = metricLabel();
    private final JLabel orderChequeLabel = metricLabel();
    private final JLabel orderMmgLabel = metricLabel();
    private final JLabel orderAccountLabel = metricLabel();
    private final JLabel orderReturnsLabel = metricLabel();

    private final JLabel invoiceCountLabel = metricLabel();
    private final JLabel invoiceTotalLabel = metricLabel();
    private final JLabel invoicePaidLabel = metricLabel();
    private final JLabel invoiceBalanceLabel = metricLabel();
    private final JLabel invoiceOpenLabel = metricLabel();
    private final JLabel invoiceDeliveredLabel = metricLabel();
    private final JLabel invoiceCashLabel = metricLabel();
    private final JLabel invoiceCardLabel = metricLabel();
    private final JLabel invoiceChequeLabel = metricLabel();
    private final JLabel invoiceMmgLabel = metricLabel();

    private ZoneId storeZone = resolveStoreZone();

    public Reports() {
        setTitle("Reports");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(14, 14));
        setJMenuBar(AppMenuBar.create(this, "Reports"));

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.setBackground(new Color(245, 247, 250));

        root.add(buildHeaderPanel(), BorderLayout.NORTH);
        root.add(buildTabs(), BorderLayout.CENTER);
        add(root, BorderLayout.CENTER);

        setDefaultRange();
        loadReports();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout(0, 12));
        headerPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("Reports");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setForeground(new Color(31, 41, 55));
        updateStoreLabel();

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(titleLabel, BorderLayout.WEST);
        storeLabel.setForeground(new Color(75, 85, 99));
        titleRow.add(storeLabel, BorderLayout.EAST);

        JPanel filterPanel = new JPanel(new GridBagLayout());
        filterPanel.setBackground(Color.WHITE);
        filterPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(12, 12, 12, 12)
        ));

        JButton runButton = new JButton("Run Reports");
        JButton todayButton = new JButton("Today");
        addFilter(filterPanel, 0, "From", fromField, 190);
        addFilter(filterPanel, 2, "To", toField, 190);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 4;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 10, 0, 0);
        filterPanel.add(runButton, gbc);
        gbc.gridx = 5;
        filterPanel.add(todayButton, gbc);

        runButton.addActionListener(e -> loadReports());
        todayButton.addActionListener(e -> {
            setDefaultRange();
            loadReports();
        });
        fromField.addActionListener(e -> loadReports());
        toField.addActionListener(e -> loadReports());

        headerPanel.add(titleRow, BorderLayout.NORTH);
        headerPanel.add(filterPanel, BorderLayout.CENTER);
        return headerPanel;
    }

    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Sales", reportPanel(
                metricsPanel(salesTransactionsLabel, salesGrossLabel, salesReturnsLabel, salesNetLabel, salesPaidLabel,
                        salesUnpaidLabel, salesCashLabel, salesCardLabel, salesMmgLabel, salesAccountLabel),
                new JTable(salesModel)
        ));
        tabs.addTab("Orders", reportPanel(
                metricsPanel(orderPaymentsLabel, orderTotalLabel, orderCollectedLabel, orderBalanceLabel, orderCashLabel,
                        orderCardLabel, orderChequeLabel, orderMmgLabel, orderAccountLabel, orderReturnsLabel),
                new JTable(orderModel)
        ));
        tabs.addTab("Invoices", reportPanel(
                metricsPanel(invoiceCountLabel, invoiceTotalLabel, invoicePaidLabel, invoiceBalanceLabel,
                        invoiceOpenLabel, invoiceDeliveredLabel, invoiceCashLabel, invoiceCardLabel,
                        invoiceChequeLabel, invoiceMmgLabel),
                new JTable(invoiceModel)
        ));
        return tabs;
    }

    private JPanel reportPanel(JPanel metricsPanel, JTable table) {
        table.setRowHeight(27);
        table.setAutoCreateRowSorter(true);
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);
        panel.add(metricsPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel metricsPanel(JLabel... labels) {
        JPanel panel = new JPanel(new GridLayout(2, 5, 10, 10));
        panel.setOpaque(false);
        for (JLabel label : labels) {
            panel.add(label);
        }
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
        updateStoreLabel();
    }

    private void loadReports() {
        ZonedDateTime from = parseDateTime(fromField.getText(), "From");
        ZonedDateTime to = parseDateTime(toField.getText(), "To");
        if (from == null || to == null) {
            return;
        }
        if (!to.isAfter(from)) {
            JOptionPane.showMessageDialog(this, "To must be after From.");
            return;
        }

        loadSalesReport(from, to);
        loadOrdersReport(from, to);
        loadInvoicesReport(from, to);
    }

    private void loadSalesReport(ZonedDateTime from, ZonedDateTime to) {
        salesModel.setRowCount(0);
        Integer locationId = SessionManager.getCurrentLocationId();
        StringBuilder sql = new StringBuilder("""
                SELECT sale_id,
                       COALESCE(receipt_number, '') AS receipt_number,
                       created_at AT TIME ZONE ? AS local_created_at,
                       COALESCE(user_name, 'Unknown') AS employee_name,
                       COALESCE(receipt_device_id, '') AS device_id,
                       COALESCE(NULLIF(TRIM(cash_drawer_name), ''), 'Unassigned') AS cash_drawer_name,
                       payment_method,
                       payment_status,
                       COALESCE(amount_paid, 0) AS amount_paid,
                       COALESCE(total_amount, 0) AS total_amount,
                       COALESCE(discount_amount, 0) AS discount_amount,
                       COALESCE(returned_amount, 0) AS returned_amount
                FROM sales
                WHERE (created_at AT TIME ZONE ?) >= ?
                  AND (created_at AT TIME ZONE ?) < ?
                """);
        List<Object> parameters = dateParameters(from, to);
        if (locationId != null) {
            sql.append(" AND location_id = ?");
            parameters.add(locationId);
        }
        sql.append(" ORDER BY created_at ASC, sale_id ASC");

        int transactions = 0;
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal returned = BigDecimal.ZERO;
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal card = BigDecimal.ZERO;
        BigDecimal mmg = BigDecimal.ZERO;
        BigDecimal account = BigDecimal.ZERO;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bind(ps, parameters);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    transactions++;
                    BigDecimal amountPaid = defaultZero(rs.getBigDecimal("amount_paid"));
                    BigDecimal total = defaultZero(rs.getBigDecimal("total_amount"));
                    BigDecimal returnAmount = defaultZero(rs.getBigDecimal("returned_amount"));
                    String method = rs.getString("payment_method");
                    gross = gross.add(total);
                    paid = paid.add(amountPaid);
                    returned = returned.add(returnAmount);
                    if ("CASH".equalsIgnoreCase(method)) {
                        cash = cash.add(amountPaid);
                    } else if ("CARD".equalsIgnoreCase(method) || "CHEQUE".equalsIgnoreCase(method)) {
                        card = card.add(amountPaid);
                    } else if ("MMG".equalsIgnoreCase(method)) {
                        mmg = mmg.add(amountPaid);
                    } else if ("ACCOUNT".equalsIgnoreCase(method)) {
                        account = account.add(total.subtract(amountPaid));
                    }
                    salesModel.addRow(new Object[]{
                            rs.getInt("sale_id"),
                            rs.getString("receipt_number"),
                            formatLocalTime(rs.getTimestamp("local_created_at")),
                            rs.getString("employee_name"),
                            rs.getString("device_id"),
                            rs.getString("cash_drawer_name"),
                            method,
                            rs.getString("payment_status"),
                            CURRENCY.format(amountPaid),
                            CURRENCY.format(total)
                    });
                }
            }
            BigDecimal returnTotal = loadSalesReturnTotal(conn, from, to, locationId);
            returned = returned.max(returnTotal);
            salesTransactionsLabel.setText("Transactions: " + transactions);
            salesGrossLabel.setText("Gross Sales: " + CURRENCY.format(gross));
            salesReturnsLabel.setText("Returns: " + CURRENCY.format(returned));
            salesNetLabel.setText("Net Sales: " + CURRENCY.format(gross.subtract(returned)));
            salesPaidLabel.setText("Paid: " + CURRENCY.format(paid));
            salesUnpaidLabel.setText("Unpaid: " + CURRENCY.format(gross.subtract(paid)));
            salesCashLabel.setText("Cash: " + CURRENCY.format(cash));
            salesCardLabel.setText("Card/Check: " + CURRENCY.format(card));
            salesMmgLabel.setText("MMG: " + CURRENCY.format(mmg));
            salesAccountLabel.setText("Account: " + CURRENCY.format(account));
        } catch (SQLException ex) {
            showReportError("sales report", ex);
        }
    }

    private void loadOrdersReport(ZonedDateTime from, ZonedDateTime to) {
        orderModel.setRowCount(0);
        Integer locationId = SessionManager.getCurrentLocationId();
        StringBuilder sql = new StringBuilder("""
                SELECT p.custom_order_payment_id,
                       co.order_number AS invoice_number,
                       p.created_at AT TIME ZONE ? AS local_created_at,
                       COALESCE(co.customer_name, '') AS customer_name,
                       COALESCE(p.taken_by_name, u.full_name, u.username, 'Unknown') AS employee_name,
                       COALESCE(p.device_name, co.device_name, p.device_id, co.device_id, '') AS device_name,
                       COALESCE(NULLIF(TRIM(p.cash_drawer_name), ''), 'Unassigned') AS cash_drawer_name,
                       p.payment_method,
                       COALESCE(p.payment_action, 'PAYMENT') AS payment_action,
                       COALESCE(p.payment_amount, 0) AS payment_amount,
                       COALESCE(co.total_amount, 0) AS total_amount,
                       COALESCE(co.balance_due, 0) AS balance_due,
                       COALESCE(co.status, '') AS status,
                       COALESCE(co.payment_status, '') AS payment_status
                FROM custom_order_payments p
                JOIN custom_orders co ON co.custom_order_id = p.custom_order_id
                LEFT JOIN users u ON u.user_id = p.taken_by_user_id
                WHERE (p.created_at AT TIME ZONE ?) >= ?
                  AND (p.created_at AT TIME ZONE ?) < ?
                """);
        List<Object> parameters = dateParameters(from, to);
        if (locationId != null) {
            sql.append(" AND co.location_id = ?");
            parameters.add(locationId);
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
            bind(ps, parameters);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    payments++;
                    BigDecimal amount = signedAmount(rs.getString("payment_action"), rs.getBigDecimal("payment_amount"));
                    String method = rs.getString("payment_method");
                    collected = collected.add(amount);
                    if ("CASH".equalsIgnoreCase(method)) {
                        cash = cash.add(amount);
                    } else if ("CARD".equalsIgnoreCase(method)) {
                        card = card.add(amount);
                    } else if ("CHEQUE".equalsIgnoreCase(method)) {
                        cheque = cheque.add(amount);
                    } else if ("MMG".equalsIgnoreCase(method)) {
                        mmg = mmg.add(amount);
                    } else if ("ACCOUNT".equalsIgnoreCase(method)) {
                        account = account.add(amount);
                    }
                    orderModel.addRow(new Object[]{
                            rs.getLong("custom_order_payment_id"),
                            rs.getString("invoice_number"),
                            formatLocalTime(rs.getTimestamp("local_created_at")),
                            rs.getString("customer_name"),
                            rs.getString("employee_name"),
                            rs.getString("device_name"),
                            rs.getString("cash_drawer_name"),
                            method,
                            CURRENCY.format(amount),
                            CURRENCY.format(defaultZero(rs.getBigDecimal("total_amount"))),
                            CURRENCY.format(defaultZero(rs.getBigDecimal("balance_due"))),
                            rs.getString("status") + " / " + rs.getString("payment_status")
                    });
                }
            }

            OrderTotals totals = loadCustomOrderTotals(conn, from, to, locationId);
            BigDecimal returnTotal = loadCustomOrderReturnTotal(conn, from, to, locationId);
            orderPaymentsLabel.setText("Payments: " + payments);
            orderTotalLabel.setText("Order Total: " + CURRENCY.format(totals.total()));
            orderCollectedLabel.setText("Collected: " + CURRENCY.format(collected));
            orderBalanceLabel.setText("Balance Due: " + CURRENCY.format(totals.balance()));
            orderCashLabel.setText("Cash: " + CURRENCY.format(cash));
            orderCardLabel.setText("Card: " + CURRENCY.format(card));
            orderChequeLabel.setText("Cheque: " + CURRENCY.format(cheque));
            orderMmgLabel.setText("MMG: " + CURRENCY.format(mmg));
            orderAccountLabel.setText("Account: " + CURRENCY.format(account));
            orderReturnsLabel.setText("Returns: " + CURRENCY.format(returnTotal));
        } catch (SQLException ex) {
            showReportError("order report", ex);
        }
    }

    private void loadInvoicesReport(ZonedDateTime from, ZonedDateTime to) {
        invoiceModel.setRowCount(0);
        Integer locationId = SessionManager.getCurrentLocationId();
        StringBuilder sql = new StringBuilder("""
                SELECT invoice_id,
                       invoice_number,
                       invoice_date,
                       customer_name,
                       status,
                       payment_status,
                       total_amount,
                       amount_paid,
                       balance_due,
                       COALESCE(created_by_name, '') AS created_by_name,
                       COALESCE(device_name, device_id, '') AS device_name,
                       COALESCE(NULLIF(TRIM(cash_drawer_name), ''), 'Unassigned') AS cash_drawer_name
                FROM invoices
                WHERE (created_at AT TIME ZONE ?) >= ?
                  AND (created_at AT TIME ZONE ?) < ?
                """);
        List<Object> parameters = rangeParameters(from, to);
        if (locationId != null) {
            sql.append(" AND location_id = ?");
            parameters.add(locationId);
        }
        sql.append(" ORDER BY created_at ASC, invoice_id ASC");

        int count = 0;
        int open = 0;
        int delivered = 0;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal balance = BigDecimal.ZERO;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bind(ps, parameters);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    count++;
                    BigDecimal rowTotal = defaultZero(rs.getBigDecimal("total_amount"));
                    BigDecimal rowPaid = defaultZero(rs.getBigDecimal("amount_paid"));
                    BigDecimal rowBalance = defaultZero(rs.getBigDecimal("balance_due"));
                    String status = rs.getString("status");
                    total = total.add(rowTotal);
                    paid = paid.add(rowPaid);
                    balance = balance.add(rowBalance);
                    if ("DELIVERED".equalsIgnoreCase(status)) {
                        delivered++;
                    } else if (!"CANCELLED".equalsIgnoreCase(status)) {
                        open++;
                    }
                    invoiceModel.addRow(new Object[]{
                            rs.getLong("invoice_id"),
                            rs.getString("invoice_number"),
                            rs.getDate("invoice_date"),
                            rs.getString("customer_name"),
                            status,
                            rs.getString("payment_status"),
                            CURRENCY.format(rowTotal),
                            CURRENCY.format(rowPaid),
                            CURRENCY.format(rowBalance),
                            rs.getString("created_by_name"),
                            rs.getString("device_name"),
                            rs.getString("cash_drawer_name")
                    });
                }
            }

            PaymentTotals payments = loadInvoicePaymentTotals(conn, from, to, locationId);
            invoiceCountLabel.setText("Invoices: " + count);
            invoiceTotalLabel.setText("Invoice Total: " + CURRENCY.format(total));
            invoicePaidLabel.setText("Paid: " + CURRENCY.format(paid));
            invoiceBalanceLabel.setText("Balance Due: " + CURRENCY.format(balance));
            invoiceOpenLabel.setText("Open: " + open);
            invoiceDeliveredLabel.setText("Delivered: " + delivered);
            invoiceCashLabel.setText("Cash: " + CURRENCY.format(payments.cash()));
            invoiceCardLabel.setText("Card: " + CURRENCY.format(payments.card()));
            invoiceChequeLabel.setText("Cheque: " + CURRENCY.format(payments.cheque()));
            invoiceMmgLabel.setText("MMG: " + CURRENCY.format(payments.mmg()));
        } catch (SQLException ex) {
            showReportError("invoice report", ex);
        }
    }

    private BigDecimal loadSalesReturnTotal(Connection conn, ZonedDateTime from, ZonedDateTime to, Integer locationId) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT COALESCE(SUM(refund_amount), 0) AS total
                FROM sale_returns
                WHERE (created_at AT TIME ZONE ?) >= ?
                  AND (created_at AT TIME ZONE ?) < ?
                """);
        List<Object> parameters = rangeParameters(from, to);
        if (locationId != null) {
            sql.append(" AND location_id = ?");
            parameters.add(locationId);
        }
        return loadMoney(conn, sql.toString(), parameters);
    }

    private OrderTotals loadCustomOrderTotals(Connection conn, ZonedDateTime from, ZonedDateTime to, Integer locationId) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT COALESCE(SUM(total_amount), 0) AS total,
                       COALESCE(SUM(balance_due), 0) AS balance
                FROM custom_orders
                WHERE (created_at AT TIME ZONE ?) >= ?
                  AND (created_at AT TIME ZONE ?) < ?
                """);
        List<Object> parameters = rangeParameters(from, to);
        if (locationId != null) {
            sql.append(" AND location_id = ?");
            parameters.add(locationId);
        }
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bind(ps, parameters);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new OrderTotals(defaultZero(rs.getBigDecimal("total")), defaultZero(rs.getBigDecimal("balance")));
                }
            }
        }
        return new OrderTotals(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private BigDecimal loadCustomOrderReturnTotal(Connection conn, ZonedDateTime from, ZonedDateTime to, Integer locationId) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT COALESCE(SUM(r.refund_amount), 0) AS total
                FROM custom_order_line_returns r
                JOIN custom_orders co ON co.custom_order_id = r.custom_order_id
                WHERE (r.created_at AT TIME ZONE ?) >= ?
                  AND (r.created_at AT TIME ZONE ?) < ?
                """);
        List<Object> parameters = rangeParameters(from, to);
        if (locationId != null) {
            sql.append(" AND co.location_id = ?");
            parameters.add(locationId);
        }
        return loadMoney(conn, sql.toString(), parameters);
    }

    private PaymentTotals loadInvoicePaymentTotals(Connection conn, ZonedDateTime from, ZonedDateTime to, Integer locationId) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT payment_method,
                       COALESCE(payment_action, 'PAYMENT') AS payment_action,
                       COALESCE(SUM(payment_amount), 0) AS total
                FROM invoice_payments
                WHERE voided_at IS NULL
                  AND (created_at AT TIME ZONE ?) >= ?
                  AND (created_at AT TIME ZONE ?) < ?
                """);
        List<Object> parameters = rangeParameters(from, to);
        if (locationId != null) {
            sql.append(" AND location_id = ?");
            parameters.add(locationId);
        }
        sql.append(" GROUP BY payment_method, COALESCE(payment_action, 'PAYMENT')");

        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal card = BigDecimal.ZERO;
        BigDecimal cheque = BigDecimal.ZERO;
        BigDecimal mmg = BigDecimal.ZERO;
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bind(ps, parameters);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal total = signedAmount(rs.getString("payment_action"), rs.getBigDecimal("total"));
                    String method = rs.getString("payment_method");
                    if ("CASH".equalsIgnoreCase(method)) {
                        cash = cash.add(total);
                    } else if ("CARD".equalsIgnoreCase(method)) {
                        card = card.add(total);
                    } else if ("CHEQUE".equalsIgnoreCase(method)) {
                        cheque = cheque.add(total);
                    } else if ("MMG".equalsIgnoreCase(method)) {
                        mmg = mmg.add(total);
                    }
                }
            }
        }
        return new PaymentTotals(cash, card, cheque, mmg);
    }

    private BigDecimal loadMoney(Connection conn, String sql, List<Object> parameters) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, parameters);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return defaultZero(rs.getBigDecimal("total"));
                }
            }
        }
        return BigDecimal.ZERO;
    }

    private List<Object> dateParameters(ZonedDateTime from, ZonedDateTime to) {
        List<Object> parameters = new ArrayList<>();
        parameters.add(storeZone.getId());
        parameters.addAll(rangeParameters(from, to));
        return parameters;
    }

    private List<Object> rangeParameters(ZonedDateTime from, ZonedDateTime to) {
        List<Object> parameters = new ArrayList<>();
        parameters.add(storeZone.getId());
        parameters.add(Timestamp.valueOf(from.toLocalDateTime()));
        parameters.add(storeZone.getId());
        parameters.add(Timestamp.valueOf(to.toLocalDateTime()));
        return parameters;
    }

    private void bind(PreparedStatement ps, List<Object> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            ps.setObject(i + 1, parameters.get(i));
        }
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

    private void showReportError(String reportName, SQLException ex) {
        JOptionPane.showMessageDialog(this, "Failed to load " + reportName + ": " + ex.getMessage(), "Reports", JOptionPane.ERROR_MESSAGE);
    }

    private static BigDecimal signedAmount(String action, BigDecimal amount) {
        BigDecimal value = defaultZero(amount);
        return "REFUND".equalsIgnoreCase(action) || "REVERSAL".equalsIgnoreCase(action) ? value.negate() : value;
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

    private static DefaultTableModel readOnlyModel(Object... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record OrderTotals(BigDecimal total, BigDecimal balance) {
    }

    private record PaymentTotals(BigDecimal cash, BigDecimal card, BigDecimal cheque, BigDecimal mmg) {
    }
}
