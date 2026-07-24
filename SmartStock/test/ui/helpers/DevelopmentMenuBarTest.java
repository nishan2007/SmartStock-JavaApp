package ui.helpers;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DevelopmentMenuBarTest {
    @Test
    void sharedMenuBarUsesPermanentRedDevelopmentTreatment() throws Exception {
        String theme = Files.readString(Path.of("src/ui/helpers/ThemeManager.java"));
        String menu = Files.readString(Path.of("src/ui/components/AppMenuBar.java"));

        assertTrue(theme.contains("DEVELOPMENT_MENU_BAR"));
        assertTrue(theme.contains("EnvironmentProfile.active() == EnvironmentProfile.DEVELOPMENT"));
        assertTrue(menu.contains("\"DEVELOPER / TEST\""));
        assertTrue(menu.contains("SmartStock.preserveForeground"));
    }
}
