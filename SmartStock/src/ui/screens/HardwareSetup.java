package ui.screens;

import managers.HardwareSettingsManager;
import managers.NavigationManager;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HardwareSetup extends JFrame {
    private final DefaultListModel<String> installedPrinterModel = new DefaultListModel<>();
    private final JList<String> installedPrinterList = new JList<>(installedPrinterModel);
    private final DefaultTableModel configuredPrinterModel;
    private final JTable configuredPrinterTable;
    private final JTextField configPathField = new JTextField();

    public HardwareSetup() {
        setTitle("Hardware Setup");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(960, 620);
        setLocationRelativeTo(null);
        setJMenuBar(AppMenuBar.create(this, "HardwareSetup"));

        JPanel rootPanel = new JPanel(new BorderLayout(18, 18));
        rootPanel.setBorder(new EmptyBorder(24, 24, 24, 24));
        rootPanel.setBackground(new Color(245, 247, 250));

        JLabel titleLabel = new JLabel("Hardware Setup");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        titleLabel.setForeground(new Color(32, 41, 57));
        rootPanel.add(titleLabel, BorderLayout.NORTH);

        JPanel contentPanel = new JPanel(new GridLayout(1, 2, 18, 0));
        contentPanel.setOpaque(false);

        installedPrinterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        contentPanel.add(wrapPanel("Installed Printers", new JScrollPane(installedPrinterList)));

        configuredPrinterModel = new DefaultTableModel(new Object[]{"POS Name", "System Printer", "Receipt Default", "Format"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0 || column == 3;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 2 ? Boolean.class : String.class;
            }
        };
        configuredPrinterTable = new JTable(configuredPrinterModel);
        configuredPrinterTable.setRowHeight(28);
        configuredPrinterTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        configuredPrinterTable.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(new JComboBox<>(HardwareSettingsManager.PrintFormat.values())));
        contentPanel.add(wrapPanel("POS Printers", new JScrollPane(configuredPrinterTable)));

        rootPanel.add(contentPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(12, 12));
        bottomPanel.setOpaque(false);

        configPathField.setEditable(false);
        configPathField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        configPathField.setText(HardwareSettingsManager.getConfigPath().toString());
        bottomPanel.add(configPathField, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);
        JButton refreshButton = new JButton("Refresh Printers");
        JButton addButton = new JButton("Add Selected");
        JButton removeButton = new JButton("Remove");
        JButton defaultButton = new JButton("Set Receipt Default");
        JButton closeButton = new JButton("Close");
        JButton saveButton = new JButton("Save");
        buttonPanel.add(refreshButton);
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(defaultButton);
        buttonPanel.add(closeButton);
        buttonPanel.add(saveButton);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        rootPanel.add(bottomPanel, BorderLayout.SOUTH);
        add(rootPanel);

        refreshButton.addActionListener(e -> loadInstalledPrinters());
        addButton.addActionListener(e -> addSelectedPrinter());
        removeButton.addActionListener(e -> removeSelectedPrinter());
        defaultButton.addActionListener(e -> setSelectedDefault());
        closeButton.addActionListener(e -> NavigationManager.showMainMenu(this));
        saveButton.addActionListener(e -> saveConfiguredPrinters());

        loadInstalledPrinters();
        loadConfiguredPrinters();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel wrapPanel(String title, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(16, 16, 16, 16)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLabel.setForeground(new Color(32, 41, 57));
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private void loadInstalledPrinters() {
        installedPrinterModel.clear();
        for (String printerName : HardwareSettingsManager.getAvailablePrinterNames()) {
            installedPrinterModel.addElement(printerName);
        }
    }

    private void loadConfiguredPrinters() {
        configuredPrinterModel.setRowCount(0);
        try {
            for (HardwareSettingsManager.PosPrinter printer : HardwareSettingsManager.getConfiguredPrinters()) {
                configuredPrinterModel.addRow(new Object[]{
                        printer.displayName(),
                        printer.systemName(),
                        printer.defaultReceiptPrinter(),
                        printer.printFormat()
                });
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load hardware settings.\n\n" + ex.getMessage(), "Hardware Setup", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addSelectedPrinter() {
        String systemPrinter = installedPrinterList.getSelectedValue();
        if (systemPrinter == null || systemPrinter.isBlank()) {
            JOptionPane.showMessageDialog(this, "Select an installed printer first.", "Hardware Setup", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String posName = JOptionPane.showInputDialog(this, "POS printer name:", systemPrinter);
        if (posName == null || posName.isBlank()) {
            return;
        }

        boolean defaultPrinter = configuredPrinterModel.getRowCount() == 0;
        configuredPrinterModel.addRow(new Object[]{posName.trim(), systemPrinter, defaultPrinter, HardwareSettingsManager.PrintFormat.RECEIPT_40});
    }

    private void removeSelectedPrinter() {
        int selectedRow = configuredPrinterTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a POS printer to remove.", "Hardware Setup", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        configuredPrinterModel.removeRow(configuredPrinterTable.convertRowIndexToModel(selectedRow));
        ensureOneDefaultSelected();
    }

    private void setSelectedDefault() {
        int selectedRow = configuredPrinterTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a POS printer first.", "Hardware Setup", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int modelRow = configuredPrinterTable.convertRowIndexToModel(selectedRow);
        for (int i = 0; i < configuredPrinterModel.getRowCount(); i++) {
            configuredPrinterModel.setValueAt(i == modelRow, i, 2);
        }
    }

    private void saveConfiguredPrinters() {
        ensureOneDefaultSelected();
        List<HardwareSettingsManager.PosPrinter> printers = new ArrayList<>();
        for (int i = 0; i < configuredPrinterModel.getRowCount(); i++) {
            printers.add(new HardwareSettingsManager.PosPrinter(
                    String.valueOf(configuredPrinterModel.getValueAt(i, 0)).trim(),
                    String.valueOf(configuredPrinterModel.getValueAt(i, 1)).trim(),
                    Boolean.TRUE.equals(configuredPrinterModel.getValueAt(i, 2)),
                    getPrintFormat(configuredPrinterModel.getValueAt(i, 3))
            ));
        }

        try {
            HardwareSettingsManager.saveConfiguredPrinters(printers);
            JOptionPane.showMessageDialog(this, "Hardware settings saved.");
            loadConfiguredPrinters();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save hardware settings.\n\n" + ex.getMessage(), "Hardware Setup", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void ensureOneDefaultSelected() {
        if (configuredPrinterModel.getRowCount() == 0) {
            return;
        }
        for (int i = 0; i < configuredPrinterModel.getRowCount(); i++) {
            if (Boolean.TRUE.equals(configuredPrinterModel.getValueAt(i, 2))) {
                return;
            }
        }
        configuredPrinterModel.setValueAt(true, 0, 2);
    }

    private HardwareSettingsManager.PrintFormat getPrintFormat(Object value) {
        if (value instanceof HardwareSettingsManager.PrintFormat format) {
            return format;
        }
        return HardwareSettingsManager.PrintFormat.fromConfigValue(String.valueOf(value));
    }
}
