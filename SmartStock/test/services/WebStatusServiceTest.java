package services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WebStatusServiceTest {
    @Test void onlyKnownServicesAndActionsCanBeControlled(){
        for(String id:java.util.List.of("website","mobile","registration","scheduler","websiteTunnel","schedulerTunnel"))
            for(String action:java.util.List.of("START","STOP","RESTART"))assertDoesNotThrow(()->WebStatusService.validateControl(id,action));
        for(String id:java.util.List.of("lan","postgres","../website","website;Stop-Process", ""))
            assertThrows(IllegalArgumentException.class,()->WebStatusService.validateControl(id,"STOP"));
        assertThrows(IllegalArgumentException.class,()->WebStatusService.validateControl("website","DELETE"));
    }
    @Test void tunnelTaskNamesCannotBeInjected(){
        assertEquals("SmartStockStorefrontTunnel",WebTunnelControl.task("websiteTunnel"));
        assertEquals("SmartStockSchedulerTunnel",WebTunnelControl.task("schedulerTunnel"));
        assertThrows(IllegalArgumentException.class,()->WebTunnelControl.task("websiteTunnel';Stop-Process"));
    }
    @Test void statusExposesOnlyAggregateMetrics(){
        WebRuntimeMetrics.started("test");var metrics=WebRuntimeMetrics.snapshot("test");
        assertEquals(true,metrics.get("available"));assertEquals(0L,metrics.get("requests"));
        assertTrue((Long)metrics.get("startedAt")>0);
        assertEquals(java.util.Set.of("available","startedAt","requests","errors","responseBytes"),metrics.keySet());
        assertEquals(false,WebRuntimeMetrics.snapshot("unknown").get("available"));
    }
    @Test void usageReportsRealServerUptimeAndHeap(){
        var usage=WebStatusService.resourceUsage();assertTrue((Long)usage.get("uptimeMs")>0);
        assertTrue((Long)usage.get("heapUsed")>0);assertTrue((Integer)usage.get("threads")>0);
    }
    @Test void actualResponsesCountRequestsBytesAndServerFailures()throws Exception{
        String id="test-http";WebRuntimeMetrics.started(id);
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var recorded=new java.util.concurrent.CountDownLatch(2);
        server.createContext("/",x->{try{
            int code=x.getRequestURI().getPath().equals("/failure")?503:200;
            byte[] body="hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            x.sendResponseHeaders(code,body.length);x.getResponseBody().write(body);
        }finally{WebRuntimeMetrics.record(id,x);x.close();recorded.countDown();}});
        server.start();try{
            var http=java.net.http.HttpClient.newHttpClient();String base="http://127.0.0.1:"+server.getAddress().getPort();
            for(String path:java.util.List.of("/ok","/failure"))http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+path)).GET().build(),java.net.http.HttpResponse.BodyHandlers.discarding());
            assertTrue(recorded.await(5,java.util.concurrent.TimeUnit.SECONDS));
            var metrics=WebRuntimeMetrics.snapshot(id);assertEquals(2L,metrics.get("requests"));assertEquals(1L,metrics.get("errors"));assertEquals(10L,metrics.get("responseBytes"));
        }finally{server.stop(0);}
    }
}
