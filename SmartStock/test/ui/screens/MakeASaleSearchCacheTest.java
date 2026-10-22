package ui.screens;

import org.junit.jupiter.api.Test;
import services.LanApiClient;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MakeASaleSearchCacheTest {
    private static LanApiClient.CatalogProduct product(int id, String searchIndex) {
        return new LanApiClient.CatalogProduct(id, "Bottle", "", "", "B1",
                BigDecimal.TEN, "INVENTORY", null, 8, "", "", searchIndex,
                null, "", "", null, "");
    }

    @Test void missingSearchIndexRequiresServerLookupEvenAfterACachedMatch() {
        assertNull(MakeASale.filterCachedProducts(
                List.of(product(1, "BOTTLE"), product(2, null)), "bottle"));
        assertNull(MakeASale.filterCachedProducts(List.of(product(1, "")), "bottle"));
    }

    @Test void dropdownCanBrowseOlderCatalogsWithoutASearchIndex() {
        var rows = MakeASale.filterCachedProducts(List.of(product(1, null)), "");
        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0)[0]);
    }

    @Test void filtersAllSearchTokensAndPreservesTheSalePrice() {
        var rows = MakeASale.filterCachedProducts(
                List.of(product(1, "BLUE BOTTLE"), product(2, "RED BOTTLE")), "bottle blue");
        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0)[0]);
        assertEquals(BigDecimal.TEN, rows.get(0)[5]);
    }
}
