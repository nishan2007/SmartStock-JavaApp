package services;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class StudioEdgeRefinerTest {
    @Test void preservesDimensionsOpaqueDetailAndExistingTransparency() {
        BufferedImage image = new BufferedImage(40, 32, BufferedImage.TYPE_INT_ARGB);
        BufferedImage mask = new BufferedImage(40, 32, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 40; x++) {
            image.setRGB(x, y, 0xff000000 | (x * 6 << 16) | (y * 7 << 8) | 53);
            mask.getRaster().setSample(x, y, 0, 255);
        }
        image.setRGB(10, 10, 0x00123456);
        image.setRGB(11, 10, 0x80123456);
        var result = StudioEdgeRefiner.apply(image, mask, true);
        assertEquals(40, result.getWidth()); assertEquals(32, result.getHeight());
        for (int y = 0; y < 32; y++) for (int x = 0; x < 40; x++)
            assertEquals(image.getRGB(x, y), result.getRGB(x, y));
    }

    @Test void removesObservedBlueSpillWithoutChangingSolidRedInterior() {
        BufferedImage image = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
        BufferedImage mask = new BufferedImage(9, 9, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < 9; y++) for (int x = 0; x < 9; x++) {
            int a = x < 4 ? 0 : x == 4 ? 128 : 255;
            int colour = x < 4 ? 0xff0000ff : x == 4 ? 0xff80007f : 0xffff0000;
            image.setRGB(x, y, colour); mask.getRaster().setSample(x, y, 0, a);
        }
        var plain = StudioEdgeRefiner.apply(image, mask, false);
        var cleaned = StudioEdgeRefiner.apply(image, mask, true);
        assertEquals(0xff80007f & 0xffffff, plain.getRGB(4, 4) & 0xffffff);
        assertTrue((cleaned.getRGB(4, 4) & 255) < (plain.getRGB(4, 4) & 255));
        assertTrue(((cleaned.getRGB(4, 4) >>> 16) & 255) > 240);
        assertEquals(0xffff0000, cleaned.getRGB(6, 4));
        assertEquals(0, cleaned.getRGB(2, 4) >>> 24);
        assertTrue((cleaned.getRGB(4, 4) >>> 24) > 100);
    }

    @Test void preservesThinForegroundAndInteriorHoles() {
        var image = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
        var mask = new BufferedImage(9, 9, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < 9; y++) for (int x = 0; x < 9; x++) {
            image.setRGB(x, y, x == 4 ? 0xff402020 : 0xffeeeeee);
            mask.getRaster().setSample(x, y, 0, x == 4 && y != 4 ? 255 : 0);
        }
        var result = StudioEdgeRefiner.apply(image, mask, true);
        assertEquals(255, result.getRGB(4, 3) >>> 24);
        assertEquals(0, result.getRGB(4, 4) >>> 24);
    }
}
