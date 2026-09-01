package ui.screens;

import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.ThemeManager;
import ui.helpers.UiDebouncer;
import ui.helpers.UiTaskRunner;
import ui.helpers.WindowHelper;
import managers.PermissionManager;
import managers.SessionManager;
import services.LanApiClient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;

public class ViewInventory extends JFrame {

    private JTable inventoryTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private JComboBox<String> stockFilterCombo;
    private JComboBox<String> departmentFilterCombo;
    private JComboBox<String> productTypeFilterCombo;
    private JComboBox<String> itemTypeFilterCombo;
    private JComboBox<String> brandFilterCombo;
    private JComboBox<String> shelfFilterCombo;
    private JComboBox<String> storageShelfFilterCombo;
    private boolean loadingDetailFilters;
    private JLabel totalItemsLabel;
    private JLabel totalProductsLabel;
    private JLabel locationLabel;
    private JButton viewDetailsButton;
    private JButton reviewPricesButton;
    private final LoadingStatePanel loadingState = new LoadingStatePanel();
    private LanApiClient.InventoryLookups allInventoryLookups;
    private final boolean canViewCostPrice = PermissionManager.hasPermission("VIEW_COST_PRICE");
    private final boolean canViewVendor = PermissionManager.hasPermission("VIEW_VENDOR");
    private final boolean canViewCreatedBy = PermissionManager.hasPermission("VIEW_CREATED_BY");

    public ViewInventory() {
        setTitle("View Inventory");
        setSize(1450, 720);
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

        loadDetailFilters();
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

        departmentFilterCombo = new JComboBox<>(new String[]{"All Departments"});
        productTypeFilterCombo = new JComboBox<>(new String[]{"All Product Types", "Inventory Only", "Service Only",
                "Non Inventory Only", "Hide Inventory", "Hide Services", "Hide Non Inventory"});
        itemTypeFilterCombo = new JComboBox<>(new String[]{"All Item Types"});
        brandFilterCombo = new JComboBox<>(new String[]{"All Brands"});
        shelfFilterCombo = new JComboBox<>(new String[]{"All Shelves"});
        storageShelfFilterCombo = new JComboBox<>(new String[]{"All Storage Shelves"});

        JPanel detailFilterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        detailFilterPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailFilterPanel.add(new JLabel("Department:"));
        detailFilterPanel.add(departmentFilterCombo);
        detailFilterPanel.add(new JLabel("Product Type:"));
        detailFilterPanel.add(productTypeFilterCombo);
        detailFilterPanel.add(new JLabel("Item Type:"));
        detailFilterPanel.add(itemTypeFilterCombo);
        detailFilterPanel.add(new JLabel("Brand:"));
        detailFilterPanel.add(brandFilterCombo);
        detailFilterPanel.add(new JLabel("Shelf:"));
        detailFilterPanel.add(shelfFilterCombo);
        detailFilterPanel.add(new JLabel("Storage Shelf:"));
        detailFilterPanel.add(storageShelfFilterCombo);

        filterPanel.add(leftPanel, BorderLayout.WEST);
        filterPanel.add(rightPanel, BorderLayout.EAST);

        searchButton.addActionListener(e -> loadInventory(searchField.getText().trim(), (String) stockFilterCombo.getSelectedItem()));
        refreshButton.addActionListener(e -> {
            searchField.setText("");
            stockFilterCombo.setSelectedIndex(0);
            departmentFilterCombo.setSelectedIndex(0);
            productTypeFilterCombo.setSelectedIndex(0);
            brandFilterCombo.setSelectedIndex(0);
            shelfFilterCombo.setSelectedIndex(0);
            storageShelfFilterCombo.setSelectedIndex(0);
            loadingDetailFilters = true;
            try {
                loadItemTypeFilter();
            } finally {
                loadingDetailFilters = false;
            }
            loadInventory(null, "All");
        });
        stockFilterCombo.addActionListener(e -> loadInventory(searchField.getText().trim(), (String) stockFilterCombo.getSelectedItem()));
        searchField.addActionListener(e -> loadInventory(searchField.getText().trim(), (String) stockFilterCombo.getSelectedItem()));
        UiDebouncer.bind(searchField, 300,
                () -> loadInventory(searchField.getText().trim(), (String) stockFilterCombo.getSelectedItem()));
        departmentFilterCombo.addActionListener(e -> {
            if (loadingDetailFilters) return;
            loadingDetailFilters = true;
            try {
                loadItemTypeFilter();
            } finally {
                loadingDetailFilters = false;
            }
            loadInventory(searchField.getText().trim(), (String) stockFilterCombo.getSelectedItem());
        });
        itemTypeFilterCombo.addActionListener(e -> reloadForDetailFilter());
        productTypeFilterCombo.addActionListener(e -> reloadForDetailFilter());
        brandFilterCombo.addActionListener(e -> reloadForDetailFilter());
        shelfFilterCombo.addActionListener(e -> reloadForDetailFilter());
        storageShelfFilterCombo.addActionListener(e -> reloadForDetailFilter());

        wrapper.add(titleLabel);
        wrapper.add(Box.createVerticalStrut(5));
        wrapper.add(locationLabel);
        wrapper.add(filterPanel);
        wrapper.add(detailFilterPanel);

        return wrapper;
    }

