package services;

import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class NewEmployeeContactTest {
    @Test void newEmployeeRequiresBothContactFields() {
        for (String missing : new String[]{null, "", "  "}) {
            assertThrows(SQLException.class, () -> LanEmployeeAdminService.validateNewEmployeeContact(missing, "5551234"));
            assertThrows(SQLException.class, () -> LanEmployeeAdminService.validateNewEmployeeContact("employee@example.com", missing));
        }
        assertDoesNotThrow(() -> LanEmployeeAdminService.validateNewEmployeeContact("employee@example.com", "5551234"));
    }
}
