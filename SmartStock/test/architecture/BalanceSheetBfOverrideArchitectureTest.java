package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BalanceSheetBfOverrideArchitectureTest {
    @Test
    void balanceBfOverrideIsPeriodScopedSyncedAndAdminEnforced() throws Exception {
        String setup = source("database/v1/local/001_schema.sql");
        String migration = source("database/v1/local/001_schema.sql");
        String server = source("src/services/ServerBalanceSheetService.java");
        String api = source("src/services/LanApiServer.java");
        String client = source("src/services/BalanceSheetService.java");
        String screen = source("src/ui/screens/BalanceSheet.java");
        String sync = source("src/services/ReferenceDataSyncService.java");

        assertTrue(setup.contains("balance_sheet_bf_overrides_location_period_unique"));
        assertTrue(migration.contains("UNIQUE (location_id, period_start)"));
        assertTrue(server.contains("loadBalanceBfOverride(conn, from, locationId)"));
        assertTrue(server.contains("ON CONFLICT (location_id, period_start) DO UPDATE"));
        assertTrue(api.contains("\"ADMIN\".equalsIgnoreCase(user.role())"));
        assertTrue(api.contains("Only an administrator can set or edit Balance B/F."));
        assertTrue(client.contains("mutate(\"SET_BALANCE_BF\",body)"));
        assertTrue(screen.contains("new JButton(\"Set Balance B/F\")"));
        assertTrue(sync.contains("\"balance_sheet_bf_overrides\""));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
