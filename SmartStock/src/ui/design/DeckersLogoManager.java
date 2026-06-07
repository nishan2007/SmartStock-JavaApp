package ui.design;

import javax.swing.ImageIcon;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class DeckersLogoManager {
    private DeckersLogoManager() {
    }

    public static ImageIcon loadSmartStockLogoIcon(Class<?> anchor) {
        return loadIcon(anchor,
                new String[]{
                        "/Images/CenterLogo.png",
                        "Images/CenterLogo.png",
                        "/CenterLogo.png",
                        "CenterLogo.png"
                },
                new String[]{
                        "src/main/Images/CenterLogo.png",
                        "src/main/resources/Images/CenterLogo.png",
                        "src/Images/CenterLogo.png",
                        "Images/CenterLogo.png",
                        "CenterLogo.png"
                });
    }

    public static ImageIcon loadDeckersLogoIcon(Class<?> anchor) {
        return loadIcon(anchor,
                new String[]{
                        "/Images/Deckers.png",
                        "Images/Deckers.png",
                        "/Deckers.png",
                        "Deckers.png"
                },
                new String[]{
                        "src/Images/Deckers.png",
                        "Images/Deckers.png",
                        "Deckers.png",
                        "SmartStock/src/Images/Deckers.png",
                        "/Users/nishan/Desktop/Deckers/Deckers.png"
                });
    }

    public static Image scaleToFit(Image image, int maxWidth, int maxHeight) {
        BufferedImage source = trimTransparentPadding(toBufferedImage(image));
        int width = Math.max(source.getWidth(), 1);
        int height = Math.max(source.getHeight(), 1);
        double scale = Math.min((double) maxWidth / width, (double) maxHeight / height);
        int scaledWidth = Math.max(1, (int) Math.round(width * scale));
        int scaledHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage scaled = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setComposite(AlphaComposite.Clear);
        graphics.fillRect(0, 0, scaledWidth, scaledHeight);
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(source, 0, 0, scaledWidth, scaledHeight, null);
        graphics.dispose();
        return scaled;
    }

    private static BufferedImage toBufferedImage(Image image) {
        if (image instanceof BufferedImage bufferedImage) {
            return bufferedImage;
        }
        int width = Math.max(image.getWidth(null), 1);
        int height = Math.max(image.getHeight(null), 1);
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = buffered.createGraphics();
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return buffered;
    }

    private static BufferedImage trimTransparentPadding(BufferedImage source) {
        int minX = source.getWidth();
        int minY = source.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int alpha = (source.getRGB(x, y) >>> 24) & 0xff;
                if (alpha > 8) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            return source;
        }
        return source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static ImageIcon loadIcon(Class<?> anchor, String[] resourcePaths, String[] filePaths) {
        for (String path : resourcePaths) {
            java.net.URL url = anchor.getResource(path);
            if (url != null) {
                return new ImageIcon(url);
            }
        }
        for (String path : filePaths) {
            ImageIcon icon = new ImageIcon(path);
            if (icon.getIconWidth() > 0) {
                return icon;
            }
        }
        return null;
    }
}
