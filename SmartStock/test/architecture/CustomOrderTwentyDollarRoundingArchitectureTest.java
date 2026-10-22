package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomOrderTwentyDollarRoundingArchitectureTest {
    @Test
    void clientServerAndQuotationConversionUseTheSharedRoundingRule() throws Exception {
        String screen = source("src/ui/screens/customorders/CustomOrders.java");
        String api = source("src/services/LanApiServer.java");
        String quotation = source("src/services/ServerQuotationInvoiceService.java");

        assertTrue(screen.contains("CurrencyFormatter.customOrderTotal(lines.stream()"),
                "The custom-order cart must honor the company rounding preference.");
        assertTrue(api.contains("customOrderTotal(total,roundToNearestTwenty)"),
                "The authenticated server must enforce the preference independently of the register.");
        assertTrue(quotation.contains("customOrderTotal(customTotal,roundToNearestTwenty)"),
                "Quotation conversion must honor the custom-order rounding preference.");
        assertTrue(quotation.contains("customTotal=customTotal.add(each.multiply(BigDecimal.valueOf(quantity)))"),
                "The converted order must sum exact unit prices before rounding.");
    }

    private static String source(String relativePath) throws Exception {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath));
    }
}
