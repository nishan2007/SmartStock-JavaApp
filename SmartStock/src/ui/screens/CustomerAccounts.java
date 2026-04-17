package ui.screens;

import data.DB;
import ui.components.AppMenuBar;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

public class CustomerAccounts extends JFrame {
    private JTable customerTable;
    private DefaultTableModel customerModel;
    private TableRowSorter<DefaultTableModel> customerSorter;
    private JTextField searchField;
    private JTextField accountNumberField;
    private JTextField nameField;
    private JTextField phoneField;
    private JTextField emailField;
    private JTextField creditLimitField;
    private JTextField balanceField;
    private JCheckBox activeCheckBox;
    private JButton addButton;
    private JButton updateButton;
    private JButton clearButton;
    private JButton refreshButton;
    private JButton addChargeButton;
    private JButton recordPaymentButton;
    private Integer selectedCustomerId;

    public CustomerAccounts() {
        setTitle("Customer Accounts");
        setSize(1050, 620);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        setJMenuBar(AppMenuBar.create(this, "CustomerAccounts"));

        JPanel mainPanel = new JPanel(new BorderLayout(14, 14));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        mainPanel.add(buildTablePanel(), BorderLayout.CENTER);
        mainPanel.add(buildFormPanel(), BorderLayout.EAST);

        add(mainPanel, BorderLayout.CENTER);

        loadCustomers();
        setVisible(true);
    }

