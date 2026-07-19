package ui.screens;

import ui.screens.customorders.EditCustomItem;

import managers.PermissionManager;
import managers.SessionManager;
import services.LanApiClient;
import ui.components.RoundedBorder;
import ui.components.AppMenuBar;
import ui.components.DepartmentSelector;
import ui.components.ItemDetailsSelector;
import ui.components.VendorSelector;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import ui.helpers.WindowHelper;
import ui.helpers.UiTaskRunner;
import ui.helpers.SessionDataCache;
import ui.helpers.ProductImageHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class EditItem extends JFrame {

    private JTextField searchField;
    private JButton searchBtn;

    private JTextField nameField;
    private JTextField sizeField;
    private JTextField skuField;
    private JTextField barcodeField;
    private JTextArea descriptionArea;
    private JTextArea barcodesArea;
    private JTextField costPriceField;
    private JTextField priceField;
    private JComboBox<String> itemTypeBox;
    private JTextField quantityField;
    private JTextField reorderLevelField;
    private DepartmentSelector departmentSelector;
    private ItemDetailsSelector itemDetailsSelector;
    private VendorSelector vendorSelector;
    private ProductImageHelper.ImageSelector imageSelector;

    private JButton saveButton;
    private JButton clearButton;
    private JButton cancelButton;
    private JLabel quantityHintLabel;
    private JLabel selectionStatusLabel;
    private JScrollPane inventoryScrollPane;

    private int selectedProductId = -1;
    private int selectedOriginalQuantity = 0;
    private String pendingSaveKey;
    private String pendingSaveFingerprint;
    private String selectedProductType = "INVENTORY";
    private final boolean canManualAdjustment = PermissionManager.hasPermission("MANUAL_ADJUSTMENT");

    public EditItem() {
        setTitle("Edit Item");
        setSize(1280, 780);
        setMinimumSize(new Dimension(900, 650));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "EditItem"));
        initializeFields();

        JTabbedPane editTabs = new JTabbedPane();
        editTabs.setFont(new Font("SansSerif", Font.BOLD, 13));
        editTabs.addTab("Inventory Item", createInventoryEditPanel());
        editTabs.addTab("Custom Item", new EditCustomItem(this));
        add(editTabs);

        wireActions();
        setFormEnabled(false);
        WindowHelper.configurePosWindow(this);
        SwingUtilities.invokeLater(() -> {
            searchField.requestFocusInWindow();
            SwingUtilities.invokeLater(() -> inventoryScrollPane.getViewport().setViewPosition(new Point(0, 0)));
        });
    }

    private void initializeFields() {
        searchField = new JTextField();
        searchBtn = new JButton("Search");
        nameField = new JTextField();
        sizeField = new JTextField();
        skuField = new JTextField();
        barcodeField = new JTextField();
        descriptionArea = new JTextArea(3, 20);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        barcodesArea = new JTextArea(2, 20);
        barcodesArea.setLineWrap(true);
        barcodesArea.setWrapStyleWord(true);
        costPriceField = new JTextField();
        priceField = new JTextField();
        itemTypeBox = new JComboBox<>(new String[]{"Inventory", "Service", "Non Inventory"});
        quantityField = new JTextField();
        if (!canManualAdjustment) {
            quantityField.setToolTipText("Requires Manual Adjustment permission.");
        }
        reorderLevelField = new JTextField();
        departmentSelector = new DepartmentSelector();
        Integer initialLocationId = getCurrentSelectedLocationId();
        itemDetailsSelector = new ItemDetailsSelector(departmentSelector, initialLocationId == null ? 1 : initialLocationId);
        vendorSelector = new VendorSelector();
        imageSelector = ProductImageHelper.createSimpleImageSelector(this);
        quantityHintLabel = createHelperLabel(canManualAdjustment
                ? "Current on-hand quantity for the selected store."
                : "Read-only. Requires Manual Adjustment permission.");
        selectionStatusLabel = createHelperLabel("No product selected.");
        saveButton = new JButton("Save Changes");
        clearButton = new JButton("Clear Selection");
        cancelButton = new JButton("Close");
    }

    private JPanel createInventoryEditPanel() {
        JPanel root = DeckersSwing.panel();
        root.setLayout(new BorderLayout());

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(createHeaderPanel(), BorderLayout.NORTH);
        top.add(createSearchPanel(), BorderLayout.SOUTH);
        root.add(top, BorderLayout.NORTH);

        ResponsiveSectionPanel form = new ResponsiveSectionPanel(
                createItemDetailsSection(),
                createPricingSection(),
                createOrganizationSection(),
                createImageSection()
        );
        inventoryScrollPane = new JScrollPane(form);
        inventoryScrollPane.setBorder(BorderFactory.createEmptyBorder());
        inventoryScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        inventoryScrollPane.getVerticalScrollBar().setUnitIncrement(18);
        root.add(inventoryScrollPane, BorderLayout.CENTER);
        root.add(createFooterPanel(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(18, 0));
        header.setBorder(new EmptyBorder(18, 26, 16, 26));
        header.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        header.setBackground(DeckersPalette.sectionFill(DeckersPalette.ORANGE));

        JPanel copy = new JPanel();
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        copy.setOpaque(false);
        JLabel title = new JLabel("Edit an inventory item");
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        title.setForeground(DeckersPalette.text());
        title.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        JLabel subtitle = new JLabel("Find a product, review its current information, and save only the changes you need.");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(DeckersPalette.muted());
        subtitle.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        copy.add(title);
        copy.add(Box.createVerticalStrut(5));
        copy.add(subtitle);

        JLabel required = new JLabel("* Required");
        required.setFont(new Font("SansSerif", Font.BOLD, 12));
        required.setForeground(DeckersPalette.ORANGE);
        required.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        header.add(copy, BorderLayout.CENTER);
        header.add(required, BorderLayout.EAST);
        return header;
    }

    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout(14, 6));
        DeckersSwing.styleBand(panel, DeckersPalette.PURPLE, new Insets(10, 20, 10, 20));

        JPanel copy = new JPanel();
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        copy.setOpaque(false);
        JLabel title = new JLabel("Find a product");
        title.setFont(new Font("SansSerif", Font.BOLD, 14));
        title.setForeground(DeckersPalette.text());
        title.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        JLabel hint = createHelperLabel("Search by name, size, SKU, or barcode.");
        copy.add(title);
        copy.add(Box.createVerticalStrut(3));
        copy.add(hint);

        JPanel searchControls = new JPanel(new BorderLayout(8, 0));
        searchControls.setOpaque(false);
        setComfortableControlSize(searchField);
        DeckersSwing.styleUtilityButton(searchBtn, DeckersPalette.PURPLE);
        searchBtn.setPreferredSize(new Dimension(105, 38));
        searchControls.add(searchField, BorderLayout.CENTER);
        searchControls.add(searchBtn, BorderLayout.EAST);

        panel.add(copy, BorderLayout.WEST);
        panel.add(searchControls, BorderLayout.CENTER);
        panel.add(selectionStatusLabel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createItemDetailsSection() {
        JPanel fields = createFieldGrid();
        addField(fields, 0, 0, "Item name", nameField, "Shown in sales and inventory search.", true, 1);
        addField(fields, 1, 0, "Primary barcode", barcodeField, "Scan or enter the main barcode.", true, 1);
        addField(fields, 0, 1, "Size", sizeField, "Optional, such as Small, 10 mm, or 5 lb.", false, 1);
        addField(fields, 1, 1, "SKU", skuField, "The product's permanent stock identifier.", true, 1);
        addField(fields, 0, 2, "Description", createTextAreaScroll(descriptionArea, 76),
                "Optional product notes or customer-facing details.", false, 2);
        return createSectionCard("1", "Item details", "Review the product identity and searchable information.", DeckersPalette.ORANGE, fields);
    }

    private JPanel createPricingSection() {
        JPanel fields = createFieldGrid();
        addField(fields, 0, 0, "Cost price", costPriceField, "What the business pays for one item.", true, 1);
        addField(fields, 1, 0, "Selling price", priceField, "The price charged to the customer.", true, 1);
        addField(fields, 0, 1, "Product classification", itemTypeBox, "Inventory, Service, or Non Inventory.", false, 1);
        addField(fields, 1, 1, "Current quantity", quantityField, quantityHintLabel, false, 1);
        addField(fields, 0, 2, "Reorder quantity", reorderLevelField,
                "Required for inventory items; this signals when more stock is needed.", false, 2);
        return createSectionCard("2", "Pricing and stock", "Update pricing and store-level inventory settings.", DeckersPalette.LIME, fields);
    }

    private JPanel createOrganizationSection() {
        JPanel fields = createFieldGrid();
        addField(fields, 0, 0, "Department", departmentSelector, "Select the business department first.", true, 1);
        addField(fields, 1, 0, "Item type", itemDetailsSelector.itemTypeComponent(), "Filtered by the selected department.", true, 1);
        addField(fields, 0, 1, "Brand", itemDetailsSelector.brandComponent(), "Choose an existing brand or enter a new one.", true, 1);
        addField(fields, 1, 1, "Vendor", vendorSelector, "Optional preferred supplier.", false, 1);
        addField(fields, 0, 2, "Sales shelf", itemDetailsSelector.shelfComponent(), "Where staff find it on the sales floor.", true, 1);
        addField(fields, 1, 2, "Storage shelf", itemDetailsSelector.storageShelfComponent(), "Optional location for reserve stock.", false, 1);
        return createSectionCard("3", "Organization", "Update where the product belongs and where it is stored.", DeckersPalette.MAGENTA, fields);
    }

    private JPanel createImageSection() {
        JPanel fields = createFieldGrid();
        imageSelector.setPreferredSize(new Dimension(380, 112));
        imageSelector.setMinimumSize(new Dimension(300, 112));
        addField(fields, 0, 0, "Additional barcodes", createTextAreaScroll(barcodesArea, 112),
                "Optional. Enter one per line, or separate them with commas.", false, 1);
        addField(fields, 1, 0, "Product image", imageSelector, "A clear product image is required.", true, 1);
        return createSectionCard("4", "Barcodes and image", "Maintain the required product image and any additional barcodes.", DeckersPalette.PURPLE, fields);
    }

    private JPanel createFieldGrid() {
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        return fields;
    }

    private void addField(JPanel panel, int column, int row, String labelText, JComponent component,
                          String helperText, boolean required, int width) {
        addField(panel, column, row, labelText, component, createHelperLabel(helperText), required, width);
    }

    private void addField(JPanel panel, int column, int row, String labelText, JComponent component,
                          JLabel helperLabel, boolean required, int width) {
        JPanel fieldPanel = new JPanel();
        fieldPanel.setLayout(new BoxLayout(fieldPanel, BoxLayout.Y_AXIS));
        fieldPanel.setOpaque(false);
        JLabel label = new JLabel(labelText + (required ? " *" : ""));
        label.setFont(new Font("SansSerif", Font.BOLD, 13));
        label.setForeground(DeckersPalette.text());
        label.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        setComfortableControlSize(component);
        helperLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        fieldPanel.add(label);
        fieldPanel.add(Box.createVerticalStrut(6));
        fieldPanel.add(component);
        if (!helperLabel.getText().isBlank()) {
            fieldPanel.add(Box.createVerticalStrut(5));
            fieldPanel.add(helperLabel);
        }

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = column;
        gbc.gridy = row;
        gbc.gridwidth = width;
        gbc.weightx = width;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(row == 0 ? 0 : 14, column == 0 ? 0 : 10, 0, column + width >= 2 ? 0 : 10);
        panel.add(fieldPanel, gbc);
    }

    private JPanel createSectionCard(String step, String titleText, String subtitleText, Color accent, JComponent body) {
        JPanel card = new JPanel(new BorderLayout(0, 16));
        DeckersSwing.styleBand(card, accent, new Insets(16, 18, 18, 18));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel heading = new JPanel(new BorderLayout(12, 0));
        heading.setOpaque(false);
        JLabel stepLabel = new JLabel(step, SwingConstants.CENTER);
        stepLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        stepLabel.setForeground(DeckersPalette.text());
        stepLabel.setOpaque(true);
        stepLabel.setBackground(DeckersPalette.tileHover(accent));
        stepLabel.setBorder(new RoundedBorder(10, DeckersPalette.sectionBorder(accent), 1));
        stepLabel.setPreferredSize(new Dimension(34, 34));
        stepLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        JLabel title = new JLabel(titleText);
        title.setFont(new Font("SansSerif", Font.BOLD, 17));
        title.setForeground(DeckersPalette.text());
        title.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        JLabel subtitle = new JLabel(subtitleText);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitle.setForeground(DeckersPalette.muted());
        subtitle.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        text.add(title);
        text.add(Box.createVerticalStrut(3));
        text.add(subtitle);
        heading.add(stepLabel, BorderLayout.WEST);
        heading.add(text, BorderLayout.CENTER);
        card.add(heading, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JScrollPane createTextAreaScroll(JTextArea area, int height) {
        area.setFont(new Font("SansSerif", Font.PLAIN, 14));
        area.setBorder(new EmptyBorder(7, 9, 7, 9));
        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.setPreferredSize(new Dimension(300, height));
        scrollPane.setMinimumSize(new Dimension(120, height));
        return scrollPane;
    }

    private JLabel createHelperLabel(String text) {
        JLabel label = new JLabel(text == null ? "" : text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 11));
        label.setForeground(DeckersPalette.muted());
        label.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        return label;
    }

    private void setComfortableControlSize(JComponent component) {
        if (component instanceof JTextField || component instanceof JComboBox<?> || component instanceof DepartmentSelector
                || component instanceof VendorSelector) {
            Dimension preferred = component.getPreferredSize();
            component.setPreferredSize(new Dimension(Math.max(180, preferred.width), Math.max(34, preferred.height)));
            component.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(34, preferred.height)));
        }
    }

    private JPanel createFooterPanel() {
        JPanel footer = new JPanel(new BorderLayout(16, 0));
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, DeckersPalette.border()),
                new EmptyBorder(12, 20, 12, 20)
        ));
        footer.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        footer.setBackground(DeckersPalette.surface());
        JLabel hint = createHelperLabel("Search for and select a product before editing its details.");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        DeckersSwing.styleUtilityButton(clearButton, DeckersPalette.YELLOW);
        DeckersSwing.styleUtilityButton(cancelButton, DeckersPalette.CORAL);
        DeckersSwing.styleUtilityButton(saveButton, DeckersPalette.LIME);
        saveButton.setPreferredSize(new Dimension(145, 40));
        actions.add(clearButton);
        actions.add(cancelButton);
        actions.add(saveButton);
        footer.add(hint, BorderLayout.WEST);
        footer.add(actions, BorderLayout.EAST);
        return footer;
    }

    private void wireActions() {
        searchBtn.addActionListener(e -> searchProduct());
        searchField.addActionListener(e -> searchProduct());
        saveButton.addActionListener(e -> saveChanges());
        clearButton.addActionListener(e -> clearSelection());
        cancelButton.addActionListener(e -> dispose());
        itemTypeBox.addActionListener(e -> {
            selectedProductType = getSelectedProductType();
            updateInventoryFieldsForType();
        });
        saveButton.setToolTipText("Save changes (Command/Ctrl+S)");

        int menuShortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_S, menuShortcut), "saveChanges");
        getRootPane().getActionMap().put("saveChanges", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (saveButton.isEnabled()) {
                    saveChanges();
                }
            }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeEditItem");
        getRootPane().getActionMap().put("closeEditItem", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dispose();
            }
        });
    }

    private Integer getCurrentSelectedLocationId() {
        return SessionManager.getCurrentLocationId();
    }

    private Integer requireCurrentSelectedLocationId() {
        Integer locationId = getCurrentSelectedLocationId();
        if (locationId == null) {
            JOptionPane.showMessageDialog(this, "No store is selected. Please log in with a store or use Change Store first.");
        }
        return locationId;
    }

    private void searchProduct() {
        if (requireCurrentSelectedLocationId() == null) {
            return;
        }
        String searchText = searchField.getText().trim();
        if (searchText.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Enter a product name, brand, item type, SKU, barcode, department, or shelf to search.");
            return;
        }

        try {
            List<LanApiClient.EditableProduct> products = LanApiClient.searchEditableProducts(searchText);
            if (products.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No matching products found.");
                return;
            }
            String[] columns = {"ID", "Name", "Size", "SKU", "Barcode", "Description", "Cost Price", "Price",
                    "Classification", "Quantity", "Reorder Qty", "Department ID", "Department", "Vendor ID",
                    "Vendor", "Image URL", "Item Type", "Brand", "Shelf", "Storage Shelf"};
            Object[][] rows = products.stream().map(product -> new Object[]{
                    product.productId(), product.name(), product.size(), product.sku(), product.barcode(),
                    product.description(), product.costPrice().doubleValue(), product.price().doubleValue(),
                    product.productType(), product.quantity(), product.reorderLevel(),
                    product.categoryId() == null ? "" : product.categoryId(), product.categoryName(),
                    product.vendorId() == null ? "" : product.vendorId(), product.vendorName(), product.imageUrl(),
                    product.itemTypeName(), product.brandName(), product.shelfName(), product.storageShelfName()
            }).toArray(Object[][]::new);
            DefaultTableModel model = new DefaultTableModel(rows, columns) {
                @Override public boolean isCellEditable(int row, int column) { return false; }
            };
            JTable table = new JTable(model);
            DeckersSwing.styleTable(table, DeckersPalette.PURPLE);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setRowSelectionInterval(0, 0);
            table.setAutoCreateRowSorter(true);
            table.setRowHeight(30);
            int[] hiddenColumns = {0, 5, 6, 7, 10, 11, 13, 15, 16, 17, 18, 19};
            for (int columnIndex : hiddenColumns) hideColumn(table, columnIndex);
            JScrollPane scrollPane = new JScrollPane(table);
            scrollPane.setPreferredSize(new Dimension(900, 300));
            String storeLabel = SessionManager.getCurrentLocationName() != null
                    && !SessionManager.getCurrentLocationName().isBlank()
                    ? SessionManager.getCurrentLocationName() : "the currently selected store";
            int result = JOptionPane.showConfirmDialog(this, scrollPane,
                    "Select a Product to Edit for " + storeLabel,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION || table.getSelectedRow() < 0) return;

            int productId = ((Number) table.getValueAt(table.getSelectedRow(), 0)).intValue();
            LanApiClient.EditableProduct product = products.stream()
                    .filter(candidate -> candidate.productId() == productId).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Selected item is no longer in the search result."));
            selectedProductId = product.productId();
            nameField.setText(product.name());
            sizeField.setText(product.size());
            skuField.setText(product.sku());
            barcodeField.setText(product.barcode());
            descriptionArea.setText(product.description());
            costPriceField.setText(product.costPrice().toPlainString());
            priceField.setText(product.price().toPlainString());
            selectedProductType = normalizeProductType(product.productType());
            itemTypeBox.setSelectedItem(formatProductType(selectedProductType));
            quantityField.setText(String.valueOf(product.quantity()));
            selectedOriginalQuantity = product.quantity();
            reorderLevelField.setText(String.valueOf(product.reorderLevel()));
            departmentSelector.setSelectedDepartment(product.categoryId(), product.categoryName());
            itemDetailsSelector.setValues(product.categoryId(), product.itemTypeName(), product.brandName(),
                    product.shelfName(), product.storageShelfName());
            vendorSelector.setSelectedVendor(product.vendorId(), product.vendorName());
            barcodesArea.setText(String.join("\n",
                    product.additionalBarcodes() == null ? List.of() : product.additionalBarcodes()));
            imageSelector.setImageUrl(product.imageUrl());
            setFormEnabled(true);
            updateInventoryFieldsForType();
            selectionStatusLabel.setText("Editing: " + product.name() + "  •  SKU " + product.sku());
            inventoryScrollPane.getViewport().setViewPosition(new Point(0, 0));
            nameField.requestFocusInWindow();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not load items: " + ex.getMessage(),
                    "SmartStock Server Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void hideColumn(JTable table, int columnIndex) {
        table.getColumnModel().getColumn(columnIndex).setMinWidth(0);
        table.getColumnModel().getColumn(columnIndex).setMaxWidth(0);
        table.getColumnModel().getColumn(columnIndex).setPreferredWidth(0);
    }

    private void saveChanges() {
        if (selectedProductId == -1) {
            JOptionPane.showMessageDialog(this, "No product selected.");
            return;
        }

        Integer selectedLocationId = requireCurrentSelectedLocationId();
        if (selectedLocationId == null) {
            return;
        }

        String name = nameField.getText().trim();
        String size = sizeField.getText().trim();
        String sku = skuField.getText().trim();
        String barcode = barcodeField.getText().trim();
        String description = descriptionArea.getText().trim();
        String barcodesText = barcodesArea.getText().trim();
        String costPriceText = costPriceField.getText().trim();
        String priceText = priceField.getText().trim();
        String quantityText = quantityField.getText().trim();
        String reorderLevelText = reorderLevelField.getText().trim();

        List<String> extraBarcodes = new ArrayList<>();
        Set<String> uniqueBarcodes = new LinkedHashSet<>();
        if (!barcodesText.isEmpty()) {
            String[] barcodeLines = barcodesText.split("[\\r\\n,;]+");
            for (String line : barcodeLines) {
                String extraBarcode = line.trim();
                if (!extraBarcode.isEmpty()) {
                    uniqueBarcodes.add(extraBarcode);
                }
            }
        }

        uniqueBarcodes.remove(sku);
        uniqueBarcodes.remove(barcode);
        extraBarcodes.addAll(uniqueBarcodes);

        String productType = getSelectedProductType();
        boolean inventoryItem = "INVENTORY".equals(productType);

        if (name.isEmpty()) {
            showValidationError("Enter an item name.", nameField);
            return;
        }
        if (sku.isEmpty()) {
            showValidationError("Enter the product SKU.", skuField);
            return;
        }
        if (barcode.isEmpty()) {
            showValidationError("Scan or enter the primary barcode.", barcodeField);
            return;
        }
        if (costPriceText.isEmpty()) {
            showValidationError("Enter the cost price.", costPriceField);
            return;
        }
        if (priceText.isEmpty()) {
            showValidationError("Enter the selling price.", priceField);
            return;
        }
        if (inventoryItem && canManualAdjustment && quantityText.isEmpty()) {
            showValidationError("Enter the current quantity.", quantityField);
            return;
        }
        if (inventoryItem && reorderLevelText.isEmpty()) {
            showValidationError("Enter the reorder quantity.", reorderLevelField);
            return;
        }

        double costPrice;
        try {
            costPrice = utils.CurrencyFormatter.normalize(new java.math.BigDecimal(costPriceText)).doubleValue();
        } catch (NumberFormatException ex) {
            showValidationError("Cost price must be a valid number.", costPriceField);
            return;
        }
        if (costPrice < 0) {
            showValidationError("Cost price cannot be negative.", costPriceField);
            return;
        }

        double price;
        try {
            price = utils.CurrencyFormatter.normalize(new java.math.BigDecimal(priceText)).doubleValue();
        } catch (NumberFormatException ex) {
            showValidationError("Selling price must be a valid number.", priceField);
            return;
        }
        if (price < 0) {
            showValidationError("Selling price cannot be negative.", priceField);
            return;
        }

        int quantity = selectedOriginalQuantity;
        if (inventoryItem && canManualAdjustment) {
            try {
                quantity = Integer.parseInt(quantityText);
            } catch (NumberFormatException ex) {
                showValidationError("Quantity must be a whole number.", quantityField);
                return;
            }
        }

        int reorderLevel = 0;
        if (inventoryItem) {
            try {
                reorderLevel = Integer.parseInt(reorderLevelText);
            } catch (NumberFormatException ex) {
                showValidationError("Reorder quantity must be a whole number.", reorderLevelField);
                return;
            }
            if (reorderLevel < 0) {
                showValidationError("Reorder quantity cannot be negative.", reorderLevelField);
                return;
            }
        }

        Integer categoryId = departmentSelector.getSelectedDepartmentId();
        if (categoryId == null && !departmentSelector.getSelectedDepartmentName().isBlank()) {
            return;
        }
        if (categoryId == null) {
            showValidationError("Select a department.", departmentSelector);
            return;
        }
        if (itemDetailsSelector.itemTypeName().isBlank()) {
            showValidationError("Select or enter an item type.", itemDetailsSelector.itemTypeComponent());
            return;
        }
        if (itemDetailsSelector.brandName().isBlank()) {
            showValidationError("Select or enter a brand.", itemDetailsSelector.brandComponent());
            return;
        }
        if (itemDetailsSelector.shelfName().isBlank()) {
            showValidationError("Select or enter the sales shelf location.", itemDetailsSelector.shelfComponent());
            return;
        }
        if (imageSelector.getImageUrl().isBlank()) {
            showValidationError("Choose a product image or enter an image URL.", imageSelector);
            return;
        }
        Integer vendorId = vendorSelector.getSelectedVendorId();
        if (vendorId == null && !vendorSelector.getSelectedVendorName().isBlank()) {
            return;
        }

        try {
            String imageInput=imageSelector.getImageUrl();
            String itemTypeName=itemDetailsSelector.itemTypeName(),brandName=itemDetailsSelector.brandName(),shelfName=itemDetailsSelector.shelfName(),storageShelfName=itemDetailsSelector.storageShelfName();
            LanApiClient.ProductSaveRequest draft = new LanApiClient.ProductSaveRequest(
                    selectedProductId, name, size, sku, barcode, description,
                    BigDecimal.valueOf(costPrice), BigDecimal.valueOf(price), productType,
                    categoryId, vendorId, imageInput, itemTypeName,
                    brandName, shelfName,
                    storageShelfName, List.copyOf(extraBarcodes),
                    quantity, reorderLevel, selectedOriginalQuantity,
                    inventoryItem && canManualAdjustment
            );
            String fingerprint = draft.toString();
            if (!fingerprint.equals(pendingSaveFingerprint) || pendingSaveKey == null) {
                pendingSaveFingerprint = fingerprint;
                pendingSaveKey = UUID.randomUUID().toString();
            }
            String mutationKey=pendingSaveKey;Integer productId=selectedProductId;Integer originalQuantity=selectedOriginalQuantity;int requestedQuantity=quantity,requestedReorderLevel=reorderLevel;boolean adjust=inventoryItem&&canManualAdjustment;
            UiTaskRunner.submit(this,"items.update",()->{String uploaded=ProductImageHelper.uploadLocalImageIfNeeded(imageInput);LanApiClient.ProductSaveRequest request=new LanApiClient.ProductSaveRequest(productId,name,size,sku,barcode,description,BigDecimal.valueOf(costPrice),BigDecimal.valueOf(price),productType,categoryId,vendorId,uploaded,itemTypeName,brandName,shelfName,storageShelfName,List.copyOf(extraBarcodes),requestedQuantity,requestedReorderLevel,originalQuantity,adjust);return new ProductSaveOutcome(LanApiClient.updateProduct(request,mutationKey),uploaded);},outcome->{pendingSaveKey=null;pendingSaveFingerprint=null;SessionDataCache.invalidate("inventory-");imageSelector.setImageUrl(outcome.imageUrl());selectedOriginalQuantity=outcome.saved().quantity();JOptionPane.showMessageDialog(this,"Item updated successfully.");clearSelection();},ex->JOptionPane.showMessageDialog(this,"Failed to update item: "+ex.getMessage()));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to update item: " + ex.getMessage());
        }
    }

    private record ProductSaveOutcome(LanApiClient.SavedProduct saved,String imageUrl) { }

    private void showValidationError(String message, JComponent component) {
        JOptionPane.showMessageDialog(this, message, "Check Item Details", JOptionPane.WARNING_MESSAGE);
        component.scrollRectToVisible(new Rectangle(component.getSize()));
        component.requestFocusInWindow();
        if (component instanceof JTextComponent textComponent) {
            textComponent.selectAll();
        } else if (component instanceof JComboBox<?> comboBox && comboBox.isEditable()) {
            Component editor = comboBox.getEditor().getEditorComponent();
            editor.requestFocusInWindow();
            if (editor instanceof JTextComponent textComponent) {
                textComponent.selectAll();
            }
        }
    }

    private void clearSelection() {
        selectedProductId = -1;
        selectedOriginalQuantity = 0;
        selectedProductType = "INVENTORY";
        nameField.setText("");
        sizeField.setText("");
        skuField.setText("");
        barcodeField.setText("");
        descriptionArea.setText("");
        barcodesArea.setText("");
        costPriceField.setText("");
        priceField.setText("");
        itemTypeBox.setSelectedItem("Inventory");
        quantityField.setText("");
        reorderLevelField.setText("");
        departmentSelector.clearSelection();
        itemDetailsSelector.clearSelection();
        vendorSelector.clearSelection();
        imageSelector.setImageUrl("");
        searchField.setText("");
        selectionStatusLabel.setText("No product selected.");
        setFormEnabled(false);
        searchField.requestFocusInWindow();
    }

    private void setFormEnabled(boolean enabled) {
        nameField.setEnabled(enabled);
        sizeField.setEnabled(enabled);
        skuField.setEnabled(enabled);
        barcodeField.setEnabled(enabled);
        descriptionArea.setEnabled(enabled);
        barcodesArea.setEnabled(enabled);
        costPriceField.setEnabled(enabled);
        priceField.setEnabled(enabled);
        itemTypeBox.setEnabled(enabled);
        updateInventoryFieldsForType();
        departmentSelector.setSelectorEnabled(enabled);
        itemDetailsSelector.setSelectorEnabled(enabled);
        vendorSelector.setSelectorEnabled(enabled);
        imageSelector.setSelectorEnabled(enabled);
        saveButton.setEnabled(enabled);
        clearButton.setEnabled(enabled);
    }

    private void updateInventoryFieldsForType() {
        boolean enabled = selectedProductId != -1;
        boolean inventoryItem = "INVENTORY".equals(getSelectedProductType());
        quantityField.setEnabled(enabled && inventoryItem && canManualAdjustment);
        reorderLevelField.setEnabled(enabled && inventoryItem);
        if (quantityHintLabel != null) {
            quantityHintLabel.setText(!inventoryItem
                    ? "Not used for services or non-inventory items."
                    : canManualAdjustment
                    ? "Current on-hand quantity for the selected store."
                    : "Read-only. Requires Manual Adjustment permission.");
        }
        if (!inventoryItem) {
            quantityField.setText("0");
            reorderLevelField.setText("0");
        }
    }

    private String getSelectedProductType() {
        Object selected = itemTypeBox == null ? null : itemTypeBox.getSelectedItem();
        return normalizeProductType(selected == null ? selectedProductType : selected.toString());
    }

    private String normalizeProductType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase().replace(' ', '_');
        if ("SERVICE".equals(normalized) || "NON_INVENTORY".equals(normalized)) {
            return normalized;
        }
        return "INVENTORY";
    }

    private String formatProductType(String productType) {
        return switch (normalizeProductType(productType)) {
            case "SERVICE" -> "Service";
            case "NON_INVENTORY" -> "Non Inventory";
            default -> "Inventory";
        };
    }

    private int parseIntOrDefault(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static class ResponsiveSectionPanel extends JPanel implements Scrollable {
        private static final int WIDE_LAYOUT_MIN_WIDTH = 1450;
        private static final int SECTION_GAP = 14;

        private final JComponent itemDetailsSection;
        private final JComponent pricingSection;
        private final JComponent organizationSection;
        private final JComponent imageSection;
        private boolean layoutInitialized;
        private boolean wideLayout;

        private ResponsiveSectionPanel(JComponent itemDetailsSection, JComponent pricingSection,
                                       JComponent organizationSection, JComponent imageSection) {
            super(new BorderLayout());
            this.itemDetailsSection = itemDetailsSection;
            this.pricingSection = pricingSection;
            this.organizationSection = organizationSection;
            this.imageSection = imageSection;
            putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
            setBackground(DeckersPalette.background());
            setBorder(new EmptyBorder(18, 20, 22, 20));
            rebuildLayout(false);
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    updateResponsiveLayout();
                }
            });
        }

        private void updateResponsiveLayout() {
            boolean shouldUseWideLayout = getWidth() >= WIDE_LAYOUT_MIN_WIDTH;
            if (!layoutInitialized || shouldUseWideLayout != wideLayout) {
                rebuildLayout(shouldUseWideLayout);
            }
        }

        private void rebuildLayout(boolean useWideLayout) {
            wideLayout = useWideLayout;
            layoutInitialized = true;
            removeAll();
            if (wideLayout) {
                JPanel columns = new JPanel(new GridLayout(1, 2, SECTION_GAP, 0));
                columns.setOpaque(false);
                columns.add(createColumn(itemDetailsSection, pricingSection));
                columns.add(createColumn(organizationSection, imageSection));
                add(columns, BorderLayout.CENTER);
            } else {
                add(createColumn(itemDetailsSection, pricingSection, organizationSection, imageSection), BorderLayout.CENTER);
            }
            revalidate();
            repaint();
        }

        private JPanel createColumn(JComponent... sections) {
            JPanel column = new JPanel(new GridBagLayout());
            column.setOpaque(false);
            for (int i = 0; i < sections.length; i++) {
                GridBagConstraints gbc = new GridBagConstraints();
                gbc.gridx = 0;
                gbc.gridy = i;
                gbc.weightx = 1;
                gbc.fill = GridBagConstraints.HORIZONTAL;
                gbc.anchor = GridBagConstraints.NORTHWEST;
                gbc.insets = new Insets(i == 0 ? 0 : SECTION_GAP, 0, 0, 0);
                column.add(sections[i], gbc);
            }
            GridBagConstraints glue = new GridBagConstraints();
            glue.gridx = 0;
            glue.gridy = sections.length;
            glue.weightx = 1;
            glue.weighty = 1;
            glue.fill = GridBagConstraints.BOTH;
            JPanel spacer = new JPanel();
            spacer.setOpaque(false);
            column.add(spacer, glue);
            return column;
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(visibleRect.height - 36, 18);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
