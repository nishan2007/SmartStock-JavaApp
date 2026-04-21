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

public class VendorList extends JFrame {
    private final JTextField searchField = new JTextField();
    private final JTextField nameField = new JTextField();
    private final JTextField contactField = new JTextField();
    private final JTextField phoneField = new JTextField();
    private final JTextField emailField = new JTextField();
    private final JTextArea addressArea = new JTextArea(3, 24);
    private final JTextArea notesArea = new JTextArea(4, 24);
    private final JCheckBox activeCheckBox = new JCheckBox("Active", true);
    private final DefaultTableModel tableModel;
    private final JTable vendorTable;
    private Integer selectedVendorId;

    public VendorList() {
        setTitle("Vendor List");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(14, 14));
        setJMenuBar(AppMenuBar.create(this, "VendorList"));

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.setBackground(new Color(245, 247, 250));

        tableModel = new DefaultTableModel(new Object[]{"ID", "Vendor", "Contact", "Phone", "Email", "Active"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        vendorTable = new JTable(tableModel);
        vendorTable.setRowHeight(28);
        vendorTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        vendorTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectCurrentRow();
            }
        });

        root.add(buildHeaderPanel(), BorderLayout.NORTH);
        root.add(new JScrollPane(vendorTable), BorderLayout.CENTER);
        root.add(buildEditorPanel(), BorderLayout.EAST);
        add(root, BorderLayout.CENTER);

        loadVendors();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setOpaque(false);

        JLabel titleLabel = new JLabel("Vendor List");
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

        searchButton.addActionListener(e -> loadVendors());
        searchField.addActionListener(e -> loadVendors());
        refreshButton.addActionListener(e -> {
            searchField.setText("");
            loadVendors();
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
        panel.setPreferredSize(new Dimension(360, 0));

        addressArea.setLineWrap(true);
        addressArea.setWrapStyleWord(true);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);

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
        JLabel editorTitle = new JLabel("Vendor Details");
        editorTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        panel.add(editorTitle, gbc);

        addFormRow(panel, gbc, 1, "Name:", nameField);
        addFormRow(panel, gbc, 2, "Contact:", contactField);
        addFormRow(panel, gbc, 3, "Phone:", phoneField);
        addFormRow(panel, gbc, 4, "Email:", emailField);
        addFormRow(panel, gbc, 5, "Address:", new JScrollPane(addressArea));
        addFormRow(panel, gbc, 6, "Notes:", new JScrollPane(notesArea));
        addFormRow(panel, gbc, 7, "", activeCheckBox);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(newButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(saveButton);

        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.gridwidth = 2;
        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.SOUTH;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(buttonPanel, gbc);

        newButton.addActionListener(e -> clearEditor());
        clearButton.addActionListener(e -> clearEditor());
        saveButton.addActionListener(e -> saveVendor());

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

    private void loadVendors() {
        tableModel.setRowCount(0);
        String search = searchField.getText().trim();
        String sql = """
                SELECT vendor_id,
                       name,
                       COALESCE(contact_name, '') AS contact_name,
                       COALESCE(phone, '') AS phone,
                       COALESCE(email, '') AS email,
                       COALESCE(address, '') AS address,
                       COALESCE(notes, '') AS notes,
                       COALESCE(is_active, TRUE) AS is_active
                FROM vendors
                """ + (search.isBlank() ? "" : """
                WHERE name ILIKE ?
                   OR COALESCE(contact_name, '') ILIKE ?
                   OR COALESCE(phone, '') ILIKE ?
                   OR COALESCE(email, '') ILIKE ?
                   OR COALESCE(notes, '') ILIKE ?
                """) + " ORDER BY name";

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
                            rs.getInt("vendor_id"),
                            rs.getString("name"),
                            rs.getString("contact_name"),
                            rs.getString("phone"),
                            rs.getString("email"),
                            rs.getBoolean("is_active") ? "Yes" : "No"
                    });
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load vendors: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void selectCurrentRow() {
        int row = vendorTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = vendorTable.convertRowIndexToModel(row);
        selectedVendorId = Integer.parseInt(String.valueOf(tableModel.getValueAt(modelRow, 0)));

        String sql = """
                SELECT name,
                       COALESCE(contact_name, '') AS contact_name,
                       COALESCE(phone, '') AS phone,
                       COALESCE(email, '') AS email,
                       COALESCE(address, '') AS address,
                       COALESCE(notes, '') AS notes,
                       COALESCE(is_active, TRUE) AS is_active
                FROM vendors
                WHERE vendor_id = ?
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, selectedVendorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    nameField.setText(rs.getString("name"));
                    contactField.setText(rs.getString("contact_name"));
                    phoneField.setText(rs.getString("phone"));
                    emailField.setText(rs.getString("email"));
                    addressArea.setText(rs.getString("address"));
                    notesArea.setText(rs.getString("notes"));
                    activeCheckBox.setSelected(rs.getBoolean("is_active"));
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load vendor details: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveVendor() {
        String name = nameField.getText().trim();
        if (name.isBlank()) {
            JOptionPane.showMessageDialog(this, "Vendor name is required.");
            return;
        }

        String sql = selectedVendorId == null
                ? "INSERT INTO vendors (name, contact_name, phone, email, address, notes, is_active) VALUES (?, ?, ?, ?, ?, ?, ?)"
                : "UPDATE vendors SET name = ?, contact_name = ?, phone = ?, email = ?, address = ?, notes = ?, is_active = ? WHERE vendor_id = ?";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, emptyToNull(contactField.getText()));
            ps.setString(3, emptyToNull(phoneField.getText()));
            ps.setString(4, emptyToNull(emailField.getText()));
            ps.setString(5, emptyToNull(addressArea.getText()));
            ps.setString(6, emptyToNull(notesArea.getText()));
            ps.setBoolean(7, activeCheckBox.isSelected());
            if (selectedVendorId != null) {
                ps.setInt(8, selectedVendorId);
            }
            ps.executeUpdate();
            clearEditor();
            loadVendors();
            JOptionPane.showMessageDialog(this, "Vendor saved.");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save vendor: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String emptyToNull(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void clearEditor() {
        selectedVendorId = null;
        vendorTable.clearSelection();
        nameField.setText("");
        contactField.setText("");
        phoneField.setText("");
        emailField.setText("");
        addressArea.setText("");
        notesArea.setText("");
        activeCheckBox.setSelected(true);
        nameField.requestFocusInWindow();
    }
}
