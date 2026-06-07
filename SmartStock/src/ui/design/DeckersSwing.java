package ui.design;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.table.JTableHeader;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Insets;

public final class DeckersSwing {
    private DeckersSwing() {
    }

    public static JPanel panel() {
        JPanel panel = new JPanel();
        panel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        panel.setBackground(DeckersPalette.background());
        return panel;
    }

    public static void styleBand(JComponent component, Color accent, Insets padding) {
        component.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        component.setBackground(DeckersPalette.sectionFill(accent));
        component.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DeckersPalette.sectionBorder(accent), 1),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                        new EmptyBorder(padding)
                )
        ));
    }

    public static void styleField(JTextField field) {
        field.setFont(new Font("SansSerif", Font.PLAIN, 14));
        field.setForeground(DeckersPalette.text());
        field.setCaretColor(DeckersPalette.text());
        field.setBackground(DeckersPalette.fieldBackground());
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DeckersPalette.border()),
                BorderFactory.createEmptyBorder(2, 10, 2, 10)
        ));
    }

    public static JLabel metaLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 13));
        label.setForeground(DeckersPalette.text());
        label.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        return label;
    }

    public static JLabel totalLabel(String text, boolean prominent) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", prominent ? Font.BOLD : Font.PLAIN, prominent ? 18 : 14));
        label.setForeground(prominent ? DeckersPalette.text() : DeckersPalette.muted());
        label.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        return label;
    }

    public static void styleTable(JTable table) {
        styleTable(table, DeckersPalette.ORANGE);
    }

    public static void styleTable(JTable table, Color accent) {
        table.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        table.setFont(new Font("SansSerif", Font.PLAIN, 14));
        table.setRowHeight(34);
        table.setShowVerticalLines(false);
        table.setGridColor(DeckersPalette.sectionBorder(accent));
        table.setForeground(DeckersPalette.text());
        table.setBackground(DeckersPalette.tableBody(accent));
        table.setSelectionBackground(DeckersPalette.tilePressed(accent));
        table.setSelectionForeground(DeckersPalette.text());
        table.setOpaque(true);
        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("SansSerif", Font.BOLD, 13));
        header.setBackground(DeckersPalette.tableHeader(accent));
        header.setForeground(DeckersPalette.text());
    }

    public static void styleUtilityButton(JButton button, Color accent) {
        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setFocusPainted(false);
        button.setForeground(DeckersPalette.text());
        button.setBackground(DeckersPalette.sectionFill(accent));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DeckersPalette.sectionBorder(accent)),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                        BorderFactory.createEmptyBorder(8, 14, 8, 14)
                )
        ));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
}
