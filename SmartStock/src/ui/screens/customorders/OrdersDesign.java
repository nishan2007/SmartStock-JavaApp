package ui.screens.customorders;

import java.awt.*;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import ui.design.DeckersLogoManager;
import ui.design.DeckersPalette;
import ui.helpers.StoreTimeZoneHelper;

/** Shared native Swing treatments for the custom-order workspace. */
final class OrdersDesign {
  private OrdersDesign() {}

  static Color ink() {
    return DeckersPalette.dark() ? DeckersPalette.text() : new Color(13, 25, 76);
  }

  static Color muted() {
    return DeckersPalette.dark() ? DeckersPalette.muted() : new Color(65, 87, 130);
  }

  static Color line() {
    return DeckersPalette.dark() ? DeckersPalette.border() : new Color(209, 225, 242);
  }

  static Color background() {
    return DeckersPalette.dark() ? DeckersPalette.background() : new Color(244, 249, 253);
  }

  static Color selection() {
    return DeckersPalette.dark() ? new Color(64, 43, 30) : new Color(255, 244, 237);
  }

  private static final Font ORDER_FONT = loadFont();

  private static Font loadFont() {
    try (var in = OrdersDesign.class.getResourceAsStream("/Images/orders-roboto-condensed.ttf")) {
      if (in == null) return new Font("SansSerif", Font.PLAIN, 16);
      Font font = Font.createFont(Font.TRUETYPE_FONT, in);
      GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
      return font;
    } catch (Exception e) {
      return new Font("SansSerif", Font.PLAIN, 16);
    }
  }

  static Font font(int style, int size) {
    return ORDER_FONT.deriveFont(style, (float) size);
  }

  static JLabel label(String text, int size, boolean bold) {
    JLabel l = new JLabel(text);
    l.setFont(font(bold ? Font.BOLD : Font.PLAIN, size));
    l.setForeground(ink());
    return l;
  }

  static JPanel panel(LayoutManager layout) {
    JPanel p = new JPanel(layout);
    p.setOpaque(false);
    p.putClientProperty("SmartStock.preserveThemeColors", true);
    return p;
  }

