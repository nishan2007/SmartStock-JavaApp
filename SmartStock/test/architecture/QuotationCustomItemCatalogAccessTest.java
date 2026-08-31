package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotationCustomItemCatalogAccessTest {
    @Test
    void quotationUsersCanReadTheCustomItemCatalog() throws Exception {
        String source = Files.readString(Path.of("src/services/LanApiServer.java"));
        int methodStart = source.indexOf("private ApiResult customOrderCatalog");
        int nextMethod = source.indexOf("private ApiResult createCustomOrder", methodStart);
        String method = source.substring(methodStart, nextMethod);

        assertTrue(method.contains("\"QUOTATIONS_ORDERS\""));
        assertTrue(method.contains("\"CREATE_QUOTATION\""));
    }
}
