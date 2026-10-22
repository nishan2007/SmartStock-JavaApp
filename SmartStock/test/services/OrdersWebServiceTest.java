package services;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OrdersWebServiceTest {
    @Test void remoteOrdersPreserveExistingViewAndEditPermissions() {
        assertTrue(OrdersWebService.canView(Set.of("ORDERS_MANAGER_DASHBOARD")));
        assertFalse(OrdersWebService.canEdit(Set.of("ORDERS_MANAGER_DASHBOARD")));
        assertTrue(OrdersWebService.canView(Set.of("MANAGE_CUSTOM_ORDERS")));
        assertTrue(OrdersWebService.canEdit(Set.of("MANAGE_CUSTOM_ORDERS")));
        assertTrue(OrdersWebService.canEdit(Set.of("CUSTOM_ORDER_OVERRIDES")));
        assertFalse(OrdersWebService.canView(Set.of("ACCESS_EMPLOYEE_WEB","DEVICE_MANAGEMENT")));
        assertFalse(OrdersWebService.canEdit(Set.of("ACCESS_EMPLOYEE_WEB","DEVICE_MANAGEMENT")));
    }
    @Test void unauthorizedRequestsAreRejectedBeforeDatabaseAccess() {
        for(String action:new String[]{"dashboard","details","assign"})
            assertThrows(SecurityException.class,()->OrdersWebService.execute(null,1,"Employee",Set.of("ACCESS_EMPLOYEE_WEB"),UUID.randomUUID(),action,new JsonObject()));
    }
}
