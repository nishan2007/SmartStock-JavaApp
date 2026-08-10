package services;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogBarcodeServiceTest {
    @Test
    void generatedCandidatesAreUniqueValidInternalEan13Values() {
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String barcode = CatalogBarcodeService.generateCandidate();
            assertEquals(13, barcode.length());
            assertTrue(barcode.chars().allMatch(Character::isDigit));
            assertTrue(barcode.startsWith("2"));
            assertEquals(BarcodeNormalizer.checkDigit(barcode.substring(0, 12)), barcode.charAt(12));
            assertTrue(values.add(barcode));
        }
    }
}
