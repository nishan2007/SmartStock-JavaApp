package services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Cancels oversized HTTP bodies before buffering them, including chunked responses. */
final class BoundedImageBody implements HttpResponse.BodySubscriber<byte[]> {
    static final int STOREFRONT_LIMIT = 12 * 1024 * 1024;
    private final int limit;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private Flow.Subscription subscription;

    BoundedImageBody(int limit) {
        if (limit < 1) throw new IllegalArgumentException("A positive image size limit is required.");
        this.limit = limit;
    }
    static HttpResponse.BodyHandler<byte[]> handler(int limit) {return info -> new BoundedImageBody(limit);}
    @Override public CompletionStage<byte[]> getBody() {return result;}
    @Override public void onSubscribe(Flow.Subscription incoming) {
        if(subscription!=null){incoming.cancel();return;}
        subscription=incoming;incoming.request(1);
    }
    @Override public void onNext(List<ByteBuffer> buffers) {
        if(result.isDone())return;
        long received=bytes.size();
        for(var buffer:buffers){received+=buffer.remaining();if(received>limit){
            subscription.cancel();result.completeExceptionally(new IOException("Product image exceeds the supported size limit."));return;
        }}
        for(var buffer:buffers){byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);}
        subscription.request(1);
    }
    @Override public void onError(Throwable error) {result.completeExceptionally(error);}
    @Override public void onComplete() {result.complete(bytes.toByteArray());}
}
