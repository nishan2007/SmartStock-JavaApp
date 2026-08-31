package architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CustomerOpenBalancesSqlArchitectureTest {
    @Test
    void saleBranchNamesDocumentNumberForOuterUnionQuery() throws Exception {
        String source = Files.readString(Path.of("src/services/LanCustomerAccountService.java"));

        assertTrue(source.contains(
                "COALESCE(receipt_number,'Sale #'||sale_id) document_number,created_at,"),
                "The first UNION branch must name document_number because the outer query selects it.");
    }
}
