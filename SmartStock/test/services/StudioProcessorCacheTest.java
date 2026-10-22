package services;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class StudioProcessorCacheTest {
    @Test void backgroundWorkChecksNativeRamSeparatelyFromJavaHeap(){
        assertTrue(StudioRemovalRuntime.enoughMemory(4L*1024*1024*1024,512L*1024*1024,false));
        assertFalse(StudioRemovalRuntime.enoughMemory(512L*1024*1024,4L*1024*1024*1024,false));
        assertTrue(StudioRemovalRuntime.enoughMemory(1024L*1024*1024,512L*1024*1024,true));
        assertFalse(StudioRemovalRuntime.enoughMemory(4L*1024*1024*1024,128L*1024*1024,true));
    }
    static final class Resource implements AutoCloseable {boolean closed;public void close(){closed=true;}}
    @Test void repeatedJobsReuseOneSessionButModelChangesCloseTheOldOne()throws Exception{
        var cache=new StudioProcessorCache<String,Resource>();var first=new Resource();var second=new Resource();
        assertSame(first,cache.use("model-fingerprint",()->first,value->value));
        assertSame(first,cache.use("model-fingerprint",()->{throw new AssertionError("Reloaded warm session");},value->value));
        assertSame(second,cache.use("new-model-fingerprint",()->second,value->value));assertTrue(first.closed);assertFalse(second.closed);cache.close();assertTrue(second.closed);cache.close();
    }
    @Test void failureDoesNotLeaveAPoisonedNativeSessionInTheCache()throws Exception{
        var cache=new StudioProcessorCache<String,Resource>();var broken=new Resource();
        assertThrows(java.io.IOException.class,()->cache.use("model",()->broken,value->{throw new java.io.IOException("worker failed");}));assertTrue(broken.closed);
        var next=new Resource();assertSame(next,cache.use("model",()->next,value->value));cache.close();
    }
    @Test void idleEvictionFreesResourcesAndUseResetsTheDeadline()throws Exception{
        var time=new AtomicLong();var cache=new StudioProcessorCache<String,Resource>(time::get);var resource=new Resource();
        cache.use("model",()->resource,value->null);time.set(9);cache.evictIfIdle(10);assertFalse(resource.closed);
        cache.use("model",()->{throw new AssertionError();},value->null);time.set(18);cache.evictIfIdle(10);assertFalse(resource.closed);
        time.set(19);cache.evictIfIdle(10);assertTrue(resource.closed);
    }
}
