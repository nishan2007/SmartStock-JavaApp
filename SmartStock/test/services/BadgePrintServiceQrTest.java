package services;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import managers.CompanyCustomizationManager;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadgePrintServiceQrTest {
    @Test
    void frontLoginQrIsHiddenByDefaultAndCanBeEnabledPerTemplate() {
        CompanyCustomizationManager.BadgeTemplateSettings settings = settings("");
        assertFalse(BadgePrintService.elementVisible(settings, "front.qr"));

        String layout = BadgePrintService.updateElementVisible(settings.layoutData(), "front.qr", true);
        assertTrue(BadgePrintService.elementVisible(settings(layout), "front.qr"));
    }

    @Test
    void qrEncodesTheNormalizedRevocableBadgeCredential() throws Exception {
        String credential = "SSB1XXWYDG1NZ59MT10P";
        BufferedImage image = BadgePrintService.renderQrCode(credential, 420);
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        String decoded = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)))).getText();
        assertEquals(credential, decoded);
    }

    @Test
    void qrRemainsDecodableWithCompanyLogoInCenter() throws Exception {
        String credential = "SSB1XXWYDG1NZ59MT10P";
        BufferedImage logo = new BufferedImage(240, 100, BufferedImage.TYPE_INT_RGB);
        var graphics = logo.createGraphics();
        graphics.setColor(new Color(0, 55, 96));
        graphics.fillRect(0, 0, logo.getWidth(), logo.getHeight());
        graphics.setColor(Color.WHITE);
        graphics.fillOval(85, 15, 70, 70);
        graphics.dispose();

        BufferedImage image = BadgePrintService.overlayQrLogo(
                BadgePrintService.renderQrCode(credential, 600), logo);
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        String decoded = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)))).getText();
        assertEquals(credential, decoded);
    }

    private static CompanyCustomizationManager.BadgeTemplateSettings settings(String layout) {
        return new CompanyCustomizationManager.BadgeTemplateSettings(
                "SmartStock", "", "Quote", "Signer", "Manager", "Instructions",
                true, true, true, true, false,
                false, "{badge_id}", "{badge_id}", "", "",
                false, "{badge_id}", "", "", layout
        );
    }
}
