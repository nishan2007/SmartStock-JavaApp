package services;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class CustomerWebAccessTest {
 @Test void customerOnlyLoginAndRouteIsolation(){
  var customers=Set.of("CUSTOMER_ACCOUNTS");
  assertTrue(EmployeeWebServer.canAuthenticate(customers));
  for(String route: new String[]{"customers/list","customers/details","customers/save"}) {
   assertDoesNotThrow(()->EmployeeWebServer.requireRouteAccess(route,customers));
   assertThrows(SecurityException.class,()->EmployeeWebServer.requireRouteAccess(route,Set.of("ACCESS_EMPLOYEE_WEB","VIEW_INVENTORY")));
  }
  for(String route:new String[]{"employees","balance-sheet","inventory/search"})assertThrows(SecurityException.class,()->EmployeeWebServer.requireRouteAccess(route,customers));
 }
}
