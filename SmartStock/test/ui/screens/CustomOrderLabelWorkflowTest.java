package ui.screens;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomOrderLabelWorkflowTest {
    @Test
    void orderDetailsProvidesLabelOnlyReprint() throws Exception {
        String panel = Files.readString(Path.of("src/ui/screens/customorders/CustomOrdersLookupTabPanel.java"));
        String screen = Files.readString(Path.of("src/ui/screens/customorders/CustomOrders.java"));

        assertTrue(panel.contains("Reprint Order Label(s)"));
        assertTrue(panel.contains("Preview Slip"));
        assertTrue(panel.contains("Print Slip"));
        assertTrue(panel.contains("handler.previewOrderSlip(orderNumber)"));
        assertTrue(panel.contains("handler.printOrderSlip(orderNumber)"));
        assertTrue(panel.contains("reprintLabelButton.setEnabled(false)"));
        assertTrue(panel.contains("handler.reprintOrderLabels(orderNumber)"));
        assertTrue(screen.contains("CustomOrderSlipBuilder.buildFromOrderNumber(orderNumber)"));
        assertTrue(screen.contains("CustomOrderLabelPrinter.print(data, count)"));
    }

    @Test
    void hardwareSettingsKeepReceiptAndOrderLabelDefaultsIndependent() throws Exception {
        String manager = Files.readString(Path.of("src/managers/HardwareSettingsManager.java"));
        String panel = Files.readString(Path.of("src/ui/screens/workstationprefs/HardwareSettingsPanel.java"));

        assertTrue(manager.contains("default_order_label"));
        assertTrue(manager.contains("getDefaultOrderLabelPrinter"));
        assertTrue(panel.contains("Set Receipt Default"));
        assertTrue(panel.contains("Set Order Label Default"));
    }

    @Test
    void closingLabelPromptStillPrintsTheSlip() throws Exception {
        String preview = Files.readString(Path.of("src/ui/screens/CustomOrderSlipPreview.java"));
        String orders = Files.readString(Path.of("src/ui/screens/customorders/CustomOrders.java"));

        assertTrue(preview.contains("if (labelCount == null) {"));
        assertTrue(preview.indexOf("CustomOrderSlipPrinter.printToPosPrinter") < preview.indexOf("if (labelCount == null) {"));
        assertTrue(orders.contains("printSlipAndLabelsAsync(orderNumber, count);"));
        assertTrue(orders.contains("if (count == null) return new OrderPrintResult(data, null, null);"));
    }
}
