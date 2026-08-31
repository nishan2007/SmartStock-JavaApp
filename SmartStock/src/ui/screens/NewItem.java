package ui.screens;

import ui.screens.customorders.NewCustomItem;

import managers.SessionManager;
import managers.CompanyCustomizationManager;
import services.LanApiClient;
import services.InventoryCatalogCache;
import ui.components.AppMenuBar;
import ui.components.DepartmentSelector;
import ui.components.ItemDetailsSelector;
import ui.components.RoundedBorder;
import ui.components.VendorSelector;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import ui.helpers.WindowHelper;
import ui.helpers.UiTaskRunner;
import ui.helpers.SessionDataCache;
import ui.helpers.ProductImageHelper;
import ui.helpers.BarcodeGenerationHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class NewItem extends JFrame {

    private JTextField nameField;
    private JTextField sizeField;
    private JTextField skuField;
    private JTextField barcodeField;
    private JTextArea descriptionArea;
    private JTextArea barcodesArea;
    private JTextField costPriceField;
    private JTextField priceField;
    private JComboBox<String> itemTypeBox;
    private DepartmentSelector departmentSelector;
    private ItemDetailsSelector itemDetailsSelector;
    private VendorSelector vendorSelector;
    private JTextField quantityField;
    private ProductImageHelper.ImageSelector imageSelector;
    private JButton saveButton;
    private JButton clearButton;
    private JButton cancelButton;
    private JLabel quantityHintLabel;
    private JScrollPane inventoryScrollPane;
    private final int selectedLocationId;
    private String pendingSaveKey;
    private String pendingSaveFingerprint;
    private final boolean requireCostPrice;

    public NewItem() {
        this(1);
    }

    public NewItem(int selectedLocationId) {
        this.selectedLocationId = selectedLocationId;
        this.requireCostPrice = loadRequireCostPricePreference();
        setTitle("Add New Item");
        setSize(1120, 800);
        setMinimumSize(new Dimension(900, 650));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this,"NewItem"));
        initializeFields();

        JTabbedPane itemTypeTabs = new JTabbedPane();
        itemTypeTabs.setFont(new Font("SansSerif", Font.BOLD, 13));
        itemTypeTabs.addTab("Inventory Item", createInventoryItemPanel());
        itemTypeTabs.addTab("Custom Item", new NewCustomItem(this));
        add(itemTypeTabs);

        wireActions();
        updateQuantityEnabledForType();

        WindowHelper.configurePosWindow(this);
        SwingUtilities.invokeLater(() -> {
            nameField.requestFocusInWindow();
            SwingUtilities.invokeLater(() -> inventoryScrollPane.getViewport().setViewPosition(new Point(0, 0)));
        });
    }

    private void initializeFields() {
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
        departmentSelector = new DepartmentSelector();
        itemDetailsSelector = new ItemDetailsSelector(departmentSelector, selectedLocationId);
        vendorSelector = new VendorSelector();
        quantityField = new JTextField("0");
        imageSelector = ProductImageHelper.createSimpleImageSelector(this);
        quantityHintLabel = createHelperLabel("Initial on-hand quantity for this location.");
        saveButton = new JButton("Save Item");
        clearButton = new JButton("Clear Form");
        cancelButton = new JButton("Cancel");
    }

    private JPanel createInventoryItemPanel() {
        JPanel root = DeckersSwing.panel();
        root.setLayout(new BorderLayout());
        root.add(createHeaderPanel(), BorderLayout.NORTH);

        ResponsiveSectionPanel form = new ResponsiveSectionPanel(
                createItemDetailsSection(),
                createPricingSection(),
                createOrganizationSection(),
                createOptionalSection()
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
        header.setBorder(new EmptyBorder(20, 26, 18, 26));
        header.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        header.setBackground(DeckersPalette.sectionFill(DeckersPalette.ORANGE));

        JPanel copy = new JPanel();
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        copy.setOpaque(false);
        JLabel title = new JLabel("Add an inventory item");
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        title.setForeground(DeckersPalette.text());
        title.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        JLabel subtitle = new JLabel("Start with the required details, then add any optional information you have.");
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

    private JPanel createItemDetailsSection() {
        JPanel fields = createFieldGrid();
        addField(fields, 0, 0, "Item name", nameField, "Shown in sales and inventory search.", true, 1);
        addField(fields, 1, 0, "Primary barcode", BarcodeGenerationHelper.field(this, barcodeField), "Scan, enter, or generate the main barcode.", true, 1);
        addField(fields, 0, 1, "Size", sizeField, "Optional, such as Small, 10 mm, or 5 lb.", false, 1);
        addField(fields, 1, 1, "SKU", skuField, "Leave blank to generate one automatically.", false, 1);

        JScrollPane descriptionScroll = createTextAreaScroll(descriptionArea, 76);
        addField(fields, 0, 2, "Description", descriptionScroll, "Optional product notes or customer-facing details.", false, 2);
        return createSectionCard("1", "Item details", "Identify the product so staff can find and scan it quickly.", DeckersPalette.ORANGE, fields);
    }

    private JPanel createPricingSection() {
        JPanel fields = createFieldGrid();
        addField(fields, 0, 0, "Cost price", costPriceField,
                requireCostPrice ? "What the business pays for one item." : "Optional; blank saves as $0.00.",
                requireCostPrice, 1);
        addField(fields, 1, 0, "Selling price", priceField, "The price charged to the customer.", true, 1);
        addField(fields, 0, 1, "Product classification", itemTypeBox, "Choose Inventory, Service, or Non Inventory.", false, 1);
        addField(fields, 1, 1, "Starting quantity", quantityField, quantityHintLabel, false, 1);
        return createSectionCard("2", "Pricing and stock", "Set how the item is sold and its opening quantity.", DeckersPalette.LIME, fields);
    }

    private JPanel createOrganizationSection() {
        JPanel fields = createFieldGrid();
        addField(fields, 0, 0, "Department", departmentSelector, "Select the business department first.", true, 1);
        addField(fields, 1, 0, "Item type", itemDetailsSelector.itemTypeComponent(), "Filtered by the selected department.", true, 1);
        addField(fields, 0, 1, "Brand", itemDetailsSelector.brandComponent(), "Choose an existing brand or enter a new one.", true, 1);
        addField(fields, 1, 1, "Vendor", vendorSelector, "Optional preferred supplier.", false, 1);
        addField(fields, 0, 2, "Sales shelf", itemDetailsSelector.shelfComponent(), "Where staff find it on the sales floor.", true, 1);
        addField(fields, 1, 2, "Storage shelf", itemDetailsSelector.storageShelfComponent(), "Optional location for reserve stock.", false, 1);
        return createSectionCard("3", "Organization", "Choose where the product belongs and where it is stored.", DeckersPalette.MAGENTA, fields);
    }

    private JPanel createOptionalSection() {
        JPanel fields = createFieldGrid();
        JScrollPane barcodeScroll = createTextAreaScroll(barcodesArea, 112);
        imageSelector.setPreferredSize(new Dimension(380, 112));
        imageSelector.setMinimumSize(new Dimension(300, 112));
        addField(fields, 0, 0, "Additional barcodes", BarcodeGenerationHelper.area(this, barcodesArea, barcodeScroll), "Optional. Enter one per line, or generate another barcode.", false, 1);
        addField(fields, 1, 0, "Product image", imageSelector,
                "Required for inventory and non-inventory items; optional for services.", false, 1);
        return createSectionCard("4", "Barcodes and image",
                "Add an image when required and any additional barcodes.", DeckersPalette.PURPLE, fields);
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

        JLabel hint = createHelperLabel("Tip: leave SKU blank and SmartStock will generate it for you.");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        DeckersSwing.styleUtilityButton(clearButton, DeckersPalette.YELLOW);
        DeckersSwing.styleUtilityButton(cancelButton, DeckersPalette.CORAL);
        DeckersSwing.styleUtilityButton(saveButton, DeckersPalette.LIME);
        saveButton.setPreferredSize(new Dimension(130, 40));
        actions.add(clearButton);
        actions.add(cancelButton);
        actions.add(saveButton);
        footer.add(hint, BorderLayout.WEST);
        footer.add(actions, BorderLayout.EAST);
        return footer;
    }

    private void wireActions() {
        saveButton.addActionListener(e -> saveItem());
        clearButton.addActionListener(e -> clearFields(true));
        cancelButton.addActionListener(e -> confirmClose());
        itemTypeBox.addActionListener(e -> updateQuantityEnabledForType());
        saveButton.setToolTipText("Save item (Command/Ctrl+S)");

        int menuShortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_S, menuShortcut), "saveItem");
        getRootPane().getActionMap().put("saveItem", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                saveItem();
            }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeItemForm");
        getRootPane().getActionMap().put("closeItemForm", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                confirmClose();
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmClose();
            }
        });
    }

    private void updateQuantityEnabledForType() {
        boolean inventoryItem = "INVENTORY".equals(getSelectedProductType());
        quantityField.setEnabled(inventoryItem);
        if (!inventoryItem) {
            quantityField.setText("0");
        }
        if (quantityHintLabel != null) {
            quantityHintLabel.setText(inventoryItem
                    ? "Initial on-hand quantity for this location."
                    : "Not used for services or non-inventory items.");
        }
    }

    private String getSelectedProductType() {
        Object selected = itemTypeBox == null ? null : itemTypeBox.getSelectedItem();
        if (selected == null) {
            return "INVENTORY";
        }
        String value = selected.toString().trim().toUpperCase().replace(' ', '_');
        return value.isBlank() ? "INVENTORY" : value;
    }

    private void saveItem() {
        String name = nameField.getText().trim();
        String size = sizeField.getText().trim();
        String sku = skuField.getText().trim();
        String barcode = barcodeField.getText().trim();
        String description = descriptionArea.getText().trim();
        String barcodesText = barcodesArea.getText().trim();
        String costPriceText = costPriceField.getText().trim();
        String priceText = priceField.getText().trim();
        String quantityText = quantityField.getText().trim();
        String productType = getSelectedProductType();
        boolean inventoryItem = "INVENTORY".equals(productType);
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

        if (name.isEmpty()) {
            showValidationError("Enter an item name.", nameField);
            return;
        }
        if (barcode.isEmpty()) {
            showValidationError("Scan or enter the primary barcode.", barcodeField);
            return;
        }
        if (requireCostPrice && costPriceText.isEmpty()) {
            showValidationError("Enter the cost price.", costPriceField);
            return;
        }
        if (priceText.isEmpty()) {
            showValidationError("Enter the selling price.", priceField);
            return;
        }

        double costPrice;
        try {
            costPrice = costPriceText.isEmpty() ? 0.0
                    : utils.CurrencyFormatter.normalize(new java.math.BigDecimal(costPriceText)).doubleValue();
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

        int quantity = 0;
        if (inventoryItem) {
            try {
                quantity = quantityText.isBlank() ? 0 : Integer.parseInt(quantityText);
            } catch (NumberFormatException ex) {
                showValidationError("Starting quantity must be a whole number.", quantityField);
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
        if (!"SERVICE".equals(productType) && imageSelector.getImageUrl().isBlank()) {
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
                    null, name, size, sku, barcode, description,
                    BigDecimal.valueOf(costPrice), BigDecimal.valueOf(price), productType,
                    categoryId, vendorId, imageInput, itemTypeName,
                    brandName, shelfName,
                    storageShelfName, List.copyOf(extraBarcodes),
                    quantity, 0, null, true
            );
            String fingerprint = draft.toString();
            if (!fingerprint.equals(pendingSaveFingerprint) || pendingSaveKey == null) {
                pendingSaveFingerprint = fingerprint;
                pendingSaveKey = UUID.randomUUID().toString();
            }
            String mutationKey=pendingSaveKey;
            UiTaskRunner.submit(this,"items.create",()->{String uploaded=ProductImageHelper.uploadLocalImageIfNeeded(imageInput,new ProductImageHelper.ProductImageNaming(draft.name(),draft.brandName(),draft.itemTypeName(),draft.size(),""));LanApiClient.ProductSaveRequest request=new LanApiClient.ProductSaveRequest(draft.productId(),draft.name(),draft.size(),draft.sku(),draft.barcode(),draft.description(),draft.costPrice(),draft.price(),draft.productType(),draft.categoryId(),draft.vendorId(),uploaded,draft.itemTypeName(),draft.brandName(),draft.shelfName(),draft.storageShelfName(),draft.additionalBarcodes(),draft.quantity(),draft.reorderLevel(),draft.expectedQuantity(),draft.adjustQuantity());return new ProductSaveOutcome(LanApiClient.createProduct(request,mutationKey),uploaded);},outcome->{pendingSaveKey=null;pendingSaveFingerprint=null;SessionDataCache.invalidate("inventory-");InventoryCatalogCache.refreshAfterMutation().exceptionally(failure->null);imageSelector.setImageUrl(outcome.imageUrl());JOptionPane.showMessageDialog(this,"Item added successfully. SKU: "+outcome.saved().sku());clearFields(false);},ex->JOptionPane.showMessageDialog(this,"Failed to save item: "+ex.getMessage()));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save item: " + ex.getMessage());
        }
    }

    private static boolean loadRequireCostPricePreference() {
        try { return CompanyCustomizationManager.loadSaleSafetySettings().requireCostPriceOnNewItem(); }
        catch (Exception ex) { return true; }
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

    private void clearFields(boolean confirm) {
        if (confirm && isFormDirty()) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "Clear everything entered on this form?",
                    "Clear Item Form",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }
        nameField.setText("");
        sizeField.setText("");
        skuField.setText("");
        barcodeField.setText("");
        descriptionArea.setText("");
        barcodesArea.setText("");
        costPriceField.setText("");
        priceField.setText("");
        itemTypeBox.setSelectedItem("Inventory");
        departmentSelector.clearSelection();
        itemDetailsSelector.clearSelection();
        vendorSelector.clearSelection();
        quantityField.setText("0");
        imageSelector.setImageUrl("");
        nameField.requestFocusInWindow();
    }

    private void confirmClose() {
        if (isFormDirty()) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "Discard this unsaved item and close?",
                    "Unsaved Item",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }
        dispose();
    }

    private boolean isFormDirty() {
        return !nameField.getText().isBlank()
                || !sizeField.getText().isBlank()
                || !skuField.getText().isBlank()
                || !barcodeField.getText().isBlank()
                || !descriptionArea.getText().isBlank()
                || !barcodesArea.getText().isBlank()
                || !costPriceField.getText().isBlank()
                || !priceField.getText().isBlank()
                || !"INVENTORY".equals(getSelectedProductType())
                || !quantityField.getText().isBlank() && !"0".equals(quantityField.getText().trim())
                || !departmentSelector.getSelectedDepartmentName().isBlank()
                || !itemDetailsSelector.itemTypeName().isBlank()
                || !itemDetailsSelector.brandName().isBlank()
                || !itemDetailsSelector.shelfName().isBlank()
                || !itemDetailsSelector.storageShelfName().isBlank()
                || !vendorSelector.getSelectedVendorName().isBlank()
                || !imageSelector.getImageUrl().isBlank();
    }

    private static class ResponsiveSectionPanel extends JPanel implements Scrollable {
        private static final int WIDE_LAYOUT_MIN_WIDTH = 1450;
        private static final int SECTION_GAP = 14;

        private final JComponent itemDetailsSection;
        private final JComponent pricingSection;
        private final JComponent organizationSection;
        private final JComponent optionalSection;
        private boolean layoutInitialized;
        private boolean wideLayout;

        private ResponsiveSectionPanel(JComponent itemDetailsSection, JComponent pricingSection,
                                       JComponent organizationSection, JComponent optionalSection) {
            super(new BorderLayout());
            this.itemDetailsSection = itemDetailsSection;
            this.pricingSection = pricingSection;
            this.organizationSection = organizationSection;
            this.optionalSection = optionalSection;
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
                columns.add(createColumn(organizationSection, optionalSection));
                add(columns, BorderLayout.CENTER);
            } else {
                add(createColumn(itemDetailsSection, pricingSection, organizationSection, optionalSection), BorderLayout.CENTER);
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
