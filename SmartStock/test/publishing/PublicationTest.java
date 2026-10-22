package publishing;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublicationTest {
  @TempDir Path dir;

  @Test
  void unitsAndResizePreservePhysicalDimensions() {
    assertEquals(25.4, Publication.toMm(1, "IN"), 1e-9);
    assertEquals(10, Publication.toMm(1, "CM"));
    assertEquals(1, Publication.fromMm(25.4, "IN"), 1e-9);
    Publication p = Publication.preset("Card");
    var e = p.pages.get(0).objects.get(0);
    double width = e.width;
    p.resize(180, 100, false);
    assertEquals(width, e.width);
    p.resize(360, 200, true);
    assertEquals(width * 2, e.width);
    assertEquals(24, e.fontSize);
  }

  @Test
  void mixedPackingBeatsBothUniformOrientations() {
    var s = new SheetLayout.Settings();
    s.width = 100;
    s.height = 100;
    s.left = s.right = s.top = s.bottom = s.gapX = s.gapY = 0;
    var mixed = SheetLayout.pack(60, 40, s);
    s.maximize = false;
    int upright = SheetLayout.pack(60, 40, s).capacity(),
        rotated = SheetLayout.pack(40, 60, s).capacity();
    assertTrue(mixed.capacity() > Math.max(upright, rotated));
    assertEquals(3, mixed.capacity());
    assertTrue(mixed.placements().stream().anyMatch(SheetLayout.Placement::rotated));
  }

  @Test
  void bleedGapsAndMarginsAreRespectedAcrossManySizes() {
    Random random = new Random(61);
    for (int n = 0; n < 100; n++) {
      var s = new SheetLayout.Settings();
      s.bleed = random.nextDouble() * 2;
      s.gapX = 1 + random.nextDouble() * 4;
      s.gapY = 1 + random.nextDouble() * 4;
      double w = 15 + random.nextDouble() * 80, h = 15 + random.nextDouble() * 80;
      var result = SheetLayout.pack(w, h, s);
      for (int i = 0; i < result.capacity(); i++) {
        var a = result.placements().get(i);
        assertTrue(a.x() - s.bleed >= s.left - 1e-7);
        assertTrue(a.y() - s.bleed >= s.top - 1e-7);
        assertTrue(a.x() + a.width() + s.bleed <= s.width - s.right + 1e-7);
        assertTrue(a.y() + a.height() + s.bleed <= s.height - s.bottom + 1e-7);
        assertEquals(a.rotated() ? h : w, a.width(), 1e-7);
        for (int j = i + 1; j < result.capacity(); j++) {
          var b = result.placements().get(j);
          assertTrue(
              a.x() + a.width() + 2 * s.bleed + s.gapX <= b.x() + 1e-7
                  || b.x() + b.width() + 2 * s.bleed + s.gapX <= a.x() + 1e-7
                  || a.y() + a.height() + 2 * s.bleed + s.gapY <= b.y() + 1e-7
                  || b.y() + b.height() + 2 * s.bleed + s.gapY <= a.y() + 1e-7);
        }
      }
    }
  }

  @Test
  void exactFitsAndOversizedItems() {
    var s = new SheetLayout.Settings();
    s.width = 100;
    s.height = 50;
    s.left = s.right = s.top = s.bottom = s.gapX = s.gapY = 0;
    assertEquals(2, SheetLayout.pack(50, 50, s).capacity());
    assertEquals(0, SheetLayout.pack(101, 51, s).capacity());
    s.gapX = .01;
    assertEquals(1, SheetLayout.pack(50, 50, s).capacity());
  }

  @Test
  void quantitiesSkippedPositionsAndDuplexMappings() {
    var s = new SheetLayout.Settings();
    var r = SheetLayout.pack(90, 50, s);
    assertEquals(2, r.pageCount(r.capacity(), 1));
    var a = new SheetLayout.Placement(10, 20, 30, 40, false);
    assertEquals(s.width - 40, SheetLayout.back(a, s).x());
    s.backMapping = "SHORT_EDGE";
    assertEquals(s.height - 60, SheetLayout.back(a, s).y());
    s.backMapping = "SAME";
    assertEquals(a, SheetLayout.back(a, s));
  }

  @Test
  void bookletOrderAndPadding() {
    assertArrayEquals(new int[] {7, 0}, SheetLayout.booklet(8).get(0));
    assertArrayEquals(new int[] {1, 6}, SheetLayout.booklet(8).get(1));
    assertArrayEquals(new int[] {-1, 0}, SheetLayout.booklet(5).get(0));
    assertEquals(4, SheetLayout.booklet(5).size());
  }

  @Test
  void archiveRoundTripIncludesAssetsMastersStoriesAndPrintSettings() throws Exception {
    Publication p = Publication.preset("Label");
    Publication.Page master = new Publication.Page();
    master.name = "Brand";
    p.masters.add(master);
    p.pages.get(0).master = "Brand";
    p.stories.put("story", "Long story");
    PublicationImport.addImage(
        p, p.pages.get(0), new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), 1, 2, 3, 4);
    p.print.gapX = 4.5;
    p.mergeRows.add(Map.of("name", "A"));
    Publication loaded = PublicationFiles.read(PublicationFiles.archive(p));
    assertEquals(Publication.JSON.toJson(p), Publication.JSON.toJson(loaded));
    assertEquals(p.assets, loaded.assets);
  }

  @Test
  void archivesRejectTraversalDuplicateAndExpandedOversize() throws Exception {
    assertThrows(
        IOException.class,
        () -> PublicationFiles.read(zip("../publication.json", "{}".getBytes())));
    assertThrows(
        IOException.class, () -> PublicationFiles.read(zip("publication.json", "null".getBytes())));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream z = new ZipOutputStream(out)) {
      z.putNextEntry(new ZipEntry("publication.json"));
      byte[] block = new byte[8192];
      for (int i = 0; i <= PublicationFiles.MAX_BYTES / 8192; i++) z.write(block);
      z.closeEntry();
    }
    assertThrows(IOException.class, () -> PublicationFiles.read(out.toByteArray()));
  }

  private byte[] zip(String name, byte[] bytes) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream z = new ZipOutputStream(out)) {
      z.putNextEntry(new ZipEntry(name));
      z.write(bytes);
      z.closeEntry();
    }
    return out.toByteArray();
  }

  @Test
  void unsupportedVersionsAndInvalidCropsFail() {
    Publication p = new Publication();
    p.version = 99;
    assertThrows(IllegalArgumentException.class, p::validate);
    p.version = 1;
    var e = new Publication.Element();
    e.cropX = .8;
    e.cropWidth = .4;
    p.pages.get(0).objects.add(e);
    assertThrows(IllegalArgumentException.class, p::validate);
  }

  @Test
  void csvHandlesQuotesCommasAndNewlines() {
    var rows =
        PublicationFiles.csv(
            "name,quantity,description\r\n"
                + "\"A, B\",2,\"line 1\n"
                + "line 2\"\r\n"
                + "\"C \"\"D\"\"\",1,test\r\n");
    assertEquals(2, rows.size());
    assertEquals("A, B", rows.get(0).get("name"));
    assertEquals("line 1\nline 2", rows.get(0).get("description"));
    assertEquals("C \"D\"", rows.get(1).get("name"));
    assertThrows(IllegalArgumentException.class, () -> PublicationFiles.csv("name,name\nA,B"));
    assertThrows(IllegalArgumentException.class, () -> PublicationFiles.csv("name\n\"A"));
  }

  @Test
  void mergeDoesNotMutateSource() {
    var row = new LinkedHashMap<>(Map.of("name", "A", "price", "20"));
    String template = "{{name}} · {{price}}";
    assertEquals("A · 20", PublicationRenderer.merge(template, row));
    assertEquals("{{name}} · {{price}}", template);
    assertEquals(2, row.size());
  }

  @Test
  void mastersAndTextOverflowAreRendered() {
    Publication p = new Publication();
    var m = new Publication.Page();
    m.name = "Brand";
    var shape = new Publication.Element();
    shape.type = "RECT";
    shape.fill = "#ff0000";
    shape.x = 0;
    shape.y = 0;
    shape.width = 20;
    shape.height = 20;
    m.objects.add(shape);
    p.masters.add(m);
    p.pages.get(0).master = "Brand";
    var text = new Publication.Element();
    text.text = "long text ".repeat(100);
    text.width = 5;
    text.height = 2;
    p.pages.get(0).objects.add(text);
    PublicationRenderer r = new PublicationRenderer(p);
    BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    r.page(g, 0, Map.of());
    g.dispose();
    assertEquals(Color.RED.getRGB(), image.getRGB(2, 2));
    assertTrue(r.warnings().stream().anyMatch(x -> x.contains("overflow")));
  }

  @Test
  void requestedQuantitiesAndMergeRowsDetermineSheets() {
    Publication p = Publication.preset("Card");
    int capacity = SheetLayout.pack(p.width, p.height, p.print).capacity();
    p.mergeRows.add(Map.of("name", "A", "quantity", String.valueOf(capacity + 1)));
    assertEquals(2, new PublicationRenderer(p).sheets());
    p.print.duplex = true;
    p.pages.add(new Publication.Page());
    assertEquals(4, new PublicationRenderer(p).sheets());
    p.mergeRows = List.of(Map.of("quantity", "1.5"));
    assertThrows(IllegalArgumentException.class, () -> new PublicationRenderer(p).sheets());
  }

  @Test
  void pdfAndPreviewUseIdenticalPhysicalPlacement() throws Exception {
    Publication p = new Publication();
    p.width = 20;
    p.height = 10;
    p.print.width = 60;
    p.print.height = 40;
    p.print.left = p.print.right = p.print.top = p.print.bottom = 5;
    p.print.quantity = 1;
    var e = new Publication.Element();
    e.type = "RECT";
    e.x = e.y = 0;
    e.width = 20;
    e.height = 10;
    e.fill = "#00ff00";
    p.pages.get(0).objects.add(e);
    Path file = dir.resolve("sheet.pdf");
    PublicationFiles.pdf(p, file, 0, 0);
    BufferedImage expected = new PublicationRenderer(p).raster(0, 150);
    try (PDDocument doc = PDDocument.load(file.toFile())) {
      assertEquals(60 * 72 / 25.4, doc.getPage(0).getMediaBox().getWidth(), .001);
      BufferedImage actual = new PDFRenderer(doc).renderImageWithDPI(0, 150);
      int[] a = greenBounds(actual), b = greenBounds(expected);
      for (int i = 0; i < 4; i++)
        assertEquals(b[i], a[i], 1, "Green artwork boundary at coordinate " + i);
      assertEquals(20, a[2] * 25.4 / 150, 0.5);
      assertEquals(10, a[3] * 25.4 / 150, 0.5);
    }
  }

  private int[] greenBounds(BufferedImage image) {
    int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
    for (int y = 0; y < image.getHeight(); y++)
      for (int x = 0; x < image.getWidth(); x++) {
        Color c = new Color(image.getRGB(x, y));
        if (c.getGreen() > 200 && c.getRed() < 40 && c.getBlue() < 40) {
          minX = Math.min(minX, x);
          minY = Math.min(minY, y);
          maxX = Math.max(maxX, x);
          maxY = Math.max(maxY, y);
        }
      }
    assertTrue(maxX >= minX);
    return new int[] {minX, minY, maxX - minX + 1, maxY - minY + 1};
  }

  @Test
  void missingConverterDoesNotModifyPublisherFile() throws Exception {
    Path source = dir.resolve("sample.pub");
    Files.writeString(source, "original");
    assertThrows(IOException.class, () -> PublicationImport.publisher(source, null));
    assertEquals("original", Files.readString(source));
  }

  @Test
  void atomicRecoveryCanBeRead() throws Exception {
    Path file = dir.resolve("recover.sspub");
    Publication p = Publication.preset("Flyer");
    PublicationFiles.writeAtomic(file, PublicationFiles.archive(p));
    assertEquals("Flyer", PublicationFiles.read(Files.readAllBytes(file)).title);
    p.title = "Updated";
    PublicationFiles.writeAtomic(file, PublicationFiles.archive(p));
    assertEquals("Updated", PublicationFiles.read(Files.readAllBytes(file)).title);
  }

  @Test
  void linkedFramesContinueAcrossPagesAndWarnOnlyAtLastFrame() {
    Publication p = new Publication();
    p.stories.put("body", "Long story ".repeat(200));
    var first = new Publication.Element();
    first.story = "body";
    first.width = 50;
    first.height = 10;
    p.pages.get(0).objects.add(first);
    var secondPage = new Publication.Page();
    var second = new Publication.Element();
    second.story = "body";
    second.width = 50;
    second.height = 10;
    secondPage.objects.add(second);
    p.pages.add(secondPage);
    PublicationRenderer r = new PublicationRenderer(p);
    BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    r.page(g, 0, Map.of());
    assertFalse(r.warnings().stream().anyMatch(x -> x.contains("Linked story overflow")));
    r.page(g, 1, Map.of());
    g.dispose();
    assertTrue(r.warnings().stream().anyMatch(x -> x.contains("Linked story overflow")));
  }

  @Test
  void publisherReferencePagesAreNeverPrinted() {
    Publication p = Publication.preset("Card");
    Publication.Page reference = new Publication.Page();
    reference.reference = true;
    p.pages.add(reference);
    assertEquals(1, new PublicationRenderer(p).sheets());
  }

  @Test
  void mergeValidationIncludesLinkedStoriesAndEveryRow() {
    Publication p = Publication.preset("Card");
    p.stories.put("body", "Hello {{name}}");
    p.pages.get(0).objects.get(0).story = "body";
    p.mergeRows.add(Map.of("name", "A"));
    p.mergeRows.add(Map.of("name", ""));
    assertTrue(PublicationPreflight.missingMergeFields(p).get(0).contains("row 2"));
    assertThrows(IllegalArgumentException.class, () -> PublicationPreflight.requireMergeData(p));
    p.mergeRows.set(1, Map.of("name", "B"));
    assertDoesNotThrow(() -> PublicationPreflight.requireMergeData(p));
  }

  @Test
  void artworkCanExtendIntoReservedBleed() {
    Publication p = new Publication();
    p.width = 20;
    p.height = 10;
    p.print.width = 60;
    p.print.height = 40;
    p.print.bleed = 2;
    p.print.quantity = 1;
    p.print.alignment = "TOP_LEFT";
    var shape = new Publication.Element();
    shape.type = "RECT";
    shape.x = shape.y = -2;
    shape.width = 24;
    shape.height = 14;
    shape.fill = "#ff0000";
    shape.stroke = "none";
    p.pages.get(0).objects.add(shape);
    BufferedImage image = new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    new PublicationRenderer(p).sheet(g, 0);
    g.dispose();
    assertEquals(Color.RED.getRGB(), image.getRGB(6, 6));
  }

  @Test
  void bookletPresetFitsAndMergeRepeatsWholeBooklets() {
    Publication p = Publication.preset("Booklet");
    assertEquals(2, new PublicationRenderer(p).sheets());
    assertDoesNotThrow(() -> new PublicationRenderer(p).raster(0, 30));
    p.mergeRows.add(Map.of("quantity", "2"));
    assertEquals(4, new PublicationRenderer(p).sheets());
  }

  @Test
  void everyPresetFitsItsDefaultSheet() {
    for (String name : List.of("Label", "Card", "Flyer", "Booklet")) {
      Publication p = Publication.preset(name);
      assertTrue(SheetLayout.pack(p.width, p.height, p.print).capacity() > 0, name);
      assertDoesNotThrow(() -> new PublicationRenderer(p).raster(0, 30), name);
    }
  }
}
