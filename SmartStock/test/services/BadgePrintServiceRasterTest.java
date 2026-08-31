package services;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BadgePrintServiceRasterTest {
    @Test
    void printerRasterIsOpaqueRgbSoWindowsBadgeDriversDoNotInterpretTextAlpha() {
        BufferedImage source = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, new Color(20, 40, 60, 255).getRGB());
        source.setRGB(1, 0, new Color(0, 0, 0, 0).getRGB());

        BufferedImage flattened = BadgePrintService.flattenForPrinter(source);

        assertEquals(BufferedImage.TYPE_INT_RGB, flattened.getType());
        assertEquals(new Color(20, 40, 60).getRGB(), flattened.getRGB(0, 0));
        assertEquals(Color.WHITE.getRGB(), flattened.getRGB(1, 0));
    }
}
