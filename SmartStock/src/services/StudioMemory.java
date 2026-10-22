package services;

/** Native memory checks also work in older updater runtimes without jdk.management. */
final class StudioMemory {
    private StudioMemory() { }
    static long availablePhysical() {
        try {
            var os=java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if(os instanceof com.sun.management.OperatingSystemMXBean extended)
                return Math.max(0,extended.getFreeMemorySize());
        } catch(LinkageError unavailable) { /* Older packaged Java runtime. */ }
        return nativeAvailable();
    }
    static long nativeAvailable() {
        if(com.sun.jna.Platform.isWindows())try {
            var memory=new com.sun.jna.platform.win32.WinBase.MEMORYSTATUSEX();
            if(com.sun.jna.platform.win32.Kernel32.INSTANCE.GlobalMemoryStatusEx(memory))
                return Math.max(0,memory.ullAvailPhys.longValue());
        } catch(LinkageError | RuntimeException unavailable) { /* Fail closed for model preparation. */ }
        return 0;
    }
}
