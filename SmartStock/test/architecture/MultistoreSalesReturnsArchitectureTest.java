package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MultistoreSalesReturnsArchitectureTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test void permissionsExistInBaseRuntimeAndMigrationSchemas() throws Exception {
        String seed = read("database/v1/local/002_seed.sql");
        for (String permission : new String[]{"VIEW_MULTI_STORE_STOCK",
                "VIEW_MULTI_STORE_SALES", "PROCESS_MULTI_STORE_RETURNS"}) {
            assertTrue(seed.contains(permission), permission);
        }
        String installer = read("src/services/BaseSchemaInstaller.java");
        assertTrue(installer.contains("SchemaContractService.requireLocalReady(connection)"));
    }

    @Test void cloudRefundFunctionsAreServiceRoleOnlyAndUseFixedSearchPath() throws Exception {
        String sql=read("database/v1/cloud/001_schema.sql");
        assertTrue(sql.contains("SECURITY DEFINER"));
        assertTrue(sql.contains("SET search_path TO ''"));
        assertTrue(sql.contains("REVOKE ALL ON FUNCTION public.smartstock_reserve_cross_store_refund(p_request jsonb) FROM PUBLIC"));
        assertTrue(sql.contains("GRANT ALL ON FUNCTION public.smartstock_reserve_cross_store_refund(p_request jsonb) TO service_role"));
    }

    @Test void screensUseServerApiAndSyncRefreshesCaches() throws Exception {
        String returns=read("src/ui/screens/ReturnSale.java");
        String history=read("src/ui/screens/ViewSales.java");
        String sync=read("src/services/SyncWorker.java");
        assertTrue(returns.contains("LanApiClient.loadReturnSaleDetails"));
        assertTrue(history.contains("LanApiClient.loadSalesHistory"));
        assertTrue(sync.contains("CrossStoreSalesService.refreshAll"));
        assertTrue(sync.contains("CrossStoreRefundService.synchronize"));
    }
}
