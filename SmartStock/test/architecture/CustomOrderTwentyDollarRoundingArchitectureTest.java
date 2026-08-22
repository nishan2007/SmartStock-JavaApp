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

        assertTrue(screen.contains("lineTotal=CurrencyFormatter.roundToNearestTwenty("),
                "The custom-order cart must display and submit a rounded line total.");
        assertTrue(api.contains("roundedUnitPrice=utils.CurrencyFormatter.roundToNearestTwenty(line.unitPrice())"),
                "The authenticated server must enforce rounding independently of the register.");
        assertTrue(quotation.contains("each=utils.CurrencyFormatter.roundToNearestTwenty("),
                "Quotation conversion must create rounded custom-order units.");
        assertTrue(quotation.contains("customTotal=customTotal.add(each.multiply(BigDecimal.valueOf(quantity)))"),
                "The converted order total must equal the sum of its rounded units.");
    }

    private static String source(String relativePath) throws Exception {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath));
    }
}
