package ui.screens;

import data.DB;
import managers.ReceiptNumberManager;
import managers.SessionManager;
import ui.components.AppMenuBar;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReturnSale extends JFrame {
    private final JTextField saleSearchField = new JTextField();
    private final JLabel saleInfoLabel = new JLabel("Load a sale to begin.");
    private final JComboBox<String> refundMethodBox = new JComboBox<>(new String[]{"CASH", "CARD", "CHEQUE", "ACCOUNT"});
    private final JTextArea reasonArea = new JTextArea(3, 30);
    private final JLabel totalReturnLabel = new JLabel("Return Total: $0.00");
    private final DefaultTableModel itemModel;
    private final DefaultTableModel saleSearchModel;
    private final JTable saleSearchTable;
    private final JPopupMenu saleSearchPopup = new JPopupMenu();
    private final Timer saleSearchTimer;
    private SaleSnapshot loadedSale;
    private boolean updatingModel;
    private boolean selectingSearchResult;

    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(Locale.US);
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");

    public ReturnSale() {
        this(null);
    }

    public ReturnSale(Integer saleId) {
        setTitle("Process Return");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(14, 14));
        setJMenuBar(AppMenuBar.create(this, "ReturnSale"));

        itemModel = new DefaultTableModel(
                new Object[]{"Sale Item ID", "Product ID", "SKU", "Item", "Sold", "Returned", "Available", "Unit Price", "Return Qty"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 8;
            }
        };
        saleSearchModel = new DefaultTableModel(
                new Object[]{"Sale ID", "Receipt", "Date / Time", "Total", "Cashier", "Device"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        saleSearchTable = new JTable(saleSearchModel);
        saleSearchTimer = new Timer(250, e -> refreshSaleSearchResults());
        saleSearchTimer.setRepeats(false);

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.setBackground(new Color(245, 247, 250));
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
            loadSaleById(saleId);
        }

        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);

        JLabel titleLabel = new JLabel("Process Return");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        titleLabel.setForeground(new Color(31, 41, 55));

        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setOpaque(false);
        saleSearchField.setToolTipText("Sale ID or receipt number");
        JButton loadButton = new JButton("Load Sale");
        searchPanel.add(new JLabel("Sale / Receipt:"), BorderLayout.WEST);
        searchPanel.add(saleSearchField, BorderLayout.CENTER);
        searchPanel.add(loadButton, BorderLayout.EAST);

        saleInfoLabel.setForeground(new Color(71, 85, 105));
        JPanel top = new JPanel(new BorderLayout(12, 8));
        top.setOpaque(false);
        top.add(titleLabel, BorderLayout.NORTH);
        top.add(searchPanel, BorderLayout.CENTER);
        top.add(saleInfoLabel, BorderLayout.SOUTH);

        loadButton.addActionListener(e -> loadSale());
        saleSearchField.addActionListener(e -> loadSale());
        saleSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                scheduleSaleSearch();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                scheduleSaleSearch();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                scheduleSaleSearch();
            }
        });
        setupSaleSearchPopup();
        panel.add(top, BorderLayout.CENTER);
        return panel;
    }

    private void setupSaleSearchPopup() {
        saleSearchTable.setRowHeight(26);
        saleSearchTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        saleSearchTable.getTableHeader().setReorderingAllowed(false);
        configureSaleSearchColumns();
        saleSearchTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 1) {
                    selectSaleSearchResult();
                }
            }
        });

        JTableHeader header = saleSearchTable.getTableHeader();
        JScrollPane scrollPane = new JScrollPane(saleSearchTable);
        JPanel popupPanel = new JPanel(new BorderLayout());
        popupPanel.add(header, BorderLayout.NORTH);
        popupPanel.add(scrollPane, BorderLayout.CENTER);
        saleSearchPopup.setBorder(BorderFactory.createLineBorder(new Color(148, 163, 184)));
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

    private JScrollPane buildTablePanel() {
        JTable table = new JTable(itemModel);
        table.setRowHeight(28);
        table.getTableHeader().setReorderingAllowed(false);
        table.removeColumn(table.getColumnModel().getColumn(0));
        table.removeColumn(table.getColumnModel().getColumn(0));
        return new JScrollPane(table);
    }

    private JPanel buildFooterPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);

        reasonArea.setLineWrap(true);
        reasonArea.setWrapStyleWord(true);
        JPanel reasonPanel = new JPanel(new BorderLayout(8, 8));
        reasonPanel.setOpaque(false);
        reasonPanel.add(new JLabel("Reason / Note:"), BorderLayout.NORTH);
        reasonPanel.add(new JScrollPane(reasonArea), BorderLayout.CENTER);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionPanel.setOpaque(false);
        totalReturnLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        JButton returnAllButton = new JButton("Return All Available");
        JButton clearButton = new JButton("Clear Qty");
        JButton submitButton = new JButton("Submit Return");

        actionPanel.add(new JLabel("Refund Method:"));
        actionPanel.add(refundMethodBox);
        actionPanel.add(totalReturnLabel);
        actionPanel.add(returnAllButton);
        actionPanel.add(clearButton);
        actionPanel.add(submitButton);

        returnAllButton.addActionListener(e -> returnAllAvailable());
        clearButton.addActionListener(e -> clearReturnQty());
        submitButton.addActionListener(e -> submitReturn());

        panel.add(reasonPanel, BorderLayout.CENTER);
        panel.add(actionPanel, BorderLayout.SOUTH);
        return panel;
    }

    private void scheduleSaleSearch() {
        if (selectingSearchResult) {
            return;
        }
        saleSearchTimer.restart();
    }

    private void refreshSaleSearchResults() {
        String search = saleSearchField.getText().trim();
        saleSearchModel.setRowCount(0);
        if (search.length() < 2) {
            saleSearchPopup.setVisible(false);
            return;
        }

        String sql = """
                SELECT sale_id,
                       COALESCE(receipt_number, '') AS receipt_number,
                       (created_at AT TIME ZONE ?) AS local_created_at,
                       COALESCE(total_amount, 0) AS total_amount,
                       COALESCE(user_name, '') AS user_name,
                       COALESCE(receipt_device_id, '') AS device_id
                FROM sales
                WHERE CAST(sale_id AS TEXT) = ?
                   OR COALESCE(receipt_number, '') ILIKE ?
                   OR COALESCE(receipt_number, '') ILIKE ?
                ORDER BY
                    CASE
                        WHEN CAST(sale_id AS TEXT) = ? THEN 0
                        WHEN COALESCE(receipt_number, '') ILIKE ? THEN 1
                        ELSE 2
                    END,
                    created_at DESC
                LIMIT 20
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, StoreTimeZoneHelper.getStoreZoneId());
            ps.setString(2, search);
            ps.setString(3, "%" + search + "%");
            ps.setString(4, "%" + search);
            ps.setString(5, search);
            ps.setString(6, "%" + search + "%");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    saleSearchModel.addRow(new Object[]{
                            rs.getInt("sale_id"),
                            rs.getString("receipt_number"),
                            StoreTimeZoneHelper.formatLocalTimestamp(rs.getTimestamp("local_created_at"), DATE_TIME_FORMAT),
                            CURRENCY.format(defaultZero(rs.getBigDecimal("total_amount"))),
                            rs.getString("user_name"),
                            rs.getString("device_id")
                    });
                }
            }

            if (saleSearchModel.getRowCount() == 0) {
                saleSearchPopup.setVisible(false);
                return;
            }
            showSaleSearchPopup();
        } catch (SQLException ex) {
            saleSearchPopup.setVisible(false);
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
        if (row < 0 && saleSearchModel.getRowCount() == 1) {
            row = 0;
        }
        if (row < 0) {
            return;
        }
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
        loadSaleById(saleId);
    }

    private void loadSale() {
        String search = saleSearchField.getText().trim();
        if (search.isBlank()) {
            JOptionPane.showMessageDialog(this, "Enter a sale ID or receipt number.");
            return;
        }

        String saleSql = """
                SELECT sale_id
                FROM sales
                WHERE CAST(sale_id AS TEXT) = ?
                   OR COALESCE(receipt_number, '') ILIKE ?
                   OR COALESCE(receipt_number, '') ILIKE ?
                ORDER BY
                    CASE
                        WHEN CAST(sale_id AS TEXT) = ? THEN 0
                        WHEN COALESCE(receipt_number, '') ILIKE ? THEN 1
                        ELSE 2
                    END,
                    created_at DESC
                LIMIT 20
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement salePs = conn.prepareStatement(saleSql)) {
            salePs.setString(1, search);
            salePs.setString(2, "%" + search + "%");
            salePs.setString(3, "%" + search);
            salePs.setString(4, search);
            salePs.setString(5, "%" + search + "%");

            try (ResultSet rs = salePs.executeQuery()) {
                if (!rs.next()) {
                    JOptionPane.showMessageDialog(this, "Sale was not found.");
                    return;
                }
                int saleId = rs.getInt("sale_id");
                if (rs.next()) {
                    refreshSaleSearchResults();
                    JOptionPane.showMessageDialog(this, "Multiple sales matched. Select the correct sale from the search list.");
                    return;
                }
                loadSaleById(saleId);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load sale: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadSaleById(int saleId) {
        String saleSql = """
                SELECT sale_id,
                       COALESCE(receipt_number, '') AS receipt_number,
                       location_id,
                       customer_id,
                       COALESCE(payment_method, '') AS payment_method,
                       COALESCE(payment_status, 'PAID') AS payment_status,
                       COALESCE(total_amount, 0) AS total_amount,
                       COALESCE(returned_amount, 0) AS returned_amount
                FROM sales
                WHERE sale_id = ?
                """;
        String itemsSql = """
                SELECT si.sale_item_id,
                       si.product_id,
                       COALESCE(p.sku, '') AS sku,
                       COALESCE(p.name, 'Unknown') AS product_name,
                       COALESCE(si.quantity, 0) AS sold_qty,
                       COALESCE(si.unit_price, 0) AS unit_price,
                       COALESCE(SUM(sri.quantity), 0) AS returned_qty
                FROM sale_items si
                LEFT JOIN products p ON p.product_id = si.product_id
                LEFT JOIN sale_return_items sri ON sri.sale_item_id = si.sale_item_id
                WHERE si.sale_id = ?
                GROUP BY si.sale_item_id, si.product_id, p.sku, p.name, si.quantity, si.unit_price
                ORDER BY si.sale_item_id ASC
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement salePs = conn.prepareStatement(saleSql);
             PreparedStatement itemsPs = conn.prepareStatement(itemsSql)) {
            salePs.setInt(1, saleId);
            try (ResultSet rs = salePs.executeQuery()) {
                if (!rs.next()) {
                    JOptionPane.showMessageDialog(this, "Sale was not found.");
                    return;
                }
                loadedSale = new SaleSnapshot(
                        rs.getInt("sale_id"),
                        rs.getString("receipt_number"),
                        rs.getInt("location_id"),
                        nullableInt(rs, "customer_id"),
                        rs.getString("payment_method"),
                        rs.getString("payment_status"),
                        defaultZero(rs.getBigDecimal("total_amount")),
                        defaultZero(rs.getBigDecimal("returned_amount"))
                );
            }

            updatingModel = true;
            itemModel.setRowCount(0);
            itemsPs.setInt(1, loadedSale.saleId());
            try (ResultSet rs = itemsPs.executeQuery()) {
                while (rs.next()) {
                    int soldQty = rs.getInt("sold_qty");
                    int returnedQty = rs.getInt("returned_qty");
                    int availableQty = Math.max(0, soldQty - returnedQty);
                    itemModel.addRow(new Object[]{
                            rs.getInt("sale_item_id"),
                            rs.getInt("product_id"),
                            rs.getString("sku"),
                            rs.getString("product_name"),
                            soldQty,
                            returnedQty,
                            availableQty,
                            defaultZero(rs.getBigDecimal("unit_price")).setScale(2, RoundingMode.HALF_UP),
                            0
                    });
                }
            } finally {
                updatingModel = false;
            }

            refundMethodBox.setSelectedItem(loadedSale.paymentMethod().isBlank() ? "CASH" : loadedSale.paymentMethod());
            saleInfoLabel.setText("Sale #" + loadedSale.saleId()
                    + "  Receipt: " + loadedSale.receiptNumber()
                    + "  Payment: " + loadedSale.paymentMethod()
                    + "  Sale Total: " + CURRENCY.format(loadedSale.totalAmount())
                    + "  Previously Returned: " + CURRENCY.format(loadedSale.returnedAmount()));
            updateReturnTotal();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load sale: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void submitReturn() {
        if (loadedSale == null) {
            JOptionPane.showMessageDialog(this, "Load a sale first.");
            return;
        }
        if (SessionManager.getCurrentUserId() == null) {
            JOptionPane.showMessageDialog(this, "No employee is logged in.");
            return;
        }

        List<ReturnLine> lines;
        try {
            lines = collectReturnLines();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid Return Quantity", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (lines.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter at least one return quantity.");
            return;
        }

        BigDecimal returnTotal = calculateReturnTotal(lines);
        int result = JOptionPane.showConfirmDialog(
                this,
                "Process return for " + CURRENCY.format(returnTotal) + "?",
                "Confirm Return",
                JOptionPane.YES_NO_OPTION
        );
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long returnId = createReturn(conn, lines, returnTotal);
                conn.commit();
                JOptionPane.showMessageDialog(this, "Return processed successfully.\nReturn ID: " + returnId);
                loadSale();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to process return: " + ex.getMessage(), "Return Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private long createReturn(Connection conn, List<ReturnLine> lines, BigDecimal returnTotal) throws Exception {
        String lockSaleSql = """
                SELECT sale_id, location_id, customer_id, payment_method, total_amount, amount_paid, returned_amount
                FROM sales
                WHERE sale_id = ?
                FOR UPDATE
                """;
        try (PreparedStatement ps = conn.prepareStatement(lockSaleSql)) {
            ps.setInt(1, loadedSale.saleId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Sale was not found.");
                }
            }
        }
        validateReturnQuantities(conn, lines);

        String deviceId = ReceiptNumberManager.getDeviceReceiptSettings(loadedSale.locationId()).deviceId();
        String insertReturnSql = """
                INSERT INTO sale_returns (
                    sale_id,
                    location_id,
                    user_id,
                    user_name,
                    refund_method,
                    refund_amount,
                    reason,
                    device_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String insertReturnItemSql = """
                INSERT INTO sale_return_items (return_id, sale_item_id, product_id, quantity, unit_price)
                VALUES (?, ?, ?, ?, ?)
                """;
        String ensureInventorySql = "INSERT INTO inventory (product_id, location_id, quantity_on_hand, reorder_level) VALUES (?, ?, 0, 0) ON CONFLICT (product_id, location_id) DO NOTHING";
        String updateInventorySql = "UPDATE inventory SET quantity_on_hand = quantity_on_hand + ? WHERE product_id = ? AND location_id = ?";
        String movementSql = "INSERT INTO inventory_movements (product_id, location_id, change_qty, reason, note, user_name) VALUES (?, ?, ?, ?, ?, ?)";
        String updateSaleSql = """
                UPDATE sales
                SET returned_amount = COALESCE(returned_amount, 0) + ?,
                    payment_status = CASE
                        WHEN payment_method = 'ACCOUNT'
                         AND COALESCE(amount_paid, 0) >= GREATEST(COALESCE(total_amount, 0) - (COALESCE(returned_amount, 0) + ?), 0)
                        THEN 'PAID'
                        ELSE payment_status
                    END
                WHERE sale_id = ?
                """;

        long returnId;
        try (PreparedStatement returnStmt = conn.prepareStatement(insertReturnSql, Statement.RETURN_GENERATED_KEYS)) {
            returnStmt.setInt(1, loadedSale.saleId());
            returnStmt.setInt(2, loadedSale.locationId());
            returnStmt.setInt(3, SessionManager.getCurrentUserId());
            returnStmt.setString(4, SessionManager.getCurrentUserDisplayName());
            returnStmt.setString(5, String.valueOf(refundMethodBox.getSelectedItem()));
            returnStmt.setBigDecimal(6, returnTotal);
            returnStmt.setString(7, reasonArea.getText().trim());
            returnStmt.setString(8, deviceId);
            returnStmt.executeUpdate();

            try (ResultSet keys = returnStmt.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Failed to create return record.");
                }
                returnId = keys.getLong(1);
            }
        }

        try (PreparedStatement itemStmt = conn.prepareStatement(insertReturnItemSql);
             PreparedStatement ensureInventoryStmt = conn.prepareStatement(ensureInventorySql);
             PreparedStatement updateInventoryStmt = conn.prepareStatement(updateInventorySql);
             PreparedStatement movementStmt = conn.prepareStatement(movementSql);
             PreparedStatement updateSaleStmt = conn.prepareStatement(updateSaleSql)) {
            for (ReturnLine line : lines) {
                itemStmt.setLong(1, returnId);
                itemStmt.setInt(2, line.saleItemId());
                itemStmt.setInt(3, line.productId());
                itemStmt.setInt(4, line.quantity());
                itemStmt.setBigDecimal(5, line.unitPrice());
                itemStmt.addBatch();

                ensureInventoryStmt.setInt(1, line.productId());
                ensureInventoryStmt.setInt(2, loadedSale.locationId());
                ensureInventoryStmt.addBatch();

                updateInventoryStmt.setInt(1, line.quantity());
                updateInventoryStmt.setInt(2, line.productId());
                updateInventoryStmt.setInt(3, loadedSale.locationId());
                updateInventoryStmt.addBatch();

                movementStmt.setInt(1, line.productId());
                movementStmt.setInt(2, loadedSale.locationId());
                movementStmt.setInt(3, line.quantity());
                movementStmt.setString(4, "RETURN");
                movementStmt.setString(5, "return_id=" + returnId + "; sale_id=" + loadedSale.saleId() + "; receipt=" + loadedSale.receiptNumber());
                movementStmt.setString(6, SessionManager.getCurrentUserDisplayName());
                movementStmt.addBatch();
            }

            itemStmt.executeBatch();
            ensureInventoryStmt.executeBatch();
            updateInventoryStmt.executeBatch();
            movementStmt.executeBatch();

            updateSaleStmt.setBigDecimal(1, returnTotal);
            updateSaleStmt.setBigDecimal(2, returnTotal);
            updateSaleStmt.setInt(3, loadedSale.saleId());
            updateSaleStmt.executeUpdate();
        }

        if (loadedSale.customerId() != null && "ACCOUNT".equalsIgnoreCase(loadedSale.paymentMethod())) {
            applyAccountReturn(conn, loadedSale.customerId(), returnTotal, returnId);
        }

        return returnId;
    }

    private void validateReturnQuantities(Connection conn, List<ReturnLine> lines) throws SQLException {
        String lockItemSql = """
                SELECT quantity
                FROM sale_items
                WHERE sale_item_id = ?
                  AND sale_id = ?
                FOR UPDATE
                """;
        String returnedSql = "SELECT COALESCE(SUM(quantity), 0) AS returned_qty FROM sale_return_items WHERE sale_item_id = ?";

        try (PreparedStatement itemPs = conn.prepareStatement(lockItemSql);
             PreparedStatement returnedPs = conn.prepareStatement(returnedSql)) {
            for (ReturnLine line : lines) {
                int soldQty;
                itemPs.setInt(1, line.saleItemId());
                itemPs.setInt(2, loadedSale.saleId());
                try (ResultSet rs = itemPs.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Sale item " + line.saleItemId() + " was not found on this sale.");
                    }
                    soldQty = rs.getInt("quantity");
                }

                returnedPs.setInt(1, line.saleItemId());
                int alreadyReturned;
                try (ResultSet rs = returnedPs.executeQuery()) {
                    rs.next();
                    alreadyReturned = rs.getInt("returned_qty");
                }

                int available = soldQty - alreadyReturned;
                if (line.quantity() > available) {
                    throw new SQLException("Return quantity is higher than available for sale item " + line.saleItemId() + ".");
                }
            }
        }
    }

    private void applyAccountReturn(Connection conn, int customerId, BigDecimal returnTotal, long returnId) throws SQLException {
        BigDecimal currentBalance;
        try (PreparedStatement ps = conn.prepareStatement("SELECT current_balance FROM customer_accounts WHERE customer_id = ? FOR UPDATE")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Customer account was not found.");
                }
                currentBalance = defaultZero(rs.getBigDecimal("current_balance"));
            }
        }

        BigDecimal newBalance = currentBalance.subtract(returnTotal);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            newBalance = BigDecimal.ZERO;
        }

        try (PreparedStatement ps = conn.prepareStatement("UPDATE customer_accounts SET current_balance = ? WHERE customer_id = ?")) {
            ps.setBigDecimal(1, newBalance);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }

        String transactionSql = """
                INSERT INTO customer_account_transactions (customer_id, sale_id, amount, transaction_type, note, user_name)
                VALUES (?, ?, ?, 'RETURN', ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(transactionSql)) {
            ps.setInt(1, customerId);
            ps.setInt(2, loadedSale.saleId());
            ps.setBigDecimal(3, returnTotal.negate());
            ps.setString(4, "Returned items. return_id=" + returnId + "; sale_id=" + loadedSale.saleId());
            ps.setString(5, SessionManager.getCurrentUserDisplayName());
            ps.executeUpdate();
        }
    }

    private List<ReturnLine> collectReturnLines() {
        List<ReturnLine> lines = new ArrayList<>();
        for (int row = 0; row < itemModel.getRowCount(); row++) {
            int qty = parseInt(itemModel.getValueAt(row, 8), 0);
            int available = parseInt(itemModel.getValueAt(row, 6), 0);
            if (qty <= 0) {
                continue;
            }
            if (qty > available) {
                throw new IllegalArgumentException("Return quantity cannot be more than available for " + itemModel.getValueAt(row, 3));
            }
            lines.add(new ReturnLine(
                    parseInt(itemModel.getValueAt(row, 0), 0),
                    parseInt(itemModel.getValueAt(row, 1), 0),
                    qty,
                    parseMoney(itemModel.getValueAt(row, 7))
            ));
        }
        return lines;
    }

    private void normalizeReturnQuantities() {
        updatingModel = true;
        try {
            for (int row = 0; row < itemModel.getRowCount(); row++) {
                int available = parseInt(itemModel.getValueAt(row, 6), 0);
                int qty = parseInt(itemModel.getValueAt(row, 8), 0);
                qty = Math.max(0, Math.min(qty, available));
                itemModel.setValueAt(qty, row, 8);
            }
        } finally {
            updatingModel = false;
        }
    }

    private void updateReturnTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (ReturnLine line : collectReturnLines()) {
            total = total.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        totalReturnLabel.setText("Return Total: " + CURRENCY.format(total));
    }

    private BigDecimal calculateReturnTotal(List<ReturnLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (ReturnLine line : lines) {
            total = total.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private void returnAllAvailable() {
        updatingModel = true;
        try {
            for (int row = 0; row < itemModel.getRowCount(); row++) {
                itemModel.setValueAt(itemModel.getValueAt(row, 6), row, 8);
            }
        } finally {
            updatingModel = false;
        }
        updateReturnTotal();
    }

    private void clearReturnQty() {
        updatingModel = true;
        try {
            for (int row = 0; row < itemModel.getRowCount(); row++) {
                itemModel.setValueAt(0, row, 8);
            }
        } finally {
            updatingModel = false;
        }
        updateReturnTotal();
    }

    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private BigDecimal parseMoney(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        try {
            return new BigDecimal(String.valueOf(value).replace("$", "").replace(",", "").trim());
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private int parseInt(Object value, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record SaleSnapshot(
            int saleId,
            String receiptNumber,
            int locationId,
            Integer customerId,
            String paymentMethod,
            String paymentStatus,
            BigDecimal totalAmount,
            BigDecimal returnedAmount
    ) {
    }

    private record ReturnLine(int saleItemId, int productId, int quantity, BigDecimal unitPrice) {
    }

    private static class TrailingTextRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String text = value == null ? "" : String.valueOf(value);
            label.setHorizontalAlignment(SwingConstants.LEFT);
            label.setToolTipText(text);
            label.setText(clipBeginning(label, text, table.getColumnModel().getColumn(column).getWidth()));
            return label;
        }

        private String clipBeginning(JLabel label, String text, int columnWidth) {
            if (text == null || text.isBlank()) {
                return "";
            }

            Insets insets = label.getInsets();
            int availableWidth = Math.max(0, columnWidth - insets.left - insets.right - 8);
            FontMetrics metrics = label.getFontMetrics(label.getFont());
            if (metrics.stringWidth(text) <= availableWidth) {
                return text;
            }

            String prefix = "...";
            int prefixWidth = metrics.stringWidth(prefix);
            if (prefixWidth >= availableWidth) {
                return prefix;
            }

            int low = 0;
            int high = text.length();
            while (low < high) {
                int mid = (low + high) / 2;
                String candidate = text.substring(mid);
                if (prefixWidth + metrics.stringWidth(candidate) <= availableWidth) {
                    high = mid;
                } else {
                    low = mid + 1;
                }
            }
            return prefix + text.substring(low);
        }
    }
}
