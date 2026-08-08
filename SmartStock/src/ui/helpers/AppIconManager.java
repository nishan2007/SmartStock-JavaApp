package ui.helpers;

import javax.imageio.ImageIO;
import java.awt.AWTEvent;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Applies the SmartStock brand icon to native taskbar/Dock surfaces and every Swing frame. */
public final class AppIconManager {
    private static final int[] ICON_SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};
    private static volatile boolean installed;
    private static volatile List<Image> lightIcons;
    private static volatile List<Image> darkIcons;

    private AppIconManager() {
    }

    public static synchronized void install() {
        if (installed || GraphicsEnvironment.isHeadless()) return;
        installed = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof WindowEvent windowEvent
                    && windowEvent.getID() == WindowEvent.WINDOW_OPENED) {
                applyTo(windowEvent.getWindow());
            }
        }, AWTEvent.WINDOW_EVENT_MASK);
        applyTaskbarIcon();
        refreshOpenWindows();
    }

    public static void refreshOpenWindows() {
        if (GraphicsEnvironment.isHeadless()) return;
        applyTaskbarIcon();
        for (Window window : Window.getWindows()) applyTo(window);
    }

    public static void applyTo(Window window) {
        if (!(window instanceof Frame frame)) return;
        List<Image> icons = iconsForCurrentTheme();
        if (!icons.isEmpty()) frame.setIconImages(icons);
    }

    static List<Image> iconsForCurrentTheme() {
        return ThemeManager.isDarkModeEnabled() ? darkIcons() : lightIcons();
    }

    private static void applyTaskbarIcon() {
        List<Image> icons = iconsForCurrentTheme();
        if (icons.isEmpty() || !Taskbar.isTaskbarSupported()) return;
        Taskbar taskbar = Taskbar.getTaskbar();
        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            try {
                taskbar.setIconImage(icons.get(icons.size() - 1));
            } catch (RuntimeException ignored) {
                // The packaged native launcher still supplies its embedded icon.
            }
        }
    }

    private static List<Image> lightIcons() {
        List<Image> cached = lightIcons;
        if (cached == null) lightIcons = cached = load("/Images/AppIconLight.png");
        return cached;
    }

    private static List<Image> darkIcons() {
        List<Image> cached = darkIcons;
        if (cached == null) darkIcons = cached = load("/Images/AppIconDark.png");
        return cached;
    }

    private static List<Image> load(String resourcePath) {
        URL resource = AppIconManager.class.getResource(resourcePath);
        if (resource == null) return List.of();
        try {
            BufferedImage source = ImageIO.read(resource);
            if (source == null) return List.of();
            List<Image> icons = new ArrayList<>(ICON_SIZES.length);
            for (int size : ICON_SIZES) icons.add(scale(source, size));
            return List.copyOf(icons);
        } catch (IOException ex) {
            return List.of();
        }
    }

    private static BufferedImage scale(BufferedImage source, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, size, size, null);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
