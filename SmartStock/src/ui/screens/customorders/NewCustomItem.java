package ui.screens.customorders;

import data.DB;
import services.CustomOrderSkuGenerator;
import ui.helpers.ProductImageHelper;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class NewCustomItem extends JPanel {
    private final Window parentWindow;
    private JTextField itemNameField;
    private JTextField itemSkuPreviewField;
    private JTextField barcodeField;
    private JTextArea barcodesArea;
    private JTextArea itemDescriptionArea;
    private ProductImageHelper.ImageSelector imageSelector;
    private JComboBox<String> productTypeBox;
    private JComboBox<String> pricingTypeBox;
    private JTextField priceField;
    private JComboBox<String> areaPriceUnitBox;
    private JComboBox<String> dimensionUnitBox;
    private JTextField maxWidthField;
    private JTextField maxLengthField;
    private JCheckBox hasVariantsCheckBox;
    private JTextField quantityField;
    private JTextField reorderLevelField;
    private JCheckBox activeCheckBox;
    private JButton saveButton;
    private JButton variantsButton;
    private Long savedCustomItemId;
    private final List<JComponent> priceComponents = new ArrayList<>();
    private final List<JComponent> areaComponents = new ArrayList<>();
    private final List<JComponent> mainImageComponents = new ArrayList<>();

    public NewCustomItem(Window parentWindow) {
        this.parentWindow = parentWindow;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buildUi();
    }

    private void buildUi() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        itemNameField = new JTextField();
        itemSkuPreviewField = new JTextField();
        itemSkuPreviewField.setEditable(false);
        barcodeField = new JTextField();
        barcodesArea = new JTextArea(3, 20);
        barcodesArea.setLineWrap(true);
        barcodesArea.setWrapStyleWord(true);
        itemDescriptionArea = new JTextArea(4, 20);
        itemDescriptionArea.setLineWrap(true);
        itemDescriptionArea.setWrapStyleWord(true);
        imageSelector = ProductImageHelper.createImageSelector(this);
        productTypeBox = new JComboBox<>(new String[]{"Inventory", "Service", "Non Inventory"});
        pricingTypeBox = new JComboBox<>(new String[]{"Variable", "Fixed", "Area"});
        priceField = new JTextField();
        areaPriceUnitBox = new JComboBox<>(new String[]{"Square Feet", "Square Inches", "Square Yards", "Square Meters", "Square Centimeters"});
        dimensionUnitBox = new JComboBox<>(new String[]{"Inches", "Feet", "Yards", "Meters", "Centimeters"});
        maxWidthField = new JTextField();
        maxLengthField = new JTextField();
        hasVariantsCheckBox = new JCheckBox("Track Sizes / Variants");
        quantityField = new JTextField("0");
        reorderLevelField = new JTextField("0");
        activeCheckBox = new JCheckBox("Active", true);
        itemNameField.getDocument().addDocumentListener(simpleDocumentListener(this::updateSkuPreview));

        pricingTypeBox.addActionListener(e -> updatePricingFields());
        productTypeBox.addActionListener(e -> updateVariantFields());
        hasVariantsCheckBox.addActionListener(e -> {
            updateVariantFields();
            updatePricingFields();
        });

        addField(form, gbc, 0, "Item:", itemNameField);
        addField(form, gbc, 1, "SKU:", itemSkuPreviewField);
        addField(form, gbc, 2, "Barcode:", barcodeField);
        JScrollPane barcodesScroll = new JScrollPane(barcodesArea);
        barcodesScroll.setPreferredSize(new Dimension(260, 76));
        addField(form, gbc, 3, "More Barcodes:", barcodesScroll);
        gbc.gridx = 1;
        gbc.gridy = 4;
        form.add(hasVariantsCheckBox, gbc);
        addField(form, gbc, 5, "Product Type:", productTypeBox);
        addField(form, gbc, 6, "Pricing:", pricingTypeBox);
        addTrackedField(priceComponents, form, gbc, 7, "Price:", priceField);
        addTrackedField(areaComponents, form, gbc, 8, "Price Unit:", areaPriceUnitBox);
        addTrackedField(areaComponents, form, gbc, 9, "Size Unit:", dimensionUnitBox);
        addTrackedField(areaComponents, form, gbc, 10, "Max Width:", maxWidthField);
        addTrackedField(areaComponents, form, gbc, 11, "Max Length:", maxLengthField);
        addField(form, gbc, 12, "Total Qty:", quantityField);
        addField(form, gbc, 13, "Total Reorder:", reorderLevelField);
        JScrollPane descriptionScroll = new JScrollPane(itemDescriptionArea);
        descriptionScroll.setPreferredSize(new Dimension(260, 90));
        addField(form, gbc, 14, "Description:", descriptionScroll);
        addTrackedField(mainImageComponents, form, gbc, 15, "Image:", imageSelector);
        gbc.gridx = 1;
        gbc.gridy = 16;
        form.add(activeCheckBox, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        saveButton = new JButton("Save Custom Item");
        variantsButton = new JButton("Sizes / Variants");
        JButton clearButton = new JButton("Clear");
        variantsButton.setEnabled(false);
        saveButton.addActionListener(e -> saveCustomItem());
        variantsButton.addActionListener(e -> openVariantsDialog());
        clearButton.addActionListener(e -> clearForm());
        buttonPanel.add(saveButton);
        buttonPanel.add(variantsButton);
        buttonPanel.add(clearButton);

        JScrollPane scrollPane = new JScrollPane(form);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        updatePricingFields();
        updateVariantFields();
        updateSkuPreview();
    }

    private void updateSkuPreview() {
        if (itemSkuPreviewField != null) {
            itemSkuPreviewField.setText(CustomOrderSkuGenerator.itemSku(itemNameField == null ? "" : itemNameField.getText()));
        }
    }

    private JLabel addField(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel labelComponent = new JLabel(label);
        panel.add(labelComponent, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
        return labelComponent;
    }

    private void addTrackedField(List<JComponent> trackedComponents, JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        trackedComponents.add(addField(panel, gbc, row, label, field));
        trackedComponents.add(field);
    }

    private javax.swing.event.DocumentListener simpleDocumentListener(Runnable callback) {
        return new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                callback.run();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                callback.run();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                callback.run();
            }
        };
    }

    private void updatePricingFields() {
        boolean hasVariants = hasVariantsCheckBox != null && hasVariantsCheckBox.isSelected();
        boolean area = "AREA".equals(getSelectedPricingType());
        boolean mainImageVisible = !hasVariants;
        boolean priceVisible = !hasVariants && ("FIXED".equals(getSelectedPricingType()) || area);
        setComponentsVisible(priceComponents, priceVisible);
        setComponentsVisible(areaComponents, area);
        setComponentsVisible(mainImageComponents, mainImageVisible);
        priceField.setEnabled(priceVisible);
        imageSelector.setSelectorEnabled(mainImageVisible);
        if (!priceVisible) {
            priceField.setText("");
        }
        if (!mainImageVisible) {
            imageSelector.setImageUrl("");
        }
        if (!area) {
            maxWidthField.setText("");
            maxLengthField.setText("");
        }
        revalidate();
        repaint();
    }

    private void updateVariantFields() {
        boolean hasVariants = hasVariantsCheckBox != null && hasVariantsCheckBox.isSelected();
        boolean inventory = "INVENTORY".equals(getSelectedProductType());
        quantityField.setEditable(!hasVariants && inventory);
        reorderLevelField.setEditable(!hasVariants && inventory);
        if ((hasVariants || !inventory) && savedCustomItemId == null) {
            quantityField.setText("0");
            reorderLevelField.setText("0");
        }
    }

    private void setComponentsVisible(List<JComponent> components, boolean visible) {
        for (JComponent component : components) {
            component.setVisible(visible);
        }
    }

    private void saveCustomItem() {
        String name = itemNameField.getText().trim();
        String barcode = barcodeField.getText().trim();
        String description = itemDescriptionArea.getText().trim();
        String pricingType = getSelectedPricingType();
        boolean hasVariants = hasVariantsCheckBox.isSelected();
        String productType = getSelectedProductType();
        String imageUrl = "";
        if (!hasVariants) {
            try {
                imageUrl = ProductImageHelper.uploadLocalImageIfNeeded(imageSelector.getImageUrl());
                imageSelector.setImageUrl(imageUrl);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Image upload failed: " + ex.getMessage());
                return;
            }
        }
        BigDecimal fixedPrice = null;
        BigDecimal maxWidth = null;
        BigDecimal maxLength = null;
        BigDecimal quantity = (hasVariants || !"INVENTORY".equals(productType)) ? BigDecimal.ZERO : parseDecimal(quantityField.getText().trim(), "Quantity");
        BigDecimal reorderLevel = (hasVariants || !"INVENTORY".equals(productType)) ? BigDecimal.ZERO : parseDecimal(reorderLevelField.getText().trim(), "Reorder level");
        if (quantity == null || reorderLevel == null) {
            return;
        }
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Item name is required.");
            return;
        }
        if (!hasVariants && ("FIXED".equals(pricingType) || "AREA".equals(pricingType))) {
            fixedPrice = parseDecimal(priceField.getText().trim(), "Price");
            if (fixedPrice == null) {
                return;
            }
        }
        if ("AREA".equals(pricingType)) {
            maxWidth = parseOptionalDecimal(maxWidthField.getText().trim(), "Max width");
            maxLength = parseOptionalDecimal(maxLengthField.getText().trim(), "Max length");
            if (maxWidth == null || maxLength == null) {
                return;
            }
            if (maxWidth.compareTo(BigDecimal.ZERO) <= 0 || maxLength.compareTo(BigDecimal.ZERO) <= 0) {
                JOptionPane.showMessageDialog(this, "Max width and max length must be greater than zero for area pricing.");
                return;
            }
        }

        Set<String> extraBarcodes = parseExtraBarcodes(barcodesArea.getText(), barcode);
        String insertSql = """
                INSERT INTO custom_order_items (
                    item_name, barcode, description, image_url, product_type, pricing_type, fixed_price,
                    area_price, area_price_unit, dimension_unit, max_width, max_length,
                    has_variants, quantity_on_hand, reorder_level, is_active
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String insertBarcodeSql = "INSERT INTO custom_order_item_barcodes (custom_item_id, barcode) VALUES (?, ?)";
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS);
                 PreparedStatement barcodePs = conn.prepareStatement(insertBarcodeSql)) {
                ps.setString(1, name);
                ps.setString(2, barcode.isEmpty() ? null : barcode);
                ps.setString(3, description.isEmpty() ? null : description);
                ps.setString(4, imageUrl.isBlank() ? null : imageUrl);
                ps.setString(5, productType);
                ps.setString(6, pricingType);
                setNullableBigDecimal(ps, 7, fixedPrice);
                setNullableBigDecimal(ps, 8, "AREA".equals(pricingType) ? fixedPrice : null);
                ps.setString(9, "AREA".equals(pricingType) ? getSelectedAreaPriceUnit() : null);
                ps.setString(10, "AREA".equals(pricingType) ? getSelectedDimensionUnit() : null);
                setNullableBigDecimal(ps, 11, maxWidth);
                setNullableBigDecimal(ps, 12, maxLength);
                ps.setBoolean(13, hasVariants);
                ps.setBigDecimal(14, quantity);
                ps.setBigDecimal(15, reorderLevel);
                ps.setBoolean(16, activeCheckBox.isSelected());
                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new SQLException("Failed to get custom item ID.");
                    }
                    savedCustomItemId = rs.getLong(1);
                }
                for (String extraBarcode : extraBarcodes) {
                    barcodePs.setLong(1, savedCustomItemId);
                    barcodePs.setString(2, extraBarcode);
                    barcodePs.addBatch();
                }
                if (!extraBarcodes.isEmpty()) {
                    barcodePs.executeBatch();
                }
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
            variantsButton.setEnabled(hasVariants);
            if (hasVariants) {
                openVariantsDialog(true);
            } else {
                JOptionPane.showMessageDialog(this, "Custom item saved.");
                clearForm();
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save custom item: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openVariantsDialog() {
        openVariantsDialog(false);
    }

    private void openVariantsDialog(boolean clearAfterFinish) {
        if (savedCustomItemId == null) {
            JOptionPane.showMessageDialog(this, "Save the custom item before adding sizes or variants.");
            return;
        }
        JDialog dialog = new JDialog(parentWindow, "Sizes / Variants - " + itemNameField.getText().trim(), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(860, 500);
        dialog.setLocationRelativeTo(parentWindow);
        dialog.setLayout(new BorderLayout(10, 10));

        DefaultTableModel variantModel = new DefaultTableModel(
                new Object[]{"ID", "Size / Variant", "SKU", "Barcode", "Price", "Qty", "Reorder At", "Stock", "Active", "Image"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable variantTable = new JTable(variantModel);
        variantTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        variantTable.setRowHeight(26);
        variantTable.getColumnModel().getColumn(0).setPreferredWidth(55);
        variantTable.getColumnModel().getColumn(1).setPreferredWidth(145);
        variantTable.getColumnModel().getColumn(2).setPreferredWidth(115);
        variantTable.getColumnModel().getColumn(2).setPreferredWidth(115);
        variantTable.getColumnModel().getColumn(3).setPreferredWidth(115);
        variantTable.getColumnModel().getColumn(4).setPreferredWidth(80);
        variantTable.getColumnModel().getColumn(5).setPreferredWidth(70);
        variantTable.getColumnModel().getColumn(6).setPreferredWidth(95);
        variantTable.getColumnModel().getColumn(7).setPreferredWidth(75);
        variantTable.getColumnModel().getColumn(8).setPreferredWidth(65);
        variantTable.getColumnModel().getColumn(9).setMinWidth(0);
        variantTable.getColumnModel().getColumn(9).setMaxWidth(0);
        variantTable.getColumnModel().getColumn(9).setPreferredWidth(0);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Variant Details"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        JTextField variantNameField = new JTextField();
        JTextField variantSkuPreviewField = new JTextField();
        variantSkuPreviewField.setEditable(false);
        JTextField variantBarcodeField = new JTextField();
        JTextField variantPriceField = new JTextField();
        ProductImageHelper.ImageSelector variantImageSelector = ProductImageHelper.createImageSelector(dialog);
        JTextField variantQtyField = new JTextField("0");
        JTextField variantReorderField = new JTextField("0");
        JCheckBox variantActiveCheckBox = new JCheckBox("Active", true);
        final Long[] selectedVariantId = new Long[1];

        variantNameField.getDocument().addDocumentListener(simpleDocumentListener(() ->
                variantSkuPreviewField.setText(CustomOrderSkuGenerator.variantSku(itemNameField.getText(), variantNameField.getText()))));

        addField(form, gbc, 0, "Size / Variant:", variantNameField);
        addField(form, gbc, 1, "SKU:", variantSkuPreviewField);
        addField(form, gbc, 2, "Barcode:", variantBarcodeField);
        addField(form, gbc, 3, "Price:", variantPriceField);
        addField(form, gbc, 4, "Image:", variantImageSelector);
        addField(form, gbc, 5, "Quantity:", variantQtyField);
        addField(form, gbc, 6, "Reorder At:", variantReorderField);
        gbc.gridx = 1;
        gbc.gridy = 7;
        form.add(variantActiveCheckBox, gbc);

        variantTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || variantTable.getSelectedRow() < 0) {
                return;
            }
            int modelRow = variantTable.convertRowIndexToModel(variantTable.getSelectedRow());
            selectedVariantId[0] = Long.parseLong(valueAt(variantModel, modelRow, 0));
            variantNameField.setText(valueAt(variantModel, modelRow, 1));
            variantSkuPreviewField.setText(valueAt(variantModel, modelRow, 2));
            variantBarcodeField.setText(valueAt(variantModel, modelRow, 3));
            variantPriceField.setText(valueAt(variantModel, modelRow, 4));
            variantImageSelector.setImageUrl(valueAt(variantModel, modelRow, 9));
            variantQtyField.setText(valueAt(variantModel, modelRow, 5));
            variantReorderField.setText(valueAt(variantModel, modelRow, 6));
            variantActiveCheckBox.setSelected(Boolean.parseBoolean(valueAt(variantModel, modelRow, 8)));
        });

        JButton saveVariantButton = new JButton("Save Variant");
        JButton updateVariantButton = new JButton("Update Variant");
        JButton clearVariantButton = new JButton("Clear");
        JButton finishedButton = new JButton("Finished");
        styleDialogButton(saveVariantButton);
        styleDialogButton(updateVariantButton);
        styleDialogButton(clearVariantButton);
        styleDialogButton(finishedButton);

        saveVariantButton.addActionListener(e -> {
            if (saveVariant(false, selectedVariantId[0], variantNameField, variantBarcodeField, variantPriceField, variantImageSelector, variantQtyField, variantReorderField, variantActiveCheckBox)) {
                clearVariantForm(variantTable, selectedVariantId, variantNameField, variantBarcodeField, variantPriceField, variantImageSelector, variantQtyField, variantReorderField, variantActiveCheckBox);
                loadVariants(variantModel);
            }
        });
        updateVariantButton.addActionListener(e -> {
            if (selectedVariantId[0] == null) {
                JOptionPane.showMessageDialog(dialog, "Select a variant to update.");
                return;
            }
            if (saveVariant(true, selectedVariantId[0], variantNameField, variantBarcodeField, variantPriceField, variantImageSelector, variantQtyField, variantReorderField, variantActiveCheckBox)) {
                clearVariantForm(variantTable, selectedVariantId, variantNameField, variantBarcodeField, variantPriceField, variantImageSelector, variantQtyField, variantReorderField, variantActiveCheckBox);
                loadVariants(variantModel);
            }
        });
        clearVariantButton.addActionListener(e -> clearVariantForm(variantTable, selectedVariantId, variantNameField, variantBarcodeField, variantPriceField, variantImageSelector, variantQtyField, variantReorderField, variantActiveCheckBox));
        finishedButton.addActionListener(e -> dialog.dispose());

        JPanel buttons = new JPanel(new GridLayout(2, 2, 8, 8));
        buttons.add(saveVariantButton);
        buttons.add(updateVariantButton);
        buttons.add(clearVariantButton);
        buttons.add(finishedButton);

        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.setPreferredSize(new Dimension(320, 0));
        right.add(form, BorderLayout.NORTH);
        right.add(buttons, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(new JScrollPane(variantTable), BorderLayout.CENTER);
        content.add(right, BorderLayout.EAST);
        dialog.add(content, BorderLayout.CENTER);

        loadVariants(variantModel);
        dialog.setVisible(true);
        if (clearAfterFinish) {
            clearForm();
        }
    }

    private void styleDialogButton(JButton button) {
        button.setOpaque(true);
        button.setBackground(new Color(245, 245, 245));
        button.setForeground(new Color(25, 25, 25));
        button.setFocusPainted(false);
    }

    private void loadVariants(DefaultTableModel variantModel) {
        variantModel.setRowCount(0);
        String sql = """
                SELECT custom_variant_id, variant_name, sku, barcode, fixed_price, quantity_on_hand, reorder_level, is_active, image_url,
                       CASE
                           WHEN is_active AND reorder_level > 0 AND quantity_on_hand <= reorder_level THEN 'Low'
                           ELSE 'OK'
                       END AS stock_status
                FROM custom_order_item_variants
                WHERE custom_item_id = ?
                ORDER BY is_active DESC, variant_name
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, savedCustomItemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    variantModel.addRow(new Object[]{
                            rs.getLong("custom_variant_id"),
                            rs.getString("variant_name"),
                            rs.getString("sku"),
                            rs.getString("barcode"),
                            formatMoney(rs.getBigDecimal("fixed_price")),
                            rs.getBigDecimal("quantity_on_hand"),
                            rs.getBigDecimal("reorder_level"),
                            rs.getString("stock_status"),
                            rs.getBoolean("is_active"),
                            rs.getString("image_url")
                    });
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load variants: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean saveVariant(boolean update, Long variantId, JTextField nameField, JTextField barcodeField, JTextField priceField, ProductImageHelper.ImageSelector imageSelector, JTextField qtyField, JTextField reorderField, JCheckBox activeCheckBox) {
        String name = nameField.getText().trim();
        String barcode = barcodeField.getText().trim();
        String imageUrl;
        try {
            imageUrl = ProductImageHelper.uploadLocalImageIfNeeded(imageSelector.getImageUrl());
            imageSelector.setImageUrl(imageUrl);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Variant image upload failed: " + ex.getMessage());
            return false;
        }
        String pricingType = getSelectedPricingType();
        BigDecimal fixedPrice = null;
        if ("FIXED".equals(pricingType) || "AREA".equals(pricingType)) {
            fixedPrice = parseDecimal(priceField.getText().trim(), "Price");
            if (fixedPrice == null) {
                return false;
            }
        }
        BigDecimal quantity = parseDecimal(qtyField.getText().trim(), "Quantity");
        BigDecimal reorderLevel = parseDecimal(reorderField.getText().trim(), "Reorder level");
        if (!"INVENTORY".equals(getSelectedProductType())) {
            quantity = BigDecimal.ZERO;
            reorderLevel = BigDecimal.ZERO;
        }
        if (quantity == null || reorderLevel == null) {
            return false;
        }
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Size / variant name is required.");
            return false;
        }

        String insertSql = """
                INSERT INTO custom_order_item_variants (
                    custom_item_id, variant_name, barcode, image_url, fixed_price, quantity_on_hand, reorder_level, is_active
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String updateSql = """
                UPDATE custom_order_item_variants
                SET variant_name = ?, barcode = ?, image_url = ?, fixed_price = ?, quantity_on_hand = ?,
                    reorder_level = ?, is_active = ?, updated_at = CURRENT_TIMESTAMP
                WHERE custom_variant_id = ?
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(update ? updateSql : insertSql)) {
            if (update) {
                ps.setString(1, name);
                ps.setString(2, barcode.isEmpty() ? null : barcode);
                ps.setString(3, imageUrl.isBlank() ? null : imageUrl);
                setNullableBigDecimal(ps, 4, fixedPrice);
                ps.setBigDecimal(5, quantity);
                ps.setBigDecimal(6, reorderLevel);
                ps.setBoolean(7, activeCheckBox.isSelected());
                ps.setLong(8, variantId);
            } else {
                ps.setLong(1, savedCustomItemId);
                ps.setString(2, name);
                ps.setString(3, barcode.isEmpty() ? null : barcode);
                ps.setString(4, imageUrl.isBlank() ? null : imageUrl);
                setNullableBigDecimal(ps, 5, fixedPrice);
                ps.setBigDecimal(6, quantity);
                ps.setBigDecimal(7, reorderLevel);
                ps.setBoolean(8, activeCheckBox.isSelected());
            }
            ps.executeUpdate();
            return true;
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save variant: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void clearVariantForm(JTable table, Long[] selectedVariantId, JTextField nameField, JTextField barcodeField, JTextField priceField, ProductImageHelper.ImageSelector imageSelector, JTextField qtyField, JTextField reorderField, JCheckBox activeCheckBox) {
        selectedVariantId[0] = null;
        table.clearSelection();
        nameField.setText("");
        barcodeField.setText("");
        priceField.setText("");
        imageSelector.setImageUrl("");
        qtyField.setText("0");
        reorderField.setText("0");
        activeCheckBox.setSelected(true);
    }

    private void clearForm() {
        savedCustomItemId = null;
        itemNameField.setText("");
        barcodeField.setText("");
        barcodesArea.setText("");
        itemDescriptionArea.setText("");
        imageSelector.setImageUrl("");
        pricingTypeBox.setSelectedItem("Variable");
        productTypeBox.setSelectedItem("Inventory");
        priceField.setText("");
        maxWidthField.setText("");
        maxLengthField.setText("");
        areaPriceUnitBox.setSelectedItem("Square Feet");
        dimensionUnitBox.setSelectedItem("Inches");
        hasVariantsCheckBox.setSelected(false);
        quantityField.setText("0");
        reorderLevelField.setText("0");
        activeCheckBox.setSelected(true);
        saveButton.setEnabled(true);
        variantsButton.setEnabled(false);
        updatePricingFields();
        updateVariantFields();
        itemNameField.requestFocusInWindow();
    }

    private String getSelectedPricingType() {
        Object selected = pricingTypeBox.getSelectedItem();
        if (selected == null) {
            return "VARIABLE";
        }
        String value = selected.toString();
        if ("Fixed".equalsIgnoreCase(value)) {
            return "FIXED";
        }
        if ("Area".equalsIgnoreCase(value)) {
            return "AREA";
        }
        return "VARIABLE";
    }

    private String getSelectedProductType() {
        Object selected = productTypeBox == null ? null : productTypeBox.getSelectedItem();
        String value = selected == null ? "Inventory" : selected.toString();
        if ("Service".equalsIgnoreCase(value)) {
            return "SERVICE";
        }
        if ("Non Inventory".equalsIgnoreCase(value)) {
            return "NON_INVENTORY";
        }
        return "INVENTORY";
    }

    private String getSelectedAreaPriceUnit() {
        return switch (String.valueOf(areaPriceUnitBox.getSelectedItem())) {
            case "Square Inches" -> "SQ_IN";
            case "Square Yards" -> "SQ_YD";
            case "Square Meters" -> "SQ_M";
            case "Square Centimeters" -> "SQ_CM";
            default -> "SQ_FT";
        };
    }

    private String getSelectedDimensionUnit() {
        return switch (String.valueOf(dimensionUnitBox.getSelectedItem())) {
            case "Feet" -> "FT";
            case "Yards" -> "YD";
            case "Meters" -> "M";
            case "Centimeters" -> "CM";
            default -> "IN";
        };
    }

    private Set<String> parseExtraBarcodes(String value, String primaryBarcode) {
        Set<String> barcodes = new LinkedHashSet<>();
        if (value != null && !value.isBlank()) {
            String[] lines = value.split("\\r?\\n");
            for (String line : lines) {
                String barcode = line.trim();
                if (!barcode.isEmpty()) {
                    barcodes.add(barcode);
                }
            }
        }
        if (primaryBarcode != null && !primaryBarcode.isBlank()) {
            barcodes.remove(primaryBarcode.trim());
        }
        return barcodes;
    }

    private BigDecimal parseDecimal(String value, String fieldName) {
        try {
            BigDecimal amount = new BigDecimal(value.replace("$", "").replace(",", "").trim());
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                JOptionPane.showMessageDialog(this, fieldName + " cannot be negative.");
                return null;
            }
            return amount;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, fieldName + " must be a valid number.");
            return null;
        }
    }

    private BigDecimal parseOptionalDecimal(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseDecimal(value, fieldName);
    }

    private void setNullableBigDecimal(PreparedStatement ps, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.NUMERIC);
        } else {
            ps.setBigDecimal(index, value);
        }
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return utils.CurrencyFormatter.format(amount);
    }

    private String valueAt(DefaultTableModel model, int row, int column) {
        Object value = model.getValueAt(row, column);
        return value == null ? "" : value.toString();
    }
}
