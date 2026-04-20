package ui.screens;

import data.DB;
import managers.SessionManager;
import ui.helpers.ProductImageHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ViewInventoryDetails extends JDialog {
    private static final Color BACKGROUND = new Color(245, 247, 250);
    private static final Color SURFACE = Color.WHITE;
    private static final Color BORDER = new Color(220, 224, 230);
    private static final Color PRIMARY = new Color(36, 99, 235);
    private static final Color TEXT = new Color(32, 41, 57);
    private static final Color MUTED = new Color(101, 116, 139);

    private final int productId;

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
            root.setBackground(BACKGROUND);
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
        header.setBackground(SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(18, 20, 18, 20)
        ));

        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel(itemDetails.get("Name", "Unnamed Item"));
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        nameLabel.setForeground(TEXT);

        JLabel metaLabel = new JLabel("Product #" + productId + "   SKU: " + itemDetails.get("Sku", ""));
        metaLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        metaLabel.setForeground(MUTED);

        titlePanel.add(nameLabel);
        titlePanel.add(Box.createVerticalStrut(6));
        titlePanel.add(metaLabel);

        JPanel metricsPanel = new JPanel(new GridLayout(1, 4, 10, 0));
        metricsPanel.setOpaque(false);
        metricsPanel.add(buildMetricPanel("Stock", itemDetails.get("Quantity On Hand", "0")));
        metricsPanel.add(buildMetricPanel("Reorder", itemDetails.get("Reorder Level", "0")));
        metricsPanel.add(buildMetricPanel("Price", moneyValue(itemDetails.get("Price", ""))));
        metricsPanel.add(buildMetricPanel("Status", getStockStatus(itemDetails)));

        JLabel imagePreview = ProductImageHelper.createImagePreview(itemDetails.get("Image Url", ""), 150, 110);
        header.add(imagePreview, BorderLayout.WEST);
        header.add(titlePanel, BorderLayout.CENTER);
        header.add(metricsPanel, BorderLayout.EAST);
        return header;
    }

    private JPanel buildMetricPanel(String label, String value) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(248, 250, 252));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240)),
                new EmptyBorder(10, 12, 10, 12)
        ));
        panel.setPreferredSize(new Dimension(115, 70));

        JLabel labelText = new JLabel(label);
        labelText.setFont(new Font("SansSerif", Font.PLAIN, 12));
        labelText.setForeground(MUTED);
        labelText.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel valueText = new JLabel(value == null || value.isBlank() ? "-" : value);
        valueText.setFont(new Font("SansSerif", Font.BOLD, 16));
        valueText.setForeground(TEXT);
        valueText.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(labelText);
        panel.add(Box.createVerticalStrut(6));
        panel.add(valueText);
        return panel;
    }

    private JTabbedPane buildContentTabs(ItemDetails itemDetails, JTable movementTable) {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.PLAIN, 14));
        tabs.addTab("Overview", buildOverviewPanel(itemDetails));
        tabs.addTab("Movement History", buildMovementPanel(movementTable));
        return tabs;
    }

    private JScrollPane buildOverviewPanel(ItemDetails itemDetails) {
        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(BACKGROUND);
        content.setBorder(new EmptyBorder(14, 0, 0, 0));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 14, 14);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1;
        gbc.gridy = 0;

        gbc.gridx = 0;
        content.add(buildSection("Product", itemDetails, List.of(
                "Product Id", "Name", "Sku", "Barcode", "Category Id", "Category Name", "Created By Name", "Image Url"
        )), gbc);

        gbc.gridx = 1;
        content.add(buildSection("Pricing", itemDetails, List.of(
                "Cost Price", "Price"
        )), gbc);

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
        gbc.weighty = 1;
        content.add(buildDescriptionSection(itemDetails), gbc);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    private JPanel buildSection(String title, ItemDetails itemDetails, List<String> fields) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(16, 16, 16, 16)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        titleLabel.setForeground(PRIMARY);

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
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(16, 16, 16, 16)
        ));

        JLabel titleLabel = new JLabel("Description");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        titleLabel.setForeground(PRIMARY);

        JTextArea descriptionArea = new JTextArea(itemDetails.get("Description", ""));
        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        descriptionArea.setForeground(TEXT);
        descriptionArea.setBackground(SURFACE);
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
        labelText.setForeground(MUTED);
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
        valueText.setForeground(TEXT);
        panel.add(valueText, valueGbc);
    }

    private JPanel buildMovementPanel(JTable movementTable) {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(BACKGROUND);
        panel.setBorder(new EmptyBorder(14, 0, 0, 0));

        JPanel summary = new JPanel(new BorderLayout());
        summary.setBackground(SURFACE);
        summary.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(12, 14, 12, 14)
        ));

        JLabel title = new JLabel("Inventory Movement History");
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setForeground(TEXT);

        JLabel count = new JLabel("Records: " + movementTable.getRowCount());
        count.setFont(new Font("SansSerif", Font.PLAIN, 13));
        count.setForeground(MUTED);

        summary.add(title, BorderLayout.WEST);
        summary.add(count, BorderLayout.EAST);

        JScrollPane scrollPane = new JScrollPane(movementTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER));

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
                       COALESCE(i.quantity_on_hand, 0) AS quantity_on_hand,
                       COALESCE(i.reorder_level, 0) AS reorder_level
                FROM products p
                LEFT JOIN categories c ON p.category_id = c.category_id
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
                        details.put(formatFieldName(metaData.getColumnLabel(i)), formatValue(rs.getObject(i)));
                    }
                }
            }
        }

        details.put("Additional Barcodes", loadAdditionalBarcodes(conn));
        return details;
    }

    private JTable buildMovementHistoryTable(Connection conn) throws SQLException {
        Set<String> movementColumns = getTableColumns(conn, "inventory_movements");
        String sql = "SELECT * FROM inventory_movements WHERE product_id = ?";

        Integer currentLocationId = getCurrentLocationId();
        if (currentLocationId != null && movementColumns.contains("location_id")) {
            sql += " AND location_id = ?";
        }

        if (movementColumns.contains("created_at")) {
            sql += " ORDER BY created_at DESC";
        } else if (movementColumns.contains("movement_id")) {
            sql += " ORDER BY movement_id DESC";
        }

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            if (currentLocationId != null && movementColumns.contains("location_id")) {
                ps.setInt(2, currentLocationId);
            }

            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                DefaultTableModel movementModel = new DefaultTableModel() {
                    @Override
                    public boolean isCellEditable(int row, int column) {
                        return false;
                    }
                };

                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    movementModel.addColumn(formatFieldName(metaData.getColumnLabel(i)));
                }

                while (rs.next()) {
                    Object[] row = new Object[metaData.getColumnCount()];
                    for (int i = 1; i <= metaData.getColumnCount(); i++) {
                        row[i - 1] = formatValue(rs.getObject(i));
                    }
                    movementModel.addRow(row);
                }

                if (movementModel.getRowCount() == 0) {
                    movementModel.addColumn("Message");
                    movementModel.addRow(new Object[]{"No movement history found for this item."});
                }

                JTable movementTable = new JTable(movementModel);
                movementTable.setRowHeight(30);
                movementTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
                movementTable.setGridColor(new Color(226, 232, 240));
                movementTable.setSelectionBackground(new Color(219, 234, 254));
                movementTable.setSelectionForeground(TEXT);
                movementTable.setDefaultRenderer(Object.class, new MovementTableRenderer());
                JTableHeader header = movementTable.getTableHeader();
                header.setReorderingAllowed(false);
                header.setFont(new Font("SansSerif", Font.BOLD, 13));
                header.setBackground(new Color(241, 245, 249));
                header.setForeground(TEXT);
                movementTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
                for (int i = 0; i < movementTable.getColumnModel().getColumnCount(); i++) {
                    movementTable.getColumnModel().getColumn(i).setPreferredWidth(155);
                }
                return movementTable;
            }
        }
    }

    private static class MovementTableRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                component.setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 250, 252));
                component.setForeground(TEXT);
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
        return value;
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
}
