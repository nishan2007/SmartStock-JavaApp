package architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomaticCustomerDiscountArchitectureTest {
    private static String read(String path)throws Exception{return Files.readString(Path.of(path));}
    @Test void schemaAndPermissionAreComplete()throws Exception{String migration=read("database/migrations/v1_after/20260911150000_automatic_customer_discounts.sql");assertTrue(migration.contains("sales_discount_enabled"));assertTrue(migration.contains("invoice_discount_enabled"));assertTrue(migration.contains("custom_order_discount_enabled"));assertTrue(migration.contains("customer_accounts_automatic_discounts_chk"));assertTrue(migration.contains("MANAGE_CUSTOMER_DISCOUNTS"));}
    @Test void everyServerTransactionReloadsOrReceivesTrustedAccountRate()throws Exception{assertTrue(read("src/services/LanSalesService.java").contains("loadCustomerSaleDiscount"));String api=read("src/services/LanApiServer.java");assertTrue(api.contains("customerDocumentDiscount"));assertTrue(api.contains("customOrderDiscountEnabled"));assertTrue(read("src/services/ServerQuotationInvoiceService.java").contains("customer_discount_applied"));assertTrue(read("src/services/ServerCustomOrderDataService.java").contains("customer_discount_amount"));}
}
