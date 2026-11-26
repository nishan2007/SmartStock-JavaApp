package services;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoreNetworkProvisioningTest {
    @Test void managedWorkspaceLinksCannotSelectAnotherStoreOrSpoofTheOrigin(){
        assertEquals("https://store-7-web.deckers.gy/employees/mobile-management/",DeckersWorkspace.workspaceHome("https://store-7-web.deckers.gy",7));
        assertEquals(DeckersWorkspace.HOME,DeckersWorkspace.workspaceHome("https://store-8-web.deckers.gy",7));
        assertFalse(DeckersWorkspace.managedOrigin("https://store-7-web.deckers.gy.evil.example",7));
        assertFalse(DeckersWorkspace.managedOrigin("https://store-7-web.deckers.gy:443",7));
        assertFalse(DeckersWorkspace.managedOrigin("https://store-7-web.deckers.gy",null));
    }
    @Test void signedPayloadCanonicalizationHasStableUtf8Digest()throws Exception{
        assertEquals("POST\n/v1/provision\n1795440000000\nnonce\n44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a",
            StoreNetworkProvisioning.canonical("/v1/provision","1795440000000","nonce","{}"));
    }
}
