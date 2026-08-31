package services;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceTagReceiptPrinterTest {
    @Test
    void formatsEachTemporaryTagAsCenteredRasterWithItsOwnCut() {
        BufferedImage first = image(Color.BLACK);
        BufferedImage second = image(Color.WHITE);

        byte[] job = PriceTagPrintService.formatReceiptPrinterJob(List.of(first, second));

        assertEquals(2, occurrences(job, new byte[]{0x1B, 0x40, 0x1B, 0x61, 0x01}));
        assertEquals(2, occurrences(job, new byte[]{0x1D, 0x76, 0x30, 0x00}));
        assertEquals(2, occurrences(job, new byte[]{0x1D, 0x56, 0x42, 0x00}));
        assertTrue(indexOf(job, new byte[]{(byte) 0xFF}) > indexOf(job, new byte[]{0x1D, 0x76, 0x30, 0x00}));
    }

    private static BufferedImage image(Color color) {
        BufferedImage image = new BufferedImage(8, 2, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        return image;
    }

    private static int occurrences(byte[] data, byte[] pattern) {
        int count = 0;
        for (int from = 0; from <= data.length - pattern.length;) {
            int found = indexOf(data, pattern, from);
            if (found < 0) break;
            count++;
            from = found + pattern.length;
        }
        return count;
    }

    private static int indexOf(byte[] data, byte[] pattern) { return indexOf(data, pattern, 0); }

    private static int indexOf(byte[] data, byte[] pattern, int from) {
        outer:
        for (int i = from; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) if (data[i + j] != pattern[j]) continue outer;
            return i;
        }
        return -1;
    }
}
