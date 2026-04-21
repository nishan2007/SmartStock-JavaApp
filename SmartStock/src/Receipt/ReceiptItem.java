package Receipt;

import java.math.BigDecimal;

public class ReceiptItem {
    private final String name;
    private final String sku;
    private final int quantity;
    private final BigDecimal originalUnitPrice;
    private final BigDecimal finalUnitPrice;
    private final BigDecimal discountPercent;
    private final BigDecimal lineTotal;

    public ReceiptItem(
            String name,
            String sku,
            int quantity,
            BigDecimal originalUnitPrice,
            BigDecimal finalUnitPrice,
            BigDecimal discountPercent,
            BigDecimal lineTotal
    ) {
        this.name = name == null ? "" : name;
        this.sku = sku == null ? "" : sku;
        this.quantity = quantity;
        this.originalUnitPrice = money(originalUnitPrice);
        this.finalUnitPrice = money(finalUnitPrice);
        this.discountPercent = money(discountPercent);
        this.lineTotal = money(lineTotal);
    }

    public String getName() {
        return name;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getOriginalUnitPrice() {
        return originalUnitPrice;
    }

    public BigDecimal getFinalUnitPrice() {
        return finalUnitPrice;
    }

    public BigDecimal getDiscountPercent() {
        return discountPercent;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
