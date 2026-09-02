package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultReceiptPrinterDestinationArchitectureTest {
    @Test
    void workstationCanExplicitlyChooseEthernetOrWindowsReceiptDestination() throws Exception {
        String manager = Files.readString(Path.of("src/managers/HardwareSettingsManager.java"));
        String panel = Files.readString(Path.of("src/ui/screens/workstationprefs/HardwareSettingsPanel.java"));
        String transport = Files.readString(Path.of("src/Receipt/NativeEscPosTransport.java"));

        assertTrue(manager.contains("receipt.default_destination"));
        assertTrue(manager.contains("ReceiptPrinterDestination"));
        assertTrue(manager.contains("WINDOWS_QUEUE"));
        assertTrue(panel.contains("Default receipt printer:"));
        assertTrue(panel.contains("saveDefaultReceiptPrinterDestination"));
        assertTrue(panel.contains("Enable Native Ethernet ESC/POS before making it the default"));
        assertTrue(transport.contains("getDefaultReceiptPrinterDestination"));
        assertTrue(transport.contains("ReceiptPrinterDestination.ETHERNET"));
    }
}