    private JScrollPane buildTablePanel() {
        List<String> columns = new ArrayList<>();
        columns.add("Product ID");
        columns.add("SKU");
        columns.add("Name");
        columns.add("Size");
        columns.add("Description");
        columns.add("Type");
        columns.add("Category");
        columns.add("Item Type");
        columns.add("Brand");
        columns.add("Shelf");
        columns.add("Storage Shelf");
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
                return canInlineEditColumn(column);
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
        installInlineEditing();

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
        setColumnWidth(columnModel, "Size", 100);
        setColumnWidth(columnModel, "Description", 240);
        setColumnWidth(columnModel, "Type", 115);
        setColumnWidth(columnModel, "Category", 120);
        setColumnWidth(columnModel, "Item Type", 130);
        setColumnWidth(columnModel, "Brand", 130);
        setColumnWidth(columnModel, "Shelf", 120);
        setColumnWidth(columnModel, "Storage Shelf", 140);
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

    private boolean canInlineEditColumn(int modelColumn) {
        if (!PermissionManager.hasPermission("EDIT_ITEM") || modelColumn < 0 || tableModel == null) return false;
        String name=tableModel.getColumnName(modelColumn);
        if ("Quantity".equals(name)) return PermissionManager.hasPermission("MANUAL_ADJUSTMENT");
        return List.of("Name","Size","Description","SKU","Type","Cost Price","Price","Reorder Level").contains(name);
    }

    private void installInlineEditing() {
        KeyStroke enter=KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER,0);
        inventoryTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(enter,"smartstock-inline-edit");
        inventoryTable.getActionMap().put("smartstock-inline-edit",new AbstractAction(){
            @Override public void actionPerformed(java.awt.event.ActionEvent event){
                int viewRow=inventoryTable.getSelectedRow(),viewColumn=inventoryTable.getSelectedColumn();
                if(viewRow<0||viewColumn<0)return;
                int modelRow=inventoryTable.convertRowIndexToModel(viewRow);
                int modelColumn=inventoryTable.convertColumnIndexToModel(viewColumn);
                if(!canInlineEditColumn(modelColumn)){
                    Toolkit.getDefaultToolkit().beep();return;
                }
                if(!inventoryTable.isEditing()){
                    Object old=tableModel.getValueAt(modelRow,modelColumn);
                    inventoryTable.putClientProperty("SmartStock.inlineOldValue",old);
                    inventoryTable.putClientProperty("SmartStock.inlineModelRow",modelRow);
                    inventoryTable.putClientProperty("SmartStock.inlineModelColumn",modelColumn);
                    if(inventoryTable.editCellAt(viewRow,viewColumn)){
                        Component editor=inventoryTable.getEditorComponent();editor.requestFocusInWindow();
                        if(editor instanceof JTextField text)text.selectAll();
                    }
                    return;
                }
                int editRow=(Integer)inventoryTable.getClientProperty("SmartStock.inlineModelRow");
                int editColumn=(Integer)inventoryTable.getClientProperty("SmartStock.inlineModelColumn");
                Object old=inventoryTable.getClientProperty("SmartStock.inlineOldValue");
                if(!inventoryTable.getCellEditor().stopCellEditing())return;
                saveInlineEdit(editRow,editColumn,old,tableModel.getValueAt(editRow,editColumn));
            }
        });
    }

