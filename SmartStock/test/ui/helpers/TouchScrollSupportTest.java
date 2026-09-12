package ui.helpers;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TouchScrollSupportTest {
    private static JScrollPane scroll(Component view) {
        view.setPreferredSize(new Dimension(800, 1000));
        JScrollPane pane = new JScrollPane(view);
        pane.setSize(300, 250);
        pane.doLayout();
        pane.getViewport().setViewSize(new Dimension(800, 1000));
        return pane;
    }

    private static MouseEvent event(Component source, int id, int x, int y) {
        return new MouseEvent(source, id, System.currentTimeMillis(),
                id == MouseEvent.MOUSE_DRAGGED ? InputEvent.BUTTON1_DOWN_MASK : 0,
                x, y, x, y, 1, false,
                id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1);
    }

    @Test void swipeScrollsBothAxesAndDoesNotActivateTheControl() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton button = new JButton("Action");
            JPanel form = new JPanel();
            form.add(button);
            JScrollPane pane = scroll(form);
            var gesture = new TouchScrollSupport.Gesture(() -> true);
            List<AWTEvent> delivered = new ArrayList<>();
            assertTrue(gesture.handle(event(button, MouseEvent.MOUSE_PRESSED, 200, 200), delivered::add));
            assertTrue(gesture.handle(event(button, MouseEvent.MOUSE_DRAGGED, 100, 60), delivered::add));
            assertEquals(new Point(100, 140), pane.getViewport().getViewPosition());
            assertTrue(gesture.handle(event(button, MouseEvent.MOUSE_RELEASED, 100, 60), delivered::add));
            assertTrue(gesture.handle(event(button, MouseEvent.MOUSE_CLICKED, 100, 60), delivered::add));
            assertTrue(delivered.isEmpty());
        });
    }

    @Test void tapWithSmallMovementReplaysPressAndKeepsReleaseAndClick() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel form = new JPanel();
            scroll(form);
            var gesture = new TouchScrollSupport.Gesture(() -> true);
            List<AWTEvent> delivered = new ArrayList<>();
            MouseEvent press = event(form, MouseEvent.MOUSE_PRESSED, 100, 100);
            assertTrue(gesture.handle(press, delivered::add));
            assertTrue(gesture.handle(event(form, MouseEvent.MOUSE_DRAGGED, 103, 102), delivered::add));
            assertFalse(gesture.handle(event(form, MouseEvent.MOUSE_RELEASED, 103, 102), delivered::add));
            assertEquals(List.of(press), delivered);
            assertFalse(gesture.handle(event(form, MouseEvent.MOUSE_CLICKED, 103, 102), delivered::add));
        });
    }

    @Test void ordinaryMouseAndEditingOrDrawingGesturesAreUntouched() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel form = new JPanel();
            scroll(form);
            assertFalse(new TouchScrollSupport.Gesture(() -> false)
                    .handle(event(form, MouseEvent.MOUSE_PRESSED, 0, 0), ignored -> fail()));
            JTextField text = new JTextField();
            form.add(text);
            assertNull(TouchScrollSupport.findViewport(text));
            JSlider slider = new JSlider();
            form.add(slider);
            assertNull(TouchScrollSupport.findViewport(slider));
            JComponent signature = new JComponent() { };
            signature.addMouseMotionListener(new MouseMotionAdapter() { });
            form.add(signature);
            assertNull(TouchScrollSupport.findViewport(signature));
            form.putClientProperty("SmartStock.touchScroll", false);
            assertNull(TouchScrollSupport.findViewport(form));
        });
    }

    @Test void scrollingClampsAtBothEdgesAndCancelDropsPendingTap() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = new JTable(100, 4);
            JScrollPane pane = scroll(table);
            var gesture = new TouchScrollSupport.Gesture(() -> true);
            gesture.handle(event(table, MouseEvent.MOUSE_PRESSED, 100, 100), ignored -> fail());
            gesture.handle(event(table, MouseEvent.MOUSE_DRAGGED, -3000, -3000), ignored -> fail());
            Dimension extent = pane.getViewport().getExtentSize();
            assertEquals(new Point(800 - extent.width, 1000 - extent.height), pane.getViewport().getViewPosition());
            gesture.handle(event(table, MouseEvent.MOUSE_DRAGGED, 3000, 3000), ignored -> fail());
            assertEquals(new Point(), pane.getViewport().getViewPosition());
            gesture.cancel();
            assertFalse(gesture.handle(event(table, MouseEvent.MOUSE_RELEASED, 100, 100), ignored -> fail()));
        });
    }
}
