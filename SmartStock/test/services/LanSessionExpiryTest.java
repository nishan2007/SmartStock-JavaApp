package services;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanSessionExpiryTest {
    private static final Instant LOGIN = Instant.parse("2026-11-25T12:00:00Z");
    private static final Instant ABSOLUTE_EXPIRY = LOGIN.plus(Duration.ofHours(12));

    @Test
    void inactivityDisabledKeepsRegisterSessionValidPastFifteenMinutes() {
        var policy = new LanApiServer.DeviceSessionPolicy(false, false, 15);
        Instant expires = LanApiServer.nextSessionExpiry(false, policy, LOGIN, ABSOLUTE_EXPIRY);

        assertEquals(ABSOLUTE_EXPIRY, expires);
        assertFalse(LanApiServer.sessionTimedOut(false, expires, ABSOLUTE_EXPIRY,
                LOGIN.plus(Duration.ofMinutes(16))));
        assertTrue(LanApiServer.sessionTimedOut(false, expires, ABSOLUTE_EXPIRY,
                ABSOLUTE_EXPIRY.plusSeconds(1)));
    }

    @Test
    void staySignedInOverridesTheIdleTimeout() {
        var policy = new LanApiServer.DeviceSessionPolicy(true, true, 15);

        assertEquals(ABSOLUTE_EXPIRY, LanApiServer.nextSessionExpiry(false, policy, LOGIN, ABSOLUTE_EXPIRY));
    }

    @Test
    void inactivityEnabledRetainsRollingIdleExpiryAndAbsoluteLimit() {
        var policy = new LanApiServer.DeviceSessionPolicy(false, true, 15);

        assertEquals(LOGIN.plus(Duration.ofMinutes(15)),
                LanApiServer.nextSessionExpiry(false, policy, LOGIN, ABSOLUTE_EXPIRY));
        assertEquals(LOGIN.plus(Duration.ofMinutes(20)), LanApiServer.nextSessionExpiry(false, policy,
                LOGIN.plus(Duration.ofMinutes(5)), ABSOLUTE_EXPIRY));
        assertEquals(ABSOLUTE_EXPIRY, LanApiServer.nextSessionExpiry(false, policy,
                ABSOLUTE_EXPIRY.minus(Duration.ofMinutes(5)), ABSOLUTE_EXPIRY));
    }
}
