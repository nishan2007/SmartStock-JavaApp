package services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class BackgroundRemovalModelTest {
    @Test void bestQualityRunsOfflineAndRetainsOriginalSizeAndTransparency() throws Exception {
        String model = System.getProperty("smartstock.test.bestPhotoModel");
        Assumptions.assumeTrue(model != null, "Supply the pinned BiRefNet path for offline verification.");
        var image = new BufferedImage(192, 128, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, 192, 128);
        g.setColor(new Color(112,34,168)); g.fillOval(56, 15, 80, 100); g.dispose();
        image.setRGB(96, 64, 0);
        var bytes = new ByteArrayOutputStream(); ImageIO.write(image, "png", bytes);
        long start = System.nanoTime();
        try (var processor = new CatalogStudioImageProcessor(Path.of(model), StudioQuality.BEST)) {
            var result = ImageIO.read(new ByteArrayInputStream(processor.removeBackgroundPng(bytes.toByteArray(), true)));
            assertEquals(192, result.getWidth()); assertEquals(128, result.getHeight());
            assertEquals(0, result.getRGB(96, 64) >>> 24);
            assertTrue((result.getRGB(96, 40) >>> 24) > 200);
            assertTrue((result.getRGB(0, 0) >>> 24) < 20);
        }
        System.out.printf("BiRefNet load + inference: %.1f seconds%n", (System.nanoTime() - start) / 1_000_000_000.0);
    }
    @Test void offlineModelReturnsTransparentPngWithoutChangingDimensions() throws Exception {
        String model = System.getProperty("smartstock.test.photoModel");
        Assumptions.assumeTrue(model != null, "Supply the packaged model path to run offline inference verification.");
        BufferedImage original = new BufferedImage(160, 128, BufferedImage.TYPE_INT_ARGB);
        var g = original.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, 160, 128);
        g.setColor(new Color(112,34,168)); g.fillOval(45, 20, 70, 95); g.dispose();
        original.setRGB(80, 60, 0);
        var bytes = new ByteArrayOutputStream(); ImageIO.write(original,"png",bytes);
        try (var processor = new CatalogStudioImageProcessor(Path.of(model))) {
            var result = ImageIO.read(new ByteArrayInputStream(processor.removeBackgroundPng(bytes.toByteArray())));
            assertEquals(160, result.getWidth()); assertEquals(128, result.getHeight());
            assertTrue(result.getColorModel().hasAlpha()); assertEquals(0, result.getRGB(80,60) >>> 24);
            boolean transparent = false, visible = false;
            for (int y=0;y<128;y++) for(int x=0;x<160;x++) {
                int alpha=result.getRGB(x,y) >>> 24;
                transparent |= alpha < 20; visible |= alpha > 200;
            }
            assertTrue(transparent); assertTrue(visible);
            assertEquals(1200, ImageIO.read(new ByteArrayInputStream(processor.makeStudioJpeg(bytes.toByteArray()))).getWidth());
        }
    }
}
