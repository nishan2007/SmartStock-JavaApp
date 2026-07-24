package ui.screens;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationPreferencesServerSectionTest {
    private static final Path PREFERENCES =
            Path.of("src/ui/screens/WorkstationPreferences.java");
    private static final Path MENU =
            Path.of("src/ui/components/AppMenuBar.java");

    @Test
    void serverSectionIsGuardedByServerMode() throws Exception {
        String source = Files.readString(PREFERENCES);

        assertTrue(source.contains(
                "DatabaseConfig.load().mode() == DatabaseMode.SERVER"
        ));
        assertTrue(source.contains("if (serverWorkstation)"));
        assertTrue(source.contains("new DefaultMutableTreeNode(NAV_SERVER)"));
        assertTrue(source.contains("new DatabaseSetup(this).setVisible(true)"));
    }

    @Test
    void databaseSetupIsNotExposedFromStatusMenu() throws Exception {
        String source = Files.readString(MENU);

        assertFalse(source.contains(
                "JMenuItem databaseSetupItem = new JMenuItem(\"Database Setup\")"
        ));
        assertFalse(source.contains("statusMenu.add(databaseSetupItem)"));
    }
}
