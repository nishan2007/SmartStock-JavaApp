package ui.screens;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CashDrawerManagementPanelLayoutTest {
    @Test
    void editorUsesVerticalScrollingSoDeviceAssignmentRemainsReachable() throws Exception {
        String source = Files.readString(Path.of(
                "src/ui/screens/CashDrawerManagementPanel.java"));

        assertTrue(source.contains("new JScrollPane(editorStack,"));
        assertTrue(source.contains("VERTICAL_SCROLLBAR_AS_NEEDED"));
        assertTrue(source.contains("HORIZONTAL_SCROLLBAR_NEVER"));
        assertTrue(source.contains("getVerticalScrollBar().setUnitIncrement(16)"));
        assertTrue(source.contains("panel.add(editorScroll, BorderLayout.CENTER)"));
        assertTrue(source.contains("implements Scrollable"));
        assertTrue(source.contains("getScrollableTracksViewportWidth()"));
        assertTrue(source.contains("return true;"));
        assertTrue(source.contains("new GridLayout(1, 2, 8, 0)"));
    }
}
