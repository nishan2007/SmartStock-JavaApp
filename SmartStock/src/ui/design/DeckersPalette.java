package ui.design;

import ui.helpers.ThemeManager;

import java.awt.Color;

public final class DeckersPalette {
    public static final Color ORANGE = new Color(255, 91, 0);
    public static final Color MAGENTA = new Color(241, 0, 255);
    public static final Color LIME = new Color(60, 255, 0);
    public static final Color YELLOW = new Color(255, 242, 0);
    public static final Color PURPLE = new Color(112, 34, 168);
    public static final Color CORAL = new Color(240, 79, 69);
    public static final Color CHECKOUT_RED = CORAL;

    private static final Color LIGHT_BACKGROUND = new Color(241, 245, 249);
    private static final Color LIGHT_SURFACE = Color.WHITE;
    private static final Color LIGHT_TEXT = new Color(15, 23, 42);
    private static final Color LIGHT_MUTED = new Color(71, 85, 105);
    private static final Color LIGHT_BORDER = new Color(203, 213, 225);
    private static final Color DARK_BACKGROUND = new Color(18, 18, 18);
    private static final Color DARK_SURFACE = new Color(30, 30, 30);
    private static final Color DARK_TEXT = new Color(245, 245, 245);
    private static final Color DARK_MUTED = new Color(190, 190, 190);
    private static final Color DARK_BORDER = new Color(75, 75, 75);

    private DeckersPalette() {
    }

    public static boolean dark() {
        return ThemeManager.isDarkModeEnabled();
    }

    public static Color background() {
        return dark() ? DARK_BACKGROUND : LIGHT_BACKGROUND;
    }

    public static Color surface() {
        return dark() ? DARK_SURFACE : LIGHT_SURFACE;
    }

    public static Color text() {
        return dark() ? DARK_TEXT : LIGHT_TEXT;
    }

    public static Color muted() {
        return dark() ? DARK_MUTED : LIGHT_MUTED;
    }

    public static Color border() {
        return dark() ? DARK_BORDER : LIGHT_BORDER;
    }

    public static Color fieldBackground() {
        return dark() ? new Color(22, 22, 22) : Color.WHITE;
    }

    public static Color tableStripe() {
        return dark() ? new Color(24, 24, 24) : new Color(248, 250, 252);
    }

    public static Color tableBody(Color accent) {
        return surface();
    }

    public static Color tableHeader(Color accent) {
        return blend(surface(), accent, dark() ? 0.12 : 0.08);
    }

    public static Color sectionFill(Color accent) {
        return blend(surface(), accent, dark() ? 0.05 : 0.03);
    }

    public static Color sectionBorder(Color accent) {
        return blend(border(), accent, dark() ? 0.42 : 0.28);
    }

    public static Color tileFill(Color accent) {
        return blend(surface(), accent, dark() ? 0.12 : 0.08);
    }

    public static Color tileHover(Color accent) {
        return blend(surface(), accent, dark() ? 0.18 : 0.12);
    }

    public static Color tilePressed(Color accent) {
        return blend(surface(), accent, dark() ? 0.28 : 0.18);
    }

    public static Color blend(Color base, Color overlay, double overlayRatio) {
        double ratio = Math.max(0, Math.min(1, overlayRatio));
        double baseRatio = 1 - ratio;
        return new Color(
                clamp((int) Math.round(base.getRed() * baseRatio + overlay.getRed() * ratio)),
                clamp((int) Math.round(base.getGreen() * baseRatio + overlay.getGreen() * ratio)),
                clamp((int) Math.round(base.getBlue() * baseRatio + overlay.getBlue() * ratio))
        );
    }

    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), clamp(alpha));
    }

    public static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
