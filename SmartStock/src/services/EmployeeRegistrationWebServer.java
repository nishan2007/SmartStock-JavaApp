package services;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import data.DB;
import data.DatabaseConfig;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Public application surface, reachable only through a local reverse proxy. */
public final class EmployeeRegistrationWebServer implements AutoCloseable {
    public static final int PORT=8448;
    private static final Gson JSON=LanJson.create();
    private final HttpsServer server;
    private final ExecutorService executor;
    private final ExecutorService authRequests = new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{Thread t=new Thread(r,"portal-auth-request");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final SupabaseAuthEmailHook emailHook = new SupabaseAuthEmailHook();
    private final EmploymentPortalCloud cloud=new EmploymentPortalCloud();
    private final String origin;
    private final Map<String,Attempt> attempts=new HashMap<>();
    private final ConcurrentMap<String,Session> sessions=new ConcurrentHashMap<>();
    private record Attempt(Instant expires,int count) { }
    private static final class Session {
        final UUID auth,application;final String csrf;final Instant absolute;String access,refresh;volatile Instant idle;
        Session(UUID auth,UUID application,String csrf,String access,String refresh){this.auth=auth;this.application=application;this.csrf=csrf;this.access=access;this.refresh=refresh;absolute=Instant.now().plusSeconds(28800);idle=Instant.now().plusSeconds(1800);}
    }
    private EmployeeRegistrationWebServer(HttpsServer s,ExecutorService e,String origin){server=s;executor=e;this.origin=origin;}
    public static EmployeeRegistrationWebServer start(LanTlsIdentity tls,EmployeeRegistrationService.Cloud ignored)throws Exception {
        String origin=EmploymentPortalConfig.origin();
        HttpsServer s=HttpsServer.create(new InetSocketAddress("127.0.0.1",PORT),20);
        ExecutorService e=new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{Thread t=new Thread(r,"employment-portal");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
        try{s.setHttpsConfigurator(new HttpsConfigurator(tls.sslContext()));s.setExecutor(e);var app=new EmployeeRegistrationWebServer(s,e,origin);s.createContext("/",app::dispatch);s.start();return app;}
        catch(Exception ex){s.stop(0);e.shutdownNow();throw ex;}
    }
    public String url(){return origin+"/register";}
    static boolean lanAddress(InetAddress a){return a!=null&&(a.isLoopbackAddress()||a.isSiteLocalAddress()||a.isLinkLocalAddress());}
    private synchronized boolean allow(String key,int maximum) {
        Instant now=Instant.now();attempts.entrySet().removeIf(e->!e.getValue().expires.isAfter(now));
        Attempt a=attempts.get(key);if(a==null){if(attempts.size()>=4096)return false;a=new Attempt(now.plusSeconds(600),0);}
        if(a.count>=maximum)return false;attempts.put(key,new Attempt(a.expires,a.count+1));return true;
    }
    private void dispatch(HttpExchange x) {
        // Auth requests wait for Supabase, which calls back into this server. Separate workers prevent deadlock.
        if (x.getRequestURI().getPath().startsWith("/register/api/auth/")) {
            try { authRequests.execute(() -> handle(x)); }
            catch (RejectedExecutionException ex) { safeSend(x,503,"Account requests are busy. Please retry."); x.close(); }
        } else handle(x);
    }
    private void handle(HttpExchange x) {
        try {
            if(!x.getRemoteAddress().getAddress().isLoopbackAddress()||!URI.create(origin).getRawAuthority().equalsIgnoreCase(x.getRequestHeaders().getFirst("Host"))){send(x,403,"Use the company application link.");return;}
            String path=x.getRequestURI().getPath();
            if(!path.equals("/register")&&!path.startsWith("/register/")){send(x,404,"Not found.");return;}
            // Server-to-server requests use signed raw bodies, not browser cookies or browser CSRF headers.
            if(SupabaseAuthEmailHook.PATH.equals(path)){emailHook.handle(x);return;}
            if("/register/health".equals(path)){json(x,Map.of("ok",ServerRoleGuard.state()==ServerRoleGuard.State.PRIMARY));return;}
            if(path.startsWith("/register/api/")){
                if(!"POST".equals(x.getRequestMethod())){send(x,405,"POST is required.");return;}
                if(!origin.equals(x.getRequestHeaders().getFirst("Origin"))||!"same-origin".equals(x.getRequestHeaders().getFirst("X-Registration-Request"))){send(x,403,"Refresh the application page and try again.");return;}
                if(ServerRoleGuard.state()!=ServerRoleGuard.State.PRIMARY){send(x,503,"The active store server is unavailable. Nothing has been saved.");return;}
                String ct=x.getRequestHeaders().getFirst("Content-Type");if(ct==null||!ct.startsWith("application/json")){send(x,415,"JSON is required.");return;}
                String route=path.substring("/register/api/".length());
                int limit="upload".equals(route)?14*1024*1024:64*1024;
                byte[] bytes=x.getRequestBody().readNBytes(limit+1);if(bytes.length>limit){Arrays.fill(bytes,(byte)0);send(x,413,"This request is too large.");return;}
                JsonObject body;try{body=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();}finally{Arrays.fill(bytes,(byte)0);}
                if(route.startsWith("auth/")){auth(x,route.substring(5),body);return;}
                Session session=requireSession(x,true);
                try(Connection c=DB.getConnection()) {
                    switch(route){
                        case "application" -> json(x,EmploymentApplicationService.read(c,session.application,session.auth,false));
                        case "save","submit" -> json(x,EmploymentApplicationService.save(c,session.application,session.auth,body.get("revision").getAsInt(),body.getAsJsonObject("form"),"submit".equals(route)));
                        case "withdraw" -> {EmploymentApplicationService.transition(c,session.application,session.auth,"WITHDRAWN","Application withdrawn.","",null);json(x,Map.of("ok",true));}
                        case "upload" -> {
                            byte[] file=Base64.getDecoder().decode(required(body,"base64",14*1024*1024));
                            try{EmploymentApplicationService.upload(c,session.application,session.auth,UUID.fromString(required(body,"id",36)),required(body,"filename",255),required(body,"category",40),EmploymentApplicationService.value(body,"description"),file,cloud);}finally{Arrays.fill(file,(byte)0);}
                            json(x,Map.of("ok",true));
                        }
                        case "remove" -> {EmploymentApplicationService.remove(c,session.application,session.auth,UUID.fromString(required(body,"id",36)));json(x,Map.of("ok",true));}
                        default -> send(x,404,"Not found.");
                    }
                }return;
            }
            if(!"GET".equals(x.getRequestMethod())){send(x,405,"GET is required.");return;}
            if("/register/branding".equals(path)){branding(x);return;}
            if("/register/logo".equals(path)){logo(x);return;}
            if(path.startsWith("/register/attachments/")){
                Session s=requireSession(x,false);UUID id=UUID.fromString(path.substring("/register/attachments/".length()));
                try(Connection c=DB.getConnection()){var doc=EmploymentApplicationService.download(c,s.application,s.auth,id,false,cloud);x.getResponseHeaders().set("Content-Disposition","inline; filename=\"document."+(doc.type().equals("application/pdf")?"pdf":doc.type().equals("image/png")?"png":"jpg")+"\"");respond(x,200,doc.type(),doc.bytes());}return;
            }
            String file=switch(path){case "/register","/register/"->"index.html";case "/register/app.js"->"app.js";case "/register/app.css"->"app.css";default->null;};
            if(file==null){send(x,404,"Not found.");return;}
            try(var in=getClass().getClassLoader().getResourceAsStream("employee-registration-web/"+file)){
                if(in==null){send(x,503,"Applications are unavailable.");return;}
                respond(x,200,file.endsWith("js")?"text/javascript; charset=utf-8":file.endsWith("css")?"text/css; charset=utf-8":"text/html; charset=utf-8",in.readAllBytes());
            }
        }catch(EmploymentPortalCloud.AuthFailure e){safeSend(x,401,e.getMessage());}
        catch(JsonParseException e){safeSend(x,400,"The request contains invalid JSON.");}
        catch(IllegalArgumentException e){safeSend(x,400,"Check your details. "+Objects.toString(e.getMessage(),"Invalid request."));}
        catch(Exception e){safeSend(x,503,"The request could not be completed. Your last saved application is safe. Please retry.");}
        finally{x.close();}
    }
    private void auth(HttpExchange x,String route,JsonObject body)throws Exception {
        if("session".equals(route)){Session s=requireSession(x,false);json(x,Map.of("csrfToken",s.csrf));return;}
        if("logout".equals(route)){requireSession(x,true);sessions.remove(cookie(x));setCookie(x,"",0);json(x,Map.of("ok",true));return;}
        String email=EmploymentApplicationService.value(body,"email").trim().toLowerCase(Locale.ROOT);
        String ip=x.getRequestHeaders().getFirst("X-Real-IP");if(ip==null||ip.length()>80)ip="proxy";
        if(!allow("ip:"+ip,40)||!allow("email:"+LanSecurity.sha256(email),15)){send(x,429,"Too many account requests. Try again in ten minutes.");return;}
        JsonObject q=new JsonObject();q.addProperty("email",email);
        if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||email.length()>254)throw new IllegalArgumentException("Enter a valid email.");
        switch(route){
            case "signup" -> {String password=required(body,"password",128);if(password.length()<8)throw new IllegalArgumentException("Use at least eight password characters.");q.addProperty("password",password);cloud.auth("signup?redirect_to="+URLEncoder.encode(url(),StandardCharsets.UTF_8),null,q);json(x,Map.of("message","Check your email for a verification code, then verify your account."));}
            case "recover" -> {cloud.auth("recover?redirect_to="+URLEncoder.encode(url(),StandardCharsets.UTF_8),null,q);json(x,Map.of("message","If this email has an account, a recovery code has been sent."));}
            case "resend" -> {q.addProperty("type","signup");cloud.auth("resend?redirect_to="+URLEncoder.encode(url(),StandardCharsets.UTF_8),null,q);json(x,Map.of("message","If this account still needs verification, a new code has been sent. Check your inbox."));}
            case "login" -> {
                try(Connection c=DB.getConnection()){cloud.reconcileLegacy(c,email);}
                q.addProperty("password",required(body,"password",128));createSession(x,cloud.auth("token?grant_type=password",null,q));
            }
            case "verify","reset" -> {
                q.addProperty("token",required(body,"code",20));q.addProperty("type","reset".equals(route)?"recovery":"signup");JsonObject tokens=cloud.auth("verify",null,q);
                if("reset".equals(route)){String password=required(body,"password",128);if(password.length()<8)throw new IllegalArgumentException("Use at least eight password characters.");JsonObject change=new JsonObject();change.addProperty("password",password);JsonObject changed=cloud.auth("user",required(tokens,"access_token",8192),change);String authId=EmploymentApplicationService.value(changed,"id");sessions.entrySet().removeIf(e->e.getValue().auth.toString().equals(authId));}
                createSession(x,tokens);
            }
            default -> send(x,404,"Not found.");
        }
    }
    private void createSession(HttpExchange x,JsonObject tokens)throws Exception {
        String access=required(tokens,"access_token",8192);JsonObject user=cloud.auth("user",access,null);
        UUID auth=UUID.fromString(required(user,"id",36));String email=required(user,"email",254);
        if(EmploymentApplicationService.value(user,"email_confirmed_at").isBlank())throw new EmploymentPortalCloud.AuthFailure("Verify your email before signing in.");
        UUID application;try(Connection c=DB.getConnection()){application=EmploymentApplicationService.ensureApplicant(c,auth,email,DatabaseConfig.load().locationId());}
        sessions.entrySet().removeIf(e->e.getValue().absolute.isBefore(Instant.now())||e.getValue().idle.isBefore(Instant.now()));
        if(sessions.size()>=2000)throw new IllegalArgumentException("The portal is busy. Please try again shortly.");
        sessions.remove(cookie(x));String cookie=LanSecurity.randomToken(),csrf=LanSecurity.randomToken();sessions.put(LanSecurity.sha256(cookie),new Session(auth,application,csrf,access,EmploymentApplicationService.value(tokens,"refresh_token")));setCookie(x,cookie,28800);json(x,Map.of("csrfToken",csrf));
    }
    private Session requireSession(HttpExchange x,boolean csrf)throws Exception {
        Session s=sessions.get(cookie(x));Instant now=Instant.now();
        if(s==null||!s.idle.isAfter(now)||!s.absolute.isAfter(now)){sessions.remove(cookie(x));throw new EmploymentPortalCloud.AuthFailure("Sign in again to continue.");}
        if(csrf&&!LanSecurity.constantTimeEquals(s.csrf,Objects.toString(x.getRequestHeaders().getFirst("X-CSRF-Token"),"")))throw new EmploymentPortalCloud.AuthFailure("Refresh this page before continuing.");
        synchronized(s){
            JsonObject user;
            try{user=cloud.auth("user",s.access,null);}catch(EmploymentPortalCloud.AuthFailure e){JsonObject q=new JsonObject();q.addProperty("refresh_token",s.refresh);JsonObject token=cloud.auth("token?grant_type=refresh_token",null,q);s.access=required(token,"access_token",8192);s.refresh=required(token,"refresh_token",8192);user=cloud.auth("user",s.access,null);}
            if(!s.auth.toString().equals(EmploymentApplicationService.value(user,"id")))throw new EmploymentPortalCloud.AuthFailure("Sign in again.");
            String banned=EmploymentApplicationService.value(user,"banned_until");
            if(!banned.isBlank()&&Instant.parse(banned).isAfter(now)){sessions.remove(cookie(x));throw new EmploymentPortalCloud.AuthFailure("This account is unavailable. Contact the company.");}
            s.idle=now.plusSeconds(1800);
        }return s;
    }
    private void branding(HttpExchange x)throws Exception {
        var settings=managers.ServerCompanyCustomizationRepository.loadReceiptSettingsForLocation(DatabaseConfig.load().locationId());
        var info=WalletCompanyBranding.from(settings);Map<String,Object> result=new LinkedHashMap<>();result.put("name",info.name());result.put("motto",info.motto());result.put("address",info.address());result.put("phone",info.phone());result.put("email",info.email());
        String logo=info.logoPath();if(logo!=null&&!logo.isBlank())result.put("logo","/register/logo?v="+LanSecurity.sha256(logo));
        json(x,result);
    }
    private void logo(HttpExchange x)throws Exception {
        String reference=managers.ServerCompanyCustomizationRepository.loadReceiptSettingsForLocation(DatabaseConfig.load().locationId()).logoPath();
        byte[] bytes;
        if(ImageAssetReference.isAssetReference(reference))bytes=ServerImageAssetService.load(reference).bytes();
        else if(reference!=null&&reference.startsWith(SupabaseProjectConfig.load().url()+"/storage/v1/object/public/")) {
            var client=java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            var response=client.send(java.net.http.HttpRequest.newBuilder(URI.create(reference)).timeout(Duration.ofSeconds(15)).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofInputStream());
            try(var in=response.body()){if(response.statusCode()!=200){send(x,404,"Logo unavailable.");return;}bytes=in.readNBytes(10*1024*1024+1);}
        } else {send(x,404,"Logo unavailable.");return;}
        var doc=EmploymentApplicationService.validateDocument(bytes);
        if(doc.type().equals("application/pdf")){send(x,404,"Logo unavailable.");return;}
        respond(x,200,doc.type(),doc.bytes());
    }
    private static String required(JsonObject b,String key,int max){String v=EmploymentApplicationService.value(b,key);if(v.isBlank()||v.length()>max)throw new IllegalArgumentException("Check "+key+".");return v;}
    private static String cookie(HttpExchange x){String raw=x.getRequestHeaders().getFirst("Cookie");if(raw!=null)for(String part:raw.split(";")){String[] pair=part.trim().split("=",2);if(pair.length==2&&"__Host-ss_application".equals(pair[0]))return LanSecurity.sha256(pair[1]);}return "";}
    private static void setCookie(HttpExchange x,String value,int age){x.getResponseHeaders().add("Set-Cookie","__Host-ss_application="+value+"; Path=/; Secure; HttpOnly; SameSite=Lax; Max-Age="+age);}
    private static void json(HttpExchange x,Object object)throws java.io.IOException{respond(x,200,"application/json; charset=utf-8",JSON.toJson(object).getBytes(StandardCharsets.UTF_8));}
    private static void safeSend(HttpExchange x,int status,String message){try{send(x,status,message);}catch(Exception ignored){}}
    private static void send(HttpExchange x,int status,String message)throws java.io.IOException{respond(x,status,"application/json; charset=utf-8",JSON.toJson(Map.of("ok",false,"message",message)).getBytes(StandardCharsets.UTF_8));}
    private static void respond(HttpExchange x,int status,String type,byte[] bytes)throws java.io.IOException {
        Headers h=x.getResponseHeaders();h.set("Content-Type",type);h.set("Cache-Control","no-store");h.set("X-Content-Type-Options","nosniff");h.set("Referrer-Policy","no-referrer");h.set("Content-Security-Policy","default-src 'self'; img-src 'self' blob: https://*.supabase.co; frame-ancestors 'none'; form-action 'self'; object-src 'none'; base-uri 'none'");
        x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);
    }
    public void close(){sessions.clear();server.stop(2);authRequests.shutdownNow();emailHook.close();executor.shutdownNow();}
}
