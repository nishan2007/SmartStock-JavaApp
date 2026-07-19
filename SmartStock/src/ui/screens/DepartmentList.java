package ui.screens;

import services.LanApiClient;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.UiTaskRunner;
import ui.helpers.UiDebouncer;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.UUID;

public class DepartmentList extends JFrame {
    private final JTextField searchField = new JTextField();
    private final JTextField nameField = new JTextField();
    private final JTextField vatRatePercentField = new JTextField("0", 8);
    private final JTextArea descriptionArea = new JTextArea(4, 24);
    private final DefaultTableModel tableModel;
    private final JTable departmentTable;
    private Integer selectedDepartmentId;
    private boolean departmentVatEditable;
    private String pendingSaveKey;
    private String pendingSaveFingerprint;
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

    public DepartmentList() {
        setTitle("Department List");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(14, 14));
        setJMenuBar(AppMenuBar.create(this, "DepartmentList"));

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.setBackground(new Color(245, 247, 250));

        tableModel = new DefaultTableModel(new Object[]{"ID", "Department", "VAT %", "Description"}, 0) {
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
        root.add(loadingState, BorderLayout.SOUTH);
        add(root, BorderLayout.CENTER);

        updateVatEditState();
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
        UiDebouncer.bind(searchField, 300, this::loadDepartments);
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
        addFormRow(panel, gbc, 2, "VAT %:", vatRatePercentField);
        addFormRow(panel, gbc, 3, "Description:", new JScrollPane(descriptionArea));

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
        String search = searchField.getText().trim();
        CachedUiLoader.load(this, "departments.search", "departments:" + search, LanApiClient.DepartmentListResult.class,
                SessionDataCache.SCREEN_TTL, loadingState,
                () -> LanApiClient.loadDepartments(search), this::applyDepartments);
    }

    private void applyDepartments(LanApiClient.DepartmentListResult result) {
        tableModel.setRowCount(0);
        departmentVatEditable = result.vatEditable();
        updateVatEditState();
        for (LanApiClient.DepartmentRecord department : result.departments()) {
            tableModel.addRow(new Object[]{department.categoryId(), department.name(),
                    department.vatRatePercent(), department.description()});
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
        vatRatePercentField.setText(String.valueOf(tableModel.getValueAt(modelRow, 2)));
        descriptionArea.setText(String.valueOf(tableModel.getValueAt(modelRow, 3)));
    }

    private void saveDepartment() {
        String name = nameField.getText().trim();
        java.math.BigDecimal vatRatePercent = departmentVatEditable ? parseVatRate() : currentSelectedVatRate();
        if (departmentVatEditable && vatRatePercent == null) {
            return;
        }
        if (vatRatePercent == null) {
            vatRatePercent = java.math.BigDecimal.ZERO;
        }
        String description = descriptionArea.getText().trim();
        if (name.isBlank()) {
            JOptionPane.showMessageDialog(this, "Department name is required.");
            return;
        }

        try {
            LanApiClient.DepartmentSaveRequest request = new LanApiClient.DepartmentSaveRequest(
                    selectedDepartmentId, name, vatRatePercent, description);
            String fingerprint = request.toString();
            if (!fingerprint.equals(pendingSaveFingerprint) || pendingSaveKey == null) {
                pendingSaveFingerprint = fingerprint;
                pendingSaveKey = UUID.randomUUID().toString();
            }
            String mutationKey = pendingSaveKey;
            UiTaskRunner.submit(this,"departments.save",()->{LanApiClient.saveDepartment(request,mutationKey);return Boolean.TRUE;},ignored->{SessionDataCache.invalidate("departments:");pendingSaveKey=null;pendingSaveFingerprint=null;clearEditor();loadDepartments();JOptionPane.showMessageDialog(this,"Department saved.");},ex->JOptionPane.showMessageDialog(this,"Failed to save department: "+ex.getMessage(),"SmartStock Server Error",JOptionPane.ERROR_MESSAGE));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save department: " + ex.getMessage(),
                    "SmartStock Server Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearEditor() {
        selectedDepartmentId = null;
        departmentTable.clearSelection();
        nameField.setText("");
        vatRatePercentField.setText("0");
        descriptionArea.setText("");
        nameField.requestFocusInWindow();
    }

    private void updateVatEditState() {
        vatRatePercentField.setEnabled(departmentVatEditable);
        vatRatePercentField.setToolTipText(departmentVatEditable
                ? "Department VAT is enabled in Company Preferences."
                : "Enable VAT and select department VAT rates in Company Preferences to edit this value.");
    }

    private java.math.BigDecimal currentSelectedVatRate() {
        if (selectedDepartmentId == null) {
            return java.math.BigDecimal.ZERO;
        }
        int selectedRow = departmentTable.getSelectedRow();
        if (selectedRow < 0) {
            return java.math.BigDecimal.ZERO;
        }
        int modelRow = departmentTable.convertRowIndexToModel(selectedRow);
        Object value = tableModel.getValueAt(modelRow, 2);
        try {
            return new java.math.BigDecimal(value == null ? "0" : String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return java.math.BigDecimal.ZERO;
        }
    }

    private java.math.BigDecimal parseVatRate() {
        String text = vatRatePercentField.getText() == null ? "" : vatRatePercentField.getText().trim();
        try {
            java.math.BigDecimal rate = new java.math.BigDecimal(text.isBlank() ? "0" : text.replace("%", ""));
            if (rate.compareTo(java.math.BigDecimal.ZERO) < 0 || rate.compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
                JOptionPane.showMessageDialog(this, "VAT percent must be between 0 and 100.");
                return null;
            }
            return rate;
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "VAT percent must be a valid number.");
            return null;
        }
    }
}
