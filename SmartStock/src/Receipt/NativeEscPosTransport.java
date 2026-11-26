package Receipt;

import managers.HardwareSettingsManager;

import javax.print.PrintException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Sends an already-formatted ESC/POS job to a printer's raw TCP socket. */
public final class NativeEscPosTransport {
    private static long lastAttemptFinished;
    private NativeEscPosTransport() { }

    public static boolean isEnabled() throws PrintException {
        try {
            return settings().enabled()
                    && HardwareSettingsManager.getDefaultReceiptPrinterDestination()
                    == HardwareSettingsManager.ReceiptPrinterDestination.ETHERNET;
        } catch (IOException ex) {
            throw new PrintException("Invalid default receipt printer setting: " + safeMessage(ex));
        }
    }

    public static String sendIfEnabled(byte[] jobBytes) throws PrintException {
        HardwareSettingsManager.NativeEthernetPrinterSettings settings = settings();
        if (!isEnabled()) return null;
        send(jobBytes, settings);
        return settings.endpoint();
    }

    static synchronized void send(byte[] jobBytes, HardwareSettingsManager.NativeEthernetPrinterSettings settings)
            throws PrintException {
        send(jobBytes, settings, Socket::new);
    }

    static synchronized void send(byte[] jobBytes, HardwareSettingsManager.NativeEthernetPrinterSettings settings,
                                  java.util.function.Supplier<Socket> sockets) throws PrintException {
        if (jobBytes == null || jobBytes.length == 0) {
            throw new PrintException("ESC/POS print job is empty.");
        }
        // Small receipt printers often accept only one raw TCP client and need time
        // to release it before the next copy. Serialize all native print callers.
        for (int attempt = 0; attempt < 3; attempt++) {
            boolean transmissionStarted = false;
            try {
                long remaining = 300_000_000L - (System.nanoTime() - lastAttemptFinished);
                if (lastAttemptFinished != 0 && remaining > 0) {
                    java.util.concurrent.TimeUnit.NANOSECONDS.sleep(remaining);
                }
                try (Socket socket = sockets.get()) {
                    socket.connect(new InetSocketAddress(settings.host(), settings.port()), settings.connectTimeoutMillis());
                    var output = socket.getOutputStream();
                    // Never replay an uncertain/partially transmitted receipt.
                    transmissionStarted = true;
                    output.write(jobBytes);
                    output.flush();
                }
                return;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new PrintException("Ethernet printing interrupted before sending the receipt.");
            } catch (IOException ex) {
                if (transmissionStarted || attempt == 2) {
                    throw new PrintException("Native Ethernet printer " + settings.endpoint() + " failed: "
                            + safeMessage(ex) + (transmissionStarted
                            ? ". Delivery is uncertain; check the printer before retrying." : ""), ex);
                }
            } finally {
                lastAttemptFinished = System.nanoTime();
            }
        }
    }

    private static HardwareSettingsManager.NativeEthernetPrinterSettings settings() throws PrintException {
        try {
            return HardwareSettingsManager.getNativeEthernetPrinterSettings();
        } catch (IOException | IllegalArgumentException ex) {
            throw new PrintException("Invalid native Ethernet printer settings: " + safeMessage(ex));
        }
    }

    private static String safeMessage(Exception ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank() ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
