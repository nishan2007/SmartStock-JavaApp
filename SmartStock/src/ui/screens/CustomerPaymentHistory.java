package ui.screens;

import data.DB;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class CustomerPaymentHistory extends JFrame {
    private final int customerId;
    private final String customerLabel;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance();
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DefaultTableModel paymentModel;
    private JLabel summaryLabel;

    public CustomerPaymentHistory(int customerId, String customerLabel) {
        this.customerId = customerId;
        this.customerLabel = customerLabel == null ? "Customer Account" : customerLabel;

        setTitle("Customer Payment History");
        setSize(1050, 620);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(12, 12));

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(new EmptyBorder(14, 14, 14, 14));
        add(mainPanel, BorderLayout.CENTER);

        mainPanel.add(buildHeaderPanel(), BorderLayout.NORTH);
        mainPanel.add(buildTablePanel(), BorderLayout.CENTER);
        mainPanel.add(buildSummaryPanel(), BorderLayout.SOUTH);

        loadPayments();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout(12, 8));

        JLabel titleLabel = new JLabel("Payment History");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 24));

        JLabel customerLabelText = new JLabel(customerLabel);
        customerLabelText.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(customerLabelText);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> loadPayments());

        headerPanel.add(titlePanel, BorderLayout.WEST);
        headerPanel.add(refreshButton, BorderLayout.EAST);
        return headerPanel;
    }

    private JScrollPane buildTablePanel() {
        paymentModel = new DefaultTableModel(
                new Object[]{"Payment ID", "Payment Date", "User", "Payment Amount", "Sale ID", "Applied", "Sale Total", "Sale Paid", "Sale Status", "Sale Date"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable paymentTable = new JTable(paymentModel);
        paymentTable.setRowHeight(26);
        paymentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        paymentTable.getTableHeader().setReorderingAllowed(false);
        paymentTable.getColumnModel().getColumn(0).setPreferredWidth(130);
        paymentTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        paymentTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        paymentTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        paymentTable.getColumnModel().getColumn(4).setPreferredWidth(90);
        paymentTable.getColumnModel().getColumn(5).setPreferredWidth(110);
        paymentTable.getColumnModel().getColumn(6).setPreferredWidth(110);
        paymentTable.getColumnModel().getColumn(7).setPreferredWidth(110);
        paymentTable.getColumnModel().getColumn(8).setPreferredWidth(100);
        paymentTable.getColumnModel().getColumn(9).setPreferredWidth(160);

        return new JScrollPane(paymentTable);
    }

    private JPanel buildSummaryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        summaryLabel = new JLabel("Payments: 0");
        summaryLabel.setBorder(new EmptyBorder(4, 2, 0, 2));
        panel.add(summaryLabel, BorderLayout.WEST);
        return panel;
    }

    private void loadPayments() {
        paymentModel.setRowCount(0);
        String sql = """
                SELECT COALESCE(t.payment_id, '') AS payment_id,
	                       t.transaction_id,
	                       t.created_at AS payment_date,
	                       COALESCE(t.user_name, '') AS user_name,
	                       ABS(COALESCE(t.amount, 0)) AS payment_amount,
                       a.sale_id,
                       a.amount AS applied_amount,
                       COALESCE(s.total_amount, 0) AS sale_total,
                       COALESCE(s.amount_paid, 0) AS sale_paid,
                       COALESCE(s.payment_status, '') AS payment_status,
                       s.created_at AS sale_date
                FROM customer_account_transactions t
                LEFT JOIN customer_account_payment_allocations a
                    ON a.payment_transaction_id = t.transaction_id
                LEFT JOIN sales s ON a.sale_id = s.sale_id
                WHERE t.customer_id = ?
                  AND t.transaction_type = 'PAYMENT'
                ORDER BY t.created_at DESC, t.transaction_id DESC, a.sale_id ASC
                """;

        int rowCount = 0;
        int paymentCount = 0;
        String lastPaymentId = null;
        BigDecimal totalPayments = BigDecimal.ZERO;
        BigDecimal totalApplied = BigDecimal.ZERO;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String paymentId = rs.getString("payment_id");
                    int transactionId = rs.getInt("transaction_id");
                    String displayPaymentId = paymentId == null || paymentId.isBlank()
                            ? String.format("PAY-%06d", transactionId)
                            : paymentId;
                    BigDecimal paymentAmount = defaultZero(rs.getBigDecimal("payment_amount"));
                    BigDecimal appliedAmount = rs.getBigDecimal("applied_amount");

                    if (!displayPaymentId.equals(lastPaymentId)) {
                        totalPayments = totalPayments.add(paymentAmount);
                        paymentCount++;
                        lastPaymentId = displayPaymentId;
                    }
                    if (appliedAmount != null) {
                        totalApplied = totalApplied.add(appliedAmount);
                    }

                    paymentModel.addRow(new Object[]{
	                            displayPaymentId,
	                            formatTimestamp(rs.getTimestamp("payment_date")),
	                            rs.getString("user_name"),
	                            currencyFormat.format(paymentAmount),
                            nullableInt(rs, "sale_id"),
                            appliedAmount == null ? "" : currencyFormat.format(appliedAmount),
                            currencyFormat.format(defaultZero(rs.getBigDecimal("sale_total"))),
                            currencyFormat.format(defaultZero(rs.getBigDecimal("sale_paid"))),
                            formatStatus(rs.getString("payment_status")),
                            formatTimestamp(rs.getTimestamp("sale_date"))
                    });
                    rowCount++;
                }
            }

            summaryLabel.setText("Payments: " + paymentCount
                    + "    Rows: " + rowCount
                    + "    Total Paid: " + currencyFormat.format(totalPayments)
                    + "    Applied To Sales: " + currencyFormat.format(totalApplied));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load payment history: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
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
