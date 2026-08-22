package Receipt;

import managers.CompanyCustomizationManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomOrderSlipFormatterTest {
    @TempDir
    Path tempDirectory;

    @Test
    void fortyColumnSlipDoesNotReserveBlankDetailLines() {
        CustomOrderSlipData data = new CustomOrderSlipData(
                "CO-001", "Alex Customer", "555-0199", "CA-000100",
                LocalDate.of(2026, 5, 30), Timestamp.from(Instant.parse("2026-05-22T12:00:00Z")),
                "Sample Cashier", "Main Store", "POS-01", "CASH", "", "PARTIAL",
                new BigDecimal("87.00"), new BigDecimal("30.00"), new BigDecimal("57.00"),
                "Use royal blue thread.",
                List.of(new CustomOrderSlipData.Line("Logo T-Shirt", "Large / Black",
                        "Front print / 4 lines", "Place logo centered on chest.", new BigDecimal("43.00"))));
        CompanyCustomizationManager.ReceiptSettings receiptSettings =
                new CompanyCustomizationManager.ReceiptSettings("Deckers", "", "", "", "", "", "", "",
                        "", "", "", "Thank you", "", false, true, true, true, true, true, true,
                        false, false, BigDecimal.ZERO, 1, BigDecimal.ZERO, false,
                        CompanyCustomizationManager.AccountPaymentReceiptSettings.defaults());
        CompanyCustomizationManager.CustomOrderSlipSettings slipSettings =
                new CompanyCustomizationManager.CustomOrderSlipSettings(true, false, "CUSTOMER'S ORDER SLIP",
                        "", "", "Order footer", 8, false, true, true, true, true, true, true,
                        true, true, true, true, false, false, false);

        String text = CustomOrderSlipFormatter.format40Column(data, receiptSettings, slipSettings);

        assertTrue(text.contains("Logo T-Shirt / Large / Black $43"));
        assertTrue(text.contains("Notes: Use royal blue thread."));
        assertFalse(text.contains("________________________________________"),
                "40-column slips must not reserve unused writing rows");
    }

    @Test
    void fortyColumnEscPosIncludesConfiguredCompanyLogo() throws Exception {
        Path logoPath = tempDirectory.resolve("company-logo.png");
        BufferedImage logo = new BufferedImage(24, 12, BufferedImage.TYPE_INT_RGB);
        var graphics = logo.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 24, 12);
        graphics.setColor(Color.BLACK);
        graphics.fillRect(3, 3, 18, 6);
        graphics.dispose();
        ImageIO.write(logo, "png", logoPath.toFile());

        CompanyCustomizationManager.ReceiptSettings receiptSettings =
                new CompanyCustomizationManager.ReceiptSettings("Deckers", "", "", "", "", "", "", "",
                        "", "", "", "Thank you", logoPath.toString(), true, true, true, true, true, true, true,
                        false, false, BigDecimal.ZERO, 1, BigDecimal.ZERO, false,
                        CompanyCustomizationManager.AccountPaymentReceiptSettings.defaults());
        CompanyCustomizationManager.CustomOrderSlipSettings slipSettings =
                new CompanyCustomizationManager.CustomOrderSlipSettings(true, false, "ORDER SLIP",
                        "", "", "", 0, true, true, true, true, true, true, true,
                        true, true, true, true, false, false, false);

        byte[] bytes = CustomOrderSlipFormatter.formatEscPos40Column(sampleData(), receiptSettings, slipSettings);

        assertTrue(count(bytes, new byte[]{0x1D, 0x76, 0x30, 0x00}) >= 2,
                "40-column output must include separate ESC/POS raster commands for the logo and order barcode");
    }

    private static CustomOrderSlipData sampleData() {
        return new CustomOrderSlipData("CO-001", "Alex Customer", "", "", null,
                Timestamp.from(Instant.parse("2026-05-22T12:00:00Z")), "Cashier", "Main Store", "POS-01",
                "CASH", "", "PAID", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, "", List.of());
    }

    private static boolean contains(byte[] bytes, byte[] sequence) {
        for (int i = 0; i <= bytes.length - sequence.length; i++) {
            boolean match = true;
            for (int j = 0; j < sequence.length; j++) {
                if (bytes[i + j] != sequence[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return true;
        }
        return false;
    }

    private static int count(byte[] bytes, byte[] sequence) {
        int count = 0;
        for (int i = 0; i <= bytes.length - sequence.length; i++) {
            boolean match = true;
            for (int j = 0; j < sequence.length; j++) {
                if (bytes[i + j] != sequence[j]) {
                    match = false;
                    break;
                }
            }
            if (match) count++;
        }
        return count;
    }
}
