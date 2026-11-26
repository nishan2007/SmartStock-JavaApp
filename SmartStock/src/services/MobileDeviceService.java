package services;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import data.DB;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Store-local, persistent device approval shared by staff browser applications. */
public final class MobileDeviceService {
    private MobileDeviceService() { }
    private static final Gson JSON = LanJson.create();
    public static final String COOKIE = "ss_mobile_device";
    public record App(String key, String name, String group, String gateway, String path) { }
    // Add an app here and its protected route mapping below. Missing grants always deny access.
    public static final List<App> APPS = List.of(
        new App("MOBILE_ITEMS", "Add Item", "Catalogue", "mobile", "/?new=1"),
        new App("EDIT_ITEMS", "Edit Items", "Catalogue", "mobile", "/edit-items.html"),
        new App("IMAGE_STUDIO", "Image Studio", "Catalogue", "mobile", "/background-remover.html"),
        new App("CUSTOM_ORDER_DESIGN", "Order Design", "Orders", "mobile", "/design.html"),
        new App("CUSTOM_ORDER_SPOILS", "Custom Order Spoils", "Orders", "mobile", "/?spoils=1"),
        new App("RECEIVING", "Receiving Inventory", "Inventory", "mobile", "/receiving.html"),
        new App("INVENTORY", "Inventory", "Inventory", "inventory", "/employees/inventory.html"),
        new App("ORDERS", "Orders", "Orders", "orders", "/employees/orders.html"),
        new App("EMPLOYEE_WEB", "Employee History", "Employees", "timeclock", "/employees/"),
        new App("OVERSEAS_ORDERS", "Overseas Orders", "Orders", "overseas", "/employees/"),
        new App("BALANCE_SHEET", "Balance Sheet", "Accounts", "balance", "/employees/balance-sheet.html"),
        new App("CUSTOMERS", "Customer Accounts", "Accounts", "customers", "/employees/customers.html"),
        new App("PHONE_MINUTES", "Phone Minutes", "Accounts", "customers", "/employees/minutes.html"),
        new App("PAYROLL", "Payroll", "Employees", "timeclock", "/employees/payroll.html"),
        new App("SCHEDULER", "Scheduler", "Employees", "scheduler", "/scheduler/"));

