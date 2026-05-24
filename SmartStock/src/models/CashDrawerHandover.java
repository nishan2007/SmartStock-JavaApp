package models;

import java.math.BigDecimal;
import java.sql.Timestamp;

public record CashDrawerHandover(
        long handoverId,
        long sessionId,
        String fromUserName,
        String toUserName,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal variance,
        Timestamp handedOverAt,
        String notes
) {
}
