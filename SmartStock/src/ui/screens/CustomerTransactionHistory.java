package ui.screens;

import data.DB;

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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
                new Object[]{"Transaction ID", "Payment ID", "Date", "Type", "Sale ID", "Amount", "Sale Status", "Sale Total", "Note"},
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
        transactionTable.getColumnModel().getColumn(3).setPreferredWidth(130);
        transactionTable.getColumnModel().getColumn(4).setPreferredWidth(90);
        transactionTable.getColumnModel().getColumn(5).setPreferredWidth(110);
        transactionTable.getColumnModel().getColumn(6).setPreferredWidth(100);
        transactionTable.getColumnModel().getColumn(7).setPreferredWidth(110);
        transactionTable.getColumnModel().getColumn(8).setPreferredWidth(240);

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
                       t.created_at,
                       COALESCE(t.transaction_type, '') AS transaction_type,
                       t.sale_id,
                       COALESCE(t.amount, 0) AS amount,
                       COALESCE(t.note, '') AS note,
                       COALESCE(s.payment_status, '') AS payment_status,
                       COALESCE(s.total_amount, 0) AS sale_total
                FROM customer_account_transactions t
                LEFT JOIN sales s ON t.sale_id = s.sale_id
                WHERE t.customer_id = ?
                ORDER BY t.created_at DESC, t.transaction_id DESC
                """;

        int count = 0;
        BigDecimal totalCharges = BigDecimal.ZERO;
        BigDecimal totalPayments = BigDecimal.ZERO;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal amount = defaultZero(rs.getBigDecimal("amount"));
                    if (amount.compareTo(BigDecimal.ZERO) >= 0) {
                        totalCharges = totalCharges.add(amount);
                    } else {
                        totalPayments = totalPayments.add(amount.abs());
                    }

                    transactionModel.addRow(new Object[]{
                            rs.getInt("transaction_id"),
                            rs.getString("payment_id"),
                            formatTimestamp(rs.getTimestamp("created_at")),
                            formatType(rs.getString("transaction_type")),
                            nullableInt(rs, "sale_id"),
                            currencyFormat.format(amount),
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
        paymentHistory.setLocationRelativeTo(this);
        paymentHistory.setVisible(true);
    }

    private Object nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? "" : value;
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        ZonedDateTime localDateTime = timestamp.toLocalDateTime()
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.systemDefault());
        return dateTimeFormatter.format(localDateTime);
    }

    private String formatType(String type) {
        if (type == null || type.isBlank()) {
            return "";
        }
        return switch (type) {
            case "SALE_CREDIT" -> "Sale Credit";
            case "SALE_PAID" -> "Sale Paid";
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
            case "UNPAID" -> "Unpaid";
            default -> status.substring(0, 1).toUpperCase() + status.substring(1).toLowerCase();
        };
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
