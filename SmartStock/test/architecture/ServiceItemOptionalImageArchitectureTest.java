package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceItemOptionalImageArchitectureTest {
    @Test
    void newAndEditedServiceItemsDoNotRequireAnImage() throws Exception {
        String newItemSource = Files.readString(Path.of("src/ui/screens/NewItem.java"));
        String editItemSource = Files.readString(Path.of("src/ui/screens/EditItem.java"));

        assertTrue(newItemSource.contains("!\"SERVICE\".equals(productType) && imageSelector.getImageUrl().isBlank()"));
        assertTrue(editItemSource.contains("!\"SERVICE\".equals(productType) && imageSelector.getImageUrl().isBlank()"));
        assertTrue(newItemSource.contains("optional for services"));
        assertTrue(editItemSource.contains("optional for services"));
    }
}
