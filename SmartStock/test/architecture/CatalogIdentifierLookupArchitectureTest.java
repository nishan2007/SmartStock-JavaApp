package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogIdentifierLookupArchitectureTest {
    @Test void scannerLookupUsesAuthenticatedExactServerRoute() throws Exception {
        String server = Files.readString(Path.of("src/services/LanApiServer.java"));
        String client = Files.readString(Path.of("src/services/LanApiClient.java"));

        assertTrue(server.contains("/v1/catalog/identifier"));
        assertTrue(server.contains("authenticateDevice(context.exchange())"));
        assertTrue(server.contains("authenticateSession(context.exchange(), device, true)"));
        assertTrue(server.contains("BarcodeNormalizer.lookupCandidates(rawIdentifier)"));
        assertTrue(server.contains("FROM product_barcodes pb WHERE pb.product_id=p.product_id"));
        assertTrue(server.indexOf("exactBarcodeProducts(") < server.indexOf("exactSkuProducts("));
        assertTrue(client.contains("lookupCatalogIdentifier(String identifier)"));
    }
}
