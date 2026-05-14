package ui.screens;

import data.DB;
import ui.components.AppMenuBar;
import ui.helpers.ProductImageHelper;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
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

public class CustomOrderItems extends JFrame {
    private DefaultTableModel itemModel;
    private JTable itemTable;
    private TableRowSorter<DefaultTableModel> itemSorter;
    private JTextField searchField;
    private JTextField itemNameField;
    private JTextField barcodeField;
    private JTextArea barcodesArea;
    private JTextArea itemDescriptionArea;
    private ProductImageHelper.ImageSelector imageSelector;
    private JComboBox<String> productTypeBox;
    private JComboBox<String> pricingTypeBox;
    private JTextField fixedPriceField;
    private JComboBox<String> areaPriceUnitBox;
    private JComboBox<String> dimensionUnitBox;
    private JTextField maxWidthField;
    private JTextField maxLengthField;
    private JCheckBox hasVariantsCheckBox;
    private JTextField quantityField;
    private JTextField reorderLevelField;
    private JCheckBox activeCheckBox;
    private JButton saveOrUpdateButton;
    private JPanel itemDetailsPanel;
    private JButton itemDetailsTabButton;
    private JPanel variantPreviewPanel;
    private DefaultTableModel variantPreviewModel;
    private JTable variantPreviewTable;
    private Long selectedCustomItemId;
    private final List<JComponent> pricePricingComponents = new ArrayList<>();
    private final List<JComponent> areaPricingComponents = new ArrayList<>();
    private final List<JComponent> mainImageComponents = new ArrayList<>();

    public CustomOrderItems() {
        setTitle("Custom Order Items");
        setSize(1180, 720);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        setJMenuBar(AppMenuBar.create(this, "CustomOrderItems"));

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(new EmptyBorder(14, 14, 14, 0));
        mainPanel.add(buildTablePanel(), BorderLayout.CENTER);
        mainPanel.add(buildDetailsDockPanel(), BorderLayout.EAST);
        add(mainPanel, BorderLayout.CENTER);

        loadItems();
        WindowHelper.showPosWindow(this);
    }

