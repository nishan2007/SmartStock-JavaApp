package ui.components;

import utils.ImageCacheManager;
import ui.helpers.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/** Full-resolution product gallery with cursor-centered zoom and drag panning. */
public final class ProductImageViewer extends JDialog {
    private final List<String> photos;
    private final ImageCanvas canvas = new ImageCanvas();
    private final JLabel status = new JLabel();
    private int selected;
    private SwingWorker<Image, Void> loader;

    public ProductImageViewer(Window owner, String title, List<String> photos, int selected) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.photos = List.copyOf(photos);
        this.selected = selected;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER));
        if (photos.size() > 1) {
            button(controls, "Previous", () -> select(-1));
            button(controls, "Next", () -> select(1));
        }
        button(controls, "−", () -> canvas.zoom(1 / 1.25, canvas.center()));
        button(controls, "+", () -> canvas.zoom(1.25, canvas.center()));
        button(controls, "Fit", canvas::fit);
        button(controls, "100%", () -> canvas.zoomTo(1, canvas.center()));
        controls.add(status);
        button(controls, "Close", this::dispose);
        add(controls, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        JLabel help = new JLabel("Scroll to zoom • Drag to move • Double-click to fit • Esc to close", SwingConstants.CENTER);
        help.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(help, BorderLayout.SOUTH);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        Rectangle bounds = owner.getGraphicsConfiguration().getBounds();
        setSize(Math.min(1100, bounds.width - 80), Math.min(800, bounds.height - 80));
        setLocationRelativeTo(owner);
        load();
    }

    private void button(JPanel panel, String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(e -> action.run());
        panel.add(button);
    }

    private void select(int direction) {
        selected = Math.floorMod(selected + direction, photos.size());
        load();
    }

    private void load() {
        if (loader != null) loader.cancel(true);
        canvas.image = null;
        canvas.message = "Loading image…";
        canvas.repaint();
        status.setText((selected + 1) + " / " + photos.size());
        String url = photos.get(selected);
        loader = new SwingWorker<>() {
            protected Image doInBackground() throws Exception { return ImageCacheManager.loadImage(url); }
            protected void done() {
                if (isCancelled()) return;
                try {
                    canvas.image = get();
                } catch (Exception ex) {
                    canvas.image = null;
                }
                canvas.message = "Image unavailable";
                if (canvas.image != null && canvas.image.getWidth(null) > 0) canvas.fit();
                else { canvas.image = null; canvas.repaint(); }
            }
        };
        loader.execute();
    }

    @Override public void dispose() {
        if (loader != null) loader.cancel(true);
        super.dispose();
    }

    private static final class ImageCanvas extends JPanel {
        private Image image;
        private String message = "Loading image…";
        private double scale = 1, x, y;
        private Point drag;

        ImageCanvas() {
            setBackground(ThemeManager.isDarkModeEnabled() ? new Color(30, 30, 30) : new Color(248, 250, 252));
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            MouseAdapter mouse = new MouseAdapter() {
                public void mousePressed(MouseEvent e) { if (SwingUtilities.isLeftMouseButton(e)) drag = e.getPoint(); }
                public void mouseReleased(MouseEvent e) { drag = null; }
                public void mouseDragged(MouseEvent e) {
                    if (drag == null || image == null) return;
                    x += e.getX() - drag.x;
                    y += e.getY() - drag.y;
                    drag = e.getPoint();
                    repaint();
                }
                public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) fit(); }
                public void mouseWheelMoved(MouseWheelEvent e) { zoom(Math.pow(1.15, -e.getPreciseWheelRotation()), e.getPoint()); }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);
            addComponentListener(new ComponentAdapter() {
                public void componentResized(ComponentEvent e) { fit(); }
            });
        }

        Point center() { return new Point(getWidth() / 2, getHeight() / 2); }
        void fit() {
            if (image == null || getWidth() <= 0 || getHeight() <= 0) return;
            scale = Math.min((double) getWidth() / image.getWidth(null), (double) getHeight() / image.getHeight(null));
            x = (getWidth() - image.getWidth(null) * scale) / 2;
            y = (getHeight() - image.getHeight(null) * scale) / 2;
            repaint();
        }
        void zoom(double factor, Point anchor) { zoomTo(scale * factor, anchor); }
        void zoomTo(double target, Point anchor) {
            if (image == null) return;
            double next = Math.max(0.01, Math.min(16, target));
            x = anchor.x - (anchor.x - x) * next / scale;
            y = anchor.y - (anchor.y - y) * next / scale;
            scale = next;
            repaint();
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D graphics = (Graphics2D) g.create();
            try {
                if (image == null) {
                    graphics.setColor(getForeground());
                    graphics.drawString(message, Math.max(8, (getWidth() - graphics.getFontMetrics().stringWidth(message)) / 2), getHeight() / 2);
                } else {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.translate(x, y);
                    graphics.scale(scale, scale);
                    graphics.drawImage(image, 0, 0, null);
                }
            } finally { graphics.dispose(); }
        }
    }
}
