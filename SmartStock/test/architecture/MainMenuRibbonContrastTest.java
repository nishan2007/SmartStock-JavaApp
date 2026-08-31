package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MainMenuRibbonContrastTest {
    private static final Path SOURCE = Path.of("src", "ui", "screens", "MainMenu.java");

    @Test
    void greetingUsesLightColorsOnTheAlwaysDarkRibbon() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("mainGreetingLabel.setForeground(RIBBON_TITLE)"));
        assertTrue(source.contains("subtitleLabel.setForeground(RIBBON_SUBTITLE)"));
        assertTrue(source.contains("g.setColor(RIBBON_BACKGROUND)"));
    }
}
