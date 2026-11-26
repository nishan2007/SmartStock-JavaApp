package services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StudioPdfOcrServiceTest {
  @TempDir Path dir;

  @Test
  void validatesServerConfiguration() throws Exception {
    Path config = dir.resolve("studio-ocr.properties");
    var defaults = StudioPdfOcrService.Settings.load(config, null);
    assertEquals("eng", defaults.language());
    assertThrows(IOException.class, () -> StudioPdfOcrService.check(
        new StudioPdfOcrService.Settings(dir.resolve("missing engine.exe").toString(), "", "eng")));
    Files.writeString(config, "language=--help" + System.lineSeparator());
    assertThrows(IOException.class, () -> StudioPdfOcrService.Settings.load(config, null));
  }

  @Test
  void rejectsNonPngAndOversizedDimensions() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> StudioPdfOcrService.decodePage("not a PNG"));
    assertThrows(IllegalArgumentException.class, () -> StudioPdfOcrService.decodePage(png(
        new BufferedImage(32768, 1, BufferedImage.TYPE_BYTE_GRAY))));
  }

  @Test
  void installedTesseractRecognizesAnUploadedPage() throws Exception {
    String executable = System.getProperty("smartstock.test.tesseract");
    assumeTrue(executable != null, "Set smartstock.test.tesseract to run with the installed engine.");
    var settings = new StudioPdfOcrService.Settings(executable, "", "eng");
    StudioPdfOcrService.check(settings);
    var page = new BufferedImage(700, 200, BufferedImage.TYPE_INT_RGB);
    var graphics = page.createGraphics();
    try {
      graphics.setColor(Color.WHITE);
      graphics.fillRect(0, 0, page.getWidth(), page.getHeight());
      graphics.setColor(Color.BLACK);
      graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 48));
      graphics.drawString("Invoice total", 40, 105);
    } finally {
      graphics.dispose();
    }
    String tsv = StudioPdfOcrService.recognize(settings, StudioPdfOcrService.decodePage(png(page)),
        Duration.ofSeconds(15));
    assertTrue(tsv.lines().anyMatch(line -> line.startsWith("5\t") && line.endsWith("\tInvoice")), tsv);
    assertTrue(tsv.lines().anyMatch(line -> line.startsWith("5\t") && line.endsWith("\ttotal")), tsv);
  }

  private static String png(BufferedImage page) throws IOException {
    var png = new ByteArrayOutputStream();
    assertTrue(ImageIO.write(page, "png", png));
    return Base64.getEncoder().encodeToString(png.toByteArray());
  }

  @Test
  void ocrProcessSupportsSpacesTimeoutAndCancellation() throws Exception {
    String javaExecutable =
        Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java")
            .toString();
    Path classes =
        Path.of(StudioPdfOcrServiceTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    StudioPdfOcrService.run(List.of(javaExecutable, "--version"), classes, Duration.ofSeconds(10));
    Path marker = dir.resolve("process marker with spaces.txt");
    List<String> command =
        List.of(javaExecutable, "-cp", classes.toString(), OcrProcess.class.getName(), marker.toString());
    assertThrows(
        IOException.class, () -> StudioPdfOcrService.run(command, classes, Duration.ofMillis(100)));
    Files.deleteIfExists(marker);
    var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
    Thread thread =
        new Thread(
            () -> {
              try {
                StudioPdfOcrService.run(command, classes, Duration.ofSeconds(30));
              } catch (Throwable ex) {
                failure.set(ex);
              }
            });
    thread.start();
    try {
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
      while (!Files.exists(marker) && thread.isAlive() && System.nanoTime() < deadline)
        Thread.sleep(25);
      assertTrue(Files.exists(marker));
      long pid = Long.parseLong(Files.readString(marker));
      thread.interrupt();
      thread.join(10000);
      assertFalse(thread.isAlive());
      assertInstanceOf(InterruptedException.class, failure.get(), String.valueOf(failure.get()));
      assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
    } finally {
      thread.interrupt();
      thread.join(10000);
    }
  }

  public static class OcrProcess {
    public static void main(String[] args) throws Exception {
      Path marker = Path.of(args[0]);
      Path pending = marker.resolveSibling(marker.getFileName() + ".pending");
      Files.writeString(pending, Long.toString(ProcessHandle.current().pid()));
      Files.move(pending, marker, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
      Thread.sleep(60000);
    }
  }
}
