package ui.screens;

import ui.components.AppMenuBar;
import ui.helpers.ThemeManager;
import ui.helpers.WindowHelper;
import managers.PermissionManager;
import managers.SessionManager;
import data.DB;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;

public class ViewInventory extends JFrame {

    private JTable inventoryTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private JComboBox<String> stockFilterCombo;
    private JLabel totalItemsLabel;
    private JLabel totalProductsLabel;
    private JLabel locationLabel;
    private JButton viewDetailsButton;
    private final boolean canViewCostPrice = PermissionManager.hasPermission("VIEW_COST_PRICE");
    private final boolean canViewVendor = PermissionManager.hasPermission("VIEW_VENDOR");
    private final boolean canViewCreatedBy = PermissionManager.hasPermission("VIEW_CREATED_BY");

    public ViewInventory() {
        setTitle("View Inventory");
        setSize(1100, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        setJMenuBar(AppMenuBar.create(this, "ViewInventory"));


        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        add(mainPanel, BorderLayout.CENTER);

        mainPanel.add(buildHeaderPanel(), BorderLayout.NORTH);
        mainPanel.add(buildTablePanel(), BorderLayout.CENTER);
        mainPanel.add(buildFooterPanel(), BorderLayout.SOUTH);

        loadInventory(null, "All");
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildHeaderPanel() {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setOpaque(false);

        JLabel titleLabel = new JLabel("Inventory Overview");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        locationLabel = new JLabel("Store: " + getCurrentLocationName());
        locationLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        locationLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel filterPanel = new JPanel(new BorderLayout(10, 10));
        filterPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        filterPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftPanel.add(new JLabel("Search:"));
        searchField = new JTextField(22);
        leftPanel.add(searchField);

        JButton searchButton = new JButton("Search");
        leftPanel.add(searchButton);

        JButton refreshButton = new JButton("Refresh");
        leftPanel.add(refreshButton);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightPanel.add(new JLabel("Stock Filter:"));
        stockFilterCombo = new JComboBox<>(new String[]{"All", "In Stock", "Low Stock", "Out of Stock"});
        rightPanel.add(stockFilterCombo);

        filterPanel.add(leftPanel, BorderLayout.WEST);
        filterPanel.add(rightPanel, BorderLayout.EAST);

        searchButton.addActionListener(e -> loadInventory(searchField.getText().trim(), (String) stockFilterCombo.getSelectedItem()));
        refreshButton.addActionListener(e -> {
            searchField.setText("");
            stockFilterCombo.setSelectedIndex(0);
            loadInventory(null, "All");
        });
        stockFilterCombo.addActionListener(e -> loadInventory(searchField.getText().trim(), (String) stockFilterCombo.getSelectedItem()));
        searchField.addActionListener(e -> loadInventory(searchField.getText().trim(), (String) stockFilterCombo.getSelectedItem()));

        wrapper.add(titleLabel);
        wrapper.add(Box.createVerticalStrut(5));
        wrapper.add(locationLabel);
        wrapper.add(filterPanel);

        return wrapper;
    }

    private JScrollPane buildTablePanel() {
        List<String> columns = new ArrayList<>();
        columns.add("Product ID");
        columns.add("SKU");
        columns.add("Name");
        columns.add("Description");
        columns.add("Type");
        columns.add("Category");
        if (canViewVendor) {
            columns.add("Vendor");
        }
        if (canViewCostPrice) {
            columns.add("Cost Price");
        }
        columns.add("Price");
        columns.add("Quantity");
        columns.add("Reorder Level");
        columns.add("Status");
        if (canViewCreatedBy) {
            columns.add("Created By");
        }

        tableModel = new DefaultTableModel(columns.toArray(new String[0]), 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        inventoryTable = new JTable(tableModel);
        inventoryTable.setRowHeight(28);
        inventoryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        inventoryTable.getTableHeader().setReorderingAllowed(false);
        inventoryTable.setFillsViewportHeight(true);
        applyInventoryTableTheme();
        inventoryTable.setDefaultRenderer(Object.class, new InventoryStatusRenderer());
        inventoryTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    showSelectedItemDetails();
                }
            }
        });

        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        setIntegerComparator(sorter, "Product ID");
        setMoneyComparator(sorter, "Cost Price");
        setMoneyComparator(sorter, "Price");
        setIntegerComparator(sorter, "Quantity");
        setIntegerComparator(sorter, "Reorder Level");
        inventoryTable.setRowSorter(sorter);

