package services;

/** Run with both a full JDK and the installed runtime that omits jdk.management. */
public final class VerifyWebStatusRuntime {
    public static void main(String[] args){
        var usage=WebStatusService.resourceUsage();
        for(String key:java.util.List.of("uptimeMs","heapUsed","heapMax","threads","cpuPercent"))
            if(!(usage.get(key) instanceof Number))throw new AssertionError("Missing metric: "+key);
        boolean provider=ModuleLayer.boot().findModule("jdk.management").isPresent();
        double cpu=((Number)usage.get("cpuPercent")).doubleValue();
        if(cpu < -1 || cpu > 100)throw new AssertionError("Invalid CPU value: "+cpu);
        if(!provider&&ProcessHandle.current().info().totalCpuDuration().isPresent()&&cpu<0)
            throw new AssertionError("CPU fallback did not report data");
        System.out.println("Web Status metrics loaded; CPU provider available: "+provider);
        if(args.length>0&&args[0].equals("--tunnels")){
            for(String id:java.util.List.of("websiteTunnel","schedulerTunnel")){
                var status=WebTunnelControl.status(id);
                System.out.println(id+": "+status);
                if(!Boolean.TRUE.equals(status.get("running")))throw new AssertionError("Expected live tunnel: "+id);
            }
        }
    }
}
