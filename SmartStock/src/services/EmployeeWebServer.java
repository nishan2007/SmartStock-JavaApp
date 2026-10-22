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
    private final boolean mfaRequired=Boolean.parseBoolean(System.getenv().getOrDefault("SMARTSTOCK_EMPLOYEE_REQUIRE_MFA",
            System.getenv().getOrDefault("SMARTSTOCK_SCHEDULER_REQUIRE_MFA","false")));
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
            String path=x.getRequestURI().getPath();
            if(!path.equals("/employees")&&!path.startsWith("/employees/")) throw err(404,"NOT_FOUND","Not found.");
            String route=path.substring("/employees".length());
            if(route.isEmpty()) {
                headers(x);x.getResponseHeaders().set("Location","/employees/");x.sendResponseHeaders(308,-1);return;
            }
            if(route.startsWith("/api/v1/")) api(x,route.substring(8)); else asset(x,route);
        } catch(WebError e) {json(x,e.status,Map.of("ok",false,"error",Map.of("code",e.code,"message",e.getMessage())));}
        catch(SecurityException e) {json(x,403,Map.of("ok",false,"error",Map.of("code","ACCESS_DENIED","message",e.getMessage())));}
        catch(IllegalArgumentException e) {json(x,400,Map.of("ok",false,"error",Map.of("code","INVALID_REQUEST","message","Check the employee, dates and request values.")));}
        catch(Exception e) {json(x,503,Map.of("ok",false,"error",Map.of("code","UNAVAILABLE","message","Employee app is unavailable. Please try again.")));}
        finally {x.close();}
    }

    private void asset(HttpExchange x,String route)throws Exception {
        if(!"GET".equals(x.getRequestMethod())) throw err(405,"METHOD_NOT_ALLOWED","GET is required.");
        if("/health".equals(route)) {
            try(Connection c=DB.getConnection()){requireAvailable(c);}
            json(x,200,Map.of("ok",true));return;
        }
        String file=switch(route) {case "","/","/index.html"->"index.html";case "/app.js"->"app.js";case "/app.css"->"app.css";case "/overseas.js"->"overseas.js";default->null;};
        if(file==null) throw err(404,"NOT_FOUND","Not found.");
        try(InputStream in=getClass().getClassLoader().getResourceAsStream("employee-web/"+file)) {
            if(in==null) throw err(404,"NOT_FOUND","Web asset missing.");
            String type=file.endsWith(".js")?"application/javascript":file.endsWith(".css")?"text/css":"text/html";
            text(x,200,type+"; charset=utf-8",new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
    }

    private void api(HttpExchange x,String route)throws Exception {
        if(!"POST".equals(x.getRequestMethod())) throw err(405,"METHOD_NOT_ALLOWED","POST is required.");
        sameOrigin(x);
        preauths.entrySet().removeIf(e->!e.getValue().expires.isAfter(Instant.now()));
        JsonObject b=body(x);
        if(route.equals("auth/login")||route.equals("auth/verify")) {
            UUID browser;
            // Do not hold a pooled database connection during online authentication.
            try(Connection c=DB.getConnection()) {
                c.setAutoCommit(false);
                try {requireAvailable(c);browser=requireBrowser(c,x);c.commit();}
                catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
            }
            Object result=route.equals("auth/login")?login(x,b,browser):verify(x,b,browser);
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
                        case "auth/session","bootstrap","employees","history","history/correct","auth/logout","overseas" -> {
                            Session s=requireSession(c,x,browser,!route.equals("auth/session"));
                            result=switch(route) {
                                case "auth/session" -> renewCsrf(c,s);
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
                        default -> throw err(404,"NOT_FOUND","API route not found.");
                    }
                }
                c.commit();
            } catch(Exception e) {c.rollback();throw e;} finally {c.setAutoCommit(true);}
        }
        json(x,200,Map.of("ok",true,"data",result));
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
        String token=cookie(x,"ss_employee_browser");
        if(token==null) throw err(403,"DEVICE_REQUIRED","Scan an administrator's activation QR on this browser first.");
        try(PreparedStatement p=c.prepareStatement("SELECT device_id FROM employee_web_devices WHERE browser_hash=? AND revoked_at IS NULL FOR UPDATE")) {
            p.setString(1,LanSecurity.sha256(token));try(ResultSet r=p.executeQuery()) {
                if(!r.next()) throw err(403,"DEVICE_REVOKED","This browser is no longer activated. Ask an administrator for a new QR.");
                return (UUID)r.getObject(1);
            }
        }
    }

    private Object login(HttpExchange x,JsonObject b,UUID browser)throws Exception {
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
        try(Connection c=DB.getConnection()){activeUser(c,auth);}
        String factor=null;
        if(user.has("factors"))for(JsonElement e:user.getAsJsonArray("factors")) {
            JsonObject f=e.getAsJsonObject();
            if("totp".equals(str(f,"factor_type"))&&"verified".equals(str(f,"status"))) {factor=str(f,"id");break;}
        }
        // Honor an enrolled authenticator even when mandatory enrollment is disabled.
        if(!mfaRequired&&factor==null || "aal2".equals(jwt(access,"aal"))) return createSession(x,auth,access,browser,key,address);
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
        preauths.put(LanSecurity.sha256(handle),new Preauth(access,auth,factor,str(challenge,"id"),Instant.now().plusSeconds(600),key,address,browser));
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
        return createSession(x,p.authUserId,access,browser,p.identifierHash,p.address);
    }

    private Object createSession(HttpExchange x,UUID auth,String access,UUID browser,String key,String address)throws Exception {
        requireAssurance(mfaRequired,jwt(access,"aal"));
        JsonObject remote=supabase(project,"/auth/v1/user",access,null);
        if(!auth.toString().equals(str(remote,"id"))) throw err(401,"AUTH_INVALID","The online session was not accepted.");
        String token=LanSecurity.randomToken(),csrf=LanSecurity.randomToken();
        try(Connection c=DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                requireAvailable(c);
                if(!browser.equals(requireBrowser(c,x))) throw err(403,"DEVICE_REVOKED","Activate this browser again.");
                User u=activeUser(c,auth);
                try(PreparedStatement p=c.prepareStatement("UPDATE employee_web_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE session_hash=?")) {
                    p.setString(1,LanSecurity.sha256(Objects.toString(cookie(x,"ss_employee"),"")));p.executeUpdate();
                }
                try(PreparedStatement p=c.prepareStatement("""
                        INSERT INTO employee_web_sessions(session_hash,csrf_hash,browser_device_id,user_id,auth_user_id,expires_at,absolute_expires_at)
                        VALUES(?,?,?,?,?,CURRENT_TIMESTAMP+INTERVAL '15 minutes',CURRENT_TIMESTAMP+INTERVAL '8 hours')
                        """)) {
                    p.setString(1,LanSecurity.sha256(token));p.setString(2,LanSecurity.sha256(csrf));p.setObject(3,browser);p.setInt(4,u.id);p.setObject(5,auth);p.executeUpdate();
                }
                recordAttempt(c,key,address,true);audit(c,"EMPLOYEE_WEB_LOGIN",u.id,"Employee web login; browser="+browser);c.commit();
            }catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
        }
        cookie(x,"ss_employee",token,8*3600);return Map.of("csrfToken",csrf);
    }

    private Session requireSession(Connection c,HttpExchange x,UUID browser,boolean csrf)throws Exception {
        String token=cookie(x,"ss_employee");
        if(token==null) throw err(401,"SESSION_REQUIRED","Sign in to continue.");
        try(PreparedStatement p=c.prepareStatement("""
                SELECT session_id,auth_user_id,csrf_hash,expires_at,absolute_expires_at,user_id
                FROM employee_web_sessions WHERE session_hash=? AND browser_device_id=? AND revoked_at IS NULL FOR UPDATE
                """)) {
            p.setString(1,LanSecurity.sha256(token));p.setObject(2,browser);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next()||!sessionAlive(r.getTimestamp(4).toInstant(),r.getTimestamp(5).toInstant(),Instant.now()))
                    throw err(401,"SESSION_EXPIRED","Sign in again.");
                if(csrf) validateCsrf(x.getRequestHeaders().getFirst("X-CSRF-Token"),r.getString(3));
                User u=activeUser(c,(UUID)r.getObject(2));
                if(u.id!=r.getInt(6)) throw err(401,"SESSION_EXPIRED","Sign in again.");
                UUID id=(UUID)r.getObject(1);
                try(PreparedStatement q=c.prepareStatement("UPDATE employee_web_sessions SET expires_at=LEAST(absolute_expires_at,CURRENT_TIMESTAMP+INTERVAL '15 minutes'),last_seen_at=CURRENT_TIMESTAMP WHERE session_id=?")) {
                    q.setObject(1,id);q.executeUpdate();
                }
                try(PreparedStatement q=c.prepareStatement("UPDATE employee_web_devices SET last_seen_at=CURRENT_TIMESTAMP WHERE device_id=?")) {q.setObject(1,browser);q.executeUpdate();}
                return new Session(id,u.id,u.name,permissions(c,u.id));
            }
        }
    }

    private Object renewCsrf(Connection c,Session s)throws Exception {
        String csrf=LanSecurity.randomToken();
        try(PreparedStatement p=c.prepareStatement("UPDATE employee_web_sessions SET csrf_hash=? WHERE session_id=?")) {p.setString(1,LanSecurity.sha256(csrf));p.setObject(2,s.id);p.executeUpdate();}
        return Map.of("csrfToken",csrf);
    }
    private Object logout(Connection c,HttpExchange x,Session s)throws Exception {
        try(PreparedStatement p=c.prepareStatement("UPDATE employee_web_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE session_id=?")){p.setObject(1,s.id);p.executeUpdate();}
        audit(c,"EMPLOYEE_WEB_LOGOUT",s.userId,"Employee browser session revoked");
        c.commit();clearCookie(x,"ss_employee");return Map.of();
    }
    private User activeUser(Connection c,UUID auth)throws Exception {
        try(PreparedStatement p=c.prepareStatement("SELECT user_id,COALESCE(NULLIF(full_name,''),username) FROM users WHERE auth_user_id=? AND is_active=TRUE")) {
            p.setObject(1,auth);try(ResultSet r=p.executeQuery()) {
                if(!r.next()) throw err(403,"ACCOUNT_DENIED","No active SmartStock account matches this login.");
                int id=r.getInt(1);
                if(!permissions(c,id).contains("ACCESS_EMPLOYEE_WEB")) throw err(403,"ACCESS_DENIED","Your role does not have Access Employee Web App permission.");
                return new User(id,r.getString(2));
            }
        }
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
    private record Preauth(String accessToken,UUID authUserId,String factorId,String challengeId,Instant expires,String identifierHash,String address,UUID browser) { }
    private record User(int id,String name) { }
    private record Session(UUID id,int userId,String name,Set<String> permissions) { }
    // Shared protocol conventions with the scheduler gateway; tokens stay server-side.
    private JsonObject supabase(SupabaseProjectConfig cfg,String path,String bearer,JsonObject payload)throws Exception{HttpRequest.Builder req=HttpRequest.newBuilder(URI.create(cfg.url()+path)).timeout(Duration.ofSeconds(20)).header("apikey",cfg.publishableKey());if(bearer!=null)req.header("Authorization","Bearer "+bearer);if(payload==null)req.GET();else req.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)));HttpResponse<String>res=http.send(req.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));if(res.statusCode()<200||res.statusCode()>=300)throw err(401,"AUTH_FAILED","The sign-in or authenticator code was not accepted.");return JsonParser.parseString(res.body()).getAsJsonObject();}
    private Set<String> permissions(Connection c,int id)throws SQLException{Set<String>out=new HashSet<>();try(PreparedStatement p=c.prepareStatement("SELECT UPPER(p.permission_key) FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions p ON p.permission_id=rp.permission_id WHERE u.user_id=? UNION SELECT UPPER(rmp.permission_key) FROM users u JOIN role_mobile_permissions rmp ON rmp.role_id=u.role_id WHERE u.user_id=?")){p.setInt(1,id);p.setInt(2,id);try(ResultSet r=p.executeQuery()){while(r.next())out.add(r.getString(1));}}return Set.copyOf(out);}
    private void rateLimit(Connection c,String key,String address)throws Exception{try(PreparedStatement p=c.prepareStatement("SELECT count(*) FROM employee_web_auth_attempts WHERE succeeded=FALSE AND attempted_at>CURRENT_TIMESTAMP-INTERVAL '15 minutes' AND (identifier_hash=? OR remote_address=?)")){p.setString(1,key);p.setString(2,address);try(ResultSet r=p.executeQuery()){r.next();if(r.getInt(1)>=8)throw err(429,"LOGIN_THROTTLED","Too many sign-in attempts. Wait 15 minutes.");}}}
    private void recordAttempt(Connection c,String key,String address,boolean ok)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT INTO employee_web_auth_attempts(identifier_hash,remote_address,succeeded) VALUES(?,?,?)")){p.setString(1,key);p.setString(2,address);p.setBoolean(3,ok);p.executeUpdate();}}
    private void audit(Connection c,String type,int user,String details)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT INTO security_audit_events(event_type,actor_user_id,details) VALUES(?,?,?)")){p.setString(1,type);p.setInt(2,user);p.setString(3,details);p.executeUpdate();}}
    private void sameOrigin(HttpExchange x)throws WebError{String origin=x.getRequestHeaders().getFirst("Origin");if(origin==null||!publicOrigin.equalsIgnoreCase(origin))throw err(403,"ORIGIN_DENIED","This web origin is not allowed.");}
    static String cleanOrigin(String v){if(v==null||v.isBlank())return null;try{URI u=URI.create(v.trim());if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getRawQuery()!=null||u.getRawFragment()!=null||u.getRawUserInfo()!=null||(u.getRawPath()!=null&&!u.getRawPath().isEmpty()&&!u.getRawPath().equals("/")))return null;return u.getPort()<0?"https://"+u.getHost():"https://"+u.getHost()+":"+u.getPort();}catch(Exception e){return null;}}
    private static String remote(HttpExchange x){String f=x.getRequestHeaders().getFirst("CF-Connecting-IP");return f!=null&&f.length()<=45&&f.matches("[0-9a-fA-F:.]+")?f:x.getRemoteAddress().getAddress().getHostAddress();}
    private static JsonObject body(HttpExchange x)throws Exception{int limit=x.getRequestURI().getPath().endsWith("/overseas")?3*1024*1024:1024*1024;byte[]b=x.getRequestBody().readNBytes(limit+1);if(b.length>limit)throw err(413,"REQUEST_TOO_LARGE","Request too large.");try{return b.length==0?new JsonObject():JsonParser.parseString(new String(b,StandardCharsets.UTF_8)).getAsJsonObject();}catch(RuntimeException e){throw err(400,"VALIDATION_ERROR","A JSON object is required.");}}
    private static String jwt(String token,String key){try{String[]p=token.split("\\.");JsonObject j=JsonParser.parseString(new String(Base64.getUrlDecoder().decode(p[1]),StandardCharsets.UTF_8)).getAsJsonObject();return str(j,key);}catch(Exception e){return null;}}
    private static String required(JsonObject b,String k,int max)throws WebError{String v=optional(b,k);if(v==null||v.isBlank()||v.length()>max)throw err(400,"VALIDATION_ERROR",k+" is required.");return v.trim();}private static String optional(JsonObject b,String k){return b.has(k)&&!b.get(k).isJsonNull()?b.get(k).getAsString():null;}private static String str(JsonObject b,String k){return b!=null&&b.has(k)&&!b.get(k).isJsonNull()?b.get(k).getAsString():null;}private static int integer(JsonObject b,String k)throws WebError{try{return b.get(k).getAsInt();}catch(Exception e){throw err(400,"VALIDATION_ERROR",k+" is invalid.");}}private static LocalDate date(JsonObject b,String k)throws WebError{try{return LocalDate.parse(required(b,k,40));}catch(Exception e){throw err(400,"VALIDATION_ERROR",k+" is invalid.");}}private static LocalTime time(JsonObject b,String k)throws WebError{try{return LocalTime.parse(required(b,k,20));}catch(Exception e){throw err(400,"VALIDATION_ERROR",k+" is invalid.");}}private static UUID uuid(JsonObject b,String k,boolean nullable)throws WebError{String v=optional(b,k);if(nullable&&(v==null||v.isBlank()))return null;try{return UUID.fromString(v);}catch(Exception e){throw err(400,"VALIDATION_ERROR",k+" is invalid.");}}private static String url(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8);}
    private static void cookie(HttpExchange x,String name,String value,int age){x.getResponseHeaders().add("Set-Cookie",name+"="+value+"; Path=/employees; Max-Age="+age+"; Secure; HttpOnly; SameSite=Strict");}private static void clearCookie(HttpExchange x,String name){cookie(x,name,"",0);}private static String cookie(HttpExchange x,String name){String h=x.getRequestHeaders().getFirst("Cookie");if(h!=null)for(String p:h.split(";")){String[]kv=p.trim().split("=",2);if(kv.length==2&&name.equals(kv[0]))return kv[1];}return null;}
    private static void headers(HttpExchange x){Headers h=x.getResponseHeaders();h.set("Cache-Control","no-store");h.set("X-Content-Type-Options","nosniff");h.set("X-Frame-Options","DENY");h.set("Referrer-Policy","no-referrer");h.set("Permissions-Policy","camera=(), microphone=(), geolocation=()");h.set("Content-Security-Policy","default-src 'self'; connect-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; manifest-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");}
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
