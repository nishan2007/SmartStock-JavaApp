package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EthernetReceiptPrinterFallbackArchitectureTest {
    @Test
    void ethernetTransportIsAttemptedBeforeAWindowsQueueIsRequired() throws Exception {
        String source = Files.readString(Path.of("src/Receipt/EpsonReceiptPrintService.java"));
        int send = source.indexOf("NativeEscPosTransport.sendIfEnabled(jobBytes)");
        int queueRequired = source.indexOf("if (printer == null)", send);

        assertTrue(send >= 0, "Receipt jobs must attempt native Ethernet transport");
        assertTrue(queueRequired > send,
                "A Windows receipt queue must only be required after Ethernet transport is unavailable");
    }

    @Test
    void cutterAndDrawerTestsAlsoAllowEthernetWithoutAWindowsQueue() throws Exception {
        String source = Files.readString(Path.of("src/Receipt/EpsonReceiptPrintService.java"));
        int controlMethod = source.indexOf("public static PrintResult testControl");
        int send = source.indexOf("NativeEscPosTransport.sendIfEnabled(out.toByteArray())", controlMethod);
        int queueRequired = source.indexOf("if (printer == null)", send);

        assertTrue(controlMethod >= 0 && send > controlMethod);
        assertTrue(queueRequired > send,
                "Ethernet cutter and drawer tests must not require a Windows queue");
    }
}
