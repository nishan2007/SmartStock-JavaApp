package services;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class OneDriveImageBoundaryTest {
    @Test void migrationAcceptsTheProductImageNamesGeneratedByTheApplication()throws Exception{
        String source=Files.readString(Path.of("src/services/ServerImageAssetService.java"));
        var checks=java.util.regex.Pattern.compile("remote_path !~ '([^']+)'").matcher(source);
        int checked=0;
        while(checks.find()){
            var pattern=java.util.regex.Pattern.compile(checks.group(1).translateEscapes());
            for(String token:java.util.List.of("1791125251381", "1791125251381-4dc9d3ad",
                    StorageObjectNameBuilder.newProductImageToken())){
                String name=StorageObjectNameBuilder.productImageFilename("photo.jpg",token,
                        "Shirt","Hanes","T-shirt","Medium","Yellow");
                assertTrue(pattern.matcher("custom-variants/"+name).find(),name);
            }
            assertFalse(pattern.matcher("products/old-photo.jpg").find());
            checked++;
        }
        assertEquals(2,checked,"Selection and migration reset must accept the same names");
    }

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
        assertTrue(imageService.contains("? <> 'DISABLED' AND category IN ('PRODUCT','CUSTOM_ITEM','CUSTOM_VARIANT','CUSTOMER_PHOTO','EMPLOYEE_PHOTO','COMPANY_LOGO','BADGE_LOGO','IMAGE')"));
        String server=Files.readString(Path.of("src/services/LanApiServer.java"));
        assertTrue(server.contains("isLoopbackAddress()"));
        assertTrue(server.contains("SERVER_LOCAL_REQUIRED"));
    }

    @Test void includesEveryManagedPhotoAndLogoInOneDriveMigration(){
        for(String category:java.util.List.of("PRODUCT","CUSTOM_ITEM","CUSTOM_VARIANT",
                "CUSTOMER_PHOTO","EMPLOYEE_PHOTO","COMPANY_LOGO","BADGE_LOGO","IMAGE")){
            assertTrue(ServerImageAssetService.isOneDriveCategory(category),category);
        }
        assertFalse(ServerImageAssetService.isOneDriveCategory(null));
        assertFalse(ServerImageAssetService.isOneDriveCategory("EMPLOYEE_DOCUMENT"));
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
