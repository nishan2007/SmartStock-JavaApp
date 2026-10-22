package services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductSearchSchemaTest {
    @Test void ordinaryProductSearchNeverReferencesCustomShirtColumns() {
        for (String query : new String[]{"", "blue shirt"}) {
            String predicate = ProductSearchHelper.predicate("p", 1, query);
            assertFalse(predicate.contains("p.is_shirt"));
            assertFalse(predicate.contains("p.shirt_designer_config"));
        }
        String index = ProductSearchHelper.searchableTextExpression("p", 1);
        assertFalse(index.contains("p.is_shirt"));
        assertFalse(index.contains("p.shirt_designer_config"));
    }

    @Test void customItemAndVariantSearchKeepShirtDesignerTermsOnTheParentItem() {
        for (String expression : new String[]{
                ProductSearchHelper.customItemSearchableTextExpression("coi"),
                ProductSearchHelper.customItemPredicate("coi", "dry fit"),
                ProductSearchHelper.customVariantPredicate("coi", "coiv", "collared")}) {
            assertTrue(expression.contains("coi.is_shirt"));
            assertTrue(expression.contains("coi.shirt_designer_config"));
            assertTrue(expression.contains("dry-fit dry fit dri fit dry fir"));
            assertFalse(expression.contains("coiv.is_shirt"));
            assertFalse(expression.contains("coiv.shirt_designer_config"));
        }
    }
}
