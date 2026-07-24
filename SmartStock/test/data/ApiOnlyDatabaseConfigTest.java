package data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiOnlyDatabaseConfigTest {
    @Test
    void apiOnlyServerConfigurationContainsNoCloudDatabaseCredentials() {
        DatabaseConfig config = config("smartstock_local", "local-password");

        assertFalse(config.hasUnresolvedCredentialPlaceholders());
        assertFalse(java.util.Arrays.stream(DatabaseConfig.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .anyMatch(name -> name.toLowerCase().contains("cloud")));
    }

    @Test
    void unresolvedLocalDatabaseLabelsStillBlockStartup() {
        DatabaseConfig config = config("SMARTSTOCK_DB_USER", "SMARTSTOCK_DB_PASSWORD");

        assertTrue(config.hasUnresolvedCredentialPlaceholders());
    }

    private static DatabaseConfig config(String dbUser, String dbPassword) {
        return new DatabaseConfig(
                DatabaseMode.SERVER,
                "jdbc:postgresql://127.0.0.1:5432/smartstock",
                dbUser,
                dbPassword,
                "127.0.0.1",
                5432,
                1,
                60);
    }
}
