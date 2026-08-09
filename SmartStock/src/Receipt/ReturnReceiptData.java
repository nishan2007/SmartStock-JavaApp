package Receipt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReturnReceiptData(long returnId, String returnReceiptNumber, int saleId, String originalReceiptNumber,
                                Instant returnTime, String cashierName, String refundMethod,
                                BigDecimal refundAmount, String reason, List<ReceiptItem> items) {
    public ReturnReceiptData {
        returnReceiptNumber = returnReceiptNumber == null ? "" : returnReceiptNumber;
        originalReceiptNumber = originalReceiptNumber == null ? "" : originalReceiptNumber;
        cashierName = cashierName == null ? "" : cashierName;
        refundMethod = refundMethod == null ? "" : refundMethod;
        refundAmount = refundAmount == null ? BigDecimal.ZERO : refundAmount;
        reason = reason == null ? "" : reason;
        items = List.copyOf(items == null ? List.of() : items);
    }
}
