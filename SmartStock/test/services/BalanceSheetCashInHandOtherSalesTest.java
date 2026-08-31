package services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BalanceSheetCashInHandOtherSalesTest {
    @Test
    void otherCashSalesAreAddedToDrawerCashInHand() {
        List<ServerBalanceSheetService.SheetLine> result = ServerBalanceSheetService.includeOtherCashInDrawerCash(
                List.of(new ServerBalanceSheetService.SheetLine("POS-01 / DRAW 1 CIH", new BigDecimal("181040"))),
                List.of(new ServerBalanceSheetService.SheetLine("OTHER CASH", new BigDecimal("150780"))));

        assertEquals(2, result.size());
        assertEquals("Other cash sales", result.get(1).label());
        assertEquals(new BigDecimal("150780"), result.get(1).amount());
        assertEquals(new BigDecimal("331820"), result.stream().map(ServerBalanceSheetService.SheetLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void recalculationReplacesExistingOtherCashInsteadOfDoublingIt() {
        List<ServerBalanceSheetService.SheetLine> result = ServerBalanceSheetService.includeOtherCashInDrawerCash(
                List.of(new ServerBalanceSheetService.SheetLine("Other cash sales", new BigDecimal("100"))),
                List.of(new ServerBalanceSheetService.SheetLine("OTHER CASH", new BigDecimal("250"))));

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("250"), result.get(0).amount());
    }
}
