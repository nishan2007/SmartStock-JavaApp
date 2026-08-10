package ui.screens;

import org.junit.jupiter.api.Test;
import services.QuotationInvoiceViewService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuotationCustomerSelectionTest {
    private static final QuotationInvoiceViewService.CustomerOption DECKERS =
            new QuotationInvoiceViewService.CustomerOption(7, "BA-000001", "Deckers", true);

    @Test void retainsExplicitCustomerWhenEditableComboContainsItsDisplayText() {
        assertEquals(DECKERS, Quotations.QuotationEditor.resolveCustomerSelection(
                DECKERS.toString(), DECKERS.toString(), List.of(), DECKERS));
    }

    @Test void resolvesExactCustomerFromRefreshedResults() {
        assertEquals(DECKERS, Quotations.QuotationEditor.resolveCustomerSelection(
                DECKERS.toString(), DECKERS.toString(), List.of(DECKERS), null));
    }

    @Test void doesNotReuseRememberedCustomerAfterUserTypesSomethingElse() {
        assertNull(Quotations.QuotationEditor.resolveCustomerSelection(
                "Different customer", "Different customer", List.of(), DECKERS));
    }
}
