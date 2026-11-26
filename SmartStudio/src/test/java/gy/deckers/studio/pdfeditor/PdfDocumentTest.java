package gy.deckers.studio.pdfeditor;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.annotation.*;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.*;
import org.apache.pdfbox.pdmodel.interactive.form.*;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import publishing.TextAlignment;

class PdfDocumentTest {
  @TempDir Path dir;

  private Path fixture(String name, String... pages) throws Exception {
    Path path = dir.resolve(name);
    try (PDDocument pdf = new PDDocument()) {
      for (String text : pages) {
        PDPage page = new PDPage(PDRectangle.LETTER);
        pdf.addPage(page);
        PdfDocument.writeText(pdf, page, text, 30, 700, 14, Color.BLACK, false);
      }
      pdf.save(path.toFile());
    }
    return path;
  }

  @Test
  void rangesValidateAndDeduplicate() {
    assertArrayEquals(new int[] {0, 1, 2, 4}, PageRanges.parse("1–3, 5, 2", 5));
    for (String invalid : List.of("", "0", "6", "3-1", "1,", "1-2-3", "a"))
      assertThrows(IllegalArgumentException.class, () -> PageRanges.parse(invalid, 5));
  }

  @Test
  void textBoxesAlignEachLineInTheSavedPdf() throws Exception {
    try (PdfDocument pdf = new PdfDocument()) {
      pdf.blank(0);
      pdf.text(0, "Left", 40, 500, 200, 14, Color.BLACK, TextAlignment.LEFT);
      pdf.text(0, "Center", 40, 450, 200, 14, Color.BLACK, TextAlignment.CENTER);
      pdf.text(0, "Right", 40, 400, 200, 14, Color.BLACK, TextAlignment.RIGHT);
      var output=dir.resolve("aligned.pdf");
      pdf.save(output,null);
      var starts=new java.util.HashMap<String,Float>();
      try (PDDocument result=PDDocument.load(output.toFile())) {
        new PDFTextStripper(){
          @Override protected void writeString(String text,List<TextPosition> positions){
            if(!positions.isEmpty())starts.put(text,positions.get(0).getXDirAdj());
          }
        }.getText(result);
      }
      assertEquals(40f,starts.get("Left"),.01);
      assertTrue(starts.get("Center")>100f);
      assertTrue(starts.get("Right")>starts.get("Center"));
    }
  }

  @Test
  void mergeReorderRotateCropExtractAndHistory() throws Exception {
    try (PdfDocument pdf = new PdfDocument()) {
      pdf.append(fixture("one.pdf", "Alpha", "Beta"), "", 0, false);
      var alpha = pdf.pageId(0);
      var beta = pdf.pageId(1);
      pdf.append(fixture("two.pdf", "Gamma"), "", 1, false);
      assertTrue(pdf.text(1).contains("Gamma"));
      pdf.move(new int[] {2}, 0);
      assertEquals(beta, pdf.pageId(0));
      assertEquals(alpha, pdf.pageId(1));
      pdf.rotate(new int[] {0}, 90);
      assertEquals(90, pdf.rotation(0));
      pdf.crop(1, 10, 20, 400, 500);
      assertEquals(400, pdf.bounds(1).getWidth());
      pdf.undo();
      assertEquals(612, pdf.bounds(1).getWidth());
      pdf.redo();
      Path target = dir.resolve("extract.pdf");
      pdf.save(target, new int[] {1, 2});
      try (PDDocument read = PDDocument.load(target.toFile())) {
        assertEquals(2, read.getNumberOfPages());
        assertTrue(new PDFTextStripper().getText(read).contains("Alpha"));
      }
      pdf.delete(new int[] {2});
      assertEquals(2, pdf.size());
      pdf.undo();
      assertEquals(3, pdf.size());
      assertThrows(IOException.class, () -> pdf.delete(new int[] {0, 1, 2}));
    }
  }

  @Test
  void duplicatedResourcesAndMovableObjectsSurviveClosingSources() throws Exception {
    try (PdfDocument pdf = new PdfDocument()) {
      pdf.append(fixture("source.pdf", "Original"), "", 0, false);
      pdf.text(0, "Added", 40, 400, 14, Color.RED);
      BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
      pdf.image(0, image, 20, 20, 20, 20);
      pdf.duplicate(new int[] {0});
      assertNotEquals(pdf.pageId(0), pdf.pageId(1));
      assertEquals(2, pdf.objects(1).size());
      var object = pdf.objects(0).get(0);
      pdf.updateObject(
          new PdfDocument.Overlay(
              object.id(),
              object.page(),
              object.kind(),
              "Moved",
              null,
              80,
              300,
              60,
              20,
              12,
              Color.BLUE));
      Path output = dir.resolve("duplicate.pdf");
      pdf.save(output, null);
      try (PDDocument read = PDDocument.load(output.toFile())) {
        String text = new PDFTextStripper().getText(read);
        assertTrue(text.contains("Moved"));
        assertTrue(text.contains("Added"));
        assertEquals(2, read.getNumberOfPages());
      }
      assertNotNull(pdf.render(1, .2f));
      pdf.undo();
      assertEquals("Added", pdf.objects(0).get(0).text());
    }
  }