    private JPanel buildTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchField = new JTextField();
        searchPanel.add(new JLabel("Search Customers:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        customerModel = new DefaultTableModel(
                new Object[]{"ID", "Account #", "Name", "Phone", "Email", "Credit Limit", "Balance", "Available", "Active"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        customerSorter = new TableRowSorter<>(customerModel);
        customerTable = new JTable(customerModel);
        customerTable.setRowSorter(customerSorter);
        customerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        customerTable.setRowHeight(26);
        customerTable.getColumnModel().getColumn(0).setMaxWidth(60);
        customerTable.getColumnModel().getColumn(8).setMaxWidth(70);

        customerTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedCustomer();
            }
        });
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                applyCustomerFilter();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                applyCustomerFilter();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                applyCustomerFilter();
            }
        });

        panel.add(searchPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(customerTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildFormPanel() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 12));
        wrapper.setPreferredSize(new Dimension(360, 0));
        wrapper.setBorder(BorderFactory.createTitledBorder("Account Details"));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        accountNumberField = new JTextField();
        nameField = new JTextField();
        phoneField = new JTextField();
        emailField = new JTextField();
        creditLimitField = new JTextField("0.00");
        balanceField = new JTextField("0.00");
        balanceField.setEditable(false);
        activeCheckBox = new JCheckBox("Active", true);

        addField(formPanel, gbc, 0, "Account #:", accountNumberField);
        addField(formPanel, gbc, 1, "Name:", nameField);
        addField(formPanel, gbc, 2, "Phone:", phoneField);
        addField(formPanel, gbc, 3, "Email:", emailField);
        addField(formPanel, gbc, 4, "Credit Limit:", creditLimitField);
        addField(formPanel, gbc, 5, "Current Balance:", balanceField);

        gbc.gridx = 0;
        gbc.gridy = 6;
        formPanel.add(new JLabel("Status:"), gbc);
        gbc.gridx = 1;
        formPanel.add(activeCheckBox, gbc);

        JPanel buttonPanel = new JPanel(new GridLayout(3, 2, 8, 8));
        addButton = new JButton("Add Account");
        updateButton = new JButton("Update Account");
        clearButton = new JButton("Clear");
        refreshButton = new JButton("Refresh");
        addChargeButton = new JButton("Add Charge");
        recordPaymentButton = new JButton("Record Payment");

        updateButton.setEnabled(false);
        addChargeButton.setEnabled(false);
        recordPaymentButton.setEnabled(false);

        buttonPanel.add(addButton);
        buttonPanel.add(updateButton);
        buttonPanel.add(addChargeButton);
        buttonPanel.add(recordPaymentButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(refreshButton);

        addButton.addActionListener(e -> addCustomer());
        updateButton.addActionListener(e -> updateCustomer());
        clearButton.addActionListener(e -> clearFields());
        refreshButton.addActionListener(e -> loadCustomers());
        addChargeButton.addActionListener(e -> adjustBalance(true));
        recordPaymentButton.addActionListener(e -> adjustBalance(false));

        wrapper.add(formPanel, BorderLayout.NORTH);
        wrapper.add(buttonPanel, BorderLayout.SOUTH);
        return wrapper;
    }

    private void addField(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
    }

    private void loadCustomers() {
        customerModel.setRowCount(0);
        String sql = """
                SELECT customer_id, account_number, name, phone, email,
                       credit_limit, current_balance,
                       (credit_limit - current_balance) AS available_credit,
                       is_active
                FROM customer_accounts
                ORDER BY name
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                customerModel.addRow(new Object[]{
                        rs.getInt("customer_id"),
                        rs.getString("account_number"),
                        rs.getString("name"),
                        rs.getString("phone"),
                        rs.getString("email"),
                        money(rs.getBigDecimal("credit_limit")),
                        money(rs.getBigDecimal("current_balance")),
                        money(rs.getBigDecimal("available_credit")),
                        rs.getBoolean("is_active")
                });
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load customer accounts: " + ex.getMessage());
        }
    }

    private void loadSelectedCustomer() {
        int row = customerTable.getSelectedRow();
        if (row == -1) {
            return;
        }
        int modelRow = customerTable.convertRowIndexToModel(row);
        selectedCustomerId = Integer.parseInt(String.valueOf(customerModel.getValueAt(modelRow, 0)));
        accountNumberField.setText(valueAt(modelRow, 1));
        nameField.setText(valueAt(modelRow, 2));
        phoneField.setText(valueAt(modelRow, 3));
        emailField.setText(valueAt(modelRow, 4));
        creditLimitField.setText(stripMoney(valueAt(modelRow, 5)));
        balanceField.setText(stripMoney(valueAt(modelRow, 6)));
        activeCheckBox.setSelected(Boolean.TRUE.equals(customerModel.getValueAt(modelRow, 8)));
        updateButton.setEnabled(true);
        addChargeButton.setEnabled(true);
        recordPaymentButton.setEnabled(true);
    }

    private void addCustomer() {
        String accountNumber = accountNumberField.getText().trim();
        String name = nameField.getText().trim();
        String phone = phoneField.getText().trim();
        String email = emailField.getText().trim();
        BigDecimal creditLimit = parseMoney(creditLimitField.getText().trim(), "Credit limit");
        if (creditLimit == null) {
            return;
        }
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Customer name is required.");
            return;
        }

        String sql = """
                INSERT INTO customer_accounts (account_number, name, phone, email, credit_limit, current_balance, is_active)
                VALUES (?, ?, ?, ?, ?, 0, ?)
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, accountNumber.isEmpty() ? null : accountNumber);
            ps.setString(2, name);
            ps.setString(3, phone.isEmpty() ? null : phone);
            ps.setString(4, email.isEmpty() ? null : email);
            ps.setBigDecimal(5, creditLimit);
            ps.setBoolean(6, activeCheckBox.isSelected());
            ps.executeUpdate();

            JOptionPane.showMessageDialog(this, "Customer account added.");
            clearFields();
            loadCustomers();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to add customer account: " + ex.getMessage());
        }
    }

    private void updateCustomer() {
        if (selectedCustomerId == null) {
            JOptionPane.showMessageDialog(this, "Select a customer first.");
            return;
        }

        String accountNumber = accountNumberField.getText().trim();
        String name = nameField.getText().trim();
        String phone = phoneField.getText().trim();
        String email = emailField.getText().trim();
        BigDecimal creditLimit = parseMoney(creditLimitField.getText().trim(), "Credit limit");
        if (creditLimit == null) {
            return;
        }
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Customer name is required.");
            return;
        }

        String sql = """
                UPDATE customer_accounts
                SET account_number = ?,
                    name = ?,
                    phone = ?,
                    email = ?,
                    credit_limit = ?,
                    is_active = ?
                WHERE customer_id = ?
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, accountNumber.isEmpty() ? null : accountNumber);
            ps.setString(2, name);
            ps.setString(3, phone.isEmpty() ? null : phone);
            ps.setString(4, email.isEmpty() ? null : email);
            ps.setBigDecimal(5, creditLimit);
            ps.setBoolean(6, activeCheckBox.isSelected());
            ps.setInt(7, selectedCustomerId);
            ps.executeUpdate();

            JOptionPane.showMessageDialog(this, "Customer account updated.");
            loadCustomers();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to update customer account: " + ex.getMessage());
        }
    }

    private void adjustBalance(boolean addCharge) {
        if (selectedCustomerId == null) {
            JOptionPane.showMessageDialog(this, "Select a customer first.");
            return;
        }

        String label = addCharge ? "charge amount" : "payment amount";
        String input = JOptionPane.showInputDialog(this, "Enter " + label + ":");
        if (input == null) {
            return;
        }

        BigDecimal amount = parseMoney(input.trim(), addCharge ? "Charge" : "Payment");
        if (amount == null) {
            return;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            JOptionPane.showMessageDialog(this, "Amount must be greater than zero.");
            return;
        }

        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                AccountSnapshot account = loadAccountForUpdate(conn, selectedCustomerId);
                BigDecimal signedAmount = addCharge ? amount : amount.negate();
                BigDecimal newBalance = account.balance.add(signedAmount);

                if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                    JOptionPane.showMessageDialog(this, "Payment is more than the current balance.");
                    conn.rollback();
                    return;
                }
                if (newBalance.compareTo(account.creditLimit) > 0) {
                    JOptionPane.showMessageDialog(this, "Charge exceeds the customer's credit limit.");
                    conn.rollback();
                    return;
                }

                updateCustomerBalance(conn, selectedCustomerId, newBalance);
                insertAccountTransaction(
                        conn,
                        selectedCustomerId,
                        signedAmount,
                        addCharge ? "MANUAL_CHARGE" : "PAYMENT",
                        addCharge ? "Manual account charge" : "Customer payment",
                        null
                );
                conn.commit();
                JOptionPane.showMessageDialog(this, addCharge ? "Charge added." : "Payment recorded.");
                loadCustomers();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to update account balance: " + ex.getMessage());
        }
    }

    private AccountSnapshot loadAccountForUpdate(Connection conn, int customerId) throws SQLException {
        String sql = """
                SELECT current_balance, credit_limit, is_active
                FROM customer_accounts
                WHERE customer_id = ?
                FOR UPDATE
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Customer account was not found.");
                }
                return new AccountSnapshot(
                        rs.getBigDecimal("current_balance"),
                        rs.getBigDecimal("credit_limit"),
                        rs.getBoolean("is_active")
                );
            }
        }
    }

    private void updateCustomerBalance(Connection conn, int customerId, BigDecimal newBalance) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE customer_accounts SET current_balance = ? WHERE customer_id = ?")) {
            ps.setBigDecimal(1, newBalance);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    private void insertAccountTransaction(Connection conn, int customerId, BigDecimal amount, String type, String note, Integer saleId) throws SQLException {
        String sql = """
                INSERT INTO customer_account_transactions (customer_id, sale_id, amount, transaction_type, note)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            if (saleId == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, saleId);
            }
            ps.setBigDecimal(3, amount);
            ps.setString(4, type);
            ps.setString(5, note);
            ps.executeUpdate();
        }
    }

    private void clearFields() {
        selectedCustomerId = null;
        accountNumberField.setText("");
        nameField.setText("");
        phoneField.setText("");
        emailField.setText("");
        creditLimitField.setText("0.00");
        balanceField.setText("0.00");
        activeCheckBox.setSelected(true);
        updateButton.setEnabled(false);
        addChargeButton.setEnabled(false);
        recordPaymentButton.setEnabled(false);
        customerTable.clearSelection();
        nameField.requestFocusInWindow();
    }

    private void applyCustomerFilter() {
        if (customerSorter == null) {
            return;
        }
        String text = searchField == null ? "" : searchField.getText().trim();
        if (text.isEmpty()) {
            customerSorter.setRowFilter(null);
        } else {
            customerSorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
        }
    }

    private String valueAt(int row, int column) {
        Object value = customerModel.getValueAt(row, column);
        return value == null ? "" : value.toString();
    }

    private String stripMoney(String value) {
        return value == null ? "" : value.replace("$", "").replace(",", "").trim();
    }

    private String money(BigDecimal value) {
        if (value == null) {
            value = BigDecimal.ZERO;
        }
        return String.format("$%.2f", value);
    }

    private BigDecimal parseMoney(String value, String fieldName) {
        try {
            return new BigDecimal(value.replace("$", "").replace(",", "").trim());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, fieldName + " must be a valid amount.");
            return null;
        }
    }

    private record AccountSnapshot(BigDecimal balance, BigDecimal creditLimit, boolean active) {
    }
}
