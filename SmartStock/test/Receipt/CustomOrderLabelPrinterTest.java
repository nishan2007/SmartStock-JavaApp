package Receipt;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomOrderLabelPrinterTest {
    @Test
    void rendersTwoByOneLabelAndEncodesExactOrderNumber() throws Exception {
        CustomOrderSlipData data = sample("CO-2026-00421", "A Customer With A Long Name That Must Fit Safely", LocalDate.of(2026, 8, 14));

        BufferedImage label = CustomOrderLabelPrinter.render(data);

        assertEquals(600, label.getWidth());
        assertEquals(300, label.getHeight());
        BufferedImage barcodeArea = label.getSubimage(20, 62, 560, 120);
        int[] pixels = barcodeArea.getRGB(0, 0, barcodeArea.getWidth(), barcodeArea.getHeight(), null, 0, barcodeArea.getWidth());
        Result decoded = new MultiFormatReader().decode(new BinaryBitmap(
                new HybridBinarizer(new RGBLuminanceSource(barcodeArea.getWidth(), barcodeArea.getHeight(), pixels))));
        assertEquals("CO-2026-00421", decoded.getText());
    }

    @Test
    void rendersWithoutDueDate() {
        BufferedImage label = CustomOrderLabelPrinter.render(sample("CO-9", "Customer", null));
        assertEquals(BufferedImage.TYPE_INT_RGB, label.getType());
    }

    @Test
    void validatesPositiveWholeLabelCountWithinSafetyLimit() {
        assertEquals(1, CustomOrderLabelPrinter.parseLabelCount("1"));
        assertEquals(100, CustomOrderLabelPrinter.parseLabelCount(" 100 "));
        for (String invalid : List.of("", "0", "-1", "1.5", "101", "abc")) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> CustomOrderLabelPrinter.parseLabelCount(invalid));
            assertTrue(failure.getMessage().contains("1 to 100"));
        }
    }

    @Test
    void rejectsMissingOrderNumber() {
        assertThrows(IllegalArgumentException.class,
                () -> CustomOrderLabelPrinter.render(sample(" ", "Customer", LocalDate.now())));
    }

    @Test
    void receiptFallbackFormatsEveryLabelWithItsOwnCut() {
        byte[] bytes = CustomOrderLabelPrinter.formatEscPosReceiptLabels(
                sample("CO-12", "Customer", LocalDate.of(2026, 8, 21)), 3);

        assertEquals(3, occurrences(bytes, new byte[]{0x1D, 0x76, 0x30, 0x00}));
        assertEquals(3, occurrences(bytes, new byte[]{0x1D, 0x56, 0x42, 0x00}));
    }

    private static int occurrences(byte[] content, byte[] needle) {
        int count = 0;
        for (int i = 0; i <= content.length - needle.length; i++) {
            boolean match = true;
            for (int j = 0; j < needle.length; j++) {
                if (content[i + j] != needle[j]) {
                    match = false;
                    break;
                }
            }
            if (match) count++;
        }
        return count;
    }

    private static CustomOrderSlipData sample(String orderNumber, String customer, LocalDate dueDate) {
        return new CustomOrderSlipData(orderNumber, customer, "", "", dueDate,
                Timestamp.valueOf("2026-08-03 12:00:00"), "Cashier", "Store", "Register",
                "CASH", "", "PAID", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                "", List.of());
    }
}
