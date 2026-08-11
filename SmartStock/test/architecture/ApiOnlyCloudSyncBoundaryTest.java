package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiOnlyCloudSyncBoundaryTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void canonicalBaselinesReplaceCompatibilitySchemas() throws Exception {
        String local = source("database/v1/local/001_schema.sql");
        String cloud = source("database/v1/cloud/001_schema.sql");
        int function = local.indexOf("CREATE FUNCTION public.set_email_outbox_updated_at()");
        int trigger = local.indexOf("EXECUTE FUNCTION public.set_email_outbox_updated_at()");

        assertTrue(function >= 0);
        assertTrue(trigger > function);
        assertFalse(local.contains("wifi_sessions"));
        assertFalse(cloud.contains("wifi_sessions"));
        assertFalse(cloud.contains("smartstock_legacy"));
        assertFalse(cloud.contains("LEGACY_CANDIDATE"));
        assertFalse(cloud.contains("CREATE TABLE public.custom_order_item_variant_barcodes"));
        assertEquals(29, Pattern.compile("CREATE TABLE public\\.")
                .matcher(cloud).results().count());
    }

    @Test
    void productionRuntimePathsDoNotOpenCloudJdbcConnections() throws Exception {
        for (String relative : List.of(
                "src/services/SyncWorker.java",
                "src/services/StoreHydrationService.java",
                "src/app/ProductionReadinessMain.java",
                "src/app/ProductionRecoveryDrillMain.java",
                "src/services/ServerImageAssetMaintenance.java")) {
            String source = source(relative);
            assertFalse(source.contains("getCloudConnection()"), relative);
            assertFalse(source.contains("cloudJdbcUrl()"), relative);
            assertFalse(source.contains("cloudDbPassword()"), relative);
        }
    }

    @Test
    void setupHidesLegacyCloudPostgresCredentials() throws Exception {
        String setup = source("src/ui/screens/DatabaseSetup.java");
        assertFalse(setup.contains("Supabase Database Address"));
        assertFalse(setup.contains("Supabase Database User"));
        assertFalse(setup.contains("Supabase Database Password"));
        assertFalse(setup.contains("!database.hasCloudConnection()"));
    }

    @Test
    void cloudControlPlaneIsRlsProtectedAndServiceOnly() throws Exception {
        String cloud = source("database/v1/cloud/001_schema.sql");
        String mirror = source("src/services/CloudRowMirrorService.java");
        String worker = source("src/services/SyncWorker.java");

        assertTrue(cloud.contains("ALTER TABLE public.smartstock_store_rows ENABLE ROW LEVEL SECURITY"));
        assertTrue(cloud.contains("REVOKE ALL ON FUNCTION public.smartstock_sync_exchange"));
        assertTrue(cloud.contains("GRANT ALL ON FUNCTION public.smartstock_sync_exchange"));
        assertTrue(mirror.contains("clean.contains(\"password\")"));
        assertTrue(mirror.contains("clean.contains(\"_pin_\")"));
        assertTrue(mirror.contains("clean.contains(\"secret\")"));
        assertTrue(worker.indexOf("CloudSyncManifest.fetch()")
                < worker.indexOf("CloudRowMirrorService.synchronize"));
    }

    @Test
    void twelveDedicatedTablesStayLocalButRemainInRecoveryManifest() throws Exception {
        String cloud = source("database/v1/cloud/001_schema.sql");
        String local = source("database/v1/local/001_schema.sql");
        String reference = source("src/services/ReferenceDataSyncService.java");
        for (String table : List.of(
                "cross_store_refund_lines", "cross_store_refund_reconciliation",
                "cross_store_refund_requests", "login_security_state",
                "security_audit_events", "sync_cross_store_inventory_cache",
                "sync_cross_store_inventory_status", "sync_cross_store_return_items_cache",
                "sync_cross_store_returns_cache", "sync_cross_store_sale_items_cache",
                "sync_cross_store_sales_cache", "sync_cross_store_sales_status")) {
            assertTrue(local.contains("CREATE TABLE public." + table), table);
            assertFalse(cloud.contains("CREATE TABLE public." + table), table);
        }
        assertTrue(reference.contains("\"cross_store_refund_requests\""));
        assertTrue(reference.contains("\"cross_store_refund_lines\""));
        assertTrue(reference.contains("\"cross_store_refund_reconciliation\""));
        assertTrue(reference.contains("\"security_audit_events\""));
    }

    @Test
    void protectedCredentialsUsePrivateGenerationVault() throws Exception {
        String cloud = source("database/v1/cloud/001_schema.sql");
        String recovery = source("src/services/CloudRecoveryService.java");
        String publicUsers = tableDefinition(cloud, "public.users");

        assertTrue(cloud.contains("CREATE TABLE smartstock_private.store_user_credentials"));
        assertTrue(cloud.contains("ALTER TABLE smartstock_private.store_user_credentials ENABLE ROW LEVEL SECURITY"));
        assertTrue(cloud.contains("REVOKE ALL ON FUNCTION public.smartstock_store_user_credentials"));
        assertFalse(publicUsers.contains("password_hash"));
        assertFalse(publicUsers.contains("employee_pin_hash"));
        assertFalse(publicUsers.contains("badge_secret_hash"));
        assertTrue(recovery.contains("smartstock_store_user_credentials"));
        assertTrue(recovery.contains("badge_secret_hash=?"));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative));
    }

    private static String tableDefinition(String sql, String table) {
        int start = sql.indexOf("CREATE TABLE " + table + " (");
        int end = start < 0 ? -1 : sql.indexOf("\n);", start);
        assertTrue(start >= 0 && end > start, "Missing table definition: " + table);
        return sql.substring(start, end);
    }
}
