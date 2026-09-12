package ui.helpers;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** App-wide Windows finger scrolling. A tap is delivered only after it is distinguished from a swipe. */
public final class TouchScrollSupport {
    private static boolean installed;
    private TouchScrollSupport() { }

    public static synchronized void install() {
        if (installed || GraphicsEnvironment.isHeadless()
                || !System.getProperty("os.name", "").startsWith("Windows")) return;
        installed = true;
        TouchSearchKeyboard.Detector detector = new TouchSearchKeyboard.Detector();
        Thread worker = new Thread(detector, "smartstock-touch-scroll");
        worker.setDaemon(true);
        worker.start();
        Toolkit.getDefaultToolkit().getSystemEventQueue().push(new EventQueue() {
            private final Gesture gesture = new Gesture(detector::consumeTouch);
            @Override protected void dispatchEvent(AWTEvent event) {
                if (event instanceof WindowEvent window
                        && (window.getID() == WindowEvent.WINDOW_DEACTIVATED
                        || window.getID() == WindowEvent.WINDOW_CLOSED)) gesture.cancel();
                if (event instanceof MouseEvent mouse && gesture.handle(mouse, super::dispatchEvent)) return;
                super.dispatchEvent(event);
            }
        });
    }

    static final class Gesture {
        private final BooleanSupplier touchPress;
        private MouseEvent press;
        private JViewport viewport;
        private Point origin;
        private boolean dragging;
        private Component suppressedClick;

        Gesture(BooleanSupplier touchPress) { this.touchPress = touchPress; }

        boolean handle(MouseEvent event, Consumer<AWTEvent> dispatch) {
            if (event.getID() == MouseEvent.MOUSE_PRESSED) {
                cancel();
                boolean touch = touchPress.getAsBoolean();
                if (!touch || !SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger()
                        || event.isControlDown() || event.isShiftDown() || event.isAltDown() || event.isMetaDown()) return false;
                viewport = findViewport(event.getComponent());
                if (viewport == null) return false;
                press = event;
                origin = viewport.getViewPosition();
                return true;
            }
            if (event.getID() == MouseEvent.MOUSE_CLICKED && event.getComponent() == suppressedClick) {
                suppressedClick = null;
                return true;
            }
            if (press == null) return false;
            if (event.getID() == MouseEvent.MOUSE_DRAGGED) {
                int dx = event.getXOnScreen() - press.getXOnScreen();
                int dy = event.getYOnScreen() - press.getYOnScreen();
                if (!dragging && Math.hypot(dx, dy) < 10) return true;
                dragging = true;
                Dimension size = viewport.getViewSize(), extent = viewport.getExtentSize();
                viewport.setViewPosition(new Point(
                        clamp(origin.x - dx, size.width - extent.width),
                        clamp(origin.y - dy, size.height - extent.height)));
                return true;
            }
            if (event.getID() == MouseEvent.MOUSE_RELEASED) {
                MouseEvent pending = press;
                boolean scrolled = dragging;
                cancel();
                if (scrolled) {
                    suppressedClick = event.getComponent();
                    return true;
                }
                dispatch.accept(pending);
                return false;
            }
            return false;
        }

        void cancel() {
            press = null;
            viewport = null;
            origin = null;
            dragging = false;
            suppressedClick = null;
        }
    }

    private static int clamp(int value, int maximum) { return Math.max(0, Math.min(value, Math.max(0, maximum))); }

    static JViewport findViewport(Component source) {
        for (Component current = source; current != null; current = current.getParent()) {
            if ((current instanceof JTextComponent text && text.isEditable()) || current instanceof JScrollBar
                    || current instanceof JSlider || current instanceof JComboBox<?> || current instanceof JSpinner
                    || current instanceof JTableHeader || current instanceof JSplitPane) return null;
            if (current instanceof JComponent component
                    && Boolean.FALSE.equals(component.getClientProperty("SmartStock.touchScroll"))) return null;
            // Custom canvases (signatures, badge/template editors) own their drag gestures.
            if (current.getMouseMotionListeners().length > 0
                    && !(current instanceof JTable) && !(current instanceof JList<?>)
                    && !(current instanceof JTree) && !(current instanceof AbstractButton)
                    && !(current instanceof JTextComponent) && !(current instanceof JScrollPane)
                    && !(current instanceof JViewport)) return null;
            if (current instanceof JTable table && (table.isEditing() || table.getDragEnabled())) return null;
            if (current instanceof JList<?> list && list.getDragEnabled()) return null;
            if (current instanceof JTree tree && tree.getDragEnabled()) return null;
            if (current instanceof JViewport view && view.getParent() instanceof JScrollPane pane
                    && pane.getViewport() == view && view.getView() != null) {
                Dimension size = view.getViewSize(), extent = view.getExtentSize();
                if (size.width > extent.width || size.height > extent.height) return view;
            }
        }
        return null;
    }
}
