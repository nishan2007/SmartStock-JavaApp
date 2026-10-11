package services;

import java.lang.management.ManagementFactory;
import java.util.*;

/** Safe status DTOs shared by the LAN screen; operational details stay on the server. */
final class WebStatusService {
    static Map<String,Object> resourceUsage(){
        var memory=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        var result=new LinkedHashMap<String,Object>();result.put("uptimeMs",ManagementFactory.getRuntimeMXBean().getUptime());
        result.put("heapUsed",memory.getUsed());result.put("heapMax",memory.getMax());
        result.put("threads",ManagementFactory.getThreadMXBean().getThreadCount());
        var os=ManagementFactory.getOperatingSystemMXBean();
        result.put("cpuPercent",cpuPercent(os));
        return result;
    }
    // Older installed runtimes omit jdk.management. Its optional CPU provider
    // must not prevent the rest of the authenticated status response loading.
    static double cpuPercent(java.lang.management.OperatingSystemMXBean os){
        try{return os instanceof com.sun.management.OperatingSystemMXBean extended ? extended.getProcessCpuLoad()*100 : -1;}
        catch(LinkageError unavailable){
            // ProcessHandle is in java.base, so older packaged runtimes can
            // still report CPU usage without the optional management provider.
            return fallbackCpuPercent();
        }
    }
    private static long previousCpuNanos,previousWallNanos;
    private static synchronized double fallbackCpuPercent(){
        var duration=ProcessHandle.current().info().totalCpuDuration();
        if(duration.isEmpty())return -1;
        long cpu=duration.get().toNanos(),now=System.nanoTime();
        long elapsed=previousWallNanos==0?java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(ManagementFactory.getRuntimeMXBean().getUptime()):now-previousWallNanos;
        long used=cpu-previousCpuNanos;previousCpuNanos=cpu;previousWallNanos=now;
        return elapsed<=0?-1:Math.max(0,Math.min(100,100.0*used/elapsed/Runtime.getRuntime().availableProcessors()));
    }
    static Map<String,Object> service(String id,String name,String description,boolean running,String url,
                                       boolean controllable,String note){
        var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("name",name);row.put("description",description);
        row.put("running",running);row.put("url",url);row.put("controllable",controllable);row.put("note",note);
        row.put("metrics",WebRuntimeMetrics.snapshot(id));return row;
    }
    static void validateControl(String id,String action){
        if(!Set.of("website","mobile","registration","scheduler","websiteTunnel","schedulerTunnel").contains(id))
            throw new IllegalArgumentException("Choose a supported web service.");
        if(!Set.of("START","STOP","RESTART").contains(action))throw new IllegalArgumentException("Choose Start, Stop, or Restart.");
    }
}
