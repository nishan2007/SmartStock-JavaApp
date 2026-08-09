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
        String base = source("database/v1/local/001_schema.sql");
        String runtime = source("src/services/SyncSchemaInstaller.java");

        assertTrue(base.contains("CREATE TABLE public.sync_cross_store_inventory_cache"));
        assertTrue(runtime.contains("SchemaContractService.requireLocalReady(connection)"));
        assertFalse(runtime.contains("CREATE TABLE"));
    }
}
