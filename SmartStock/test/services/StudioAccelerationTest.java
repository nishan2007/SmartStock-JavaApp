package services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;

class StudioAccelerationTest {
    @Test void failedGpuTileIsRetriedOnCpuAndWorkerIsClosed()throws Exception {
        String model=System.getProperty("smartstock.test.enhancementModel");
        org.junit.jupiter.api.Assumptions.assumeTrue(model!=null&&System.getProperty("os.name").startsWith("Windows"));
        boolean[] closed={false},continued={false};
        var backend=new StudioImageEnhancer.GpuBackend(){
            public java.nio.FloatBuffer run(float[] rgb,int w,int h,java.util.function.BooleanSupplier c)throws Exception{throw new java.io.EOFException("GPU process exited mid-tile");}
            public void close(){closed[0]=true;}
        };
        try(var enhancer=new StudioImageEnhancer(java.nio.file.Path.of(model),(p,c)->backend)){
            var image=new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);image.setRGB(10,10,0xff776655);
            var result=enhancer.enhance(image,2,StudioImageEnhancer.Mode.GPU,(d,c,t,n)->{if(n.contains("continued"))continued[0]=true;});
            assertEquals(64,result.getWidth());assertEquals(0,result.getRGB(0,0));assertTrue(closed[0]);assertTrue(continued[0]);
        }
    }
    @Test void failedGpuInitializationFallsBackToRealCpuAndCancellationStopsBeforeInference()throws Exception {
        String model=System.getProperty("smartstock.test.enhancementModel");
        org.junit.jupiter.api.Assumptions.assumeTrue(model!=null);
        var image=new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        image.setRGB(8,8,0x8044aaee);boolean[] fallback={false};
        try(var enhancer=new StudioImageEnhancer(java.nio.file.Path.of(model),(p,c)->{throw new java.io.IOException("Simulated unavailable device");})){
            var out=enhancer.enhance(image,2,StudioImageEnhancer.Mode.GPU,(d,c,t,n)->{if(d.equals("CPU")&&n.contains("unavailable"))fallback[0]=true;});
            assertEquals(64,out.getWidth());assertEquals(0,out.getRGB(0,0));
            if(System.getProperty("os.name").startsWith("Windows"))assertTrue(fallback[0]);
        }
        try(var enhancer=new StudioImageEnhancer(java.nio.file.Path.of(model))){
            assertThrows(java.util.concurrent.CancellationException.class,()->enhancer.enhance(image,2,StudioImageEnhancer.Mode.AUTO,new StudioImageEnhancer.Progress(){
                public void update(String d,int c,int t,String n){}public boolean cancelled(){return true;}
            }));
        }
    }
    @Test void lowMemoryReducesTilesAndKeepsCpuCapacityForTheStore(){
        assertEquals(48,StudioImageEnhancer.tileSize(256L*1024*1024));
        assertEquals(96,StudioImageEnhancer.tileSize(1024L*1024*1024));
        assertEquals(160,StudioImageEnhancer.tileSize(4L*1024*1024*1024));
        assertEquals(2,StudioImageEnhancer.cpuThreads(256L*1024*1024,12));
        assertEquals(4,StudioImageEnhancer.cpuThreads(4L*1024*1024*1024,12));
        assertEquals(1,StudioImageEnhancer.cpuThreads(Long.MAX_VALUE,1));
        assertTrue(StudioImageEnhancer.memoryFailure(new RuntimeException("backend failed",new java.io.IOException("bad_alloc"))));
        assertFalse(StudioImageEnhancer.memoryFailure(new java.io.IOException("Invalid model input")));
    }
    @Test void progressAndCancellationAreRestrictedToRequestOwner(){
        String id=UUID.randomUUID().toString();
        try(var job=StudioJobs.begin("device:user",id)){
            job.update("GPU (DirectML)",3,10,"");
            assertEquals("unknown",StudioJobs.status("other:user",id,true).get("state"));
            assertFalse(job.cancelled());
            assertEquals(30,StudioJobs.status("device:user",id,false).get("percent"));
            StudioJobs.status("device:user",id,true);assertTrue(job.cancelled());
            assertThrows(IllegalArgumentException.class,()->StudioJobs.begin("other:user",id));
        }
        assertEquals("cancelled",StudioJobs.status("device:user",id,false).get("state"));
    }
    @Test void completedJobsDoNotAcceptCancellation(){
        String id=UUID.randomUUID().toString();
        try(var job=StudioJobs.begin("owner",id)){job.success();}
        assertEquals(false,StudioJobs.status("owner",id,true).get("cancelRequested"));
        assertThrows(IllegalArgumentException.class,()->StudioJobs.begin("owner","invalid"));
    }
}
