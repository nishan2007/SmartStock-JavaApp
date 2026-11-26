package ui.screens;

import org.junit.jupiter.api.Test;
import ui.components.DocumentWorkspace;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.Printable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LetterDocumentViewTest {
    private static <T extends Component> T find(Container root, Class<T> type,
            java.util.function.Predicate<T> condition) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && condition.test(type.cast(child))) return type.cast(child);
            if (child instanceof Container container) {
                T found = find(container, type, condition);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test void pageButtonsNavigateAndClearingCannotPrintAnOldDocument() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LetterDocumentView view = new LetterDocumentView();
            view.setDocument("<html><body><div class='page'>First</div><div class='page'>Second</div></body></html>");
            assertTrue(view.isDocumentReady());
            JButton next = find(view, JButton.class, b -> b.getText().equals("Next"));
            JButton previous = find(view, JButton.class, b -> b.getText().equals("Previous"));
            assertNotNull(next); assertNotNull(previous);
            assertFalse(previous.isEnabled()); next.doClick();
            assertNotNull(find(view, JLabel.class, l -> l.getText().equals("Page 2 of 2")));
            assertFalse(next.isEnabled()); assertTrue(previous.isEnabled());
            previous.doClick();
            assertNotNull(find(view, JLabel.class, l -> l.getText().equals("Page 1 of 2")));
            view.showMessage("Select another document");
            assertFalse(view.isDocumentReady()); assertFalse(previous.isEnabled()); assertFalse(next.isEnabled());
        });
    }

    @Test void documentSearchTreatsUserInputAsLiteralTextAndKeepsModelIdentity() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var model = Quotations.readOnlyModel("ID", "Document", "Customer");
            model.addRow(new Object[]{7, "QT-1042", "A+B Supplies"});
            model.addRow(new Object[]{9, "QT-1043", "Coastal Traders"});
            JTable table = new JTable(model); table.setAutoCreateRowSorter(true);
            var workspace = SalesDocumentWorkspace.create(null, table, new JPanel(), null);
            JTextField search = find(workspace, JTextField.class, f -> true);
            assertNotNull(search); search.setText("A+B");
            assertEquals(1, table.getRowCount());
            assertEquals(7, model.getValueAt(table.convertRowIndexToModel(0), 0));
            search.setText("["); assertEquals(0, table.getRowCount());
            search.setText(""); assertEquals(2, table.getRowCount());
        });
    }

    @Test void documentActionsUseTheStoredIdAfterColumnsAreHiddenReorderedAndSorted() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var model = Quotations.readOnlyModel("ID", "Document", "Customer");
            model.addRow(new Object[]{7L, "QT-1042", "Harbour"});
            model.addRow(new Object[]{9L, "QT-1043", "Coastal"});
            JTable table = new JTable(model); table.setAutoCreateRowSorter(true);
            table.removeColumn(table.getColumnModel().getColumn(0));
            table.moveColumn(0, 1);
            table.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(2, SortOrder.ASCENDING)));
            table.setRowSelectionInterval(0, 0);
            assertEquals(9L, SalesDocumentWorkspace.selectedModelId(table));
            table.setRowSelectionInterval(1, 1);
            assertEquals(7L, SalesDocumentWorkspace.selectedModelId(table));
            table.clearSelection(); assertNull(SalesDocumentWorkspace.selectedModelId(table));
        });
    }

    @Test void letterFormatAndFitKeepPaperInsideViewportWithoutDistortion() {
        var format = QuotationInvoiceDocumentPreview.letterPageFormat();
        assertEquals(612, format.getWidth()); assertEquals(792, format.getHeight());
        assertEquals(54, format.getImageableX()); assertEquals(18, format.getImageableY());
        for (Dimension size : List.of(new Dimension(320, 400), new Dimension(480, 600), new Dimension(900, 900))) {
            double scale = LetterDocumentView.fitScale(size.width, size.height, false);
            assertTrue(612 * scale <= size.width - 31);
            assertTrue(792 * scale <= size.height - 31);
        }
    }

    @Test void explicitPageBoundariesArePreservedForPrintingAndNavigation() throws Exception {
        String html = "<html><head><style>td{font-size:10px}</style></head><body>"
                + "<div class='page'>First</div><div class='page'>Second</div></body></html>";
        var pages = QuotationInvoiceDocumentPreview.extractSalesDocumentPages(html);
        assertEquals(2, pages.size());
        assertFalse(pages.get(0).contains("Second")); assertFalse(pages.get(1).contains("First"));
        SwingUtilities.invokeAndWait(() -> {
            var printer = new QuotationInvoiceDocumentPreview.HtmlPagePrintable(pages);
            var image = new BufferedImage(612, 792, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            try {
                assertEquals(Printable.PAGE_EXISTS, printer.print(graphics, QuotationInvoiceDocumentPreview.letterPageFormat(), 1));
                assertEquals(Printable.NO_SUCH_PAGE, printer.print(graphics, QuotationInvoiceDocumentPreview.letterPageFormat(), 2));
            } finally { graphics.dispose(); }
        });
    }

    @Test void compactWindowsSwitchViewsAndRetainEditorStateWhenWidened() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextField editor = new JTextField("Unsaved work");
            JPanel preview = new JPanel();
            DocumentWorkspace workspace = new DocumentWorkspace(editor, preview);
            workspace.setSize(980, 640);
            for (var listener : workspace.getComponentListeners()) listener.componentResized(null);
            assertInstanceOf(JTabbedPane.class, workspace.getComponent(0));
            workspace.setSize(1440, 800);
            for (var listener : workspace.getComponentListeners()) listener.componentResized(null);
            var split = assertInstanceOf(JSplitPane.class, workspace.getComponent(0));
            assertSame(editor, split.getLeftComponent()); assertSame(preview, split.getRightComponent());
            assertEquals("Unsaved work", editor.getText());
        });
    }
}
