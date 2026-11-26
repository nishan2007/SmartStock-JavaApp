package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OreallaRidersPermitTest {
    @Test void savedPortraitLoadsWithItsEditsAndCanBeReframedForPrinting() throws Exception {
        var source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0x71DC2828);
        var bytes = new java.io.ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);
        var item = new com.google.gson.JsonObject();
        item.addProperty("image", java.util.Base64.getEncoder().encodeToString(bytes.toByteArray()));
        item.addProperty("zoom", 1.4);
        item.addProperty("offsetX", .16);
        item.addProperty("offsetY", -.12);
        item.addProperty("turns", 0);
        item.addProperty("background", 0xffffff);
        var adjustments = new com.google.gson.JsonObject();
        adjustments.addProperty("brightness", 20);
        adjustments.addProperty("saturation", -100);
        item.add("adjustments", adjustments);

        var saved = BadgeCardsPanel.decodeRecordPhoto(item);
        assertEquals(1.4, saved.zoom());
        assertEquals(.16, saved.offsetX());
        assertEquals(-.12, saved.offsetY());
        assertEquals(java.awt.Color.WHITE, saved.background());
        assertEquals(0x71686868, saved.rendered().getRGB(0, 0));
        var print = saved.positioned(1, 0, 0, 0);
        assertSame(saved.source(), print.source());
        assertSame(saved.rendered(), print.rendered());
        assertEquals(1.4, saved.zoom());

        item.remove("adjustments");
        assertEquals(0x71DC2828, BadgeCardsPanel.decodeRecordPhoto(item).rendered().getRGB(0, 0));
    }
    @Test void legacyDesignSplitsTheValuesAndRendersTheSavedNumberOnTheBack(@TempDir Path directory) throws Exception {
        var legacy = new BadgeTemplate();
        legacy.name = OreallaRidersPermit.NAME;
        for (String text : List.of("Synthetic name", "OREALLA/SIPARUTA VILLLAGE", "Synthetic village",
                "EYE COLOR   HEIGHT    SEX   DATE OF BIRTH", "Synthetic combined attributes",
                "ISSUE DATE     EXPIRY DATE", "Synthetic combined dates", "CLASSES",
                "Synthetic classes", "PLACE OF ISSUE", "Synthetic place of issue")) {
            var layer = new BadgeTemplate.Element();
            layer.label = "Text";
            layer.text = text;
            layer.width = 30;
            layer.height = 5;
            legacy.elements.add(layer);
        }
        var qr = new BadgeTemplate.Element();
        qr.label = "QR code";
        qr.type = "IMAGE";
        qr.x = 47;
        qr.y = 14;
        qr.width = qr.height = 32;
        var bytes = new java.io.ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        qr.image = java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
        legacy.backElements.add(qr);

        Path source = directory.resolve("legacy.sstbadge");
        legacy.save(source);
        var permit = BadgeTemplate.load(source);
        assertTrue(permit.savePrintRecords);
        assertEquals(List.of("name", "village", "eyeColor", "height", "sex", "dateOfBirth",
                "issueDate", "expiryDate", "classes", "placeOfIssue"),
                permit.elements.stream().map(layer -> layer.field).filter(field -> !field.isEmpty()).toList());
        var riderFields = permit.elements.stream().filter(layer -> !layer.field.isEmpty()).toList();
        assertTrue(riderFields.stream().allMatch(layer -> layer.bold && OreallaRidersPermit.usesTypography(layer)));
        var place = riderFields.stream().filter(layer -> layer.field.equals("placeOfIssue")).findFirst().orElseThrow();
        var placeCaption = permit.elements.stream().filter(layer -> layer.text.equals("PLACE OF ISSUE")).findFirst().orElseThrow();
        assertEquals(placeCaption.x, place.x);
        assertEquals(publishing.TextAlignment.LEFT, permit.defaultTextAlignment(place));
        assertEquals(publishing.TextAlignment.LEFT, permit.defaultTextAlignment(placeCaption));
        place.textAlignment = publishing.TextAlignment.RIGHT;
        place.x += 3.5;
        double movedPlace = place.x;
        assertTrue(permit.elements.stream().noneMatch(layer -> layer.text.startsWith("Synthetic ")));
        Path upgraded = directory.resolve("permit.sstbadge");
        permit.save(upgraded);
        permit = BadgeTemplate.load(upgraded);
        assertEquals(publishing.TextAlignment.RIGHT, permit.elements.stream().filter(layer -> layer.field.equals("placeOfIssue")).findFirst().orElseThrow().textAlignment);
        assertEquals(movedPlace, permit.elements.stream().filter(layer -> layer.field.equals("placeOfIssue")).findFirst().orElseThrow().x);
        assertFalse(Files.readString(upgraded).contains("OSV-"));
        assertTrue(OreallaRidersPermit.enabled(permit));
        var back = permit.renderSide(true, Map.of("permitNumber", "OSV-0007"), null, 254);
        var cropped = back.getSubimage(470, 140, 320, 320);
        assertEquals("OSV-0007", publishing.PublicationBarcode.decode(cropped).orElseThrow().text());
    }

    @Test void existingPermitsAlignFieldsWithTheirCaptionsAndUseEqualColumns(@TempDir Path directory) throws Exception {
        var original = new BadgeTemplate();
        original.name = OreallaRidersPermit.NAME;
        var qr = new BadgeTemplate.Element();
        qr.type = "QR";
        qr.label = "Permit QR";
        qr.field = OreallaRidersPermit.NUMBER;
        qr.width = qr.height = 12;
        original.backElements.add(qr);
        original.elements.add(text("", "name"));
        var captions = List.of("EYE COLOR", "HEIGHT", "SEX", "DATE OF BIRTH", "ISSUE DATE", "EXPIRY DATE");
        var fields = List.of("eyeColor", "height", "sex", "dateOfBirth", "issueDate", "expiryDate");
        for (int i = 0; i < fields.size(); i++) {
            original.elements.add(text(captions.get(i), ""));
            original.elements.add(text("", fields.get(i)));
        }
        Path design = directory.resolve("already-upgraded.sstbadge");
        original.save(design);
        var permit = BadgeTemplate.load(design);
        var name = permit.elements.get(0);
        assertTrue(name.bold);
        assertTrue(name.width > 45);
        for (int i = 0; i < fields.size(); i++) {
            var caption = permit.elements.get(2 * i + 1);
            var value = permit.elements.get(2 * i + 2);
            assertEquals(caption.x, value.x);
            assertEquals(caption.width, value.width);
            assertEquals(caption.fontMm, value.fontMm);
            assertEquals(permit.elements.get(i < 4 ? 1 : 9).width, value.width);
            assertEquals(permit.elements.get(1).fontMm, value.fontMm);
            assertFalse(caption.bold);
            assertTrue(value.bold);
            assertTrue(value.y >= caption.y + caption.height);
        }
        assertEquals(name.x, permit.elements.get(1).x);
        assertEquals(name.x, permit.elements.get(9).x);
    }

    @Test void textGrowsRightwardAndFitsWithoutClipping() {
        var image = new BufferedImage(300, 50, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            var font = new java.awt.Font("Arial", java.awt.Font.BOLD, 29);
            var shortName = OreallaRidersPermit.fitText(graphics, font, "test", publishing.TextAlignment.LEFT, true, 10, 5, 200, 35).getBounds2D();
            var caps = OreallaRidersPermit.fitText(graphics, font, "TEST", publishing.TextAlignment.LEFT, true, 10, 5, 200, 35).getBounds2D();
            var standardBold = OreallaRidersPermit.fitText(graphics, font, "TEST", publishing.TextAlignment.LEFT, false, 10, 5, 200, 35).getBounds2D();
            var longName = OreallaRidersPermit.fitText(graphics, font, "Test Rider With A Longer Name", publishing.TextAlignment.LEFT, true, 10, 5, 200, 35).getBounds2D();
            assertEquals(caps, shortName);
            assertTrue(caps.getHeight() > standardBold.getHeight());
            assertEquals(shortName.getMinX(), longName.getMinX(), .001);
            assertEquals(shortName.getHeight(), longName.getHeight(), 1.0);
            assertTrue(longName.getMaxX() > shortName.getMaxX());
            assertTrue(longName.getMaxX() <= 209.001);
            var date = OreallaRidersPermit.fitText(graphics, font, "25 Nov 2025", publishing.TextAlignment.LEFT, true, 10, 5, 95, 17).getBounds2D();
            assertTrue(date.getMinX() >= 10 && date.getMaxX() <= 105);
            assertTrue(date.getMinY() >= 5 && date.getMaxY() <= 22);
        } finally {
            graphics.dispose();
        }
    }

    private static BadgeTemplate.Element text(String text, String field) {
        var layer = new BadgeTemplate.Element();
        layer.label = field.isEmpty() ? text : field;
        layer.text = text;
        layer.field = field;
        layer.width = 20;
        layer.height = 5;
        return layer;
    }
}
