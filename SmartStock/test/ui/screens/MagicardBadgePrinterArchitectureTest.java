package ui.screens;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicardBadgePrinterArchitectureTest {
    @Test
    void hardwareSettingsExposeDedicatedMagicardQueueAndTestAction() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/workstationprefs/HardwareSettingsPanel.java"));

        assertTrue(source.contains("Badge printer (Magicard 600)"));
        assertTrue(source.contains("badgePrinterQueueBox"));
        assertTrue(source.contains("saveBadgePrinterSettings"));
        assertTrue(source.contains("printTestBadge(this)"));
        assertTrue(source.contains("Queue missing - install/restore the Magicard Windows queue"));
    }
}
