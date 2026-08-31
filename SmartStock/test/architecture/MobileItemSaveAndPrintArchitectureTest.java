package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MobileItemSaveAndPrintArchitectureTest {
    @Test
    void mobileEditorOffersSaveAndPrintForNewItems() throws Exception {
        String html = Files.readString(Path.of("src/mobile-web/index.html"));
        String javascript = Files.readString(Path.of("src/mobile-web/app.js"));

        assertTrue(html.contains("id=\"saveAndPrint\""));
        assertTrue(html.contains("Save and Print"));
        assertTrue(javascript.contains("call('/price-tags/print'"));
        assertTrue(javascript.contains("Item saved, but the price tag was not printed"));
    }

    @Test
    void serverUsesStandardTemplateAndReceiptPrinterPath() throws Exception {
        String server = Files.readString(Path.of("src/services/MobileItemWebServer.java"));

        assertTrue(server.contains("case \"/price-tags/print\""));
        assertTrue(server.contains("decodePriceTagTemplatesForLan"));
        assertTrue(server.contains(".get(0)"));
        assertTrue(server.contains("PriceTagPrintService.printOnReceiptPrinter"));
        assertTrue(server.contains("MOBILE_WEB_PRICE_TAG_PRINT_FAILED"));
    }
}
