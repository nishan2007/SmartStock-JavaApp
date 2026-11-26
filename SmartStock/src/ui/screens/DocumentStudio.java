package ui.screens;

import ui.design.DeckersPalette;
import ui.design.DeckersLogoManager;
import ui.design.DeckersSwing;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/** Shared layout vocabulary for the approved Document Studio screens. */
final class DocumentStudio {
    private DocumentStudio() { }
    static JPanel surface(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.putClientProperty("SmartStock.preserveBackground", true);
        panel.setBackground(DeckersPalette.surface());
        return panel;
    }
    static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        label.setForeground(DeckersPalette.text());
        return label;
    }
    static JPanel field(String caption, JComponent control) {
        JPanel panel = surface(new BorderLayout(0, 7));
        JLabel label = label(caption); label.setLabelFor(control);
        panel.add(label, BorderLayout.NORTH); panel.add(control, BorderLayout.CENTER);
        return panel;
    }
    static JPanel row(JComponent... components) {
        JPanel panel = surface(new GridLayout(1, components.length, 14, 0));
        for (JComponent component : components) panel.add(component);
        return panel;
    }
    static JPanel stack(JComponent... components) {
        JPanel panel = surface(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.anchor = GridBagConstraints.NORTH;
        for (int i = 0; i < components.length; i++) {
            c.gridy = i; c.insets = new Insets(i == 0 ? 0 : 12, 0, 0, 0); panel.add(components[i], c);
        }
        return panel;
    }
    static JPanel section(String title, JComponent content) {
        JPanel panel = surface(new BorderLayout(0, 12));
        JLabel heading = label(title); heading.setFont(heading.getFont().deriveFont(16f));
        heading.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DeckersPalette.border()), new EmptyBorder(0, 0, 10, 0)));
        panel.add(heading, BorderLayout.NORTH); panel.add(content, BorderLayout.CENTER);
        return panel;
    }
    static JScrollPane scroll(JComponent content) {
        JScrollPane pane = new JScrollPane(content);
        pane.setBorder(null); pane.getViewport().setBackground(DeckersPalette.surface());
        pane.getVerticalScrollBar().setUnitIncrement(24);
        return pane;
    }
    static JComponent topAligned(JComponent content) {
        class FormViewport extends JPanel implements Scrollable {
            FormViewport() { super(new BorderLayout()); setBackground(DeckersPalette.surface()); putClientProperty("SmartStock.preserveBackground", true); }
            public Dimension getPreferredScrollableViewportSize() { return new Dimension(560, 600); }
            public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return 24; }
            public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return Math.max(24, r.height - 24); }
            public boolean getScrollableTracksViewportWidth() { return true; }
            public boolean getScrollableTracksViewportHeight() { return false; }
        }
        JPanel panel = new FormViewport(); panel.add(content, BorderLayout.NORTH);
        return panel;
    }
    static JPanel inline(JComponent... controls) {
        JPanel panel = surface(new FlowLayout(FlowLayout.LEFT, 0, 0));
        for (JComponent control : controls) panel.add(control);
        return panel;
    }
    static JPanel workflow(int active) {
        JPanel row = surface(new FlowLayout(FlowLayout.LEFT, 9, 0));
        String[] stages = {"Draft", "Accepted", "Invoiced", "Delivered"};
        for (int i = 0; i < stages.length; i++) {
            JLabel stage = label(stages[i]);
            stage.setFont(stage.getFont().deriveFont(12f));
            stage.setBorder(new EmptyBorder(5, 10, 5, 10));
            if (i == active) {
                stage.setOpaque(true); stage.setBackground(DeckersPalette.PURPLE); stage.setForeground(Color.WHITE);
                stage.putClientProperty("SmartStock.preserveForeground", true);
            } else stage.setForeground(DeckersPalette.muted());
            row.add(stage);
            if (i + 1 < stages.length) { JLabel separator = new JLabel("›"); separator.setForeground(DeckersPalette.muted()); row.add(separator); }
        }
        return row;
    }
    static JComponent templateSwitch(JComboBox<String> selection) {
        JPanel panel = surface(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JButton[] buttons = {new JButton("Grid"), new JButton("Compact")};
        Runnable update = () -> {
            for (int i = 0; i < buttons.length; i++) {
                JButton button = buttons[i]; boolean active = selection.getSelectedIndex() == i;
                button(button, false);
                Color fill = active ? DeckersPalette.PURPLE : DeckersPalette.surface();
                Color ink = active ? Color.WHITE : DeckersPalette.text();
                button.setBackground(fill); button.setForeground(ink); button.setEnabled(selection.isEnabled());
                button.putClientProperty("SmartStock.ownedButtonBackground", fill);
                button.putClientProperty("SmartStock.ownedButtonForeground", ink);
                button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(DeckersPalette.border()), new EmptyBorder(6, 11, 6, 11)));
            }
        };
        for (int i = 0; i < buttons.length; i++) {
            final int index = i; buttons[i].addActionListener(e -> selection.setSelectedIndex(index)); panel.add(buttons[i]);
        }
        selection.addActionListener(e -> update.run());
        selection.addPropertyChangeListener("enabled", e -> update.run()); update.run();
        return panel;
    }
    static JPanel shell(JComponent body, int active, Runnable overview, Runnable create, Runnable custom, Runnable fulfillment) {
        JPanel shell = surface(new BorderLayout());
        JPanel header = surface(new BorderLayout(20, 0)); header.setBorder(new EmptyBorder(12, 22, 12, 22));
        ImageIcon icon = DeckersLogoManager.loadDeckersLogoIcon(DocumentStudio.class);
        JLabel brand = new JLabel();
        if (icon != null) brand.setIcon(new ImageIcon(DeckersLogoManager.scaleToFit(icon.getImage(), 94, 30)));
        else { brand.setText("Deckers"); brand.setForeground(DeckersPalette.ORANGE); brand.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 23)); }
        header.add(brand, BorderLayout.WEST);
        JLabel title = label("SmartStock — Document Studio"); header.add(title, BorderLayout.CENTER);
        JLabel hint = label("Sales documents"); hint.setForeground(DeckersPalette.muted()); header.add(hint, BorderLayout.EAST);
        JPanel nav = surface(new GridLayout(1, 4));
        String[] names = {"Quotations & invoices", "Create quotation", "Custom item details", "Invoice & fulfillment"};
        Runnable[] commands = {overview, create, custom, fulfillment};
        JButton[] navigation = new JButton[names.length];
        for (int i = 0; i < names.length; i++) {
            JButton link = new JButton(names[i]);
            button(link, false);
            link.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, i == active ? 3 : 1, 0,
                            i == active ? DeckersPalette.PURPLE : DeckersPalette.border()), new EmptyBorder(10, 8, 10, 8)));
            link.setBackground(DeckersPalette.surface());
            link.setForeground(i == active ? DeckersPalette.PURPLE : DeckersPalette.muted());
            link.putClientProperty("SmartStock.ownedButtonForeground", link.getForeground());
            Runnable action = commands[i]; link.setEnabled(i == active || action != null);
            navigation[i] = link;
            if (action != null) link.addActionListener(e -> action.run());
            nav.add(link);
        }
        JPanel top = surface(new BorderLayout()); top.add(header, BorderLayout.NORTH); top.add(nav, BorderLayout.SOUTH);
        shell.add(top, BorderLayout.NORTH); shell.add(body, BorderLayout.CENTER);
        shell.putClientProperty("DocumentStudio.navigation", navigation);
        finish(shell);
        return shell;
    }
    static void activate(JPanel shell, int active) {
        if (!(shell.getClientProperty("DocumentStudio.navigation") instanceof JButton[] navigation)) return;
        for (int i = 0; i < navigation.length; i++) {
            JButton link = navigation[i];
            link.setForeground(i == active ? DeckersPalette.PURPLE : DeckersPalette.muted());
            link.putClientProperty("SmartStock.ownedButtonForeground", link.getForeground());
            link.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, i == active ? 3 : 1, 0,
                    i == active ? DeckersPalette.PURPLE : DeckersPalette.border()), new EmptyBorder(10, 8, 10, 8)));
        }
    }
    static void button(JButton button, boolean primary) {
        button.setUI(new BasicButtonUI());
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        button.setOpaque(true); button.setContentAreaFilled(true); button.setFocusPainted(false);
        Color fill = primary ? DeckersPalette.ORANGE : DeckersPalette.surface();
        Color ink = primary ? new Color(15, 23, 42) : DeckersPalette.text();
        button.setBackground(fill); button.setForeground(ink);
        button.putClientProperty("SmartStock.ownedButtonBackground", fill);
        button.putClientProperty("SmartStock.ownedButtonForeground", ink);
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(primary ? fill : DeckersPalette.border()),
                new EmptyBorder(9, 15, 9, 15)));
    }
    static void table(JTable table) {
        DeckersSwing.styleTable(table, DeckersPalette.PURPLE);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.setRowHeight(38); table.setFillsViewportHeight(false);
        table.setShowGrid(false); table.setIntercellSpacing(new Dimension(0, 1));
        table.getTableHeader().setPreferredSize(new Dimension(0, 36));
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, false, row, column);
                String heading = String.valueOf(t.getColumnModel().getColumn(column).getHeaderValue()).toLowerCase(java.util.Locale.ROOT);
                if (value instanceof Number number && (heading.contains("rate") || heading.contains("amount") || heading.contains("total")
                        || heading.contains("balance") || heading.contains("price") || heading.contains("unit") || heading.contains("charge"))) {
                    setText(utils.CurrencyFormatter.format(number)); setHorizontalAlignment(SwingConstants.RIGHT);
                } else setHorizontalAlignment(SwingConstants.LEFT);
                setBorder(new EmptyBorder(5, 9, 5, 9));
                setBackground(selected ? DeckersPalette.tilePressed(DeckersPalette.PURPLE)
                        : row % 2 == 0 ? DeckersPalette.surface() : DeckersPalette.tableStripe());
                setForeground(DeckersPalette.text()); return this;
            }
        });
        for (int i = 0; i < table.getColumnCount(); i++) {
            String heading = String.valueOf(table.getColumnModel().getColumn(i).getHeaderValue());
            if ("Status".equals(heading) || "Payment".equals(heading)) table.getColumnModel().getColumn(i).setCellRenderer((t, value, selected, focus, row, column) -> {
                JPanel cell = surface(new FlowLayout(FlowLayout.LEFT, 8, 6));
                cell.setBackground(selected ? DeckersPalette.tilePressed(DeckersPalette.PURPLE) : DeckersPalette.surface());
                String text = value == null ? "" : value.toString().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
                if (!text.isEmpty()) text = Character.toUpperCase(text.charAt(0)) + text.substring(1);
                JLabel status = label(text); status.setFont(status.getFont().deriveFont(12f));
                status.setOpaque(true); status.setBackground(DeckersPalette.tilePressed(DeckersPalette.PURPLE));
                status.setForeground(DeckersPalette.PURPLE); status.setBorder(new EmptyBorder(3, 8, 3, 8));
                cell.add(status);
                cell.setSize(t.getColumnModel().getColumn(column).getWidth(), t.getRowHeight(row));
                cell.doLayout();
                return cell;
            });
        }
    }
    static void finish(Component root) {
        if (root instanceof JTextField field) {
            DeckersSwing.styleField(field);
            field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(DeckersPalette.border()), new EmptyBorder(7, 9, 7, 9)));
        }
        else if (root instanceof JTextArea area) {
            area.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14)); area.setLineWrap(true); area.setWrapStyleWord(true);
            area.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(DeckersPalette.border()), new EmptyBorder(7, 9, 7, 9)));
        } else if (root instanceof JComboBox<?> combo) {
            combo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            combo.setBackground(DeckersPalette.fieldBackground()); combo.setForeground(DeckersPalette.text());
            combo.setBorder(BorderFactory.createLineBorder(DeckersPalette.border()));
            combo.setPreferredSize(new Dimension(Math.min(340, combo.getPreferredSize().width), 36));
        }
        if (root instanceof Container container)
            for (Component child : container.getComponents()) finish(child);
    }
}
