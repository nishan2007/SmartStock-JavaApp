package services;

import com.sun.net.httpserver.HttpExchange;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Presentation and authorization for the shared launcher; existing app APIs remain authoritative. */
final class DeckersWorkspace {
    private DeckersWorkspace() { }
    static final String ORIGIN="https://deckersapps.deckers.gy";
    static final String HOME=ORIGIN+"/employees/mobile-management/";
    record User(int id, String name, Set<String> permissions,DeckersScreens.Api api) {
        User(int id,String name,Set<String> permissions){this(id,name,permissions,null);}
    }
    @FunctionalInterface interface Authentication { User require(HttpExchange exchange, Connection connection) throws Exception; }
    static final Map<String,String> ICONS = Map.ofEntries(
        Map.entry("ORDERS", "shared-mobile/icons/ORDERS.png"),
        Map.entry("INVENTORY", "shared-mobile/icons/INVENTORY.png"),
        Map.entry("EDIT_ITEMS", "shared-mobile/icons/EDIT_ITEMS.png"),
        Map.entry("CUSTOMERS", "shared-mobile/icons/CUSTOMERS.png"),
        Map.entry("PHONE_MINUTES", "shared-mobile/icons/PHONE_MINUTES.png"),
        Map.entry("BALANCE_SHEET", "shared-mobile/icons/BALANCE_SHEET.png"),
        Map.entry("EMPLOYEE_WEB", "shared-mobile/icons/EMPLOYEE_WEB.png"),
        Map.entry("OVERSEAS_ORDERS", "shared-mobile/icons/OVERSEAS_ORDERS.png"),
        Map.entry("PAYROLL", "shared-mobile/icons/PAYROLL.png"),
        Map.entry("SCHEDULER", "shared-mobile/icons/SCHEDULER.png"),
        Map.entry("MOBILE_ITEMS", "shared-mobile/icons/MOBILE_ITEMS.png"),
        Map.entry("RECEIVING", "shared-mobile/icons/RECEIVING.png"),
        Map.entry("IMAGE_STUDIO", "shared-mobile/icons/IMAGE_STUDIO.png"),
        Map.entry("CUSTOM_ORDER_DESIGN", "shared-mobile/icons/CUSTOM_ORDER_DESIGN.png"),
        Map.entry("CUSTOM_ORDER_SPOILS", "shared-mobile/icons/CUSTOM_ORDER_SPOILS.png"));
    private static final List<String> ORDER = List.of("PAYROLL","ORDERS","INVENTORY","EDIT_ITEMS","CUSTOMERS","PHONE_MINUTES","BALANCE_SHEET","EMPLOYEE_WEB","OVERSEAS_ORDERS","SCHEDULER","MOBILE_ITEMS","RECEIVING","IMAGE_STUDIO","CUSTOM_ORDER_DESIGN","CUSTOM_ORDER_SPOILS");

