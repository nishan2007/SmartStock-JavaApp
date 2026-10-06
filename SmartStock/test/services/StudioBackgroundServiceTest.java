package services;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class StudioBackgroundServiceTest {
    @Test void oldClientsDefaultToFastAndUnknownQualityIsRejected() {
        assertEquals(StudioQuality.FAST, StudioQuality.parse(null));
        assertEquals(StudioQuality.FAST, StudioQuality.parse(""));
        assertEquals(StudioQuality.BEST, StudioQuality.parse("best"));
        assertThrows(IllegalArgumentException.class, () -> StudioQuality.parse("ultra"));
    }

    @Test void unavailableOrUntrustedQualityModelFailsClearly() throws Exception {
        assertThrows(CatalogStudioImageProcessor.ModelUnavailableException.class, () ->
                new CatalogStudioImageProcessor(java.nio.file.Path.of("missing-quality-model.onnx"), StudioQuality.BEST));
        var model = java.nio.file.Files.createTempFile("invalid-studio-model", ".onnx");
        try {
            java.nio.file.Files.writeString(model, "untrusted model");
            assertThrows(CatalogStudioImageProcessor.ModelUnavailableException.class, () ->
                    new CatalogStudioImageProcessor(model, StudioQuality.BEST));
        } finally { java.nio.file.Files.deleteIfExists(model); }
    }
    private byte[] image(int width, int height, String format) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),format,output);
        return output.toByteArray();
    }
    @Test void allowsSupportedImagesAtMinimumDimensions() throws Exception {
        StudioBackgroundService.validate(image(32,32,"png"));
        StudioBackgroundService.validate(image(32,32,"jpeg"));
    }
    @Test void rejectsUnsupportedAndOversizedInputBeforeInference() throws Exception {
        assertThrows(IllegalArgumentException.class,()->StudioBackgroundService.validate(new byte[0]));
        assertThrows(IllegalArgumentException.class,()->StudioBackgroundService.validate(new byte[6*1024*1024+1]));
        assertThrows(IllegalArgumentException.class,()->StudioBackgroundService.validate(image(32,32,"gif")));
        assertThrows(IllegalArgumentException.class,()->StudioBackgroundService.validate(image(31,100,"png")));
        assertThrows(IllegalArgumentException.class,()->StudioBackgroundService.validate(image(4001,3000,"png")));
        assertThrows(IllegalArgumentException.class,()->StudioBackgroundService.validate("not a photo".getBytes()));
    }
}
