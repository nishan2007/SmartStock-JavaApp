package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobileItemWebArchitectureTest {
    private static String source(String path)throws Exception{return Files.readString(Path.of(path));}

    @Test void mobileRuntimeIsLocalOnlyAndPersistent()throws Exception{
        String migration=source("database/migrations/v1_after/20260818120000_mobile_item_web.sql");
        String contract=source("src/services/SchemaContractService.java");
        String worker=source("src/services/LanApiServer.java");
        assertTrue(migration.contains("mobile_item_web_runtime"));
        assertTrue(migration.contains("mobile_item_web_activations"));
        assertTrue(migration.contains("mobile_item_web_sessions"));
        assertTrue(migration.contains("ENABLE ROW LEVEL SECURITY"));
        assertTrue(contract.contains("20260818120000_mobile_item_web.sql"));
        assertTrue(worker.contains("restoreMobileItemWebIfEnabled"));
        assertTrue(worker.contains("MOBILE_ITEM_WEB_STOPPED"));
        assertTrue(source("src/services/CompanyBackupService.java").contains("!lower.startsWith(\"mobile_item_web_\")"));
        assertFalse(source("database/v1/cloud/001_schema.sql").contains("mobile_item_web_sessions"));
    }

    @Test void controlIsServerLocalAndPermissionChecked()throws Exception{
        String server=source("src/services/LanApiServer.java");
        String menu=source("src/ui/components/AppMenuBar.java");
        assertTrue(server.contains("/v1/mobile-item-web/start"));
        assertTrue(server.contains("/v1/mobile-item-web/stop"));
        assertTrue(server.contains("requireAnyPermission(c,s.userId(),\"DEVICE_MANAGEMENT\")"));
        assertTrue(server.contains("isLocalAddress(exchange.getRemoteAddress().getAddress())"));
        assertTrue(menu.contains("Mobile Item Web App…"));
        assertTrue(menu.contains("DatabaseMode.SERVER"));
        assertTrue(menu.contains("PermissionManager.hasPermission(\"NEW_ITEM\")"));
        assertTrue(server.contains("requireMobileQrAccess"));
    }

    @Test void webBoundaryIncludesActivationSessionsAndDomainServices()throws Exception{
        String api=source("src/services/MobileItemWebServer.java");
        String js=source("src/mobile-web/app.js");
        for(String value:new String[]{"UI_PORT = 8444","API_PORT = 8445","ACTIVATION_INVALID","CSRF_INVALID","SameSite=Strict","Idempotency-Key","requireLan"})assertTrue(api.contains(value),value);
        assertTrue(api.contains("origin.equalsIgnoreCase(allowed)"));
        assertTrue(api.contains("LanProductAdminService.create"));
        assertTrue(api.contains("LanProductAdminService.update"));
        assertTrue(api.contains("device_name='WEB APP'"));
        assertTrue(api.contains("smartstock-mobile-item-web:"));
        assertTrue(api.contains("LanProductAdminService.create(c,b,deviceId"));
        assertTrue(source("src/services/LanProductAdminService.java").contains("DEVICE_ID_REQUIRED"));
        assertTrue(api.contains("LanCustomOrderCatalogAdminService.mutate"));
        assertTrue(api.contains("ServerImageAssetService.storeUpload"));
        assertTrue(js.contains("capture=\"environment\"")||source("src/mobile-web/index.html").contains("capture=\"environment\""));
        assertTrue(js.contains("SAVE_VARIANT"));
        assertTrue(api.contains("/barcodes/scan"));
        assertTrue(api.contains("MultiFormatReader"));
        assertTrue(js.contains("scan-barcode"));
        assertTrue(js.contains("getUserMedia"));
        assertTrue(js.contains("focusMode:'continuous'"));
        assertTrue(source("src/mobile-web/index.html").contains("class=\"scan-box\""));
        assertTrue(source("src/mobile-web/index.html").contains("id=\"barcodeCamera\""));
        assertTrue(js.contains("function productFields(p){const isNew=!p;p=p||{}"));
        assertTrue(js.contains("function customFields(p){p=p||{}"));
        assertTrue(js.contains("image/jpeg"));
        assertTrue(source("src/mobile-web/index.html").contains("/boot.js"));
        assertTrue(js.contains("${location.origin}/api/v1"));
        assertTrue(api.contains("path.startsWith(\"/api/v1/\")"));
        assertTrue(api.contains("/trust"));
        assertTrue(source("src/mobile-web/boot.js").contains("smartstockMobileReady"));
    }
}
