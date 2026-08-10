package services;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BarcodeNormalizerTest {
    @Test void normalizesScannerSeparatorsWithoutLosingLeadingZeros() {
        assertEquals("0036000291452", BarcodeNormalizer.normalize(" 0036-000 291452 "));
        assertEquals("ABC123", BarcodeNormalizer.normalize(" abc-123 "));
    }

    @Test void addsAndRemovesValidUpcCheckDigit() {
        assertEquals(List.of("03600029145", "036000291452"),
                BarcodeNormalizer.lookupCandidates("03600029145"));
        assertEquals(List.of("036000291452", "03600029145", "0360002914522"),
                BarcodeNormalizer.lookupCandidates("036000291452"));
    }

    @Test void supportsEan13DataAndValidatedCheckDigit() {
        assertEquals(List.of("400638133393", "4006381333931"),
                BarcodeNormalizer.lookupCandidates("400638133393"));
        assertEquals(List.of("4006381333931", "400638133393"),
                BarcodeNormalizer.lookupCandidates("4006381333931"));
    }

    @Test void doesNotAlterNonNumericIdentifiersBeyondNormalization() {
        assertEquals(List.of("CODE128ABC"), BarcodeNormalizer.lookupCandidates("CODE-128 ABC"));
    }
}
