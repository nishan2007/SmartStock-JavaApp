package architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtherIncomeArchitectureTest {
    @Test
    void otherIncomeIsPersistentSecuredSyncedAndIncludedOnlyInIncome() throws IOException {
        String server = source("src/services/ServerBalanceSheetService.java");
        String client = source("src/services/BalanceSheetService.java");
        String api = source("src/services/LanApiServer.java");
        String screen = source("src/ui/screens/BalanceSheet.java");
        String setup = source("database/v1/local/001_schema.sql");
        String contract = source("src/services/SchemaContractService.java");
        String sync = source("src/services/ReferenceDataSyncService.java");

        assertFalse(server.contains("CREATE TABLE IF NOT EXISTS other_income_entries"));
        assertTrue(server.contains("SchemaContractService.requireLocalReady(conn)"));
        assertTrue(server.contains("new SheetLine(\"OTHER CASH\", amount)"));
        assertTrue(server.contains("entry.amount().signum() <= 0"));
        assertTrue(server.contains("entry.amount().remainder(BigDecimal.ONE).signum() != 0"));
        assertTrue(client.contains("ADD_OTHER_INCOME"));
        assertTrue(client.contains("DELETE_OTHER_INCOME"));
        assertTrue(api.contains("DELETABLE_OTHER_INCOME"));
        assertTrue(api.contains("requireAnyPermission(connection,session.userId(),\"BALANCE_SHEET\")"));
        assertTrue(screen.contains("addIncomeRow(\"Other\", other)"));
        assertTrue(screen.contains("incomeAmount(income, \"OTHER CASH\")"));
        assertTrue(setup.contains("other_income_payment_method_chk CHECK ((payment_method = 'CASH'::text))"));
        assertTrue(setup.contains("other_income_whole_gyd_chk CHECK ((amount = trunc(amount)))"));
        assertTrue(contract.contains("database/v1/local/001_schema.sql"));
        assertTrue(sync.contains("\"other_income_entries\""));
        assertTrue(server.contains("ReferenceDataSyncService.recordTombstone"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of(relative));
    }
}
