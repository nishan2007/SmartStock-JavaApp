package services;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
class StudioSessionTimeoutTest {
 @Test void studioDoesNotExpireWithIdleOrAbsoluteTime(){
  Instant now=Instant.parse("2026-10-15T00:00:00Z");
  assertFalse(LanApiServer.sessionTimedOut(true,now.minusSeconds(3600),now.minusSeconds(1),now));
 }
 @Test void registerIdleAndAbsoluteTimeoutsRemainEnforced(){
  Instant now=Instant.parse("2026-10-15T00:00:00Z");
  assertTrue(LanApiServer.sessionTimedOut(false,now.minusSeconds(1),now.plusSeconds(3600),now));
  assertTrue(LanApiServer.sessionTimedOut(false,now.plusSeconds(3600),now.minusSeconds(1),now));
  assertFalse(LanApiServer.sessionTimedOut(false,now.plusSeconds(60),now.plusSeconds(3600),now));
 }
}
