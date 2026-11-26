package ui.screens.customorders;

import static ui.screens.customorders.OrdersDesign.*;

import java.awt.*;
import java.util.List;
import javax.swing.*;
import ui.design.DeckersPalette;

/** Whole-order stage selector; changes use the existing validated production workflow. */
final class OrdersProductionSteps extends JPanel {
  private final int current;
  private static final List<String> VALUES = services.CustomOrderProductionStages.CODES;

  OrdersProductionSteps(String status) {
    this(status, null, false);
  }

  OrdersProductionSteps(String status, java.util.function.Consumer<String> update, boolean enabled) {
    super(new GridLayout(1, 8, 0, 0));
    setOpaque(false);
    current = VALUES.indexOf(status);
    String[] names = {
      "Not<br>started",
      "In<br>progress",
      "Waiting<br>approval",
      "Design<br>approved",
      "Printed",
      "Finished",
      "Quality<br>checked",
      "Ready"
    };
    for (int i = 0; i < 8; i++) {
      JButton cell =
          new JButton("<html><center>" + names[i] + "</center></html>",
              OrdersIcons.icon(
                  i == current ? "step-current" : "step",
                  i <= current ? DeckersPalette.ORANGE : line(),
                  16));
      cell.setFont(font(i == current ? Font.BOLD : Font.PLAIN, 11));
      cell.setForeground(ink());
      cell.setOpaque(false);
      cell.setContentAreaFilled(false);
      cell.setDisabledIcon(cell.getIcon());
      cell.setBorder(BorderFactory.createEmptyBorder());
      cell.setHorizontalTextPosition(SwingConstants.CENTER);
      cell.setVerticalTextPosition(SwingConstants.BOTTOM);
      cell.setVerticalAlignment(SwingConstants.TOP);
      cell.setIconTextGap(5);
      String value = VALUES.get(i);
      cell.setActionCommand(value);
      cell.setEnabled(enabled && update != null);
      cell.setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
      cell.setToolTipText("Set whole order to " + human(value));
      cell.getAccessibleContext().setAccessibleName("Set whole order production to " + human(value));
      cell.addActionListener(e -> { if (update != null) update.accept(value); });
      add(cell);
    }
    setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    int start = getWidth() / 16, end = getWidth() - start, y = 16;
    g.setColor(line());
    g.fillRect(start, y, end - start, 2);
    if (current >= 0) {
      g.setColor(DeckersPalette.ORANGE);
      g.fillRect(start, y, Math.max(0, getWidth() * (2 * current + 1) / 16 - start), 2);
    }
  }
}
