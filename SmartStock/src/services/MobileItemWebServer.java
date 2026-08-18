package services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import data.DB;
import data.DatabaseConfig;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Opt-in, store-LAN-only mobile item UI and its separately bound API. */
public final class MobileItemWebServer implements AutoCloseable {
    public static final int UI_PORT = 8444;
    public static final int API_PORT = 8445;
    private static final int MAX_JSON = 18 * 1024 * 1024;
    private static final Duration IDLE = Duration.ofMinutes(15);
    private static final Duration ABSOLUTE = Duration.ofHours(12);
    private static final Gson GSON = LanJson.create();
    private final HttpsServer ui;
    private final HttpsServer api;
    private final ExecutorService executor;
    private final LanApiServer owner;
    private final String host;

    private MobileItemWebServer(HttpsServer ui, HttpsServer api, ExecutorService executor,
                                LanApiServer owner, String host) {
        this.ui=ui; this.api=api; this.executor=executor; this.owner=owner; this.host=host;
    }

    public static MobileItemWebServer start(LanTlsIdentity identity, LanApiServer owner) throws Exception {
        String host=LanTlsIdentity.tlsHostName();
        HttpsServer ui=null,api=null;ExecutorService pool=null;
        try{
            ui=HttpsServer.create(new InetSocketAddress(UI_PORT),20);api=HttpsServer.create(new InetSocketAddress(API_PORT),40);
            ui.setHttpsConfigurator(new HttpsConfigurator(identity.sslContext()));api.setHttpsConfigurator(new HttpsConfigurator(identity.sslContext()));
            pool=Executors.newFixedThreadPool(8,r->{Thread t=new Thread(r,"smartstock-mobile-web");t.setDaemon(true);return t;});
            ui.setExecutor(pool);api.setExecutor(pool);MobileItemWebServer server=new MobileItemWebServer(ui,api,pool,owner,host);
            ui.createContext("/",server::ui);api.createContext("/api/v1/",server::api);ui.start();api.start();return server;
        }catch(Exception e){if(ui!=null)ui.stop(0);if(api!=null)api.stop(0);if(pool!=null)pool.shutdownNow();throw e;}
    }

    public String url(){return "https://"+host+":"+UI_PORT+"/";}

    private void ui(HttpExchange x) {
        try {
            requireLan(x); String path=x.getRequestURI().getPath();
            String resource=switch(path){case "/","/index.html"->"mobile-web/index.html";case "/app.css"->"mobile-web/app.css";case "/app.js"->"mobile-web/app.js";default->null;};
            if(resource==null){sendText(x,404,"text/plain; charset=utf-8","Not found");return;}
            try(InputStream in=MobileItemWebServer.class.getClassLoader().getResourceAsStream(resource)){
                if(in==null){sendText(x,404,"text/plain; charset=utf-8","Web asset missing");return;}
                byte[] bytes=in.readAllBytes(); String type=resource.endsWith(".css")?"text/css; charset=utf-8":resource.endsWith(".js")?"application/javascript; charset=utf-8":"text/html; charset=utf-8";
                security(x.getResponseHeaders());x.getResponseHeaders().set("Content-Type",type);x.sendResponseHeaders(200,bytes.length);x.getResponseBody().write(bytes);
            }
        }catch(Exception e){quietError(x,e);}
        finally{x.close();}
    }

