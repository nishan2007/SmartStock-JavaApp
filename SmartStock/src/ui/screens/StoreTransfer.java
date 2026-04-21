package ui.screens;

import data.DB;
import managers.ReceiptNumberManager;
import managers.SessionManager;
import ui.components.AppMenuBar;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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

        searchButton.addActionListener(e -> loadProducts());
        searchField.addActionListener(e -> loadProducts());
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
        destinationBox.removeAllItems();
        Integer sourceLocationId = SessionManager.getCurrentLocationId();
        String sql = "SELECT location_id, name FROM locations ORDER BY name";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int locationId = rs.getInt("location_id");
                if (sourceLocationId != null && locationId == sourceLocationId) {
                    continue;
                }
                destinationBox.addItem(new LocationOption(locationId, rs.getString("name")));
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load stores: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadProducts() {
        productListModel.clear();
        Integer sourceLocationId = SessionManager.getCurrentLocationId();
        if (sourceLocationId == null) {
            return;
        }

        String search = searchField.getText().trim();
        StringBuilder sql = new StringBuilder("""
                SELECT p.product_id,
                       p.sku,
                       p.name,
                       COALESCE(i.quantity_on_hand, 0) AS quantity_on_hand
                FROM products p
                JOIN inventory i ON i.product_id = p.product_id
                WHERE i.location_id = ?
                """);
        boolean hasSearch = !search.isBlank();
        if (hasSearch) {
            sql.append(" AND (CAST(p.product_id AS TEXT) ILIKE ? OR p.sku ILIKE ? OR p.name ILIKE ?)");
        }
        sql.append(" ORDER BY p.name ASC LIMIT 100");

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, sourceLocationId);
            if (hasSearch) {
                String pattern = "%" + search + "%";
                ps.setString(2, pattern);
                ps.setString(3, pattern);
                ps.setString(4, pattern);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    productListModel.addElement(new ProductOption(
                            rs.getInt("product_id"),
                            rs.getString("sku"),
                            rs.getString("name"),
                            rs.getInt("quantity_on_hand")
                    ));
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load products: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addSelectedProduct() {
        ProductOption product = productList.getSelectedValue();
        if (product == null) {
            JOptionPane.showMessageDialog(this, "Select an item to transfer.");
            return;
        }

        int qty = (Integer) quantitySpinner.getValue();
        if (qty > product.availableQuantity()) {
            JOptionPane.showMessageDialog(this, "Transfer quantity cannot be higher than the available stock.");
            return;
        }

        for (int i = 0; i < transferModel.getRowCount(); i++) {
            int existingProductId = Integer.parseInt(String.valueOf(transferModel.getValueAt(i, 0)));
            if (existingProductId == product.productId()) {
                int currentQty = parseInt(transferModel.getValueAt(i, 4), 0);
                int newQty = currentQty + qty;
                if (newQty > product.availableQuantity()) {
                    JOptionPane.showMessageDialog(this, "Total transfer quantity cannot be higher than the available stock.");
                    return;
                }
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

        List<TransferItem> items = new ArrayList<>();
        for (int i = 0; i < transferModel.getRowCount(); i++) {
            int productId = Integer.parseInt(String.valueOf(transferModel.getValueAt(i, 0)));
            int available = parseInt(transferModel.getValueAt(i, 3), 0);
            int qty = parseInt(transferModel.getValueAt(i, 4), 0);
            if (qty <= 0) {
                JOptionPane.showMessageDialog(this, "Transfer quantity must be greater than zero.");
                return;
            }
            if (qty > available) {
                JOptionPane.showMessageDialog(this, "Transfer quantity cannot be higher than available stock for product " + productId + ".");
                return;
            }
            items.add(new TransferItem(productId, qty));
        }

        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long transferId = createTransfer(conn, sourceLocationId, destination, items);
                conn.commit();
                JOptionPane.showMessageDialog(this, "Transfer sent successfully.\nTransfer ID: " + transferId + "\nThe receiving store must verify it before stock is added.");
                transferModel.setRowCount(0);
                noteArea.setText("");
                loadProducts();
                loadIncomingTransfers();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Transfer failed: " + ex.getMessage(), "Transfer Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private long createTransfer(Connection conn, int sourceLocationId, LocationOption destination, List<TransferItem> items) throws SQLException {
        String insertTransferSql = """
                INSERT INTO store_transfers (
                    from_location_id,
                    to_location_id,
                    user_id,
                    user_name,
                    note
                )
                VALUES (?, ?, ?, ?, ?)
                """;
        String insertTransferItemSql = "INSERT INTO store_transfer_items (transfer_id, product_id, quantity) VALUES (?, ?, ?)";
        String ensureInventorySql = "INSERT INTO inventory (product_id, location_id, quantity_on_hand, reorder_level) VALUES (?, ?, 0, 0) ON CONFLICT (product_id, location_id) DO NOTHING";
        String subtractInventorySql = "UPDATE inventory SET quantity_on_hand = quantity_on_hand - ? WHERE product_id = ? AND location_id = ? AND quantity_on_hand >= ?";
        String insertMovementSql = "INSERT INTO inventory_movements (product_id, location_id, change_qty, reason, note, user_name) VALUES (?, ?, ?, ?, ?, ?)";

        long transferId;
        try (PreparedStatement transferStmt = conn.prepareStatement(insertTransferSql, Statement.RETURN_GENERATED_KEYS)) {
            transferStmt.setInt(1, sourceLocationId);
            transferStmt.setInt(2, destination.locationId());
            setNullableInteger(transferStmt, 3, SessionManager.getCurrentUserId());
            transferStmt.setString(4, SessionManager.getCurrentUserDisplayName());
            transferStmt.setString(5, noteArea.getText().trim());
            transferStmt.executeUpdate();

            try (ResultSet keys = transferStmt.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Failed to create transfer record.");
                }
                transferId = keys.getLong(1);
            }
        }

        try (PreparedStatement itemStmt = conn.prepareStatement(insertTransferItemSql);
             PreparedStatement ensureInventoryStmt = conn.prepareStatement(ensureInventorySql);
             PreparedStatement subtractStmt = conn.prepareStatement(subtractInventorySql);
             PreparedStatement movementStmt = conn.prepareStatement(insertMovementSql)) {

            for (TransferItem item : items) {
                ensureInventoryStmt.setInt(1, item.productId());
                ensureInventoryStmt.setInt(2, sourceLocationId);
                ensureInventoryStmt.addBatch();
            }
            ensureInventoryStmt.executeBatch();

            for (TransferItem item : items) {
                subtractStmt.setInt(1, item.quantity());
                subtractStmt.setInt(2, item.productId());
                subtractStmt.setInt(3, sourceLocationId);
                subtractStmt.setInt(4, item.quantity());
                int updated = subtractStmt.executeUpdate();
                if (updated == 0) {
                    throw new SQLException("Not enough stock to transfer product " + item.productId() + ".");
                }

                itemStmt.setLong(1, transferId);
                itemStmt.setInt(2, item.productId());
                itemStmt.setInt(3, item.quantity());
                itemStmt.addBatch();

                String note = "transfer_id=" + transferId + "; from_location_id=" + sourceLocationId + "; to_location_id=" + destination.locationId();
                movementStmt.setInt(1, item.productId());
                movementStmt.setInt(2, sourceLocationId);
                movementStmt.setInt(3, -item.quantity());
                movementStmt.setString(4, "TRANSFER_OUT");
                movementStmt.setString(5, note);
                movementStmt.setString(6, SessionManager.getCurrentUserDisplayName());
                movementStmt.addBatch();

            }

            itemStmt.executeBatch();
            movementStmt.executeBatch();
        }

        return transferId;
    }

    private void loadIncomingTransfers() {
        incomingModel.setRowCount(0);
        incomingItemsModel.setRowCount(0);
        Integer currentLocationId = SessionManager.getCurrentLocationId();
        if (currentLocationId == null) {
            return;
        }

        String sql = """
                SELECT st.transfer_id,
                       COALESCE(l.name, 'Unknown') AS from_store,
                       (st.created_at AT TIME ZONE ?) AS local_created_at,
                       COALESCE(st.user_name, '') AS sent_by,
                       COALESCE(st.note, '') AS note,
                       COUNT(sti.transfer_item_id) AS item_count,
                       COALESCE(SUM(sti.quantity), 0) AS unit_count
                FROM store_transfers st
                LEFT JOIN store_transfer_items sti ON sti.transfer_id = st.transfer_id
                LEFT JOIN locations l ON l.location_id = st.from_location_id
                WHERE st.to_location_id = ?
                  AND UPPER(COALESCE(st.status, 'PENDING')) = 'PENDING'
                GROUP BY st.transfer_id, l.name, st.created_at, st.user_name, st.note
                ORDER BY st.created_at ASC, st.transfer_id ASC
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, StoreTimeZoneHelper.getStoreZoneId());
            ps.setInt(2, currentLocationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    incomingModel.addRow(new Object[]{
                            rs.getLong("transfer_id"),
                            rs.getString("from_store"),
                            formatLocalTimestamp(rs.getTimestamp("local_created_at")),
                            rs.getInt("item_count"),
                            rs.getInt("unit_count"),
                            rs.getString("sent_by"),
                            rs.getString("note")
                    });
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load incoming transfers: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
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
        incomingItemsModel.setRowCount(0);
        if (transferId == null) {
            return;
        }

        String sql = """
                SELECT sti.product_id,
                       COALESCE(p.sku, '') AS sku,
                       COALESCE(p.name, 'Unknown') AS product_name,
                       sti.quantity
                FROM store_transfer_items sti
                LEFT JOIN products p ON p.product_id = sti.product_id
                WHERE sti.transfer_id = ?
                ORDER BY p.name ASC, sti.product_id ASC
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, transferId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    incomingItemsModel.addRow(new Object[]{
                            rs.getInt("product_id"),
                            rs.getString("sku"),
                            rs.getString("product_name"),
                            rs.getInt("quantity")
                    });
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load transfer items: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void receiveSelectedTransfer() {
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

        Integer locationId = SessionManager.getCurrentLocationId();
        if (locationId == null) {
            JOptionPane.showMessageDialog(this, "No receiving store is selected.");
            return;
        }

        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String receiveId = receiveTransfer(conn, transferId, locationId);
                conn.commit();
                JOptionPane.showMessageDialog(this, "Transfer received successfully.\nReceive ID: " + receiveId);
                loadIncomingTransfers();
                incomingItemsModel.setRowCount(0);
                loadProducts();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to receive transfer: " + ex.getMessage(), "Transfer Error", JOptionPane.ERROR_MESSAGE);
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

    private String receiveTransfer(Connection conn, long transferId, int receivingLocationId) throws Exception {
        ReceiptNumberManager.ReceiveNumber receive = ReceiptNumberManager.nextReceive(receivingLocationId);
        String lockTransferSql = """
                SELECT transfer_id, from_location_id, to_location_id, status
                FROM store_transfers
                WHERE transfer_id = ?
                FOR UPDATE
                """;
        int fromLocationId;
        try (PreparedStatement ps = conn.prepareStatement(lockTransferSql)) {
            ps.setLong(1, transferId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Transfer not found.");
                }
                if (rs.getInt("to_location_id") != receivingLocationId) {
                    throw new SQLException("This transfer belongs to a different receiving store.");
                }
                if (!"PENDING".equalsIgnoreCase(rs.getString("status"))) {
                    throw new SQLException("This transfer has already been received.");
                }
                fromLocationId = rs.getInt("from_location_id");
            }
        }

        String insertReceivingBatchSql = """
                INSERT INTO receiving_batches (
                    receive_id,
                    location_id,
                    user_id,
                    user_name,
                    receive_device_id,
                    receive_sequence
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        String selectItemsSql = "SELECT product_id, quantity FROM store_transfer_items WHERE transfer_id = ?";
        String ensureInventorySql = "INSERT INTO inventory (product_id, location_id, quantity_on_hand, reorder_level) VALUES (?, ?, 0, 0) ON CONFLICT (product_id, location_id) DO NOTHING";
        String addInventorySql = "UPDATE inventory SET quantity_on_hand = quantity_on_hand + ? WHERE product_id = ? AND location_id = ?";
        String insertMovementSql = """
                INSERT INTO inventory_movements (
                    product_id,
                    location_id,
                    change_qty,
                    reason,
                    note,
                    user_name,
                    receive_id,
                    receive_device_id,
                    receive_sequence
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String updateTransferSql = """
                UPDATE store_transfers
                SET status = 'RECEIVED',
                    received_at = CURRENT_TIMESTAMP,
                    received_by_user_id = ?,
                    received_by_name = ?,
                    receive_id = ?
                WHERE transfer_id = ?
                """;

        List<TransferItem> items = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(selectItemsSql)) {
            ps.setLong(1, transferId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new TransferItem(rs.getInt("product_id"), rs.getInt("quantity")));
                }
            }
        }
        if (items.isEmpty()) {
            throw new SQLException("Transfer has no items.");
        }

        try (PreparedStatement receivingBatchStmt = conn.prepareStatement(insertReceivingBatchSql);
             PreparedStatement ensureInventoryStmt = conn.prepareStatement(ensureInventorySql);
             PreparedStatement addInventoryStmt = conn.prepareStatement(addInventorySql);
             PreparedStatement movementStmt = conn.prepareStatement(insertMovementSql);
             PreparedStatement updateTransferStmt = conn.prepareStatement(updateTransferSql)) {

            receivingBatchStmt.setString(1, receive.receiveId());
            receivingBatchStmt.setInt(2, receivingLocationId);
            setNullableInteger(receivingBatchStmt, 3, SessionManager.getCurrentUserId());
            receivingBatchStmt.setString(4, SessionManager.getCurrentUserDisplayName());
            receivingBatchStmt.setString(5, receive.deviceId());
            receivingBatchStmt.setInt(6, receive.sequence());
            receivingBatchStmt.executeUpdate();

            for (TransferItem item : items) {
                ensureInventoryStmt.setInt(1, item.productId());
                ensureInventoryStmt.setInt(2, receivingLocationId);
                ensureInventoryStmt.addBatch();
            }
            ensureInventoryStmt.executeBatch();

            for (TransferItem item : items) {
                addInventoryStmt.setInt(1, item.quantity());
                addInventoryStmt.setInt(2, item.productId());
                addInventoryStmt.setInt(3, receivingLocationId);
                addInventoryStmt.addBatch();

                movementStmt.setInt(1, item.productId());
                movementStmt.setInt(2, receivingLocationId);
                movementStmt.setInt(3, item.quantity());
                movementStmt.setString(4, "INVENTORY_ENTRY");
                movementStmt.setString(5, "transfer_id=" + transferId + "; from_location_id=" + fromLocationId + "; received_by_user_id=" + SessionManager.getCurrentUserId());
                movementStmt.setString(6, SessionManager.getCurrentUserDisplayName());
                movementStmt.setString(7, receive.receiveId());
                movementStmt.setString(8, receive.deviceId());
                movementStmt.setInt(9, receive.sequence());
                movementStmt.addBatch();
            }

            addInventoryStmt.executeBatch();
            movementStmt.executeBatch();

            setNullableInteger(updateTransferStmt, 1, SessionManager.getCurrentUserId());
            updateTransferStmt.setString(2, SessionManager.getCurrentUserDisplayName());
            updateTransferStmt.setString(3, receive.receiveId());
            updateTransferStmt.setLong(4, transferId);
            updateTransferStmt.executeUpdate();
        }

        return receive.receiveId();
    }

    private static void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
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

    private static String formatLocalTimestamp(Timestamp timestamp) {
        return StoreTimeZoneHelper.formatLocalTimestamp(timestamp, DATE_TIME_FORMAT);
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

    private record TransferItem(int productId, int quantity) {
    }
}
