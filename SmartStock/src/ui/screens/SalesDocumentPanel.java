package ui.screens;

import ui.design.DeckersPalette;

import Receipt.QuotationInvoiceDocumentBuilder;
import ui.components.WrappingToolbarLayout;
import ui.helpers.UiTaskRunner;
import ui.helpers.WindowHelper;

import javax.swing.*;
import java.awt.*;

/** Shared saved-document preview for quotation, invoice, and delivery workspaces. */
final class SalesDocumentPanel extends JPanel {
    private final Window owner;
    private final LetterDocumentView view = new LetterDocumentView();
    private final JComboBox<String> template = new JComboBox<>(new String[]{"Grid", "Compact"});
    private final JLabel heading = new JLabel("Document preview");
    private final JButton print = new JButton("Print"), open = new JButton("Open preview");
    private String type;
    private Long id;
    private long generation;

    SalesDocumentPanel(Window owner) {
        super(new BorderLayout(6, 6)); this.owner = owner;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(DeckersPalette.surface()); putClientProperty("SmartStock.preserveBackground", true);
        JPanel toolbar = DocumentStudio.surface(new WrappingToolbarLayout());
        DocumentStudio.button(print, false); DocumentStudio.button(open, false);
        toolbar.add(DocumentStudio.templateSwitch(template)); toolbar.add(print); toolbar.add(open);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        JPanel top = DocumentStudio.surface(new BorderLayout(0, 8)); top.add(heading, BorderLayout.NORTH); top.add(toolbar, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH); add(view, BorderLayout.CENTER);
        template.addActionListener(e -> reload());
        view.addPropertyChangeListener("documentReady", e -> print.setEnabled(Boolean.TRUE.equals(e.getNewValue())));
        print.addActionListener(e -> view.printDocument(this, heading.getText()));
        open.addActionListener(e -> {
            if (id != null) WindowHelper.showPosWindow(new QuotationInvoiceDocumentPreview(
                    heading.getText(), false, type, id, template.getSelectedIndex() == 1), owner);
        });
        clear();
    }

    void clear() {
        generation++; type = null; id = null;
        heading.setText("Document preview"); template.setEnabled(false); print.setEnabled(false); open.setEnabled(false);
        view.showMessage("Select a document to preview");
    }

    void showDocument(String type, long id, String title) {
        this.type = type; this.id = id; heading.setText(title);
        template.setEnabled(!"DELIVERY_BILL".equals(type));
        reload();
    }

    void reload() {
        if (id == null) return;
        final long request = ++generation;
        final long documentId = id;
        final String documentType = type;
        final boolean compact = template.getSelectedIndex() == 1;
        print.setEnabled(false); open.setEnabled(false);
        view.showMessage("Loading document…");
        UiTaskRunner.submit(owner, "sales-preview-" + System.identityHashCode(this), () -> switch (documentType) {
            case "QUOTATION" -> QuotationInvoiceDocumentBuilder.buildQuotation(documentId, compact);
            case "INVOICE" -> QuotationInvoiceDocumentBuilder.buildInvoice(documentId, compact);
            case "DELIVERY_BILL" -> QuotationInvoiceDocumentBuilder.buildDelivery(documentId);
            default -> throw new IllegalArgumentException("Unsupported document");
        }, html -> {
            if (request != generation) return;
            view.setDocument(html); open.setEnabled(true);
        }, error -> {
            if (request != generation) return;
            view.showMessage("Could not load preview. Use Refresh to retry.");
            heading.setToolTipText(error.getMessage());
        });
    }
}
