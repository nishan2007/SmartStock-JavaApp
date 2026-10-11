package services;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Fixed installed tasks only. Browser/register input can never become a shell command. */
final class WebTunnelControl {
    static String task(String id){return switch(id){case "websiteTunnel"->"SmartStockStorefrontTunnel";
        case "schedulerTunnel"->"SmartStockSchedulerTunnel";default->throw new IllegalArgumentException("Unknown tunnel.");};}
    static Path directory(String id){task(id);return Path.of(System.getProperty("user.home"),".smartstock",
            id.equals("websiteTunnel")?"storefront-tunnel":"scheduler-tunnel").toAbsolutePath().normalize();}
    static boolean windows(){return System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("windows");}
    static List<ProcessHandle> connectors(String id){
        String expected=directory(id).toString().toLowerCase(Locale.ROOT);
        if(windows())return windowsConnectors(expected);
        return ProcessHandle.allProcesses().filter(p->{var info=p.info();
            String command=info.command().orElse("").toLowerCase(Locale.ROOT);
            String line=info.commandLine().orElse("").toLowerCase(Locale.ROOT);
            return command.endsWith("cloudflared.exe")&&line.contains(expected);
        }).toList();
    }
    private static List<ProcessHandle> windowsConnectors(String expected){
        // Windows Java ProcessHandle does not expose commandLine reliably.
        // CIM verifies the executable name and installed tunnel directory;
        // only process IDs leave the child process, never connector tokens.
        var exe=Path.of(System.getenv("SystemRoot"),"System32","WindowsPowerShell","v1.0","powershell.exe");
        String script="$ErrorActionPreference='Stop';Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'cloudflared.exe' -and $_.CommandLine -and $_.CommandLine.ToLowerInvariant().Contains($env:SMARTSTOCK_TUNNEL_DIRECTORY) } | ForEach-Object { [Console]::WriteLine($_.ProcessId) }";
        Process process=null;
        try{
            var builder=new ProcessBuilder(exe.toString(),"-NoProfile","-NonInteractive","-Command",script).redirectError(ProcessBuilder.Redirect.DISCARD);
            builder.environment().put("SMARTSTOCK_TUNNEL_DIRECTORY",expected);process=builder.start();
            if(!process.waitFor(5,TimeUnit.SECONDS))throw new IllegalStateException("Tunnel status check timed out.");
            if(process.exitValue()!=0)throw new IllegalStateException("Tunnel process status could not be checked.");
            String output=new String(process.getInputStream().readNBytes(4096),java.nio.charset.StandardCharsets.US_ASCII);
            return output.lines().filter(line->line.matches("[0-9]+"))
                    .map(Long::parseLong).map(ProcessHandle::of).flatMap(Optional::stream).filter(ProcessHandle::isAlive).toList();
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Tunnel status check interrupted.",e);}
        catch(java.io.IOException e){throw new IllegalStateException("Tunnel process status could not be checked.",e);}
        finally{if(process!=null&&process.isAlive())process.destroyForcibly();}
    }
    static Map<String,Object> status(String id){
        var processes=connectors(id);var out=new LinkedHashMap<String,Object>();
        out.put("running",!processes.isEmpty());out.put("connectors",processes.size());
        out.put("startedAt",processes.stream().map(p->p.info().startInstant().orElse(Instant.now()).toEpochMilli()).min(Long::compare).orElse(0L));
        out.put("cpuSeconds",processes.stream().mapToDouble(p->p.info().totalCpuDuration().orElse(java.time.Duration.ZERO).toMillis()/1000.0).sum());
        out.put("supported",windows());return out;
    }
    static synchronized void control(String id,String action)throws Exception{
        String name=task(id);if(!windows())throw new IllegalStateException("Tunnel controls currently require the Windows store server.");
        if(!Set.of("START","STOP","RESTART").contains(action))throw new IllegalArgumentException("Unknown tunnel action.");
        if(action.equals("START")&&!connectors(id).isEmpty())return;
        if(!action.equals("START")){
            powershell("Stop-ScheduledTask -TaskName '"+name+"'");
            for(var process:connectors(id)){
                process.destroy();try{process.onExit().get(5,TimeUnit.SECONDS);}catch(java.util.concurrent.TimeoutException ex){
                    process.destroyForcibly();process.onExit().get(5,TimeUnit.SECONDS);
                }
            }
            if(!connectors(id).isEmpty())throw new IllegalStateException("The connector did not stop. Check the server account permissions.");
        }
        if(!action.equals("STOP")){
            powershell("Start-ScheduledTask -TaskName '"+name+"'");
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(connectors(id).isEmpty()&&System.nanoTime()<deadline)Thread.sleep(200);
            if(connectors(id).isEmpty())throw new IllegalStateException("The installed tunnel task did not start a connector. Check its last result on the server.");
        }
    }
    private static void powershell(String command)throws Exception{
        String systemRoot=System.getenv("SystemRoot");if(systemRoot==null)throw new IllegalStateException("Windows PowerShell is unavailable.");
        var exe=Path.of(systemRoot,"System32","WindowsPowerShell","v1.0","powershell.exe");
        Process p=new ProcessBuilder(exe.toString(),"-NoProfile","-NonInteractive","-Command","$ErrorActionPreference='Stop';"+command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if(!p.waitFor(15,TimeUnit.SECONDS)){p.destroyForcibly();throw new IllegalStateException("Tunnel control timed out.");}
        if(p.exitValue()!=0)throw new IllegalStateException("Could not control the installed tunnel task. Check that it is installed for this server account.");
    }
}
