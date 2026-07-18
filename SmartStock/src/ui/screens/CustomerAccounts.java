package ui.screens;

import managers.PermissionManager;
import services.LanApiClient;
import ui.components.AppMenuBar;
import ui.components.CustomerTypeSelector;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.regex.Pattern;
import java.util.UUID;
import java.util.List;

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
    private String pendingSaveKey;
    private String pendingSaveFingerprint;
    private String pendingAdjustmentKey;
    private String pendingAdjustmentFingerprint;
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

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
        mainPanel.add(loadingState, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);

        loadCustomers();
        WindowHelper.configurePosWindow(this);
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
        creditLimitField = new JTextField("0");
        balanceField = new JTextField("0");
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
        CachedUiLoader.load(this, "customer-accounts:list", CustomerAccountsSnapshot.class,
                SessionDataCache.SCREEN_TTL, loadingState,
                () -> new CustomerAccountsSnapshot(LanApiClient.loadCustomerAccountRecords()),
                this::applyCustomers);
    }

    private void applyCustomers(CustomerAccountsSnapshot snapshot) {
        customerModel.setRowCount(0);
        for (LanApiClient.CustomerAccountRecord row : snapshot.rows()) {
                    customerModel.addRow(new Object[]{
                            row.customerId(),row.accountNumber(),row.name(),row.customerTypeId()==null?"":row.customerTypeId(),
                            row.customerTypeName(),row.phone(),row.email(),money(row.creditLimit()),money(row.currentBalance()),
                            money(row.availableCredit()),row.business()?"Business":"Personal",row.active(),row.accountNotes()
                    });
        }
    }

    private record CustomerAccountsSnapshot(List<LanApiClient.CustomerAccountRecord> rows) { }

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

    private void openPaymentReceipt(int customerId, int transactionId) {
        WindowHelper.showPosWindow(new AccountPaymentReceiptPreview(customerId, transactionId), this);
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

        LanApiClient.CustomerAccountSaveRequest request=new LanApiClient.CustomerAccountSaveRequest(null,null,name,customerTypeId,
                phone,email,creditLimit,businessAccount,activeCheckBox.isSelected(),accountNotes);
        String fingerprint=request.toString();
        try {
            if(pendingSaveKey==null||!fingerprint.equals(pendingSaveFingerprint)){pendingSaveKey=UUID.randomUUID().toString();pendingSaveFingerprint=fingerprint;}
            LanApiClient.SavedCustomerAccount saved=LanApiClient.saveCustomerAccount(request,pendingSaveKey);
            SessionDataCache.invalidate("customer-accounts:");
            pendingSaveKey=null;pendingSaveFingerprint=null;accountNumberField.setText(saved.accountNumber());
            JOptionPane.showMessageDialog(this, "Customer account added.");
            clearFields();
            loadCustomers();
        } catch (Exception ex) {
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

        LanApiClient.CustomerAccountSaveRequest request=new LanApiClient.CustomerAccountSaveRequest(selectedCustomerId,accountNumber,name,customerTypeId,
                phone,email,creditLimit,businessAccount,activeCheckBox.isSelected(),accountNotes);
        String fingerprint=request.toString();
        try {
            if(pendingSaveKey==null||!fingerprint.equals(pendingSaveFingerprint)){pendingSaveKey=UUID.randomUUID().toString();pendingSaveFingerprint=fingerprint;}
            LanApiClient.saveCustomerAccount(request,pendingSaveKey);SessionDataCache.invalidate("customer-accounts:");pendingSaveKey=null;pendingSaveFingerprint=null;
            JOptionPane.showMessageDialog(this, "Customer account updated.");
            loadCustomers();
        } catch (Exception ex) {
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

        String paymentMethod = null;
        String paymentReference = null;
        if (!addCharge) {
            paymentMethod = promptForPaymentMethod();
            if (paymentMethod == null) {
                return;
            }
            if (requiresPaymentReference(paymentMethod)) {
                paymentReference = JOptionPane.showInputDialog(this, "Enter payment reference:");
                if (paymentReference == null) {
                    return;
                }
                paymentReference = paymentReference.trim();
                if (paymentReference.isBlank()) {
                    JOptionPane.showMessageDialog(this, "Payment reference is required for card, cheque, and MMG payments.");
                    return;
                }
            }
        }

        LanApiClient.CustomerAccountAdjustmentRequest request=new LanApiClient.CustomerAccountAdjustmentRequest(selectedCustomerId,amount,
                addCharge?"CHARGE":"PAYMENT",paymentMethod,paymentReference);String fingerprint=request.toString();
        try {
            if(pendingAdjustmentKey==null||!fingerprint.equals(pendingAdjustmentFingerprint)){pendingAdjustmentKey=UUID.randomUUID().toString();pendingAdjustmentFingerprint=fingerprint;}
            LanApiClient.CustomerAccountAdjustmentResult result=LanApiClient.adjustCustomerAccount(request,pendingAdjustmentKey);
            SessionDataCache.invalidate("customer-accounts:");
            pendingAdjustmentKey=null;pendingAdjustmentFingerprint=null;
            if(addCharge)JOptionPane.showMessageDialog(this,"Charge added.");else{
                JOptionPane.showMessageDialog(this,"Payment recorded. Payment ID: "+result.paymentId());
                openPaymentReceipt(selectedCustomerId,(int)result.transactionId());
            }
            loadCustomers();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to update account balance: " + ex.getMessage());
        }
    }

    private void clearFields() {
        selectedCustomerId = null;
        accountNumberField.setText("Generated on save");
        accountNumberField.setEditable(false);
        nameField.setText("");
        phoneField.setText("");
        emailField.setText("");
        customerTypeSelector.clearSelection();
        creditLimitField.setText("0");
        balanceField.setText("0");
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

    private String promptForPaymentMethod() {
        String[] options = {"CASH", "CARD", "CHEQUE", "MMG", "ACCOUNT"};
        Object selected = JOptionPane.showInputDialog(
                this,
                "Select payment method:",
                "Payment Method",
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]
        );
        if (selected == null) {
            return null;
        }
        return selected.toString();
    }

    private boolean requiresPaymentReference(String paymentMethod) {
        return "CARD".equalsIgnoreCase(paymentMethod)
                || "CHEQUE".equalsIgnoreCase(paymentMethod)
                || "MMG".equalsIgnoreCase(paymentMethod);
    }

    private String stripMoney(String value) {
        return value == null ? "" : value.replace("$", "").replace(",", "").trim();
    }

    private String money(BigDecimal value) {
        value = defaultZero(value);
        return utils.CurrencyFormatter.format(value);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal parseMoney(String value, String fieldName) {
        try {
            return utils.CurrencyFormatter.normalize(new BigDecimal(value.replace("$", "").replace(",", "").trim()));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, fieldName + " must be a valid amount.");
            return null;
        }
    }

    private record CustomerSelection(int customerId, String accountLabel) {
    }
}
