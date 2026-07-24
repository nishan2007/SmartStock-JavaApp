package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaMaintenanceIdempotencyTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void ledgerCleanupDoesNotRewriteNullPaymentIds() throws Exception {
        String source = Files.readString(
                ROOT.resolve("src/services/CustomerAccountLedgerService.java"));

        assertTrue(source.contains("AND payment_id IS NOT NULL"));
        assertTrue(source.contains("AND TRIM(payment_id) = ''"));
    }

    @Test
    void deviceSessionBackfillUpdatesOnlyChangedCounts() throws Exception {
        String script = Files.readString(
                ROOT.resolve("database/device_management_setup.sql"));
        String source = Files.readString(
                ROOT.resolve("src/services/ReferenceDataSyncService.java"));
        String guard =
                "d.session_count IS DISTINCT FROM COALESCE(session_totals.session_count, 0)";

        assertTrue(script.contains(guard));
        assertTrue(source.contains(guard));
    }
}
