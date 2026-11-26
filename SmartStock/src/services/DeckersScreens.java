package services;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.*;
import java.util.regex.*;

/** Existing workflow controllers mounted as isolated screens in the single Deckers document. */
final class DeckersScreens {
    private DeckersScreens() { }
    @FunctionalInterface interface Api {Object execute(HttpExchange x,Connection c,String app,String route,JsonObject body)throws Exception;}
    record Screen(String template,String path,List<String> scripts,List<String> styles,List<String> libraries) { }
    private static Screen screen(String template,String path,String scripts,String styles) {
        String directory=template.substring(0,template.lastIndexOf('/')+1);
        return new Screen(template,path,Arrays.stream(scripts.split(",")).map(directory::concat).toList(),
            Arrays.stream(styles.split(",")).map(directory::concat).toList(),List.of());
    }
    static final Map<String,Screen> SCREENS=Map.ofEntries(
        Map.entry("ORDERS",screen("mobile-web/orders.html","/employees/orders.html","orders.js","orders.css")),
        Map.entry("INVENTORY",screen("mobile-web/inventory.html","/employees/inventory.html","inventory.js","inventory.css")),
        Map.entry("CUSTOMERS",screen("mobile-web/customers.html","/employees/customers.html","customers.js","customers.css")),
        Map.entry("PHONE_MINUTES",screen("mobile-web/minutes.html","/employees/minutes.html","minutes.js","minutes.css")),
        Map.entry("BALANCE_SHEET",screen("employee-web/balance-sheet.html","/employees/balance-sheet.html","balance-sheet.js","balance-sheet.css")),
        Map.entry("EMPLOYEE_WEB",screen("employee-web/index.html","/employees/","app.js","app.css")),
        Map.entry("OVERSEAS_ORDERS",screen("employee-web/index.html","/employees/overseas.html","app.js,overseas.js","app.css")),
        Map.entry("MOBILE_ITEMS",screen("mobile-web/index.html","/?new=1","boot.js,app.js,spoils.js","app.css")),
        Map.entry("EDIT_ITEMS",screen("mobile-web/edit-items.html","/edit-items.html","boot.js,app.js,spoils.js,edit-items.js","app.css,edit-items.css")),
        Map.entry("CUSTOM_ORDER_SPOILS",screen("mobile-web/index.html","/?spoils=1","boot.js,app.js,spoils.js","app.css")),
        Map.entry("RECEIVING",new Screen("mobile-web/receiving.html","/receiving.html",List.of("mobile-web/receiving.js"),List.of("mobile-web/receiving.css"),List.of("mobile-web/orders-barcode.js"))),
        Map.entry("IMAGE_STUDIO",screen("mobile-web/background-remover.html","/background-remover.html","background-remover.js","background-remover.css")),
        Map.entry("CUSTOM_ORDER_DESIGN",screen("mobile-web/design.html","/design.html","design.js","design.css")),
        Map.entry("PAYROLL",screen("employee-web/payroll.html","/employees/payroll.html","payroll.js","payroll.css")),
        Map.entry("SCHEDULER",screen("scheduler-web/index.html","/scheduler/","app.js","app.css")));
    static String read(String resource)throws Exception {
        try(var in=DeckersScreens.class.getClassLoader().getResourceAsStream(resource)) {
            if(in==null)throw new IllegalStateException("Workspace screen resource missing: "+resource);
            return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    static Screen get(String key)throws MobileDeviceService.Denied {
        Screen screen=SCREENS.get(key);if(screen==null)throw new MobileDeviceService.Denied(404,"SCREEN_UNKNOWN","This screen is not available.");return screen;
    }
    static String module(String key)throws Exception {
        StringBuilder source=new StringBuilder("export function mount(context){\n'use strict';\nconst {document,window,location,history,fetch,navigator,setTimeout,clearTimeout,setInterval,clearInterval,requestAnimationFrame,cancelAnimationFrame,MutationObserver,ResizeObserver}=context;\n");
        for(String script:get(key).scripts)source.append(read(script)).append("\n;\n");
        return source.append("\n}\n").toString();
    }
    static Map<String,Object> packet(String key)throws Exception {
        Screen screen=get(key);Matcher body=Pattern.compile("<body([^>]*)>(.*)</body>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(read(screen.template));
        if(!body.find())throw new IllegalStateException("Workspace body missing");
        String html=body.group(2).replaceAll("(?is)<script\\b[^>]*>.*?</script>","");
        return Map.of("app",key,"name",MobileDeviceService.app(key).name(),"body",html,"bodyAttributes",key.equals("OVERSEAS_ORDERS")?"data-workspace=\"overseas\"":body.group(1),"path",screen.path,
            "styles",screen.styles,"libraries",screen.libraries,"module","screen-modules/"+key+".js?v="+LanSecurity.sha256(module(key)));
    }
    static String asset(String route) {
        if(!route.startsWith("screen-assets/"))return null;
        String resource=route.substring("screen-assets/".length());
        if(resource.contains("..")||resource.contains("\\"))return null;
        boolean named=SCREENS.values().stream().anyMatch(s->s.styles.contains(resource)||s.libraries.contains(resource));
        if(named||Set.of("mobile-web/orders-barcode.js","mobile-web/orders-barcode-LICENSE.txt","mobile-web/minutes-icons.ttf","mobile-web/minutes-icons-LICENSE.txt","mobile-web/inventory-icon.svg","mobile-web/minutes-logo.png","mobile-web/inventory-icon-192.png","mobile-web/orders-icon-v2-192.png","scheduler-web/icon.svg").contains(resource))return resource;
        return null;
    }
    static boolean allows(String app,String route,JsonObject body)throws Exception {
        String gateway=MobileDeviceService.app(app).gateway();
        if(gateway.equals("mobile")) {
            if(!route.startsWith("mobile/"))return false;
            String child=route.substring(7);
            if(Set.of("bootstrap","custom/state").contains(child))return true;
            if(app.equals("CUSTOM_ORDER_DESIGN")&&Set.of("orders/lookup","orders/dashboard").contains(child))return true;
            return MobileDeviceService.routeApps("mobile",child,body).contains(app);
        }
        return switch(app) {
            case "ORDERS" -> Set.of("orders/dashboard","orders/details","orders/assign","orders/lookup").contains(route);
            case "INVENTORY" -> route.equals("inventory/search");
            case "CUSTOMERS" -> Set.of("customers/list","customers/details","customers/history","customers/transaction","customers/order-file","customers/payment-options","customers/record-adjustment","customers/save").contains(route);
            case "PHONE_MINUTES" -> Set.of("minutes/bootstrap","minutes/history","minutes/create","minutes/reverse").contains(route);
            case "PAYROLL" -> Set.of("payroll/dashboard","payroll/pay").contains(route);
            case "BALANCE_SHEET" -> route.equals("balance-sheet");
            case "EMPLOYEE_WEB" -> Set.of("bootstrap","employees","history","history/correct").contains(route);
            case "OVERSEAS_ORDERS" -> Set.of("bootstrap","overseas").contains(route);
            case "SCHEDULER" -> Set.of("scheduler/bootstrap","scheduler/snapshot","scheduler/update","scheduler/auto-generate","scheduler/auto-apply").contains(route);
            default -> false;
        };
    }
}
