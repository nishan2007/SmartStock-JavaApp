package services;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.*;

/** Shares a verified, warm removal session across foreground and queue jobs. */
final class StudioRemovalRuntime {
    private record Key(Path model,StudioQuality quality,StudioImageEnhancer.Mode mode,long size,java.nio.file.attribute.FileTime modified,Object fileKey){}
    private static final StudioProcessorCache<Key,CatalogStudioImageProcessor> CACHE=new StudioProcessorCache<>();
    static {
        var timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"studio-removal-idle");t.setDaemon(true);return t;});
        timer.scheduleWithFixedDelay(()->{try{CACHE.evictIfIdle(TimeUnit.MINUTES.toNanos(15));}catch(Exception ignored){}},1,1,TimeUnit.MINUTES);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{try{CACHE.close();}catch(Exception ignored){}},"studio-removal-close"));
    }
    static <T>T use(StudioQuality quality,StudioImageEnhancer.Mode mode,Path explicitModel,StudioImageEnhancer.Progress progress,StudioProcessorCache.Operation<CatalogStudioImageProcessor,T> operation)throws Exception{
        long start=System.nanoTime();boolean managed=explicitModel==null;
        Path path=(managed?CatalogStudioImageProcessor.packagedModel(quality):explicitModel).toAbsolutePath().normalize();
        BasicFileAttributes a=Files.readAttributes(path,BasicFileAttributes.class);
        StudioImageEnhancer.Mode effective=mode==StudioImageEnhancer.Mode.CPU?mode:StudioImageEnhancer.Mode.AUTO;
        Key key=new Key(path,quality,effective,a.size(),a.lastModifiedTime(),a.fileKey());
        return CACHE.use(key,()->{
            long reserve=Math.max(768L*1024*1024,a.size()+512L*1024*1024);
            if(physicalMemory()<reserve||heapHeadroom()<256L*1024*1024)throw new IllegalStateException("Not enough free memory to load the "+quality+" background remover. Close other applications and try again.");
            var processor=new CatalogStudioImageProcessor(path,quality,managed,effective);
            System.out.printf(java.util.Locale.ROOT,"SmartStudio removal quality=%s loadMs=%d%n",quality,(System.nanoTime()-start)/1_000_000);return processor;
        },processor->{
            processor.progress(progress);long run=System.nanoTime();
            try{return operation.run(processor);}finally{processor.progress(null);System.out.printf(java.util.Locale.ROOT,"SmartStudio removal quality=%s totalMs=%d%n",quality,(System.nanoTime()-run)/1_000_000);}
        });
    }
    static boolean canPrepare(){
        boolean warm=CACHE.matches(key->key.quality()==StudioQuality.BEST&&key.mode()!=StudioImageEnhancer.Mode.CPU);
        return enoughMemory(physicalMemory(),heapHeadroom(),warm);
    }
    static boolean hasSession(){return CACHE.matches(key->true);}
    static boolean enoughMemory(long physical,long heap,boolean warm){return physical>=(warm?768L*1024*1024:3L*1024*1024*1024)&&heap>=256L*1024*1024;}
    static long physicalMemory(){return StudioMemory.availablePhysical();}
    private static long heapHeadroom(){Runtime r=Runtime.getRuntime();return r.maxMemory()-r.totalMemory()+r.freeMemory();}
}