  static JPanel card(LayoutManager layout) {
    JPanel p = new JPanel(layout);
    p.setBackground(DeckersPalette.surface());
    p.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(line()), new EmptyBorder(10, 14, 10, 14)));
    return p;
  }

  static JButton button(String text, boolean primary) {
    JButton b = new JButton(text);
    style(b, primary);
    return b;
  }

  static void style(JButton b, boolean primary) {
    b.setUI(new javax.swing.plaf.basic.BasicButtonUI());
    b.putClientProperty("SmartStock.preserveThemeColors", true);
    b.putClientProperty("SmartStock.customPaintedButton", true);
    b.setFont(font(primary ? Font.BOLD : Font.PLAIN, 15));
    b.setBackground(primary ? DeckersPalette.ORANGE : DeckersPalette.surface());
    b.setForeground(primary ? Color.WHITE : ink());
    b.setOpaque(true);
    b.setFocusPainted(true);
    b.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(primary ? DeckersPalette.ORANGE : line()),
            new EmptyBorder(11, 12, 11, 12)));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  }

  static void field(JComponent c) {
    c.putClientProperty("SmartStock.preserveThemeColors", true);
    c.setFont(font(Font.PLAIN, 16));
    c.setForeground(ink());
    c.setBackground(DeckersPalette.fieldBackground());
    if (c instanceof javax.swing.text.JTextComponent text) text.setCaretColor(ink());
    c.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(line()), new EmptyBorder(7, 10, 7, 10)));
  }

  static void table(JTable table) {
    table.setFont(font(Font.PLAIN, 16));
    table.setForeground(ink());
    table.setBackground(DeckersPalette.surface());
    table.setRowHeight(37);
    table.setGridColor(line());
    table.setShowGrid(true);
    table.setIntercellSpacing(new Dimension(1, 1));
    table.getTableHeader().setFont(font(Font.PLAIN, 16));
    table.getTableHeader().setForeground(ink());
    table.getTableHeader().setBackground(background());
    table.getTableHeader().setPreferredSize(new Dimension(0, 36));
    table
        .getTableHeader()
        .setDefaultRenderer(
            (t, value, selected, focus, row, column) -> {
              JLabel header = label(java.util.Objects.toString(value, ""), 16, false);
              header.setHorizontalAlignment(
                  column == 1 ? SwingConstants.LEFT : SwingConstants.CENTER);
              header.setOpaque(true);
              header.setBackground(DeckersPalette.dark() ? background() : new Color(230, 240, 248));
              header.setBorder(
                  BorderFactory.createCompoundBorder(
                      BorderFactory.createMatteBorder(0, 0, 1, 1, line()),
                      new EmptyBorder(6, 10, 6, 6)));
              return header;
            });
    table.setSelectionBackground(selection());
    table.setSelectionForeground(ink());
  }

  static String html(String s) {
    return java.util.Objects.toString(s, "")
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  static String human(String s) {
    if (s != null && services.CustomOrderProductionStages.CODES.contains(s)) return services.CustomOrderProductionStages.label(s);
    if ("WAITING_FOR_APPROVAL".equals(s)) return "Waiting approval";
    if (s == null || s.isBlank()) return "Not set";
    String v = s.toLowerCase(Locale.ROOT).replace('_', ' ');
    return Character.toUpperCase(v.charAt(0)) + v.substring(1);
  }

  static String money(Object value) {
    try {
      return "G$" + new DecimalFormat("#,##0").format(new java.math.BigDecimal(value.toString()));
    } catch (Exception e) {
      return java.util.Objects.toString(value, "");
    }
  }

  static JLabel badge(String value) {
    JLabel b = label(human(value), 15, true);
    b.setOpaque(true);
    String v = java.util.Objects.toString(value, "");

    boolean ready = v.equals("READY") || v.equals("DELIVERED") || v.equals("PAID");
    boolean warning = v.contains("WAIT") || v.contains("PART") || v.contains("APPROV");
    b.setBackground(
        DeckersPalette.dark()
            ? DeckersPalette.background()
            : ready
                ? new Color(211, 242, 219)
                : warning ? new Color(255, 235, 204) : new Color(214, 230, 252));
    b.setForeground(
        DeckersPalette.dark()
            ? (ready
                ? new Color(121, 224, 147)
                : warning ? new Color(255, 190, 116) : new Color(151, 188, 255))
            : ready
                ? new Color(0, 111, 30)
                : warning ? new Color(156, 64, 0) : new Color(20, 64, 164));
    b.setBorder(new EmptyBorder(5, 10, 5, 10));
    return b;
  }

  static JPanel header(String store, String staff, LocalDate date) {
    JPanel root = panel(new BorderLayout(28, 0));
    root.setOpaque(true);
    root.setBackground(background());
    root.setPreferredSize(new Dimension(1000, 80));
    root.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, line()), new EmptyBorder(8, 28, 8, 24)));
    ImageIcon image = DeckersLogoManager.loadDeckersLogoIcon(OrdersDesign.class);
    JLabel logo =
        new JLabel(
            image == null
                ? null
                : new ImageIcon(DeckersLogoManager.scaleToFit(image.getImage(), 190, 64)));
    logo.setPreferredSize(new Dimension(190, 64));
    JPanel brand = panel(new BorderLayout(26, 0));
    brand.add(logo, BorderLayout.WEST);
    JPanel titles = panel(new GridLayout(2, 1));
    titles.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, line()), new EmptyBorder(2, 28, 2, 0)));
    JLabel appName = label("SmartStock", 28, true);
    appName.setFont(new Font("Segoe UI", Font.BOLD, 28));
    titles.add(appName);
    JLabel subtitle = label("Custom Orders", 17, false);
    subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 17));
    titles.add(subtitle);
    brand.add(titles);
    root.add(brand);
    JPanel meta = panel(new GridLayout(1, 3, 24, 0));
    meta.add(meta("Store", store));
    meta.add(meta("Staff", staff));
    meta.add(
        meta(
            date.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)),
            date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))));
    root.add(meta, BorderLayout.EAST);
    return root;
  }

  private static JPanel meta(String title, String value) {
    JPanel p = panel(new BorderLayout(12, 0));
    p.setBorder(new EmptyBorder(4, 14, 4, 14));
    p.add(
        new JLabel(
            OrdersIcons.icon(
                title.equals("Store") ? "pin" : title.equals("Staff") ? "person" : "calendar",
                ink(),
                21)),
        BorderLayout.WEST);
    JPanel text = panel(new GridLayout(2, 1));
    text.add(label(title, 15, false));
    text.add(label(java.util.Objects.toString(value, "—"), 18, true));
    p.add(text);
    return p;
  }

  static final class SearchField extends JTextField {
    @Override
    public Insets getInsets() {
      Insets i = super.getInsets();
      return new Insets(i.top, i.left + 24, i.bottom, i.right);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Icon icon = OrdersIcons.icon("search", muted(), 17);
      if (icon != null) icon.paintIcon(this, g, 12, (getHeight() - 17) / 2);
      if (getText().isEmpty() && !hasFocus()) {
        Graphics2D copy = (Graphics2D) g.create();
        copy.setColor(muted());
        copy.setFont(font(Font.PLAIN, 14));
        copy.drawString(
            "Search order #, customer or phone…",
            getInsets().left,
            (getHeight() - copy.getFontMetrics().getHeight()) / 2
                + copy.getFontMetrics().getAscent());
        copy.dispose();
      }
    }
  }

  static final class MoneyField extends JTextField {
    MoneyField(int columns) {
      super(columns);
    }

    @Override
    public Insets getInsets() {
      Insets i = super.getInsets();
      return new Insets(i.top, i.left + 19, i.bottom, i.right);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      Graphics2D g = (Graphics2D) graphics.create();
      g.setFont(getFont());
      g.setColor(getForeground());
      g.drawString(
          "G$",
          10,
          (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent());
      g.dispose();
    }
  }

  static final class ListCell extends DefaultTableCellRenderer {
    private final javax.swing.table.DefaultTableModel model;

    ListCell(javax.swing.table.DefaultTableModel model) {
      this.model = model;
    }

    @Override
    public Component getTableCellRendererComponent(
        JTable t, Object value, boolean selected, boolean focus, int row, int column) {
      super.getTableCellRendererComponent(t, value, selected, focus, row, column);
      int r = t.convertRowIndexToModel(row), c = t.convertColumnIndexToModel(column);
      String name = model.getColumnName(c), text = java.util.Objects.toString(value, "");

      setToolTipText(text);
      setHorizontalAlignment(LEFT);
      setFont(font(Font.PLAIN, 16));
      setForeground(ink());
      setBackground(selected ? selection() : DeckersPalette.surface());
      setBorder(
          BorderFactory.createCompoundBorder(
              BorderFactory.createMatteBorder(
                  0,
                  selected && column == 0 ? 4 : 0,
                  1,
                  0,
                  selected ? DeckersPalette.ORANGE : line()),
              new EmptyBorder(10, 12, 10, 8)));
      if (name.equals("Order #") || name.equals("Customer")) {
        JPanel cell = panel(new GridLayout(2, 1, 0, 2));
        cell.setOpaque(true);
        cell.setBackground(getBackground());
        cell.setBorder(getBorder());
        int size = name.equals("Order #") ? 18 : 16;
        int available = t.getColumnModel().getColumn(column).getWidth() - 24;
        while (size > 14 && t.getFontMetrics(font(Font.BOLD, size)).stringWidth(text) > available)
          size--;
        JLabel first = label(text, size, true);
        String secondary = at(r, name.equals("Order #") ? "Assigned To" : "Phone");
        JLabel second =
            label(
                secondary.isBlank() && name.equals("Order #") ? "Unassigned" : secondary,
                16,
                false);
        second.setForeground(secondary.isBlank() ? DeckersPalette.ORANGE : muted());
        first.setToolTipText(text);
        second.setToolTipText(second.getText());
        cell.add(first);
        cell.add(second);
        return cell;
      } else if (name.equals("Balance")) {
        text = "<b>" + html(money(value)) + "</b>";
        setHorizontalAlignment(RIGHT);
      } else if (name.equals("Status")) {
        text = CustomOrderListColumns.displayStatus(text, at(r, "Production"));
        JPanel p = panel(new GridBagLayout());
        p.setOpaque(true);
        p.setBackground(getBackground());
        JLabel state = badge(text.equals("NEW") ? "NOT_STARTED" : text);
        state.setFont(font(Font.BOLD, 14));
        p.add(state);
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, line()));
        return p;
      } else if (name.equals("Due")) {
        text = html(CustomOrderListColumns.formatDate(value, StoreTimeZoneHelper.getStoreZone()));
        if (value instanceof LocalDate date) {
          LocalDate today = LocalDate.now(StoreTimeZoneHelper.getStoreZone());
          if (date.equals(today)) text = "<b><font color='#ff4c00'>Today</font></b><br>" + text;
          else if (date.equals(today.plusDays(1))) text = "Tomorrow<br>" + text;
        }
      }
      setText("<html>" + text + "</html>");
      return this;
    }

    private String at(int row, String column) {
      int c = model.findColumn(column);
      return c < 0 ? "" : java.util.Objects.toString(model.getValueAt(row, c), "");
    }
  }
}
