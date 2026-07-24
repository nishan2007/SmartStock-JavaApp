package architecture;

import data.DatabaseConfig;
import data.DatabaseMode;
import org.junit.jupiter.api.Test;
import services.RemoteAdminPolicy;

import static org.junit.jupiter.api.Assertions.*;

class RemoteAdminBoundaryTest {
    @Test
    void modeParserRecognizesRemoteAdmin() {
        assertEquals(DatabaseMode.REMOTE_ADMIN, DatabaseMode.from("remote-admin"));
    }

    @Test
    void remoteConfigurationDropsEveryDatabaseCredential() {
        DatabaseConfig config = DatabaseConfig.fromForm(DatabaseMode.REMOTE_ADMIN,
                "jdbc:postgresql://example/db", "user", "password", "gateway.example.com", 443,
                1, 60);
        assertEquals("", config.jdbcUrl());
        assertEquals("", config.dbUser());
        assertEquals("", config.dbPassword());
    }

    @Test
    void physicalOperationsAreDeniedBySharedPolicy() {
        assertTrue(RemoteAdminPolicy.isPhysicalOperation("/v1/cash/drawer/open"));
        assertTrue(RemoteAdminPolicy.isPhysicalOperation("/v1/time-clock/punch"));
        assertTrue(RemoteAdminPolicy.isPhysicalOperation("/v1/sales/checkout"));
        assertFalse(RemoteAdminPolicy.isPhysicalOperation("/v1/reports/load"));
        assertFalse(RemoteAdminPolicy.isPhysicalOperation("/v1/inventory/list"));
    }
}
