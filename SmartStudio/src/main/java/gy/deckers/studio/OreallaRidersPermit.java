package gy.deckers.studio;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.util.List;
import java.util.Locale;
import publishing.TextAlignment;

/** Converts the existing two-sided Orealla design while retaining its artwork. */
final class OreallaRidersPermit {
    static final String NAME = "Orealla Riders Permit";
    static final String NUMBER = "permitNumber";
    private static final List<String> ATTRIBUTES = List.of("EYE COLOR", "HEIGHT", "SEX", "DATE OF BIRTH");
    private static final List<String> ATTRIBUTE_FIELDS = List.of("eyeColor", "height", "sex", "dateOfBirth");
    private static final List<String> DATES = List.of("ISSUE DATE", "EXPIRY DATE");
    private static final List<String> DATE_FIELDS = List.of("issueDate", "expiryDate");
    private static final List<String> OTHER_FIELDS = List.of("village", "classes");

    private OreallaRidersPermit() {}

    static boolean enabled(BadgeTemplate template) {
        return template.backElements.stream().anyMatch(e -> "QR".equals(e.type) && NUMBER.equals(e.field));
    }

    static boolean usesFrontLayout(BadgeTemplate template) {
        return NAME.equalsIgnoreCase(template.name) && enabled(template);
    }

    static void upgrade(BadgeTemplate template) {
        if (!NAME.equalsIgnoreCase(template.name) || enabled(template)) return;

        var front = template.elements;
        var heading = text(front, "OREALLA/SIPARUTA VILLLAGE");
        if (heading == null) heading = text(front, "OREALLA/SIPARUTA VILLAGE");
        var name = relativeText(front, heading, -1);
        var village = relativeText(front, heading, 1);
        var place = relativeText(front, text(front, "PLACE OF ISSUE"), 1);
        var attributes = text(front, "EYE COLOR HEIGHT SEX DATE OF BIRTH");
        var attributeValues = relativeText(front, attributes, 1);
        var dates = text(front, "ISSUE DATE EXPIRY DATE");
        var dateValues = relativeText(front, dates, 1);
        var classes = relativeText(front, text(front, "CLASSES"), 1);
        var qr = template.backElements.stream().filter(e -> "IMAGE".equals(e.type)
                && "QR code".equalsIgnoreCase(e.label)).findFirst().orElse(null);
        if (name == null || village == null || place == null || attributes == null || attributeValues == null
                || dates == null || dateValues == null || classes == null || qr == null || front.size() > 56) return;

        bind(name, "Name", "name");
        bind(village, "Village", "village");
        bind(classes, "Classes", "classes");
        bind(place, "Place of issue", "placeOfIssue");
        replace(template, attributes, 1.85, false, "", "EYE COLOR", "HEIGHT", "SEX", "DATE OF BIRTH");
        replace(template, attributeValues, 1.68, true, "eyeColor height sex dateOfBirth",
                "Eye color", "Height", "Sex", "Date of birth");
        replace(template, dates, 2.12, false, "", "ISSUE DATE", "EXPIRY DATE");
        replace(template, dateValues, 2.25, true, "issueDate expiryDate", "Issue date", "Expiry date");
        qr.type = "QR";
        qr.image = "";
        bind(qr, "Permit number QR", NUMBER);
        template.backElements.remove(qr);
        template.backElements.add(qr);
        template.savePrintRecords = true;
        template.validate();
    }

    static void layoutFront(BadgeTemplate template) {
        if (!usesFrontLayout(template)) return;
        for (var layer : template.elements) {
            if (!usesTypography(layer)) continue;
            // Once written with an explicit alignment, a layer keeps its edited position on reload.
            boolean placed = usesLeftAlignment(layer);
            if (placed && layer.textAlignment != null) continue;
            if (placed) layer.textAlignment = TextAlignment.LEFT;
            if (isPlaceOfIssue(layer)) {
                layer.x = 2.5 * template.widthMm / 85.6;
                if (layer.field.isEmpty()) continue;
            }
            if ("name".equals(layer.field)) {
                position(template, layer, 0, 1, 11.85, 5.91, 2.65);
            } else if (placed && !isPlaceOfIssue(layer)) {
                boolean date = DATE_FIELDS.contains(layer.field) || DATES.contains(layer.text);
                var captions = date ? DATES : ATTRIBUTES;
                var fields = date ? DATE_FIELDS : ATTRIBUTE_FIELDS;
                boolean value = !layer.field.isEmpty();
                int column = value ? fields.indexOf(layer.field) : captions.indexOf(layer.text);
                double y = (date ? 29.65 : 21.25) + (value ? 2.7 : 0);
                position(template, layer, column, captions.size(), y, 2.7, 1.85);
            }
            layer.font = "Arial";
            layer.bold = !layer.field.isEmpty();
        }
    }

