package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaMaintenanceIdempotencyTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void runtimeServicesDoNotPerformCompatibilitySchemaMaintenance() throws Exception {
        for (String relative : new String[]{
                "src/services/CustomerAccountLedgerService.java",
                "src/services/ReferenceDataSyncService.java",
                "src/services/SyncSchemaInstaller.java",
                "src/services/DeviceCredentialSchemaInstaller.java"}) {
            String source = Files.readString(ROOT.resolve(relative));
            assertFalse(source.contains("ADD COLUMN IF NOT EXISTS"), relative);
            assertFalse(source.contains("CREATE TABLE IF NOT EXISTS"), relative);
        }
    }

    @Test
    void canonicalBaselineContainsCurrentDeviceSessionShape() throws Exception {
        String baseline = Files.readString(
                ROOT.resolve("database/v1/local/001_schema.sql"));
        String devices = baseline.substring(
                baseline.indexOf("CREATE TABLE public.devices"),
                baseline.indexOf("\n);", baseline.indexOf("CREATE TABLE public.devices")));

        assertTrue(devices.contains("session_count bigint DEFAULT 0 NOT NULL"));
        assertTrue(devices.contains("auto_logout_enabled boolean DEFAULT false NOT NULL"));
        assertTrue(devices.contains("auto_logout_minutes integer DEFAULT 15 NOT NULL"));
    }
}