    static boolean canOpen(String key, Set<String> p) {
        return switch(key) {
            case "MOBILE_ITEMS" -> p.contains("NEW_ITEM") || customCatalogue(p);
            case "EDIT_ITEMS" -> p.contains("EDIT_ITEM") || customCatalogue(p);
            case "RECEIVING" -> p.contains("RECEIVING_INVENTORY");
            case "INVENTORY" -> p.contains("VIEW_INVENTORY");
            case "ORDERS" -> p.contains("ACCESS_EMPLOYEE_WEB") && OrdersWebService.canView(p);
            case "EMPLOYEE_WEB" -> p.contains("ACCESS_EMPLOYEE_WEB");
            case "OVERSEAS_ORDERS" -> p.contains("ACCESS_EMPLOYEE_WEB") && OverseasOrderService.canView(p);
            case "PAYROLL" -> p.contains("PAYROLL_DASHBOARD");
            case "BALANCE_SHEET" -> p.contains("BALANCE_SHEET");
            case "CUSTOMERS", "PHONE_MINUTES" -> p.contains("CUSTOMER_ACCOUNTS");
            case "SCHEDULER" -> p.contains("ACCESS_SCHEDULER_WEB") && p.contains("VIEW_EMPLOYEE_SCHEDULE");
            case "IMAGE_STUDIO" -> p.contains("NEW_ITEM") || p.contains("EDIT_ITEM") || customCatalogue(p);
            case "CUSTOM_ORDER_DESIGN" -> p.contains("MANAGE_CUSTOM_ORDERS");
            case "CUSTOM_ORDER_SPOILS" -> p.contains("RECORD_CUSTOM_ORDER_SPOILS") || p.contains("REVERSE_CUSTOM_ORDER_SPOILS") || p.contains("MANAGE_CUSTOM_ORDERS");
            default -> false;
        };
    }
    private static boolean customCatalogue(Set<String> p) {return p.contains("MANAGE_CUSTOM_ORDER_ITEMS") || p.contains("CUSTOM_ORDER_ITEMS") || p.contains("MANAGE_CUSTOM_ORDERS");}
    static List<Map<String,Object>> apps(List<String> grants, Set<String> permissions) throws Exception {
        List<Map<String,Object>> result = new ArrayList<>();
        for(String key:ORDER) if(grants.contains(key) && canOpen(key,permissions)) {
            MobileDeviceService.App app=MobileDeviceService.app(key);
            result.add(Map.of("key",key,"name",key.equals("MOBILE_ITEMS")?"Add Item":app.name(),
                "icon","icons/"+key+(ICONS.get(key).endsWith(".svg")?".svg":".png"),"localOnly",app.gateway().equals("mobile")));
        }
        return result;
    }
    static String asset(String route) {
        if(route.equals("repair-qr.js"))return "mobile-web/orders-barcode.js";
        if(route.equals("repair-qr-LICENSE.txt"))return "mobile-web/orders-barcode-LICENSE.txt";
        if(route.equals("device-info.js"))return "shared-mobile/device-info.js";
        if(route.equals("logo.png"))return "Images/Deckers.png";
        if(route.equals("network.png"))return "ICONS/MainMenuWebStatus.png";
        if(route.equals("home.png"))return "ICONS/MainMenuDepartments.png";
        if(route.equals("account.png"))return "ICONS/Employee.png";
        if(route.equals("icons.ttf"))return "mobile-web/minutes-icons.ttf";
        if(route.equals("icons-LICENSE.txt"))return "mobile-web/minutes-icons-LICENSE.txt";
        if(route.equals("icon-180.png"))return "shared-mobile/deckers-180.png";
        if(route.equals("icon-192.png"))return "shared-mobile/deckers-192.png";
        if(route.equals("icon-512.png"))return "shared-mobile/deckers-512.png";
        if(route.startsWith("icons/")) {
            for(var icon:ICONS.entrySet()) if(route.equals("icons/"+icon.getKey()+(icon.getValue().endsWith(".svg")?".svg":".png")))return icon.getValue();
        }
        return null;
    }
    static String sameOriginDestination(MobileDeviceService.App app, String origin) {
        // The main Deckers gateway serves these existing employee workflows together.
        // Specialized legacy origins keep their scoped gateways and destination-bound handoffs.
        if(!"https://timeclock.deckers.gy".equals(origin)&&!managedOrigin(origin,data.DatabaseConfig.load().locationId()))return null;
        return switch(app.gateway()) {
            case "orders","inventory","balance","customers","timeclock" -> origin+app.path();
            case "overseas" -> origin+"/employees/overseas.html";
            default -> null;
        };
    }

    static boolean managedOrigin(String origin,Integer locationId){
        return locationId!=null&&locationId>0&&("https://store-"+locationId+"-web.deckers.gy").equals(origin);
    }
    static String workspaceHome(String configured,Integer locationId){
        return managedOrigin(configured,locationId)?configured+"/employees/mobile-management/":HOME;
    }

    // A credential-free LAN probe proves that this browser can actually reach the store listener.
    // The public gateway cannot mark a challenge verified. No pairing/session secret crosses origins.
    record Probe(UUID device, int user, String app, String origin, Instant expires, boolean verified) { }
    private static final Map<String,Probe> PROBES=new ConcurrentHashMap<>();
    static synchronized String challenge(UUID device, int user, String app, String origin) throws MobileDeviceService.Denied {
        PROBES.entrySet().removeIf(e->!e.getValue().expires.isAfter(Instant.now()));
        if(PROBES.size()>=256)throw new MobileDeviceService.Denied(429,"NETWORK_BUSY","Please try again shortly.");
        String token=LanSecurity.randomToken();PROBES.put(token,new Probe(device,user,app,origin,Instant.now().plusSeconds(30),false));return token;
    }
    static boolean verify(String token, String origin) {
        Probe probe=PROBES.get(token);
        return probe!=null && probe.origin.equals(origin) && probe.expires.isAfter(Instant.now()) &&
            PROBES.replace(token,probe,new Probe(probe.device,probe.user,probe.app,probe.origin,probe.expires,true));
    }
    static void consume(String token, UUID device, int user, String app, String origin) throws MobileDeviceService.Denied {
        Probe probe=PROBES.get(token);
        if(probe==null || !probe.verified || !probe.expires.isAfter(Instant.now()) || !probe.device.equals(device) || probe.user!=user || !probe.app.equals(app) || !probe.origin.equals(origin) || !PROBES.remove(token,probe))
            throw new MobileDeviceService.Denied(403,"LAN_ONLY","Connect to the store network to open this app.");
    }
    static boolean allowedProbeOrigin(String origin, String localOrigin) {
        return origin!=null && (origin.equals(localOrigin) || Set.of(ORIGIN,"https://timeclock.deckers.gy","https://ordersmanagement.deckers.gy","https://inventory.deckers.gy","https://balancesheet.deckers.gy","https://customermanagement.deckers.gy","https://overseasshipping.deckers.gy").contains(origin) ||
            PROBES.values().stream().anyMatch(probe->probe.origin.equals(origin) && probe.expires.isAfter(Instant.now())));
    }
}
