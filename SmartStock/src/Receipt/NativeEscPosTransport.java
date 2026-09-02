package Receipt;

import managers.HardwareSettingsManager;

import javax.print.PrintException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Sends an already-formatted ESC/POS job to a printer's raw TCP socket. */
public final class NativeEscPosTransport {
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

    static void send(byte[] jobBytes, HardwareSettingsManager.NativeEthernetPrinterSettings settings)
            throws PrintException {
        if (jobBytes == null || jobBytes.length == 0) {
            throw new PrintException("ESC/POS print job is empty.");
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(settings.host(), settings.port()), settings.connectTimeoutMillis());
            socket.getOutputStream().write(jobBytes);
            socket.getOutputStream().flush();
        } catch (IOException ex) {
            throw new PrintException("Native Ethernet printer " + settings.endpoint() + " failed: "
                    + safeMessage(ex));
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