    public static final class Denied extends Exception {
        public final int status;
        public final String code;
        Denied(int status, String code, String message) { super(message); this.status=status; this.code=code; }
    }
    static App app(String key) throws Denied {
        return APPS.stream().filter(a -> a.key.equals(key)).findFirst()
            .orElseThrow(() -> new Denied(400,"APP_UNKNOWN","Choose a registered staff web app."));
    }
    static void requireAdmin(Connection c, int actor) throws Exception {
        try(var p=c.prepareStatement("SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions p USING(permission_id) WHERE u.user_id=? AND COALESCE(u.is_active,TRUE) AND p.permission_key='DEVICE_MANAGEMENT'")) {
            p.setInt(1,actor); try(var r=p.executeQuery()) { if(r.next()) return; }
        }
        throw new Denied(403,"PERMISSION_DENIED","You do not have permission to manage mobile devices.");
    }
    static List<String> keys(JsonObject b) throws Denied {
        LinkedHashSet<String> keys=new LinkedHashSet<>();
        try { for(var e:b.getAsJsonArray("apps")) { String key=e.getAsString(); app(key); keys.add(key); } }
        catch(Denied e) { throw e; }
        catch(Exception e) { throw new Denied(400,"VALIDATION_ERROR","Select the allowed web apps."); }
        return List.copyOf(keys);
    }
    static String name(JsonObject b) throws Denied {
        String name=b.has("name")&&!b.get("name").isJsonNull()?b.get("name").getAsString().trim():"";
        if(name.isEmpty()||name.length()>120) throw new Denied(400,"VALIDATION_ERROR","Enter a device name of 1 to 120 characters.");
        return name;
    }
    static Map<String,Object> state(Connection c, int actor) throws Exception {
        requireAdmin(c,actor);
        List<Map<String,Object>> devices=new ArrayList<>();
        try(var p=c.prepareStatement("SELECT device_id,device_name,created_at,last_seen_at,revoked_at,revision,remember_login,device_info::text,device_info_updated_at FROM mobile_managed_devices ORDER BY revoked_at NULLS FIRST,lower(device_name)");var r=p.executeQuery()) {
            while(r.next()) {
                UUID id=(UUID)r.getObject(1);
                devices.add(Map.of("deviceId",id.toString(),"name",r.getString(2),"createdAt",r.getTimestamp(3).toInstant().toString(),
                    "lastSeenAt",r.getTimestamp(4).toInstant().toString(),"revoked",r.getTimestamp(5)!=null,"revision",r.getLong(6),"rememberLogin",r.getBoolean(7),"deviceInfo",JSON.fromJson(r.getString(8),Map.class),"deviceInfoUpdatedAt",r.getTimestamp(9)==null?"":r.getTimestamp(9).toInstant().toString(),"apps",grants(c,id)));
            }
        }
        return Map.of("devices",devices,"apps",APPS,"pairingUrl",broker(c));
    }
    static Map<String,Object> issue(Connection c,int actor,JsonObject b) throws Exception {
        requireAdmin(c,actor);
        String name=name(b),code=LanSecurity.randomToken(),base=broker(c);
        List<String> keys=keys(b);
        if(base.isEmpty()) throw new Denied(409,"WEB_STOPPED","Start Mobile Items or Employee Web before pairing a phone.");
        Instant expires=Instant.now().plusSeconds(600);
        try(var p=c.prepareStatement("INSERT INTO mobile_device_pairings(code_hash,device_name,app_keys,issued_by,expires_at) VALUES(?,?,?,?,?)")) {
            p.setString(1,LanSecurity.sha256(code));p.setString(2,name);p.setArray(3,c.createArrayOf("text",keys.toArray()));p.setInt(4,actor);p.setTimestamp(5,Timestamp.from(expires));p.executeUpdate();
        }
        cleanup(c);
        audit(c,actor,"MOBILE_DEVICE_QR_ISSUED","Issued approval for "+name+"; apps="+keys);
        return Map.of("url",base+"#pair="+code,"expiresAt",expires.toString());
    }
    static Map<String,Object> update(Connection c,int actor,JsonObject b) throws Exception {
        requireAdmin(c,actor);
        UUID id; long revision;
        try { id=UUID.fromString(b.get("deviceId").getAsString());revision=b.get("revision").getAsLong(); }
        catch(Exception e) { throw new Denied(400,"VALIDATION_ERROR","Select a current mobile device."); }
        String name=name(b);List<String> keys=keys(b);
        boolean revoke=b.has("revoke")&&b.get("revoke").getAsBoolean();
        boolean previous;
        try(var p=c.prepareStatement("SELECT remember_login FROM mobile_managed_devices WHERE device_id=? AND revision=? AND revoked_at IS NULL FOR UPDATE")) {
            p.setObject(1,id);p.setLong(2,revision);try(var r=p.executeQuery()){if(!r.next())throw new Denied(409,"DEVICE_CHANGED","This device changed or was revoked. Refresh before saving.");previous=r.getBoolean(1);}
        }
        boolean remember=previous;
        if(b.has("rememberLogin")) {
            if(!b.get("rememberLogin").isJsonPrimitive()||!b.getAsJsonPrimitive("rememberLogin").isBoolean())throw new Denied(400,"VALIDATION_ERROR","Choose a valid login policy.");
            remember=b.get("rememberLogin").getAsBoolean();
        }
        try(var p=c.prepareStatement("UPDATE mobile_managed_devices SET device_name=?,revoked_at=CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE revoked_at END,remember_login=?,revision=revision+1 WHERE device_id=? AND revision=? AND revoked_at IS NULL")) {
            p.setString(1,name);p.setBoolean(2,revoke);p.setBoolean(3,remember);p.setObject(4,id);p.setLong(5,revision);
            if(p.executeUpdate()!=1) throw new Denied(409,"DEVICE_CHANGED","This device changed or was revoked. Refresh before saving.");
        }
        replaceGrants(c,id,keys);
        if(previous!=remember) {
            try(var p=c.prepareStatement("UPDATE employee_web_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE revoked_at IS NULL AND browser_device_id IN (SELECT e.device_id FROM employee_web_devices e JOIN mobile_device_credentials k ON k.credential_hash=e.browser_hash WHERE k.device_id=?)")){p.setObject(1,id);p.executeUpdate();}
        }
        audit(c,actor,revoke?"MOBILE_DEVICE_REVOKED":"MOBILE_DEVICE_UPDATED","Device "+id+"; name="+name+"; apps="+keys+"; rememberLogin="+remember);
        return Map.of("saved",true);
    }
    static Map<String,Object> restore(Connection c,int actor,JsonObject body)throws Exception {
        requireAdmin(c,actor);UUID device;long revision;
        try{device=UUID.fromString(body.get("deviceId").getAsString());revision=body.get("revision").getAsLong();}
        catch(Exception e){throw new Denied(400,"VALIDATION_ERROR","Select a current revoked device.");}
        try(var p=c.prepareStatement("UPDATE mobile_managed_devices SET revoked_at=NULL,revision=revision+1 WHERE device_id=? AND revision=? AND revoked_at IS NOT NULL")) {
            p.setObject(1,device);p.setLong(2,revision);
            if(p.executeUpdate()!=1)throw new Denied(409,"DEVICE_CHANGED","This device changed or is already approved. Refresh before restoring it.");
        }
        audit(c,actor,"MOBILE_DEVICE_RESTORED","Restored device "+device+"; existing app grants and browser credentials retained.");
        return Map.of("restored",true);
    }
    static void replaceGrants(Connection c,UUID id,List<String> keys) throws Exception {
        try(var p=c.prepareStatement("DELETE FROM mobile_device_apps WHERE device_id=?")) {p.setObject(1,id);p.executeUpdate();}
        try(var p=c.prepareStatement("INSERT INTO mobile_device_apps(device_id,app_key) VALUES(?,?)")) {
            for(String key:keys) {app(key);p.setObject(1,id);p.setString(2,key);p.addBatch();}p.executeBatch();
        }
    }
    static List<String> grants(Connection c,UUID id) throws SQLException {
        List<String> keys=new ArrayList<>();
        try(var p=c.prepareStatement("SELECT app_key FROM mobile_device_apps WHERE device_id=? ORDER BY app_key")) {p.setObject(1,id);try(var r=p.executeQuery()){while(r.next())keys.add(r.getString(1));}}
        return keys;
    }
    static UUID redeemPairing(Connection c,String code,String credential) throws Exception {
        String name;List<String> keys;
        try(var p=c.prepareStatement("UPDATE mobile_device_pairings SET consumed_at=CURRENT_TIMESTAMP WHERE code_hash=? AND consumed_at IS NULL AND expires_at>CURRENT_TIMESTAMP RETURNING device_name,app_keys")) {
            p.setString(1,LanSecurity.sha256(code));try(var r=p.executeQuery()) {
                if(!r.next()) throw new Denied(401,"PAIRING_INVALID","This QR expired or was already used. Ask for a new QR.");
                name=r.getString(1);keys=Arrays.asList((String[])r.getArray(2).getArray());
            }
        }
        UUID id;
        try(var p=c.prepareStatement("INSERT INTO mobile_managed_devices(device_name) VALUES(?) RETURNING device_id")) {p.setString(1,name);try(var r=p.executeQuery()){r.next();id=(UUID)r.getObject(1);}}
        replaceGrants(c,id,keys);addCredential(c,id,credential);
        audit(c,null,"MOBILE_DEVICE_PAIRED","Device "+id+"; name="+name);
        return id;
    }
    static Map<String,Object> issueRepair(Connection c,int actor,JsonObject body)throws Exception {
        requireAdmin(c,actor);UUID device;
        try{device=UUID.fromString(body.get("deviceId").getAsString());}catch(Exception e){throw new Denied(400,"VALIDATION_ERROR","Select the phone to re-pair.");}
        requireGrant(c,device,"WORKSPACE");
        try(var p=c.prepareStatement("UPDATE mobile_device_handoffs SET consumed_at=CURRENT_TIMESTAMP WHERE device_id=? AND app_key='DEVICE_REPAIR' AND consumed_at IS NULL")){p.setObject(1,device);p.executeUpdate();}
        String token=LanSecurity.randomToken();Instant expires=Instant.now().plusSeconds(600);
        try(var p=c.prepareStatement("INSERT INTO mobile_device_handoffs(token_hash,device_id,app_key,destination,expires_at) VALUES(?,?,'DEVICE_REPAIR',?,?)")) {
            p.setString(1,LanSecurity.sha256(token));p.setObject(2,device);p.setString(3,DeckersWorkspace.HOME);p.setTimestamp(4,Timestamp.from(expires));p.executeUpdate();
        }
        cleanup(c);audit(c,actor,"MOBILE_DEVICE_REPAIR_QR_ISSUED","Re-pair approval for device "+device);
        return Map.of("url",DeckersWorkspace.HOME+"#repair="+token,"expiresAt",expires.toString());
    }
    static UUID redeemRepair(Connection c,String token,String previousCredential,String newCredential)throws Exception {
        UUID device;
        try(var p=c.prepareStatement("UPDATE mobile_device_handoffs SET consumed_at=CURRENT_TIMESTAMP WHERE token_hash=? AND app_key='DEVICE_REPAIR' AND destination=? AND consumed_at IS NULL AND expires_at>CURRENT_TIMESTAMP RETURNING device_id")) {
            p.setString(1,LanSecurity.sha256(token));p.setString(2,DeckersWorkspace.HOME);try(var r=p.executeQuery()){if(!r.next())throw new Denied(401,"REPAIR_INVALID","This re-pair QR expired or was already used. Ask for a new Re-pair QR for this phone.");device=(UUID)r.getObject(1);}
        }
        requireGrant(c,device,"WORKSPACE");
        if(previousCredential!=null)try(var p=c.prepareStatement("DELETE FROM mobile_device_credentials WHERE credential_hash=?")){p.setString(1,LanSecurity.sha256(previousCredential));p.executeUpdate();}
        addCredential(c,device,newCredential);
        audit(c,null,"MOBILE_DEVICE_REPAIRED","Browser approval restored for device "+device);
        return device;
    }
    static void addCredential(Connection c,UUID id,String credential) throws SQLException {
        try(var p=c.prepareStatement("INSERT INTO mobile_device_credentials(credential_hash,device_id) VALUES(?,?) ON CONFLICT(credential_hash) DO NOTHING")) {p.setString(1,LanSecurity.sha256(credential));p.setObject(2,id);p.executeUpdate();}
    }
    public static String credential(HttpExchange x) {
        String cookies=x.getRequestHeaders().getFirst("Cookie");
        if(cookies!=null) for(String part:cookies.split(";")) {String[] pair=part.trim().split("=",2);if(pair.length==2&&pair[0].equals(COOKIE))return pair[1];}
        return null;
    }
    static UUID identity(Connection c,String credential) throws Exception {
        if(credential==null||credential.length()>300) throw new Denied(401,"DEVICE_REQUIRED","Open this app from your paired phone's SmartStock launcher, or scan an administrator's QR.");
        try(var p=c.prepareStatement("SELECT d.device_id FROM mobile_device_credentials k JOIN mobile_managed_devices d USING(device_id) WHERE k.credential_hash=? AND d.revoked_at IS NULL")) {
            p.setString(1,LanSecurity.sha256(credential));try(var r=p.executeQuery()){if(r.next())return (UUID)r.getObject(1);}
        }
        throw new Denied(401,"DEVICE_REVOKED","This browser is not approved. Pair it using Mobile Management.");
    }
    static boolean rememberLogin(Connection c,String credential)throws Exception {
        UUID device=identity(c,credential);
        try(var p=c.prepareStatement("SELECT remember_login FROM mobile_managed_devices WHERE device_id=? AND revoked_at IS NULL")) {
            p.setObject(1,device);try(var r=p.executeQuery()){if(r.next())return r.getBoolean(1);}
        }
        throw new Denied(401,"DEVICE_REVOKED","This device is no longer approved.");
    }

