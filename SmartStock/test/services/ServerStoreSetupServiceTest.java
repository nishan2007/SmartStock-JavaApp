package services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerStoreSetupServiceTest {
    @Test
    void acceptsOnlyFourDigitNonzeroStoreCodes() {
        assertTrue(ServerStoreSetupService.validStoreCode("0001"));
        assertTrue(ServerStoreSetupService.validStoreCode("9999"));
        assertFalse(ServerStoreSetupService.validStoreCode("0000"));
        assertFalse(ServerStoreSetupService.validStoreCode("1"));
        assertFalse(ServerStoreSetupService.validStoreCode("ABCDE"));
    }
}
