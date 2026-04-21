package ui.screens;

import data.DB;
import managers.PermissionManager;
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

public class CustomerAccountDetails extends JFrame {
    private final int customerId;
    private final Runnable afterSave;
    private final boolean canSetCreditLimit;
    private final boolean canEditAccountNumber;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance();
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private JLabel titleLabel;
    private JTextField accountNumberField;
    private JTextField nameField;
    private JTextField phoneField;
    private JTextField emailField;
    private JCheckBox businessAccountCheckBox;
    private JCheckBox activeCheckBox;
    private JLabel balanceLabel;
    private JLabel availableCreditLabel;
    private JTextField creditLimitField;
    private JTextArea notesArea;
    private JButton saveButton;
    private DefaultTableModel transactionModel;
    private JLabel transactionSummaryLabel;
    private String customerLabel = "Customer Account";

    public CustomerAccountDetails(int customerId, Runnable afterSave) {
        this.customerId = customerId;
        this.afterSave = afterSave;
        this.canSetCreditLimit = PermissionManager.hasPermission("SET_CREDIT_LIMIT");
        this.canEditAccountNumber = PermissionManager.hasPermission("EDIT_ACCOUNT_NUMBER");

        setTitle("Customer Account Details");
        setSize(1120, 720);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(12, 12));

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(new EmptyBorder(14, 14, 14, 14));
        add(mainPanel, BorderLayout.CENTER);

        mainPanel.add(buildHeaderPanel(), BorderLayout.NORTH);
        mainPanel.add(buildContentPanel(), BorderLayout.CENTER);
        mainPanel.add(buildFooterPanel(), BorderLayout.SOUTH);

