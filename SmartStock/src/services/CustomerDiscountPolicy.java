package services;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Shared deterministic math for account-configured document discounts. */
public final class CustomerDiscountPolicy {
    private static final BigDecimal HUNDRED=BigDecimal.valueOf(100);
    private CustomerDiscountPolicy(){}
    public static BigDecimal validated(BigDecimal value){BigDecimal rate=value==null?BigDecimal.ZERO:value;if(rate.signum()<0||rate.compareTo(HUNDRED)>0)throw new IllegalArgumentException("Discount must be between 0 and 100%.");return rate.setScale(4,RoundingMode.HALF_UP);}
    public static BigDecimal effective(BigDecimal manual,boolean enabled,BigDecimal configured,boolean apply){return validated(manual).max(enabled&&apply?validated(configured):BigDecimal.ZERO);}
    public static BigDecimal amount(BigDecimal base,BigDecimal rate){BigDecimal safe=base==null?BigDecimal.ZERO:base.max(BigDecimal.ZERO);return safe.multiply(validated(rate)).divide(HUNDRED,2,RoundingMode.HALF_UP);}
}
