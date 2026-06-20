package utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Canonical display and normalization rules for SmartStock monetary values.
 * Monetary values are whole currency units; fractions round half-up.
 */
public final class CurrencyFormatter {
    private CurrencyFormatter() {
    }

    public static BigDecimal normalize(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(0, RoundingMode.HALF_UP);
    }

    public static String format(BigDecimal value) {
        return create().format(normalize(value));
    }

    public static String format(Number value) {
        if (value == null) {
            return format(BigDecimal.ZERO);
        }
        return format(value instanceof BigDecimal decimal
                ? decimal
                : new BigDecimal(value.toString()));
    }

    public static NumberFormat create() {
        return create(Locale.getDefault());
    }

    public static NumberFormat create(Locale locale) {
        NumberFormat currency = NumberFormat.getCurrencyInstance(locale == null ? Locale.getDefault() : locale);
        currency.setMinimumFractionDigits(0);
        currency.setMaximumFractionDigits(0);
        currency.setRoundingMode(RoundingMode.HALF_UP);
        return currency;
    }
}
