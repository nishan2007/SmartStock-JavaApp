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
    @Test void handoffWaitsForDesktopExitBeforeRunningSetup()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        var directory=java.nio.file.Files.createTempDirectory("studio handoff ' test ");
        var installer=directory.resolve("setup.cmd");var marker=directory.resolve("started.txt");
        java.nio.file.Files.writeString(installer,"@echo off\r\necho started>\""+marker+"\"\r\n");
        var desktop=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-WindowStyle","Hidden","-Command","Start-Sleep -Seconds 4").start();
        Process helper=null;
        try{
            helper=new ProcessBuilder(StudioUpdates.handoffCommand(installer,directory,desktop.pid())).redirectErrorStream(true).redirectOutput(directory.resolve("helper.log").toFile()).start();
            assertFalse(desktop.waitFor(500,java.util.concurrent.TimeUnit.MILLISECONDS));assertFalse(java.nio.file.Files.exists(marker),"Setup must wait for the desktop");
            assertTrue(helper.waitFor(20,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,helper.exitValue(),java.nio.file.Files.readString(directory.resolve("helper.log")));
            assertFalse(desktop.isAlive());assertTrue(java.nio.file.Files.exists(marker));
        }finally{if(desktop.isAlive())desktop.destroyForcibly();if(helper!=null&&helper.isAlive())helper.destroyForcibly();}
    }
    @Test void handoffQuotesPathsAndNeverForcesTheDesktopClosed(){
        var path=java.nio.file.Path.of("C:/Studio's folder/setup.exe");var root=java.nio.file.Path.of("C:/Studio's folder");
        var command=StudioUpdates.handoffCommand(path,root,12345);
        String script=new String(java.util.Base64.getDecoder().decode(command.get(command.size()-1)),java.nio.charset.StandardCharsets.UTF_16LE);
        assertTrue(script.contains("Studio''s folder"));assertTrue(script.contains("WaitForExit(120000)"));
        assertTrue(script.indexOf("WaitForExit")<script.indexOf("& '"));assertFalse(script.contains("Stop-Process"));assertFalse(script.contains("/RESTARTAPPLICATIONS"));
        assertThrows(IllegalArgumentException.class,()->StudioUpdates.handoffCommand(path,root,0));
    }

}
