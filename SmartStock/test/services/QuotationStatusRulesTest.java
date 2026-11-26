package services;

import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class QuotationStatusRulesTest {
    @Test void correctionsAreAllowedBeforeAcceptance() {
        assertTrue(ServerQuotationInvoiceService.isEditableQuotationStatus("DRAFT"));
        assertTrue(ServerQuotationInvoiceService.isEditableQuotationStatus("ISSUED"));
        for (String status : new String[]{"ACCEPTED", "CANCELLED", "EXPIRED"})
            assertFalse(ServerQuotationInvoiceService.isEditableQuotationStatus(status));
    }

    @Test void cancellationCannotInvalidateAnAcceptedInvoice() {
        for (String status : new String[]{"DRAFT", "ISSUED"})
            assertDoesNotThrow(() -> ServerQuotationInvoiceService.validateQuotationStatusChange(status, "CANCELLED"));
        for (String status : new String[]{"ACCEPTED", "CANCELLED", "EXPIRED"})
            assertThrows(SQLException.class,
                    () -> ServerQuotationInvoiceService.validateQuotationStatusChange(status, "CANCELLED"));
    }

    @Test void issuingCannotReopenCancelledOrAcceptedQuotes() {
        assertDoesNotThrow(() -> ServerQuotationInvoiceService.validateQuotationStatusChange("DRAFT", "ISSUED"));
        for (String status : new String[]{"ISSUED", "ACCEPTED", "CANCELLED"})
            assertThrows(SQLException.class,
                    () -> ServerQuotationInvoiceService.validateQuotationStatusChange(status, "ISSUED"));
    }
}
