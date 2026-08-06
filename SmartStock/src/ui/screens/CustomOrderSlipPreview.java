package ui.screens;

import Receipt.CustomOrderSlipBuilder;
import Receipt.CustomOrderSlipData;
import Receipt.CustomOrderSlipFormatter;
import Receipt.CustomOrderSlipPrinter;
import Receipt.CustomOrderLabelPrinter;
import Receipt.CustomOrderSlipRenderer;
import managers.CompanyCustomizationManager;
import managers.HardwareSettingsManager;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.UiTaskRunner;
import ui.helpers.WindowHelper;

import javax.print.PrintException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

public class CustomOrderSlipPreview extends JFrame {
    private CustomOrderSlipData slipData;
    private CompanyCustomizationManager.ReceiptSettings receiptSettings;
    private CompanyCustomizationManager.CustomOrderSlipSettings slipSettings;
    private final SlipLetterPanel letterPanel = new SlipLetterPanel();
    private final ReceiptPreview.ReceiptPaperPanel receipt40Panel = new ReceiptPreview.ReceiptPaperPanel();
    private final JComboBox<PrinterOption> printerBox = new JComboBox<>();
    private final JComboBox<HardwareSettingsManager.PrintFormat> formatBox = new JComboBox<>(HardwareSettingsManager.PrintFormat.values());
    private final JTabbedPane previewTabs = new JTabbedPane();
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

    public CustomOrderSlipPreview(String orderNumber) {
        this(null, orderNumber);
    }

    public CustomOrderSlipPreview(CustomOrderSlipData slipData) {
        this(slipData, null);
    }

