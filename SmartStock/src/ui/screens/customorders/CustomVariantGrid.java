package ui.screens.customorders;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import services.LanCustomOrderCatalogAdminService.Item;
import services.LanCustomOrderCatalogAdminService.Variant;
import services.LanJson;
import ui.helpers.BarcodeGenerationHelper;
import ui.helpers.ProductImageHelper;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EventObject;
import java.util.List;
import java.util.function.BiConsumer;

/** Editable variant list. The table always displays the last confirmed server values. */
final class CustomVariantGrid extends JTable {
    private static final com.google.gson.Gson GSON = LanJson.create();
    private static final String[] UNITS = {"IN", "FT", "YD", "CM", "M"};
    private static final String[] SIZING = {"Inherit item setting", "Use order measurements", "Use template dimensions"};

    private enum Column {
        ID("ID", 55, null), NAME("Name", 211, "name"), SIZE("Size", 77, "size"), COLOR("Color", 76, "color"),
        BRAND("Brand", 92, "brand"), SKU("SKU", 100, null), BARCODE("Barcode", 113, "barcode"),
        PRICE("Price", 82, "fixedPrice"), QUANTITY("Qty", 52, "quantity"), REORDER("Reorder", 68, "reorderLevel"),
        ACTIVE("Active", 70, "active"), EXTRA("More barcodes", 128, "extraBarcodes"),
        WIDTH("Template width", 121, "templateWidth"), HEIGHT("Template height", 125, "templateHeight"),
        UNIT("Template unit", 105, "templateUnit"), SIZING_MODE("Area design size", 150, "templateUsesOrderArea"),
        PHOTOS("Photos", 88, "imageUrl");

        final String heading, field;
        final int width;

        Column(String heading, int width, String field) {
            this.heading = heading;
            this.width = width;
            this.field = field;
        }

        boolean opensEditor() { return this == BARCODE || this == EXTRA || this == PHOTOS; }
    }

    private final JDialog owner;
    private final BiConsumer<Variant, JsonObject> save;
    private final VariantModel rows = new VariantModel();
    private Item item;
    private boolean fullScreen;
    private boolean editable = true;
    private boolean dimensionEditPending;

    CustomVariantGrid(JDialog owner, Item item, BiConsumer<Variant, JsonObject> save) {
        this.owner = owner;
        this.item = item;
        this.save = save;
        setModel(rows);
        CustomCatalogDesign.table(this);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setColumnSelectionAllowed(true);
        setRowSorter(new TableRowSorter<>(rows));
        setAutoResizeMode(AUTO_RESIZE_OFF);
        putClientProperty("terminateEditOnFocusLost", true);
        setSurrendersFocusOnKeystroke(true);
        configureColumns();
    }

    void setFullScreen(boolean expanded) {
        Long selected = selectedVariant() == null ? null : selectedVariant().variantId();
        int column = selectedModelColumn();
        var sort = getRowSorter().getSortKeys();
        fullScreen = expanded;
        rows.fireTableStructureChanged();
        configureColumns();
        setAutoResizeMode(expanded ? AUTO_RESIZE_ALL_COLUMNS : AUTO_RESIZE_OFF);
        getRowSorter().setSortKeys(sort.stream().filter(key -> key.getColumn() < rows.getColumnCount()).toList());
        restoreSelection(selected, column);
    }

    @Override public boolean getScrollableTracksViewportWidth() {
        if (!fullScreen || !(getParent() instanceof JViewport viewport)) return false;
        int minimum = 0;
        for (int index = 0; index < getColumnCount(); index++) minimum += getColumnModel().getColumn(index).getMinWidth();
        return viewport.getWidth() >= minimum;
    }

    boolean isFullScreen() { return fullScreen; }

    boolean hasPendingDimensionEdit() { return dimensionEditPending; }

    void setEditable(boolean editable) { this.editable = editable; }

    void reload(Item updated) {
        Long selected = selectedVariant() == null ? null : selectedVariant().variantId();
        int column = selectedModelColumn();
        item = updated;
        rows.fireTableDataChanged();
        restoreSelection(selected, column);
    }

