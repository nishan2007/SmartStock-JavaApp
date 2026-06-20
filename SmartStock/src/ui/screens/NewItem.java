package ui.screens;

import ui.screens.customorders.NewCustomItem;

import data.DB;
import managers.SessionManager;
import services.DeviceContextService;
import services.OfflineWriteGuard;
import ui.components.AppMenuBar;
import ui.components.DepartmentSelector;
import ui.components.RoundedBorder;
import ui.components.VendorSelector;
import ui.helpers.WindowHelper;
import ui.helpers.ProductImageHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    private VendorSelector vendorSelector;
    private JTextField quantityField;
    private ProductImageHelper.ImageSelector imageSelector;
    private JButton saveButton;
    private JButton clearButton;
    private JButton cancelButton;
    private final int selectedLocationId;

    public NewItem() {
        this(1);
    }

    public NewItem(int selectedLocationId) {
        this.selectedLocationId = selectedLocationId;
        setTitle("Add New Item");
        setSize(980, 620);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this,"NewItem"));

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(5, 5, 5, 5),
                BorderFactory.createTitledBorder(
                        new RoundedBorder(12, Color.GRAY, 1), // 👈 control radius here
                        "New Item",
                        javax.swing.border.TitledBorder.LEFT,
                        javax.swing.border.TitledBorder.TOP,
                        panel.getFont().deriveFont(Font.BOLD)
                )
        ));

        JPanel formPanel = new JPanel(new GridLayout(1, 2, 20, 0));

        JPanel leftColumn = new JPanel(new GridBagLayout());
        JPanel rightColumn = new JPanel(new GridBagLayout());

        GridBagConstraints leftGbc = new GridBagConstraints();
        leftGbc.insets = new Insets(5, 5, 5, 5);
        leftGbc.fill = GridBagConstraints.HORIZONTAL;
        leftGbc.anchor = GridBagConstraints.NORTHWEST;

        GridBagConstraints rightGbc = new GridBagConstraints();
        rightGbc.insets = new Insets(5, 5, 5, 5);
        rightGbc.fill = GridBagConstraints.HORIZONTAL;
        rightGbc.anchor = GridBagConstraints.NORTHWEST;

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
        vendorSelector = new VendorSelector();
        quantityField = new JTextField("0");
        imageSelector = ProductImageHelper.createImageSelector(this);

        JScrollPane barcodeScrollPane = new JScrollPane(barcodesArea);
        barcodeScrollPane.setPreferredSize(new Dimension(220, 75));
        JScrollPane descriptionScrollPane = new JScrollPane(descriptionArea);
        descriptionScrollPane.setPreferredSize(new Dimension(240, 120));

        leftGbc.gridx = 0;
        leftGbc.gridy = 0;
        leftGbc.weightx = 0;
        leftGbc.weighty = 0;
        leftColumn.add(new JLabel("Item Name:"), leftGbc);

        leftGbc.gridx = 1;
        leftGbc.weightx = 1;
        leftColumn.add(nameField, leftGbc);

        leftGbc.gridx = 0;
        leftGbc.gridy = 1;
        leftGbc.weightx = 0;
        leftColumn.add(new JLabel("Size:"), leftGbc);

        leftGbc.gridx = 1;
        leftGbc.weightx = 1;
        leftColumn.add(sizeField, leftGbc);

        leftGbc.gridx = 0;
        leftGbc.gridy = 2;
        leftGbc.weightx = 0;
        leftColumn.add(new JLabel("SKU (auto if blank):"), leftGbc);

        leftGbc.gridx = 1;
        leftGbc.weightx = 1;
        leftColumn.add(skuField, leftGbc);

        leftGbc.gridx = 0;
        leftGbc.gridy = 3;
        leftGbc.weightx = 0;
        leftGbc.anchor = GridBagConstraints.NORTHWEST;
        leftColumn.add(new JLabel("Description:"), leftGbc);

        leftGbc.gridx = 1;
        leftGbc.weightx = 1;
        leftGbc.weighty = 1;
        leftGbc.fill = GridBagConstraints.BOTH;
        leftColumn.add(descriptionScrollPane, leftGbc);

        leftGbc.fill = GridBagConstraints.HORIZONTAL;
        leftGbc.weighty = 0;
        leftGbc.gridx = 0;
        leftGbc.gridy = 4;
        leftGbc.weightx = 0;
        leftColumn.add(new JLabel("Barcode:"), leftGbc);

        leftGbc.gridx = 1;
        leftGbc.weightx = 1;
        leftColumn.add(barcodeField, leftGbc);

        leftGbc.gridx = 0;
        leftGbc.gridy = 5;
        leftGbc.weightx = 0;
        leftColumn.add(new JLabel("Price:"), leftGbc);

        leftGbc.gridx = 1;
        leftGbc.weightx = 1;
        leftColumn.add(priceField, leftGbc);

        leftGbc.gridx = 0;
        leftGbc.gridy = 6;
        leftGbc.weightx = 0;
        leftColumn.add(new JLabel("Item Type:"), leftGbc);

        leftGbc.gridx = 1;
        leftGbc.weightx = 1;
        leftColumn.add(itemTypeBox, leftGbc);

        leftGbc.gridx = 0;
        leftGbc.gridy = 7;
        leftGbc.weightx = 0;
        leftColumn.add(new JLabel("Starting Quantity:"), leftGbc);

        leftGbc.gridx = 1;
        leftGbc.weightx = 1;
        leftColumn.add(quantityField, leftGbc);

        leftGbc.gridx = 0;
        leftGbc.gridy = 8;
        leftGbc.weightx = 0;
        leftGbc.weighty = 1;
        leftColumn.add(Box.createVerticalGlue(), leftGbc);



        rightGbc.gridx = 0;
        rightGbc.gridy = 0;
        rightGbc.weightx = 0;
        rightGbc.weighty = 0;
        rightColumn.add(new JLabel("Cost Price:"), rightGbc);

        rightGbc.gridx = 1;
        rightGbc.weightx = 1;
        rightColumn.add(costPriceField, rightGbc);

        rightGbc.gridx = 0;
        rightGbc.gridy = 1;
        rightGbc.weightx = 0;
        rightColumn.add(new JLabel("Department:"), rightGbc);

        rightGbc.gridx = 1;
        rightGbc.weightx = 1;
        rightColumn.add(departmentSelector, rightGbc);

        rightGbc.gridx = 0;
        rightGbc.gridy = 2;
        rightGbc.weightx = 0;
        rightColumn.add(new JLabel("Vendor:"), rightGbc);

        rightGbc.gridx = 1;
        rightGbc.weightx = 1;
        rightColumn.add(vendorSelector, rightGbc);

        rightGbc.gridx = 0;
        rightGbc.gridy = 3;
        rightGbc.weightx = 0;
        rightGbc.anchor = GridBagConstraints.NORTHWEST;
        rightColumn.add(new JLabel("Additional Barcodes:"), rightGbc);

        rightGbc.gridx = 1;
        rightGbc.weightx = 1;
        rightGbc.weighty = 0;
        rightGbc.fill = GridBagConstraints.HORIZONTAL;
        rightColumn.add(barcodeScrollPane, rightGbc);

        rightGbc.gridx = 0;
        rightGbc.gridy = 4;
        rightGbc.weightx = 0;
        rightGbc.anchor = GridBagConstraints.NORTHWEST;
        rightColumn.add(new JLabel("Image URL / Path:"), rightGbc);

        rightGbc.gridx = 1;
        rightGbc.weightx = 1;
        rightGbc.weighty = 0;
        rightGbc.fill = GridBagConstraints.HORIZONTAL;
        rightColumn.add(imageSelector, rightGbc);

        rightGbc.gridx = 0;
        rightGbc.gridy = 5;
        rightGbc.weightx = 0;
        rightGbc.weighty = 1;
        rightColumn.add(Box.createVerticalGlue(), rightGbc);

        formPanel.add(leftColumn);
        formPanel.add(rightColumn);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        saveButton = new JButton("Save Item");
        clearButton = new JButton("Clear");
        cancelButton = new JButton("Close");

        buttonPanel.add(saveButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(cancelButton);

        panel.add(formPanel, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(780, 360));
        panel.add(buttonPanel, BorderLayout.SOUTH);

        JTabbedPane itemTypeTabs = new JTabbedPane();
        itemTypeTabs.addTab("Inventory Item", panel);
        itemTypeTabs.addTab("Custom Item", new NewCustomItem(this));
        add(itemTypeTabs);

        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveItem();
            }
        });

        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clearFields();
            }
        });

        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        itemTypeBox.addActionListener(e -> updateQuantityEnabledForType());
        updateQuantityEnabledForType();

        WindowHelper.showPosWindow(this);
    }

    private void updateQuantityEnabledForType() {
        boolean inventoryItem = "INVENTORY".equals(getSelectedProductType());
        quantityField.setEnabled(inventoryItem);
        if (!inventoryItem) {
            quantityField.setText("0");
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
        try {
            OfflineWriteGuard.requireCloudForGlobalWrite("Product setup");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cloud Required", JOptionPane.WARNING_MESSAGE);
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
        String productType = getSelectedProductType();
        boolean inventoryItem = "INVENTORY".equals(productType);
        String imageUrl;
        try {
            imageUrl = ProductImageHelper.uploadLocalImageIfNeeded(imageSelector.getImageUrl());
            imageSelector.setImageUrl(imageUrl);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Image upload failed: " + ex.getMessage());
            return;
        }
        List<String> extraBarcodes = new ArrayList<>();
        Set<String> uniqueBarcodes = new LinkedHashSet<>();
        if (!barcodesText.isEmpty()) {
            String[] barcodeLines = barcodesText.split("\\r?\\n");

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

        if (name.isEmpty() || barcode.isEmpty() || costPriceText.isEmpty() || priceText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name, Barcode, Cost Price, and Price are required. Leave SKU blank to auto-generate it.");
            return;
        }

        double costPrice;
        try {
            costPrice = utils.CurrencyFormatter.normalize(new java.math.BigDecimal(costPriceText)).doubleValue();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Cost price must be a valid number.");
            return;
        }
        double price;
        try {
            price = utils.CurrencyFormatter.normalize(new java.math.BigDecimal(priceText)).doubleValue();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Price must be a valid number.");
            return;
        }

        int quantity = 0;
        if (inventoryItem) {
            try {
                quantity = Integer.parseInt(quantityText);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Starting quantity must be a whole number.");
                return;
            }
        }

        Integer categoryId = departmentSelector.getSelectedDepartmentId();
        if (categoryId == null && !departmentSelector.getSelectedDepartmentName().isBlank()) {
            return;
        }
        Integer vendorId = vendorSelector.getSelectedVendorId();
        if (vendorId == null && !vendorSelector.getSelectedVendorName().isBlank()) {
            return;
        }

        String sql = """
                INSERT INTO products (name, size, sku, barcode, description, cost_price, price, product_type, category_id, vendor_id, image_url, created_by_user_id, created_by_name)
                VALUES (?, NULLIF(?, ''), NULLIF(?, ''), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING product_id, sku
                """;

        String upsertInventorySql = """
                INSERT INTO inventory (product_id, location_id, quantity_on_hand, reorder_level)
                VALUES (?, ?, ?, 0)
                ON CONFLICT (product_id, location_id)
                DO UPDATE SET quantity_on_hand = EXCLUDED.quantity_on_hand
                """;
        String insertBarcodeSql = """
                INSERT INTO product_barcodes (product_id, barcode)
                VALUES (?, ?)
                ON CONFLICT (barcode)
                DO UPDATE SET
                    product_id = EXCLUDED.product_id,
                    updated_at = CURRENT_TIMESTAMP
                """;
        String insertMovementSql = """
                INSERT INTO inventory_movements (
                    product_id, location_id, change_qty, reason, note, user_name,
                    user_id, device_id, device_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql);
                 PreparedStatement inventoryPs = conn.prepareStatement(upsertInventorySql);
                 PreparedStatement barcodePs = conn.prepareStatement(insertBarcodeSql);
                 PreparedStatement movementPs = conn.prepareStatement(insertMovementSql)) {
                ps.setString(1, name);
                ps.setString(2, size);
                ps.setString(3, sku);
                ps.setString(4, barcode);
                ps.setString(5, description);
                ps.setDouble(6, costPrice);
                ps.setDouble(7, price);

                ps.setString(8, productType);

                if (categoryId == null) {
                    ps.setNull(9, java.sql.Types.INTEGER);
                } else {
                    ps.setInt(9, categoryId);
                }
                if (vendorId == null) {
                    ps.setNull(10, java.sql.Types.INTEGER);
                } else {
                    ps.setInt(10, vendorId);
                }
                ps.setString(11, imageUrl);
                setCurrentUserAuditParameters(ps, 12, 13);

                int productId;
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Failed to get new product ID and SKU.");
                    }
                    productId = rs.getInt("product_id");
                    sku = rs.getString("sku");
                    extraBarcodes.remove(sku);
                }

                if (inventoryItem) {
                    inventoryPs.setInt(1, productId);
                    inventoryPs.setInt(2, selectedLocationId);
                    inventoryPs.setInt(3, quantity);
                    inventoryPs.executeUpdate();
                }

                if (inventoryItem && quantity != 0) {
                    movementPs.setInt(1, productId);
                    movementPs.setInt(2, selectedLocationId);
                    movementPs.setInt(3, quantity);
                    movementPs.setString(4, "NEW_ITEM");
                    movementPs.setString(5, "Starting quantity for new item");
                    movementPs.setString(6, SessionManager.getCurrentUserDisplayName());
                    setCurrentUserDeviceMovementParameters(movementPs, 7, 8, 9);
                    movementPs.executeUpdate();
                }

                for (String extraBarcode : extraBarcodes) {
                    barcodePs.setInt(1, productId);
                    barcodePs.setString(2, extraBarcode);
                    barcodePs.addBatch();
                }
                if (!extraBarcodes.isEmpty()) {
                    barcodePs.executeBatch();
                }

                conn.commit();
                JOptionPane.showMessageDialog(this, "Item added successfully. SKU: " + sku);
                clearFields();

            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save item: " + ex.getMessage());
        }
    }

    private void setCurrentUserAuditParameters(PreparedStatement ps, int userIdParameter, int userNameParameter) throws SQLException {
        if (SessionManager.getCurrentUserId() == null) {
            ps.setNull(userIdParameter, java.sql.Types.INTEGER);
        } else {
            ps.setInt(userIdParameter, SessionManager.getCurrentUserId());
        }
        ps.setString(userNameParameter, SessionManager.getCurrentUserDisplayName());
    }

    private void setCurrentUserDeviceMovementParameters(PreparedStatement ps, int userIdParameter,
                                                        int deviceIdParameter, int deviceNameParameter) throws SQLException {
        if (SessionManager.getCurrentUserId() == null) {
            ps.setNull(userIdParameter, java.sql.Types.INTEGER);
        } else {
            ps.setInt(userIdParameter, SessionManager.getCurrentUserId());
        }
        ps.setString(deviceIdParameter, DeviceContextService.currentDeviceId());
        ps.setString(deviceNameParameter, DeviceContextService.currentDeviceName());
    }

    private void clearFields() {
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
        vendorSelector.clearSelection();
        quantityField.setText("0");
        imageSelector.setImageUrl("");
        nameField.requestFocusInWindow();
    }
}
