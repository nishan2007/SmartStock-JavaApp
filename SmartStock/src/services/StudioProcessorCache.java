package services;

import java.util.function.LongSupplier;

/** One reusable native resource, serialized and discarded after failures or model changes. */
final class StudioProcessorCache<K,V extends AutoCloseable> implements AutoCloseable {
    interface Factory<V>{V open()throws Exception;}
    interface Operation<V,T>{T run(V value)throws Exception;}
    private K key;private V value;private long used;
    private final LongSupplier nanos;
    StudioProcessorCache(){this(System::nanoTime);}
    StudioProcessorCache(LongSupplier nanos){this.nanos=nanos;}
    synchronized <T>T use(K next,Factory<V> factory,Operation<V,T> operation)throws Exception{
        if(value==null||!next.equals(key)){close();value=factory.open();key=next;}
        try{return operation.run(value);}
        catch(Exception|Error ex){try{close();}catch(Exception close){ex.addSuppressed(close);}throw ex;}
        finally{used=nanos.getAsLong();}
    }
    synchronized void evictIfIdle(long idleNanos)throws Exception{if(value!=null&&nanos.getAsLong()-used>=idleNanos)close();}
    synchronized boolean matches(java.util.function.Predicate<K> predicate){return value!=null&&predicate.test(key);}
    public synchronized void close()throws Exception{V old=value;value=null;key=null;if(old!=null)old.close();}
}
