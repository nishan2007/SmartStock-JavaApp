package services;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanCatalogProductTypeFilterTest {
    @Test
    void acceptsKnownCatalogProductTypesAndAnOmittedFilter() throws Exception {
        assertNull(LanApiServer.normalizeCatalogProductTypeFilter(null));
        assertNull(LanApiServer.normalizeCatalogProductTypeFilter("  "));
        assertEquals("SERVICE", LanApiServer.normalizeCatalogProductTypeFilter("service"));
        assertEquals("NON_INVENTORY", LanApiServer.normalizeCatalogProductTypeFilter("non inventory"));
        assertEquals("INVENTORY", LanApiServer.normalizeCatalogProductTypeFilter("INVENTORY"));
    }

    @Test
    void rejectsUnknownCatalogProductTypes() {
        assertThrows(Exception.class,
                () -> LanApiServer.normalizeCatalogProductTypeFilter("subscription"));
    }

    @Test
    void appliesTheProductTypePredicateBeforeTheCatalogLimit() throws Exception {
        String source = Files.readString(Path.of("src/services/LanApiServer.java"));
        int searchMethod = source.indexOf("private ApiResult searchCatalog");
        int filter = source.indexOf("UPPER(COALESCE(p.product_type", searchMethod);
        int limit = source.indexOf("LIMIT 250", searchMethod);

        assertTrue(searchMethod >= 0);
        assertTrue(filter > searchMethod);
        assertTrue(limit > filter);
    }
}
