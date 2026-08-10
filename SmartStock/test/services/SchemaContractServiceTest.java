package services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaContractServiceTest {
    @Test
    void recognizesBothReleasedPreReturnReceiptBaselines() {
        assertTrue(SchemaContractService.isPreReturnReceiptFingerprint(
                "61fab3e60b61c1dfc6aea5b8087c81e946b64ba415bdb1cc08d642677131be9f"));
        assertTrue(SchemaContractService.isPreReturnReceiptFingerprint(
                "09e4ddc87f31f8add4d7550b7058cc087c086b6872aec3853233b6e4dfb47584"));
        assertFalse(SchemaContractService.isPreReturnReceiptFingerprint(
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    }
}
