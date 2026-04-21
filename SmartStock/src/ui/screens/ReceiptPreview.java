package ui.screens;

import Receipt.ReceiptData;
import Receipt.ReceiptFormatter;
import Receipt.ReceiptPrinter;
import managers.CompanyCustomizationManager;
import managers.HardwareSettingsManager;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;

import javax.print.PrintException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

public class ReceiptPreview extends JFrame {
    private final ReceiptData receiptData;
    private final CompanyCustomizationManager.ReceiptSettings receiptSettings;
    private final ReceiptPaperPanel receiptPaperPanel = new ReceiptPaperPanel();
    private final JComboBox<PrinterOption> printerBox = new JComboBox<>();
    private final JComboBox<HardwareSettingsManager.PrintFormat> formatBox = new JComboBox<>(HardwareSettingsManager.PrintFormat.values());

    public ReceiptPreview(ReceiptData receiptData) {
        this.receiptData = receiptData;
        this.receiptSettings = CompanyCustomizationManager.loadReceiptSettings();

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

        JScrollPane previewScrollPane = new JScrollPane(receiptPaperPanel);
        previewScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        mainPanel.add(previewScrollPane, BorderLayout.CENTER);

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
        if (format == HardwareSettingsManager.PrintFormat.LETTER) {
            receiptPaperPanel.setReceiptText(ReceiptFormatter.formatLetterText(receiptData, receiptSettings), true);
        } else {
            receiptPaperPanel.setReceiptText(ReceiptFormatter.formatText(receiptData, receiptSettings), false);
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

    static class ReceiptPaperPanel extends JPanel {
        private static final int RECEIPT_PAPER_WIDTH = 360;
        private static final int LETTER_PAPER_WIDTH = 760;
        private static final int PADDING = 20;

        private String receiptText = "";
        private boolean letterFormat = false;
        private BufferedImage logo;
        private boolean logoLoading = false;
        private final Font receiptFont = new Font(Font.MONOSPACED, Font.PLAIN, 14);

        ReceiptPaperPanel() {
            setBackground(new Color(241, 245, 249));
            setOpaque(true);
            setReceiptText("Loading receipt preview...", false);
        }

        void setReceiptText(String receiptText, boolean letterFormat) {
            this.receiptText = receiptText == null ? "" : receiptText;
            this.letterFormat = letterFormat;
            revalidate();
            repaint();
        }

        void setLogo(BufferedImage logo, boolean loading) {
            this.logo = logo;
            this.logoLoading = loading;
            revalidate();
            repaint();
        }

        void setLogoLoading(boolean loading) {
            this.logoLoading = loading;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics metrics = getFontMetrics(receiptFont);
            int paperWidth = getPaperWidth();
            int textHeight = receiptText.split("\\R", -1).length * metrics.getHeight();
            int logoHeight = getLogoDisplayHeight(paperWidth) + (hasLogoSpace() ? 12 : 0);
            int paperHeight = Math.max(360, PADDING + logoHeight + textHeight + PADDING);
            return new Dimension(paperWidth + 80, paperHeight + 40);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int paperWidth = getPaperWidth();
            int paperX = Math.max((getWidth() - paperWidth) / 2, 20);
            int paperY = 20;
            int paperHeight = Math.max(getPreferredSize().height - 40, getHeight() - 40);

            g2.setColor(Color.WHITE);
            g2.fillRect(paperX, paperY, paperWidth, paperHeight);
            g2.setColor(new Color(203, 213, 225));
            g2.drawRect(paperX, paperY, paperWidth, paperHeight);

            int y = paperY + PADDING;
            if (logoLoading) {
                g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
                g2.setColor(new Color(100, 116, 139));
                String loadingText = "Loading logo...";
                int x = paperX + ((paperWidth - g2.getFontMetrics().stringWidth(loadingText)) / 2);
                g2.drawString(loadingText, x, y + g2.getFontMetrics().getAscent());
                y += g2.getFontMetrics().getHeight() + 12;
            } else if (logo != null) {
                Dimension logoSize = getLogoDisplaySize(paperWidth);
                int x = paperX + ((paperWidth - logoSize.width) / 2);
                g2.drawImage(logo, x, y, logoSize.width, logoSize.height, null);
                y += logoSize.height + 12;
            }

            g2.setFont(receiptFont);
            g2.setColor(Color.BLACK);
            FontMetrics metrics = g2.getFontMetrics();
            int textX = paperX + PADDING;
            for (String line : receiptText.split("\\R", -1)) {
                y += metrics.getHeight();
                g2.drawString(line, textX, y);
            }

            g2.dispose();
        }

        private int getPaperWidth() {
            return letterFormat ? LETTER_PAPER_WIDTH : RECEIPT_PAPER_WIDTH;
        }

        private boolean hasLogoSpace() {
            return logo != null || logoLoading;
        }

        private int getLogoDisplayHeight(int paperWidth) {
            return logo == null ? (logoLoading ? 18 : 0) : getLogoDisplaySize(paperWidth).height;
        }

        private Dimension getLogoDisplaySize(int paperWidth) {
            if (logo == null) {
                return new Dimension(0, 0);
            }
            int maxWidth = Math.min(letterFormat ? 220 : 180, paperWidth - (PADDING * 2));
            int maxHeight = letterFormat ? 90 : 80;
            double scale = Math.min((double) maxWidth / logo.getWidth(), (double) maxHeight / logo.getHeight());
            scale = Math.min(scale, 1.0);
            return new Dimension(
                    Math.max((int) Math.round(logo.getWidth() * scale), 1),
                    Math.max((int) Math.round(logo.getHeight() * scale), 1)
            );
        }
    }
}