    static UUID requireAny(Connection c,String credential,Collection<String> apps) throws Exception {
        UUID id=identity(c,credential);List<String> enabled=grants(c,id);
        if(apps.stream().noneMatch(enabled::contains)) throw new Denied(403,"DEVICE_APP_DISABLED","This web app is disabled for this device. Ask a device manager to enable it.");
        try(var p=c.prepareStatement("UPDATE mobile_managed_devices SET last_seen_at=CURRENT_TIMESTAMP WHERE device_id=? AND last_seen_at<CURRENT_TIMESTAMP-INTERVAL '1 minute'")){p.setObject(1,id);p.executeUpdate();}
        return id;
    }
    public static UUID requireAny(HttpExchange x,Collection<String> apps) throws Exception {
        try(Connection c=DB.getConnection()) {
            UUID id=requireAny(c,credential(x),apps);
            x.getResponseHeaders().add("Set-Cookie",COOKIE+"="+credential(x)+"; Path=/; Max-Age=34560000; Secure; HttpOnly; SameSite=Lax");
            return id;
        }
    }

    static List<String> mobileApps() {return APPS.stream().filter(a->a.gateway.equals("mobile")||Set.of("INVENTORY","ORDERS").contains(a.key)).map(App::key).toList();}
    static List<String> employeeApps() {return APPS.stream().filter(a->!a.gateway.equals("mobile")&&!a.gateway.equals("scheduler")).map(App::key).toList();}
    static List<String> routeApps(String gateway,String route,JsonObject body) {
        String r=route.startsWith("/")?route.substring(1):route;
        if(gateway.equals("mobile")) {
            if(r.equals("products/save"))return List.of(!body.has("productId")||body.get("productId").isJsonNull()?"MOBILE_ITEMS":"EDIT_ITEMS");
            if(r.equals("custom/save")&&body.has("action")) {
                String action=body.get("action").getAsString();
                String field=action.equals("SAVE_ITEM")?"item":action.equals("SAVE_VARIANT")?"variant":null;
                if(field!=null&&body.has(field)&&body.get(field).isJsonObject()) {
                    JsonObject record=body.getAsJsonObject(field);String id=field+"Id";
                    return List.of(!record.has(id)||record.get(id).isJsonNull()?"MOBILE_ITEMS":"EDIT_ITEMS");
                }
                return List.of("EDIT_ITEMS");
            }
        }
        return routeApps(gateway,route);
    }
    static List<String> routeApps(String gateway,String route) {
        String r=route.startsWith("/")?route.substring(1):route;
        if(gateway.equals("scheduler"))return List.of("SCHEDULER");
        if(r.startsWith("orders/"))return List.of("ORDERS");
        if(r.startsWith("inventory/"))return List.of("INVENTORY");
        if(r.startsWith("receiving/"))return List.of("RECEIVING");
        if(r.startsWith("spoils/"))return List.of("CUSTOM_ORDER_SPOILS");
        if(r.startsWith("design/"))return List.of("CUSTOM_ORDER_DESIGN");
        if(Set.of("account/profile","account/nickname","account/password","account/device-info").contains(r))return APPS.stream().map(App::key).toList();
        if(r.startsWith("auth/drive-"))return List.of("EMPLOYEE_WEB");
        if(r.startsWith("photo/"))return List.of("MOBILE_ITEMS","EDIT_ITEMS","IMAGE_STUDIO");
        if(r.startsWith("studio/"))return List.of("IMAGE_STUDIO");
        if(r.startsWith("customers/"))return List.of("CUSTOMERS");
        if(r.startsWith("minutes/"))return List.of("PHONE_MINUTES");
        if(r.startsWith("payroll/"))return List.of("PAYROLL");
        if(r.equals("balance-sheet"))return List.of("BALANCE_SHEET");
        if(r.equals("overseas"))return List.of("OVERSEAS_ORDERS");
        if(Set.of("employees","history","history/correct").contains(r))return List.of("EMPLOYEE_WEB");
        if(r.startsWith("products/")||r.startsWith("custom/")||r.startsWith("barcodes/")||r.startsWith("price-tags/"))return List.of("MOBILE_ITEMS","EDIT_ITEMS");
        if(r.startsWith("images/"))return List.of("MOBILE_ITEMS","EDIT_ITEMS","IMAGE_STUDIO","RECEIVING","CUSTOM_ORDER_DESIGN","CUSTOM_ORDER_SPOILS");
        if(Set.of("activate","login","session","logout","bootstrap","auth/login","auth/verify","auth/session","auth/logout","auth/drive-login","auth/drive-session","auth/drive-authorize").contains(r))
            return gateway.equals("mobile")?mobileApps():APPS.stream().map(App::key).toList();
        return List.of(); // Unmapped new APIs fail closed until registered.
    }

