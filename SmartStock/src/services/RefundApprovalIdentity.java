package services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.TreeMap;

/** Builds the exact, stable resource identity bound to a return override approval. */
public final class RefundApprovalIdentity {
    private RefundApprovalIdentity() {
    }

    public static String build(int saleId, BigDecimal amount, Map<Integer, Integer> quantities) {
        TreeMap<Integer, Integer> ordered = new TreeMap<>();
        if (quantities != null) {
            quantities.forEach((saleItemId, quantity) -> {
                if (saleItemId != null && quantity != null && saleItemId > 0 && quantity > 0) {
                    ordered.put(saleItemId, quantity);
                }
            });
        }
        StringBuilder lines = new StringBuilder();
        ordered.forEach((saleItemId, quantity) -> {
            if (!lines.isEmpty()) {
                lines.append(',');
            }
            lines.append(saleItemId).append(':').append(quantity);
        });
        BigDecimal normalized = (amount == null ? BigDecimal.ZERO : amount)
                .setScale(2, RoundingMode.HALF_UP);
        return "sale=" + saleId + ";amount=" + normalized.toPlainString() + ";lines=" + lines;
    }

    public static String withReason(String resourceIdentity, String reason) {
        return (resourceIdentity == null ? "" : resourceIdentity)
                + "|reason=" + (reason == null ? "" : reason.trim());
    }
}
