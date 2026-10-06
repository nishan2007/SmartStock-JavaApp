package services;

import java.awt.image.BufferedImage;

/** Conservative, colour-guided mask refinement and local background decontamination. */
final class StudioEdgeRefiner {
    private StudioEdgeRefiner() { }

    static BufferedImage apply(BufferedImage original, BufferedImage mask, boolean cleanEdges) {
        int width = original.getWidth(), height = original.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int rgb = original.getRGB(x, y), alpha = mask.getRaster().getSample(x, y, 0);
            if (cleanEdges && alpha > 4 && alpha < 251 && (rgb >>> 24) > 0) {
                // Only refine the uncertain band. Colour differences protect fine foreground edges.
                double sum = alpha * 4.0, weight = 4;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if ((dx == 0 && dy == 0) || nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                    int neighbour = original.getRGB(nx, ny);
                    if ((neighbour >>> 24) == 0) continue;
                    double w = 1.0 / (1 + colourDistance(rgb, neighbour) / 400.0);
                    sum += mask.getRaster().getSample(nx, ny, 0) * w; weight += w;
                }
                alpha = (int) Math.round(sum / weight);
                rgb = decontaminate(original, mask, x, y, rgb, alpha);
            }
            // Never recreate pixels which were transparent in the source.
            int finalAlpha = (alpha * (original.getRGB(x, y) >>> 24) + 127) / 255;
            result.setRGB(x, y, (finalAlpha << 24) | (rgb & 0xffffff));
        }
        return result;
    }

    private static int decontaminate(BufferedImage original, BufferedImage mask, int x, int y, int rgb, int alpha) {
        if (alpha < 24 || alpha > 240) return rgb;
        int background = 0, foreground = 0;
        boolean hasBackground = false, hasForeground = false;
        // Look across the edge for actual opaque/empty neighbours, rather than guessing a white background.
        for (int radius = 1; radius <= 8 && !(hasBackground && hasForeground); radius *= 2) {
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx * radius, ny = y + dy * radius;
                if (nx < 0 || ny < 0 || nx >= original.getWidth() || ny >= original.getHeight()) continue;
                int colour = original.getRGB(nx, ny), a = mask.getRaster().getSample(nx, ny, 0);
                if ((colour >>> 24) < 250) continue;
                if (a <= 4 && !hasBackground) { background = colour; hasBackground = true; }
                if (a >= 251 && !hasForeground) { foreground = colour; hasForeground = true; }
            }
        }
        if (!hasBackground || !hasForeground) return rgb;
        int corrected = 0;
        double a = alpha / 255.0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            double c = (rgb >>> shift) & 255, b = (background >>> shift) & 255;
            int value = (int) Math.round(Math.max(0, Math.min(255, (c - (1 - a) * b) / a)));
            corrected |= value << shift;
        }
        // Reject the estimate when it moves away from the observed foreground colour.
        if (colourDistance(corrected, foreground) >= colourDistance(rgb, foreground)) return rgb;
        return corrected;
    }

    private static int colourDistance(int first, int second) {
        int r = ((first >>> 16) & 255) - ((second >>> 16) & 255);
        int g = ((first >>> 8) & 255) - ((second >>> 8) & 255);
        int b = (first & 255) - (second & 255);
        return r * r + g * g + b * b;
    }
}
