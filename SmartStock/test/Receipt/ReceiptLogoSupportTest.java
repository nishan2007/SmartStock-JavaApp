package Receipt;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptLogoSupportTest {
    @Test
    void createsCenteredEscPosRasterDataForALogo() {
        BufferedImage logo = new BufferedImage(16, 8, BufferedImage.TYPE_INT_RGB);
        var graphics = logo.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 16, 8);
        graphics.setColor(Color.BLACK);
        graphics.fillRect(2, 2, 12, 4);
        graphics.dispose();

        byte[] bytes = ReceiptLogoSupport.escPosLogo(logo);

        assertTrue(bytes.length > 10);
        assertEquals(0x1B, bytes[0] & 0xFF);
        assertEquals(0x61, bytes[1] & 0xFF);
        assertEquals(0x01, bytes[2] & 0xFF);
        assertEquals(0x1D, bytes[3] & 0xFF);
        assertEquals(0x76, bytes[4] & 0xFF);
        assertEquals(0x30, bytes[5] & 0xFF);
    }

    @Test
    void omitsLogoCommandsWhenNoLogoIsConfigured() {
        assertArrayEquals(new byte[0], ReceiptLogoSupport.escPosLogo(null));
    }
}
