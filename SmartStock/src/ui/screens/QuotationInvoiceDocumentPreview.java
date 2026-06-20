package ui.screens;

import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;
import services.EmailOutboxService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuotationInvoiceDocumentPreview extends JFrame {
    private final JTextArea documentArea = new JTextArea();
    private final JEditorPane documentPane = new JEditorPane();
    private final String documentText;
    private final String emailDocumentType;
    private final Long emailDocumentId;

    public QuotationInvoiceDocumentPreview(String title, String documentText) {
        this(title, documentText, false, null, null);
    }

    public QuotationInvoiceDocumentPreview(String title, String documentText, boolean showPrintDialogOnOpen) {
        this(title, documentText, showPrintDialogOnOpen, null, null);
    }

    public QuotationInvoiceDocumentPreview(String title, String documentText, boolean showPrintDialogOnOpen,
                                           String emailDocumentType, Long emailDocumentId) {
        this.emailDocumentType = emailDocumentType;
        this.emailDocumentId = emailDocumentId;
        setTitle(title);
        setSize(980, 780);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "QuotationInvoiceDocumentPreview"));

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(new EmptyBorder(14, 14, 14, 14));
        add(mainPanel, BorderLayout.CENTER);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        String safeText = documentText == null ? "" : documentText;
        this.documentText = safeText;
        documentArea.setEditable(false);
        documentArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        documentArea.setText(stripHtml(safeText));
        documentArea.setCaretPosition(0);

        documentPane.setEditable(false);
        documentPane.setContentType(safeText.stripLeading().startsWith("<html") ? "text/html" : "text/plain");
        documentPane.setText(safeText);
        documentPane.setCaretPosition(0);
        JScrollPane previewScrollPane = new JScrollPane(documentPane);
        previewScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        previewScrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        mainPanel.add(previewScrollPane, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton emailButton = new JButton("Email");
        JButton printButton = new JButton("Print");
        JButton closeButton = new JButton("Close");
        if (emailDocumentType != null && emailDocumentId != null) {
            buttons.add(emailButton);
        }
        buttons.add(printButton);
        buttons.add(closeButton);
        mainPanel.add(buttons, BorderLayout.SOUTH);

        emailButton.addActionListener(e -> emailDocument());
        printButton.addActionListener(e -> printDocument());
        closeButton.addActionListener(e -> dispose());
        WindowHelper.configurePosWindow(this);
        if (showPrintDialogOnOpen) {
            SwingUtilities.invokeLater(() -> {
                toFront();
                requestFocus();
                printDocument();
            });
        }
    }

    private void emailDocument() {
        String recipient = JOptionPane.showInputDialog(
                this,
                "Customer email (leave blank to use document email):",
                "Email " + getTitle(),
                JOptionPane.PLAIN_MESSAGE
        );
        if (recipient == null) {
            return;
        }
        try {
            EmailOutboxService.QueueResult result = switch (emailDocumentType) {
                case "QUOTATION" -> EmailOutboxService.queueQuotation(emailDocumentId, recipient.trim(), false);
                case "INVOICE" -> EmailOutboxService.queueInvoice(emailDocumentId, recipient.trim(), false);
                case "DELIVERY_BILL" -> EmailOutboxService.queueDeliveryBill(emailDocumentId, recipient.trim(), false);
                default -> EmailOutboxService.QueueResult.skipped("This document cannot be emailed.");
            };
            if (result.queued()) {
                JOptionPane.showMessageDialog(this, "Email queued. Outbox #" + result.outboxId() + ".");
            } else {
                JOptionPane.showMessageDialog(this, result.message(), "Email Document", JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to queue email.\n\n" + ex.getMessage(),
                    "Email Document",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void printDocument() {
        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setJobName(getTitle());
            PageFormat pageFormat = createLetterPageFormat(job);
            if (isSalesDocumentHtml(documentText)) {
                job.setPrintable(new HtmlPagePrintable(extractSalesDocumentPages(documentText)), pageFormat);
            } else {
                job.setPrintable(documentPane.getPrintable(null, null), pageFormat);
            }
            if (job.printDialog()) {
                job.print();
            }
        } catch (PrinterException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to print document.\n\n" + ex.getMessage(),
                    "Print Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private PageFormat createLetterPageFormat(PrinterJob job) {
        PageFormat pageFormat = job.defaultPage();
        Paper paper = new Paper();
        double width = 8.5 * 72.0;
        double height = 11.0 * 72.0;
        double margin = 0.25 * 72.0;
        paper.setSize(width, height);
        paper.setImageableArea(margin, margin, width - (margin * 2), height - (margin * 2));
        pageFormat.setPaper(paper);
        pageFormat.setOrientation(PageFormat.PORTRAIT);
        return pageFormat;
    }

    private boolean isSalesDocumentHtml(String value) {
        return value != null && value.contains("class='page'");
    }

    private List<String> extractSalesDocumentPages(String html) {
        List<String> pages = new ArrayList<>();
        String safeHtml = html == null ? "" : html;
        int bodyIndex = safeHtml.indexOf("<body>");
        int firstPage = safeHtml.indexOf("<div class='page'>");
        if (bodyIndex < 0 || firstPage < 0) {
            pages.add(safeHtml);
            return pages;
        }
        String prefix = safeHtml.substring(0, bodyIndex + "<body>".length());
        int cursor = firstPage;
        while (cursor >= 0 && cursor < safeHtml.length()) {
            int next = safeHtml.indexOf("<div class='page'>", cursor + 1);
            String pageHtml = next < 0
                    ? safeHtml.substring(cursor).replaceFirst("(?is)</body>\\s*</html>\\s*$", "")
                    : safeHtml.substring(cursor, next);
            pages.add(prefix + pageHtml + "</body></html>");
            cursor = next;
        }
        if (pages.isEmpty()) {
            pages.add(safeHtml);
        }
        return pages;
    }

    private static class HtmlPagePrintable implements Printable {
        private static final Pattern IMG_SRC_PATTERN = Pattern.compile("(?is)<img\\b[^>]*\\bsrc=['\"]([^'\"]+)['\"]");
        private final List<String> pages;

        private HtmlPagePrintable(List<String> pages) {
            this.pages = pages == null || pages.isEmpty() ? List.of("") : pages;
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
            if (pageIndex < 0 || pageIndex >= pages.size()) {
                return NO_SUCH_PAGE;
            }
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                double imageableWidth = pageFormat.getImageableWidth();
                double imageableHeight = pageFormat.getImageableHeight();
                JEditorPane pagePane = new JEditorPane();
                pagePane.setEditable(false);
                pagePane.setOpaque(true);
                pagePane.setBackground(Color.WHITE);
                pagePane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
                pagePane.setContentType("text/html");
                String pageHtml = pages.get(pageIndex);
                List<ImageIcon> loadedImages = preloadImages(pageHtml);
                pagePane.setText(removeImageTags(pageHtml));
                pagePane.setCaretPosition(0);
                int renderWidth = (int) Math.ceil(imageableWidth);
                pagePane.setSize(renderWidth, Short.MAX_VALUE);
                Dimension preferred = pagePane.getPreferredSize();
                int renderHeight = Math.max((int) Math.ceil(imageableHeight), preferred.height);
                double scaleY = Math.min(1.0, imageableHeight / renderHeight);
                if (scaleY <= 0 || Double.isNaN(scaleY) || Double.isInfinite(scaleY)) {
                    scaleY = 1.0;
                }
                pagePane.setSize(renderWidth, renderHeight);
                pagePane.validate();
                g2.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, (int) Math.ceil(imageableWidth), (int) Math.ceil(imageableHeight));
                AffineTransform pageTransform = g2.getTransform();
                g2.scale(1.0, scaleY);
                pagePane.printAll(g2);
                g2.setTransform(pageTransform);
                drawHeaderLogo(g2, loadedImages, renderWidth);
                loadedImages.size();
                return PAGE_EXISTS;
            } finally {
                g2.dispose();
            }
        }

        private List<ImageIcon> preloadImages(String html) {
            List<ImageIcon> loadedImages = new ArrayList<>();
            Matcher matcher = IMG_SRC_PATTERN.matcher(html == null ? "" : html);
            while (matcher.find()) {
                String source = htmlDecode(matcher.group(1));
                try {
                    ImageIcon icon = new ImageIcon(new URL(source));
                    if (icon.getIconWidth() > 0 && icon.getIconHeight() > 0) {
                        loadedImages.add(icon);
                    }
                } catch (Exception ignored) {
                    // Printing can continue without optional images; the preview still shows broken paths.
                }
            }
            return loadedImages;
        }

        private String removeImageTags(String html) {
            return IMG_SRC_PATTERN.matcher(html == null ? "" : html).replaceAll("");
        }

        private void drawHeaderLogo(Graphics2D g2, List<ImageIcon> loadedImages, int renderWidth) {
            if (loadedImages == null || loadedImages.isEmpty()) {
                return;
            }
            ImageIcon icon = loadedImages.get(0);
            int sourceWidth = icon.getIconWidth();
            int sourceHeight = icon.getIconHeight();
            if (sourceWidth <= 0 || sourceHeight <= 0) {
                return;
            }
            Object oldInterpolation = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int pagePadding = 14;
            int leftHeaderWidth = Math.max(1, (int) Math.round(renderWidth * 0.70));
            int maxWidth = Math.min(330, leftHeaderWidth - (pagePadding * 2));
            int maxHeight = 70;
            double logoScale = Math.min(maxWidth / (double) sourceWidth, maxHeight / (double) sourceHeight);
            int drawWidth = Math.max(1, (int) Math.round(sourceWidth * logoScale));
            int drawHeight = Math.max(1, (int) Math.round(sourceHeight * logoScale));
            int drawX = pagePadding + Math.max(0, (leftHeaderWidth - (pagePadding * 2) - drawWidth) / 2);
            int drawY = pagePadding + 2;
            g2.drawImage(icon.getImage(), drawX, drawY, drawWidth, drawHeight, null);
            if (oldInterpolation == null) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            } else {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
            }
        }

        private String htmlDecode(String value) {
            return value == null ? "" : value
                    .replace("&amp;", "&")
                    .replace("&#39;", "'")
                    .replace("&quot;", "\"");
        }
    }

    private String stripHtml(String value) {
        if (value == null || !value.stripLeading().startsWith("<html")) {
            return value == null ? "" : value;
        }
        return value.replaceAll("(?is)<style.*?</style>", "")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replaceAll("[ \\t]+", " ")
                .trim();
    }
}
