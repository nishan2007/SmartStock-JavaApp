package ui.screens;

import data.DB;
import managers.PermissionManager;
import ui.components.AppMenuBar;
import ui.components.CustomerTypeSelector;
import ui.helpers.WindowHelper;

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
    private CustomerTypeSelector customerTypeSelector;
    private JTextField creditLimitField;
    private JTextField balanceField;
    private JTextArea accountNotesArea;
    private JCheckBox businessAccountCheckBox;
    private JCheckBox activeCheckBox;
    private JButton addButton;
    private JButton updateButton;
    private JButton clearButton;
    private JButton refreshButton;
    private JButton addChargeButton;
    private JButton recordPaymentButton;
    private JButton transactionHistoryButton;
    private JButton paymentHistoryButton;
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
        WindowHelper.showPosWindow(this);
    }

    private JPanel buildTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchField = new JTextField();
        searchPanel.add(new JLabel("Search Customers:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        customerModel = new DefaultTableModel(
                new Object[]{"ID", "Account #", "Name", "Customer Type ID", "Customer Type", "Phone", "Email", "Credit Limit", "Balance", "Available", "Account Type", "Active", "Notes"},
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
        hideColumn(customerTable, 3);
        customerTable.getColumnModel().getColumn(10).setMaxWidth(95);
        customerTable.getColumnModel().getColumn(11).setMaxWidth(70);
        customerTable.getColumnModel().getColumn(12).setPreferredWidth(220);

        customerTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedCustomer();
            }
        });
        customerTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    openAccountDetails();
                }
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
        accountNumberField.setEditable(false);
        nameField = new JTextField();
        phoneField = new JTextField();
        emailField = new JTextField();
        customerTypeSelector = new CustomerTypeSelector();
        creditLimitField = new JTextField("0.00");
        balanceField = new JTextField("0.00");
        balanceField.setEditable(false);
        accountNotesArea = new JTextArea(4, 20);
        accountNotesArea.setLineWrap(true);
        accountNotesArea.setWrapStyleWord(true);
        businessAccountCheckBox = new JCheckBox("Business Account");
        activeCheckBox = new JCheckBox("Active", true);

        addField(formPanel, gbc, 0, "Account #:", accountNumberField);
        addField(formPanel, gbc, 1, "Name:", nameField);
        addField(formPanel, gbc, 2, "Customer Type:", customerTypeSelector);
        addField(formPanel, gbc, 3, "Phone:", phoneField);
        addField(formPanel, gbc, 4, "Email:", emailField);
        addField(formPanel, gbc, 5, "Credit Limit:", creditLimitField);
        addField(formPanel, gbc, 6, "Current Balance:", balanceField);

        gbc.gridx = 0;
        gbc.gridy = 7;
        formPanel.add(new JLabel("Account Type:"), gbc);
        gbc.gridx = 1;
        formPanel.add(businessAccountCheckBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 8;
        formPanel.add(new JLabel("Status:"), gbc);
        gbc.gridx = 1;
        formPanel.add(activeCheckBox, gbc);

        JScrollPane notesScrollPane = new JScrollPane(accountNotesArea);
        notesScrollPane.setPreferredSize(new Dimension(0, 82));
        addField(formPanel, gbc, 9, "Notes:", notesScrollPane);

        JPanel buttonPanel = new JPanel(new GridLayout(5, 2, 8, 8));
        addButton = new JButton("Add Account");
        updateButton = new JButton("Update Account");
        clearButton = new JButton("Clear");
        refreshButton = new JButton("Refresh");
        JButton customerTypesButton = new JButton("Customer Types");
        addChargeButton = new JButton("Add Charge");
        recordPaymentButton = new JButton("Record Payment");
        transactionHistoryButton = new JButton("Details");
        paymentHistoryButton = new JButton("Payments");

        updateButton.setEnabled(false);
        addChargeButton.setEnabled(false);
        recordPaymentButton.setEnabled(false);
        transactionHistoryButton.setEnabled(false);
        paymentHistoryButton.setEnabled(false);

        buttonPanel.add(addButton);
        buttonPanel.add(updateButton);
        buttonPanel.add(addChargeButton);
        buttonPanel.add(recordPaymentButton);
        buttonPanel.add(transactionHistoryButton);
        buttonPanel.add(paymentHistoryButton);
        buttonPanel.add(customerTypesButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(refreshButton);

        addButton.addActionListener(e -> addCustomer());
        updateButton.addActionListener(e -> updateCustomer());
        customerTypesButton.addActionListener(e -> openCustomerTypes());
        clearButton.addActionListener(e -> clearFields());
        refreshButton.addActionListener(e -> loadCustomers());
        addChargeButton.addActionListener(e -> adjustBalance(true));
        recordPaymentButton.addActionListener(e -> adjustBalance(false));
        transactionHistoryButton.addActionListener(e -> openAccountDetails());
        paymentHistoryButton.addActionListener(e -> openPaymentHistory());

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

    private void hideColumn(JTable table, int columnIndex) {
        table.getColumnModel().getColumn(columnIndex).setMinWidth(0);
        table.getColumnModel().getColumn(columnIndex).setMaxWidth(0);
        table.getColumnModel().getColumn(columnIndex).setPreferredWidth(0);
    }

    private void loadCustomers() {
        customerModel.setRowCount(0);
        String sql = """
                SELECT ca.customer_id,
                       ca.account_number,
                       ca.name AS customer_name,
                       ca.phone AS customer_phone,
                       ca.email AS customer_email,
                       ca.credit_limit,
                       ca.current_balance,
                       (ca.credit_limit - ca.current_balance) AS available_credit,
                       COALESCE(ca.is_business, FALSE) AS is_business,
                       ca.is_active,
                       COALESCE(ca.account_notes, '') AS account_notes,
                       ca.customer_type_id,
                       COALESCE(ct.name, '') AS customer_type_name
                FROM customer_accounts ca
                LEFT JOIN customer_types ct ON ct.customer_type_id = ca.customer_type_id
                ORDER BY ca.name
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                customerModel.addRow(new Object[]{
                        rs.getInt("customer_id"),
                        rs.getString("account_number"),
                        rs.getString("customer_name"),
                        rs.getObject("customer_type_id") == null ? "" : rs.getInt("customer_type_id"),
                        rs.getString("customer_type_name"),
                        rs.getString("customer_phone"),
                        rs.getString("customer_email"),
                        money(rs.getBigDecimal("credit_limit")),
                        money(rs.getBigDecimal("current_balance")),
                        money(rs.getBigDecimal("available_credit")),
                        rs.getBoolean("is_business") ? "Business" : "Personal",
                        rs.getBoolean("is_active"),
                        rs.getString("account_notes")
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
        accountNumberField.setEditable(PermissionManager.hasPermission("EDIT_ACCOUNT_NUMBER"));
        nameField.setText(valueAt(modelRow, 2));
        customerTypeSelector.setSelectedCustomerType(parseNullableInt(valueAt(modelRow, 3)), valueAt(modelRow, 4));
        phoneField.setText(valueAt(modelRow, 5));
        emailField.setText(valueAt(modelRow, 6));
        creditLimitField.setText(stripMoney(valueAt(modelRow, 7)));
        balanceField.setText(stripMoney(valueAt(modelRow, 8)));
        businessAccountCheckBox.setSelected("Business".equalsIgnoreCase(valueAt(modelRow, 10)));
        activeCheckBox.setSelected(Boolean.TRUE.equals(customerModel.getValueAt(modelRow, 11)));
        accountNotesArea.setText(valueAt(modelRow, 12));
        updateButton.setEnabled(true);
        addChargeButton.setEnabled(true);
        recordPaymentButton.setEnabled(true);
        transactionHistoryButton.setEnabled(true);
        paymentHistoryButton.setEnabled(true);
    }

    private void openAccountDetails() {
        CustomerSelection selection = getSelectedCustomer();
        if (selection == null) {
            JOptionPane.showMessageDialog(this, "Select a customer first.");
            return;
        }
        CustomerAccountDetails details = new CustomerAccountDetails(selection.customerId(), this::loadCustomers);
        WindowHelper.showPosWindow(details, this);
    }

    private void openPaymentHistory() {
        CustomerSelection selection = getSelectedCustomer();
        if (selection == null) {
            JOptionPane.showMessageDialog(this, "Select a customer first.");
            return;
        }
        CustomerPaymentHistory history = new CustomerPaymentHistory(selection.customerId(), selection.accountLabel());
        WindowHelper.showPosWindow(history, this);
    }

    private void openCustomerTypes() {
        if (WindowHelper.focusIfAlreadyOpen(CustomerTypeList.class)) {
            return;
        }
        WindowHelper.showPosWindow(new CustomerTypeList(), this);
    }

    private CustomerSelection getSelectedCustomer() {
        int row = customerTable.getSelectedRow();
        if (row == -1) {
            return null;
        }
        int modelRow = customerTable.convertRowIndexToModel(row);
        int customerId = Integer.parseInt(String.valueOf(customerModel.getValueAt(modelRow, 0)));
        String accountNumber = valueAt(modelRow, 1);
        String name = valueAt(modelRow, 2);
        String accountLabel = accountNumber.isBlank() ? name : accountNumber + " - " + name;
        return new CustomerSelection(customerId, accountLabel);
    }

    private void addCustomer() {
        String name = nameField.getText().trim();
        String phone = phoneField.getText().trim();
        String email = emailField.getText().trim();
        String accountNotes = accountNotesArea.getText().trim();
        Integer customerTypeId = customerTypeSelector.getSelectedCustomerTypeId();
        if (customerTypeId == null && !customerTypeSelector.getSelectedCustomerTypeName().isBlank()) {
            return;
        }
        boolean businessAccount = businessAccountCheckBox.isSelected();
        BigDecimal creditLimit = parseMoney(creditLimitField.getText().trim(), "Credit limit");
        if (creditLimit == null) {
            return;
        }
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Customer name is required.");
            return;
        }

        String accountNumber = generateNextAccountNumber(businessAccount);
        if (accountNumber == null) {
            return;
        }
        accountNumberField.setText(accountNumber);

        String sql = """
                INSERT INTO customer_accounts (account_number, name, customer_type_id, phone, email, credit_limit, current_balance, is_business, is_active, account_notes)
                VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?)
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, accountNumber.isEmpty() ? null : accountNumber);
            ps.setString(2, name);
            setNullableInteger(ps, 3, customerTypeId);
            ps.setString(4, phone.isEmpty() ? null : phone);
            ps.setString(5, email.isEmpty() ? null : email);
            ps.setBigDecimal(6, creditLimit);
            ps.setBoolean(7, businessAccount);
            ps.setBoolean(8, activeCheckBox.isSelected());
            ps.setString(9, accountNotes.isEmpty() ? null : accountNotes);
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
        String accountNotes = accountNotesArea.getText().trim();
        Integer customerTypeId = customerTypeSelector.getSelectedCustomerTypeId();
        if (customerTypeId == null && !customerTypeSelector.getSelectedCustomerTypeName().isBlank()) {
            return;
        }
        boolean businessAccount = businessAccountCheckBox.isSelected();
        BigDecimal creditLimit = parseMoney(creditLimitField.getText().trim(), "Credit limit");
        if (creditLimit == null) {
            return;
        }
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Customer name is required.");
            return;
        }

        if (!PermissionManager.hasPermission("EDIT_ACCOUNT_NUMBER")) {
            accountNumber = valueAt(customerTable.convertRowIndexToModel(customerTable.getSelectedRow()), 1);
        }
        if (accountNumber.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Account number is required.");
            return;
        }

        String sql = """
                UPDATE customer_accounts
                SET account_number = ?,
                    name = ?,
                    customer_type_id = ?,
                    phone = ?,
                    email = ?,
                    credit_limit = ?,
                    is_business = ?,
                    is_active = ?,
                    account_notes = ?
                WHERE customer_id = ?
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, accountNumber.isEmpty() ? null : accountNumber);
            ps.setString(2, name);
            setNullableInteger(ps, 3, customerTypeId);
            ps.setString(4, phone.isEmpty() ? null : phone);
            ps.setString(5, email.isEmpty() ? null : email);
            ps.setBigDecimal(6, creditLimit);
            ps.setBoolean(7, businessAccount);
            ps.setBoolean(8, activeCheckBox.isSelected());
            ps.setString(9, accountNotes.isEmpty() ? null : accountNotes);
            ps.setInt(10, selectedCustomerId);
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
                AccountTransaction transaction = insertAccountTransaction(
                        conn,
                        selectedCustomerId,
                        signedAmount,
                        addCharge ? "MANUAL_CHARGE" : "PAYMENT",
                        addCharge ? "Manual account charge" : "Customer payment",
                        null
                );
                if (!addCharge) {
                    String allocationNote = applyPaymentToUnpaidSales(conn, selectedCustomerId, amount, transaction.transactionId());
                    updateTransactionNote(conn, transaction.transactionId(), allocationNote);
                }
                conn.commit();
                if (addCharge) {
                    JOptionPane.showMessageDialog(this, "Charge added.");
                } else {
                    JOptionPane.showMessageDialog(this, "Payment recorded. Payment ID: " + transaction.paymentId());
                }
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

    private String applyPaymentToUnpaidSales(Connection conn, int customerId, BigDecimal paymentAmount, int paymentTransactionId) throws SQLException {
        BigDecimal remainingPayment = paymentAmount;
        StringBuilder appliedSales = new StringBuilder();
        String selectSql = """
                SELECT sale_id,
                       GREATEST(COALESCE(total_amount, 0) - COALESCE(returned_amount, 0), 0) AS total_amount,
                       COALESCE(amount_paid, 0) AS amount_paid
                FROM sales
                WHERE customer_id = ?
                  AND payment_method = 'ACCOUNT'
                  AND COALESCE(payment_status, 'PAID') <> 'PAID'
                ORDER BY created_at ASC, sale_id ASC
                FOR UPDATE
                """;
        String updateSql = """
                UPDATE sales
                SET amount_paid = ?,
                    payment_status = ?
                WHERE sale_id = ?
                """;
        String allocationSql = """
                INSERT INTO customer_account_payment_allocations (payment_transaction_id, customer_id, sale_id, amount)
                VALUES (?, ?, ?, ?)
                """;

        try (PreparedStatement selectPs = conn.prepareStatement(selectSql);
             PreparedStatement updatePs = conn.prepareStatement(updateSql);
             PreparedStatement allocationPs = conn.prepareStatement(allocationSql)) {

            selectPs.setInt(1, customerId);
            try (ResultSet rs = selectPs.executeQuery()) {
                while (rs.next() && remainingPayment.compareTo(BigDecimal.ZERO) > 0) {
                    int saleId = rs.getInt("sale_id");
                    BigDecimal totalAmount = defaultZero(rs.getBigDecimal("total_amount"));
                    BigDecimal amountPaid = defaultZero(rs.getBigDecimal("amount_paid"));
                    BigDecimal amountDue = totalAmount.subtract(amountPaid);

                    if (amountDue.compareTo(BigDecimal.ZERO) <= 0) {
                        updateSalePaymentStatus(updatePs, saleId, totalAmount, "PAID");
                        continue;
                    }

                    BigDecimal appliedAmount = remainingPayment.min(amountDue);
                    BigDecimal newAmountPaid = amountPaid.add(appliedAmount);
                    String paymentStatus = newAmountPaid.compareTo(totalAmount) >= 0 ? "PAID" : "UNPAID";
                    updateSalePaymentStatus(updatePs, saleId, newAmountPaid, paymentStatus);
                    insertPaymentAllocation(allocationPs, paymentTransactionId, customerId, saleId, appliedAmount);
                    if (!appliedSales.isEmpty()) {
                        appliedSales.append("; ");
                    }
                    appliedSales.append("sale #")
                            .append(saleId)
                            .append(" ")
                            .append(money(appliedAmount));
                    remainingPayment = remainingPayment.subtract(appliedAmount);
                }
            }
        }

        if (appliedSales.isEmpty()) {
            return "Customer payment. No unpaid account sales were available to apply this payment to.";
        }
        if (remainingPayment.compareTo(BigDecimal.ZERO) > 0) {
            appliedSales.append("; unapplied ")
                    .append(money(remainingPayment));
        }
        return "Customer payment applied to " + appliedSales;
    }

    private void updateSalePaymentStatus(PreparedStatement ps, int saleId, BigDecimal amountPaid, String paymentStatus) throws SQLException {
        ps.setBigDecimal(1, amountPaid);
        ps.setString(2, paymentStatus);
        ps.setInt(3, saleId);
        ps.executeUpdate();
    }

    private void insertPaymentAllocation(PreparedStatement ps, int paymentTransactionId, int customerId, int saleId, BigDecimal amount) throws SQLException {
        ps.setInt(1, paymentTransactionId);
        ps.setInt(2, customerId);
        ps.setInt(3, saleId);
        ps.setBigDecimal(4, amount);
        ps.executeUpdate();
    }

    private void updateTransactionNote(Connection conn, int transactionId, String note) throws SQLException {
        String sql = "UPDATE customer_account_transactions SET note = ? WHERE transaction_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, note);
            ps.setInt(2, transactionId);
            ps.executeUpdate();
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

    private AccountTransaction insertAccountTransaction(Connection conn, int customerId, BigDecimal amount, String type, String note, Integer saleId) throws SQLException {
        String sql = """
                INSERT INTO customer_account_transactions (customer_id, sale_id, amount, transaction_type, note, user_name)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, customerId);
            if (saleId == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, saleId);
            }
            ps.setBigDecimal(3, amount);
            ps.setString(4, type);
            ps.setString(5, note);
            ps.setString(6, managers.SessionManager.getCurrentUserDisplayName());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int transactionId = rs.getInt(1);
                    String paymentId = null;
                    if ("PAYMENT".equals(type)) {
                        paymentId = generatePaymentId(transactionId);
                        updateTransactionPaymentId(conn, transactionId, paymentId);
                    }
                    return new AccountTransaction(transactionId, paymentId);
                }
            }
        }

        throw new SQLException("Failed to create account transaction.");
    }

    private String generatePaymentId(int transactionId) {
        return String.format("PAY-%06d", transactionId);
    }

    private void updateTransactionPaymentId(Connection conn, int transactionId, String paymentId) throws SQLException {
        String sql = "UPDATE customer_account_transactions SET payment_id = ? WHERE transaction_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, paymentId);
            ps.setInt(2, transactionId);
            ps.executeUpdate();
        }
    }

    private void clearFields() {
        selectedCustomerId = null;
        accountNumberField.setText("");
        accountNumberField.setEditable(false);
        nameField.setText("");
        phoneField.setText("");
        emailField.setText("");
        customerTypeSelector.clearSelection();
        creditLimitField.setText("0.00");
        balanceField.setText("0.00");
        accountNotesArea.setText("");
        businessAccountCheckBox.setSelected(false);
        activeCheckBox.setSelected(true);
        updateButton.setEnabled(false);
        addChargeButton.setEnabled(false);
        recordPaymentButton.setEnabled(false);
        transactionHistoryButton.setEnabled(false);
        paymentHistoryButton.setEnabled(false);
        customerTable.clearSelection();
        nameField.requestFocusInWindow();
    }

    private String generateNextAccountNumber(boolean businessAccount) {
        String prefix = businessAccount ? "BA" : "CA";
        String sql = """
                SELECT COALESCE(MAX(CAST(SUBSTRING(account_number FROM 4) AS INTEGER)), 0) + 1 AS next_number
                FROM customer_accounts
                WHERE account_number ~ ?
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, "^" + prefix + "-[0-9]+$");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return String.format("%s-%06d", prefix, rs.getInt("next_number"));
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to generate account number: " + ex.getMessage());
        }

        return null;
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

    private Integer parseNullableInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
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

    private record AccountSnapshot(BigDecimal balance, BigDecimal creditLimit, boolean active) {
    }

    private record AccountTransaction(int transactionId, String paymentId) {
    }

    private record CustomerSelection(int customerId, String accountLabel) {
    }
}
