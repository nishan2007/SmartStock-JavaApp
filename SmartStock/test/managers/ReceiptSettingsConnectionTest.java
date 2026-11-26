package managers;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import services.SchemaContractService;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ReceiptSettingsConnectionTest {
    @Test void requestConnectionReturnsCurrentStoreSettingsWithoutRegisterOrFileAccess() throws Exception {
        String url = System.getProperty("smartstock.receipt.test.jdbc", "");
        assumeTrue(!url.isBlank());
        try (var connection = DriverManager.getConnection(url, "receipt_test", "")) {
            SchemaContractService.installLocalBaseline(connection);
            SchemaContractService.requireLocalReady(connection);
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO company_info(company_info_id,company_name) VALUES(1,'Receipt Test') ON CONFLICT(company_info_id) DO UPDATE SET company_name=EXCLUDED.company_name");
                var settings = ServerCompanyCustomizationRepository.loadReceiptSettings(connection, 1);
                assertEquals("Receipt Test", settings.companyName());
                assertTrue(settings.visibility().showCompanyName());
                var decoded = new Gson().fromJson(new Gson().toJson(settings),
                        CompanyCustomizationManager.ReceiptSettings.class);
                assertEquals(settings, decoded);
                statement.executeUpdate("UPDATE company_info SET company_name='Changed Store' WHERE company_info_id=1");
                assertEquals("Changed Store", ServerCompanyCustomizationRepository
                        .loadReceiptSettings(connection, 1).companyName());
                assertFalse(connection.isClosed());
            } finally {
                connection.rollback();
            }
        }
    }
}
