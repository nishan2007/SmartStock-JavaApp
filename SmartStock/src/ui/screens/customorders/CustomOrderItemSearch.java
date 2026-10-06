package ui.screens.customorders;

import services.CustomOrderDataService;
import services.CustomOrderDataService.ItemSearchOption;
import ui.helpers.UiTaskRunner;
import ui.design.DeckersPalette;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.function.Consumer;

/** Suggestions for the custom-order Search / Scan field, without blocking typing. */
final class CustomOrderItemSearch {
    final DefaultListModel<ItemSearchOption> model = new DefaultListModel<>();
    final JList<ItemSearchOption> list = new JList<>(model);
    final JPopupMenu popup = new JPopupMenu();
    private final JTextField field;
    private final Consumer<ItemSearchOption> selection;
    private final Runnable lookup;
    private final Timer debounce;
    private final JLabel status = new JLabel();
    private final JScrollPane scroll = new JScrollPane(list);
    private long generation;
    private boolean selecting;
    private ItemSearchOption chosen;

    CustomOrderItemSearch(JTextField field, Consumer<ItemSearchOption> selection, Runnable lookup) {
        this.field = field;
        this.selection = selection;
        this.lookup = lookup;
        debounce = new Timer(250, e -> search());
        debounce.setRepeats(false);
        popup.setFocusable(false);
        popup.setLayout(new BorderLayout());
        popup.add(scroll, BorderLayout.CENTER);
        popup.add(status, BorderLayout.SOUTH);
        popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) { }
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) { }
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) { dismiss(); }
        });
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFocusable(false);
        list.setVisibleRowCount(8);
        list.setBackground(DeckersPalette.surface());
        list.setForeground(DeckersPalette.text());
        status.setForeground(DeckersPalette.muted());
        status.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { changed(); }
            public void removeUpdate(DocumentEvent e) { changed(); }
            public void changedUpdate(DocumentEvent e) { changed(); }
            private void changed() {
                if (selecting) return;
                chosen = null;
                generation++;
                popup.setVisible(false);
                model.clear();
                debounce.restart();
            }
        });
        field.addActionListener(e -> lookup());
        field.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    dismiss();
                    e.consume();
                } else if (popup.isVisible() && !model.isEmpty()
                        && (e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_UP)) {
                    int offset = e.getKeyCode() == KeyEvent.VK_DOWN ? 1 : -1;
                    int index = Math.floorMod(list.getSelectedIndex() + offset, model.size());
                    list.setSelectedIndex(index);
                    list.ensureIndexIsVisible(index);
                    e.consume();
                }
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (SwingUtilities.isLeftMouseButton(e) && index >= 0
                        && list.getCellBounds(index, index).contains(e.getPoint())) {
                    list.setSelectedIndex(index);
                    selectHighlighted();
                }
            }
        });
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) {
                // Let a click on the suggestion list finish before dismissing it.
                SwingUtilities.invokeLater(() -> { if (!field.hasFocus()) dismiss(); });
            }
        });
        field.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !field.isShowing()) dismiss();
        });
    }

    void dismiss() {
        generation++;
        debounce.stop();
        popup.setVisible(false);
        model.clear();
    }

    void lookup() {
        if (popup.isVisible() && !model.isEmpty()) selectHighlighted();
        else if (chosen != null && chosen.label().equals(field.getText().trim())) selection.accept(chosen);
        else { dismiss(); lookup.run(); }
    }

    void selectHighlighted() {
        ItemSearchOption option = list.getSelectedValue();
        if (option == null) return;
        dismiss();
        chosen = option;
        selecting = true;
        try { field.setText(option.label()); }
        finally { selecting = false; }
        selection.accept(option);
    }

    private void search() {
        String query = field.getText().trim();
        if (query.isEmpty() || !field.hasFocus() || !field.isShowing()) return;
        Window owner = SwingUtilities.getWindowAncestor(field);
        if (owner == null) return;
        long request = generation;
        UiTaskRunner.submit(owner, "custom-orders.item-suggestions-" + System.identityHashCode(this),
                () -> CustomOrderDataService.searchCustomItems(query),
                options -> acceptResults(request, query, options),
                failure -> {
                    if (!current(request, query)) return;
                    model.clear();
                    showPopup("Cannot load suggestions. Check the server connection, then try Lookup.");
                });
    }

    boolean current(long request, String query) {
        return request == generation && query.equals(field.getText().trim());
    }

    void acceptResults(long request, String query, List<ItemSearchOption> options) {
        if (!current(request, query)) return;
        model.clear();
        options.forEach(model::addElement);
        if (!model.isEmpty()) list.setSelectedIndex(0);
        showPopup(model.isEmpty() ? "No matching custom items or variants." : "");
    }

    private void showPopup(String message) {
        if (!field.isShowing() || !field.hasFocus()) return;
        scroll.setVisible(!model.isEmpty());
        status.setText(message);
        status.setToolTipText(message.isEmpty() ? null : message);
        status.setVisible(!message.isEmpty());
        popup.setPopupSize(Math.max(360, field.getWidth()), model.isEmpty() ? 36 : 220);
        popup.show(field, 0, field.getHeight());
        field.requestFocusInWindow();
    }
}
