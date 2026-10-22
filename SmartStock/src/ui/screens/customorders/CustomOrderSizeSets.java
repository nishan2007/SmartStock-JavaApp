package ui.screens.customorders;

import com.google.gson.Gson;
import services.LanJson;
import services.CustomOrderDataService.CustomItemOption;
import services.CustomOrderDataService.OrderLineRequest;
import utils.CurrencyFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Expands a validated shared design into the existing per-shirt server contract. */
final class CustomOrderSizeSets {
    private static final Gson JSON = LanJson.create();
    record Unit(OrderLineRequest request, services.CustomOrderDataService.VariantOption variant) {}

    static BigDecimal unitPrice(BigDecimal original, BigDecimal percent, boolean round) {
        if (percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException("Discount must be between 0 and 100%.");
        BigDecimal reduction = original.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal result = CurrencyFormatter.normalize(original.subtract(reduction).max(BigDecimal.ZERO));
        return result;
    }
    static List<Unit> expand(CustomItemOption item, OrderLineRequest shared,
                             List<CustomOrderSizeSetEditor.SizeQuantity> sizes, boolean pending, boolean round) {
        if (!CustomOrderSizeSetEditor.supportsSizes(item) || sizes.isEmpty())
            throw new IllegalArgumentException("Select a per-item priced catalogue item and its sizes.");
        Set<Long> seen = new HashSet<>(); int total = 0;
        String colour = sizes.get(0).variant().effectiveColor();
        List<Unit> result = new ArrayList<>();
        for (var size : sizes) {
            var variant = size.variant();
            if (variant.variantId() == null || !seen.add(variant.variantId())) throw new IllegalArgumentException("Select each size only once.");
            if (variant.effectiveSize() == null || variant.effectiveSize().isBlank() || !Objects.equals(colour, variant.effectiveColor()))
                throw new IllegalArgumentException("All sizes in a set must have the same colour.");
            total = Math.addExact(total, size.quantity());
            if (total > 5000) throw new IllegalArgumentException("A shirt set may contain up to 5,000 shirts.");
            BigDecimal configured = variant.fixedPrice() == null ? item.fixedPrice() : variant.fixedPrice();
            if (!pending && (configured == null || configured.signum() < 0)) throw new IllegalArgumentException("This size has no valid catalogue price.");
            BigDecimal base = pending ? BigDecimal.ZERO : configured;
            BigDecimal original = base.add(shared.printCharge() == null ? BigDecimal.ZERO : shared.printCharge());
            BigDecimal percent = pending || shared.lineDiscountPercent() == null ? BigDecimal.ZERO : shared.lineDiscountPercent();
            var json = JSON.toJsonTree(shared).getAsJsonObject();
            json.addProperty("customVariantId", variant.variantId()); json.addProperty("variantName", variant.name());
            json.addProperty("itemSize", variant.effectiveSize()); json.addProperty("itemColor", variant.effectiveColor());
            json.addProperty("baseItemPrice", base); json.addProperty("areaPrice", base); json.addProperty("originalBasePrice", configured);
            json.addProperty("originalLineTotal", original); json.addProperty("lineDiscountPercent", percent);
            json.addProperty("lineDiscountAmount", original.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            json.addProperty("unitPrice", pending ? BigDecimal.ZERO : unitPrice(original, percent, round));
            json.remove("priceOverridePrice"); json.remove("priceOverrideReason"); json.remove("priceOverrideApprovalToken");
            OrderLineRequest request = JSON.fromJson(json, OrderLineRequest.class);
            for (int n = 0; n < size.quantity(); n++) result.add(new Unit(request, variant));
        }
        return List.copyOf(result);
    }
}
