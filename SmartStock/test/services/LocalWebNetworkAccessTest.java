package services;
import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import static org.junit.jupiter.api.Assertions.*;
class LocalWebNetworkAccessTest {
 @Test void permitsLocalClientsAndRejectsPublicOrForwardedTraffic() throws Exception {
  for(String address:new String[]{"127.0.0.1","10.1.1.50","192.168.1.20","172.16.2.3","::1"}) {
   assertTrue(MobileItemWebServer.allowsLocalWebPeer(InetAddress.getByName(address),false));
   assertFalse(MobileItemWebServer.allowsLocalWebPeer(InetAddress.getByName(address),true));
  }
  for(String address:new String[]{"8.8.8.8","1.1.1.1","172.32.0.1"})
   assertFalse(MobileItemWebServer.allowsLocalWebPeer(InetAddress.getByName(address),false));
  assertFalse(MobileItemWebServer.allowsLocalWebPeer(null,false));
 }
}
