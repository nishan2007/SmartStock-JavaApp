package gy.deckers.studio.pdfeditor;

import static org.junit.jupiter.api.Assertions.*;

import gy.deckers.studio.PdfEditorPanel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Opt-in real Swing layout check; no store session, native dialogs, or visible window. */
@EnabledIfSystemProperty(named = "smartstock.pdfEditorUiTest", matches = "true")
class PdfEditorSwingTest {
  @Test
  void workspaceRendersAtDesktopAndMinimumSizes() throws Exception {
    AtomicReference<PdfEditorPanel> holder = new AtomicReference<>();
    AtomicReference<JFrame> window = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          var frame = new PdfEditorPanel();
          var host = new JFrame("SmartStudio PDF Editor");
          host.setContentPane(frame);
          host.setSize(1320, 850);
          host.addNotify();
          host.validate();
          window.set(host);
          holder.set(frame);
        });
    PdfEditorPanel frame = holder.get();
    Field field = PdfEditorPanel.class.getDeclaredField("document");
    field.setAccessible(true);
    PdfDocument document = (PdfDocument) field.get(frame);
    try {
      document.blank(0);
      document.text(0, "SmartStudio PDF Editor", 60, 700, 24, Color.BLACK);
      document.text(0, "Local PDF editing and page organization", 60, 660, 14, Color.BLACK);
      Method refresh = PdfEditorPanel.class.getDeclaredMethod("refresh");
      refresh.setAccessible(true);
      SwingUtilities.invokeAndWait(
          () -> {
            try {
              refresh.invoke(frame);
            } catch (Exception ex) {
              throw new RuntimeException(ex);
            }
          });
      Field task = PdfEditorPanel.class.getDeclaredField("task");
      task.setAccessible(true);
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
      while (System.nanoTime() < deadline) {
        AtomicReference<Object> running = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
            () -> {
              try {
                running.set(task.get(frame));
              } catch (Exception ex) {
                throw new RuntimeException(ex);
              }
            });
        if (running.get() == null) break;
        Thread.sleep(30);
      }
      SwingUtilities.invokeAndWait(
          () -> {
            try {
              assertNull(task.get(frame));
            } catch (IllegalAccessException ex) {
              throw new RuntimeException(ex);
            }
          });
      Path output = Path.of("target", "pdf-editor-preview");
      Files.createDirectories(output);
      for (Dimension size :
          new Dimension[] {
            new Dimension(1320, 850), new Dimension(960, 640), new Dimension(820, 580)
          }) {
        SwingUtilities.invokeAndWait(
            () -> {
              window.get().setSize(size);
              window.get().validate();
              assertTrue(
                  findTabs(frame).getWidth() >= 250,
                  "PDF tools must remain usable at Studio's minimum width");
              BufferedImage image =
                  new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
              Graphics2D g = image.createGraphics();
              try {
                frame.getRootPane().printAll(g);
                ImageIO.write(
                    image, "png", output.resolve("workspace-" + size.width + ".png").toFile());
              } catch (Exception ex) {
                throw new RuntimeException(ex);
              } finally {
                g.dispose();
              }
            });
      }
    } finally {
      document.close();
      SwingUtilities.invokeAndWait(window.get()::dispose);
    }
  }

  private static JTabbedPane findTabs(Container parent) {
    for (Component child : parent.getComponents()) {
      if (child instanceof JTabbedPane tabs) return tabs;
      if (child instanceof Container nested) {
        JTabbedPane tabs = findTabs(nested);
        if (tabs != null) return tabs;
      }
    }
    return null;
  }
}