    private void api(HttpExchange x) {
        try {
            requireLan(x); cors(x);
            if("OPTIONS".equals(x.getRequestMethod())){x.sendResponseHeaders(204,-1);return;}
            String route=x.getRequestURI().getPath().substring("/api/v1".length());
            JsonObject body=readJson(x);
            Object result=switch(route){
                case "/activate"->activate(x,body);
                case "/login"->login(x,body);
                case "/session"->session(x);
                case "/logout"->logout(x);
                case "/bootstrap"->bootstrap(requireSession(x,false));
                case "/products/search"->productSearch(requireSession(x,false),body);
                case "/products/save"->productSave(x,requireSession(x,true),body);
                case "/custom/state"->customState(requireSession(x,false));
                case "/custom/save"->customSave(x,requireSession(x,true),body);
                case "/barcodes/generate"->barcode(requireSession(x,true));
                case "/images/upload"->imageUpload(x,requireSession(x,true),body);
                case "/images/fetch"->imageFetch(requireSession(x,false),body);
                default->throw new WebError(404,"NOT_FOUND","Web API route not found.");
            };
            sendJson(x,200,Map.of("ok",true,"data",result==null?Map.of():result));
        }catch(WebError e){try{sendJson(x,e.status,Map.of("ok",false,"error",Map.of("code",e.code,"message",e.getMessage())));}catch(Exception ignored){}}
        catch(LanProductAdminService.RuleViolation e){try{sendJson(x,e.status(),Map.of("ok",false,"error",Map.of("code",e.code(),"message",e.safeMessage())));}catch(Exception ignored){}}
        catch(CatalogBarcodeService.ConflictException e){try{sendJson(x,409,Map.of("ok",false,"error",Map.of("code","BARCODE_EXISTS","message",safe(e))));}catch(Exception ignored){}}
        catch(Exception e){try{sendJson(x,500,Map.of("ok",false,"error",Map.of("code","SERVER_ERROR","message",safe(e))));}catch(Exception ignored){}}
        finally{x.close();}
    }

    private Object activate(HttpExchange x,JsonObject b)throws Exception{
        String token=text(b,"token",300);String hash=LanSecurity.sha256(token);String browser=LanSecurity.randomToken();
        try(Connection c=DB.getConnection()){c.setAutoCommit(false);try(PreparedStatement p=c.prepareStatement("""
            UPDATE mobile_item_web_activations a SET used_at=CURRENT_TIMESTAMP
            FROM mobile_item_web_runtime r WHERE a.token_hash=? AND a.generation=r.generation AND r.enabled
              AND a.used_at IS NULL AND a.revoked_at IS NULL AND a.expires_at>CURRENT_TIMESTAMP
            RETURNING a.generation
            """)){p.setString(1,hash);try(ResultSet r=p.executeQuery()){if(!r.next())throw new WebError(401,"ACTIVATION_INVALID","This activation link is invalid, expired, or already used.");UUID generation=(UUID)r.getObject(1);try(PreparedStatement q=c.prepareStatement("INSERT INTO mobile_item_web_browsers(generation,credential_hash) VALUES(?,?)")){q.setObject(1,generation);q.setString(2,LanSecurity.sha256(browser));q.executeUpdate();}audit(c,"MOBILE_WEB_BROWSER_ACTIVATED",null,"A browser used a one-time mobile item activation");}c.commit();}}
        setCookie(x,"ss_browser",browser,12*60*60);return Map.of("activated",true);}

    private Object login(HttpExchange x,JsonObject b)throws Exception{
        Browser browser=requireBrowser(x);int location=DatabaseConfig.load().locationId()==null?0:DatabaseConfig.load().locationId();
        LanApiServer.MobileLogin user;try{user=owner.authenticateMobileLogin(text(b,"identifier",512),optional(b,"secret"),location);}catch(Exception e){throw new WebError(401,"LOGIN_FAILED",safe(e));}
        String token=LanSecurity.randomToken(),csrf=LanSecurity.randomToken();Instant now=Instant.now();
        try(Connection c=DB.getConnection();PreparedStatement p=c.prepareStatement("""
            INSERT INTO mobile_item_web_sessions(browser_id,session_hash,csrf_hash,user_id,location_id,auth_source,expires_at,absolute_expires_at)
            VALUES(?,?,?,?,?,?,?,?)
            """)){p.setObject(1,browser.id);p.setString(2,LanSecurity.sha256(token));p.setString(3,LanSecurity.sha256(csrf));p.setInt(4,user.userId());p.setInt(5,user.locationId());p.setString(6,user.source());p.setTimestamp(7,Timestamp.from(now.plus(IDLE)));p.setTimestamp(8,Timestamp.from(now.plus(ABSOLUTE)));p.executeUpdate();audit(c,"MOBILE_WEB_LOGIN",user.userId(),"Mobile item web login via "+user.source());}
        setCookie(x,"ss_session",token,12*60*60);return Map.of("csrfToken",csrf,"user",user,"permissions",owner.mobilePermissions(user.userId()));
    }