    private JPanel buildTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(0, 34));
        JButton refreshButton = new JButton("Refresh");
        refreshButton.setPreferredSize(new Dimension(100, 34));
        refreshButton.addActionListener(e -> loadItems());
        searchPanel.add(new JLabel("Search Items:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(refreshButton, BorderLayout.EAST);

        itemModel = new DefaultTableModel(
                new Object[]{"ID", "Item", "Barcode", "Pricing", "Price", "Variants", "Qty", "Reorder At", "Stock", "Active", "Description"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        itemSorter = new TableRowSorter<>(itemModel);
        itemTable = new JTable(itemModel);
        itemTable.setRowSorter(itemSorter);
        itemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        itemTable.setRowHeight(28);
        itemTable.getColumnModel().getColumn(0).setMaxWidth(70);
        itemTable.getColumnModel().getColumn(5).setMaxWidth(80);
        itemTable.getColumnModel().getColumn(9).setMaxWidth(70);
        itemTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedItem();
            }
        });
        itemTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && itemTable.getSelectedRow() >= 0) {
                    setItemDetailsVisible(true);
                }
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

    private JPanel buildDetailsDockPanel() {
        JPanel dock = new JPanel(new BorderLayout());
        itemDetailsPanel = buildFormPanel();
        itemDetailsTabButton = new VerticalTabButton("Details");
        itemDetailsTabButton.setFocusPainted(false);
        itemDetailsTabButton.setContentAreaFilled(false);
        itemDetailsTabButton.setBorderPainted(false);
        itemDetailsTabButton.setVisible(false);
        itemDetailsTabButton.addActionListener(e -> setItemDetailsVisible(true));

        JPanel tabHolder = new JPanel(new BorderLayout());
        tabHolder.setOpaque(false);
        tabHolder.setBorder(new EmptyBorder(46, 0, 0, 0));
        tabHolder.add(itemDetailsTabButton, BorderLayout.NORTH);

        dock.add(tabHolder, BorderLayout.EAST);
        dock.add(itemDetailsPanel, BorderLayout.CENTER);
        return dock;
    }

    private JPanel buildFormPanel() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 10));
        wrapper.setPreferredSize(new Dimension(470, 0));
        wrapper.setBorder(BorderFactory.createTitledBorder("Item Details"));

        JButton hideButton = new JButton("Hide");
        hideButton.setFocusPainted(false);
        hideButton.addActionListener(e -> setItemDetailsVisible(false));
        JPanel header = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        header.add(hideButton);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        itemNameField = new JTextField();
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
        fixedPriceField = new JTextField();
        areaPriceUnitBox = new JComboBox<>(new String[]{"Square Feet", "Square Inches", "Square Yards", "Square Meters", "Square Centimeters"});
        dimensionUnitBox = new JComboBox<>(new String[]{"Inches", "Feet", "Yards", "Meters", "Centimeters"});
        maxWidthField = new JTextField();
        maxLengthField = new JTextField();
        hasVariantsCheckBox = new JCheckBox("Track Sizes / Variants");
        quantityField = new JTextField("0");
        reorderLevelField = new JTextField("0");
        activeCheckBox = new JCheckBox("Active", true);
        pricingTypeBox.addActionListener(e -> updateFixedPriceEnabled());
        productTypeBox.addActionListener(e -> updateVariantTrackingFields());
        hasVariantsCheckBox.addActionListener(e -> {
            updateVariantTrackingFields();
            updateFixedPriceEnabled();
            loadVariantPreview(selectedCustomItemId, hasVariantsCheckBox.isSelected());
        });

        addField(form, gbc, 0, "Item:", itemNameField);
        addField(form, gbc, 1, "Barcode:", barcodeField);
        JScrollPane barcodesScrollPane = new JScrollPane(barcodesArea);
        barcodesScrollPane.setPreferredSize(new Dimension(300, 76));
        addField(form, gbc, 2, "More Barcodes:", barcodesScrollPane);
        gbc.gridx = 1;
        gbc.gridy = 3;
        form.add(hasVariantsCheckBox, gbc);
        addField(form, gbc, 4, "Product Type:", productTypeBox);
        addField(form, gbc, 5, "Pricing:", pricingTypeBox);
        addTrackedField(pricePricingComponents, form, gbc, 6, "Price:", fixedPriceField);
        addTrackedField(areaPricingComponents, form, gbc, 7, "Price Unit:", areaPriceUnitBox);
        addTrackedField(areaPricingComponents, form, gbc, 8, "Size Unit:", dimensionUnitBox);
        addTrackedField(areaPricingComponents, form, gbc, 9, "Max Width:", maxWidthField);
        addTrackedField(areaPricingComponents, form, gbc, 10, "Max Length:", maxLengthField);
        addField(form, gbc, 11, "Total Qty:", quantityField);
        addField(form, gbc, 12, "Total Reorder:", reorderLevelField);
        JScrollPane descriptionScrollPane = new JScrollPane(itemDescriptionArea);
        descriptionScrollPane.setPreferredSize(new Dimension(300, 92));
        addField(form, gbc, 13, "Description:", descriptionScrollPane);
        addTrackedField(mainImageComponents, form, gbc, 14, "Image:", imageSelector);
        gbc.gridx = 1;
        gbc.gridy = 15;
        form.add(activeCheckBox, gbc);

        variantPreviewModel = new DefaultTableModel(
                new Object[]{"Variant", "Price", "Qty", "Reorder", "Stock", "Active"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        variantPreviewTable = new JTable(variantPreviewModel);
        variantPreviewTable.setRowHeight(24);
        variantPreviewPanel = new JPanel(new BorderLayout(0, 6));
        variantPreviewPanel.setBorder(BorderFactory.createTitledBorder("Variants"));
        variantPreviewPanel.add(new JScrollPane(variantPreviewTable), BorderLayout.CENTER);
        variantPreviewPanel.setPreferredSize(new Dimension(0, 190));
        variantPreviewPanel.setVisible(false);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.add(form, BorderLayout.NORTH);
        content.add(variantPreviewPanel, BorderLayout.CENTER);
        JScrollPane formScrollPane = new JScrollPane(content);
        formScrollPane.setBorder(null);
        formScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        formScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel buttons = new JPanel(new GridLayout(2, 2, 8, 8));
        saveOrUpdateButton = new JButton("Save Item");
        JButton variantsButton = new JButton("Sizes / Variants");
        JButton deleteButton = new JButton("Delete Item");
        JButton clearButton = new JButton("Clear");
        buttons.add(saveOrUpdateButton);
        buttons.add(variantsButton);
        buttons.add(deleteButton);
        buttons.add(clearButton);

        saveOrUpdateButton.addActionListener(e -> saveItem(selectedCustomItemId != null));
        variantsButton.addActionListener(e -> openVariantsDialog());
        deleteButton.addActionListener(e -> deleteSelectedItem());
        clearButton.addActionListener(e -> clearForm());

        wrapper.add(header, BorderLayout.NORTH);
        wrapper.add(formScrollPane, BorderLayout.CENTER);
        wrapper.add(buttons, BorderLayout.SOUTH);
        updateFixedPriceEnabled();
        updateVariantTrackingFields();
        updateVariantPreviewVisibility(false);
        updateSaveOrUpdateButton();
        return wrapper;
    }

    private void setItemDetailsVisible(boolean visible) {
        if (itemDetailsPanel != null) {
            itemDetailsPanel.setVisible(visible);
        }
        if (itemDetailsTabButton != null) {
            itemDetailsTabButton.setVisible(!visible);
        }
        revalidate();
        repaint();
    }

    private static class VerticalTabButton extends JButton {
        private VerticalTabButton(String text) {
            super(text);
            setMargin(new Insets(0, 0, 0, 0));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ButtonModel model = getModel();
            Color background = model.isPressed() ? getBackground().darker() : getBackground();
            g2.setColor(background);
            g2.fillRoundRect(0, 0, getWidth() + 14, getHeight() - 1, 14, 14);
            g2.setColor(getForeground());
            g2.rotate(-Math.PI / 2);
            FontMetrics metrics = g2.getFontMetrics();
            int x = -(getHeight() + metrics.stringWidth(getText())) / 2;
            int y = (getWidth() + metrics.getAscent() - metrics.getDescent()) / 2;
            g2.drawString(getText(), x, y);
            g2.dispose();
        }

        @Override
        protected void paintBorder(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.GRAY);
            g2.drawRoundRect(0, 0, getWidth() + 14, getHeight() - 1, 14, 14);
            g2.dispose();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics metrics = getFontMetrics(getFont());
            return new Dimension(34, metrics.stringWidth(getText()) + 18);
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

    private void loadItems() {
        itemModel.setRowCount(0);
        String sql = """
                SELECT custom_item_id, item_name, barcode, description, product_type, pricing_type, fixed_price,
                       has_variants,
                       quantity_on_hand, reorder_level, is_active,
                       CASE
                           WHEN is_active AND reorder_level > 0 AND quantity_on_hand <= reorder_level THEN 'Low'
                           ELSE 'OK'
                       END AS stock_status
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
                        rs.getString("barcode"),
                        rs.getString("pricing_type"),
                        formatMoney(rs.getBigDecimal("fixed_price")),
                        rs.getBoolean("has_variants") ? "Yes" : "No",
                        rs.getBigDecimal("quantity_on_hand"),
                        rs.getBigDecimal("reorder_level"),
                        rs.getString("stock_status"),
                        rs.getBoolean("is_active"),
                        rs.getString("description")
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Run database/custom_orders_setup.sql before using this screen.\n\n" + ex.getMessage(), "Database Setup Needed", JOptionPane.ERROR_MESSAGE);
        }
        applyFilter();
    }

    private void saveItem(boolean update) {
        String name = itemNameField.getText().trim();
        String barcode = barcodeField.getText().trim();
        String description = itemDescriptionArea.getText().trim();
        String pricingType = getSelectedPricingType();
        String productType = getSelectedProductType();
        String imageUrl = "";
        boolean hasVariants = hasVariantsCheckBox.isSelected();
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
        String areaPriceUnit = getSelectedAreaPriceUnit();
        String dimensionUnit = getSelectedDimensionUnit();
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
            fixedPrice = parseDecimal(fixedPriceField.getText().trim(), "Price");
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
        if (update && selectedCustomItemId == null) {
            JOptionPane.showMessageDialog(this, "Select an item to update.");
            return;
        }
        if (update && !hasVariants && itemHasVariants(selectedCustomItemId)) {
            JOptionPane.showMessageDialog(this, "This item already has sizes / variants. Remove or deactivate them before turning off variant tracking.");
            hasVariantsCheckBox.setSelected(true);
            updateVariantTrackingFields();
            return;
        }
        Set<String> extraBarcodes = parseExtraBarcodes(barcodesArea.getText(), barcode);

        String insertSql = """
                INSERT INTO custom_order_items (
                    item_name, barcode, description, image_url, product_type, pricing_type, fixed_price,
                    area_price, area_price_unit, dimension_unit, max_width, max_length,
                    has_variants,
                    quantity_on_hand, reorder_level, is_active
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String updateSql = """
                UPDATE custom_order_items
                SET item_name = ?, barcode = ?, description = ?, image_url = ?, product_type = ?, pricing_type = ?, fixed_price = ?,
                    area_price = ?, area_price_unit = ?, dimension_unit = ?, max_width = ?, max_length = ?,
                    has_variants = ?,
                    quantity_on_hand = ?, reorder_level = ?, is_active = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_item_id = ?
                """;
        String deleteBarcodesSql = "DELETE FROM custom_order_item_barcodes WHERE custom_item_id = ?";
        String insertBarcodeSql = "INSERT INTO custom_order_item_barcodes (custom_item_id, barcode) VALUES (?, ?)";
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(update ? updateSql : insertSql, PreparedStatement.RETURN_GENERATED_KEYS);
                 PreparedStatement deleteBarcodePs = conn.prepareStatement(deleteBarcodesSql);
                 PreparedStatement insertBarcodePs = conn.prepareStatement(insertBarcodeSql)) {
                ps.setString(1, name);
                ps.setString(2, barcode.isEmpty() ? null : barcode);
                ps.setString(3, description.isEmpty() ? null : description);
                ps.setString(4, imageUrl.isBlank() ? null : imageUrl);
                ps.setString(5, productType);
                ps.setString(6, pricingType);
                if (fixedPrice == null) {
                    ps.setNull(7, Types.NUMERIC);
                } else {
                    ps.setBigDecimal(7, fixedPrice);
                }
                setNullableBigDecimal(ps, 8, "AREA".equals(pricingType) ? fixedPrice : null);
                ps.setString(9, "AREA".equals(pricingType) ? areaPriceUnit : null);
                ps.setString(10, "AREA".equals(pricingType) ? dimensionUnit : null);
                setNullableBigDecimal(ps, 11, maxWidth);
                setNullableBigDecimal(ps, 12, maxLength);
                ps.setBoolean(13, hasVariants);
                ps.setBigDecimal(14, quantity);
                ps.setBigDecimal(15, reorderLevel);
                ps.setBoolean(16, activeCheckBox.isSelected());
                if (update) {
                    ps.setLong(17, selectedCustomItemId);
                }
                ps.executeUpdate();

                long customItemId = selectedCustomItemId == null ? 0 : selectedCustomItemId;
                if (!update) {
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) {
                            throw new SQLException("Failed to get custom item ID.");
                        }
                        customItemId = rs.getLong(1);
                    }
                }

                deleteBarcodePs.setLong(1, customItemId);
                deleteBarcodePs.executeUpdate();
                for (String extraBarcode : extraBarcodes) {
                    insertBarcodePs.setLong(1, customItemId);
                    insertBarcodePs.setString(2, extraBarcode);
                    insertBarcodePs.addBatch();
                }
                if (!extraBarcodes.isEmpty()) {
                    insertBarcodePs.executeBatch();
                }
                if (hasVariants) {
                    refreshVariantTrackedTotals(conn, customItemId);
                }

                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
            clearForm();
            loadItems();
            JOptionPane.showMessageDialog(this, update ? "Custom order item updated." : "Custom order item saved.");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save item: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadSelectedItem() {
        int row = itemTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = itemTable.convertRowIndexToModel(row);
        selectedCustomItemId = Long.parseLong(valueAt(modelRow, 0));
        itemNameField.setText(valueAt(modelRow, 1));
        barcodeField.setText(valueAt(modelRow, 2));
        pricingTypeBox.setSelectedItem(toPricingDisplay(valueAt(modelRow, 3)));
        fixedPriceField.setText(valueAt(modelRow, 4));
        loadAreaPricingFields(selectedCustomItemId);
        loadMainImageField(selectedCustomItemId);
        loadProductTypeField(selectedCustomItemId);
        hasVariantsCheckBox.setSelected("Yes".equalsIgnoreCase(valueAt(modelRow, 5)));
        quantityField.setText(valueAt(modelRow, 6));
        reorderLevelField.setText(valueAt(modelRow, 7));
        activeCheckBox.setSelected(Boolean.parseBoolean(valueAt(modelRow, 9)));
        itemDescriptionArea.setText(valueAt(modelRow, 10));
        loadExtraBarcodes(selectedCustomItemId);
        loadVariantPreview(selectedCustomItemId, hasVariantsCheckBox.isSelected());
        updateFixedPriceEnabled();
        updateVariantTrackingFields();
        updateSaveOrUpdateButton();
    }

    private void refreshVariantTrackedTotals(Connection conn, long customItemId) throws SQLException {
        String sql = """
                UPDATE custom_order_items coi
                SET quantity_on_hand = COALESCE((
                        SELECT SUM(coiv.quantity_on_hand)
                        FROM custom_order_item_variants coiv
                        WHERE coiv.custom_item_id = coi.custom_item_id
                          AND coiv.is_active = TRUE
                    ), 0),
                    reorder_level = COALESCE((
                        SELECT SUM(coiv.reorder_level)
                        FROM custom_order_item_variants coiv
                        WHERE coiv.custom_item_id = coi.custom_item_id
                          AND coiv.is_active = TRUE
                    ), 0),
                    updated_at = CURRENT_TIMESTAMP
                WHERE coi.custom_item_id = ?
                  AND coi.has_variants = TRUE
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, customItemId);
            ps.executeUpdate();
        }
    }

    private void applyFilter() {
        if (itemSorter == null) {
            return;
        }
        String search = searchField == null ? "" : searchField.getText().trim();
        itemSorter.setRowFilter(search.isEmpty() ? null : RowFilter.regexFilter("(?i)" + Pattern.quote(search)));
    }

    private void updateFixedPriceEnabled() {
        boolean hasVariants = hasVariantsCheckBox != null && hasVariantsCheckBox.isSelected();
        boolean area = "AREA".equals(getSelectedPricingType());
        boolean mainImageVisible = !hasVariants;
        boolean priceRequired = !hasVariants && ("FIXED".equals(getSelectedPricingType()) || area);
        setComponentsVisible(pricePricingComponents, priceRequired);
        setComponentsVisible(areaPricingComponents, area);
        setComponentsVisible(mainImageComponents, mainImageVisible);
        fixedPriceField.setEnabled(priceRequired);
        imageSelector.setSelectorEnabled(mainImageVisible);
        if (!priceRequired) {
            fixedPriceField.setText("");
        }
        if (!mainImageVisible) {
            imageSelector.setImageUrl("");
        }
        areaPriceUnitBox.setEnabled(area);
        dimensionUnitBox.setEnabled(area);
        maxWidthField.setEnabled(area);
        maxLengthField.setEnabled(area);
        if (!area) {
            maxWidthField.setText("");
            maxLengthField.setText("");
        }
        revalidate();
        repaint();
    }

    private void setComponentsVisible(List<JComponent> components, boolean visible) {
        for (JComponent component : components) {
            component.setVisible(visible);
        }
    }

    private void clearForm() {
        selectedCustomItemId = null;
        itemTable.clearSelection();
        itemNameField.setText("");
        barcodeField.setText("");
        barcodesArea.setText("");
        itemDescriptionArea.setText("");
        imageSelector.setImageUrl("");
        pricingTypeBox.setSelectedItem("Variable");
        productTypeBox.setSelectedItem("Inventory");
        fixedPriceField.setText("");
        maxWidthField.setText("");
        maxLengthField.setText("");
        areaPriceUnitBox.setSelectedItem("Square Feet");
        dimensionUnitBox.setSelectedItem("Inches");
        hasVariantsCheckBox.setSelected(false);
        quantityField.setText("0");
        reorderLevelField.setText("0");
        activeCheckBox.setSelected(true);
        clearVariantPreview();
        updateFixedPriceEnabled();
        updateVariantTrackingFields();
        updateSaveOrUpdateButton();
    }

    private void clearVariantPreview() {
        if (variantPreviewModel != null) {
            variantPreviewModel.setRowCount(0);
        }
        updateVariantPreviewVisibility(false);
    }

    private void updateVariantPreviewVisibility(boolean visible) {
        if (variantPreviewPanel != null) {
            variantPreviewPanel.setVisible(visible);
            variantPreviewPanel.revalidate();
            variantPreviewPanel.repaint();
        }
    }

    private void loadVariantPreview(Long customItemId, boolean hasVariants) {
        if (variantPreviewModel == null) {
            return;
        }
        variantPreviewModel.setRowCount(0);
        updateVariantPreviewVisibility(hasVariants);
        if (!hasVariants || customItemId == null) {
            return;
        }
        String sql = """
                SELECT variant_name, fixed_price, quantity_on_hand, reorder_level, is_active,
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
            ps.setLong(1, customItemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    variantPreviewModel.addRow(new Object[]{
                            rs.getString("variant_name"),
                            formatMoney(rs.getBigDecimal("fixed_price")),
                            rs.getBigDecimal("quantity_on_hand"),
                            rs.getBigDecimal("reorder_level"),
                            rs.getString("stock_status"),
                            rs.getBoolean("is_active")
                    });
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load variant preview: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateSaveOrUpdateButton() {
        if (saveOrUpdateButton != null) {
            saveOrUpdateButton.setText(selectedCustomItemId == null ? "Save Item" : "Update Item");
        }
    }

    private void deleteSelectedItem() {
        if (selectedCustomItemId == null) {
            JOptionPane.showMessageDialog(this, "Select an item to delete.");
            return;
        }
        String itemName = itemNameField.getText().trim();
        int choice = JOptionPane.showConfirmDialog(
                this,
                "Delete " + (itemName.isEmpty() ? "this item" : "\"" + itemName + "\"") + "?\n\nThis will also delete its variants and extra barcodes. Items already used on saved orders cannot be deleted.",
                "Delete Custom Order Item",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        String sql = "DELETE FROM custom_order_items WHERE custom_item_id = ?";
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, selectedCustomItemId);
            int deleted = ps.executeUpdate();
            if (deleted == 0) {
                JOptionPane.showMessageDialog(this, "Item was not found or was already deleted.");
            } else {
                clearForm();
                loadItems();
                JOptionPane.showMessageDialog(this, "Custom order item deleted.");
            }
        } catch (SQLException ex) {
            if ("23503".equals(ex.getSQLState())) {
                JOptionPane.showMessageDialog(
                        this,
                        "This item is already used on an order, so it cannot be deleted.\n\nUncheck Active instead if you no longer want it available.",
                        "Cannot Delete Item",
                        JOptionPane.WARNING_MESSAGE
                );
            } else {
                JOptionPane.showMessageDialog(this, "Failed to delete item: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        }
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
        if ("AREA".equalsIgnoreCase(pricingType) || "SQUARE_FOOT".equalsIgnoreCase(pricingType)) {
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

    private void selectAreaUnit(JComboBox<String> comboBox, String value) {
        String display = switch (value == null ? "" : value) {
            case "SQ_IN" -> "Square Inches";
            case "SQ_YD" -> "Square Yards";
            case "SQ_M" -> "Square Meters";
            case "SQ_CM" -> "Square Centimeters";
            default -> "Square Feet";
        };
        comboBox.setSelectedItem(display);
    }

    private void selectDimensionUnit(String value) {
        String display = switch (value == null ? "" : value) {
            case "FT" -> "Feet";
            case "YD" -> "Yards";
            case "M" -> "Meters";
            case "CM" -> "Centimeters";
            default -> "Inches";
        };
        dimensionUnitBox.setSelectedItem(display);
    }

    private void updateVariantTrackingFields() {
        boolean hasVariants = hasVariantsCheckBox != null && hasVariantsCheckBox.isSelected();
        boolean inventory = "INVENTORY".equals(getSelectedProductType());
        quantityField.setEditable(!hasVariants && inventory);
        reorderLevelField.setEditable(!hasVariants && inventory);
        quantityField.setToolTipText(hasVariants ? "Calculated from active sizes / variants." : null);
        reorderLevelField.setToolTipText(hasVariants ? "Calculated from active sizes / variants." : null);
        if ((hasVariants || !inventory) && selectedCustomItemId == null) {
            quantityField.setText("0");
            reorderLevelField.setText("0");
        }
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

    private void loadAreaPricingFields(Long customItemId) {
        maxWidthField.setText("");
        maxLengthField.setText("");
        selectAreaUnit(areaPriceUnitBox, "SQ_FT");
        selectDimensionUnit("IN");
        if (customItemId == null) {
            return;
        }
        String sql = """
                SELECT area_price, area_price_unit, dimension_unit, max_width, max_length
                FROM custom_order_items
                WHERE custom_item_id = ?
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, customItemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal areaPrice = rs.getBigDecimal("area_price");
                    BigDecimal maxWidth = rs.getBigDecimal("max_width");
                    BigDecimal maxLength = rs.getBigDecimal("max_length");
                    if (fixedPriceField.getText().isBlank() && areaPrice != null) {
                        fixedPriceField.setText(areaPrice.toPlainString());
                    }
                    maxWidthField.setText(maxWidth == null ? "" : maxWidth.toPlainString());
                    maxLengthField.setText(maxLength == null ? "" : maxLength.toPlainString());
                    selectAreaUnit(areaPriceUnitBox, rs.getString("area_price_unit"));
                    selectDimensionUnit(rs.getString("dimension_unit"));
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load area pricing: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadMainImageField(Long customItemId) {
        imageSelector.setImageUrl("");
        if (customItemId == null) {
            return;
        }
        String sql = "SELECT image_url FROM custom_order_items WHERE custom_item_id = ?";
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, customItemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    imageSelector.setImageUrl(rs.getString("image_url"));
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load item image: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadProductTypeField(Long customItemId) {
        selectProductType("INVENTORY");
        if (customItemId == null) {
            return;
        }
        String sql = "SELECT product_type FROM custom_order_items WHERE custom_item_id = ?";
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, customItemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    selectProductType(rs.getString("product_type"));
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load product type: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
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

    private void loadExtraBarcodes(Long customItemId) {
        barcodesArea.setText("");
        if (customItemId == null) {
            return;
        }
        String sql = """
                SELECT barcode
                FROM custom_order_item_barcodes
                WHERE custom_item_id = ?
                ORDER BY barcode
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, customItemId);
            try (ResultSet rs = ps.executeQuery()) {
                StringBuilder barcodes = new StringBuilder();
                while (rs.next()) {
                    if (barcodes.length() > 0) {
                        barcodes.append('\n');
                    }
                    barcodes.append(rs.getString("barcode"));
                }
                barcodesArea.setText(barcodes.toString());
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load item barcodes: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openVariantsDialog() {
        if (selectedCustomItemId == null) {
            JOptionPane.showMessageDialog(this, "Select and save a custom order item before adding sizes or variants.");
            return;
        }
        if (!hasVariantsCheckBox.isSelected()) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "This will turn on variant tracking. The main quantity will become the total of active variants.",
                    "Track Sizes / Variants",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.INFORMATION_MESSAGE
            );
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }
            long currentItemId = selectedCustomItemId;
            if (!setVariantTracking(currentItemId, true)) {
                return;
            }
            hasVariantsCheckBox.setSelected(true);
            updateVariantTrackingFields();
            loadItems();
            selectItemById(currentItemId);
        }

        JDialog dialog = new JDialog(this, "Sizes / Variants - " + itemNameField.getText().trim(), true);
        dialog.setSize(780, 440);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        DefaultTableModel variantModel = new DefaultTableModel(
                new Object[]{"ID", "Size / Variant", "Barcode", "Price", "Qty", "Reorder At", "Stock", "Active", "Image"},
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
        variantTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        variantTable.getColumnModel().getColumn(4).setPreferredWidth(70);
        variantTable.getColumnModel().getColumn(5).setPreferredWidth(95);
        variantTable.getColumnModel().getColumn(6).setPreferredWidth(75);
        variantTable.getColumnModel().getColumn(7).setPreferredWidth(65);
        variantTable.getColumnModel().getColumn(8).setMinWidth(0);
        variantTable.getColumnModel().getColumn(8).setMaxWidth(0);
        variantTable.getColumnModel().getColumn(8).setPreferredWidth(0);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Variant Details"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        JTextField variantNameField = new JTextField();
        JTextField variantBarcodeField = new JTextField();
        JTextField variantPriceField = new JTextField();
        ProductImageHelper.ImageSelector variantImageSelector = ProductImageHelper.createImageSelector(dialog);
        JTextField variantQtyField = new JTextField("0");
        JTextField variantReorderField = new JTextField("0");
        JCheckBox variantActiveCheckBox = new JCheckBox("Active", true);
        final Long[] selectedVariantId = new Long[1];

        addField(form, gbc, 0, "Size / Variant:", variantNameField);
        addField(form, gbc, 1, "Barcode:", variantBarcodeField);
        addField(form, gbc, 2, "Price:", variantPriceField);
        addField(form, gbc, 3, "Image:", variantImageSelector);
        addField(form, gbc, 4, "Quantity:", variantQtyField);
        addField(form, gbc, 5, "Reorder At:", variantReorderField);
        gbc.gridx = 1;
        gbc.gridy = 6;
        form.add(variantActiveCheckBox, gbc);

        variantTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || variantTable.getSelectedRow() < 0) {
                return;
            }
            int modelRow = variantTable.convertRowIndexToModel(variantTable.getSelectedRow());
            selectedVariantId[0] = Long.parseLong(valueAt(variantModel, modelRow, 0));
            variantNameField.setText(valueAt(variantModel, modelRow, 1));
            variantBarcodeField.setText(valueAt(variantModel, modelRow, 2));
            variantPriceField.setText(valueAt(variantModel, modelRow, 3));
            variantImageSelector.setImageUrl(valueAt(variantModel, modelRow, 8));
            variantQtyField.setText(valueAt(variantModel, modelRow, 4));
            variantReorderField.setText(valueAt(variantModel, modelRow, 5));
            variantActiveCheckBox.setSelected(Boolean.parseBoolean(valueAt(variantModel, modelRow, 7)));
        });

        JButton saveButton = new JButton("Save Variant");
        JButton updateButton = new JButton("Update Variant");
        JButton clearButton = new JButton("Clear");
        JButton refreshButton = new JButton("Refresh");
        styleDialogButton(saveButton);
        styleDialogButton(updateButton);
        styleDialogButton(clearButton);
        styleDialogButton(refreshButton);

        saveButton.addActionListener(e -> {
            if (saveVariant(false, selectedVariantId[0], variantNameField, variantBarcodeField, variantPriceField, variantImageSelector, variantQtyField, variantReorderField, variantActiveCheckBox)) {
                clearVariantForm(variantTable, selectedVariantId, variantNameField, variantBarcodeField, variantPriceField, variantImageSelector, variantQtyField, variantReorderField, variantActiveCheckBox);
                loadVariants(variantModel, selectedCustomItemId);
            }
        });
        updateButton.addActionListener(e -> {
            if (selectedVariantId[0] == null) {
                JOptionPane.showMessageDialog(dialog, "Select a variant to update.");
                return;
            }
            if (saveVariant(true, selectedVariantId[0], variantNameField, variantBarcodeField, variantPriceField, variantImageSelector, variantQtyField, variantReorderField, variantActiveCheckBox)) {
                clearVariantForm(variantTable, selectedVariantId, variantNameField, variantBarcodeField, variantPriceField, variantImageSelector, variantQtyField, variantReorderField, variantActiveCheckBox);
                loadVariants(variantModel, selectedCustomItemId);
            }
        });
        clearButton.addActionListener(e -> clearVariantForm(variantTable, selectedVariantId, variantNameField, variantBarcodeField, variantPriceField, variantImageSelector, variantQtyField, variantReorderField, variantActiveCheckBox));
        refreshButton.addActionListener(e -> loadVariants(variantModel, selectedCustomItemId));

        JPanel buttonPanel = new JPanel(new GridLayout(2, 2, 8, 8));
        buttonPanel.add(saveButton);
        buttonPanel.add(updateButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(refreshButton);

        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.setPreferredSize(new Dimension(320, 0));
        right.add(form, BorderLayout.NORTH);
        right.add(buttonPanel, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(10, 10, 10, 10));
        content.add(new JScrollPane(variantTable), BorderLayout.CENTER);
        content.add(right, BorderLayout.EAST);
        dialog.add(content, BorderLayout.CENTER);

        loadVariants(variantModel, selectedCustomItemId);
        dialog.setVisible(true);
        long currentItemId = selectedCustomItemId == null ? -1 : selectedCustomItemId;
        loadItems();
        if (currentItemId > 0) {
            selectItemById(currentItemId);
            loadVariantPreview(currentItemId, hasVariantsCheckBox.isSelected());
        }
    }

    private boolean itemHasVariants(Long customItemId) {
        if (customItemId == null) {
            return false;
        }
        String sql = "SELECT EXISTS (SELECT 1 FROM custom_order_item_variants WHERE custom_item_id = ?) AS has_variants";
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, customItemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean("has_variants");
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to check variants: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
            return true;
        }
    }

    private boolean setVariantTracking(long customItemId, boolean enabled) {
        String sql = """
                UPDATE custom_order_items
                SET has_variants = ?,
                    fixed_price = CASE WHEN ? THEN NULL ELSE fixed_price END,
                    area_price = CASE WHEN ? THEN NULL ELSE area_price END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_item_id = ?
                """;
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setBoolean(1, enabled);
                ps.setBoolean(2, enabled);
                ps.setBoolean(3, enabled);
                ps.setLong(4, customItemId);
                ps.executeUpdate();
                if (enabled) {
                    refreshVariantTrackedTotals(conn, customItemId);
                }
                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to update variant tracking: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void selectItemById(Long customItemId) {
        if (customItemId == null) {
            return;
        }
        for (int row = 0; row < itemModel.getRowCount(); row++) {
            Object value = itemModel.getValueAt(row, 0);
            if (value != null && customItemId.toString().equals(value.toString())) {
                int viewRow = itemTable.convertRowIndexToView(row);
                if (viewRow >= 0) {
                    itemTable.setRowSelectionInterval(viewRow, viewRow);
                    itemTable.scrollRectToVisible(itemTable.getCellRect(viewRow, 0, true));
                }
                return;
            }
        }
    }

    private void styleDialogButton(JButton button) {
        button.setOpaque(true);
        button.setBackground(new Color(245, 245, 245));
        button.setForeground(new Color(25, 25, 25));
        button.setFocusPainted(false);
    }

    private void loadVariants(DefaultTableModel variantModel, Long customItemId) {
        variantModel.setRowCount(0);
        String sql = """
                SELECT custom_variant_id, variant_name, barcode, fixed_price, quantity_on_hand, reorder_level, is_active, image_url,
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
            ps.setLong(1, customItemId);
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
                ps.setLong(1, selectedCustomItemId);
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

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return "$" + amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String valueAt(int row, int column) {
        Object value = itemModel.getValueAt(row, column);
        return value == null ? "" : value.toString();
    }

    private String valueAt(DefaultTableModel model, int row, int column) {
        Object value = model.getValueAt(row, column);
        return value == null ? "" : value.toString();
    }
}
