package services;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OneDriveImageCloudProviderTest {
    private static final UUID ID=UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test void createsStableProviderPathsForEachManagedCategory(){
        assertEquals("products/"+ID+".jpg",OneDriveImageCloudProvider.remotePath(ID,"PRODUCT","products/a.JPG"));
        assertEquals("custom-items/"+ID+".png",OneDriveImageCloudProvider.remotePath(ID,"CUSTOM_ITEM","products/a.png"));
        assertEquals("custom-variants/"+ID+".webp",OneDriveImageCloudProvider.remotePath(ID,"CUSTOM_VARIANT","products/a.webp"));
    }

    @Test void doesNotLeakOriginalNamesAndSanitizesUnknownExtensions(){
        String path=OneDriveImageCloudProvider.remotePath(ID,"PRODUCT","products/customer name.secret.exe-long");
        assertEquals("products/"+ID+".img",path);
        assertFalse(path.contains("customer"));
    }

    @Test void retriesOnlyTransientGraphStatuses(){
        assertTrue(OneDriveImageCloudProvider.isRetryableStatus(429));
        assertTrue(OneDriveImageCloudProvider.isRetryableStatus(503));
        assertFalse(OneDriveImageCloudProvider.isRetryableStatus(401));
        assertFalse(OneDriveImageCloudProvider.isRetryableStatus(404));
    }

    @Test void acceptsOnlySafeHttpsContentRedirects()throws Exception{
        URI graph=URI.create("https://graph.microsoft.com/v1.0/drives/drive/items/item/content");
        assertEquals("https://files.example.microsoft/test",OneDriveImageCloudProvider.downloadRedirect(
                graph,"https://files.example.microsoft/test").toString());
        assertThrows(IOException.class,()->OneDriveImageCloudProvider.downloadRedirect(graph,"http://files.example/test"));
        assertThrows(IOException.class,()->OneDriveImageCloudProvider.downloadRedirect(graph,"https://user@files.example/test"));
    }
}
