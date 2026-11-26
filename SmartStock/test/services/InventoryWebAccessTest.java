package services;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class InventoryWebAccessTest {
    @Test void inventoryPermissionGrantsOnlyInventoryAndSharedAuthentication() {
        var inventory=Set.of("VIEW_INVENTORY");
        assertTrue(EmployeeWebServer.canAuthenticate(inventory));
        for(String route:Set.of("auth/session","auth/logout","inventory/search"))
            assertDoesNotThrow(()->EmployeeWebServer.requireRouteAccess(route,inventory));
        for(String route:Set.of("employees","history","overseas","orders/dashboard","orders/assign","balance-sheet"))
            assertThrows(SecurityException.class,()->EmployeeWebServer.requireRouteAccess(route,inventory));
    }
    @Test void otherWebPermissionsDoNotGrantInventory() {
        for(String key:Set.of("ACCESS_EMPLOYEE_WEB","BALANCE_SHEET","EDIT_ITEM","RECEIVING_INVENTORY"))
            assertThrows(SecurityException.class,()->EmployeeWebServer.requireRouteAccess("inventory/search",Set.of(key)));
    }
}
