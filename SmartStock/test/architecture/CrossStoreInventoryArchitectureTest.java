package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossStoreInventoryArchitectureTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void lookupUiIsReadOnlyAndSeparatedFromCloudRefreshProcess() throws Exception {
        String ui = source("src/ui/screens/CrossStoreInventory.java");
        String service = source("src/services/CrossStoreInventoryService.java");
        String worker = source("src/services/SyncWorker.java");

        assertTrue(ui.contains("Read-only"));
        assertTrue(ui.contains("LanApiClient.loadCrossStoreInventory"));
        assertFalse(ui.contains("Supabase"));
        assertFalse(ui.contains("INSERT INTO"));
        assertTrue(service.contains("smartstock_store_table_snapshot"));
        assertTrue(service.contains("sync_cross_store_inventory_cache"));
        assertTrue(worker.contains("CrossStoreInventoryService.refreshAll"));
    }

    @Test
    void schemaExistsInEveryLocalProvisioningPath() throws Exception {
        String base = source("database/base_schema_setup.sql");
        String runtime = source("src/services/SyncSchemaInstaller.java");
        String migration = source("database/migrations/20260807113000_add_cross_store_inventory_cache.sql");

        assertTrue(base.contains("CREATE TABLE IF NOT EXISTS sync_cross_store_inventory_cache"));
        assertTrue(runtime.contains("CREATE TABLE IF NOT EXISTS sync_cross_store_inventory_cache"));
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS sync_cross_store_inventory_cache"));
    }
}
