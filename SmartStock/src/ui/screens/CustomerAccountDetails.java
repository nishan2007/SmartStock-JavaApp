package ui.screens;

import utils.CurrencyFormatter;
import managers.PermissionManager;
import services.LanApiClient;
import ui.components.CustomerTypeSelector;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.UiTaskRunner;
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
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

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

        add(loadingState, BorderLayout.SOUTH);
        WindowHelper.configurePosWindow(this);
        loadAccountSnapshot();
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
            SessionDataCache.invalidate("customer-account:" + customerId);
            loadAccountSnapshot();
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
        addInfoField(grid, gbc, 3, 1, "Balance (All Stores):", balanceLabel);
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
                new Object[]{"Record", "Date", "Store", "User", "Device", "Drawer", "Activity", "Document", "Document #", "Method", "Reference", "Amount", "Payment Status", "Document Status", "Total", "Data Source", "Note"},
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
        transactionTable.getColumnModel().getColumn(14).setPreferredWidth(110);
        transactionTable.getColumnModel().getColumn(15).setPreferredWidth(80);
        transactionTable.getColumnModel().getColumn(16).setPreferredWidth(260);

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

    private void loadAccountSnapshot() {
        String cacheKey = "customer-account:" + customerId;
        CachedUiLoader.load(this, "customer-account.load", cacheKey, AccountSnapshot.class,
                SessionDataCache.SCREEN_TTL, loadingState, () -> {
                    var details = UiTaskRunner.supplyAsync(() -> LanApiClient.loadCustomerAccountDetails(customerId));
                    var transactions = UiTaskRunner.supplyAsync(() -> LanApiClient.loadCustomerTransactions(customerId));
                    return new AccountSnapshot(details.join(), transactions.join());
                }, this::applyAccountSnapshot);
    }

    private void applyAccountSnapshot(AccountSnapshot snapshot) {
                LanApiClient.CustomerAccountRecord account = snapshot.account();
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
        transactionModel.setRowCount(0);
            LanApiClient.CustomerTransactionResult result = snapshot.transactions();
            for(LanApiClient.CustomerTransactionRecord row:result.transactions())transactionModel.addRow(new Object[]{
                    formatRecord(row),formatTimestamp(row.createdAtEpochMillis()),row.storeName(),row.userName(),row.deviceName(),
                    row.cashDrawerName(),formatType(row.transactionType()),formatType(row.documentType()),row.documentNumber(),
                    row.paymentMethod(),row.paymentReference(),currencyFormat.format(defaultZero(row.amount())),formatStatus(row.paymentStatus()),
                    formatStatus(row.documentStatus()),currencyFormat.format(defaultZero(row.chargeTotal())),row.remote()?"Synced snapshot":"Live store data",row.note()});
            transactionSummaryLabel.setText("Transactions: "+result.count()+"    Charges: "+currencyFormat.format(result.totalCharges())
                    +"    Payments: "+currencyFormat.format(result.totalPayments()));
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

        LanApiClient.CustomerAccountSaveRequest request=new LanApiClient.CustomerAccountSaveRequest(customerId,accountNumber,name,
                    customerTypeId,phone,email,creditLimit,businessAccountCheckBox.isSelected(),activeCheckBox.isSelected(),notes);
            String fingerprint=request.toString();if(pendingSaveKey==null||!fingerprint.equals(pendingFingerprint)){
                pendingFingerprint=fingerprint;pendingSaveKey=UUID.randomUUID().toString();}
        String saveKey = pendingSaveKey;
        saveButton.setEnabled(false);
        UiTaskRunner.submit(this, "customer-account.save", () -> {
            LanApiClient.saveCustomerAccount(request, saveKey);
            return null;
        }, ignored -> {
            pendingSaveKey=null;pendingFingerprint=null;
            saveButton.setEnabled(true);
            JOptionPane.showMessageDialog(this, "Customer account updated.");
            if (afterSave != null) {
                afterSave.run();
            }
            SessionDataCache.invalidate("customer-account:" + customerId);
            loadAccountSnapshot();
        }, failure -> {
            saveButton.setEnabled(true);
            loadingState.failed(failure.getMessage(), true, this::saveAccountDetails);
        });
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

    private record AccountSnapshot(LanApiClient.CustomerAccountRecord account,
                                   LanApiClient.CustomerTransactionResult transactions) { }

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

    private String formatRecord(LanApiClient.CustomerTransactionRecord row){
        String event=row.eventId()==null?"":row.eventId();int last=event.lastIndexOf(':');String id=last>=0?event.substring(last+1):event;
        String kind=event.contains("LEDGER:")?"Account entry":event.contains("CUSTOM_ORDER:")?"Custom order":
                event.contains("QUOTATION:")?"Quotation":event.contains("INVOICE:")?"Invoice":event.contains("SALE:")?"Sale":"Record";
        return id.isBlank()?kind:kind+" #"+id;
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
