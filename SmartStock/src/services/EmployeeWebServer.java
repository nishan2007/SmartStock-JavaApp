package services;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import data.DB;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Dedicated browser gateway. Only /employees paths may be exposed by the reverse proxy. */
public final class EmployeeWebServer implements AutoCloseable {
    public static final int DEFAULT_PORT=8450;
    private static final Gson GSON=LanJson.create();
    private final HttpsServer server;
    private final ExecutorService executor;
    private final String publicOrigin;
    private final UUID generation;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final SupabaseProjectConfig project=SupabaseProjectConfig.load();
    private final boolean mfaRequired=(Boolean.parseBoolean(System.getenv("SMARTSTOCK_EMPLOYEE_REQUIRE_MFA"))||Boolean.parseBoolean(System.getenv("SMARTSTOCK_SCHEDULER_REQUIRE_MFA")));
    private final ConcurrentMap<String,Preauth> preauths=new ConcurrentHashMap<>();

    private EmployeeWebServer(HttpsServer server,ExecutorService executor,String origin,UUID generation) {
        this.server=server;this.executor=executor;this.publicOrigin=origin;this.generation=generation;
    }

    public static EmployeeWebServer start(String origin)throws Exception {
        origin=cleanOrigin(origin);
        if(origin==null) throw new IllegalArgumentException("An HTTPS employee web origin is required.");
        UUID generation;
        try(Connection c=DB.getConnection();PreparedStatement p=c.prepareStatement(
                "SELECT generation FROM employee_web_runtime WHERE runtime_id=1 AND enabled=TRUE");ResultSet r=p.executeQuery()) {
            if(!r.next()) throw new IllegalStateException("Employee web app is stopped.");
            generation=(UUID)r.getObject(1);
        }
        HttpsServer server=HttpsServer.create(new InetSocketAddress("127.0.0.1",
                Integer.getInteger("smartstock.employee.web.port",DEFAULT_PORT)),50);
        ExecutorService pool=new ThreadPoolExecutor(8,8,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(64),
                r->{Thread t=new Thread(r,"smartstock-employee-web");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
        try {
            server.setHttpsConfigurator(new HttpsConfigurator(LanTlsIdentity.loadOrCreate().sslContext()));
            server.setExecutor(pool);
            EmployeeWebServer app=new EmployeeWebServer(server,pool,origin,generation);
            server.createContext("/employees",app::handle);server.start();return app;
        } catch(Exception e) {server.stop(0);pool.shutdownNow();throw e;}
    }

    private void handle(HttpExchange x) {
        try {
            if(MobileDeviceService.handle(x,"/employees/mobile-management/",publicOrigin,(exchange,c)->{
                requireAvailable(c);UUID browser=requireBrowser(c,exchange);
                Session s=requireSession(c,exchange,browser,exchange.getRequestURI().getPath().endsWith("/screen-api"),false);
                return new DeckersWorkspace.User(s.userId,s.name,s.permissions,
                    (request,connection,app,route,body)->workspaceExecute(request,connection,s,browser,app,route,body));
            }))return;
            String path=x.getRequestURI().getPath();
            if(!path.equals("/employees")&&!path.startsWith("/employees/")) throw err(404,"NOT_FOUND","Not found.");
            String route=path.substring("/employees".length());
            if(route.isEmpty()) {
                headers(x);x.getResponseHeaders().set("Location","/employees/");x.sendResponseHeaders(308,-1);return;
            }
            if(route.startsWith("/api/v1/")) api(x,route.substring(8)); else asset(x,route);
        } catch(WebError e) {json(x,e.status,Map.of("ok",false,"error",Map.of("code",e.code,"message",e.getMessage())));}
        catch(BalanceSheetWorkspaceService.Conflict e) {json(x,409,Map.of("ok",false,"error",Map.of("code","BALANCE_SHEET_CONFLICT","message",e.getMessage())));}
        catch(BalanceSheetWorkspaceService.Validation e) {json(x,400,Map.of("ok",false,"error",Map.of("code","BALANCE_SHEET_VALIDATION","message",e.getMessage())));}
        catch(LanCustomerAccountService.RuleViolation e) {json(x,e.status(),Map.of("ok",false,"error",Map.of("code",e.code(),"message",e.safeMessage())));}
        catch(SQLException e) {if("22023".equals(e.getSQLState()))json(x,409,Map.of("ok",false,"error",Map.of("code","MINUTES_REJECTED","message",e.getMessage())));else {e.printStackTrace();json(x,503,Map.of("ok",false,"error",Map.of("code","UNAVAILABLE","message","The store could not complete this request. Please try again.")));}}
        catch(MobileDeviceService.Denied e) {json(x,e.status,Map.of("ok",false,"error",Map.of("code",e.code,"message",e.getMessage())));}
        catch(SecurityException e) {json(x,403,Map.of("ok",false,"error",Map.of("code","ACCESS_DENIED","message",e.getMessage())));}
        catch(PayrollWebService.Validation e) {json(x,400,Map.of("ok",false,"error",Map.of("code","PAYROLL_REJECTED","message",e.getMessage())));}
        catch(IllegalArgumentException e) {json(x,400,Map.of("ok",false,"error",Map.of("code","INVALID_REQUEST","message","Check the employee, dates and request values.")));}
        catch(Exception e) {json(x,503,Map.of("ok",false,"error",Map.of("code","UNAVAILABLE","message","Employee app is unavailable. Please try again.")));}
        finally {x.close();}
    }

    private void asset(HttpExchange x,String route)throws Exception {
        if(!"GET".equals(x.getRequestMethod())) throw err(405,"METHOD_NOT_ALLOWED","GET is required.");
        if(Set.of("/orders.html","/orders.js","/orders.css","/orders.webmanifest","/orders-icon-v2-180.png","/orders-icon-v2-192.png","/orders-icon-v2-512.png","/orders-icon-v2-1024.png").contains(route)) {
            try(InputStream in=getClass().getClassLoader().getResourceAsStream("mobile-web"+route)) {
                if(in==null)throw err(404,"NOT_FOUND","Web asset missing.");
                String type=route.endsWith(".png")?"image/png":route.endsWith(".js")?"application/javascript":route.endsWith(".css")?"text/css":route.endsWith(".webmanifest")?"application/manifest+json":"text/html";
                byte[] bytes=in.readAllBytes();headers(x);x.getResponseHeaders().set("Content-Type",type);x.sendResponseHeaders(200,bytes.length);x.getResponseBody().write(bytes);return;
            }
        }
        if(route.equals("/overseas.html")) {
            try(InputStream in=getClass().getClassLoader().getResourceAsStream("employee-web/index.html")) {
                if(in==null)throw err(404,"NOT_FOUND","Web asset missing.");
                String html=new String(in.readAllBytes(),StandardCharsets.UTF_8).replace("<body>","<body data-workspace=\"overseas\">").replace("<title>Deckers Time Clock</title>","<title>Deckers Overseas Orders</title>");
                text(x,200,"text/html; charset=utf-8",html);return;
            }
        }
        if("/health".equals(route)||"/orders-health".equals(route)||"/inventory-health".equals(route)||"/customers-health".equals(route)) {
            try(Connection c=DB.getConnection()){requireAvailable(c);}
            json(x,200,Map.of("ok",true));return;
        }
        if(java.util.Set.of("/minutes.html","/minutes.js","/minutes.css","/minutes-icons.ttf","/minutes-icons-LICENSE.txt","/minutes-logo.png","/customers.html","/customers.js","/customers.css","/inventory-icon-180.png","/inventory-icon-192.png","/inventory-icon-512.png","/inventory.html","/inventory.js","/inventory.css","/inventory.webmanifest","/inventory-icon.svg","/orders-barcode.js").contains(route)) {
            try(InputStream in=getClass().getClassLoader().getResourceAsStream("mobile-web"+route)) {
                if(in==null)throw err(404,"NOT_FOUND","Web asset missing.");
                if(route.endsWith(".png")||route.endsWith(".ttf")){byte[] bytes=in.readAllBytes();headers(x);x.getResponseHeaders().set("Content-Type",route.endsWith(".ttf")?"font/ttf":"image/png");x.sendResponseHeaders(200,bytes.length);x.getResponseBody().write(bytes);return;}
                String type=route.endsWith(".txt")?"text/plain":route.endsWith(".svg")?"image/svg+xml":route.endsWith(".js")?"application/javascript":route.endsWith(".css")?"text/css":route.endsWith(".webmanifest")?"application/manifest+json":"text/html";
                text(x,200,type+"; charset=utf-8",new String(in.readAllBytes(),StandardCharsets.UTF_8));return;
            }
        }
        String file=switch(route) {case "","/","/index.html"->"index.html";case "/app.js"->"app.js";case "/app.css"->"app.css";case "/overseas.js"->"overseas.js";case "/balance-sheet.html"->"balance-sheet.html";case "/balance-sheet.js"->"balance-sheet.js";case "/balance-sheet.css"->"balance-sheet.css";case "/balance-sheet.webmanifest"->"balance-sheet.webmanifest";case "/icons/balance-sheet-180.png"->"icons/balance-sheet-180.png";case "/icons/balance-sheet-192.png"->"icons/balance-sheet-192.png";case "/icons/balance-sheet-512.png"->"icons/balance-sheet-512.png";default->null;};
        if(file==null) throw err(404,"NOT_FOUND","Not found.");
        try(InputStream in=getClass().getClassLoader().getResourceAsStream("employee-web/"+file)) {
            if(in==null) throw err(404,"NOT_FOUND","Web asset missing.");
            if(file.endsWith(".png")){byte[] bytes=in.readAllBytes();headers(x);x.getResponseHeaders().set("Content-Type","image/png");x.sendResponseHeaders(200,bytes.length);x.getResponseBody().write(bytes);return;}
            String type=file.endsWith(".webmanifest")?"application/manifest+json":file.endsWith(".js")?"application/javascript":file.endsWith(".css")?"text/css":"text/html";
            text(x,200,type+"; charset=utf-8",new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
    }

    private void api(HttpExchange x,String route)throws Exception {
        if(!"POST".equals(x.getRequestMethod())) throw err(405,"METHOD_NOT_ALLOWED","POST is required.");
        sameOrigin(x);
        if(route.equals("activate"))throw err(401,"ACTIVATION_REQUIRED","Pair this browser in Mobile Management.");
        MobileDeviceService.requireAny(x,MobileDeviceService.routeApps("employee",route));
        preauths.entrySet().removeIf(e->!e.getValue().expires.isAfter(Instant.now()));
        JsonObject b=body(x);
        if(route.equals("auth/login")||route.equals("auth/drive-login")||route.equals("auth/verify")) {
            UUID browser;
            // Do not hold a pooled database connection during online authentication.
            try(Connection c=DB.getConnection()) {
                c.setAutoCommit(false);
                try {requireAvailable(c);browser=requireBrowser(c,x);c.commit();}
                catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
            }
            Object result=route.equals("auth/verify")?verify(x,b,browser):login(x,b,browser,route.equals("auth/drive-login"));
            json(x,200,Map.of("ok",true,"data",result));return;
        }
        Object result;
        try(Connection c=DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                requireAvailable(c);
                if(route.equals("activate")) result=activate(c,x,b);
                else {
                    UUID browser=requireBrowser(c,x);
                    switch(route) {
                        case "auth/session","account/profile","account/nickname","account/password","account/device-info","auth/drive-session","auth/drive-authorize","bootstrap","employees","history","history/correct","auth/logout","overseas","orders/dashboard","orders/details","orders/assign","orders/lookup","balance-sheet","inventory/search","customers/list","customers/details","customers/history","customers/transaction","customers/order-file","customers/payment-options","customers/record-adjustment","customers/save","minutes/bootstrap","minutes/history","minutes/create","minutes/reverse" -> {
                            boolean drive=route.startsWith("auth/drive-") ||
                                    (Set.of("auth/session","auth/logout").contains(route)&&"smartdrive".equals(str(b,"application")));
                            Session s=requireSession(c,x,browser,!Set.of("auth/session","auth/drive-session").contains(route),drive);
                            requireRouteAccess(route,s.permissions);
                            result=authenticated(x,c,s,browser,route,b);
                        }
                        default -> throw err(404,"NOT_FOUND","API route not found.");
                    }
                }
                c.commit();
            } catch(Exception e) {c.rollback();throw e;} finally {c.setAutoCommit(true);}
        }
        json(x,200,Map.of("ok",true,"data",result));
    }

    private Object workspaceExecute(HttpExchange x,Connection c,Session s,UUID browser,String app,String route,JsonObject b)throws Exception {
        if(route.startsWith("mobile/")) {
            c.commit();
            return MobileItemWebServer.executeWorkspace(x,s.id,s.userId,route.substring(7),b);
        }
        if(app.equals("SCHEDULER")) {
            c.commit();
            return SchedulerWebServer.executeWorkspace(x,s.id,s.userId,route,b);
        }
        requireRouteAccess(route,s.permissions);
        return authenticated(x,c,s,browser,route,b);
    }
    private Object authenticated(HttpExchange x,Connection c,Session s,UUID browser,String route,JsonObject b)throws Exception {
        return switch(route) {
            case "auth/session" -> renewCsrf(c,s);
            case "account/device-info" -> {
                MobileDeviceInfo.save(c,MobileDeviceService.identity(c,MobileDeviceService.credential(x)),b,x.getRequestHeaders().getFirst("User-Agent"));
                yield Map.of("saved",true);
            }
            case "account/profile" -> EmployeeAccountService.profile(c,s.userId);
            case "account/nickname" -> {
                if(!b.keySet().equals(Set.of("nickname"))||!b.get("nickname").isJsonPrimitive()||!b.getAsJsonPrimitive("nickname").isString())throw err(400,"INVALID_PROFILE","Only your nickname can be edited here.");
                EmployeeAccountService.saveNickname(c,s.userId,b.get("nickname").getAsString());
                audit(c,"EMPLOYEE_NICKNAME_CHANGED",s.userId,"Employee updated own nickname");
                yield EmployeeAccountService.profile(c,s.userId);
            }
            case "account/password" -> changeAccountPassword(x,c,s,b);
            case "payroll/dashboard","payroll/pay" -> PayrollWebService.execute(c,s.userId,s.name,browser,route,b);
            case "auth/drive-session","auth/drive-authorize" -> driveSession(c,s);
            case "customers/list","customers/details","customers/history","customers/transaction","customers/order-file","customers/payment-options","customers/record-adjustment","customers/save" -> customers(c,s,b,browser,route);
            case "minutes/bootstrap","minutes/history","minutes/create","minutes/reverse" -> PhoneMinutesWebService.execute(c,s.userId,s.name,browser,Objects.requireNonNull(data.DatabaseConfig.load().locationId(),"Store is not configured"),route.substring("minutes/".length()),b);
            case "inventory/search" -> InventoryWebService.search(c,s.userId,Objects.requireNonNull(data.DatabaseConfig.load().locationId(),"Store is not configured"),b);
            case "balance-sheet" -> balanceSheet(c,s,b,browser);
            case "orders/dashboard","orders/details","orders/assign","orders/lookup" -> OrdersWebService.execute(c,s.userId,s.name,s.permissions,browser,route.substring("orders/".length()),b);
            case "overseas" -> overseas(c,s,b,browser);
            case "bootstrap" -> Map.of("userId",s.userId,"name",s.name,"canBrowse",EmployeeWebHistoryService.canBrowse(s.permissions),
                    "canEdit",EmployeeWebCorrectionService.canEdit(s.permissions),"canOverseas",OverseasOrderService.canView(s.permissions),"overseasStores",overseasStores(c,s.userId));
            case "employees" -> EmployeeWebHistoryService.employees(c,s.userId,s.permissions,
                    search(b),b.has("includeInactive")&&b.get("includeInactive").getAsBoolean(),offset(b));
            case "history" -> EmployeeWebHistoryService.history(c,s.userId,b.has("userId")?integer(b,"userId"):s.userId,
                    s.permissions,optionalDate(b,"start"),optionalDate(b,"end"),offset(b));
            case "history/correct" -> {
                EmployeeWebCorrectionService.correct(c,s.userId,s.name,s.permissions,b);
                yield Map.of("corrected",true);
            }
            default -> logout(c,x,s);
        };
    }

    private Object customers(Connection c,Session s,JsonObject b,UUID browser,String route)throws Exception {
        Integer location=data.DatabaseConfig.load().locationId();
        if(location==null)throw new SecurityException("The active store is not configured.");
        try(var p=c.prepareStatement("SELECT 1 FROM user_locations WHERE user_id=? AND location_id=?")) {
            p.setInt(1,s.userId);p.setInt(2,location);try(var r=p.executeQuery()){if(!r.next())throw new SecurityException("This store is not assigned to your account.");}
        }
        return switch(route) {
            case "customers/list" -> Map.of("accounts",LanCustomerAccountService.list(c,s.userId),
                    "customerTypes",LanCatalogAdminService.customerTypes(c,"",true,s.userId));
            case "customers/details" -> LanCustomerAccountService.details(c,integer(b,"customerId"),s.userId);
            case "customers/history" -> CustomerAccountWebHistory.list(c,integer(b,"customerId"),s.userId,
                    location,optional(b,"query"),b.has("offset")?integer(b,"offset"):0);
            case "customers/transaction" -> CustomerAccountWebHistory.detail(c,integer(b,"customerId"),s.userId,
                    location,integer(b,"locationId"),required(b,"eventId",160),s.permissions);
            case "customers/order-file" -> CustomerAccountWebHistory.orderFile(c,integer(b,"customerId"),location,
                    required(b,"eventId",160),uuid(b,"fileId",false),integer(b,"offset"),s.permissions);
            case "customers/payment-options" -> Map.of(
                    "balances",LanCustomerAccountService.openBalances(c,integer(b,"customerId"),s.userId),
                    "drawers",PhoneMinutesWebService.drawers(c,s.userId,location));
            case "customers/record-adjustment" -> CustomerAccountWebAdjustment.record(c,s.userId,s.name,browser,location,b,uuid(b,"idempotencyKey",false));
            case "customers/save" -> {
                LanCustomerAccountService.require(c,s.userId,"CUSTOMER_ACCOUNTS");
                if(b.has("customerId")&&!b.get("customerId").isJsonNull()) {
                    int id=integer(b,"customerId");
                    try(var lock=c.prepareStatement("SELECT customer_id FROM customer_accounts WHERE customer_id=? FOR UPDATE")){lock.setInt(1,id);try(var r=lock.executeQuery()){if(!r.next())throw err(404,"CUSTOMER_NOT_FOUND","Customer account was not found.");}}
                    JsonElement current=GSON.toJsonTree(LanCustomerAccountService.details(c,id,s.userId));
                    if(!b.has("expectedAccount")||!current.equals(b.get("expectedAccount")))throw err(409,"CUSTOMER_CHANGED","This account changed. Close and reopen it before saving.");
                }
                var saved=LanCustomerAccountService.save(c,b,null,s.userId);
                audit(c,"CUSTOMER_WEB_ACCOUNT_SAVED",s.userId,"Browser "+browser+"; customer "+saved);
                yield saved;
            }
            default -> throw err(404,"NOT_FOUND","Customer route not found.");
        };
    }

    private Object balanceSheet(Connection c,Session s,JsonObject b,UUID browser)throws Exception {
        Integer location=data.DatabaseConfig.load().locationId();
        if(location==null)throw new SecurityException("The active store is not configured.");
        try(var p=c.prepareStatement("SELECT 1 FROM user_locations WHERE user_id=? AND location_id=?")) {
            p.setInt(1,s.userId);p.setInt(2,location);try(var r=p.executeQuery()){if(!r.next())throw new SecurityException("This store is not assigned to your account.");}
        }
        ServerRequestIdentity.bind(s.userId,location,"",s.name,browser.toString(),"Balance sheet web");
        try{b.addProperty("queueEmail",true);return BalanceSheetWorkspaceService.execute(c,BalanceSheetWorkspaceService.text(b,"action"),b);}
        finally{ServerRequestIdentity.clear();}
    }

    private List<Map<String,Object>> overseasStores(Connection c,int user)throws SQLException {
        List<Map<String,Object>> stores=new ArrayList<>();
        try(var p=c.prepareStatement("SELECT l.location_id,l.name FROM locations l JOIN user_locations ul USING(location_id) WHERE ul.user_id=? ORDER BY l.name")) {
            p.setInt(1,user);try(var r=p.executeQuery()){while(r.next())stores.add(Map.of("id",r.getInt(1),"name",r.getString(2)));}
        }return stores;
    }
    private Object overseas(Connection c,Session s,JsonObject b,UUID browser)throws Exception {
        if(ServerRoleGuard.blocksMutations())throw err(409,"SERVER_UNAVAILABLE",ServerRoleGuard.safeMessage());
        int location=b.has("locationId")?integer(b,"locationId"):0;
        if(location==0)try(var p=c.prepareStatement("SELECT location_id FROM user_locations WHERE user_id=? ORDER BY location_id LIMIT 2")) {
            p.setInt(1,s.userId);try(var r=p.executeQuery()) {if(!r.next())throw err(403,"ACCESS_DENIED","No store is assigned to your account.");location=r.getInt(1);if(r.next())throw err(400,"STORE_REQUIRED","Choose a store for overseas orders.");}
        }
        String locationName;try(var p=c.prepareStatement("SELECT l.name FROM locations l JOIN user_locations ul USING(location_id) WHERE ul.user_id=? AND l.location_id=?")) {
            p.setInt(1,s.userId);p.setInt(2,location);try(var r=p.executeQuery()){if(!r.next())throw err(403,"ACCESS_DENIED","This store is not assigned to you.");locationName=r.getString(1);}
        }
        ServerRequestIdentity.bind(s.userId,location,locationName,s.name,browser.toString(),"Employee web");
        try{return OverseasOrderService.execute(c,new OverseasOrderService.Actor(s.userId,s.name,location,browser.toString(),"Employee web",s.permissions,true),b);}
        catch(IllegalArgumentException e){throw err(409,"OVERSEAS_ORDER_REJECTED",e.getMessage());}
        catch(SQLException e){throw err(409,"OVERSEAS_ORDER_REJECTED",CustomerPhoneOwnership.isConflict(e)?CustomerPhoneOwnership.MESSAGE:"Order could not be saved. Check the quantities and customer details.");}
        finally{ServerRequestIdentity.clear();}
    }
    private Object activate(Connection c,HttpExchange x,JsonObject b)throws Exception {
        String token=required(b,"token",200),browserToken=LanSecurity.randomToken();
        try(PreparedStatement p=c.prepareStatement("""
                UPDATE employee_web_grants SET consumed_at=CURRENT_TIMESTAMP
                WHERE grant_hash=? AND consumed_at IS NULL AND expires_at>CURRENT_TIMESTAMP RETURNING device_name
                """)) {
            p.setString(1,LanSecurity.sha256(token));
            try(ResultSet r=p.executeQuery()) {
                if(!r.next()) throw err(403,"ACTIVATION_INVALID","This activation has expired or was already used. Ask for a new QR code.");
                try(PreparedStatement q=c.prepareStatement("INSERT INTO employee_web_devices(browser_hash,device_name) VALUES(?,?)")) {
                    q.setString(1,LanSecurity.sha256(browserToken));q.setString(2,r.getString(1));q.executeUpdate();
                }
            }
        }
        // Set only after commit so an unsuccessful redemption cannot replace an existing activation.
        c.commit();clearCookie(x,"ss_employee");cookie(x,"ss_employee_browser",browserToken,365*24*3600);
        return Map.of("activated",true);
    }

    private UUID requireBrowser(Connection c,HttpExchange x)throws Exception {
        String token=MobileDeviceService.credential(x);
        MobileDeviceService.requireAny(c,token,MobileDeviceService.APPS.stream().map(MobileDeviceService.App::key).toList());
        try(var p=c.prepareStatement("INSERT INTO employee_web_devices(browser_hash,device_name) VALUES(?,?) ON CONFLICT(browser_hash) DO UPDATE SET last_seen_at=CURRENT_TIMESTAMP RETURNING device_id,revoked_at")) {
            p.setString(1,LanSecurity.sha256(token));p.setString(2,"Shared mobile browser");
            try(var r=p.executeQuery()){r.next();if(r.getTimestamp(2)!=null)throw err(403,"DEVICE_REVOKED","This browser was revoked.");return (UUID)r.getObject(1);}
        }
    }

    private Object login(HttpExchange x,JsonObject b,UUID browser,boolean drive)throws Exception {
        String identifier=required(b,"identifier",512),password=password(b),key=LanSecurity.sha256(identifier.toLowerCase(Locale.ROOT)),address=remote(x);
        reserveAttempt(key,address);
        String email=identifier.toLowerCase(Locale.ROOT);
        if(!identifier.contains("@"))try(Connection c=DB.getConnection();PreparedStatement p=c.prepareStatement(
                "SELECT email FROM users WHERE lower(username)=lower(?) AND is_active=TRUE ORDER BY user_id LIMIT 2")) {
            p.setString(1,identifier);try(ResultSet r=p.executeQuery()) {
                if(!r.next()) throw err(401,"AUTH_FAILED","The sign-in was not accepted.");email=r.getString(1);
                if(r.next()) throw err(401,"AUTH_FAILED","The sign-in was not accepted.");
            }
        }
        JsonObject q=new JsonObject();q.addProperty("email",email);q.addProperty("password",password);
        JsonObject token=supabase(project,"/auth/v1/token?grant_type=password",null,q);
        String access=str(token,"access_token");
        JsonObject user=supabase(project,"/auth/v1/user",access,null);
        UUID auth=UUID.fromString(str(user,"id"));
        try(Connection c=DB.getConnection()){activeUser(c,auth,drive);}
        String factor=null;
        if(user.has("factors"))for(JsonElement e:user.getAsJsonArray("factors")) {
            JsonObject f=e.getAsJsonObject();
            if("totp".equals(str(f,"factor_type"))&&"verified".equals(str(f,"status"))) {factor=str(f,"id");break;}
        }
        // Honor an enrolled authenticator even when mandatory enrollment is disabled.
        if(!mfaRequired&&factor==null || "aal2".equals(jwt(access,"aal"))) return createSession(x,auth,access,browser,key,address,drive);
        String qr=null,secret=null;
        if(factor==null) {
            JsonObject enroll=new JsonObject();enroll.addProperty("factor_type","totp");enroll.addProperty("friendly_name","SmartStock Employee Web");
            JsonObject enrolled=supabase(project,"/auth/v1/factors",access,enroll);
            factor=str(enrolled,"id");JsonObject totp=enrolled.getAsJsonObject("totp");
            qr=qrData(str(totp,"qr_code"));secret=str(totp,"secret");
        }
        JsonObject challenge=supabase(project,"/auth/v1/factors/"+url(factor)+"/challenge",access,new JsonObject());
        if(preauths.size()>=256) throw err(503,"AUTH_BUSY","Please try again shortly.");
        String handle=LanSecurity.randomToken();
        preauths.put(LanSecurity.sha256(handle),new Preauth(access,auth,factor,str(challenge,"id"),Instant.now().plusSeconds(600),key,address,browser,drive));
        Map<String,Object> out=new LinkedHashMap<>();out.put("preauth",handle);out.put("mfaRequired",true);
        if(qr!=null){out.put("qrCode",qr);out.put("secret",secret);}
        return out;
    }

    private Object verify(HttpExchange x,JsonObject b,UUID browser)throws Exception {
        Preauth p=preauths.remove(LanSecurity.sha256(required(b,"preauth",200)));
        if(p==null||!p.expires.isAfter(Instant.now())||!p.browser.equals(browser)) throw err(401,"PREAUTH_EXPIRED","Start sign-in again.");
        reserveAttempt(p.identifierHash,p.address);
        JsonObject q=new JsonObject();q.addProperty("challenge_id",p.challengeId);q.addProperty("code",required(b,"code",12));
        JsonObject verified=supabase(project,"/auth/v1/factors/"+url(p.factorId)+"/verify",p.accessToken,q);
        String access=str(verified,"access_token");
        requireAssurance(true,jwt(access,"aal"));
        return createSession(x,p.authUserId,access,browser,p.identifierHash,p.address,p.drive);
    }

    private Object createSession(HttpExchange x,UUID auth,String access,UUID browser,String key,String address,boolean drive)throws Exception {
        requireAssurance(mfaRequired,jwt(access,"aal"));
        JsonObject remote=supabase(project,"/auth/v1/user",access,null);
        if(!auth.toString().equals(str(remote,"id"))) throw err(401,"AUTH_INVALID","The online session was not accepted.");
        boolean rememberDevice;
        String token=LanSecurity.randomToken(),csrf=LanSecurity.randomToken();
        try(Connection c=DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                requireAvailable(c);
                if(!browser.equals(requireBrowser(c,x))) throw err(403,"DEVICE_REVOKED","Activate this browser again.");
                rememberDevice=MobileDeviceService.rememberLogin(c,MobileDeviceService.credential(x));
                User u=activeUser(c,auth,drive);
                try(PreparedStatement p=c.prepareStatement("UPDATE employee_web_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE session_hash=?")) {
                    p.setString(1,LanSecurity.sha256(Objects.toString(cookie(x,"ss_employee"),"")));p.executeUpdate();
                }
                try(PreparedStatement p=c.prepareStatement("""
                        INSERT INTO employee_web_sessions(session_hash,csrf_hash,browser_device_id,user_id,auth_user_id,expires_at,absolute_expires_at,remember_device)
                        VALUES(?,?,?,?,?,CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),?)
                        """)) {
                    p.setString(1,LanSecurity.sha256(token));p.setString(2,LanSecurity.sha256(csrf));p.setObject(3,browser);p.setInt(4,u.id);p.setObject(5,auth);p.setInt(6,rememberDevice?REMEMBER_SECONDS:900);p.setInt(7,rememberDevice?REMEMBER_SECONDS:28800);p.setBoolean(8,rememberDevice);p.executeUpdate();
                }
                recordAttempt(c,key,address,true);audit(c,"EMPLOYEE_WEB_LOGIN",u.id,"Employee web login; browser="+browser);c.commit();
            }catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
        }
        sessionCookie(x,token,rememberDevice);return Map.of("csrfToken",csrf,"rememberDevice",rememberDevice);
    }

    private Session requireSession(Connection c,HttpExchange x,UUID browser,boolean csrf,boolean drive)throws Exception {
        String token=cookie(x,"ss_employee");
        if(token==null) throw err(401,"SESSION_REQUIRED","Sign in to continue.");
        try(PreparedStatement p=c.prepareStatement("""
                SELECT session_id,auth_user_id,csrf_hash,expires_at,absolute_expires_at,user_id,remember_device
                FROM employee_web_sessions WHERE session_hash=? AND browser_device_id=? AND revoked_at IS NULL FOR UPDATE
                """)) {
            p.setString(1,LanSecurity.sha256(token));p.setObject(2,browser);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next()||!sessionAlive(r.getTimestamp(4).toInstant(),r.getTimestamp(5).toInstant(),Instant.now()))
                    throw err(401,"SESSION_EXPIRED","Sign in again.");
                if(csrf) validateCsrf(x.getRequestHeaders().getFirst("X-CSRF-Token"),r.getString(3));
                User u=activeUser(c,(UUID)r.getObject(2),drive);
                if(u.id!=r.getInt(6)) throw err(401,"SESSION_EXPIRED","Sign in again.");
                UUID id=(UUID)r.getObject(1);
                boolean remembered=r.getBoolean(7);
                if(remembered!=MobileDeviceService.rememberLogin(c,MobileDeviceService.credential(x)))throw err(401,"SESSION_EXPIRED","Your manager changed this device’s login policy. Sign in again.");
                try(PreparedStatement q=c.prepareStatement(remembered
                        ? "UPDATE employee_web_sessions SET expires_at=CURRENT_TIMESTAMP+INTERVAL '400 days',absolute_expires_at=CURRENT_TIMESTAMP+INTERVAL '400 days',last_seen_at=CURRENT_TIMESTAMP WHERE session_id=?"
                        : "UPDATE employee_web_sessions SET expires_at=LEAST(absolute_expires_at,CURRENT_TIMESTAMP+INTERVAL '15 minutes'),last_seen_at=CURRENT_TIMESTAMP WHERE session_id=?")) {
                    q.setObject(1,id);q.executeUpdate();
                }
                try(PreparedStatement q=c.prepareStatement("UPDATE employee_web_devices SET last_seen_at=CURRENT_TIMESTAMP WHERE device_id=?")) {q.setObject(1,browser);q.executeUpdate();}
                if(remembered)sessionCookie(x,token,true);
                return new Session(id,u.id,u.name,permissions(c,u.id));
            }
        }
    }

