package services;

import com.google.gson.*;
import data.DB;
import data.DatabaseConfig;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.sql.Connection;
import java.util.concurrent.*;
import static services.StorefrontService.*;

/** Public listener and store-to-store snapshots/order handoff share the installed service lifetime. */
public final class StorefrontRuntime implements AutoCloseable {
    private static volatile StorefrontRuntime active;
    private volatile long lastSuccess;
    private volatile long lastAttempt;
    private volatile String lastError="";
    private volatile boolean running=true;
    static java.util.Map<String,Object> status(){
        var current=active;
        return java.util.Map.of("running",current!=null&&current.running,
            "origin",current==null?"":current.config.origin(),"lastSuccess",current==null?0L:current.lastSuccess,
            "lastAttempt",current==null?0L:current.lastAttempt,"lastError",current==null?"":current.lastError,
            "role",ServerRoleGuard.state().name());
    }
    private final StorefrontConfig config;private final StorefrontWebServer web;
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"storefront-sync");t.setDaemon(true);return t;});
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final StorefrontImagePrefetch imagePrefetch=new StorefrontImagePrefetch();
    private final ScheduledExecutorService images=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"storefront-image-prefetch");t.setDaemon(true);return t;});
    private StorefrontRuntime(StorefrontConfig c)throws Exception{config=c;web=StorefrontWebServer.start(c);scheduler.scheduleWithFixedDelay(this::synchronize,0,15,TimeUnit.SECONDS);images.scheduleWithFixedDelay(this::prefetch,15,15,TimeUnit.SECONDS);}
    private void prefetch(){try{imagePrefetch.batch(4,StorefrontRuntime::requirePrimary,ServerImageAssetService::loadStorefrontSnapshot);}catch(IllegalStateException fenced){/* Recheck authority on the next scheduled batch. */}}
    public static synchronized StorefrontRuntime startIfConfigured()throws Exception{
        if(active!=null&&active.running)return active;
        var config=StorefrontConfig.load();if(config==null)return null;
        try(var c=DB.getConnection()){StorefrontSchema.ensure(c);}var runtime=new StorefrontRuntime(config);active=runtime;return runtime;
    }
    private void synchronize(){
        if(ServerRoleGuard.state()!=ServerRoleGuard.State.PRIMARY)return;
        lastAttempt=System.currentTimeMillis();
        try(Connection c=DB.getConnection()){
            int location=DatabaseConfig.load().locationId();c.setAutoCommit(false);JsonObject payload=new JsonObject();
            try {
                requirePrimary();
                if(!config.browseOnly())expire(c,location);
                JsonObject localSnapshot=snapshot(c,location);
                payload.add("snapshot",config.browseOnly()?publicSyncSnapshot(localSnapshot):localSnapshot);
                payload.add("events",config.browseOnly()?new JsonArray():rows(c,"SELECT event_id,payload FROM storefront.events WHERE delivered_at IS NULL ORDER BY created_at LIMIT 100"));
                payload.add("enrollments",config.browseOnly()?new JsonArray():rows(c,"SELECT * FROM storefront.enrollments"));
                requirePrimary();c.commit();
            }
            catch(Exception e){c.rollback();throw e;}
            var request=HttpRequest.newBuilder(URI.create(config.origin()+"/shop/internal/sync?storeId="+location)).timeout(Duration.ofSeconds(25)).header("Content-Type","application/json").header("X-Storefront-Key",config.edgeKey()).header("X-Storefront-Instance",config.instanceId()).POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new IllegalStateException("Gateway synchronization is unavailable.");
            var result=JsonParser.parseString(response.body()).getAsJsonObject();
            if(config.browseOnly()){
                if(!result.getAsJsonArray("orders").isEmpty()||!result.getAsJsonArray("enrollments").isEmpty())
                    throw new IllegalStateException("Browse-only sync received customer-specific data.");
                JsonArray publicSnapshots=new JsonArray();
                for(var entry:result.getAsJsonArray("snapshots"))publicSnapshots.add(publicSyncSnapshot(entry.getAsJsonObject()));
                result.add("snapshots",publicSnapshots);
            }
            applyHandoff(c,result,payload,location,StorefrontRuntime::requirePrimary);
            imagePrefetch.replace(result.getAsJsonArray("snapshots"),location);
            lastSuccess=System.currentTimeMillis();lastError="";
        }catch(Exception e){lastError="Gateway synchronization failed ("+e.getClass().getSimpleName()+").";System.err.println("Storefront sync will retry: "+e.getClass().getSimpleName());}
    }
    static void requirePrimary(){if(ServerRoleGuard.state()!=ServerRoleGuard.State.PRIMARY)throw new IllegalStateException("The store server is no longer authorized to apply storefront changes.");}
    static void applyHandoff(Connection c,JsonObject result,JsonObject payload,int location,Runnable checkAuthority)throws Exception{
            if(c.getAutoCommit())throw new IllegalArgumentException("Storefront handoff requires a transaction.");
            try{
                checkAuthority.run();
                for(var e:result.getAsJsonArray("snapshots")){var s=e.getAsJsonObject();execute(c,"INSERT INTO storefront.snapshots(location_id,captured_at,payload) VALUES(?,?::timestamptz,?::jsonb) ON CONFLICT(location_id) DO UPDATE SET captured_at=EXCLUDED.captured_at,payload=EXCLUDED.payload,refreshed_at=now() WHERE EXCLUDED.captured_at>=storefront.snapshots.captured_at",integer(s,"locationId"),text(s,"capturedAt"),s.toString());}
                for(var e:result.getAsJsonArray("orders"))importOrder(c,e.getAsJsonObject(),location);
                if(result.has("enrollments"))for(var e:result.getAsJsonArray("enrollments"))StorefrontEnrollmentService.importEnrollment(c,e.getAsJsonObject(),location);
                for(var e:payload.getAsJsonArray("events"))execute(c,"UPDATE storefront.events SET delivered_at=now() WHERE event_id=?",java.util.UUID.fromString(text(e.getAsJsonObject(),"event_id")));
                checkAuthority.run();c.commit();
            }catch(Exception e){c.rollback();throw e;}
    }
    public synchronized void close(){if(!running)return;running=false;if(active==this)active=null;scheduler.shutdownNow();images.shutdownNow();web.close();}
}
