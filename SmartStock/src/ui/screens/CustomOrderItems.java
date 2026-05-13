package ui.screens;

import data.DB;
import ui.components.AppMenuBar;
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
import java.util.LinkedHashSet;
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
    private JComboBox<String> pricingTypeBox;
    private JTextField fixedPriceField;
    private JTextField quantityField;
    private JTextField reorderLevelField;
    private JCheckBox activeCheckBox;
    private Long selectedCustomItemId;

    public CustomOrderItems() {
        setTitle("Custom Order Items");
        setSize(1050, 640);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        setJMenuBar(AppMenuBar.create(this, "CustomOrderItems"));

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(new EmptyBorder(14, 14, 14, 14));
        mainPanel.add(buildTablePanel(), BorderLayout.CENTER);
        mainPanel.add(buildFormPanel(), BorderLayout.EAST);
        add(mainPanel, BorderLayout.CENTER);

        loadItems();
        WindowHelper.showPosWindow(this);
    }

    private JPanel buildTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchField = new JTextField();
        searchPanel.add(new JLabel("Search Items:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        itemModel = new DefaultTableModel(
                new Object[]{"ID", "Item", "Barcode", "Pricing", "Fixed Price", "Qty", "Reorder At", "Stock", "Active", "Description"},
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
        itemTable.getColumnModel().getColumn(8).setMaxWidth(70);
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

    private JPanel buildFormPanel() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 10));
        wrapper.setPreferredSize(new Dimension(360, 0));
        wrapper.setBorder(BorderFactory.createTitledBorder("Order Item"));

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
        pricingTypeBox = new JComboBox<>(new String[]{"Variable", "Fixed"});
        fixedPriceField = new JTextField();
        quantityField = new JTextField("0");
        reorderLevelField = new JTextField("0");
        activeCheckBox = new JCheckBox("Active", true);
        pricingTypeBox.addActionListener(e -> updateFixedPriceEnabled());

        addField(form, gbc, 0, "Item:", itemNameField);
        addField(form, gbc, 1, "Barcode:", barcodeField);
        addField(form, gbc, 2, "More Barcodes:", new JScrollPane(barcodesArea));
        addField(form, gbc, 3, "Pricing:", pricingTypeBox);
        addField(form, gbc, 4, "Fixed Price:", fixedPriceField);
        addField(form, gbc, 5, "Quantity:", quantityField);
        addField(form, gbc, 6, "Reorder At:", reorderLevelField);
        addField(form, gbc, 7, "Description:", new JScrollPane(itemDescriptionArea));
        gbc.gridx = 1;
        gbc.gridy = 8;
        form.add(activeCheckBox, gbc);

        JPanel buttons = new JPanel(new GridLayout(2, 2, 8, 8));
        JButton saveButton = new JButton("Save Item");
        JButton updateButton = new JButton("Update Item");
        JButton clearButton = new JButton("Clear");
        JButton refreshButton = new JButton("Refresh");
        buttons.add(saveButton);
        buttons.add(updateButton);
        buttons.add(clearButton);
        buttons.add(refreshButton);

        saveButton.addActionListener(e -> saveItem(false));
        updateButton.addActionListener(e -> saveItem(true));
        clearButton.addActionListener(e -> clearForm());
        refreshButton.addActionListener(e -> loadItems());

        wrapper.add(form, BorderLayout.NORTH);
        wrapper.add(buttons, BorderLayout.SOUTH);
        updateFixedPriceEnabled();
        return wrapper;
    }

    private void addField(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
    }

    private void loadItems() {
        itemModel.setRowCount(0);
        String sql = """
                SELECT custom_item_id, item_name, barcode, description, pricing_type, fixed_price,
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
        BigDecimal fixedPrice = null;
        BigDecimal quantity = parseDecimal(quantityField.getText().trim(), "Quantity");
        BigDecimal reorderLevel = parseDecimal(reorderLevelField.getText().trim(), "Reorder level");
        if (quantity == null || reorderLevel == null) {
            return;
        }
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Item name is required.");
            return;
        }
        if ("FIXED".equals(pricingType)) {
            fixedPrice = parseDecimal(fixedPriceField.getText().trim(), "Fixed price");
            if (fixedPrice == null) {
                return;
            }
        }
        if (update && selectedCustomItemId == null) {
            JOptionPane.showMessageDialog(this, "Select an item to update.");
            return;
        }
        Set<String> extraBarcodes = parseExtraBarcodes(barcodesArea.getText(), barcode);

        String insertSql = """
                INSERT INTO custom_order_items (
                    item_name, barcode, description, pricing_type, fixed_price,
                    quantity_on_hand, reorder_level, is_active
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String updateSql = """
                UPDATE custom_order_items
                SET item_name = ?, barcode = ?, description = ?, pricing_type = ?, fixed_price = ?,
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
                ps.setString(4, pricingType);
                if (fixedPrice == null) {
                    ps.setNull(5, Types.NUMERIC);
                } else {
                    ps.setBigDecimal(5, fixedPrice);
                }
                ps.setBigDecimal(6, quantity);
                ps.setBigDecimal(7, reorderLevel);
                ps.setBoolean(8, activeCheckBox.isSelected());
                if (update) {
                    ps.setLong(9, selectedCustomItemId);
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
        pricingTypeBox.setSelectedItem("FIXED".equals(valueAt(modelRow, 3)) ? "Fixed" : "Variable");
        fixedPriceField.setText(valueAt(modelRow, 4));
        quantityField.setText(valueAt(modelRow, 5));
        reorderLevelField.setText(valueAt(modelRow, 6));
        activeCheckBox.setSelected(Boolean.parseBoolean(valueAt(modelRow, 8)));
        itemDescriptionArea.setText(valueAt(modelRow, 9));
        loadExtraBarcodes(selectedCustomItemId);
        updateFixedPriceEnabled();
    }

    private void applyFilter() {
        if (itemSorter == null) {
            return;
        }
        String search = searchField == null ? "" : searchField.getText().trim();
        itemSorter.setRowFilter(search.isEmpty() ? null : RowFilter.regexFilter("(?i)" + Pattern.quote(search)));
    }

    private void updateFixedPriceEnabled() {
        boolean fixed = "FIXED".equals(getSelectedPricingType());
        fixedPriceField.setEnabled(fixed);
        if (!fixed) {
            fixedPriceField.setText("");
        }
    }

    private void clearForm() {
        selectedCustomItemId = null;
        itemTable.clearSelection();
        itemNameField.setText("");
        barcodeField.setText("");
        barcodesArea.setText("");
        itemDescriptionArea.setText("");
        pricingTypeBox.setSelectedItem("Variable");
        fixedPriceField.setText("");
        quantityField.setText("0");
        reorderLevelField.setText("0");
        activeCheckBox.setSelected(true);
        updateFixedPriceEnabled();
    }

    private String getSelectedPricingType() {
        Object selected = pricingTypeBox.getSelectedItem();
        return selected != null && "Fixed".equalsIgnoreCase(selected.toString()) ? "FIXED" : "VARIABLE";
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
}
