package models;

import java.math.BigDecimal;
import java.sql.Timestamp;

public record CashDrawerSession(
        long sessionId,
        long cashDrawerId,
        int locationId,
        String deviceId,
        String drawerName,
        String deviceName,
        BigDecimal openingCash,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal cashToRemove,
        BigDecimal variance,
        String status,
        Timestamp openedAt,
        String openedByName,
        Integer mainCashierUserId,
        String mainCashierName,
        Integer currentCashierUserId,
        String currentCashierName,
        Timestamp closedAt,
        String closedByName,
        Integer balancedByUserId,
        String balancedByName,
        String openingNotes,
        String closingNotes,
        String closingReport
) {
    public boolean isOpen() {
        return "OPEN".equalsIgnoreCase(status);
    }
}
