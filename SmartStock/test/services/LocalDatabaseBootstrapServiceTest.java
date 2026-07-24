package services;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDatabaseBootstrapServiceTest {
    @Test
    void generatesPrivateLoopbackServerConfiguration() throws Exception {
        String source = Files.readString(
                Path.of("src/services/LocalDatabaseBootstrapService.java"));

        assertTrue(source.contains("SecureRandom"));
        assertTrue(source.contains("smartstock_server"));
        assertTrue(source.contains("jdbc:postgresql://127.0.0.1:5432/"));
        assertTrue(source.contains("ALTER SYSTEM SET listen_addresses = 'localhost'"));
        assertTrue(source.contains("configured.save()"));
        assertTrue(source.contains("Arrays.fill(supplied, '\\0')"));
    }
}
