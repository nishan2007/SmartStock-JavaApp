package ui.screens;

import managers.PermissionManager;
import managers.SessionManager;
import services.LanApiClient;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.UiDebouncer;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StoreTransfer extends JFrame {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");

    private final JLabel sourceStoreLabel;
    private final JComboBox<LocationOption> destinationBox;
    private final JTextField searchField;
    private final DefaultListModel<ProductOption> productListModel;
    private final JList<ProductOption> productList;
    private final JSpinner quantitySpinner;
    private final DefaultTableModel transferModel;
    private final JTable transferTable;
    private final JTextArea noteArea;
    private final DefaultTableModel incomingModel;
    private final JTable incomingTable;
    private final DefaultTableModel incomingItemsModel;
    private final JTable incomingItemsTable;
    private String pendingCreateKey;
    private String pendingCreateFingerprint;
    private Long pendingReceiveTransferId;
    private String pendingReceiveKey;
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

    public StoreTransfer() {
        setTitle("Store Transfer");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(16, 16));
        setJMenuBar(AppMenuBar.create(this, "StoreTransfer"));

        JPanel createTransferPanel = new JPanel(new BorderLayout(16, 16));
        createTransferPanel.setBorder(new EmptyBorder(18, 18, 18, 18));
        createTransferPanel.setBackground(new Color(245, 247, 250));

        JLabel titleLabel = new JLabel("Store Transfer");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setForeground(new Color(31, 41, 55));

        sourceStoreLabel = new JLabel();
        sourceStoreLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        refreshSourceStoreLabel();

        destinationBox = new JComboBox<>();
        JPanel destinationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        destinationPanel.setOpaque(false);
        destinationPanel.add(new JLabel("Transfer To:"));
        destinationBox.setPreferredSize(new Dimension(280, 30));
        destinationPanel.add(destinationBox);

        JPanel headerPanel = new JPanel();
        headerPanel.setOpaque(false);
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sourceStoreLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        destinationPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.add(titleLabel);
        headerPanel.add(Box.createVerticalStrut(6));
        headerPanel.add(sourceStoreLabel);
        headerPanel.add(Box.createVerticalStrut(12));
        headerPanel.add(destinationPanel);

        JPanel productPanel = new JPanel(new BorderLayout(8, 8));
        productPanel.setOpaque(false);
        productPanel.setBorder(BorderFactory.createTitledBorder("Find Item"));
        searchField = new JTextField();
        JButton searchButton = new JButton("Search");
        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setOpaque(false);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);
        productListModel = new DefaultListModel<>();
        productList = new JList<>(productListModel);
        productList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        productPanel.add(searchPanel, BorderLayout.NORTH);
        productPanel.add(new JScrollPane(productList), BorderLayout.CENTER);

        quantitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999999, 1));
        JButton addItemButton = new JButton("Add Item");
        JPanel addPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        addPanel.setOpaque(false);
        addPanel.add(new JLabel("Quantity:"));
        addPanel.add(quantitySpinner);
        addPanel.add(addItemButton);
        productPanel.add(addPanel, BorderLayout.SOUTH);

        transferModel = new DefaultTableModel(
                new Object[]{"Product ID", "SKU", "Name", "Available", "Transfer Qty"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 4;
            }
        };
        transferTable = new JTable(transferModel);
        transferTable.setRowHeight(28);
        JScrollPane transferScroll = new JScrollPane(transferTable);

        JButton removeButton = new JButton("Remove Selected");
        JButton submitButton = new JButton("Submit Transfer");
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(removeButton);
        actionPanel.add(submitButton);

        noteArea = new JTextArea(3, 30);
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        JPanel notePanel = new JPanel(new BorderLayout(8, 8));
        notePanel.setOpaque(false);
        notePanel.add(new JLabel("Notes:"), BorderLayout.NORTH);
        notePanel.add(new JScrollPane(noteArea), BorderLayout.CENTER);

        JPanel centerPanel = new JPanel(new BorderLayout(16, 16));
        centerPanel.setOpaque(false);
        centerPanel.add(productPanel, BorderLayout.WEST);
        centerPanel.add(transferScroll, BorderLayout.CENTER);

        JPanel footerPanel = new JPanel(new BorderLayout(12, 12));
        footerPanel.setOpaque(false);
        footerPanel.add(notePanel, BorderLayout.CENTER);
        footerPanel.add(actionPanel, BorderLayout.SOUTH);

        createTransferPanel.add(headerPanel, BorderLayout.NORTH);
        createTransferPanel.add(centerPanel, BorderLayout.CENTER);
        createTransferPanel.add(footerPanel, BorderLayout.SOUTH);

        incomingModel = new DefaultTableModel(
                new Object[]{"Transfer ID", "From Store", "Created", "Items", "Units", "Sent By", "Note"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        incomingTable = new JTable(incomingModel);
        incomingTable.setRowHeight(28);
        incomingTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        incomingItemsModel = new DefaultTableModel(
                new Object[]{"Product ID", "SKU", "Name", "Quantity"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        incomingItemsTable = new JTable(incomingItemsModel);
        incomingItemsTable.setRowHeight(28);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Create Transfer", createTransferPanel);
        tabbedPane.addTab("Incoming Transfers", buildIncomingPanel());
        add(tabbedPane, BorderLayout.CENTER);
        add(loadingState, BorderLayout.SOUTH);

        searchButton.addActionListener(e -> loadProducts());
        searchField.addActionListener(e -> loadProducts());
        UiDebouncer.bind(searchField, 300, this::loadProducts);
        addItemButton.addActionListener(e -> addSelectedProduct());
        removeButton.addActionListener(e -> removeSelectedRow());
        submitButton.addActionListener(e -> submitTransfer());
        incomingTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadIncomingTransferItems(getSelectedIncomingTransferId());
            }
        });

        loadLocations();
        loadProducts();
        loadIncomingTransfers();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildIncomingPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(18, 18, 18, 18));
        panel.setBackground(new Color(245, 247, 250));

        JLabel titleLabel = new JLabel("Incoming Transfers");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        titleLabel.setForeground(new Color(31, 41, 55));

        JButton refreshButton = new JButton("Refresh");
        JButton receiveButton = new JButton("Receive Selected Transfer");
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(refreshButton);
        actionPanel.add(receiveButton);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(actionPanel, BorderLayout.EAST);

        refreshButton.addActionListener(e -> loadIncomingTransfers());
        receiveButton.addActionListener(e -> receiveSelectedTransfer());

        panel.add(headerPanel, BorderLayout.NORTH);
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(incomingTable),
                buildIncomingItemsPanel()
        );
        splitPane.setResizeWeight(0.58);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildIncomingItemsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);
        JLabel label = new JLabel("Selected Transfer Items");
        label.setFont(new Font("SansSerif", Font.BOLD, 15));
        panel.add(label, BorderLayout.NORTH);
        panel.add(new JScrollPane(incomingItemsTable), BorderLayout.CENTER);
        return panel;
    }

    private void refreshSourceStoreLabel() {
        Integer locationId = SessionManager.getCurrentLocationId();
        String locationName = SessionManager.getCurrentLocationName();
        if (locationId == null) {
            sourceStoreLabel.setText("Transfer From: No store selected");
        } else {
            sourceStoreLabel.setText("Transfer From: " + safe(locationName) + " (ID: " + locationId + ")");
        }
    }

    private void loadLocations() {
        CachedUiLoader.load(this, "reference:transfer-destinations", TransferLocationsSnapshot.class,
                SessionDataCache.REFERENCE_TTL, loadingState,
                () -> new TransferLocationsSnapshot(LanApiClient.loadTransferDestinations()),
                this::applyLocations);
    }

    private void applyLocations(TransferLocationsSnapshot snapshot) {
        destinationBox.removeAllItems();
        for (LanApiClient.TransferLocation location : snapshot.locations()) {
                destinationBox.addItem(new LocationOption(location.locationId(), location.name()));
        }
    }

    private void loadProducts() {
        productListModel.clear();
        Integer sourceLocationId = SessionManager.getCurrentLocationId();
        if (sourceLocationId == null) {
            return;
        }

        String search = searchField.getText().trim();
        CachedUiLoader.load(this, "transfer-products.search", "transfer-products:" + search, TransferProductsSnapshot.class,
                SessionDataCache.SCREEN_TTL, loadingState,
                () -> new TransferProductsSnapshot(LanApiClient.searchTransferProducts(search)),
                this::applyProducts);
    }

    private void applyProducts(TransferProductsSnapshot snapshot) {
        productListModel.clear();
        for (LanApiClient.TransferProduct product : snapshot.products()) {
                productListModel.addElement(new ProductOption(product.productId(), product.sku(), product.name(),
                        product.availableQuantity()));
        }
    }

    private void addSelectedProduct() {
        ProductOption product = productList.getSelectedValue();
        if (product == null) {
            JOptionPane.showMessageDialog(this, "Select an item to transfer.");
            return;
        }

        int qty = (Integer) quantitySpinner.getValue();
        for (int i = 0; i < transferModel.getRowCount(); i++) {
            int existingProductId = Integer.parseInt(String.valueOf(transferModel.getValueAt(i, 0)));
            if (existingProductId == product.productId()) {
                int currentQty = parseInt(transferModel.getValueAt(i, 4), 0);
                int newQty = currentQty + qty;
                transferModel.setValueAt(newQty, i, 4);
                return;
            }
        }

        transferModel.addRow(new Object[]{
                product.productId(),
                product.sku(),
                product.name(),
                product.availableQuantity(),
                qty
        });
    }

    private void removeSelectedRow() {
        int selectedRow = transferTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }
        transferModel.removeRow(transferTable.convertRowIndexToModel(selectedRow));
    }

    private void submitTransfer() {
        if (!PermissionManager.requirePermission("STORE_TRANSFER", this, "Submit Transfer")) {
            return;
        }
        Integer sourceLocationId = SessionManager.getCurrentLocationId();
        LocationOption destination = (LocationOption) destinationBox.getSelectedItem();
        if (sourceLocationId == null) {
            JOptionPane.showMessageDialog(this, "No source store is selected for this session.");
            return;
        }
        if (destination == null) {
            JOptionPane.showMessageDialog(this, "Select the destination store.");
            return;
        }
        if (destination.locationId() == sourceLocationId) {
            JOptionPane.showMessageDialog(this, "Source and destination stores must be different.");
            return;
        }
        if (transferModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Add at least one item to transfer.");
            return;
        }

        List<LanApiClient.TransferLine> items = new ArrayList<>();
        for (int i = 0; i < transferModel.getRowCount(); i++) {
            int productId = Integer.parseInt(String.valueOf(transferModel.getValueAt(i, 0)));
            int available = parseInt(transferModel.getValueAt(i, 3), 0);
            int quantity = parseInt(transferModel.getValueAt(i, 4), 0);
            if (quantity <= 0) {
                JOptionPane.showMessageDialog(this, "Transfer quantity must be greater than zero.");
                return;
            }
            items.add(new LanApiClient.TransferLine(productId, quantity));
        }

        try {
            LanApiClient.CreateTransferRequest request = new LanApiClient.CreateTransferRequest(
                    destination.locationId(), noteArea.getText().trim(), List.copyOf(items));
            String fingerprint = request.toString();
            if (!fingerprint.equals(pendingCreateFingerprint) || pendingCreateKey == null) {
                pendingCreateFingerprint = fingerprint;
                pendingCreateKey = UUID.randomUUID().toString();
            }
            LanApiClient.CreateTransferResult result =
                    LanApiClient.createTransfer(request, pendingCreateKey);
            SessionDataCache.invalidate("transfer-products:");
            SessionDataCache.invalidate("incoming-transfer");
            pendingCreateKey = null;
            pendingCreateFingerprint = null;

            JOptionPane.showMessageDialog(this,
                    "Transfer sent successfully.\nTransfer ID: " + result.transferId()
                            + "\nThe receiving store must verify it before stock is added.");
            transferModel.setRowCount(0);
            noteArea.setText("");
            loadProducts();
            loadIncomingTransfers();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Transfer failed: " + ex.getMessage(),
                    "Transfer Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadIncomingTransfers() {
        if (SessionManager.getCurrentLocationId() == null) {
            return;
        }
        CachedUiLoader.load(this, "incoming-transfers", IncomingTransfersSnapshot.class,
                SessionDataCache.SCREEN_TTL, loadingState,
                () -> new IncomingTransfersSnapshot(LanApiClient.loadIncomingTransfers()),
                this::applyIncomingTransfers);
    }

    private void applyIncomingTransfers(IncomingTransfersSnapshot snapshot) {
        incomingModel.setRowCount(0);
        incomingItemsModel.setRowCount(0);
        for (LanApiClient.IncomingTransfer transfer : snapshot.transfers()) {
                incomingModel.addRow(new Object[]{
                        transfer.transferId(),
                        transfer.fromStore(),
                        formatLocalTimestamp(transfer.createdAtEpochMillis()),
                        transfer.itemCount(),
                        transfer.unitCount(),
                        transfer.sentBy(),
                        transfer.note()
                });
        }
    }

    private Long getSelectedIncomingTransferId() {
        int selectedRow = incomingTable.getSelectedRow();
        if (selectedRow < 0) {
            return null;
        }
        int modelRow = incomingTable.convertRowIndexToModel(selectedRow);
        return Long.parseLong(String.valueOf(incomingModel.getValueAt(modelRow, 0)));
    }

    private void loadIncomingTransferItems(Long transferId) {
        if (transferId == null) {
            incomingItemsModel.setRowCount(0);
            return;
        }
        CachedUiLoader.load(this, "incoming-transfer-items.selection", "incoming-transfer-items:" + transferId, TransferItemsSnapshot.class,
                SessionDataCache.SCREEN_TTL, loadingState,
                () -> new TransferItemsSnapshot(LanApiClient.loadTransferItems(transferId)),
                this::applyTransferItems);
    }

    private void applyTransferItems(TransferItemsSnapshot snapshot) {
        incomingItemsModel.setRowCount(0);
        for (LanApiClient.TransferDetailItem item : snapshot.items()) {
                incomingItemsModel.addRow(new Object[]{
                        item.productId(), item.sku(), item.name(), item.quantity()
                });
        }
    }

    private record TransferLocationsSnapshot(List<LanApiClient.TransferLocation> locations) { }
    private record TransferProductsSnapshot(List<LanApiClient.TransferProduct> products) { }
    private record IncomingTransfersSnapshot(List<LanApiClient.IncomingTransfer> transfers) { }
    private record TransferItemsSnapshot(List<LanApiClient.TransferDetailItem> items) { }

    private void receiveSelectedTransfer() {
        if (!PermissionManager.requirePermission("STORE_TRANSFER", this, "Receive Transfer")) {
            return;
        }
        int selectedRow = incomingTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Select an incoming transfer to receive.");
            return;
        }

        int modelRow = incomingTable.convertRowIndexToModel(selectedRow);
        long transferId = Long.parseLong(String.valueOf(incomingModel.getValueAt(modelRow, 0)));
        loadIncomingTransferItems(transferId);
        int result = JOptionPane.showConfirmDialog(
                this,
                buildReceiveConfirmationMessage(transferId),
                "Receive Transfer",
                JOptionPane.YES_NO_OPTION
        );
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        if (SessionManager.getCurrentLocationId() == null) {
            JOptionPane.showMessageDialog(this, "No receiving store is selected.");
            return;
        }

        try {
            if (pendingReceiveTransferId == null || pendingReceiveTransferId != transferId
                    || pendingReceiveKey == null) {
                pendingReceiveTransferId = transferId;
                pendingReceiveKey = UUID.randomUUID().toString();
            }
            LanApiClient.ReceiveTransferResult response =
                    LanApiClient.receiveTransfer(transferId, pendingReceiveKey);
            SessionDataCache.invalidate("transfer-products:");
            SessionDataCache.invalidate("incoming-transfer");
            pendingReceiveTransferId = null;
            pendingReceiveKey = null;

            JOptionPane.showMessageDialog(this,
                    "Transfer received successfully.\nReceive ID: " + response.receiveId());
            loadIncomingTransfers();
            incomingItemsModel.setRowCount(0);
            loadProducts();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to receive transfer: " + ex.getMessage(),
                    "Transfer Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String buildReceiveConfirmationMessage(long transferId) {
        StringBuilder message = new StringBuilder("Receive transfer #")
                .append(transferId)
                .append(" into this store's inventory?\n\nItems:\n");

        if (incomingItemsModel.getRowCount() == 0) {
            message.append("No item lines found.");
            return message.toString();
        }

        for (int i = 0; i < incomingItemsModel.getRowCount(); i++) {
            message.append("- ")
                    .append(incomingItemsModel.getValueAt(i, 2))
                    .append(" | SKU: ")
                    .append(incomingItemsModel.getValueAt(i, 1))
                    .append(" | Qty: ")
                    .append(incomingItemsModel.getValueAt(i, 3))
                    .append("\n");
        }

        return message.toString();
    }

    private static int parseInt(Object value, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String formatLocalTimestamp(long epochMillis) {
        if (epochMillis <= 0) {
            return "";
        }
        return Instant.ofEpochMilli(epochMillis)
                .atZone(StoreTimeZoneHelper.getStoreZone())
                .format(DATE_TIME_FORMAT);
    }

    private record LocationOption(int locationId, String name) {
        @Override
        public String toString() {
            return name + " (ID: " + locationId + ")";
        }
    }

    private record ProductOption(int productId, String sku, String name, int availableQuantity) {
        @Override
        public String toString() {
            return name + " | " + sku + " | Available: " + availableQuantity;
        }
    }

}