    private void saveInlineEdit(int modelRow,int modelColumn,Object oldValue,Object newValue){
        String before=String.valueOf(oldValue==null?"":oldValue),after=String.valueOf(newValue==null?"":newValue).trim();
        if(before.equals(after))return;
        int productId=((Number)tableModel.getValueAt(modelRow,tableModel.findColumn("Product ID"))).intValue();
        String field=switch(tableModel.getColumnName(modelColumn)){
            case"Name"->"NAME";case"Size"->"SIZE";case"Description"->"DESCRIPTION";case"SKU"->"SKU";
            case"Type"->"PRODUCT_TYPE";case"Cost Price"->"COST_PRICE";case"Price"->"PRICE";
            case"Quantity"->"QUANTITY";case"Reorder Level"->"REORDER_LEVEL";
            default->null;};
        if(field==null){tableModel.setValueAt(oldValue,modelRow,modelColumn);return;}
        LanApiClient.InventoryCellUpdate request=new LanApiClient.InventoryCellUpdate(productId,field,after,before);
        UiTaskRunner.submit(this,"inventory.inline-update",()->{LanApiClient.updateInventoryCell(request,java.util.UUID.randomUUID().toString());return true;},
                ignored->{SessionDataCache.invalidate("inventory-");loadInventory(searchField.getText().trim(),(String)stockFilterCombo.getSelectedItem());},
                failure->{tableModel.setValueAt(oldValue,modelRow,modelColumn);JOptionPane.showMessageDialog(this,
                        "Could not save inventory change: "+failure.getMessage(),"Inventory Edit",JOptionPane.ERROR_MESSAGE);});
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
        reviewPricesButton = new JButton("Review $20 Prices");
        reviewPricesButton.setEnabled(PermissionManager.hasPermission("EDIT_ITEM"));
        reviewPricesButton.setToolTipText(reviewPricesButton.isEnabled()
                ? "Find item prices that are not multiples of $20."
                : "Requires Edit Item permission.");
        reviewPricesButton.addActionListener(e -> new InventoryPriceRoundingDialog(this, () -> {
            SessionDataCache.invalidate("inventory-");
            loadInventory(searchField.getText().trim(), (String) stockFilterCombo.getSelectedItem());
        }).setVisible(true));

        footerPanel.add(totalProductsLabel);
        footerPanel.add(totalItemsLabel);
        footerPanel.add(viewDetailsButton);
        footerPanel.add(reviewPricesButton);
        footerPanel.add(loadingState);

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

    private void loadDetailFilters() {
        CachedUiLoader.load(this, "inventory-lookups:all", LanApiClient.InventoryLookups.class,
                SessionDataCache.REFERENCE_TTL, loadingState,
                () -> LanApiClient.loadInventoryLookups(null), this::applyDetailFilters);
    }

    private void applyDetailFilters(LanApiClient.InventoryLookups lookups) {
        loadingDetailFilters = true;
        try {
            allInventoryLookups = lookups;
            replaceOptions(departmentFilterCombo, "All Departments",
                    lookups.departments().stream().map(LanApiClient.NamedId::name).toList());
            replaceOptions(brandFilterCombo, "All Brands", lookups.brands());
            replaceOptions(shelfFilterCombo, "All Shelves", lookups.shelves());
            replaceOptions(storageShelfFilterCombo, "All Storage Shelves", lookups.shelves());
            loadItemTypeFilter();
        } finally {
            loadingDetailFilters = false;
        }
    }

    private void reloadForDetailFilter() {
        if (!loadingDetailFilters) loadInventory(searchField.getText().trim(),
                (String) stockFilterCombo.getSelectedItem());
    }

    private void loadItemTypeFilter() {
        String department = selectedFilter(departmentFilterCombo, "All Departments");
        LanApiClient.InventoryLookups all = allInventoryLookups;
        if (all == null) return;
        Integer categoryId = department == null ? null : all.departments().stream()
                .filter(item -> item.name().equals(department))
                .map(LanApiClient.NamedId::id).findFirst().orElse(null);
        if (categoryId == null) {
            replaceOptions(itemTypeFilterCombo, "All Item Types", all.itemTypes());
            return;
        }
        int selectedCategoryId = categoryId;
        CachedUiLoader.load(this, "inventory-lookups:category:" + selectedCategoryId,
                LanApiClient.InventoryLookups.class, SessionDataCache.REFERENCE_TTL, loadingState,
                () -> LanApiClient.loadInventoryLookups(selectedCategoryId),
                filtered -> {
                    loadingDetailFilters = true;
                    try { replaceOptions(itemTypeFilterCombo, "All Item Types", filtered.itemTypes()); }
                    finally { loadingDetailFilters = false; }
                    loadInventory(searchField.getText().trim(), (String) stockFilterCombo.getSelectedItem());
                });
    }

    private void replaceOptions(JComboBox<String> box, String allLabel, List<String> values) {
        Object selected = box.getSelectedItem();
        box.removeAllItems();
        box.addItem(allLabel);
        if (values != null) values.forEach(box::addItem);
        if (selected != null) {
            for (int i = 0; i < box.getItemCount(); i++) {
                if (box.getItemAt(i).equals(selected.toString())) {
                    box.setSelectedIndex(i);
                    return;
                }
            }
        }
        box.setSelectedIndex(0);
    }

    private String selectedFilter(JComboBox<String> box, String allLabel) {
        if (box == null || box.getSelectedItem() == null || allLabel.equals(box.getSelectedItem())) return null;
        return box.getSelectedItem().toString();
    }

    private void loadInventory(String searchText, String stockFilter) {
        LanApiClient.InventoryRequest request = new LanApiClient.InventoryRequest(
                searchText, stockFilter,
                selectedFilter(departmentFilterCombo, "All Departments"),
                selectedFilter(productTypeFilterCombo, "All Product Types"),
                selectedFilter(itemTypeFilterCombo, "All Item Types"),
                selectedFilter(brandFilterCombo, "All Brands"),
                selectedFilter(shelfFilterCombo, "All Shelves"),
                selectedFilter(storageShelfFilterCombo, "All Storage Shelves"));
        String cacheKey = "inventory-list:" + request;
        CachedUiLoader.load(this, "inventory-list.search", cacheKey, LanApiClient.InventoryResult.class,
                SessionDataCache.SCREEN_TTL, loadingState,
                () -> LanApiClient.loadInventory(request), this::applyInventory);
    }

    private void applyInventory(LanApiClient.InventoryResult result) {
        tableModel.setRowCount(0);
        for (LanApiClient.InventoryProduct product : result.products()) {
                String productType = normalizeProductType(product.productType());
                Vector<Object> row = new Vector<>();
                row.add(product.productId()); row.add(product.sku()); row.add(product.name());
                row.add(product.size()); row.add(product.description()); row.add(formatProductType(productType));
                row.add(product.department()); row.add(product.itemType()); row.add(product.brand());
                row.add(product.shelf()); row.add(product.storageShelf());
                if (canViewVendor) row.add(product.vendor());
                if (canViewCostPrice) row.add(utils.CurrencyFormatter.format(product.costPrice()));
                row.add(utils.CurrencyFormatter.format(product.price()));
                row.add(product.quantityOnHand()); row.add(product.reorderLevel());
                row.add(isInventoryProduct(productType)
                        ? getStockStatus(product.quantityOnHand(), product.reorderLevel())
                        : formatProductType(productType));
                if (canViewCreatedBy) row.add(product.createdBy());
                tableModel.addRow(row);
        }
        totalProductsLabel.setText("Products: " + result.totalProducts());
        totalItemsLabel.setText("Units in Stock: " + result.totalUnits());
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
