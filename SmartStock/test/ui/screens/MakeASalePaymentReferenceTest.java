package ui.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MakeASalePaymentReferenceTest {
    @Test
    void referenceFieldIsRequiredForCardChequeAndMmg() {
        assertTrue(MakeASale.requiresPaymentReference("CARD"));
        assertTrue(MakeASale.requiresPaymentReference("CHEQUE"));
        assertTrue(MakeASale.requiresPaymentReference("MMG"));
        assertFalse(MakeASale.requiresPaymentReference("CASH"));
        assertFalse(MakeASale.requiresPaymentReference("ACCOUNT"));
        assertFalse(MakeASale.requiresPaymentReference(null));
    }
}
