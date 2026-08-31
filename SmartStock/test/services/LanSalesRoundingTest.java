package services;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanSalesRoundingTest {
    @Test
    void roundsOnlyTheFinalSaleTotalToTheNearestTwenty() {
        assertEquals(new BigDecimal("20"), LanSalesService.roundSaleTotal(new BigDecimal("29"), true));
        assertEquals(new BigDecimal("40"), LanSalesService.roundSaleTotal(new BigDecimal("30"), true));
        assertEquals(new BigDecimal("40"), LanSalesService.roundSaleTotal(new BigDecimal("31"), true));
        assertEquals(new BigDecimal("31.00"), LanSalesService.roundSaleTotal(new BigDecimal("31"), false));
    }

    @Test
    void normalCatalogPricesUseTheRegistersWholeCurrencyNormalization() {
        assertEquals(new BigDecimal("13"),
                LanSalesService.normalizeCheckoutUnitPrice(new BigDecimal("13.30"), false));
        assertEquals(new BigDecimal("13"),
                LanHeldCartService.normalizeHeldUnitPrice(new BigDecimal("13.30"), false));
    }

    @Test
    void miscellaneousPricesRetainTheirTwoDecimalPrecision() {
        assertEquals(new BigDecimal("13.30"),
                LanSalesService.normalizeCheckoutUnitPrice(new BigDecimal("13.30"), true));
        assertEquals(new BigDecimal("13.30"),
                LanHeldCartService.normalizeHeldUnitPrice(new BigDecimal("13.30"), true));
    }
}