    private Object session(HttpExchange x)throws Exception{Session s=requireSession(x,false);String csrf=LanSecurity.randomToken();try(Connection c=DB.getConnection();PreparedStatement p=c.prepareStatement("UPDATE mobile_item_web_sessions SET csrf_hash=? WHERE session_id=?")){p.setString(1,LanSecurity.sha256(csrf));p.setObject(2,s.id);p.executeUpdate();}return Map.of("csrfToken",csrf,"userId",s.userId,"locationId",s.locationId,"permissions",owner.mobilePermissions(s.userId));}
    private Object logout(HttpExchange x)throws Exception{String token=cookie(x,"ss_session");if(token!=null)try(Connection c=DB.getConnection();PreparedStatement p=c.prepareStatement("UPDATE mobile_item_web_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE session_hash=?")){p.setString(1,LanSecurity.sha256(token));p.executeUpdate();}clearCookie(x,"ss_session");return Map.of();}

    private Object bootstrap(Session s)throws Exception{
        try(Connection c=DB.getConnection()){
            Map<String,Object> out=new LinkedHashMap<>();out.put("permissions",owner.mobilePermissions(s.userId));
            out.put("departments",rows(c,"SELECT category_id id,name FROM categories ORDER BY name"));
            out.put("vendors",rows(c,"SELECT vendor_id id,name FROM vendors ORDER BY name"));
            out.put("shelves",rows(c,"SELECT shelf_location_id id,name FROM shelf_locations WHERE location_id="+s.locationId+" ORDER BY name"));
            return out;
        }
    }
    private Object productSearch(Session s,JsonObject b)throws Exception{try(Connection c=DB.getConnection()){return Map.of("products",LanProductAdminService.searchEditable(c,optional(b,"query"),s.userId,s.locationId));}}
    private Object productSave(HttpExchange x,Session s,JsonObject b)throws Exception{return idempotent(x,s,b,b.has("productId")&&!b.get("productId").isJsonNull()?"product.update":"product.create",c->b.has("productId")&&!b.get("productId").isJsonNull()?LanProductAdminService.update(c,b,null,s.userId,owner.mobileDisplayName(c,s.userId,s.locationId),s.locationId):LanProductAdminService.create(c,b,null,s.userId,owner.mobileDisplayName(c,s.userId,s.locationId),s.locationId));}
    private Object customState(Session s)throws Exception{owner.requireMobileCustomPermission(s.userId);try(Connection c=DB.getConnection()){return Map.of("state",LanCustomOrderCatalogAdminService.load(c));}}
    private Object customSave(HttpExchange x,Session s,JsonObject b)throws Exception{owner.requireMobileCustomPermission(s.userId);return idempotent(x,s,b,"custom."+text(b,"action",40),c->Map.of("recordId",LanCustomOrderCatalogAdminService.mutate(c,text(b,"action",40),b)));}
    private Object barcode(Session s)throws Exception{owner.requireMobileAny(s.userId,"NEW_ITEM","EDIT_ITEM","MANAGE_CUSTOM_ORDER_ITEMS","CUSTOM_ORDER_ITEMS","MANAGE_CUSTOM_ORDERS");try(Connection c=DB.getConnection()){return Map.of("barcode",CatalogBarcodeService.generateAvailable(c));}}
    private Object imageUpload(HttpExchange x,Session s,JsonObject b)throws Exception{owner.requireMobileAny(s.userId,"NEW_ITEM","EDIT_ITEM","MANAGE_CUSTOM_ORDER_ITEMS","CUSTOM_ORDER_ITEMS","MANAGE_CUSTOM_ORDERS");byte[] bytes=Base64.getDecoder().decode(text(b,"bytesBase64",16*1024*1024));if(bytes.length>2*1024*1024)throw new WebError(413,"IMAGE_TOO_LARGE","The optimized image must be 2 MB or smaller.");bytes=optimizeJpeg(bytes);String requested=optional(b,"category").toUpperCase(java.util.Locale.ROOT),category=switch(requested){case"CUSTOM_ITEM","CUSTOM_VARIANT"->requested;default->"PRODUCT";};String name="mobile-"+System.currentTimeMillis()+"-"+UUID.randomUUID()+".jpg";try(Connection c=DB.getConnection()){String ref=ServerImageAssetService.storeUpload(c,category,"Product Images","products/"+name,"image/jpeg",name,"PUBLIC",bytes);return Map.of("reference",ref,"cloudStatus","PENDING");}}
    private Object imageFetch(Session s,JsonObject b)throws Exception{ServerImageAssetService.AssetBytes a=ServerImageAssetService.load(text(b,"reference",1000));return Map.of("contentType",a.contentType(),"bytesBase64",Base64.getEncoder().encodeToString(a.bytes()));}

