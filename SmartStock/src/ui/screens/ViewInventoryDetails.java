package ui.screens;

import managers.PermissionManager;
import services.LanApiClient;
import ui.helpers.ProductImageHelper;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        try {
            LanApiClient.InventoryDetails payload = LanApiClient.loadInventoryDetails(productId);
            ItemDetails itemDetails = new ItemDetails();
            if (payload.fields() != null) payload.fields().forEach(itemDetails::put);
            JTable movementTable = buildMovementHistoryTable(payload.activities());

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
                    "LAN Service",
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
        content.add(buildSection("Product", itemDetails, productFields), gbc);

        gbc.gridx = 1;
        List<String> classificationFields = new ArrayList<>(List.of(
                "Item Type", "Item Brand", "Shelf Location", "Storage Shelf Location"
        ));
        if (canViewVendor) {
            classificationFields.add("Vendor Name");
        }
        content.add(buildSection("Item Details", itemDetails, classificationFields), gbc);

        gbc.gridy = 1;
        gbc.gridx = 0;
        List<String> pricingFields = new ArrayList<>();
        if (canViewCostPrice) {
            pricingFields.add("Cost Price");
        }
        pricingFields.add("Price");
        content.add(buildSection("Pricing", itemDetails, pricingFields), gbc);

        gbc.gridx = 1;
        content.add(buildSection("Inventory", itemDetails, List.of(
                "Quantity On Hand", "Reorder Level"
        )), gbc);

        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        content.add(buildSection("Barcodes", itemDetails, List.of(
                "Additional Barcodes"
        )), gbc);

        gbc.gridy = 3;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        content.add(buildSection("Sales Summary", itemDetails, List.of(
                "Total Sold", "Total Sales Amount", "Total Returned", "Total Return Amount"
        )), gbc);

        gbc.gridy = 4;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weighty = 1;
        content.add(buildDescriptionSection(itemDetails), gbc);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(backgroundColor());
        scrollPane.getViewport().setBackground(backgroundColor());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
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
            addDetailRow(panel, row++, displayLabel(field), formatDisplayValue(field, itemDetails.get(field, "")));
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

        JLabel valueText = new JLabel("<html><body style='width:190px'>" + escapeHtml(value == null || value.isBlank() ? "-" : value) + "</body></html>");
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

    private JTable buildMovementHistoryTable(List<LanApiClient.InventoryActivity> activities) {
        DefaultTableModel movementModel = new DefaultTableModel(
                new Object[]{"Date / Time", "Activity", "Qty", "Amount", "Reference", "User", "Note"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        if (activities != null) {
            for (LanApiClient.InventoryActivity row : activities) {
                String time = row.createdAtEpochMillis() <= 0 ? ""
                        : Instant.ofEpochMilli(row.createdAtEpochMillis())
                        .atZone(StoreTimeZoneHelper.getStoreZone()).format(DATE_TIME_FORMAT);
                String amount = row.amount() == null || row.amount().isBlank()
                        ? "" : moneyValue(row.amount());
                movementModel.addRow(new Object[]{time, row.activityType(), row.quantity(), amount,
                        row.reference(), row.userName(), row.note()});
            }
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

    private String formatDisplayValue(String field, String value) {
        if ("Cost Price".equals(field) || "Price".equals(field)) {
            return moneyValue(value);
        }
        if ("Product Type".equals(field)) {
            return formatProductType(value);
        }
        return value;
    }

    private String displayLabel(String field) {
        return switch (field) {
            case "Product Type" -> "Product Classification";
            case "Category Id" -> "Department ID";
            case "Category Name" -> "Department";
            default -> field;
        };
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
            return utils.CurrencyFormatter.format(Double.parseDouble(value));
        } catch (NumberFormatException ex) {
            return value;
        }
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
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
