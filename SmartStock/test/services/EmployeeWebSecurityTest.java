package services;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class EmployeeWebSecurityTest {
    @Test void serverSelectedPersistenceUsesSecureCookies() {
        String temporary=EmployeeWebServer.sessionCookieValue("test",false);
        assertFalse(temporary.contains("Max-Age"));
        assertTrue(temporary.contains("Secure; HttpOnly; SameSite=Strict"));
        assertTrue(EmployeeWebServer.sessionCookieValue("test",true).contains("Max-Age=34560000"));
    }

    @Test void driveSignInAllowsActiveEmployeesWithoutGrantingOtherAppPermissions() {
        assertTrue(EmployeeWebServer.canAuthenticate(Set.of(),true));
        assertFalse(EmployeeWebServer.canAuthenticate(Set.of(),false));
        assertDoesNotThrow(()->EmployeeWebServer.requireRouteAccess("auth/drive-session",Set.of()));
        assertThrows(SecurityException.class,()->EmployeeWebServer.requireRouteAccess("history",Set.of()));
        assertThrows(SecurityException.class,()->EmployeeWebServer.requireRouteAccess("inventory/search",Set.of()));
    }
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
    @Test void minutesRequireCustomerAccountPermissionOnEveryRoute() {
        for(String route:java.util.List.of("minutes/bootstrap","minutes/history","minutes/create","minutes/reverse")) {
            assertThrows(SecurityException.class,()->EmployeeWebServer.requireRouteAccess(route,Set.of("ACCESS_EMPLOYEE_WEB")));
            assertDoesNotThrow(()->EmployeeWebServer.requireRouteAccess(route,Set.of("CUSTOMER_ACCOUNTS")));
        }
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
