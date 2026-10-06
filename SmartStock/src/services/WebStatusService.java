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
        result.put("cpuPercent",os instanceof com.sun.management.OperatingSystemMXBean extended ? extended.getProcessCpuLoad()*100 : -1);
        return result;
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
