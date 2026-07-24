package ui.screens;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyPreferencesDesignTest {
    @Test
    void lazyPreferenceCardsReceiveTheActiveTheme() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/CompanyCustomization.java"));

        int cardAdded = source.indexOf("rightContentPanel.add(card, cardKey)");
        int themeApplied = source.indexOf("ThemeManager.applyToWindow(this)", cardAdded);
        assertTrue(cardAdded >= 0);
        assertTrue(themeApplied > cardAdded);
    }

    @Test
    void companyPreferencesUsesSharedPaletteAndActionStyles() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/CompanyCustomization.java"));
        String identity = Files.readString(
                Path.of("src/ui/screens/companyprefs/CompanyIdentityPanel.java")
        );

        assertTrue(source.contains("DeckersSwing.styleUtilityButton(saveButton, DeckersPalette.LIME)"));
        assertTrue(source.contains("setMinimumSize(new Dimension(980, 680))"));
        assertTrue(identity.contains("DeckersPalette.surface()"));
        assertTrue(identity.contains("DeckersSwing.styleField(companyNameField)"));
    }

    @Test
    void everyPreferenceCardUsesTheSharedPageDecorator() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/CompanyCustomization.java"));

        assertTrue(source.contains("JComponent card = decoratePreferenceCard(cardKey, cardContent)"));
        assertTrue(source.contains("stylePreferenceControls(content)"));
        assertTrue(source.contains("preferenceSectionDescription(cardKey)"));
        assertTrue(source.contains("DeckersSwing.styleTable(table, DeckersPalette.ORANGE)"));
        assertTrue(source.contains("preferenceButtonAccent(button.getText())"));
    }

    @Test
    void navigationTreeUsesTextOnlyThemeAwareRendering() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/CompanyCustomization.java"));

        assertTrue(source.contains("new PreferenceTreeCellRenderer()"));
        assertTrue(source.contains("tree.setBackground(DeckersPalette.surface())"));
        assertTrue(source.contains(
                "navigationScroll.getViewport().setBackground(DeckersPalette.surface())"
        ));
        String renderer = Files.readString(
                Path.of("src/ui/components/PreferenceTreeCellRenderer.java")
        );
        assertTrue(renderer.contains("setBackgroundNonSelectionColor(tree.getBackground())"));
        assertTrue(renderer.contains("setBackgroundSelectionColor("));
        assertTrue(renderer.contains("setOpaque(true)"));
        assertTrue(renderer.contains("DeckersPalette.tilePressed(DeckersPalette.ORANGE)"));
        assertTrue(renderer.contains("BorderFactory.createMatteBorder(0, 4, 0, 0"));
    }
}
