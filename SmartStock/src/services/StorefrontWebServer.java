package services;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import data.DB;
import data.DatabaseConfig;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static services.StorefrontService.*;

/** Public shopping surface; only a trusted loopback Cloudflare connector can reach it. */
public final class StorefrontWebServer implements AutoCloseable {
    private final HttpsServer server;private final ExecutorService pool;private final StorefrontConfig config;
    private final EmploymentPortalCloud auth=new EmploymentPortalCloud();
    private final Map<String,long[]> attempts=new HashMap<>();
    private StorefrontWebServer(HttpsServer server,ExecutorService pool,StorefrontConfig config){this.server=server;this.pool=pool;this.config=config;}
    static StorefrontWebServer start(StorefrontConfig config)throws Exception{
        var server=HttpsServer.create(new InetSocketAddress("127.0.0.1",config.port()),32);
        var pool=new ThreadPoolExecutor(8,8,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),r->{var t=new Thread(r,"storefront-http");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
        try {server.setHttpsConfigurator(new HttpsConfigurator(LanTlsIdentity.loadOrCreate().sslContext()));server.setExecutor(pool);var app=new StorefrontWebServer(server,pool,config);String installerKey=InstallerDownloadAuthService.originKey(config.edgeKey());
            server.createContext("/v1/installer/authenticate",x->{try{InstallerDownloadAuthService.handle(x,installerKey,LanApiServer::verifyInstallerPassword);}finally{WebRuntimeMetrics.record("website",x);}});
            server.createContext("/",x->{try{app.handle(x);}finally{WebRuntimeMetrics.record("website",x);}});
            server.start();WebRuntimeMetrics.started("website");return app;}
        catch(Exception e){pool.shutdownNow();server.stop(0);throw e;}
    }
    private synchronized boolean allow(String key){long now=System.currentTimeMillis();attempts.entrySet().removeIf(e->e.getValue()[0]<now);if(attempts.size()>4096)return false;long[] a=attempts.computeIfAbsent(key,k->new long[]{now+600000,0});return ++a[1]<=40;}
    static boolean browseOnlyAllows(String route){return "catalog".equals(route);}
    static void markBrowseOnly(JsonObject publicCatalog){
        publicCatalog.addProperty("browseOnly",true);
        if(publicCatalog.has("settings")&&publicCatalog.get("settings").isJsonObject())
            publicCatalog.getAsJsonObject("settings").addProperty("enabled",false);
        for(var item:publicCatalog.getAsJsonArray("products"))item.getAsJsonObject().addProperty("canOrder",false);
        if(publicCatalog.has("campaign")&&publicCatalog.get("campaign").isJsonObject()){
            var campaign=publicCatalog.getAsJsonObject("campaign");
            if("3D Printing".equals(campaign.has("topic")?campaign.get("topic").getAsString():"")){
                campaign.addProperty("description","Discover what Deckers can create with 3D printing. Ask your store about current availability.");
                campaign.addProperty("steps","Bring your idea · Discuss the details · Plan the print");
                campaign.addProperty("primary","Find your store");
                campaign.addProperty("primaryAction","START");
            }
        }
    }
    private void handle(HttpExchange x){
        try{
            if(!x.getRemoteAddress().getAddress().isLoopbackAddress()||!LanSecurity.constantTimeEquals(config.edgeKey(),Objects.toString(x.getRequestHeaders().getFirst("X-Storefront-Key"),""))){send(x,403,Map.of("message","Use the Deckers website."));return;}
            if(!config.instanceId().equals(x.getRequestHeaders().getFirst("X-Storefront-Instance"))){send(x,403,Map.of("message","Store server identity does not match."));return;}
            if(ServerRoleGuard.state()!=ServerRoleGuard.State.PRIMARY){send(x,503,Map.of("message","This server is unavailable."));return;}
            String path=x.getRequestURI().getPath();
            if(path.equals("/shop/internal/resolve-command")){
                if(config.browseOnly()){send(x,503,Map.of("message","Order commands are disabled during browse-only launch."));return;}
                if(!"POST".equals(x.getRequestMethod())){send(x,405,Map.of("message","POST required."));return;}
                byte[] bytes=x.getRequestBody().readNBytes(1025);if(bytes.length>1024){send(x,413,Map.of("message","Request too large."));return;}
                JsonObject command=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
                try(Connection c=DB.getConnection()){
                    c.setAutoCommit(false);try{StorefrontRuntime.requirePrimary();JsonObject result=resolveCommand(c,UUID.fromString(text(command,"orderId")),integer(command,"storeId"));StorefrontRuntime.requirePrimary();c.commit();send(x,200,result);}catch(Exception e){c.rollback();throw e;}
                }return;
            }
            if(path.equals("/shop/health")){
                Map<String,Long> snapshots=new HashMap<>();try(Connection c=DB.getConnection()){for(var e:rows(c,"SELECT location_id,captured_at FROM storefront.snapshots")){var s=e.getAsJsonObject();snapshots.put(text(s,"location_id"),Instant.parse(text(s,"captured_at")).toEpochMilli());}}
                send(x,200,Map.of("ok",true,"version",1,"instanceId",config.instanceId(),"locationId",DatabaseConfig.load().locationId(),"snapshots",snapshots));return;
            }
            if(path.equals("/shop/api/v1/custom-order-private")){
                privateOrderApi(x);return;
            }
            if(path.startsWith("/shop/api/v1/")){
                if(!"POST".equals(x.getRequestMethod())){send(x,405,Map.of("message","POST required."));return;}
                if(!config.origin().equals(x.getRequestHeaders().getFirst("Origin"))||!"same-origin".equals(x.getRequestHeaders().getFirst("X-Storefront-Request"))){send(x,403,Map.of("message","Refresh the website before continuing."));return;}
                byte[] bytes=x.getRequestBody().readNBytes(8*1024*1024+1);if(bytes.length>8*1024*1024){send(x,413,Map.of("message","Request too large."));return;}
                JsonObject body=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();String route=path.substring("/shop/api/v1/".length());
                if(config.browseOnly()&&!browseOnlyAllows(route)){send(x,503,Map.of("message","Online accounts and requests are not available yet. Contact your Deckers store."));return;}
                if(route.startsWith("auth/")){auth(x,route.substring(5),body);return;}
                int location=integer(body,"storeId");
                try(Connection c=DB.getConnection()){
                    JsonObject s=body.has("_snapshot")?body.getAsJsonObject("_snapshot"):cached(c,location);
                    if(integer(s,"locationId")!=location)throw new IllegalArgumentException("Store selection does not match the catalog.");
                    if(route.equals("catalog")){JsonObject publicCatalog=catalog(s);publicCatalog.addProperty("servingBackup",location!=DatabaseConfig.load().locationId());if(config.browseOnly())markBrowseOnly(publicCatalog);send(x,200,publicCatalog);return;}
                    JsonObject session=session(x,true);String email=text(session,"email");UUID user=UUID.fromString(text(session,"id"));
                    if(route.equals("quote-template")){
                        if(location!=DatabaseConfig.load().locationId())throw new IllegalArgumentException("Choose this store to repeat a custom request.");
                        send(x,200,StorefrontQuoteRequests.template(c,location,user,UUID.fromString(text(body,"requestId"))));return;
                    }
                    if(route.equals("custom-quote")){
                        if(location!=DatabaseConfig.load().locationId())throw new IllegalArgumentException("Choose this store to send a custom request.");
                        c.setAutoCommit(false);try{
                            StorefrontRuntime.requirePrimary();
                            JsonObject result=StorefrontQuoteRequests.submit(c,location,user,email,body);
                            StorefrontRuntime.requirePrimary();c.commit();send(x,200,result);
                        }catch(Exception e){c.rollback();throw e;}return;
                    }
                    if(route.equals("custom-quote-file")){
                        if(location!=DatabaseConfig.load().locationId())throw new IllegalArgumentException("Choose this store to upload artwork.");
                        c.setAutoCommit(false);try{
                            StorefrontRuntime.requirePrimary();
                            JsonObject result=StorefrontQuoteRequests.upload(c,location,user,body);
                            StorefrontRuntime.requirePrimary();c.commit();send(x,200,result);
                        }catch(Exception e){c.rollback();throw e;}return;
                    }
                    if(route.equals("proof-file")){
                        if(location!=DatabaseConfig.load().locationId())throw new IllegalArgumentException("Choose the proof's store.");
                        send(x,200,StorefrontQuoteProofs.customerFile(c,location,user,UUID.fromString(text(body,"proofId"))));return;
                    }
                    if(route.equals("proof-decision")){
                        if(location!=DatabaseConfig.load().locationId())throw new IllegalArgumentException("Choose the proof's store.");
                        c.setAutoCommit(false);try{
                            StorefrontRuntime.requirePrimary();
                            JsonObject result=StorefrontQuoteProofs.decide(c,location,user,body);
                            StorefrontRuntime.requirePrimary();c.commit();send(x,200,result);
                        }catch(Exception e){c.rollback();throw e;}return;
                    }
                    if(route.equals("quote")){send(x,200,quote(s,body,user,email));return;}
                    if(route.equals("account")){if(location==DatabaseConfig.load().locationId())s=snapshot(c,location);send(x,200,account(c,s,user,email));return;}
                    if(route.equals("favorite-state")){
                        if(location!=DatabaseConfig.load().locationId())throw new IllegalArgumentException("Choose this store to view saved products.");
                        send(x,200,Map.of("favorites",StorefrontFavorites.list(c,location,user)));return;
                    }
                    if(route.equals("favorite")){
                        if(location!=DatabaseConfig.load().locationId())throw new IllegalArgumentException("Choose this store to save a product.");
                        if(!body.has("saved")||!body.get("saved").isJsonPrimitive()||!body.getAsJsonPrimitive("saved").isBoolean())throw new IllegalArgumentException("Choose whether to save the product.");
                        c.setAutoCommit(false);try{
                            StorefrontRuntime.requirePrimary();
                            lock(c,location);
                            JsonArray favorites=StorefrontFavorites.set(c,location,user,integer(body,"productId"),body.get("saved").getAsBoolean());
                            StorefrontRuntime.requirePrimary();c.commit();send(x,200,Map.of("favorites",favorites));
                        }catch(Exception e){c.rollback();throw e;}return;
                    }
                    if(route.equals("checkout")){
                        c.setAutoCommit(false);try{StorefrontRuntime.requirePrimary();var o=checkout(c,s,body,user,email,DatabaseConfig.load().locationId());StorefrontRuntime.requirePrimary();c.commit();send(x,200,o);}catch(Exception e){c.rollback();throw e;}return;
                    }
                    send(x,404,Map.of("message","Not found."));return;
                }
            }
            if(!"GET".equals(x.getRequestMethod())){send(x,405,Map.of("message","GET required."));return;}
            if(path.equals("/shop/custom-order-portal.js")||path.equals("/shop/custom-order-portal.css")){
                String resource=path.endsWith(".js")?"custom-order-portal/portal.js":"custom-order-portal/portal.css";
                try(var in=getClass().getClassLoader().getResourceAsStream(resource)){
                    if(in==null){send(x,404,Map.of("message","Not found."));return;}
                    respond(x,200,resource.endsWith(".js")?"text/javascript; charset=utf-8":"text/css; charset=utf-8",in.readAllBytes());
                }return;
            }
            if(path.equals("/shop/custom-order-files")||path.equals("/shop/custom-order-proof")){
                x.getResponseHeaders().set("Referrer-Policy","no-referrer");
                x.getResponseHeaders().set("Cache-Control","no-store");
                x.getResponseHeaders().set("Content-Security-Policy","default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' blob:; frame-src blob:; connect-src 'self'; base-uri 'none'; form-action 'none'");
                try(var in=getClass().getClassLoader().getResourceAsStream("custom-order-portal/index.html")){
                    if(in==null){send(x,503,Map.of("message","The private order page is unavailable."));return;}
                    respond(x,200,"text/html; charset=utf-8",in.readAllBytes());
                }return;
            }
            if(path.equals("/shop/image")){image(x);return;}
            if(path.equals("/shop/project-qr")){projectQr(x);return;}
            if(path.equals("/shop/sitemap.xml")){sitemap(x);return;}
            if(path.startsWith("/shop/projects/")){projectPage(x,path);return;}
            if(path.startsWith("/shop/products/")){productPage(x,path);return;}
            String editorial=path.startsWith("/shop/")?path.substring("/shop/".length()).replaceFirst("/$",""):"";
            if(StorefrontEditorialPage.has(editorial)){
                try(var in=getClass().getClassLoader().getResourceAsStream("storefront-web/index.html")){
                    if(in==null){send(x,503,Map.of("message","The storefront bundle is not installed."));return;}
                    respond(x,200,"text/html",StorefrontEditorialPage.render(new String(in.readAllBytes(),StandardCharsets.UTF_8),config.origin(),editorial).getBytes(StandardCharsets.UTF_8));
                }
                return;
            }
            if(path.startsWith("/shop/services/")){
                String slug=path.substring("/shop/services/".length()).replaceFirst("/$","");
                if(!StorefrontServicePage.has(slug)){send(x,404,Map.of("message","Not found."));return;}
                try(var in=getClass().getClassLoader().getResourceAsStream("storefront-web/index.html")){
                    if(in==null){send(x,503,Map.of("message","The storefront bundle is not installed."));return;}
                    respond(x,200,"text/html",StorefrontServicePage.render(new String(in.readAllBytes(),StandardCharsets.UTF_8),config.origin(),slug).getBytes(StandardCharsets.UTF_8));
                }
                return;
            }
            String file=path.startsWith("/shop/assets/")?path.substring("/shop/".length()):path.equals("/shop/favicon.svg")?"favicon.svg":path.equals("/shop/hero-3d.jpg")?"hero-3d.jpg":path.equals("/shop/services-editorial.jpg")?"services-editorial.jpg":path.equals("/shop/new-3d.jpg")?"new-3d.jpg":path.equals("/shop/new-apparel.jpg")?"new-apparel.jpg":"index.html";
            if(file.contains("..")||!file.matches("[a-zA-Z0-9_./-]+")){send(x,404,Map.of("message","Not found."));return;}
            try(var in=getClass().getClassLoader().getResourceAsStream("storefront-web/"+file)){
                if(in==null){send(x,503,Map.of("message","The storefront bundle is not installed."));return;}
                respond(x,200,file.endsWith(".js")?"text/javascript":file.endsWith(".css")?"text/css":file.endsWith(".svg")?"image/svg+xml":file.endsWith(".jpg")?"image/jpeg":"text/html",in.readAllBytes());
            }
        }catch(EmploymentPortalCloud.AuthFailure e){safe(x,401,e.getMessage());}
        catch(IllegalArgumentException|JsonParseException e){safe(x,400,e instanceof JsonParseException?"Invalid request.":e.getMessage());}
        catch(Exception e){safe(x,503,"The store could not complete this request. Your bag is saved. Please retry.");}
        finally{x.close();}
    }
    private void privateOrderApi(HttpExchange x)throws Exception{
        if(!"POST".equals(x.getRequestMethod())){send(x,405,Map.of("message","POST required."));return;}
        if(!config.origin().equals(x.getRequestHeaders().getFirst("Origin"))||!"same-origin".equals(x.getRequestHeaders().getFirst("X-Storefront-Request"))){send(x,403,Map.of("message","Refresh the private order page."));return;}
        byte[] bytes=x.getRequestBody().readNBytes(1_500_001);if(bytes.length>1_500_000){send(x,413,Map.of("message","Upload chunk is too large."));return;}
        JsonObject body=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
        int location=integer(body,"storeId");if(location!=DatabaseConfig.load().locationId()){send(x,404,Map.of("message","Order unavailable."));return;}
        String token=text(body,"token"),action=text(body,"action");
        if(!token.matches("[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException("Private link is invalid.");
        boolean proof=action.startsWith("PROOF_");
        try(Connection c=DB.getConnection()){
            c.setAutoCommit(false);try{
                StorefrontRuntime.requirePrimary();
                Object response;
                if(proof){
                    String sql="SELECT p.proof_id,p.custom_order_id,p.custom_order_line_id,p.revision,p.filename,p.content_type,p.byte_size,p.storage_key,p.status,COALESCE(p.feedback,'') FROM custom_order_design_proofs p JOIN custom_orders o ON o.custom_order_id=p.custom_order_id WHERE p.token_sha256=? AND p.revoked_at IS NULL AND p.deleted_at IS NULL AND (p.decided_at IS NULL OR (p.status='APPROVED' AND p.decided_at>now()-interval '3 months') OR (p.status<>'APPROVED' AND p.decided_at>now()-interval '30 days')) AND o.location_id=?";
                    UUID proofId;long orderId,lineId,size;String filename,mime,key,status,feedback;int revision;
                    try(var p=c.prepareStatement(sql)){p.setString(1,CustomOrderMediaService.tokenHash(token));p.setInt(2,location);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Preview link is no longer available.");proofId=r.getObject(1,UUID.class);orderId=r.getLong(2);lineId=r.getLong(3);revision=r.getInt(4);filename=r.getString(5);mime=r.getString(6);size=r.getLong(7);key=r.getString(8);status=r.getString(9);feedback=r.getString(10);}}
                    switch(action){
                        case "PROOF_DETAILS"->response=Map.of("proofId",proofId.toString(),"orderId",orderId,"lineId",lineId,"revision",revision,"filename",filename,"contentType",mime,"bytes",size,"status",status,"feedback",feedback);
                        case "PROOF_CHUNK"->response=privateFileChunk(body,key,size,filename,mime);
                        case "PROOF_DECIDE"->{var decision=CustomOrderMediaService.decide(c,token,location,text(body,"decision"),text(body,"reason"),text(body,"changes"));response=Map.of("proofId",decision.proofId().toString(),"status",decision.status());}
                        default->throw new IllegalArgumentException("Unknown preview action.");
                    }
                }else{
                    long orderId=CustomOrderMediaService.orderForAccessLink(c,token,location);UUID linkId=CustomOrderMediaService.accessLinkId(c,token,location);
                    switch(action){
                        case "LIST"->{JsonObject result=new JsonObject();result.add("lines",rows(c,"SELECT custom_order_line_id AS \"lineId\",item_name AS name,COALESCE(variant_name,'') AS variant FROM custom_order_lines WHERE custom_order_id=? ORDER BY sort_order,custom_order_line_id",orderId));result.add("files",rows(c,"SELECT f.file_id AS id,f.custom_order_line_id AS \"lineId\",f.filename,f.content_type AS \"contentType\",f.byte_size AS bytes,f.created_at AS \"createdAt\" FROM custom_order_files f JOIN custom_orders o ON o.custom_order_id=f.custom_order_id WHERE f.custom_order_id=? AND f.removed_at IS NULL AND f.deleted_at IS NULL AND (o.status<>'DELIVERED' OR o.delivered_at>now()-interval '30 days') ORDER BY f.created_at",orderId));JsonObject order=one(rows(c,"SELECT order_number AS \"orderNumber\",status FROM custom_orders WHERE custom_order_id=?",orderId));result.addProperty("orderNumber",text(order,"orderNumber"));result.addProperty("status",text(order,"status"));result.addProperty("limitBytes",CustomOrderMediaService.limit(c,location));response=result;}
                        case "BEGIN"->{var started=CustomOrderMediaService.begin(c,orderId,Long.parseLong(text(body,"lineId")),location,"ATTACHMENT",text(body,"filename"),Long.parseLong(text(body,"size")),text(body,"sha256"),null,linkId,null,null,UUID.fromString(text(body,"uploadId")));response=Map.of("uploadId",started.uploadId().toString(),"limitBytes",started.limitBytes());}
                        case "CHUNK"->{byte[] data=Base64.getDecoder().decode(text(body,"bytesBase64"));long received=CustomOrderMediaService.chunk(c,UUID.fromString(text(body,"uploadId")),Long.parseLong(text(body,"offset")),data,null,linkId);response=Map.of("receivedBytes",received);}
                        case "FINISH"->{var done=CustomOrderMediaService.finish(c,UUID.fromString(text(body,"uploadId")),location,null,linkId);response=Map.of("fileId",done.fileId().toString());}
                        case "FILE_CHUNK"->{String sql="SELECT f.storage_key,f.filename,f.content_type,f.byte_size FROM custom_order_files f JOIN custom_orders o ON o.custom_order_id=f.custom_order_id WHERE f.file_id=? AND f.custom_order_id=? AND f.removed_at IS NULL AND f.deleted_at IS NULL AND (o.status<>'DELIVERED' OR o.delivered_at>now()-interval '30 days')";try(var p=c.prepareStatement(sql)){p.setObject(1,UUID.fromString(text(body,"fileId")));p.setLong(2,orderId);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("File is unavailable.");response=privateFileChunk(body,r.getString(1),r.getLong(4),r.getString(2),r.getString(3));}}}
                        default->throw new IllegalArgumentException("Unknown private order action.");
                    }
                }
                StorefrontRuntime.requirePrimary();c.commit();x.getResponseHeaders().set("Cache-Control","no-store");send(x,200,response);
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    private static Map<String,Object> privateFileChunk(JsonObject body,String key,long size,String filename,String mime)throws Exception{
        long offset=Long.parseLong(text(body,"offset"));if(offset<0||offset>=size)throw new IllegalArgumentException("Invalid file range.");
        int length=(int)Math.min(524288,size-offset);byte[] data=new byte[length];
        try(var channel=java.nio.channels.FileChannel.open(CustomOrderMediaService.localFile(key),java.nio.file.StandardOpenOption.READ)){
            java.nio.ByteBuffer buffer=java.nio.ByteBuffer.wrap(data);while(buffer.hasRemaining()&&channel.read(buffer,offset+buffer.position())>0){}if(buffer.hasRemaining())throw new java.io.IOException("File could not be read.");
        }
        return Map.of("bytesBase64",Base64.getEncoder().encodeToString(data),"filename",filename,"contentType",mime,"totalBytes",size);
    }
    private void auth(HttpExchange x,String route,JsonObject body)throws Exception{
        if(route.equals("session")){JsonObject s=session(x,false);send(x,200,Map.of("csrf",text(s,"csrf"),"email",text(s,"email"),"id",text(s,"id"),"issuedAt",s.get("issuedAt").getAsLong()));return;}
        if(route.equals("logout")){JsonObject s=session(x,true);auth.auth("logout?scope=local",text(s,"access"),new JsonObject());cookie(x,"",0);send(x,200,Map.of("ok",true));return;}
        String email=text(body,"email").trim().toLowerCase(Locale.ROOT);
        if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||email.length()>254)throw new IllegalArgumentException("Enter a valid email address.");
        if(!allow("ip:"+Objects.toString(x.getRequestHeaders().getFirst("X-Real-IP"),"gateway"))||!allow("email:"+LanSecurity.sha256(email))){send(x,429,Map.of("message","Too many attempts. Try again in ten minutes."));return;}
        JsonObject q=new JsonObject();q.addProperty("email",email);
        if(route.equals("activate")){q.addProperty("create_user",true);auth.auth("otp",null,q);send(x,200,Map.of("message","Check your email for your verification code."));return;}
        if(route.equals("recover")){auth.auth("recover",null,q);send(x,200,Map.of("message","If this email has an account, a recovery code has been sent."));return;}
        JsonObject tokens;
        if(route.equals("verify")||route.equals("reset")){
            String password=text(body,"password");if(password.length()<12||password.length()>128)throw new IllegalArgumentException("Use a password with 12 to 128 characters.");
            q.addProperty("token",text(body,"code"));q.addProperty("type",route.equals("reset")?"recovery":"email");tokens=auth.auth("verify",null,q);
            JsonObject change=new JsonObject();change.addProperty("password",password);auth.auth("user",text(tokens,"access_token"),change);
        }else if(route.equals("login")){q.addProperty("password",text(body,"password"));tokens=auth.auth("token?grant_type=password",null,q);}
        else throw new IllegalArgumentException("Unknown account action.");
        var user=auth.auth("user",text(tokens,"access_token"),null);if(text(user,"email_confirmed_at").isBlank())throw new EmploymentPortalCloud.AuthFailure("Verify your email first.");
        try(Connection c=DB.getConnection()){
            int location=integer(body,"storeId");cached(c,location); // Only enroll at a known published store.
            c.setAutoCommit(false);
            try{StorefrontRuntime.requirePrimary();StorefrontEnrollmentService.enroll(c,location,UUID.fromString(text(user,"id")),text(user,"email"),text(body,"name"),DatabaseConfig.load().locationId());StorefrontRuntime.requirePrimary();c.commit();}
            catch(Exception e){c.rollback();throw e;}
        }
        JsonObject s=new JsonObject();s.addProperty("id",text(user,"id"));s.addProperty("email",text(user,"email"));s.addProperty("access",text(tokens,"access_token"));s.addProperty("csrf",LanSecurity.randomToken());s.addProperty("expires",Instant.now().getEpochSecond()+Math.min(3600,integer(tokens,"expires_in")));
        s.addProperty("issuedAt",System.currentTimeMillis());
        cookie(x,StorefrontSession.seal(s,config.sessionKey()),3600);send(x,200,Map.of("csrf",text(s,"csrf"),"email",text(s,"email"),"id",text(s,"id"),"issuedAt",s.get("issuedAt").getAsLong()));
    }
    private JsonObject session(HttpExchange x,boolean csrf)throws Exception{
        String cookie="";for(String part:Objects.toString(x.getRequestHeaders().getFirst("Cookie"),"").split(";")){String[] p=part.trim().split("=",2);if(p.length==2&&p[0].equals("__Host-deckers"))cookie=p[1];}
        JsonObject s;try{s=StorefrontSession.open(cookie,config.sessionKey());}catch(Exception e){throw new EmploymentPortalCloud.AuthFailure("Sign in to continue.");}
        if(csrf&&!LanSecurity.constantTimeEquals(text(s,"csrf"),Objects.toString(x.getRequestHeaders().getFirst("X-CSRF-Token"),"")))throw new EmploymentPortalCloud.AuthFailure("Refresh the page before continuing.");
        var u=auth.auth("user",text(s,"access"),null);if(!text(s,"id").equals(text(u,"id"))||text(u,"email_confirmed_at").isBlank()||!text(s,"email").equalsIgnoreCase(text(u,"email"))||(!text(u,"banned_until").isBlank()&&Instant.parse(text(u,"banned_until")).isAfter(Instant.now())))throw new EmploymentPortalCloud.AuthFailure("Sign in again.");return s;
    }
    private void image(HttpExchange x)throws Exception{
        Map<String,String> q=new HashMap<>();for(String v:Objects.toString(x.getRequestURI().getQuery(),"").split("&")){String[] a=v.split("=",2);if(a.length==2)q.put(a[0],a[1]);}
        try(Connection c=DB.getConnection()){JsonObject s=cached(c,Integer.parseInt(q.getOrDefault("storeId","0")));int id=Integer.parseInt(q.getOrDefault("id","0"));
            if("logo".equals(q.get("kind"))&&s.has("branding")){
                var branding=s.getAsJsonObject("branding");
                if(branding.has("_logoManifest")){
                    var manifest=branding.getAsJsonObject("_logoManifest");
                    if(!"COMPANY_LOGO".equals(text(manifest,"category")))throw new IllegalArgumentException("Logo unavailable.");
                    var logo=ServerImageAssetService.loadStorefrontSnapshot(manifest);
                    respond(x,200,logo.contentType(),logo.bytes());return;
                }
            }
            if("project".equals(q.get("kind"))&&s.has("projects")){
                String projectId=q.getOrDefault("projectId","");
                for(var e:s.getAsJsonArray("projects")){
                    var project=e.getAsJsonObject();
                    if(projectId.equals(text(project,"id"))&&project.has("_coverManifest")){
                        var manifest=project.getAsJsonObject("_coverManifest");
                        if(!"PROJECT".equals(text(manifest,"category")))break;
                        var image=ServerImageAssetService.loadStorefrontSnapshot(manifest);
                        respond(x,200,image.contentType(),image.bytes());return;
                    }
                }
            }
            if("project-media".equals(q.get("kind"))&&s.has("projects")){
                String projectId=q.getOrDefault("projectId",""),mediaId=q.getOrDefault("mediaId","");
                for(var e:s.getAsJsonArray("projects")){
                    var project=e.getAsJsonObject();if(!projectId.equals(text(project,"id"))||!project.has("gallery"))continue;
                    for(var item:project.getAsJsonArray("gallery")){
                        var media=item.getAsJsonObject();if(!mediaId.equals(text(media,"id"))||!media.has("_manifest"))continue;
                        var manifest=media.getAsJsonObject("_manifest");
                        if(!"PROJECT".equals(text(manifest,"category")))break;
                        var image=ServerImageAssetService.loadStorefrontSnapshot(manifest);
                        respond(x,200,image.contentType(),image.bytes());return;
                    }
                }
            }

            for(var e:s.getAsJsonArray("products")){var p=e.getAsJsonObject();if(integer(p,"id")==id&&!text(p,"image").isBlank()){
                if(!ImageAssetReference.isAssetReference(text(p,"image")))break;
                var image=integer(s,"locationId")==DatabaseConfig.load().locationId()
                    ?ServerImageAssetService.loadStorefrontProduct(text(p,"image"))
                    :ServerImageAssetService.loadStorefrontSnapshot(p.getAsJsonObject("_imageManifest"));
                respond(x,200,image.contentType(),image.bytes());return;
            }}
        }respond(x,404,"text/plain",new byte[0]);
    }
    private void projectQr(HttpExchange x)throws Exception{
        Map<String,String> q=new HashMap<>();for(String v:Objects.toString(x.getRequestURI().getQuery(),"").split("&")){String[] a=v.split("=",2);if(a.length==2)q.put(a[0],a[1]);}
        int store;UUID id;try{store=Integer.parseInt(q.getOrDefault("storeId",""));id=UUID.fromString(q.getOrDefault("projectId",""));}
        catch(IllegalArgumentException e){throw new IllegalArgumentException("Choose a valid project and store.");}
        try(Connection c=DB.getConnection()){
            JsonObject snapshot=cached(c,store);
            if(!StorefrontProjectQr.published(snapshot,id)){respond(x,404,"text/plain",new byte[0]);return;}
        }
        respond(x,200,"image/svg+xml",StorefrontProjectQr.svg(StorefrontProjectQr.url(config.origin(),store,id)));
    }
    private void projectPage(HttpExchange x,String path)throws Exception{
        var match=java.util.regex.Pattern.compile("^/shop/projects/([1-9][0-9]*)/([0-9a-fA-F-]{36})/?$").matcher(path);
        if(!match.matches()){respond(x,404,"text/plain",new byte[0]);return;}
        int store;UUID id;try{store=Integer.parseInt(match.group(1));id=UUID.fromString(match.group(2));}
        catch(IllegalArgumentException e){respond(x,404,"text/plain",new byte[0]);return;}
        JsonObject project=null;
        try(Connection c=DB.getConnection()){
            JsonObject publicCatalog=catalog(cached(c,store));
            if(publicCatalog.get("projects").isJsonArray())for(var item:publicCatalog.getAsJsonArray("projects"))
                if(id.toString().equals(text(item.getAsJsonObject(),"id"))){project=item.getAsJsonObject();break;}
        }
        if(project==null){respond(x,404,"text/plain",new byte[0]);return;}
        try(var in=getClass().getClassLoader().getResourceAsStream("storefront-web/index.html")){
            if(in==null){respond(x,503,"text/plain",new byte[0]);return;}
            String html=StorefrontProjectPage.render(new String(in.readAllBytes(),StandardCharsets.UTF_8),project,config.origin(),store,id);
            respond(x,200,"text/html",html.getBytes(StandardCharsets.UTF_8));
        }
    }
    private void productPage(HttpExchange x,String path)throws Exception{
        var match=java.util.regex.Pattern.compile("^/shop/products/([1-9][0-9]*)/([1-9][0-9]*)/?$").matcher(path);
        if(!match.matches()){respond(x,404,"text/plain",new byte[0]);return;}
        int store,id;try{store=Integer.parseInt(match.group(1));id=Integer.parseInt(match.group(2));}
        catch(IllegalArgumentException e){respond(x,404,"text/plain",new byte[0]);return;}
        JsonObject product=null,settings=null;
        try(Connection c=DB.getConnection()){
            JsonObject publicCatalog=catalog(cached(c,store));
            settings=publicCatalog.getAsJsonObject("settings");
            if(publicCatalog.get("products").isJsonArray())for(var item:publicCatalog.getAsJsonArray("products"))
                if(id==integer(item.getAsJsonObject(),"id")){product=item.getAsJsonObject();break;}
        }
        if(product==null){respond(x,404,"text/plain",new byte[0]);return;}
        try(var in=getClass().getClassLoader().getResourceAsStream("storefront-web/index.html")){
            if(in==null){respond(x,503,"text/plain",new byte[0]);return;}
            String html=StorefrontProductPage.render(new String(in.readAllBytes(),StandardCharsets.UTF_8),product,settings,config.origin(),store,id);
            respond(x,200,"text/html",html.getBytes(StandardCharsets.UTF_8));
        }
    }
    private void sitemap(HttpExchange x)throws Exception{
        Set<String> urls=new TreeSet<>();
        for(String path:StorefrontEditorialPage.paths())urls.add(config.origin()+"/shop/"+path);
        for(String slug:StorefrontServicePage.slugs())urls.add(config.origin()+"/shop/services/"+slug);
        try(Connection c=DB.getConnection()){
            for(var entry:rows(c,"SELECT location_id,payload FROM storefront.snapshots")){
                JsonObject row=entry.getAsJsonObject();
                int store=integer(row,"location_id");JsonObject snapshot=row.getAsJsonObject("payload");
                if(snapshot!=null&&snapshot.has("products")&&snapshot.get("products").isJsonArray()){
                    JsonObject publicCatalog=catalog(snapshot);
                    for(var item:publicCatalog.getAsJsonArray("products")){
                        int id=integer(item.getAsJsonObject(),"id");
                        if(id>0)urls.add(StorefrontProductPage.url(config.origin(),store,id));
                    }
                }
                if(snapshot==null||!snapshot.has("projects")||!snapshot.get("projects").isJsonArray())continue;
                for(var item:snapshot.getAsJsonArray("projects")){
                    try{UUID id=UUID.fromString(text(item.getAsJsonObject(),"id"));urls.add(StorefrontProjectQr.url(config.origin(),store,id));}
                    catch(IllegalArgumentException ignored){}
                }
            }
        }
        respond(x,200,"application/xml",StorefrontProjectPage.sitemap(config.origin(),urls).getBytes(StandardCharsets.UTF_8));
    }
    private static void cookie(HttpExchange x,String value,int age){x.getResponseHeaders().add("Set-Cookie","__Host-deckers="+value+"; Path=/; Secure; HttpOnly; SameSite=Lax; Max-Age="+age);}
    private static void safe(HttpExchange x,int status,String message){try{send(x,status,Map.of("message",Objects.toString(message,"Request failed.")));}catch(Exception ignored){}}
    private static void send(HttpExchange x,int status,Object object)throws java.io.IOException{respond(x,status,"application/json",JSON.toJson(object).getBytes(StandardCharsets.UTF_8));}
    private static void respond(HttpExchange x,int status,String type,byte[] bytes)throws java.io.IOException{
        var h=x.getResponseHeaders();h.set("Content-Type",type+"; charset=utf-8");h.set("Cache-Control","no-store");h.set("X-Content-Type-Options","nosniff");h.set("Referrer-Policy","same-origin");h.set("Content-Security-Policy","default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);
    }
    public void close(){server.stop(1);pool.shutdownNow();}
}
