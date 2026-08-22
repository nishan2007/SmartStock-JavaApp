package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawerAndReturnReceiptLogoTest {
    @Test
    void bothReceiptPrintersIncludeLogosForReceiptAndLetterFormats() throws Exception {
        for (String file : new String[]{"CashDrawerCloseReceiptPrinter.java", "ReturnReceiptPrinter.java"}) {
            String source = Files.readString(Path.of("src", "Receipt", file));
            assertTrue(source.contains("ReceiptLogoSupport.escPosLogo"), file);
            assertTrue(source.contains("ReceiptLogoSupport.drawLetterLogo"), file);
            assertTrue(source.contains("CompanyCustomizationManager.loadReceiptLogo"), file);
        }
    }
}
