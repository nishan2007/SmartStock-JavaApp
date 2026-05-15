package ui.screens.customorders;

import data.DB;
import ui.helpers.ProductImageHelper;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
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
import java.util.regex.Pattern;

public class EditCustomItem extends JPanel {
    private DefaultTableModel itemModel;
    private JTable itemTable;
    private TableRowSorter<DefaultTableModel> itemSorter;
    private JTextField searchField;
    private JTextField itemNameField;
    private JTextField barcodeField;
    private JTextArea barcodesArea;
    private JTextArea descriptionArea;
    private ProductImageHelper.ImageSelector imageSelector;
    private JComboBox<String> productTypeBox;
    private JCheckBox hasVariantsCheckBox;
    private JComboBox<String> pricingTypeBox;
    private JTextField priceField;
    private JComboBox<String> areaPriceUnitBox;
    private JComboBox<String> dimensionUnitBox;
    private JTextField maxWidthField;
    private JTextField maxLengthField;
    private JTextField quantityField;
    private JTextField reorderLevelField;
    private JCheckBox activeCheckBox;
    private JButton saveButton;

    private DefaultTableModel variantModel;
    private JTable variantTable;
    private JPanel variantPanel;
    private JTextField variantNameField;
    private JTextField variantBarcodeField;
    private JTextField variantPriceField;
    private ProductImageHelper.ImageSelector variantImageSelector;
    private JTextField variantQtyField;
    private JTextField variantReorderField;
    private JCheckBox variantActiveCheckBox;

    private Long selectedCustomItemId;
    private Long selectedVariantId;
    private final List<JComponent> priceComponents = new ArrayList<>();
    private final List<JComponent> areaComponents = new ArrayList<>();
    private final List<JComponent> mainImageComponents = new ArrayList<>();

    public EditCustomItem(Window parentWindow) {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buildUi(parentWindow);
        loadItems();
        setFormEnabled(false);
    }

    private void buildUi(Window parentWindow) {
        add(buildTablePanel(), BorderLayout.WEST);
        add(buildEditorPanel(parentWindow), BorderLayout.CENTER);
    }

