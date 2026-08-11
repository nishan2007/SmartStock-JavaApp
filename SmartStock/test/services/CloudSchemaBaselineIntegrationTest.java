package services;

import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CloudSchemaBaselineIntegrationTest {
    @Test
    void cloudV1BaselineInstallsAndValidatesInEmptySupabaseEmulation()
            throws Exception {
        String jdbcUrl = System.getProperty("smartstock.test.jdbc", "");
        String user = System.getProperty("smartstock.test.dbUser", "");
        String password = System.getProperty("smartstock.test.dbPassword", "");
        assumeTrue(!jdbcUrl.isBlank() && !user.isBlank());

        try (var connection = DriverManager.getConnection(jdbcUrl, user, password)) {
            SchemaContractService.installCloudBaseline(connection);
            for (String resource : SchemaContractService.cloudPostV1MigrationResources()) {
                SqlScriptRunner.runResource(connection, resource);
            }
            SchemaContractService.refreshCloudContract(connection);
            var readiness = SchemaContractService.validateCloud(connection);
            assertTrue(readiness.ready(), readiness.message());
            assertEquals(SchemaContractService.BASELINE_VERSION, readiness.version());
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("""
                         SELECT count(*) FROM pg_tables WHERE schemaname='public'
                         """)) {
                assertTrue(rows.next());
                assertEquals(29, rows.getInt(1));
            }
        }
    }
}
