package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomOrderCreditSeparationArchitectureTest {
    private static String read(String path)throws Exception{return Files.readString(Path.of(path));}

    @Test void customOrderBalancesAreInformationalAndPaymentsTrackCreditPortion()throws Exception{
        String orders=read("src/services/ServerCustomOrderDataService.java");
        assertTrue(orders.contains("CUSTOM_ORDER_BALANCE"));
        assertFalse(orders.contains("CUSTOM_ORDER_CREDIT"));
        String workflow=read("src/services/LanCustomOrderWorkflowService.java");
        assertTrue(workflow.contains("CUSTOM_ORDER_PAYMENT"));
        String ledger=read("src/services/CustomerAccountLedgerService.java");
        assertTrue(ledger.contains("credit_applied_amount"));
        assertTrue(ledger.contains("'CUSTOM_ORDER_BALANCE', 'CUSTOM_ORDER_PAYMENT'"));
    }

    @Test void paymentApiRequiresExplicitAllocationsAndUiShowsSeparatedTotals()throws Exception{
        String service=read("src/services/LanCustomerAccountService.java");
        assertTrue(service.contains("ALLOCATION_MISMATCH"));
        assertTrue(service.contains("applySelectedAllocations"));
        assertTrue(service.contains("FOR UPDATE"));
        String client=read("src/services/LanApiClient.java");
        assertTrue(client.contains("CustomerPaymentAllocationRequest"));
        assertTrue(client.contains("/v1/customer-accounts/open-balances"));
        String screen=read("src/ui/screens/CustomerAccountDetails.java");
        assertTrue(screen.contains("Credit Balance (All Stores)"));
        assertTrue(screen.contains("Custom Order Due"));
        assertTrue(screen.contains("Total Due"));
    }

    @Test void balanceSheetReclassifiesCustomOrderChargesAndShowsOpenOrderReceivables()throws Exception{
        String balanceSheet=read("src/services/ServerBalanceSheetService.java");
        assertTrue(balanceSheet.contains("cat.transaction_type IN ('CUSTOM_ORDER_BALANCE', 'CUSTOM_ORDER_PAYMENT')"));
        assertTrue(balanceSheet.contains("SELECT -COALESCE(allocation.amount, 0) AS adjustment"));
        assertTrue(balanceSheet.contains("SELECT -COALESCE(order_return.balance_reduction, 0) AS adjustment"));
        assertTrue(balanceSheet.contains("lines.add(new SheetLine(\"ORDER ACCOUNT\", amount))"));
        assertTrue(balanceSheet.contains("SUM(COALESCE(co.balance_due, 0)) AS amount"));
        assertTrue(balanceSheet.contains("COALESCE(co.status, '') <> 'CANCELLED'"));
        assertTrue(balanceSheet.contains("CUSTOM ORDER REFUND - "));
        assertTrue(balanceSheet.contains("SALE REFUND - "));
        assertTrue(balanceSheet.contains("INVOICE REFUND - "));
        assertTrue(balanceSheet.contains("CROSS-STORE REFUND - "));
        assertTrue(balanceSheet.contains("COALESCE(p.payment_action, 'PAYMENT') IN ('REFUND', 'REVERSAL')"));
    }

    @Test void baselineAndMigrationCoverHistoricalRows()throws Exception{
        String baseline=read("database/v1/local/001_schema.sql");
        String migration=read("database/migrations/v1_after/20260811130000_separate_custom_order_credit.sql");
        assertTrue(baseline.contains("credit_applied_amount numeric(12,2)"));
        assertTrue(migration.contains("transaction_type='CUSTOM_ORDER_BALANCE'"));
        assertTrue(migration.contains("customer_account_payment_allocations"));
    }
}
