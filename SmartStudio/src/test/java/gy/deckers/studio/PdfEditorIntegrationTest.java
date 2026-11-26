package gy.deckers.studio;

import static org.junit.jupiter.api.Assertions.*;

import gy.deckers.studio.pdfeditor.PdfDocument;
import java.awt.*;
import java.nio.file.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfEditorIntegrationTest {
  @TempDir Path folder;

  @Test
  void editorIsOwnedByStudioAndAbsentFromStock() throws Exception {
    assertTrue(
        Files.readString(Path.of("src/main/java/gy/deckers/studio/SmartStudio.java"))
            .contains("sections.add(pdfEditor,\"PDF Editor\")"));
    assertFalse(
        Files.readString(Path.of("../SmartStock/src/ui/components/AppMenuBar.java"))
            .contains("PdfEditor"));
    assertFalse(Files.exists(Path.of("../SmartStock/src/ui/screens/PdfEditorWorkspace.java")));
    assertFalse(Files.exists(Path.of("../SmartStock/src/pdfeditor/PdfDocument.java")));
  }

  @Test
  void resettingAfterSignOutDropsDocumentAndEditHistory() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          PdfEditorPanel panel = new PdfEditorPanel();
          try {
            var field = PdfEditorPanel.class.getDeclaredField("document");
            field.setAccessible(true);
            PdfDocument previous = (PdfDocument) field.get(panel);
            previous.blank(0);
            previous.text(0, "Private document", 30, 700, 14, Color.BLACK);
            assertTrue(previous.canUndo());
            panel.reset();
            PdfDocument current = (PdfDocument) field.get(panel);
            assertNotSame(previous, current);
            assertEquals(0, current.size());
            assertFalse(current.canUndo());
            assertTrue(panel.discard());
          } catch (Exception ex) {
            throw new RuntimeException(ex);
          } finally {
            panel.shutdown();
          }
        });
  }

  @Test
  void workspaceChooserOpensEmbeddedEditorAndKeepsItWhenSwitching() throws Exception {
    if (GraphicsEnvironment.isHeadless()) return;
    SwingUtilities.invokeAndWait(
        () -> {
          SmartStudio studio = new SmartStudio(false);
          try {
            JButton entry = find(studio, "PDF Editor");
            assertNotNull(entry);
            entry.doClick();
            var field = SmartStudio.class.getDeclaredField("pdfEditor");
            field.setAccessible(true);
            PdfEditorPanel editor = (PdfEditorPanel) field.get(studio);
            assertTrue(editor.isVisible());
            find(studio, "Workspaces").doClick();
            assertFalse(editor.isVisible());
            entry.doClick();
            assertTrue(editor.isVisible());
          } catch (Exception ex) {
            throw new RuntimeException(ex);
          } finally {
            studio.dispose();
          }
        });
  }

  private static JButton find(Container parent, String text) {
    for (Component child : parent.getComponents()) {
      if (child instanceof JButton button && text.equals(button.getText())) return button;
      if (child instanceof Container nested) {
        JButton found = find(nested, text);
        if (found != null) return found;
      }
    }
    return null;
  }
}
