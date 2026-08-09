package managers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerReceiptNumberManagerTest {
    @Test
    void formatsPermanentReturnReceiptNumberWithStoreDeviceAndSharedSequence() {
        assertEquals("RET-0007-0002-000123",
                ServerReceiptNumberManager.formatReturnNumber("0007", "0002", 123));
    }
}
