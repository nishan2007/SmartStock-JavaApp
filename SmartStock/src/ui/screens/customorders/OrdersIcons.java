package ui.screens.customorders;

import java.awt.*;
import javax.swing.*;

/** The same licensed Font Awesome asset used by the existing sales-task screen. */
final class OrdersIcons {
  private static final Font FONT = load();

  private static Font load() {
    try (var in = OrdersIcons.class.getResourceAsStream("/Images/sales-task-icons.ttf")) {
      return in == null ? null : Font.createFont(Font.TRUETYPE_FONT, in);
    } catch (Exception e) {
      return null;
    }
  }

  static Icon icon(String name, Color color, int size) {
    if (FONT == null) return null;
    int code =
        switch (name) {
          case "step" -> 0xf111;
          case "step-current" -> 0xf058;
          case "pdf" -> 0xf1c1;
          case "plus" -> 0xf055;
          case "search" -> 0xf002;
          case "production" -> 0xf013;
          case "payment" -> 0xf09d;
          case "delivery" -> 0xf0d1;
          case "file" -> 0xf15c;
          case "print" -> 0xf02f;
          case "more" -> 0xf141;
          case "clip" -> 0xf0c6;
          case "pin" -> 0xf3c5;
          case "person" -> 0xf007;
          case "calendar" -> 0xf073;
          case "check" -> 0xf00c;
          default -> 0xf105;
        };
    return new Icon() {
      public int getIconWidth() {
        return size;
      }

      public int getIconHeight() {
        return size;
      }

      public void paintIcon(Component c, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(FONT.deriveFont((float) size));
        g.setColor(c.isEnabled() ? color : OrdersDesign.muted());
        FontMetrics m = g.getFontMetrics();
        String glyph = String.valueOf((char) code);
        g.drawString(
            glyph,
            x + (size - m.stringWidth(glyph)) / 2,
            y + (size - m.getHeight()) / 2 + m.getAscent());
        g.dispose();
      }
    };
  }

  static void install(AbstractButton button, String name, boolean primary) {
    button.setIcon(icon(name, primary ? Color.WHITE : OrdersDesign.ink(), 19));
    button.setIconTextGap(9);
  }
}
