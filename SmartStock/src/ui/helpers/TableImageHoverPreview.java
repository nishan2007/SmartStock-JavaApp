package ui.helpers;

import ui.design.DeckersPalette;
import utils.ImageCacheManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/** Delayed, non-focusable product-image preview shared by catalog result tables. */
public final class TableImageHoverPreview {
    private TableImageHoverPreview() { }

    public static void install(Window owner, JTable table, int imageModelColumn, Color accent) {
        Preview preview = new Preview(owner, table, imageModelColumn, accent);
        table.addMouseMotionListener(preview);
        table.addMouseListener(preview);
    }

    private static final class Preview extends MouseAdapter implements MouseMotionListener {
        private final JTable table;
        private final int imageColumn;
        private final JWindow window;
        private final JLabel label;
        private Timer timer;
        private SwingWorker<ImageIcon, Void> worker;
        private int row = -1;
        private long generation;

        private Preview(Window owner, JTable table, int imageColumn, Color accent) {
            this.table = table;
            this.imageColumn = imageColumn;
            window = new JWindow(owner);
            window.setFocusableWindowState(false);
            label = new JLabel("", SwingConstants.CENTER);
            label.setPreferredSize(new Dimension(210, 210));
            label.setOpaque(true);
            label.setBackground(DeckersPalette.surface());
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(DeckersPalette.sectionBorder(accent)),
                    BorderFactory.createEmptyBorder(5, 5, 5, 5)));
            window.setContentPane(label);
        }

        @Override public void mouseMoved(MouseEvent event) {
            int next = table.rowAtPoint(event.getPoint());
            if (next == row) return;
            hide();
            if (next < 0) return;
            row = next;
            long expected = generation;
            Point anchor = event.getLocationOnScreen();
            timer = new Timer(300, ignored -> load(next, anchor, expected));
            timer.setRepeats(false);
            timer.start();
        }

        @Override public void mouseExited(MouseEvent event) { hide(); }

        private void load(int viewRow, Point anchor, long expected) {
            if (!current(viewRow, expected)) return;
            int modelRow = table.convertRowIndexToModel(viewRow);
            Object value = table.getModel().getValueAt(modelRow, imageColumn);
            String url = value == null ? "" : String.valueOf(value).trim();
            if (url.isBlank()) { show(null, "No Image", anchor, viewRow, expected); return; }
            worker = new SwingWorker<>() {
                @Override protected ImageIcon doInBackground() {
                    Image image = ImageCacheManager.loadImage(url);
                    if (image == null || image.getWidth(null) <= 0 || image.getHeight(null) <= 0) return null;
                    double scale = Math.min(200d / image.getWidth(null), 200d / image.getHeight(null));
                    int width = Math.max(1, (int)Math.round(image.getWidth(null) * scale));
                    int height = Math.max(1, (int)Math.round(image.getHeight(null) * scale));
                    return new ImageIcon(image.getScaledInstance(width, height, Image.SCALE_SMOOTH));
                }
                @Override protected void done() {
                    if (!current(viewRow, expected)) return;
                    ImageIcon icon = null;
                    try { if (!isCancelled()) icon = get(); } catch (Exception ignored) { }
                    show(icon, icon == null ? "Image unavailable" : "", anchor, viewRow, expected);
                }
            };
            worker.execute();
        }

        private boolean current(int viewRow, long expected) {
            return generation == expected && row == viewRow && table.isShowing();
        }

        private void show(ImageIcon icon, String text, Point anchor, int viewRow, long expected) {
            if (!current(viewRow, expected)) return;
            label.setIcon(icon); label.setText(icon == null ? text : "");
            label.setForeground(DeckersPalette.muted()); window.pack();
            Rectangle screen = table.getGraphicsConfiguration().getBounds();
            int x = anchor.x + 18;
            if (x + window.getWidth() > screen.x + screen.width) x = anchor.x - window.getWidth() - 18;
            int y = Math.max(screen.y, Math.min(anchor.y - window.getHeight() / 2,
                    screen.y + screen.height - window.getHeight()));
            window.setLocation(Math.max(screen.x, x), y); window.setVisible(true);
        }

        private void hide() {
            generation++; row = -1;
            if (timer != null) timer.stop();
            if (worker != null) worker.cancel(true);
            window.setVisible(false);
        }
    }
}
