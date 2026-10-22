package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class BadgeArtworkCacheTest {
    @Test void repeatedPaintsReuseArtworkAndReplacementRefreshesIt() throws Exception {
        BadgeTemplate.Element element = new BadgeTemplate.Element();
        element.image = artwork(0xff0000);
        BufferedImage first = element.artwork();
        assertSame(first, element.artwork());
        element.image = artwork(0x0000ff);
        BufferedImage replacement = element.artwork();
        assertNotSame(first, replacement);
        assertEquals(0x0000ff, replacement.getRGB(0, 0) & 0xffffff);
        element.image = "invalid";
        assertThrows(java.io.IOException.class, element::artwork);
    }

    private String artwork(int colour) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, colour);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
}