    private JPanel buildTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setPreferredSize(new Dimension(430, 0));

        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(0, 34));
        JButton refreshButton = new JButton("Refresh");
        refreshButton.setPreferredSize(new Dimension(96, 34));
        refreshButton.addActionListener(e -> loadItems());
        searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(refreshButton, BorderLayout.EAST);

        itemModel = new DefaultTableModel(new Object[]{"ID", "Item", "Pricing", "Variants", "Qty", "Stock", "Active"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        itemSorter = new TableRowSorter<>(itemModel);
        itemTable = new JTable(itemModel);
        itemTable.setRowSorter(itemSorter);
        itemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        itemTable.setRowHeight(26);
        itemTable.getColumnModel().getColumn(0).setMaxWidth(60);
        itemTable.getColumnModel().getColumn(3).setMaxWidth(75);
        itemTable.getColumnModel().getColumn(6).setMaxWidth(65);
        itemTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedItem();
            }
        });

        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }
        });

        panel.add(searchPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(itemTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildEditorPanel(Window parentWindow) {
        JPanel editor = new JPanel(new BorderLayout(10, 10));
        editor.add(buildItemForm(parentWindow), BorderLayout.NORTH);
        editor.add(buildVariantPanel(parentWindow), BorderLayout.CENTER);
        return editor;
    }

    private JPanel buildItemForm(Window parentWindow) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 10));
        wrapper.setBorder(BorderFactory.createTitledBorder("Custom Item"));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = formGbc();
        itemNameField = new JTextField();
        barcodeField = new JTextField();
        barcodesArea = new JTextArea(2, 20);
        barcodesArea.setLineWrap(true);
        barcodesArea.setWrapStyleWord(true);
        descriptionArea = new JTextArea(3, 20);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        imageSelector = ProductImageHelper.createImageSelector(parentWindow);
        productTypeBox = new JComboBox<>(new String[]{"Inventory", "Service", "Non Inventory"});
        hasVariantsCheckBox = new JCheckBox("Track Sizes / Variants");
        pricingTypeBox = new JComboBox<>(new String[]{"Variable", "Fixed", "Area"});
        priceField = new JTextField();
        areaPriceUnitBox = new JComboBox<>(new String[]{"Square Feet", "Square Inches", "Square Yards", "Square Meters", "Square Centimeters"});
        dimensionUnitBox = new JComboBox<>(new String[]{"Inches", "Feet", "Yards", "Meters", "Centimeters"});
        maxWidthField = new JTextField();
        maxLengthField = new JTextField();
        quantityField = new JTextField("0");
        reorderLevelField = new JTextField("0");
        activeCheckBox = new JCheckBox("Active", true);

        hasVariantsCheckBox.addActionListener(e -> {
            updatePricingFields();
            updateVariantPanelVisibility();
        });
        pricingTypeBox.addActionListener(e -> updatePricingFields());
        productTypeBox.addActionListener(e -> updatePricingFields());

        addField(form, gbc, 0, "Item:", itemNameField);
        addField(form, gbc, 1, "Barcode:", barcodeField);
        JScrollPane barcodeScroll = new JScrollPane(barcodesArea);
        barcodeScroll.setPreferredSize(new Dimension(220, 64));
        addField(form, gbc, 2, "More Barcodes:", barcodeScroll);
        gbc.gridx = 1;
        gbc.gridy = 3;
        form.add(hasVariantsCheckBox, gbc);
        addField(form, gbc, 4, "Product Type:", productTypeBox);
        addField(form, gbc, 5, "Pricing:", pricingTypeBox);
        addTrackedField(priceComponents, form, gbc, 6, "Price:", priceField);
        addTrackedField(areaComponents, form, gbc, 7, "Price Unit:", areaPriceUnitBox);
        addTrackedField(areaComponents, form, gbc, 8, "Size Unit:", dimensionUnitBox);
        addTrackedField(areaComponents, form, gbc, 9, "Max Width:", maxWidthField);
        addTrackedField(areaComponents, form, gbc, 10, "Max Length:", maxLengthField);
        addField(form, gbc, 11, "Total Qty:", quantityField);
        addField(form, gbc, 12, "Total Reorder:", reorderLevelField);
        JScrollPane descScroll = new JScrollPane(descriptionArea);
        descScroll.setPreferredSize(new Dimension(220, 80));
        addField(form, gbc, 13, "Description:", descScroll);
        addTrackedField(mainImageComponents, form, gbc, 14, "Image:", imageSelector);
        gbc.gridx = 1;
        gbc.gridy = 15;
        form.add(activeCheckBox, gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        saveButton = new JButton("Save Changes");
        JButton clearButton = new JButton("Clear");
        saveButton.addActionListener(e -> saveCustomItem());
        clearButton.addActionListener(e -> clearSelection());
        buttons.add(saveButton);
        buttons.add(clearButton);

        wrapper.add(form, BorderLayout.CENTER);
        wrapper.add(buttons, BorderLayout.SOUTH);
        return wrapper;
    }

    private JPanel buildVariantPanel(Window parentWindow) {
        variantPanel = new JPanel(new BorderLayout(10, 10));
        variantPanel.setBorder(BorderFactory.createTitledBorder("Variants"));

        variantModel = new DefaultTableModel(new Object[]{"ID", "Variant", "Barcode", "Price", "Qty", "Reorder", "Stock", "Active", "Image"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        variantTable = new JTable(variantModel);
        variantTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        variantTable.setRowHeight(26);
        hideColumn(variantTable, 8);
        variantTable.getColumnModel().getColumn(0).setMaxWidth(60);
        variantTable.getColumnModel().getColumn(7).setMaxWidth(70);
        variantTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedVariant();
            }
        });

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Variant Details"));
        GridBagConstraints gbc = formGbc();
        variantNameField = new JTextField();
        variantBarcodeField = new JTextField();
        variantPriceField = new JTextField();
        variantImageSelector = ProductImageHelper.createImageSelector(parentWindow);
        variantQtyField = new JTextField("0");
        variantReorderField = new JTextField("0");
        variantActiveCheckBox = new JCheckBox("Active", true);

        addField(form, gbc, 0, "Size / Variant:", variantNameField);
        addField(form, gbc, 1, "Barcode:", variantBarcodeField);
        addField(form, gbc, 2, "Price:", variantPriceField);
        addField(form, gbc, 3, "Image:", variantImageSelector);
        addField(form, gbc, 4, "Quantity:", variantQtyField);
        addField(form, gbc, 5, "Reorder At:", variantReorderField);
        gbc.gridx = 1;
        gbc.gridy = 6;
        form.add(variantActiveCheckBox, gbc);

        JPanel buttons = new JPanel(new GridLayout(2, 2, 8, 8));
        JButton saveVariantButton = new JButton("Save Variant");
        JButton updateVariantButton = new JButton("Update Variant");
        JButton deleteVariantButton = new JButton("Delete Variant");
        JButton clearVariantButton = new JButton("Clear");
        saveVariantButton.addActionListener(e -> {
            if (saveVariant(false)) {
                clearVariantForm();
                loadVariants();
                loadItems();
                selectItemById(selectedCustomItemId);
            }
        });
        updateVariantButton.addActionListener(e -> {
            if (selectedVariantId == null) {
                JOptionPane.showMessageDialog(this, "Select a variant to update.");
                return;
            }
            if (saveVariant(true)) {
                clearVariantForm();
                loadVariants();
                loadItems();
                selectItemById(selectedCustomItemId);
            }
        });
        deleteVariantButton.addActionListener(e -> deleteSelectedVariant());
        clearVariantButton.addActionListener(e -> clearVariantForm());
        buttons.add(saveVariantButton);
        buttons.add(updateVariantButton);
        buttons.add(deleteVariantButton);
        buttons.add(clearVariantButton);

        JScrollPane formScrollPane = new JScrollPane(form);
        formScrollPane.setBorder(null);
        formScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        formScrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.setPreferredSize(new Dimension(390, 0));
        right.add(formScrollPane, BorderLayout.CENTER);
        right.add(buttons, BorderLayout.SOUTH);

        variantPanel.add(new JScrollPane(variantTable), BorderLayout.CENTER);
        variantPanel.add(right, BorderLayout.EAST);
        return variantPanel;
    }

    private GridBagConstraints formGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
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

    private void addTrackedField(List<JComponent> components, JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        components.add(addField(panel, gbc, row, label, field));
        components.add(field);
    }

    private void loadItems() {
        itemModel.setRowCount(0);
        String sql = """
                SELECT custom_item_id, item_name, pricing_type, has_variants,
                       quantity_on_hand, reorder_level, is_active,
                       CASE WHEN is_active AND reorder_level > 0 AND quantity_on_hand <= reorder_level THEN 'Low' ELSE 'OK' END AS stock_status
                FROM custom_order_items
                ORDER BY is_active DESC, item_name
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                itemModel.addRow(new Object[]{
                        rs.getLong("custom_item_id"),
                        rs.getString("item_name"),
                        rs.getString("pricing_type"),
                        rs.getBoolean("has_variants") ? "Yes" : "No",
                        rs.getBigDecimal("quantity_on_hand"),
                        rs.getString("stock_status"),
                        rs.getBoolean("is_active")
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load custom items: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
        applyFilter();
    }

    private void applyFilter() {
        if (itemSorter == null) {
            return;
        }
        String search = searchField == null ? "" : searchField.getText().trim();
        itemSorter.setRowFilter(search.isEmpty() ? null : RowFilter.regexFilter("(?i)" + Pattern.quote(search)));
    }

    private void loadSelectedItem() {
        int row = itemTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = itemTable.convertRowIndexToModel(row);
        selectedCustomItemId = Long.parseLong(valueAt(itemModel, modelRow, 0));

        String sql = """
                SELECT custom_item_id, item_name, barcode, description, image_url, pricing_type,
                       fixed_price, area_price, area_price_unit, dimension_unit, max_width, max_length,
                       product_type, has_variants, quantity_on_hand, reorder_level, is_active
                FROM custom_order_items
                WHERE custom_item_id = ?
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, selectedCustomItemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return;
                }
                itemNameField.setText(rs.getString("item_name"));
                barcodeField.setText(nullToBlank(rs.getString("barcode")));
                descriptionArea.setText(nullToBlank(rs.getString("description")));
                pricingTypeBox.setSelectedItem(toPricingDisplay(rs.getString("pricing_type")));
                imageSelector.setImageUrl(rs.getBoolean("has_variants") ? "" : nullToBlank(rs.getString("image_url")));
                selectProductType(rs.getString("product_type"));
                BigDecimal price = rs.getBigDecimal("fixed_price");
                if (price == null) {
                    price = rs.getBigDecimal("area_price");
                }
                priceField.setText(price == null ? "" : price.toPlainString());
                selectAreaUnit(rs.getString("area_price_unit"));
                selectDimensionUnit(rs.getString("dimension_unit"));
                maxWidthField.setText(rs.getBigDecimal("max_width") == null ? "" : rs.getBigDecimal("max_width").toPlainString());
                maxLengthField.setText(rs.getBigDecimal("max_length") == null ? "" : rs.getBigDecimal("max_length").toPlainString());
                hasVariantsCheckBox.setSelected(rs.getBoolean("has_variants"));
                quantityField.setText(String.valueOf(rs.getBigDecimal("quantity_on_hand")));
                reorderLevelField.setText(String.valueOf(rs.getBigDecimal("reorder_level")));
                activeCheckBox.setSelected(rs.getBoolean("is_active"));
            }
            barcodesArea.setText(loadExtraBarcodes(selectedCustomItemId));
            setFormEnabled(true);
            updatePricingFields();
            updateVariantPanelVisibility();
            loadVariants();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load custom item: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveCustomItem() {
        if (selectedCustomItemId == null) {
            JOptionPane.showMessageDialog(this, "Select a custom item first.");
            return;
        }
        String pricingType = getSelectedPricingType();
        boolean hasVariants = hasVariantsCheckBox.isSelected();
        String productType = getSelectedProductType();
        BigDecimal fixedPrice = null;
        BigDecimal maxWidth = null;
        BigDecimal maxLength = null;
        BigDecimal quantity = (hasVariants || !"INVENTORY".equals(productType)) ? BigDecimal.ZERO : parseDecimal(quantityField.getText().trim(), "Quantity");
        BigDecimal reorderLevel = (hasVariants || !"INVENTORY".equals(productType)) ? BigDecimal.ZERO : parseDecimal(reorderLevelField.getText().trim(), "Reorder level");
        if (quantity == null || reorderLevel == null) {
            return;
        }
        if (itemNameField.getText().trim().isEmpty()) {
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
        }
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

        String updateSql = """
                UPDATE custom_order_items
                SET item_name = ?, barcode = ?, description = ?, image_url = ?, product_type = ?, pricing_type = ?,
                    fixed_price = ?, area_price = ?, area_price_unit = ?, dimension_unit = ?,
                    max_width = ?, max_length = ?, has_variants = ?, quantity_on_hand = ?,
                    reorder_level = ?, is_active = ?, updated_at = CURRENT_TIMESTAMP
                WHERE custom_item_id = ?
                """;
        String deleteBarcodesSql = "DELETE FROM custom_order_item_barcodes WHERE custom_item_id = ?";
        String insertBarcodeSql = "INSERT INTO custom_order_item_barcodes (custom_item_id, barcode) VALUES (?, ?)";
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(updateSql);
                 PreparedStatement deletePs = conn.prepareStatement(deleteBarcodesSql);
                 PreparedStatement insertPs = conn.prepareStatement(insertBarcodeSql)) {
                ps.setString(1, itemNameField.getText().trim());
                ps.setString(2, blankToNull(barcodeField.getText()));
                ps.setString(3, blankToNull(descriptionArea.getText()));
                ps.setString(4, blankToNull(imageUrl));
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
                ps.setLong(17, selectedCustomItemId);
                ps.executeUpdate();

                deletePs.setLong(1, selectedCustomItemId);
                deletePs.executeUpdate();
                for (String extraBarcode : parseExtraBarcodes(barcodesArea.getText(), barcodeField.getText())) {
                    insertPs.setLong(1, selectedCustomItemId);
                    insertPs.setString(2, extraBarcode);
                    insertPs.addBatch();
                }
                insertPs.executeBatch();
                if (hasVariants) {
                    refreshVariantTrackedTotals(conn, selectedCustomItemId);
                }
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
            loadItems();
            selectItemById(selectedCustomItemId);
            JOptionPane.showMessageDialog(this, "Custom item updated.");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to update custom item: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadVariants() {
        variantModel.setRowCount(0);
        clearVariantForm();
        if (selectedCustomItemId == null) {
            return;
        }
        String sql = """
                SELECT custom_variant_id, variant_name, barcode, image_url, fixed_price,
                       quantity_on_hand, reorder_level, is_active,
                       CASE WHEN is_active AND reorder_level > 0 AND quantity_on_hand <= reorder_level THEN 'Low' ELSE 'OK' END AS stock_status
                FROM custom_order_item_variants
                WHERE custom_item_id = ?
                ORDER BY is_active DESC, variant_name
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, selectedCustomItemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    variantModel.addRow(new Object[]{
                            rs.getLong("custom_variant_id"),
                            rs.getString("variant_name"),
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

    private void loadSelectedVariant() {
        int row = variantTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = variantTable.convertRowIndexToModel(row);
        selectedVariantId = Long.parseLong(valueAt(variantModel, modelRow, 0));
        variantNameField.setText(valueAt(variantModel, modelRow, 1));
        variantBarcodeField.setText(valueAt(variantModel, modelRow, 2));
        variantPriceField.setText(valueAt(variantModel, modelRow, 3));
        variantQtyField.setText(valueAt(variantModel, modelRow, 4));
        variantReorderField.setText(valueAt(variantModel, modelRow, 5));
        variantActiveCheckBox.setSelected(Boolean.parseBoolean(valueAt(variantModel, modelRow, 7)));
        variantImageSelector.setImageUrl(valueAt(variantModel, modelRow, 8));
    }

    private boolean saveVariant(boolean update) {
        if (selectedCustomItemId == null) {
            JOptionPane.showMessageDialog(this, "Select a custom item first.");
            return false;
        }
        if (update && selectedVariantId == null) {
            JOptionPane.showMessageDialog(this, "Select a variant to update.");
            return false;
        }
        String name = variantNameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Size / variant name is required.");
            return false;
        }
        BigDecimal fixedPrice = null;
        if ("FIXED".equals(getSelectedPricingType()) || "AREA".equals(getSelectedPricingType())) {
            fixedPrice = parseDecimal(variantPriceField.getText().trim(), "Price");
            if (fixedPrice == null) {
                return false;
            }
        }
        BigDecimal quantity = parseDecimal(variantQtyField.getText().trim(), "Quantity");
        BigDecimal reorder = parseDecimal(variantReorderField.getText().trim(), "Reorder level");
        if (quantity == null || reorder == null) {
            return false;
        }
        String imageUrl;
        try {
            imageUrl = ProductImageHelper.uploadLocalImageIfNeeded(variantImageSelector.getImageUrl());
            variantImageSelector.setImageUrl(imageUrl);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Variant image upload failed: " + ex.getMessage());
            return false;
        }

        String insertSql = """
                INSERT INTO custom_order_item_variants
                    (custom_item_id, variant_name, barcode, image_url, fixed_price, quantity_on_hand, reorder_level, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String updateSql = """
                UPDATE custom_order_item_variants
                SET variant_name = ?, barcode = ?, image_url = ?, fixed_price = ?,
                    quantity_on_hand = ?, reorder_level = ?, is_active = ?, updated_at = CURRENT_TIMESTAMP
                WHERE custom_variant_id = ?
                """;
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(update ? updateSql : insertSql)) {
                if (update) {
                    ps.setString(1, name);
                    ps.setString(2, blankToNull(variantBarcodeField.getText()));
                    ps.setString(3, blankToNull(imageUrl));
                    setNullableBigDecimal(ps, 4, fixedPrice);
                    ps.setBigDecimal(5, quantity);
                    ps.setBigDecimal(6, reorder);
                    ps.setBoolean(7, variantActiveCheckBox.isSelected());
                    ps.setLong(8, selectedVariantId);
                } else {
                    ps.setLong(1, selectedCustomItemId);
                    ps.setString(2, name);
                    ps.setString(3, blankToNull(variantBarcodeField.getText()));
                    ps.setString(4, blankToNull(imageUrl));
                    setNullableBigDecimal(ps, 5, fixedPrice);
                    ps.setBigDecimal(6, quantity);
                    ps.setBigDecimal(7, reorder);
                    ps.setBoolean(8, variantActiveCheckBox.isSelected());
                }
                ps.executeUpdate();
                refreshVariantTrackedTotals(conn, selectedCustomItemId);
                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save variant: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void deleteSelectedVariant() {
        if (selectedVariantId == null) {
            JOptionPane.showMessageDialog(this, "Select a variant to delete.");
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this, "Delete this variant?", "Delete Variant", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM custom_order_item_variants WHERE custom_variant_id = ?")) {
                ps.setLong(1, selectedVariantId);
                ps.executeUpdate();
                refreshVariantTrackedTotals(conn, selectedCustomItemId);
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
            clearVariantForm();
            loadVariants();
            loadItems();
            selectItemById(selectedCustomItemId);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to delete variant: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearSelection() {
        selectedCustomItemId = null;
        itemTable.clearSelection();
        itemNameField.setText("");
        barcodeField.setText("");
        barcodesArea.setText("");
        descriptionArea.setText("");
        imageSelector.setImageUrl("");
        hasVariantsCheckBox.setSelected(false);
        productTypeBox.setSelectedItem("Inventory");
        pricingTypeBox.setSelectedItem("Variable");
        priceField.setText("");
        maxWidthField.setText("");
        maxLengthField.setText("");
        quantityField.setText("0");
        reorderLevelField.setText("0");
        activeCheckBox.setSelected(true);
        variantModel.setRowCount(0);
        clearVariantForm();
        setFormEnabled(false);
    }

    private void clearVariantForm() {
        selectedVariantId = null;
        if (variantTable != null) {
            variantTable.clearSelection();
        }
        variantNameField.setText("");
        variantBarcodeField.setText("");
        variantPriceField.setText("");
        variantImageSelector.setImageUrl("");
        variantQtyField.setText("0");
        variantReorderField.setText("0");
        variantActiveCheckBox.setSelected(true);
    }

    private void setFormEnabled(boolean enabled) {
        itemNameField.setEnabled(enabled);
        barcodeField.setEnabled(enabled);
        barcodesArea.setEnabled(enabled);
        descriptionArea.setEnabled(enabled);
        imageSelector.setSelectorEnabled(enabled);
        hasVariantsCheckBox.setEnabled(enabled);
        pricingTypeBox.setEnabled(enabled);
        productTypeBox.setEnabled(enabled);
        activeCheckBox.setEnabled(enabled);
        saveButton.setEnabled(enabled);
        updatePricingFields();
        updateVariantPanelVisibility();
    }

    private void updatePricingFields() {
        boolean enabled = selectedCustomItemId != null;
        boolean hasVariants = hasVariantsCheckBox != null && hasVariantsCheckBox.isSelected();
        boolean inventory = "INVENTORY".equals(getSelectedProductType());
        boolean area = "AREA".equals(getSelectedPricingType());
        boolean mainImageVisible = !hasVariants;
        boolean priceVisible = !hasVariants && ("FIXED".equals(getSelectedPricingType()) || area);
        setComponentsVisible(priceComponents, priceVisible);
        setComponentsVisible(areaComponents, area);
        setComponentsVisible(mainImageComponents, mainImageVisible);
        priceField.setEnabled(enabled && priceVisible);
        imageSelector.setSelectorEnabled(enabled && mainImageVisible);
        areaPriceUnitBox.setEnabled(enabled && area);
        dimensionUnitBox.setEnabled(enabled && area);
        maxWidthField.setEnabled(enabled && area);
        maxLengthField.setEnabled(enabled && area);
        quantityField.setEnabled(enabled && !hasVariants && inventory);
        reorderLevelField.setEnabled(enabled && !hasVariants && inventory);
        if (!inventory) {
            quantityField.setText("0");
            reorderLevelField.setText("0");
        }
        if (!priceVisible) {
            priceField.setText("");
        }
        if (!mainImageVisible) {
            imageSelector.setImageUrl("");
        }
        revalidate();
        repaint();
    }

    private void updateVariantPanelVisibility() {
        boolean visible = selectedCustomItemId != null && hasVariantsCheckBox.isSelected();
        variantPanel.setVisible(visible);
        for (Component component : variantPanel.getComponents()) {
            component.setEnabled(visible);
        }
    }

    private void setComponentsVisible(List<JComponent> components, boolean visible) {
        for (JComponent component : components) {
            component.setVisible(visible);
        }
    }

    private void refreshVariantTrackedTotals(Connection conn, long customItemId) throws SQLException {
        String sql = """
                UPDATE custom_order_items coi
                SET quantity_on_hand = COALESCE((SELECT SUM(quantity_on_hand) FROM custom_order_item_variants WHERE custom_item_id = ? AND is_active = TRUE), 0),
                    reorder_level = COALESCE((SELECT SUM(reorder_level) FROM custom_order_item_variants WHERE custom_item_id = ? AND is_active = TRUE), 0),
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_item_id = ?
                  AND has_variants = TRUE
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, customItemId);
            ps.setLong(2, customItemId);
            ps.setLong(3, customItemId);
            ps.executeUpdate();
        }
    }

    private void selectItemById(Long itemId) {
        if (itemId == null) {
            return;
        }
        for (int row = 0; row < itemModel.getRowCount(); row++) {
            Object value = itemModel.getValueAt(row, 0);
            if (value != null && itemId.toString().equals(value.toString())) {
                int viewRow = itemTable.convertRowIndexToView(row);
                if (viewRow >= 0) {
                    itemTable.setRowSelectionInterval(viewRow, viewRow);
                }
                return;
            }
        }
    }

    private String loadExtraBarcodes(Long customItemId) {
        List<String> barcodes = new ArrayList<>();
        String sql = "SELECT barcode FROM custom_order_item_barcodes WHERE custom_item_id = ? ORDER BY barcode";
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, customItemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    barcodes.add(rs.getString("barcode"));
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load barcodes: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
        return String.join("\n", barcodes);
    }

    private Set<String> parseExtraBarcodes(String value, String primaryBarcode) {
        Set<String> barcodes = new LinkedHashSet<>();
        if (value != null && !value.isBlank()) {
            for (String line : value.split("\\r?\\n")) {
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

    private String getSelectedPricingType() {
        Object selected = pricingTypeBox == null ? null : pricingTypeBox.getSelectedItem();
        if ("Fixed".equals(String.valueOf(selected))) {
            return "FIXED";
        }
        if ("Area".equals(String.valueOf(selected))) {
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

    private void selectProductType(String productType) {
        if ("SERVICE".equalsIgnoreCase(productType)) {
            productTypeBox.setSelectedItem("Service");
        } else if ("NON_INVENTORY".equalsIgnoreCase(productType)) {
            productTypeBox.setSelectedItem("Non Inventory");
        } else {
            productTypeBox.setSelectedItem("Inventory");
        }
    }

    private String toPricingDisplay(String pricingType) {
        if ("FIXED".equalsIgnoreCase(pricingType)) {
            return "Fixed";
        }
        if ("AREA".equalsIgnoreCase(pricingType)) {
            return "Area";
        }
        return "Variable";
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

    private void selectAreaUnit(String value) {
        areaPriceUnitBox.setSelectedItem(switch (value == null ? "" : value) {
            case "SQ_IN" -> "Square Inches";
            case "SQ_YD" -> "Square Yards";
            case "SQ_M" -> "Square Meters";
            case "SQ_CM" -> "Square Centimeters";
            default -> "Square Feet";
        });
    }

    private void selectDimensionUnit(String value) {
        dimensionUnitBox.setSelectedItem(switch (value == null ? "" : value) {
            case "FT" -> "Feet";
            case "YD" -> "Yards";
            case "M" -> "Meters";
            case "CM" -> "Centimeters";
            default -> "Inches";
        });
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
        return "$" + amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String valueAt(DefaultTableModel model, int row, int column) {
        Object value = model.getValueAt(row, column);
        return value == null ? "" : value.toString();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isBlank() ? null : value.trim();
    }

    private void hideColumn(JTable table, int columnIndex) {
        table.getColumnModel().getColumn(columnIndex).setMinWidth(0);
        table.getColumnModel().getColumn(columnIndex).setMaxWidth(0);
        table.getColumnModel().getColumn(columnIndex).setPreferredWidth(0);
    }
}
