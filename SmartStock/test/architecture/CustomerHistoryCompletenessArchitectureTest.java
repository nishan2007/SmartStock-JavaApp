package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerHistoryCompletenessArchitectureTest {
    private static String read(String path)throws Exception{return Files.readString(Path.of(path));}

    @Test void historyIncludesEveryCustomerDocumentAndCrossStoreRefresh()throws Exception{
        String service=read("src/services/LanCustomerAccountService.java");
        for(String table:new String[]{"customer_account_transactions","sales","custom_orders","quotations","invoices"})
            assertTrue(service.contains("FROM "+table),"Customer history must include "+table);
        assertTrue(service.contains("CrossStoreCustomerHistoryService.rows"));
        assertTrue(service.contains("historyIdentity"),"Customer history must deduplicate live and synchronized copies");
        String remote=read("src/services/CrossStoreCustomerHistoryService.java");
        assertTrue(remote.contains("source_location_id<>?"),"The current store must not be returned from its own remote cache");
        String sync=read("src/services/SyncWorker.java");
        assertTrue(sync.contains("CrossStoreCustomerHistoryService.refreshAll"));
    }

    @Test void apiAndUiExposeStoreAndDocumentIdentity()throws Exception{
        String client=read("src/services/LanApiClient.java");
        for(String field:new String[]{"locationId","storeName","documentType","documentId","documentNumber","documentStatus","remote"})
            assertTrue(client.contains(field),"Customer history API must expose "+field);
        String screen=read("src/ui/screens/CustomerTransactionHistory.java");
        assertTrue(screen.contains("\"Store\""));
        assertTrue(screen.contains("\"Document #\""));
        assertTrue(screen.contains("\"Payment Status\""));
        assertTrue(screen.contains("\"Document Status\""));
    }

    @Test void customerCreditUsesAllStoreLedgerAndFailsClosedWhenSyncIsIncomplete()throws Exception{
        String ledger=read("src/services/CustomerAccountLedgerService.java");
        assertTrue(ledger.contains("sync_cross_store_customer_history_cache"));
        assertTrue(ledger.contains("requireCurrentMultiStoreBalance"));
        for(String path:new String[]{"src/services/LanSalesService.java","src/services/LanCustomerAccountService.java",
                "src/services/ServerQuotationInvoiceService.java"})
            assertTrue(read(path).contains("requireCurrentMultiStoreBalance"),path+" must enforce synchronized company-wide credit");
        String customOrders=read("src/services/ServerCustomOrderDataService.java");
        assertTrue(customOrders.contains("CUSTOM_ORDER_BALANCE"),"Custom-order balances must be informational rather than revolving credit");
        String screen=read("src/ui/screens/CustomerAccounts.java");
        assertTrue(screen.contains("Credit Balance"));
        assertTrue(screen.contains("Custom Order Due"));
    }
}
