package ui.screens;

import ui.design.DeckersPalette;

import ui.components.WrappingToolbarLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.List;

/** A true letter sheet, painted with the same Printable used for the printer. */
public final class LetterDocumentView extends JPanel {
    public static final int LETTER_WIDTH = 612;
    public static final int LETTER_HEIGHT = 792;
    private final JComboBox<String> zoom = new JComboBox<>(new String[]{"Fit page", "Fit width", "100%", "125%", "150%"});
    private final JLabel pageLabel = new JLabel("No document");
    private final JButton previous = new JButton("Previous"), next = new JButton("Next");
    private final Sheet sheet = new Sheet();
    private final JScrollPane scroll = new JScrollPane(sheet);
    private List<String> pages = List.of();
    private QuotationInvoiceDocumentPreview.HtmlPagePrintable printable;
    private BufferedImage pageImage;
    private int pageIndex;
    private String message = "Select a document to preview";
    private long generation;
    private SwingWorker<?, ?> imageLoader;

    public LetterDocumentView() {
        super(new BorderLayout(4, 4));
        JPanel toolbar = DocumentStudio.surface(new WrappingToolbarLayout());
        toolbar.add(DocumentStudio.label("Letter · 8.5 × 11 in")); toolbar.add(zoom);
        toolbar.add(previous); toolbar.add(pageLabel); toolbar.add(next);
        DocumentStudio.button(previous, false); DocumentStudio.button(next, false);
        for (JButton button : new JButton[]{previous, next}) button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DeckersPalette.border()), BorderFactory.createEmptyBorder(6, 9, 6, 9)));
        add(toolbar, BorderLayout.NORTH); add(scroll, BorderLayout.CENTER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getHorizontalScrollBar().setUnitIncrement(24);
        zoom.addActionListener(e -> { sheet.revalidate(); sheet.repaint(); });
        previous.addActionListener(e -> showPage(pageIndex - 1));
        next.addActionListener(e -> showPage(pageIndex + 1));
        scroll.getViewport().addChangeListener(e -> { sheet.revalidate(); sheet.repaint(); });
        updateNavigation();
    }

    public void setDocument(String html) {
        firePropertyChange("documentReady", true, false);
        if (imageLoader != null) imageLoader.cancel(true);
        long request = ++generation;
        pages = QuotationInvoiceDocumentPreview.extractSalesDocumentPages(html);
        var prepared = new QuotationInvoiceDocumentPreview.HtmlPagePrintable(pages);
        if (html == null || !html.toLowerCase(java.util.Locale.ROOT).contains("<img")) {
            printable = prepared; showPage(0); firePropertyChange("documentReady", false, true); return;
        }
        printable = null; pageImage = null; message = "Loading letterhead…";
        updateNavigation(); sheet.repaint();
        imageLoader = new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() { prepared.prepareImages(); return null; }
            @Override protected void done() {
                if (request != generation || isCancelled()) return;
                try { get(); printable = prepared; showPage(0); firePropertyChange("documentReady", false, true); }
                catch (Exception ex) { showMessage("Could not prepare document images. Reload the preview."); }
            }
        };
        imageLoader.execute();
    }

    public void showMessage(String text) {
        firePropertyChange("documentReady", true, false);
        generation++;
        if (imageLoader != null) imageLoader.cancel(true);
        pages = List.of(); printable = null; pageImage = null;
        message = text; pageIndex = 0; updateNavigation(); sheet.revalidate(); sheet.repaint();
    }

    private void showPage(int index) {
        if (printable == null || index < 0 || index >= pages.size()) return;
        pageIndex = index;
        pageImage = new BufferedImage(LETTER_WIDTH * 2, LETTER_HEIGHT * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = pageImage.createGraphics();
        try {
            g.setColor(Color.WHITE); g.fillRect(0, 0, pageImage.getWidth(), pageImage.getHeight());
            g.scale(2, 2);
            printable.print(g, QuotationInvoiceDocumentPreview.letterPageFormat(), pageIndex);
        } finally { g.dispose(); }
        updateNavigation(); sheet.revalidate(); sheet.repaint();
        SwingUtilities.invokeLater(() -> scroll.getViewport().setViewPosition(new Point()));
    }

    private void updateNavigation() {
        previous.setEnabled(printable != null && pageIndex > 0); next.setEnabled(printable != null && pageIndex + 1 < pages.size());
        pageLabel.setText(pages.isEmpty() ? "No document" : "Page " + (pageIndex + 1) + " of " + pages.size());
    }

    public void printDocument(Component owner, String title) {
        if (printable == null) return;
        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setJobName(title); job.setPrintable(printable, QuotationInvoiceDocumentPreview.letterPageFormat());
            if (job.printDialog()) job.print();
        } catch (PrinterException ex) {
            JOptionPane.showMessageDialog(owner, ex.getMessage(), "Print document", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isDocumentReady() { return printable != null; }

    static double fitScale(int width, int height, boolean fitWidth) {
        double x = Math.max(1, width - 32) / (double) LETTER_WIDTH;
        double y = Math.max(1, height - 32) / (double) LETTER_HEIGHT;
        return Math.max(0.05, fitWidth ? x : Math.min(x, y));
    }

    private double scale() {
        Dimension size = scroll.getViewport().getExtentSize();
        return switch (zoom.getSelectedIndex()) {
            case 1 -> fitScale(size.width, size.height, true);
            case 2 -> 1.0; case 3 -> 1.25; case 4 -> 1.5;
            default -> fitScale(size.width, size.height, false);
        };
    }

    private final class Sheet extends JComponent implements Scrollable {
        @Override public Dimension getPreferredSize() {
            double s = scale();
            return new Dimension((int) Math.ceil(LETTER_WIDTH * s) + 32, (int) Math.ceil(LETTER_HEIGHT * s) + 32);
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(DeckersPalette.background()); g.fillRect(0, 0, getWidth(), getHeight());
                if (pageImage == null) {
                    g.setColor(new Color(30, 41, 59));
                    g.drawString(message == null ? "" : message, 20, 34); return;
                }
                double s = scale();
                int w = (int) Math.round(LETTER_WIDTH * s), h = (int) Math.round(LETTER_HEIGHT * s);
                int x = Math.max(16, (getWidth() - w) / 2);
                g.setColor(new Color(160, 167, 177)); g.fillRect(x + 3, 19, w, h);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(pageImage, x, 16, w, h, null);
            } finally { g.dispose(); }
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(440, 520); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(24, (o == SwingConstants.VERTICAL ? r.height : r.width) - 24); }
        @Override public boolean getScrollableTracksViewportWidth() { return getPreferredSize().width <= scroll.getViewport().getWidth(); }
        @Override public boolean getScrollableTracksViewportHeight() { return getPreferredSize().height <= scroll.getViewport().getHeight(); }
    }
}
