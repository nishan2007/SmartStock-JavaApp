package Receipt;

import org.junit.jupiter.api.Test;
import services.LanApiClient.CustomerOpenBalance;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AccountStatementFormatterTest {
    @Test void includesPartialBalancesAndExcludesSettledDocuments() {
        var partial = new CustomerOpenBalance("SALE", 1L, "REC-1", 1000,
                new BigDecimal("500"), new BigDecimal("200"), new BigDecimal("300"));
        var paid = new CustomerOpenBalance("SALE", 2L, "REC-PAID", 1000,
                new BigDecimal("500"), new BigDecimal("500"), BigDecimal.ZERO);
        String result = AccountStatementFormatter.format("Shop", "Customer", "123",
                List.of(partial, paid), Instant.EPOCH, ZoneId.of("UTC"));
        assertTrue(result.contains("REC-1"));
        assertFalse(result.contains("REC-PAID"));
        assertTrue(result.contains("TOTAL DUE: " + utils.CurrencyFormatter.format(new BigDecimal("300"))));
        assertTrue(result.contains("Open entries: 1"));
        assertTrue(result.lines().allMatch(line -> line.length() <= 40));
    }

    @Test void emptyAccountHasNoUnpaidPurchases() {
        String result = AccountStatementFormatter.format("Shop", "Customer", "123",
                List.of(), Instant.EPOCH, ZoneId.of("UTC"));
        assertTrue(result.contains("No unpaid purchases"));
        assertTrue(result.contains("No payment recorded"));
    }
}
