package services;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class StorefrontImageTest {
    @TempDir Path cache;
    private final byte[] bytes = new byte[]{1,2,3,4};
    private JsonObject manifest() throws Exception {
        var row=new ServerImageAssetService.AssetRow(UUID.randomUUID(),"PRODUCT","products","shirt.jpg","PUBLIC",
                "image/jpeg",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                "ACTIVE","PRESENT","PRESENT","SUPABASE",null,null,null,null,"NOT_REQUIRED");
        return new com.google.gson.Gson().toJsonTree(row).getAsJsonObject();
    }
    @Test void backupDownloadsVerifiesCachesAndRepairsWithoutDatabase() throws Exception {
        var manifest=manifest();var calls=new AtomicInteger();
        ServerImageAssetService.StorefrontDownload download=row->{calls.incrementAndGet();return bytes;};
        assertArrayEquals(bytes,ServerImageAssetService.loadStorefrontSnapshot(manifest,cache,download).bytes());
        assertArrayEquals(bytes,ServerImageAssetService.loadStorefrontSnapshot(manifest,cache,download).bytes());
        assertEquals(1,calls.get());
        assertArrayEquals(bytes,ServerImageAssetService.loadStorefrontSnapshot(manifest,cache,row->{throw new AssertionError("Cached image must work while the provider is offline");}).bytes());
        Files.write(cache.resolve(manifest.get("sha256").getAsString()),new byte[]{9});
        assertArrayEquals(bytes,ServerImageAssetService.loadStorefrontSnapshot(manifest,cache,download).bytes());
        assertEquals(2,calls.get());
    }
    @Test void companyLogoUsesVerifiedCacheAndHidesStorageMetadata() throws Exception {
        var logo=manifest();logo.addProperty("category","COMPANY_LOGO");
        assertArrayEquals(bytes,ServerImageAssetService.loadStorefrontSnapshot(logo,cache,row->bytes).bytes());
        assertArrayEquals(bytes,ServerImageAssetService.loadStorefrontSnapshot(logo,cache,row->{throw new AssertionError("Use cached logo offline");}).bytes());
        var snapshot=new JsonObject();snapshot.add("products",new com.google.gson.JsonArray());
        var brand=new JsonObject();brand.addProperty("name","Sample Company");brand.add("_logoManifest",logo);snapshot.add("branding",brand);
        var publicBrand=StorefrontService.catalog(snapshot).getAsJsonObject("branding");
        assertEquals("Sample Company",StorefrontService.text(publicBrand,"name"));
        assertEquals(StorefrontService.text(logo,"sha256"),StorefrontService.text(publicBrand,"logo"));
        assertFalse(publicBrand.has("_logoManifest"));assertTrue(brand.has("_logoManifest"));
        assertFalse(ServerImageAssetService.storefrontAssetAllowed("COMPANY_LOGO","ACTIVE","image/jpeg"));
    }
    @Test void corruptCloudBytesAreNeverCachedOrServed() throws Exception {
        assertThrows(java.io.IOException.class,()->ServerImageAssetService.loadStorefrontSnapshot(manifest(),cache,row->new byte[]{9}));
        try(var files=Files.list(cache)){assertEquals(0,files.count());}
    }
    @Test void unsafeManifestsFailBeforeCloudAccess() throws Exception {
        for(var entry:Map.of("category","EMPLOYEE_PHOTO","lifecycleStatus","DELETED","contentType","image/svg+xml",
                "sha256","","objectPath","../private.jpg","cloudProvider","UNKNOWN").entrySet()){
            var manifest=manifest();manifest.addProperty(entry.getKey(),entry.getValue());
            assertThrows(java.io.IOException.class,()->ServerImageAssetService.loadStorefrontSnapshot(manifest,cache,row->{throw new AssertionError("Must reject before download");}));
        }
    }
}
