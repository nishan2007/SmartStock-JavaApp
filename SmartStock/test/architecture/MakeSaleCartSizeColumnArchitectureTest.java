package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MakeSaleCartSizeColumnArchitectureTest {
    @Test
    void cartDisplaysProductSizeAsItsOwnColumn() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/MakeASale.java"));

        assertTrue(source.contains("new Object[]{\"ID\", \"Name\", \"Size\", \"Description\""));
        assertTrue(source.contains("private static final int CART_COL_SIZE = 2;"));
        assertTrue(source.contains("product.name(), product.size(), product.description()"));
        assertTrue(source.contains("item.productName(), item.size(), item.description()"));
        assertTrue(source.contains("setSearchResultColumnWidth(2, 30, Integer.MAX_VALUE, 110)"));
        assertTrue(source.contains("setSearchResultColumnWidth(3, 50, Integer.MAX_VALUE, 220)"));
    }
}
