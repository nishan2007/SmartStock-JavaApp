package services;

import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SchemaContractValidationIntegrationTest {
    @Test
    void installedLocalV1CatalogMatchesItsRecordedFingerprint() throws Exception {
        String jdbcUrl = System.getProperty("smartstock.test.jdbc", "");
        String user = System.getProperty("smartstock.test.dbUser", "");
        String password = System.getProperty("smartstock.test.dbPassword", "");
        assumeTrue(!jdbcUrl.isBlank() && !user.isBlank());

        try (var connection = DriverManager.getConnection(jdbcUrl, user, password)) {
            SchemaContractService.requireLocalReady(connection);
            var readiness = SchemaContractService.validateLocal(connection);
            assertTrue(readiness.ready(), readiness.message());
            assertEquals(SchemaContractService.BASELINE_VERSION, readiness.version());
        }
    }
}