    private static void position(BadgeTemplate template, BadgeTemplate.Element layer, int column,
                                 int columns, double y, double height, double fontMm) {
        double widthScale = template.widthMm / 85.6;
        double heightScale = template.heightMm / 54;
        double columnWidth = columns == 1 ? 50.0 : 47.5 / columns;
        double gutter = columns == 1 ? .5 : 1.25;
        layer.x = (2.5 + column * columnWidth) * widthScale;
        layer.width = (columnWidth - gutter) * widthScale;
        layer.y = y * heightScale;
        layer.height = height * heightScale;
        layer.fontMm = fontMm * heightScale;
    }

    static boolean usesTypography(BadgeTemplate.Element layer) {
        return "TEXT".equals(layer.type) && (usesLeftAlignment(layer) || OTHER_FIELDS.contains(layer.field));
    }

    static boolean usesLeftAlignment(BadgeTemplate.Element layer) {
        return "name".equals(layer.field) || isPlaceOfIssue(layer) || ATTRIBUTE_FIELDS.contains(layer.field) || DATE_FIELDS.contains(layer.field)
                || (layer.field.isEmpty() && (ATTRIBUTES.contains(layer.text) || DATES.contains(layer.text)));
    }

    private static boolean isPlaceOfIssue(BadgeTemplate.Element layer) {
        return "placeOfIssue".equals(layer.field) || (layer.field.isEmpty() && "PLACE OF ISSUE".equals(layer.text));
    }

    static Shape fitText(Graphics2D graphics, Font font, String text, TextAlignment alignment, boolean riderValue,
                         int x, int y, int width, int height) {
        var outline = font.createGlyphVector(graphics.getFontRenderContext(), text.toUpperCase(Locale.ROOT)).getOutline();
        if (riderValue) {
            // Thicken the filled letters before fitting so their edges stay inside the text box.
            var weight = new BasicStroke(font.getSize2D() * .065f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            var heavier = new Area(outline);
            heavier.add(new Area(weight.createStrokedShape(outline)));
            outline = heavier;
        }
        var ink = outline.getBounds2D();
        if (ink.isEmpty()) return outline;

        // Fit the visible glyphs, including their edges, inside the card's text box.
        double inset = Math.min(1, Math.min(width, height) / 4.0);
        double scaleX = Math.min(1, (width - 2 * inset) / ink.getWidth());
        double scaleY = Math.min(1, (height - 2 * inset) / ink.getHeight());
        var metrics = graphics.getFontMetrics(font);
        double baseline = y + (height - metrics.getHeight()) / 2.0 + metrics.getAscent();
        baseline = Math.max(y + inset - ink.getMinY() * scaleY,
                Math.min(baseline, y + height - inset - ink.getMaxY() * scaleY));
        double left = x + inset + alignment.offset(width - 2 * inset, ink.getWidth() * scaleX);
        var placement = AffineTransform.getTranslateInstance(left - ink.getMinX() * scaleX, baseline);
        placement.scale(scaleX, scaleY);
        return placement.createTransformedShape(outline);
    }

    private static BadgeTemplate.Element text(List<BadgeTemplate.Element> layers, String value) {
        return layers.stream().filter(e -> "TEXT".equals(e.type) && value.equals(e.text.strip().replaceAll("\\s+", " ")))
                .findFirst().orElse(null);
    }

    private static BadgeTemplate.Element relativeText(List<BadgeTemplate.Element> layers, BadgeTemplate.Element anchor, int distance) {
        if (anchor == null) return null;
        int position = layers.indexOf(anchor) + distance;
        if (position < 0 || position >= layers.size()) return null;
        var layer = layers.get(position);
        return "TEXT".equals(layer.type) ? layer : null;
    }

    private static void bind(BadgeTemplate.Element layer, String label, String field) {
        layer.label = label;
        layer.field = field;
        layer.text = "";
    }

    private static void replace(BadgeTemplate template, BadgeTemplate.Element source, double fontMm,
                                boolean fields, String keys, String... labels) {
        // The two date columns sit under their original captions.
        double[] starts = labels.length == 4 ? new double[]{2.8, 17.6, 27.5, 33, 52}
                : new double[]{2.5, 17.5, 25, 44};
        String[] bindings = keys.split(" ");
        int position = template.elements.indexOf(source);
        template.elements.remove(position);
        for (int i = 0; i < labels.length; i++) {
            var layer = new BadgeTemplate.Element();
            layer.label = labels[i];
            layer.text = fields ? "" : labels[i];
            layer.field = fields ? bindings[i] : "";
            int column = labels.length == 4 ? i : i * 2;
            layer.x = starts[column] * template.widthMm / 85.6;
            layer.width = (starts[column + 1] - starts[column]) * template.widthMm / 85.6;
            layer.y = source.y;
            layer.height = source.height;
            layer.font = source.font;
            layer.fontMm = fontMm;
            layer.bold = source.bold;
            layer.color = source.color;
            template.elements.add(position + i, layer);
        }
    }
}
