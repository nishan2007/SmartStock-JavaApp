package ui.screens;

import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

public class SalesQuoteOrderDocumentPreview extends JFrame {
    private final JTextArea documentArea = new JTextArea();
    private final JEditorPane documentPane = new JEditorPane();

    public SalesQuoteOrderDocumentPreview(String title, String documentText) {
        setTitle(title);
        setSize(980, 780);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "SalesQuoteOrderDocumentPreview"));

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(new EmptyBorder(14, 14, 14, 14));
        add(mainPanel, BorderLayout.CENTER);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        String safeText = documentText == null ? "" : documentText;
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
        JButton printButton = new JButton("Print");
        JButton closeButton = new JButton("Close");
        buttons.add(printButton);
        buttons.add(closeButton);
        mainPanel.add(buttons, BorderLayout.SOUTH);

        printButton.addActionListener(e -> printDocument());
        closeButton.addActionListener(e -> dispose());
        WindowHelper.configurePosWindow(this);
    }

    private void printDocument() {
        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setJobName(getTitle());
            job.setPrintable(documentPane.getPrintable(null, null), createLetterPageFormat(job));
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
        double margin = 0.5 * 72.0;
        paper.setSize(width, height);
        paper.setImageableArea(margin, margin, width - (margin * 2), height - (margin * 2));
        pageFormat.setPaper(paper);
        pageFormat.setOrientation(PageFormat.PORTRAIT);
        return pageFormat;
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
