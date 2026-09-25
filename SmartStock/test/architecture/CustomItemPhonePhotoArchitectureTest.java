package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomItemPhonePhotoArchitectureTest {
    private static String source(String path)throws Exception{return Files.readString(Path.of(path));}

    @Test void desktopIssuesAndTracksScopedPhonePhotoHandoffs()throws Exception{
        String server=source("src/services/LanApiServer.java");
        String client=source("src/services/LanApiClient.java");
        String screen=source("src/ui/screens/customorders/CustomOrderItems.java");
        assertTrue(server.contains("/v1/mobile-item-web/photo-handoff"));
        assertTrue(server.contains("MANAGE_CUSTOM_ORDER_ITEMS"));
        assertTrue(client.contains("createMobileItemPhotoHandoff"));
        assertTrue(client.contains("mobileItemPhotoHandoffStatus"));
        assertTrue(screen.contains("Photo from Phone"));
        assertTrue(screen.contains("showPhonePhoto(\"variant\""));
    }

    @Test void phonePageUsesExpiringSingleTargetUpload()throws Exception{
        String web=source("src/services/MobileItemWebServer.java");
        String page=source("src/mobile-web/photo.html");
        String script=source("src/mobile-web/photo.js");
        assertTrue(web.contains("Instant.now().plus(Duration.ofMinutes(10))"));
        assertTrue(web.contains("PHOTO_HANDOFF_USED"));
        assertTrue(web.contains("UPDATE custom_order_item_variants SET image_url=?"));
        assertTrue(web.contains("UPDATE custom_order_items SET image_url=?"));
        assertTrue(page.contains("capture=\"environment\""));
        assertTrue(script.contains("/photo/upload"));
        assertTrue(script.contains("API=`${location.origin}/api/v1`"));
        assertTrue(!script.contains("location.hostname}:8445"));
    }
}
