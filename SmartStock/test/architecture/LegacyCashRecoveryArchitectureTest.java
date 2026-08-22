package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyCashRecoveryArchitectureTest {
    @Test void recoveryIsServerAuthorizedAuditedAndNullOnly() throws Exception {
        String api=Files.readString(Path.of("src/services/LanApiServer.java"));
        String service=Files.readString(Path.of("src/services/ServerBalanceSheetService.java"));
        assertTrue(api.contains("requireAnyPermission(connection,session.userId(),\"BALANCE_DRAWER\")"));
        assertTrue(api.contains("Only an administrator can recover legacy cash rows"));
        assertTrue(service.contains("cash_drawer_session_id IS NULL"));
        assertTrue(service.contains("LEGACY_CASH_SESSION_RECOVERED"));
        assertTrue(service.contains("This row already belongs to a drawer session and cannot be reassigned"));
    }

    @Test void drawerChecksExplainTheSpecificMismatch() throws Exception {
        String service=Files.readString(Path.of("src/services/ServerBalanceSheetService.java"));
        assertTrue(service.contains("session unassigned"));
        assertTrue(service.contains("unselected session "));
        assertTrue(service.contains("receipt_number"));
        assertTrue(service.contains("order_number"));
    }
}
