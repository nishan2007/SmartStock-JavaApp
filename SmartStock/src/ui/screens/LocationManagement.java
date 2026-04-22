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
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocationManagement extends JFrame {
    private static final String DEFAULT_TIMEZONE = "America/New_York";

    private final JTextField searchField = new JTextField();
    private final JTextField nameField = new JTextField();
    private final JTextArea addressArea = new JTextArea(4, 24);
    private final JComboBox<String> timezoneBox = new JComboBox<>();
    private final DefaultTableModel tableModel;
    private final JTable locationTable;
    private Integer selectedLocationId;
    private boolean hasTimezoneColumn;

    public LocationManagement() {
        setTitle("Location Management");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(14, 14));
        setJMenuBar(AppMenuBar.create(this, "LocationManagement"));

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.setBackground(new Color(245, 247, 250));

        tableModel = new DefaultTableModel(new Object[]{"ID", "Store Name", "Address", "Timezone"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        locationTable = new JTable(tableModel);
        locationTable.setRowHeight(28);
        locationTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        locationTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectCurrentRow();
            }
        });

        configureTimezoneBox();
        root.add(buildHeaderPanel(), BorderLayout.NORTH);
        root.add(new JScrollPane(locationTable), BorderLayout.CENTER);
        root.add(buildEditorPanel(), BorderLayout.EAST);
        add(root, BorderLayout.CENTER);

        loadLocations();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 8));
        panel.setOpaque(false);

        JLabel titleLabel = new JLabel("Location Management");
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

        searchButton.addActionListener(e -> loadLocations());
        searchField.addActionListener(e -> loadLocations());
        refreshButton.addActionListener(e -> {
            searchField.setText("");
            loadLocations();
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
        panel.setPreferredSize(new Dimension(380, 0));

        addressArea.setLineWrap(true);
        addressArea.setWrapStyleWord(true);

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
        JLabel editorTitle = new JLabel("Location Details");
        editorTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        panel.add(editorTitle, gbc);

        addFormRow(panel, gbc, 1, "Name:", nameField);
        addFormRow(panel, gbc, 2, "Address:", new JScrollPane(addressArea));
        addFormRow(panel, gbc, 3, "Timezone:", timezoneBox);

        JLabel timezoneHelp = new JLabel("<html><div style='width:230px;color:#6b7280;'>Used for End of Day date boundaries and store reports.</div></html>");
        timezoneHelp.setFont(new Font("SansSerif", Font.PLAIN, 12));
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(-4, 0, 14, 0);
        panel.add(timezoneHelp, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(newButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(saveButton);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.SOUTH;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 0, 0, 0);
        panel.add(buttonPanel, gbc);

        newButton.addActionListener(e -> clearEditor());
        clearButton.addActionListener(e -> clearEditor());
        saveButton.addActionListener(e -> saveLocation());

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

    private void configureTimezoneBox() {
        timezoneBox.setEditable(true);
        List<String> zones = new ArrayList<>(ZoneId.getAvailableZoneIds());
        Collections.sort(zones);
        for (String zone : zones) {
            timezoneBox.addItem(zone);
        }
        timezoneBox.setSelectedItem(DEFAULT_TIMEZONE);
    }

    private void loadLocations() {
        tableModel.setRowCount(0);
        hasTimezoneColumn = hasColumn("locations", "timezone");
        String search = searchField.getText().trim();
        String sql = "SELECT location_id, name, COALESCE(address, '') AS address"
                + (hasTimezoneColumn ? ", COALESCE(timezone, ?) AS timezone" : ", ? AS timezone")
                + " FROM locations"
                + (search.isBlank() ? "" : " WHERE name ILIKE ? OR COALESCE(address, '') ILIKE ? OR CAST(location_id AS TEXT) LIKE ?")
                + " ORDER BY location_id";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, DEFAULT_TIMEZONE);
            if (!search.isBlank()) {
                String pattern = "%" + search + "%";
                ps.setString(2, pattern);
                ps.setString(3, pattern);
                ps.setString(4, pattern);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tableModel.addRow(new Object[]{
                            rs.getInt("location_id"),
                            rs.getString("name"),
                            rs.getString("address"),
                            rs.getString("timezone")
                    });
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load locations: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
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
        int row = locationTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = locationTable.convertRowIndexToModel(row);
        selectedLocationId = Integer.parseInt(String.valueOf(tableModel.getValueAt(modelRow, 0)));
        nameField.setText(String.valueOf(tableModel.getValueAt(modelRow, 1)));
        addressArea.setText(String.valueOf(tableModel.getValueAt(modelRow, 2)));
        timezoneBox.setSelectedItem(String.valueOf(tableModel.getValueAt(modelRow, 3)));
    }

    private void saveLocation() {
        String name = nameField.getText().trim();
        String address = addressArea.getText().trim();
        String timezone = getTimezoneValue();

        if (name.isBlank()) {
            JOptionPane.showMessageDialog(this, "Location name is required.");
            return;
        }
        if (!isValidTimezone(timezone)) {
            JOptionPane.showMessageDialog(this, "Enter a valid timezone, such as America/New_York.", "Invalid Timezone", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String sql;
        if (selectedLocationId == null) {
            sql = hasTimezoneColumn
                    ? "INSERT INTO locations (name, address, timezone) VALUES (?, ?, ?)"
                    : "INSERT INTO locations (name, address) VALUES (?, ?)";
        } else {
            sql = hasTimezoneColumn
                    ? "UPDATE locations SET name = ?, address = ?, timezone = ? WHERE location_id = ?"
                    : "UPDATE locations SET name = ?, address = ? WHERE location_id = ?";
        }

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, emptyToNull(address));
            if (hasTimezoneColumn) {
                ps.setString(3, timezone);
                if (selectedLocationId != null) {
                    ps.setInt(4, selectedLocationId);
                }
            } else if (selectedLocationId != null) {
                ps.setInt(3, selectedLocationId);
            }

            ps.executeUpdate();
            clearEditor();
            loadLocations();
            JOptionPane.showMessageDialog(this, "Location saved.");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save location: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String getTimezoneValue() {
        Object selected = timezoneBox.getEditor().getItem();
        String timezone = selected == null ? "" : selected.toString().trim();
        return timezone.isBlank() ? DEFAULT_TIMEZONE : timezone;
    }

    private boolean isValidTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
            return true;
        } catch (ZoneRulesException ex) {
            return false;
        }
    }

    private String emptyToNull(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void clearEditor() {
        selectedLocationId = null;
        locationTable.clearSelection();
        nameField.setText("");
        addressArea.setText("");
        timezoneBox.setSelectedItem(DEFAULT_TIMEZONE);
        nameField.requestFocusInWindow();
    }
}