    Variant selectedVariant() {
        return getSelectedRow() < 0 ? null : item.variants().get(convertRowIndexToModel(getSelectedRow()));
    }

    private int selectedModelColumn() {
        return getSelectedColumn() < 0 ? -1 : convertColumnIndexToModel(getSelectedColumn());
    }

    private void restoreSelection(Long id, int modelColumn) {
        if (id == null) return;
        for (int row = 0; row < item.variants().size(); row++) {
            if (item.variants().get(row).variantId() != id) continue;
            int view = convertRowIndexToView(row);
            if (view >= 0) {
                if (modelColumn >= 0 && modelColumn < getColumnCount()) {
                    changeSelection(view, convertColumnIndexToView(modelColumn), false, false);
                } else setRowSelectionInterval(view, view);
            }
            return;
        }
    }

    private void configureColumns() {
        @SuppressWarnings("unchecked")
        var sorter = (TableRowSorter<VariantModel>) getRowSorter();
        Comparator<String> numeric = Comparator.comparing(value -> value.isBlank() ? null
                : new BigDecimal(value), Comparator.nullsLast(Comparator.naturalOrder()));
        for (Column column : List.of(Column.PRICE, Column.QUANTITY, Column.REORDER, Column.WIDTH, Column.HEIGHT)) {
            if (column.ordinal() < getColumnCount()) sorter.setComparator(column.ordinal(), numeric);
        }
        for (int index = 0; index < getColumnCount(); index++) {
            Column column = Column.values()[index];
            var display = getColumnModel().getColumn(index);
            if (fullScreen) {
                FontMetrics metrics = getTableHeader().getFontMetrics(getTableHeader().getFont());
                display.setMinWidth(metrics.stringWidth(column.heading) + 20);
            }
            display.setPreferredWidth(column.width);
            if (column == Column.SIZING_MODE) display.setCellEditor(new DefaultCellEditor(new JComboBox<>(SIZING)));
            if (column == Column.UNIT) display.setCellEditor(new DefaultCellEditor(new JComboBox<>(UNITS)));
            if (column == Column.ACTIVE) display.setCellEditor(new DefaultCellEditor(new JComboBox<>(new String[]{"Active", "Inactive"})));
            if (column == Column.BRAND) {
                var renderer = getDefaultRenderer(Object.class);
                display.setCellRenderer((table, value, selected, focused, row, viewColumn) ->
                        renderer.getTableCellRendererComponent(table, value.toString().isBlank() ? item.brand() : value,
                                selected, focused, row, viewColumn));
            }
            if (column == Column.PRICE && !fullScreen) {
                var renderer = getDefaultRenderer(Object.class);
                display.setCellRenderer((table, value, selected, focused, row, viewColumn) ->
                        renderer.getTableCellRendererComponent(table, OrdersDesign.money(value), selected, focused, row, viewColumn));
            }
        }
    }

    @Override public boolean editCellAt(int row, int column, EventObject event) {
        if (row < 0 || column < 0 || !isCellEditable(row, column)) return false;
        Column field = Column.values()[convertColumnIndexToModel(column)];
        if (!field.opensEditor()) return super.editCellAt(row, column, event);
        if (event instanceof MouseEvent click && click.getClickCount() < 2) return false;
        Variant variant = item.variants().get(convertRowIndexToModel(row));
        SwingUtilities.invokeLater(() -> {
            if (editable && field.ordinal() < getColumnCount()) editOptions(field, variant);
        });
        return false;
    }

    @Override public String getToolTipText(MouseEvent event) {
        int row = rowAtPoint(event.getPoint()), column = columnAtPoint(event.getPoint());
        if (row < 0 || column < 0) return null;
        Column field = Column.values()[convertColumnIndexToModel(column)];
        if (field == Column.BRAND && item.variants().get(convertRowIndexToModel(row)).brand().isBlank()) {
            return "Inherited brand: " + item.brand() + ". Enter a brand to override it.";
        }
        if (field == Column.WIDTH || field == Column.HEIGHT) return "Blank dimensions inherit from the parent item.";
        if (field.opensEditor()) return "Double-click or press F2 to edit " + field.heading.toLowerCase(java.util.Locale.ROOT);
        return super.getToolTipText(event);
    }

