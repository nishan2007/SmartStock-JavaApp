package Receipt;

import managers.HardwareSettingsManager;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativeEscPosTransportTest {
    @Test
    void sendsTheCompleteJobToTheConfiguredRawTcpEndpoint() throws Exception {
        byte[] expected = {0x1B, 0x40, 65, 10, 0x1D, 0x56, 0x42, 0};
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<byte[]> received = CompletableFuture.supplyAsync(() -> {
                try (var socket = server.accept()) {
                    return socket.getInputStream().readAllBytes();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            NativeEscPosTransport.send(expected, new HardwareSettingsManager.NativeEthernetPrinterSettings(
                    true, "127.0.0.1", server.getLocalPort(), 2000));
            assertArrayEquals(expected, received.get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void rejectsInvalidEndpointValues() {
        assertThrows(IllegalArgumentException.class, () ->
                new HardwareSettingsManager.NativeEthernetPrinterSettings(true, "", 9100, 3000));
        assertThrows(IllegalArgumentException.class, () ->
                new HardwareSettingsManager.NativeEthernetPrinterSettings(true, "10.1.1.23", 0, 3000));
    }
}
