package services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Keeps review upload and final import on the same decoded-image limit. */
final class StudioReviewImageLimits {
    static final int MAX_SIDE = 4096;

    private StudioReviewImageLimits() { }

    static boolean accepts(byte[] jpeg) throws IOException {
        if (jpeg == null || jpeg.length < 4 || (jpeg[0] & 255) != 0xff || (jpeg[1] & 255) != 0xd8)
            return false;
        var image = ImageIO.read(new ByteArrayInputStream(jpeg));
        return image != null && image.getWidth() > 0 && image.getHeight() > 0
                && image.getWidth() <= MAX_SIDE && image.getHeight() <= MAX_SIDE;
    }
}