    private CustomOrderSlipPreview(CustomOrderSlipData initialData, String orderNumber) {
        this.slipData = initialData;

        setTitle("Custom Order Slip Preview");
        setSize(680, 760);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "CustomOrderSlipPreview"));

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(new EmptyBorder(14, 14, 14, 14));
        add(mainPanel, BorderLayout.CENTER);

        JPanel headerPanel = new JPanel(new BorderLayout(12, 8));
        headerPanel.setOpaque(false);
        JLabel titleLabel = new JLabel("Custom Order Slip Preview");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JPanel printerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        printerPanel.setOpaque(false);
        printerPanel.add(new JLabel("Printer:"));
        printerBox.setPreferredSize(new Dimension(240, 28));
        printerPanel.add(printerBox);
        printerPanel.add(new JLabel("Format:"));
        formatBox.setPreferredSize(new Dimension(160, 28));
        printerPanel.add(formatBox);
        headerPanel.add(printerPanel, BorderLayout.EAST);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        JScrollPane letterScroll = new JScrollPane(letterPanel);
        letterScroll.getVerticalScrollBar().setUnitIncrement(16);
        JScrollPane receipt40Scroll = new JScrollPane(receipt40Panel);
        receipt40Scroll.getVerticalScrollBar().setUnitIncrement(16);
        previewTabs.addTab("Letter", letterScroll);
        previewTabs.addTab("40 Column", receipt40Scroll);
        mainPanel.add(previewTabs, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton printButton = new JButton("Print Slip");
        printButton.setEnabled(false);
        JButton closeButton = new JButton("Close");
        buttonPanel.add(printButton);
        buttonPanel.add(closeButton);
        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.add(loadingState, BorderLayout.CENTER);
        footerPanel.add(buttonPanel, BorderLayout.SOUTH);
        mainPanel.add(footerPanel, BorderLayout.SOUTH);

        printerBox.addActionListener(e -> updateFormatFromPrinter());
        formatBox.addActionListener(e -> previewTabs.setSelectedIndex(getSelectedPrintFormat() == HardwareSettingsManager.PrintFormat.LETTER ? 0 : 1));
        printButton.addActionListener(e -> printSlip());
        closeButton.addActionListener(e -> dispose());

        loadPrinterOptions();
        receipt40Panel.setReceiptText("Loading custom order slip preview...", false);
        WindowHelper.configurePosWindow(this);
        String snapshotIdentity = orderNumber != null ? orderNumber
                : initialData == null ? "unknown" : initialData.orderNumber();
        String cacheKey = "custom-order-slip:" + snapshotIdentity;
        CachedUiLoader.load(this, "custom-order-slip-preview.load", cacheKey, PreviewSnapshot.class,
                SessionDataCache.SCREEN_TTL, loadingState, () -> {
                    var data = orderNumber == null
                            ? java.util.concurrent.CompletableFuture.completedFuture(initialData)
                            : UiTaskRunner.supplyAsync(() -> CustomOrderSlipBuilder.buildFromOrderNumber(orderNumber));
                    var settings = UiTaskRunner.supplyAsync(CompanyCustomizationManager::loadAllSettings);
                    CompanyCustomizationManager.AllSettings all = settings.join();
                    return new PreviewSnapshot(data.join(), all.receipt(), all.customOrderSlip());
                }, snapshot -> {
                    slipData = snapshot.data();
                    receiptSettings = snapshot.receiptSettings();
                    slipSettings = snapshot.slipSettings();
                    printButton.setEnabled(true);
                    updatePreview();
                    loadLogoPreviewAsync();
                });
    }

    private void loadPrinterOptions() {
        printerBox.removeAllItems();
        try {
            List<HardwareSettingsManager.PosPrinter> printers = HardwareSettingsManager.getConfiguredPrinters();
            for (HardwareSettingsManager.PosPrinter printer : printers) {
                printerBox.addItem(new PrinterOption(printer));
                if (printer.defaultReceiptPrinter()) {
                    printerBox.setSelectedIndex(printerBox.getItemCount() - 1);
                }
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load configured printers.\n\n" + ex.getMessage(), "Receipt Printers", JOptionPane.WARNING_MESSAGE);
        }
        if (printerBox.getItemCount() == 0) {
            printerBox.addItem(new PrinterOption(null));
        }
        updateFormatFromPrinter();
    }

    private void updateFormatFromPrinter() {
        PrinterOption selected = (PrinterOption) printerBox.getSelectedItem();
        if (selected != null && selected.printer != null) {
            formatBox.setSelectedItem(selected.printer.printFormat());
        }
        syncPreviewTabToFormat();
    }

    private void syncPreviewTabToFormat() {
        HardwareSettingsManager.PrintFormat selectedFormat = getSelectedPrintFormat();
        int tabIndex = selectedFormat == HardwareSettingsManager.PrintFormat.LETTER ? 0 : 1;
        if (previewTabs.getSelectedIndex() != tabIndex) {
            previewTabs.setSelectedIndex(tabIndex);
        }
    }

    private void updatePreview() {
        if (slipData == null || receiptSettings == null || slipSettings == null) return;
        letterPanel.setSlip(slipData, receiptSettings, slipSettings);
        receipt40Panel.setReceiptText(CustomOrderSlipFormatter.format40Column(slipData, receiptSettings, slipSettings), false);
    }

    private void loadLogoPreviewAsync() {
        letterPanel.setLogo(null);
        receipt40Panel.setLogo(null, false);
        if (!slipSettings.showLogo() || receiptSettings.logoPath().isBlank()) {
            return;
        }
        receipt40Panel.setLogoLoading(true);
        UiTaskRunner.submit(this, "custom-order-slip-preview.logo",
                () -> CompanyCustomizationManager.loadCompanyLogo(receiptSettings), logo -> {
                    letterPanel.setLogo(logo);
                    receipt40Panel.setLogo(logo, false);
                }, failure -> {
                    letterPanel.setLogo(null);
                    receipt40Panel.setLogo(null, false);
                });
    }

    private void printSlip() {
        Integer labelCount = CustomOrderLabelPrinter.promptLabelCount(this);
        if (labelCount == null) return;
        try {
            PrinterOption selected = (PrinterOption) printerBox.getSelectedItem();
            CustomOrderSlipPrinter.printToPosPrinter(slipData, selected == null ? null : selected.printer, getSelectedPrintFormat());
            try {
                CustomOrderLabelPrinter.print(slipData, labelCount);
                JOptionPane.showMessageDialog(this, "Custom order slip and " + labelCount + " label" + (labelCount == 1 ? "" : "s") + " sent to the printers.");
            } catch (PrintException labelFailure) {
                int retry = JOptionPane.showConfirmDialog(this,
                        "The custom order slip printed, but the order label did not.\n\n" + labelFailure.getMessage() + "\n\nRetry the label now?",
                        "Order Label Print Error", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
                if (retry == JOptionPane.YES_OPTION) retryLabels(labelCount);
            }
        } catch (PrintException ex) {
            JOptionPane.showMessageDialog(this, "Failed to print custom order slip.\n\n" + ex.getMessage(), "Print Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void retryLabels(int labelCount) {
        try {
            CustomOrderLabelPrinter.print(slipData, labelCount);
            JOptionPane.showMessageDialog(this, labelCount + " order label" + (labelCount == 1 ? "" : "s") + " sent to the label printer.");
        } catch (PrintException ex) {
            JOptionPane.showMessageDialog(this, "Failed to print order labels.\n\n" + ex.getMessage(), "Print Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private HardwareSettingsManager.PrintFormat getSelectedPrintFormat() {
        Object selectedFormat = formatBox.getSelectedItem();
        if (selectedFormat instanceof HardwareSettingsManager.PrintFormat format) {
            return format;
        }
        return HardwareSettingsManager.PrintFormat.RECEIPT_40;
    }

    private record PreviewSnapshot(CustomOrderSlipData data,
                                   CompanyCustomizationManager.ReceiptSettings receiptSettings,
                                   CompanyCustomizationManager.CustomOrderSlipSettings slipSettings) { }

    private static class PrinterOption {
        private final HardwareSettingsManager.PosPrinter printer;

        private PrinterOption(HardwareSettingsManager.PosPrinter printer) {
            this.printer = printer;
        }

        @Override
        public String toString() {
            return printer == null ? "System Default Printer" : printer.toString();
        }
    }

    private static class SlipLetterPanel extends JPanel {
        private CustomOrderSlipData slipData;
        private CompanyCustomizationManager.ReceiptSettings receiptSettings;
        private CompanyCustomizationManager.CustomOrderSlipSettings slipSettings;
        private BufferedImage logo;

        SlipLetterPanel() {
            setPreferredSize(new Dimension(820, 470));
            setBackground(new Color(241, 245, 249));
        }

        void setSlip(CustomOrderSlipData slipData,
                     CompanyCustomizationManager.ReceiptSettings receiptSettings,
                     CompanyCustomizationManager.CustomOrderSlipSettings slipSettings) {
            this.slipData = slipData;
            this.receiptSettings = receiptSettings;
            this.slipSettings = slipSettings;
            repaint();
        }

        void setLogo(BufferedImage logo) {
            this.logo = logo;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (slipData == null || receiptSettings == null || slipSettings == null) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                CustomOrderSlipRenderer.paintSlip(g, 30, 24, Math.max(getWidth() - 60, 720), 390, slipData, receiptSettings, slipSettings, logo);
            } finally {
                g.dispose();
            }
        }
    }
}
