package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class CustomerTypeReceiptPrintingArchitectureTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void schemaSupportsExistingAndFreshStores() throws Exception {
        String base = read("database/v1/local/001_schema.sql");
        String seed = read("database/v1/local/002_seed.sql");
        String migration = read("database/migrations/v1_after/20260827130000_customer_type_receipt_printing.sql");
        String contract = read("src/services/SchemaContractService.java");
        String installer = read("src/services/LanApiSchemaInstaller.java");

        assertTrue(base.contains("auto_print_sale_receipt boolean DEFAULT true NOT NULL"));
        assertTrue(seed.contains("auto_print_sale_receipt, created_at"));
        assertTrue(migration.contains("ADD COLUMN IF NOT EXISTS auto_print_sale_receipt boolean DEFAULT true NOT NULL"));
        assertTrue(contract.contains("20260827130000_customer_type_receipt_printing.sql"));
        assertTrue(contract.contains("ensureCustomerTypeReceiptPrintingUpgrade(connection)"));
        assertTrue(installer.contains("ensureCustomerTypeReceiptPrintingUpgrade(connection)"));
    }

    @Test
    void customerTypeEditorAndApiCarryThePreference() throws Exception {
        String client = read("src/services/LanApiClient.java");
        String catalog = read("src/services/LanCatalogAdminService.java");
        String editor = read("src/ui/screens/CustomerTypeList.java");

        assertTrue(client.contains("boolean autoPrintSaleReceipt"));
        assertTrue(catalog.contains("COALESCE(auto_print_sale_receipt,TRUE)"));
        assertTrue(catalog.contains("request.autoPrintSaleReceipt()"));
        assertTrue(editor.contains("Automatically print sale receipts"));
        assertTrue(editor.contains("autoPrintSaleReceiptBox.isSelected()"));
        assertTrue(editor.contains("autoPrintSaleReceiptBox.setSelected(true)"));
    }

    @Test
    void checkoutUsesServerPreferenceWithoutSuppressingCashDrawer() throws Exception {
        String sales = read("src/services/LanSalesService.java");
        String screen = read("src/ui/screens/MakeASale.java");

        assertTrue(sales.contains("COALESCE(ct.auto_print_sale_receipt, TRUE)"));
        assertTrue(sales.contains("result.put(\"autoPrintSaleReceipt\""));
        assertTrue(screen.contains("showReceiptPreview || !result.autoPrintSaleReceipt()"));
        assertTrue(screen.contains("showReceiptPreview && result.autoPrintSaleReceipt()"));
        assertTrue(screen.contains("EpsonReceiptPrintService.openDrawer(printer)"));
        assertTrue(screen.contains("Receipt preview opened for manual letter-size printing."));
    }
}
