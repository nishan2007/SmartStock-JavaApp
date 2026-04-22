package ui.screens;

import data.DB;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CustomerTypeList extends JFrame {
    private final JTextField searchField = new JTextField();
    private final JTextField nameField = new JTextField();
    private final JTextArea descriptionArea = new JTextArea(4, 24);
    private final JCheckBox activeBox = new JCheckBox("Active", true);
    private final DefaultTableModel tableModel;
    private final JTable typeTable;
    private Integer selectedTypeId;

    public CustomerTypeList() {
        setTitle("Customer Types");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(14, 14));
        setJMenuBar(AppMenuBar.create(this, "CustomerTypeList"));

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.setBackground(new Color(245, 247, 250));

        tableModel = new DefaultTableModel(new Object[]{"ID", "Customer Type", "Description", "Active"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        typeTable = new JTable(tableModel);
        typeTable.setRowHeight(28);
        typeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        typeTable.getColumnModel().getColumn(0).setMaxWidth(70);
        typeTable.getColumnModel().getColumn(3).setMaxWidth(80);
        typeTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectCurrentRow();
            }
        });

        root.add(buildHeaderPanel(), BorderLayout.NORTH);
        root.add(new JScrollPane(typeTable), BorderLayout.CENTER);
        root.add(buildEditorPanel(), BorderLayout.EAST);
        add(root, BorderLayout.CENTER);

        loadCustomerTypes();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setOpaque(false);

        JLabel titleLabel = new JLabel("Customer Types");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setForeground(new Color(31, 41, 55));

        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setOpaque(false);
        JButton searchButton = new JButton("Search");
        JButton refreshButton = new JButton("Refresh");
        searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(searchButton);
        buttons.add(refreshButton);
        searchPanel.add(buttons, BorderLayout.EAST);

        searchButton.addActionListener(e -> loadCustomerTypes());
        searchField.addActionListener(e -> loadCustomerTypes());
        refreshButton.addActionListener(e -> {
            searchField.setText("");
            loadCustomerTypes();
        });

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(searchPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildEditorPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(16, 16, 16, 16)
        ));
        panel.setPreferredSize(new Dimension(340, 0));

        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);

        JButton newButton = new JButton("New");
        JButton saveButton = new JButton("Save");
        JButton clearButton = new JButton("Clear");

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 12, 0);
        JLabel editorTitle = new JLabel("Type Details");
        editorTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        panel.add(editorTitle, gbc);

        addFormRow(panel, gbc, 1, "Name:", nameField);
        addFormRow(panel, gbc, 2, "Description:", new JScrollPane(descriptionArea));
        addFormRow(panel, gbc, 3, "Status:", activeBox);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(newButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(saveButton);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.SOUTH;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(buttonPanel, gbc);

        newButton.addActionListener(e -> clearEditor());
        clearButton.addActionListener(e -> clearEditor());
        saveButton.addActionListener(e -> saveCustomerType());

        return panel;
    }

    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, String label, Component field) {
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

    private void loadCustomerTypes() {
        tableModel.setRowCount(0);
        String search = searchField.getText().trim();
        String sql = """
                SELECT customer_type_id, name, COALESCE(description, '') AS description, COALESCE(is_active, TRUE) AS is_active
                FROM customer_types
                """;
        if (!search.isBlank()) {
            sql += " WHERE name ILIKE ? OR COALESCE(description, '') ILIKE ?";
        }
        sql += " ORDER BY name";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (!search.isBlank()) {
                String pattern = "%" + search + "%";
                ps.setString(1, pattern);
                ps.setString(2, pattern);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tableModel.addRow(new Object[]{
                            rs.getInt("customer_type_id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getBoolean("is_active")
                    });
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load customer types: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void selectCurrentRow() {
        int row = typeTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = typeTable.convertRowIndexToModel(row);
        selectedTypeId = Integer.parseInt(String.valueOf(tableModel.getValueAt(modelRow, 0)));
        nameField.setText(String.valueOf(tableModel.getValueAt(modelRow, 1)));
        descriptionArea.setText(String.valueOf(tableModel.getValueAt(modelRow, 2)));
        activeBox.setSelected(Boolean.TRUE.equals(tableModel.getValueAt(modelRow, 3)));
    }

    private void saveCustomerType() {
        String name = nameField.getText().trim();
        String description = descriptionArea.getText().trim();
        if (name.isBlank()) {
            JOptionPane.showMessageDialog(this, "Customer type name is required.");
            return;
        }

        String sql = selectedTypeId == null
                ? "INSERT INTO customer_types (name, description, is_active) VALUES (?, ?, ?)"
                : "UPDATE customer_types SET name = ?, description = ?, is_active = ? WHERE customer_type_id = ?";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, description.isBlank() ? null : description);
            ps.setBoolean(3, activeBox.isSelected());
            if (selectedTypeId != null) {
                ps.setInt(4, selectedTypeId);
            }
            ps.executeUpdate();
            clearEditor();
            loadCustomerTypes();
            JOptionPane.showMessageDialog(this, "Customer type saved.");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save customer type: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearEditor() {
        selectedTypeId = null;
        typeTable.clearSelection();
        nameField.setText("");
        descriptionArea.setText("");
        activeBox.setSelected(true);
        nameField.requestFocusInWindow();
    }
}
