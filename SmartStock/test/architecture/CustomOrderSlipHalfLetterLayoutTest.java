package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomOrderSlipHalfLetterLayoutTest {
    @Test void detailsReserveFooterSpaceAndBlankRulesStopAtTheBoundary()throws Exception{
        String renderer=Files.readString(Path.of("src/Receipt/CustomOrderSlipRenderer.java"));
        assertTrue(renderer.contains("footerStart - 10"));
        assertTrue(renderer.contains("currentY + lineHeight <= maxY"));
        String printer=Files.readString(Path.of("src/Receipt/CustomOrderSlipPrinter.java"));
        assertTrue(printer.contains("Math.min((int) pageFormat.getImageableHeight(), 360)"),
                "The customer-order slip must remain half-letter height");
    }
}
