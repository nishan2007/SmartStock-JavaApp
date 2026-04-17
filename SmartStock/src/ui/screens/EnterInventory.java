package ui.screens;

import data.DB;
import managers.SessionManager;
import ui.components.AppMenuBar;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class EnterInventory extends JFrame {
    private JTextField searchField;
    private JTable inventoryTable;
    private DefaultTableModel inventoryModel;
    private boolean updatingInventoryRows = false;
    private JLabel selectedStoreLabel;
    private JLabel currentUserLabel;
    private JLabel currentDateLabel;
    private JLabel currentTimeLabel;
    private JLabel totalUnitsLabel;
    private String lastShownDate;
    private JPopupMenu searchPopup;
    private JTable searchResultsTable;
    private JScrollPane searchResultsScrollPane;
    private javax.swing.Timer searchDebounceTimer;

    public EnterInventory() {
        setTitle("Enter Inventory");
        setSize(1000, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "EnterInventory"));

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel searchPanel = new JPanel(new BorderLayout(10, 10));

        JLabel logoLabel = new JLabel();
        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        ImageIcon centerLogoIcon = loadCenterLogoIcon();
        if (centerLogoIcon != null) {
            Image scaledImage = centerLogoIcon.getImage().getScaledInstance(180, 80, Image.SCALE_SMOOTH);
            logoLabel.setIcon(new ImageIcon(scaledImage));
        } else {
            logoLabel.setText("SmartStock");
        }

        selectedStoreLabel = new JLabel("Store: Not selected");
        currentUserLabel = new JLabel("No User currently logged in");
        currentDateLabel = new JLabel("No date yet");
        currentTimeLabel = new JLabel("No time yet");

        JPanel rightSidePanel = new JPanel();
        rightSidePanel.setLayout(new BoxLayout(rightSidePanel, BoxLayout.Y_AXIS));
        selectedStoreLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        currentUserLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        currentDateLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        currentTimeLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        updateCurrentDateLabel();
        updateCurrentTimeLabel();
        startDateRefreshTimer();

        rightSidePanel.add(currentDateLabel);
        rightSidePanel.add(Box.createVerticalStrut(5));
        rightSidePanel.add(currentTimeLabel);
        rightSidePanel.add(Box.createVerticalStrut(10));
        rightSidePanel.add(selectedStoreLabel);
        rightSidePanel.add(Box.createVerticalStrut(10));
        rightSidePanel.add(currentUserLabel);

        searchPanel.add(logoLabel, BorderLayout.CENTER);
        searchPanel.add(rightSidePanel, BorderLayout.EAST);

        JPanel searchRow = new JPanel(new BorderLayout(10, 10));
        JLabel searchLabel = new JLabel("Search Product");
        searchField = new JTextField();
        JButton searchBtn = new JButton("Search");
        searchRow.add(searchLabel, BorderLayout.WEST);
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(searchBtn, BorderLayout.EAST);
        searchPanel.add(searchRow, BorderLayout.SOUTH);

        inventoryModel = new DefaultTableModel(
                new Object[]{"ID", "Name", "Description", "SKU", "Current Stock", "Qty to Add", "New Stock"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 5;
            }
        };
        inventoryTable = new JTable(inventoryModel);
        inventoryTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        inventoryTable.getColumnModel().getColumn(5).setCellEditor(new DefaultCellEditor(new JTextField()));
        configureInventoryTableColumns();

        JScrollPane inventoryScrollPane = new JScrollPane(inventoryTable);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        JButton removeSelectedBtn = new JButton("Remove Selected");
        JButton clearBtn = new JButton("Clear");
        JButton receiveBtn = new JButton("Add to Inventory");
        totalUnitsLabel = new JLabel("Units to Add: 0");
        bottomPanel.add(removeSelectedBtn);
        bottomPanel.add(clearBtn);
        bottomPanel.add(totalUnitsLabel);
        bottomPanel.add(receiveBtn);

        panel.add(searchPanel, BorderLayout.NORTH);
        panel.add(inventoryScrollPane, BorderLayout.CENTER);
        panel.add(bottomPanel, BorderLayout.SOUTH);
        add(panel);

        searchBtn.addActionListener(e -> searchProducts());
        searchField.addActionListener(e -> addSelectedSearchResultToInventory());
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            private void restartSearchDebounce() {
                if (searchDebounceTimer == null) {
                    searchDebounceTimer = new javax.swing.Timer(250, e -> searchProducts(false));
                    searchDebounceTimer.setRepeats(false);
                }
                searchDebounceTimer.restart();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(this::restartSearchDebounce);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(this::restartSearchDebounce);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(this::restartSearchDebounce);
            }
        });
        searchField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (searchResultsTable == null || searchResultsTable.getRowCount() == 0) {
                    return;
                }

                int selectedRow = searchResultsTable.getSelectedRow();
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_DOWN) {
                    int nextRow = Math.min(selectedRow + 1, searchResultsTable.getRowCount() - 1);
                    if (nextRow >= 0) {
                        searchResultsTable.setRowSelectionInterval(nextRow, nextRow);
                        searchResultsTable.scrollRectToVisible(searchResultsTable.getCellRect(nextRow, 0, true));
                    }
                    e.consume();
                } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_UP) {
                    int nextRow = Math.max(selectedRow - 1, 0);
                    if (nextRow >= 0) {
                        searchResultsTable.setRowSelectionInterval(nextRow, nextRow);
                        searchResultsTable.scrollRectToVisible(searchResultsTable.getCellRect(nextRow, 0, true));
                    }
                    e.consume();
                } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                    closeSearchPopup();
                }
            }
        });
        inventoryModel.addTableModelListener(e -> {
            if (updatingInventoryRows) {
                return;
            }
            if (e.getColumn() == 5 || e.getColumn() == javax.swing.event.TableModelEvent.ALL_COLUMNS) {
                updateNewStockTotals();
            }
        });
        removeSelectedBtn.addActionListener(e -> removeSelectedRow());
        clearBtn.addActionListener(e -> {
            inventoryModel.setRowCount(0);
            updateTotalUnitsLabel();
        });
        receiveBtn.addActionListener(e -> addInventory());

        updateSelectedStoreLabel();
        updateCurrentUserLabel();
        setVisible(true);
    }

    private ImageIcon loadCenterLogoIcon() {
        String[] resourcePaths = {
                "/Images/CenterLogo.png",
                "Images/CenterLogo.png",
                "/CenterLogo.png",
                "CenterLogo.png"
        };

        for (String path : resourcePaths) {
            URL url = getClass().getResource(path);
            if (url != null) {
                return new ImageIcon(url);
            }
        }

        String[] filePaths = {
                "src/main/Images/CenterLogo.png",
                "src/main/resources/Images/CenterLogo.png",
                "src/Images/CenterLogo.png",
                "Images/CenterLogo.png",
                "CenterLogo.png"
        };

        for (String path : filePaths) {
            ImageIcon icon = new ImageIcon(path);
            if (icon.getIconWidth() > 0) {
                return icon;
            }
        }

        return null;
    }

    private void configureInventoryTableColumns() {
        if (inventoryTable == null || inventoryTable.getColumnModel().getColumnCount() < 7) {
            return;
        }

        TableColumnModel columnModel = inventoryTable.getColumnModel();
        columnModel.getColumn(0).setMinWidth(40);
        columnModel.getColumn(0).setMaxWidth(70);
        columnModel.getColumn(0).setPreferredWidth(50);
        columnModel.getColumn(1).setMinWidth(90);
        columnModel.getColumn(1).setMaxWidth(220);
        columnModel.getColumn(1).setPreferredWidth(140);
        columnModel.getColumn(2).setMinWidth(220);
        columnModel.getColumn(2).setPreferredWidth(320);
        columnModel.getColumn(2).setCellRenderer(new MultiLineTableCellRenderer());
        columnModel.getColumn(3).setMinWidth(90);
        columnModel.getColumn(3).setPreferredWidth(110);
        columnModel.getColumn(4).setMinWidth(90);
        columnModel.getColumn(4).setMaxWidth(120);
        columnModel.getColumn(5).setMinWidth(80);
        columnModel.getColumn(5).setMaxWidth(110);
        columnModel.getColumn(6).setMinWidth(80);
        columnModel.getColumn(6).setMaxWidth(110);
        updateDescriptionRowHeights();
    }

    private void updateDescriptionRowHeights() {
        if (inventoryTable == null || inventoryTable.getRowCount() == 0) {
            return;
        }

        for (int row = 0; row < inventoryTable.getRowCount(); row++) {
            int rowHeight = 24;
            Object value = inventoryTable.getValueAt(row, 2);
            String text = value == null ? "" : value.toString();

            TableCellRenderer renderer = inventoryTable.getCellRenderer(row, 2);
            Component component = renderer.getTableCellRendererComponent(inventoryTable, text, false, false, row, 2);
            if (component instanceof JTextArea textArea) {
                int columnWidth = inventoryTable.getColumnModel().getColumn(2).getWidth();
                textArea.setSize(columnWidth, Short.MAX_VALUE);
                rowHeight = Math.max(rowHeight, textArea.getPreferredSize().height + 4);
            }

            inventoryTable.setRowHeight(row, rowHeight);
        }
    }

    private static class MultiLineTableCellRenderer extends JTextArea implements TableCellRenderer {
        public MultiLineTableCellRenderer() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setText(value == null ? "" : value.toString());
            setFont(table.getFont());
            if (isSelected) {
                setForeground(table.getSelectionForeground());
                setBackground(table.getSelectionBackground());
            } else {
                setForeground(table.getForeground());
                setBackground(table.getBackground());
            }
            setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            return this;
        }
    }

    private void searchProducts() {
        searchProducts(true);
    }

    private void searchProducts(boolean showMessages) {
        String searchText = searchField.getText().trim();

        if (SessionManager.getCurrentLocationId() == null) {
            JOptionPane.showMessageDialog(this, "No store is selected for this session.");
            return;
        }

        if (searchText.isEmpty()) {
            closeSearchPopup();
            if (showMessages) {
                JOptionPane.showMessageDialog(this, "Type a product name or SKU first.");
            }
            return;
        }

        String sql = """
                SELECT p.product_id, p.name, p.description, p.sku,
                       COALESCE(i.quantity_on_hand, 0) AS quantity_on_hand
                FROM products p
                LEFT JOIN inventory i
                    ON p.product_id = i.product_id
                   AND i.location_id = ?
                WHERE p.name ILIKE ? OR p.sku ILIKE ?
                ORDER BY p.name
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, SessionManager.getCurrentLocationId());
            ps.setString(2, "%" + searchText + "%");
            ps.setString(3, "%" + searchText + "%");

            ResultSet rs = ps.executeQuery();
            java.util.List<Object[]> rows = new java.util.ArrayList<>();

            while (rs.next()) {
                rows.add(new Object[]{
                        rs.getInt("product_id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("sku"),
                        rs.getInt("quantity_on_hand")
                });
            }

            if (rows.isEmpty()) {
                closeSearchPopup();
                if (showMessages) {
                    JOptionPane.showMessageDialog(this, "No matching products found.");
                }
                return;
            }

            showSearchResultsPopup(rows);

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Database error: " + e.getMessage());
        }
    }

    private void showSearchResultsPopup(java.util.List<Object[]> rows) {
        if (searchPopup == null) {
            searchPopup = new JPopupMenu();
            searchPopup.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
            searchPopup.setFocusable(false);

            String[] columns = {"ID", "Name", "Description", "SKU", "Stock"};
            DefaultTableModel resultsModel = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

            searchResultsTable = new JTable(resultsModel);
            searchResultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            searchResultsTable.setAutoCreateRowSorter(true);
            searchResultsTable.setRowHeight(24);
            JTableHeader header = searchResultsTable.getTableHeader();
            header.setReorderingAllowed(false);
            header.setPreferredSize(new Dimension(0, 0));
            header.setMinimumSize(new Dimension(0, 0));
            header.setMaximumSize(new Dimension(0, 0));
            header.setVisible(false);
            searchResultsTable.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        addSelectedSearchResultToInventory();
                    }
                }
            });

            searchResultsScrollPane = new JScrollPane(searchResultsTable);
            searchResultsScrollPane.setBorder(BorderFactory.createEmptyBorder());
            searchResultsScrollPane.setColumnHeaderView(null);
            searchResultsScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

            searchPopup.setLayout(new BorderLayout());
            searchPopup.add(searchResultsScrollPane, BorderLayout.CENTER);
        }

        DefaultTableModel model = (DefaultTableModel) searchResultsTable.getModel();
        model.setRowCount(0);
        for (Object[] row : rows) {
            model.addRow(row);
        }

        if (searchResultsTable.getRowCount() > 0) {
            searchResultsTable.setRowSelectionInterval(0, 0);
        }

        searchResultsScrollPane.setPreferredSize(new Dimension(Math.max(searchField.getWidth(), 500), 220));
        searchResultsTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        searchResultsTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        searchResultsTable.getColumnModel().getColumn(2).setPreferredWidth(240);
        searchResultsTable.getColumnModel().getColumn(3).setPreferredWidth(110);
        searchResultsTable.getColumnModel().getColumn(4).setPreferredWidth(70);

        if (searchPopup.isVisible()) {
            searchPopup.setVisible(false);
        }

        searchPopup.show(searchField, 0, searchField.getHeight());
        searchField.requestFocusInWindow();
    }

    private void addSelectedSearchResultToInventory() {
        if (searchResultsTable == null || searchResultsTable.getSelectedRow() == -1) {
            if (searchPopup != null && searchPopup.isVisible()) {
                JOptionPane.showMessageDialog(this, "Please select a product.");
            }
            return;
        }

        int selectedRow = searchResultsTable.convertRowIndexToModel(searchResultsTable.getSelectedRow());
        int productId = ((Number) searchResultsTable.getModel().getValueAt(selectedRow, 0)).intValue();
        String name = String.valueOf(searchResultsTable.getModel().getValueAt(selectedRow, 1));
        String description = String.valueOf(searchResultsTable.getModel().getValueAt(selectedRow, 2));
        String sku = String.valueOf(searchResultsTable.getModel().getValueAt(selectedRow, 3));
        int currentStock = ((Number) searchResultsTable.getModel().getValueAt(selectedRow, 4)).intValue();

        String qtyText = JOptionPane.showInputDialog(this, "Enter quantity to add:", "1");
        if (qtyText == null) {
            return;
        }

        int qty;
        try {
            qty = Integer.parseInt(qtyText.trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter a valid number.");
            return;
        }

        if (qty <= 0) {
            JOptionPane.showMessageDialog(this, "Quantity must be greater than zero.");
            return;
        }

        addToInventoryTable(productId, name, description, sku, currentStock, qty);
        closeSearchPopup();
        searchField.requestFocusInWindow();
        searchField.selectAll();
    }

    private void closeSearchPopup() {
        if (searchPopup != null) {
            searchPopup.setVisible(false);
        }
    }

    private void addToInventoryTable(int productId, String name, String description, String sku, int currentStock, int qty) {
        for (int i = 0; i < inventoryModel.getRowCount(); i++) {
            int existingProductId = Integer.parseInt(inventoryModel.getValueAt(i, 0).toString());
            if (existingProductId == productId) {
                int existingQty = Integer.parseInt(inventoryModel.getValueAt(i, 5).toString());
                int newQty = existingQty + qty;
                inventoryModel.setValueAt(newQty, i, 5);
                inventoryModel.setValueAt(currentStock + newQty, i, 6);
                updateTotalUnitsLabel();
                configureInventoryTableColumns();
                return;
            }
        }

        inventoryModel.addRow(new Object[]{productId, name, description, sku, currentStock, qty, currentStock + qty});
        updateNewStockTotals();
        configureInventoryTableColumns();
    }

    private void updateNewStockTotals() {
        updatingInventoryRows = true;
        try {
            for (int i = 0; i < inventoryModel.getRowCount(); i++) {
                int currentStock = parsePositiveInt(inventoryModel.getValueAt(i, 4), 0);
                int qtyToAdd = parsePositiveInt(inventoryModel.getValueAt(i, 5), 1);
                inventoryModel.setValueAt(qtyToAdd, i, 5);
                inventoryModel.setValueAt(currentStock + qtyToAdd, i, 6);
            }
            updateTotalUnitsLabel();
            updateDescriptionRowHeights();
            configureInventoryTableColumns();
        } finally {
            updatingInventoryRows = false;
        }
    }

    private int parsePositiveInt(Object value, int fallback) {
        try {
            int parsed = Integer.parseInt(value.toString().trim());
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private void updateTotalUnitsLabel() {
        int total = 0;
        for (int i = 0; i < inventoryModel.getRowCount(); i++) {
            total += parsePositiveInt(inventoryModel.getValueAt(i, 5), 0);
        }
        totalUnitsLabel.setText("Units to Add: " + total);
    }

    private void removeSelectedRow() {
        int selectedRow = inventoryTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a row to remove.");
            return;
        }

        int modelRow = inventoryTable.convertRowIndexToModel(selectedRow);
        inventoryModel.removeRow(modelRow);
        updateTotalUnitsLabel();
    }

    private void updateCurrentDateLabel() {
        if (currentDateLabel == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        lastShownDate = now.format(formatter);
        currentDateLabel.setText("Date: " + lastShownDate);
    }

    private void updateCurrentTimeLabel() {
        if (currentTimeLabel == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a");
        currentTimeLabel.setText("Time: " + now.format(formatter));
    }

    private void startDateRefreshTimer() {
        javax.swing.Timer dateTimer = new javax.swing.Timer(1000, e -> {
            updateCurrentTimeLabel();
            String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            if (!today.equals(lastShownDate)) {
                updateCurrentDateLabel();
            }
        });
        dateTimer.setInitialDelay(0);
        dateTimer.start();
    }

    private void updateCurrentUserLabel() {
        if (currentUserLabel == null) {
            return;
        }

        if (SessionManager.getCurrentUserId() == null || SessionManager.getCurrentUsername() == null) {
            currentUserLabel.setText("No User currently logged in");
        } else {
            currentUserLabel.setText("Current User: " + SessionManager.getCurrentUsername());
        }
    }

    private void updateSelectedStoreLabel() {
        if (selectedStoreLabel == null) {
            return;
        }

        if (SessionManager.getCurrentLocationId() == null || SessionManager.getCurrentLocationName() == null) {
            selectedStoreLabel.setText("Store: Not selected");
        } else {
            selectedStoreLabel.setText("Store: " + SessionManager.getCurrentLocationName() + " (ID: " + SessionManager.getCurrentLocationId() + ")");
        }
    }

    private void addInventory() {
        if (inventoryModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No inventory entries have been added.");
            return;
        }

        if (SessionManager.getCurrentLocationId() == null) {
            JOptionPane.showMessageDialog(this, "No store is selected for this session.");
            return;
        }

        if (SessionManager.getCurrentUserId() == null) {
            JOptionPane.showMessageDialog(this, "No user is logged in for this session.");
            return;
        }

        updateNewStockTotals();
        int locationId = SessionManager.getCurrentLocationId();

        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);

            try {
                String ensureInventorySql = "INSERT INTO inventory (product_id, location_id, quantity_on_hand, reorder_level) VALUES (?, ?, 0, 0) ON CONFLICT (product_id, location_id) DO NOTHING";
                String updateInventorySql = "UPDATE inventory SET quantity_on_hand = quantity_on_hand + ? WHERE product_id = ? AND location_id = ?";
                String insertMovementSql = "INSERT INTO inventory_movements (product_id, location_id, change_qty, reason, note) VALUES (?, ?, ?, ?, ?)";

                try (PreparedStatement ensureInventoryStmt = conn.prepareStatement(ensureInventorySql);
                     PreparedStatement updateInventoryStmt = conn.prepareStatement(updateInventorySql);
                     PreparedStatement movementStmt = conn.prepareStatement(insertMovementSql)) {

                    for (int i = 0; i < inventoryModel.getRowCount(); i++) {
                        int productId = Integer.parseInt(inventoryModel.getValueAt(i, 0).toString());
                        int qty = parsePositiveInt(inventoryModel.getValueAt(i, 5), 0);
                        if (qty <= 0) {
                            throw new SQLException("Quantity must be greater than zero for product " + productId + ".");
                        }

                        ensureInventoryStmt.setInt(1, productId);
                        ensureInventoryStmt.setInt(2, locationId);
                        ensureInventoryStmt.addBatch();

                        updateInventoryStmt.setInt(1, qty);
                        updateInventoryStmt.setInt(2, productId);
                        updateInventoryStmt.setInt(3, locationId);
                        updateInventoryStmt.addBatch();

                        movementStmt.setInt(1, productId);
                        movementStmt.setInt(2, locationId);
                        movementStmt.setInt(3, qty);
                        movementStmt.setString(4, "INVENTORY_ENTRY");
                        movementStmt.setString(5, "entered_by_user_id=" + SessionManager.getCurrentUserId());
                        movementStmt.addBatch();
                    }

                    ensureInventoryStmt.executeBatch();
                    updateInventoryStmt.executeBatch();
                    movementStmt.executeBatch();
                }

                conn.commit();
                JOptionPane.showMessageDialog(this, "Inventory added successfully.");
                inventoryModel.setRowCount(0);
                searchField.setText("");
                updateTotalUnitsLabel();
                configureInventoryTableColumns();

            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Inventory entry failed: " + ex.getMessage());
        }
    }
}