    private void editOptions(Column column, Variant variant) {
        switch (column) {
            case BARCODE -> {
                JTextField input = new JTextField(variant.barcode(), 22);
                if (confirm(column, BarcodeGenerationHelper.field(input, input))) commit(column, variant, input.getText());
            }
            case EXTRA -> {
                JTextArea input = new JTextArea(String.join("\n", variant.extraBarcodes()), 5, 26);
                if (confirm(column, BarcodeGenerationHelper.area(input, input, new JScrollPane(input)))) {
                    commit(column, variant, input.getText());
                }
            }
            case PHOTOS -> {
                JPanel content = new JPanel(new BorderLayout());
                var images = ProductImageHelper.createSimpleImageSelector(content);
                content.add(images);
                images.setImageUrl(variant.imageUrl());
                images.setAdditionalImageUrls(variant.additionalImageUrls());
                if (confirm(column, content)) {
                    JsonObject changes = new JsonObject();
                    changes.addProperty("imageUrl", images.getImageUrl());
                    changes.add("additionalImageUrls", GSON.toJsonTree(images.getAdditionalImageUrls()));
                    save.accept(variant, changes);
                }
            }
            default -> throw new IllegalArgumentException("This field does not open an editor.");
        }
    }

    private boolean confirm(Column column, JComponent content) {
        return JOptionPane.showConfirmDialog(owner, content, column.heading, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }

    private void commit(Column column, Variant variant, Object value) {
        try {
            String text = value.toString().strip();
            if (column == Column.WIDTH || column == Column.HEIGHT) {
                dimensionEditPending = true;
                SwingUtilities.invokeLater(() -> {
                    try { editDimension(column, variant, text); }
                    finally { dimensionEditPending = false; }
                });
                return;
            }
            if (column == Column.UNIT) {
                saveTemplate(variant, variant.templateWidth(), variant.templateHeight(), text);
                return;
            }
            JsonObject changes = new JsonObject();
            switch (column) {
                case NAME -> {
                    if (text.isEmpty()) throw new IllegalArgumentException("Enter a variant name.");
                    changes.addProperty(column.field, text);
                }
                case PRICE, QUANTITY, REORDER -> {
                    BigDecimal amount = decimal(text, column != Column.PRICE);
                    if (column == Column.REORDER && amount.signum() < 0) throw new IllegalArgumentException("Enter a non-negative reorder level.");
                    changes.addProperty(column.field, amount);
                }
                case ACTIVE -> changes.addProperty(column.field, "Active".equals(text));
                case SIZING_MODE -> {
                    if (SIZING[1].equals(text) && !"AREA".equals(item.pricingType())) {
                        throw new IllegalArgumentException("Order measurements are only available for area-priced variants.");
                    }
                    if (SIZING[0].equals(text)) changes.add(column.field, JsonNull.INSTANCE);
                    else changes.addProperty(column.field, SIZING[1].equals(text));
                }
                case EXTRA -> changes.add(column.field, GSON.toJsonTree(Arrays.stream(text.split("\\R"))
                        .map(String::strip).filter(line -> !line.isBlank()).distinct().toList()));
                default -> changes.addProperty(column.field, text);
            }
            save.accept(variant, changes);
        } catch (IllegalArgumentException invalid) { showInvalid(invalid); }
    }

    private void editDimension(Column column, Variant variant, String text) {
        if (!fullScreen || !editable) return;
        try {
            BigDecimal value = dimension(text, false);
            BigDecimal previous = column == Column.WIDTH ? variant.templateWidth() : variant.templateHeight();
            if (value == null && previous == null || value != null && previous != null && value.compareTo(previous) == 0) return;
            if (value == null) {
                if (JOptionPane.showConfirmDialog(owner, "Clear both dimensions and inherit the parent template?",
                        "Inherit Template Dimensions", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                    saveTemplate(variant, null, null, null);
                }
                return;
            }
            BigDecimal other = column == Column.WIDTH ? variant.templateHeight() : variant.templateWidth();
            String unit = variant.templateUnit();
            if (other == null) {
                Column companion = column == Column.WIDTH ? Column.HEIGHT : Column.WIDTH;
                JTextField field = new JTextField(12);
                JComboBox<String> units = new JComboBox<>(UNITS);
                if (item.templateUnit() != null) units.setSelectedItem(item.templateUnit());
                JPanel fields = new JPanel(new GridLayout(0, 2, 6, 6));
                fields.add(new JLabel(companion.heading)); fields.add(field);
                fields.add(new JLabel("Template unit")); fields.add(units);
                if (!confirm(companion, fields)) return;
                other = dimension(field.getText(), true);
                unit = (String) units.getSelectedItem();
            }
            if (unit == null) unit = UNITS[0];
            saveTemplate(variant, column == Column.WIDTH ? value : other, column == Column.HEIGHT ? value : other, unit);
        } catch (IllegalArgumentException invalid) { showInvalid(invalid); }
    }

    private void saveTemplate(Variant variant, BigDecimal width, BigDecimal height, String unit) {
        JsonObject changes = new JsonObject();
        changes.addProperty("templateWidth", width);
        changes.addProperty("templateHeight", height);
        changes.addProperty("templateUnit", unit);
        save.accept(variant, changes);
    }

    private static BigDecimal dimension(String text, boolean required) {
        BigDecimal amount = decimal(text.strip(), required);
        if (amount != null && (amount.signum() <= 0 || amount.compareTo(BigDecimal.valueOf(10_000)) > 0)) {
            throw new IllegalArgumentException("Enter a template dimension greater than zero and at most 10,000.");
        }
        return amount;
    }

    private void showInvalid(IllegalArgumentException error) {
        JOptionPane.showMessageDialog(owner, error.getMessage(), "Variant value", JOptionPane.WARNING_MESSAGE);
    }

    private static BigDecimal decimal(String text, boolean required) {
        if (text.isBlank() && !required) return null;
        try { return new BigDecimal(text.strip()); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Enter a valid number."); }
    }

    private static String number(BigDecimal value) { return value == null ? "" : value.stripTrailingZeros().toPlainString(); }

    private final class VariantModel extends AbstractTableModel {
        @Override public int getRowCount() { return item.variants().size(); }
        @Override public int getColumnCount() { return fullScreen ? Column.values().length : Column.ACTIVE.ordinal() + 1; }
        @Override public String getColumnName(int column) { return Column.values()[column].heading; }
        @Override public Class<?> getColumnClass(int column) { return column == Column.ID.ordinal() ? Long.class : Object.class; }
        @Override public boolean isCellEditable(int row, int column) {
            Column field = Column.values()[column];
            return editable && field.field != null
                    && !dimensionEditPending && (field != Column.UNIT || item.variants().get(row).templateWidth() != null
                            && item.variants().get(row).templateHeight() != null);
        }

        @Override public Object getValueAt(int row, int index) {
            Variant variant = item.variants().get(row);
            return switch (Column.values()[index]) {
                case ID -> variant.variantId(); case NAME -> variant.name(); case SIZE -> variant.size(); case COLOR -> variant.color();
                case BRAND -> variant.brand(); case SKU -> variant.sku();
                case BARCODE -> variant.barcode(); case PRICE -> number(variant.fixedPrice());
                case QUANTITY -> number(variant.quantity()); case REORDER -> number(variant.reorderLevel());
                case ACTIVE -> variant.active() ? "Active" : "Inactive";
                case EXTRA -> String.join(", ", variant.extraBarcodes());
                case WIDTH -> number(variant.templateWidth());
                case HEIGHT -> number(variant.templateHeight());
                case UNIT -> variant.templateUnit() == null ? "Inherit" : variant.templateUnit();
                case SIZING_MODE -> SIZING[variant.templateUsesOrderArea() == null ? 0 : variant.templateUsesOrderArea() ? 1 : 2];
                case PHOTOS -> (variant.additionalImageUrls().size() + (variant.imageUrl() == null || variant.imageUrl().isBlank() ? 0 : 1)) + " photos";
            };
        }

        @Override public void setValueAt(Object value, int row, int column) {
            if (!isCellEditable(row, column)) return;
            Column field = Column.values()[column];
            if (!field.opensEditor()) commit(field, item.variants().get(row), value);
        }
    }
}
