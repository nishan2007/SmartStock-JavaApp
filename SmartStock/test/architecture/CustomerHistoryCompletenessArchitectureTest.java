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
        assertTrue(service.contains("rs.getBigDecimal(25)"),"The 25-column history projection must read its balance delta from column 25");
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
                "src/services/ServerCustomOrderDataService.java","src/services/ServerQuotationInvoiceService.java"})
            assertTrue(read(path).contains("requireCurrentMultiStoreBalance"),path+" must enforce synchronized company-wide credit");
        String screen=read("src/ui/screens/CustomerAccounts.java");
        assertTrue(screen.contains("Balance (All Stores)"));
    }
}
