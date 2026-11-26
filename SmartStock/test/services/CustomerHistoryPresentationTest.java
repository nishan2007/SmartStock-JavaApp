package services;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomerHistoryPresentationTest {
    private Map<String,Object> row(String type,int store,long document,long time) {
        Map<String,Object> row=new LinkedHashMap<>();row.put("transactionType",type);row.put("locationId",store);
        row.put("documentId",document);row.put("createdAtEpochMillis",time);return row;
    }
    @Test void combinesDocumentAndLinkedEntryButKeepsPaymentsRefundsAndOtherStores() {
        var sale=row("SALE",1,9577,100);
        var credit=row("SALE_CREDIT",1,9577,100);credit.put("transactionId",55L);credit.put("authorization",Map.of("representativeName","Example"));
        var payment=row("PAYMENT",1,9577,200);
        var refund=row("RETURN",1,9577,300);
        var otherStore=row("SALE_CREDIT",2,9577,100);
        var orphan=row("SALE_CREDIT",1,9999,100);
        var output=CustomerHistoryPresentation.consolidate(List.of(sale,credit,payment,refund,otherStore,orphan));
        assertEquals(5,output.size());assertEquals("RETURN",output.get(0).get("transactionType"));
        var combined=output.stream().filter(r->"SALE".equals(r.get("transactionType"))).findFirst().orElseThrow();
        assertEquals(55L,combined.get("transactionId"));assertNotNull(combined.get("authorization"));
        assertFalse(sale.containsKey("transactionId"),"Presentation must not mutate ledger evidence");
    }
    @Test void paidMarkersAndRemoteOrdersCombineWhileDistinctDocumentKindsStaySeparate() {
        var sale=row("SALE",1,4,100);var paid=row("SALE_PAID",1,4,100);
        var order=row("CUSTOM_ORDER",2,4,100);var orderMarker=row("CUSTOM_ORDER_PAID",2,4,100);orderMarker.put("documentType","ACCOUNT");
        var invoice=row("INVOICE",2,4,100);
        var separatePayment=row("CUSTOM_ORDER_PAYMENT",2,4,200);
        assertEquals(4,CustomerHistoryPresentation.consolidate(List.of(sale,paid,order,orderMarker,invoice,separatePayment)).size());
    }
    @Test void unknownDocumentIdentityIsNeverGuessed() {
        var sale=row("SALE",1,4,100);var ledger=row("SALE_CREDIT",1,4,100);ledger.remove("documentId");
        assertEquals(2,CustomerHistoryPresentation.consolidate(List.of(sale,ledger)).size());
    }
    @Test void linkedOrderIdWinsOverInvoiceDocumentIdWithoutMergingUnrelatedOrders() {
        var order=row("CUSTOM_ORDER",1,40,100);
        var marker=row("CUSTOM_ORDER_PAID",1,90,100);marker.put("documentType","INVOICE");marker.put("customOrderId",40L);
        var invoice=row("INVOICE",1,90,100);
        assertEquals(2,CustomerHistoryPresentation.consolidate(List.of(order,marker,invoice)).size());
    }
}