        TableColumnModel columnModel = inventoryTable.getColumnModel();
        setColumnWidth(columnModel, "Product ID", 80);
        setColumnWidth(columnModel, "SKU", 130);
        setColumnWidth(columnModel, "Name", 180);
        setColumnWidth(columnModel, "Description", 240);
        setColumnWidth(columnModel, "Type", 115);
        setColumnWidth(columnModel, "Category", 120);
        setColumnWidth(columnModel, "Vendor", 160);
        setColumnWidth(columnModel, "Cost Price", 90);
        setColumnWidth(columnModel, "Price", 90);
        setColumnWidth(columnModel, "Quantity", 90);
        setColumnWidth(columnModel, "Reorder Level", 110);
        setColumnWidth(columnModel, "Status", 110);
        setColumnWidth(columnModel, "Created By", 150);

        JScrollPane scrollPane = new JScrollPane(inventoryTable);
        applyInventoryScrollPaneTheme(scrollPane);
        return scrollPane;
    }

    private void applyInventoryTableTheme() {
        boolean dark = ThemeManager.isDarkModeEnabled();
        Color tableBackground = dark ? new Color(30, 30, 30) : Color.WHITE;
        Color tableText = dark ? new Color(235, 235, 235) : Color.BLACK;
        Color grid = dark ? new Color(75, 75, 75) : new Color(180, 180, 180);
        Color selectionBackground = dark ? new Color(48, 72, 120) : new Color(57, 73, 171);
        Color selectionText = Color.WHITE;

        inventoryTable.setBackground(tableBackground);
        inventoryTable.setForeground(tableText);
        inventoryTable.setGridColor(grid);
        inventoryTable.setSelectionBackground(selectionBackground);
        inventoryTable.setSelectionForeground(selectionText);
        inventoryTable.setOpaque(true);

        JTableHeader header = inventoryTable.getTableHeader();
        if (header != null) {
            header.setBackground(dark ? new Color(38, 38, 38) : new Color(241, 245, 249));
            header.setForeground(dark ? new Color(235, 235, 235) : new Color(17, 24, 39));
            header.setOpaque(true);
        }
    }

    private void applyInventoryScrollPaneTheme(JScrollPane scrollPane) {
        boolean dark = ThemeManager.isDarkModeEnabled();
        Color background = dark ? new Color(30, 30, 30) : Color.WHITE;
        scrollPane.setBackground(background);
        scrollPane.getViewport().setBackground(background);
    }

    private void setIntegerComparator(TableRowSorter<DefaultTableModel> sorter, String columnName) {
        int index = tableModel.findColumn(columnName);
        if (index >= 0) {
            sorter.setComparator(index, Comparator.comparingInt(value -> Integer.parseInt(String.valueOf(value))));
        }
    }

    private void setMoneyComparator(TableRowSorter<DefaultTableModel> sorter, String columnName) {
        int index = tableModel.findColumn(columnName);
        if (index >= 0) {
            sorter.setComparator(index, Comparator.comparingDouble(value -> {
                String text = String.valueOf(value).replace("$", "").replace(",", "").trim();
                if (text.isEmpty()) {
                    return 0.0;
                }
                return Double.parseDouble(text);
            }));
        }
    }

    private void setColumnWidth(TableColumnModel columnModel, String columnName, int width) {
        int index = tableModel.findColumn(columnName);
        if (index >= 0) {
            columnModel.getColumn(index).setPreferredWidth(width);
        }
    }

    private JPanel buildFooterPanel() {
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));

        totalProductsLabel = new JLabel("Products: 0");
        totalItemsLabel = new JLabel("Units in Stock: 0");
        viewDetailsButton = new JButton("View Details");
        viewDetailsButton.setEnabled(PermissionManager.hasPermission("VIEW_ITEM_DETAILS"));
        viewDetailsButton.addActionListener(e -> showSelectedItemDetails());

        footerPanel.add(totalProductsLabel);
        footerPanel.add(totalItemsLabel);
        footerPanel.add(viewDetailsButton);

        return footerPanel;
    }

    private class InventoryStatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            int modelRow = table.convertRowIndexToModel(row);
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            int quantityColumn = model.findColumn("Quantity");
            int reorderColumn = model.findColumn("Reorder Level");
            int typeColumn = model.findColumn("Type");
            Object quantityObj = quantityColumn >= 0 ? model.getValueAt(modelRow, quantityColumn) : 0;
            Object reorderObj = reorderColumn >= 0 ? model.getValueAt(modelRow, reorderColumn) : 0;
            String productType = typeColumn >= 0 ? normalizeProductType(String.valueOf(model.getValueAt(modelRow, typeColumn))) : "INVENTORY";

            int quantity = 0;
            int reorderLevel = 0;

            try {
                quantity = Integer.parseInt(String.valueOf(quantityObj));
            } catch (Exception ignored) {
            }

            try {
                reorderLevel = Integer.parseInt(String.valueOf(reorderObj));
            } catch (Exception ignored) {
            }

            if (isSelected) {
                component.setBackground(table.getSelectionBackground());
                component.setForeground(table.getSelectionForeground());
            } else {
                boolean dark = ThemeManager.isDarkModeEnabled();
                Color text = dark ? new Color(235, 235, 235) : Color.BLACK;
                Color normalRow = dark ? new Color(30, 30, 30) : Color.WHITE;
                Color serviceRow = dark ? new Color(36, 36, 36) : Color.WHITE;
                Color negativeRow = dark ? new Color(116, 58, 20) : new Color(255, 229, 204);
                Color outOfStockRow = dark ? new Color(127, 29, 29) : new Color(255, 199, 206);
                Color lowStockRow = dark ? new Color(113, 82, 18) : new Color(255, 242, 204);

                component.setForeground(text);

                if (!isInventoryProduct(productType)) {
                    component.setBackground(serviceRow);
                } else if (quantity < 0) {
                    component.setBackground(negativeRow);
                } else if (quantity == 0) {
                    component.setBackground(outOfStockRow);
                } else if (quantity <= reorderLevel) {
                    component.setBackground(lowStockRow);
                } else {
                    component.setBackground(normalRow);
                }
            }

            return component;
        }
    }

    private void loadInventory(String searchText, String stockFilter) {
        tableModel.setRowCount(0);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT p.product_id, ");
        sql.append("p.sku, ");
        sql.append("p.name, ");
        sql.append("COALESCE(p.description, '') AS description, ");
        sql.append("COALESCE(p.product_type, 'INVENTORY') AS product_type, ");
        sql.append("COALESCE(c.name, '') AS category_name, ");
        sql.append(canViewVendor ? "COALESCE(v.name, '') AS vendor_name, " : "'' AS vendor_name, ");
        sql.append(canViewCostPrice ? "COALESCE(p.cost_price, 0) AS cost_price, " : "0 AS cost_price, ");
        sql.append("COALESCE(p.price, 0) AS price, ");
        sql.append("COALESCE(i.quantity_on_hand, 0) AS quantity_on_hand, ");
        sql.append("COALESCE(i.reorder_level, 0) AS reorder_level, ");
        sql.append(canViewCreatedBy ? "COALESCE(p.created_by_name, '') AS created_by_name " : "'' AS created_by_name ");
        sql.append("FROM products p ");
        sql.append("LEFT JOIN inventory i ON p.product_id = i.product_id ");
        sql.append("LEFT JOIN categories c ON p.category_id = c.category_id ");
        if (canViewVendor) {
            sql.append("LEFT JOIN vendors v ON p.vendor_id = v.vendor_id ");
        }
        sql.append("WHERE 1 = 1");

        Integer currentLocationId = getCurrentLocationId();
        if (currentLocationId != null) {
            sql.append(" AND (i.location_id = ? OR i.location_id IS NULL)");
        }

        boolean hasSearch = searchText != null && !searchText.isBlank();
        if (hasSearch) {
            sql.append(" AND (CAST(p.product_id AS TEXT) ILIKE ? OR p.sku ILIKE ? OR p.name ILIKE ? OR COALESCE(p.description, '') ILIKE ?");
            if (canViewCreatedBy) {
                sql.append(" OR COALESCE(p.created_by_name, '') ILIKE ?");
            }
            if (canViewVendor) {
                sql.append(" OR COALESCE(v.name, '') ILIKE ?");
            }
            sql.append(")");
        }

        if ("In Stock".equals(stockFilter)) {
            sql.append(" AND COALESCE(p.product_type, 'INVENTORY') = 'INVENTORY' AND COALESCE(i.quantity_on_hand, 0) > 0");
        } else if ("Low Stock".equals(stockFilter)) {
            sql.append(" AND COALESCE(p.product_type, 'INVENTORY') = 'INVENTORY' AND COALESCE(i.quantity_on_hand, 0) <= COALESCE(i.reorder_level, 0) AND COALESCE(i.quantity_on_hand, 0) > 0");
        } else if ("Out of Stock".equals(stockFilter)) {
            sql.append(" AND COALESCE(p.product_type, 'INVENTORY') = 'INVENTORY' AND COALESCE(i.quantity_on_hand, 0) <= 0");
        }

        sql.append(" ORDER BY p.name ASC");

        int totalProducts = 0;
        int totalUnits = 0;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (currentLocationId != null) {
                ps.setInt(paramIndex++, currentLocationId);
            }

            if (hasSearch) {
                String pattern = "%" + searchText + "%";
                ps.setString(paramIndex++, pattern);
                ps.setString(paramIndex++, pattern);
                ps.setString(paramIndex++, pattern);
                ps.setString(paramIndex++, pattern);
                if (canViewCreatedBy) {
                    ps.setString(paramIndex++, pattern);
                }
                if (canViewVendor) {
                    ps.setString(paramIndex++, pattern);
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int productId = rs.getInt("product_id");
                    String sku = rs.getString("sku");
                    String name = rs.getString("name");
                    String description = rs.getString("description");
                    String productType = normalizeProductType(rs.getString("product_type"));
                    String category = rs.getString("category_name");
                    String vendor = rs.getString("vendor_name");
                    double costPrice = rs.getDouble("cost_price");
                    double price = rs.getDouble("price");
                    int quantity = rs.getInt("quantity_on_hand");
                    int reorderLevel = rs.getInt("reorder_level");
                    String createdBy = rs.getString("created_by_name");
                    String status = isInventoryProduct(productType) ? getStockStatus(quantity, reorderLevel) : formatProductType(productType);

                    Vector<Object> row = new Vector<>();
                    row.add(productId);
                    row.add(sku);
                    row.add(name);
                    row.add(description);
                    row.add(formatProductType(productType));
                    row.add(category);
                    if (canViewVendor) {
                        row.add(vendor);
                    }
                    if (canViewCostPrice) {
                        row.add(String.format("$%.2f", costPrice));
                    }
                    row.add(String.format("$%.2f", price));
                    row.add(quantity);
                    row.add(reorderLevel);
                    row.add(status);
                    if (canViewCreatedBy) {
                        row.add(createdBy);
                    }
                    tableModel.addRow(row);

                    totalProducts++;
                    if (isInventoryProduct(productType)) {
                        totalUnits += quantity;
                    }
                }
            }

            totalProductsLabel.setText("Products: " + totalProducts);
            totalItemsLabel.setText("Units in Stock: " + totalUnits);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to load inventory.\n" + ex.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private String getStockStatus(int quantity, int reorderLevel) {
        if (quantity <= 0) {
            return "Out of Stock";
        }
        if (quantity <= reorderLevel) {
            return "Low Stock";
        }
        return "In Stock";
    }

    private String normalizeProductType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase().replace(' ', '_');
        if ("SERVICE".equals(normalized) || "NON_INVENTORY".equals(normalized)) {
            return normalized;
        }
        return "INVENTORY";
    }

    private boolean isInventoryProduct(String productType) {
        return "INVENTORY".equals(normalizeProductType(productType));
    }

    private String formatProductType(String productType) {
        return switch (normalizeProductType(productType)) {
            case "SERVICE" -> "Service";
            case "NON_INVENTORY" -> "Non Inventory";
            default -> "Inventory";
        };
    }

    private void showSelectedItemDetails() {
        if (!PermissionManager.requirePermission("VIEW_ITEM_DETAILS", this, "View Item Details")) {
            if (viewDetailsButton != null) {
                viewDetailsButton.setEnabled(false);
            }
            return;
        }

        int selectedRow = inventoryTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select an item first.");
            return;
        }

        int modelRow = inventoryTable.convertRowIndexToModel(selectedRow);
        int productId = Integer.parseInt(String.valueOf(tableModel.getValueAt(modelRow, 0)));

        new ViewInventoryDetails(this, productId).setVisible(true);
    }

    private Integer getCurrentLocationId() {
        try {
            return SessionManager.getCurrentLocationId();
        } catch (Exception e) {
            return null;
        }
    }

    private String getCurrentLocationName() {
        try {
            if (SessionManager.getCurrentLocationName() != null && !SessionManager.getCurrentLocationName().isBlank()) {
                return SessionManager.getCurrentLocationName();
            }
        } catch (Exception ignored) {
        }
        return "All Locations";
    }
}
