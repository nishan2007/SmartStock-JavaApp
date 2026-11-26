package services;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeckersWorkspaceTest {
    @Test void dedicatedDeckersOriginIsAllowedWithoutTrustingLookalikes() {
        assertEquals("https://deckersapps.deckers.gy/employees/mobile-management/",DeckersWorkspace.HOME);
        assertTrue(EmployeeWebServer.workspaceOriginAllowed("https://timeclock.deckers.gy",DeckersWorkspace.ORIGIN));
        assertFalse(EmployeeWebServer.workspaceOriginAllowed("https://timeclock.deckers.gy",DeckersWorkspace.ORIGIN+".evil.example"));
        assertFalse(EmployeeWebServer.workspaceOriginAllowed("https://timeclock.deckers.gy",null));
        assertTrue(DeckersWorkspace.allowedProbeOrigin(DeckersWorkspace.ORIGIN,"https://store.local:8444"));
        assertFalse(DeckersWorkspace.allowedProbeOrigin(DeckersWorkspace.ORIGIN+".evil.example","https://store.local:8444"));
    }

    @Test void appVisibilityIsTheIntersectionOfRoleAndDeviceGrants() throws Exception {
        var grants=List.of("ORDERS","INVENTORY","MOBILE_ITEMS","RECEIVING","CUSTOMERS");
        var permissions=Set.of("VIEW_INVENTORY","NEW_ITEM","CUSTOMER_ACCOUNTS","BALANCE_SHEET");
        var apps=DeckersWorkspace.apps(grants,permissions);
        assertEquals(List.of("INVENTORY","CUSTOMERS","MOBILE_ITEMS"),apps.stream().map(a->a.get("key")).toList());
        assertEquals("Add Item",apps.get(2).get("name"));
        assertEquals(true,apps.get(2).get("localOnly"));
        assertEquals(false,apps.get(0).get("localOnly"));
        assertTrue(DeckersWorkspace.apps(List.of(),permissions).isEmpty());
        assertTrue(DeckersWorkspace.apps(grants,Set.of()).isEmpty());
    }
    @Test void nativeAppAuthorizationRequirementsAreRetained() {
        assertFalse(DeckersWorkspace.canOpen("PAYROLL",Set.of("ACCESS_EMPLOYEE_WEB")));
        assertTrue(DeckersWorkspace.canOpen("PAYROLL",Set.of("PAYROLL_DASHBOARD")));
        assertDoesNotThrow(()->EmployeeWebServer.requireRouteAccess("payroll/pay",Set.of("PAYROLL_DASHBOARD")));
        assertThrows(SecurityException.class,()->EmployeeWebServer.requireRouteAccess("payroll/pay",Set.of("ACCESS_EMPLOYEE_WEB")));
        assertFalse(DeckersWorkspace.canOpen("ORDERS",Set.of("ACCESS_EMPLOYEE_WEB")));
        assertFalse(DeckersWorkspace.canOpen("ORDERS",Set.of("MANAGE_CUSTOM_ORDERS")));
        assertTrue(DeckersWorkspace.canOpen("ORDERS",Set.of("ACCESS_EMPLOYEE_WEB","MANAGE_CUSTOM_ORDERS")));
        assertFalse(DeckersWorkspace.canOpen("SCHEDULER",Set.of("VIEW_EMPLOYEE_SCHEDULE")));
        assertTrue(DeckersWorkspace.canOpen("SCHEDULER",Set.of("VIEW_EMPLOYEE_SCHEDULE","ACCESS_SCHEDULER_WEB")));
        assertFalse(DeckersWorkspace.canOpen("UNREGISTERED",Set.of("DEVICE_MANAGEMENT")));
        assertTrue(EmployeeWebServer.canAuthenticate(Set.of("NEW_ITEM")));
        assertThrows(SecurityException.class,()->EmployeeWebServer.requireRouteAccess("history",Set.of("NEW_ITEM")));
    }
    @Test void primaryWorkspaceKeepsEmployeeAppsInTheSameInstalledApp() throws Exception {
        String origin="https://timeclock.deckers.gy";
        assertEquals(origin+"/employees/orders.html",DeckersWorkspace.sameOriginDestination(MobileDeviceService.app("ORDERS"),origin));
        assertEquals(origin+"/employees/overseas.html",DeckersWorkspace.sameOriginDestination(MobileDeviceService.app("OVERSEAS_ORDERS"),origin));
        assertNull(DeckersWorkspace.sameOriginDestination(MobileDeviceService.app("RECEIVING"),origin));
        assertNull(DeckersWorkspace.sameOriginDestination(MobileDeviceService.app("ORDERS"),"https://inventory.deckers.gy"));
    }
    @Test void networkCheckIsBoundToEmployeeDeviceAppAndOriginAndCannotBeReplayed() throws Exception {
        UUID device=UUID.randomUUID();String origin="https://timeclock.deckers.gy";
        String challenge=DeckersWorkspace.challenge(device,7,"RECEIVING",origin);
        assertThrows(MobileDeviceService.Denied.class,()->DeckersWorkspace.consume(challenge,device,7,"RECEIVING",origin));
        assertFalse(DeckersWorkspace.verify(challenge,"https://evil.example"));
        assertTrue(DeckersWorkspace.verify(challenge,origin));
        assertThrows(MobileDeviceService.Denied.class,()->DeckersWorkspace.consume(challenge,UUID.randomUUID(),7,"RECEIVING",origin));
        assertThrows(MobileDeviceService.Denied.class,()->DeckersWorkspace.consume(challenge,device,8,"RECEIVING",origin));
        assertThrows(MobileDeviceService.Denied.class,()->DeckersWorkspace.consume(challenge,device,7,"MOBILE_ITEMS",origin));
        assertThrows(MobileDeviceService.Denied.class,()->DeckersWorkspace.consume(challenge,device,7,"RECEIVING","https://inventory.deckers.gy"));
        DeckersWorkspace.consume(challenge,device,7,"RECEIVING",origin);
        assertFalse(DeckersWorkspace.verify(challenge,origin));
        assertThrows(MobileDeviceService.Denied.class,()->DeckersWorkspace.consume(challenge,device,7,"RECEIVING",origin));
    }
    @Test void assetsAndProbeOriginsAreExplicitlyAllowlisted() throws Exception {
        assertNull(DeckersWorkspace.asset("icons/../../database.properties"));
        assertNull(DeckersWorkspace.asset("icons/UNKNOWN.png"));
        assertFalse(DeckersWorkspace.allowedProbeOrigin(null,"https://store.local:8444"));
        assertFalse(DeckersWorkspace.allowedProbeOrigin("https://timeclock.deckers.gy.evil.example","https://store.local:8444"));
        assertTrue(DeckersWorkspace.allowedProbeOrigin("https://timeclock.deckers.gy","https://store.local:8444"));
        String scheduler="https://scheduling.example.test";
        assertFalse(DeckersWorkspace.allowedProbeOrigin(scheduler,"https://store.local:8444"));
        String challenge=DeckersWorkspace.challenge(UUID.randomUUID(),7,"RECEIVING",scheduler);
        assertTrue(DeckersWorkspace.allowedProbeOrigin(scheduler,"https://store.local:8444"));
        assertTrue(DeckersWorkspace.verify(challenge,scheduler));
        for(var icon:DeckersWorkspace.ICONS.entrySet()) {
            String resource=icon.getValue();
            assertEquals(resource,DeckersWorkspace.asset("icons/"+icon.getKey()+".png"));
            try(var input=getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input,resource);
                var image=javax.imageio.ImageIO.read(input);
                assertNotNull(image,resource);
                assertEquals(192,image.getWidth(),resource);
                assertEquals(192,image.getHeight(),resource);
            }
        }
    }
}
