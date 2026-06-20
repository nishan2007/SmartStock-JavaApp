package Receipt;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Objects;

public final class ReceiptBarcodeRenderer {
    private ReceiptBarcodeRenderer() {
    }

    public static boolean hasScannableReceiptNumber(ReceiptData receipt) {
        return receipt != null && hasScannableText(receipt.getReceiptNumber());
    }

    public static boolean hasScannableText(String text) {
        return text != null && !text.trim().isBlank();
    }

    public static BufferedImage renderCode128(String text, int width, int height) {
        String value = Objects.requireNonNullElse(text, "").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Receipt barcode text is blank.");
        }
        int safeWidth = Math.max(180, width);
        int safeHeight = Math.max(48, height);
        BitMatrix matrix = new Code128Writer().encode(
                value,
                BarcodeFormat.CODE_128,
                safeWidth,
                safeHeight,
                Map.of(EncodeHintType.MARGIN, 12)
        );
        BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                image.setRGB(x, y, matrix.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }
        return image;
    }

    public static void drawBarcodeFit(Graphics2D g, BufferedImage image, int x, int y, int width, int height) {
        double scale = Math.min(width / (double) image.getWidth(), height / (double) image.getHeight());
        int drawW = Math.max((int) Math.round(image.getWidth() * scale), 1);
        int drawH = Math.max((int) Math.round(image.getHeight() * scale), 1);
        int drawX = x + ((width - drawW) / 2);
        int drawY = y + ((height - drawH) / 2);
        Object previousInterpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        Object previousAntialias = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.drawImage(image, drawX, drawY, drawW, drawH, null);
        } finally {
            if (previousInterpolation != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, previousInterpolation);
            }
            if (previousAntialias != null) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, previousAntialias);
            }
        }
    }
}
