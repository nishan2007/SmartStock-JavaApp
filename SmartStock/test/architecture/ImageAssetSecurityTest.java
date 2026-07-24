package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImageAssetSecurityTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void purgeUsesStorageApiAndRegistryIsNotClientExposed() throws Exception {
        String service = Files.readString(ROOT.resolve("src/services/ServerImageAssetService.java"));
        String migration = Files.readString(ROOT.resolve("database/migrations/20260722180000_image_asset_registry.sql"));

        assertTrue(service.contains(".DELETE()"));
        assertTrue(service.contains("ServerSupabaseCredentials.applyTo(request).build()"));
        assertFalse(service.contains("DELETE FROM storage.objects"));
        assertTrue(migration.contains("ENABLE ROW LEVEL SECURITY"));
        assertTrue(migration.contains("REVOKE ALL ON public.image_assets"));
    }

    @Test
    void idCardDocumentsRemainOutsideImageRegistry() throws Exception {
        String server = Files.readString(ROOT.resolve("src/services/LanApiServer.java"));

        assertTrue(server.contains("path.startsWith(\"ID cards/\")"));
        assertTrue(server.contains("return ApiResult.ok(Map.of(\"url\", url))"));
    }
}
