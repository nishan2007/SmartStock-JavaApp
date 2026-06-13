package ui.screens;

import data.DB;
import services.OfflineWriteGuard;

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

public class LocationManagementPanel extends JPanel {
    private static final String DEFAULT_TIMEZONE = "America/New_York";

    private final JTextField searchField = new JTextField();
    private final JTextField nameField = new JTextField();
    private final JTextField storeCodeField = new JTextField("0001");
    private final JTextArea addressArea = new JTextArea(4, 24);
    private final JTextField addressLine1Field = new JTextField();
    private final JTextField addressLine2Field = new JTextField();
    private final JTextField addressLine3Field = new JTextField();
    private final JTextField phoneLine1Field = new JTextField();
    private final JTextField phoneLine2Field = new JTextField();
    private final JTextField emailLine1Field = new JTextField();
    private final JTextField emailLine2Field = new JTextField();
    private final JComboBox<String> timezoneBox = new JComboBox<>();
    private final DefaultTableModel tableModel;
    private final JTable locationTable;
    private Integer selectedLocationId;
    private boolean hasTimezoneColumn;
    private boolean hasIdentityColumns;

    public LocationManagementPanel() {
        setLayout(new BorderLayout(14, 14));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setOpaque(false);

        tableModel = new DefaultTableModel(new Object[]{
                "ID", "Store Name", "Store Code", "Address", "Address Line 1", "Address Line 2", "Address Line 3",
                "Phone Line 1", "Phone Line 2", "Email Line 1", "Email Line 2", "Timezone"
        }, 0) {
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
        add(buildHeaderPanel(), BorderLayout.NORTH);
        add(new JScrollPane(locationTable), BorderLayout.CENTER);
        add(buildEditorPanel(), BorderLayout.EAST);
        ensureLocationIdentitySchema();
        loadLocations();
    }

    private JPanel buildHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 8));
        panel.setOpaque(false);

        JLabel titleLabel = new JLabel("Locations");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
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
        addFormRow(panel, gbc, 2, "Store Code:", storeCodeField);
        addFormRow(panel, gbc, 3, "Address:", new JScrollPane(addressArea));
        addFormRow(panel, gbc, 4, "Address Line 1:", addressLine1Field);
        addFormRow(panel, gbc, 5, "Address Line 2:", addressLine2Field);
        addFormRow(panel, gbc, 6, "Address Line 3:", addressLine3Field);
        addFormRow(panel, gbc, 7, "Phone Line 1:", phoneLine1Field);
        addFormRow(panel, gbc, 8, "Phone Line 2:", phoneLine2Field);
        addFormRow(panel, gbc, 9, "Email Line 1:", emailLine1Field);
        addFormRow(panel, gbc, 10, "Email Line 2:", emailLine2Field);
        addFormRow(panel, gbc, 11, "Timezone:", timezoneBox);

        JLabel timezoneHelp = new JLabel("<html><div style='width:230px;color:#6b7280;'>Used for report date boundaries and store totals.</div></html>");
        timezoneHelp.setFont(new Font("SansSerif", Font.PLAIN, 12));
        gbc.gridx = 1;
        gbc.gridy = 12;
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
        gbc.gridy = 13;
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
        hasIdentityColumns = hasColumn("locations", "company_address_line1")
                && hasColumn("locations", "company_address_line2")
                && hasColumn("locations", "company_address_line3")
                && hasColumn("locations", "company_phone_line1")
                && hasColumn("locations", "company_phone_line2")
                && hasColumn("locations", "company_email_line1")
                && hasColumn("locations", "company_email_line2");
        String search = searchField.getText().trim();
        String searchWhere = "";
        if (!search.isBlank()) {
            searchWhere = hasIdentityColumns
                    ? " WHERE name ILIKE ? OR COALESCE(address, '') ILIKE ? OR COALESCE(company_phone_line1, '') ILIKE ? OR COALESCE(company_email_line1, '') ILIKE ? OR CAST(location_id AS TEXT) LIKE ?"
                    : " WHERE name ILIKE ? OR COALESCE(address, '') ILIKE ? OR CAST(location_id AS TEXT) LIKE ?";
        }
        String sql = "SELECT location_id, name, COALESCE(receipt_store_code, '0001') AS receipt_store_code, COALESCE(address, '') AS address"
                + (hasIdentityColumns
                ? ", COALESCE(company_address_line1, '') AS company_address_line1"
                + ", COALESCE(company_address_line2, '') AS company_address_line2"
                + ", COALESCE(company_address_line3, '') AS company_address_line3"
                + ", COALESCE(company_phone_line1, '') AS company_phone_line1"
                + ", COALESCE(company_phone_line2, '') AS company_phone_line2"
                + ", COALESCE(company_email_line1, '') AS company_email_line1"
                + ", COALESCE(company_email_line2, '') AS company_email_line2"
                : ", '' AS company_address_line1, '' AS company_address_line2, '' AS company_address_line3"
                + ", '' AS company_phone_line1, '' AS company_phone_line2, '' AS company_email_line1, '' AS company_email_line2")
                + (hasTimezoneColumn ? ", COALESCE(timezone, ?) AS timezone" : ", ? AS timezone")
                + " FROM locations"
                + searchWhere
                + " ORDER BY location_id";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, DEFAULT_TIMEZONE);
            if (!search.isBlank()) {
                String pattern = "%" + search + "%";
                ps.setString(2, pattern);
                ps.setString(3, pattern);
                ps.setString(4, pattern);
                if (hasIdentityColumns) {
                    ps.setString(5, pattern);
                    ps.setString(6, pattern);
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tableModel.addRow(new Object[]{
                            rs.getInt("location_id"),
                            rs.getString("name"),
                            rs.getString("receipt_store_code"),
                            rs.getString("address"),
                            rs.getString("company_address_line1"),
                            rs.getString("company_address_line2"),
                            rs.getString("company_address_line3"),
                            rs.getString("company_phone_line1"),
                            rs.getString("company_phone_line2"),
                            rs.getString("company_email_line1"),
                            rs.getString("company_email_line2"),
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
        storeCodeField.setText(String.valueOf(tableModel.getValueAt(modelRow, 2)));
        addressArea.setText(String.valueOf(tableModel.getValueAt(modelRow, 3)));
        addressLine1Field.setText(String.valueOf(tableModel.getValueAt(modelRow, 4)));
        addressLine2Field.setText(String.valueOf(tableModel.getValueAt(modelRow, 5)));
        addressLine3Field.setText(String.valueOf(tableModel.getValueAt(modelRow, 6)));
        phoneLine1Field.setText(String.valueOf(tableModel.getValueAt(modelRow, 7)));
        phoneLine2Field.setText(String.valueOf(tableModel.getValueAt(modelRow, 8)));
        emailLine1Field.setText(String.valueOf(tableModel.getValueAt(modelRow, 9)));
        emailLine2Field.setText(String.valueOf(tableModel.getValueAt(modelRow, 10)));
        timezoneBox.setSelectedItem(String.valueOf(tableModel.getValueAt(modelRow, 11)));
    }

    private void saveLocation() {
        try {
            OfflineWriteGuard.requireCloudForGlobalWrite("Location setup");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cloud Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String name = nameField.getText().trim();
        String storeCode = sanitizeStoreCode(storeCodeField.getText());
        String address = addressArea.getText().trim();
        String addressLine1 = addressLine1Field.getText().trim();
        String addressLine2 = addressLine2Field.getText().trim();
        String addressLine3 = addressLine3Field.getText().trim();
        String phoneLine1 = phoneLine1Field.getText().trim();
        String phoneLine2 = phoneLine2Field.getText().trim();
        String emailLine1 = emailLine1Field.getText().trim();
        String emailLine2 = emailLine2Field.getText().trim();
        String timezone = getTimezoneValue();

        if (name.isBlank()) {
            JOptionPane.showMessageDialog(this, "Location name is required.");
            return;
        }
        if (!isValidTimezone(timezone)) {
            JOptionPane.showMessageDialog(this, "Enter a valid timezone, such as America/New_York.", "Invalid Timezone", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (storeCode.isBlank()) {
            JOptionPane.showMessageDialog(this, "Store code is required (0001-9999).");
            return;
        }

        String sql;
        if (selectedLocationId == null) {
            sql = hasTimezoneColumn
                    ? "INSERT INTO locations (name, receipt_store_code, address, company_address_line1, company_address_line2, company_address_line3, company_phone_line1, company_phone_line2, company_email_line1, company_email_line2, timezone) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    : "INSERT INTO locations (name, receipt_store_code, address, company_address_line1, company_address_line2, company_address_line3, company_phone_line1, company_phone_line2, company_email_line1, company_email_line2) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        } else {
            sql = hasTimezoneColumn
                    ? "UPDATE locations SET name = ?, receipt_store_code = ?, address = ?, company_address_line1 = ?, company_address_line2 = ?, company_address_line3 = ?, company_phone_line1 = ?, company_phone_line2 = ?, company_email_line1 = ?, company_email_line2 = ?, timezone = ? WHERE location_id = ?"
                    : "UPDATE locations SET name = ?, receipt_store_code = ?, address = ?, company_address_line1 = ?, company_address_line2 = ?, company_address_line3 = ?, company_phone_line1 = ?, company_phone_line2 = ?, company_email_line1 = ?, company_email_line2 = ? WHERE location_id = ?";
        }

        try (Connection conn = DB.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, storeCode);
            ps.setString(3, emptyToNull(address));
            ps.setString(4, addressLine1);
            ps.setString(5, addressLine2);
            ps.setString(6, addressLine3);
            ps.setString(7, phoneLine1);
            ps.setString(8, phoneLine2);
            ps.setString(9, emailLine1);
            ps.setString(10, emailLine2);
            if (hasTimezoneColumn) {
                ps.setString(11, timezone);
                if (selectedLocationId != null) {
                    ps.setInt(12, selectedLocationId);
                }
            } else if (selectedLocationId != null) {
                ps.setInt(11, selectedLocationId);
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
        storeCodeField.setText("0001");
        addressArea.setText("");
        addressLine1Field.setText("");
        addressLine2Field.setText("");
        addressLine3Field.setText("");
        phoneLine1Field.setText("");
        phoneLine2Field.setText("");
        emailLine1Field.setText("");
        emailLine2Field.setText("");
        timezoneBox.setSelectedItem(DEFAULT_TIMEZONE);
        nameField.requestFocusInWindow();
    }

    private void ensureLocationIdentitySchema() {
        try (Connection conn = DB.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_address_line1 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_address_line2 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_address_line3 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_phone_line1 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_phone_line2 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_email_line1 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_email_line2 TEXT NOT NULL DEFAULT ''");
            if (hasTable("company_customization")) {
                stmt.executeUpdate("""
                        DO $$
                        BEGIN
                            IF EXISTS (
                                SELECT 1
                                FROM information_schema.columns
                                WHERE table_schema = 'public'
                                  AND table_name = 'company_customization'
                                  AND column_name = 'company_address_line1'
                            ) THEN
                                EXECUTE $sql$
                                    UPDATE locations l
                                    SET company_address_line1 = COALESCE(NULLIF(l.company_address_line1, ''), cc.company_address_line1, ''),
                                        company_address_line2 = COALESCE(NULLIF(l.company_address_line2, ''), cc.company_address_line2, ''),
                                        company_address_line3 = COALESCE(NULLIF(l.company_address_line3, ''), cc.company_address_line3, ''),
                                        company_phone_line1 = COALESCE(NULLIF(l.company_phone_line1, ''), cc.company_phone_line1, ''),
                                        company_phone_line2 = COALESCE(NULLIF(l.company_phone_line2, ''), cc.company_phone_line2, ''),
                                        company_email_line1 = COALESCE(NULLIF(l.company_email_line1, ''), cc.company_email_line1, ''),
                                        company_email_line2 = COALESCE(NULLIF(l.company_email_line2, ''), cc.company_email_line2, '')
                                    FROM company_customization cc
                                    WHERE cc.location_id = l.location_id
                                      AND (l.company_address_line1 = '' OR l.company_address_line2 = '' OR l.company_address_line3 = ''
                                           OR l.company_phone_line1 = '' OR l.company_phone_line2 = ''
                                           OR l.company_email_line1 = '' OR l.company_email_line2 = '')
                                $sql$;
                            END IF;
                        END $$;
                        """);
                stmt.executeUpdate("ALTER TABLE company_customization DROP COLUMN IF EXISTS company_address_line1");
                stmt.executeUpdate("ALTER TABLE company_customization DROP COLUMN IF EXISTS company_address_line2");
                stmt.executeUpdate("ALTER TABLE company_customization DROP COLUMN IF EXISTS company_address_line3");
                stmt.executeUpdate("ALTER TABLE company_customization DROP COLUMN IF EXISTS company_phone_line1");
                stmt.executeUpdate("ALTER TABLE company_customization DROP COLUMN IF EXISTS company_phone_line2");
                stmt.executeUpdate("ALTER TABLE company_customization DROP COLUMN IF EXISTS company_email_line1");
                stmt.executeUpdate("ALTER TABLE company_customization DROP COLUMN IF EXISTS company_email_line2");
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to prepare location identity fields: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean hasTable(String tableName) {
        String sql = """
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = ?
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            return false;
        }
    }

    private String sanitizeStoreCode(String value) {
        if (value == null) {
            return "";
        }
        String digits = value.replaceAll("\\D+", "");
        if (digits.isBlank()) {
            return "";
        }
        int parsed = Integer.parseInt(digits);
        if (parsed < 1) parsed = 1;
        if (parsed > 9999) parsed = 9999;
        return String.format("%04d", parsed);
    }
}
