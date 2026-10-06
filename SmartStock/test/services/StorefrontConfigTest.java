package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorefrontConfigTest {
    @Test void browsingIsTheDefaultAndTransactionsRequireAnExplicitSetting() {
        assertTrue(StorefrontConfig.browseOnly(null));
        assertTrue(StorefrontConfig.browseOnly(""));
        assertTrue(StorefrontConfig.browseOnly("true"));
        assertTrue(StorefrontConfig.browseOnly("FALSE"));
        assertFalse(StorefrontConfig.browseOnly("false"));
    }
    @Test void originBrowseOnlyModeKeepsDiscoveryAndDisablesCustomerRoutes() {
        assertTrue(StorefrontWebServer.browseOnlyAllows("catalog"));
        for(String route: new String[]{"auth/session","account","favorite","custom-quote","quote","checkout","proof-decision"})
            assertFalse(StorefrontWebServer.browseOnlyAllows(route));
        JsonObject catalog=new JsonObject(),product=new JsonObject();
        product.addProperty("name","Notebook");product.addProperty("canOrder",true);
        JsonArray products=new JsonArray();products.add(product);catalog.add("products",products);
        catalog.addProperty("store","Skeldon");
        JsonObject settings=new JsonObject();settings.addProperty("enabled",true);catalog.add("settings",settings);
        JsonObject campaign=new JsonObject();campaign.addProperty("topic","3D Printing");campaign.addProperty("steps","Upload your design");catalog.add("campaign",campaign);
        StorefrontWebServer.markBrowseOnly(catalog);
        assertTrue(catalog.get("browseOnly").getAsBoolean());
        assertFalse(catalog.getAsJsonObject("settings").get("enabled").getAsBoolean());
        assertFalse(catalog.getAsJsonArray("products").get(0).getAsJsonObject().get("canOrder").getAsBoolean());
        assertEquals("Notebook",catalog.getAsJsonArray("products").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("Find your store",catalog.getAsJsonObject("campaign").get("primary").getAsString());
        assertFalse(catalog.getAsJsonObject("campaign").get("steps").getAsString().contains("Upload"));
    }
    @Test void browseOnlySynchronizationHasAnExplicitPublicAllowlist() {
        JsonObject snapshot=new JsonObject(),settings=new JsonObject();
        snapshot.addProperty("locationId",1);settings.addProperty("enabled",true);snapshot.add("settings",settings);
        snapshot.add("products",new JsonArray());snapshot.add("projects",new JsonArray());
        for(String key:new String[]{"customers","links","orders","receipts","futurePrivateField"}){
            JsonArray privateRows=new JsonArray();privateRows.add("private");snapshot.add(key,privateRows);
        }
        JsonObject publicOnly=StorefrontService.publicSyncSnapshot(snapshot);
        assertTrue(publicOnly.has("locationId"));
        assertTrue(publicOnly.has("products"));
        assertTrue(publicOnly.has("projects"));
        for(String key:new String[]{"customers","links","orders","receipts","futurePrivateField"})
            assertFalse(publicOnly.has(key));
        assertFalse(publicOnly.getAsJsonObject("settings").get("enabled").getAsBoolean());
        assertTrue(snapshot.getAsJsonObject("settings").get("enabled").getAsBoolean());
    }

    @Test void readsCurrentUserProtectedSettingsWithoutAServiceWrapper(@TempDir Path directory)throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name","").toLowerCase().contains("windows"));
        Path file=directory.resolve("website-config.dpapi");
        Path powershell=Path.of(System.getenv("SystemRoot"),"System32","WindowsPowerShell","v1.0","powershell.exe");
        String script="Add-Type -AssemblyName System.Security;"
                +"$json=@{origin='https://example.test';edgeKey='abcdefghijklmnopqrstuvwxyz123456';sessionKey='AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA='}|ConvertTo-Json -Compress;"
                +"$bytes=[Text.Encoding]::UTF8.GetBytes($json);"
                +"$protected=[Security.Cryptography.ProtectedData]::Protect($bytes,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);"
                +"[IO.File]::WriteAllText($env:TEST_STORE_SETTINGS,[Convert]::ToBase64String($protected));"
                +"[Array]::Clear($bytes,0,$bytes.Length)";
        ProcessBuilder command=new ProcessBuilder(powershell.toString(),"-NoProfile","-NonInteractive","-Command",script);
        command.environment().put("TEST_STORE_SETTINGS",file.toString());
        command.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process=command.start();
        assertTrue(process.waitFor(15,TimeUnit.SECONDS));
        assertEquals(0,process.exitValue());
        assertTrue(Files.isRegularFile(file));
        var saved=StorefrontConfig.readProtectedSettings(file);
        assertEquals("https://example.test",saved.get("origin").getAsString());
        assertEquals("abcdefghijklmnopqrstuvwxyz123456",saved.get("edgeKey").getAsString());
        assertThrows(IllegalStateException.class,()->StorefrontConfig.readProtectedSettings(directory.resolve("missing.dpapi")));
    }

    @Test void installedProtectedSettingsRemainReadableWhenExplicitlyChecked() {
        String filename=System.getProperty("storefront.test.protectedSettings","");
        Assumptions.assumeTrue(!filename.isBlank());
        var saved=StorefrontConfig.readProtectedSettings(Path.of(filename));
        assertEquals("https://www.deckers.gy",saved.get("origin").getAsString());
        assertTrue(saved.get("edgeKey").getAsString().length()>=32);
        assertEquals(32,Base64.getDecoder().decode(saved.get("sessionKey").getAsString()).length);
    }
}