    private Object idempotent(HttpExchange x,Session s,JsonObject b,String op,Work work)throws Exception{String key=x.getRequestHeaders().getFirst("Idempotency-Key");if(key==null||key.isBlank()||key.length()>160)throw new WebError(400,"IDEMPOTENCY_REQUIRED","A valid idempotency key is required.");String hash=LanSecurity.sha256(GSON.toJson(b));try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{try(PreparedStatement lock=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")){lock.setString(1,s.browserId+":"+key);lock.execute();}try(PreparedStatement p=c.prepareStatement("SELECT operation_key,request_hash,response_json::text FROM mobile_item_web_idempotency WHERE browser_id=? AND idempotency_key=?")){p.setObject(1,s.browserId);p.setString(2,key);try(ResultSet r=p.executeQuery()){if(r.next()){if(!op.equals(r.getString(1))||!hash.equals(r.getString(2)))throw new WebError(409,"IDEMPOTENCY_CONFLICT","That save key was already used for different data.");Object prior=GSON.fromJson(r.getString(3),Object.class);c.commit();return prior;}}}Object value=work.run(c);try(PreparedStatement p=c.prepareStatement("INSERT INTO mobile_item_web_idempotency(browser_id,idempotency_key,operation_key,request_hash,response_json) VALUES(?,?,?,?,?::jsonb)")){p.setObject(1,s.browserId);p.setString(2,key);p.setString(3,op);p.setString(4,hash);p.setString(5,GSON.toJson(value));p.executeUpdate();}c.commit();return value;}catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}}}

    private Browser requireBrowser(HttpExchange x)throws Exception{String token=cookie(x,"ss_browser");if(token==null)throw new WebError(401,"ACTIVATION_REQUIRED","Scan a current SmartStock activation QR code.");try(Connection c=DB.getConnection();PreparedStatement p=c.prepareStatement("""
        SELECT b.browser_id FROM mobile_item_web_browsers b JOIN mobile_item_web_runtime r ON r.generation=b.generation
        WHERE b.credential_hash=? AND b.revoked_at IS NULL AND r.enabled
        """)){p.setString(1,LanSecurity.sha256(token));try(ResultSet r=p.executeQuery()){if(!r.next())throw new WebError(401,"ACTIVATION_REQUIRED","This browser authorization is no longer active.");return new Browser((UUID)r.getObject(1));}}}
    private Session requireSession(HttpExchange x,boolean csrf)throws Exception{Browser b=requireBrowser(x);String token=cookie(x,"ss_session");if(token==null)throw new WebError(401,"LOGIN_REQUIRED","Employee login is required.");try(Connection c=DB.getConnection()){c.setAutoCommit(false);try(PreparedStatement p=c.prepareStatement("""
        SELECT s.session_id,s.user_id,s.location_id,s.csrf_hash,s.expires_at,s.absolute_expires_at FROM mobile_item_web_sessions s
        JOIN users u ON u.user_id=s.user_id AND COALESCE(u.is_active,TRUE)=TRUE
        WHERE s.browser_id=? AND s.session_hash=? AND s.revoked_at IS NULL FOR UPDATE OF s
        """)){p.setObject(1,b.id);p.setString(2,LanSecurity.sha256(token));try(ResultSet r=p.executeQuery()){if(!r.next())throw new WebError(401,"SESSION_INVALID","Please log in again.");Instant now=Instant.now(),exp=r.getTimestamp(5).toInstant(),abs=r.getTimestamp(6).toInstant();if(!exp.isAfter(now)||!abs.isAfter(now))throw new WebError(401,"SESSION_EXPIRED","Please log in again.");if(csrf){String supplied=x.getRequestHeaders().getFirst("X-CSRF-Token");if(supplied==null||!LanSecurity.constantTimeEquals(LanSecurity.sha256(supplied),r.getString(4)))throw new WebError(403,"CSRF_INVALID","The form security token is invalid.");}UUID id=(UUID)r.getObject(1);Instant next=now.plus(IDLE).isBefore(abs)?now.plus(IDLE):abs;try(PreparedStatement q=c.prepareStatement("UPDATE mobile_item_web_sessions SET expires_at=?,last_seen_at=CURRENT_TIMESTAMP WHERE session_id=?")){q.setTimestamp(1,Timestamp.from(next));q.setObject(2,id);q.executeUpdate();}c.commit();return new Session(id,b.id,r.getInt(2),r.getInt(3));}}catch(Exception e){c.rollback();throw e;}}}

    private static List<Map<String,Object>> rows(Connection c,String sql)throws Exception{List<Map<String,Object>> out=new ArrayList<>();try(var p=c.prepareStatement(sql);var r=p.executeQuery()){while(r.next())out.add(Map.of("id",r.getObject(1),"name",r.getString(2)));}return out;}
    private static void audit(Connection c,String type,Integer userId,String details)throws Exception{try(PreparedStatement p=c.prepareStatement("INSERT INTO security_audit_events(event_type,actor_user_id,details) VALUES(?,?,?)")){p.setString(1,type);if(userId==null)p.setNull(2,java.sql.Types.INTEGER);else p.setInt(2,userId);p.setString(3,details);p.executeUpdate();}}
    private static byte[] optimizeJpeg(byte[] input)throws Exception{java.awt.image.BufferedImage source=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(input));if(source==null)throw new WebError(400,"IMAGE_INVALID","Choose a valid JPEG or PNG image.");double scale=Math.min(1d,1200d/Math.max(source.getWidth(),source.getHeight()));int w=Math.max(1,(int)Math.round(source.getWidth()*scale)),h=Math.max(1,(int)Math.round(source.getHeight()*scale));java.awt.image.BufferedImage output=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g=output.createGraphics();try{g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,w,h);g.drawImage(source,0,0,w,h,null);}finally{g.dispose();}ByteArrayOutputStream bytes=new ByteArrayOutputStream();var writers=javax.imageio.ImageIO.getImageWritersByFormatName("jpeg");var writer=writers.next();try(var stream=javax.imageio.ImageIO.createImageOutputStream(bytes)){writer.setOutput(stream);var param=writer.getDefaultWriteParam();param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);param.setCompressionQuality(.78f);writer.write(null,new javax.imageio.IIOImage(output,null,null),param);}finally{writer.dispose();}return bytes.toByteArray();}
    private static JsonObject readJson(HttpExchange x)throws Exception{if(!"POST".equals(x.getRequestMethod()))throw new WebError(405,"METHOD_NOT_ALLOWED","POST is required.");byte[] bytes=readLimited(x.getRequestBody(),MAX_JSON);if(bytes.length==0)return new JsonObject();return JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static byte[] readLimited(InputStream in,int max)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n,total=0;while((n=in.read(buf))>=0){total+=n;if(total>max)throw new WebError(413,"REQUEST_TOO_LARGE","The request is too large.");out.write(buf,0,n);}return out.toByteArray();}
    private void cors(HttpExchange x)throws WebError{String origin=x.getRequestHeaders().getFirst("Origin"),allowed="https://"+host+":"+UI_PORT;if(origin!=null&&!origin.equals(allowed))throw new WebError(403,"ORIGIN_DENIED","This web origin is not allowed.");Headers h=x.getResponseHeaders();h.set("Access-Control-Allow-Origin",allowed);h.set("Access-Control-Allow-Credentials","true");h.set("Access-Control-Allow-Headers","Content-Type,X-CSRF-Token,Idempotency-Key");h.set("Access-Control-Allow-Methods","POST,OPTIONS");h.set("Vary","Origin");security(h);}
    private static void security(Headers h){h.set("Cache-Control","no-store");h.set("X-Content-Type-Options","nosniff");h.set("X-Frame-Options","DENY");h.set("Referrer-Policy","no-referrer");h.set("Content-Security-Policy","default-src 'self'; connect-src https:; img-src 'self' blob: data:; style-src 'self'; script-src 'self'; base-uri 'none'; frame-ancestors 'none'");}
    private static void requireLan(HttpExchange x)throws WebError{InetAddress a=x.getRemoteAddress().getAddress();if(!(a.isLoopbackAddress()||a.isSiteLocalAddress()||a.isLinkLocalAddress()))throw new WebError(403,"LAN_ONLY","This app is available only on the store network.");}
    private static String cookie(HttpExchange x,String name){String all=x.getRequestHeaders().getFirst("Cookie");if(all==null)return null;for(String p:all.split(";")){String[] kv=p.trim().split("=",2);if(kv.length==2&&name.equals(kv[0]))return kv[1];}return null;}
    private static void setCookie(HttpExchange x,String name,String value,int age){x.getResponseHeaders().add("Set-Cookie",name+"="+value+"; Path=/; Max-Age="+age+"; Secure; HttpOnly; SameSite=Strict");}
    private static void clearCookie(HttpExchange x,String name){x.getResponseHeaders().add("Set-Cookie",name+"=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Strict");}
    private static String text(JsonObject b,String key,int max)throws WebError{String v=optional(b,key);if(v==null||v.isBlank()||v.length()>max)throw new WebError(400,"VALIDATION_ERROR",key+" is required.");return v.trim();}
    private static String optional(JsonObject b,String key){return b!=null&&b.has(key)&&!b.get(key).isJsonNull()?b.get(key).getAsString():"";}
    private static void sendJson(HttpExchange x,int status,Object value)throws Exception{byte[] bytes=GSON.toJson(value).getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");security(x.getResponseHeaders());x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);}
    private static void sendText(HttpExchange x,int status,String type,String value)throws Exception{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type",type);x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);}
    private static void quietError(HttpExchange x,Exception e){try{sendText(x,500,"text/plain; charset=utf-8",safe(e));}catch(Exception ignored){}}
    private static String safe(Throwable e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}

    public void close(){ui.stop(1);api.stop(1);executor.shutdownNow();}
    private record Browser(UUID id){}
    private record Session(UUID id,UUID browserId,int userId,int locationId){}
    private interface Work{Object run(Connection connection)throws Exception;}
    private static final class WebError extends Exception{final int status;final String code;WebError(int status,String code,String message){super(message);this.status=status;this.code=code;}}
}
