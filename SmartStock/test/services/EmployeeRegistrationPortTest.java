package services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EmployeeRegistrationPortTest {
    @Test void registrationDoesNotCollideWithOtherServerListeners() {
        assertNotEquals(WalletEnrollmentServer.DEFAULT_PORT, EmployeeRegistrationWebServer.PORT);
        assertNotEquals(SchedulerWebServer.DEFAULT_PORT, EmployeeRegistrationWebServer.PORT);
        assertNotEquals(LanApiServer.DEFAULT_PORT, EmployeeRegistrationWebServer.PORT);
    }
}
