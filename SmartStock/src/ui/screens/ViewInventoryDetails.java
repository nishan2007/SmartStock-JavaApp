package ui.screens;

import data.DB;
import managers.PermissionManager;
import managers.SessionManager;
import ui.helpers.ProductImageHelper;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ViewInventoryDetails extends JDialog {
    private static final Color LIGHT_BACKGROUND = new Color(245, 247, 250);
    private static final Color LIGHT_SURFACE = Color.WHITE;
    private static final Color LIGHT_CARD = new Color(248, 250, 252);
    private static final Color LIGHT_BORDER = new Color(220, 224, 230);
    private static final Color LIGHT_PRIMARY = new Color(36, 99, 235);
    private static final Color LIGHT_TEXT = new Color(32, 41, 57);
    private static final Color LIGHT_MUTED = new Color(101, 116, 139);
    private static final Color DARK_BACKGROUND = new Color(18, 18, 18);
    private static final Color DARK_SURFACE = new Color(30, 30, 30);
    private static final Color DARK_CARD = new Color(42, 42, 42);
    private static final Color DARK_BORDER = new Color(75, 75, 75);
    private static final Color DARK_PRIMARY = new Color(96, 165, 250);
    private static final Color DARK_TEXT = new Color(235, 235, 235);
    private static final Color DARK_MUTED = new Color(180, 180, 180);
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");

    private final int productId;
    private final boolean canViewCostPrice = PermissionManager.hasPermission("VIEW_COST_PRICE");
    private final boolean canViewVendor = PermissionManager.hasPermission("VIEW_VENDOR");
    private final boolean canViewCreatedBy = PermissionManager.hasPermission("VIEW_CREATED_BY");

    private static boolean darkMode() {
        return ThemeManager.isDarkModeEnabled();
    }

    private static Color backgroundColor() {
        return darkMode() ? DARK_BACKGROUND : LIGHT_BACKGROUND;
    }

    private static Color surfaceColor() {
        return darkMode() ? DARK_SURFACE : LIGHT_SURFACE;
    }

    private static Color cardColor() {
        return darkMode() ? DARK_CARD : LIGHT_CARD;
    }

    private static Color borderColor() {
        return darkMode() ? DARK_BORDER : LIGHT_BORDER;
    }

    private static Color primaryColor() {
        return darkMode() ? DARK_PRIMARY : LIGHT_PRIMARY;
    }

    private static Color textColor() {
        return darkMode() ? DARK_TEXT : LIGHT_TEXT;
    }

    private static Color mutedColor() {
        return darkMode() ? DARK_MUTED : LIGHT_MUTED;
    }

    public ViewInventoryDetails(Window owner, int productId) {
        super(owner, "Item Details - Product #" + productId, ModalityType.APPLICATION_MODAL);
        this.productId = productId;

        setSize(1000, 700);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        try (Connection conn = DB.getConnection()) {
            ItemDetails itemDetails = loadItemDetails(conn);
            JTable movementTable = buildMovementHistoryTable(conn);

            JPanel root = new JPanel(new BorderLayout(0, 16));
            root.setBackground(backgroundColor());
            root.setBorder(new EmptyBorder(18, 18, 18, 18));
            root.add(buildHeaderPanel(itemDetails), BorderLayout.NORTH);
            root.add(buildContentTabs(itemDetails, movementTable), BorderLayout.CENTER);

            add(root, BorderLayout.CENTER);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    owner,
                    "Failed to load item details.\n" + ex.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
            dispose();
        }
    }

    private JPanel buildHeaderPanel(ItemDetails itemDetails) {
        JPanel header = new JPanel(new BorderLayout(18, 12));
        header.setBackground(surfaceColor());
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor()),
                new EmptyBorder(18, 20, 18, 20)
        ));

        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel(itemDetails.get("Name", "Unnamed Item"));
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        nameLabel.setForeground(textColor());

        JLabel metaLabel = new JLabel("Product #" + productId + "   SKU: " + itemDetails.get("Sku", ""));
        metaLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        metaLabel.setForeground(mutedColor());

        titlePanel.add(nameLabel);
        titlePanel.add(Box.createVerticalStrut(6));
        titlePanel.add(metaLabel);

        boolean inventoryItem = isInventoryProduct(itemDetails.get("Product Type", "INVENTORY"));
        JPanel metricsPanel = new JPanel(new GridLayout(inventoryItem ? 2 : 1, 3, 10, 10));
        metricsPanel.setOpaque(false);
        if (inventoryItem) {
            metricsPanel.add(buildMetricPanel("Stock", itemDetails.get("Quantity On Hand", "0")));
            metricsPanel.add(buildMetricPanel("Reorder", itemDetails.get("Reorder Level", "0")));
        }
        metricsPanel.add(buildMetricPanel("Price", moneyValue(itemDetails.get("Price", ""))));
        if (inventoryItem) {
            metricsPanel.add(buildMetricPanel("Status", getStockStatus(itemDetails)));
        }
        metricsPanel.add(buildMetricPanel("Sold", itemDetails.get("Total Sold", "0")));
        metricsPanel.add(buildMetricPanel("Sales", moneyValue(itemDetails.get("Total Sales Amount", "0"))));

        JLabel imagePreview = ProductImageHelper.createImagePreview(itemDetails.get("Image Url", ""), 150, 110);
        header.add(imagePreview, BorderLayout.WEST);
        header.add(titlePanel, BorderLayout.CENTER);
        header.add(metricsPanel, BorderLayout.EAST);
        return header;
    }

    private JPanel buildMetricPanel(String label, String value) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(cardColor());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor()),
                new EmptyBorder(10, 12, 10, 12)
        ));
        panel.setPreferredSize(new Dimension(115, 70));

        JLabel labelText = new JLabel(label);
        labelText.setFont(new Font("SansSerif", Font.PLAIN, 12));
        labelText.setForeground(mutedColor());
        labelText.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel valueText = new JLabel(value == null || value.isBlank() ? "-" : value);
        valueText.setFont(new Font("SansSerif", Font.BOLD, 16));
        valueText.setForeground(textColor());
        valueText.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(labelText);
        panel.add(Box.createVerticalStrut(6));
        panel.add(valueText);
        return panel;
    }

    private JTabbedPane buildContentTabs(ItemDetails itemDetails, JTable movementTable) {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.PLAIN, 14));
        tabs.setBackground(surfaceColor());
        tabs.setForeground(textColor());
        tabs.addTab("Overview", buildOverviewPanel(itemDetails));
        tabs.addTab("Movement History", buildMovementPanel(movementTable));
        return tabs;
    }

    private JScrollPane buildOverviewPanel(ItemDetails itemDetails) {
        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(backgroundColor());
        content.setBorder(new EmptyBorder(14, 0, 0, 0));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 14, 14);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1;
        gbc.gridy = 0;

        gbc.gridx = 0;
        List<String> productFields = new ArrayList<>(List.of(
                "Product Id", "Name", "Product Type", "Sku", "Barcode", "Category Id", "Category Name", "Image Url"
        ));
        if (canViewCreatedBy) {
            productFields.add("Created By Name");
        }
        if (canViewVendor) {
            productFields.add("Vendor Name");
        }
        content.add(buildSection("Product", itemDetails, productFields), gbc);

        gbc.gridx = 1;
        List<String> pricingFields = new ArrayList<>();
        if (canViewCostPrice) {
            pricingFields.add("Cost Price");
        }
        pricingFields.add("Price");
        content.add(buildSection("Pricing", itemDetails, pricingFields), gbc);

        gbc.gridy = 1;
        gbc.gridx = 0;
        content.add(buildSection("Inventory", itemDetails, List.of(
                "Quantity On Hand", "Reorder Level"
        )), gbc);

        gbc.gridx = 1;
        content.add(buildSection("Barcodes", itemDetails, List.of(
                "Additional Barcodes"
        )), gbc);

        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        content.add(buildSection("Sales Summary", itemDetails, List.of(
                "Total Sold", "Total Sales Amount", "Total Returned", "Total Return Amount"
        )), gbc);

        gbc.gridy = 3;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weighty = 1;
        content.add(buildDescriptionSection(itemDetails), gbc);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(backgroundColor());
        scrollPane.getViewport().setBackground(backgroundColor());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    private JPanel buildSection(String title, ItemDetails itemDetails, List<String> fields) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(surfaceColor());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor()),
                new EmptyBorder(16, 16, 16, 16)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        titleLabel.setForeground(primaryColor());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 12, 0);
        panel.add(titleLabel, gbc);

        int row = 1;
        for (String field : fields) {
            addDetailRow(panel, row++, field, formatDisplayValue(field, itemDetails.get(field, "")));
        }

        gbc.gridy = row;
        gbc.weighty = 1;
        panel.add(Box.createVerticalGlue(), gbc);
        return panel;
    }

    private JPanel buildDescriptionSection(ItemDetails itemDetails) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(surfaceColor());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor()),
                new EmptyBorder(16, 16, 16, 16)
        ));

        JLabel titleLabel = new JLabel("Description");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        titleLabel.setForeground(primaryColor());

        JTextArea descriptionArea = new JTextArea(itemDetails.get("Description", ""));
        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        descriptionArea.setForeground(textColor());
        descriptionArea.setBackground(surfaceColor());
        descriptionArea.setBorder(BorderFactory.createEmptyBorder());

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(descriptionArea, BorderLayout.CENTER);
        return panel;
    }

    private void addDetailRow(JPanel panel, int row, String label, String value) {
        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.gridx = 0;
        labelGbc.gridy = row;
        labelGbc.anchor = GridBagConstraints.NORTHWEST;
        labelGbc.insets = new Insets(0, 0, 10, 14);

        JLabel labelText = new JLabel(label);
        labelText.setFont(new Font("SansSerif", Font.PLAIN, 13));
        labelText.setForeground(mutedColor());
        panel.add(labelText, labelGbc);

        GridBagConstraints valueGbc = new GridBagConstraints();
        valueGbc.gridx = 1;
        valueGbc.gridy = row;
        valueGbc.weightx = 1;
        valueGbc.fill = GridBagConstraints.HORIZONTAL;
        valueGbc.anchor = GridBagConstraints.NORTHWEST;
        valueGbc.insets = new Insets(0, 0, 10, 0);

        JLabel valueText = new JLabel("<html><body style='width:260px'>" + escapeHtml(value == null || value.isBlank() ? "-" : value) + "</body></html>");
        valueText.setFont(new Font("SansSerif", Font.BOLD, 13));
        valueText.setForeground(textColor());
        panel.add(valueText, valueGbc);
    }

    private JPanel buildMovementPanel(JTable movementTable) {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(backgroundColor());
        panel.setBorder(new EmptyBorder(14, 0, 0, 0));

        JPanel summary = new JPanel(new BorderLayout());
        summary.setBackground(surfaceColor());
        summary.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor()),
                new EmptyBorder(12, 14, 12, 14)
        ));

        JLabel title = new JLabel("Item Activity History");
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setForeground(textColor());

        JLabel count = new JLabel("Records: " + movementTable.getRowCount());
        count.setFont(new Font("SansSerif", Font.PLAIN, 13));
        count.setForeground(mutedColor());

        summary.add(title, BorderLayout.WEST);
        summary.add(count, BorderLayout.EAST);

        JScrollPane scrollPane = new JScrollPane(movementTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(borderColor()));
        scrollPane.setBackground(surfaceColor());
        scrollPane.getViewport().setBackground(surfaceColor());

        panel.add(summary, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private ItemDetails loadItemDetails(Connection conn) throws SQLException {
        ItemDetails details = new ItemDetails();

        Integer currentLocationId = getCurrentLocationId();
        String sql = """
                SELECT p.*,
                       COALESCE(c.name, '') AS category_name,
                       COALESCE(v.name, '') AS vendor_name,
                       COALESCE(i.quantity_on_hand, 0) AS quantity_on_hand,
                       COALESCE(i.reorder_level, 0) AS reorder_level
                FROM products p
                LEFT JOIN categories c ON p.category_id = c.category_id
                LEFT JOIN vendors v ON p.vendor_id = v.vendor_id
                LEFT JOIN inventory i ON p.product_id = i.product_id
                """;
        if (currentLocationId != null) {
            sql += " AND i.location_id = ?";
        }
        sql += """

                WHERE p.product_id = ?
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (currentLocationId != null) {
                ps.setInt(1, currentLocationId);
                ps.setInt(2, productId);
            } else {
                ps.setInt(1, productId);
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    for (int i = 1; i <= metaData.getColumnCount(); i++) {
                        String fieldName = formatFieldName(metaData.getColumnLabel(i));
                        if (isRestrictedField(fieldName)) {
                            continue;
                        }
                        details.put(fieldName, formatValue(rs.getObject(i)));
                    }
                }
            }
        }

        details.put("Additional Barcodes", loadAdditionalBarcodes(conn));
        loadSalesSummary(conn, details);
        return details;
    }

    private void loadSalesSummary(Connection conn, ItemDetails details) throws SQLException {
        String salesSql = """
                SELECT COALESCE(SUM(si.quantity), 0) AS total_sold,
                       COALESCE(SUM(si.quantity * si.unit_price), 0) AS total_sales_amount
                FROM sale_items si
                JOIN sales s ON s.sale_id = si.sale_id
                WHERE si.product_id = ?
                """;
        String returnsSql = """
                SELECT COALESCE(SUM(sri.quantity), 0) AS total_returned,
                       COALESCE(SUM(sri.quantity * sri.unit_price), 0) AS total_return_amount
                FROM sale_return_items sri
                JOIN sale_returns sr ON sr.return_id = sri.return_id
                WHERE sri.product_id = ?
                """;

        Integer currentLocationId = getCurrentLocationId();
        if (currentLocationId != null) {
            salesSql += " AND s.location_id = ?";
            returnsSql += " AND sr.location_id = ?";
        }

        int totalSold = 0;
        BigDecimal totalSalesAmount = BigDecimal.ZERO;
        try (PreparedStatement ps = conn.prepareStatement(salesSql)) {
            ps.setInt(1, productId);
            if (currentLocationId != null) {
                ps.setInt(2, currentLocationId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    totalSold = rs.getInt("total_sold");
                    totalSalesAmount = defaultZero(rs.getBigDecimal("total_sales_amount"));
                }
            }
        }

        int totalReturned = 0;
        BigDecimal totalReturnAmount = BigDecimal.ZERO;
        if (!getTableColumns(conn, "sale_return_items").isEmpty() && !getTableColumns(conn, "sale_returns").isEmpty()) {
            try (PreparedStatement ps = conn.prepareStatement(returnsSql)) {
                ps.setInt(1, productId);
                if (currentLocationId != null) {
                    ps.setInt(2, currentLocationId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        totalReturned = rs.getInt("total_returned");
                        totalReturnAmount = defaultZero(rs.getBigDecimal("total_return_amount"));
                    }
                }
            }
        }

        details.put("Total Sold", String.valueOf(Math.max(0, totalSold - totalReturned)));
        details.put("Total Sales Amount", totalSalesAmount.subtract(totalReturnAmount).max(BigDecimal.ZERO).toPlainString());
        details.put("Total Returned", String.valueOf(totalReturned));
        details.put("Total Return Amount", totalReturnAmount.toPlainString());
    }

    private boolean isRestrictedField(String fieldName) {
        if (!canViewCostPrice && "Cost Price".equals(fieldName)) {
            return true;
        }
        if (!canViewVendor && ("Vendor Id".equals(fieldName) || "Vendor Name".equals(fieldName))) {
            return true;
        }
        return !canViewCreatedBy && ("Created By User Id".equals(fieldName) || "Created By Name".equals(fieldName));
    }

    private JTable buildMovementHistoryTable(Connection conn) throws SQLException {
        List<ActivityRow> rows = new ArrayList<>();
        loadInventoryMovementRows(conn, rows);
        loadNonInventorySaleRows(conn, rows);
        loadNonInventoryReturnRows(conn, rows);

        rows.sort((left, right) -> {
            Timestamp leftTime = left.sortTime();
            Timestamp rightTime = right.sortTime();
            if (leftTime == null && rightTime == null) {
                return 0;
            }
            if (leftTime == null) {
                return 1;
            }
            if (rightTime == null) {
                return -1;
            }
            return rightTime.compareTo(leftTime);
        });

        DefaultTableModel movementModel = new DefaultTableModel(
                new Object[]{"Date / Time", "Activity", "Qty", "Amount", "Reference", "User", "Note"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        for (ActivityRow row : rows) {
            movementModel.addRow(new Object[]{
                    formatValue(row.displayTime()),
                    row.activityType(),
                    row.quantity(),
                    row.amount(),
                    row.reference(),
                    row.userName(),
                    row.note()
            });
        }

        if (movementModel.getRowCount() == 0) {
            movementModel.addRow(new Object[]{"", "No activity history found for this item.", "", "", "", "", ""});
        }

        JTable movementTable = new JTable(movementModel);
        movementTable.setRowHeight(30);
        movementTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        movementTable.setBackground(surfaceColor());
        movementTable.setForeground(textColor());
        movementTable.setGridColor(borderColor());
        movementTable.setSelectionBackground(darkMode() ? new Color(48, 72, 120) : new Color(219, 234, 254));
        movementTable.setSelectionForeground(darkMode() ? Color.WHITE : textColor());
        movementTable.setDefaultRenderer(Object.class, new MovementTableRenderer());
        JTableHeader header = movementTable.getTableHeader();
        header.setReorderingAllowed(false);
        header.setFont(new Font("SansSerif", Font.BOLD, 13));
        header.setBackground(darkMode() ? new Color(38, 38, 38) : new Color(241, 245, 249));
        header.setForeground(textColor());
        header.setOpaque(true);
        movementTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {150, 130, 70, 95, 220, 150, 320};
        for (int i = 0; i < movementTable.getColumnModel().getColumnCount(); i++) {
            movementTable.getColumnModel().getColumn(i).setPreferredWidth(widths[Math.min(i, widths.length - 1)]);
        }
        return movementTable;
    }

    private void loadInventoryMovementRows(Connection conn, List<ActivityRow> rows) throws SQLException {
        Set<String> movementColumns = getTableColumns(conn, "inventory_movements");
        if (movementColumns.isEmpty()) {
            return;
        }

        String createdAtSelect = movementColumns.contains("created_at")
                ? "created_at, (created_at AT TIME ZONE ?) AS local_created_at"
                : "NULL::timestamptz AS created_at, NULL::timestamp AS local_created_at";
        String movementIdSelect = movementColumns.contains("movement_id")
                ? "CAST(movement_id AS TEXT) AS reference"
                : "'' AS reference";
        String reasonSelect = movementColumns.contains("reason") ? "COALESCE(reason, 'INVENTORY') AS reason" : "'INVENTORY' AS reason";
        String noteSelect = movementColumns.contains("note") ? "COALESCE(note, '') AS note" : "'' AS note";
        String userSelect = movementColumns.contains("user_name") ? "COALESCE(user_name, '') AS user_name" : "'' AS user_name";

        String sql = "SELECT " + createdAtSelect + ", " + movementIdSelect + ", " + reasonSelect + ", "
                + noteSelect + ", " + userSelect + ", COALESCE(change_qty, 0) AS change_qty "
                + "FROM inventory_movements WHERE product_id = ?";
        Integer currentLocationId = getCurrentLocationId();
        if (currentLocationId != null && movementColumns.contains("location_id")) {
            sql += " AND location_id = ?";
        }

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            if (movementColumns.contains("created_at")) {
                ps.setString(paramIndex++, StoreTimeZoneHelper.getStoreZoneId());
            }
            ps.setInt(paramIndex++, productId);
            if (currentLocationId != null && movementColumns.contains("location_id")) {
                ps.setInt(paramIndex, currentLocationId);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ActivityRow(
                            rs.getTimestamp("created_at"),
                            rs.getTimestamp("local_created_at"),
                            rs.getString("reason"),
                            rs.getInt("change_qty"),
                            "",
                            rs.getString("reference"),
                            rs.getString("user_name"),
                            rs.getString("note")
                    ));
                }
            }
        }
    }

    private void loadNonInventorySaleRows(Connection conn, List<ActivityRow> rows) throws SQLException {
        String sql = """
                SELECT s.created_at,
                       (s.created_at AT TIME ZONE ?) AS local_created_at,
                       COALESCE(si.quantity, 0) AS quantity,
                       COALESCE(si.unit_price, 0) * COALESCE(si.quantity, 0) AS amount,
                       COALESCE(s.receipt_number, 'Sale #' || s.sale_id) AS reference,
                       COALESCE(s.user_name, '') AS user_name
                FROM sale_items si
                JOIN sales s ON s.sale_id = si.sale_id
                WHERE si.product_id = ?
                  AND COALESCE(si.product_type, 'INVENTORY') <> 'INVENTORY'
                """;
        Integer currentLocationId = getCurrentLocationId();
        if (currentLocationId != null) {
            sql += " AND s.location_id = ?";
        }

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, StoreTimeZoneHelper.getStoreZoneId());
            ps.setInt(2, productId);
            if (currentLocationId != null) {
                ps.setInt(3, currentLocationId);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ActivityRow(
                            rs.getTimestamp("created_at"),
                            rs.getTimestamp("local_created_at"),
                            "SALE",
                            -rs.getInt("quantity"),
                            moneyValue(defaultZero(rs.getBigDecimal("amount")).toPlainString()),
                            rs.getString("reference"),
                            rs.getString("user_name"),
                            "Sold without inventory quantity change"
                    ));
                }
            }
        }
    }

    private void loadNonInventoryReturnRows(Connection conn, List<ActivityRow> rows) throws SQLException {
        if (getTableColumns(conn, "sale_returns").isEmpty() || getTableColumns(conn, "sale_return_items").isEmpty()) {
            return;
        }

        String sql = """
                SELECT sr.created_at,
                       (sr.created_at AT TIME ZONE ?) AS local_created_at,
                       COALESCE(sri.quantity, 0) AS quantity,
                       COALESCE(sri.unit_price, 0) * COALESCE(sri.quantity, 0) AS amount,
                       'Return #' || sr.return_id AS reference,
                       COALESCE(sr.user_name, '') AS user_name,
                       COALESCE(s.receipt_number, '') AS receipt_number
                FROM sale_return_items sri
                JOIN sale_returns sr ON sr.return_id = sri.return_id
                JOIN sale_items si ON si.sale_item_id = sri.sale_item_id
                LEFT JOIN sales s ON s.sale_id = sr.sale_id
                WHERE sri.product_id = ?
                  AND COALESCE(si.product_type, 'INVENTORY') <> 'INVENTORY'
                """;
        Integer currentLocationId = getCurrentLocationId();
        if (currentLocationId != null) {
            sql += " AND sr.location_id = ?";
        }

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, StoreTimeZoneHelper.getStoreZoneId());
            ps.setInt(2, productId);
            if (currentLocationId != null) {
                ps.setInt(3, currentLocationId);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ActivityRow(
                            rs.getTimestamp("created_at"),
                            rs.getTimestamp("local_created_at"),
                            "RETURN",
                            rs.getInt("quantity"),
                            moneyValue(defaultZero(rs.getBigDecimal("amount")).toPlainString()),
                            rs.getString("reference"),
                            rs.getString("user_name"),
                            "Returned from receipt " + rs.getString("receipt_number")
                    ));
                }
            }
        }
    }

    private static class MovementTableRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                if (darkMode()) {
                    component.setBackground(row % 2 == 0 ? DARK_SURFACE : DARK_CARD);
                    component.setForeground(DARK_TEXT);
                } else {
                    component.setBackground(row % 2 == 0 ? LIGHT_SURFACE : LIGHT_CARD);
                    component.setForeground(LIGHT_TEXT);
                }
            }
            setBorder(new EmptyBorder(0, 8, 0, 8));
            return component;
        }
    }

    private String loadAdditionalBarcodes(Connection conn) throws SQLException {
        String sql = "SELECT barcode FROM product_barcodes WHERE product_id = ? ORDER BY barcode";
        List<String> barcodes = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String barcode = rs.getString("barcode");
                    if (barcode != null && !barcode.isBlank()) {
                        barcodes.add(barcode);
                    }
                }
            }
        }

        return String.join(", ", barcodes);
    }

    private Set<String> getTableColumns(Connection conn, String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        String sql = """
                SELECT LOWER(column_name) AS column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString("column_name"));
                }
            }
        }

        return columns;
    }

    private String getStockStatus(ItemDetails details) {
        int quantity = parseInt(details.get("Quantity On Hand", "0"));
        int reorderLevel = parseInt(details.get("Reorder Level", "0"));
        if (quantity <= 0) {
            return "Out";
        }
        if (quantity <= reorderLevel) {
            return "Low";
        }
        return "Good";
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return 0;
        }
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String formatFieldName(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return "";
        }

        String[] words = fieldName.replace("_", " ").split("\\s+");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!formatted.isEmpty()) {
                formatted.append(" ");
            }
            formatted.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                formatted.append(word.substring(1).toLowerCase());
            }
        }
        return formatted.toString();
    }

    private String formatDisplayValue(String field, String value) {
        if ("Cost Price".equals(field) || "Price".equals(field)) {
            return moneyValue(value);
        }
        if ("Product Type".equals(field)) {
            return formatProductType(value);
        }
        return value;
    }

    private String formatProductType(String productType) {
        return switch (normalizeProductType(productType)) {
            case "SERVICE" -> "Service";
            case "NON_INVENTORY" -> "Non Inventory";
            default -> "Inventory";
        };
    }

    private String normalizeProductType(String productType) {
        String normalized = productType == null ? "" : productType.trim().toUpperCase().replace(' ', '_');
        if ("SERVICE".equals(normalized) || "NON_INVENTORY".equals(normalized)) {
            return normalized;
        }
        return "INVENTORY";
    }

    private boolean isInventoryProduct(String productType) {
        return "INVENTORY".equals(normalizeProductType(productType));
    }

    private String moneyValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return String.format("$%.2f", Double.parseDouble(value));
        } catch (NumberFormatException ex) {
            return value;
        }
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Timestamp timestamp) {
            return StoreTimeZoneHelper.formatLocalTimestamp(timestamp, DATE_TIME_FORMAT);
        }
        return String.valueOf(value);
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private Integer getCurrentLocationId() {
        try {
            return SessionManager.getCurrentLocationId();
        } catch (Exception e) {
            return null;
        }
    }

    private static class ItemDetails {
        private final Map<String, String> values = new LinkedHashMap<>();

        void put(String key, String value) {
            values.put(key, value == null ? "" : value);
        }

        String get(String key, String fallback) {
            return values.getOrDefault(key, fallback);
        }
    }

    private record ActivityRow(
            Timestamp sortTime,
            Timestamp displayTime,
            String activityType,
            int quantity,
            String amount,
            String reference,
            String userName,
            String note
    ) {
    }
}
