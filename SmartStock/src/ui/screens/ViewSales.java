
package ui.screens;

import Receipt.ReceiptBuilder;
import utils.CurrencyFormatter;
import managers.PermissionManager;
import managers.SessionManager;
import services.LanApiClient;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.UiDebouncer;
import ui.helpers.UiTaskRunner;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class ViewSales extends JFrame {

    private JTable salesTable;
    private DefaultTableModel salesTableModel;
    private JTextField searchField;
    private JTextField fromDateField;
    private JTextField toDateField;
    private JComboBox<StoreChoice> storeFilter;
    private boolean loadingStores;
    private List<LanApiClient.SalesHistoryRow> displayedRows=List.of();
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

    private final DateTimeFormatter displayDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final NumberFormat currencyFormat = CurrencyFormatter.create();

    public ViewSales() {
        setTitle("View Sales");
        setSize(1100, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(12, 12));

        setJMenuBar(AppMenuBar.create(this,"ViewSales", loadingState));

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(new EmptyBorder(14, 14, 14, 14));
        add(mainPanel, BorderLayout.CENTER);

        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.add(buildHeaderPanel(), BorderLayout.NORTH);
        topPanel.add(buildFilterPanel(), BorderLayout.CENTER);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        salesTableModel = new DefaultTableModel(
                new Object[]{"Sale ID", "Receipt #", "Date / Time", "Cashier", "Store", "Items", "Payment", "Payment Status", "Paid", "Returned", "Discount", "Total", "Net"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        salesTable = new JTable(salesTableModel);
        salesTable.setRowHeight(26);
        salesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        salesTable.getTableHeader().setReorderingAllowed(false);
        salesTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        salesTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        salesTable.getColumnModel().getColumn(2).setPreferredWidth(170);
        salesTable.getColumnModel().getColumn(3).setPreferredWidth(180);
        salesTable.getColumnModel().getColumn(4).setPreferredWidth(160);
        salesTable.getColumnModel().getColumn(5).setPreferredWidth(80);
        salesTable.getColumnModel().getColumn(6).setPreferredWidth(120);
        salesTable.getColumnModel().getColumn(7).setPreferredWidth(120);
        salesTable.getColumnModel().getColumn(8).setPreferredWidth(120);
        salesTable.getColumnModel().getColumn(9).setPreferredWidth(120);
        salesTable.getColumnModel().getColumn(10).setPreferredWidth(120);
        salesTable.getColumnModel().getColumn(11).setPreferredWidth(120);
        salesTable.getColumnModel().getColumn(12).setPreferredWidth(120);

        JScrollPane scrollPane = new JScrollPane(salesTable);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        loadSales();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());

        JLabel titleLabel = new JLabel("Previous Transactions");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 24));

        JLabel subtitleLabel = new JLabel("Search and review completed sales.");
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(4));
        textPanel.add(subtitleLabel);

        headerPanel.add(textPanel, BorderLayout.WEST);
        return headerPanel;
    }

    private JPanel buildFilterPanel() {
        JPanel filterPanel = new JPanel(new GridBagLayout());
        filterPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 210, 210), 1, true),
                new EmptyBorder(12, 12, 12, 12)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        JLabel searchLabel = new JLabel("Search");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        filterPanel.add(searchLabel, gbc);

        searchField = new JTextField();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1;
        filterPanel.add(searchField, gbc);

        JLabel fromLabel = new JLabel("From Date");
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.weightx = 0;
        filterPanel.add(fromLabel, gbc);

        fromDateField = new JTextField();
        fromDateField.setToolTipText("yyyy-MM-dd");
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.weightx = 0.35;
        filterPanel.add(fromDateField, gbc);

        JLabel toLabel = new JLabel("To Date");
        gbc.gridx = 4;
        gbc.gridy = 0;
        gbc.weightx = 0;
        filterPanel.add(toLabel, gbc);

        toDateField = new JTextField();
        toDateField.setToolTipText("yyyy-MM-dd");
        gbc.gridx = 5;
        gbc.gridy = 0;
        gbc.weightx = 0.35;
        filterPanel.add(toDateField, gbc);

        if(canViewMultistore()){
            gbc.gridx=0;gbc.gridy=1;gbc.weightx=0;filterPanel.add(new JLabel("Store"),gbc);
            storeFilter=new JComboBox<>();storeFilter.addItem(new StoreChoice(SessionManager.getCurrentLocationId(),"Current Store",false));
            gbc.gridx=1;gbc.gridy=1;gbc.weightx=0.5;filterPanel.add(storeFilter,gbc);
            storeFilter.addActionListener(e->{if(!loadingStores)loadSales();});
        }

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton refreshButton = new JButton("Refresh");
        JButton clearButton = new JButton("Clear Filters");
        JButton detailsButton = new JButton("View Details");
        JButton reprintReceiptButton = new JButton("Reprint Receipt");
        JButton returnButton = new JButton("Process Return");

        buttonPanel.add(refreshButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(detailsButton);
        buttonPanel.add(reprintReceiptButton);
        buttonPanel.add(returnButton);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 6;
        gbc.weightx = 1;
        filterPanel.add(buttonPanel, gbc);

        refreshButton.addActionListener(e -> loadSales());
        clearButton.addActionListener(e -> clearFilters());
        detailsButton.addActionListener(e -> showSelectedSaleDetails());
        reprintReceiptButton.addActionListener(e -> reprintSelectedReceipt());
        returnButton.addActionListener(e -> openReturnForSelectedSale());
        returnButton.setEnabled(PermissionManager.hasPermission("PROCESS_RETURNS"));

        searchField.addActionListener(e -> loadSales());
        UiDebouncer.bind(searchField, 300, this::loadSales);
        fromDateField.addActionListener(e -> loadSales());
        toDateField.addActionListener(e -> loadSales());

        return filterPanel;
    }

    private void clearFilters() {
        searchField.setText("");
        fromDateField.setText("");
        toDateField.setText("");
        loadSales();
    }

    private void loadSales() {
        LocalDate fromDate = parseDate(fromDateField.getText().trim(), "From Date");
        if (fromDate == null && !fromDateField.getText().trim().isEmpty()) return;
        LocalDate toDate = parseDate(toDateField.getText().trim(), "To Date");
        if (toDate == null && !toDateField.getText().trim().isEmpty()) return;
        String search = searchField.getText().trim();
        String from = fromDate == null ? "" : fromDate.toString();
        String to = toDate == null ? "" : toDate.toString();
        String cacheKey = "view-sales:" + search + ":" + from + ":" + to;
        StoreChoice selected=storeFilter==null?null:(StoreChoice)storeFilter.getSelectedItem();
        Integer locationId=selected==null?SessionManager.getCurrentLocationId():selected.locationId();boolean all=selected!=null&&selected.allStores();
        cacheKey += ":"+(all?"all":locationId);
        CachedUiLoader.load(this, "view-sales.search", cacheKey, SalesSnapshot.class, SessionDataCache.SCREEN_TTL,
                loadingState,
                () -> new SalesSnapshot(LanApiClient.loadSalesHistory(search,from,to,locationId,all)),
                this::applySales);
    }

    private void applySales(SalesSnapshot snapshot) {
        salesTableModel.setRowCount(0);
        displayedRows=snapshot.result().transactions();
        populateStores(snapshot.result());
        for (LanApiClient.SalesHistoryRow row : displayedRows) {
                boolean returnRow = "RETURN".equalsIgnoreCase(row.transactionType());
                salesTableModel.addRow(new Object[]{
                        row.saleId(),
                        returnRow ? row.receiptNumber() + " / " + (row.returnReceiptNumber()==null||row.returnReceiptNumber().isBlank()?"Return #"+row.returnId():row.returnReceiptNumber()) : row.receiptNumber(),
                        formatEpoch(row.createdAtEpochMillis()),
                        row.cashierName(),
                        row.storeName(),
                        row.itemCount(),
                        row.paymentMethod(),
                        formatPaymentStatus(row.paymentStatus()),
                        currencyFormat.format(zero(row.amountPaid())),
                        currencyFormat.format(zero(row.returnedAmount())),
                        currencyFormat.format(zero(row.discountAmount())),
                        currencyFormat.format(zero(row.totalAmount())),
                        currencyFormat.format(zero(row.netAmount()))
                });
        }
    }

    private record SalesSnapshot(LanApiClient.SalesHistoryResult result) { }

    private void showSelectedSaleDetails() {
        int selectedRow = salesTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this,
                    "Please select a transaction first.",
                    "No Transaction Selected",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int modelRow = salesTable.convertRowIndexToModel(selectedRow);
        LanApiClient.SalesHistoryRow selected=displayedRows.get(modelRow);
        showSaleDetailsDialog(selected.saleId(),selected.sourceLocationId());
    }

    private void openReturnForSelectedSale() {
        if (!PermissionManager.requirePermission("PROCESS_RETURNS", this, "Process Returns")) {
            return;
        }

        int selectedRow = salesTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this,
                    "Please select a transaction first.",
                    "No Transaction Selected",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int modelRow = salesTable.convertRowIndexToModel(selectedRow);
        LanApiClient.SalesHistoryRow selected=displayedRows.get(modelRow);
        boolean remote=selected.sourceLocationId()!=null&&!selected.sourceLocationId().equals(SessionManager.getCurrentLocationId());
        if(remote&&!PermissionManager.hasPermission("PROCESS_MULTI_STORE_RETURNS")){
            JOptionPane.showMessageDialog(this,"You do not have permission to return another store's sale.");return;
        }
        WindowHelper.showPosWindow(new ReturnSale(selected.saleId(),selected.sourceLocationId()), this);
    }

    private void reprintSelectedReceipt() {
        int selectedRow = salesTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a transaction first.",
                    "No Transaction Selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int modelRow = salesTable.convertRowIndexToModel(selectedRow);
        LanApiClient.SalesHistoryRow selected = displayedRows.get(modelRow);
        boolean remote = selected.sourceLocationId() != null
                && !selected.sourceLocationId().equals(SessionManager.getCurrentLocationId());
        if (remote) {
            JOptionPane.showMessageDialog(this,
                    "Receipts can only be reprinted at the store where the sale was completed.",
                    "Remote Sale Receipt", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        loadingState.loading(false, java.time.Instant.now());
        UiTaskRunner.submit(this, "sales.receipt-reprint",
                () -> ReceiptBuilder.loadSaleReceipt(selected.saleId(), null, null),
                receipt -> {
                    loadingState.ready(java.time.Instant.now());
                    WindowHelper.showPosWindow(new ReceiptPreview(receipt, true), this);
                }, failure -> loadingState.failed(failure.getMessage(), false, this::reprintSelectedReceipt));
    }

    private void showSaleDetailsDialog(int saleId,Integer sourceLocationId) {
        loadingState.loading(false, java.time.Instant.now());
        UiTaskRunner.submit(this, "sales.details", () -> LanApiClient.loadSaleHistoryDetails(saleId,sourceLocationId),
                details -> {
                    loadingState.ready(java.time.Instant.now());
                    renderSaleDetailsDialog(saleId, details);
                }, failure -> loadingState.failed(failure.getMessage(), false,
                        () -> showSaleDetailsDialog(saleId,sourceLocationId)));
    }

    private boolean canViewMultistore(){return PermissionManager.hasPermission("VIEW_SALES")&&PermissionManager.hasPermission("VIEW_MULTI_STORE_SALES");}
    private void populateStores(LanApiClient.SalesHistoryResult result){if(storeFilter==null)return;StoreChoice selected=(StoreChoice)storeFilter.getSelectedItem();loadingStores=true;try{storeFilter.removeAllItems();storeFilter.addItem(new StoreChoice(result.currentLocationId(),"Current Store",false));storeFilter.addItem(new StoreChoice(null,"All Stores",true));for(var store:result.stores())storeFilter.addItem(new StoreChoice(store.locationId(),store.name()+("CURRENT".equals(store.status())?"":" ("+store.status()+")"),false));if(selected!=null)for(int i=0;i<storeFilter.getItemCount();i++){StoreChoice option=storeFilter.getItemAt(i);if(option.allStores()==selected.allStores()&&java.util.Objects.equals(option.locationId(),selected.locationId())){storeFilter.setSelectedIndex(i);break;}}}finally{loadingStores=false;}}
    private record StoreChoice(Integer locationId,String label,boolean allStores){@Override public String toString(){return label;}}

    private void renderSaleDetailsDialog(int saleId, LanApiClient.SaleHistoryDetails details) {
        DefaultTableModel detailsModel = readOnlyModel(
                "Product ID", "Item Name", "Qty", "Returned", "Original Unit",
                "Item Disc %", "Item Discount", "Final Unit", "Line Total");
        DefaultTableModel returnsModel = readOnlyModel(
                "Return ID", "Return Receipt", "Date / Time", "Employee", "Method", "Amount", "Reason");
        DefaultTableModel returnItemsModel = readOnlyModel(
                "Return ID", "Product ID", "Item Name", "Qty", "Unit Price", "Line Total");
        DefaultTableModel overrideAuditModel = readOnlyModel(
                "Time", "Action", "Scope", "Field", "Old", "New", "Amount", "Qty",
                "Reason", "Note", "By User", "Device");

        if (details.items() != null) {
            for (LanApiClient.SaleHistoryItem item : details.items()) {
                detailsModel.addRow(new Object[]{
                        item.productId(), item.productName(), item.quantity(), item.returnedQuantity(),
                        currencyFormat.format(zero(item.originalUnitPrice())),
                        String.format("%.2f%%", zero(item.discountPercent())),
                        currencyFormat.format(zero(item.discountAmount())),
                        currencyFormat.format(zero(item.unitPrice())),
                        currencyFormat.format(zero(item.lineTotal()))
                });
            }
        }
        BigDecimal returnedTotal = BigDecimal.ZERO;
        if (details.returns() != null) {
            for (LanApiClient.SaleHistoryReturn item : details.returns()) {
                returnedTotal = returnedTotal.add(zero(item.refundAmount()));
                returnsModel.addRow(new Object[]{
                        item.returnId(), item.returnReceiptNumber(), formatEpoch(item.createdAtEpochMillis()), item.userName(),
                        item.refundMethod(), currencyFormat.format(zero(item.refundAmount())), item.reason()
                });
            }
        }
        if (details.returnItems() != null) {
            for (LanApiClient.SaleHistoryReturnItem item : details.returnItems()) {
                returnItemsModel.addRow(new Object[]{
                        item.returnId(), item.productId(), item.productName(), item.quantity(),
                        currencyFormat.format(zero(item.unitPrice())),
                        currencyFormat.format(zero(item.lineTotal()))
                });
            }
        }
        if (details.overrideAudit() != null) {
            for (LanApiClient.SaleHistoryAudit item : details.overrideAudit()) {
                overrideAuditModel.addRow(new Object[]{
                        formatEpoch(item.createdAtEpochMillis()), item.actionType(), item.actionScope(),
                        item.fieldName(), item.oldValue(), item.newValue(),
                        item.amount() == null ? "" : currencyFormat.format(item.amount()),
                        item.quantity() == null ? "" : item.quantity(), item.reason(), item.note(),
                        item.userName(), item.deviceName()
                });
            }
        }

        JTable detailsTable = new JTable(detailsModel);
        detailsTable.setRowHeight(24);
        detailsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        detailsTable.getTableHeader().setReorderingAllowed(false);
        JScrollPane scrollPane = new JScrollPane(detailsTable);
        scrollPane.setPreferredSize(new Dimension(850, 300));

        JTable returnsTable = new JTable(returnsModel);
        returnsTable.setRowHeight(24);
        returnsTable.getTableHeader().setReorderingAllowed(false);
        JTable returnItemsTable = new JTable(returnItemsModel);
        returnItemsTable.setRowHeight(24);
        returnItemsTable.getTableHeader().setReorderingAllowed(false);
        JTable overrideAuditTable = new JTable(overrideAuditModel);
        overrideAuditTable.setRowHeight(24);
        overrideAuditTable.getTableHeader().setReorderingAllowed(false);

        JPanel returnsPanel = new JPanel(new BorderLayout(8, 8));
        JSplitPane returnsSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(returnsTable), new JScrollPane(returnItemsTable));
        returnsSplit.setResizeWeight(0.45);
        returnsPanel.add(returnsSplit, BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Sale Items", scrollPane);
        tabs.addTab("Returns (" + returnsModel.getRowCount() + ")", returnsPanel);
        tabs.addTab("Override Audit (" + overrideAuditModel.getRowCount() + ")",
                new JScrollPane(overrideAuditTable));
        panel.add(tabs, BorderLayout.CENTER);

        BigDecimal total = zero(details.totalAmount());
        BigDecimal subtotal = zero(details.subtotalAmount());
        BigDecimal discount = zero(details.discountAmount());
        BigDecimal net = total.subtract(returnedTotal).max(BigDecimal.ZERO);
        JLabel totalLabel = new JLabel("Subtotal: " + currencyFormat.format(subtotal)
                + "    Discount: " + currencyFormat.format(discount) + " ("
                + String.format("%.2f", zero(details.discountPercent())) + "%)"
                + "    Sale Total: " + currencyFormat.format(total)
                + "    Returned: " + currencyFormat.format(returnedTotal)
                + "    Net: " + currencyFormat.format(net));
        totalLabel.setBorder(new EmptyBorder(4, 4, 0, 4));
        totalLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        panel.add(totalLabel, BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(this, panel,
                "Transaction Details - Sale #" + saleId, JOptionPane.PLAIN_MESSAGE);
    }

    private static DefaultTableModel readOnlyModel(Object... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private LocalDate parseDate(String text, String label) {
        if (text == null || text.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            JOptionPane.showMessageDialog(this,
                    label + " must be in yyyy-MM-dd format.",
                    "Invalid Date",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }
    }

    private String formatEpoch(long epochMillis) {
        if (epochMillis <= 0) return "";
        return Instant.ofEpochMilli(epochMillis)
                .atZone(StoreTimeZoneHelper.getStoreZone())
                .format(displayDateTimeFormatter);
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String formatPaymentStatus(String paymentStatus) {
        if (paymentStatus == null || paymentStatus.isBlank()) {
            return "Paid";
        }
        return switch (paymentStatus.toUpperCase()) {
            case "UNPAID" -> "Unpaid";
            case "PAID" -> "Paid";
            default -> paymentStatus.substring(0, 1).toUpperCase() + paymentStatus.substring(1).toLowerCase();
        };
    }
}
