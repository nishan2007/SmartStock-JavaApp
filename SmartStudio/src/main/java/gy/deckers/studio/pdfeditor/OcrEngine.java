package gy.deckers.studio.pdfeditor;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import services.LanApiClient;

/** Connects store-server OCR results to SmartStudio's PDF editing operations. */
public final class OcrEngine {
  public record Word(int x, int y, int width, int height, float confidence, String text) {}

  public record Result(BufferedImage image, List<Word> words) {}

  private OcrEngine() {}

  public static Result recognize(BufferedImage image) throws Exception {
    var png = new ByteArrayOutputStream();
    if (!ImageIO.write(image, "png", png)) throw new IOException("Could not encode the OCR page.");
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("OCR cancelled.");
    String tsv = LanApiClient.recognizeStudioPdfPage(png.toByteArray());
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("OCR cancelled.");
    return new Result(image, parse(tsv));
  }

  public static List<Word> parse(String tsv) {
    List<Word> words = new ArrayList<>();
    for (String line : tsv.split("\\R")) {
      String[] cells = line.split("\t", 12);
      if (cells.length != 12 || !cells[0].equals("5") || cells[11].isBlank()) continue;
      try {
        words.add(
            new Word(
                Integer.parseInt(cells[6]),
                Integer.parseInt(cells[7]),
                Integer.parseInt(cells[8]),
                Integer.parseInt(cells[9]),
                Float.parseFloat(cells[10]),
                cells[11]));
      } catch (NumberFormatException ignored) {
        /* Ignore malformed engine rows. */
      }
    }
    return List.copyOf(words);
  }

  public static BufferedImage correct(
      Result result,
      Rectangle region,
      String replacement,
      Font font,
      Color foreground,
      Color background) {
    BufferedImage image =
        new BufferedImage(
            result.image.getWidth(), result.image.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.drawImage(result.image, 0, 0, null);
      g.setClip(region);
      g.setColor(background);
      g.fill(region);
      g.setColor(foreground);
      g.setFont(font);
      g.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      int y = region.y + g.getFontMetrics().getAscent();
      for (String line : replacement.split("\\R", -1)) {
        g.drawString(line, region.x, y);
        y += g.getFontMetrics().getHeight();
      }
    } finally {
      g.dispose();
    }
    return image;
  }

  public static void apply(PdfDocument document, int index, Result result, boolean rasterize)
      throws Exception {
    document.edit(
        rasterize ? "Correct scanned region" : "Searchable OCR",
        pdf -> {
          PDPage page = pdf.getPage(index);
          if (rasterize) {
            PDRectangle old = page.getCropBox();
            boolean rotated = Math.floorMod(page.getRotation(), 180) != 0;
            PDRectangle box =
                new PDRectangle(
                    rotated ? old.getHeight() : old.getWidth(),
                    rotated ? old.getWidth() : old.getHeight());
            // Replace content/resources rather than placing a cover over recoverable text.
            page.setRotation(0);
            page.setMediaBox(box);
            page.setCropBox(box);
            page.getCOSObject().removeItem(org.apache.pdfbox.cos.COSName.TRIM_BOX);
            page.getCOSObject().removeItem(org.apache.pdfbox.cos.COSName.BLEED_BOX);
            page.getCOSObject().removeItem(org.apache.pdfbox.cos.COSName.ART_BOX);
            page.setResources(new PDResources());
            page.setAnnotations(new ArrayList<>());
            page.getCOSObject().removeItem(org.apache.pdfbox.cos.COSName.CONTENTS);
            try (var out = new PDPageContentStream(pdf, page)) {
              out.drawImage(
                  LosslessFactory.createFromImage(pdf, result.image),
                  0,
                  0,
                  box.getWidth(),
                  box.getHeight());
            }
          }
          PDRectangle box = page.getCropBox();
          // Renderer coordinates are top-left and include page rotation; map back to PDF space.
          for (Word word : result.words) {
            String safe = word.text.replaceAll("[^\\x20-\\x7e\\u00a0-\\u00ff]", "?");
            float vx =
                word.x
                    * (Math.floorMod(page.getRotation(), 180) == 0
                        ? box.getWidth()
                        : box.getHeight())
                    / result.image.getWidth();
            float vy =
                (word.y + word.height)
                    * (Math.floorMod(page.getRotation(), 180) == 0
                        ? box.getHeight()
                        : box.getWidth())
                    / result.image.getHeight();
            float size =
                Math.max(
                    1,
                    word.height
                        * (Math.floorMod(page.getRotation(), 180) == 0
                            ? box.getHeight()
                            : box.getWidth())
                        / result.image.getHeight());
            try (var out =
                new PDPageContentStream(
                    pdf, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
              out.transform(displayTransform(page));
              out.beginText();
              out.setRenderingMode(org.apache.pdfbox.pdmodel.graphics.state.RenderingMode.NEITHER);
              out.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, size);
              float natural =
                  org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA.getStringWidth(safe)
                      / 1000
                      * size;
              float desired =
                  word.width
                      * (Math.floorMod(page.getRotation(), 180) == 0
                          ? box.getWidth()
                          : box.getHeight())
                      / result.image.getWidth();
              if (natural > 0) out.setHorizontalScaling(100 * desired / natural);
              out.newLineAtOffset(vx, -vy);
              out.showText(safe);
              out.endText();
            }
          }
        });
  }

  private static org.apache.pdfbox.util.Matrix displayTransform(PDPage page) {
    PDRectangle b = page.getCropBox();
    return switch (Math.floorMod(page.getRotation(), 360)) {
      case 90 ->
          new org.apache.pdfbox.util.Matrix(0, 1, -1, 0, b.getLowerLeftX(), b.getLowerLeftY());
      case 180 ->
          new org.apache.pdfbox.util.Matrix(-1, 0, 0, -1, b.getUpperRightX(), b.getLowerLeftY());
      case 270 ->
          new org.apache.pdfbox.util.Matrix(0, -1, 1, 0, b.getUpperRightX(), b.getUpperRightY());
      default ->
          new org.apache.pdfbox.util.Matrix(1, 0, 0, 1, b.getLowerLeftX(), b.getUpperRightY());
    };
  }
}
