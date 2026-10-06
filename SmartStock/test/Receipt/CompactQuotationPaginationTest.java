package Receipt;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class CompactQuotationPaginationTest {
    private List<ServerQuotationInvoiceDocumentBuilder.DocumentLine> lines(int count, String description) {
        var result = new ArrayList<ServerQuotationInvoiceDocumentBuilder.DocumentLine>();
        for (int i=0;i<count;i++) result.add(new ServerQuotationInvoiceDocumentBuilder.DocumentLine(1,null,null,
                description + i, BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.TEN));
        return result;
    }
    @Test void fitsThirtyFourShortItemsAndPreservesOverflowOrder() {
        var items=lines(35,"Product ");
        var pages=ServerQuotationInvoiceDocumentBuilder.compactPages(items);
        assertEquals(2,pages.size());
        assertEquals(34,pages.get(0).size());
        assertEquals(items.get(34),pages.get(1).get(0));
    }
    @Test void wrapsNotesIntoThePageBudget() {
        var pages=ServerQuotationInvoiceDocumentBuilder.compactPages(lines(20,"Coffee mug\nPersonalized teacher appreciation gift "));
        assertEquals(2,pages.size());
        assertEquals(17,pages.get(0).size());
        assertEquals(3,pages.get(1).size());
    }
    @Test void emptyDocumentStillHasOnePage() {
        assertEquals(1,ServerQuotationInvoiceDocumentBuilder.compactPages(List.of()).size());
    }
}