    static String mobileBase() throws Exception {return "https://"+LanTlsIdentity.mobileWebHostName()+":"+MobileItemWebServer.UI_PORT;}
    static String broker(Connection c) throws Exception {
        try(var p=c.prepareStatement("SELECT public_origin FROM employee_web_runtime WHERE runtime_id=1 AND enabled AND gateway_running AND gateway_heartbeat_at>CURRENT_TIMESTAMP-INTERVAL '10 seconds'");var r=p.executeQuery()) {
            if(r.next()&&r.getString(1)!=null&&!r.getString(1).isBlank()) {
                String assigned=StoreNetworkProvisioning.assignedWebsite();
                return DeckersWorkspace.workspaceHome(assigned==null?r.getString(1):assigned,data.DatabaseConfig.load().locationId());
            }
        }
        try(var p=c.prepareStatement("SELECT enabled FROM mobile_item_web_runtime WHERE runtime_id=1");var r=p.executeQuery()) {if(r.next()&&r.getBoolean(1))return DeckersWorkspace.HOME;}
        return "";
    }
    static String destination(Connection c,App a) throws Exception {
        String origin=switch(a.gateway) {
            case "mobile" -> mobileBase();
            case "inventory" -> URI.create(InventoryWebService.PUBLIC_URL).getScheme()+"://"+URI.create(InventoryWebService.PUBLIC_URL).getAuthority();
            case "orders" -> URI.create(OrdersWebService.PUBLIC_URL).getScheme()+"://"+URI.create(OrdersWebService.PUBLIC_URL).getAuthority();
            case "balance" -> URI.create(BalanceSheetWorkspaceService.PUBLIC_URL).getScheme()+"://"+URI.create(BalanceSheetWorkspaceService.PUBLIC_URL).getAuthority();
            case "customers" -> "https://customermanagement.deckers.gy";
            case "timeclock" -> "https://timeclock.deckers.gy";
            case "overseas" -> "https://overseasshipping.deckers.gy";
            default -> {
                String table=a.gateway.equals("scheduler")?"scheduler_web_runtime":"employee_web_runtime";
                try(var p=c.prepareStatement("SELECT public_origin FROM "+table+" WHERE runtime_id=1 AND enabled");var r=p.executeQuery()) {
                    if(!r.next()||r.getString(1)==null||r.getString(1).isBlank()) throw new Denied(409,"WEB_STOPPED","Start "+a.name+" before opening it.");
                    yield r.getString(1);
                }
            }
        };
        return origin+a.path;
    }
    static String callback(String destination) {URI uri=URI.create(destination);return uri.getScheme()+"://"+uri.getAuthority()+(uri.getPath().startsWith("/employees")?"/employees/mobile-management/":uri.getPath().startsWith("/scheduler")?"/scheduler/mobile-management/":"/mobile-management/");}
    static String handoff(Connection c,UUID id,String key) throws Exception {
        App a=app(key);
        if(!grants(c,id).contains(key))throw new Denied(403,"DEVICE_APP_DISABLED","This app is disabled for this device.");
        String destination=destination(c,a),token=LanSecurity.randomToken();
        try(var p=c.prepareStatement("INSERT INTO mobile_device_handoffs(token_hash,device_id,app_key,destination,expires_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP+INTERVAL '60 seconds')")) {
            p.setString(1,LanSecurity.sha256(token));p.setObject(2,id);p.setString(3,key);p.setString(4,destination);p.executeUpdate();
        }
        return callback(destination)+"#handoff="+token;
    }
    static String redeemHandoff(Connection c,String token,String base,String credential) throws Exception {
        UUID device;String key,destination;
        try(var p=c.prepareStatement("UPDATE mobile_device_handoffs SET consumed_at=CURRENT_TIMESTAMP WHERE token_hash=? AND consumed_at IS NULL AND expires_at>CURRENT_TIMESTAMP RETURNING device_id,app_key,destination")) {
            p.setString(1,LanSecurity.sha256(token));try(var r=p.executeQuery()) {
                if(!r.next())throw new Denied(401,"HANDOFF_INVALID","This app link expired. Open it again from the launcher.");
                device=(UUID)r.getObject(1);key=r.getString(2);destination=r.getString(3);
            }
        }
        if(!callback(destination).equals(base))throw new Denied(403,"HANDOFF_DESTINATION","This link belongs to a different app origin.");
        try(var p=c.prepareStatement("SELECT 1 FROM mobile_managed_devices d JOIN mobile_device_apps a USING(device_id) WHERE d.device_id=? AND d.revoked_at IS NULL AND a.app_key=?")) {
            p.setObject(1,device);p.setString(2,key);try(var r=p.executeQuery()){if(!r.next())throw new Denied(403,"DEVICE_APP_DISABLED","This device or app was disabled.");}
        }
        try(var p=c.prepareStatement("SELECT device_id FROM mobile_device_credentials WHERE credential_hash=?")) {
            p.setString(1,LanSecurity.sha256(credential));try(var r=p.executeQuery()){if(r.next()&&!device.equals(r.getObject(1)))throw new Denied(409,"DEVICE_IDENTITY_CHANGED","This browser is paired to a different phone. Clear its site data before pairing it again.");}
        }
        addCredential(c,device,credential);return destination;
    }
    static void cleanup(Connection c) throws SQLException {
        try(var s=c.createStatement()){s.executeUpdate("DELETE FROM mobile_device_pairings WHERE expires_at<CURRENT_TIMESTAMP-INTERVAL '1 day'");s.executeUpdate("DELETE FROM mobile_device_handoffs WHERE expires_at<CURRENT_TIMESTAMP-INTERVAL '1 day'");s.executeUpdate("DELETE FROM mobile_device_links WHERE expires_at<CURRENT_TIMESTAMP-INTERVAL '1 day'");}
    }

