package services;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.junit.jupiter.api.Assertions.*;

class WindowsDisplayUpdateServiceTest {
    @Test @EnabledOnOs(OS.WINDOWS)
    void restartWatcherExecutesThroughStaleStateAndWaitsForAppExit()throws Exception{
        for(String terminal:new String[]{"COMPLETE","FAILED"}){
            String harness="$script:reads=0;$script:alive=0;"
                +"function Start-Sleep {param($Seconds,$Milliseconds)};"
                +"function Get-Content {param([switch]$Raw,$LiteralPath) $script:reads++;"
                +"if($script:reads -eq 1){return '{\"runId\":\"old\",\"phase\":\"COMPLETE\"}'};"
                +"if($script:reads -eq 2){return '{\"runId\":\"run-123\",\"phase\":\"INSTALLING\"}'};"
                +"return '{\"runId\":\"run-123\",\"phase\":\""+terminal+"\"}'};"
                +"function Get-Process {param($Id,$ErrorAction) $script:alive++;if($script:alive -eq 1){return 'running'}};"
                +"function Start-Process {param($FilePath) if($script:reads -lt 3 -or $script:alive -lt 2){throw 'Restarted too soon'};Write-Output ('RESTART '+$FilePath)};"
                +WindowsDisplayUpdateService.watcherScript(Path.of("C:/fake/status.json"),"run-123",Path.of("C:/Program Files/Deckers' Stock/SmartStock.exe"));
            assertTrue(runPowerShell(harness).contains("RESTART C:\\Program Files\\Deckers' Stock\\SmartStock.exe"));
        }
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void launcherVersionParsingIgnoresLeftoverNewerJar()throws Exception{
        Path app=Files.createTempDirectory("smartstock-launcher-test");
        Files.createDirectories(app.resolve("app"));
        Files.writeString(app.resolve("app/SmartStock.cfg"),"[Application]\napp.classpath=$APPDIR\\inventory-management-1.1.140.jar\napp.classpath=$APPDIR\\dependency\\gson-2.11.0.jar\n");
        Files.createFile(app.resolve("app/inventory-management-9.9.999.jar"));
        String service=Files.readString(Path.of("installer/windows/run-display-update-service.ps1"));
        String detection=service.substring(service.indexOf("    $Launcher="),service.indexOf("    if(-not $Current)"));
        assertTrue(runPowerShell("$ErrorActionPreference='Stop';$App="+WindowsDisplayUpdateService.quote(app.toString())+";"+detection+";Write-Output $Current.ToString()").contains("1.1.140"));
        Files.writeString(app.resolve("app/SmartStock.cfg"),"[Application]\napp.classpath=$APPDIR\\dependency\\gson-2.11.0.jar\n");
        assertTrue(runPowerShell("$ErrorActionPreference='Stop';$App="+WindowsDisplayUpdateService.quote(app.toString())+";try{"+detection+";throw 'Accepted invalid launcher'}catch{if($_.Exception.Message -notlike '*ambiguous or missing*'){throw};Write-Output 'REJECTED'}").contains("REJECTED"));
    }

    private static String runPowerShell(String script)throws Exception{
        String encoded=Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        Path output=Files.createTempFile("smartstock-display-test", ".log");
        Process process=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-EncodedCommand",encoded)
            .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if(!process.waitFor(20,TimeUnit.SECONDS)){process.destroyForcibly();fail("PowerShell verification timed out");}
        String result=Files.readString(output);
        assertEquals(0,process.exitValue(),result);
        return result;
    }
    @Test void installedVersionFollowsLauncherInsteadOfNewestLeftoverJar()throws Exception{
        String service=Files.readString(Path.of("installer/windows/run-display-update-service.ps1"));
        assertTrue(service.contains("app\\SmartStock.cfg"));
        assertTrue(service.contains("$Versions.Count -ne 1"));
        assertFalse(service.contains("Sort-Object -Descending"));
    }
    @Test void permissionChecksUseRawSidsWithoutAccountNameResolution()throws Exception{
        for(String script : new String[]{"run-display-update-service.ps1","install-display-update-service.ps1"}){
            String source=Files.readString(Path.of("installer/windows",script));
            assertTrue(source.contains(".GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier])"),script);
            assertTrue(source.contains("$identity=$rule.IdentityReference.Value"),script);
            assertFalse(source.contains(".Translate("),script);
            assertTrue(source.contains("$identity -notin"),script);
        }
    }
    @Test void restartWatcherRemainsUnprivilegedAndQuotesPaths(){
        String script=WindowsDisplayUpdateService.watcherScript(Path.of("C:/ProgramData/SmartStockDisplayUpdates/status.json"),"run-123",Path.of("C:/Program Files/Deckers' Stock/SmartStock.exe"));
        assertTrue(script.contains("Deckers'' Stock"));
        assertTrue(script.contains("$s.runId -eq 'run-123'"));
        assertTrue(script.contains("@('COMPLETE','FAILED')"));
        assertFalse(script.contains("RunAs"));
        assertFalse(script.contains("-ArgumentList"));
    }
    @Test void privilegedTaskAcceptsNoUserCommandOrManifestAndVerifiesBeforeExecution()throws Exception{
        String service=Files.readString(Path.of("installer/windows/run-display-update-service.ps1"));
        assertFalse(service.contains("param("));
        assertFalse(service.contains("Invoke-Expression"));
        assertTrue(service.contains("https://downloads.deckers.gy/manifest/windows"));
        assertTrue(service.contains("-MaximumRedirection 0"));
        assertTrue(service.indexOf("Get-FileHash")<service.indexOf("Start-Process"));
        assertTrue(service.contains("Status 'FAILED'"));
        assertTrue(service.contains("[version]$Metadata.version -le $Current"));
    }
}
