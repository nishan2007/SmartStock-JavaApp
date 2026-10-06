package services;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class WebPublicHealthTest {
    @Test
    void slowPublicProbeDoesNotBlockLocalStatusOrStartDuplicateProbes() throws Exception {
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1),finished=new CountDownLatch(1);
        try {
            var initial=assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> WebPublicHealth.status(() -> {
                started.countDown();
                try { release.await(5,TimeUnit.SECONDS); }
                catch(InterruptedException ex){Thread.currentThread().interrupt();}
                finally { finished.countDown(); }
                return List.of(WebStatusService.service("downloads","Test","",true,"",false,"Ready"));
            }));
            assertEquals(2,initial.size());
            assertTrue(started.await(1,TimeUnit.SECONDS));
            var during=assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> WebPublicHealth.status(() -> {
                fail("A concurrent request must reuse the in-progress check");
                return List.of();
            }));
            assertEquals(initial,during);
        } finally { release.countDown();assertTrue(finished.await(2,TimeUnit.SECONDS)); }
    }
}
