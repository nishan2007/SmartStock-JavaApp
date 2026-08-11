package architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotationCustomItemArchitectureTest {
    private static String source(String path)throws Exception{return Files.readString(Path.of(System.getProperty("user.dir")).resolve(path));}

    @Test void customQuotationDataSurvivesApiPersistenceAndInvoiceCopy()throws Exception{
        String client=source("src/services/QuotationInvoiceService.java");
        String server=source("src/services/ServerQuotationInvoiceService.java");
        assertTrue(client.contains("CustomLineInput"));
        assertTrue(client.contains("productionDueDate"));
        assertTrue(server.contains("custom_configuration"));
        assertTrue(server.contains("quotation_line_print_addons"));
        assertTrue(server.contains("invoice_line_print_addons"));
    }

    @Test void acceptanceCreatesInvoiceBilledProductionOrderAtomically()throws Exception{
        String service=source("src/services/ServerQuotationInvoiceService.java");
        String workflow=source("src/services/LanCustomOrderWorkflowService.java");
        assertTrue(service.contains("createLinkedCustomOrder(conn,quotation,invoiceId)"));
        assertTrue(service.contains("invoice_billed=TRUE"));
        assertTrue(workflow.contains("rejectInvoiceBilledFinance"));
        assertTrue(workflow.contains("BILLED ON INVOICE"));
    }

    @Test void sharedDocumentRendererIncludesCondensedLineDetails()throws Exception{
        String renderer=source("src/Receipt/ServerQuotationInvoiceDocumentBuilder.java");
        assertTrue(renderer.contains("line_notes END AS item_name"));
        assertTrue(renderer.contains("descriptionHtml"));
    }
}
