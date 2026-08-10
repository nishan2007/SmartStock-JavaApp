package services;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CrossStoreSalesServiceTest {
    @Test void parsesCloudAndPostgresTimestampFormats() {
        Instant expected = Instant.parse("2026-08-09T20:14:15Z");

        assertEquals(expected, CrossStoreSalesService.time("2026-08-09T20:14:15Z").toInstant());
        assertEquals(expected, CrossStoreSalesService.time("2026-08-09 20:14:15+00:00").toInstant());
        assertEquals(expected, CrossStoreSalesService.time("2026-08-09T20:14:15").toInstant());
        assertEquals(Instant.parse("2026-08-10T00:14:33.833342Z"),
                CrossStoreSalesService.time("2026-08-09 20:14:33.833342-04").toInstant());
        assertNull(CrossStoreSalesService.time(""));
        assertNull(CrossStoreSalesService.time("not-a-date"));
    }
}
