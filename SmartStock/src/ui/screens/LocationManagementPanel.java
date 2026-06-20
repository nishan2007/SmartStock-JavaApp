package ui.screens;

import data.DB;
import services.EmailOutboxService;
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
    private final JTextField senderEmailField = new JTextField();
    private final JTextField senderNameField = new JTextField();
    private final JTextField bccEmailField = new JTextField();
    private final JCheckBox emailReceiptsBox = new JCheckBox("Receipts");
    private final JCheckBox emailOrderConfirmationsBox = new JCheckBox("Order Confirmations");
    private final JCheckBox emailQuotesBox = new JCheckBox("Quotes");
    private final JCheckBox emailInvoicesBox = new JCheckBox("Invoices");
    private final JCheckBox emailDeliveryBillsBox = new JCheckBox("Delivery Bills");
    private final JComboBox<String> timezoneBox = new JComboBox<>();
    private final DefaultTableModel tableModel;
    private final JTable locationTable;
    private Integer selectedLocationId;
    private boolean hasTimezoneColumn;
    private boolean hasIdentityColumns;
    private boolean hasEmailDeliveryColumns;

    public LocationManagementPanel() {
        setLayout(new BorderLayout(14, 14));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setOpaque(false);

        tableModel = new DefaultTableModel(new Object[]{
                "ID", "Store Name", "Store Code", "Address", "Address Line 1", "Address Line 2", "Address Line 3",
                "Phone Line 1", "Phone Line 2", "Email Line 1", "Email Line 2", "Sender Email", "Sender Name",
                "BCC Email", "Email Receipts", "Email Orders", "Email Quotes", "Email Invoices", "Email Delivery", "Timezone"
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
        JButton processEmailButton = new JButton("Process Email Outbox");
        searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(searchButton);
        buttons.add(refreshButton);
        buttons.add(processEmailButton);
        searchPanel.add(buttons, BorderLayout.EAST);

        searchButton.addActionListener(e -> loadLocations());
        searchField.addActionListener(e -> loadLocations());
        refreshButton.addActionListener(e -> {
            searchField.setText("");
            loadLocations();
        });
        processEmailButton.addActionListener(e -> processEmailOutbox());

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(searchPanel, BorderLayout.SOUTH);
        return panel;
    }

    private void processEmailOutbox() {
        try {
            List<EmailOutboxService.SendResult> results = EmailOutboxService.processQueued(25);
            if (results.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No queued email is ready to process.");
                return;
            }
            long sent = results.stream().filter(result -> "SENT".equals(result.status())).count();
            long failed = results.stream().filter(result -> "FAILED".equals(result.status())).count();
            long skipped = results.stream().filter(result -> "SKIPPED".equals(result.status())).count();
            JOptionPane.showMessageDialog(
                    this,
                    "Email outbox processed.\nSent: " + sent + "\nFailed: " + failed + "\nSkipped: " + skipped,
                    "Email Outbox",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to process email outbox: " + ex.getMessage(), "Email Outbox", JOptionPane.ERROR_MESSAGE);
        }
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
        emailReceiptsBox.setOpaque(false);
        emailOrderConfirmationsBox.setOpaque(false);
        emailQuotesBox.setOpaque(false);
        emailInvoicesBox.setOpaque(false);
        emailDeliveryBillsBox.setOpaque(false);

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
        addFormRow(panel, gbc, 11, "Sender Gmail:", senderEmailField);
        addFormRow(panel, gbc, 12, "Sender Name:", senderNameField);
        addFormRow(panel, gbc, 13, "BCC Email:", bccEmailField);
        addFormRow(panel, gbc, 14, "Auto Email:", buildEmailTogglePanel());
        addFormRow(panel, gbc, 15, "Timezone:", timezoneBox);

        JLabel timezoneHelp = new JLabel("<html><div style='width:230px;color:#6b7280;'>Used for report date boundaries and store totals.</div></html>");
        timezoneHelp.setFont(new Font("SansSerif", Font.PLAIN, 12));
        gbc.gridx = 1;
        gbc.gridy = 16;
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
        gbc.gridy = 17;
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

    private JPanel buildEmailTogglePanel() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 2));
        panel.setOpaque(false);
        panel.add(emailReceiptsBox);
        panel.add(emailOrderConfirmationsBox);
        panel.add(emailQuotesBox);
        panel.add(emailInvoicesBox);
        panel.add(emailDeliveryBillsBox);
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
        hasEmailDeliveryColumns = hasColumn("locations", "email_sender_address")
                && hasColumn("locations", "email_sender_name")
                && hasColumn("locations", "email_bcc_address")
                && hasColumn("locations", "email_receipts_enabled")
                && hasColumn("locations", "email_order_confirmations_enabled")
                && hasColumn("locations", "email_quotes_enabled")
                && hasColumn("locations", "email_invoices_enabled")
                && hasColumn("locations", "email_delivery_bills_enabled");
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
                + (hasEmailDeliveryColumns
                ? ", COALESCE(email_sender_address, '') AS email_sender_address"
                + ", COALESCE(email_sender_name, '') AS email_sender_name"
                + ", COALESCE(email_bcc_address, '') AS email_bcc_address"
                + ", COALESCE(email_receipts_enabled, FALSE) AS email_receipts_enabled"
                + ", COALESCE(email_order_confirmations_enabled, FALSE) AS email_order_confirmations_enabled"
                + ", COALESCE(email_quotes_enabled, FALSE) AS email_quotes_enabled"
                + ", COALESCE(email_invoices_enabled, FALSE) AS email_invoices_enabled"
                + ", COALESCE(email_delivery_bills_enabled, FALSE) AS email_delivery_bills_enabled"
                : ", '' AS email_sender_address, '' AS email_sender_name, '' AS email_bcc_address"
                + ", FALSE AS email_receipts_enabled, FALSE AS email_order_confirmations_enabled"
                + ", FALSE AS email_quotes_enabled, FALSE AS email_invoices_enabled, FALSE AS email_delivery_bills_enabled")
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
                            rs.getString("email_sender_address"),
                            rs.getString("email_sender_name"),
                            rs.getString("email_bcc_address"),
                            rs.getBoolean("email_receipts_enabled"),
                            rs.getBoolean("email_order_confirmations_enabled"),
                            rs.getBoolean("email_quotes_enabled"),
                            rs.getBoolean("email_invoices_enabled"),
                            rs.getBoolean("email_delivery_bills_enabled"),
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
        senderEmailField.setText(String.valueOf(tableModel.getValueAt(modelRow, 11)));
        senderNameField.setText(String.valueOf(tableModel.getValueAt(modelRow, 12)));
        bccEmailField.setText(String.valueOf(tableModel.getValueAt(modelRow, 13)));
        emailReceiptsBox.setSelected(Boolean.parseBoolean(String.valueOf(tableModel.getValueAt(modelRow, 14))));
        emailOrderConfirmationsBox.setSelected(Boolean.parseBoolean(String.valueOf(tableModel.getValueAt(modelRow, 15))));
        emailQuotesBox.setSelected(Boolean.parseBoolean(String.valueOf(tableModel.getValueAt(modelRow, 16))));
        emailInvoicesBox.setSelected(Boolean.parseBoolean(String.valueOf(tableModel.getValueAt(modelRow, 17))));
        emailDeliveryBillsBox.setSelected(Boolean.parseBoolean(String.valueOf(tableModel.getValueAt(modelRow, 18))));
        timezoneBox.setSelectedItem(String.valueOf(tableModel.getValueAt(modelRow, 19)));
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
        String senderEmail = senderEmailField.getText().trim();
        String senderName = senderNameField.getText().trim();
        String bccEmail = bccEmailField.getText().trim();
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
        if (!senderEmail.isBlank() && !isValidEmail(senderEmail)) {
            JOptionPane.showMessageDialog(this, "Enter a valid sender Gmail address.", "Invalid Email", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!bccEmail.isBlank() && !isValidEmail(bccEmail)) {
            JOptionPane.showMessageDialog(this, "Enter a valid BCC email address.", "Invalid Email", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String sql;
        if (selectedLocationId == null) {
            sql = hasTimezoneColumn
                    ? "INSERT INTO locations (name, receipt_store_code, address, company_address_line1, company_address_line2, company_address_line3, company_phone_line1, company_phone_line2, company_email_line1, company_email_line2, email_sender_address, email_sender_name, email_bcc_address, email_receipts_enabled, email_order_confirmations_enabled, email_quotes_enabled, email_invoices_enabled, email_delivery_bills_enabled, timezone) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    : "INSERT INTO locations (name, receipt_store_code, address, company_address_line1, company_address_line2, company_address_line3, company_phone_line1, company_phone_line2, company_email_line1, company_email_line2, email_sender_address, email_sender_name, email_bcc_address, email_receipts_enabled, email_order_confirmations_enabled, email_quotes_enabled, email_invoices_enabled, email_delivery_bills_enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        } else {
            sql = hasTimezoneColumn
                    ? "UPDATE locations SET name = ?, receipt_store_code = ?, address = ?, company_address_line1 = ?, company_address_line2 = ?, company_address_line3 = ?, company_phone_line1 = ?, company_phone_line2 = ?, company_email_line1 = ?, company_email_line2 = ?, email_sender_address = ?, email_sender_name = ?, email_bcc_address = ?, email_receipts_enabled = ?, email_order_confirmations_enabled = ?, email_quotes_enabled = ?, email_invoices_enabled = ?, email_delivery_bills_enabled = ?, timezone = ? WHERE location_id = ?"
                    : "UPDATE locations SET name = ?, receipt_store_code = ?, address = ?, company_address_line1 = ?, company_address_line2 = ?, company_address_line3 = ?, company_phone_line1 = ?, company_phone_line2 = ?, company_email_line1 = ?, company_email_line2 = ?, email_sender_address = ?, email_sender_name = ?, email_bcc_address = ?, email_receipts_enabled = ?, email_order_confirmations_enabled = ?, email_quotes_enabled = ?, email_invoices_enabled = ?, email_delivery_bills_enabled = ? WHERE location_id = ?";
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
            ps.setString(11, senderEmail);
            ps.setString(12, senderName);
            ps.setString(13, bccEmail);
            ps.setBoolean(14, emailReceiptsBox.isSelected());
            ps.setBoolean(15, emailOrderConfirmationsBox.isSelected());
            ps.setBoolean(16, emailQuotesBox.isSelected());
            ps.setBoolean(17, emailInvoicesBox.isSelected());
            ps.setBoolean(18, emailDeliveryBillsBox.isSelected());
            if (hasTimezoneColumn) {
                ps.setString(19, timezone);
                if (selectedLocationId != null) {
                    ps.setInt(20, selectedLocationId);
                }
            } else if (selectedLocationId != null) {
                ps.setInt(19, selectedLocationId);
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

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
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
        senderEmailField.setText("");
        senderNameField.setText("");
        bccEmailField.setText("");
        emailReceiptsBox.setSelected(false);
        emailOrderConfirmationsBox.setSelected(false);
        emailQuotesBox.setSelected(false);
        emailInvoicesBox.setSelected(false);
        emailDeliveryBillsBox.setSelected(false);
        timezoneBox.setSelectedItem(DEFAULT_TIMEZONE);
        nameField.requestFocusInWindow();
    }

    private void ensureLocationIdentitySchema() {
        if (data.DatabaseConfig.load().mode() != data.DatabaseMode.SERVER) {
            return;
        }
        try (Connection conn = DB.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_address_line1 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_address_line2 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_address_line3 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_phone_line1 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_phone_line2 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_email_line1 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_email_line2 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS email_sender_address TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS email_sender_name TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS email_bcc_address TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS email_receipts_enabled BOOLEAN NOT NULL DEFAULT FALSE");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS email_order_confirmations_enabled BOOLEAN NOT NULL DEFAULT FALSE");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS email_quotes_enabled BOOLEAN NOT NULL DEFAULT FALSE");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS email_invoices_enabled BOOLEAN NOT NULL DEFAULT FALSE");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS email_delivery_bills_enabled BOOLEAN NOT NULL DEFAULT FALSE");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS email_connected_at TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS email_last_tested_at TIMESTAMPTZ");
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
