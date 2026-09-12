package services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ui.helpers.CustomerPhotoHelper;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CustomerPhotoTest {
    @TempDir Path directory;

    @Test void absentPhotoAndExistingReferenceNeedNoUpload() throws Exception {
        assertEquals("", CustomerPhotoHelper.uploadIfNeeded(null));
        assertEquals("", CustomerPhotoHelper.uploadIfNeeded("  "));
        assertEquals("https://example.com/photo.jpg",
                CustomerPhotoHelper.uploadIfNeeded("https://example.com/photo.jpg"));
        assertThrows(IllegalArgumentException.class,
                () -> CustomerPhotoHelper.uploadIfNeeded(directory.resolve("missing.jpg").toString()));
    }

    @Test void cardCentersPortraitWithoutStretching() throws Exception {
        BufferedImage portrait = new BufferedImage(40, 80, BufferedImage.TYPE_INT_RGB);
        var graphics = portrait.createGraphics();
        graphics.setColor(Color.MAGENTA);
        graphics.fillRect(0, 0, 40, 80);
        graphics.dispose();
        Path file = directory.resolve("portrait.png");
        ImageIO.write(portrait, "png", file.toFile());
        var data = new CustomerCardService.CardData(1, "Customer", "Individual", "CA-1",
                "", "", null, file.toString(), null, 1);
        var card = CustomerCardService.render(data, CustomerCardService.defaults().get(0));
        var bounds = CustomerCardService.layoutRects("").get("photo");
        assertEquals(Color.MAGENTA.getRGB(), card.getRGB(bounds.x + bounds.width / 2, bounds.y + 10));
        assertNotEquals(Color.MAGENTA.getRGB(), card.getRGB(bounds.x + 5, bounds.y + 10));
    }
}
