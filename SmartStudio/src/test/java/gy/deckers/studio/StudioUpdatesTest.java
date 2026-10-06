package gy.deckers.studio;
import services.AppUpdateService.AppRelease;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StudioUpdatesTest {
    private AppRelease release(String path,String platform,long size,String hash) {
        return new AppRelease(1,"0.1.2",102,platform,"smartstock-releases",path,hash,size,"",false,"");
    }
    @Test void comparesNumericVersionsAndRejectsInjectedVersions() {
        assertTrue(StudioUpdates.newer("0.1.10","0.1.2"));
        assertFalse(StudioUpdates.newer("0.1.1","0.1.1"));
        assertFalse(StudioUpdates.newer("0.1.0","0.1.1"));
        assertThrows(IllegalArgumentException.class,()->StudioUpdates.newer("../update","0.1.1"));
    }
    @Test void excludesSmartStockAndInvalidArtifacts() {
        assertDoesNotThrow(()->StudioUpdates.validate(release("smartstudio/windows/0.1.2/setup.exe","windows",100,"a".repeat(64))));
        for(String path:new String[]{"windows/stock.exe","smartstudio/windows/../setup.exe","smartstudio/windows/update.zip"})
            assertThrows(java.io.IOException.class,()->StudioUpdates.validate(release(path,"windows",100,"a".repeat(64))));
        assertThrows(java.io.IOException.class,()->StudioUpdates.validate(release("smartstudio/windows/setup.exe","mac",100,"a".repeat(64))));
        assertThrows(java.io.IOException.class,()->StudioUpdates.validate(release("smartstudio/windows/setup.exe","windows",0,"a".repeat(64))));
        assertThrows(java.io.IOException.class,()->StudioUpdates.validate(release("smartstudio/windows/setup.exe","windows",100,"bad")));
    }
    @Test void versionResourceIsFilteredFromPom() throws Exception {assertTrue(StudioUpdates.currentVersion().matches("\\d+\\.\\d+\\.\\d+"));}
    @Test void installerIsVerifiedAgainBeforeExecution() throws Exception {
        var path=java.nio.file.Files.createTempFile("studio-updater-test", ".exe");
        try {
            byte[] bytes="verified sample".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            java.nio.file.Files.write(path,bytes);
            String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            var release=release("smartstudio/windows/setup.exe","windows",bytes.length,hash);
            assertDoesNotThrow(()->StudioUpdates.verifyInstaller(path,release));
            bytes[0]^=1;java.nio.file.Files.write(path,bytes);
            assertThrows(java.io.IOException.class,()->StudioUpdates.verifyInstaller(path,release));
        }finally{java.nio.file.Files.deleteIfExists(path);}
    }
}
