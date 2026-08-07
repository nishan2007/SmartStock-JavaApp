package services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void parsesExistingStoresReturnedByTheServerOnlyCloudApi() throws Exception {
        var stores = ServerStoreSetupService.parseCloudStores("""
                [{"location_id":7,"name":"Main Store","receipt_store_code":"0007",
                  "timezone":"America/Guyana"}]
                """);

        assertEquals(1, stores.size());
        assertEquals(7, stores.get(0).locationId());
        assertEquals("Main Store (0007)", stores.get(0).toString());
        assertThrows(Exception.class, () -> ServerStoreSetupService.parseCloudStores("{}"));
    }
}
