package ui.screens;

import data.DB;
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
import java.time.format.DateTimeFormatter;

public class CustomerTransactionHistory extends JFrame {
    private final int customerId;
    private final String customerLabel;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance();
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DefaultTableModel transactionModel;
    private JLabel summaryLabel;

    public CustomerTransactionHistory(int customerId, String customerLabel) {
        this.customerId = customerId;
        this.customerLabel = customerLabel == null ? "Customer Account" : customerLabel;

        setTitle("Customer Transaction History");
        setSize(1050, 620);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(12, 12));

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(new EmptyBorder(14, 14, 14, 14));
        add(mainPanel, BorderLayout.CENTER);

        mainPanel.add(buildHeaderPanel(), BorderLayout.NORTH);
        mainPanel.add(buildTablePanel(), BorderLayout.CENTER);
        mainPanel.add(buildSummaryPanel(), BorderLayout.SOUTH);

        loadTransactions();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout(12, 8));

        JLabel titleLabel = new JLabel("Transaction History");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 24));

        JLabel customerLabelText = new JLabel(customerLabel);
        customerLabelText.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(customerLabelText);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton refreshButton = new JButton("Refresh");
        JButton paymentHistoryButton = new JButton("Payment History");
        buttonPanel.add(refreshButton);
        buttonPanel.add(paymentHistoryButton);

        refreshButton.addActionListener(e -> loadTransactions());
        paymentHistoryButton.addActionListener(e -> openPaymentHistory());

        headerPanel.add(titlePanel, BorderLayout.WEST);
        headerPanel.add(buttonPanel, BorderLayout.EAST);
        return headerPanel;
    }

    private JScrollPane buildTablePanel() {
        transactionModel = new DefaultTableModel(
                new Object[]{"Transaction ID", "Payment ID", "Date", "User", "Device", "Drawer", "Type", "Method", "Reference", "Sale ID", "Custom Order ID", "Amount", "Sale Status", "Sale Total", "Note"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable transactionTable = new JTable(transactionModel);
        transactionTable.setRowHeight(26);
        transactionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        transactionTable.getTableHeader().setReorderingAllowed(false);
        transactionTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        transactionTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        transactionTable.getColumnModel().getColumn(2).setPreferredWidth(160);
        transactionTable.getColumnModel().getColumn(3).setPreferredWidth(150);
        transactionTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        transactionTable.getColumnModel().getColumn(5).setPreferredWidth(120);
        transactionTable.getColumnModel().getColumn(6).setPreferredWidth(130);
        transactionTable.getColumnModel().getColumn(7).setPreferredWidth(90);
        transactionTable.getColumnModel().getColumn(8).setPreferredWidth(150);
        transactionTable.getColumnModel().getColumn(9).setPreferredWidth(90);
        transactionTable.getColumnModel().getColumn(10).setPreferredWidth(120);
        transactionTable.getColumnModel().getColumn(11).setPreferredWidth(110);
        transactionTable.getColumnModel().getColumn(12).setPreferredWidth(100);
        transactionTable.getColumnModel().getColumn(13).setPreferredWidth(110);
        transactionTable.getColumnModel().getColumn(14).setPreferredWidth(240);

        return new JScrollPane(transactionTable);
    }

    private JPanel buildSummaryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        summaryLabel = new JLabel("Transactions: 0");
        summaryLabel.setBorder(new EmptyBorder(4, 2, 0, 2));
        panel.add(summaryLabel, BorderLayout.WEST);
        return panel;
    }

    private void loadTransactions() {
        transactionModel.setRowCount(0);
        String sql = """
                SELECT t.transaction_id,
                       COALESCE(t.payment_id, '') AS payment_id,
		                       (t.created_at AT TIME ZONE ?) AS local_created_at,
		                       COALESCE(t.user_name, '') AS user_name,
	                       COALESCE(t.device_name, t.device_id, '') AS device_name,
                       COALESCE(t.cash_drawer_name, '') AS cash_drawer_name,
		                       COALESCE(t.transaction_type, '') AS transaction_type,
                       COALESCE(t.payment_method, '') AS payment_method,
                       COALESCE(t.payment_reference, '') AS payment_reference,
	                       t.sale_id,
	                       t.custom_order_id,
                       COALESCE(t.amount, 0) AS ledger_amount,
                       CASE
                           WHEN t.custom_order_id IS NOT NULL
                                AND COALESCE(t.transaction_type, '') IN ('CUSTOM_ORDER_PAID', 'CUSTOM_ORDER_CREDIT')
                               THEN COALESCE(co.amount_paid, 0)
                           ELSE COALESCE(t.amount, 0)
                       END AS display_amount,
                       COALESCE(t.note, '') AS note,
                       COALESCE(s.payment_status, co.payment_status, '') AS payment_status,
                       COALESCE(s.total_amount, co.total_amount, 0) AS sale_total
                FROM customer_account_transactions t
                LEFT JOIN sales s ON t.sale_id = s.sale_id
                LEFT JOIN custom_orders co ON t.custom_order_id = co.custom_order_id
                WHERE t.customer_id = ?
                ORDER BY t.created_at DESC, t.transaction_id DESC
                """;

        int count = 0;
        BigDecimal totalCharges = BigDecimal.ZERO;
        BigDecimal totalPayments = BigDecimal.ZERO;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, StoreTimeZoneHelper.getStoreZoneId());
            ps.setInt(2, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal ledgerAmount = defaultZero(rs.getBigDecimal("ledger_amount"));
                    BigDecimal displayAmount = defaultZero(rs.getBigDecimal("display_amount"));
                    if (ledgerAmount.compareTo(BigDecimal.ZERO) >= 0) {
                        totalCharges = totalCharges.add(ledgerAmount);
                    } else {
                        totalPayments = totalPayments.add(ledgerAmount.abs());
                    }

                    transactionModel.addRow(new Object[]{
                            rs.getInt("transaction_id"),
	                            rs.getString("payment_id"),
	                            formatTimestamp(rs.getTimestamp("local_created_at")),
		                            rs.getString("user_name"),
	                            rs.getString("device_name"),
                            rs.getString("cash_drawer_name"),
		                            formatType(rs.getString("transaction_type")),
                            rs.getString("payment_method"),
                            rs.getString("payment_reference"),
	                            nullableInt(rs, "sale_id"),
                            nullableLong(rs, "custom_order_id"),
                            currencyFormat.format(displayAmount),
                            formatStatus(rs.getString("payment_status")),
                            currencyFormat.format(defaultZero(rs.getBigDecimal("sale_total"))),
                            rs.getString("note")
                    });
                    count++;
                }
            }

            summaryLabel.setText("Transactions: " + count
                    + "    Charges: " + currencyFormat.format(totalCharges)
                    + "    Payments: " + currencyFormat.format(totalPayments));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load transaction history: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openPaymentHistory() {
        CustomerPaymentHistory paymentHistory = new CustomerPaymentHistory(customerId, customerLabel);
        WindowHelper.showPosWindow(paymentHistory, this);
    }

    private Object nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? "" : value;
    }

    private Object nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? "" : value;
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        return StoreTimeZoneHelper.formatLocalTimestamp(timestamp, dateTimeFormatter);
    }

    private String formatType(String type) {
        if (type == null || type.isBlank()) {
            return "";
        }
        return switch (type) {
            case "SALE_CREDIT" -> "Sale Credit";
            case "SALE_PAID" -> "Sale Paid";
            case "CUSTOM_ORDER_REFUND" -> "Custom Order Refund";
            case "MANUAL_CHARGE" -> "Manual Charge";
            case "PAYMENT" -> "Payment";
            default -> type.replace('_', ' ');
        };
    }

    private String formatStatus(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        return switch (status.toUpperCase()) {
            case "PAID" -> "Paid";
            case "PARTIAL" -> "Partial Paid";
            case "UNPAID" -> "Unpaid";
            default -> status.substring(0, 1).toUpperCase() + status.substring(1).toLowerCase();
        };
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
