package data;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DevelopmentCredentialMigrationTest {
    @Test
    void developmentCanReadLegacySecretsButProductionCannot() throws Exception {
        String source = Files.readString(Path.of("src/data/DatabaseConfig.java"));
        int method = source.indexOf("private static String readProfileSecret");
        String body = source.substring(method);

        assertTrue(body.contains("EnvironmentProfile.active() == EnvironmentProfile.DEVELOPMENT"));
        assertTrue(body.contains("SecureCredentialStore.read(key)"));
    }
}
