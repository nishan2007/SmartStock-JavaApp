package ui.components;

import ui.design.DeckersPalette;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/** Keep usable form widths; on smaller displays give each side its own view. */
public final class DocumentWorkspace extends JPanel {
    private final JComponent editor, preview;
    private final JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    private final JTabbedPane views = new JTabbedPane();
    private boolean narrow;
    private boolean initialized;
    private final double ratio;

    public DocumentWorkspace(JComponent editor, JComponent preview) {
        this(editor, preview, 0.52);
    }
    public DocumentWorkspace(JComponent editor, JComponent preview, double ratio) {
        super(new BorderLayout());
        this.ratio = ratio;
        this.editor = editor; this.preview = preview;
        applyReadableTypography(editor);
        applyReadableTypography(preview);
        editor.setMinimumSize(new Dimension(420, 160));
        preview.setMinimumSize(new Dimension(320, 160));
        split.setResizeWeight(ratio);
        split.setContinuousLayout(true);
        split.setBorder(null);
        split.setDividerSize(5);
        split.setBackground(DeckersPalette.border());
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { arrange(); }
        });
        arrange();
    }

    public static void applyReadableTypography(Component component) {
        Font current = component.getFont();
        if (current != null && !(component instanceof JEditorPane))
            component.setFont(new Font(Font.SANS_SERIF, current.getStyle(), Math.max(14, current.getSize())));
        if (component instanceof JTable table) {
            table.setRowHeight(Math.max(32, table.getRowHeight()));
            table.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        }
        if (component instanceof Container container)
            for (Component child : container.getComponents()) applyReadableTypography(child);
    }

    private void arrange() {
        boolean next = getWidth() > 0 && getWidth() < 1120;
        if (initialized && next == narrow) return;
        initialized = true; narrow = next;
        removeAll();
        split.setLeftComponent(null); split.setRightComponent(null); views.removeAll();
        if (narrow) {
            views.addTab("Editor / Details", editor); views.addTab("Document preview", preview);
            add(views, BorderLayout.CENTER);
        } else {
            split.setLeftComponent(editor); split.setRightComponent(preview);
            add(split, BorderLayout.CENTER);
            SwingUtilities.invokeLater(() -> split.setDividerLocation(ratio));
        }
        revalidate(); repaint();
    }

    public static void fitDialog(JDialog dialog, int desiredWidth, int desiredHeight) {
        GraphicsConfiguration configuration = dialog.getGraphicsConfiguration();
        Rectangle bounds = configuration.getBounds();
        Insets in = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        int width = Math.min(desiredWidth, bounds.width - in.left - in.right - 24);
        int height = Math.min(desiredHeight, bounds.height - in.top - in.bottom - 24);
        dialog.setSize(Math.max(320, width), Math.max(240, height));
        dialog.setLocationRelativeTo(dialog.getOwner());
    }
}
