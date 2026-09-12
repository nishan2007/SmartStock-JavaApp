package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerChargeAuthorizationArchitectureTest {
    private static String read(String path) throws Exception { return Files.readString(Path.of(path)); }

    @Test void requiredEvidenceIsEnforcedAtEveryServerChargeBoundary() throws Exception {
        String sales=read("src/services/LanSalesService.java");
        String orders=read("src/services/ServerCustomOrderDataService.java");
        String invoices=read("src/services/ServerQuotationInvoiceService.java");
        String validation=read("src/services/CustomerChargeAuthorizationService.java");
        assertTrue(sales.contains("CustomerChargeAuthorizationService.validateRequired"));
        assertTrue(orders.contains("CustomerChargeAuthorizationService.validateRequired"));
        assertTrue(invoices.contains("CustomerChargeAuthorizationService.validateRequired"));
        assertTrue(validation.contains("Please provide a complete signature"));
        assertTrue(validation.contains("MAX_BYTES=300_000"));
        assertTrue(validation.contains("account_signature_retention_years,3) FROM company_customization WHERE location_id=?"));
    }

    @Test void schemaRetentionHistoryAndRecoveryAreWired() throws Exception {
        String migration=read("database/migrations/v1_after/20260907120000_customer_charge_authorizations.sql");
        String sync=read("src/services/ReferenceDataSyncService.java");
        String recovery=read("src/services/ProductionRecoveryDrillService.java");
        String contract=read("src/services/SchemaContractService.java");
        String receipt=read("src/Receipt/ReceiptFormatter.java");
        String history=read("src/services/LanSalesHistoryService.java");
        assertTrue(migration.contains("require_charge_authorization boolean NOT NULL DEFAULT false"));
        assertTrue(migration.contains("account_signature_retention_years integer NOT NULL DEFAULT 3"));
        assertTrue(migration.contains("customer_charge_authorizations_evidence_chk"));
        assertTrue(sync.contains("\"customer_charge_authorizations\""));
        assertTrue(recovery.contains("\"customer_charge_authorizations\""));
        assertTrue(contract.contains("ensureCustomerChargeAuthorizationUpgrade(connection)"));
        assertTrue(contract.contains("20260907120000_customer_charge_authorizations.sql"));
        assertTrue(receipt.contains("Account authorized by"));
        assertTrue(history.contains("signature_purged_at"));
    }

    @Test void captureDialogSupportsDrawClearRedoAndUnsignedReason() throws Exception {
        String dialog=read("src/ui/helpers/CustomerChargeAuthorizationDialog.java");
        assertTrue(dialog.contains("addMouseMotionListener"));
        assertTrue(dialog.contains("Clear"));
        assertTrue(dialog.contains("Redo"));
        assertTrue(dialog.contains("Unable to capture signature"));
        assertTrue(dialog.contains("Base64.getEncoder"));
    }
}
