package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MakeSaleCartSizeColumnArchitectureTest {
    @Test
    void cartDisplaysAvailableVariantAttributesInOneColumn() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/MakeASale.java"));

        assertTrue(source.contains("new Object[]{\"ID\", \"Name\", \"Size / Color / Flavor\", \"Description\""));
        assertTrue(source.contains("searchItemAttributes(product.size(), product.color(), product.flavor())"));
        assertTrue(source.contains("private static final int CART_COL_SIZE = 2;"));
        assertTrue(source.contains("searchItemAttributes(product.size(), product.color(), product.flavor()), product.description()"));
        assertTrue(source.contains("searchItemAttributes(item.size(), item.color(), item.flavor()), item.description()"));
        assertTrue(source.contains("setSearchResultColumnWidth(2, 30, Integer.MAX_VALUE, 150)"));
        assertTrue(source.contains("setSearchResultColumnWidth(3, 50, Integer.MAX_VALUE, 220)"));
    }
}
