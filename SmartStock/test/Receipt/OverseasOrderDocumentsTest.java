package Receipt;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OverseasOrderDocumentsTest {
    private JsonObject detail() {
        return JsonParser.parseString("""
            {"order":{"number":"OS-1-241","customer_name":"Sample customer",
            "customer_phone":"+5926001234","customer_account_number":"CA-000241",
            "taken_by_name":"Quote creator","store_name":"Skeldon","state":"ISSUED",
            "created_at":"2026-11-17T10:00:00-04:00","expected_date":"","shipping":0,
            "total":40000,"paid":20000,"balance":20000,"notes":"","lines":[]}}
            """).getAsJsonObject();
    }
    private JsonObject payment() {
        return JsonParser.parseString("""
            {"payment_action":"PAYMENT","custom_order_payment_id":12,"payment_amount":20000,
            "taken_by_name":"Payment cashier","created_at":"2026-11-17T11:00:00-04:00",
            "payment_method":"CASH","payment_reference":""}
            """).getAsJsonObject();
    }
    @Test void digitalItemSlipIdentifiesCodeWithoutPhysicalDelivery() {
        JsonObject detail=detail();
        detail.getAsJsonObject("order").getAsJsonArray("lines").add(JsonParser.parseString("""
            {"description":"Game code","quantity":1,"unit_price":40000,"digital":true}
            """));
        var slip=OverseasOrderDocuments.build(detail,null);
        assertTrue(slip.lines().get(0).details().contains("Digital item (code; no physical delivery)"));
    }
    @Test void orderSlipIncludesAccountAndOriginalOrderTaker() {
        var slip=OverseasOrderDocuments.build(detail(),null);
        assertEquals("CA-000241",slip.customerAccountNumber());
        assertEquals("Quote creator",slip.takenByName());
    }
    @Test void paymentUsesActualCashierRatherThanQuoteCreator() {
        var slip=OverseasOrderDocuments.build(detail(),payment());
        assertEquals("CA-000241",slip.customerAccountNumber());
        assertEquals("Payment cashier",slip.takenByName());
    }
    @Test void oldSnapshotGetsMissingAccountWithoutReplacingHistoricalAmounts() {
        JsonObject detail=detail(),payment=payment(),snapshot=detail.getAsJsonObject("order").deepCopy();
        snapshot.remove("customer_account_number");snapshot.addProperty("balance",30000);
        payment.add("overseas_receipt",snapshot);
        var slip=OverseasOrderDocuments.build(detail,payment);
        assertEquals("CA-000241",slip.customerAccountNumber());
        assertEquals("Payment cashier",slip.takenByName());
        assertEquals(30000,slip.balanceDue().intValueExact());
        snapshot.addProperty("customer_account_number","CA-HISTORICAL");
        assertEquals("CA-HISTORICAL",OverseasOrderDocuments.build(detail,payment).customerAccountNumber());
    }
}
