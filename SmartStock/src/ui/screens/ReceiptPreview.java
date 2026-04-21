package ui.screens;

import Receipt.ReceiptData;
import Receipt.ReceiptFormatter;
import Receipt.ReceiptPrinter;
import managers.HardwareSettingsManager;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;

import javax.print.PrintException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.util.List;

public class ReceiptPreview extends JFrame {
    private final ReceiptData receiptData;
    private final JTextArea receiptPreviewArea;
    private final JComboBox<PrinterOption> printerBox = new JComboBox<>();
    private final JComboBox<HardwareSettingsManager.PrintFormat> formatBox = new JComboBox<>(HardwareSettingsManager.PrintFormat.values());

    public ReceiptPreview(ReceiptData receiptData) {
        this.receiptData = receiptData;

        setTitle("Receipt Preview");
        setSize(520, 760);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "ReceiptPreview"));

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(new EmptyBorder(14, 14, 14, 14));
        add(mainPanel, BorderLayout.CENTER);

        JPanel headerPanel = new JPanel(new BorderLayout(12, 8));
        headerPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("Receipt Preview");
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

        receiptPreviewArea = new JTextArea(ReceiptFormatter.formatText(receiptData));
        receiptPreviewArea.setEditable(false);
        receiptPreviewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        receiptPreviewArea.setMargin(new Insets(18, 18, 18, 18));
        mainPanel.add(new JScrollPane(receiptPreviewArea), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton printButton = new JButton("Print Receipt");
        JButton closeButton = new JButton("Close");
        buttonPanel.add(printButton);
        buttonPanel.add(closeButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        printButton.addActionListener(e -> printReceipt());
        closeButton.addActionListener(e -> dispose());
        printerBox.addActionListener(e -> updateFormatFromPrinter());
        formatBox.addActionListener(e -> updateReceiptPreview());

        loadPrinterOptions();
        updateReceiptPreview();
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
        if (format == HardwareSettingsManager.PrintFormat.LETTER) {
            receiptPreviewArea.setText(ReceiptFormatter.formatLetterText(receiptData));
        } else {
            receiptPreviewArea.setText(ReceiptFormatter.formatText(receiptData));
        }
        receiptPreviewArea.setCaretPosition(0);
    }

    private void printReceipt() {
        try {
            PrinterOption selected = (PrinterOption) printerBox.getSelectedItem();
            ReceiptPrinter.printToPosPrinter(receiptData, selected == null ? null : selected.printer, getSelectedPrintFormat());
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
