import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class GenerateAppIcons {
    private static final int SIZE = 1024;

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

        Color bgTop = dark ? new Color(27, 33, 40) : new Color(255, 255, 255);
        Color bgBottom = dark ? new Color(7, 11, 17) : new Color(234, 244, 250);
        Color border = dark ? new Color(73, 91, 107) : new Color(190, 210, 220);
        Color text = dark ? new Color(245, 250, 253) : new Color(28, 45, 58);
        Color subText = dark ? new Color(150, 172, 187) : new Color(92, 112, 126);
        Color blue = dark ? new Color(68, 169, 241) : new Color(16, 117, 183);
        Color brightBlue = dark ? new Color(93, 197, 255) : new Color(40, 146, 213);
        Color green = dark ? new Color(123, 208, 69) : new Color(88, 172, 54);
        Color yellow = dark ? new Color(246, 199, 70) : new Color(218, 165, 36);

        RoundRectangle2D card = new RoundRectangle2D.Double(48, 48, 928, 928, 210, 210);
        g.setColor(new Color(0, 0, 0, dark ? 110 : 35));
        g.fill(new RoundRectangle2D.Double(64, 82, 896, 872, 200, 200));
        g.setPaint(new GradientPaint(48, 48, bgTop, 976, 976, bgBottom));
        g.fill(card);
        g.setColor(border);
        g.setStroke(new BasicStroke(6f));
        g.draw(card);

        drawShelf(g, dark, blue, brightBlue, green, yellow);
        drawWordmark(g, text, subText);
        g.dispose();
        return image;
    }

    private static void drawShelf(Graphics2D g, boolean dark, Color blue, Color brightBlue, Color green, Color yellow) {
        int left = 178;
        int bottom = 618;
        int barWidth = 96;
        int gap = 34;
        int[] heights = {176, 270, 374, 470};
        Color[] colors = {yellow, green, blue, brightBlue};

        g.setColor(new Color(0, 0, 0, dark ? 70 : 28));
        g.fillRoundRect(142, bottom + 30, 734, 46, 30, 30);

        g.setStroke(new BasicStroke(20f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(dark ? new Color(86, 106, 122) : new Color(153, 176, 188));
        g.drawLine(164, bottom + 10, 860, bottom + 10);

        for (int i = 0; i < heights.length; i++) {
            int x = left + i * (barWidth + gap);
            int h = heights[i];
            g.setColor(new Color(0, 0, 0, dark ? 90 : 38));
            g.fillRoundRect(x + 14, bottom - h + 18, barWidth, h, 36, 36);
            g.setPaint(new GradientPaint(x, bottom - h, colors[i].brighter(), x + barWidth, bottom, colors[i].darker()));
            g.fillRoundRect(x, bottom - h, barWidth, h, 36, 36);
            g.setColor(new Color(255, 255, 255, dark ? 58 : 78));
            g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(x + 28, bottom - h + 38, x + 28, bottom - 34);
        }

        Path2D line = new Path2D.Double();
        line.moveTo(230, 430);
        line.curveTo(335, 365, 385, 405, 480, 314);
        line.curveTo(560, 236, 650, 292, 770, 164);
        g.setColor(dark ? new Color(140, 226, 91) : new Color(70, 154, 47));
        g.setStroke(new BasicStroke(26f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(line);

        g.setColor(dark ? new Color(214, 249, 192) : new Color(255, 255, 255));
        g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(line);

        Path2D arrow = new Path2D.Double();
        arrow.moveTo(766, 164);
        arrow.lineTo(756, 238);
        arrow.moveTo(766, 164);
        arrow.lineTo(690, 176);
        g.setColor(dark ? new Color(140, 226, 91) : new Color(70, 154, 47));
        g.setStroke(new BasicStroke(24f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(arrow);

        drawBox(g, 678, 410, dark);
    }

    private static void drawBox(Graphics2D g, int x, int y, boolean dark) {
        Color face = dark ? new Color(199, 138, 61) : new Color(207, 145, 61);
        Color side = dark ? new Color(153, 94, 43) : new Color(172, 103, 42);
        Color top = dark ? new Color(242, 184, 92) : new Color(236, 181, 83);

        Path2D topFace = new Path2D.Double();
        topFace.moveTo(x + 72, y);
        topFace.lineTo(x + 160, y + 42);
        topFace.lineTo(x + 86, y + 82);
        topFace.lineTo(x, y + 38);
        topFace.closePath();

        Path2D leftFace = new Path2D.Double();
        leftFace.moveTo(x, y + 38);
        leftFace.lineTo(x + 86, y + 82);
        leftFace.lineTo(x + 86, y + 178);
        leftFace.lineTo(x, y + 132);
        leftFace.closePath();

        Path2D rightFace = new Path2D.Double();
        rightFace.moveTo(x + 160, y + 42);
        rightFace.lineTo(x + 86, y + 82);
        rightFace.lineTo(x + 86, y + 178);
        rightFace.lineTo(x + 160, y + 132);
        rightFace.closePath();

        g.setColor(new Color(0, 0, 0, dark ? 80 : 32));
        g.fillRoundRect(x + 16, y + 64, 150, 136, 30, 30);
        g.setColor(top);
        g.fill(topFace);
        g.setColor(face);
        g.fill(leftFace);
        g.setColor(side);
        g.fill(rightFace);
        g.setColor(new Color(255, 255, 255, dark ? 48 : 80));
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(x + 72, y, x + 86, y + 82);
        g.drawLine(x + 86, y + 82, x + 86, y + 178);
    }

    private static void drawWordmark(Graphics2D g, Color text, Color subText) {
        Font title = new Font("Avenir Next", Font.BOLD, 112);
        if (!title.getFamily().contains("Avenir")) {
            title = new Font("Helvetica Neue", Font.BOLD, 112);
        }
        g.setFont(title);
        FontMetrics titleMetrics = g.getFontMetrics();
        String name = "SmartStock";
        int x = (SIZE - titleMetrics.stringWidth(name)) / 2;
        g.setColor(text);
        g.drawString(name, x, 786);

        Font subtitle = new Font("Avenir Next", Font.BOLD, 42);
        if (!subtitle.getFamily().contains("Avenir")) {
            subtitle = new Font("Helvetica Neue", Font.BOLD, 42);
        }
        g.setFont(subtitle);
        FontMetrics subtitleMetrics = g.getFontMetrics();
        String label = "INVENTORY";
        int subtitleX = (SIZE - subtitleMetrics.stringWidth(label)) / 2;
        g.setColor(subText);
        g.drawString(label, subtitleX, 846);
    }
}
