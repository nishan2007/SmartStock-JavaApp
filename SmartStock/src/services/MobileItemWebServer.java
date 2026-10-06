package services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import data.DB;
import data.DatabaseConfig;
import utils.DeviceUtils;

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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<String,PhotoHandoff> photoHandoffsByToken=new ConcurrentHashMap<>();
    private final Map<UUID,PhotoHandoff> photoHandoffsById=new ConcurrentHashMap<>();

    private MobileItemWebServer(HttpsServer ui, HttpsServer api, ExecutorService executor,
                                LanApiServer owner, String host) {
        this.ui=ui; this.api=api; this.executor=executor; this.owner=owner; this.host=host;
    }

    public static MobileItemWebServer start(LanTlsIdentity identity, LanApiServer owner) throws Exception {
        String host=LanTlsIdentity.mobileWebHostName();
        HttpsServer ui=null,api=null;ExecutorService pool=null;
        try{
            InetAddress ipv4Any=InetAddress.getByName("0.0.0.0");
            ui=HttpsServer.create(new InetSocketAddress(ipv4Any,UI_PORT),20);
            api=HttpsServer.create(new InetSocketAddress(ipv4Any,API_PORT),40);
            var browserTls = LanTlsIdentity.mobileWebSslContext(identity);
            ui.setHttpsConfigurator(new HttpsConfigurator(browserTls));api.setHttpsConfigurator(new HttpsConfigurator(browserTls));
            pool=Executors.newFixedThreadPool(8,r->{Thread t=new Thread(r,"smartstock-mobile-web");t.setDaemon(true);return t;});
            ui.setExecutor(pool);api.setExecutor(pool);MobileItemWebServer server=new MobileItemWebServer(ui,api,pool,owner,host);
            try(Connection c=DB.getConnection()){server.mobileDeviceId(c,DatabaseConfig.load().locationId());}
            ui.createContext("/",x->{try{server.ui(x);}finally{WebRuntimeMetrics.record("mobile",x);}});
            api.createContext("/api/v1/",x->{try{server.api(x);}finally{WebRuntimeMetrics.record("mobile",x);}});
            ui.start();api.start();WebRuntimeMetrics.started("mobile");return server;
        }catch(Exception e){if(ui!=null)ui.stop(0);if(api!=null)api.stop(0);if(pool!=null)pool.shutdownNow();throw e;}
    }

    public String url(){return "https://"+host+":"+UI_PORT+"/";}

    private void ui(HttpExchange x) {
        try {
            requireLan(x); String path=x.getRequestURI().getPath();
            if(path.startsWith("/api/v1/")){api(x);return;}
            String resource=switch(path){case "/","/index.html"->"mobile-web/index.html";case "/app.css"->"mobile-web/app.css";case "/boot.js"->"mobile-web/boot.js";case "/app.js"->"mobile-web/app.js";case "/photo.html"->"mobile-web/photo.html";case "/photo.js"->"mobile-web/photo.js";case "/background-remover.html"->"mobile-web/background-remover.html";case "/background-remover.js"->"mobile-web/background-remover.js";case "/background-remover.css"->"mobile-web/background-remover.css";case "/spoils.js"->"mobile-web/spoils.js";case "/design.html"->"mobile-web/design.html";case "/design.js"->"mobile-web/design.js";case "/design.css"->"mobile-web/design.css";default->null;};
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
            if("GET".equals(x.getRequestMethod())&&"/trust".equals(route)){
                security(x.getResponseHeaders());
                sendText(x,200,"text/html; charset=utf-8","""
                    <!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>SmartStock API ready</title></head>
                    <body style="font-family:-apple-system,sans-serif;background:#f3f5fa;color:#172033;padding:32px"><main style="max-width:560px;margin:auto;background:white;border-radius:20px;padding:28px"><h1>SmartStock API is ready</h1><p>The secure mobile API connection is working. Return to the previous SmartStock tab and tap <b>Try again</b>.</p></main></body></html>
                    """);
                return;
            }
            JsonObject body=readJson(x);
            Object result=switch(route){
                case "/activate"->activate(x,body);
                case "/login"->login(x,body);
                case "/session"->session(x);
                case "/logout"->logout(x);
                case "/bootstrap"->bootstrap(requireSession(x,false));
                case "/products/search"->productSearch(requireSession(x,false),body);
                case "/products/save"->productSave(x,requireSession(x,true),body);
                case "/price-tags/print"->priceTagPrint(requireSession(x,true),body);
                case "/custom/state"->customState(requireSession(x,false));
                case "/custom/save"->customSave(x,requireSession(x,true),body);
                case "/barcodes/generate"->barcode(requireSession(x,true));
                case "/barcodes/scan"->barcodeScan(requireSession(x,true),body);
                case "/images/upload"->imageUpload(x,requireSession(x,true),body);
                case "/images/fetch"->imageFetch(requireSession(x,false),body);
                case "/studio/branding"->studioBranding(requireSession(x,false));
                case "/studio/remove-background"->studioRemoveBackground(requireSession(x,true),body);
                case "/studio/catalog"->studioCatalog(requireSession(x,false),body);
                case "/studio/import"->studioImport(x,requireSession(x,true),body);
                case "/spoils/search"->spoilRead(requireSession(x,false),body,"SEARCH");
                case "/spoils/state"->spoilRead(requireSession(x,false),body,"STATE");
                case "/spoils/photo"->spoilRead(requireSession(x,false),body,"PHOTO");
                case "/spoils/submit"->spoilSubmit(x,requireSession(x,true),body);
                case "/spoils/reverse"->spoilReverse(x,requireSession(x,true),body);
                case "/design/state"->designState(requireSession(x,false),body);
                case "/design/save"->designSave(x,requireSession(x,true),body);
                case "/design/file"->designFile(requireSession(x,false),body);
                case "/design/attach"->designAttach(x,requireSession(x,true),body);
                case "/design/publish"->designPublish(x,requireSession(x,true),body);
                case "/photo/target"->photoTarget(body);
                case "/photo/upload"->photoUpload(body);
                default->throw new WebError(404,"NOT_FOUND","Web API route not found.");
            };
            sendJson(x,200,Map.of("ok",true,"data",result==null?Map.of():result));
        }catch(WebError e){try{sendJson(x,e.status,Map.of("ok",false,"error",Map.of("code",e.code,"message",e.getMessage())));}catch(Exception ignored){}}
        catch(LanProductAdminService.RuleViolation e){try{sendJson(x,e.status(),Map.of("ok",false,"error",Map.of("code",e.code(),"message",e.safeMessage())));}catch(Exception ignored){}}
        catch(CatalogBarcodeService.ConflictException e){try{sendJson(x,409,Map.of("ok",false,"error",Map.of("code","BARCODE_EXISTS","message",safe(e))));}catch(Exception ignored){}}
        catch(IllegalArgumentException e){try{sendJson(x,400,Map.of("ok",false,"error",Map.of("code","VALIDATION_ERROR","message",safe(e))));}catch(Exception ignored){}}
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
            try(PreparedStatement p=c.prepareStatement("SELECT COALESCE(require_cost_price_on_new_item,TRUE) FROM company_customization WHERE location_id=?")){p.setInt(1,s.locationId);try(ResultSet r=p.executeQuery()){out.put("requireCostPriceOnNewItem",!r.next()||r.getBoolean(1));}}
            out.put("departments",rows(c,"SELECT category_id id,name FROM categories ORDER BY name"));
            out.put("vendors",rows(c,"SELECT vendor_id id,name FROM vendors WHERE COALESCE(is_active,TRUE)=TRUE ORDER BY name"));
            out.put("itemTypes",itemTypeRows(c));
            out.put("brands",rows(c,"SELECT brand_id id,name FROM item_brands ORDER BY name"));
            out.put("shelves",rows(c,"SELECT shelf_location_id id,name FROM shelf_locations WHERE location_id="+s.locationId+" ORDER BY name"));
            return out;
        }
    }
    private Object productSearch(Session s,JsonObject b)throws Exception{try(Connection c=DB.getConnection()){return Map.of("products",LanProductAdminService.searchEditable(c,optional(b,"query"),s.userId,s.locationId));}}
    private Object productSave(HttpExchange x,Session s,JsonObject b)throws Exception{return idempotent(x,s,b,b.has("productId")&&!b.get("productId").isJsonNull()?"product.update":"product.create",c->{UUID deviceId=mobileDeviceId(c,s.locationId);return b.has("productId")&&!b.get("productId").isJsonNull()?LanProductAdminService.update(c,b,deviceId,s.userId,owner.mobileDisplayName(c,s.userId,s.locationId),s.locationId):LanProductAdminService.create(c,b,deviceId,s.userId,owner.mobileDisplayName(c,s.userId,s.locationId),s.locationId);});}
    private Object priceTagPrint(Session s,JsonObject b)throws Exception{
        String itemType=text(b,"itemType",20);long itemId;
        try{itemId=b.get("itemId").getAsLong();}catch(Exception ex){throw new WebError(400,"VALIDATION_ERROR","The saved item ID is required.");}
        if(itemId<=0)throw new WebError(400,"VALIDATION_ERROR","The saved item ID is required.");
        PriceTagPrintService.PriceTagItem item;managers.CompanyCustomizationManager.PriceTagTemplateSettings template;
        try(Connection c=DB.getConnection()){
            Map<String,Object> row=LanProductAdminService.priceTagItem(c,itemType,itemId,s.userId,s.locationId);
            item=new PriceTagPrintService.PriceTagItem(String.valueOf(row.get("name")),String.valueOf(row.get("size")),String.valueOf(row.get("description")),String.valueOf(row.get("code")),String.valueOf(row.get("code")),(java.math.BigDecimal)row.get("price"));
            Map<String,Object> configured=LanProductAdminService.priceTagSettings(c,s.userId,s.locationId);
            var standard=managers.ServerCompanyCustomizationRepository.decodePriceTagTemplatesForLan(String.valueOf(configured.get("encodedTemplates")),(Boolean)configured.get("showCompany"),(Boolean)configured.get("showSku"),(Boolean)configured.get("showBarcode"),((Number)configured.get("widthInches")).doubleValue(),((Number)configured.get("heightInches")).doubleValue()).get(0);
            template=new managers.CompanyCustomizationManager.PriceTagTemplateSettings(standard.name(),standard.showCompany(),standard.showName(),standard.showPrice(),standard.showSku(),standard.showBarcode(),standard.showSize(),standard.showDescription(),standard.widthInches(),standard.heightInches(),standard.layoutData());
        }
        try{
            String message=PriceTagPrintService.printOnReceiptPrinter(List.of(item),template);
            try(Connection c=DB.getConnection()){audit(c,"MOBILE_WEB_PRICE_TAG_PRINTED",s.userId,"Printed standard receipt-printer price tag for "+itemType+" ID "+itemId);}
            return Map.of("message",message);
        }catch(Exception ex){
            try(Connection c=DB.getConnection()){audit(c,"MOBILE_WEB_PRICE_TAG_PRINT_FAILED",s.userId,"Failed standard receipt-printer price tag for "+itemType+" ID "+itemId+": "+safe(ex));}
            throw new WebError(503,"PRICE_TAG_PRINT_FAILED",safe(ex));
        }
    }
    private Object customState(Session s)throws Exception{owner.requireMobileCustomPermission(s.userId);try(Connection c=DB.getConnection()){return Map.of("state",LanCustomOrderCatalogAdminService.load(c));}}
    private Object customSave(HttpExchange x,Session s,JsonObject b)throws Exception{owner.requireMobileCustomPermission(s.userId);return idempotent(x,s,b,"custom."+text(b,"action",40),c->Map.of("recordId",LanCustomOrderCatalogAdminService.mutate(c,text(b,"action",40),b)));}
    private void requireSpoilPermission(Session s,String... keys)throws Exception{
        var current=owner.mobilePermissions(s.userId);for(String key:keys)if(current.contains(key))return;
        throw new WebError(403,"PERMISSION_DENIED","You do not have permission for this spoil action.");
    }
    private Object spoilRead(Session s,JsonObject b,String action)throws Exception{
        requireSpoilPermission(s,"RECORD_CUSTOM_ORDER_SPOILS","REVERSE_CUSTOM_ORDER_SPOILS","MANAGE_CUSTOM_ORDERS");
        try(Connection c=DB.getConnection()){
            return switch(action){
                case "SEARCH" -> Map.of("orders",CustomOrderSpoilService.search(c,s.locationId,optional(b,"query")));
                case "PHOTO" -> CustomOrderSpoilService.photo(c,s.locationId,UUID.fromString(text(b,"photoId",40)));
                default -> CustomOrderSpoilService.state(c,s.locationId,b.get("orderId").getAsLong());
            };
        }
    }
    private Object spoilSubmit(HttpExchange x,Session s,JsonObject b)throws Exception{
        requireSpoilPermission(s,"RECORD_CUSTOM_ORDER_SPOILS");
        List<CustomOrderSpoilService.Photo> photos=new ArrayList<>();
        var values=b.getAsJsonArray("photos");if(values==null||values.isEmpty()||values.size()>3)throw new WebError(400,"PHOTO_REQUIRED","Attach between one and three photos.");
        for(var value:values){byte[] bytes=Base64.getDecoder().decode(value.getAsString());if(bytes.length>2*1024*1024)throw new WebError(413,"IMAGE_TOO_LARGE","Each photo must be 2 MB or smaller.");photos.add(new CustomOrderSpoilService.Photo(optimizeSpoilPhoto(bytes)));}
        UUID id=UUID.fromString(text(b,"reportId",40));long order=b.get("orderId").getAsLong(),line=b.get("lineId").getAsLong();String why=text(b,"reason",2000);
        return idempotent(x,s,b,"spoils.submit",c->CustomOrderSpoilService.submit(c,s.locationId,s.userId,owner.mobileDisplayName(c,s.userId,s.locationId),id,order,line,why,photos));
    }
    private Object spoilReverse(HttpExchange x,Session s,JsonObject b)throws Exception{
        requireSpoilPermission(s,"REVERSE_CUSTOM_ORDER_SPOILS");
        UUID id=UUID.fromString(text(b,"reportId",40));String why=text(b,"reason",2000);
        return idempotent(x,s,b,"spoils.reverse",c->CustomOrderSpoilService.reverse(c,s.locationId,s.userId,owner.mobileDisplayName(c,s.userId,s.locationId),id,why));
    }
    private Object designState(Session s,JsonObject b)throws Exception{
        owner.requireMobileAny(s.userId,"MANAGE_CUSTOM_ORDERS");long orderId=b.get("orderId").getAsLong();
        try(Connection c=DB.getConnection()){
            Map<String,Object> out=new LinkedHashMap<>();out.put("documents",CustomOrderDesignService.list(c,orderId,s.locationId));
            List<Map<String,Object>> files=new ArrayList<>();
            try(PreparedStatement p=c.prepareStatement("""
                    SELECT f.file_id,f.custom_order_line_id,f.filename,f.content_type,f.byte_size
                    FROM custom_order_files f JOIN custom_orders o ON o.custom_order_id=f.custom_order_id
                    WHERE f.custom_order_id=? AND o.location_id=? AND f.removed_at IS NULL AND f.deleted_at IS NULL
                    ORDER BY f.created_at
                    """)){
                p.setLong(1,orderId);p.setInt(2,s.locationId);
                try(ResultSet r=p.executeQuery()){while(r.next())files.add(Map.of("id",r.getObject(1).toString(),"lineId",r.getLong(2),"name",r.getString(3),"contentType",r.getString(4),"bytes",r.getLong(5)));}
            }
            out.put("files",files);
            List<Map<String,Object>> proofs=new ArrayList<>();
            try(PreparedStatement p=c.prepareStatement("""
                    SELECT p.custom_order_line_id,p.revision,p.status,COALESCE(p.feedback,''),p.document_revision
                    FROM custom_order_design_proofs p JOIN custom_orders o ON o.custom_order_id=p.custom_order_id
                    WHERE p.custom_order_id=? AND o.location_id=? AND p.revoked_at IS NULL ORDER BY p.created_at DESC
                    """)){
                p.setLong(1,orderId);p.setInt(2,s.locationId);
                try(ResultSet r=p.executeQuery()){while(r.next()){Map<String,Object> row=new LinkedHashMap<>();row.put("lineId",r.getLong(1));row.put("revision",r.getInt(2));row.put("status",r.getString(3));row.put("feedback",r.getString(4));row.put("documentRevision",r.getObject(5));proofs.add(row);}}
            }
            out.put("proofs",proofs);return out;
        }
    }
    private Object designSave(HttpExchange x,Session s,JsonObject b)throws Exception{
        owner.requireMobileAny(s.userId,"MANAGE_CUSTOM_ORDERS");
        long orderId=b.get("orderId").getAsLong();UUID documentId=UUID.fromString(text(b,"documentId",40));
        int revision=b.get("revision").getAsInt();JsonObject document=b.getAsJsonObject("document");
        return idempotent(x,s,b,"design.save",c->CustomOrderDesignService.save(c,orderId,s.locationId,documentId,revision,document,s.userId));
    }
    private Object designFile(Session s,JsonObject b)throws Exception{
        owner.requireMobileAny(s.userId,"MANAGE_CUSTOM_ORDERS");long orderId=b.get("orderId").getAsLong();
        UUID fileId=UUID.fromString(text(b,"fileId",40));long offset=b.get("offset").getAsLong();
        String key,name,mime;long size;
        try(Connection c=DB.getConnection();PreparedStatement p=c.prepareStatement("""
                SELECT f.storage_key,f.filename,f.content_type,f.byte_size FROM custom_order_files f
                JOIN custom_orders o ON o.custom_order_id=f.custom_order_id
                WHERE f.file_id=? AND f.custom_order_id=? AND o.location_id=?
                  AND f.removed_at IS NULL AND f.deleted_at IS NULL
                """)){
            p.setObject(1,fileId);p.setLong(2,orderId);p.setInt(3,s.locationId);
            try(ResultSet r=p.executeQuery()){if(!r.next())throw new WebError(404,"FILE_UNAVAILABLE","This attachment is unavailable.");key=r.getString(1);name=r.getString(2);mime=r.getString(3);size=r.getLong(4);}
        }
        if(offset<0||offset>size)throw new WebError(400,"OFFSET_INVALID","Invalid attachment offset.");
        int length=(int)Math.min(262144,size-offset);byte[] bytes=new byte[length];
        try(var file=java.nio.channels.FileChannel.open(CustomOrderMediaService.localFile(key),java.nio.file.StandardOpenOption.READ)){
            java.nio.ByteBuffer buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining()&&file.read(buffer,offset+buffer.position())>0){}
            if(buffer.hasRemaining())throw new java.io.IOException("The attachment is incomplete.");
        }
        return Map.of("bytesBase64",Base64.getEncoder().encodeToString(bytes),"totalBytes",size,"filename",name,"contentType",mime);
    }
    private Object designAttach(HttpExchange x,Session s,JsonObject b)throws Exception{
        owner.requireMobileAny(s.userId,"MANAGE_CUSTOM_ORDERS");
        long orderId=b.get("orderId").getAsLong(),lineId=b.get("lineId").getAsLong();
        String filename=text(b,"filename",180),encoded=text(b,"bytesBase64",9_000_000);
        String extension=filename.substring(filename.lastIndexOf('.')+1).toLowerCase(java.util.Locale.ROOT);
        if(!java.util.Set.of("png","jpg","jpeg","webp","gif").contains(extension))
            throw new WebError(400,"IMAGE_TYPE_INVALID","Use a PNG, JPEG, WebP, or GIF image.");
        byte[] bytes;
        try{bytes=Base64.getDecoder().decode(encoded);}catch(IllegalArgumentException e){throw new WebError(400,"IMAGE_INVALID","The image data is invalid.");}
        if(bytes.length<8||bytes.length>6_291_456)throw new WebError(413,"IMAGE_TOO_LARGE","Choose an image of at most 6 MB.");
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        return idempotent(x,s,b,"design.attach",c->{
            var upload=CustomOrderMediaService.begin(c,orderId,lineId,s.locationId,"ATTACHMENT",filename,bytes.length,hash,s.userId,null,null,null);
            for(int offset=0;offset<bytes.length;offset+=1024*1024){
                byte[] chunk=java.util.Arrays.copyOfRange(bytes,offset,Math.min(bytes.length,offset+1024*1024));
                CustomOrderMediaService.chunk(c,upload.uploadId(),offset,chunk,s.userId,null);
            }
            var done=CustomOrderMediaService.finish(c,upload.uploadId(),s.locationId,s.userId,null);
            return Map.of("fileId",done.fileId().toString());
        });
    }
    private Object designPublish(HttpExchange x,Session s,JsonObject b)throws Exception{
        owner.requireMobileAny(s.userId,"MANAGE_CUSTOM_ORDERS");
        long orderId=b.get("orderId").getAsLong(),lineId=b.get("lineId").getAsLong();
        UUID documentId=UUID.fromString(text(b,"documentId",40));int revision=b.get("revision").getAsInt();
        String encoded=text(b,"pngBase64",12_000_000);byte[] png=Base64.getDecoder().decode(encoded);
        if(png.length<8||png.length>8_388_608||(png[0]&255)!=137||png[1]!=80||png[2]!=78||png[3]!=71)
            throw new WebError(400,"PREVIEW_INVALID","Choose a PNG preview under 8 MB.");
        StorefrontConfig config=StorefrontConfig.load();if(config==null)throw new WebError(503,"STOREFRONT_UNAVAILABLE","Configure the customer website before sending a design.");
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(png));
        return idempotent(x,s,b,"design.publish",c->{
            try(PreparedStatement p=c.prepareStatement("""
                    SELECT 1 FROM custom_order_design_documents d JOIN custom_orders o ON o.custom_order_id=d.custom_order_id
                    WHERE d.document_id=? AND d.custom_order_id=? AND d.custom_order_line_id=? AND d.revision=?
                      AND o.location_id=? AND o.status NOT IN ('DELIVERED','CANCELLED')
                    """)){
                p.setObject(1,documentId);p.setLong(2,orderId);p.setLong(3,lineId);p.setInt(4,revision);p.setInt(5,s.locationId);
                try(ResultSet r=p.executeQuery()){if(!r.next())throw new WebError(409,"DESIGN_CHANGED","Save the current line design before sending it.");}
            }
            var upload=CustomOrderMediaService.begin(c,orderId,lineId,s.locationId,"PROOF","design-preview.png",png.length,hash,s.userId,null,null,null);
            for(int offset=0;offset<png.length;offset+=1024*1024){byte[] chunk=java.util.Arrays.copyOfRange(png,offset,Math.min(png.length,offset+1024*1024));CustomOrderMediaService.chunk(c,upload.uploadId(),offset,chunk,s.userId,null);}
            var done=CustomOrderMediaService.finish(c,upload.uploadId(),s.locationId,s.userId,null);
            try(PreparedStatement p=c.prepareStatement("UPDATE custom_order_design_proofs SET document_revision=? WHERE proof_id=?")){
                p.setInt(1,revision);p.setObject(2,done.fileId());p.executeUpdate();
            }
            String url=config.origin()+"/shop/custom-order-proof?storeId="+s.locationId()+"#token="+done.proofToken();
            try(PreparedStatement p=c.prepareStatement("SELECT o.order_number,COALESCE(a.email,'') FROM custom_orders o LEFT JOIN customer_accounts a ON a.customer_id=o.customer_id WHERE o.custom_order_id=? AND o.location_id=?")){
                p.setLong(1,orderId);p.setInt(2,s.locationId);
                try(ResultSet r=p.executeQuery()){if(r.next()&&!r.getString(2).isBlank())ServerEmailOutboxService.queueCustomOrderDesignProof(c,s.locationId,r.getString(2),r.getString(1),done.revision(),url);}
            }
            return Map.of("approvalUrl",url,"revision",done.revision(),"proofId",done.fileId().toString());
        });
    }
    private Object barcode(Session s)throws Exception{owner.requireMobileAny(s.userId,"NEW_ITEM","EDIT_ITEM","MANAGE_CUSTOM_ORDER_ITEMS","CUSTOM_ORDER_ITEMS","MANAGE_CUSTOM_ORDERS");try(Connection c=DB.getConnection()){return Map.of("barcode",CatalogBarcodeService.generateAvailable(c));}}
    private Object barcodeScan(Session s,JsonObject b)throws Exception{
        owner.requireMobileAny(s.userId,"NEW_ITEM","EDIT_ITEM","MANAGE_CUSTOM_ORDER_ITEMS","CUSTOM_ORDER_ITEMS","MANAGE_CUSTOM_ORDERS");
        byte[] bytes;try{bytes=Base64.getDecoder().decode(text(b,"bytesBase64",12*1024*1024));}catch(IllegalArgumentException e){throw new WebError(400,"BARCODE_IMAGE_INVALID","The barcode photo is invalid.");}
        if(bytes.length>8*1024*1024)throw new WebError(413,"BARCODE_IMAGE_TOO_LARGE","The barcode photo must be 8 MB or smaller.");
        java.awt.image.BufferedImage image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));if(image==null)throw new WebError(400,"BARCODE_IMAGE_INVALID","Choose a valid barcode photo.");
        int[] pixels=image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());BinaryBitmap attempt=new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(image.getWidth(),image.getHeight(),pixels)));
        EnumMap<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);hints.put(DecodeHintType.TRY_HARDER,Boolean.TRUE);hints.put(DecodeHintType.ALSO_INVERTED,Boolean.TRUE);Result result=null;
        for(int rotation=0;rotation<4&&result==null;rotation++){try{result=new MultiFormatReader().decode(attempt,hints);}catch(com.google.zxing.NotFoundException ignored){}if(attempt.isRotateSupported())attempt=attempt.rotateCounterClockwise();else break;}
        if(result==null||result.getText()==null||result.getText().isBlank())throw new WebError(422,"BARCODE_NOT_FOUND","No barcode was found. Move closer, keep the label sharp, and try again.");
        return Map.of("barcode",result.getText().trim(),"format",result.getBarcodeFormat().name());
    }
    private Object imageUpload(HttpExchange x,Session s,JsonObject b)throws Exception{owner.requireMobileAny(s.userId,"NEW_ITEM","EDIT_ITEM","MANAGE_CUSTOM_ORDER_ITEMS","CUSTOM_ORDER_ITEMS","MANAGE_CUSTOM_ORDERS");byte[] bytes=Base64.getDecoder().decode(text(b,"bytesBase64",16*1024*1024));if(bytes.length>2*1024*1024)throw new WebError(413,"IMAGE_TOO_LARGE","The optimized image must be 2 MB or smaller.");bytes=optimizeJpeg(bytes);String requested=optional(b,"category").toUpperCase(java.util.Locale.ROOT),category=switch(requested){case"CUSTOM_ITEM","CUSTOM_VARIANT"->requested;default->"PRODUCT";};String name=StorageObjectNameBuilder.productImageFilename("image.jpg",StorageObjectNameBuilder.newProductImageToken(),optional(b,"productName"),optional(b,"brand"),optional(b,"type"),optional(b,"size"),optional(b,"variant"));try(Connection c=DB.getConnection()){String ref=ServerImageAssetService.storeUpload(c,category,"Product Images","products/"+name,"image/jpeg",name,"PUBLIC",bytes);return Map.of("reference",ref,"cloudStatus","PENDING");}}
    private Object imageFetch(Session s,JsonObject b)throws Exception{ServerImageAssetService.AssetBytes a=ServerImageAssetService.load(text(b,"reference",1000));return Map.of("contentType",a.contentType(),"bytesBase64",Base64.getEncoder().encodeToString(a.bytes()));}

    private Object studioBranding(Session s) throws Exception {
        return StudioBackgroundService.branding(s.locationId);
    }

    private Object studioRemoveBackground(Session s, JsonObject body) throws Exception {
        owner.requireMobileAny(s.userId,"NEW_ITEM","EDIT_ITEM","MANAGE_CUSTOM_ORDER_ITEMS","CUSTOM_ORDER_ITEMS","MANAGE_CUSTOM_ORDERS");
        try {
            byte[] bytes = Base64.getDecoder().decode(text(body,"bytesBase64",8*1024*1024));
            StudioQuality quality = StudioQuality.parse(optional(body,"quality"));
            boolean cleanEdges = body.has("cleanEdges") && body.get("cleanEdges").getAsBoolean();
            return Map.of("contentType","image/png","bytesBase64",Base64.getEncoder().encodeToString(StudioBackgroundService.remove(bytes,quality,cleanEdges)));
        } catch (IllegalArgumentException e) {
            throw new WebError(400,"IMAGE_INVALID",e.getMessage());
        } catch (StudioBackgroundService.BusyException e) {
            throw new WebError(429,"STUDIO_BUSY",e.getMessage());
        } catch (CatalogStudioImageProcessor.ModelUnavailableException e) {
            throw new WebError(503,"STUDIO_MODEL_UNAVAILABLE",e.getMessage());
        }
    }

    private Object studioCatalog(Session s,JsonObject b)throws Exception{
        owner.requireMobileAny(s.userId,"EDIT_ITEM");
        long after=b.has("afterId")?Math.max(0,b.get("afterId").getAsLong()):0;
        List<Map<String,Object>> products=new ArrayList<>();
        try(Connection c=DB.getConnection();PreparedStatement p=c.prepareStatement("SELECT product_id,name,COALESCE(image_url,''),additional_image_urls::text FROM products WHERE product_id>? AND is_active=TRUE AND COALESCE(image_url,'')<>'' AND sku<>'SMARTSTOCK-MISC' ORDER BY product_id LIMIT 100")){
            p.setLong(1,after);
            try(ResultSet r=p.executeQuery()){while(r.next()){
                Map<String,Object> row=new LinkedHashMap<>();row.put("productId",r.getLong(1));row.put("name",r.getString(2));row.put("imageUrl",r.getString(3));row.put("additionalImageUrls",CatalogPhotoGalleryService.parse(r.getString(4)));products.add(row);
            }}
        }
        return Map.of("products",products);
    }

    private Object studioImport(HttpExchange x,Session s,JsonObject b)throws Exception{
        owner.requireMobileAny(s.userId,"EDIT_ITEM");
        long id;try{id=b.get("productId").getAsLong();}catch(Exception e){throw new WebError(400,"VALIDATION_ERROR","A product ID is required.");}
        if(id<=0)throw new WebError(400,"VALIDATION_ERROR","A product ID is required.");
        String expected=text(b,"sourceImageUrl",4000);
        String digest=text(b,"sourceSha256",64).toLowerCase(java.util.Locale.ROOT);
        if(!digest.matches("[0-9a-f]{64}"))throw new WebError(400,"VALIDATION_ERROR","A valid source digest is required.");
        byte[] bytes;try{bytes=Base64.getDecoder().decode(text(b,"bytesBase64",16*1024*1024));}catch(IllegalArgumentException e){throw new WebError(400,"IMAGE_INVALID","The approved image is invalid.");}
        if(bytes.length>2*1024*1024)throw new WebError(413,"IMAGE_TOO_LARGE","The approved image must be 2 MB or smaller.");
        bytes=optimizeJpeg(bytes);
        final byte[] upload=bytes;
        return idempotent(x,s,b,"studio.import",c->{
            String primary,name;List<String> photos;
            try(PreparedStatement p=c.prepareStatement("SELECT name,image_url,additional_image_urls::text FROM products WHERE product_id=? AND is_active=TRUE FOR UPDATE")){
                p.setLong(1,id);try(ResultSet r=p.executeQuery()){
                    if(!r.next())throw new WebError(404,"PRODUCT_NOT_FOUND","This product is no longer active.");
                    name=r.getString(1);primary=r.getString(2);photos=new ArrayList<>(CatalogPhotoGalleryService.parse(r.getString(3)));
                }
            }
            if(!expected.equals(primary))throw new WebError(409,"SOURCE_CHANGED","The primary photo changed. Review this product again.");
            String marker="studio-"+id+"-"+digest.substring(0,16);
            String filename=marker+".jpg";
            try(PreparedStatement p=c.prepareStatement("SELECT asset_id FROM image_assets WHERE bucket_name='Product Images' AND object_path=? AND lifecycle_status<>'DELETED'")){
                p.setString(1,"products/"+filename);try(ResultSet r=p.executeQuery()){if(r.next()){
                    String existing=ImageAssetReference.format((UUID)r.getObject(1));
                    if(photos.contains(existing))return Map.of("status","skipped","reference",existing);
                    throw new WebError(409,"IMPORT_CONFLICT","A previous upload for this product needs review.");
                }}
            }
            if(photos.size()>=20)throw new WebError(409,"PHOTO_LIMIT","This product already has 20 additional photos.");
            String ref=ServerImageAssetService.storeUpload(c,"PRODUCT","Product Images","products/"+filename,"image/jpeg",filename,"PUBLIC",upload);
            photos.add(ref);CatalogPhotoGalleryService.save(c,"products","product_id",id,primary,photos);
            audit(c,"STUDIO_GALLERY_PHOTO_IMPORTED",s.userId,"Imported approved studio photo for product ID "+id);
            return Map.of("status","imported","reference",ref);
        });
    }

    public Map<String,Object> issuePhotoHandoff(String targetType,long targetId,int userId)throws Exception{
        String type=normalizePhotoTarget(targetType);PhotoTarget target=loadPhotoTarget(type,targetId);
        String token=LanSecurity.randomToken();UUID id=UUID.randomUUID();Instant expires=Instant.now().plus(Duration.ofMinutes(10));
        PhotoHandoff handoff=new PhotoHandoff(id,LanSecurity.sha256(token),type,targetId,target.name(),target.productName(),target.brand(),target.itemType(),target.variant(),userId,expires);
        purgePhotoHandoffs();photoHandoffsByToken.put(handoff.tokenHash,handoff);photoHandoffsById.put(id,handoff);
        return Map.of("handoffId",id.toString(),"url",url()+"photo.html?token="+java.net.URLEncoder.encode(token,StandardCharsets.UTF_8),"expiresAt",expires.toString(),"targetName",target.name());
    }

    public Map<String,Object> photoHandoffStatus(UUID id,int userId)throws Exception{
        purgePhotoHandoffs();PhotoHandoff h=photoHandoffsById.get(id);
        if(h==null||h.createdBy!=userId)throw new WebError(404,"PHOTO_HANDOFF_NOT_FOUND","This phone photo request is no longer available.");
        Map<String,Object> out=new LinkedHashMap<>();out.put("handoffId",h.id.toString());out.put("targetName",h.targetName);out.put("expiresAt",h.expiresAt.toString());out.put("uploaded",h.usedAt!=null);out.put("expired",h.expiresAt.isBefore(Instant.now()));out.put("reference",h.reference==null?"":h.reference);return out;
    }

    private Object photoTarget(JsonObject b)throws Exception{
        PhotoHandoff h=requirePhotoHandoff(text(b,"token",300));
        return Map.of("targetName",h.targetName,"targetType",h.targetType,"expiresAt",h.expiresAt.toString());
    }

    private Object photoUpload(JsonObject b)throws Exception{
        PhotoHandoff h=requirePhotoHandoff(text(b,"token",300));byte[] bytes;
        try{bytes=Base64.getDecoder().decode(text(b,"bytesBase64",16*1024*1024));}catch(IllegalArgumentException e){throw new WebError(400,"IMAGE_INVALID","Choose a valid photo.");}
        if(bytes.length>2*1024*1024)throw new WebError(413,"IMAGE_TOO_LARGE","The optimized image must be 2 MB or smaller.");bytes=optimizeJpeg(bytes);
        synchronized(h){
            if(h.usedAt!=null)throw new WebError(409,"PHOTO_HANDOFF_USED","A photo was already uploaded with this link.");
            if(!h.expiresAt.isAfter(Instant.now()))throw new WebError(410,"PHOTO_HANDOFF_EXPIRED","This photo link has expired. Create a new one on the computer.");
            String category="variant".equals(h.targetType)?"CUSTOM_VARIANT":"CUSTOM_ITEM";
            String name=StorageObjectNameBuilder.productImageFilename("image.jpg",StorageObjectNameBuilder.newProductImageToken(),h.productName,h.brand,h.itemType,"",h.variant);
            try(Connection c=DB.getConnection()){
                c.setAutoCommit(false);try{
                    ServerImageAssetService.ensureSchema(c);
                    String table="variant".equals(h.targetType)?"custom_order_item_variants":"custom_order_items";
                    String key="variant".equals(h.targetType)?"custom_variant_id":"custom_item_id";
                    String ref;
                    try(PreparedStatement p=c.prepareStatement("SELECT image_url,additional_image_urls::text FROM "+table+" WHERE "+key+"=? FOR UPDATE")){
                        p.setLong(1,h.targetId);
                        try(ResultSet row=p.executeQuery()){
                            if(!row.next())throw new WebError(404,"PHOTO_TARGET_NOT_FOUND","The saved custom item could not be found.");
                            String primary=row.getString(1);
                            List<String> photos=new ArrayList<>(CatalogPhotoGalleryService.parse(row.getString(2)));
                            if(primary!=null&&!primary.isBlank()&&photos.size()>=20)
                                throw new WebError(409,"PHOTO_LIMIT","This item already has 20 additional photos.");
                            ref=ServerImageAssetService.storeUpload(c,category,"Product Images","products/"+name,"image/jpeg",name,"PUBLIC",bytes);
                            if(primary==null||primary.isBlank()){
                                try(PreparedStatement update=c.prepareStatement("UPDATE "+table+" SET image_url=?,updated_at=CURRENT_TIMESTAMP WHERE "+key+"=?")){
                                    update.setString(1,ref);update.setLong(2,h.targetId);update.executeUpdate();
                                }
                            }else{
                                photos.add(ref);
                                CatalogPhotoGalleryService.save(c,table,key,h.targetId,primary,photos);
                            }
                        }
                    }
                    audit(c,"CUSTOM_ITEM_PHONE_PHOTO_UPLOADED",h.createdBy,"Uploaded phone photo for "+h.targetType+" ID "+h.targetId);c.commit();h.reference=ref;h.usedAt=Instant.now();return Map.of("uploaded",true,"reference",ref);
                }catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
            }
        }
    }

    private PhotoHandoff requirePhotoHandoff(String token)throws WebError{
        purgePhotoHandoffs();PhotoHandoff h=photoHandoffsByToken.get(LanSecurity.sha256(token));
        if(h==null)throw new WebError(404,"PHOTO_HANDOFF_INVALID","This photo link is invalid or no longer available.");
        if(h.usedAt!=null)throw new WebError(409,"PHOTO_HANDOFF_USED","A photo was already uploaded with this link.");
        if(!h.expiresAt.isAfter(Instant.now()))throw new WebError(410,"PHOTO_HANDOFF_EXPIRED","This photo link has expired. Create a new one on the computer.");return h;
    }

    private PhotoTarget loadPhotoTarget(String type,long id)throws Exception{
        String sql="variant".equals(type)?"SELECT i.item_name||' - '||v.variant_name,i.item_name,COALESCE(b.name,''),COALESCE(t.name,''),CONCAT_WS(' / ',COALESCE(NULLIF(v.size,''),NULLIF(i.size,'')),COALESCE(NULLIF(v.color,''),NULLIF(i.color,''))) FROM custom_order_item_variants v JOIN custom_order_items i ON i.custom_item_id=v.custom_item_id LEFT JOIN item_brands b ON b.brand_id=i.brand_id LEFT JOIN item_types t ON t.item_type_id=i.item_type_id WHERE v.custom_variant_id=? AND i.is_active=TRUE AND v.is_active=TRUE":"SELECT i.item_name,i.item_name,COALESCE(b.name,''),COALESCE(t.name,''),CONCAT_WS(' / ',NULLIF(i.size,''),NULLIF(i.color,'')) FROM custom_order_items i LEFT JOIN item_brands b ON b.brand_id=i.brand_id LEFT JOIN item_types t ON t.item_type_id=i.item_type_id WHERE i.custom_item_id=? AND i.is_active=TRUE";
        try(Connection c=DB.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setLong(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new WebError(404,"PHOTO_TARGET_NOT_FOUND","Select a saved, active custom item or variant.");return new PhotoTarget(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5));}}
    }

    private static String normalizePhotoTarget(String value)throws WebError{String v=value==null?"":value.trim().toLowerCase(java.util.Locale.ROOT);if(!"item".equals(v)&&!"variant".equals(v))throw new WebError(400,"PHOTO_TARGET_INVALID","Choose a custom item or variant.");return v;}
    private void purgePhotoHandoffs(){Instant cutoff=Instant.now().minus(Duration.ofHours(1));photoHandoffsById.values().removeIf(h->{boolean remove=h.expiresAt.isBefore(cutoff);if(remove)photoHandoffsByToken.remove(h.tokenHash,h);return remove;});}

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
    private static List<Map<String,Object>> itemTypeRows(Connection c)throws Exception{List<Map<String,Object>> out=new ArrayList<>();try(var p=c.prepareStatement("SELECT item_type_id,category_id,name FROM item_types ORDER BY name");var r=p.executeQuery()){while(r.next())out.add(Map.of("id",r.getInt(1),"categoryId",r.getInt(2),"name",r.getString(3)));}return out;}
    private void audit(Connection c,String type,Integer userId,String details)throws Exception{try(PreparedStatement p=c.prepareStatement("INSERT INTO security_audit_events(event_type,device_id,actor_user_id,details) VALUES(?,?,?,?)")){p.setString(1,type);p.setObject(2,mobileDeviceId(c,DatabaseConfig.load().locationId()));if(userId==null)p.setNull(3,java.sql.Types.INTEGER);else p.setInt(3,userId);p.setString(4,details);p.executeUpdate();}}
    static UUID mobileDeviceId(Connection c,Integer locationId)throws Exception{
        String installationId="smartstock-mobile-item-web:"+DeviceUtils.collectDeviceInfo().getInstallationId();
        try(PreparedStatement p=c.prepareStatement("""
            INSERT INTO devices(installation_id,device_name,hostname,os_name,last_store_id,is_approved,is_blocked,
              allow_sales,allow_orders,access_mode,credential_status,status_notes)
            VALUES (?,'WEB APP','SmartStock Mobile Item Web App','WEB',?,TRUE,FALSE,FALSE,FALSE,'CLIENT','REVOKED',
              'Server-owned virtual identity for Mobile Item Web App activity; not an enrolled browser or register.')
            ON CONFLICT(installation_id) DO UPDATE SET device_name='WEB APP',hostname='SmartStock Mobile Item Web App',
              os_name='WEB',last_store_id=COALESCE(EXCLUDED.last_store_id,devices.last_store_id),is_approved=TRUE,
              is_blocked=FALSE,allow_sales=FALSE,allow_orders=FALSE,credential_status='REVOKED',
              status_notes=EXCLUDED.status_notes,last_seen=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
            RETURNING device_id
            """)){p.setString(1,installationId);if(locationId==null||locationId<=0)p.setNull(2,java.sql.Types.INTEGER);else p.setInt(2,locationId);try(ResultSet r=p.executeQuery()){if(!r.next())throw new java.sql.SQLException("The WEB APP device identity could not be created.");return (UUID)r.getObject(1);}}
    }
    static byte[] optimizeSpoilPhoto(byte[] input)throws Exception{
        if(input==null||input.length==0||input.length>2*1024*1024)throw new IllegalArgumentException("Photo must be 2 MB or smaller.");
        try(var stream=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(input))){
            var readers=javax.imageio.ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IllegalArgumentException("Choose a JPEG or PNG photo.");
            var reader=readers.next();try{reader.setInput(stream);String format=reader.getFormatName();int w=reader.getWidth(0),h=reader.getHeight(0);
                if((!format.equalsIgnoreCase("JPEG")&&!format.equalsIgnoreCase("PNG"))||w<1||h<1||w>6000||h>6000||(long)w*h>24000000)throw new IllegalArgumentException("Choose a JPEG or PNG photo up to 24 megapixels.");
            }finally{reader.dispose();}
        }
        return optimizeJpeg(input);
    }
    private static byte[] optimizeJpeg(byte[] input)throws Exception{java.awt.image.BufferedImage source=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(input));if(source==null)throw new WebError(400,"IMAGE_INVALID","Choose a valid JPEG or PNG image.");int largest=Math.max(source.getWidth(),source.getHeight());if(isJpeg(input)&&largest<=1200)return input;double scale=Math.min(1d,1200d/largest);int w=Math.max(1,(int)Math.round(source.getWidth()*scale)),h=Math.max(1,(int)Math.round(source.getHeight()*scale));java.awt.image.BufferedImage output=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g=output.createGraphics();try{g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,w,h);g.drawImage(source,0,0,w,h,null);}finally{g.dispose();}ByteArrayOutputStream bytes=new ByteArrayOutputStream();var writers=javax.imageio.ImageIO.getImageWritersByFormatName("jpeg");var writer=writers.next();try(var stream=javax.imageio.ImageIO.createImageOutputStream(bytes)){writer.setOutput(stream);var param=writer.getDefaultWriteParam();param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);param.setCompressionQuality(.78f);writer.write(null,new javax.imageio.IIOImage(output,null,null),param);}finally{writer.dispose();}return bytes.toByteArray();}
    private static boolean isJpeg(byte[] bytes){return bytes.length>=3&&(bytes[0]&255)==0xff&&(bytes[1]&255)==0xd8&&(bytes[2]&255)==0xff;}
    private static JsonObject readJson(HttpExchange x)throws Exception{if(!"POST".equals(x.getRequestMethod()))throw new WebError(405,"METHOD_NOT_ALLOWED","POST is required.");byte[] bytes=readLimited(x.getRequestBody(),MAX_JSON);if(bytes.length==0)return new JsonObject();return JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static byte[] readLimited(InputStream in,int max)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n,total=0;while((n=in.read(buf))>=0){total+=n;if(total>max)throw new WebError(413,"REQUEST_TOO_LARGE","The request is too large.");out.write(buf,0,n);}return out.toByteArray();}
    private void cors(HttpExchange x)throws WebError{String origin=x.getRequestHeaders().getFirst("Origin"),allowed="https://"+host+":"+UI_PORT;if(origin!=null&&!origin.equalsIgnoreCase(allowed))throw new WebError(403,"ORIGIN_DENIED","This web origin is not allowed.");Headers h=x.getResponseHeaders();h.set("Access-Control-Allow-Origin",origin==null?allowed:origin);h.set("Access-Control-Allow-Credentials","true");h.set("Access-Control-Allow-Headers","Content-Type,X-CSRF-Token,Idempotency-Key");h.set("Access-Control-Allow-Methods","POST,OPTIONS");h.set("Vary","Origin");security(h);}
    private static void security(Headers h){h.set("Cache-Control","no-store");h.set("X-Content-Type-Options","nosniff");h.set("X-Frame-Options","DENY");h.set("Referrer-Policy","no-referrer");h.set("Content-Security-Policy","default-src 'self'; connect-src https:; img-src 'self' blob: data:; style-src 'self'; script-src 'self'; base-uri 'none'; frame-ancestors 'none'");}
    static boolean allowsLocalWebPeer(InetAddress address, boolean forwarded) {
        return address != null && !forwarded && (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress());
    }
    private static void requireLan(HttpExchange x)throws WebError{InetAddress a=x.getRemoteAddress().getAddress();if(!allowsLocalWebPeer(a, x.getRequestHeaders().containsKey("Forwarded") || x.getRequestHeaders().containsKey("X-Forwarded-For")))throw new WebError(403,"LAN_ONLY","This app is available only on the store network.");}
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
    private record PhotoTarget(String name,String productName,String brand,String itemType,String variant){}
    private static final class PhotoHandoff{final UUID id;final String tokenHash,targetType;final long targetId;final String targetName,productName,brand,itemType,variant;final int createdBy;final Instant expiresAt;volatile Instant usedAt;volatile String reference;PhotoHandoff(UUID id,String tokenHash,String targetType,long targetId,String targetName,String productName,String brand,String itemType,String variant,int createdBy,Instant expiresAt){this.id=id;this.tokenHash=tokenHash;this.targetType=targetType;this.targetId=targetId;this.targetName=targetName;this.productName=productName;this.brand=brand;this.itemType=itemType;this.variant=variant;this.createdBy=createdBy;this.expiresAt=expiresAt;}}
    private interface Work{Object run(Connection connection)throws Exception;}
    private static final class WebError extends Exception{final int status;final String code;WebError(int status,String code,String message){super(message);this.status=status;this.code=code;}}
}
