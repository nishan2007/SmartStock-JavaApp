package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomOrderFailureMessageArchitectureTest {
    @Test
    void cashDrawerFailuresRemainActionableAtTheLanBoundary() throws Exception {
        String server = Files.readString(Path.of("src/services/LanApiServer.java"));

        assertTrue(server.contains("catch(SQLException e){c.rollback();throw customOrderSqlException(e);}"));
        assertTrue(server.contains("CUSTOM_ORDER_CASH_DRAWER_REQUIRED"));
        assertTrue(server.contains("This device is not assigned to an active cash drawer"));
        assertTrue(server.contains("No active draw session is open for"));
    }
}
