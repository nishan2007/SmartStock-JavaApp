package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceFinancialsQueryArchitectureTest {
    @Test
    void customerFinancialFieldsHaveStableResultSetAliases() throws Exception {
        String source = Files.readString(Path.of("src/services/ServerQuotationInvoiceViewService.java"));

        assertTrue(source.contains("COALESCE(ca.name, '') AS customer_name"));
        assertTrue(source.contains("COALESCE(ca.require_charge_authorization, FALSE) AS require_charge_authorization"));
        assertTrue(source.contains("rs.getString(\"customer_name\")"));
    }
}
