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
        for (String file : new String[]{"database/base_schema_setup.sql",
                "database/migrations/20260807140000_add_multistore_sales_returns.sql",
                "src/services/BaseSchemaInstaller.java"}) {
            String source=read(file);
            assertTrue(source.contains("VIEW_MULTI_STORE_STOCK"),file);
            assertTrue(source.contains("VIEW_MULTI_STORE_SALES"),file);
            assertTrue(source.contains("PROCESS_MULTI_STORE_RETURNS"),file);
        }
    }

    @Test void cloudRefundFunctionsAreServiceRoleOnlyAndUseFixedSearchPath() throws Exception {
        String sql=read("database/migrations/20260807143000_add_multistore_refund_queue.sql");
        assertTrue(sql.contains("SECURITY DEFINER SET search_path TO ''"));
        assertTrue(sql.contains("REVOKE ALL ON FUNCTION public.smartstock_reserve_cross_store_refund(jsonb) FROM PUBLIC,anon,authenticated"));
        assertTrue(sql.contains("GRANT EXECUTE ON FUNCTION public.smartstock_reserve_cross_store_refund(jsonb) TO service_role"));
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
