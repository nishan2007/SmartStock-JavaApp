package ui.screens;

import ui.design.DeckersPalette;
import ui.design.DeckersLogoManager;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/** The Clear Ledger customer workspace. All colors follow the shared device theme. */
final class CustomerAccountsDesign {
    private CustomerAccountsDesign() { }

    static Font font(int style, int size) { return new Font("Segoe UI", style, size); }
    static JPanel panel(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }
    static JLabel label(String text, int size, boolean bold) {
        JLabel l = new JLabel(text) {
            @Override public Color getForeground() {
                Object tone = getClientProperty("customerTone");
                if ("due".equals(tone)) return DeckersPalette.CORAL;
                if ("credit".equals(tone)) return DeckersPalette.dark() ? new Color(217, 180, 255) : DeckersPalette.PURPLE;
                if ("muted".equals(tone)) return DeckersPalette.muted();
                return DeckersPalette.text();
            }
        };
        l.putClientProperty("SmartStock.preserveForeground", true);
        l.setFont(font(bold ? Font.BOLD : Font.PLAIN, size));
        l.setForeground(DeckersPalette.text());
        return l;
    }
    static JButton button(String text, String icon, boolean primary) {
        JButton b = SalesTaskVisuals.button(text, icon, primary);
        b.setFont(font(primary ? Font.BOLD : Font.PLAIN, 16));
        b.setMargin(new Insets(8, 14, 8, 14));
        b.setMinimumSize(new Dimension(0, 38));
        if (icon != null) b.setIcon(new Icon() {
            public int getIconWidth() { return 18; }
            public int getIconHeight() { return 18; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Color color = !c.isEnabled() ? DeckersPalette.muted() : primary ? Color.WHITE : Boolean.TRUE.equals(b.getClientProperty("taskOutline")) ? DeckersPalette.ORANGE : DeckersPalette.muted();
                SalesTaskVisuals.icon(icon,color,18).paintIcon(c,g,x,y);
            }
        });
        return b;
    }
    static JPanel surface(LayoutManager layout, Color accent) {
        JPanel p = new JPanel(layout) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                SalesTaskVisuals.antialias(g);
                g.setColor(Boolean.TRUE.equals(getClientProperty("customerTint")) ? DeckersPalette.tileFill(accent) : DeckersPalette.surface());
                g.fillRoundRect(0, 0, getWidth()-1, getHeight()-1, 8, 8);
                g.setColor(DeckersPalette.sectionBorder(accent));
                g.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 8, 8);
                g.dispose();
            }
        };
        p.setOpaque(false);
        return p;
    }
    static JScrollPane scroll(JComponent content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }
    static void styleTree(Component component) {
        if (component instanceof JLabel label) label.setForeground(DeckersPalette.text());
        if (component instanceof JTextField field) {
            field.setFont(font(Font.PLAIN, 14));
            field.setBackground(DeckersPalette.fieldBackground());
            field.setForeground(DeckersPalette.text());
            field.setCaretColor(DeckersPalette.text());
            field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(DeckersPalette.border()), new EmptyBorder(3, 9, 3, 9)));
            field.setPreferredSize(new Dimension(100, 29));
            field.setMinimumSize(new Dimension(60, 29));
        } else if (component instanceof JTextArea area) {
            area.setFont(font(Font.PLAIN, 14));
            area.setForeground(DeckersPalette.text());
            area.setBackground(DeckersPalette.fieldBackground());
            area.setCaretColor(DeckersPalette.text());
            area.setBorder(new EmptyBorder(6, 9, 6, 9));
        } else if (component instanceof JCheckBox check) {
            check.setFont(font(Font.PLAIN, 13));
            check.setForeground(DeckersPalette.text());
            check.setOpaque(false);
        } else if (component instanceof JComboBox<?> combo) {
            combo.setFont(font(Font.PLAIN, 14));
            combo.setForeground(DeckersPalette.text());
            combo.setBackground(DeckersPalette.fieldBackground());
            combo.setPreferredSize(new Dimension(100, 29));
        }
        if (component instanceof Container c) for (Component child : c.getComponents()) styleTree(child);
    }
    static JLabel logo() {
        JLabel label = new JLabel() {
            @Override protected void paintComponent(Graphics graphics) {
                if (DeckersPalette.dark()) {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRoundRect(0,0,getWidth(),getHeight(),6,6);
                }
                super.paintComponent(graphics);
            }
        };
        var url = CustomerAccounts.class.getResource("/Images/SmartStockLogoVariations/New/transparent/primary-horizontal.png");
        ImageIcon source = url == null ? DeckersLogoManager.loadSmartStockLogoIcon(CustomerAccounts.class) : new ImageIcon(url);
        if (source != null) label.setIcon(new ImageIcon(DeckersLogoManager.scaleToFit(source.getImage(), 204, 48)));
        else label.setText("SmartStock");
        return label;
    }

    static String initials(String name) {
        if (name == null || name.isBlank()) return "";
        String[] words = name.trim().split("\\s+");
        return (words[0].substring(0, 1) + (words.length > 1 ? words[words.length-1].substring(0, 1) : "")).toUpperCase(java.util.Locale.ROOT);
    }
    static final class Avatar extends JLabel {
        Avatar(int size) {
            setPreferredSize(new Dimension(size, size));
            setHorizontalAlignment(CENTER);
            setFont(font(Font.BOLD, size > 55 ? 24 : 19));
        }
        @Override public Color getForeground() { return DeckersPalette.dark() ? new Color(217, 180, 255) : DeckersPalette.PURPLE; }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            SalesTaskVisuals.antialias(g);
            g.setColor(DeckersPalette.blend(DeckersPalette.surface(), DeckersPalette.PURPLE, .13));
            int size = Math.min(getWidth(), getHeight());
            g.fillOval((getWidth()-size)/2, (getHeight()-size)/2, size, size);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    /** Model indices stay stable for saves and selection; the view shows six useful columns. */
    static void configureTable(JTable table) {
        table.setFont(font(Font.PLAIN, 16));
        table.putClientProperty("SmartStock.preserveBackground", true);
        table.setRowHeight(68);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(DeckersPalette.border());
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionBackground(DeckersPalette.blend(DeckersPalette.surface(), DeckersPalette.ORANGE, .12));
        table.setSelectionForeground(DeckersPalette.text());
        table.setBackground(DeckersPalette.surface());
        table.setForeground(DeckersPalette.text());
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.getTableHeader().setPreferredSize(new Dimension(0, 44));
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setDefaultRenderer((t, value, selected, focus, row, col) -> {
            JLabel label = label(String.valueOf(value), 15, true);
            label.setOpaque(true);
            label.setBackground(DeckersPalette.tableHeader(DeckersPalette.PURPLE));
            label.setBorder(new EmptyBorder(8, col == 0 ? 62 : 10, 8, 8));
            label.setHorizontalTextPosition(SwingConstants.LEFT);
            label.setIconTextGap(8);
            if (t.getRowSorter() != null) {
                int modelColumn = t.convertColumnIndexToModel(col);
                for (RowSorter.SortKey key : t.getRowSorter().getSortKeys()) {
                    if (key.getColumn() == modelColumn && key.getSortOrder() != SortOrder.UNSORTED) {
                        label.setIcon(UIManager.getIcon(key.getSortOrder() == SortOrder.ASCENDING
                                ? "Table.ascendingSortIcon" : "Table.descendingSortIcon"));
                        break;
                    }
                }
            }
            return label;
        });
        table.getTableHeader().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        table.getTableHeader().setToolTipText("Click a heading to sort; click again to reverse the order");
        int[] visible = {2, 4, 8, 9, 10};
        var columns = table.getColumnModel();
        for (int i = columns.getColumnCount()-1; i >= 0; i--) {
            int modelIndex = columns.getColumn(i).getModelIndex();
            boolean keep = false;
            for (int index : visible) if (index == modelIndex) keep = true;
            if (!keep) columns.removeColumn(columns.getColumn(i));
        }
        String[] titles = {"Customer", "Type", "Credit Balance", "Order Due", "Total Due"};
        int[] widths = {290, 130, 150, 135, 150};
        for (int i = 0; i < titles.length; i++) {
            var column = columns.getColumn(i);
            column.setHeaderValue(titles[i]);
            column.setPreferredWidth(widths[i]);
            column.setMinWidth(i == 0 ? 155 : 75);
            column.setCellRenderer(new CustomerRenderer());
        }
    }
    private static final class CustomerRenderer implements TableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
            JPanel cell = panel(new BorderLayout(12, 0));
            cell.setOpaque(true);
            cell.setBorder(new EmptyBorder(8, column == 0 ? 18 : 10, 8, 8));
            int modelColumn = table.convertColumnIndexToModel(column);
            int modelRow = table.convertRowIndexToModel(row);
            boolean inactive = !Boolean.TRUE.equals(table.getModel().getValueAt(modelRow, 13));
            cell.setBackground(inactive ? DeckersPalette.blend(DeckersPalette.surface(), DeckersPalette.CORAL, selected ? .30 : .15)
                    : selected ? DeckersPalette.blend(DeckersPalette.surface(), DeckersPalette.ORANGE, .12)
                    : row % 2 == 0 ? DeckersPalette.surface() : DeckersPalette.tableStripe());
            if (inactive) cell.setToolTipText("Inactive customer account");
            if (modelColumn == 2) {
                Avatar avatar = new Avatar(46);
                avatar.setText(initials(String.valueOf(value)));
                cell.add(avatar, BorderLayout.WEST);
                JPanel identity = panel(new GridLayout(2, 1, 0, 0));
                JLabel name = label(String.valueOf(value), 18, true);
                JLabel account = label(String.valueOf(table.getModel().getValueAt(modelRow, 1)), 15, false);
                account.setForeground(DeckersPalette.muted());
                account.putClientProperty("customerTone", "muted");
                identity.add(name); identity.add(account);
                cell.add(identity);
            } else {
                JLabel text = label(value == null ? "" : value.toString(), 17, false);
                cell.add(text);
            }
            if (focus) cell.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(DeckersPalette.ORANGE), new EmptyBorder(7, 9, 7, 7)));
            return cell;
        }
    }
}
