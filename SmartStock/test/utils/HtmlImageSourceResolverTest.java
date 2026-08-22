package utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlImageSourceResolverTest {
    @TempDir
    Path tempDirectory;

    @Test
    void protectedAssetReferencesBecomeLocalSwingImageUrls() throws Exception {
        String oldCache = System.getProperty("smartstock.image.cache");
        String reference = "smartstock-asset:123e4567-e89b-12d3-a456-426614174000";
        Path source = tempDirectory.resolve("logo.png");
        BufferedImage logo = new BufferedImage(120, 60, BufferedImage.TYPE_INT_RGB);
        var graphics = logo.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, logo.getWidth(), logo.getHeight());
        graphics.dispose();
        ImageIO.write(logo, "png", source.toFile());
        try {
            System.setProperty("smartstock.image.cache", tempDirectory.resolve("cache").toString());
            ImageCacheManager.cacheUploadedImage(reference, source);

            String resolved = HtmlImageSourceResolver.resolveForSwing(
                    "<html><img src='" + reference + "'></html>");

            assertFalse(resolved.contains(reference));
            assertTrue(resolved.contains("src='file:"));
        } finally {
            if (oldCache == null) System.clearProperty("smartstock.image.cache");
            else System.setProperty("smartstock.image.cache", oldCache);
        }
    }
}
