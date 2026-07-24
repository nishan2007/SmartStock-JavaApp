package services;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudSyncApiTest {
    @Test
    void buildsBoundedBatchedRpcRequest() {
        UUID eventId = UUID.randomUUID();
        JsonObject body = CloudSyncApi.requestBody(7, 42,
                List.of(new CloudSyncApi.OutboundEvent(eventId, "SALE_COMPLETED",
                        "register-1", 12, "{\"sale_id\":55}",
                        Instant.parse("2026-07-23T14:00:00Z"))), 10_000);

        assertEquals(7, body.get("p_location_id").getAsInt());
        assertEquals(42, body.get("p_cursor").getAsLong());
        assertEquals(500, body.get("p_limit").getAsInt());
        JsonObject event = body.getAsJsonArray("p_events").get(0).getAsJsonObject();
        assertEquals(eventId.toString(), event.get("event_id").getAsString());
        assertEquals(55, event.getAsJsonObject("payload").get("sale_id").getAsInt());
    }

    @Test
    void parsesAcknowledgementsAndCursorDeltas() throws Exception {
        UUID acknowledged = UUID.randomUUID();
        UUID changed = UUID.randomUUID();
        String response = """
                {
                  "acknowledged_event_ids":["%s"],
                  "changes":[{
                    "sequence":44,
                    "event_id":"%s",
                    "event_type":"PRODUCT_CHANGED",
                    "location_id":7,
                    "payload":{"product_id":91},
                    "origin_location_id":2,
                    "created_at":"2026-07-23T14:01:00Z"
                  }],
                  "next_cursor":44,
                  "has_more":false
                }
                """.formatted(acknowledged, changed);

        CloudSyncApi.ExchangeResponse parsed = CloudSyncApi.parseResponse(response, 42);

        assertTrue(parsed.acknowledgedEventIds().contains(acknowledged));
        assertEquals(1, parsed.changes().size());
        assertEquals(44, parsed.nextCursor());
        assertFalse(parsed.hasMore());
    }

    @Test
    void rejectsBackwardsOrMalformedCloudCursor() {
        assertThrows(IOException.class, () -> CloudSyncApi.parseResponse("""
                {"acknowledged_event_ids":[],"changes":[],"next_cursor":9,"has_more":false}
                """, 10));
        assertThrows(IOException.class, () -> CloudSyncApi.parseResponse("not-json", 0));
    }
}