  private Path form(String name) throws Exception {
    Path path = dir.resolve(name);
    try (PDDocument pdf = new PDDocument()) {
      PDPage page = new PDPage();
      pdf.addPage(page);
      PDAcroForm form = new PDAcroForm(pdf);
      pdf.getDocumentCatalog().setAcroForm(form);
      PDResources resources = new PDResources();
      resources.put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA);
      form.setDefaultResources(resources);
      form.setDefaultAppearance("/Helv 12 Tf 0 g");
      PDTextField field = new PDTextField(form);
      field.setPartialName("name");
      field.setDefaultAppearance("/Helv 12 Tf 0 g");
      form.getFields().add(field);
      var widget = field.getWidgets().get(0);
      widget.setRectangle(new PDRectangle(30, 600, 150, 30));
      widget.setPage(page);
      page.getAnnotations().add(widget);
      field.setValue(name);
      pdf.save(path.toFile());
    }
    return path;
  }

  @Test
  void collidingFormFieldsRemainIndependent() throws Exception {
    try (PdfDocument pdf = new PdfDocument()) {
      pdf.append(form("a.pdf"), "", 0, false);
      pdf.append(form("b.pdf"), "", 1, false);
      assertEquals(2, pdf.fields().size());
      String field = pdf.fields().keySet().iterator().next();
      pdf.field(field, "Changed", TextAlignment.RIGHT);
      assertEquals(TextAlignment.RIGHT,pdf.fieldTextAlignment(field).orElseThrow());
      Path out = dir.resolve("forms.pdf");
      pdf.save(out, null);
      try (PDDocument read = PDDocument.load(out.toFile())) {
        assertEquals(
            "Changed", read.getDocumentCatalog().getAcroForm().getField(field).getValueAsString());
        assertEquals(2,((PDTextField)read.getDocumentCatalog().getAcroForm().getField(field)).getQ());
        assertEquals(1, read.getPage(0).getAnnotations().size());
      }
    }
  }

  @Test
  void numericLinkDestinationsTrackReordering() throws Exception {
    Path source = fixture("links.pdf", "First", "Second");
    try (PDDocument pdf = PDDocument.load(source.toFile())) {
      PDAnnotationLink link = new PDAnnotationLink();
      link.setRectangle(new PDRectangle(10, 10, 100, 30));
      PDPageFitDestination destination = new PDPageFitDestination();
      destination.setPageNumber(1);
      link.setDestination(destination);
      pdf.getPage(0).getAnnotations().add(link);
      pdf.save(source.toFile());
    }
    try (PdfDocument pdf = new PdfDocument()) {
      pdf.append(source, "", 0, false);
      pdf.move(new int[] {1}, 0);
      try (PDDocument copy = PDDocument.load(pdf.bytes())) {
        var link = (PDAnnotationLink) copy.getPage(1).getAnnotations().get(0);
        var destination = (PDPageDestination) link.getDestination();
        assertEquals(copy.getPage(0).getCOSObject(), destination.getPage().getCOSObject());
      }
    }
  }

  @Test
  void failedEditAndFailedSaveLeaveExistingContentIntact() throws Exception {
    try (PdfDocument pdf = new PdfDocument()) {
      pdf.append(fixture("original.pdf", "Keep"), "", 0, false);
      assertThrows(
          Exception.class,
          () ->
              pdf.edit(
                  "broken",
                  doc -> {
                    doc.removePage(0);
                    throw new IOException("Failure");
                  }));
      assertEquals(1, pdf.size());
      assertTrue(pdf.text(0).contains("Keep"));
      Path existing = dir.resolve("destination");
      Files.createDirectory(existing);
      Files.writeString(existing.resolve("sentinel"), "Keep");
      assertThrows(Exception.class, () -> pdf.save(existing, null));
      assertEquals("Keep", Files.readString(existing.resolve("sentinel")));
      try (var files = Files.list(dir)) {
        assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith(".smartstock-pdf-")));
      }
    }
  }

  @Test
  void encryptedAndMalformedDocuments() throws Exception {
    Path encrypted = fixture("encrypted.pdf", "Private");
    try (PDDocument pdf = PDDocument.load(encrypted.toFile())) {
      AccessPermission permissions = new AccessPermission();
      permissions.setCanModify(false);
      permissions.setCanAssembleDocument(false);
      pdf.protect(new StandardProtectionPolicy("owner", "reader", permissions));
      pdf.save(encrypted.toFile());
    }
    assertThrows(InvalidPasswordException.class, () -> PdfDocument.load(encrypted, ""));
    assertThrows(IOException.class, () -> PdfDocument.load(encrypted, "reader"));
    try (PDDocument pdf = PdfDocument.load(encrypted, "owner")) {
      assertEquals(1, pdf.getNumberOfPages());
    }
    Path malformed = dir.resolve("bad.pdf");
    Files.writeString(malformed, "Not a PDF");
    assertThrows(IOException.class, () -> PdfDocument.load(malformed, ""));
  }

  @Test
  void correctionReplacesPageContentAndOldSearchableText() throws Exception {
    try (PdfDocument pdf = new PdfDocument()) {
      pdf.append(fixture("scan.pdf", "OLD_SECRET"), "", 0, false);
      BufferedImage image = new BufferedImage(612, 792, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = image.createGraphics();
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, 612, 792);
      g.dispose();
      var result =
          new OcrEngine.Result(image, List.of(new OcrEngine.Word(30, 50, 80, 15, 99, "Corrected")));
      OcrEngine.apply(pdf, 0, result, true);
      Path out = dir.resolve("corrected.pdf");
      pdf.save(out, null);
      try (PDDocument read = PDDocument.load(out.toFile())) {
        String text = new PDFTextStripper().getText(read);
        assertTrue(text.contains("Corrected"));
        assertFalse(text.contains("OLD_SECRET"));
      }
      pdf.undo();
      assertTrue(pdf.text(0).contains("OLD_SECRET"));
    }
  }

  @Test
  void ocrParsingHandlesWordPositions() {
    var words =
        OcrEngine.parse(
            "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight"
                + "\tconf\ttext\n"
                + "5\t1\t1\t1\t1\t1\t10\t20\t30\t12\t93.5\tHello\n"
                + "5\tbad\n");
    assertEquals(1, words.size());
    assertEquals("Hello", words.get(0).text());

  }

  @Test
  void extractionRemovesOtherPagesFormFieldsAndLinks() throws Exception {
    try (PdfDocument pdf = new PdfDocument()) {
      pdf.append(form("first.pdf"), "", 0, false);
      pdf.append(form("second.pdf"), "", 1, false);
      Path out = dir.resolve("single.pdf");
      pdf.save(out, new int[] {0});
      try (PDDocument copy = PDDocument.load(out.toFile())) {
        assertEquals(1, copy.getNumberOfPages());
        assertEquals(1, copy.getDocumentCatalog().getAcroForm().getFields().size());
      }
      pdf.delete(new int[] {0});
      assertEquals(1, pdf.fields().size());
      pdf.undo();
      assertEquals(2, pdf.fields().size());
    }
  }

  @Test
  void ocrRotatedPageKeepsGeometryAndUndo() throws Exception {
    try (PdfDocument pdf = new PdfDocument()) {
      pdf.append(fixture("rotated.pdf", "Original"), "", 0, false);
      pdf.rotate(new int[] {0}, 90);
      pdf.crop(0, 10, 250, 400, 500);
      BufferedImage image = pdf.render(0, 1);
      assertEquals(500, image.getWidth());
      assertEquals(400, image.getHeight());
      OcrEngine.apply(
          pdf,
          0,
          new OcrEngine.Result(image, List.of(new OcrEngine.Word(30, 30, 50, 14, 99, "Scanned"))),
          false);
      assertTrue(pdf.text(0).contains("Scanned"));
      assertEquals(90, pdf.rotation(0));
      String beforeCorrection = pdf.text(0);
      OcrEngine.apply(
          pdf,
          0,
          new OcrEngine.Result(image, List.of(new OcrEngine.Word(30, 30, 50, 14, 99, "Fixed"))),
          true);
      assertEquals(0, pdf.rotation(0));
      assertEquals(500, pdf.bounds(0).getWidth());
      assertFalse(pdf.text(0).contains("Original"));
      pdf.undo();
      assertEquals(90, pdf.rotation(0));
      assertEquals(beforeCorrection, pdf.text(0));
    }
  }


  @Test
  void signedImportRequiresAcknowledgmentAndRemovesSignatureFields() throws Exception {
    Path signed = fixture("signed.pdf", "Signed appearance");
    try (PDDocument source = PDDocument.load(signed.toFile())) {
      PDAcroForm form = new PDAcroForm(source);
      source.getDocumentCatalog().setAcroForm(form);
      PDSignatureField field = new PDSignatureField(form);
      field.setPartialName("Signature");
      form.getFields().add(field);
      var signature = new org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature();
      signature.setFilter(
          org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature.FILTER_ADOBE_PPKLITE);
      field.setValue(signature);
      var widget = field.getWidgets().get(0);
      widget.setPage(source.getPage(0));
      widget.setRectangle(new PDRectangle(10, 10, 100, 30));
      source.getPage(0).getAnnotations().add(widget);
      source.save(signed.toFile());
    }
    try (PdfDocument document = new PdfDocument()) {
      assertThrows(IOException.class, () -> document.append(signed, "", 0, false));
      assertEquals(0, document.size());
      document.append(signed, "", 0, true);
      try (PDDocument exported = PDDocument.load(document.bytes())) {
        assertTrue(exported.getSignatureDictionaries().isEmpty());
        assertEquals(1, exported.getNumberOfPages());
      }
    }
    try (PDDocument original = PDDocument.load(signed.toFile())) {
      assertEquals(1, original.getSignatureDictionaries().size());
    }
  }
}
