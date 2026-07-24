package ui.components;

import ui.design.DeckersPalette;

import javax.swing.BorderFactory;
import javax.swing.JTree;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Component;

/** Text-only preference navigation that remains themed after Aqua UI refreshes. */
public final class PreferenceTreeCellRenderer extends DefaultTreeCellRenderer {
    public PreferenceTreeCellRenderer() {
        configure();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        configure();
    }

    @Override
    public Component getTreeCellRendererComponent(
            JTree tree,
            Object value,
            boolean selected,
            boolean expanded,
            boolean leaf,
            int row,
            boolean hasFocus
    ) {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        setLeafIcon(null);
        setOpenIcon(null);
        setClosedIcon(null);
        setIcon(null);
        setForeground(DeckersPalette.text());
        setTextSelectionColor(DeckersPalette.text());
        setTextNonSelectionColor(DeckersPalette.text());
        setBackgroundSelectionColor(DeckersPalette.tilePressed(DeckersPalette.ORANGE));
        setBackgroundNonSelectionColor(tree.getBackground());
        setBackground(selected
                ? getBackgroundSelectionColor()
                : getBackgroundNonSelectionColor());
        // DefaultTreeCellRenderer paints with its own selection/non-selection
        // color properties rather than relying only on JLabel opacity. Keep it
        // opaque with colors resolved from the live tree on every paint.
        setOpaque(true);
        setBorder(selected
                ? BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 4, 0, 0, DeckersPalette.ORANGE),
                        new EmptyBorder(3, 7, 3, 7)
                )
                : new EmptyBorder(3, 11, 3, 7));
        return this;
    }

    private void configure() {
        setLeafIcon(null);
        setOpenIcon(null);
        setClosedIcon(null);
        setIcon(null);
        setOpaque(false);
        setBorder(new EmptyBorder(3, 7, 3, 7));
    }
}
