package managers;

import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.lang.reflect.Proxy;
import javax.print.PrintService;

import static org.junit.jupiter.api.Assertions.*;

class HardwareSettingsManagerBadgePrinterTest {
    @Test
    void defaultsAreSafeAndTargetMagicard600Duo() {
        HardwareSettingsManager.BadgePrinterSettings settings =
                HardwareSettingsManager.readBadgePrinterSettings(new Properties());

        assertFalse(settings.enabled());
        assertEquals("", settings.systemName());
        assertEquals("Magicard 600", settings.model());
        assertTrue(settings.duplex());
        assertTrue(settings.showPrintDialog());
    }

    @Test
    void settingsRoundTripThroughHardwareProperties() {
        Properties properties = new Properties();
        HardwareSettingsManager.BadgePrinterSettings expected =
                new HardwareSettingsManager.BadgePrinterSettings(true, " Magicard 600 @ 10.1.1.50 ",
                        "Magicard 600", true, true);

        HardwareSettingsManager.writeBadgePrinterSettings(properties, expected);
        HardwareSettingsManager.BadgePrinterSettings actual =
                HardwareSettingsManager.readBadgePrinterSettings(properties);

        assertEquals("Magicard 600 @ 10.1.1.50", actual.systemName());
        assertEquals(expected.model(), actual.model());
        assertTrue(actual.enabled());
        assertTrue(actual.duplex());
        assertTrue(actual.showPrintDialog());
    }

    @Test
    void enabledPrinterRequiresAWindowsQueue() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new HardwareSettingsManager.BadgePrinterSettings(true, " ", "Magicard 600", true, true));
        assertTrue(error.getMessage().contains("Windows queue"));
    }

    @Test
    void queueResolutionUsesAnExactWindowsQueueName() {
        PrintService queue = (PrintService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PrintService.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "Magicard 600 @ 10.1.1.50";
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                });

        assertSame(queue, HardwareSettingsManager.findPrintService("Magicard 600 @ 10.1.1.50",
                new PrintService[]{queue}));
        assertNull(HardwareSettingsManager.findPrintService("magicard 600 @ 10.1.1.50",
                new PrintService[]{queue}));
    }
}
