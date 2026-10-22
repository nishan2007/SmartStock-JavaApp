package ui.screens.customorders;

import services.CustomOrderDataService.CustomItemOption;
import services.CustomOrderDataService.VariantOption;
import ui.design.DeckersPalette;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Function;

/** Draft-only size selection; persisted orders still contain one line per shirt. */
final class CustomOrderSizeSetEditor extends JPanel {
    record SizeQuantity(VariantOption variant, int quantity) {
        SizeQuantity { if (variant == null || quantity < 1) throw new IllegalArgumentException("Enter a positive whole quantity."); }
    }
    final JButton button = new JButton("Sizes and quantities…");
    private final JLabel summary = new JLabel();
    private List<VariantOption> choices = List.of();
    private List<SizeQuantity> sizes = List.of();
    private Long itemId;
    private String colour;
    private Function<VariantOption, BigDecimal> price = v -> BigDecimal.ZERO;
    private Runnable changed = () -> {};

    CustomOrderSizeSetEditor() {
        super(new BorderLayout(6, 4)); setOpaque(false);
        button.setBackground(DeckersPalette.surface());
        summary.setForeground(DeckersPalette.text());
        add(button, BorderLayout.WEST); add(summary, BorderLayout.CENTER);
        button.addActionListener(e -> {
            try { open(); }
            catch (IllegalArgumentException ex) { JOptionPane.showMessageDialog(this, ex.getMessage()); }
        });
        button.setEnabled(false);
        summary.setText("Select a shirt item and colour to choose sizes.");
    }
    void onChanged(Runnable action) { changed = action; }
    static List<VariantOption> eligible(CustomItemOption item, List<VariantOption> variants, VariantOption selected) {
        if (!supportsSizes(item) || selected == null) return List.of();
        return variants.stream().filter(v -> v.effectiveSize() != null && !v.effectiveSize().isBlank())
            .filter(v -> Objects.equals(v.effectiveColor(), selected.effectiveColor())).toList();
    }
    static boolean supportsSizes(CustomItemOption item) {
        return item != null && item.customItemId() != null
            && ("FIXED".equals(item.pricingType()) || "VARIABLE".equals(item.pricingType()));
    }
    void configure(CustomItemOption item, List<VariantOption> variants, VariantOption selected, Function<VariantOption, BigDecimal> unitPrice) {
        Long nextId = item == null ? null : item.customItemId();
        String nextColour = selected == null ? null : selected.effectiveColor();
        if (!Objects.equals(itemId, nextId) || !Objects.equals(colour, nextColour)) reset();
        itemId = nextId; colour = nextColour; choices = eligible(item, variants, selected); price = unitPrice;
        button.setEnabled(choices.size() > 1); refresh();
    }
    List<SizeQuantity> selections() { return sizes; }
    void restore(List<SizeQuantity> selected) { sizes = List.copyOf(selected); refresh(); changed.run(); }
    void reset() { sizes = List.of(); refresh(); changed.run(); }
    private void refresh() {
        String hint = choices.size() > 1 ? "Choose quantities for each size."
            : "Needs two sizes in the selected colour.";
        summary.setText(sizes.isEmpty() ? hint : breakdown(sizes));
        button.setToolTipText(hint);
    }
    static String breakdown(List<SizeQuantity> rows) {
        return String.join(", ", rows.stream().map(r -> r.variant().effectiveSize() + " × " + r.quantity()).toList());
    }
    static int quantity(Object value) {
        try { int n = new BigDecimal(value.toString().trim()).intValueExact(); if (n < 0 || n > 5000) throw new ArithmeticException(); return n; }
        catch (Exception e) { throw new IllegalArgumentException("Enter whole quantities from 0 to 5,000; 0 omits a size."); }
    }
    record Content(JTable table, DefaultTableModel model, JPanel panel) {}
    Content content() {
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Size", "Quantity", "Unit price + add-ons", "Subtotal"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return col == 1; }
        };
        for (VariantOption v : choices) {
            int count = sizes.stream().filter(s -> Objects.equals(s.variant().variantId(), v.variantId())).mapToInt(SizeQuantity::quantity).sum();
            BigDecimal each = price.apply(v);
            model.addRow(new Object[]{v.effectiveSize(), count, each == null ? "Price pending" : each.toPlainString(), each == null ? "Price pending" : each.multiply(BigDecimal.valueOf(count)).toPlainString()});
        }
        model.addTableModelListener(e -> {
            if (e.getColumn() != 1 || e.getFirstRow() < 0) return;
            int row = e.getFirstRow();
            try { BigDecimal each = price.apply(choices.get(row)); model.setValueAt(each == null ? "Price pending" : each.multiply(BigDecimal.valueOf(quantity(model.getValueAt(row, 1)))).toPlainString(), row, 3); }
            catch (IllegalArgumentException ex) { model.setValueAt("Invalid quantity", row, 3); }
        });
        JTable table = new JTable(model); table.setRowHeight(30);
        table.setBackground(DeckersPalette.surface()); table.setForeground(DeckersPalette.text());
        table.setGridColor(DeckersPalette.border());
        table.getTableHeader().setBackground(DeckersPalette.tableStripe()); table.getTableHeader().setForeground(DeckersPalette.text());
        JScrollPane scroll = new JScrollPane(table); scroll.setPreferredSize(new Dimension(620, 240));
        scroll.setColumnHeaderView(table.getTableHeader()); scroll.getViewport().setBackground(DeckersPalette.surface());
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(DeckersPalette.background());
        panel.add(new JLabel("Shared colour, design and add-ons. Enter 0 for sizes you do not need."), BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return new Content(table, model, panel);
    }
    private void open() {
        var content = content();
        JTable table = content.table(); DefaultTableModel model = content.model(); JPanel panel = content.panel();
        while (JOptionPane.showConfirmDialog(this, panel, "Sizes and quantities", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            if (table.isEditing() && !table.getCellEditor().stopCellEditing()) continue;
            try {
                List<SizeQuantity> result = new ArrayList<>(); int total = 0;
                for (int row = 0; row < choices.size(); row++) { int n = quantity(model.getValueAt(row, 1)); total += n; if (n > 0) result.add(new SizeQuantity(choices.get(row), n)); }
                if (total == 0 || total > 5000) throw new IllegalArgumentException("Select between 1 and 5,000 shirts in the set.");
                restore(result); return;
            } catch (IllegalArgumentException ex) { JOptionPane.showMessageDialog(this, ex.getMessage()); }
        }
    }
}
