package services;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.CompletionException;
import static org.junit.jupiter.api.Assertions.*;

class BoundedImageBodyTest {
    @Test void cancelsBeforeCopyingAnOversizedChunk(){
        var body=new BoundedImageBody(4);var cancelled=new boolean[1];
        body.onSubscribe(new Flow.Subscription(){public void request(long n){}public void cancel(){cancelled[0]=true;}});
        var chunk=ByteBuffer.wrap(new byte[5]);body.onNext(List.of(chunk));
        assertTrue(cancelled[0]);assertEquals(0,chunk.position());
        assertThrows(CompletionException.class,()->body.getBody().toCompletableFuture().join());
        body.onComplete();assertTrue(body.getBody().toCompletableFuture().isCompletedExceptionally());
    }
    @Test void actualHttpHandlesExactLimitAndRejectsChunkedAndFixedOversize()throws Exception{
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            byte[] data=exchange.getRequestURI().getPath().equals("/exact")?new byte[]{1,2,3,4}:new byte[5];
            exchange.sendResponseHeaders(200,exchange.getRequestURI().getPath().equals("/chunked")?0:data.length);
            try(var output=exchange.getResponseBody()){output.write(data);}finally{exchange.close();}
        });server.start();
        try{
            var client=HttpClient.newHttpClient();String base="http://127.0.0.1:"+server.getAddress().getPort();
            var exact=HttpRequest.newBuilder(URI.create(base+"/exact")).timeout(Duration.ofSeconds(5)).build();
            assertArrayEquals(new byte[]{1,2,3,4},client.send(exact,BoundedImageBody.handler(4)).body());
            for(String path:List.of("/chunked","/fixed")){
                var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(5)).build();
                assertThrows(java.io.IOException.class,()->client.send(request,BoundedImageBody.handler(4)));
            }
        }finally{server.stop(0);}
    }
}
