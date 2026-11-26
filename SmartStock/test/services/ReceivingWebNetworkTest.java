package services;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReceivingWebNetworkTest {
    @Test void rejectsPublicAndForwardedPeers() throws Exception {
        assertFalse(MobileItemWebServer.allowsLocalWebPeer(InetAddress.getByName("8.8.8.8"),false));
        assertFalse(MobileItemWebServer.allowsLocalWebPeer(InetAddress.getByName("192.168.1.5"),true));
        assertFalse(MobileItemWebServer.allowsLocalWebPeer(InetAddress.getByName("127.0.0.1"),true));
        assertFalse(MobileItemWebServer.allowsLocalWebPeer(null,false));
    }
    @Test void acceptsDirectLocalPeers() throws Exception {
        assertTrue(MobileItemWebServer.allowsLocalWebPeer(InetAddress.getByName("192.168.1.5"),false));
        assertTrue(MobileItemWebServer.allowsLocalWebPeer(InetAddress.getByName("10.0.0.5"),false));
        assertTrue(MobileItemWebServer.allowsLocalWebPeer(InetAddress.getByName("127.0.0.1"),false));
    }
}
