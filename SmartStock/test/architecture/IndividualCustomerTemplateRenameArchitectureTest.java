package architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class IndividualCustomerTemplateRenameArchitectureTest {
    private static String read(String path)throws Exception{return Files.readString(Path.of(path));}
    @Test void upgradeRenamesWithoutReassigningCustomerAccounts()throws Exception{
        String migration=read("database/migrations/v1_after/20260827170000_rename_individual_customer_template.sql");
        String contract=read("src/services/SchemaContractService.java");
        assertTrue(migration.contains("REPLACE(customer_card_template_layout_data"));
        assertTrue(migration.contains("SET name='Individual'"));
        assertTrue(migration.contains("LOWER(BTRIM(name)) IN ('individual','general','personal / regular','personal','regular')"));
        assertTrue(contract.contains("ensureIndividualCustomerTemplateRename(connection)"));
    }
}
