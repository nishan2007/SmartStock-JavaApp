package services;

import managers.ServerCompanyCustomizationRepository;
import managers.ServerCompanyCustomizationRepository.PriceTagTemplateSettings;
import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.List;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class PriceTagStockLayoutTest {
    @Test void portraitBarcodeIsCompleteAndDecodableAfterRotation() throws Exception {
        var settings=PriceTagPrintService.ct221bPortraitTemplate(10/25.4);
        var image=PriceTagPrintService.render(new PriceTagPrintService.PriceTagItem("Test", "", "", "061-0001", "061-0001", BigDecimal.valueOf(100)),settings);
        BufferedImage rotated=new BufferedImage(image.getHeight(),image.getWidth(),BufferedImage.TYPE_INT_RGB);
        var g=rotated.createGraphics();
        try {g.translate(0,image.getWidth());g.rotate(-Math.PI/2);g.drawImage(image,0,0,null);}finally{g.dispose();}
        int[] pixels=rotated.getRGB(0,0,rotated.getWidth(),rotated.getHeight(),null,0,rotated.getWidth());
        var bitmap=new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(
                new com.google.zxing.RGBLuminanceSource(rotated.getWidth(),rotated.getHeight(),pixels)));
        assertEquals("061-0001",new com.google.zxing.MultiFormatReader().decode(bitmap).getText());
        assertEquals(90,PriceTagPrintService.elementRotation(settings.layoutData(),"barcode"));
        assertEquals(0,PriceTagPrintService.elementRotation("barcode:1,2,3,4","barcode"));
    }

    @Test void oversizedBarcodeIsRejectedInsteadOfCropped() {
        assertThrows(IllegalArgumentException.class,()->PriceTagPrintService.barcodeImage("061-0001-TOO-LONG",20,40));
    }

    @Test void tallNarrowBarcodeBoxAutomaticallyUsesItsLongAxis() {
        var settings = new PriceTagTemplateSettings("Half inch by one inch", false, true, true, false, false,
                true, false, .5, 1, "barcode:80,150,840,310,0", 2, 0, 0);
        var item = new PriceTagPrintService.PriceTagItem("Impedance protected", "", "", "10FR-0001",
                "10FR-0001", BigDecimal.valueOf(190));
        assertEquals(90, PriceTagPrintService.barcodeRotationThatFits("10FR-0001",
                new java.awt.Rectangle(8, 61, 86, 126), 0));
        assertDoesNotThrow(() -> PriceTagPrintService.render(item, settings));
    }

    @Test void printingKeepsPhysicalSizeOnLargerDriverPage() throws Exception {
        BufferedImage output=new BufferedImage(200,200,BufferedImage.TYPE_INT_RGB);
        var g=output.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,200,200);
        var paper=new java.awt.print.Paper();paper.setSize(200,200);paper.setImageableArea(10,10,180,180);
        var page=new java.awt.print.PageFormat();page.setPaper(paper);
        try {assertEquals(java.awt.print.Printable.PAGE_EXISTS,new PriceTagPrintService.TagsPrintable(List.of(solid(Color.BLACK)),72,100).print(g,page,0));}finally{g.dispose();}
        assertEquals(Color.BLACK.getRGB(),output.getRGB(20,20));
        assertEquals(Color.WHITE.getRGB(),output.getRGB(80,20));
        assertEquals(Color.WHITE.getRGB(),output.getRGB(20,110));
    }

    @Test void rowGapAddsFeedSpaceWithoutResizingIndividualLabels() {
        var settings = new PriceTagTemplateSettings("Row gap", false, true, true, false, false,
                false, false, .5, 1, "", 2, 0, .1);
        var rows = PriceTagPrintService.arrangeRows(List.of(solid(Color.BLACK), solid(Color.RED)), settings);
        assertEquals(1.1, settings.rowPitchInches(), .0001);
        assertEquals(223, rows.get(0).getHeight());
        assertEquals(Color.BLACK.getRGB(), rows.get(0).getRGB(50, 202));
        assertEquals(Color.RED.getRGB(), rows.get(0).getRGB(160, 202));
        assertEquals(Color.WHITE.getRGB(), rows.get(0).getRGB(50, 213));
    }

    @Test void ct221bJobCalibratesTheTransmissiveGapHoleBeforePrinting() {
        var settings = PriceTagPrintService.ct221bPortraitTemplate(10 / 25.4);
        byte[] job = PriceTagPrintService.formatCt221bGapJob(List.of(solid(Color.BLACK), solid(Color.WHITE)), settings);
        String commands = new String(job, StandardCharsets.ISO_8859_1);
        assertTrue(commands.startsWith("SIZE 25.400 mm,25.400 mm\r\nGAP 10.000 mm,0 mm\r\nGAPDETECT 203,80\r\n"));
        assertEquals(2, commands.split("PRINT 1,1", -1).length - 1);
        assertTrue(commands.contains("BITMAP 0,0,14,220,0,"));
    }

    @Test void ct221bRecognitionAllowsDriverPunctuationAndSpacing() {
        assertTrue(PriceTagPrintService.isCt221b("Clabel- CT221B"));
        assertTrue(PriceTagPrintService.isCt221b("Label Printer (CT 221B)"));
        assertFalse(PriceTagPrintService.isCt221b("Receipt Printer"));
    }

    @Test void priceTagWholeAmountsNeverUseThousandsOrDecimalSeparators() {
        assertEquals("$18000", PriceTagPrintService.formatPriceTagPrice(BigDecimal.valueOf(18000), java.util.Locale.US));
        String german = PriceTagPrintService.formatPriceTagPrice(BigDecimal.valueOf(18000), java.util.Locale.GERMANY);
        assertFalse(german.contains("18.000"));
        assertFalse(german.contains(",00"));
        assertTrue(german.contains("18000"));
    }

    private PriceTagTemplateSettings stock(double gap) {
        return new PriceTagTemplateSettings("Two across", false, true, true, false, false,
                false, false, .5, 1, "", 2, gap);
    }

    @Test void halfInchLabelRendersWithCorrectAspectRatio() {
        var image = PriceTagPrintService.render(new PriceTagPrintService.PriceTagItem(
                "Item", "", "", "", "", BigDecimal.ONE), stock(0));
        assertEquals(102, image.getWidth());
        assertEquals(203, image.getHeight());
        assertEquals(1, stock(0).rowWidthInches());
    }

    @Test void packsRowsInOrderWithGapAndBlankOddPosition() {
        BufferedImage black = solid(Color.BLACK), red = solid(Color.RED), blue = solid(Color.BLUE);
        var rows = PriceTagPrintService.arrangeRows(List.of(black, red, blue), stock(.1));
        assertEquals(2, rows.size());
        assertEquals(224, rows.get(0).getWidth());
        assertEquals(Color.BLACK.getRGB(), rows.get(0).getRGB(50, 50));
        assertEquals(Color.WHITE.getRGB(), rows.get(0).getRGB(120, 50));
        assertEquals(Color.RED.getRGB(), rows.get(0).getRGB(180, 50));
        assertEquals(Color.BLUE.getRGB(), rows.get(1).getRGB(50, 50));
        assertEquals(Color.WHITE.getRGB(), rows.get(1).getRGB(180, 50));
    }

    @Test void stockSettingsSurviveStoredAndLanEncodingAndLegacyDefaults() throws Exception {
        var encoder = ServerCompanyCustomizationRepository.class.getDeclaredMethod("encodePriceTagTemplates", List.class);
        encoder.setAccessible(true);
        var withRowGap = new PriceTagTemplateSettings("Two across", false, true, true, false, false,
                false, false, .5, 1, "", 2, .1, .125);
        String encoded = (String) encoder.invoke(null, List.of(withRowGap));
        var decoded = ServerCompanyCustomizationRepository.decodePriceTagTemplatesForLan(encoded, true, true, true, 2.25, 1.25).get(0);
        assertEquals(withRowGap, decoded);
        String previous = encoded.split(";")[0];
        previous = previous.substring(0, previous.lastIndexOf('|'));
        assertEquals(0, ServerCompanyCustomizationRepository.decodePriceTagTemplatesForLan(previous,
                true, true, true, 2.25, 1.25).get(0).rowGapInches());
        String legacy = encoded.split(";")[0].substring(0, encoded.split(";")[0].lastIndexOf("|2|"));
        var old = ServerCompanyCustomizationRepository.decodePriceTagTemplatesForLan(legacy, true, true, true, 2.25, 1.25).get(0);
        assertEquals(1, old.labelsAcross());
        assertEquals(0, old.columnGapInches());
        assertEquals(0, old.rowGapInches());
    }

    private BufferedImage solid(Color color) {
        BufferedImage image = new BufferedImage(110, 220, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { g.setColor(color); g.fillRect(0, 0, 110, 220); } finally { g.dispose(); }
        return image;
    }
}
