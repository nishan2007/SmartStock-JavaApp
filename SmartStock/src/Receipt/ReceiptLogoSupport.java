package Receipt;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.io.ByteArrayOutputStream;

final class ReceiptLogoSupport {
    private ReceiptLogoSupport() {
    }

    static byte[] escPosLogo(BufferedImage logo) {
        if (logo == null) return new byte[0];
        BufferedImage prepared = monochrome(logo, 384, 160);
        int width = prepared.getWidth();
        int height = prepared.getHeight();
        int bytesPerRow = (width + 7) / 8;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, 0x1B, 0x61, 0x01);
        write(out, 0x1D, 0x76, 0x30, 0x00, bytesPerRow & 0xFF,
                (bytesPerRow >> 8) & 0xFF, height & 0xFF, (height >> 8) & 0xFF);
        for (int y = 0; y < height; y++) {
            for (int xByte = 0; xByte < bytesPerRow; xByte++) {
                int value = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int x = xByte * 8 + bit;
                    if (x < width && isDark(prepared.getRGB(x, y))) value |= 0x80 >> bit;
                }
                out.write(value);
            }
        }
        out.write('\n');
        return out.toByteArray();
    }

    static int drawLetterLogo(Graphics2D graphics, PageFormat pageFormat, BufferedImage logo) {
        if (logo == null) return 0;
        int maxWidth = Math.min((int) pageFormat.getImageableWidth(), 300);
        int maxHeight = 100;
        double scale = Math.min((double) maxWidth / logo.getWidth(),
                (double) maxHeight / logo.getHeight());
        scale = Math.min(scale, 1.0);
        int width = Math.max(1, (int) Math.round(logo.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(logo.getHeight() * scale));
        int x = (int) pageFormat.getImageableX()
                + Math.max(((int) pageFormat.getImageableWidth() - width) / 2, 0);
        int y = (int) pageFormat.getImageableY();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(logo, x, y, width, height, null);
        return height + 12;
    }

    private static BufferedImage monochrome(BufferedImage logo, int maxWidth, int maxHeight) {
        double scale = Math.min((double) maxWidth / logo.getWidth(),
                (double) maxHeight / logo.getHeight());
        scale = Math.min(scale, 1.0);
        int width = Math.max(1, (int) Math.round(logo.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(logo.getHeight() * scale));
        BufferedImage prepared = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = prepared.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(logo, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return prepared;
    }

    private static boolean isDark(int rgb) {
        Color color = new Color(rgb);
        return color.getRed() * 299 + color.getGreen() * 587 + color.getBlue() * 114 < 180_000;
    }

    private static void write(ByteArrayOutputStream out, int... values) {
        for (int value : values) out.write(value);
    }
}
