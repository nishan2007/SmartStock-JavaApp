package Receipt;

import managers.CompanyCustomizationManager;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomOrderSlipRendererTest {
    @Test
    void letterSlipPaintsProvidedCompanyLogo() {
        BufferedImage page = new BufferedImage(700, 400, BufferedImage.TYPE_INT_RGB);
        var pageGraphics = page.createGraphics();
        pageGraphics.setColor(Color.WHITE);
        pageGraphics.fillRect(0, 0, page.getWidth(), page.getHeight());

        BufferedImage logo = new BufferedImage(80, 30, BufferedImage.TYPE_INT_RGB);
        var logoGraphics = logo.createGraphics();
        logoGraphics.setColor(Color.RED);
        logoGraphics.fillRect(0, 0, logo.getWidth(), logo.getHeight());
        logoGraphics.dispose();

        CustomOrderSlipRenderer.paintSlip(pageGraphics, 0, 0, 700, 390, sampleData(), receiptSettings(), slipSettings(), logo);
        pageGraphics.dispose();

        boolean foundLogoPixel = false;
        for (int y = 10; y < 90 && !foundLogoPixel; y++) {
            for (int x = 10; x < 180; x++) {
                Color pixel = new Color(page.getRGB(x, y));
                if (pixel.getRed() > 200 && pixel.getGreen() < 50 && pixel.getBlue() < 50) {
                    foundLogoPixel = true;
                    break;
                }
            }
        }
        assertTrue(foundLogoPixel, "Letter slips must paint the supplied company logo");
    }

    private static CompanyCustomizationManager.ReceiptSettings receiptSettings() {
        return new CompanyCustomizationManager.ReceiptSettings("Deckers", "", "", "", "", "", "", "",
                "", "", "", "Thank you", "logo", true, true, true, true, true, true, true,
                false, false, BigDecimal.ZERO, 1, BigDecimal.ZERO, false,
                CompanyCustomizationManager.AccountPaymentReceiptSettings.defaults());
    }

    private static CompanyCustomizationManager.CustomOrderSlipSettings slipSettings() {
        return new CompanyCustomizationManager.CustomOrderSlipSettings(true, false, "ORDER SLIP", "", "", "",
                0, true, true, true, true, true, true, true, true, true, true, true, false, false, false);
    }

    private static CustomOrderSlipData sampleData() {
        return new CustomOrderSlipData("CO-001", "Customer", "", "", null,
                Timestamp.from(Instant.parse("2026-05-22T12:00:00Z")), "Cashier", "Main Store", "POS-01",
                "CASH", "", "PAID", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, "", List.of());
    }
}
