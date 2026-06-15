import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class GenerateAppIcons {
    private static final int SIZE = 1024;
    private static final Color ORANGE = new Color(255, 91, 0);
    private static final Color MAGENTA = new Color(241, 0, 255);
    private static final Color LIME = new Color(60, 255, 0);
    private static final Color YELLOW = new Color(255, 242, 0);
    private static final Color PURPLE = new Color(112, 34, 168);
    private static final Color CORAL = new Color(240, 79, 69);
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

    private GenerateAppIcons() {
    }

    public static void main(String[] args) throws Exception {
        File outputDir = args.length > 0 ? new File(args[0]) : new File("src/Images");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IllegalStateException("Unable to create " + outputDir);
        }
        ImageIO.write(drawIcon(false), "png", new File(outputDir, "AppIconLight.png"));
        ImageIO.write(drawIcon(true), "png", new File(outputDir, "AppIconDark.png"));
    }

    private static BufferedImage drawIcon(boolean dark) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        drawMenuTileGlyph(g, dark);
        g.dispose();
        return image;
    }

    private static void drawMenuTileGlyph(Graphics2D g, boolean dark) {
        int tileX = 0;
        int tileY = 0;
        int tileSize = SIZE;
        int radius = 232;
        Shape oldClip = g.getClip();
        RoundRectangle2D tile = new RoundRectangle2D.Double(tileX, tileY, tileSize, tileSize, radius, radius);

        g.setClip(tile);
        drawPaletteField(g, tileX, tileY, tileSize, dark);
        g.setColor(new Color(255, 255, 255, dark ? 42 : 58));
        g.fill(new Ellipse2D.Double(tileX - 190, tileY - 220, 480, 480));
        g.setColor(new Color(255, 255, 255, dark ? 24 : 36));
        g.fill(new Ellipse2D.Double(tileX + 590, tileY + 570, 420, 420));
        g.setClip(oldClip);

        drawCartInventoryGlyph(g, tileX + 186, tileY + 280);
        drawBadge(g, tileX + 590, tileY + 590, dark);
    }

    private static void drawPaletteField(Graphics2D g, int x, int y, int size, boolean dark) {
        Color top = dark ? blend(ORANGE, Color.WHITE, 0.06) : blend(ORANGE, Color.WHITE, 0.10);
        Color bottom = dark ? blend(CORAL, DARK_BACKGROUND, 0.18) : blend(CORAL, Color.BLACK, 0.12);
        g.setPaint(new GradientPaint(x, y, top, x + size, y + size, bottom));
        g.fillRect(x, y, size, size);

        g.setColor(new Color(PURPLE.getRed(), PURPLE.getGreen(), PURPLE.getBlue(), dark ? 92 : 78));
        g.fill(new Ellipse2D.Double(x + size - 260, y - 116, 390, 390));
        g.setColor(new Color(MAGENTA.getRed(), MAGENTA.getGreen(), MAGENTA.getBlue(), dark ? 62 : 56));
        g.fill(new Ellipse2D.Double(x - 164, y - 178, 410, 410));
        g.setColor(new Color(LIME.getRed(), LIME.getGreen(), LIME.getBlue(), dark ? 190 : 170));
        g.fill(new Ellipse2D.Double(x + 72, y + 504, 360, 360));

        g.setColor(new Color(YELLOW.getRed(), YELLOW.getGreen(), YELLOW.getBlue(), dark ? 54 : 48));
        g.fill(new Ellipse2D.Double(x + 82, y + size - 280, 270, 270));
        g.setColor(new Color(LIME.getRed(), LIME.getGreen(), LIME.getBlue(), dark ? 210 : 188));
        g.fill(new Ellipse2D.Double(x + 286, y + size - 250, 250, 250));
        g.setColor(new Color(MAGENTA.getRed(), MAGENTA.getGreen(), MAGENTA.getBlue(), dark ? 54 : 48));
        g.fill(new Ellipse2D.Double(x + size - 324, y + size - 284, 330, 330));

        g.setColor(new Color(0, 0, 0, dark ? 42 : 18));
        g.fillRect(x, y + size - 92, size, 92);
    }

    private static void drawCartInventoryGlyph(Graphics2D g, int x, int y) {
        g.setColor(new Color(0, 0, 0, 38));
        g.fillRoundRect(x + 36, y + 92, 362, 214, 42, 42);
        g.fillOval(x + 96, y + 342, 54, 54);
        g.fillOval(x + 314, y + 342, 54, 54);

        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(34f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D handle = new Path2D.Double();
        handle.moveTo(x + 4, y + 20);
        handle.lineTo(x + 82, y + 20);
        handle.lineTo(x + 116, y + 110);
        g.draw(handle);

        g.setStroke(new BasicStroke(30f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D basket = new Path2D.Double();
        basket.moveTo(x + 112, y + 108);
        basket.lineTo(x + 392, y + 108);
        basket.lineTo(x + 358, y + 292);
        basket.lineTo(x + 148, y + 292);
        basket.closePath();
        g.draw(basket);

        g.setStroke(new BasicStroke(22f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(x + 168, y + 158, x + 342, y + 158);
        g.drawLine(x + 184, y + 214, x + 326, y + 214);
        g.drawLine(x + 204, y + 270, x + 306, y + 270);

        g.fillOval(x + 94, y + 326, 70, 70);
        g.fillOval(x + 298, y + 326, 70, 70);
    }

    private static void drawBadge(Graphics2D g, int x, int y, boolean dark) {
        g.setColor(new Color(0, 0, 0, dark ? 85 : 48));
        g.fillOval(x + 10, y + 14, 184, 184);
        g.setPaint(new GradientPaint(x, y, blend(PURPLE, MAGENTA, 0.34), x + 184, y + 184, blend(CORAL, ORANGE, 0.22)));
        g.fillOval(x, y, 184, 184);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(26f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(x + 92, y + 46, x + 92, y + 138);
        g.drawLine(x + 46, y + 92, x + 138, y + 92);
    }

    private static Color blend(Color base, Color overlay, double overlayRatio) {
        double ratio = Math.max(0, Math.min(1, overlayRatio));
        double baseRatio = 1 - ratio;
        return new Color(
                clamp((int) Math.round(base.getRed() * baseRatio + overlay.getRed() * ratio)),
                clamp((int) Math.round(base.getGreen() * baseRatio + overlay.getGreen() * ratio)),
                clamp((int) Math.round(base.getBlue() * baseRatio + overlay.getBlue() * ratio))
        );
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
