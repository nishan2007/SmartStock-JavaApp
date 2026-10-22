package ui.screens;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class PublishingWorkspaceLayoutTest {
  @Test
  void arrowsMoveSelectedObjectsRespectLocksAndCanBeUndone() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    SwingUtilities.invokeAndWait(() -> {
      PublishingWorkspace frame = new PublishingWorkspace();
      try {
        var documentField = PublishingWorkspace.class.getDeclaredField("document");
        documentField.setAccessible(true);
        var document = (publishing.Publication) documentField.get(frame);
        var text = document.pages.get(0).objects.get(0);
        double x = text.x, y = text.y;
        var locked = new publishing.Publication.Element();
        locked.locked = true;
        document.pages.get(0).objects.add(locked);
        var selectionField = PublishingWorkspace.class.getDeclaredField("selection");
        selectionField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var selection = (java.util.Set<String>) selectionField.get(frame);
        selection.add(text.id);
        selection.add(locked.id);
        var canvasField = PublishingWorkspace.class.getDeclaredField("canvas");
        canvasField.setAccessible(true);
        JComponent canvas = (JComponent) canvasField.get(frame);
        for (int i = 0; i < 8; i++) canvas.getActionMap().get("RIGHT").actionPerformed(null);
        canvas.getActionMap().get("shift UP").actionPerformed(null);
        assertEquals(x + .8, text.x, .00001);
        assertEquals(y - 1, text.y, .00001);
        assertEquals(5, locked.x);
        assertEquals(5, locked.y);
        var undo = PublishingWorkspace.class.getDeclaredMethod("undo");
        undo.setAccessible(true);
        undo.invoke(frame);
        document = (publishing.Publication) documentField.get(frame);
        assertEquals(y, document.pages.get(0).objects.get(0).y, .00001);
        assertEquals(x + .8, document.pages.get(0).objects.get(0).x, .00001);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      } finally {
        try {
          var dirty = PublishingWorkspace.class.getDeclaredField("dirty");
          dirty.setAccessible(true);
          dirty.set(frame, false);
          frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
          frame.dispose();
        } catch (Exception ex) { throw new RuntimeException(ex); }
      }
    });
  }

  @Test
  void workspaceFitsItsSupportedWindowWithoutOpeningAVisibleWindow() throws Exception {
    Assumptions.assumeFalse(
        GraphicsEnvironment.isHeadless(),
        "Optional Windows Swing layout verification requires a graphics environment.");
    AtomicReference<PublishingWorkspace> window = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          PublishingWorkspace frame = new PublishingWorkspace();
          window.set(frame);
          frame.addNotify();
          frame.validate();
        });
    Thread.sleep(1200); // Allow the debounced worker to paint the first design before capture.
    SwingUtilities.invokeAndWait(
        () -> {
          PublishingWorkspace frame = window.get();
          try {
            frame.addNotify();
            frame.validate();
            assertEquals("Project", frame.getJMenuBar().getMenu(0).getText());
            assertTrue(frame.getContentPane().getComponentCount() >= 3);
            BufferedImage image =
                new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            frame.getRootPane().printAll(g);
            g.dispose();
            Path target = Path.of("target", "publishing-workspace-preview.png");
            Files.createDirectories(target.getParent());
            ImageIO.write(image, "png", target.toFile());
            frame.setSize(960, 640);
            frame.validate();
            assertTrue(frame.getContentPane().getWidth() > 900);
          } catch (Exception ex) {
            throw new RuntimeException(ex);
          } finally {
            frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
            frame.dispose();
          }
        });
  }
}