    static String accountPassword(JsonObject body,String key)throws WebError {
        if(!body.has(key)||!body.get(key).isJsonPrimitive()||!body.getAsJsonPrimitive(key).isString())throw err(400,"PASSWORD_POLICY","Password is required.");
        String value=body.get(key).getAsString();
        if(value.isEmpty()||value.length()>1024)throw err(400,"PASSWORD_POLICY","Use a password of up to 1024 characters.");
        return value;
    }

    private Object changeAccountPassword(HttpExchange x,Connection c,Session s,JsonObject b)throws Exception {
        String current=accountPassword(b,"currentPassword"),next=accountPassword(b,"newPassword");
        if(next.length()<8||next.equals(current))throw err(400,"PASSWORD_POLICY","Use a different password with at least 8 characters.");
        String email,authId;
        try(var p=c.prepareStatement("SELECT email,auth_user_id FROM users WHERE user_id=? AND is_active=TRUE")) {
            p.setInt(1,s.userId);try(var r=p.executeQuery()) {
                if(!r.next())throw err(403,"ACCOUNT_DENIED","Your account is unavailable.");
                email=r.getString(1);authId=r.getString(2);
            }
        }
        String key=LanSecurity.sha256("password-change:"+s.userId),address=remote(x);
        reserveAttempt(key,address);
        updateAccountPassword(project,http,email,authId,current,next,optional(b,"code"),mfaRequired);
        try(var p=c.prepareStatement("UPDATE users SET password_hash=NULL,password_cache_invalidated_at=clock_timestamp(),updated_at=CURRENT_TIMESTAMP WHERE user_id=?")){p.setInt(1,s.userId);p.executeUpdate();}
        try(var p=c.prepareStatement("UPDATE employee_web_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=? AND session_id<>? AND revoked_at IS NULL")){p.setInt(1,s.userId);p.setObject(2,s.id);p.executeUpdate();}
        audit(c,"EMPLOYEE_PASSWORD_CHANGED",s.userId,"Employee changed own password; other workspace sessions revoked");
        recordAttempt(c,key,address,true);
        return Map.of("changed",true);
    }

