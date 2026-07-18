package services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class LanApiClientTransportTest {
    @AfterEach
    void reset() {
        LanApiClient.resetTransportForTests();
    }

    @Test
    void reusesClientUntilTransportStateIsReset() throws Exception {
        LanApiClient.resetTransportForTests();
        HttpClient first = LanApiClient.bootstrapClientForTests();
        assertSame(first, LanApiClient.bootstrapClientForTests());

        LanApiClient.resetTransportForTests();
        assertNotSame(first, LanApiClient.bootstrapClientForTests());
    }
}
