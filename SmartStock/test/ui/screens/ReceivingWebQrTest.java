package ui.screens;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReceivingWebQrTest {
    @Test void activationQrOpensReceivingAndPreservesOneTimeToken() {
        assertEquals("https://store.local:8444/receiving.html#activate=example-token",
                MobileItemWebDialog.receivingUrl("https://store.local:8444/?activate=example-token"));
        assertEquals("https://store.local:8444/receiving.html#activate=example-token",
                MobileItemWebDialog.receivingUrl("https://store.local:8444/#activate=example-token"));
    }
    @Test void plainAddressOpensReceivingWithoutAnActivation() {
        assertEquals("https://store.local:8444/receiving.html",
                MobileItemWebDialog.receivingUrl("https://store.local:8444/"));
        assertEquals("",MobileItemWebDialog.receivingUrl(""));
        assertEquals("",MobileItemWebDialog.receivingUrl(null));
    }
}