    record LinkRequest(String requestId,String claim,String approvalUrl,Instant expiresAt) { }
    static LinkRequest requestLink(Connection c,String key,String destination) throws Exception {
        if(!key.equals("WORKSPACE"))app(key);
        String base=broker(c);
        if(base.isBlank())throw new Denied(409,"WEB_STOPPED","Start the staff gateway before connecting this app.");
        // This entry point is intentionally available before approval; bound its live queue.
        try(var p=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended('smartstock.mobile-device-links',0))")){p.execute();}
        try(var p=c.prepareStatement("SELECT count(*) FROM mobile_device_links WHERE consumed_at IS NULL AND expires_at>CURRENT_TIMESTAMP");var r=p.executeQuery()) {
            r.next();if(r.getInt(1)>=256)throw new Denied(429,"LINK_BUSY","Too many app connection requests. Wait a few minutes and try again.");
        }
        String request=LanSecurity.randomToken(),claim=LanSecurity.randomToken();Instant expires=Instant.now().plusSeconds(600);
        try(var p=c.prepareStatement("INSERT INTO mobile_device_links(request_hash,claim_hash,app_key,destination,expires_at) VALUES(?,?,?,?,?)")) {
            p.setString(1,LanSecurity.sha256(request));p.setString(2,LanSecurity.sha256(claim));p.setString(3,key);p.setString(4,destination);p.setTimestamp(5,Timestamp.from(expires));p.executeUpdate();
        }
        cleanup(c);
        return new LinkRequest(request,claim,base+"#link="+request,expires);
    }
    private static String pendingLinkApp(Connection c,String request,boolean lock) throws Exception {
        try(var p=c.prepareStatement("SELECT app_key FROM mobile_device_links WHERE request_hash=? AND consumed_at IS NULL AND expires_at>CURRENT_TIMESTAMP"+(lock?" FOR UPDATE":""))) {
            p.setString(1,LanSecurity.sha256(request));try(var r=p.executeQuery()){if(r.next())return r.getString(1);}
        }
        throw new Denied(401,"LINK_INVALID","This connection request expired or was already used. Start again in the home-screen app.");
    }
    private static void requireGrant(Connection c,UUID device,String key) throws Exception {
        if(key.equals("WORKSPACE")) {
            try(var p=c.prepareStatement("SELECT 1 FROM mobile_managed_devices WHERE device_id=? AND revoked_at IS NULL")) {
                p.setObject(1,device);try(var r=p.executeQuery()){if(r.next())return;}
            }
            throw new Denied(403,"DEVICE_REVOKED","This device was revoked.");
        }
        try(var p=c.prepareStatement("SELECT 1 FROM mobile_managed_devices d JOIN mobile_device_apps a USING(device_id) WHERE d.device_id=? AND d.revoked_at IS NULL AND a.app_key=?")) {
            p.setObject(1,device);p.setString(2,key);try(var r=p.executeQuery()){if(r.next())return;}
        }
        throw new Denied(403,"DEVICE_APP_DISABLED","This app is disabled for this device. A device manager must enable it.");
    }
    static Map<String,Object> linkInfo(Connection c,String pairedCredential,String request) throws Exception {
        UUID device=identity(c,pairedCredential);String key=pendingLinkApp(c,request,false);requireGrant(c,device,key);
        try(var p=c.prepareStatement("SELECT device_name FROM mobile_managed_devices WHERE device_id=?")) {
            p.setObject(1,device);try(var r=p.executeQuery()){r.next();return Map.of("appName",key.equals("WORKSPACE")?"Deckers workspace":app(key).name(),"deviceName",r.getString(1));}
        }
    }
    static void approveLink(Connection c,String pairedCredential,String request) throws Exception {
        UUID device=identity(c,pairedCredential);String key=pendingLinkApp(c,request,true);requireGrant(c,device,key);
        try(var p=c.prepareStatement("UPDATE mobile_device_links SET device_id=?,approved_at=CURRENT_TIMESTAMP WHERE request_hash=? AND (device_id IS NULL OR device_id=?)")) {
            p.setObject(1,device);p.setString(2,LanSecurity.sha256(request));p.setObject(3,device);
            if(p.executeUpdate()!=1)throw new Denied(409,"LINK_ALREADY_APPROVED","This app was connected to a different device. Start again in the home-screen app.");
        }
        audit(c,null,"MOBILE_DEVICE_APP_LINK_APPROVED","Device "+device+"; app="+key);
    }
    static boolean claimLink(Connection c,String request,String claim,String destination,String credential) throws Exception {
        if(claim==null)throw new Denied(401,"LINK_INVALID","Start the connection in this home-screen app first.");
        UUID device;String key;
        try(var p=c.prepareStatement("SELECT device_id,app_key FROM mobile_device_links WHERE request_hash=? AND claim_hash=? AND destination=? AND consumed_at IS NULL AND expires_at>CURRENT_TIMESTAMP FOR UPDATE")) {
            p.setString(1,LanSecurity.sha256(request));p.setString(2,LanSecurity.sha256(claim));p.setString(3,destination);
            try(var r=p.executeQuery()){if(!r.next())throw new Denied(401,"LINK_INVALID","This app connection expired or belongs to another browser. Start again.");device=(UUID)r.getObject(1);key=r.getString(2);}
        }
        if(device==null)return false;
        requireGrant(c,device,key);addCredential(c,device,credential);
        try(var p=c.prepareStatement("UPDATE mobile_device_links SET consumed_at=CURRENT_TIMESTAMP WHERE request_hash=?")){p.setString(1,LanSecurity.sha256(request));p.executeUpdate();}
        audit(c,null,"MOBILE_DEVICE_APP_CONNECTED","Device "+device+"; app="+key);
        return true;
    }
    private static String linkCookie(HttpExchange x) {
        String cookies=x.getRequestHeaders().getFirst("Cookie");
        if(cookies!=null)for(String part:cookies.split(";")){String[] pair=part.trim().split("=",2);if(pair.length==2&&pair[0].equals("ss_mobile_link"))return pair[1];}
        return null;
    }
    static void audit(Connection c,Integer actor,String event,String details) throws SQLException {
        try(var p=c.prepareStatement("INSERT INTO security_audit_events(event_type,actor_user_id,details) VALUES(?,?,?)")) {p.setString(1,event);if(actor==null)p.setNull(2,Types.INTEGER);else p.setInt(2,actor);p.setString(3,details);p.executeUpdate();}
    }