    static void updateAccountPassword(SupabaseProjectConfig project,HttpClient http,String email,String authId,String current,String next,String code,boolean mfaRequired)throws Exception {
        JsonObject credentials=new JsonObject();credentials.addProperty("email",email);credentials.addProperty("password",current);
        JsonObject token=accountAuthRequest(project,http,"/auth/v1/token?grant_type=password",null,credentials);
        String access=str(token,"access_token");
        JsonObject user=accountAuthRequest(project,http,"/auth/v1/user",access,null);
        if(!Objects.equals(authId,str(user,"id")))throw err(403,"ACCOUNT_DENIED","The sign-in did not match your account.");
        String factor=null;
        if(user.has("factors"))for(JsonElement element:user.getAsJsonArray("factors")) {
            JsonObject f=element.getAsJsonObject();
            if("totp".equals(str(f,"factor_type"))&&"verified".equals(str(f,"status"))){factor=str(f,"id");break;}
        }
        if(mfaRequired&&factor==null)throw err(403,"MFA_REQUIRED","Set up your authenticator before changing your password.");
        if(factor!=null) {

            if(code==null||!code.matches("[0-9]{6}"))throw err(400,"MFA_REQUIRED","Enter your current authenticator code to change your password.");
            JsonObject challenge=accountAuthRequest(project,http,"/auth/v1/factors/"+url(factor)+"/challenge",access,new JsonObject());
            JsonObject verify=new JsonObject();verify.addProperty("challenge_id",str(challenge,"id"));verify.addProperty("code",code);
            access=str(accountAuthRequest(project,http,"/auth/v1/factors/"+url(factor)+"/verify",access,verify),"access_token");
            requireAssurance(true,jwt(access,"aal"));
        }
        JsonObject payload=new JsonObject();payload.addProperty("password",next);payload.addProperty("current_password",current);
        HttpRequest request=HttpRequest.newBuilder(URI.create(project.url()+"/auth/v1/user"))
                .timeout(Duration.ofSeconds(20)).header("apikey",project.publishableKey()).header("Authorization","Bearer "+access)
                .header("Content-Type","application/json").PUT(HttpRequest.BodyPublishers.ofString(payload.toString())).build();
        HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if(response.statusCode()<200||response.statusCode()>=300)
            throw err(400,"PASSWORD_REJECTED","Password could not be changed. Check your current password and authenticator code, and use a stronger new password.");
    }

