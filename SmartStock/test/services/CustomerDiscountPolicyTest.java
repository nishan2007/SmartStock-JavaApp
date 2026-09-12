package services;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class CustomerDiscountPolicyTest {
    @Test void automaticRateWinsOnlyWhenLargerAndEnabled(){assertEquals(new BigDecimal("15.0000"),CustomerDiscountPolicy.effective(new BigDecimal("5"),true,new BigDecimal("15"),true));assertEquals(new BigDecimal("20.0000"),CustomerDiscountPolicy.effective(new BigDecimal("20"),true,new BigDecimal("15"),true));assertEquals(new BigDecimal("5.0000"),CustomerDiscountPolicy.effective(new BigDecimal("5"),true,new BigDecimal("15"),false));}
    @Test void calculatesCurrencyAmountBeforeTax(){assertEquals(new BigDecimal("15.00"),CustomerDiscountPolicy.amount(new BigDecimal("100"),new BigDecimal("15")));}
    @Test void validatesBounds(){assertThrows(IllegalArgumentException.class,()->CustomerDiscountPolicy.validated(new BigDecimal("-1")));assertThrows(IllegalArgumentException.class,()->CustomerDiscountPolicy.validated(new BigDecimal("100.01")));assertDoesNotThrow(()->CustomerDiscountPolicy.validated(new BigDecimal("100")));}
}
