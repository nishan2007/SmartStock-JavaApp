package services;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderExchangeServiceTest {
    private static BigDecimal n(String value){return new BigDecimal(value);}
    private static void amount(String expected,BigDecimal actual){assertEquals(0,n(expected).compareTo(actual));}

    @Test void higherPriceCreatesBalanceWithoutInventingPayment() {
        var p=CustomOrderExchangeService.price(n("15000"),n("18000"),n("0"),n("0"),n("15000"),n("15000"));
        amount("18000",p.orderTotal());amount("15000",p.paid());amount("3000",p.balance());amount("0",p.refund());
    }
    @Test void lowerPriceRefundsOnlyTheOverpayment() {
        var p=CustomOrderExchangeService.price(n("15000"),n("12000"),n("0"),n("0"),n("15000"),n("14000"));
        amount("2000",p.refund());amount("12000",p.paid());amount("0",p.balance());
        var unpaid=CustomOrderExchangeService.price(n("15000"),n("12000"),n("0"),n("0"),n("15000"),n("5000"));
        amount("0",unpaid.refund());amount("7000",unpaid.balance());
    }
    @Test void preservesAddonsAndDiscountUsingExistingCurrencyRounding() {
        var p=CustomOrderExchangeService.price(n("900"),n("805"),n("200"),n("10"),n("1900"),n("1000"));
        amount("100.50",p.discount());amount("905",p.lineTotal());amount("1905",p.orderTotal());amount("905",p.balance());
    }
    @Test void equalPriceKeepsPaidOrderPaid() {
        var p=CustomOrderExchangeService.price(n("15000"),n("15000"),n("0"),n("0"),n("15000"),n("15000"));
        amount("0",p.balance());amount("0",p.refund());amount("15000",p.orderTotal());
    }
    @Test void orderRoundingRetainsTheOriginalRemainder() {
        var line=CustomOrderExchangeService.price(n("1009"),n("1010"),n("0"),n("0"),n("1000"),n("1000"));
        var rounded=CustomOrderExchangeService.roundedOrderPrice(line,n("1009"),n("1009"),n("1000"),n("1000"),true);
        amount("1020",rounded.orderTotal());amount("20",rounded.balance());
        var unrounded=CustomOrderExchangeService.roundedOrderPrice(line,n("1009"),n("1009"),n("1000"),n("1000"),false);
        amount("1001",unrounded.orderTotal());amount("1",unrounded.balance());
    }
}
