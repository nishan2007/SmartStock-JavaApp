package ui.screens;

import services.LanApiClient;
import ui.components.AppMenuBar;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.WindowHelper;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import javax.swing.table.TableRowSorter;
import javax.swing.table.DefaultTableCellRenderer;
import java.util.List;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

public class CustomerTransactionHistory extends JFrame {
    private final int customerId;
    private final String customerLabel;
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DefaultTableModel transactionModel;
    private JLabel summaryLabel;
    private JTable transactionTable;
    private JTextField searchField;
    private JTextArea selectedDetails;
    private List<LanApiClient.CustomerTransactionRecord> records=List.of();
    private final LoadingStatePanel loadingState=new LoadingStatePanel();

    public CustomerTransactionHistory(int customerId, String customerLabel) {
        this(customerId,customerLabel,true);
    }

    CustomerTransactionHistory(int customerId,String customerLabel,boolean connected) {
        this.customerId = customerId;
        this.customerLabel = customerLabel == null ? "Customer Account" : customerLabel;

        setTitle("Customer Transaction History");
        setSize(1440, 900);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        if(connected)setJMenuBar(AppMenuBar.create(this, "CustomerTransactionHistory"));
        setLayout(new BorderLayout(12, 12));

        JPanel mainPanel = CustomerAccountsDesign.panel(new BorderLayout(12, 18));
        mainPanel.setBorder(new EmptyBorder(14, 14, 14, 14));
        add(mainPanel, BorderLayout.CENTER);

        mainPanel.add(buildHeaderPanel(), BorderLayout.NORTH);
        mainPanel.add(buildTablePanel(), BorderLayout.CENTER);
        JPanel footer=CustomerAccountsDesign.panel(new BorderLayout(0,10));
        selectedDetails=new JTextArea("Select a transaction to see its details.",5,30);
        selectedDetails.setEditable(false);selectedDetails.setLineWrap(true);selectedDetails.setWrapStyleWord(true);
        selectedDetails.setFont(CustomerAccountsDesign.font(Font.PLAIN,14));
        selectedDetails.setBorder(new EmptyBorder(12,16,12,16));
        JScrollPane details=new JScrollPane(selectedDetails);
        details.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(DeckersPalette.sectionBorder(DeckersPalette.PURPLE)),"Transaction details"));
        footer.add(details,BorderLayout.CENTER);
        JPanel totals=CustomerAccountsDesign.panel(new BorderLayout());totals.add(loadingState,BorderLayout.NORTH);totals.add(buildSummaryPanel(),BorderLayout.SOUTH);
        footer.add(totals,BorderLayout.SOUTH);mainPanel.add(footer, BorderLayout.SOUTH);

        if(connected){loadTransactions();WindowHelper.configurePosWindow(this);}
        getContentPane().setBackground(DeckersPalette.background());
    }

    private JPanel buildHeaderPanel() {
        JPanel headerPanel = CustomerAccountsDesign.panel(new BorderLayout(12, 18));

        JLabel titleLabel = CustomerAccountsDesign.label("Transaction History",32,true);
        titleLabel.setIcon(SalesTaskVisuals.icon("orders",DeckersPalette.PURPLE,28));titleLabel.setIconTextGap(12);

        JLabel customerLabelText = new JLabel(customerLabel);
        customerLabelText.setFont(CustomerAccountsDesign.font(Font.PLAIN,16));

        JPanel titlePanel = CustomerAccountsDesign.panel(null);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(customerLabelText);

        JPanel buttonPanel = CustomerAccountsDesign.panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton refreshButton = CustomerAccountsDesign.button("Refresh","refresh",false);
        JButton paymentHistoryButton = CustomerAccountsDesign.button("Payment History","payment",true);
        buttonPanel.add(refreshButton);
        buttonPanel.add(paymentHistoryButton);

        refreshButton.addActionListener(e -> {SessionDataCache.invalidate("customer-transactions:"+customerId);loadTransactions();});
        paymentHistoryButton.addActionListener(e -> openPaymentHistory());

        headerPanel.add(titlePanel, BorderLayout.CENTER);
        headerPanel.add(buttonPanel, BorderLayout.EAST);
        searchField=new JTextField();
        searchField.setToolTipText("Search transactions, documents, methods, stores and notes");
        searchField.getAccessibleContext().setAccessibleName("Search transaction history");
        DeckersSwing.styleField(searchField);
        JPanel search=CustomerAccountsDesign.panel(new BorderLayout(12,0));
        search.add(CustomerAccountsDesign.label("Search history",14,true),BorderLayout.WEST);search.add(searchField);
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            private void filter(){if(transactionTable!=null){@SuppressWarnings("unchecked") TableRowSorter<DefaultTableModel> sorter=(TableRowSorter<DefaultTableModel>)transactionTable.getRowSorter();String text=searchField.getText().trim();sorter.setRowFilter(text.isEmpty()?null:RowFilter.regexFilter("(?i)"+java.util.regex.Pattern.quote(text)));}}
            public void insertUpdate(javax.swing.event.DocumentEvent e){filter();}public void removeUpdate(javax.swing.event.DocumentEvent e){filter();}public void changedUpdate(javax.swing.event.DocumentEvent e){filter();}
        });
        headerPanel.add(search,BorderLayout.SOUTH);
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

        transactionTable = new JTable(transactionModel);
        DeckersSwing.styleTable(transactionTable,DeckersPalette.PURPLE);
        transactionTable.setFont(CustomerAccountsDesign.font(Font.PLAIN,15));
        transactionTable.setRowHeight(48);
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

        int[] visible={1,6,8,2,11,12,9};
        var all=transactionTable.getColumnModel();
        java.util.Map<Integer,javax.swing.table.TableColumn> columns=new java.util.HashMap<>();
        while(all.getColumnCount()>0){var column=all.getColumn(0);columns.put(column.getModelIndex(),column);all.removeColumn(column);}
        int[] widths={180,170,220,160,140,140,130};
        for(int index=0;index<visible.length;index++){var column=columns.get(visible[index]);column.setPreferredWidth(widths[index]);column.setMinWidth(95);all.addColumn(column);}
        TableRowSorter<DefaultTableModel> sorter=new TableRowSorter<>(transactionModel);
        sorter.setComparator(11,(a,b)->parseAmount(a).compareTo(parseAmount(b)));
        sorter.setSortKeys(List.of(new RowSorter.SortKey(1,SortOrder.DESCENDING)));
        transactionTable.setRowSorter(sorter);
        transactionTable.getTableHeader().setPreferredSize(new Dimension(0,42));
        transactionTable.getTableHeader().setDefaultRenderer((table,value,selected,focus,row,column)->{
            JLabel heading=CustomerAccountsDesign.label(String.valueOf(value),14,true);
            heading.setOpaque(true);heading.setBackground(DeckersPalette.tableHeader(DeckersPalette.PURPLE));
            heading.setBorder(new EmptyBorder(10,12,10,12));heading.setHorizontalTextPosition(SwingConstants.LEFT);heading.setIconTextGap(8);
            for(var key:table.getRowSorter().getSortKeys())if(key.getColumn()==table.convertColumnIndexToModel(column)){
                heading.setIcon(UIManager.getIcon(key.getSortOrder()==SortOrder.ASCENDING?"Table.ascendingSortIcon":"Table.descendingSortIcon"));break;
            }
            return heading;
        });
        transactionTable.getTableHeader().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        DefaultTableCellRenderer renderer=new DefaultTableCellRenderer(){
            @Override public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int col){
                super.getTableCellRendererComponent(table,value,selected,focus,row,col);
                setBorder(new EmptyBorder(8,12,8,12));setFont(CustomerAccountsDesign.font(Font.PLAIN,15));
                setBackground(selected?DeckersPalette.tilePressed(DeckersPalette.ORANGE):row%2==0?DeckersPalette.surface():DeckersPalette.tableStripe());
                setForeground(DeckersPalette.text());setHorizontalAlignment(table.convertColumnIndexToModel(col)==11?SwingConstants.RIGHT:SwingConstants.LEFT);return this;
            }
        };
        transactionTable.setDefaultRenderer(Object.class,renderer);
        transactionTable.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting())showSelectedDetails();});
        JScrollPane scroll=new JScrollPane(transactionTable);scroll.setBorder(BorderFactory.createLineBorder(DeckersPalette.sectionBorder(DeckersPalette.PURPLE)));scroll.getViewport().setBackground(DeckersPalette.surface());
        return scroll;
    }

    private JPanel buildSummaryPanel() {
        JPanel panel = CustomerAccountsDesign.panel(new BorderLayout());
        summaryLabel = CustomerAccountsDesign.label("Transactions: 0",16,true);
        summaryLabel.setBorder(new EmptyBorder(4, 2, 0, 2));
        panel.add(summaryLabel, BorderLayout.WEST);
        return panel;
    }

    private void loadTransactions() {
        CachedUiLoader.load(this,"customer-transactions:"+customerId,LanApiClient.CustomerTransactionResult.class,SessionDataCache.SCREEN_TTL,loadingState,()->LanApiClient.loadCustomerTransactions(customerId),this::applyTransactions);
    }

    void applyTransactions(LanApiClient.CustomerTransactionResult result) {
        records=result.transactions()==null?List.of():result.transactions();
        transactionModel.setRowCount(0);
            for(LanApiClient.CustomerTransactionRecord row:records){
                    transactionModel.addRow(new Object[]{
                            formatRecord(row),formatTimestamp(row.createdAtEpochMillis()),row.storeName(),row.userName(),
                            row.deviceName(),row.cashDrawerName(),formatType(row.transactionType()),formatType(row.documentType()),
                            row.documentNumber(),row.paymentMethod(),row.paymentReference(),historyMoney(defaultZero(row.amount())),
                            formatStatus(row.paymentStatus()),formatStatus(row.documentStatus()),"PRICE_PENDING".equals(row.paymentStatus())?"Price pending":historyMoney(defaultZero(row.chargeTotal())),
                            row.remote()?"Synced snapshot":"Live store data",row.note()
                    });
            }
            summaryLabel.setText("Transactions: "+result.count()+"    Charges: "+historyMoney(result.totalCharges())
                    +"    Payments: "+historyMoney(result.totalPayments()));
        selectedDetails.setText(records.isEmpty()?"No transactions or payments for this customer.":"Select a transaction to see its details.");
    }

    private BigDecimal parseAmount(Object value){try{return new BigDecimal(value.toString().replaceAll("[^0-9.\\-]",""));}catch(Exception ex){return BigDecimal.ZERO;}}

    private void showSelectedDetails() {
        if(selectedDetails==null)return;
        int selected=transactionTable.getSelectedRow();
        if(selected<0){selectedDetails.setText("Select a transaction to see its details.");return;}
        int modelRow=transactionTable.convertRowIndexToModel(selected);
        if(modelRow>=records.size())return;
        var row=records.get(modelRow);
        selectedDetails.setText(formatRecord(row)+"  ·  "+formatTimestamp(row.createdAtEpochMillis())+"\n"
                +"Store: "+text(row.storeName())+"    User: "+text(row.userName())+"    Device: "+text(row.deviceName())+"    Drawer: "+text(row.cashDrawerName())+"\n"
                +"Document: "+formatType(row.documentType())+" "+text(row.documentNumber())+"    Reference: "+text(row.paymentReference())+"\n"
                +"Document status: "+formatStatus(row.documentStatus())+"    Total: "+historyMoney(defaultZero(row.chargeTotal()))+"    Source: "+(row.remote()?"Synced snapshot":"Live store data")+"\n"
                +"Note: "+text(row.note())
                +(row.authorization()==null?"":"\nCharge authorization: "+text(row.authorization().representativeName())));
        selectedDetails.setCaretPosition(0);
    }
    private static String text(String value){return value==null?"":value;}

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
            default -> {
                String words=type.replace('_',' ').toLowerCase(java.util.Locale.ROOT);
                yield Character.toUpperCase(words.charAt(0))+words.substring(1);
            }
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

    private String historyMoney(BigDecimal value) { return "G$"+java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(defaultZero(value)); }
    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
