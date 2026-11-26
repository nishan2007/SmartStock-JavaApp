package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class SharedLabelQueueTest {
    @TempDir Path root;
    private static final String HOST = "host-device", CLIENT = "other-device";
    private static JsonObject payload() throws Exception {
        JsonObject p = SharedLabelPrinting.request("ORDER"); p.addProperty("count", 2);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(600, 300, BufferedImage.TYPE_INT_RGB), "png", out);
        JsonArray images = new JsonArray(); images.add(Base64.getEncoder().encodeToString(out.toByteArray()));
        p.add("images", images); return p;
    }
    private SharedLabelQueue online() throws Exception {
        SharedLabelQueue q = new SharedLabelQueue(root, Clock.systemUTC());
        q.configure(1, HOST, "Counter label printer", "CT221B", true); q.poll(1, HOST); return q;
    }
    @Test void isolatesStoresAndAllowsOnlyConfiguredHostToClaimAndAcknowledge() throws Exception {
        SharedLabelQueue q = online(); String id = UUID.randomUUID().toString();
        q.enqueue(1, CLIENT, id, payload());
        assertFalse(q.poll(2, HOST).has("job")); assertFalse(q.poll(1, CLIENT).has("job"));
        JsonObject job = q.poll(1, HOST).getAsJsonObject("job");
        assertEquals(id, job.get("id").getAsString()); assertEquals("SENDING", job.get("state").getAsString());
        assertThrows(IllegalArgumentException.class, () -> q.finish(1, CLIENT, id, true, "ok"));
        assertEquals("SPOOLED", q.finish(1, HOST, id, true, "ok").get("state").getAsString());
        assertFalse(q.poll(1, HOST).has("job"));
    }
    @Test void survivesRestartWithoutReplayingClaimedJobsAndDeduplicatesSubmissions() throws Exception {
        SharedLabelQueue q = online(); String id = UUID.randomUUID().toString();
        q.enqueue(1, CLIENT, id, payload()); q.enqueue(1, CLIENT, id, payload());
        assertEquals(1, q.status(1).getAsJsonArray("jobs").size());
        q.poll(1, HOST);
        SharedLabelQueue restarted = new SharedLabelQueue(root, Clock.systemUTC());
        assertFalse(restarted.status(1).get("online").getAsBoolean());
        assertFalse(restarted.poll(1, HOST).has("job"));
        assertEquals("SENDING", restarted.enqueue(1, CLIENT, id, payload()).get("state").getAsString());
        assertThrows(IllegalArgumentException.class, () -> restarted.enqueue(1, "intruder", id, payload()));
    }
    @Test void rejectsOfflineHostAndCancelsQueuedJobsWhenHostChangesOrSharingStops() throws Exception {
        SharedLabelQueue offline = new SharedLabelQueue(root, Clock.systemUTC());
        assertThrows(IllegalStateException.class, () -> offline.enqueue(1, CLIENT, UUID.randomUUID().toString(), payload()));
        SharedLabelQueue q = online(); final SharedLabelQueue queue = q;
        String id = UUID.randomUUID().toString(); q.enqueue(1, CLIENT, id, payload());
        assertThrows(IllegalArgumentException.class, () -> queue.configure(1, "new", "", "office", true));
        assertEquals(HOST, q.status(1).get("device").getAsString());
        q.configure(1, "new", "New counter", "CT221B USB", true);
        assertEquals("CANCELLED", q.status(1).getAsJsonArray("jobs").get(0).getAsJsonObject().get("state").getAsString());
        q.poll(1, "new"); q.enqueue(1, CLIENT, UUID.randomUUID().toString(), payload());
        q.configure(1, "new", null, null, false);
        assertFalse(q.poll(1, "new").has("job"));
        for (JsonElement j : q.status(1).getAsJsonArray("jobs")) assertEquals("CANCELLED", j.getAsJsonObject().get("state").getAsString());
    }
    @Test void expiresStaleQueuedJobsAndMakesUncertainClaimsVisible() throws Exception {
        SharedLabelQueue q = online(); q.enqueue(1, CLIENT, UUID.randomUUID().toString(), payload());
        q.enqueue(1, CLIENT, UUID.randomUUID().toString(), payload()); q.poll(1, HOST);
        SharedLabelQueue later = new SharedLabelQueue(root, Clock.offset(Clock.systemUTC(), Duration.ofMinutes(11)));
        Set<String> states = new HashSet<>();
        for (JsonElement j : later.status(1).getAsJsonArray("jobs")) {
            states.add(j.getAsJsonObject().get("state").getAsString()); assertFalse(j.getAsJsonObject().has("payload"));
        }
        assertEquals(Set.of("EXPIRED", "UNKNOWN"), states); assertFalse(later.poll(1, HOST).has("job"));
    }
    @Test void rejectsMalformedImagesOversizedRasterAndInvalidPhysicalDimensions() throws Exception {
        JsonObject p = payload(); p.addProperty("count", 101);
        assertThrows(IllegalArgumentException.class, () -> SharedLabelPrinting.validatePayload(p));
        p.addProperty("count", 1); p.getAsJsonArray("images").set(0, new JsonPrimitive("not an image"));
        assertThrows(Exception.class, () -> SharedLabelPrinting.validatePayload(p));
        JsonObject price = payload(); price.addProperty("action", "PRICE"); price.addProperty("count", 1);
        price.addProperty("width", 100); price.addProperty("height", 1); price.addProperty("gap", 0.1);
        assertThrows(IllegalArgumentException.class, () -> SharedLabelPrinting.validatePayload(price));
        ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(1201, 10, BufferedImage.TYPE_INT_RGB), "png", out);
        JsonObject big = payload(); big.getAsJsonArray("images").set(0, new JsonPrimitive(Base64.getEncoder().encodeToString(out.toByteArray())));
        assertThrows(IllegalArgumentException.class, () -> SharedLabelPrinting.validatePayload(big));
    }
}
