package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;

/** Small rotating batches avoid delaying order synchronization or starving later products. */
final class StorefrontImagePrefetch {
    private List<JsonObject> manifests=List.of();
    private int cursor;
    synchronized void replace(JsonArray snapshots,int localStore){
        Map<String,JsonObject> unique=new LinkedHashMap<>();
        for(var element:snapshots){
            var snapshot=element.getAsJsonObject();
            if(StorefrontService.integer(snapshot,"locationId")==localStore)continue;
            if(snapshot.has("branding")&&snapshot.getAsJsonObject("branding").has("_logoManifest")){
                var logo=snapshot.getAsJsonObject("branding").getAsJsonObject("_logoManifest");
                String hash=StorefrontService.text(logo,"sha256");
                if(hash.matches("[a-f0-9]{64}"))unique.putIfAbsent(hash,logo.deepCopy());
            }
            for(var product:snapshot.getAsJsonArray("products")){
                var item=product.getAsJsonObject();if(!item.has("_imageManifest"))continue;
                var manifest=item.getAsJsonObject("_imageManifest");String hash=StorefrontService.text(manifest,"sha256");
                if(hash.matches("[a-f0-9]{64}"))unique.putIfAbsent(hash,manifest.deepCopy());
            }
            if(snapshot.has("projects"))for(var project:snapshot.getAsJsonArray("projects")){
                var item=project.getAsJsonObject();
                if(item.has("_coverManifest")){
                    var manifest=item.getAsJsonObject("_coverManifest");String hash=StorefrontService.text(manifest,"sha256");
                    if(hash.matches("[a-f0-9]{64}"))unique.putIfAbsent(hash,manifest.deepCopy());
                }
                if(item.has("gallery")&&item.get("gallery").isJsonArray())for(var photo:item.getAsJsonArray("gallery")){
                    var media=photo.getAsJsonObject();if(!media.has("_manifest"))continue;
                    var manifest=media.getAsJsonObject("_manifest");String hash=StorefrontService.text(manifest,"sha256");
                    if(hash.matches("[a-f0-9]{64}"))unique.putIfAbsent(hash,manifest.deepCopy());
                }
            }
        }
        manifests=List.copyOf(unique.values());cursor=manifests.isEmpty()?0:cursor%manifests.size();
    }
    @FunctionalInterface interface Loader {void load(JsonObject manifest)throws Exception;}
    void batch(int limit,Runnable authority,Loader loader){
        List<JsonObject> selected=new ArrayList<>();
        synchronized(this){for(int i=0;i<Math.min(limit,manifests.size());i++){
            selected.add(manifests.get(cursor));cursor=(cursor+1)%manifests.size();
        }}
        for(var manifest:selected){
            authority.run();
            try{loader.load(manifest);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
            catch(Exception e){System.err.println("Storefront image prefetch will retry: "+e.getClass().getSimpleName());}
        }
    }
}
