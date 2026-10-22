package Receipt;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public record CustomOrderSlipData(
        String orderNumber,
        String customerName,
        String customerPhone,
        String customerAccountNumber,
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
        List<Line> lines,
        String loyaltySummary
) {
    public CustomOrderSlipData(String orderNumber,String customerName,String customerPhone,String customerAccountNumber,LocalDate dueDate,Timestamp createdAt,String takenByName,String locationName,String deviceName,String paymentMethod,String paymentReference,String paymentStatus,BigDecimal totalAmount,BigDecimal amountPaid,BigDecimal balanceDue,String orderNotes,List<Line> lines){this(orderNumber,customerName,customerPhone,customerAccountNumber,dueDate,createdAt,takenByName,locationName,deviceName,paymentMethod,paymentReference,paymentStatus,totalAmount,amountPaid,balanceDue,orderNotes,lines,"");}
    public CustomOrderSlipData withLoyaltySummary(String summary){return new CustomOrderSlipData(orderNumber,customerName,customerPhone,customerAccountNumber,dueDate,createdAt,takenByName,locationName,deviceName,paymentMethod,paymentReference,paymentStatus,totalAmount,amountPaid,balanceDue,orderNotes,lines,summary);}

    public boolean pricePending(){return "PRICE_PENDING".equals(paymentStatus);}
    /** Presentation only: retain stored unit lines and their individual identities. */
    public List<PrintLine> printLines() {
        record Key(String item, String variant, String details, String instructions, BigDecimal price) {}
        LinkedHashMap<Key, PrintLine> groups = new LinkedHashMap<>();
        for (Line line : lines) {
            BigDecimal price = line.lineTotal() == null ? BigDecimal.ZERO : line.lineTotal();
            Key key = new Key(line.itemName(), line.variantName(), line.details(), line.instructions(), price.stripTrailingZeros());
            PrintLine previous = groups.get(key);
            groups.put(key, previous == null
                    ? new PrintLine(line, 1, price)
                    : new PrintLine(previous.line(), previous.quantity() + 1, previous.total().add(price)));
        }
        return new ArrayList<>(groups.values());
    }
    public record PrintLine(Line line, int quantity, BigDecimal total) {
        public String quantitySuffix() { return quantity > 1 ? " x " + quantity : ""; }
    }
    public record Line(
            String itemName,
            String variantName,
            String details,
            String instructions,
            BigDecimal lineTotal
    ) {
    }
}
