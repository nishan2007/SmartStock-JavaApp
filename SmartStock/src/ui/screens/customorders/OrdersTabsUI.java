package ui.screens.customorders;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import ui.design.DeckersPalette;

/** Flat order-content tabs with the shared orange selection indicator. */
final class OrdersTabsUI extends BasicTabbedPaneUI {
  @Override
  protected void paintContentBorder(Graphics g, int placement, int selected) {
    if (tabPane.getTabCount() == 0) return;
    Rectangle selectedTab = getTabBounds(tabPane, selected < 0 ? 0 : selected);
    int y = selectedTab.y + selectedTab.height;
    g.setColor(OrdersDesign.line());
    g.drawRect(0, y, tabPane.getWidth() - 1, Math.max(0, tabPane.getHeight() - y - 1));
  }

  @Override
  protected void installDefaults() {
    super.installDefaults();
    tabInsets = new Insets(10, 28, 10, 28);
    tabAreaInsets = new Insets(0, 0, 0, 0);
    contentBorderInsets = new Insets(1, 1, 1, 1);
    selectedTabPadInsets = new Insets(0, 0, 0, 0);
  }

  @Override
  protected void paintTabBackground(
      Graphics g, int placement, int index, int x, int y, int w, int h, boolean selected) {
    g.setColor(selected ? DeckersPalette.surface() : OrdersDesign.background());
    g.fillRect(x, y, w, h);
  }

  @Override
  protected void paintTabBorder(
      Graphics g, int placement, int index, int x, int y, int w, int h, boolean selected) {
    g.setColor(OrdersDesign.line());
    g.drawRect(x, y, w, h);
    if (selected) {
      g.setColor(DeckersPalette.ORANGE);
      g.fillRect(x, y + h - 2, w, 3);
    }
  }

  @Override
  protected void paintFocusIndicator(
      Graphics g,
      int placement,
      Rectangle[] rectangles,
      int index,
      Rectangle icon,
      Rectangle text,
      boolean selected) {
    if (tabPane.hasFocus() && selected) {
      g.setColor(OrdersDesign.ink());
      g.drawRect(text.x - 2, text.y - 2, text.width + 4, text.height + 4);
    }
  }
}
