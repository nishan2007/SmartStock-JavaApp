package ui.screens;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationManagementPanelLayoutTest {
    @Test
    void locationDetailsEditorUsesVerticalScrollingAtShortWindowHeights() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/LocationManagementPanel.java"));

        assertTrue(source.contains("add(buildEditorScrollPane(), BorderLayout.EAST)"));
        assertTrue(source.contains("ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED"));
        assertTrue(source.contains("ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER"));
        assertTrue(source.contains("getVerticalScrollBar().setUnitIncrement(16)"));
        assertTrue(source.contains("new Dimension(380, preferred.height)"));
    }
}
