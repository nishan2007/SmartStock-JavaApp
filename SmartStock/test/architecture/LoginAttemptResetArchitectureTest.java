package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptResetArchitectureTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void employeeManagersCanClearLoginFailuresThroughTheServer() throws Exception {
        String server = source("src/services/LanApiServer.java");
        String security = source("src/services/LoginSecurityService.java");
        String employees = source("src/services/LanEmployeeAdminService.java");
        String screen = source("src/ui/screens/EmployeeManagement.java");

        assertTrue(server.contains("requireAnyPermission(c,s.userId(),\"EMPLOYEE_MANAGEMENT\")"));
        assertTrue(server.contains("case\"CLEAR_LOGIN_ATTEMPTS\""));
        assertTrue(server.contains("LOGIN_FAILURES_CLEARED"));
        assertTrue(employees.contains("LoginSecurityService.clearFailures"));
        assertTrue(security.contains("DELETE FROM login_security_state WHERE identifier_hash = ?"));
        assertTrue(screen.contains("new JButton(\"Clear Login Attempts\")"));
        assertTrue(screen.contains("updateEmployeeAdmin(\"CLEAR_LOGIN_ATTEMPTS\""));
    }
}
