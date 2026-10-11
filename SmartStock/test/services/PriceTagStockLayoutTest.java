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
    @Test void tsplBitmapUsesBlackZeroAndWhiteOneIncludingPadding() {
        BufferedImage image=new BufferedImage(9,3,BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();try{graphics.setColor(Color.WHITE);graphics.fillRect(0,0,9,3);graphics.setColor(Color.BLACK);graphics.fillRect(0,1,9,1);graphics.fillRect(0,2,1,1);}finally{graphics.dispose();}
        byte[] job=PriceTagPrintService.formatCt221bGapJob(List.of(image),stock(0));
        byte[] header="BITMAP 0,0,2,3,0,".getBytes(StandardCharsets.US_ASCII);
        int offset=-1;for(int i=0;i<=job.length-header.length;i++){boolean match=true;for(int j=0;j<header.length;j++)if(job[i+j]!=header[j]){match=false;break;}if(match){offset=i+header.length;break;}}
        assertTrue(offset>=0);
        assertArrayEquals(new byte[]{(byte)0xFF,(byte)0xFF,0,0x7F,0x7F,(byte)0xFF},java.util.Arrays.copyOfRange(job,offset,offset+6));
    }
    @Test void variantNameKeepsItsExplicitSecondLine() {
        var image=new BufferedImage(600,100,BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();
        try {
            var fit=PriceTagPrintService.fitText(g,"Main Custom Item\nLarge Blue",new java.awt.Rectangle(0,0,600,100),java.awt.Font.BOLD,true);
            assertEquals(List.of("Main Custom Item","Large Blue"),fit.lines());
            assertTrue(fit.fontSize()>8);
        } finally {g.dispose();}
    }
    @Test void brandSelectionSurvivesLayoutAndCanBeDisabled() {
        String layout=PriceTagPrintService.withBrand(PriceTagPrintService.encodeLayout(PriceTagPrintService.defaultLayout()),true);
        assertTrue(PriceTagPrintService.brandVisible(layout));
        assertTrue(PriceTagPrintService.layoutRects(layout).containsKey("brand"));
        assertFalse(PriceTagPrintService.brandVisible(PriceTagPrintService.withBrand(layout,false)));
        assertFalse(PriceTagPrintService.brandVisible(""));
    }
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
        var rawRows = PriceTagPrintService.arrangeRawRows(List.of(solid(Color.BLACK), solid(Color.WHITE)), settings);
        assertEquals(203, rawRows.get(0).getHeight(), "Raw TSPL bitmap must fit inside the declared one-inch label");
        byte[] job = PriceTagPrintService.formatCt221bGapJob(rawRows, settings);
        String commands = new String(job, StandardCharsets.ISO_8859_1);
        assertTrue(commands.startsWith("SIZE 25.400 mm,25.400 mm\r\nGAP 10.000 mm,0 mm\r\nGAPDETECT 203,80\r\n"));
        assertEquals(1, commands.split("PRINT 1,1", -1).length - 1);
        assertTrue(commands.contains("BITMAP 0,0,26,203,0,"));
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

    @Test void longUnbrokenTextUsesAvailableLinesBeforeShrinkingToMinimumFont() {
        BufferedImage image=new BufferedImage(240,120,BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();
        try {
            var fit=PriceTagPrintService.fitText(graphics,"EXTRALONGVARIANTIDENTIFIERWITHOUTSPACES",
                    new java.awt.Rectangle(0,0,180,90),java.awt.Font.BOLD,true);
            assertTrue(fit.lines().size()>1,"Long text should wrap even when it contains no spaces");
            assertTrue(fit.fontSize()>8,"The renderer should use the second line before choosing the minimum font");
            assertEquals("EXTRALONGVARIANTIDENTIFIERWITHOUTSPACES",String.join("",fit.lines()));
        } finally { graphics.dispose(); }
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

    @Test void shirtsTemplateSurvivesLegacySettingsAndSaving() throws Exception {
        var encoder = ServerCompanyCustomizationRepository.class.getDeclaredMethod("encodePriceTagTemplates", List.class);
        encoder.setAccessible(true);
        var defaults = ServerCompanyCustomizationRepository.decodePriceTagTemplatesForLan("", true, true, true, 2.25, 1.25);
        String encoded = (String) encoder.invoke(null, defaults);
        String oldFive = String.join(";", java.util.Arrays.copyOf(encoded.split(";"), 5));
        var restored = ServerCompanyCustomizationRepository.decodePriceTagTemplatesForLan(oldFive, true, true, true, 2.25, 1.25);
        assertEquals(6, restored.size());
        var shirts = restored.get(5);
        assertEquals("Shirts", shirts.name());
        assertEquals(2, shirts.widthInches());
        assertEquals(1, shirts.heightInches());
        assertEquals("Cotton Shirt", PriceTagPrintService.nameForTemplate("Cotton Shirt\nXL / Blue", shirts.layoutData()));
        assertEquals("Cotton Shirt\nXL / Blue", PriceTagPrintService.nameForTemplate("Cotton Shirt\nXL / Blue", ""));
        assertTrue(shirts.showSize() && shirts.showPrice() && shirts.showBarcode() && shirts.showSku());
        var boxes = PriceTagPrintService.layoutRects(shirts.layoutData());
        assertTrue(boxes.get("size").height > boxes.get("name").height);
        assertTrue(boxes.get("color").height > boxes.get("name").height);
        for (var entry : boxes.entrySet()) {
            if (!PriceTagPrintService.elementVisible(shirts, entry.getKey())) continue;
            var box = entry.getValue();
            assertTrue(box.x >= 50 && box.y >= 50, entry.getKey() + " must respect the left/top 0.1-inch margins");
            assertTrue(box.x + box.width <= 950 && box.y + box.height <= 450,
                    entry.getKey() + " must respect the right/bottom 0.1-inch margins");
        }
        assertEquals(shirts, ServerCompanyCustomizationRepository.decodePriceTagTemplatesForLan((String) encoder.invoke(null, restored), true, true, true, 2.25, 1.25).get(5));
        String edited = PriceTagPrintService.withMainNameOnly(PriceTagPrintService.encodeLayout(boxes), true);
        assertEquals("Cotton Shirt", PriceTagPrintService.nameForTemplate("Cotton Shirt\nXL / Blue", edited));
        var image = PriceTagPrintService.render(new PriceTagPrintService.PriceTagItem("Cotton Shirt\nXL / Blue", "XL", "Blue", "", "SHIRT-XL-BL", "SHIRT-XL-BL", BigDecimal.TEN), shirts);
        assertEquals(406, image.getWidth());
        assertEquals(203, image.getHeight());
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (x < 20 || x >= image.getWidth() - 20 || y < 20 || y >= image.getHeight() - 20)
                    assertEquals(Color.WHITE.getRGB(), image.getRGB(x, y), "Margin must remain blank");
            }
        }
    }

    private BufferedImage solid(Color color) {
        BufferedImage image = new BufferedImage(110, 220, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { g.setColor(color); g.fillRect(0, 0, 110, 220); } finally { g.dispose(); }
        return image;
    }
}
