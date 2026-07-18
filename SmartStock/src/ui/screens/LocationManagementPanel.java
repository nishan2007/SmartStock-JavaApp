package ui.screens;

import services.LanApiClient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

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
    private final JTextField balanceSheetEmailField = new JTextField();
    private final JCheckBox emailReceiptsBox = new JCheckBox("Receipts");
    private final JCheckBox emailOrderConfirmationsBox = new JCheckBox("Order Confirmations");
    private final JCheckBox emailQuotesBox = new JCheckBox("Quotes");
    private final JCheckBox emailInvoicesBox = new JCheckBox("Invoices");
    private final JCheckBox emailDeliveryBillsBox = new JCheckBox("Delivery Bills");
    private final JComboBox<String> timezoneBox = new JComboBox<>();
    private final DefaultTableModel tableModel;
    private final JTable locationTable;
    private Integer selectedLocationId;
    private boolean hasTimezoneColumn=true;
    private boolean hasIdentityColumns=true;
    private boolean hasEmailDeliveryColumns=true;
    private String pendingSaveKey;
    private String pendingSaveFingerprint;

    public LocationManagementPanel() {
        setLayout(new BorderLayout(14, 14));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setOpaque(false);

        tableModel = new DefaultTableModel(new Object[]{
                "ID", "Store Name", "Store Code", "Address", "Address Line 1", "Address Line 2", "Address Line 3",
                "Phone Line 1", "Phone Line 2", "Email Line 1", "Email Line 2", "Sender Email", "Sender Name",
                "BCC Email", "Balance Sheet Email", "Email Receipts", "Email Orders", "Email Quotes", "Email Invoices", "Email Delivery", "Timezone"
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
            LanApiClient.EmailProcessingResult results=LanApiClient.processLocationEmailOutbox();
            if (results.processed()==0) {
                JOptionPane.showMessageDialog(this, "No queued email is ready to process.");
                return;
            }
            JOptionPane.showMessageDialog(
                    this,
                    "Email outbox processed.\nSent: " + results.sent() + "\nFailed: " + results.failed() + "\nSkipped: " + results.skipped(),
                    "Email Outbox",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (Exception ex) {
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
        addFormRow(panel, gbc, 14, "Balance Sheet Email:", balanceSheetEmailField);
        addFormRow(panel, gbc, 15, "Auto Email:", buildEmailTogglePanel());
        addFormRow(panel, gbc, 16, "Timezone:", timezoneBox);

        JLabel timezoneHelp = new JLabel("<html><div style='width:230px;color:#6b7280;'>Used for report date boundaries and store totals.</div></html>");
        timezoneHelp.setFont(new Font("SansSerif", Font.PLAIN, 12));
        gbc.gridx = 1;
        gbc.gridy = 17;
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
        gbc.gridy = 18;
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
        String search = searchField.getText().trim();
        try {
            for(LanApiClient.LocationRecord r:LanApiClient.loadLocationRecords(search)){
                    tableModel.addRow(new Object[]{
                            r.locationId(),r.name(),r.storeCode(),r.address(),r.addressLine1(),r.addressLine2(),r.addressLine3(),r.phoneLine1(),r.phoneLine2(),
                            r.emailLine1(),r.emailLine2(),r.senderEmail(),r.senderName(),r.bccEmail(),r.balanceSheetEmail(),r.emailReceipts(),r.emailOrders(),
                            r.emailQuotes(),r.emailInvoices(),r.emailDelivery(),r.timezone()
                    });
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load locations: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
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
        balanceSheetEmailField.setText(String.valueOf(tableModel.getValueAt(modelRow, 14)));
        emailReceiptsBox.setSelected(Boolean.parseBoolean(String.valueOf(tableModel.getValueAt(modelRow, 15))));
        emailOrderConfirmationsBox.setSelected(Boolean.parseBoolean(String.valueOf(tableModel.getValueAt(modelRow, 16))));
        emailQuotesBox.setSelected(Boolean.parseBoolean(String.valueOf(tableModel.getValueAt(modelRow, 17))));
        emailInvoicesBox.setSelected(Boolean.parseBoolean(String.valueOf(tableModel.getValueAt(modelRow, 18))));
        emailDeliveryBillsBox.setSelected(Boolean.parseBoolean(String.valueOf(tableModel.getValueAt(modelRow, 19))));
        timezoneBox.setSelectedItem(String.valueOf(tableModel.getValueAt(modelRow, 20)));
    }

    private void saveLocation() {
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
        String balanceSheetEmail = balanceSheetEmailField.getText().trim();
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
        if (!balanceSheetEmail.isBlank() && !isValidEmail(balanceSheetEmail)) {
            JOptionPane.showMessageDialog(this, "Enter a valid balance sheet email address.", "Invalid Email", JOptionPane.WARNING_MESSAGE);
            return;
        }

        LanApiClient.LocationRecord request=new LanApiClient.LocationRecord(selectedLocationId,name,storeCode,address,addressLine1,addressLine2,addressLine3,
                phoneLine1,phoneLine2,emailLine1,emailLine2,senderEmail,senderName,bccEmail,balanceSheetEmail,emailReceiptsBox.isSelected(),
                emailOrderConfirmationsBox.isSelected(),emailQuotesBox.isSelected(),emailInvoicesBox.isSelected(),emailDeliveryBillsBox.isSelected(),timezone);
        String fingerprint=request.toString();
        try {
            if(pendingSaveKey==null||!fingerprint.equals(pendingSaveFingerprint)){pendingSaveKey=UUID.randomUUID().toString();pendingSaveFingerprint=fingerprint;}
            LanApiClient.saveLocationRecord(request,pendingSaveKey);pendingSaveKey=null;pendingSaveFingerprint=null;
            clearEditor();
            loadLocations();
            JOptionPane.showMessageDialog(this, "Location saved.");
        } catch (Exception ex) {
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
        balanceSheetEmailField.setText("");
        emailReceiptsBox.setSelected(false);
        emailOrderConfirmationsBox.setSelected(false);
        emailQuotesBox.setSelected(false);
        emailInvoicesBox.setSelected(false);
        emailDeliveryBillsBox.setSelected(false);
        timezoneBox.setSelectedItem(DEFAULT_TIMEZONE);
        nameField.requestFocusInWindow();
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
