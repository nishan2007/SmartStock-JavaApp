package services;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DraftQuotationPreviewValidationTest {
    @Test void invalidAmountsAreRejectedBeforeAnyDatabaseAccess() {
        var invalid = new ServerQuotationInvoiceService.QuotationLineInput(null, "Invalid", "", -1,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, "PICKUP", "", null, null, null, null, null);
        SQLException error = assertThrows(SQLException.class, () ->
                ServerQuotationInvoiceService.previewDraftQuotation(null, 1, null, null, List.of(invalid), false));
        assertTrue(error.getMessage().contains("valid quantities"));
        assertThrows(SQLException.class, () ->
                ServerQuotationInvoiceService.previewDraftQuotation(null, 1, null, null, null, false));
    }
}
