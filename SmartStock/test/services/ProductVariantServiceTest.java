package services;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductVariantServiceTest {
    @Test void normalizesValuesInDefinitionOrderWithoutChangingExistingItems() throws Exception {
        var names=ProductVariantService.validateOptionNames(List.of(" Color ","Size"));
        var options=ProductVariantService.normalizeOptions(names,Map.of("Size"," Small ","Color"," Blue "));
        assertEquals(List.of("Color","Size"),new ArrayList<>(options.keySet()));
        assertEquals(List.of("Blue","Small"),new ArrayList<>(options.values()));
    }
    @Test void duplicateCombinationDetectionIgnoresCaseAndPreservesValueBoundaries() throws Exception {
        var names=List.of("Color","Size");
        assertEquals(ProductVariantService.combinationKey(ProductVariantService.normalizeOptions(names,Map.of("Color","Blue","Size","small"))),
                ProductVariantService.combinationKey(ProductVariantService.normalizeOptions(names,Map.of("Color","blue","Size","SMALL"))));
        assertNotEquals(ProductVariantService.combinationKey(new LinkedHashMap<>(Map.of("A","x|y","B","z"))),
                ProductVariantService.combinationKey(new LinkedHashMap<>(Map.of("A","x","B","y|z"))));
    }
    @Test void permitsOptionalStandardSeparatorsButRequiresOnePerVariant() throws Exception {
        var names=List.of("Size","Color","Flavor");
        var options=ProductVariantService.normalizeOptions(names,Map.of("Size","","Color"," Blue ","Flavor",""));
        assertEquals(List.of("","Blue",""),new ArrayList<>(options.values()));
        assertThrows(LanProductAdminService.RuleViolation.class,()->ProductVariantService.normalizeOptions(names,Map.of("Size","","Color","","Flavor","")));
    }
    @Test void rejectsAmbiguousDefinitionsAndIncompleteCustomCombinations() {
        assertThrows(LanProductAdminService.RuleViolation.class,()->ProductVariantService.validateOptionNames(List.of("Color","color")));
        assertThrows(LanProductAdminService.RuleViolation.class,()->ProductVariantService.normalizeOptions(List.of("Color","Size"),Map.of("Color","Blue")));
        assertThrows(LanProductAdminService.RuleViolation.class,()->ProductVariantService.normalizeOptions(List.of("Color"),Map.of("Color"," ")));
        assertThrows(LanProductAdminService.RuleViolation.class,()->ProductVariantService.normalizeOptions(List.of("Color","Package"),Map.of("Color","Blue","Package","")));
    }
}
