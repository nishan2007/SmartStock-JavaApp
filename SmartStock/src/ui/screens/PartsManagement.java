package ui.screens;

import data.DB;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PartsManagement extends JFrame {
    private final JTextField searchField = new JTextField();
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"ID", "Part", "Part #", "On Hand", "Reorder", "Vendor", "Active"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final JTextField nameField = new JTextField();
    private final JTextField partNumberField = new JTextField();
    private final JTextField categoryField = new JTextField();
    private final JTextField quantityField = new JTextField("0");
    private final JTextField reorderPointField = new JTextField("0");
    private final JTextField reorderQuantityField = new JTextField("0");
    private final JTextField unitCostField = new JTextField("0");
    private final JTextField vendorField = new JTextField();
    private final JTextField binLocationField = new JTextField();
    private final JCheckBox activeBox = new JCheckBox("Active", true);
    private final JTextArea notesArea = new JTextArea(4, 24);
    private Integer selectedPartId;

    public PartsManagement() {
        setTitle("Parts List");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "PartsManagement"));

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.setBackground(new Color(245, 247, 250));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        root.add(buildEditor(), BorderLayout.EAST);
        add(root);

        table.setRowHeight(28);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedPart();
            }
        });
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);

        loadParts();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout(12, 8));
        panel.setOpaque(false);

        JLabel titleLabel = new JLabel("Parts List");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setForeground(new Color(31, 41, 55));

        JButton searchButton = new JButton("Search");
        JButton refreshButton = new JButton("Refresh");
        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setOpaque(false);
        searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(searchButton);
        buttons.add(refreshButton);
        searchPanel.add(buttons, BorderLayout.EAST);

        searchField.addActionListener(e -> loadParts());
        searchButton.addActionListener(e -> loadParts());
        refreshButton.addActionListener(e -> {
            searchField.setText("");
            loadParts();
        });

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(searchPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildEditor() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setPreferredSize(new Dimension(390, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(16, 16, 16, 16)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 12, 0);
        JLabel title = new JLabel("Part Details");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        panel.add(title, gbc);

        addFormRow(panel, gbc, 1, "Name:", nameField);
        addFormRow(panel, gbc, 2, "Part #:", partNumberField);
        addFormRow(panel, gbc, 3, "Category:", categoryField);
        addFormRow(panel, gbc, 4, "On Hand:", quantityField);
        addFormRow(panel, gbc, 5, "Reorder Point:", reorderPointField);
        addFormRow(panel, gbc, 6, "Reorder Qty:", reorderQuantityField);
        addFormRow(panel, gbc, 7, "Unit Cost:", unitCostField);
        addFormRow(panel, gbc, 8, "Vendor:", vendorField);
        addFormRow(panel, gbc, 9, "Bin:", binLocationField);
        addFormRow(panel, gbc, 10, "", activeBox);
        addFormRow(panel, gbc, 11, "Notes:", new JScrollPane(notesArea));

        JButton newButton = new JButton("New");
        JButton deleteButton = new JButton("Delete");
        JButton saveButton = new JButton("Save");
        newButton.addActionListener(e -> clearEditor());
        deleteButton.addActionListener(e -> deletePart());
        saveButton.addActionListener(e -> savePart());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(newButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(saveButton);

        gbc.gridx = 0;
        gbc.gridy = 12;
        gbc.gridwidth = 2;
        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.SOUTH;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 0, 0, 0);
        panel.add(buttonPanel, gbc);
        return panel;
    }

    private void loadParts() {
        tableModel.setRowCount(0);
        String search = searchField.getText().trim();
        String sql = """
                SELECT part_id, part_name, COALESCE(part_number, '') AS part_number,
                       quantity_on_hand, reorder_point, COALESCE(vendor_name, '') AS vendor_name, is_active
                FROM maintenance_parts
                """ + (search.isBlank() ? "" : """
                WHERE part_name ILIKE ?
                   OR COALESCE(part_number, '') ILIKE ?
                   OR COALESCE(category, '') ILIKE ?
                   OR COALESCE(vendor_name, '') ILIKE ?
                   OR COALESCE(bin_location, '') ILIKE ?
                """) + " ORDER BY part_name";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (!search.isBlank()) {
                String pattern = "%" + search + "%";
                for (int i = 1; i <= 5; i++) {
                    ps.setString(i, pattern);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tableModel.addRow(new Object[]{
                            rs.getInt("part_id"),
                            rs.getString("part_name"),
                            rs.getString("part_number"),
                            rs.getBigDecimal("quantity_on_hand"),
                            rs.getBigDecimal("reorder_point"),
                            rs.getString("vendor_name"),
                            rs.getBoolean("is_active") ? "Yes" : "No"
                    });
                }
            }
        } catch (SQLException ex) {
            showError("load parts", ex);
        }
    }

    private void loadSelectedPart() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        Integer id = (Integer) tableModel.getValueAt(table.convertRowIndexToModel(row), 0);
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM maintenance_parts WHERE part_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    selectedPartId = id;
                    nameField.setText(rs.getString("part_name"));
                    partNumberField.setText(value(rs.getString("part_number")));
                    categoryField.setText(value(rs.getString("category")));
                    quantityField.setText(rs.getBigDecimal("quantity_on_hand").toPlainString());
                    reorderPointField.setText(rs.getBigDecimal("reorder_point").toPlainString());
                    reorderQuantityField.setText(rs.getBigDecimal("reorder_quantity").toPlainString());
                    unitCostField.setText(rs.getBigDecimal("unit_cost").toPlainString());
                    vendorField.setText(value(rs.getString("vendor_name")));
                    binLocationField.setText(value(rs.getString("bin_location")));
                    activeBox.setSelected(rs.getBoolean("is_active"));
                    notesArea.setText(value(rs.getString("notes")));
                }
            }
        } catch (SQLException ex) {
            showError("load selected part", ex);
        }
    }

    private void savePart() {
        String name = nameField.getText().trim();
        if (name.isBlank()) {
            showWarning("Part name is required.");
            return;
        }
        String insertSql = """
                INSERT INTO maintenance_parts (
                    part_name, part_number, category, quantity_on_hand, reorder_point,
                    reorder_quantity, unit_cost, vendor_name, bin_location, is_active, notes
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String updateSql = """
                UPDATE maintenance_parts
                SET part_name = ?, part_number = ?, category = ?, quantity_on_hand = ?,
                    reorder_point = ?, reorder_quantity = ?, unit_cost = ?, vendor_name = ?,
                    bin_location = ?, is_active = ?, notes = ?, updated_at = CURRENT_TIMESTAMP
                WHERE part_id = ?
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectedPartId == null ? insertSql : updateSql)) {
            ps.setString(1, name);
            ps.setString(2, nullable(partNumberField));
            ps.setString(3, nullable(categoryField));
            ps.setBigDecimal(4, decimal(quantityField, "On hand"));
            ps.setBigDecimal(5, decimal(reorderPointField, "Reorder point"));
            ps.setBigDecimal(6, decimal(reorderQuantityField, "Reorder quantity"));
            ps.setBigDecimal(7, moneyDecimal(unitCostField, "Unit cost"));
            ps.setString(8, nullable(vendorField));
            ps.setString(9, nullable(binLocationField));
            ps.setBoolean(10, activeBox.isSelected());
            ps.setString(11, nullable(notesArea));
            if (selectedPartId != null) {
                ps.setInt(12, selectedPartId);
            }
            ps.executeUpdate();
            clearEditor();
            loadParts();
        } catch (SQLException | IllegalArgumentException ex) {
            showError("save part", ex);
        }
    }

    private void deletePart() {
        if (selectedPartId == null) {
            showWarning("Select a part to delete.");
            return;
        }
        int result = JOptionPane.showConfirmDialog(
                this,
                "Delete this part? Ticket usage can prevent deletion.",
                "Delete Part",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM maintenance_parts WHERE part_id = ?")) {
            ps.setInt(1, selectedPartId);
            ps.executeUpdate();
            clearEditor();
            loadParts();
        } catch (SQLException ex) {
            showError("delete part", ex);
        }
    }

    private void clearEditor() {
        selectedPartId = null;
        table.clearSelection();
        nameField.setText("");
        partNumberField.setText("");
        categoryField.setText("");
        quantityField.setText("0");
        reorderPointField.setText("0");
        reorderQuantityField.setText("0");
        unitCostField.setText("0");
        vendorField.setText("");
        binLocationField.setText("");
        activeBox.setSelected(true);
        notesArea.setText("");
    }

    private static void addFormRow(JPanel panel, GridBagConstraints gbc, int row, String label, Component field) {
        gbc.gridwidth = 1;
        gbc.weighty = 0;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, 10, 8);
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 10, 0);
        panel.add(field, gbc);
    }

    private static String nullable(JTextField field) {
        String value = field.getText().trim();
        return value.isEmpty() ? null : value;
    }

    private static String nullable(JTextArea area) {
        String value = area.getText().trim();
        return value.isEmpty() ? null : value;
    }

    private static BigDecimal decimal(JTextField field, String label) {
        String value = field.getText().trim();
        if (value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " must be a number.");
        }
    }

    private static BigDecimal moneyDecimal(JTextField field, String label) {
        return utils.CurrencyFormatter.normalize(decimal(field, label));
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "Parts List", JOptionPane.WARNING_MESSAGE);
    }

    private void showError(String action, Exception ex) {
        JOptionPane.showMessageDialog(this, "Could not " + action + ".\n\n" + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
    }
}
