package ui.components;

import java.awt.*;

/** A FlowLayout that reserves the height of all wrapped rows. */
public final class WrappingToolbarLayout extends FlowLayout {
    public WrappingToolbarLayout() { super(FlowLayout.LEFT, 8, 6); }
    @Override public Dimension preferredLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            int width = target.getWidth();
            Container parent = target.getParent();
            while (width <= 0 && parent != null) { width = parent.getWidth(); parent = parent.getParent(); }
            if (width <= 0) width = 600;
            Insets in = target.getInsets();
            int available = Math.max(1, width - in.left - in.right - getHgap() * 2);
            int rowWidth = 0, rowHeight = 0, height = getVgap() * 2;
            for (Component child : target.getComponents()) if (child.isVisible()) {
                Dimension d = child.getPreferredSize();
                if (rowWidth > 0 && rowWidth + getHgap() + d.width > available) {
                    height += rowHeight + getVgap(); rowWidth = 0; rowHeight = 0;
                }
                rowWidth += (rowWidth > 0 ? getHgap() : 0) + d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            return new Dimension(width, height + rowHeight + in.top + in.bottom);
        }
    }
    @Override public Dimension minimumLayoutSize(Container target) { return new Dimension(0, preferredLayoutSize(target).height); }
}
