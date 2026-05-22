package Receipt;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

public record CustomOrderSlipData(
        String orderNumber,
        String customerName,
        String customerPhone,
        LocalDate dueDate,
        Timestamp createdAt,
        String takenByName,
        String locationName,
        String deviceName,
        String paymentMethod,
        String paymentReference,
        String paymentStatus,
        BigDecimal totalAmount,
        BigDecimal amountPaid,
        BigDecimal balanceDue,
        String orderNotes,
        List<Line> lines
) {
    public record Line(
            String itemName,
            String variantName,
            String details,
            String instructions,
            BigDecimal lineTotal
    ) {
    }
}
