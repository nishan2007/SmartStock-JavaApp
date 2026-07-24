package services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LanSalesPaymentReferenceTest {
    @Test
    void serverRequiresTheSamePaymentReferencesAsTheRegister() {
        assertTrue(LanSalesService.requiresPaymentReference("CARD"));
        assertTrue(LanSalesService.requiresPaymentReference("CHEQUE"));
        assertTrue(LanSalesService.requiresPaymentReference("MMG"));
        assertFalse(LanSalesService.requiresPaymentReference("CASH"));
        assertFalse(LanSalesService.requiresPaymentReference("ACCOUNT"));
        assertFalse(LanSalesService.requiresPaymentReference(null));
    }
}
