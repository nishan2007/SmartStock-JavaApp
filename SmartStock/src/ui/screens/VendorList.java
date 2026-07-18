package ui.screens;

import services.LanApiClient;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.UUID;

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
    private List<LanApiClient.VendorRecord> loadedVendors = List.of();
    private String pendingSaveKey;
    private String pendingSaveFingerprint;

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
        try {
            loadedVendors = LanApiClient.loadVendors(searchField.getText().trim());
            for (LanApiClient.VendorRecord vendor : loadedVendors) {
                tableModel.addRow(new Object[]{vendor.vendorId(), vendor.name(), vendor.contactName(),
                        vendor.phone(), vendor.email(), vendor.active() ? "Yes" : "No"});
            }
        } catch (Exception ex) {
            loadedVendors = List.of();
            JOptionPane.showMessageDialog(this, "Failed to load vendors: " + ex.getMessage(),
                    "SmartStock Server Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void selectCurrentRow() {
        int row = vendorTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = vendorTable.convertRowIndexToModel(row);
        selectedVendorId = Integer.parseInt(String.valueOf(tableModel.getValueAt(modelRow, 0)));

        loadedVendors.stream().filter(vendor -> vendor.vendorId() == selectedVendorId).findFirst().ifPresent(vendor -> {
            nameField.setText(vendor.name());
            contactField.setText(vendor.contactName());
            phoneField.setText(vendor.phone());
            emailField.setText(vendor.email());
            addressArea.setText(vendor.address());
            notesArea.setText(vendor.notes());
            activeCheckBox.setSelected(vendor.active());
        });
    }

    private void saveVendor() {
        String name = nameField.getText().trim();
        if (name.isBlank()) {
            JOptionPane.showMessageDialog(this, "Vendor name is required.");
            return;
        }

        try {
            LanApiClient.VendorSaveRequest request = new LanApiClient.VendorSaveRequest(
                    selectedVendorId, name, emptyToNull(contactField.getText()), emptyToNull(phoneField.getText()),
                    emptyToNull(emailField.getText()), emptyToNull(addressArea.getText()),
                    emptyToNull(notesArea.getText()), activeCheckBox.isSelected());
            String fingerprint = request.toString();
            if (!fingerprint.equals(pendingSaveFingerprint) || pendingSaveKey == null) {
                pendingSaveFingerprint = fingerprint;
                pendingSaveKey = UUID.randomUUID().toString();
            }
            LanApiClient.saveVendor(request, pendingSaveKey);
            pendingSaveKey = null;
            pendingSaveFingerprint = null;
            clearEditor();
            loadVendors();
            JOptionPane.showMessageDialog(this, "Vendor saved.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save vendor: " + ex.getMessage(),
                    "SmartStock Server Error", JOptionPane.ERROR_MESSAGE);
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
