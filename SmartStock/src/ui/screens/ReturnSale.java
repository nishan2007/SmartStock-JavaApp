package ui.screens;

import managers.PermissionManager;
import managers.SessionManager;
import services.LanApiClient;
import services.ManagerApprovalService;
import services.RefundApprovalIdentity;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.WindowHelper;
import ui.helpers.UiTaskRunner;
import utils.CurrencyFormatter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Return editor backed exclusively by the authenticated SmartStock LAN service. */
public class ReturnSale extends JFrame {
    private static final String RETURN_OVERRIDE_PERMISSION = "RETURN_OVERRIDE";
    private static final NumberFormatAdapter CURRENCY = new NumberFormatAdapter();
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");

    private final JTextField saleSearchField = new JTextField();
    private final JLabel saleInfoLabel = new JLabel("Load a sale to begin.");
    private final JComboBox<String> refundMethodBox = new JComboBox<>(
            new String[]{"CASH", "CARD", "CHEQUE", "MMG", "ACCOUNT"});
    private final JTextArea reasonArea = new JTextArea(3, 30);
    private final JLabel totalReturnLabel = new JLabel("Return Total: $0");
    private final JLabel overrideStatusLabel = new JLabel("No active override approvals");
    private final DefaultTableModel itemModel;
    private final DefaultTableModel saleSearchModel;
    private final JTable saleSearchTable;
    private JTable returnItemsTable;
    private final JPopupMenu saleSearchPopup = new JPopupMenu();
    private final Timer saleSearchTimer;
    private final LoadingStatePanel loadingState = new LoadingStatePanel();
    private final JButton submitButton = new JButton("Submit Return");
    private final JComboBox<StoreChoice> searchStoreBox=new JComboBox<>();
    private final List<LanApiClient.SaleSearchResult> searchResults=new ArrayList<>();
    private final List<StoreChoice> storeChoices=new ArrayList<>();
    private final Integer initialSourceLocationId;

    private SaleSnapshot loadedSale;
    private boolean updatingModel;
    private boolean selectingSearchResult;
    private String overrideApprovalToken;
    private String overrideApprovalReason;
    private String overrideApprovedByName;
    private String overrideApprovedResource;
    private String pendingRefundKey;
    private String pendingRefundFingerprint;
    private UUID pendingCrossStoreRequestId;

    public ReturnSale() {
        this(null,null);
    }

    public ReturnSale(Integer saleId) {
        this(saleId,null);
    }

    public ReturnSale(Integer saleId,Integer sourceLocationId) {
        initialSourceLocationId=sourceLocationId;
        setTitle("Process Return");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        setJMenuBar(AppMenuBar.create(this, "ReturnSale", loadingState));

        itemModel = new DefaultTableModel(
                new Object[]{"Sale Item ID", "Product ID", "SKU", "Item", "Sold", "Returned",
                        "Available", "Unit Price", "Product Type", "Return Qty","Disposition","Destination","Disposition Reason"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 9 || (loadedSale!=null&&loadedSale.remote()&&column>=10);
            }
        };
        saleSearchModel = new DefaultTableModel(
                new Object[]{"Sale ID", "Receipt", "Date / Time", "Total", "Cashier", "Device"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        saleSearchTable = new JTable(saleSearchModel);
        saleSearchTimer = new Timer(300, e -> refreshSaleSearchResults());
        saleSearchTimer.setRepeats(false);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        root.setBorder(new EmptyBorder(16, 18, 18, 18));
        root.setBackground(DeckersPalette.background());
        root.add(buildHeaderPanel(), BorderLayout.NORTH);
        root.add(buildTablePanel(), BorderLayout.CENTER);
        root.add(buildFooterPanel(), BorderLayout.SOUTH);
        add(root, BorderLayout.CENTER);

        itemModel.addTableModelListener(e -> {
            if (!updatingModel && e.getType() == TableModelEvent.UPDATE) {
                normalizeReturnQuantities();
                updateReturnTotal();
            }
        });

        if (saleId != null) {
            saleSearchField.setText(String.valueOf(saleId));
            loadSaleById(saleId,sourceLocationId);
        }
        loadStoreChoices();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);
        JLabel titleLabel = new JLabel("Process Return");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        preserveForeground(titleLabel, DeckersPalette.text());
        JLabel subtitleLabel = new JLabel("Find a completed sale, select the items, and issue the refund.");
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
        preserveForeground(subtitleLabel, DeckersPalette.muted());

        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(subtitleLabel);

        JPanel hero = new JPanel(new BorderLayout());
        DeckersSwing.styleBand(hero, DeckersPalette.ORANGE, new Insets(16, 18, 16, 18));
        hero.add(titlePanel, BorderLayout.WEST);

        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        DeckersSwing.styleBand(searchPanel, DeckersPalette.MAGENTA, new Insets(10, 14, 10, 14));
        saleSearchField.setToolTipText("Sale ID or receipt number");
        saleSearchField.putClientProperty("JTextField.placeholderText", "Enter a sale ID or receipt number");
        DeckersSwing.styleField(saleSearchField);
        JButton loadButton = new JButton("Load Sale");
        DeckersSwing.styleUtilityButton(loadButton, DeckersPalette.MAGENTA);
        JPanel searchLeft=new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));searchLeft.setOpaque(false);
        searchLeft.add(DeckersSwing.metaLabel("Sale / Receipt"));searchLeft.add(searchStoreBox);
        searchPanel.add(searchLeft, BorderLayout.WEST);
        searchPanel.add(saleSearchField, BorderLayout.CENTER);
        searchPanel.add(loadButton, BorderLayout.EAST);

        saleInfoLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        preserveForeground(saleInfoLabel, DeckersPalette.muted());
        saleInfoLabel.setBorder(new EmptyBorder(0, 6, 0, 0));
        JPanel lookup = new JPanel(new BorderLayout(0, 8));
        lookup.setOpaque(false);
        lookup.add(searchPanel, BorderLayout.NORTH);
        lookup.add(saleInfoLabel, BorderLayout.SOUTH);

        loadButton.addActionListener(e -> loadSale());
        saleSearchField.addActionListener(e -> loadSale());
        saleSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { scheduleSaleSearch(); }
            @Override public void removeUpdate(DocumentEvent e) { scheduleSaleSearch(); }
            @Override public void changedUpdate(DocumentEvent e) { scheduleSaleSearch(); }
        });
        setupSaleSearchPopup();
        panel.add(hero, BorderLayout.NORTH);
        panel.add(lookup, BorderLayout.SOUTH);
        return panel;
    }

    private void setupSaleSearchPopup() {
        saleSearchTable.setRowHeight(26);
        saleSearchTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        saleSearchTable.getTableHeader().setReorderingAllowed(false);
        DeckersSwing.styleTable(saleSearchTable, DeckersPalette.MAGENTA);
        configureSaleSearchColumns();
        saleSearchTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 1) selectSaleSearchResult();
            }
        });
        JTableHeader header = saleSearchTable.getTableHeader();
        JScrollPane scrollPane = new JScrollPane(saleSearchTable);
        JPanel popupPanel = new JPanel(new BorderLayout());
        popupPanel.add(header, BorderLayout.NORTH);
        popupPanel.add(scrollPane, BorderLayout.CENTER);
        saleSearchPopup.setBorder(BorderFactory.createLineBorder(
                DeckersPalette.sectionBorder(DeckersPalette.MAGENTA)));
        saleSearchPopup.add(popupPanel);
        saleSearchPopup.setFocusable(false);
    }

    private void configureSaleSearchColumns() {
        TableColumn saleIdColumn = saleSearchTable.getColumnModel().getColumn(0);
        saleSearchTable.removeColumn(saleIdColumn);
        TableColumn receiptColumn = saleSearchTable.getColumnModel().getColumn(0);
        receiptColumn.setPreferredWidth(420);
        receiptColumn.setCellRenderer(new TrailingTextRenderer());
        saleSearchTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        saleSearchTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        saleSearchTable.getColumnModel().getColumn(3).setPreferredWidth(170);
        saleSearchTable.getColumnModel().getColumn(4).setPreferredWidth(210);
    }

    private JPanel buildTablePanel() {
        JTable table = new JTable(itemModel);
        returnItemsTable=table;
        DeckersSwing.styleTable(table, DeckersPalette.LIME);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.removeColumn(table.getColumnModel().getColumn(0));
        table.removeColumn(table.getColumnModel().getColumn(0));
        table.removeColumn(table.getColumnModel().getColumn(6));
        table.getColumnModel().getColumn(0).setPreferredWidth(120);
        table.getColumnModel().getColumn(1).setPreferredWidth(360);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(4).setPreferredWidth(90);
        table.getColumnModel().getColumn(5).setPreferredWidth(110);
        table.getColumnModel().getColumn(6).setPreferredWidth(110);
        table.getColumnModel().getColumn(7).setPreferredWidth(100);
        table.getColumnModel().getColumn(8).setPreferredWidth(150);
        table.getColumnModel().getColumn(9).setPreferredWidth(190);
        JComboBox<String> dispositions=new JComboBox<>(new String[]{"RESTOCK","DISCARD"});
        table.getColumnModel().getColumn(7).setCellEditor(new DefaultCellEditor(dispositions));

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(DeckersPalette.tableBody(DeckersPalette.LIME));

        JLabel sectionTitle = new JLabel("Return Items");
        sectionTitle.setFont(new Font("SansSerif", Font.BOLD, 16));
        preserveForeground(sectionTitle, DeckersPalette.text());
        JLabel sectionHelp = new JLabel("Enter a quantity in the Return Qty column.");
        sectionHelp.setFont(new Font("SansSerif", Font.PLAIN, 13));
        preserveForeground(sectionHelp, DeckersPalette.muted());
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.setBorder(new EmptyBorder(0, 2, 8, 2));
        heading.add(sectionTitle, BorderLayout.WEST);
        heading.add(sectionHelp, BorderLayout.EAST);

        JPanel section = new JPanel(new BorderLayout());
        DeckersSwing.styleBand(section, DeckersPalette.LIME, new Insets(10, 10, 10, 10));
        section.add(heading, BorderLayout.NORTH);
        section.add(scrollPane, BorderLayout.CENTER);
        return section;
    }

    private JPanel buildFooterPanel() {
        JPanel panel = new JPanel(new BorderLayout(14, 0));
        DeckersSwing.styleBand(panel, DeckersPalette.CORAL, new Insets(14, 14, 14, 14));
        reasonArea.setLineWrap(true);
        reasonArea.setWrapStyleWord(true);
        reasonArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        reasonArea.setForeground(DeckersPalette.text());
        reasonArea.setCaretColor(DeckersPalette.text());
        reasonArea.setBackground(DeckersPalette.fieldBackground());
        reasonArea.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        reasonArea.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        JPanel reasonPanel = new JPanel(new BorderLayout(8, 8));
        reasonPanel.setOpaque(false);
        reasonPanel.add(DeckersSwing.metaLabel("Reason / Note"), BorderLayout.NORTH);
        JScrollPane reasonScroll = new JScrollPane(reasonArea);
        reasonScroll.setPreferredSize(new Dimension(0, 78));
        reasonScroll.setBorder(BorderFactory.createLineBorder(DeckersPalette.border()));
        reasonPanel.add(reasonScroll, BorderLayout.CENTER);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionPanel.setOpaque(false);
        totalReturnLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        preserveForeground(totalReturnLabel, DeckersPalette.text());
        overrideStatusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        preserveForeground(overrideStatusLabel, DeckersPalette.muted());
        JButton returnAllButton = new JButton("Return All Available");
        JButton clearButton = new JButton("Clear Qty");
        DeckersSwing.styleUtilityButton(returnAllButton, DeckersPalette.YELLOW);
        DeckersSwing.styleUtilityButton(clearButton, DeckersPalette.PURPLE);
        DeckersSwing.styleUtilityButton(submitButton, DeckersPalette.LIME);
        refundMethodBox.setBackground(DeckersPalette.fieldBackground());
        refundMethodBox.setForeground(DeckersPalette.text());
        refundMethodBox.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        refundMethodBox.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        actionPanel.add(DeckersSwing.metaLabel("Refund Method"));
        actionPanel.add(refundMethodBox);
        actionPanel.add(returnAllButton);
        actionPanel.add(clearButton);
        actionPanel.add(submitButton);

        returnAllButton.addActionListener(e -> returnAllAvailable());
        clearButton.addActionListener(e -> clearReturnQty());
        submitButton.addActionListener(e -> submitReturn());

        JPanel summaryPanel = new JPanel(new BorderLayout(0, 10));
        summaryPanel.setOpaque(false);
        summaryPanel.add(totalReturnLabel, BorderLayout.NORTH);
        summaryPanel.add(actionPanel, BorderLayout.CENTER);
        summaryPanel.add(overrideStatusLabel, BorderLayout.SOUTH);

        panel.add(reasonPanel, BorderLayout.CENTER);
        panel.add(summaryPanel, BorderLayout.EAST);
        JPanel footer = new JPanel(new BorderLayout(8, 6));
        footer.setOpaque(false);
        footer.add(loadingState, BorderLayout.NORTH);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private static void preserveForeground(JComponent component, Color color) {
        component.setForeground(color);
        component.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
    }

    private void scheduleSaleSearch() {
        if (!selectingSearchResult) saleSearchTimer.restart();
    }

    private void refreshSaleSearchResults() {
        String search = saleSearchField.getText().trim();
        saleSearchModel.setRowCount(0);
        if (search.length() < 2) {
            saleSearchPopup.setVisible(false);
            return;
        }
        Integer source=selectedSourceLocation();
        UiTaskRunner.submit(this,"return-sale.search",()->LanApiClient.searchSalesForReturn(search,source),results->{
            populateSearchResults(results);
            if (results.isEmpty()) {
                saleSearchPopup.setVisible(false);
            } else {
                showSaleSearchPopup();
            }
        },ex->saleSearchPopup.setVisible(false));
    }

    private void populateSearchResults(List<LanApiClient.SaleSearchResult> results) {
        saleSearchModel.setRowCount(0);
        searchResults.clear();searchResults.addAll(results);
        for (LanApiClient.SaleSearchResult sale : results) {
            String localTime = Instant.ofEpochMilli(sale.createdAtEpochMillis())
                    .atZone(StoreTimeZoneHelper.getStoreZone()).format(DATE_TIME_FORMAT);
            saleSearchModel.addRow(new Object[]{sale.saleId(), sale.receiptNumber(), localTime,
                    CURRENCY.format(sale.totalAmount()), sale.cashierName(),
                    sale.storeName()==null||sale.storeName().isBlank()?sale.deviceId():sale.storeName()});
        }
    }

    private void showSaleSearchPopup() {
        int width = Math.max(saleSearchField.getWidth(), 1040);
        int height = Math.min(260, 30 + saleSearchModel.getRowCount() * 28);
        saleSearchPopup.setPopupSize(width, Math.max(height, 90));
        saleSearchPopup.show(saleSearchField, 0, saleSearchField.getHeight());
        saleSearchField.requestFocusInWindow();
    }

    private void selectSaleSearchResult() {
        int row = saleSearchTable.getSelectedRow();
        if (row < 0 && saleSearchModel.getRowCount() == 1) row = 0;
        if (row < 0) return;
        int modelRow = saleSearchTable.convertRowIndexToModel(row);
        int saleId = Integer.parseInt(String.valueOf(saleSearchModel.getValueAt(modelRow, 0)));
        String receipt = String.valueOf(saleSearchModel.getValueAt(modelRow, 1));
        selectingSearchResult = true;
        try {
            saleSearchField.setText(receipt.isBlank() ? String.valueOf(saleId) : receipt);
        } finally {
            selectingSearchResult = false;
        }
        saleSearchPopup.setVisible(false);
        Integer source=modelRow<searchResults.size()?searchResults.get(modelRow).sourceLocationId():selectedSourceLocation();
        loadSaleById(saleId,source);
    }

    private void loadSale() {
        if (!PermissionManager.requirePermission("PROCESS_RETURNS", this, "Load Sale for Return")) return;
        String search = saleSearchField.getText().trim();
        if (search.isBlank()) {
            JOptionPane.showMessageDialog(this, "Enter a sale ID or receipt number.");
            return;
        }
        Integer source=selectedSourceLocation();
        UiTaskRunner.submit(this,"return-sale.search",()->LanApiClient.searchSalesForReturn(search,source),matches->{
            if (matches.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Sale was not found.");
            } else if (matches.size() > 1) {
                populateSearchResults(matches);
                showSaleSearchPopup();
                JOptionPane.showMessageDialog(this,
                        "Multiple sales matched. Select the correct sale from the search list.");
            } else {
                loadSaleById(matches.get(0).saleId(),matches.get(0).sourceLocationId());
            }
        },ex->showApiError("Failed to load sale",ex));
    }

    private void loadSaleById(int saleId) {
        loadSaleById(saleId,selectedSourceLocation());
    }
    private void loadSaleById(int saleId,Integer sourceLocationId) {
        UiTaskRunner.submit(this,"return-sale.details",()->LanApiClient.loadReturnSaleDetails(saleId,sourceLocationId),this::applySaleDetails,
                ex->{updatingModel=false;showApiError("Failed to load sale",ex);});
    }

    private void applySaleDetails(LanApiClient.ReturnSaleDetails details) {
            loadedSale = new SaleSnapshot(details.saleId(), details.receiptNumber(), details.customerId(),
                    safe(details.paymentMethod()), safe(details.paymentStatus()), zero(details.totalAmount()),
                    zero(details.returnedAmount()), zero(details.returnApprovalLimit()),
                    details.requesterCanOverride(),details.sourceLocationId());
            clearApproval();
            pendingRefundKey = null;
            pendingRefundFingerprint = null;
            pendingCrossStoreRequestId = null;
            updatingModel = true;
            itemModel.setRowCount(0);
            if (details.items() != null) {
                for (LanApiClient.ReturnSaleLine item : details.items()) {
                    itemModel.addRow(new Object[]{item.saleItemId(), item.productId(), safe(item.sku()),
                            safe(item.productName()), item.soldQuantity(), item.returnedQuantity(),
                            item.availableQuantity(), zero(item.unitPrice()),
                            normalizeProductType(item.productType()),0,
                            details.sourceLocationId()==null?"":"RESTOCK",defaultDestination(),""});
                }
            }
            updatingModel = false;
            refundMethodBox.setSelectedItem(loadedSale.paymentMethod().isBlank()
                    ? "CASH" : loadedSale.paymentMethod());
            saleInfoLabel.setText("Sale #" + loadedSale.saleId()
                    + "  Receipt: " + loadedSale.receiptNumber()
                    + "  Payment: " + loadedSale.paymentMethod()
                    + "  Sale Total: " + CURRENCY.format(loadedSale.totalAmount())
                    + "  Previously Returned: " + CURRENCY.format(loadedSale.returnedAmount()));
            if(loadedSale.remote())saleInfoLabel.setText(saleInfoLabel.getText()+"  [MULTISTORE - synchronized data]");
            updateReturnTotal();
    }

    private void submitReturn() {
        if (!PermissionManager.requirePermission("PROCESS_RETURNS", this, "Submit Return")) return;
        if (loadedSale == null) {
            JOptionPane.showMessageDialog(this, "Load a sale first.");
            return;
        }
        if(loadedSale.remote()&&!PermissionManager.hasPermission("PROCESS_MULTI_STORE_RETURNS")){
            JOptionPane.showMessageDialog(this,"You do not have permission to process another store's return.");return;
        }
        if (SessionManager.getCurrentUserId() == null) {
            JOptionPane.showMessageDialog(this, "No employee is logged in.");
            return;
        }

        List<ReturnLine> lines;
        try {
            lines = collectReturnLines();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Invalid Return Quantity", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (lines.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter at least one return quantity.");
            return;
        }
        BigDecimal returnTotal = calculateReturnTotal(lines);
        if (returnTotal.signum() <= 0) {
            JOptionPane.showMessageDialog(this, "Return total must be greater than zero.");
            return;
        }
        if (!ensureApprovalFor(lines, returnTotal)) return;

        String reason = reasonArea.getText() == null ? "" : reasonArea.getText().trim();
        if (reason.isBlank()) {
            JOptionPane.showMessageDialog(this, "Reason / note is required for returns.");
            return;
        }
        int confirmed = JOptionPane.showConfirmDialog(this,
                "Process return for " + CURRENCY.format(returnTotal) + "?",
                "Confirm Return", JOptionPane.YES_NO_OPTION);
        if (confirmed != JOptionPane.YES_OPTION) return;

        List<LanApiClient.RefundLine> apiLines=new ArrayList<>();
        for(ReturnLine line:lines){int row=line.row();String disposition=loadedSale.remote()?String.valueOf(itemModel.getValueAt(row,10)):null;StoreChoice destination=loadedSale.remote()?storeByLabel(String.valueOf(itemModel.getValueAt(row,11))):null;String dispositionReason=loadedSale.remote()?String.valueOf(itemModel.getValueAt(row,12)):null;
            if(loadedSale.remote()&&"RESTOCK".equals(disposition)&&destination==null){JOptionPane.showMessageDialog(this,"Select a destination store for every restocked item.");return;}
            if(loadedSale.remote()&&"DISCARD".equals(disposition)&&dispositionReason.isBlank()){JOptionPane.showMessageDialog(this,"Enter a discard reason for damaged or discarded items.");return;}
            apiLines.add(new LanApiClient.RefundLine(line.saleItemId(),line.quantity(),disposition,destination==null?null:destination.locationId(),dispositionReason));}
        if(loadedSale.remote()&&pendingCrossStoreRequestId==null)pendingCrossStoreRequestId=UUID.randomUUID();
        UUID requestId=loadedSale.remote()?pendingCrossStoreRequestId:null;
        LanApiClient.RefundRequest request = new LanApiClient.RefundRequest(requestId,loadedSale.sourceLocationId(),
                loadedSale.saleId(),String.valueOf(refundMethodBox.getSelectedItem()),reason,
                overrideApprovalToken,overrideApprovalReason,apiLines);
        String fingerprint = refundFingerprint(request);
        if (!fingerprint.equals(pendingRefundFingerprint)) {
            pendingRefundFingerprint = fingerprint;
            pendingRefundKey = "return-" + UUID.randomUUID();
        }
        String refundKey = pendingRefundKey;
        int saleId = loadedSale.saleId();
        submitButton.setEnabled(false);
        loadingState.loading(false, java.time.Instant.now());
        UiTaskRunner.submit(this,"return-sale.refund",()->LanApiClient.refund(request,refundKey),result->{
            pendingRefundKey = null;
            pendingRefundFingerprint = null;
            pendingCrossStoreRequestId = null;
            submitButton.setEnabled(true);
            loadingState.ready(java.time.Instant.now());
            JOptionPane.showMessageDialog(this,
                    "Return processed successfully.\nReturn ID: " + result.returnId()
                            + "\nRefund amount: " + CURRENCY.format(result.refundAmount())
                            +(result.status()==null?"":"\nStatus: "+result.status()));
            loadSaleById(saleId,loadedSale==null?null:loadedSale.sourceLocationId());
        },failure->{submitButton.setEnabled(true);loadingState.actionFailed("Return",failure.getMessage(),this::submitReturn);});
    }

    private boolean ensureApprovalFor(List<ReturnLine> lines, BigDecimal total) {
        if (!overrideRequired(total)) {
            clearApproval();
            return true;
        }
        if (loadedSale.requesterCanOverride()) {
            overrideStatusLabel.setText("Return override approved by: "
                    + SessionManager.getCurrentUserDisplayName());
            return true;
        }
        String resource = approvalResource(lines, total);
        if (overrideApprovalToken != null && resource.equals(overrideApprovedResource)) return true;
        try {
            String label = "Sale #" + loadedSale.saleId() + " return total: " + CURRENCY.format(total);
            ManagerApprovalService.ApprovalResult approval = ManagerApprovalService.requestApproval(
                    this, RETURN_OVERRIDE_PERMISSION, "Return Override",
                    "Reason for return override:", label, resource);
            if (approval == null) {
                clearApproval();
                overrideStatusLabel.setText("Return override required before submit");
                return false;
            }
            overrideApprovalToken = approval.lanApprovalToken();
            overrideApprovalReason = approval.reason();
            overrideApprovedByName = approval.approvedByName();
            overrideApprovedResource = resource;
            if ((reasonArea.getText() == null || reasonArea.getText().trim().isBlank())
                    && approval.reason() != null) {
                reasonArea.setText(approval.reason());
            }
            overrideStatusLabel.setText("Return override approved by: " + overrideApprovedByName);
            return true;
        } catch (Exception ex) {
            clearApproval();
            showApiError("Manager approval could not be completed", ex);
            return false;
        }
    }

    private List<ReturnLine> collectReturnLines() {
        List<ReturnLine> lines = new ArrayList<>();
        for (int row = 0; row < itemModel.getRowCount(); row++) {
            int quantity = parseInt(itemModel.getValueAt(row, 9), 0);
            int available = parseInt(itemModel.getValueAt(row, 6), 0);
            if (quantity <= 0) continue;
            if (quantity > available) {
                throw new IllegalArgumentException("Return quantity cannot be more than available for "
                        + itemModel.getValueAt(row, 3));
            }
            lines.add(new ReturnLine(row,parseInt(itemModel.getValueAt(row,0),0),quantity,
                    parseMoney(itemModel.getValueAt(row,7))));
        }
        return lines;
    }

    private void normalizeReturnQuantities() {
        updatingModel = true;
        try {
            for (int row = 0; row < itemModel.getRowCount(); row++) {
                int available = parseInt(itemModel.getValueAt(row, 6), 0);
                int quantity = parseInt(itemModel.getValueAt(row, 9), 0);
                itemModel.setValueAt(Math.max(0, Math.min(quantity, available)), row, 9);
            }
        } finally {
            updatingModel = false;
        }
    }

    private void updateReturnTotal() {
        List<ReturnLine> lines = collectReturnLines();
        BigDecimal total = calculateReturnTotal(lines);
        if (!overrideRequired(total)) {
            clearApproval();
        } else if (loadedSale != null && loadedSale.requesterCanOverride()) {
            overrideStatusLabel.setText("Return override approved by: "
                    + SessionManager.getCurrentUserDisplayName());
        } else if (overrideApprovalToken != null
                && !approvalResource(lines, total).equals(overrideApprovedResource)) {
            clearApproval();
            overrideStatusLabel.setText("Return override required before submit");
        }
        totalReturnLabel.setText("Return Total: " + CURRENCY.format(total));
    }

    private boolean overrideRequired(BigDecimal total) {
        return loadedSale != null && loadedSale.returnApprovalLimit().signum() > 0
                && total.compareTo(loadedSale.returnApprovalLimit()) > 0;
    }

    private String approvalResource(List<ReturnLine> lines, BigDecimal total) {
        Map<Integer, Integer> quantities = new LinkedHashMap<>();
        for (ReturnLine line : lines) quantities.put(line.saleItemId(), line.quantity());
        return RefundApprovalIdentity.build(loadedSale.saleId(), total, quantities);
    }

    private void clearApproval() {
        overrideApprovalToken = null;
        overrideApprovalReason = null;
        overrideApprovedByName = null;
        overrideApprovedResource = null;
        overrideStatusLabel.setText("No active override approvals");
    }

    private BigDecimal calculateReturnTotal(List<ReturnLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (ReturnLine line : lines) {
            total = total.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        return CurrencyFormatter.normalize(total);
    }

    private void returnAllAvailable() {
        updatingModel = true;
        try {
            for (int row = 0; row < itemModel.getRowCount(); row++) {
                itemModel.setValueAt(itemModel.getValueAt(row, 6), row, 9);
            }
        } finally {
            updatingModel = false;
        }
        updateReturnTotal();
    }

    private void clearReturnQty() {
        updatingModel = true;
        try {
            for (int row = 0; row < itemModel.getRowCount(); row++) itemModel.setValueAt(0, row, 9);
        } finally {
            updatingModel = false;
        }
        updateReturnTotal();
    }

    private String refundFingerprint(LanApiClient.RefundRequest request) {
        StringBuilder value = new StringBuilder();
        value.append(request.saleId()).append('|').append(request.refundMethod()).append('|')
                .append(request.sourceLocationId()).append('|')
                .append(request.reason()).append('|').append(safe(request.approvalToken())).append('|')
                .append(safe(request.approvalReason()));
        for (LanApiClient.RefundLine line : request.lines()) {
            value.append('|').append(line.saleItemId()).append(':').append(line.quantity()).append(':').append(line.disposition()).append(':').append(line.destinationLocationId());
        }
        return value.toString();
    }

    private void showApiError(String prefix, Throwable ex) {
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "The SmartStock server could not complete the request." : ex.getMessage();
        JOptionPane.showMessageDialog(this, prefix + ": " + message,
                "SmartStock Server", JOptionPane.ERROR_MESSAGE);
    }

    private static int parseInt(Object value, int fallback) {
        try { return Integer.parseInt(String.valueOf(value).trim()); }
        catch (Exception ex) { return fallback; }
    }

    private static BigDecimal parseMoney(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        try { return new BigDecimal(String.valueOf(value).replace("$", "").replace(",", "").trim()); }
        catch (Exception ex) { return BigDecimal.ZERO; }
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeProductType(String value) {
        String normalized = safe(value).trim().toUpperCase().replace(' ', '_');
        return "SERVICE".equals(normalized) || "NON_INVENTORY".equals(normalized)
                ? normalized : "INVENTORY";
    }

    private record SaleSnapshot(int saleId, String receiptNumber, Integer customerId,
                                String paymentMethod, String paymentStatus, BigDecimal totalAmount,
                                BigDecimal returnedAmount, BigDecimal returnApprovalLimit,
                                boolean requesterCanOverride,Integer sourceLocationId) {
        boolean remote(){return sourceLocationId!=null&&!sourceLocationId.equals(SessionManager.getCurrentLocationId());}
    }

    private record ReturnLine(int row,int saleItemId, int quantity, BigDecimal unitPrice) {
    }

    private void loadStoreChoices(){Integer current=SessionManager.getCurrentLocationId();StoreChoice local=new StoreChoice(current,"Current Store");storeChoices.add(local);searchStoreBox.addItem(local);if(PermissionManager.hasPermission("PROCESS_RETURNS")&&PermissionManager.hasPermission("PROCESS_MULTI_STORE_RETURNS"))UiTaskRunner.submit(this,"return-sale.stores",LanApiClient::loadReturnStores,result->{for(var s:result){StoreChoice option=new StoreChoice(s.locationId(),s.name());storeChoices.add(option);searchStoreBox.addItem(option);if(java.util.Objects.equals(initialSourceLocationId,s.locationId()))searchStoreBox.setSelectedItem(option);}JComboBox<StoreChoice> destinations=new JComboBox<>(storeChoices.toArray(StoreChoice[]::new));if(returnItemsTable!=null)returnItemsTable.getColumnModel().getColumn(8).setCellEditor(new DefaultCellEditor(destinations));},ignored->{});}
    private Integer selectedSourceLocation(){StoreChoice value=(StoreChoice)searchStoreBox.getSelectedItem();return value==null?SessionManager.getCurrentLocationId():value.locationId();}
    private String defaultDestination(){Integer current=SessionManager.getCurrentLocationId();for(StoreChoice s:storeChoices)if(java.util.Objects.equals(s.locationId(),current))return s.label();return "Current Store";}
    private StoreChoice storeByLabel(String label){for(StoreChoice s:storeChoices)if(s.label().equals(label))return s;return null;}
    private record StoreChoice(Integer locationId,String label){@Override public String toString(){return label;}}

    private static final class NumberFormatAdapter {
        private final java.text.NumberFormat formatter = CurrencyFormatter.create(Locale.US);
        private String format(BigDecimal value) { return formatter.format(zero(value)); }
    }

    private static class TrailingTextRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            String text = value == null ? "" : String.valueOf(value);
            label.setHorizontalAlignment(SwingConstants.LEFT);
            label.setToolTipText(text);
            label.setText(clipBeginning(label, text,
                    table.getColumnModel().getColumn(column).getWidth()));
            return label;
        }

        private String clipBeginning(JLabel label, String text, int columnWidth) {
            if (text == null || text.isBlank()) return "";
            Insets insets = label.getInsets();
            int availableWidth = Math.max(0, columnWidth - insets.left - insets.right - 8);
            FontMetrics metrics = label.getFontMetrics(label.getFont());
            if (metrics.stringWidth(text) <= availableWidth) return text;
            String prefix = "...";
            int prefixWidth = metrics.stringWidth(prefix);
            if (prefixWidth >= availableWidth) return prefix;
            int low = 0;
            int high = text.length();
            while (low < high) {
                int mid = (low + high) / 2;
                if (prefixWidth + metrics.stringWidth(text.substring(mid)) <= availableWidth) high = mid;
                else low = mid + 1;
            }
            return prefix + text.substring(low);
        }
    }
}
