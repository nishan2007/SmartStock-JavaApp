package ui.helpers;

import javax.swing.*;
import java.awt.*;

public class WindowHelper {
    private static final Dimension POS_MINIMUM_SIZE = new Dimension(1280, 720);
    private static GraphicsConfiguration lastPosGraphicsConfiguration;

    public static <T extends JFrame> T findOpenWindow(Class<T> screenClass) {
        for (Window window : Window.getWindows()) {
            if (!window.isDisplayable()) {
                continue;
            }

            if (screenClass.isInstance(window)) {
                return screenClass.cast(window);
            }
        }
        return null;
    }

    public static boolean focusIfAlreadyOpen(Class<? extends JFrame> screenClass) {
        JFrame frame = findOpenWindow(screenClass);
        if (frame == null) {
            return false;
        }

        if (frame.getState() == Frame.ICONIFIED) {
            frame.setState(Frame.NORMAL);
        }

        configurePosWindow(frame, frame);
        frame.toFront();
        frame.requestFocus();
        frame.setAlwaysOnTop(true);
        frame.setAlwaysOnTop(false);

        return true;
    }

    public static void configurePosWindow(JFrame frame) {
        configurePosWindow(frame, null);
    }

    public static void configurePosWindow(JFrame frame, Window anchor) {
        if (frame == null) {
            return;
        }

        GraphicsConfiguration graphicsConfiguration = getTargetGraphicsConfiguration(frame, anchor);
        Rectangle usableBounds = getUsableBounds(graphicsConfiguration);

        frame.setMinimumSize(POS_MINIMUM_SIZE);
        frame.setExtendedState(Frame.NORMAL);
        frame.setBounds(usableBounds);
        frame.setExtendedState(frame.getExtendedState() | JFrame.MAXIMIZED_BOTH);
    }

    public static void showPosWindow(JFrame frame) {
        showPosWindow(frame, null);
    }

    public static void showPosWindow(JFrame frame, Window anchor) {
        configurePosWindow(frame, anchor);
        frame.setVisible(true);
    }

    private static GraphicsConfiguration getTargetGraphicsConfiguration(JFrame frame, Window anchor) {
        GraphicsConfiguration graphicsConfiguration = getWindowGraphicsConfiguration(anchor);

        if (graphicsConfiguration == null) {
            graphicsConfiguration = getWindowGraphicsConfiguration(frame);
        }

        if (graphicsConfiguration == null) {
            graphicsConfiguration = lastPosGraphicsConfiguration;
        }

        if (graphicsConfiguration == null) {
            graphicsConfiguration = GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration();
        }

        lastPosGraphicsConfiguration = graphicsConfiguration;
        return graphicsConfiguration;
    }

    private static GraphicsConfiguration getWindowGraphicsConfiguration(Window window) {
        if (window == null || !window.isDisplayable()) {
            return null;
        }
        return window.getGraphicsConfiguration();
    }

    private static Rectangle getUsableBounds(GraphicsConfiguration graphicsConfiguration) {
        Rectangle bounds = graphicsConfiguration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration);
        int width = Math.max(bounds.width - insets.left - insets.right, 1);
        int height = Math.max(bounds.height - insets.top - insets.bottom, 1);
        return new Rectangle(
                bounds.x + insets.left,
                bounds.y + insets.top,
                width,
                height
        );
    }
}
