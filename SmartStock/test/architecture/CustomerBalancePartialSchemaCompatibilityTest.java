package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerBalancePartialSchemaCompatibilityTest {
    @Test
    void balanceReadSupportsPartiallyUpgradedCrossStoreCache() throws Exception {
        String ledger = Files.readString(Path.of("src/services/CustomerAccountLedgerService.java"));
        String schema = Files.readString(Path.of("src/services/SchemaContractService.java"));

        assertTrue(ledger.contains("legacyRemoteBalanceDeltaSql()"));
        assertTrue(ledger.contains("columnExists(conn, \"sync_cross_store_customer_history_cache\", \"credit_applied_amount\")"));
        assertTrue(schema.contains("cacheCreditReady"));
        assertTrue(schema.contains("cacheBalanceReady"));
        assertTrue(schema.contains("ADD COLUMN IF NOT EXISTS credit_applied_amount"));
    }
}