    private static JsonObject accountAuthRequest(SupabaseProjectConfig config,HttpClient client,String path,String bearer,JsonObject payload)throws Exception {
        HttpRequest.Builder request=HttpRequest.newBuilder(URI.create(config.url()+path)).timeout(Duration.ofSeconds(20)).header("apikey",config.publishableKey());
        if(bearer!=null)request.header("Authorization","Bearer "+bearer);
        if(payload==null)request.GET();else request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(payload.toString()));
        HttpResponse<String> response=client.send(request.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if(response.statusCode()<200||response.statusCode()>=300)throw err(400,"ACCOUNT_AUTH_FAILED","Check your current password and authenticator code, then try again.");
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private Object renewCsrf(Connection c,Session s)throws Exception {
        String csrf=LanSecurity.randomToken();
        try(PreparedStatement p=c.prepareStatement("UPDATE employee_web_sessions SET csrf_hash=? WHERE session_id=?")) {p.setString(1,LanSecurity.sha256(csrf));p.setObject(2,s.id);p.executeUpdate();}
        boolean remembered;
        try(PreparedStatement p=c.prepareStatement("SELECT remember_device FROM employee_web_sessions WHERE session_id=?")){p.setObject(1,s.id);try(ResultSet r=p.executeQuery()){r.next();remembered=r.getBoolean(1);}}
        return Map.of("rememberDevice",remembered,"csrfToken",csrf,"permissions",s.permissions,"userId",s.userId,"locationId",Objects.requireNonNull(data.DatabaseConfig.load().locationId(),"Store is not configured"));
    }

    private static Object driveSession(Connection c,Session session)throws SQLException {
        List<String> storeIds=new ArrayList<>();
        try(PreparedStatement statement=c.prepareStatement(
                "SELECT location_id FROM user_locations WHERE user_id=? ORDER BY location_id")) {
            statement.setInt(1,session.userId);
            try(ResultSet rows=statement.executeQuery()) {
                while(rows.next())storeIds.add(Integer.toString(rows.getInt(1)));
            }
        }
        return Map.of("userId",session.userId,"name",session.name,"storeIds",storeIds,
                "permissions",session.permissions);
    }
    private Object logout(Connection c,HttpExchange x,Session s)throws Exception {
        try(PreparedStatement p=c.prepareStatement("UPDATE employee_web_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE session_id=?")){p.setObject(1,s.id);p.executeUpdate();}
        audit(c,"EMPLOYEE_WEB_LOGOUT",s.userId,"Employee browser session revoked");
        c.commit();clearCookie(x,"ss_employee");return Map.of();
    }
    private User activeUser(Connection c,UUID auth,boolean drive)throws Exception {
        try(PreparedStatement p=c.prepareStatement("SELECT user_id,COALESCE(NULLIF(full_name,''),username) FROM users WHERE auth_user_id=? AND is_active=TRUE")) {
            p.setObject(1,auth);try(ResultSet r=p.executeQuery()) {
                if(!r.next()) throw err(403,"ACCOUNT_DENIED","No active SmartStock account matches this login.");
                int id=r.getInt(1);
                if(!canAuthenticate(permissions(c,id),drive)) throw err(403,"ACCESS_DENIED","Your role does not have access to an enabled web app.");
                return new User(id,r.getString(2));
            }
        }
    }
    static boolean canAuthenticate(Set<String> permissions) {
        return MobileDeviceService.APPS.stream().anyMatch(app->DeckersWorkspace.canOpen(app.key(),permissions));
    }
    static boolean canAuthenticate(Set<String> permissions,boolean drive) {
        return drive||canAuthenticate(permissions);
    }
    /** Browser approval and login are shared; each app keeps its own authorization. */
    static void requireRouteAccess(String route,Set<String> permissions) {
        if(Set.of("auth/session","auth/drive-session","auth/drive-authorize","auth/logout","account/profile","account/nickname","account/password").contains(route))return;
        String permission=route.startsWith("payroll/")?"PAYROLL_DASHBOARD":(route.startsWith("customers/")||route.startsWith("minutes/"))?"CUSTOMER_ACCOUNTS":route.equals("inventory/search")?"VIEW_INVENTORY":route.equals("balance-sheet")?"BALANCE_SHEET":"ACCESS_EMPLOYEE_WEB";
        if(!permissions.contains(permission))throw new SecurityException("Your role does not have "+(permission.equals("PAYROLL_DASHBOARD")?"Payroll Dashboard":permission.equals("CUSTOMER_ACCOUNTS")?"Customer Accounts":permission.equals("VIEW_INVENTORY")?"View Inventory":permission.equals("BALANCE_SHEET")?"Balance Sheet":"Access Employee Web App")+" permission.");
    }
    private void requireAvailable(Connection c)throws Exception {
        if(ServerRoleGuard.state()!=ServerRoleGuard.State.PRIMARY) throw err(503,"SERVER_INACTIVE","The active store server is unavailable.");
        try(PreparedStatement p=c.prepareStatement("SELECT generation FROM employee_web_runtime WHERE runtime_id=1 AND enabled=TRUE FOR SHARE");ResultSet r=p.executeQuery()) {
            if(!r.next()||!generation.equals(r.getObject(1))) throw err(503,"APP_STOPPED","The employee app was stopped or restarted. Sign in again.");
        }
    }
    private static String search(JsonObject b) { String s=optional(b,"search"); if(s==null)return ""; if(s.length()>200)throw new IllegalArgumentException("Search is too long."); return s.trim(); }
    private static int offset(JsonObject b) {
        int value=b.has("offset")?b.get("offset").getAsInt():0;
        if(value<0||value>1000000) throw new IllegalArgumentException("Invalid offset.");return value;
    }
    private static LocalDate optionalDate(JsonObject b,String key) {String v=optional(b,key);return v==null||v.isBlank()?null:LocalDate.parse(v);}
    @Override public void close(){server.stop(1);executor.shutdownNow();preauths.clear();}
    private record Preauth(String accessToken,UUID authUserId,String factorId,String challengeId,Instant expires,String identifierHash,String address,UUID browser,boolean drive) { }
    private record User(int id,String name) { }
    private record Session(UUID id,int userId,String name,Set<String> permissions) { }
    // Shared protocol conventions with the scheduler gateway; tokens stay server-side.
    private JsonObject supabase(SupabaseProjectConfig cfg,String path,String bearer,JsonObject payload)throws Exception{HttpRequest.Builder req=HttpRequest.newBuilder(URI.create(cfg.url()+path)).timeout(Duration.ofSeconds(20)).header("apikey",cfg.publishableKey());if(bearer!=null)req.header("Authorization","Bearer "+bearer);if(payload==null)req.GET();else req.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)));HttpResponse<String>res=http.send(req.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));if(res.statusCode()<200||res.statusCode()>=300)throw err(401,"AUTH_FAILED","The sign-in or authenticator code was not accepted.");return JsonParser.parseString(res.body()).getAsJsonObject();}
    private Set<String> permissions(Connection c,int id)throws SQLException{Set<String>out=new HashSet<>();try(PreparedStatement p=c.prepareStatement("SELECT UPPER(p.permission_key) FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions p ON p.permission_id=rp.permission_id WHERE u.user_id=? UNION SELECT UPPER(rmp.permission_key) FROM users u JOIN role_mobile_permissions rmp ON rmp.role_id=u.role_id WHERE u.user_id=?")){p.setInt(1,id);p.setInt(2,id);try(ResultSet r=p.executeQuery()){while(r.next())out.add(r.getString(1));}}return Set.copyOf(out);}
    private void rateLimit(Connection c,String key,String address)throws Exception{try(PreparedStatement p=c.prepareStatement("SELECT count(*) FROM employee_web_auth_attempts WHERE succeeded=FALSE AND attempted_at>CURRENT_TIMESTAMP-INTERVAL '15 minutes' AND (identifier_hash=? OR remote_address=?)")){p.setString(1,key);p.setString(2,address);try(ResultSet r=p.executeQuery()){r.next();if(r.getInt(1)>=8)throw err(429,"LOGIN_THROTTLED","Too many sign-in attempts. Wait 15 minutes.");}}}
    private void recordAttempt(Connection c,String key,String address,boolean ok)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT INTO employee_web_auth_attempts(identifier_hash,remote_address,succeeded) VALUES(?,?,?)")){p.setString(1,key);p.setString(2,address);p.setBoolean(3,ok);p.executeUpdate();}}
    private void audit(Connection c,String type,int user,String details)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT INTO security_audit_events(event_type,actor_user_id,details) VALUES(?,?,?)")){p.setString(1,type);p.setInt(2,user);p.setString(3,details);p.executeUpdate();}}
    static boolean workspaceOriginAllowed(String configured,String origin){return origin!=null&&(configured.equalsIgnoreCase(origin)||DeckersWorkspace.ORIGIN.equalsIgnoreCase(origin)||DeckersWorkspace.managedOrigin(origin,data.DatabaseConfig.load().locationId()));}
    private void sameOrigin(HttpExchange x)throws WebError{String origin=x.getRequestHeaders().getFirst("Origin");if(!workspaceOriginAllowed(publicOrigin,origin))throw err(403,"ORIGIN_DENIED","This web origin is not allowed.");}
    static String cleanOrigin(String v){if(v==null||v.isBlank())return null;try{URI u=URI.create(v.trim());if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getRawQuery()!=null||u.getRawFragment()!=null||u.getRawUserInfo()!=null||(u.getRawPath()!=null&&!u.getRawPath().isEmpty()&&!u.getRawPath().equals("/")))return null;return u.getPort()<0?"https://"+u.getHost():"https://"+u.getHost()+":"+u.getPort();}catch(Exception e){return null;}}
    private static String remote(HttpExchange x){String f=x.getRequestHeaders().getFirst("CF-Connecting-IP");return f!=null&&f.length()<=45&&f.matches("[0-9a-fA-F:.]+")?f:x.getRemoteAddress().getAddress().getHostAddress();}
    private static JsonObject body(HttpExchange x)throws Exception{int limit=x.getRequestURI().getPath().endsWith("/overseas")?3*1024*1024:1024*1024;byte[]b=x.getRequestBody().readNBytes(limit+1);if(b.length>limit)throw err(413,"REQUEST_TOO_LARGE","Request too large.");try{return b.length==0?new JsonObject():JsonParser.parseString(new String(b,StandardCharsets.UTF_8)).getAsJsonObject();}catch(RuntimeException e){throw err(400,"VALIDATION_ERROR","A JSON object is required.");}}
    private static String jwt(String token,String key){try{String[]p=token.split("\\.");JsonObject j=JsonParser.parseString(new String(Base64.getUrlDecoder().decode(p[1]),StandardCharsets.UTF_8)).getAsJsonObject();return str(j,key);}catch(Exception e){return null;}}
    private static String required(JsonObject b,String k,int max)throws WebError{String v=optional(b,k);if(v==null||v.isBlank()||v.length()>max)throw err(400,"VALIDATION_ERROR",k+" is required.");return v.trim();}private static String optional(JsonObject b,String k){return b.has(k)&&!b.get(k).isJsonNull()?b.get(k).getAsString():null;}private static String str(JsonObject b,String k){return b!=null&&b.has(k)&&!b.get(k).isJsonNull()?b.get(k).getAsString():null;}private static int integer(JsonObject b,String k)throws WebError{try{return b.get(k).getAsInt();}catch(Exception e){throw err(400,"VALIDATION_ERROR",k+" is invalid.");}}private static LocalDate date(JsonObject b,String k)throws WebError{try{return LocalDate.parse(required(b,k,40));}catch(Exception e){throw err(400,"VALIDATION_ERROR",k+" is invalid.");}}private static LocalTime time(JsonObject b,String k)throws WebError{try{return LocalTime.parse(required(b,k,20));}catch(Exception e){throw err(400,"VALIDATION_ERROR",k+" is invalid.");}}private static UUID uuid(JsonObject b,String k,boolean nullable)throws WebError{String v=optional(b,k);if(nullable&&(v==null||v.isBlank()))return null;try{return UUID.fromString(v);}catch(Exception e){throw err(400,"VALIDATION_ERROR",k+" is invalid.");}}private static String url(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8);}
    private static final int REMEMBER_SECONDS=400*24*3600;
    static String sessionCookieValue(String token,boolean remembered){return "ss_employee="+token+"; Path=/employees"+(remembered?"; Max-Age="+REMEMBER_SECONDS:"")+"; Secure; HttpOnly; SameSite=Strict";}
    private static void sessionCookie(HttpExchange x,String token,boolean remembered){x.getResponseHeaders().add("Set-Cookie",sessionCookieValue(token,remembered));}
    private static void cookie(HttpExchange x,String name,String value,int age){x.getResponseHeaders().add("Set-Cookie",name+"="+value+"; Path=/employees; Max-Age="+age+"; Secure; HttpOnly; SameSite=Strict");}private static void clearCookie(HttpExchange x,String name){cookie(x,name,"",0);}private static String cookie(HttpExchange x,String name){String h=x.getRequestHeaders().getFirst("Cookie");if(h!=null)for(String p:h.split(";")){String[]kv=p.trim().split("=",2);if(kv.length==2&&name.equals(kv[0]))return kv[1];}return null;}
    private static void headers(HttpExchange x){Headers h=x.getResponseHeaders();h.set("Cache-Control","no-store");h.set("X-Content-Type-Options","nosniff");h.set("X-Frame-Options","DENY");h.set("Referrer-Policy","no-referrer");h.set("Permissions-Policy","camera=(self), microphone=(), geolocation=()");h.set("Content-Security-Policy","default-src 'self'; connect-src 'self'; img-src 'self' data: blob:; media-src 'self' blob:; style-src 'self'; script-src 'self'; manifest-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");}
    private static void json(HttpExchange x,int status,Object v){try{byte[]b=GSON.toJson(v).getBytes(StandardCharsets.UTF_8);headers(x);x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");x.sendResponseHeaders(status,b.length);x.getResponseBody().write(b);}catch(Exception ignored){}}private static void text(HttpExchange x,int status,String type,String v)throws IOException{byte[]b=v.getBytes(StandardCharsets.UTF_8);headers(x);x.getResponseHeaders().set("Content-Type",type);x.sendResponseHeaders(status,b.length);x.getResponseBody().write(b);}
    static boolean sessionAlive(Instant idle,Instant absolute,Instant now){return idle.isAfter(now)&&absolute.isAfter(now);}
    static void validateCsrf(String supplied,String stored)throws WebError{
        if(supplied==null||stored==null||!LanSecurity.constantTimeEquals(LanSecurity.sha256(supplied),stored))throw err(403,"CSRF_INVALID","Refresh the page and try again.");
    }
    static void requireAssurance(boolean mfaRequired,String aal)throws WebError{
        if(mfaRequired){requireAal2(aal);return;}
        if(!"aal1".equals(aal)&&!"aal2".equals(aal))throw err(401,"AUTH_INVALID","The online session was not accepted.");
    }
    static void requireAal2(String aal)throws WebError{
        if(!"aal2".equals(aal))throw err(401,"MFA_REQUIRED","Authenticator verification is required.");
    }
    private void reserveAttempt(String key,String address)throws Exception{
        try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{
            try(PreparedStatement p=c.prepareStatement("SELECT pg_advisory_xact_lock(734982167)")){p.execute();}
            rateLimit(c,key,address);recordAttempt(c,key,address,false);c.commit();
        }catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}}
    }
    static String password(JsonObject b)throws WebError{
        String value=optional(b,"password");if(value==null||value.isEmpty()||value.length()>4096)throw err(400,"VALIDATION_ERROR","Password is required.");return value;
    }
    static String qrData(String qr){
        if(qr==null||qr.startsWith("data:image/"))return qr;
        return "data:image/svg+xml;base64,"+Base64.getEncoder().encodeToString(qr.getBytes(StandardCharsets.UTF_8));
    }

    private static WebError err(int status,String code,String message){return new WebError(status,code,message);}
    static final class WebError extends Exception { final int status; final String code; WebError(int status,String code,String message){super(message);this.status=status;this.code=code;} }
}
