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
        assertTrue(imageService.contains("image_cloud_configuration"));
        assertTrue(imageService.contains("hydrateOneDriveConfiguration"));
        assertTrue(imageService.contains("savePublicIdentifiers"));
        String referenceSync=Files.readString(Path.of("src/services/ReferenceDataSyncService.java"));
        assertTrue(referenceSync.contains("image_cloud_configuration"));
        assertFalse(referenceSync.contains("onedrive-image-private-key-pem"));
        assertTrue(imageService.contains("? <> 'DISABLED' AND category IN ('PRODUCT','CUSTOM_ITEM','CUSTOM_VARIANT')"));
        String server=Files.readString(Path.of("src/services/LanApiServer.java"));
        assertTrue(server.contains("isLoopbackAddress()"));
        assertTrue(server.contains("SERVER_LOCAL_REQUIRED"));
    }

    @Test void sharedIdentifierSchemaDoesNotRequireSupabaseRolesLocally()throws Exception{
        String migration=Files.readString(Path.of(
                "database/migrations/v1_after/20260819230000_onedrive_shared_identifiers.sql"));
        assertTrue(migration.contains("IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role')"));

        String localBaseline=Files.readString(Path.of("database/v1/local/001_schema.sql"));
        assertFalse(localBaseline.contains(
                "GRANT ALL ON TABLE public.image_cloud_configuration TO service_role"));

        String contract=Files.readString(Path.of("src/services/SchemaContractService.java"));
        assertTrue(contract.contains(
                "database/migrations/v1_after/20260819230000_onedrive_shared_identifiers.sql"));
    }
}
