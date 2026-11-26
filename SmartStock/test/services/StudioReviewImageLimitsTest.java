package services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class StudioReviewImageLimitsTest {
    @Test
    void acceptsWideReviewedPhotoThatPreviouslyFailedAtImport() throws Exception {
        assertTrue(StudioReviewImageLimits.accepts(jpeg(2048, 768)));
    }

    @Test
    void rejectsOversizedAndInvalidPreviewsBeforeApproval() throws Exception {
        assertFalse(StudioReviewImageLimits.accepts(jpeg(4097, 1)));
        assertFalse(StudioReviewImageLimits.accepts(new byte[] {1, 2, 3, 4}));
    }

    private static byte[] jpeg(int width, int height) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", output);
        return output.toByteArray();
    }
}
