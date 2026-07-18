package ui.screens;

import utils.CurrencyFormatter;
import managers.PermissionManager;
import services.LanApiClient;
import ui.components.CustomerTypeSelector;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class CustomerAccountDetails extends JFrame {
    private final int customerId;
    private final Runnable afterSave;
    private final boolean canSetCreditLimit;
    private final boolean canEditAccountNumber;
    private final NumberFormat currencyFormat = CurrencyFormatter.create();
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private JLabel titleLabel;
    private JTextField accountNumberField;
    private JTextField nameField;
    private JTextField phoneField;
    private JTextField emailField;
    private CustomerTypeSelector customerTypeSelector;
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
    private String pendingSaveKey;
    private String pendingFingerprint;

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
        customerTypeSelector = new CustomerTypeSelector();
        businessAccountCheckBox = new JCheckBox("Business Account");
        activeCheckBox = new JCheckBox("Active");
        balanceLabel = new JLabel();
        availableCreditLabel = new JLabel();
        creditLimitField = new JTextField();
        creditLimitField.setEditable(canSetCreditLimit);
        saveButton = new JButton("Save Changes");

        addInfoField(grid, gbc, 0, 0, "Account #:", accountNumberField);
        addInfoField(grid, gbc, 0, 1, "Name:", nameField);
        addInfoField(grid, gbc, 1, 0, "Customer Type:", customerTypeSelector);
        addInfoField(grid, gbc, 1, 1, "Phone:", phoneField);
        addInfoField(grid, gbc, 2, 0, "Email:", emailField);
        addInfoField(grid, gbc, 2, 1, "Type:", businessAccountCheckBox);
        addInfoField(grid, gbc, 3, 0, "Status:", activeCheckBox);
        addInfoField(grid, gbc, 3, 1, "Balance:", balanceLabel);
        addInfoField(grid, gbc, 4, 0, "Available Credit:", availableCreditLabel);

        gbc.gridx = 0;
        gbc.gridy = 5;
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
        transactionTable.getColumnModel().getColumn(14).setPreferredWidth(260);

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
        try {
                LanApiClient.CustomerAccountRecord account=LanApiClient.loadCustomerAccountDetails(customerId);
                String accountNumber=text(account.accountNumber());String name=text(account.name());
                customerLabel = accountNumber.isBlank() ? name : accountNumber + " - " + name;

                titleLabel.setText(customerLabel);
                accountNumberField.setText(accountNumber);
                nameField.setText(name);
                customerTypeSelector.setSelectedCustomerType(
                        account.customerTypeId(),account.customerTypeName()
                );
                phoneField.setText(text(account.phone()));emailField.setText(text(account.email()));businessAccountCheckBox.setSelected(account.business());
                activeCheckBox.setSelected(account.active());balanceLabel.setText(money(account.currentBalance()));availableCreditLabel.setText(money(account.availableCredit()));
                creditLimitField.setText(stripMoney(money(account.creditLimit())));notesArea.setText(account.accountNotes());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,"Failed to load account details: "+ex.getMessage(),"SmartStock Server Error",JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadTransactions() {
        transactionModel.setRowCount(0);
        try {
            LanApiClient.CustomerTransactionResult result=LanApiClient.loadCustomerTransactions(customerId);
            for(LanApiClient.CustomerTransactionRecord row:result.transactions())transactionModel.addRow(new Object[]{
                    row.transactionId(),row.paymentId(),formatTimestamp(row.createdAtEpochMillis()),row.userName(),row.deviceName(),
                    row.cashDrawerName(),formatType(row.transactionType()),row.paymentMethod(),row.paymentReference(),
                    row.saleId()==null?"":row.saleId(),row.customOrderId()==null?"":row.customOrderId(),
                    currencyFormat.format(defaultZero(row.amount())),formatStatus(row.paymentStatus()),
                    currencyFormat.format(defaultZero(row.chargeTotal())),row.note()});
            transactionSummaryLabel.setText("Transactions: "+result.count()+"    Charges: "+currencyFormat.format(result.totalCharges())
                    +"    Payments: "+currencyFormat.format(result.totalPayments()));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,"Failed to load transaction history: "+ex.getMessage(),"SmartStock Server Error",JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveAccountDetails() {
        String accountNumber = accountNumberField.getText().trim();
        String name = nameField.getText().trim();
        String phone = phoneField.getText().trim();
        String email = emailField.getText().trim();
        String notes = notesArea.getText().trim();
        Integer customerTypeId = customerTypeSelector.getSelectedCustomerTypeId();
        if (customerTypeId == null && !customerTypeSelector.getSelectedCustomerTypeName().isBlank()) {
            return;
        }

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

        try {
            LanApiClient.CustomerAccountSaveRequest request=new LanApiClient.CustomerAccountSaveRequest(customerId,accountNumber,name,
                    customerTypeId,phone,email,creditLimit,businessAccountCheckBox.isSelected(),activeCheckBox.isSelected(),notes);
            String fingerprint=request.toString();if(pendingSaveKey==null||!fingerprint.equals(pendingFingerprint)){
                pendingFingerprint=fingerprint;pendingSaveKey=UUID.randomUUID().toString();}
            LanApiClient.saveCustomerAccount(request,pendingSaveKey);pendingSaveKey=null;pendingFingerprint=null;
            JOptionPane.showMessageDialog(this, "Customer account updated.");
            if (afterSave != null) {
                afterSave.run();
            }
            loadDetails();
            loadTransactions();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,"Failed to update customer account: "+ex.getMessage(),"SmartStock Server Error",JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openPaymentHistory() {
        CustomerPaymentHistory paymentHistory = new CustomerPaymentHistory(customerId, customerLabel);
        WindowHelper.showPosWindow(paymentHistory, this);
    }

    private String formatTimestamp(long epochMillis) {
        if (epochMillis <= 0) {
            return "";
        }
        return java.time.Instant.ofEpochMilli(epochMillis).atZone(StoreTimeZoneHelper.getStoreZone()).format(dateTimeFormatter);
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

    private String text(String value) {
        return value == null ? "" : value;
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
            return new BigDecimal(value.replace("$", "").replace(",", "").trim());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, fieldName + " must be a valid amount.");
            return null;
        }
    }

}
