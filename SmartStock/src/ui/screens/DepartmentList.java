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

public class DepartmentList extends JFrame {
    private final JTextField searchField = new JTextField();
    private final JTextField nameField = new JTextField();
    private final JTextArea descriptionArea = new JTextArea(4, 24);
    private final DefaultTableModel tableModel;
    private final JTable departmentTable;
    private Integer selectedDepartmentId;
    private boolean hasDescriptionColumn;

    public DepartmentList() {
        setTitle("Department List");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(14, 14));
        setJMenuBar(AppMenuBar.create(this, "DepartmentList"));

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.setBackground(new Color(245, 247, 250));

        tableModel = new DefaultTableModel(new Object[]{"ID", "Department", "Description"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        departmentTable = new JTable(tableModel);
        departmentTable.setRowHeight(28);
        departmentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        departmentTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectCurrentRow();
            }
        });

        root.add(buildHeaderPanel(), BorderLayout.NORTH);
        root.add(new JScrollPane(departmentTable), BorderLayout.CENTER);
        root.add(buildEditorPanel(), BorderLayout.EAST);
        add(root, BorderLayout.CENTER);

        loadDepartments();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setOpaque(false);

        JLabel titleLabel = new JLabel("Department List");
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

        searchButton.addActionListener(e -> loadDepartments());
        searchField.addActionListener(e -> loadDepartments());
        refreshButton.addActionListener(e -> {
            searchField.setText("");
            loadDepartments();
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
        JLabel editorTitle = new JLabel("Department Details");
        editorTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        panel.add(editorTitle, gbc);

        addFormRow(panel, gbc, 1, "Name:", nameField);
        addFormRow(panel, gbc, 2, "Description:", new JScrollPane(descriptionArea));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(newButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(saveButton);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.SOUTH;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(buttonPanel, gbc);

        newButton.addActionListener(e -> clearEditor());
        clearButton.addActionListener(e -> clearEditor());
        saveButton.addActionListener(e -> saveDepartment());

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

    private void loadDepartments() {
        tableModel.setRowCount(0);
        hasDescriptionColumn = hasColumn("categories", "description");
        String search = searchField.getText().trim();
        String sql = "SELECT category_id, name"
                + (hasDescriptionColumn ? ", COALESCE(description, '') AS description" : ", '' AS description")
                + " FROM categories"
                + (search.isBlank() ? "" : hasDescriptionColumn ? " WHERE name ILIKE ? OR COALESCE(description, '') ILIKE ?" : " WHERE name ILIKE ?")
                + " ORDER BY name";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (!search.isBlank()) {
                String pattern = "%" + search + "%";
                ps.setString(1, pattern);
                if (hasDescriptionColumn) {
                    ps.setString(2, pattern);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tableModel.addRow(new Object[]{
                            rs.getInt("category_id"),
                            rs.getString("name"),
                            rs.getString("description")
                    });
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load departments: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean hasColumn(String tableName, String columnName) {
        String sql = """
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            return false;
        }
    }

    private void selectCurrentRow() {
        int row = departmentTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = departmentTable.convertRowIndexToModel(row);
        selectedDepartmentId = Integer.parseInt(String.valueOf(tableModel.getValueAt(modelRow, 0)));
        nameField.setText(String.valueOf(tableModel.getValueAt(modelRow, 1)));
        descriptionArea.setText(String.valueOf(tableModel.getValueAt(modelRow, 2)));
    }

    private void saveDepartment() {
        String name = nameField.getText().trim();
        String description = descriptionArea.getText().trim();
        if (name.isBlank()) {
            JOptionPane.showMessageDialog(this, "Department name is required.");
            return;
        }

        String sql;
        if (selectedDepartmentId == null) {
            sql = hasDescriptionColumn
                    ? "INSERT INTO categories (name, description) VALUES (?, ?)"
                    : "INSERT INTO categories (name) VALUES (?)";
        } else {
            sql = hasDescriptionColumn
                    ? "UPDATE categories SET name = ?, description = ? WHERE category_id = ?"
                    : "UPDATE categories SET name = ? WHERE category_id = ?";
        }

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            if (selectedDepartmentId == null) {
                if (hasDescriptionColumn) {
                    ps.setString(2, description);
                }
            } else if (hasDescriptionColumn) {
                ps.setString(2, description);
                ps.setInt(3, selectedDepartmentId);
            } else {
                ps.setInt(2, selectedDepartmentId);
            }
            ps.executeUpdate();
            clearEditor();
            loadDepartments();
            JOptionPane.showMessageDialog(this, "Department saved.");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save department: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearEditor() {
        selectedDepartmentId = null;
        departmentTable.clearSelection();
        nameField.setText("");
        descriptionArea.setText("");
        nameField.requestFocusInWindow();
    }
}
