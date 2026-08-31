package ui.screens.workstationprefs;

import managers.HardwareSettingsManager;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.UiTaskRunner;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HardwareSettingsPanel extends JPanel {
    private final DefaultListModel<String> installedPrinterModel = new DefaultListModel<>();
    private final JList<String> installedPrinterList = new JList<>(installedPrinterModel);
    private final DefaultTableModel configuredPrinterModel;
    private final JTable configuredPrinterTable;
    private final JTextField configPathField = new JTextField();
    private final LoadingStatePanel loadingState = new LoadingStatePanel();
    private final JCheckBox epsonEnabledBox = new JCheckBox("Epson ESC/POS");
    private final JCheckBox automaticCutBox = new JCheckBox("Automatic cut", true);
    private final JCheckBox cashDrawerBox = new JCheckBox("Cash drawer");
    private final JCheckBox dialogFallbackBox = new JCheckBox("Print dialog fallback", true);
    private final JCheckBox nativeEthernetBox = new JCheckBox("Native Ethernet ESC/POS");
    private final JTextField ethernetHostField = new JTextField("10.1.1.23", 11);
    private final JSpinner ethernetPortSpinner = new JSpinner(new SpinnerNumberModel(9100, 1, 65535, 1));
    private final JCheckBox badgePrinterEnabledBox = new JCheckBox("Enable Magicard badge printing");
    private final JComboBox<String> badgePrinterQueueBox = new JComboBox<>();
    private final JCheckBox badgeDuplexBox = new JCheckBox("Magicard 600 Duo", true);
    private final JCheckBox badgePrintDialogBox = new JCheckBox("Show print dialog", true);
    private final JLabel badgePrinterStatusLabel = new JLabel("Not configured");
    private final JComboBox<String> drawerPinBox = new JComboBox<>(new String[]{"Pin 2", "Pin 5"});
    private final JSpinner drawerOnSpinner = new JSpinner(new SpinnerNumberModel(120, 2, 510, 2));
    private final JSpinner drawerOffSpinner = new JSpinner(new SpinnerNumberModel(240, 2, 510, 2));

    public HardwareSettingsPanel() {
        setLayout(new BorderLayout(0, 12));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(18, 18, 18, 18)
        ));

        JLabel titleLabel = new JLabel("Hardware Settings");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLabel.setForeground(new Color(32, 41, 57));

        JPanel contentPanel = new JPanel(new GridLayout(1, 2, 18, 0));
        contentPanel.setOpaque(false);

        installedPrinterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        contentPanel.add(wrapPanel("Installed Printers", new JScrollPane(installedPrinterList)));

        configuredPrinterModel = new DefaultTableModel(new Object[]{"POS Name", "System Printer", "Receipt Default", "Order Label Default", "Format"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0 || column == 4;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 2 || columnIndex == 3 ? Boolean.class : String.class;
            }
        };
        configuredPrinterTable = new JTable(configuredPrinterModel);
        configuredPrinterTable.setRowHeight(28);
        configuredPrinterTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        configuredPrinterTable.getColumnModel().getColumn(4).setCellEditor(new DefaultCellEditor(new JComboBox<>(HardwareSettingsManager.PrintFormat.values())));
        contentPanel.add(wrapPanel("POS Printers", new JScrollPane(configuredPrinterTable)));

        JPanel bottomPanel = new JPanel(new BorderLayout(12, 12));
        bottomPanel.setOpaque(false);

        JPanel epsonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        epsonPanel.setOpaque(false);
        epsonPanel.setBorder(BorderFactory.createTitledBorder("Epson receipt printer (80 mm)"));
        epsonPanel.add(epsonEnabledBox);
        epsonPanel.add(automaticCutBox);
        epsonPanel.add(cashDrawerBox);
        epsonPanel.add(new JLabel("Drawer:"));
        epsonPanel.add(drawerPinBox);
        epsonPanel.add(new JLabel("On ms:"));
        epsonPanel.add(drawerOnSpinner);
        epsonPanel.add(new JLabel("Off ms:"));
        epsonPanel.add(drawerOffSpinner);
        epsonPanel.add(dialogFallbackBox);

        JPanel ethernetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        ethernetPanel.setOpaque(false);
        ethernetPanel.setBorder(BorderFactory.createTitledBorder("Native Ethernet receipt printer"));
        ethernetPanel.add(nativeEthernetBox);
        ethernetPanel.add(new JLabel("Host:"));
        ethernetPanel.add(ethernetHostField);
        ethernetPanel.add(new JLabel("Port:"));
        ethernetPanel.add(ethernetPortSpinner);

        JPanel badgePrinterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        badgePrinterPanel.setOpaque(false);
        badgePrinterPanel.setBorder(BorderFactory.createTitledBorder("Badge printer (Magicard 600)"));
        badgePrinterQueueBox.setPrototypeDisplayValue("Magicard 600 Network Printer Queue");
        badgePrinterPanel.add(badgePrinterEnabledBox);
        badgePrinterPanel.add(new JLabel("Windows queue:"));
        badgePrinterPanel.add(badgePrinterQueueBox);
        badgePrinterPanel.add(badgeDuplexBox);
        badgePrinterPanel.add(badgePrintDialogBox);
        badgePrinterPanel.add(badgePrinterStatusLabel);

        configPathField.setEditable(false);
        configPathField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        configPathField.setText(HardwareSettingsManager.getConfigPath().toString());
        JPanel localSettingsPanel = new JPanel(new BorderLayout(0, 6));
        localSettingsPanel.setOpaque(false);
        JPanel printerControls = new JPanel(new GridLayout(0, 1, 0, 6));
        printerControls.setOpaque(false);
        printerControls.add(epsonPanel);
        printerControls.add(ethernetPanel);
        printerControls.add(badgePrinterPanel);
        localSettingsPanel.add(printerControls, BorderLayout.CENTER);
        localSettingsPanel.add(configPathField, BorderLayout.SOUTH);
        bottomPanel.add(localSettingsPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);
        JButton refreshButton = new JButton("Refresh Printers");
        JButton addButton = new JButton("Add Selected");
        JButton removeButton = new JButton("Remove");
        JButton defaultButton = new JButton("Set Receipt Default");
        JButton labelDefaultButton = new JButton("Set Order Label Default");
        JButton saveButton = new JButton("Save");
        JButton testReceiptButton = new JButton("Test Receipt");
        JButton testCutButton = new JButton("Test Cutter");
        JButton testDrawerButton = new JButton("Test Drawer");
        JButton testBadgeButton = new JButton("Test Badge");
        buttonPanel.add(testBadgeButton);
        buttonPanel.add(testReceiptButton);
        buttonPanel.add(testCutButton);
        buttonPanel.add(testDrawerButton);
        buttonPanel.add(refreshButton);
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(defaultButton);
        buttonPanel.add(labelDefaultButton);
        buttonPanel.add(saveButton);
        bottomPanel.add(loadingState,BorderLayout.CENTER);bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(titleLabel, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        refreshButton.addActionListener(e -> {SessionDataCache.invalidate("workstation:hardware");loadHardware();});
        addButton.addActionListener(e -> addSelectedPrinter());
        removeButton.addActionListener(e -> removeSelectedPrinter());
        defaultButton.addActionListener(e -> setSelectedDefault());
        labelDefaultButton.addActionListener(e -> setSelectedLabelDefault());
        saveButton.addActionListener(e -> saveConfiguredPrinters());
        testReceiptButton.addActionListener(e -> testReceipt());
        testCutButton.addActionListener(e -> testControl(Receipt.EpsonReceiptPrintService.ControlAction.CUT));
        testDrawerButton.addActionListener(e -> testControl(Receipt.EpsonReceiptPrintService.ControlAction.DRAWER));
        testBadgeButton.addActionListener(e -> testBadgePrinter());

        loadHardware();
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

    private void loadHardware() {
        CachedUiLoader.loadAfterDisplay(this,"workstation.hardware","workstation:hardware",HardwareSnapshot.class,
                SessionDataCache.REFERENCE_TTL,loadingState,()->{
                    var installed=UiTaskRunner.supplyAsync(HardwareSettingsManager::getAvailablePrinterNames);
                    var configured=UiTaskRunner.supplyAsync(HardwareSettingsManager::getConfiguredPrinters);
                    var epson=UiTaskRunner.supplyAsync(HardwareSettingsManager::getEpsonSettings);
                    var ethernet=UiTaskRunner.supplyAsync(HardwareSettingsManager::getNativeEthernetPrinterSettings);
                    var badge=UiTaskRunner.supplyAsync(HardwareSettingsManager::getBadgePrinterSettings);
                    var badgeSettings=badge.join();
                    boolean badgeAvailable=badgeSettings.enabled() && installed.join().contains(badgeSettings.systemName());
                    return new HardwareSnapshot(installed.join(),configured.join(),epson.join(),ethernet.join(),badgeSettings,badgeAvailable);
                },snapshot->{installedPrinterModel.clear();snapshot.installed().forEach(installedPrinterModel::addElement);applyInstalledBadgePrinters(snapshot.installed());applyConfiguredPrinters(snapshot.configured());applyEpsonSettings(snapshot.epson());applyEthernetSettings(snapshot.ethernet());applyBadgePrinterSettings(snapshot.badge(),snapshot.badgeAvailable());});
    }

    private void loadConfiguredPrinters() {
        try {
            applyConfiguredPrinters(HardwareSettingsManager.getConfiguredPrinters());
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load hardware settings.\n\n" + ex.getMessage(), "Hardware Settings", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyConfiguredPrinters(List<HardwareSettingsManager.PosPrinter> printers) {
        configuredPrinterModel.setRowCount(0);
            for (HardwareSettingsManager.PosPrinter printer : printers) {
                configuredPrinterModel.addRow(new Object[]{
                        printer.displayName(),
                        printer.systemName(),
                        printer.defaultReceiptPrinter(),
                        printer.defaultOrderLabelPrinter(),
                        printer.printFormat()
                });
            }
    }

    private void addSelectedPrinter() {
        String systemPrinter = installedPrinterList.getSelectedValue();
        if (systemPrinter == null || systemPrinter.isBlank()) {
            JOptionPane.showMessageDialog(this, "Select an installed printer first.", "Hardware Settings", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String posName = JOptionPane.showInputDialog(this, "POS printer name:", systemPrinter);
        if (posName == null || posName.isBlank()) {
            return;
        }

        boolean defaultPrinter = configuredPrinterModel.getRowCount() == 0;
        configuredPrinterModel.addRow(new Object[]{posName.trim(), systemPrinter, defaultPrinter, false, HardwareSettingsManager.PrintFormat.RECEIPT_40});
    }

    private void removeSelectedPrinter() {
        int selectedRow = configuredPrinterTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a POS printer to remove.", "Hardware Settings", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        configuredPrinterModel.removeRow(configuredPrinterTable.convertRowIndexToModel(selectedRow));
        ensureOneDefaultSelected();
    }

    private void setSelectedDefault() {
        int selectedRow = configuredPrinterTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a POS printer first.", "Hardware Settings", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int modelRow = configuredPrinterTable.convertRowIndexToModel(selectedRow);
        for (int i = 0; i < configuredPrinterModel.getRowCount(); i++) {
            configuredPrinterModel.setValueAt(i == modelRow, i, 2);
        }
    }

    private void setSelectedLabelDefault() {
        int selectedRow = configuredPrinterTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a POS printer first.", "Hardware Settings", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int modelRow = configuredPrinterTable.convertRowIndexToModel(selectedRow);
        for (int i = 0; i < configuredPrinterModel.getRowCount(); i++) {
            configuredPrinterModel.setValueAt(i == modelRow, i, 3);
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
                    Boolean.TRUE.equals(configuredPrinterModel.getValueAt(i, 3)),
                    getPrintFormat(configuredPrinterModel.getValueAt(i, 4))
            ));
        }

        try {
            HardwareSettingsManager.saveConfiguredPrinters(printers);
            HardwareSettingsManager.saveEpsonSettings(readEpsonSettings());
            HardwareSettingsManager.saveNativeEthernetPrinterSettings(readEthernetSettings());
            HardwareSettingsManager.saveBadgePrinterSettings(readBadgePrinterSettings());
            SessionDataCache.invalidate("workstation:hardware");
            JOptionPane.showMessageDialog(this, "Hardware settings saved.");
            loadHardware();
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save hardware settings.\n\n" + ex.getMessage(), "Hardware Settings", JOptionPane.ERROR_MESSAGE);
        }
    }

    private HardwareSettingsManager.EpsonSettings readEpsonSettings() {
        return new HardwareSettingsManager.EpsonSettings(epsonEnabledBox.isSelected(), automaticCutBox.isSelected(),
                cashDrawerBox.isSelected(), drawerPinBox.getSelectedIndex(),
                ((Number) drawerOnSpinner.getValue()).intValue(), ((Number) drawerOffSpinner.getValue()).intValue(),
                dialogFallbackBox.isSelected());
    }

    private void applyEpsonSettings(HardwareSettingsManager.EpsonSettings settings) {
        HardwareSettingsManager.EpsonSettings value = settings == null ? HardwareSettingsManager.EpsonSettings.defaults() : settings;
        epsonEnabledBox.setSelected(value.enabled());
        automaticCutBox.setSelected(value.automaticCut());
        cashDrawerBox.setSelected(value.cashDrawerEnabled());
        drawerPinBox.setSelectedIndex(value.drawerPin());
        drawerOnSpinner.setValue(value.drawerOnMillis());
        drawerOffSpinner.setValue(value.drawerOffMillis());
        dialogFallbackBox.setSelected(value.printDialogFallback());
    }

    private HardwareSettingsManager.NativeEthernetPrinterSettings readEthernetSettings() {
        return new HardwareSettingsManager.NativeEthernetPrinterSettings(nativeEthernetBox.isSelected(),
                ethernetHostField.getText(), ((Number) ethernetPortSpinner.getValue()).intValue(), 3000);
    }

    private void applyEthernetSettings(HardwareSettingsManager.NativeEthernetPrinterSettings settings) {
        HardwareSettingsManager.NativeEthernetPrinterSettings value = settings == null
                ? HardwareSettingsManager.NativeEthernetPrinterSettings.defaults() : settings;
        nativeEthernetBox.setSelected(value.enabled());
        ethernetHostField.setText(value.host());
        ethernetPortSpinner.setValue(value.port());
    }

    private HardwareSettingsManager.BadgePrinterSettings readBadgePrinterSettings() {
        Object selected = badgePrinterQueueBox.getSelectedItem();
        return new HardwareSettingsManager.BadgePrinterSettings(badgePrinterEnabledBox.isSelected(),
                selected == null ? "" : selected.toString(), HardwareSettingsManager.BadgePrinterSettings.MAGICARD_600,
                badgeDuplexBox.isSelected(), badgePrintDialogBox.isSelected());
    }

    private void applyInstalledBadgePrinters(List<String> installed) {
        Object selected = badgePrinterQueueBox.getSelectedItem();
        badgePrinterQueueBox.removeAllItems();
        installed.forEach(badgePrinterQueueBox::addItem);
        if (selected != null) badgePrinterQueueBox.setSelectedItem(selected);
    }

    private void applyBadgePrinterSettings(HardwareSettingsManager.BadgePrinterSettings settings, boolean available) {
        HardwareSettingsManager.BadgePrinterSettings value = settings == null
                ? HardwareSettingsManager.BadgePrinterSettings.defaults() : settings;
        badgePrinterEnabledBox.setSelected(value.enabled());
        badgeDuplexBox.setSelected(value.duplex());
        badgePrintDialogBox.setSelected(value.showPrintDialog());
        if (!value.systemName().isBlank()) {
            boolean found = false;
            for (int i = 0; i < badgePrinterQueueBox.getItemCount(); i++) {
                if (value.systemName().equals(badgePrinterQueueBox.getItemAt(i))) { found = true; break; }
            }
            if (!found) badgePrinterQueueBox.addItem(value.systemName());
            badgePrinterQueueBox.setSelectedItem(value.systemName());
        }
        badgePrinterStatusLabel.setText(!value.enabled() ? "Disabled"
                : available ? "Queue available" : "Queue missing - install/restore the Magicard Windows queue");
        badgePrinterStatusLabel.setForeground(available || !value.enabled() ? new Color(32, 92, 0) : Color.RED.darker());
    }

    private void testBadgePrinter() {
        try {
            HardwareSettingsManager.BadgePrinterSettings settings = readBadgePrinterSettings();
            HardwareSettingsManager.saveBadgePrinterSettings(settings);
            services.BadgePrintService.printTestBadge(this);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Magicard Badge Test", JOptionPane.ERROR_MESSAGE);
        }
    }

    private HardwareSettingsManager.PosPrinter selectedConfiguredPrinter() {
        int viewRow = configuredPrinterTable.getSelectedRow();
        if (viewRow < 0) {
            try { return HardwareSettingsManager.getDefaultReceiptPrinter(); }
            catch (IOException ex) { return null; }
        }
        int row = configuredPrinterTable.convertRowIndexToModel(viewRow);
        return new HardwareSettingsManager.PosPrinter(String.valueOf(configuredPrinterModel.getValueAt(row, 0)),
                String.valueOf(configuredPrinterModel.getValueAt(row, 1)),
                Boolean.TRUE.equals(configuredPrinterModel.getValueAt(row, 2)),
                Boolean.TRUE.equals(configuredPrinterModel.getValueAt(row, 3)),
                getPrintFormat(configuredPrinterModel.getValueAt(row, 4)));
    }

    private void testControl(Receipt.EpsonReceiptPrintService.ControlAction action) {
        try {
            HardwareSettingsManager.saveEpsonSettings(readEpsonSettings());
            HardwareSettingsManager.saveNativeEthernetPrinterSettings(readEthernetSettings());
            var result = Receipt.EpsonReceiptPrintService.testControl(selectedConfiguredPrinter(), action);
            JOptionPane.showMessageDialog(this, result.message(), "Epson " + action + " Test",
                    result.successful() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Hardware Settings", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void testReceipt() {
        Receipt.ReceiptData sample = new Receipt.ReceiptData(0, "TEST-RECEIPT", new java.sql.Timestamp(System.currentTimeMillis()),
                "SmartStock", "Hardware Test", "", "", "TEST", "TEST", "This workstation",
                java.math.BigDecimal.TEN, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, "", java.math.BigDecimal.TEN,
                java.math.BigDecimal.TEN, java.math.BigDecimal.ZERO, null, null,
                List.of(new Receipt.ReceiptItem("Epson 80 mm printer test", "TEST", 1,
                        java.math.BigDecimal.TEN, java.math.BigDecimal.ZERO, java.math.BigDecimal.TEN,
                        java.math.BigDecimal.TEN)));
        try {
            HardwareSettingsManager.saveEpsonSettings(readEpsonSettings());
            HardwareSettingsManager.saveNativeEthernetPrinterSettings(readEthernetSettings());
            var result = Receipt.EpsonReceiptPrintService.print(sample, selectedConfiguredPrinter(), false, false);
            JOptionPane.showMessageDialog(this, result.message(), "Epson Receipt Test",
                    result.successful() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Hardware Settings", JOptionPane.ERROR_MESSAGE);
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

    private record HardwareSnapshot(List<String> installed,
                                    List<HardwareSettingsManager.PosPrinter> configured,
                                    HardwareSettingsManager.EpsonSettings epson,
                                    HardwareSettingsManager.NativeEthernetPrinterSettings ethernet,
                                    HardwareSettingsManager.BadgePrinterSettings badge,
                                    boolean badgeAvailable) { }
}
