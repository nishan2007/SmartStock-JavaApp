package services;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class OneDriveImageBoundaryTest {
    @Test void microsoftCredentialsRemainServerOnly()throws Exception{
        String client=Files.readString(Path.of("src/services/LanApiClient.java"));
        String cache=Files.readString(Path.of("src/utils/ImageCacheManager.java"));
        assertFalse(client.contains("graph.microsoft.com"));
        assertFalse(client.contains("onedrive-image-private-key"));
        assertFalse(cache.contains("graph.microsoft.com"));
        assertTrue(Files.readString(Path.of("src/services/OneDriveImageStorageConfig.java"))
                .contains("SecureCredentialStore"));
        assertTrue(Files.readString(Path.of("src/services/OneDriveImageStorageConfig.java"))
                .contains("pem-v1:"));
        String provider=Files.readString(Path.of("src/services/OneDriveImageCloudProvider.java"));
        assertTrue(provider.contains("GRAPH+\"/drives/\""));
        assertFalse(provider.contains("GRAPH+\"/users/\""));
        String imageService=Files.readString(Path.of("src/services/ServerImageAssetService.java"));
        assertTrue(imageService.contains("migration_status NOT IN ('VERIFIED','RESOLVED')"));
        assertTrue(imageService.contains("? <> 'DISABLED' AND category IN ('PRODUCT','CUSTOM_ITEM','CUSTOM_VARIANT')"));
        String server=Files.readString(Path.of("src/services/LanApiServer.java"));
        assertTrue(server.contains("isLoopbackAddress()"));
        assertTrue(server.contains("SERVER_LOCAL_REQUIRED"));
    }
}
