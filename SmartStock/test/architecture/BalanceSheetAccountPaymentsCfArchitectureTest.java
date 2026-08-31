package architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BalanceSheetAccountPaymentsCfArchitectureTest {
    @Test
    void customerPaymentsAreIncludedInEveryBalanceCfCalculation() throws Exception {
        String source = Files.readString(Path.of("src/services/ServerBalanceSheetService.java"));

        assertEquals(2, occurrences(source, ".add(total(accountPayments))"),
                "Both live balance-sheet loading paths must include customer payments in Balance C/F.");
        assertTrue(source.contains(".add(total(beforeSheet.accountPayments()))"),
                "Editing a saved balance sheet must retain customer payments in Balance C/F.");
    }

    @Test
    void expectedExampleAddsCustomerPaymentWithoutTreatingItAsIncome() {
        BigDecimal cf = new BigDecimal("0")
                .add(new BigDecimal("79912"))
                .add(new BigDecimal("83460"))
                .subtract(BigDecimal.ZERO)
                .subtract(BigDecimal.ZERO);

        assertEquals(new BigDecimal("163372"), cf);
    }

    private static int occurrences(String text, String value) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(value, at)) >= 0; at += value.length()) count++;
        return count;
    }
}
