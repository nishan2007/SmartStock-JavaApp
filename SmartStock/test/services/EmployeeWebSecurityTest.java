package services;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class EmployeeWebSecurityTest {
    @Test void employeeGatewayDoesNotCollideWithExistingListeners() {
        assertNotEquals(EmployeeRegistrationWebServer.PORT,EmployeeWebServer.DEFAULT_PORT);
        assertNotEquals(SchedulerWebServer.DEFAULT_PORT,EmployeeWebServer.DEFAULT_PORT);
        assertNotEquals(WalletEnrollmentServer.DEFAULT_PORT,EmployeeWebServer.DEFAULT_PORT);
        assertNotEquals(LanApiServer.DEFAULT_PORT,EmployeeWebServer.DEFAULT_PORT);
    }
    @Test void dedicatedPermissionsProtectSelfAndDirectory() {
        Set<String> self=Set.of("ACCESS_EMPLOYEE_WEB"),manager=Set.of("ACCESS_EMPLOYEE_WEB","VIEW_ALL_EMPLOYEE_WEB");
        assertDoesNotThrow(()->EmployeeWebHistoryService.authorize(1,1,self));
        assertThrows(SecurityException.class,()->EmployeeWebHistoryService.authorize(1,2,self));
        assertDoesNotThrow(()->EmployeeWebHistoryService.authorize(1,2,manager));
        assertFalse(EmployeeWebHistoryService.canBrowse(Set.of("VIEW_ALL_EMPLOYEE_WEB")));
        assertFalse(EmployeeWebHistoryService.canBrowse(Set.of("EMPLOYEE_MANAGEMENT","PAYROLL_DASHBOARD")));
        assertThrows(SecurityException.class,()->EmployeeWebHistoryService.authorize(1,1,Set.of()));
    }
    @Test void sessionAndCsrfBoundaries()throws Exception {
        Instant now=Instant.parse("2026-10-20T12:00:00Z");
        assertTrue(EmployeeWebServer.sessionAlive(now.plusSeconds(1),now.plusSeconds(2),now));
        assertFalse(EmployeeWebServer.sessionAlive(now,now.plusSeconds(2),now));
        assertFalse(EmployeeWebServer.sessionAlive(now.plusSeconds(1),now,now));
        String token="csrf-test",hash=LanSecurity.sha256(token);
        EmployeeWebServer.validateCsrf(token,hash);
        assertThrows(EmployeeWebServer.WebError.class,()->EmployeeWebServer.validateCsrf(hash,hash));
        assertThrows(EmployeeWebServer.WebError.class,()->EmployeeWebServer.validateCsrf(null,hash));
        EmployeeWebServer.requireAssurance(false,"aal1");
        assertThrows(EmployeeWebServer.WebError.class,()->EmployeeWebServer.requireAssurance(true,"aal1"));
        EmployeeWebServer.requireAssurance(true,"aal2");
    }
}
