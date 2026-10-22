package services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/** Pure, currency-safe loyalty calculations shared by server previews and settlement. */
public final class LoyaltyPolicy {
    private LoyaltyPolicy() { }
    public record Settings(long version, boolean enabled, Integer authorityLocationId,
                           BigDecimal spendPerAward, long pointsPerAward, BigDecimal pointValue,
                           long minimumRedemption, BigDecimal redemptionCapPercent) {
        public Settings {
            if (version < 0 || spendPerAward == null || spendPerAward.signum() <= 0
                    || spendPerAward.scale() > 2 || pointsPerAward < 1 || pointsPerAward > 1_000_000
                    || pointValue == null || pointValue.compareTo(new BigDecimal("0.01")) < 0
                    || pointValue.scale() > 2 || minimumRedemption < 1 || redemptionCapPercent == null
                    || redemptionCapPercent.signum() <= 0 || redemptionCapPercent.compareTo(new BigDecimal("100")) > 0)
                throw new IllegalArgumentException("Enter positive loyalty amounts, whole points, and a cap between 0 and 100%.");
            if (enabled && (authorityLocationId == null || authorityLocationId < 1))
                throw new IllegalArgumentException("Select the loyalty authority store before enabling loyalty.");
        }
        public static Settings defaults() {
            return new Settings(0, false, null, new BigDecimal("100.00"), 1,
                    new BigDecimal("1.00"), 100, new BigDecimal("20.00"));
        }
        public BigDecimal rewardPercent() {
            return pointValue.multiply(BigDecimal.valueOf(pointsPerAward)).multiply(new BigDecimal("100"))
                    .divide(spendPerAward, 4, RoundingMode.HALF_UP);
        }
    }
    public record Lot(UUID id, long available, BigDecimal value) { }
    public record Portion(UUID lotId, long points, BigDecimal value) { }
    public record Redemption(long points, BigDecimal discount, List<Portion> portions) { }

    public static long earn(Settings settings, BigDecimal eligible) {
        if (!settings.enabled() || eligible == null || eligible.signum() <= 0) return 0;
        // Round once per document, rather than losing rewards on individual lines.
        return eligible.multiply(BigDecimal.valueOf(settings.pointsPerAward()))
                .divide(settings.spendPerAward(), 0, RoundingMode.DOWN).longValueExact();
    }
    public static Redemption redeem(Settings settings, long requested, BigDecimal eligible, List<Lot> fifoLots) {
        if (!settings.enabled()) throw new IllegalArgumentException("Loyalty is disabled.");
        if (requested < settings.minimumRedemption()) throw new IllegalArgumentException("Minimum redemption is " + settings.minimumRedemption() + " points.");
        if (eligible == null || eligible.signum() <= 0) throw new IllegalArgumentException("No eligible spending for loyalty redemption.");
        long left = requested;
        BigDecimal discount = BigDecimal.ZERO;
        var portions = new java.util.ArrayList<Portion>();
        for (Lot lot : fifoLots) {
            long count = Math.min(left, Math.max(0, lot.available()));
            if (count > 0) {
                portions.add(new Portion(lot.id(), count, lot.value()));
                discount = discount.add(lot.value().multiply(BigDecimal.valueOf(count)));
                left -= count;
            }
            if (left == 0) break;
        }
        if (left > 0) throw new IllegalArgumentException("Insufficient available loyalty points.");
        BigDecimal cap = eligible.multiply(settings.redemptionCapPercent()).divide(new BigDecimal("100"), 2, RoundingMode.DOWN);
        if (discount.compareTo(cap) > 0) throw new IllegalArgumentException("Points exceed the loyalty limit of " + cap.toPlainString() + " for this purchase.");
        return new Redemption(requested, discount.setScale(2, RoundingMode.UNNECESSARY), List.copyOf(portions));
    }
}
