package services;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OneDriveImageCloudProviderTest {
    @Test void cancelsStalledResponseBodyBeforeItCanBlockSync(){
        var pending=new java.util.concurrent.CompletableFuture<String>();
        assertThrows(java.net.http.HttpTimeoutException.class,()->
                OneDriveImageCloudProvider.awaitResponse(pending,java.time.Duration.ofMillis(20)));
        assertTrue(pending.isCancelled());
    }

    @Test void preservesResponseAndTransferFailures()throws Exception{
        assertEquals("complete",OneDriveImageCloudProvider.awaitResponse(
                java.util.concurrent.CompletableFuture.completedFuture("complete"),java.time.Duration.ofSeconds(1)));
        var failure=new IOException("download failed");
        assertSame(failure,assertThrows(IOException.class,()->OneDriveImageCloudProvider.awaitResponse(
                java.util.concurrent.CompletableFuture.failedFuture(failure),java.time.Duration.ofSeconds(1))));
    }
    private static final UUID ID=UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test void createsStableProviderPathsForEachManagedCategory(){
        assertEquals("products/a.jpg",OneDriveImageCloudProvider.remotePath(ID,"PRODUCT","products/a.JPG"));
        assertEquals("custom-items/a.png",OneDriveImageCloudProvider.remotePath(ID,"CUSTOM_ITEM","products/a.png"));
        assertEquals("custom-variants/a.webp",OneDriveImageCloudProvider.remotePath(ID,"CUSTOM_VARIANT","products/a.webp"));
        assertEquals("customer-photos/a.jpg",OneDriveImageCloudProvider.remotePath(ID,"CUSTOMER_PHOTO","customer photos/a.jpg"));
        assertEquals("employee-photos-"+ID+"-a.jpg",OneDriveImageCloudProvider.remotePath(ID,"EMPLOYEE_PHOTO","employee photos/a.jpg"));
        assertEquals("company-logos-"+ID+"-logo.png",OneDriveImageCloudProvider.remotePath(ID,"COMPANY_LOGO","company/location-1/logo.png"));
        assertEquals("badge-logos-"+ID+"-logo.png",OneDriveImageCloudProvider.remotePath(ID,"BADGE_LOGO","company/location-1/logo.png"));
        assertEquals("project-artwork-"+ID+"-cover.png",OneDriveImageCloudProvider.remotePath(ID,"PROJECT","projects/cover.png"));
        assertEquals("quote-artwork-"+ID+"-proof.pdf",OneDriveImageCloudProvider.remotePath(ID,"QUOTE_ARTWORK","quotes/proof.pdf"));
    }

    @Test void logosAndEmployeePhotosWithTheSameFilenameCannotOverwriteEachOther(){
        for(String category:java.util.List.of("EMPLOYEE_PHOTO","COMPANY_LOGO","BADGE_LOGO","IMAGE")){
            assertNotEquals(OneDriveImageCloudProvider.remotePath(ID,category,"location-1/photo.png"),
                    OneDriveImageCloudProvider.remotePath(UUID.randomUUID(),category,"location-2/photo.png"));
        }
    }

    @Test void keepsReadableSafeNamesAndSanitizesUnknownExtensions(){
        String path=OneDriveImageCloudProvider.remotePath(ID,"PRODUCT","products/customer name.secret.exe-long");
        assertEquals("products/customer-name-secret.img",path);
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
