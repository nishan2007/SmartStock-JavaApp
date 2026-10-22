package services;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import data.*;
import managers.SessionManager;
import java.nio.file.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Local administrator initiated, immutable diagnostics. No remote command surface. */
public final class ServerHandoffDiagnostics {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "handoff-expiry"); t.setDaemon(true); return t;
    });
    private static Sharing active;
    static { Runtime.getRuntime().addShutdownHook(new Thread(ServerHandoffDiagnostics::revoke,"handoff-shutdown")); }
    private ServerHandoffDiagnostics() { }

    public static void requireAdministrator() throws Exception {
        if (DatabaseConfig.load().mode() != DatabaseMode.SERVER || SessionManager.getCurrentUserId() == null)
            throw new SecurityException("Sign in on the physical store server.");
        try (Connection c = DB.getConnection(); PreparedStatement p = c.prepareStatement(
                "SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id "
                + "JOIN permissions p ON p.permission_id=rp.permission_id WHERE u.user_id=? "
                + "AND u.is_active=TRUE AND UPPER(p.permission_key)='ROLE_MANAGEMENT'")) {
            p.setInt(1, SessionManager.getCurrentUserId());
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SecurityException("Server diagnostics require Role Management permission.");
            }
        }
    }

    public static JsonObject capture() throws Exception {
        requireAdministrator();
        JsonObject report = new JsonObject();
        report.addProperty("format", 1);
        report.addProperty("capturedAt", Instant.now().toString());
        report.addProperty("environment", EnvironmentProfile.active().id());
        report.addProperty("locationId", DatabaseConfig.load().locationId());
        report.addProperty("availableProcessors", Runtime.getRuntime().availableProcessors());
        report.addProperty("lanCertificate", LanTlsIdentity.loadExisting().fingerprint());
        report.addProperty("serverRole",ServerRoleGuard.state().name());
        JsonObject health=new JsonObject();
        for(int port:List.of(8443,8444,8445,8448)) {
            try(java.net.Socket socket=new java.net.Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1",port),500);health.addProperty("tcp"+port,true);
            }catch(java.io.IOException e){health.addProperty("tcp"+port,false);}
        }
        report.add("localListeners",health);
        JsonObject tables = new JsonObject();
        try (Connection c = DB.getConnection()) {
            c.setReadOnly(true); c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); c.setAutoCommit(false);
            try (Statement s = c.createStatement()) { s.execute("SET LOCAL statement_timeout='30s'"); }
            List<String[]> names = new ArrayList<>();
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                    "SELECT schemaname,tablename FROM pg_tables WHERE schemaname IN ('public','storefront') ORDER BY 1,2")) {
                while (r.next()) names.add(new String[]{r.getString(1),r.getString(2)});
            }
            for (String[] name : names) {
                try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                        "SELECT count(*) FROM " + quote(name[0]) + "." + quote(name[1]))) {
                    r.next(); tables.addProperty(name[0]+"."+name[1], r.getLong(1));
                }
            }
            report.add("drawerAndRegisterContinuity",ServerHandoffContinuity.capture(c));
            c.rollback();
        }
        report.add("tableCounts", tables);
        JsonObject assets = new JsonObject();
        Path root = EnvironmentProfile.active().directory();
        for (String folder : List.of("image-store","custom-order-media","studio-templates","studio-photo-queue","ai-models"))
            assets.add(folder, digestTree(root.resolve(folder)));
        report.add("assets", assets);
        report.addProperty("scope", "Database counts, drawer/register content fingerprints and selected local asset hashes. Capture after writes stop; files are not an atomic database snapshot. Matching evidence does not perform a transfer.");
        report.addProperty("manualChecks", "Record contents, database roles/sequences, Windows startup, tunnels, cloud sync, credential usability, register trust, printing, drawers and NFC still require verification.");
        return report;
    }

    static String quote(String name) { return "\""+name.replace("\"","\"\"")+"\""; }
    static JsonObject digestTree(Path root) throws Exception {
        JsonObject result = new JsonObject();
        result.addProperty("present", Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS));
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return result;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long count=0, bytes=0;
        try (var walk = Files.walk(root)) {
            for (Path file : walk.sorted().toList()) {
                if (Files.isSymbolicLink(file)) throw new IllegalStateException("Asset links require manual verification.");
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                String relative = root.relativize(file).toString().replace('\\','/');
                MessageDigest content = MessageDigest.getInstance("SHA-256");
                try (var input = Files.newInputStream(file)) {
                    byte[] buffer = new byte[65536]; int n;
                    while ((n=input.read(buffer))!=-1) { content.update(buffer,0,n); bytes+=n; }
                }
                digest.update(relative.getBytes(StandardCharsets.UTF_8)); digest.update((byte)0);
                digest.update(content.digest()); count++;
            }
        }
        result.addProperty("files",count); result.addProperty("bytes",bytes);
        result.addProperty("sha256",HexFormat.of().formatHex(digest.digest())); return result;
    }

    public static JsonObject compare(JsonObject baseline, JsonObject current) {
        if (!baseline.has("format") || baseline.get("format").getAsInt()!=1)
            throw new IllegalArgumentException("Unsupported handoff baseline.");
        JsonObject result = new JsonObject(); JsonArray differences = new JsonArray();
        for (String key : List.of("environment","locationId","lanCertificate","tableCounts","assets","drawerAndRegisterContinuity"))
            if (!Objects.equals(baseline.get(key),current.get(key))) differences.add(key);
        JsonObject details=new JsonObject();
        for(String section:List.of("tableCounts","assets","drawerAndRegisterContinuity")) {
            if(!baseline.has(section)||!baseline.get(section).isJsonObject()||!current.has(section)||!current.get(section).isJsonObject())continue;
            JsonObject before=baseline.getAsJsonObject(section),after=current.getAsJsonObject(section),changed=new JsonObject();
            Set<String> keys=new TreeSet<>(before.keySet());keys.addAll(after.keySet());
            for(String key:keys)if(!Objects.equals(before.get(key),after.get(key))) {
                JsonObject values=new JsonObject();values.add("baseline",before.get(key));values.add("current",after.get(key));changed.add(key,values);
            }
            details.add(section,changed);
        }
        result.add("details",details);
        result.add("differentSections",differences);
        boolean identityMatches=true;
        for(String key:List.of("environment","locationId","lanCertificate"))
            identityMatches &= baseline.has(key)&&current.has(key)&&Objects.equals(baseline.get(key),current.get(key));
        JsonObject continuity=ServerHandoffContinuity.compare(
                baseline.has("drawerAndRegisterContinuity")?baseline.getAsJsonObject("drawerAndRegisterContinuity"):null,
                current.has("drawerAndRegisterContinuity")?current.getAsJsonObject("drawerAndRegisterContinuity"):null,identityMatches);
        result.add("drawerAndRegisterPreservation",continuity);
        result.addProperty("automatedSectionsMatch",differences.isEmpty()&&continuity.get("preservationChecksPassed").getAsBoolean());
        result.addProperty("verifiedComplete",false);
        result.addProperty("note","Matching counts and files are partial evidence; complete the manual handoff checks.");
        return result;
    }

    public static void save(Path target, JsonObject report) throws Exception {
        requireAdministrator(); Files.writeString(target,JSON.toJson(report),StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW); utils.SecureFilePermissions.restrictFileToOwner(target);
    }

    public static synchronized String share(JsonObject report) throws Exception {
        requireAdministrator(); revoke();
        Sharing next = new Sharing(report); active=next;
        try { next.open(); return next.url(); }
        catch (Exception e) { next.close(); active=null; throw e; }
    }
    public static synchronized void revoke() { if (active!=null) { active.close(); active=null; } }

    static final class Sharing implements AutoCloseable {
        final String token=LanSecurity.randomToken();
        final Instant expiry;
        final byte[] bytes;
        HttpsServer server; CloudflareQuickTunnel tunnel; ExecutorService pool;
        ScheduledFuture<?> timeout; volatile boolean closed; int reads;
        Sharing(JsonObject report) { this(report,Instant.now().plusSeconds(1800)); }
        Sharing(JsonObject report,Instant expiry) {
            this.expiry=expiry;
            JsonObject copy=report.deepCopy(); copy.addProperty("diagnosticsExpireAt",expiry.toString());
            bytes=JSON.toJson(copy).getBytes(StandardCharsets.UTF_8);
            if (bytes.length>2*1024*1024) throw new IllegalArgumentException("Diagnostic report is too large.");
        }
        synchronized void open() throws Exception {
            LanHttpsTransport.preserveDefaultProvider();
            // The packaged Jetty HttpServer provider reports -1 for a port-zero
            // connector. Select an explicit loopback port and fail if another
            // process wins the bind; never expose a non-loopback listener.
            int port;
            try(java.net.ServerSocket reservation=new java.net.ServerSocket()) {
                reservation.bind(new InetSocketAddress("127.0.0.1",0));port=reservation.getLocalPort();
            }
            server=HttpsServer.create(new InetSocketAddress("127.0.0.1",port),8);
            server.setHttpsConfigurator(new HttpsConfigurator(LanTlsIdentity.loadExisting().sslContext()));
            pool=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{Thread t=new Thread(r,"handoff-report");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
            server.setExecutor(pool); server.createContext("/",this::handle); server.start();
            timeout=TIMER.schedule(this::close,30,TimeUnit.MINUTES);
            tunnel=CloudflareQuickTunnel.start(port);
        }
        String url() { return tunnel.publicOrigin()+"/#token="+token; }
        synchronized int authorize(String method,String path,String authorization) {
            if(closed || !Instant.now().isBefore(expiry)) return 410;
            if (!"GET".equals(method)) return 405;
            if (!"/report".equals(path)) return 404;
            if (!LanSecurity.constantTimeEquals("Bearer "+token,Objects.toString(authorization,""))) return 403;
            return ++reads>60?429:200;
        }
        void handle(HttpExchange x) throws java.io.IOException {
            try {
                x.getResponseHeaders().set("Cache-Control","no-store");
                x.getResponseHeaders().set("Referrer-Policy","no-referrer");
                x.getResponseHeaders().set("X-Content-Type-Options","nosniff");
                x.getResponseHeaders().set("Content-Security-Policy","default-src 'none'; script-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'");
                if ("/".equals(x.getRequestURI().getPath()) && "GET".equals(x.getRequestMethod())) {
                    byte[] page=("<!doctype html><meta charset=utf-8><title>SmartStock handoff diagnostics</title><pre id=r>Loading diagnostics…</pre><script>const token=new URLSearchParams(location.hash.slice(1)).get('token');history.replaceState(null,'','/');fetch('/report',{headers:{Authorization:'Bearer '+token}}).then(async r=>{document.getElementById('r').textContent=r.ok?JSON.stringify(await r.json(),null,2):'Diagnostics unavailable or expired.'}).catch(()=>document.getElementById('r').textContent='Connection unavailable.');</script>").getBytes(StandardCharsets.UTF_8);
                    x.getResponseHeaders().set("Content-Type","text/html; charset=utf-8"); x.sendResponseHeaders(200,page.length);x.getResponseBody().write(page);return;
                }
                int status=authorize(x.getRequestMethod(),x.getRequestURI().getPath(),x.getRequestHeaders().getFirst("Authorization"));
                byte[] body=status==200?bytes:"Diagnostics unavailable.".getBytes(StandardCharsets.UTF_8);
                x.getResponseHeaders().set("Content-Type",status==200?"application/json":"text/plain");
                x.sendResponseHeaders(status,body.length); x.getResponseBody().write(body);
            } finally { x.close(); }
        }
        public synchronized void close() {
            closed=true; if(timeout!=null)timeout.cancel(false);
            if(server!=null)server.stop(0); if(pool!=null)pool.shutdownNow(); if(tunnel!=null)tunnel.close();
        }
    }
}
