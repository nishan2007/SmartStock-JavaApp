package services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Unprivileged client: it can trigger only the installer's fixed maintenance task. */
final class WindowsDisplayUpdateService {
    private WindowsDisplayUpdateService() { }
    static boolean windows(){return System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win");}
    static void update(Consumer<String> status,BooleanSupplier active)throws Exception {
        Path root=Path.of(System.getenv().getOrDefault("ProgramData","C:\\ProgramData"),"SmartStockDisplayUpdates");
        Path state=root.resolve("status.json");
        if(!Files.isRegularFile(root.resolve("installation.json")))
            throw new IOException("Run the new SmartStock installer once as administrator to enable prompt-free updates.");
        Path app=Path.of(WindowsDisplayUpdateService.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().getParent();
        JsonObject installed=JsonParser.parseString(Files.readString(root.resolve("installation.json")).replace("\uFEFF","")).getAsJsonObject();
        if(!app.toRealPath().equals(Path.of(installed.get("appDirectory").getAsString()).toRealPath()))
            throw new IOException("The update service belongs to a different SmartStock installation.");
        String previous=runId(readState(state));
        Path system=Path.of(System.getenv().getOrDefault("WINDIR","C:\\Windows"),"System32");
        Process trigger=new ProcessBuilder(system.resolve("schtasks.exe").toString(),"/Run","/TN","SmartStockDisplayUpdates")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if(!trigger.waitFor(30,TimeUnit.SECONDS)||trigger.exitValue()!=0)
            throw new IOException("The Windows display update service could not start. Repair it with the installer.");
        long deadline=System.nanoTime()+Duration.ofMinutes(40).toNanos();
        while(active.getAsBoolean() && System.nanoTime()<deadline){
            JsonObject current=readState(state);
            String id=runId(current);
            if(current!=null && !id.equals(previous)){
                String phase=current.get("phase").getAsString();
                switch(phase){
                    case "CURRENT" -> {
                        // Installer and app-update catalogues can be published separately.
                        // Do not claim the app is current just because no newer installer exists.
                        var available=AppUpdateService.checkForUpdate();
                        if(available.release()!=null)
                            throw new IOException("SmartStock "+available.release().version()+" is available, but the Windows installer service has no newer installer. Publish the matching verified installer or restart if it is already installed.");
                        status.accept("SmartStock "+utils.DeviceUtils.getAppVersion()+" is up to date");return;
                    }
                    case "FAILED" -> throw new IOException("Windows update service: "+current.get("message").getAsString());
                    case "DOWNLOADING" -> status.accept("Downloading verified SmartStock installer…");
                    case "READY", "INSTALLING", "COMPLETE" -> {
                        if(!active.getAsBoolean())return;
                        launchWatcher(system,state,id,app.resolve("SmartStock.exe"));
                        System.exit(0);
                        return;
                    }
                    default -> { }
                }
            }
            Thread.sleep(1000);
        }
        if(active.getAsBoolean())throw new IOException("Windows update service timed out; see its protected installer log.");
    }
    private static JsonObject readState(Path path){
        try{return JsonParser.parseString(Files.readString(path).replace("\uFEFF","")).getAsJsonObject();}
        catch(Exception ignored){return null;}
    }
    private static String runId(JsonObject state){return state==null?"":state.get("runId").getAsString();}
    static String quote(String value){return "'"+value.replace("'","''")+"'";}
    static String watcherScript(Path state,String id,Path launcher){
        return "$ErrorActionPreference='Stop'; $deadline=(Get-Date).AddMinutes(45); while((Get-Date) -lt $deadline){"
                +"Start-Sleep -Seconds 2; try{$s=Get-Content -Raw -LiteralPath "+quote(state.toString())+" | ConvertFrom-Json;"
                +"if($s.runId -eq "+quote(id)+" -and $s.phase -in @('COMPLETE','FAILED')){"
                +"while(Get-Process -Id "+ProcessHandle.current().pid()+" -ErrorAction SilentlyContinue){"
                +"if((Get-Date) -ge $deadline){exit 1};Start-Sleep -Milliseconds 250};"
                +"Start-Process -FilePath "+quote(launcher.toString())+";exit}}catch{}}";
    }
    private static void launchWatcher(Path system,Path state,String id,Path launcher)throws IOException{
        String encoded=Base64.getEncoder().encodeToString(watcherScript(state,id,launcher).getBytes(StandardCharsets.UTF_16LE));
        // This watcher stays in the original interactive user's session and never elevates.
        Process watcher=new ProcessBuilder(system.resolve("WindowsPowerShell/v1.0/powershell.exe").toString(),"-NoProfile","-NonInteractive",
                "-WindowStyle","Hidden","-EncodedCommand",encoded).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if(watcher.waitFor(1,TimeUnit.SECONDS))throw new IOException("The display restart watcher could not start. SmartStock will stay open.");
        }catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IOException("Interrupted while starting display restart watcher.",ex);}
    }
}
