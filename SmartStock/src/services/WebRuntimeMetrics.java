package services;

import com.sun.net.httpserver.HttpExchange;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Aggregate counters only: no credentials, image bytes, URLs, or visitor identities. */
public final class WebRuntimeMetrics {
    private static final Map<String,WebRuntimeMetrics> ALL=new ConcurrentHashMap<>();
    private volatile long startedAt;
    private final AtomicLong requests=new AtomicLong(),errors=new AtomicLong(),bytes=new AtomicLong();
    public static void started(String id){ALL.computeIfAbsent(id,k->new WebRuntimeMetrics()).startedAt=System.currentTimeMillis();}
    public static void record(String id,HttpExchange x){
        var metric=ALL.computeIfAbsent(id,k->new WebRuntimeMetrics());metric.requests.incrementAndGet();
        if(x.getResponseCode()>=500)metric.errors.incrementAndGet();
        try{metric.bytes.addAndGet(Long.parseLong(x.getResponseHeaders().getFirst("Content-Length")));}catch(Exception ignored){}
    }
    static Map<String,Object> snapshot(String id){
        var m=ALL.get(id);
        return m==null?Map.of("available",false):Map.of("available",true,"startedAt",m.startedAt,
                "requests",m.requests.get(),"errors",m.errors.get(),"responseBytes",m.bytes.get());
    }
}
