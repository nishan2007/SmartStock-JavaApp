package ui.screens;

import utils.CurrencyFormatter;
import services.LanApiClient;
import ui.components.AppMenuBar;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.WindowHelper;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;

public class CustomerTransactionHistory extends JFrame {
    private final int customerId;
    private final String customerLabel;
    private final NumberFormat currencyFormat = CurrencyFormatter.create();
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DefaultTableModel transactionModel;
    private JLabel summaryLabel;
    private final LoadingStatePanel loadingState=new LoadingStatePanel();

    public CustomerTransactionHistory(int customerId, String customerLabel) {
        this.customerId = customerId;
        this.customerLabel = customerLabel == null ? "Customer Account" : customerLabel;

        setTitle("Customer Transaction History");
        setSize(1050, 620);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "CustomerTransactionHistory"));
        setLayout(new BorderLayout(12, 12));

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(new EmptyBorder(14, 14, 14, 14));
        add(mainPanel, BorderLayout.CENTER);

        mainPanel.add(buildHeaderPanel(), BorderLayout.NORTH);
        mainPanel.add(buildTablePanel(), BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout());footer.add(loadingState,BorderLayout.NORTH);footer.add(buildSummaryPanel(),BorderLayout.SOUTH);mainPanel.add(footer, BorderLayout.SOUTH);

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
        transactionTable.getColumnModel().getColumn(16).setPreferredWidth(240);

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
        CachedUiLoader.load(this,"customer-transactions:"+customerId,LanApiClient.CustomerTransactionResult.class,SessionDataCache.SCREEN_TTL,loadingState,()->LanApiClient.loadCustomerTransactions(customerId),result->{transactionModel.setRowCount(0);
            for(LanApiClient.CustomerTransactionRecord row:result.transactions()){
                    transactionModel.addRow(new Object[]{
                            formatRecord(row),formatTimestamp(row.createdAtEpochMillis()),row.storeName(),row.userName(),
                            row.deviceName(),row.cashDrawerName(),formatType(row.transactionType()),formatType(row.documentType()),
                            row.documentNumber(),row.paymentMethod(),row.paymentReference(),currencyFormat.format(defaultZero(row.amount())),
                            formatStatus(row.paymentStatus()),formatStatus(row.documentStatus()),currencyFormat.format(defaultZero(row.chargeTotal())),
                            row.remote()?"Synced snapshot":"Live store data",row.note()
                    });
            }
            summaryLabel.setText("Transactions: "+result.count()+"    Charges: "+currencyFormat.format(result.totalCharges())
                    +"    Payments: "+currencyFormat.format(result.totalPayments()));
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

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