    /** Handles only the explicitly registered pairing/launcher path, on each gateway. */
    public static boolean handle(HttpExchange x,String prefix,String origin) throws Exception {
        return handle(x,prefix,origin,null);
    }
    static boolean handle(HttpExchange x,String prefix,String origin,DeckersWorkspace.Authentication authentication) throws Exception {
        String path=x.getRequestURI().getPath();
        if(!path.startsWith(prefix))return false;
        String caller=x.getRequestHeaders().getFirst("Origin");
        if(prefix.equals("/employees/mobile-management/") && (Set.of(DeckersWorkspace.ORIGIN,"https://timeclock.deckers.gy","https://overseasshipping.deckers.gy","https://ordersmanagement.deckers.gy","https://inventory.deckers.gy","https://balancesheet.deckers.gy","https://customermanagement.deckers.gy").contains(caller==null?"":caller)||DeckersWorkspace.managedOrigin(caller,data.DatabaseConfig.load().locationId()))) origin=caller;
        String route=path.substring(prefix.length());
        x.getResponseHeaders().set("Cache-Control","no-store");
        x.getResponseHeaders().set("Referrer-Policy","no-referrer");
        x.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        x.getResponseHeaders().set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; font-src 'self'; media-src 'self' blob:; connect-src 'self' "+mobileBase()+"; img-src 'self' data: blob:; manifest-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
        try {
            String image=DeckersWorkspace.asset(route);
            if(image==null)image=DeckersScreens.asset(route);
            if("GET".equals(x.getRequestMethod())&&route.matches("screen-modules/[A-Z_]+\\.js")) {
                send(x,200,"application/javascript",DeckersScreens.module(route.substring(15,route.length()-3)).getBytes(StandardCharsets.UTF_8));return true;
            }
            if("GET".equals(x.getRequestMethod())&&(Set.of("","app.js","screens.js","app.css","signin.css","reconnect.js","manifest.webmanifest").contains(route)||image!=null)) {
                String resource=image!=null?image:"shared-mobile/"+(route.isEmpty()?"index.html":route);
                try(var in=MobileDeviceService.class.getClassLoader().getResourceAsStream(resource)) {
                    if(in==null)throw new IllegalStateException("Shared mobile asset missing");
                    send(x,200,route.endsWith(".ttf")?"font/ttf":route.endsWith(".txt")?"text/plain":route.endsWith(".png")?"image/png":route.endsWith(".svg")?"image/svg+xml":route.endsWith(".webmanifest")?"application/manifest+json":route.endsWith("js")?"application/javascript":route.endsWith("css")?"text/css":"text/html",in.readAllBytes());
                }return true;
            }
            if(route.equals("network-probe")) {
                if(!prefix.equals("/mobile-management/") || !MobileItemWebServer.allowsLocalWebPeer(x.getRemoteAddress().getAddress(),x.getRequestHeaders().containsKey("Forwarded")||x.getRequestHeaders().containsKey("X-Forwarded-For")))throw new Denied(403,"LAN_ONLY","Connect to the store network.");
                if(!DeckersWorkspace.allowedProbeOrigin(caller,origin))throw new Denied(403,"ORIGIN_INVALID","This web origin is not allowed.");
                x.getResponseHeaders().set("Access-Control-Allow-Origin",caller);
                x.getResponseHeaders().set("Vary","Origin");
                x.getResponseHeaders().set("Access-Control-Allow-Methods","POST");
                x.getResponseHeaders().set("Access-Control-Allow-Headers","Content-Type");
                x.getResponseHeaders().set("Access-Control-Allow-Private-Network","true");
                if("OPTIONS".equals(x.getRequestMethod())){x.sendResponseHeaders(204,-1);return true;}
                if(!"POST".equals(x.getRequestMethod()))throw new Denied(405,"METHOD_NOT_ALLOWED","POST is required.");
                byte[] probe=x.getRequestBody().readNBytes(1025);
                if(probe.length>1024)throw new Denied(413,"REQUEST_TOO_LARGE","Request is too large.");
                String token=value(JsonParser.parseString(new String(probe,StandardCharsets.UTF_8)).getAsJsonObject(),"challenge");
                if(!DeckersWorkspace.verify(token,caller))throw new Denied(403,"LAN_ONLY","Check the store connection and try again.");
                send(x,200,"application/json","{\"ok\":true,\"data\":{\"connected\":true}}".getBytes(StandardCharsets.UTF_8));return true;
            }
            if(!"POST".equals(x.getRequestMethod()))throw new Denied(405,"METHOD_NOT_ALLOWED","POST is required.");
            if(!origin.equals(x.getRequestHeaders().getFirst("Origin")))throw new Denied(403,"ORIGIN_INVALID","Open this page on its configured HTTPS address.");
            int limit=route.equals("screen-api")?18*1024*1024:8192;
            byte[] bytes=x.getRequestBody().readNBytes(limit+1);
            if(bytes.length>limit)throw new Denied(413,"REQUEST_TOO_LARGE","Request is too large.");
            if(ServerRoleGuard.blocksMutations())throw new Denied(503,"SERVER_NOT_PRIMARY",ServerRoleGuard.safeMessage());
            JsonObject b=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
            Object result;String newCredential=null,newClaim=null;boolean clearClaim=false,repaired=false;
            try(Connection c=DB.getConnection()) {
                c.setAutoCommit(false);
                try {
                    if(route.equals("link-request")) {
                        String key=value(b,"app");
                        List<String> available=prefix.equals("/mobile-management/")?mobileApps():prefix.equals("/scheduler/mobile-management/")?List.of("SCHEDULER"):employeeApps();
                        if(!key.equals("WORKSPACE")&&!available.contains(key))throw new Denied(400,"APP_UNKNOWN","Choose an app served by this gateway.");
                        LinkRequest link=requestLink(c,key,origin+prefix);newClaim=link.claim();
                        result=Map.of("requestId",link.requestId(),"approvalUrl",link.approvalUrl(),"expiresAt",link.expiresAt().toString());
                    }else if(route.equals("link-status")) {
                        String candidate=LanSecurity.randomToken();boolean approved=claimLink(c,value(b,"requestId"),linkCookie(x),origin+prefix,candidate);
                        if(approved){newCredential=candidate;clearClaim=true;}
                        result=Map.of("approved",approved);
                    }else if(route.equals("link-info")) {
                        result=linkInfo(c,credential(x),value(b,"requestId"));
                    }else if(route.equals("approve-link")) {
                        approveLink(c,credential(x),value(b,"requestId"));result=Map.of("approved",true);
                    }else if(route.equals("repair")) {
                        newCredential=LanSecurity.randomToken();redeemRepair(c,value(b,"token"),credential(x),newCredential);
                        repaired=true;clearClaim=true;result=Map.of("paired",true,"repaired",true);
                    }else if(route.equals("pair")) {
                        boolean alreadyPaired=false;
                        try {identity(c,credential(x));alreadyPaired=true;}catch(Denied ignored) { }
                        if(alreadyPaired)throw new Denied(409,"ALREADY_PAIRED","This browser is already paired. Edit its apps in Mobile Management.");
                        newCredential=LanSecurity.randomToken();redeemPairing(c,value(b,"token"),newCredential);result=Map.of("paired",true);
                    }else if(route.equals("redeem")) {
                        newCredential=credential(x);if(newCredential==null)newCredential=LanSecurity.randomToken();result=Map.of("url",redeemHandoff(c,value(b,"token"),origin+prefix,newCredential));
                    }else {
                        UUID id=identity(c,credential(x));
                        newCredential=credential(x);
                        if(authentication==null)throw new Denied(401,"SESSION_REQUIRED","Sign in to continue.");
                        DeckersWorkspace.User user=authentication.require(x,c);
                        if(route.equals("open")||route.equals("screen")||route.equals("screen-api")||route.equals("network-challenge")) {
                            String key=value(b,"app");App selected=app(key);
                            requireGrant(c,id,key);
                            if(!DeckersWorkspace.canOpen(key,user.permissions()))throw new Denied(403,"ACCESS_DENIED","Your employee role does not have access to this app.");
                            if(route.equals("network-challenge")) {
                                if(!selected.gateway.equals("mobile")&&!key.equals("PAYROLL"))throw new Denied(400,"APP_UNKNOWN","This app does not need a store network check.");
                                result=Map.of("challenge",DeckersWorkspace.challenge(id,user.id(),key,origin),"probeUrl",mobileBase()+"/mobile-management/network-probe");
                            }else {
                                if(selected.gateway.equals("mobile"))DeckersWorkspace.consume(value(b,"challenge"),id,user.id(),key,origin);
                                if(route.equals("screen")||route.equals("screen-api")) {
                                    if(user.api()==null)throw new Denied(409,"WORKSPACE_REQUIRED","Open the Deckers workspace to use this screen.");
                                    if(route.equals("screen"))result=DeckersScreens.packet(key);
                                    else {
                                        String operation=value(b,"route");JsonObject payload=b.has("payload")?b.getAsJsonObject("payload"):new JsonObject();
                                        payload.remove("networkStoreId");
                                        if(key.equals("PAYROLL")&&operation.equals("payroll/dashboard")&&b.has("challenge")) {
                                            // Network detection chooses a default; Payroll itself works off-site.
                                            try{DeckersWorkspace.consume(value(b,"challenge"),id,user.id(),key,origin);payload.addProperty("networkStoreId",data.DatabaseConfig.load().locationId());}
                                            catch(Denied unverified){if(!"LAN_ONLY".equals(unverified.code))throw unverified;}
                                        }
                                        if(!DeckersScreens.allows(key,operation,payload))throw new Denied(403,"ACCESS_DENIED","This operation does not belong to this screen.");
                                        result=user.api().execute(x,c,key,operation,payload);
                                    }
                                }else {
                                    String sameOrigin=DeckersWorkspace.sameOriginDestination(selected,origin);
                                    result=Map.of("url",sameOrigin==null?handoff(c,id,key):sameOrigin);
                                }
                            }
                        }
                        else if(route.equals("state")) {
                            List<String> enabled=grants(c,id);String name;
                            try(var p=c.prepareStatement("SELECT device_name FROM mobile_managed_devices WHERE device_id=?")){p.setObject(1,id);try(var r=p.executeQuery()){r.next();name=r.getString(1);}}
                            result=Map.of("name",name,"employeeName",user.name(),"userId",user.id(),"screens",user.api()!=null,"apps",DeckersWorkspace.apps(enabled,user.permissions()));
                        }else throw new Denied(404,"NOT_FOUND","Unknown mobile management route.");
                    }
                    c.commit();
                }catch(Exception e){c.rollback();throw e;}
            }
            if(repaired) {
                for(String cookie:List.of("ss_employee","ss_employee_browser"))x.getResponseHeaders().add("Set-Cookie",cookie+"=; Path=/employees; Max-Age=0; Secure; HttpOnly; SameSite=Strict");
                for(String cookie:List.of("ss_scheduler","ss_scheduler_browser"))x.getResponseHeaders().add("Set-Cookie",cookie+"=; Path=/scheduler; Max-Age=0; Secure; HttpOnly; SameSite=Strict");
                x.getResponseHeaders().add("Set-Cookie","ss_session=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Strict");
            }
            if(newCredential!=null)x.getResponseHeaders().add("Set-Cookie",COOKIE+"="+newCredential+"; Path=/; Max-Age=34560000; Secure; HttpOnly; SameSite=Lax");
            if(newClaim!=null)x.getResponseHeaders().add("Set-Cookie","ss_mobile_link="+newClaim+"; Path="+prefix+"; Max-Age=600; Secure; HttpOnly; SameSite=Lax");
            if(clearClaim)x.getResponseHeaders().add("Set-Cookie","ss_mobile_link=; Path="+prefix+"; Max-Age=0; Secure; HttpOnly; SameSite=Lax");
            send(x,200,"application/json",JSON.toJson(Map.of("ok",true,"data",result)).getBytes(StandardCharsets.UTF_8));
        }catch(Denied e) {send(x,e.status,"application/json",JSON.toJson(Map.of("ok",false,"error",Map.of("code",e.code,"message",e.getMessage()))).getBytes(StandardCharsets.UTF_8));}
        catch(JsonParseException|IllegalStateException e){send(x,400,"application/json","{\"ok\":false,\"error\":{\"message\":\"Invalid request.\"}}".getBytes(StandardCharsets.UTF_8));}
        return true;
    }
    private static String value(JsonObject b,String key)throws Denied {try{String v=b.get(key).getAsString();if(v.isBlank()||v.length()>300)throw new IllegalArgumentException();return v;}catch(Exception e){throw new Denied(400,"VALIDATION_ERROR","A valid "+key+" is required.");}}
    private static void send(HttpExchange x,int status,String type,byte[] bytes)throws Exception {x.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);}
}
