package ui.screens;

import ui.components.DocumentWorkspace;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** The approved compact search/list surface, with saved documents alongside it. */
final class SalesDocumentWorkspace {
    private SalesDocumentWorkspace() { }
    static Long selectedModelId(JTable table) {
        int selected = table.getSelectedRow();
        if (selected < 0) return null;
        Object value = table.getModel().getValueAt(table.convertRowIndexToModel(selected), 0);
        return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
    }
    static JComponent create(Window owner, JTable table, JPanel actions, String type) {
        JPanel list = DocumentStudio.surface(new BorderLayout(0, 18));
        list.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
        JTextField search = new JTextField();
        search.setToolTipText("Document number, customer, status or reference");
        JTextField customer = new JTextField(); customer.setToolTipText("Filter customer name");
        JPanel filters = type == null ? DocumentStudio.field("Search", search) : DocumentStudio.stack(
                DocumentStudio.field("Customer", customer), DocumentStudio.field("Document", search));
        JPanel toolbar = DocumentStudio.surface(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JPopupMenu menu = new JPopupMenu();
        // Retain the original commands and their permission/server-side enforcement.
        List<JButton> commands = new ArrayList<>();
        for (Component component : actions.getComponents()) if (component instanceof JButton button) commands.add(button);
        for (JButton button : commands) {
            if (button.getText().startsWith("New") || button.getText().equals("Refresh")) {
                DocumentStudio.button(button, button.getText().startsWith("New")); toolbar.add(button);
            } else {
                JMenuItem item = new JMenuItem(button.getText()); item.addActionListener(e -> button.doClick());
                button.addPropertyChangeListener("enabled", e -> item.setEnabled(button.isEnabled()));
                item.setEnabled(button.isEnabled()); menu.add(item);
            }
        }
        if (menu.getComponentCount() > 0) {
            JButton more = new JButton("Actions"); DocumentStudio.button(more, false);
            more.addActionListener(e -> menu.show(more, 0, more.getHeight())); toolbar.add(more);
        }
        list.add(DocumentStudio.stack(filters, toolbar), BorderLayout.NORTH);
        DocumentStudio.table(table);
        JScrollPane rows = DocumentStudio.scroll(table); rows.setColumnHeaderView(table.getTableHeader());
        rows.setMinimumSize(new Dimension(280, 160)); list.add(rows, BorderLayout.CENTER);
        DocumentListener listener = new DocumentListener() {
            private void filter() {
                if (table.getRowSorter() instanceof TableRowSorter<?> sorter) {
                    List<RowFilter<Object, Object>> filters = new ArrayList<>();
                    if (!search.getText().isBlank()) filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(search.getText().trim())));
                    int column = -1;
                    for (int i = 0; i < table.getModel().getColumnCount(); i++) if ("Customer".equals(table.getModel().getColumnName(i))) column = i;
                    if (column >= 0 && !customer.getText().isBlank()) filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(customer.getText().trim()), column));
                    sorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
                }
            }
            public void insertUpdate(DocumentEvent e) { filter(); }
            public void removeUpdate(DocumentEvent e) { filter(); }
            public void changedUpdate(DocumentEvent e) { filter(); }
        };
        search.getDocument().addDocumentListener(listener); customer.getDocument().addDocumentListener(listener);
        if (type == null) { DocumentStudio.finish(list); return list; }
        if (table.getColumnModel().getColumnCount() > 0 && table.getColumnModel().getColumn(0).getModelIndex() == 0)
            table.removeColumn(table.getColumnModel().getColumn(0));
        // Operational detail columns remain available in the printout and Actions menu.
        int[] visible = "QUOTATION".equals(type) ? new int[]{1, 2, 5, 3}
                : "INVOICE".equals(type) ? new int[]{1, 2, 5, 4}
                : "DELIVERY_BILL".equals(type) ? new int[]{1, 3, 4, 6} : null;
        if (visible != null) {
            for (int i = table.getColumnCount() - 1; i >= 0; i--) {
                int index = table.getColumnModel().getColumn(i).getModelIndex();
                boolean keep = false; for (int value : visible) if (value == index) keep = true;
                if (!keep) table.removeColumn(table.getColumnModel().getColumn(i));
            }
            for (int i = 0; i < visible.length; i++) table.moveColumn(table.convertColumnIndexToView(visible[i]), i);
            table.getColumnModel().getColumn(0).setPreferredWidth(160);
            table.getColumnModel().getColumn(0).setMinWidth(150);
            table.getColumnModel().getColumn(1).setPreferredWidth(190);
            table.getColumnModel().getColumn(1).setMinWidth(140);
            table.getColumnModel().getColumn(2).setMinWidth(90);
            table.getColumnModel().getColumn(3).setMinWidth(90);
        }
        SalesDocumentPanel preview = new SalesDocumentPanel(owner);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            Long id = selectedModelId(table);
            if (id == null) { preview.clear(); return; }
            int modelRow = table.convertRowIndexToModel(table.getSelectedRow());
            preview.showDocument(type, id, String.valueOf(table.getModel().getValueAt(modelRow, 1)));
        });
        JComponent details = "INVOICE".equals(type) ? new InvoiceFulfillmentPanel(owner, table, list, commands) : list;
        DocumentStudio.finish(details);
        return new DocumentWorkspace(details, preview, "QUOTATION".equals(type) ? 0.42 : 0.5);
    }
}
