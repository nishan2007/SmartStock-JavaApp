package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import services.LanApiClient;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class StudioBadgeCaptureTest {
  private StudioBadgeCapture.Reader reader(boolean present,String value){return new StudioBadgeCapture.Reader(){public boolean available(){return present;}public String read(Duration timeout){assertEquals(Duration.ofSeconds(20),timeout);return value;}};}
  @Test void rejectsMissingReaderAndUidWithoutConsultingServer(){
    StudioBadgeCapture.Policy forbidden=(badge,location)->{fail("Invalid capture must not reach server");return null;};
    assertThrows(IllegalStateException.class,()->StudioBadgeCapture.capture(1,reader(false,""),forbidden));
    assertThrows(IllegalArgumentException.class,()->StudioBadgeCapture.capture(1,reader(true,"04AB1234"),forbidden));
  }
  @Test void enforcesServerPolicyAndDoesNotExposeCredentialInDescription()throws Exception{
    var captured=StudioBadgeCapture.capture(2,reader(true,"SSB1123456"),(badge,location)->{assertEquals(2,location);assertEquals("SSB1123456",badge);return new LanApiClient.BadgeStatus(true,true);});
    assertTrue(captured.policy().pinRequired());assertFalse(captured.toString().contains(captured.identifier()));
    assertThrows(SecurityException.class,()->StudioBadgeCapture.capture(2,reader(true,"SSB1123456"),(badge,location)->{throw new SecurityException("Revoked badge");}));
  }
  @Test void automaticPollingUsesShortReadsAndSkipsHeldCards()throws Exception{
    StudioBadgeCapture.Reader reader=new StudioBadgeCapture.Reader(){
      public boolean available(){return true;}
      public String read(Duration timeout){assertEquals(Duration.ofSeconds(2),timeout);return "SSB1123456";}
    };
    StudioBadgeCapture.Policy forbidden=(badge,location)->{fail("Held badge must not call server again");return null;};
    assertNull(StudioBadgeCapture.poll(1,"SSB1123456",reader,forbidden));
    assertNull(StudioBadgeCapture.poll(1,null,reader(false,""),forbidden));
    assertNotNull(StudioBadgeCapture.poll(1,null,reader,(badge,location)->new LanApiClient.BadgeStatus(true,true)));
    assertThrows(IllegalArgumentException.class,()->StudioBadgeCapture.poll(0,null,reader,forbidden));
    StudioBadgeCapture.Reader uid=new StudioBadgeCapture.Reader(){public boolean available(){return true;}public String read(Duration timeout){return "04AB1234";}};
    assertThrows(IllegalArgumentException.class,()->StudioBadgeCapture.poll(1,null,uid,forbidden));
  }
}
