package services;

import com.google.gson.*;
import data.DatabaseConfig;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Unauthenticated, bounded connectivity checks; does not attempt a staff login. */
final class WebPublicHealth {
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final java.util.concurrent.ExecutorService PROBES = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "web-public-health");
        thread.setDaemon(true);
        return thread;
    });
    private static long checked;
    private static boolean refreshing;
    private static List<Map<String,Object>> cached=List.of();
    static synchronized List<Map<String,Object>> status(){
        return status(WebPublicHealth::probe);
    }
    static synchronized List<Map<String,Object>> status(java.util.function.Supplier<List<Map<String,Object>>> probe){
        if(cached.isEmpty()) cached=List.of(
            pending("downloads","Installer download website"),
            pending("downloadAuth","Public store verification"));
        if(!refreshing && System.currentTimeMillis()-checked>=15000){
            refreshing=true;
            PROBES.execute(() -> {
                try {
                    var result=List.copyOf(probe.get());
                    synchronized(WebPublicHealth.class){cached=result;checked=System.currentTimeMillis();}
                } finally {
                    synchronized(WebPublicHealth.class){refreshing=false;}
                }
            });
        }
        return cached;
    }
    private static Map<String,Object> pending(String id,String name){
        return WebStatusService.service(id,name,"Public connectivity check",false,"",false,
            "Checking public connectivity in the background. Local service status remains available.");
    }
    private static List<Map<String,Object>> probe(){
        var rows=new ArrayList<Map<String,Object>>();
        rows.add(check("downloads","Installer download website","https://downloads.deckers.gy/download/windows",401));
        try{
            Path path=Path.of(System.getProperty("user.home"),".smartstock","storefront-tunnel","wrangler.website.json");
            if(Files.isRegularFile(path)&&Files.size(path)<65536){
                var config=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                var origins=JsonParser.parseString(config.getAsJsonObject("vars").get("ORIGINS_JSON").getAsString()).getAsJsonArray();
                for(var element:origins){var origin=element.getAsJsonObject();
                    if(origin.get("storeId").getAsInt()==DatabaseConfig.load().locationId()){
                        URI uri=URI.create(origin.get("url").getAsString());
                        if("https".equals(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null&&uri.getRawQuery()==null)
                            rows.add(check("downloadAuth","Public store verification",uri.resolve("/v1/installer/authenticate").toString(),405));
                        break;
                    }
                }
            }
        }catch(Exception ignored){}
        if(rows.size()==1)rows.add(WebStatusService.service("downloadAuth","Public store verification","Connection used to verify download logins",false,"",false,"The registered public origin is unavailable in this server account's website configuration."));
        return List.copyOf(rows);
    }
    private static Map<String,Object> check(String id,String name,String url,int expected){
        int code=0;try{var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).method("HEAD",HttpRequest.BodyPublishers.noBody()).build();
            code=HTTP.send(request,HttpResponse.BodyHandlers.discarding()).statusCode();
        }catch(InterruptedException ex){Thread.currentThread().interrupt();}catch(Exception ignored){}
        return WebStatusService.service(id,name,"Public connectivity check",code==expected,url,false,
            code==expected?"Reachable · HTTP "+code+" is the expected unauthenticated response. No login was attempted.":
                code==0?"Could not connect. Check the internet connection and tunnel.":"Unexpected HTTP "+code+". Check the public connection and tunnel.");
    }
}
