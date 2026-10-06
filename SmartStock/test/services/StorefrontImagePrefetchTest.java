package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StorefrontImagePrefetchTest {
    private JsonArray snapshots(){
        JsonArray snapshots=new JsonArray();
        for(int store=1;store<=2;store++){
            var snapshot=new JsonObject();snapshot.addProperty("locationId",store);var products=new JsonArray();
            for(char hash:new char[]{'a','b','c','a'}){
                var product=new JsonObject();var manifest=new JsonObject();manifest.addProperty("sha256",String.valueOf(store==1?'d':hash).repeat(64));
                product.add("_imageManifest",manifest);products.add(product);
            }
            snapshot.add("products",products);snapshots.add(snapshot);
        }
        return snapshots;
    }
    @Test void batchesRotateAcrossSnapshotRefreshAndDeduplicateRemoteImages(){
        var prefetch=new StorefrontImagePrefetch();var downloaded=new ArrayList<String>();
        StorefrontImagePrefetch.Loader loader=m->downloaded.add(m.get("sha256").getAsString().substring(0,1));
        prefetch.replace(snapshots(),1);prefetch.batch(2,()->{},loader);
        prefetch.replace(snapshots(),1);prefetch.batch(2,()->{},loader);
        assertEquals(List.of("a","b","c","a"),downloaded);
        prefetch.replace(new JsonArray(),1);prefetch.batch(4,()->{},loader);assertEquals(4,downloaded.size());
    }
    @Test void failedImageDoesNotStarveOthersAndRoleFenceStopsDownloads(){
        var prefetch=new StorefrontImagePrefetch();prefetch.replace(snapshots(),1);var downloaded=new ArrayList<String>();
        prefetch.batch(4,()->{},m->{String hash=m.get("sha256").getAsString().substring(0,1);downloaded.add(hash);if(hash.equals("a"))throw new java.io.IOException("isolated provider failure");});
        assertEquals(List.of("a","b","c"),downloaded);
        assertThrows(IllegalStateException.class,()->prefetch.batch(4,()->{throw new IllegalStateException("fenced");},m->{fail("Fenced server must not start downloads");}));
    }
    @Test void remoteProjectGalleryPhotosJoinThePrefetchRotation(){
        var snapshots=snapshots();var remote=snapshots.get(1).getAsJsonObject();
        var galleryManifest=new JsonObject();galleryManifest.addProperty("sha256","e".repeat(64));
        var media=new JsonObject();media.add("_manifest",galleryManifest);
        var project=new JsonObject();var gallery=new JsonArray();gallery.add(media);project.add("gallery",gallery);
        var projects=new JsonArray();projects.add(project);remote.add("projects",projects);
        var prefetch=new StorefrontImagePrefetch();prefetch.replace(snapshots,1);
        var hashes=new ArrayList<String>();prefetch.batch(4,()->{},m->hashes.add(m.get("sha256").getAsString().substring(0,1)));
        assertEquals(List.of("a","b","c","e"),hashes);
    }
}
