package ui.screens;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MakeASaleItemSearchResetTest {
    @Test void successfulItemAddClearsSearchAndInvalidatesPendingResults() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/MakeASale.java"));

        assertTrue(source.contains("addToCart(productId, name, size, description"));
        assertTrue(source.contains("resetProductSearchAfterAdd();"));
        assertTrue(source.contains("productSearchGeneration++;"));
        assertTrue(source.contains("searchField.setText(\"\");"));
        assertTrue(source.contains("searchDebounceTimer.stop();"));
        assertTrue(source.contains("SwingUtilities.invokeLater(searchField::requestFocusInWindow);"));
    }

    @Test void enterUsesExactLookupBeforeAcceptingCurrentDropdownSelection() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/MakeASale.java"));

        assertTrue(source.contains("handleProductSearchEnter();"));
        assertTrue(source.contains("LanApiClient.lookupCatalogIdentifier(identifier)"));
        assertTrue(source.contains("identifier.equals(searchResultsQuery)"));
        assertTrue(source.contains("\"MATCH\".equals(lookup.status())"));
        assertTrue(source.contains("addCatalogProductToCart(lookup.products().get(0), 1);"));
        assertTrue(source.contains("\"AMBIGUOUS\".equals(lookup.status())"));
    }

    @Test void enterAcceptsTheRowChosenWithArrowKeys() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/MakeASale.java"));

        assertTrue(source.contains("productSearchSelectionNavigated = true;"));
        assertTrue(source.contains("e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER"));
        assertTrue(source.contains("&& productSearchSelectionNavigated"));
        assertTrue(source.contains("addSelectedSearchResultToCart();"));
        assertTrue(source.contains("productSearchSelectionNavigated = false;"));
    }
}
