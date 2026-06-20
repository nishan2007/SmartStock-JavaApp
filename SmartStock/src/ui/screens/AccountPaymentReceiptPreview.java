package ui.screens;

import Receipt.AccountPaymentReceiptData;
import Receipt.AccountPaymentReceiptFormatter;
import Receipt.AccountPaymentReceiptPrinter;
import managers.CompanyCustomizationManager;
import managers.HardwareSettingsManager;
import services.EmailOutboxService;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;

import javax.print.PrintException;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

public class AccountPaymentReceiptPreview extends JFrame {
    private final AccountPaymentReceiptData receiptData;
    private final CompanyCustomizationManager.ReceiptSettings receiptSettings;
    private final ReceiptPreview.ReceiptPaperPanel receiptPaperPanel = new ReceiptPreview.ReceiptPaperPanel();
    private final JComboBox<PrinterOption> printerBox = new JComboBox<>();
    private final JComboBox<HardwareSettingsManager.PrintFormat> formatBox = new JComboBox<>(HardwareSettingsManager.PrintFormat.values());

    public AccountPaymentReceiptPreview(AccountPaymentReceiptData receiptData) {
        this.receiptData = receiptData;
        this.receiptSettings = CompanyCustomizationManager.loadReceiptSettings();

        setTitle("Account Payment Receipt");
        setSize(560, 760);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "AccountPaymentReceiptPreview"));

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(new EmptyBorder(14, 14, 14, 14));
        add(mainPanel, BorderLayout.CENTER);

        JPanel headerPanel = new JPanel(new BorderLayout(12, 8));
        headerPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("Account Payment Receipt");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JPanel printerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        printerPanel.setOpaque(false);
        printerPanel.add(new JLabel("Printer:"));
        printerBox.setPreferredSize(new Dimension(260, 28));
        printerPanel.add(printerBox);
        printerPanel.add(new JLabel("Format:"));
        formatBox.setPreferredSize(new Dimension(170, 28));
        printerPanel.add(formatBox);
        headerPanel.add(printerPanel, BorderLayout.EAST);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        JScrollPane previewScrollPane = new JScrollPane(receiptPaperPanel);
        previewScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        mainPanel.add(previewScrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton emailButton = new JButton("Email Receipt");
        JButton printButton = new JButton("Print Receipt");
        JButton closeButton = new JButton("Close");
        buttonPanel.add(emailButton);
        buttonPanel.add(printButton);
        buttonPanel.add(closeButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        emailButton.addActionListener(e -> emailReceipt());
        printButton.addActionListener(e -> printReceipt());
        closeButton.addActionListener(e -> dispose());
        printerBox.addActionListener(e -> updateFormatFromPrinter());
        formatBox.addActionListener(e -> updateReceiptPreview());

        loadPrinterOptions();
        updateReceiptPreview();
        loadLogoPreviewAsync();
        WindowHelper.configurePosWindow(this);
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
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to load configured printers.\n\n" + ex.getMessage(),
                    "Receipt Printers",
                    JOptionPane.WARNING_MESSAGE
            );
        }

        if (printerBox.getItemCount() == 0) {
            printerBox.addItem(new PrinterOption(null));
        }
    }

    private void updateFormatFromPrinter() {
        PrinterOption selected = (PrinterOption) printerBox.getSelectedItem();
        if (selected != null && selected.printer != null) {
            formatBox.setSelectedItem(selected.printer.printFormat());
        }
        updateReceiptPreview();
    }

    private void updateReceiptPreview() {
        HardwareSettingsManager.PrintFormat format = getSelectedPrintFormat();
        String barcodeText = receiptSettings.accountPaymentReceiptSettings().showBarcode() ? receiptData.getPaymentId() : "";
        if (format == HardwareSettingsManager.PrintFormat.LETTER) {
            receiptPaperPanel.setReceiptText(AccountPaymentReceiptFormatter.formatLetterText(receiptData, receiptSettings), true, barcodeText);
        } else {
            receiptPaperPanel.setReceiptText(AccountPaymentReceiptFormatter.formatText(receiptData, receiptSettings), false, barcodeText);
        }
    }

    private void loadLogoPreviewAsync() {
        receiptPaperPanel.setLogo(null, false);
        if (!receiptSettings.showLogo() || receiptSettings.logoPath().isBlank()) {
            return;
        }

        receiptPaperPanel.setLogoLoading(true);
        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                return CompanyCustomizationManager.loadReceiptLogo(receiptSettings);
            }

            @Override
            protected void done() {
                try {
                    receiptPaperPanel.setLogo(get(), false);
                } catch (Exception ex) {
                    receiptPaperPanel.setLogo(null, false);
                }
            }
        }.execute();
    }

    private void printReceipt() {
        try {
            PrinterOption selected = (PrinterOption) printerBox.getSelectedItem();
            AccountPaymentReceiptPrinter.printToPosPrinter(receiptData, selected == null ? null : selected.printer, getSelectedPrintFormat());
            JOptionPane.showMessageDialog(this, "Receipt sent to " + (selected == null ? "the printer" : selected) + ".");
        } catch (PrintException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to print receipt.\n\n" + ex.getMessage(),
                    "Print Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void emailReceipt() {
        String recipient = JOptionPane.showInputDialog(
                this,
                "Customer email (leave blank to use account email):",
                "Email Payment Receipt",
                JOptionPane.PLAIN_MESSAGE
        );
        if (recipient == null) {
            return;
        }
        try {
            EmailOutboxService.QueueResult result = EmailOutboxService.queueAccountPaymentReceipt(receiptData, recipient.trim(), false);
            if (result.queued()) {
                JOptionPane.showMessageDialog(this, "Payment receipt email queued. Outbox #" + result.outboxId() + ".");
            } else {
                JOptionPane.showMessageDialog(this, result.message(), "Email Payment Receipt", JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to queue payment receipt email.\n\n" + ex.getMessage(),
                    "Email Payment Receipt",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private HardwareSettingsManager.PrintFormat getSelectedPrintFormat() {
        Object selectedFormat = formatBox.getSelectedItem();
        if (selectedFormat instanceof HardwareSettingsManager.PrintFormat format) {
            return format;
        }
        return HardwareSettingsManager.PrintFormat.RECEIPT_40;
    }

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
}
