package ui.screens;

import data.DatabaseConfig;
import data.DatabaseMode;
import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RegisterConnectionSetupTest {
    @Test
    void reopeningUsesTheActiveEndpointAndSavedStore() {
        DatabaseConfig config = new DatabaseConfig(DatabaseMode.CLIENT, "", "", "",
                "old-host", 8443, 17, 90);
        RegisterConnectionSetup.Settings settings = RegisterConnectionSetup.initialSettings(config,
                URI.create("https://rosehall-server:9443"));
        assertEquals("rosehall-server", settings.host());
        assertEquals(9443, settings.port());
        assertEquals(17, settings.location());
        assertEquals(90, settings.interval());
    }

    @Test
    void unconfiguredRegisterUsesSetupDefaults() {
        DatabaseConfig config = new DatabaseConfig(DatabaseMode.CLIENT, "", "", "",
                "127.0.0.1", 5432, null, 60);
        RegisterConnectionSetup.Settings settings = RegisterConnectionSetup.initialSettings(config,
                URI.create("https://127.0.0.1:8443"));
        assertEquals("POS-SERVER", settings.host());
        assertEquals(8443, settings.port());
        assertEquals(1, settings.location());
    }
}
