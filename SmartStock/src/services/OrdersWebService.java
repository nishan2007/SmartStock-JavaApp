package services;

import com.google.gson.JsonObject;
import data.DatabaseConfig;
import java.sql.Connection;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Remote orders use the approved employee browser gateway and local store authority. */
final class OrdersWebService {
    static final String PUBLIC_URL="https://ordersmanagement.deckers.gy/employees/orders.html";
    private OrdersWebService() { }

    static boolean canView(Set<String> permissions) {
        return permissions.contains("ORDERS_MANAGER_DASHBOARD")||permissions.contains("MANAGE_CUSTOM_ORDERS");
    }
    static boolean canEdit(Set<String> permissions) {
        return permissions.contains("MANAGE_CUSTOM_ORDERS")||permissions.contains("CUSTOM_ORDER_OVERRIDES");
    }
    static Object execute(Connection c,int user,String name,Set<String> permissions,UUID browser,String action,JsonObject body)throws Exception {
        if(!canView(permissions))throw new SecurityException("Your account does not have orders dashboard access.");
        Integer location=DatabaseConfig.load().locationId();
        if(location==null)throw new IllegalStateException("The store server has no configured location.");
        ZoneId zone;
        // Never accept a client-supplied store: the active server and user membership own this boundary.
        try(var p=c.prepareStatement("SELECT l.timezone FROM locations l JOIN user_locations ul USING(location_id) WHERE ul.user_id=? AND l.location_id=?")) {
            p.setInt(1,user);p.setInt(2,location);
            try(var r=p.executeQuery()) {
                if(!r.next())throw new SecurityException("This store is not assigned to your account.");
                zone=r.getString(1)==null||r.getString(1).isBlank()?ZoneId.of("America/Guyana"):ZoneId.of(r.getString(1));
            }
        }
        if(action.equals("dashboard")) {
            if(body.has("view")&&"DELIVERED".equals(body.get("view").getAsString()))
                return Map.of("orders",LanOrdersDashboardService.delivered(c,location,body.has("range")&&"ALL".equals(body.get("range").getAsString())));
            return Map.of("dashboard",LanOrdersDashboardService.load(c,location,zone),"employees",ServerCustomOrderDataService.listActiveEmployees(c,location));
        }
        if(!action.equals("details")&&!action.equals("assign"))throw new IllegalArgumentException("Unknown orders action.");
        if(action.equals("assign")&&!canEdit(permissions))throw new SecurityException("Your account cannot change order assignments or statuses.");
        long id=body.get("orderId").getAsLong();
        try(var p=c.prepareStatement("SELECT 1 FROM custom_orders WHERE custom_order_id=? AND location_id=? AND order_type='CUSTOM' FOR UPDATE")) {
            p.setLong(1,id);p.setInt(2,location);try(var r=p.executeQuery()){if(!r.next())throw new SecurityException("Custom order was not found at this store.");}
        }
        if(action.equals("details"))return Map.of("details",LanCustomOrderWorkflowService.details(c,id,location));
        Integer employee=body.has("employeeId")&&!body.get("employeeId").isJsonNull()?body.get("employeeId").getAsInt():null;
        String status=body.get("status").getAsString();
        LanOrdersDashboardService.assign(c,id,employee,status,user,name,browser,"Orders web app",location);
        return Map.of("saved",true);
    }
}
