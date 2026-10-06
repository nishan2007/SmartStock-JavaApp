package services;

import java.math.BigDecimal;

/** Order-local description of an item supplied by the customer. */
public record CustomerSuppliedItem(String itemType, String color, String size, String brand) {
    public CustomerSuppliedItem {
        itemType = clean(itemType);
        color = clean(color);
        size = clean(size);
        brand = clean(brand);
        if (itemType.isBlank()) throw new IllegalArgumentException("Enter an item type.");
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    public String name() { return "Customer Item — " + itemType; }
    public String details() {
        String result = "Customer-supplied item | Item Type: " + itemType;
        if (!color.isEmpty()) result += " | Color: " + color;
        if (!size.isEmpty()) result += " | Size: " + size;
        if (!brand.isEmpty()) result += " | Brand: " + brand;
        return result;
    }

    public static void validate(ServerCustomOrderDataService.OrderLineRequest line) {
        if (line.customerItem() == null) return;
        if (line.customerItem().itemType() == null || line.customerItem().itemType().isBlank())
            throw new IllegalArgumentException("Enter an item type.");
        if (line.customItemId() != null || line.customVariantId() != null)
            throw new IllegalArgumentException("Customer items cannot reference store stock.");
        if (nonzero(line.baseItemPrice()) || nonzero(line.originalBasePrice()) || nonzero(line.areaPrice())
                || line.priceOverridePrice() != null || (line.priceOverrideReason() != null && !line.priceOverrideReason().isBlank()))
            throw new IllegalArgumentException("Customer items must have a zero base price.");
        if (line.printAddons() == null || line.printAddons().isEmpty())
            throw new IllegalArgumentException("Add at least one add-on to the customer item.");
        BigDecimal total = BigDecimal.ZERO;
        for (var addon : line.printAddons()) {
            if (addon == null || addon.printCharge() == null || addon.printCharge().signum() < 0)
                throw new IllegalArgumentException("Enter a valid add-on charge.");
            total = total.add(addon.printCharge());
        }
        if (line.originalLineTotal() == null || line.originalLineTotal().compareTo(total) != 0
                || line.printCharge() == null || line.printCharge().compareTo(total) != 0)
            throw new IllegalArgumentException("Customer item total must match its add-ons.");
    }
    private static boolean nonzero(BigDecimal value) { return value != null && value.signum() != 0; }
}
