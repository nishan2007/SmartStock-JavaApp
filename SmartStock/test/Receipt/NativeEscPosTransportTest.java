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
    void retriesConnectionFailuresBeforeSendingBytes() throws Exception {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var received = new java.io.ByteArrayOutputStream();
        byte[] job = {65, 10};
        NativeEscPosTransport.send(job,
                new HardwareSettingsManager.NativeEthernetPrinterSettings(true, "127.0.0.1", 9100, 1000),
                () -> new java.net.Socket() {
                    @Override public void connect(java.net.SocketAddress endpoint, int timeout) throws java.io.IOException {
                        if (attempts.incrementAndGet() < 3) throw new java.net.ConnectException("Busy");
                    }
                    @Override public java.io.OutputStream getOutputStream() { return received; }
                });
        org.junit.jupiter.api.Assertions.assertEquals(3, attempts.get());
        assertArrayEquals(job, received.toByteArray());
    }

    @Test
    void doesNotReplayAJobAfterTransmissionStarts() {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var failure = assertThrows(javax.print.PrintException.class, () -> NativeEscPosTransport.send(new byte[]{65},
                new HardwareSettingsManager.NativeEthernetPrinterSettings(true, "127.0.0.1", 9100, 1000),
                () -> new java.net.Socket() {
                    @Override public void connect(java.net.SocketAddress endpoint, int timeout) { attempts.incrementAndGet(); }
                    @Override public java.io.OutputStream getOutputStream() {
                        return new java.io.OutputStream() {
                            @Override public void write(int value) throws java.io.IOException { throw new java.io.IOException("Connection lost"); }
                        };
                    }
                }));
        org.junit.jupiter.api.Assertions.assertEquals(1, attempts.get());
        org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains("Delivery is uncertain"));
    }

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
