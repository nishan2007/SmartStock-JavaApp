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
    }
}
