package services;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BalanceSheetWebAccessTest {
    @Test void balanceSheetActivationOpensItsOwnDomainAndPreservesOtherWorkspaces() {
        String employee="https://timeclock.deckers.gy/employees/";
        assertEquals("https://balancesheet.deckers.gy/employees/balance-sheet.html",EmployeeWebAdminService.activationEntry("balance-sheet",employee));
        assertEquals(InventoryWebService.PUBLIC_URL,EmployeeWebAdminService.activationEntry("inventory",employee));
        assertEquals(OrdersWebService.PUBLIC_URL,EmployeeWebAdminService.activationEntry("orders",employee));
        assertEquals(employee,EmployeeWebAdminService.activationEntry("employees",employee));
    }
    @Test void balanceSheetAccountCanSignInWithoutEmployeeWebPermission() {
        var balance=Set.of("BALANCE_SHEET");
        assertTrue(EmployeeWebServer.canAuthenticate(balance));
        for(String route:Set.of("auth/session","auth/logout","balance-sheet"))
            assertDoesNotThrow(()->EmployeeWebServer.requireRouteAccess(route,balance));
    }
    @Test void balanceSheetPermissionDoesNotExposeEmployeeOrOrdersRoutes() {
        for(String route:Set.of("bootstrap","employees","history","history/correct","overseas","orders/dashboard","orders/details","orders/assign","orders/lookup"))
            assertThrows(SecurityException.class,()->EmployeeWebServer.requireRouteAccess(route,Set.of("BALANCE_SHEET")));
    }
    @Test void employeeWebAccessDoesNotGrantBalanceSheetAccess() {
        var employee=Set.of("ACCESS_EMPLOYEE_WEB");assertTrue(EmployeeWebServer.canAuthenticate(employee));
        assertDoesNotThrow(()->EmployeeWebServer.requireRouteAccess("history",employee));
        assertThrows(SecurityException.class,()->EmployeeWebServer.requireRouteAccess("balance-sheet",employee));
        assertFalse(EmployeeWebServer.canAuthenticate(Set.of()));
        assertFalse(EmployeeWebServer.canAuthenticate(Set.of("VIEW_BALANCE_SHEET_HISTORY")));
    }
}
