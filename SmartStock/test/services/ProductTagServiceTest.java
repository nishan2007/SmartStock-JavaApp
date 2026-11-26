package services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductTagServiceTest {
    @Test void namesAreNormalizedAndBounded() {
        assertEquals("School Wear",ProductTagService.cleanName("  School   Wear  "));
        assertThrows(IllegalArgumentException.class,()->ProductTagService.cleanName("  "));
        assertThrows(IllegalArgumentException.class,()->ProductTagService.cleanName("a".repeat(81)));
        assertThrows(IllegalArgumentException.class,()->ProductTagService.cleanName("Bad\u0000Tag"));
    }
    @Test void staffSearchIncludesTagsWithoutChangingCustomItems() {
        assertTrue(ProductSearchHelper.searchableTextExpression("p",1).contains("product_tags"));
        assertFalse(ProductSearchHelper.customItemSearchableTextExpression("coi").contains("product_tags"));
        assertTrue(ProductSearchHelper.textMatches("White polo School Wear Embroidery Ready","school polo"));
    }
}