        loadDetails();
        loadTransactions();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout(12, 8));

        titleLabel = new JLabel("Customer Account");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 24));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton refreshButton = new JButton("Refresh");
        JButton paymentHistoryButton = new JButton("Payment History");
        buttonPanel.add(refreshButton);
        buttonPanel.add(paymentHistoryButton);

        refreshButton.addActionListener(e -> {
            loadDetails();
            loadTransactions();
        });
        paymentHistoryButton.addActionListener(e -> openPaymentHistory());

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(buttonPanel, BorderLayout.EAST);
        return headerPanel;
    }

    private JPanel buildContentPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.add(buildAccountInfoPanel(), BorderLayout.NORTH);
        panel.add(buildTransactionPanel(), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildAccountInfoPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createTitledBorder("Account Information"));

        JPanel grid = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 8, 5, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        accountNumberField = new JTextField();
        accountNumberField.setEditable(canEditAccountNumber);
        nameField = new JTextField();
        phoneField = new JTextField();
        emailField = new JTextField();
        businessAccountCheckBox = new JCheckBox("Business Account");
        activeCheckBox = new JCheckBox("Active");
        balanceLabel = new JLabel();
        availableCreditLabel = new JLabel();
        creditLimitField = new JTextField();
        creditLimitField.setEditable(canSetCreditLimit);
        saveButton = new JButton("Save Changes");

        addInfoField(grid, gbc, 0, 0, "Account #:", accountNumberField);
        addInfoField(grid, gbc, 0, 1, "Name:", nameField);
        addInfoField(grid, gbc, 1, 0, "Phone:", phoneField);
        addInfoField(grid, gbc, 1, 1, "Email:", emailField);
        addInfoField(grid, gbc, 2, 0, "Type:", businessAccountCheckBox);
        addInfoField(grid, gbc, 2, 1, "Status:", activeCheckBox);
        addInfoField(grid, gbc, 3, 0, "Balance:", balanceLabel);
        addInfoField(grid, gbc, 3, 1, "Available Credit:", availableCreditLabel);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0;
        grid.add(new JLabel("Credit Limit:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        grid.add(creditLimitField, gbc);

        notesArea = new JTextArea(4, 30);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);

        panel.add(grid, BorderLayout.CENTER);
        panel.add(new JScrollPane(notesArea), BorderLayout.SOUTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.add(saveButton);
        panel.add(buttonPanel, BorderLayout.EAST);
        saveButton.addActionListener(e -> saveAccountDetails());
        return panel;
    }

    private void addInfoField(JPanel panel, GridBagConstraints gbc, int row, int columnGroup, String label, JComponent valueField) {
        int baseColumn = columnGroup * 2;
        gbc.gridx = baseColumn;
        gbc.gridy = row;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = baseColumn + 1;
        gbc.weightx = 1;
        panel.add(valueField, gbc);
    }

    private JPanel buildTransactionPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createTitledBorder("Transaction History"));

        transactionModel = new DefaultTableModel(
                new Object[]{"Transaction ID", "Payment ID", "Date", "User", "Type", "Sale ID", "Amount", "Sale Status", "Sale Total", "Note"},
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
        transactionTable.getColumnModel().getColumn(4).setPreferredWidth(130);
        transactionTable.getColumnModel().getColumn(5).setPreferredWidth(90);
        transactionTable.getColumnModel().getColumn(6).setPreferredWidth(110);
        transactionTable.getColumnModel().getColumn(7).setPreferredWidth(100);
        transactionTable.getColumnModel().getColumn(8).setPreferredWidth(110);
        transactionTable.getColumnModel().getColumn(9).setPreferredWidth(260);

        transactionSummaryLabel = new JLabel("Transactions: 0");
        transactionSummaryLabel.setBorder(new EmptyBorder(4, 2, 0, 2));

        panel.add(new JScrollPane(transactionTable), BorderLayout.CENTER);
        panel.add(transactionSummaryLabel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildFooterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        panel.add(closeButton);
        return panel;
    }

    private void loadDetails() {
        String sql = """
                SELECT account_number,
                       name,
                       phone,
                       email,
                       credit_limit,
                       current_balance,
                       (credit_limit - current_balance) AS available_credit,
                       COALESCE(is_business, FALSE) AS is_business,
                       is_active,
                       COALESCE(account_notes, '') AS account_notes
                FROM customer_accounts
                WHERE customer_id = ?
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    JOptionPane.showMessageDialog(this, "Customer account was not found.", "Not Found", JOptionPane.WARNING_MESSAGE);
                    dispose();
                    return;
                }

                String accountNumber = text(rs.getString("account_number"));
                String name = text(rs.getString("name"));
                customerLabel = accountNumber.isBlank() ? name : accountNumber + " - " + name;

                titleLabel.setText(customerLabel);
                accountNumberField.setText(accountNumber);
                nameField.setText(name);
                phoneField.setText(text(rs.getString("phone")));
                emailField.setText(text(rs.getString("email")));
                businessAccountCheckBox.setSelected(rs.getBoolean("is_business"));
                activeCheckBox.setSelected(rs.getBoolean("is_active"));
                balanceLabel.setText(money(rs.getBigDecimal("current_balance")));
                availableCreditLabel.setText(money(rs.getBigDecimal("available_credit")));
                creditLimitField.setText(stripMoney(money(rs.getBigDecimal("credit_limit"))));
                notesArea.setText(rs.getString("account_notes"));
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load account details: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadTransactions() {
        transactionModel.setRowCount(0);
        String sql = """
                SELECT t.transaction_id,
                       COALESCE(t.payment_id, '') AS payment_id,
                       (t.created_at AT TIME ZONE ?) AS local_created_at,
	                       COALESCE(t.user_name, '') AS user_name,
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

            ps.setString(1, StoreTimeZoneHelper.getStoreZoneId());
            ps.setInt(2, customerId);
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
	                            formatTimestamp(rs.getTimestamp("local_created_at")),
	                            rs.getString("user_name"),
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

            transactionSummaryLabel.setText("Transactions: " + count
                    + "    Charges: " + currencyFormat.format(totalCharges)
                    + "    Payments: " + currencyFormat.format(totalPayments));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load transaction history: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveAccountDetails() {
        String accountNumber = accountNumberField.getText().trim();
        String name = nameField.getText().trim();
        String phone = phoneField.getText().trim();
        String email = emailField.getText().trim();
        String notes = notesArea.getText().trim();

        if (accountNumber.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Account number is required.");
            return;
        }
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Customer name is required.");
            return;
        }

        BigDecimal creditLimit = null;
        if (canSetCreditLimit) {
            creditLimit = parseMoney(creditLimitField.getText().trim(), "Credit limit");
            if (creditLimit == null) {
                return;
            }
            if (creditLimit.compareTo(BigDecimal.ZERO) < 0) {
                JOptionPane.showMessageDialog(this, "Credit limit cannot be negative.");
                return;
            }
        }

        String sql = canSetCreditLimit
                ? """
                UPDATE customer_accounts
                SET account_number = ?,
                    name = ?,
                    phone = ?,
                    email = ?,
                    is_business = ?,
                    is_active = ?,
                    account_notes = ?,
                    credit_limit = ?
                WHERE customer_id = ?
                """
                : """
                UPDATE customer_accounts
                SET account_number = ?,
                    name = ?,
                    phone = ?,
                    email = ?,
                    is_business = ?,
                    is_active = ?,
                    account_notes = ?
                WHERE customer_id = ?
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, accountNumber);
            ps.setString(2, name);
            ps.setString(3, phone.isEmpty() ? null : phone);
            ps.setString(4, email.isEmpty() ? null : email);
            ps.setBoolean(5, businessAccountCheckBox.isSelected());
            ps.setBoolean(6, activeCheckBox.isSelected());
            ps.setString(7, notes.isEmpty() ? null : notes);
            if (canSetCreditLimit) {
                ps.setBigDecimal(8, creditLimit);
                ps.setInt(9, customerId);
            } else {
                ps.setInt(8, customerId);
            }
            ps.executeUpdate();

            JOptionPane.showMessageDialog(this, "Customer account updated.");
            if (afterSave != null) {
                afterSave.run();
            }
            loadDetails();
            loadTransactions();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to update customer account: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
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

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String stripMoney(String value) {
        return value == null ? "" : value.replace("$", "").replace(",", "").trim();
    }

    private String money(BigDecimal value) {
        value = defaultZero(value);
        return String.format("$%.2f", value);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal parseMoney(String value, String fieldName) {
        try {
            return new BigDecimal(value.replace("$", "").replace(",", "").trim());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, fieldName + " must be a valid amount.");
            return null;
        }
    }
}
